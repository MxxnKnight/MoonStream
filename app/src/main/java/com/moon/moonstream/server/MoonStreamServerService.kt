package com.moon.moonstream.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.moon.moonstream.MainActivity
import com.moon.moonstream.R
import com.moon.moonstream.ui.state.AppUiState

/**
 * Foreground Service that keeps the MoonStream Addon Server running
 * in the background while streaming or when the app is minimized.
 */
class MoonStreamServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, AppUiState.port) ?: AppUiState.port
        startServer(port)
        return START_STICKY
    }

    private fun startServer(port: Int) {
        val success = MoonStreamServer.start(port)
        if (success) {
            AppUiState.serverOn = true
            val ip = MoonStreamServer.getLocalIpAddress()
            AppUiState.currentIp = ip
            AppUiState.addLog("SERVER", "Addon server running on http://$ip:$port/manifest.json")
            startForeground(NOTIFICATION_ID, buildNotification("Running at http://$ip:$port/manifest.json"))
        } else {
            AppUiState.serverOn = false
            AppUiState.addLog("SERVER", "Failed to start server on port $port")
            stopSelf()
        }
    }

    private fun stopServer() {
        MoonStreamServer.stop()
        AppUiState.serverOn = false
        AppUiState.addLog("SERVER", "Addon server stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoonStream Addon Server")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MoonStream Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while the Stremio addon server is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "moonstream_server_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.moon.moonstream.STOP_SERVER"
        const val EXTRA_PORT = "extra_port"

        fun start(context: Context, port: Int = 8080) {
            val intent = Intent(context, MoonStreamServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
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
