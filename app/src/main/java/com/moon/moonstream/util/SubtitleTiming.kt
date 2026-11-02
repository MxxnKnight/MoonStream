package com.moon.moonstream.util

/**
 * Subtitle timestamp shifting, done on-device.
 *
 * The addon server already does this for Stremio clients (`?delayMs=` in
 * MoonStreamServer.kt); this is the in-app copy so the player can show
 * subtitles with the server switched off. Same rules as the server's:
 * only "-->" cue lines are touched, SRT and WebVTT are supported, and
 * ASS/SSA is passed through untouched (its timing lives in a style-aware
 * format we don't rewrite).
 *
 * TODO(moon): MoonStreamServer's private shift* helpers can be replaced by
 * this object once the server is being changed anyway; behaviour is
 * identical, including clamping at zero.
 */
object SubtitleTiming {

    /** SRT cue time: `00:00:00,000`. */
    private val SRT_TS = Regex("""\d{2}:\d{2}:\d{2},\d{3}""")

    /** WebVTT cue time, long (`00:00:00.000`) or short (`00:00.000`). */
    private val VTT_TS = Regex("""(\d{2}:)?\d{2}:\d{2}\.\d{3}""")

    /** Returns [bytes] with every cue shifted by [delayMs], or [bytes] as-is. */
    fun shift(bytes: ByteArray, fileName: String, delayMs: Long): ByteArray {
        if (delayMs == 0L) return bytes
        return try {
            val text = bytes.toString(Charsets.UTF_8)
            val lower = fileName.lowercase()
            val shifted = when {
                lower.endsWith(".srt") -> shiftLines(text, delayMs, ::shiftSrtLine)
                lower.endsWith(".vtt") -> shiftLines(text, delayMs, ::shiftVttLine)
                else -> text // ASS/SSA: leave untouched.
            }
            shifted.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            bytes
        }
    }

    private fun shiftLines(
        text: String,
        delayMs: Long,
        shiftLine: (String, Long) -> String,
    ): String = text.lineSequence()
        .map { line -> if ("-->" in line) shiftLine(line, delayMs) else line }
        .joinToString("\n")

    private fun shiftSrtLine(line: String, delayMs: Long): String =
        SRT_TS.replace(line) { m ->
            val p = m.value.split(":", ",")
            val ms = (p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong()) * 1000 +
                p[3].toLong() + delayMs
            formatTs(ms.coerceAtLeast(0), ",")
        }

    private fun shiftVttLine(line: String, delayMs: Long): String =
        VTT_TS.replace(line) { m ->
            val parts = m.value.split(":", ".")
            val (h, mi, s, x) = if (parts.size == 4) {
                listOf(parts[0], parts[1], parts[2], parts[3])
            } else {
                listOf("0", parts[0], parts[1], parts[2])
            }
            val ms = (h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 +
                x.toLong() + delayMs
            val long = m.value.count { it == ':' } == 2
            formatTs(ms.coerceAtLeast(0), ".", withHours = long)
        }

    private fun formatTs(ms: Long, fracSep: String, withHours: Boolean = true): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val x = ms % 1000
        val base = if (withHours) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        return "$base$fracSep%03d".format(x)
    }
}
