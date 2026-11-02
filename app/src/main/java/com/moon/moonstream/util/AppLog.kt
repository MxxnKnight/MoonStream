package com.moon.moonstream.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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
 * UPDATE 2026-11-02 (moon's bug: "log also clears after kill!"): every line
 * is now ALSO appended to filesDir/logs/app.log, and init() seeds the
 * in-memory buffer with the file's tail — so after a crash/kill the Logs
 * screen still shows what happened before the death.
 *
 * THREAD SAFETY: Compose's mutableStateListOf crashes if modified from a
 * background thread while the UI reads it (moon hit this opening Logs
 * during a deep audit). ALL mutations are posted to the main thread.
 */
object AppLog {
    private const val MAX = 200 // reduced from 500: fewer items = less memory, faster render
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val TAG_APP = "APP"
    val lines = mutableStateListOf<LogLine>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextId = java.util.concurrent.atomic.AtomicLong(0)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var logFile: File? = null

    /**
     * Call once from MainActivity.onCreate. Points the file mirror at
     * filesDir/logs/app.log and seeds [lines] with the previous run's tail
     * (so a crash/kill doesn't wipe the evidence).
     */
    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "app.log")
        logFile = file
        io.execute {
            try {
                if (!file.exists()) return@execute
                val tail = file.readLines().takeLast(MAX)
                val seeded = tail.mapNotNull { parseLine(it) }
                if (seeded.isNotEmpty()) {
                    mainHandler.post {
                        // Oldest first; make room, then append.
                        while (lines.size + seeded.size > MAX) lines.removeAt(0)
                        lines.addAll(seeded)
                    }
                }
            } catch (e: Exception) { /* logging must never crash the app */ }
        }
        // Boundary marker: makes it obvious where this launch begins when the
        // older lines are still on screen (they are, until Clear or 200 lines).
        i(TAG_APP, "app launched — older lines kept from previous run")
    }

    private fun formatLine(line: LogLine): String =
        "${line.id}|${line.time}|${line.level}|${line.tag}|${line.message}"

    private fun parseLine(raw: String): LogLine? {
        val parts = raw.split("|", limit = 5)
        if (parts.size < 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        // Keep ids unique across restarts.
        nextId.updateAndGet { cur -> maxOf(cur, id) }
        return LogLine(id, parts[1], parts[3], parts[2], parts[4])
    }

    private fun appendToFile(line: LogLine) {
        val file = logFile ?: return
        io.execute {
            try {
                if (file.length() > MAX_FILE_BYTES) {
                    // Rotate: keep the last ~300 lines.
                    val kept = file.readLines().takeLast(300)
                    file.writeText(kept.joinToString("\n") + "\n")
                }
                file.appendText(formatLine(line) + "\n")
            } catch (e: Exception) { /* never break the app for logging */ }
        }
    }

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
        appendToFile(line)
    }

    fun d(tag: String, msg: String) = add("D", tag, msg)
    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    /**
     * Empties the screen AND the on-disk mirror.
     *
     * The file half used to be skipped, so "Clear" looked like it worked and
     * then init() seeded the same old lines straight back on the next launch.
     * The truncate is queued on the single I/O executor before the marker line
     * is appended, so ordering is guaranteed and the marker survives.
     */
    fun clear() {
        val file = logFile
        io.execute {
            try {
                file?.writeText("")
            } catch (e: Exception) { /* never break the app for logging */ }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lines.clear()
        } else {
            mainHandler.post { lines.clear() }
        }
        i(TAG_APP, "logs cleared by user")
    }
}
