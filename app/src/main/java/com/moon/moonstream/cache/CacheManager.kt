package com.moon.moonstream.cache

import android.content.Context
import com.moon.moonstream.util.AppLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's on-disk cache: OTA APK downloads, and (in the future) video
 * chunks if a disk cache is ever added. The server currently streams
 * straight from Telegram without storing video, so this is mostly the
 * update downloads today.
 *
 * NOTE: video played in the app IS stored on the phone — Telegram's
 * download manager keeps the bytes under filesDir/tdlib_files (see
 * streamCacheBytes below). That directory belongs to TDLib: we may
 * MEASURE it, but only TDLib itself may DELETE from it (via
 * TdApi.DeleteFile). Deleting those files behind TDLib's back would
 * corrupt its download state and break streaming, so there is deliberately
 * no clear function for it here — clearing waits on the backend contract
 * (MoonStreamBackend.deleteCachedFile, implemented by the TDLib module).
 */
object CacheManager {
    /** Total bytes under [Context.getCacheDir]. */
    suspend fun usedBytes(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(context.cacheDir)
    }

    /**
     * Everything the in-app player leaves on the phone: Telegram's own
     * download dir (filesDir/tdlib_files, measured only) plus our sliding
     * byte window (filesDir/tdstream, ours to delete — see [TdStreamCache]).
     * Read-only measurement; safe to call any time.
     */
    suspend fun streamCacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(java.io.File(context.filesDir, "tdlib_files")) + TdStreamCache.bytes(context)
    }

    /** Deletes everything under the cache dir. Returns bytes freed. */
    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir
        val before = dirSize(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        val freed = before - dirSize(dir)
        AppLog.i("CACHE", "app cache: ${freed / (1024 * 1024)} MB freed")
        freed
    }

    private fun dirSize(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return realFileSize(f)
        return f.listFiles()?.sumOf { dirSize(it) } ?: 0
    }

    /**
     * Actual disk usage, not logical size. TDLib pre-allocates download files
     * at their full size (sparse files), so File.length() wildly overcounts —
     * a 2 GB video 10% downloaded reports 2 GB logical but ~200 MB on disk.
     * st_blocks counts the 512-byte blocks really allocated.
     */
    private fun realFileSize(f: File): Long {
        return try {
            val stat = android.system.Os.stat(f.absolutePath)
            stat.st_blocks * 512L
        } catch (e: Exception) {
            f.length()
        }
    }
}
