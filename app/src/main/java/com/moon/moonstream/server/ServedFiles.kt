package com.moon.moonstream.server

import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.util.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tracks files the addon server served to Stremio/Nuvio, and drops
 * their Telegram copies once playback has been quiet (moon's
 * 2026-11-20 — server-streamed bytes had no cleanup, so they piled up
 * in TDLib's dir until a manual Clear Cache).
 *
 * The in-app player already purges its own files as you watch; this
 * covers the server path, which has no player window. Purging only
 * drops TDLib's cached copy via backend.deleteCachedFile — the
 * library entry stays, and the bytes download again on the next play.
 * A file idle under 10 minutes is never touched (a paused client may
 * resume), and a file the in-app player holds is never touched.
 */
object ServedFiles {

    /** A served file idle this long is done playing. */
    private const val IDLE_MS = 10L * 60L * 1000L

    /** Sweeps run at most this often. */
    private const val SWEEP_INTERVAL_MS = 10L * 60L * 1000L

    /** Give up on entries this old (a delete that keeps failing would
     *  otherwise retry forever). */
    private const val GIVE_UP_MS = 24L * 60L * 60L * 1000L

    private val lastServed = ConcurrentHashMap<Pair<Long, Long>, Long>()
    private val lastSweepAt = AtomicLong(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Remember that a file was just served (call on every range served). */
    fun note(channelId: Long, messageId: Long) {
        lastServed[channelId to messageId] = System.currentTimeMillis()
    }

    /** Runs a sweep on a background thread if one is due; returns at
     *  once otherwise. Safe to call from any thread, including the
     *  server's request threads. */
    fun maybeSweep() {
        val now = System.currentTimeMillis()
        val last = lastSweepAt.get()
        if (now - last < SWEEP_INTERVAL_MS) return
        if (!lastSweepAt.compareAndSet(last, now)) return
        scope.launch { sweep() }
    }

    private suspend fun sweep() {
        try {
            val now = System.currentTimeMillis()
            val idle = lastServed.entries.filter { now - it.value > IDLE_MS }
            if (idle.isEmpty()) return
            var purged = 0
            for ((key, servedAt) in idle) {
                val (channelId, messageId) = key
                if (now - servedAt > GIVE_UP_MS) {
                    lastServed.remove(key)
                    continue
                }
                if (TdStreamCache.isActive(channelId, messageId)) continue
                try {
                    if (BackendProvider.backend.deleteCachedFile(channelId, messageId)) {
                        lastServed.remove(key)
                        purged++
                    }
                } catch (e: Exception) {
                    // Retry on the next sweep.
                }
            }
            if (purged > 0) {
                AppLog.i("CACHE", "server sweep: dropped Telegram copies of $purged file(s) idle over 10 min")
            }
        } catch (e: Exception) {
            AppLog.w("CACHE", "server sweep failed: ${e.message}")
        }
    }
}
