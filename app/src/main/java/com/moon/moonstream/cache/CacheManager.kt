package com.moon.moonstream.cache

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's on-disk cache: OTA APK downloads, and (in the future) video
 * chunks if a disk cache is ever added. The server currently streams
 * straight from Telegram without storing video, so this is mostly the
 * update downloads today.
 */
object CacheManager {
    /** Total bytes under [Context.getCacheDir]. */
    suspend fun usedBytes(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(context.cacheDir)
    }

    /** Deletes everything under the cache dir. Returns bytes freed. */
    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir
        val before = dirSize(dir)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        before - dirSize(dir)
    }

    private fun dirSize(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { dirSize(it) } ?: 0
    }
}
