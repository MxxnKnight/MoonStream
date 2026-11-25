package com.moon.moonstream.server

import android.content.Context
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.util.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
 *
 * The served list survives restarts: it is saved to SharedPreferences
 * (debounced) and reloaded on init, so a restart no longer strands
 * downloaded bytes with no one remembering them. Sweeps run on a
 * timer while the server service is alive, plus the existing
 * app-start / player-close / served-request triggers.
 *
 * Active serves are counted too (moon's 2026-11-25): when a client's
 * stream ends — played through, or closed early — and no other client
 * is serving that file, its Telegram copy is dropped right away
 * instead of waiting for the 10-minute idle sweep. The in-app player
 * still wins: a file it holds is never touched.
 *
 * Known gap (TDLib source, 2026-11-25): deleteFile only unlinks the
 * local partial — it does NOT cancel the background download (only
 * cancelDownloadFile does that, and the app has no such API yet). So
 * the drop frees the visible copy at once, but TDLib's loader may
 * keep downloading until the cancel API lands — see
 * docs/server-cancel-freebuff.md. The warmer has the same gap.
 */
object ServedFiles {

    /** A served file idle this long is done playing. */
    private const val IDLE_MS = 10L * 60L * 1000L

    /** Sweeps run at most this often. */
    private const val SWEEP_INTERVAL_MS = 10L * 60L * 1000L

    /** Give up on entries this old (a delete that keeps failing would
     *  otherwise retry forever). */
    private const val GIVE_UP_MS = 24L * 60L * 60L * 1000L

    /** SharedPreferences holding the served list. */
    private const val PREFS = "served_files"

    /** One string key: entries as "channelId:messageId=timestamp",
     *  semicolon-separated. */
    private const val KEY_ENTRIES = "entries"

    /** Most entries kept on disk (the most recent ones win). */
    private const val MAX_STORED_ENTRIES = 1000

    /** Saves from note() run at most this often. */
    private const val SAVE_DEBOUNCE_MS = 60L * 1000L

    private val lastServed = ConcurrentHashMap<Pair<Long, Long>, Long>()

    /** Live streams per file (channelId to messageId). The server
     *  calls [beginServe] when a stream starts and [endServe] when it
     *  ends; the count guards the drop so one client's disconnect
     *  never pulls the file out from under another client. */
    private val activeServes = ConcurrentHashMap<Pair<Long, Long>, AtomicInteger>()

    private val lastSweepAt = AtomicLong(0L)
    private val lastSaveAt = AtomicLong(0L)
    private val initialized = AtomicBoolean(false)
    private val appContext = AtomicReference<Context?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Load the saved served list. Called once from MoonStreamApp
     *  onCreate; safe to call again (a no-op after the first init). */
    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext.set(context.applicationContext)
        scope.launch { load() }
    }

    /** Remember that a file was just served (call on every range served). */
    fun note(channelId: Long, messageId: Long) {
        lastServed[channelId to messageId] = System.currentTimeMillis()
        maybeSave()
    }

    /** A client started streaming a file. Call once per stream, before
     *  the first byte is read; pair with [endServe]. Safe from any
     *  thread, including the server's request threads. */
    fun beginServe(channelId: Long, messageId: Long) {
        activeServes.compute(channelId to messageId) { _, v ->
            (v ?: AtomicInteger(0)).apply { incrementAndGet() }
        }
        note(channelId, messageId)
    }

    /** A client's stream ended — played through, errored, or the
     *  client disconnected. When the count hits zero the file's
     *  Telegram copy is dropped (unless the in-app player holds it),
     *  so a closed-early session stops piling up bytes at once
     *  instead of waiting for the idle sweep. Safe from any thread. */
    fun endServe(channelId: Long, messageId: Long) {
        val key = channelId to messageId
        val count = activeServes.computeIfPresent(key) { _, v ->
            v.apply { decrementAndGet() }
        }
        if (count == null || count.get() <= 0) {
            activeServes.remove(key)
            scope.launch { dropIfIdle(key) }
        }
    }

    /** Drop one file's Telegram copy now that no client serves it.
     *  Runs on the IO scope. Re-checks the active count first: a new
     *  stream may have started between the decrement and this launch,
     *  and it must not lose the file mid-stream. */
    private suspend fun dropIfIdle(key: Pair<Long, Long>) {
        try {
            if (activeServes.containsKey(key)) return
            val (channelId, messageId) = key
            if (TdStreamCache.isActive(channelId, messageId)) return
            if (BackendProvider.backend.deleteCachedFile(channelId, messageId)) {
                lastServed.remove(key)
                AppLog.i("SERVER", "client left — dropped Telegram copy for $channelId:$messageId")
                save()
            }
        } catch (e: Exception) {
            AppLog.w("SERVER", "drop after stream end failed: ${e.message}")
        }
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
                save()
            }
        } catch (e: Exception) {
            AppLog.w("CACHE", "server sweep failed: ${e.message}")
        }
    }

    /** Reload the served list saved by an earlier process. Entries
     *  older than GIVE_UP_MS are dropped; the rest cap at the 1000
     *  most recent. */
    private fun load() {
        val ctx = appContext.get() ?: return
        try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, null) ?: return
            val now = System.currentTimeMillis()
            raw.split(";").forEach { entry ->
                val kv = entry.split("=", limit = 2)
                if (kv.size != 2) return@forEach
                val ids = kv[0].split(":", limit = 2)
                if (ids.size != 2) return@forEach
                val channelId = ids[0].toLongOrNull() ?: return@forEach
                val messageId = ids[1].toLongOrNull() ?: return@forEach
                val servedAt = kv[1].toLongOrNull() ?: return@forEach
                if (now - servedAt > GIVE_UP_MS) return@forEach
                lastServed[channelId to messageId] = servedAt
            }
            pruneToCap()
        } catch (e: Exception) {
            AppLog.w("CACHE", "served list load failed: ${e.message}")
        }
    }

    /** Save the served list, debounced to at most once a minute.
     *  A no-op until init() has run. */
    private fun maybeSave() {
        if (!initialized.get()) return
        val now = System.currentTimeMillis()
        val last = lastSaveAt.get()
        if (now - last < SAVE_DEBOUNCE_MS) return
        if (!lastSaveAt.compareAndSet(last, now)) return
        scope.launch { save() }
    }

    /** Write the served list to SharedPreferences now. A no-op until
     *  init() has run. Runs on the IO scope — never call on the main
     *  thread. */
    private fun save() {
        val ctx = appContext.get() ?: return
        try {
            pruneToCap()
            val raw = lastServed.entries.joinToString(";") { (key, servedAt) ->
                "${key.first}:${key.second}=$servedAt"
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENTRIES, raw).apply()
        } catch (e: Exception) {
            AppLog.w("CACHE", "served list save failed: ${e.message}")
        }
    }

    /** Keep only the MAX_STORED_ENTRIES most recently served. */
    private fun pruneToCap() {
        if (lastServed.size <= MAX_STORED_ENTRIES) return
        lastServed.entries.sortedByDescending { it.value }
            .drop(MAX_STORED_ENTRIES)
            .forEach { lastServed.remove(it.key) }
    }
}
