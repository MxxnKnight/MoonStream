package com.moon.moonstream.index.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.moon.moonstream.R
import com.moon.moonstream.backend.BackendProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs [ScanManager] so a scan keeps going when the
 * user navigates to another screen — or puts the app in the background.
 * (moon's finding #1: the old fake scan died with the Channels screen.)
 *
 * This is a NEW service; the existing MoonStreamServerService (moon's AI,
 * Phase 4) is untouched.
 */
class ScanService : Service() {

    companion object {
        private const val TAG = "ScanService"
        private const val ACTION_START = "com.moon.moonstream.index.scan.START"
        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "moonstream_scan"

        private const val EXTRA_IDS = "ids"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_ANIME = "anime"
        private const val EXTRA_MODE = "mode"

        /** Starts a scan run. Safe to call from any screen. */
        fun start(context: Context, targets: List<ScanTarget>, mode: ScanMode) {
            if (targets.isEmpty()) return
            val intent = Intent(context, ScanService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IDS, targets.map { it.channelId }.toLongArray())
                putExtra(EXTRA_TITLES, targets.map { it.title }.toTypedArray())
                putExtra(EXTRA_ANIME, targets.map { it.isAnime }.toBooleanArray())
                putExtra(EXTRA_MODE, mode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (running) {
            Log.i(TAG, "scan already running — ignoring duplicate start")
            return START_NOT_STICKY
        }
        running = true

        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: longArrayOf()
        val titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: emptyArray()
        val anime = intent.getBooleanArrayExtra(EXTRA_ANIME) ?: BooleanArray(0)
        val mode = try {
            ScanMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "INCREMENTAL")
        } catch (e: Exception) {
            ScanMode.INCREMENTAL
        }
        val targets = ids.mapIndexed { i, id ->
            ScanTarget(id, titles.getOrElse(i) { "Channel" }, anime.getOrElse(i) { false })
        }
        if (targets.isEmpty()) {
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForegroundCompat(buildNotification("Starting scan…"))

        scope.launch {
            try {
                updateNotification("Scanning ${targets.size} channel(s)…")
                ScanManager.scanAll(this@ScanService, BackendProvider.backend, targets, mode)
            } catch (e: Exception) {
                Log.e(TAG, "scan run failed: ${e.message}")
            } finally {
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        running = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Library scan",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows while MoonStream scans your channels" },
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoonStream scan")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(notif: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }
}
