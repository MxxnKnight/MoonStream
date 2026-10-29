package com.moon.moonstream.ui.model

import androidx.compose.ui.graphics.Color

/** Phase 1 fake data. TODO(Phase 3): load from Room library. */

data class FakeStream(
    val id: String,
    val quality: String,
    val fileName: String,
    val size: String,
)

/** One episode inside a season (series/anime only). */
data class FakeEpisode(
    val number: Int,
    val title: String,
    val streams: List<FakeStream>,
)

data class FakeSeason(
    val season: Int,
    val episodes: List<FakeEpisode>,
)

data class FakeTitle(
    val id: String,
    val title: String,
    val year: Int,
    val type: String, // "movie" | "series" | "anime"
    val desc: String,
    val colorA: Color,
    val colorB: Color,
    val streams: List<FakeStream> = emptyList(), // movies: flat list
    val seasons: List<FakeSeason> = emptyList(), // series/anime: grouped
    val hidden: Boolean = false,
) {
    /** Total episodes across seasons (series/anime). */
    fun episodeCount(): Int = seasons.sumOf { it.episodes.size }

    /** All streams (flat for movies, via seasons otherwise), for size math. */
    fun allStreams(): List<FakeStream> =
        streams + seasons.flatMap { s -> s.episodes.flatMap { it.streams } }

    fun totalSizeMb(): Double = allStreams().sumOf { parseSizeMb(it.size) }
}

/** Parse "8.4 GB" / "425 MB" / "1.2 TB" into megabytes. */
fun parseSizeMb(s: String): Double {
    val t = s.trim().uppercase()
    val num = t.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    return when {
        t.contains("TB") -> num * 1024 * 1024
        t.contains("GB") -> num * 1024
        t.contains("KB") -> num / 1024
        else -> num // MB
    }
}

/** Format megabytes back into a human size string. */
fun formatSizeMb(mb: Double): String = when {
    mb >= 1024 * 1024 -> "%.1f TB".format(mb / (1024 * 1024))
    mb >= 1024 -> "%.1f GB".format(mb / 1024)
    else -> "%.0f MB".format(mb)
}

/** Metadata search result row: poster, title, year AND IMDb id for identification. */
data class FakeMetaResult(
    val title: String,
    val year: Int,
    val imdbId: String,
    val colorA: Color,
    val colorB: Color,
)

data class FakeChannel(
    val id: String,
    val name: String,
    val files: Int,
    val isAnime: Boolean,
    var enabled: Boolean,
)

data class UnmatchedFile(
    val id: String,
    val fileName: String,
    val size: String,
)

