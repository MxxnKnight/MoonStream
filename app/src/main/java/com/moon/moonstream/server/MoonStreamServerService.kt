package com.moon.moonstream.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.moon.moonstream.MainActivity
import com.moon.moonstream.R
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.state.AppUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service that keeps the MoonStream addon + stream servers running
 * while the app is in the background. A persistent notification (with a Stop
 * button) is always shown while running — that is how the user knows the
 * server is actually alive.
 */
class MoonStreamServerService : Service() {

    private var server: MoonStreamServer? = null
    private var notificationManager: NotificationManager? = null

    /** Timer that sweeps server-streamed bytes idle over 10 min while
     *  the server runs (moon's 2026-11-20 — without a timer, nothing
     *  cleaned up after a Stremio/Nuvio session ended). */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sweepJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshForNetworkChange("available")
        override fun onLost(network: Network) = refreshForNetworkChange("lost")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(NotificationManager::class.java)
        val cm = getSystemService(ConnectivityManager::class.java)
        try {
            cm?.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val port = intent?.getIntExtra(EXTRA_PORT, AppUiState.port) ?: AppUiState.port
        val streamPort = intent?.getIntExtra(EXTRA_STREAM_PORT, AppUiState.streamPort)
            ?: AppUiState.streamPort
        startServer(port, streamPort)
        return START_STICKY
    }

    private fun startServer(port: Int, streamPort: Int) {
        // Android requires startForeground() within ~5s of onStartCommand.
        // Call it FIRST so a slow/failed server bind can't trigger an ANR
        // ("did not call startForeground").
        startForeground(NOTIFICATION_ID, buildNotification())
        // Tear down any previous instance first (e.g. port changed).
        try { server?.stop() } catch (_: Exception) {}
        val s = MoonStreamServer(this, BackendProvider.backend, port, streamPort)
        server = s
        if (s.start()) {
            AppUiState.serverOn = true
            ServerTelemetry.onServerStart()
            val ip = MoonStreamServer.getLocalIpAddress()
            AppUiState.currentIp = ip
            com.moon.moonstream.util.AppLog.i("SERVER", "Addon server running: ${s.addonManifestUrl()}")
            // Refresh the notification now that we have the real URL.
            startForeground(NOTIFICATION_ID, buildNotification())
            startSweepTimer()
        } else {
            server = null
            AppUiState.serverOn = false
            com.moon.moonstream.util.AppLog.i("SERVER", "Failed to start server on ports $port/$streamPort")
            stopSelf()
        }
    }

    /** First sweep check 2 min after the server starts, then every
     *  15 min. maybeSweep throttles itself to 10-min intervals, so
     *  the timer composes with the app-start / player-close /
     *  served-request triggers. Safe to call again (a restart
     *  replaces the old timer). */
    private fun startSweepTimer() {
        sweepJob?.cancel()
        sweepJob = serviceScope.launch {
            delay(SWEEP_FIRST_TICK_MS)
            while (isActive) {
                ServedFiles.maybeSweep()
                delay(SWEEP_INTERVAL_MS)
            }
        }
    }

    private fun stopSweepTimer() {
        sweepJob?.cancel()
        sweepJob = null
    }

    private fun stopServer() {
        stopSweepTimer()
        try { server?.stop() } catch (_: Exception) {}
        server = null
        if (AppUiState.serverOn) {
            AppUiState.serverOn = false
            ServerTelemetry.flush(this)
            ServerTelemetry.onServerStop()
            com.moon.moonstream.util.AppLog.i("SERVER", "Addon server stopped")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        try {
            getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Re-advertise the LAN IP when the network changes; the sockets stay
     *  bound to 0.0.0.0 so no rebind is needed. */
    private fun refreshForNetworkChange(reason: String) {
        if (server?.isRunning() != true) return
        val ip = MoonStreamServer.getLocalIpAddress()
        if (ip != AppUiState.currentIp) {
            AppUiState.currentIp = ip
            com.moon.moonstream.util.AppLog.i("SERVER", "Network $reason — now at $ip")
            notificationManager?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val s = server
        val ip = MoonStreamServer.getLocalIpAddress()
        val statusText = if (s != null && s.isRunning()) {
            "Addon :${
                AppUiState.port
            } • Stream :${AppUiState.streamPort} • $ip"
        } else {
            "Starting…"
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = Intent(this, MoonStreamServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoonStream Server running")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_stat_stop, "Stop", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MoonStream Server",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows while the Stremio addon server is active, with a Stop button"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MoonStreamServerService"
        const val CHANNEL_ID = "moonstream_server_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.moon.moonstream.STOP_SERVER"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_STREAM_PORT = "extra_stream_port"

        /** First sweep check after the server starts. */
        private const val SWEEP_FIRST_TICK_MS = 2L * 60L * 1000L

        /** Sweep checks while the server runs. */
        private const val SWEEP_INTERVAL_MS = 15L * 60L * 1000L

        fun start(context: Context, port: Int = 8080, streamPort: Int = 8081) {
            val intent = Intent(context, MoonStreamServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_STREAM_PORT, streamPort)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MoonStreamServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
