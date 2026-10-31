package com.moon.moonstream.util

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One log line. */
data class LogLine(
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
 */
object AppLog {
    private const val MAX = 500
    val lines = mutableStateListOf<LogLine>()

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun add(level: String, tag: String, msg: String) {
        val line = LogLine(timeFmt.format(Date()), tag, level, msg)
        // Keep UI thread safe: post to main if needed (callers are often on IO).
        synchronized(lines) {
            lines.add(line)
            while (lines.size > MAX) lines.removeAt(0)
        }
        // Also to logcat for debugging.
        when (level) {
            "E" -> android.util.Log.e("MoonStream", "[$tag] $msg")
            "W" -> android.util.Log.w("MoonStream", "[$tag] $msg")
            else -> android.util.Log.d("MoonStream", "[$tag] $msg")
        }
    }

    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    fun clear() {
        synchronized(lines) { lines.clear() }
    }
}