val fakeTitles = listOf(
    FakeTitle(
        "t1", "Dune: Part Two", 2024, "movie",
        "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
        Color(0xFFB3541E), Color(0xFF3B1F0E),
        listOf(
            FakeStream("s1", "1080p", "Dune.Part.Two.2024.1080p.BluRay.mkv", "8.4 GB"),
            FakeStream("s2", "720p", "Dune.Part.Two.2024.720p.WEB-DL.mkv", "4.1 GB"),
            FakeStream("s3", "4K", "Dune.Part.Two.2024.2160p.WEB-DL.mkv", "18.2 GB"),
        ),
    ),
    FakeTitle(
        "t2", "Seven Deadly Sins", 2014, "anime",
        "A group of powerful knights must clear their name and save the kingdom of Britannia.",
        Color(0xFF7B1FA2), Color(0xFF1A0B2E),
        seasons = listOf(
            FakeSeason(
                1, listOf(
                    FakeEpisode(
                        16, "The Druids' Holy War",
                        listOf(FakeStream("s4", "1080p", "[S01 E16] Seven Deadly Sins [1080p][Dual].mkv", "425 MB")),
                    ),
                    FakeEpisode(
                        17, "The Seven Deadly Sins, Assembled",
                        listOf(FakeStream("s5", "1080p", "[S01 E17] Seven Deadly Sins [1080p][Dual].mkv", "431 MB")),
                    ),
                ),
            ),
            FakeSeason(
                2, listOf(
                    FakeEpisode(
                        1, "The Dark Omen",
                        listOf(FakeStream("s5a", "1080p", "[S02 E01] Seven Deadly Sins [1080p][Dual].mkv", "430 MB")),
                    ),
                    FakeEpisode(
                        2, "The Demon Clan's Scheme",
                        listOf(FakeStream("s5b", "720p", "[S02 E02] Seven Deadly Sins [720p][Dual].mkv", "290 MB")),
                    ),
                ),
            ),
        ),
    ),
    FakeTitle(
        "t3", "Breaking Bad", 2008, "series",
        "A chemistry teacher turned meth manufacturer descends into the criminal underworld.",
        Color(0xFF1B5E20), Color(0xFF081208),
        seasons = listOf(
            FakeSeason(
                1, listOf(
                    FakeEpisode(
                        1, "Pilot",
                        listOf(
                            FakeStream("s6", "1080p", "Breaking.Bad.S01E01.1080p.BluRay.mkv", "2.8 GB"),
                            FakeStream("s7", "720p", "Breaking.Bad.S01E01.720p.HDTV.mkv", "1.2 GB"),
                        ),
                    ),
                    FakeEpisode(
                        2, "Cat's in the Bag…",
                        listOf(FakeStream("s6a", "1080p", "Breaking.Bad.S01E02.1080p.BluRay.mkv", "2.6 GB")),
                    ),
                ),
            ),
            FakeSeason(
                2, listOf(
                    FakeEpisode(
                        1, "Seven Thirty-Seven",
                        listOf(FakeStream("s6b", "1080p", "Breaking.Bad.S02E01.1080p.BluRay.mkv", "2.7 GB")),
                    ),
                    FakeEpisode(
                        2, "Grilled",
                        listOf(FakeStream("s6c", "720p", "Breaking.Bad.S02E02.720p.HDTV.mkv", "1.1 GB")),
                    ),
                ),
            ),
        ),
    ),
    FakeTitle(
        "t4", "Spider-Man: Brand New Day", 2026, "movie",
        "Peter Parker faces a brand new threat that forces him to question everything.",
        Color(0xFFB71C1C), Color(0xFF0D1B3E),
        listOf(
            FakeStream("s8", "1080p", "Spider-Man.Brand.New.Day.2026.1080p.WEB.mkv", "6.9 GB"),
        ),
    ),
    FakeTitle(
        "t5", "One Piece", 1999, "anime",
        "Monkey D. Luffy sails the Grand Line in search of the legendary One Piece.",
        Color(0xFFE65100), Color(0xFF3E1400),
        seasons = listOf(
            FakeSeason(
                1, listOf(
                    FakeEpisode(
                        1123, "A Pivotal Clash! Shanks vs. Eustass Kid",
                        listOf(FakeStream("s9", "1080p", "One.Piece.E1123.1080p.SubPlease.mkv", "1.4 GB")),
                    ),
                    FakeEpisode(
                        1124, "The Truth of the Void Century",
                        listOf(FakeStream("s9a", "1080p", "One.Piece.E1124.1080p.SubPlease.mkv", "1.4 GB")),
                    ),
                ),
            ),
            FakeSeason(
                2, listOf(
                    FakeEpisode(
                        1125, "The Final Road to Laugh Tale",
                        listOf(FakeStream("s9b", "1080p", "One.Piece.E1125.1080p.SubPlease.mkv", "1.5 GB")),
                    ),
                ),
            ),
        ),
    ),
    FakeTitle(
        "t6", "The Dark Knight", 2008, "movie",
        "Batman faces the Joker in a battle for Gotham's soul.",
        Color(0xFF37474F), Color(0xFF05080B),
        listOf(
            FakeStream("s10", "1080p", "The.Dark.Knight.2008.1080p.BluRay.mkv", "9.7 GB"),
        ),
    ),
    FakeTitle(
        "t7", "Class Recording - Physics 12", 2026, "movie",
        "Personal class video uploaded by me.",
        Color(0xFF00695C), Color(0xFF00211D),
        listOf(
            FakeStream("s11", "720p", "Physics.Class.12.2026-10-20.mp4", "890 MB"),
        ),
        hidden = true,
    ),
    FakeTitle(
        "t8", "Interstellar", 2014, "movie",
        "Explorers travel through a wormhole in search of a new home for humanity.",
        Color(0xFF0D47A1), Color(0xFF020610),
        listOf(
            FakeStream("s12", "4K", "Interstellar.2014.2160p.BluRay.mkv", "22.5 GB"),
            FakeStream("s13", "1080p", "Interstellar.2014.1080p.BluRay.mkv", "10.1 GB"),
        ),
    ),
    FakeTitle(
        "t9", "Naruto Shippuden", 2007, "anime",
        "Naruto returns to the village, stronger and ready to face new threats.",
        Color(0xFFF57F17), Color(0xFF2B1500),
        seasons = listOf(
            FakeSeason(
                1, listOf(
                    FakeEpisode(
                        16, "The Secret of Jinchuriki",
                        listOf(FakeStream("s14", "720p", "Naruto.Shippuden.E016.720p.mkv", "640 MB")),
                    ),
                    FakeEpisode(
                        17, "The Death of Gaara!",
                        listOf(FakeStream("s14a", "720p", "Naruto.Shippuden.E017.720p.mkv", "635 MB")),
                    ),
                ),
            ),
            FakeSeason(
                2, listOf(
                    FakeEpisode(
                        18, "The Medical Ninja's Student",
                        listOf(FakeStream("s14b", "720p", "Naruto.Shippuden.E018.720p.mkv", "640 MB")),
                    ),
                ),
            ),
        ),
    ),
)

