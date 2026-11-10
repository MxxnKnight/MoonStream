package com.moon.moonstream.ui.player

/**
 * One subtitle line: when it shows and the plain text. Used by the player's
 * subtitle list (moon's pick 2026-11-10) — tap a line to jump to that moment.
 */
data class SubCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Minimal subtitle parser for the player's subtitle list. SRT and WebVTT
 * share the same block shape (an optional id, a "start --> end" line, then
 * text); ASS/SSA is parsed from its [Events] Dialogue lines. Anything it
 * can't parse yields an empty list — the list just stays empty.
 */
object SubtitleCues {

    fun parse(bytes: ByteArray, fileName: String): List<SubCue> {
        val raw = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        return when {
            fileName.endsWith(".ass", ignoreCase = true) ||
                fileName.endsWith(".ssa", ignoreCase = true) -> parseAss(raw)
            fileName.endsWith(".vtt", ignoreCase = true) -> parseBlocks(raw)
            else -> parseBlocks(raw) // .srt (and .sub as a best effort)
        }
    }

    /** SRT / WebVTT: blocks split by blank lines, each with a "-->" line. */
    private fun parseBlocks(text: String): List<SubCue> {
        val cues = mutableListOf<SubCue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n')
            .split("\n\n")
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            // Skip WebVTT header / notes / style blocks.
            if (lines[0].startsWith("WEBVTT") || lines[0].startsWith("NOTE") ||
                lines[0].startsWith("STYLE") || lines[0].startsWith("REGION")
            ) continue
            val timeIdx = lines.indexOfFirst { "-->" in it }
            if (timeIdx < 0) continue
            val (start, end) = parseTimeRange(lines[timeIdx]) ?: continue
            if (end <= start) continue
            val cueText = lines.drop(timeIdx + 1)
                .joinToString(" ")
                .let { stripTags(it) }
                .trim()
            if (cueText.isNotEmpty()) cues.add(SubCue(start, end, cueText))
        }
        return cues.sortedBy { it.startMs }
    }

    /** ASS/SSA: Dialogue lines in the [Events] section. */
    private fun parseAss(text: String): List<SubCue> {
        val cues = mutableListOf<SubCue>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        var inEvents = false
        // Field order from the "Format:" line; default is the usual order.
        var format = listOf(
            "Layer", "Start", "End", "Style", "Name",
            "MarginL", "MarginR", "MarginV", "Effect", "Text",
        )
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                format = line.substringAfter(":").split(",").map { it.trim() }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val body = line.substringAfter(":")
            // Split into at most format.size parts — Text (the last field)
            // may itself contain commas.
            val parts = body.split(",", limit = format.size)
            if (parts.size < format.size) continue
            val startIdx = format.indexOf("Start")
            val endIdx = format.indexOf("End")
            val textIdx = format.indexOf("Text")
            if (startIdx < 0 || endIdx < 0 || textIdx < 0) continue
            val start = parseAssTime(parts[startIdx].trim()) ?: continue
            val end = parseAssTime(parts[endIdx].trim()) ?: continue
            if (end <= start) continue
            val cueText = stripTags(parts[textIdx].trim())
                .replace("\\N", " ").replace("\\n", " ").trim()
            if (cueText.isNotEmpty()) cues.add(SubCue(start, end, cueText))
        }
        return cues.sortedBy { it.startMs }
    }

    /** "HH:MM:SS,mmm --> HH:MM:SS,mmm" (SRT) or with "." (WebVTT). */
    private fun parseTimeRange(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val start = parseClock(parts[0].trim()) ?: return null
        val end = parseClock(parts[1].trim().substringBefore(" ")) ?: return null
        return start to end
    }

    /** "HH:MM:SS,mmm" or "HH:MM:SS.mmm" (VTT also allows MM:SS.mmm). */
    private fun parseClock(s: String): Long? {
        val clean = s.replace(',', '.')
        val chunks = clean.split(":")
        return try {
            when (chunks.size) {
                3 -> {
                    val h = chunks[0].toLong()
                    val m = chunks[1].toLong()
                    val sec = chunks[2].toDouble()
                    ((h * 3600 + m * 60) * 1000 + (sec * 1000)).toLong()
                }
                2 -> {
                    val m = chunks[0].toLong()
                    val sec = chunks[1].toDouble()
                    ((m * 60) * 1000 + (sec * 1000)).toLong()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** ASS time "H:MM:SS.cc" (centiseconds). */
    private fun parseAssTime(s: String): Long? {
        val chunks = s.split(":")
        if (chunks.size != 3) return null
        return try {
            val h = chunks[0].toLong()
            val m = chunks[1].toLong()
            val sec = chunks[2].toDouble()
            ((h * 3600 + m * 60) * 1000 + (sec * 1000)).toLong()
        } catch (e: Exception) {
            null
        }
    }

    /** Strip <i>/<b>/etc tags and ASS {\...} override blocks. */
    private fun stripTags(s: String): String {
        var out = s
        out = out.replace(Regex("\\{[^}]*\\}"), "")
        out = out.replace(Regex("<[^>]+>"), "")
        return out
    }
}
