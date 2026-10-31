package com.moon.moonstream.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a release APK into the app cache and fires the system installer.
 * Needs REQUEST_INSTALL_PACKAGES (already in the manifest); on Android 8+
 * the system prompts the user for "install unknown apps" permission once.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /**
     * Downloads [info]'s APK, reporting 0..100 progress. Returns the APK
     * file. Throws on failure.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clear stale downloads first.
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, info.apkName)
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MoonStream")
            // GitHub release assets redirect to a CDN — follow them.
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw Exception("Download failed (HTTP $code)")
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total != null) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            onProgress(100)
            out
        } finally {
            conn.disconnect()
        }
    }

    /** Fires the system package installer for a downloaded APK. */
    fun install(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            throw e
        }
    }
}
