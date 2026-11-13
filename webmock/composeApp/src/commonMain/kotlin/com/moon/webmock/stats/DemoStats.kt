package com.moon.webmock.stats

// Demo numbers for the stats mockup — plausible data shaped like moon's
// library (Malayalam + English, 1080p files, 3,135 titles), rich enough
// that every section of the screen shows something.

private const val MIN = 60_000L
private const val HOUR = 3_600_000L
private const val TB_BYTES = 1_099_511_627_776L
private const val GB_BYTES = 1_073_741_824L

fun demoStats(range: StatsRange): WatchStats = when (range) {
    StatsRange.WEEK -> WatchStats(
        range = range,
        hoursWatchedMs = 2 * HOUR + 47 * MIN,
        titlesFinished = 1,
        episodesWatched = 6,
        moviesWatched = 2,
        chartBars = listOf(
            "Fri" to 0.4f, "Sat" to 1.2f, "Sun" to 0.8f, "Mon" to 0f,
            "Tue" to 0.3f, "Wed" to 0f, "Thu" to 0.1f,
        ),
        mostWatched = listOf(
            MostWatchedItem(1, "Wednesday", null, 1 * HOUR + 12 * MIN, 3, "series"),
            MostWatchedItem(2, "American Gangster", null, 58 * MIN, 1, "movie"),
            MostWatchedItem(3, "Reacher", null, 37 * MIN, 2, "series"),
        ),
        byType = listOf(
            TypeCount("series", 5), TypeCount("movies", 2),
            TypeCount("anime", 1), TypeCount("dramas", 1),
        ),
        topGenres = listOf(
            GenreCount("Drama", 1 * HOUR + 30 * MIN),
            GenreCount("Action", 58 * MIN),
            GenreCount("Comedy", 19 * MIN),
        ),
        genreCoverage = 2400 to 3135,
        topLanguages = listOf(
            LangCount("en", "English", 2 * HOUR + 10 * MIN),
            LangCount("ml", "Malayalam", 37 * MIN),
        ),
        languageCoverage = 2100 to 3135,
        topCountries = listOf(
            CountryCount("US", "United States", 2 * HOUR + 10 * MIN),
            CountryCount("IN", "India", 37 * MIN),
        ),
        countryCoverage = 2100 to 3135,
        topDecades = listOf(
            DecadeCount("2020s", 2 * HOUR + 20 * MIN),
            DecadeCount("2010s", 27 * MIN),
        ),
        currentStreakDays = 3,
        longestStreakDays = 9,
        recentlyFinished = listOf(
            FinishedItem(2, "American Gangster", null, "10 Nov 2026"),
        ),
        prevHoursMs = 1 * HOUR + 52 * MIN,
        todayMs = 24 * MIN,
        bestDayLabel = "Sat",
        bestDayMs = 1 * HOUR + 12 * MIN,
        activeDays = 5,
        longestSessionMs = 58 * MIN,
        byQuality = listOf(
            QualityCount("1080p", 2 * HOUR + 5 * MIN),
            QualityCount("720p", 42 * MIN),
        ),
        libraryTitles = 3135,
        libraryFiles = 19312,
        libraryBytes = (18.3 * TB_BYTES).toLong(),
        watchedTitles = 47,
        streamedBytes = (2.9 * GB_BYTES).toLong(),
        hourBars = listOf(
            "12am" to 0.1f, "4am" to 0f, "8am" to 0.1f,
            "12pm" to 0.3f, "4pm" to 0.4f, "8pm" to 1.9f,
        ),
        completionRate = 20,
    )
    StatsRange.MONTH -> WatchStats(
        range = range,
        hoursWatchedMs = 14 * HOUR + 20 * MIN,
        titlesFinished = 4,
        episodesWatched = 31,
        moviesWatched = 6,
        chartBars = listOf(
            "Mon" to 1.1f, "Tue" to 2.4f, "Wed" to 0.8f, "Thu" to 1.9f,
            "Fri" to 2.2f, "Sat" to 3.6f, "Sun" to 2.2f,
        ),
        mostWatched = listOf(
            MostWatchedItem(1, "Wednesday", null, 4 * HOUR + 5 * MIN, 8, "series"),
            MostWatchedItem(3, "Reacher", null, 3 * HOUR + 40 * MIN, 6, "series"),
            MostWatchedItem(4, "One Piece", null, 2 * HOUR + 15 * MIN, 12, "anime"),
            MostWatchedItem(2, "American Gangster", null, 1 * HOUR + 58 * MIN, 1, "movie"),
            MostWatchedItem(5, "Bigg Boss Malayalam", null, 1 * HOUR + 22 * MIN, 4, "series"),
        ),
        byType = listOf(
            TypeCount("series", 18), TypeCount("movies", 6),
            TypeCount("anime", 4), TypeCount("dramas", 3),
        ),
        topGenres = listOf(
            GenreCount("Drama", 6 * HOUR + 10 * MIN),
            GenreCount("Action", 4 * HOUR + 45 * MIN),
            GenreCount("Thriller", 2 * HOUR + 5 * MIN),
            GenreCount("Comedy", 1 * HOUR + 20 * MIN),
        ),
        genreCoverage = 2400 to 3135,
        topLanguages = listOf(
            LangCount("en", "English", 9 * HOUR + 30 * MIN),
            LangCount("ml", "Malayalam", 3 * HOUR + 10 * MIN),
            LangCount("ja", "Japanese", 1 * HOUR + 40 * MIN),
        ),
        languageCoverage = 2100 to 3135,
        topCountries = listOf(
            CountryCount("US", "United States", 9 * HOUR + 30 * MIN),
            CountryCount("IN", "India", 3 * HOUR + 10 * MIN),
            CountryCount("JP", "Japan", 1 * HOUR + 40 * MIN),
        ),
        countryCoverage = 2100 to 3135,
        topDecades = listOf(
            DecadeCount("2020s", 11 * HOUR + 5 * MIN),
            DecadeCount("2010s", 2 * HOUR + 35 * MIN),
            DecadeCount("2000s", 40 * MIN),
        ),
        currentStreakDays = 3,
        longestStreakDays = 12,
        recentlyFinished = listOf(
            FinishedItem(2, "American Gangster", null, "10 Nov 2026"),
            FinishedItem(6, "The Batman", null, "4 Nov 2026"),
            FinishedItem(7, "Alice in Borderland", null, "28 Oct 2026"),
        ),
        prevHoursMs = 9 * HOUR + 45 * MIN,
        todayMs = 24 * MIN,
        bestDayLabel = "Sat",
        bestDayMs = 3 * HOUR + 36 * MIN,
        activeDays = 21,
        longestSessionMs = 2 * HOUR + 5 * MIN,
        byQuality = listOf(
            QualityCount("1080p", 11 * HOUR + 20 * MIN),
            QualityCount("720p", 2 * HOUR + 10 * MIN),
            QualityCount("2160p", 50 * MIN),
        ),
        libraryTitles = 3135,
        libraryFiles = 19312,
        libraryBytes = (18.3 * TB_BYTES).toLong(),
        watchedTitles = 47,
        streamedBytes = (14.6 * GB_BYTES).toLong(),
        hourBars = listOf(
            "12am" to 0.8f, "4am" to 0.1f, "8am" to 0.6f,
            "12pm" to 1.9f, "4pm" to 2.8f, "8pm" to 8.0f,
        ),
        completionRate = 13,
    )
    StatsRange.ALL -> WatchStats(
        range = range,
        hoursWatchedMs = 86 * HOUR + 15 * MIN,
        titlesFinished = 23,
        episodesWatched = 148,
        moviesWatched = 34,
        chartBars = listOf(
            "May" to 6.5f, "Jun" to 9.2f, "Jul" to 12.4f, "Aug" to 8.1f,
            "Sep" to 14.6f, "Oct" to 18.3f, "Nov" to 17.1f,
        ),
        mostWatched = listOf(
            MostWatchedItem(4, "One Piece", null, 18 * HOUR + 30 * MIN, 96, "anime"),
            MostWatchedItem(3, "Reacher", null, 12 * HOUR + 10 * MIN, 24, "series"),
            MostWatchedItem(1, "Wednesday", null, 8 * HOUR + 45 * MIN, 16, "series"),
            MostWatchedItem(8, "Breaking Bad", null, 7 * HOUR + 20 * MIN, 14, "series"),
            MostWatchedItem(5, "Bigg Boss Malayalam", null, 6 * HOUR + 40 * MIN, 22, "series"),
        ),
        byType = listOf(
            TypeCount("series", 96), TypeCount("movies", 34),
            TypeCount("anime", 28), TypeCount("dramas", 14),
        ),
        topGenres = listOf(
            GenreCount("Drama", 32 * HOUR + 10 * MIN),
            GenreCount("Action", 24 * HOUR + 30 * MIN),
            GenreCount("Thriller", 12 * HOUR + 45 * MIN),
            GenreCount("Comedy", 9 * HOUR + 20 * MIN),
            GenreCount("Animation", 7 * HOUR + 30 * MIN),
        ),
        genreCoverage = 2400 to 3135,
        topLanguages = listOf(
            LangCount("en", "English", 54 * HOUR),
            LangCount("ml", "Malayalam", 19 * HOUR + 30 * MIN),
            LangCount("ja", "Japanese", 8 * HOUR + 45 * MIN),
            LangCount("hi", "Hindi", 4 * HOUR),
        ),
        languageCoverage = 2100 to 3135,
        topCountries = listOf(
            CountryCount("US", "United States", 54 * HOUR),
            CountryCount("IN", "India", 19 * HOUR + 30 * MIN),
            CountryCount("JP", "Japan", 8 * HOUR + 45 * MIN),
            CountryCount("KR", "South Korea", 4 * HOUR),
        ),
        countryCoverage = 2100 to 3135,
        topDecades = listOf(
            DecadeCount("2020s", 58 * HOUR + 15 * MIN),
            DecadeCount("2010s", 21 * HOUR + 40 * MIN),
            DecadeCount("2000s", 5 * HOUR + 20 * MIN),
            DecadeCount("1990s", 1 * HOUR),
        ),
        currentStreakDays = 3,
        longestStreakDays = 18,
        recentlyFinished = listOf(
            FinishedItem(2, "American Gangster", null, "10 Nov 2026"),
            FinishedItem(6, "The Batman", null, "4 Nov 2026"),
            FinishedItem(7, "Alice in Borderland", null, "28 Oct 2026"),
            FinishedItem(9, "Interstellar", null, "19 Oct 2026"),
        ),
        prevHoursMs = 0L,
        todayMs = 24 * MIN,
        bestDayLabel = "Sat",
        bestDayMs = 4 * HOUR + 12 * MIN,
        activeDays = 74,
        longestSessionMs = 3 * HOUR + 2 * MIN,
        byQuality = listOf(
            QualityCount("1080p", 64 * HOUR + 20 * MIN),
            QualityCount("720p", 15 * HOUR + 45 * MIN),
            QualityCount("2160p", 6 * HOUR + 10 * MIN),
        ),
        libraryTitles = 3135,
        libraryFiles = 19312,
        libraryBytes = (18.3 * TB_BYTES).toLong(),
        watchedTitles = 47,
        streamedBytes = (89.2 * GB_BYTES).toLong(),
        hourBars = listOf(
            "12am" to 6.5f, "4am" to 0.8f, "8am" to 4.2f,
            "12pm" to 11.0f, "4pm" to 16.5f, "8pm" to 47.2f,
        ),
        completionRate = 13,
    )
}
