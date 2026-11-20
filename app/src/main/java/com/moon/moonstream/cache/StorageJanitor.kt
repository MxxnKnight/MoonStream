package com.moon.moonstream.cache

import android.content.Context
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.util.AppLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the app small on moon's phone (2026-11-20 — the app had grown
 * to 248 MB): an automatic cleanup when the stream cache overgrows,
 * and enforcement of the Cache MB cap the user sets in Settings.
 *
 * Everything here is best effort and never throws: a failed cleanup
 * logs a warning and leaves the bytes for the next run. Nothing here
 * ever touches the library database, SharedPreferences, TDLib's own
 * database, or a file a playback is holding.
 */
object StorageJanitor {

    /** Stream cache above this triggers an automatic cleanup. */
    const val AUTO_CLEAN_THRESHOLD_MB = 500L

    /** Files played more recently than this are never auto-purged. */
    private const val RECENT_PLAY_DAYS = 7L

    /** Max Telegram copies dropped in one auto-clean run. Each purge
     *  can cost a Telegram lookup, so the run stays bounded. */
    private const val MAX_PURGE_PER_RUN = 200

    /** Max purge passes in one auto-clean run (25 x 200 = 5000 files,
     *  the whole library and then some). Each pass takes ~1s. */
    private const val MAX_CLEAN_PASSES = 25

    /** Purge attempts remembered for a day, so consecutive runs make
     *  progress down the list instead of retrying the same head. */
    private const val ATTEMPT_MEMORY_MS = 24L * 60L * 60L * 1000L

    /** Files touched this recently are never trimmed: an update
     *  download in progress, or an APK that just landed. */
    private const val FRESH_FILE_AGE_MS = 10L * 60L * 1000L

    /** Coil's poster cache dir — Coil bounds it itself at 50 MB
     *  (see MoonStreamApp), so the trimmer never deletes behind it. */
    private const val COIL_CACHE_DIR = "image_cache"

    /** The updater's folder — the trimmer must never eat a staged
     *  or in-progress update (moon's 2026-11-20). */
    private const val UPDATES_DIR = "updates"

    private const val PREFS = "moonstream_prefs"
    private const val KEY_CACHE_MAX_MB = "cache_max_mb"

    private val attemptedRecently = ConcurrentHashMap<Long, Long>()

    /**
     * Automatic cleanup (moon's pick 2026-11-20). Runs on app start
     * and after the player closes. If the stream cache (Telegram's
     * downloaded bytes plus our player chunks) is over 500 MB, first
     * drops our leftover chunks, then drops Telegram's copies of
     * files not played in the last 7 days, oldest first, in passes of
     * 200 until the cache is under the threshold or nothing is left
     * to drop (one pass freed 0 MB on moon's phone — most picked
     * files never held bytes). Files a playback is holding are
     * skipped. Purging only drops TDLib's
     * cached copy — the library entry stays, and the bytes download
     * again on the next play.
     */
    suspend fun maybeAutoClean(context: Context) {
        try {
            val threshold = AUTO_CLEAN_THRESHOLD_MB * 1024L * 1024L
            val before = CacheManager.streamCacheBytes(context)
            if (before <= threshold) return
            val chunksFreed = withContext(Dispatchers.IO) { TdStreamCache.clear(context) }
            var after = CacheManager.streamCacheBytes(context)
            var purged = 0
            var passes = 0
            // One pass drops at most 200 copies and most of them never
            // held bytes (moon's 2026-11-20 log: 200 dropped, 0 MB freed),
            // so keep passing until the cache is under the threshold or
            // nothing is left to drop. Each pass takes the next 200
            // (attempted files are remembered for 24h), so the loop walks
            // the whole library instead of stopping at the first 200
            // empty deletes.
            while (after > threshold && passes < MAX_CLEAN_PASSES) {
                val purgedThisPass = purgeStaleTelegramCopies(context)
                passes++
                if (purgedThisPass == 0) break
                purged += purgedThisPass
                after = CacheManager.streamCacheBytes(context)
            }
            val mb = 1024L * 1024L
            AppLog.i(
                "CACHE",
                "auto-cleanup: stream cache ${before / mb} MB over " +
                    "${AUTO_CLEAN_THRESHOLD_MB} MB — freed ${(before - after) / mb} MB " +
                    "(chunks ${chunksFreed / mb} MB, $purged Telegram copies dropped in $passes passes)",
            )
        } catch (e: Exception) {
            AppLog.w("CACHE", "auto-cleanup failed: ${e.message}")
        }
    }

