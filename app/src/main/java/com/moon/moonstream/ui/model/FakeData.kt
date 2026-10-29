package com.moon.moonstream.ui.model

import androidx.compose.ui.graphics.Color

/** Phase 1 fake data. TODO(Phase 3): load from Room library. */

data class FakeStream(
    val id: String,
    val quality: String,
    val fileName: String,
    val size: String,
)

data class FakeTitle(
    val id: String,
    val title: String,
    val year: Int,
    val type: String, // "movie" | "series" | "anime"
    val desc: String,
    val colorA: Color,
    val colorB: Color,
    val streams: List<FakeStream>,
    val hidden: Boolean = false,
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
        listOf(
            FakeStream("s4", "1080p", "[S01 E16] Seven Deadly Sins [1080p][Dual].mkv", "425 MB"),
            FakeStream("s5", "1080p", "[S01 E17] Seven Deadly Sins [1080p][Dual].mkv", "431 MB"),
        ),
    ),
    FakeTitle(
        "t3", "Breaking Bad", 2008, "series",
        "A chemistry teacher turned meth manufacturer descends into the criminal underworld.",
        Color(0xFF1B5E20), Color(0xFF081208),
        listOf(
            FakeStream("s6", "1080p", "Breaking.Bad.S01E01.1080p.BluRay.mkv", "2.8 GB"),
            FakeStream("s7", "720p", "Breaking.Bad.S01E01.720p.HDTV.mkv", "1.2 GB"),
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
        listOf(
            FakeStream("s9", "1080p", "One.Piece.E1123.1080p.SubPlease.mkv", "1.4 GB"),
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
        listOf(
            FakeStream("s14", "720p", "Naruto.Shippuden.E016.720p.mkv", "640 MB"),
        ),
    ),
)

val fakeSearchResults = listOf(
    "Dune: Part Two (2024)",
    "Dune (2021)",
    "The Seven Deadly Sins (2014)",
    "Breaking Bad (2008)",
    "Spider-Man: Brand New Day (2026)",
    "One Piece (1999)",
    "Interstellar (2014)",
    "Naruto: Shippuden (2007)",
    "The Dark Knight (2008)",
    "Physics Class Recordings (2026)",
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
