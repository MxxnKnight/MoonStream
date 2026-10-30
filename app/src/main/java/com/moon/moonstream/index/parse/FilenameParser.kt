package com.moon.moonstream.index.parse

/**
* Phase 3 filename parser — written fresh for MoonStream.
*
* Turns messy Telegram filenames ("[ToonWorld4all] Solo.Leveling.S02E03.1080p.x264.mkv",
* "Avengers Endgame (2019) 720p [YTS] @MovieHub") into a clean title, type,
* year, season/episode and quality badge.
*
* What it strips: URLs, t.me links, @mentions, emojis, promo/spam lines,
* bracketed quality/codec tags, release-group tags, CRC codes, channel tags.
*/

/** Result of parsing one filename. `type` is "movie" or "series". */
data class ParsedMedia(
val title: String,
val type: String,
val year: String?,
val season: Int?,
val episode: Int?,
val quality: String,
val qualityBadge: String,
val languages: List<String>,
)

/** Lower-cased, punctuation-free title for grouping + matching. */
fun normalizeTitle(title: String): String =
title.lowercase()
.replace(Regex("[^a-z0-9]"), " ")
.replace(Regex("\\s+"), " ")
.trim()

object FilenameParser {

// ---- patterns ----

private val urlRe = Regex("""https?://\S+|t\.me/\S+|tg://\S+|www\.\S+""")
private val mentionRe = Regex("""(?:^|\s)@[\w_]+""")
private val emojiRe = Regex(
"[\uD800-\uDBFF][\uDC00-\uDFFF]|[\u2600-\u27BF\u2300-\u23FF\u2B50\u2B55\u200D\uFE0F]",
)
private val yearRe = Regex("""(?:^|[\s._\-(\[])(19\d{2}|20\d{2})(?:[\s._\-)\]]|$)""")
private val crcRe = Regex("""\[[0-9A-Fa-f]{8}\]""")

/** [1080p HEVC] style bracket tags. */
private val tagBracketRe = Regex(
"""[\(\[]\s*(?:1080p|720p|480p|360p|2160p|4k|uhd|bd|bluray|web-?rip|web-?dl|dual|multi|flac|hevc|x264|x265|ddp[^\]\)]*|atmos|hdr|dv|h\.?264|h\.?265|aac)[^\)\]]*[\)\]]""",
RegexOption.IGNORE_CASE,
)

/** Known release groups in brackets. */
private val groupRe = Regex(
"""[\(\[](?:YTS|YIFY|PSA|TGx|Pahe|GalaxyRG|GalaxyTV|Toonworld4all|SubsPlease|Erai|Judas|EMBER|MeGusta|ION10|EZTV|Vegamovies|KatmovieHD|PSArips|Hive|Tigole)[^\)\]]*[\)\]]""",
RegexOption.IGNORE_CASE,
)

/** S01E01 / S01EP01 / S1 E1 / S01 E01 / Season 1 Episode 2 */
private val sxxexxRe = Regex(
"""(?i)(?<![a-z0-9])(?:s|season\s*)(\d{1,2})[\s._-]*(?:e|ep|episode\s*)(\d{1,4})(?![a-z0-9])""",
)

/** 1x01 */
private val nxnRe = Regex("""(?<![a-zA-Z0-9])(\d{1,2})x(\d{1,4})(?![a-zA-Z0-9])""")

/** "Season 2" + "Episode 5" appearing separately. */
private val seasonOnlyRe = Regex("""(?i)(?<![a-z0-9])season\s*(\d{1,2})(?![a-z0-9])""")
private val episodeOnlyRe = Regex("""(?i)(?<![a-z0-9])episode\s*(\d{1,4})(?![a-z0-9])""")

/** [12 - Episode Title] bracketed anime episode. */
private val bracketEpRe = Regex("""^\[\s*(\d{1,4})\s*[-–—]\s*([^\]]+)\]""")

/** "Show Name - 02" anime hyphen episode. */
private val hyphenEpRe =
Regex("""^([A-Za-z0-9\s._\-+]+?)\s*[-–—]\s*0*(\d{1,4})(?:\s|$|\[|\()""")

/** "Show Name 08" trailing bare number (anime absolute numbering). */
private val trailingNumRe =
Regex("""^([A-Za-z0-9\s._\-+]+?)\s+0*(\d{1,4})\s*$""")

private val qualityPatterns = listOf(
Regex("""(?i)\b(2160p|4k|uhd)\b""") to "4K 2160p",
Regex("""(?i)\b(1080p|fhd)\b""") to "1080p",
Regex("""(?i)\b(720p|hd)\b""") to "720p",
Regex("""(?i)\b(480p|sd)\b""") to "480p",
Regex("""(?i)\b(360p|240p)\b""") to "360p",
)

private val languageMap = mapOf(
"hin" to "Hin", "hindi" to "Hin",
"tam" to "Tam", "tamil" to "Tam",
"tel" to "Tel", "telugu" to "Tel",
"mal" to "Mal", "malayalam" to "Mal",
"kan" to "Kan", "kannada" to "Kan",
"eng" to "Eng", "english" to "Eng",
"jap" to "Jap", "japanese" to "Jap", "jpn" to "Jap",
"kor" to "Kor", "korean" to "Kor",
"multi" to "Multi", "dual" to "Dual",
)

private val promoLineRe = Regex(
"""(?i)^\s*(?:join|subscribe|forwarded\s*from|channel|download|backup|contact|admin|owner|share)\b""",
)

/**
* Cleans one filename. Also accepts a caption: promo lines, URLs and
* @mentions are stripped, so "@MovieHub\nAvengers.Endgame.2019.720p.mkv"
* still parses.
*/
fun clean(raw: String): String {
var text = urlRe.replace(raw, " ")
text = mentionRe.replace(text, " ")
text = emojiRe.replace(text, " ")
// Drop promo lines, keep the rest.
val lines = text.split("\n", "\r")
.map { it.trim()}
.filter { it.isNotEmpty() &&!promoLineRe.containsMatchIn(it)}
text = lines.joinToString(" ")
return text.replace(Regex("\\s+"), " ").trim()
}

fun parse(rawFileName: String, isAnime: Boolean = false): ParsedMedia {
val cleaned = clean(rawFileName)
// Strip the extension for title work.
val base = cleaned.replace(Regex("""\.[a-zA-Z0-9]{2,4}$"""), "")

val year = yearRe.find(base)?.groupValues?.get(1)

val quality = qualityPatterns.firstOrNull { it.first.containsMatchIn(base)}?.second
?: "1080p"

val languages = base.lowercase()
.split(Regex("""[\s._\-+\[\](),]+"""))
.mapNotNull { languageMap[it]}
.distinct()

// Remove machine tags before title extraction.
var work = crcRe.replace(base, "")
work = tagBracketRe.replace(work, "")
work = groupRe.replace(work, "")
// Leading uploader tag: "[ToonWorld] Solo Leveling..." -> "Solo Leveling..."
val leading = Regex("""^(\[[^\]]+\]|\([^)]+\)|]+】)\s*""").find(work)
if (leading!= null) {
val rest = work.substring(leading.range.last + 1)
if (Regex("[a-zA-Z]{2,}").containsMatchIn(rest)) work = rest
}
// Trailing lone bracket group.
work = Regex("""\s*[\(\[][A-Za-z0-9_\-\.\s]{1,30}[\)\]]\s*$""").replace(work, "")

var season: Int? = null
var episode: Int? = null
var title: String = work

fun before(match: MatchResult): String =
work.substring(0, match.range.first).trim(' ', '.', '_', '-', '[', ']', '(', ')', '\t')

sxxexxRe.find(work)?.let { m ->
season = m.groupValues[1].toIntOrNull()
episode = m.groupValues[2].toIntOrNull()
title = before(m)
}?: nxnRe.find(work)?.let { m ->
season = m.groupValues[1].toIntOrNull()
episode = m.groupValues[2].toIntOrNull()
title = before(m)
}?: bracketEpRe.find(work)?.let { m ->
season = 1
episode = m.groupValues[1].toIntOrNull()
title = m.groupValues[2].trim()
}?: hyphenEpRe.find(work)?.let { m ->
// Only treat as episode when the tail after the number has no letters
// (avoids eating "Movie - The Beginning").
val tail = work.substring(m.range.last + 1)
if (!Regex("[a-zA-Z]{2,}").containsMatchIn(tail)) {
season = 1
episode = m.groupValues[2].toIntOrNull()
title = m.groupValues[1].trim()
}
}

if (season == null && episode == null) {
val sOnly = seasonOnlyRe.find(work)
val eOnly = episodeOnlyRe.find(work)
if (sOnly!= null && eOnly!= null) {
season = sOnly.groupValues[1].toIntOrNull()
episode = eOnly.groupValues[1].toIntOrNull()
val first = minOf(sOnly.range.first, eOnly.range.first)
title = work.substring(0, first).trim(' ', '.', '_', '-', '[', ']', '(', ')', '\t')
} else if (isAnime) {
// Anime absolute numbering: trailing bare number.
trailingNumRe.find(work)?.let { m ->
season = 1
episode = m.groupValues[2].toIntOrNull()
title = m.groupValues[1].trim()
}
}
}

// Strip year, quality words and leftover delimiters from the title.
year?.let { y -> title = title.replace(y, "")}
title = Regex(
"""(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|proper|repack|hq)\b.*$""",
).replace(title, "")
title = title.replace(Regex("""[._()\[\]\-]+"""), " ").trim()
title = title.replace(Regex("""^[:\s._\-~·•]+"""), "")
title = title.replace(Regex("""[:\s._\-~·•]+$"""), "")
title = title.replace(Regex("""\s+"""), " ").trim()

val type = if (season!= null || episode!= null) "series" else "movie"
val badge = if (languages.isEmpty()) quality else "$quality • ${languages.joinToString("/")}"

return ParsedMedia(
title = title,
type = type,
year = year,
season = season,
episode = episode,
quality = quality,
qualityBadge = badge,
languages = languages,
)
}
}
