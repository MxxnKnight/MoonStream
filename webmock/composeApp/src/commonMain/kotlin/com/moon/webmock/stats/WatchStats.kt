package com.moon.webmock.stats

// Watch-stats data model, copied from the app's ui.stats.WatchStats (the
// DB loader is not ported — the mockup uses demo data from DemoStats.kt).
// One divergence: FinishedItem carries a precomputed display label instead
// of a timestamp, because java.util.Date/SimpleDateFormat don't exist on
// web (Kotlin/Wasm has no java.util date API).

enum class StatsRange(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    ALL("All time"),
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
    val finishedLabel: String,
)

data class GenreCount(val name: String, val hoursMs: Long)

data class LangCount(val code: String, val name: String, val hoursMs: Long)

data class CountryCount(val code: String, val name: String, val hoursMs: Long)

data class DecadeCount(val label: String, val hoursMs: Long)

data class QualityCount(val label: String, val hoursMs: Long)

data class TypeCount(val type: String, val count: Int)

data class WatchStats(
    val range: StatsRange,
    val hoursWatchedMs: Long,
    val titlesFinished: Int,
    val episodesWatched: Int,
    val moviesWatched: Int,
    val chartBars: List<Pair<String, Float>>,
    val mostWatched: List<MostWatchedItem>,
    val byType: List<TypeCount>,
    val topGenres: List<GenreCount>,
    val genreCoverage: Pair<Int, Int>,
    val topLanguages: List<LangCount>,
    val languageCoverage: Pair<Int, Int>,
    val topCountries: List<CountryCount>,
    val countryCoverage: Pair<Int, Int>,
    val topDecades: List<DecadeCount>,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val recentlyFinished: List<FinishedItem>,
    val prevHoursMs: Long = 0L,
    val todayMs: Long = 0L,
    val bestDayLabel: String = "",
    val bestDayMs: Long = 0L,
    val activeDays: Int = 0,
    val longestSessionMs: Long = 0L,
    val byQuality: List<QualityCount> = emptyList(),
    val libraryTitles: Int = 0,
    val libraryFiles: Int = 0,
    val libraryBytes: Long = 0L,
)
