package com.moon.moonstream.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

/**
 * Real server telemetry, replacing the old fake stats card.
 *
 * - [startTimeMs]: when the current server session began (0 = off). Lives
 *   here — not in a composable — so navigating away and back doesn't reset
 *   the uptime.
 * - [sessionBytes]: bytes served since the server started.
 * - [totalBytes]: bytes served across all sessions, persisted.
 *
 * THREAD SAFETY: addBytes() is called from the server's IO threads.
 * Compose state is only mutated on the main thread (same crash class
 * as the AppLog fix — moon hit it opening Logs during a deep audit).
 */
object ServerTelemetry {
    var startTimeMs by mutableStateOf(0L)
        private set

    private val _sessionBytes = AtomicLong(0)
    var sessionBytes by mutableLongStateOf(0L)
        private set

    var totalBytes by mutableLongStateOf(0L)
        private set

    private const val PREFS = "moonstream_prefs"
    private const val KEY_TOTAL = "server_total_bytes"

    private val mainHandler = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    fun load(context: Context) {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TOTAL, 0L)
        onMain { totalBytes = v }
    }

    fun onServerStart() {
        onMain {
            startTimeMs = System.currentTimeMillis()
            sessionBytes = 0
        }
        _sessionBytes.set(0)
        speedSamples.clear()
    }

    fun onServerStop() {
        onMain { startTimeMs = 0L }
    }

    /** Call for every chunk written to a client. */
    fun addBytes(context: Context, n: Long) {
        if (n <= 0) return
        val s = _sessionBytes.addAndGet(n)
        onMain {
            sessionBytes = s
            totalBytes += n
        }
        recordSpeedSample()
        // Persist every ~10 MB to avoid wearing the flash on every chunk.
        // Read the atomic value; the Compose copy catches up on main.
        val t = totalBytes
        if (t % (10L * 1024 * 1024) < n) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_TOTAL, t).apply()
        }
    }

    fun flush(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TOTAL, totalBytes).apply()
    }

    /** Seconds since the server started, or 0 when off. */
    fun uptimeSecs(): Int {
        val s = startTimeMs
        if (s == 0L) return 0
        return ((System.currentTimeMillis() - s) / 1000).toInt().coerceAtLeast(0)
    }

    // Rolling speed: keep (timestamp, bytes) samples, report bytes in the
    // last 3 seconds as B/s.
    private val speedSamples = ArrayDeque<Pair<Long, Long>>()

    /** Approximate current throughput in bytes/second. */
    fun speedBps(): Long {
        val now = System.currentTimeMillis()
        val cutoff = now - 3000
        while (speedSamples.isNotEmpty() && speedSamples.first().first < cutoff) {
            speedSamples.removeFirst()
        }
        if (speedSamples.size < 2) return 0
        val first = speedSamples.first()
        val last = speedSamples.last()
        val dt = (last.first - first.first).coerceAtLeast(1)
        return (last.second - first.second) * 1000 / dt
    }

    private fun recordSpeedSample() {
        val now = System.currentTimeMillis()
        // speedSamples is only touched from server IO threads; guard it.
        synchronized(speedSamples) {
            speedSamples.addLast(now to _sessionBytes.get())
            while (speedSamples.isNotEmpty() && speedSamples.first().first < now - 3000) {
                speedSamples.removeFirst()
            }
        }
    }
}
