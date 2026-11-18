package com.moon.moonstream.ui.stats

import com.moon.moonstream.index.db.MsDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Watch Stats (moon's pick 2026-11-10): real numbers from the library.
 *
 * Sources (all already in the DB — no new tracking):
 * - watch_progress: playedAt (last-played time, updated on every position
 *   save), positionMs, durationMs, mediaId, title, posterUrl.
 * - media_items: type, genres (v15, comma-separated; null for titles
 *   indexed before v15), originalLanguage + country (v16; null for
 *   titles indexed before v16), year (for decades).
 *
 * "Hours watched" is a proxy: the sum of positionMs across the rows played
 * in the range. It counts time spent *into* titles, so a rewatch or a
 * rewind is not double-counted — close enough for a stats screen, and
 * honest about what the DB actually holds.
 */
enum class StatsRange(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    ALL("All time");

    /** Cutoff in ms; null = no cutoff (all time). */
    fun sinceMs(now: Long): Long? = when (this) {
        WEEK -> now - TimeUnit.DAYS.toMillis(7)
        MONTH -> now - TimeUnit.DAYS.toMillis(30)
        ALL -> null
    }
}

data class MostWatchedItem(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val hoursMs: Long,
    val episodes: Int,
    /** Raw media type ("movie"/"series"/"anime") — the card shows "N ep"
     *  only for series/anime; a movie shows just the time. Null when the
     *  media row is gone (orphan watch row). */
    val type: String? = null,
)

data class FinishedItem(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val finishedAtMs: Long,
)

data class GenreCount(val name: String, val hoursMs: Long)

data class LangCount(val code: String, val name: String, val hoursMs: Long)

data class CountryCount(val code: String, val name: String, val hoursMs: Long)

data class DecadeCount(val label: String, val hoursMs: Long)

/** Hours per video quality ("1080p", "720p", …) — the files actually played. */
data class QualityCount(val label: String, val hoursMs: Long)

data class TypeCount(val type: String, val count: Int)

/** One day of the streak calendar: midnight timestamp + ms watched. */
data class DayHours(val dayStartMs: Long, val hoursMs: Long)

data class WatchStats(
    val range: StatsRange,
    val hoursWatchedMs: Long,
    val titlesFinished: Int,
    val episodesWatched: Int,
    val moviesWatched: Int,
    /** Chart bars: label + hours. Week = last 7 days, Month = by weekday,
     *  All = last 7 months. */
    val chartBars: List<Pair<String, Float>>,
    val mostWatched: List<MostWatchedItem>,
    val byType: List<TypeCount>,
    val topGenres: List<GenreCount>,
    /** Titles in the library that have a genre recorded (for the caption). */
    val genreCoverage: Pair<Int, Int>,
    val topLanguages: List<LangCount>,
    /** Titles in the library that have a language recorded. */
    val languageCoverage: Pair<Int, Int>,
    val topCountries: List<CountryCount>,
    /** Titles in the library that have a country recorded. */
    val countryCoverage: Pair<Int, Int>,
    val topDecades: List<DecadeCount>,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val recentlyFinished: List<FinishedItem>,

    // ---- 2026-11-11: hero, habits and library numbers ----
    /** Same-length period just before this one (0 = nothing / all time). */
    val prevHoursMs: Long = 0L,
    /** Time watched today, whatever the range. */
    val todayMs: Long = 0L,
    /** Busiest single day in the range: "Sat" + its hours. */
    val bestDayLabel: String = "",
    val bestDayMs: Long = 0L,
    /** Distinct days with a play in the range. */
    val activeDays: Int = 0,
    /** Longest single file watched in the range (position reached). */
    val longestSessionMs: Long = 0L,
    /** Hours per quality, from stream_files.quality of the played files. */
    val byQuality: List<QualityCount> = emptyList(),
    /** Library snapshot: titles, Telegram files, total size. */
    val libraryTitles: Int = 0,
    val libraryFiles: Int = 0,
    val libraryBytes: Long = 0L,

    // ---- 2026-11-13: coverage, streamed data, channels, hours, completion ----
    /** Distinct titles with any watch row, all time (library coverage). */
    val watchedTitles: Int = 0,
    /** Bytes actually streamed in range (file size × fraction watched). */
    val streamedBytes: Long = 0L,
    /** Hours per 4-hour bucket of the day: 12am, 4am, 8am, 12pm, 4pm, 8pm. */
    val hourBars: List<Pair<String, Float>> = emptyList(),
    /** Finished / started titles in range, as a percent (0-100). */
    val completionRate: Int = 0,
    /** Streak calendar: 22 full weeks (Sun-Sat) ending this week — 154
     *  days, range-independent like GitHub's graph. Days after today
     *  read 0; the card blanks them. */
    val calendarDays: List<DayHours> = emptyList(),
)

/** ISO language code → display name (only the ones in the library's orbit). */
private val LANGUAGE_NAMES = mapOf(
    "ml" to "Malayalam", "en" to "English", "hi" to "Hindi",
    "ta" to "Tamil", "te" to "Telugu", "kn" to "Kannada",
    "bn" to "Bengali", "mr" to "Marathi", "ko" to "Korean",
    "ja" to "Japanese", "zh" to "Chinese", "th" to "Thai",
    "es" to "Spanish", "fr" to "French", "de" to "German",
    "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian",
    "tr" to "Turkish", "ar" to "Arabic", "fa" to "Persian",
    "id" to "Indonesian", "ms" to "Malay", "vi" to "Vietnamese",
    "tl" to "Filipino",
)

/** ISO country code → display name. */
private val COUNTRY_NAMES = mapOf(
    "IN" to "India", "US" to "United States", "KR" to "South Korea",
    "JP" to "Japan", "CN" to "China", "GB" to "United Kingdom",
    "TH" to "Thailand", "ES" to "Spain", "FR" to "France",
    "DE" to "Germany", "IT" to "Italy", "BR" to "Brazil",
    "RU" to "Russia", "TR" to "Turkey", "EG" to "Egypt",
    "IR" to "Iran", "ID" to "Indonesia", "MY" to "Malaysia",
    "VN" to "Vietnam", "PH" to "Philippines",
)

/** The stats type a title counts as lives in SQL now (the CASE in
 *  WatchProgressDao.countByTypeSince): movies and anime as-is, a series
 *  in a drama language (Korean/Chinese/Japanese/Thai) counts as a drama,
 *  everything else stays a series. */

object WatchStatsLoader {
    // SQL aggregates (moon's 2026-11-15): every number below comes from
    // the database — no table is ever loaded whole. The only lists that
    // reach Kotlin are one small row per day / weekday / month / hour /
    // quality / watched title, plus the top-5 rows. The shaping (day
    // labels, streaks, genre splitting) is unchanged from the old
    // in-Kotlin version, so the numbers on screen stay the same.
    suspend fun load(db: MsDatabase, range: StatsRange): WatchStats {
        val now = System.currentTimeMillis()
        val since = range.sinceMs(now)
        val wp = db.watchProgressDao()
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val today = dayStart(now)

        // Core numbers — one small value each, straight from SQLite.
        val hoursMs = wp.sumPositionSince(since)
        val episodesWatched = wp.countEpisodesSince(since)
        val moviesWatched = wp.countMoviesWatchedSince(since)
        val titlesFinished = wp.countFinishedSince(since)
        val startedTitles = wp.countDistinctMediaSince(since)
        val watchedTitles = wp.countDistinctMediaSince(null)
        val longestSessionMs = wp.maxPositionSince(since)

        // Per-day hours in range, keyed by local midnight — drives the
        // chart, the best day, and the active-days count.
        val perDay = wp.sumByDaySince(since).associate { parseDay(it.day) to it.ms }
        val activeDays = perDay.count { it.value > 0 }
        val bestDay = perDay.maxByOrNull { it.value }
        val bestDayLabel = bestDay
            ?.let { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it.key)) }
            ?: ""
        val bestDayMs = bestDay?.value ?: 0L

        // Chart bars.
        val chartBars = chartBars(perDay, wp, since, range, now)

        // Hero numbers.
        val todayMs = wp.sumPositionSince(today)
        // Same-length window before the selected one, for the hero's delta.
        val prevHoursMs = when (range) {
            StatsRange.WEEK -> wp.sumPositionBetween(now - 14 * dayMs, now - 7 * dayMs)
            StatsRange.MONTH -> wp.sumPositionBetween(now - 60 * dayMs, now - 30 * dayMs)
            StatsRange.ALL -> 0L
        }

        // Played files → their quality, so "By quality" is about watching,
        // not about what the library happens to hold.
        val byQuality = wp.sumByQualitySince(since)
            // Collapse "1080p • Mal" / "1080p • Tam" into plain "1080p" —
            // the badge's language suffix split one resolution into many
            // rows (moon's 2026-11-13).
            .groupBy {
                it.quality.substringBefore("•").trim().takeIf { q -> q.isNotBlank() } ?: "Unknown"
            }
            .map { (label, rows) -> QualityCount(label, rows.sumOf { it.ms }) }
            .sortedByDescending { it.hoursMs }
            .take(5)

        // Data streamed in range: each played file contributes its size ×
        // the fraction watched (a finished file counts whole — its saved
        // position is 0, so finishedAt is the signal).
        val streamedBytes = wp.streamedBytesSince(since)

        // When you watch: hours per 4-hour bucket of the day.
        val hourLabels = listOf("12am", "4am", "8am", "12pm", "4pm", "8pm")
        val hourSums = LongArray(6)
        for (r in wp.sumByHourSince(since)) {
            hourSums[r.hour / 4] += r.ms
        }
        val hourBars = hourLabels.mapIndexed { i, label ->
            label to hourSums[i] / 3_600_000f
        }

        // Completion rate: finished / started titles in range, as a percent.
        val completionRate = if (startedTitles > 0) titlesFinished * 100 / startedTitles else 0

        // Most watched: top 5 titles by summed positionMs in range.
        val mostWatched = wp.topWatchedSince(since).map {
            MostWatchedItem(
                mediaId = it.mediaId,
                title = it.title,
                posterUrl = it.posterUrl,
                hoursMs = it.hoursMs,
                episodes = it.episodes,
                type = it.type,
            )
        }

        // By type: distinct titles played in range, grouped by display
        // type — movies, series, anime, and dramas (Asian-language series)
        // each get their own slice (moon's 2026-11-11).
        val byType = wp.countByTypeSince(since).map { TypeCount(it.type, it.count) }

        // One row per watched title in range: its metadata + hours.
        // The genre / language / country / decade breakdowns share it.
        val watchedMeta = wp.watchedTitleMetaSince(since)

        // Top genres: hours per genre, from titles that have genres.
        val genreHours = mutableMapOf<String, Long>()
        for (m in watchedMeta) {
            val gs = m.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            if (gs.isNullOrEmpty()) continue
            for (g in gs) genreHours[g] = (genreHours[g] ?: 0L) + m.hoursMs
        }
        val topGenres = genreHours.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { GenreCount(it.key, it.value) }

        // Top languages: hours per original language (v16).
        val langHours = mutableMapOf<String, Long>()
        for (m in watchedMeta) {
            val lang = m.originalLanguage?.takeIf { it.isNotBlank() } ?: continue
            langHours[lang] = (langHours[lang] ?: 0L) + m.hoursMs
        }
        val topLanguages = langHours.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { LangCount(it.key, LANGUAGE_NAMES[it.key] ?: it.key, it.value) }

        // Top countries: hours per country (v16).
        val countryHours = mutableMapOf<String, Long>()
        for (m in watchedMeta) {
            val c = m.country?.takeIf { it.isNotBlank() } ?: continue
            countryHours[c] = (countryHours[c] ?: 0L) + m.hoursMs
        }
        val topCountries = countryHours.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { CountryCount(it.key, COUNTRY_NAMES[it.key] ?: it.key, it.value) }

        // Top decades: hours per release decade, from the title year.
        val decadeHours = mutableMapOf<String, Long>()
        for (m in watchedMeta) {
            val y = m.year?.take(4)?.toIntOrNull() ?: continue
            val label = "${(y / 10) * 10}s"
            decadeHours[label] = (decadeHours[label] ?: 0L) + m.hoursMs
        }
        val topDecades = decadeHours.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { DecadeCount(it.key, it.value) }

        // Library coverage counts — plain COUNTs, no rows loaded.
        val mediaDao = db.mediaDao()
        val totalMedia = mediaDao.countAll()
        val withGenres = mediaDao.countWithGenres()
        val withLang = mediaDao.countWithLanguage()
        val withCountry = mediaDao.countWithCountry()
        val libraryFiles = db.streamDao().countAll()
        val libraryBytes = db.streamDao().totalSize()

        // Streaks from distinct play days (all time, not just the range).
        val days = wp.distinctDays().map { parseDay(it) }.sorted()
        val (current, longest) = streaks(days, dayStart(now))

        // Recently finished: finished titles ordered by latest finish.
        val recentlyFinished = wp.recentlyFinished().map {
            FinishedItem(
                mediaId = it.mediaId,
                title = it.title,
                posterUrl = it.posterUrl,
                finishedAtMs = it.finishedAtMs,
            )
        }

        // Streak calendar: 22 full weeks (Sun-Sat) ending this week, from
        // ALL rows (range-independent, like GitHub's graph). The grid
        // starts on the Sunday 21 weeks ago; days after today read 0.
        val gridStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, -21)
        }.timeInMillis
        val allPerDay = wp.sumByDaySince(null).associate { parseDay(it.day) to it.ms }
        val calendarDays = (0 until 154).map { i ->
            val d = gridStart + i * dayMs
            DayHours(d, allPerDay[d] ?: 0L)
        }

        return WatchStats(
            range = range,
            hoursWatchedMs = hoursMs,
            titlesFinished = titlesFinished,
            episodesWatched = episodesWatched,
            moviesWatched = moviesWatched,
            chartBars = chartBars,
            mostWatched = mostWatched,
            byType = byType,
            topGenres = topGenres,
            genreCoverage = withGenres to totalMedia,
            topLanguages = topLanguages,
            languageCoverage = withLang to totalMedia,
            topCountries = topCountries,
            countryCoverage = withCountry to totalMedia,
            topDecades = topDecades,
            currentStreakDays = current,
            longestStreakDays = longest,
            recentlyFinished = recentlyFinished,
            prevHoursMs = prevHoursMs,
            todayMs = todayMs,
            bestDayLabel = bestDayLabel,
            bestDayMs = bestDayMs,
            activeDays = activeDays,
            longestSessionMs = longestSessionMs,
            byQuality = byQuality,
            libraryTitles = totalMedia,
            libraryFiles = libraryFiles,
            libraryBytes = libraryBytes,
            watchedTitles = watchedTitles,
            streamedBytes = streamedBytes,
            hourBars = hourBars,
            completionRate = completionRate,
            calendarDays = calendarDays,
        )
    }

    /** "yyyy-MM-dd" (SQLite local date) → that day's midnight, local
     *  time. Parsed by hand (no SimpleDateFormat: this runs for every
     *  play day and the formatter isn't thread-safe). */
    private fun parseDay(day: String): Long {
        val parts = day.split("-")
        if (parts.size != 3) return 0L
        val y = parts[0].toIntOrNull() ?: return 0L
        val m = parts[1].toIntOrNull() ?: return 0L
        val d = parts[2].toIntOrNull() ?: return 0L
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Start of the day (midnight) for a timestamp, local time. */
    private fun dayStart(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Current + longest streak (consecutive days with a play). */
    private fun streaks(days: List<Long>, today: Long): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val set = days.toSet()
        val oneDay = TimeUnit.DAYS.toMillis(1)
        // Longest run anywhere.
        var longest = 1
        var run = 1
        for (i in 1 until days.size) {
            if (days[i] - days[i - 1] == oneDay) {
                run++
                longest = maxOf(longest, run)
            } else {
                run = 1
            }
        }
        // Current run ending today (or yesterday, if today has no play yet).
        var current = 0
        var d = today
        if (!set.contains(d)) d -= oneDay
        while (set.contains(d)) {
            current++
            d -= oneDay
        }
        return current to longest
    }

    /** Chart bars for the range. Week = last 7 days, Month = by weekday
     *  (Mon..Sun), All = last 7 months. The numbers come from the SQL
     *  aggregates; only the labels are built here. */
    private suspend fun chartBars(
        perDay: Map<Long, Long>,
        wp: com.moon.moonstream.index.db.WatchProgressDao,
        since: Long?,
        range: StatsRange,
        now: Long,
    ): List<Pair<String, Float>> {
        val cal = Calendar.getInstance()
        return when (range) {
            StatsRange.WEEK -> {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                (6 downTo 0).map { back ->
                    cal.timeInMillis = now - TimeUnit.DAYS.toMillis(back.toLong())
                    val day = dayStart(cal.timeInMillis)
                    val label = dayNames[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]
                    val hours = (perDay[day] ?: 0L) / 3_600_000f
                    label to hours
                }
            }
            StatsRange.MONTH -> {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                // SQLite %w: Sunday = 0 .. Saturday = 6; the labels run
                // Monday = 0 .. Sunday = 6.
                val byWeekday = wp.sumByWeekdaySince(since).associate {
                    (it.weekday + 6) % 7 to it.ms
                }
                dayNames.mapIndexed { idx, name ->
                    name to (byWeekday[idx] ?: 0L) / 3_600_000f
                }
            }
            StatsRange.ALL -> {
                val monthNames = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
                )
                val byMonth = wp.sumByMonthSince(since).associate { it.month to it.ms }
                (6 downTo 0).map { back ->
                    cal.timeInMillis = now
                    cal.add(Calendar.MONTH, -back)
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH)
                    val label = monthNames[m]
                    val key = "%04d-%02d".format(y, m + 1)
                    val hours = (byMonth[key] ?: 0L) / 3_600_000f
                    label to hours
                }
            }
        }
    }
}
