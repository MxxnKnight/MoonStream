package com.moon.moonstream.ui.stats

import com.moon.moonstream.index.db.MsDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Watch Stats (moon's pick 2026-11-10): real numbers from the library.
 *
 * Sources (all already in the DB — no new tracking):
 * - watch_progress: playedAt (last-played time, updated on every position
 *   save), positionMs, durationMs, mediaId, title, posterUrl.
 * - media_items: type, genres (v15, comma-separated; null for titles
 *   indexed before v15).
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
)

data class FinishedItem(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val finishedAtMs: Long,
)

data class GenreCount(val name: String, val hoursMs: Long)

data class TypeCount(val type: String, val count: Int)

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
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val recentlyFinished: List<FinishedItem>,
)

object WatchStatsLoader {
    private const val FINISHED_FRAC = 0.9

    suspend fun load(db: MsDatabase, range: StatsRange): WatchStats {
        val now = System.currentTimeMillis()
        val since = range.sinceMs(now)
        val rows = db.watchProgressDao().getAll()
        val inRange = if (since == null) rows else rows.filter { it.playedAt >= since }
        val media = db.mediaDao().getAll()
        val mediaById = media.associateBy { it.id }

        // Hours watched (proxy): sum of positionMs for rows played in range.
        val hoursMs = inRange.sumOf { it.positionMs }
        // Episodes/files played (position past 10s) in range.
        val episodesWatched = inRange.count { it.positionMs > 10_000L }
        // Movies watched in range (distinct movie mediaIds).
        val moviesWatched = inRange
            .filter { mediaById[it.mediaId]?.type == "movie" }
            .map { it.mediaId }.distinct().size
        // Finished: distinct mediaIds whose best position is >= 90% of duration.
        val finishedIds = rows
            .groupBy { it.mediaId }
            .filter { (_, rs) ->
                rs.any { r ->
                    r.durationMs > 0L && r.positionMs >= (r.durationMs * FINISHED_FRAC).toLong()
                }
            }
            .keys
        // "Titles finished" counts finishes that happened in range; for ALL
        // it is every finished title.
        val finishedInRange = if (since == null) finishedIds else finishedIds.filter { id ->
            rows.any { it.mediaId == id && it.playedAt >= since }
        }
        val titlesFinished = finishedInRange.size

        // Chart bars.
        val chartBars = chartBars(inRange, range, now)

        // Most watched: top 5 mediaIds by summed positionMs in range.
        val mostWatched = inRange
            .groupBy { it.mediaId }
            .map { (id, rs) ->
                val first = rs.first()
                MostWatchedItem(
                    mediaId = id,
                    title = mediaById[id]?.title ?: first.title,
                    posterUrl = mediaById[id]?.posterUrl ?: first.posterUrl,
                    hoursMs = rs.sumOf { it.positionMs },
                    episodes = rs.size,
                )
            }
            .sortedByDescending { it.hoursMs }
            .take(5)

        // By type: distinct mediaIds played in range, grouped by type.
        val byType = inRange.map { it.mediaId }.distinct()
            .mapNotNull { mediaById[it]?.type }
            .groupingBy { it }.eachCount()
            .map { TypeCount(it.key, it.value) }
            .sortedByDescending { it.count }

        // Top genres: hours per genre, from titles that have genres.
        val genreHours = mutableMapOf<String, Long>()
        var withGenres = 0
        var totalMedia = 0
        for (m in media) {
            totalMedia++
            val gs = m.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            if (gs.isNullOrEmpty()) continue
            withGenres++
            val hours = inRange.filter { it.mediaId == m.id }.sumOf { it.positionMs }
            if (hours <= 0L) continue
            for (g in gs) genreHours[g] = (genreHours[g] ?: 0L) + hours
        }
        val topGenres = genreHours.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { GenreCount(it.key, it.value) }

        // Streaks from distinct play days (all time, not just the range).
        val days = rows.map { dayStart(it.playedAt) }.distinct().sorted()
        val (current, longest) = streaks(days, dayStart(now))

        // Recently finished: finished titles ordered by latest playedAt.
        val recentlyFinished = finishedIds
            .mapNotNull { id ->
                val last = rows.filter { it.mediaId == id }.maxOfOrNull { it.playedAt }
                    ?: return@mapNotNull null
                val m = mediaById[id]
                FinishedItem(
                    mediaId = id,
                    title = m?.title ?: rows.first { it.mediaId == id }.title,
                    posterUrl = m?.posterUrl,
                    finishedAtMs = last,
                )
            }
            .sortedByDescending { it.finishedAtMs }
            .take(5)

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
            currentStreakDays = current,
            longestStreakDays = longest,
            recentlyFinished = recentlyFinished,
        )
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
     *  (Mon..Sun), All = last 7 months. */
    private fun chartBars(
        rows: List<com.moon.moonstream.index.db.WatchProgressEntity>,
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
                    val hours = rows.filter { dayStart(it.playedAt) == day }
                        .sumOf { it.positionMs } / 3_600_000f
                    label to hours
                }
            }
            StatsRange.MONTH -> {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                dayNames.map { name ->
                    val idx = dayNames.indexOf(name)
                    val hours = rows.filter {
                        cal.timeInMillis = it.playedAt
                        (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 == idx
                    }.sumOf { it.positionMs } / 3_600_000f
                    name to hours
                }
            }
            StatsRange.ALL -> {
                val monthNames = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
                )
                (6 downTo 0).map { back ->
                    cal.timeInMillis = now
                    cal.add(Calendar.MONTH, -back)
                    val y = cal.get(Calendar.YEAR)
                    val m = cal.get(Calendar.MONTH)
                    val label = monthNames[m]
                    val hours = rows.filter {
                        cal.timeInMillis = it.playedAt
                        cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == m
                    }.sumOf { it.positionMs } / 3_600_000f
                    label to hours
                }
            }
        }
    }
}
