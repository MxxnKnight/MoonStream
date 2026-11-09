package com.moon.moonstream.update

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
import androidx.core.content.ContextCompat
import com.moon.moonstream.MainActivity
import com.moon.moonstream.R
import com.moon.moonstream.util.AppLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service for APK update downloads. The download keeps going
 * even if the user changes screen or puts the app in the background
 * (moon's request 2026-11-02). A persistent notification shows progress;
 * tapping it returns to the app.
 *
 * Start via [start]; observe [state] for UI updates. Only one download
 * runs at a time.
 */
class UpdateDownloadService : Service() {

    companion object {
        private const val TAG = "UpdateDownload"
        private const val CHANNEL_ID = "moonstream_update"
        private const val NOTIF_ID = 43

        private const val ACTION_START = "com.moon.moonstream.update.START"
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_APK_NAME = "apk_name"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_NOTES = "notes"

        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val state: StateFlow<UpdateState> = _state.asStateFlow()

        /** The downloaded APK, set on success — the UI fires the installer. */
        @Volatile
        var downloadedApk: File? = null
            private set

        fun start(context: Context, info: UpdateInfo) {
            if (_state.value is UpdateState.Downloading) {
                AppLog.w(TAG, "Download already running — ignored")
                return
            }
            downloadedApk = null
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_APK_URL, info.apkUrl)
                putExtra(EXTRA_APK_NAME, info.apkName)
                putExtra(EXTRA_VERSION, info.version)
                putExtra(EXTRA_TAG, info.tag)
                putExtra(EXTRA_NOTES, info.notes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UpdateDownloadService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationManager: NotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (_state.value is UpdateState.Downloading) return START_NOT_STICKY

        val info = UpdateInfo(
            version = intent.getStringExtra(EXTRA_VERSION).orEmpty(),
            tag = intent.getStringExtra(EXTRA_TAG).orEmpty(),
            notes = intent.getStringExtra(EXTRA_NOTES).orEmpty(),
            apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty(),
            apkName = intent.getStringExtra(EXTRA_APK_NAME).orEmpty(),
        )
        startForeground(NOTIF_ID, buildNotification("Downloading update…", 0, "v${info.version}"))
        scope.launch { runDownload(info) }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(0, "v${info.version}")
        AppLog.i(TAG, "Download started: v${info.version}")
        try {
            // Throttle the notification: the downloader reports progress
            // on every 64 KB chunk (hundreds of calls for a 41 MB APK),
            // and each notify() is an IPC to the system. Only push a
            // notification when the whole percent actually changes.
            var lastNotified = -1
            val file = ApkInstaller.download(this, info) { p ->
                _state.value = UpdateState.Downloading(p, "v${info.version}")
                if (p != lastNotified) {
                    lastNotified = p
                    notificationManager?.notify(
                        NOTIF_ID,
                        buildNotification("Downloading update…", p, "v${info.version} — $p%"),
                    )
                }
            }
            downloadedApk = file
            _state.value = UpdateState.Done(info.version, file)
            notificationManager?.notify(
                NOTIF_ID,
                buildNotification("Update ready", 100, "Tap to install v${info.version}"),
            )
            AppLog.i(TAG, "Download complete: ${file.absolutePath}")
            // Fire the system installer.
            ApkInstaller.install(this, file)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            _state.value = UpdateState.Failed(msg)
            notificationManager?.notify(NOTIF_ID, buildNotification("Download failed", 0, msg))
            AppLog.e(TAG, "Download failed: $msg")
        } finally {
            kotlinx.coroutines.delay(4000)
            stopSelf()
        }
    }

    private fun buildNotification(title: String, progress: Int, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentIntent(pi)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Update downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "APK update downloads" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Observable download state for the UI. */
sealed class UpdateState {
    data object Idle : UpdateState()
    data class Downloading(val progress: Int, val version: String) : UpdateState()
    data class Done(val version: String, val apk: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}
