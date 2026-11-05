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
    val episodeEnd: Int? = null,
    val quality: String,
    val qualityBadge: String,
    val languages: List<String>,
    val splitPart: Int? = null,
    /** Explicit IMDb id found in the name/caption (e.g. "tt0133093"), else null. */
    val imdbId: String? = null,
)

/** Lower-cased, punctuation-free title for grouping + matching. */
fun normalizeTitle(title: String): String =
    title.lowercase()
        .replace(Regex("[^a-z0-9]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

object FilenameParser {

    // ---- patterns ----

    private val splitExtRe = Regex("""(?i)\.(?:(?:mkv|mp4|avi|webm)\.)?0*(\d{1,3})$""")
    private val splitPartRe = Regex("""(?i)(?:^|[\s._\-])(?:part|pt)0*(\d{1,3})(?:[\s._\-]|$)|0*(\d{1,3})$""")
    private val urlRe = Regex("""https?://\S+|t\.me/\S+|tg://\S+|www\.\S+""")
    /** Explicit IMDb id typed into a name/caption. */
    private val imdbRe = Regex("""(?i)(?<![a-z0-9])tt\d{7,10}(?![a-z0-9])""")
    /** Bare site names with unambiguous TLDs: "Movies4u.com", "1TamilMV.cc". */
    private val domainRe = Regex(
        """(?i)(?<![a-z0-9])[a-z0-9\-]+\.(?:com|net|org|xyz|cc|info|club|site|ws|lol|vip|cyou|sbs|click|biz|cfd|buzz|icu|ink)(?![a-z0-9])""",
    )
    /** Language / audio words left dangling after the title ("Kalki 2898 AD Hindi"). */
    private val trailingLangRe = Regex(
        """(?i)(?:\s+(?:hindi|tamil|telugu|malayalam|kannada|bengali|punjabi|english|korean|japanese|dubbed|dual\s+audio|multi\s+audio|dual|multi|audio|org))+$""",
    )
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

    /** Standalone "E22" (episode without season, e.g. "Psycho Pass E22").
     *  Must not match words ending in E like "TRUE". */
    private val standaloneEpRe = Regex("""(?i)(?<![a-z0-9])(?:e|ep)\s*(\d{1,4})(?![a-z0-9])""")

    /** Multi-episode range: "E7-8", "Ep 1-10", "E01-E05", "S01E01-E02",
     *  "EP (01-06)" (moon's #Love file 2026-11-02 — parens around the range).
     *  Group 1 = start, group 2 = end. */
    private val multiEpRe = Regex("""(?i)(?<![a-z0-9])(?:e|ep)\s*\(?\s*(\d{1,4})\s*[-–—]\s*(?:e|ep)?\s*(\d{1,4})\s*\)?(?![a-z0-9])""")

    /** [12 - Episode Title] bracketed anime episode. */
    private val bracketEpRe = Regex("""^\[\s*(\d{1,4})\s*[-–—]\s*([^\]]+)\]""")

    /** "Show Name - 02" anime hyphen episode. */
    private val hyphenEpRe =
        Regex("""^([A-Za-z0-9\s._\-+]+?)\s*[-–—]\s*0*(\d{1,4})(?:\s|$|\[|\()""")

    /** "Show Name 08" trailing bare number (anime absolute numbering). */
    private val trailingNumRe =
        Regex("""^([A-Za-z0-9\s._\-+]+?)\s+0*(\d{1,4})\s*$""")

    /** Cut from the first quality word to end of string ("One Piece 1012
     *  1080p x264" -> "One Piece 1012"). Used by the title cleanup AND by
     *  the absolute-episode probe, so both agree on where quality starts. */
    private val qualityCutRe = Regex(
        """(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|hd-?rip|hdtv|hdtc|hdcam|dvd-?rip|br-?rip|bd-?rip|cam-?rip|amzn|dsnp|hmax|atvp|zee5|hotstar|sonyliv|10bit|esubs?|msubs?|dd[p+]?\d|atmos|remux|proper|repack|hq)\b.*$""",
    )

    /** File-size token ("450MB", "1.2 GB") — stripped by the absolute probe
     *  so it can't hide the episode number at the end of a name. */
    private val sizeTokenRe = Regex("""(?i)\s*\d+(\.\d+)?\s*(GB|MB|KB)\b""")

    /** One trailing bracket group ("[Tam + Eng]", "(2026)") — the absolute
     *  probe strips these repeatedly from the end of a name. */
    private val trailingBracketRe = Regex("""\s*[\(\[][^\]\)]*[\)\]]\s*$""")

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
        text = domainRe.replace(text, " ")
        text = mentionRe.replace(text, " ")
        text = emojiRe.replace(text, " ")
        // Drop promo lines, keep the rest.
        val lines = text.split("\n", "\r")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !promoLineRe.containsMatchIn(it) }
        text = lines.joinToString(" ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Light cleanup for DISPLAY names (moon's request 2026-11-04): strips
     * @channel mentions and telegram/URLs but keeps everything else intact
     * (emojis, quality tags, etc.). Also trims the separator junk a stripped
     * mention leaves behind — "@Chan_Title..." leaves ",_Title..." — and the
     * space before the extension ("750MB @Chan.mkv" -> "750MB.mkv").
     */
    fun cleanDisplayName(raw: String): String {
        var text = urlRe.replace(raw, " ")
        text = domainRe.replace(text, " ")
        text = mentionRe.replace(text, " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        text = text.replace(Regex("^[\\s,._\\-\u2013\u2014]+"), "").trim()
        text = text.replace(
            Regex("\\s+\\.(?i)(mkv|mp4|avi|ts|webm|m4v|mov|flv|wmv)$"), ".$1",
        )
        return text
    }

    // ---- caption-first naming (same idea as NoxMedia's extract_best_filename) ----

    private val dividerRe = Regex("""^[\s\-_=*#~━─—–•·:|]+$""")
    private val labelRe = Regex("""(?i)^(?:file(?:\s*name)?|name|title)\s*[:：\-]\s*""")
    private val videoExtRe = Regex("""(?i)\.(?:mkv|mp4|avi|ts|webm|m4v|mov|flv|wmv)(?:\.\d{2,4})?$""")
    private val epOrYearRe = Regex("""(?i)(?:s\d+|season|ep|episode|\b(?:19|20)\d{2}\b)""")
    private val mediaLikeRe = Regex(
        """(?i)\b(?:2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x265|x264|h\.?265|h\.?264|bluray|blu-ray|bdrip|web-?dl|webrip|dvdrip|hdrip|remux|flac|aac|dts|atmos|ddp|truehd)\b|(?<![a-zA-Z0-9])(?:s|season\s*)\d{1,2}(?:[\s._-]*(?:e|ep)\d{1,4})?(?![a-zA-Z0-9])|(?<![a-zA-Z0-9])\d{1,2}x\d{1,4}(?![a-zA-Z0-9])|(?:^|[\s._\-(\[])(?:19|20)\d{2}(?:[\s._\-)\]]|$)""",
    )
    /** Names our own backend invents when Telegram gave none (file_123, video_9.mp4) or hash-like names. */
    private val genericNameRe = Regex("""(?i)^(?:(?:file|video)_\d+|[0-9a-f]{16,}|\d{6,})(?:\.\w{2,4})?$""")

    /**
     * Picks the best text to parse: the cleaned caption when it looks like a
     * release name (or the filename is generic/obfuscated), else the filename.
     * Captions are where channels usually put the clean "Title (Year) ..." and
     * where the ads/links are, so they are cleaned first.
     */
    fun bestName(fileName: String, caption: String): String {
        val fn = fileName.trim()
        if (caption.isBlank()) return fn
        var cap = urlRe.replace(caption, " ")
        cap = domainRe.replace(cap, " ")
        cap = mentionRe.replace(cap, " ")
        val lines = cap.split("\n", "\r")
            .map { emojiRe.replace(it, " ").trim().trimStart('-', '*', '#', '\u2022', ' ') }
            .filter { it.isNotEmpty() && !dividerRe.matches(it) && !promoLineRe.containsMatchIn(it) }
            .map { labelRe.replace(it, "").trim() }
            .filter { it.isNotEmpty() && Regex("[a-zA-Z0-9]{2,}").containsMatchIn(it) }
        if (lines.isEmpty()) return fn

        // A single line that is itself a complete file name wins; otherwise the
        // first media-looking line; otherwise everything joined.
        val capText = lines.firstOrNull { videoExtRe.containsMatchIn(it) && epOrYearRe.containsMatchIn(it) }
            ?: lines.firstOrNull { mediaLikeRe.containsMatchIn(it) }
            ?: lines.joinToString(" ")
        val fnIsGeneric = genericNameRe.matches(fn)
        // The caption must actually contain a title (letters before the first
        // quality/year tag) - "1080p WEB-DL" alone must not replace a good name.
        val head = capText.substring(0, mediaLikeRe.find(capText)?.range?.first ?: capText.length)
        val capHasTitle = Regex("[A-Za-z]{3,}").containsMatchIn(head) ||
            sxxexxRe.containsMatchIn(capText)
        val capIsMedia = mediaLikeRe.containsMatchIn(capText) && capHasTitle
        if (!capIsMedia && !fnIsGeneric) return fn
        // Don't lose episode numbers: if only the filename has S01E02, keep it.
        val fnHasEp = sxxexxRe.containsMatchIn(fn) || nxnRe.containsMatchIn(fn)
        val capHasEp = sxxexxRe.containsMatchIn(capText) || nxnRe.containsMatchIn(capText)
        if (fnHasEp && !capHasEp && !fnIsGeneric) return fn

        // parse() strips a trailing ".ext", so make sure there is one.
        val ext = Regex("""(?i)\.(?:mkv|mp4|avi|ts|webm|m4v|mov|rar|zip|7z)(?:\.\d{2,4})?$""").find(fn)?.value
        return if (videoExtRe.containsMatchIn(capText)) capText else capText + (ext ?: ".mkv")
    }

    /**
     * @param absoluteAnime honours the Settings "Anime numbering" switch:
     *  true = Absolute (One Piece counts 1..1012), false = Seasonal (S/E).
     *  Only matters when [isAnime] is true.
     */
    fun parse(
        rawFileName: String,
        isAnime: Boolean = false,
        caption: String = "",
        absoluteAnime: Boolean = false,
    ): ParsedMedia {
        val name = bestName(rawFileName, caption)
        // An explicit IMDb id (filename OR caption) wins over any title
        // guessing; remove it so it never pollutes the title.
        val imdbId = imdbRe.find(rawFileName)?.value?.lowercase()
            ?: imdbRe.find(caption)?.value?.lowercase()
        val cleaned = clean(imdbRe.replace(name, " "))
        var splitPart: Int? = null
        splitExtRe.find(cleaned)?.let { m ->
            splitPart = m.groupValues[1].toIntOrNull()
        }

        // Strip split extensions (.001, .002) and media extensions for title work.
        var noSplit = cleaned.replace(Regex("""(?i)\.(?:(?:mkv|mp4|avi|webm)\.)?0*\d{1,3}$"""), "")
        val base = noSplit.replace(Regex("""\.[a-zA-Z0-9]{2,4}$"""), "")

        val year = yearRe.find(base)?.groupValues?.get(1)

        val quality = qualityPatterns.firstOrNull { it.first.containsMatchIn(base) }?.second
            ?: "1080p"

        val languages = base.lowercase()
            .split(Regex("""[\s._\-+\[\](),]+"""))
            .mapNotNull { languageMap[it] }
            .distinct()

        // Remove machine tags before title extraction.
        var work = crcRe.replace(base, "")
        work = tagBracketRe.replace(work, "")
        work = groupRe.replace(work, "")
        // Leading uploader tag: "[ToonWorld] Solo Leveling..." -> "Solo Leveling..."
        val leading = Regex("""^(\[[^\]]+\]|\([^)]+\)|【[^】]+】)\s*""").find(work)
        // S/E carried by a leading tag like "[S01 E01] Seven Deadly Sins":
        // the title comes AFTER the tag, not before it (moon's case 2026-11-02 —
        // before() was returning "[" → empty title → unmatched).
        var leadingSeason: Int? = null
        var leadingEpisode: Int? = null
        if (leading != null) {
            val tag = leading.groupValues[1]
            sxxexxRe.find(tag)?.let { m ->
                leadingSeason = m.groupValues[1].toIntOrNull()
                leadingEpisode = m.groupValues[2].toIntOrNull()
            } ?: nxnRe.find(tag)?.let { m ->
                leadingSeason = m.groupValues[1].toIntOrNull()
                leadingEpisode = m.groupValues[2].toIntOrNull()
            }
            if (leadingSeason != null) {
                // Strip the tag; title is parsed from what follows.
                work = work.substring(leading.range.last + 1)
            } else {
                val rest = work.substring(leading.range.last + 1)
                if (Regex("[a-zA-Z]{2,}").containsMatchIn(rest)) work = rest
            }
        }
        // Trailing lone bracket group.
        work = Regex("""\s*[\(\[][A-Za-z0-9_\-\.\s]{1,30}[\)\]]\s*$""").replace(work, "")

        var season: Int? = leadingSeason
        var episode: Int? = leadingEpisode
        var episodeEnd: Int? = null
        var title: String = work

        fun before(match: MatchResult): String =
            work.substring(0, match.range.first).trim(' ', '.', '_', '-', '[', ']', '(', ')', '\t')

        // If S/E came from a leading tag, the title is the remainder after
        // stripping episode markers — don't re-search the body for S/E.
        if (season == null) {
            sxxexxRe.find(work)?.let { m ->
                season = m.groupValues[1].toIntOrNull()
                episode = m.groupValues[2].toIntOrNull()
                title = before(m)
            } ?: nxnRe.find(work)?.let { m ->
                season = m.groupValues[1].toIntOrNull()
                episode = m.groupValues[2].toIntOrNull()
                title = before(m)
            } ?: bracketEpRe.find(work)?.let { m ->
                season = 1
                episode = m.groupValues[1].toIntOrNull()
                title = m.groupValues[2].trim()
            } ?: hyphenEpRe.find(work)?.let { m ->
                // Only treat as episode when the tail after the number has no letters
                // (avoids eating "Movie - The Beginning").
                val tail = work.substring(m.range.last + 1)
                if (!Regex("[a-zA-Z]{2,}").containsMatchIn(tail)) {
                    season = 1
                    episode = m.groupValues[2].toIntOrNull()
                    title = m.groupValues[1].trim()
                }
            } ?: multiEpRe.find(work)?.let { m ->
                // "Show E7-8" -> title="Show", episode=7, episodeEnd=8.
                // "Show Ep 1-10" -> episode=1, episodeEnd=10.
                season = 1
                episode = m.groupValues[1].toIntOrNull()
                episodeEnd = m.groupValues[2].toIntOrNull()
                title = before(m)
            } ?: standaloneEpRe.find(work)?.let { m ->
                // "Psycho Pass E22" -> title="Psycho Pass", episode=22.
                // Don't match if it's part of a word (e.g. "TRUE" ends with E).
                season = 1
                episode = m.groupValues[1].toIntOrNull()
                title = before(m)
            }
        } else {
            // S/E from leading tag: strip any leftover episode markers from
            // the title (e.g. trailing "[1080p]" already gone; guard anyway).
            title = Regex("""(?i)\s+s\d{1,2}\s*(?:e|ep)?\s*\d{0,4}\s*""").replace(work, " ").trim()
        }

        // Strip trailing junk: "COMBINED", "TRUE", lone numbers after episode info.
        // e.g. "My Brilliant Career S01 COMBINED" -> "My Brilliant Career"
        // e.g. "Reacher S04 EP 01 03 TRUE" -> "Reacher" (episode already extracted)
        title = Regex("""(?i)\s+(?:combined|true)\s*$""").replace(title, "")
        title = Regex("""\s+\d{1,3}\s*$""").replace(title, "").trim()
        // If title still has S01E01-style markers that weren't caught, strip them.
        title = Regex("""(?i)\s+s\d{1,2}\s*(?:e|ep)?\s*\d{0,4}\s*$""").replace(title, "").trim()

        if (season == null && episode == null) {
            val sOnly = seasonOnlyRe.find(work)
            val eOnly = episodeOnlyRe.find(work)
            if (sOnly != null && eOnly != null) {
                season = sOnly.groupValues[1].toIntOrNull()
                episode = eOnly.groupValues[1].toIntOrNull()
                val first = minOf(sOnly.range.first, eOnly.range.first)
                title = work.substring(0, first).trim(' ', '.', '_', '-', '[', ']', '(', ')', '\t')
            } else if (isAnime) {
                // Anime absolute numbering (moon's 2026-11-06 setting).
                if (absoluteAnime) {
                    // Strengthened probe: the bare number is often NOT last
                    // ("One Piece 1012 1080p x264", "One Piece 1012 450MB",
                    // "One Piece 1012 [Tam + Eng]") — strip trailing bracket
                    // groups, sizes and everything from the first quality
                    // word, then take the trailing number. Never the year.
                    var probe = work
                    var prev: String
                    do {
                        prev = probe
                        probe = trailingBracketRe.replace(probe, "")
                    } while (probe != prev)
                    probe = sizeTokenRe.replace(probe, "")
                    probe = qualityCutRe.replace(probe, "")
                    probe = probe.replace(Regex("""[\s._\-]+$"""), "").trim()
                    trailingNumRe.find(probe)?.let { m ->
                        val ep = m.groupValues[2].toIntOrNull()
                        if (ep != null && ep > 0 && ep.toString() != year) {
                            season = 1
                            episode = ep
                            title = m.groupValues[1].trim()
                        }
                    }
                } else {
                    // Seasonal (default): trailing bare number only.
                    trailingNumRe.find(work)?.let { m ->
                        season = 1
                        episode = m.groupValues[2].toIntOrNull()
                        title = m.groupValues[1].trim()
                    }
                }
            }
        }

        // Strip year, quality words and leftover delimiters from the title.
        year?.let { y -> title = title.replace(y, "") }
        title = qualityCutRe.replace(title, "")
        title = title.replace(Regex("""[._()\[\]\-]+"""), " ").trim()
        title = title.replace(Regex("""^[:\s._\-~·•]+"""), "")
        title = title.replace(Regex("""[:\s._\-~·•]+$"""), "")
        title = title.replace(Regex("""\s+"""), " ").trim()
        // Drop dangling language words, but never reduce the title to nothing.
        trailingLangRe.replace(title, "").trim().takeIf { it.length >= 2 }?.let { title = it }

        val type = if (season != null || episode != null) "series" else "movie"
        val partSuffix = if (splitPart != null) " • Part $splitPart" else ""
        val badge = (if (languages.isEmpty()) quality else "$quality • ${languages.joinToString("/")}") + partSuffix

        return ParsedMedia(
            title = title,
            type = type,
            year = year,
            season = season,
            episode = episode,
            episodeEnd = episodeEnd,
            quality = quality,
            qualityBadge = badge,
            languages = languages,
            splitPart = splitPart,
            imdbId = imdbId,
        )
    }
}