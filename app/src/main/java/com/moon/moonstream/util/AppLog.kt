package com.moon.moonstream.util

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One log line. */
data class LogLine(
    /** Unique per line — used as the LazyColumn key (identical lines are common). */
    val id: Long,
    val time: String,
    val tag: String,
    val level: String, // "I", "W", "E"
    val message: String,
)

/**
 * Real in-app log ring buffer. Replaces the old fake log lines.
 * Everything important goes here: indexing, filename mapping, metadata
 * matching (Cinemeta/TMDB/TVDB/Kitsu), hides, subtitles, custom content,
 * metadata fixes, merges, server on/off, Telegram bridge errors.
 *
 * Not persisted — cleared when the app closes (fresh on every launch).
 * The Logs screen's Clear button empties it.
 *
 * THREAD SAFETY: Compose's mutableStateListOf crashes if modified from a
 * background thread while the UI reads it (moon hit this opening Logs
 * during a deep audit). ALL mutations are posted to the main thread.
 */
object AppLog {
    private const val MAX = 200 // reduced from 500: fewer items = less memory, faster render
    val lines = mutableStateListOf<LogLine>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextId = java.util.concurrent.atomic.AtomicLong(0)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun add(level: String, tag: String, msg: String) {
        // SimpleDateFormat is NOT thread-safe and add() is called from many
        // threads during scans/audits — guard it.
        val time = synchronized(timeFmt) { timeFmt.format(Date()) }
        val line = LogLine(nextId.incrementAndGet(), time, tag, level, msg)
        // Also to logcat for debugging (safe from any thread).
        when (level) {
            "E" -> android.util.Log.e("MoonStream", "[$tag] $msg")
            "W" -> android.util.Log.w("MoonStream", "[$tag] $msg")
            else -> android.util.Log.d("MoonStream", "[$tag] $msg")
        }
        // Mutate Compose state ONLY on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            addOnMain(line)
        } else {
            mainHandler.post { addOnMain(line) }
        }
    }

    private fun addOnMain(line: LogLine) {
        lines.add(line)
        while (lines.size > MAX) lines.removeAt(0)
    }

    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lines.clear()
        } else {
            mainHandler.post { lines.clear() }
        }
    }
}