    /**
     * Drops Telegram's cached copies of files not played in 7 days,
     * oldest first, up to MAX_PURGE_PER_RUN. Returns how many were
     * dropped. Files a playback holds are skipped. Used by the
     * auto-cleanup and by cap enforcement.
     */
    suspend fun purgeStaleTelegramCopies(context: Context): Int {
        val db = MsDatabase.get(context)
        val cutoff = System.currentTimeMillis() - RECENT_PLAY_DAYS * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        attemptedRecently.entries.removeIf { now - it.value > ATTEMPT_MEMORY_MS }
        val lastPlayed = db.watchProgressDao().getAll()
            .groupBy { it.streamFileId }
            .mapValues { (_, rows) -> rows.maxOf { it.playedAt } }
        val candidates = db.streamDao().getAll()
            .filter { f ->
                !TdStreamCache.isActive(f.channelId, f.messageId) &&
                    (lastPlayed[f.id] ?: 0L) < cutoff &&
                    !attemptedRecently.containsKey(f.id)
            }
            .sortedBy { lastPlayed[it.id] ?: 0L }
            .take(MAX_PURGE_PER_RUN)
        var purged = 0
        val backend = BackendProvider.backend
        for (f in candidates) {
            attemptedRecently[f.id] = System.currentTimeMillis()
            try {
                if (backend.deleteCachedFile(f.channelId, f.messageId)) purged++
            } catch (e: Exception) {
                // One failure never stops the run.
            }
        }
        return purged
    }

    /**
     * Enforces the Cache MB cap from Settings (moon's pick
     * 2026-11-20 — the cap was display-only until now). Runs on app
     * start and after an update APK lands. Measures the whole cache
     * (app cache dir + Telegram's downloaded video bytes): trims
     * cacheDir back under the cap first, oldest files first, then
     * drops Telegram copies of files unplayed 7+ days if still over.
     * Files played in the last 7 days are never purged, so the total
     * can stay over the cap until they age out. Never touches Coil's
     * poster cache (Coil caps it at 50 MB itself), and never deletes
     * a file touched in the last 10 minutes (an update download in
     * progress or an APK that just landed). A cap of 0 means
     * enforcement is off.
     */
    suspend fun enforceCacheCap(context: Context) {
        try {
            val maxMb = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_CACHE_MAX_MB, 2048)
            if (maxMb <= 0) return
            val cap = maxMb.toLong() * 1024L * 1024L
            val mb = 1024L * 1024L
            var used = CacheManager.totalCacheBytes(context)
            if (used <= cap) return
            val over = used
            val cacheFreed = withContext(Dispatchers.IO) { trimCacheDir(context, used - cap) }
            used = CacheManager.totalCacheBytes(context)
            var purged = 0
            if (used > cap) {
                purged = purgeStaleTelegramCopies(context)
                used = CacheManager.totalCacheBytes(context)
            }
            AppLog.i(
                "CACHE",
                "cache cap: total ${over / mb} MB over ${maxMb} MB cap — " +
                    "trimmed ${cacheFreed / mb} MB from app cache, dropped " +
                    "$purged stale Telegram copies — now ${used / mb} MB",
            )
        } catch (e: Exception) {
            AppLog.w("CACHE", "cache cap enforcement failed: ${e.message}")
        }
    }

    /** Deletes oldest-first files under cacheDir until needBytes are
     *  freed. Skips Coil's poster dir and fresh files. Returns bytes freed. */
    private fun trimCacheDir(context: Context, needBytes: Long): Long {
        val now = System.currentTimeMillis()
        val files = ArrayList<File>()
        fun walk(dir: File) {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    if (f.name != COIL_CACHE_DIR && f.name != UPDATES_DIR) walk(f)
                } else if (now - f.lastModified() > FRESH_FILE_AGE_MS) {
                    files.add(f)
                }
            }
        }
        walk(context.cacheDir)
        files.sortBy { it.lastModified() }
        var freed = 0L
        for (f in files) {
            if (freed >= needBytes) break
            val size = f.length()
            if (f.delete()) freed += size
        }
        return freed
    }
}