/** Search results carry poster colors, title, year AND IMDb id for identification. */
val fakeSearchResults = listOf(
    FakeMetaResult("Dune: Part Two", 2024, "tt15239678", Color(0xFFB3541E), Color(0xFF3B1F0E)),
    FakeMetaResult("Dune", 2021, "tt1160419", Color(0xFF8D6E2F), Color(0xFF2A1F08)),
    FakeMetaResult("The Seven Deadly Sins", 2014, "tt3909224", Color(0xFF7B1FA2), Color(0xFF1A0B2E)),
    FakeMetaResult("Breaking Bad", 2008, "tt0903747", Color(0xFF1B5E20), Color(0xFF081208)),
    FakeMetaResult("Spider-Man: Brand New Day", 2026, "tt22084616", Color(0xFFB71C1C), Color(0xFF0D1B3E)),
    FakeMetaResult("One Piece", 1999, "tt0388629", Color(0xFFE65100), Color(0xFF3E1400)),
    FakeMetaResult("Interstellar", 2014, "tt0816692", Color(0xFF0D47A1), Color(0xFF020610)),
    FakeMetaResult("Naruto: Shippuden", 2007, "tt0988824", Color(0xFFF57F17), Color(0xFF2B1500)),
    FakeMetaResult("The Dark Knight", 2008, "tt0468569", Color(0xFF37474F), Color(0xFF05080B)),
)

val fakeChannels = listOf(
    FakeChannel("c1", "Movies Hub", 2140, false, true),
    FakeChannel("c2", "Anime Vault", 1890, true, true),
    FakeChannel("c3", "Series Central", 1620, false, true),
    FakeChannel("c4", "Class Videos", 96, false, false),
)

val fakeUnmatched = listOf(
    UnmatchedFile("u1", "random.movie.2023.1080p.mkv", "5.2 GB"),
    UnmatchedFile("u2", "Lecture_05_Physics.mp4", "410 MB"),
    UnmatchedFile("u3", "[E22] Unknown Anime [720p].mkv", "380 MB"),
)

val subtitleLanguages = listOf("English", "Malayalam", "Hindi", "Tamil", "Spanish", "Arabic")
