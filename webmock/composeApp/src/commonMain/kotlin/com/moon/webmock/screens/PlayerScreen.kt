package com.moon.webmock.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.webmock.theme.MsColors
import com.moon.webmock.theme.doto
import kotlinx.coroutines.delay
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Player redesign mockup (moon's 2026-11-14): the in-app player, rebuilt
// here FIRST — the app only changes if he likes it. Interactive: play/pause,
// seek, double-tap ±10s, swipe brightness/volume/seek, skip segments,
// subtitles/audio/episodes sheets, lock, NextUp. Demo episode: Alice in
// Borderland S1, 24:00, intro 0:00-1:30, outro 22:30-24:00.
// In portrait the player doesn't fit (it's landscape-only, like the app),
// so portrait shows what changed + how to try it; rotate to play.
// 2026-11-19: three looks (PlayerLooks.kt) — Current, Tube, Rail —
// same buttons, same styling, only the ARRANGEMENT differs; switch
// them in App.kt's Look row. (The first round only restyled one layout;
// moon rightly said they were all the same, so this round moves the
// controls instead.)
// ---------------------------------------------------------------------------

private enum class MockSheet { SUBS, AUDIO, EPISODES, SUBLINES, PLAYLIST }

private sealed interface MockHud {
    val fraction: Float
    data class Brightness(override val fraction: Float) : MockHud
    data class Volume(override val fraction: Float) : MockHud
}

private val mockChanges = listOf(
    "Segment markers on the seekbar" to
        "Intro (amber) and outro (violet) blocks drawn ON the bar, so you can see them before scrubbing.",
    "Scrub bubble" to
        "Drag the seekbar and the target time floats above your thumb — with the segment name when inside one.",
    "Cleaner top bar" to
        "Title in Inter, no heavy bordered pill; icons a touch closer together.",
    "Slim gesture HUD" to
        "Brightness/volume is a small pill under the top bar now, not a big bar on the screen edge.",
    "Soft Skip / Resume pills" to
        "Translucent capsules with an icon, instead of solid block buttons.",
    "Resume, once" to
        "The floating Resume pill stays; the duplicate History icon is gone from the bottom row.",
    "NextUp card with poster" to
        "Thumb + title + countdown + Play now, in the app's card style.",
    "Press feedback" to
        "Buttons shrink slightly while pressed, iOS-style.",
    "Trimmer drawer" to
        "Scissors button: mark Start/End, drag the range slider, Preview loops the clip, Save Clip fakes a save.",
    "Full subtitle sheet" to
        "Tracks, All lines, ±250ms delay, and Appearance — size, stroke, colors, position — all live on the subtitle line.",
    "Subtitle lines" to
        "All lines… lists the dialogue — tap a line to jump there.",
    "Playlist drawer" to
        "The List button shows the episode's streams by quality.",
)

@Composable
fun PlayerScreen(look: PlayerLook = PlayerLooks.current) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (maxWidth < maxHeight) {
            PlayerPortraitNotes(look)
        } else {
            PlayerLandscapeMock(look)
        }
    }
}

/** Portrait: what changed + how to try it (the player is landscape-only). */
@Composable
private fun PlayerPortraitNotes(look: PlayerLook) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .padding(horizontal = 20.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ScreenRotation,
                    contentDescription = null,
                    tint = MsColors.Accent,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Rotate your phone",
                    style = MaterialTheme.typography.headlineSmall.doto(),
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The player is landscape-only, like in the app — turn your " +
                    "phone sideways to try the redesign. It plays (a fake " +
                    "episode), seeks, skips and opens the sheets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Three looks to try",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Same buttons, same styling — just arranged three different " +
                    "ways. Switch them in the Look row above the player. " +
                    "Playback keeps going when you switch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
        }
        items(PlayerLooks.all) { lookOption ->
            Row(Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(7.dp)
                        .background(
                            if (lookOption.id == look.id) MsColors.Accent
                            else MsColors.TextSecondary,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        lookOption.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        lookOption.blurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                }
            }
        }
        item {
            Text(
                "What's new in this design",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
        }
        items(mockChanges) { (title, desc) ->
            Row(Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(7.dp)
                        .background(MsColors.Accent, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Try: play/pause, drag the seekbar, double-tap the left/right " +
                    "half for -10s/+10s, swipe up/down on the left half for " +
                    "brightness and the right half for volume, swipe sideways " +
                    "to seek, open Subtitles (appearance controls work live), " +
                    "Audio, Episodes and Playlist, and the scissors button " +
                    "for the trimmer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerLandscapeMock(look: PlayerLook) {
    // Demo episode.
    val durationMs = 1_440_000L // 24:00
    val segments = remember {
        listOf(
            MockSeekSegment(0L, 90_000L, "Intro", Color(0xFFFFAA33)),
            MockSeekSegment(1_350_000L, 1_440_000L, "Outro", Color(0xFFB388FF)),
        )
    }
    val resumeMs = 754_000L // 12:34
    val subTracks = listOf("Off", "English", "Malayalam")
    val audioTracks = listOf("Japanese · AAC 5.1", "Japanese · EAC3 5.1", "English · AAC 2.0")
    val episodes = (1..8).map { "Episode $it" }
    val aspects = listOf("FIT", "FILL", "STRETCH")
    val subLines = listOf(
        MockSubLine(12_000L, "Arisu: Another day, another game."),
        MockSubLine(47_000L, "Arisu: This world... it's not the one we knew."),
        MockSubLine(125_000L, "Usagi: We have to keep moving. The lasers don't wait."),
        MockSubLine(221_000L, "Chishiya: Every game has a rule. Find it."),
        MockSubLine(312_000L, "Arisu: The beach... it has to mean something."),
        MockSubLine(453_000L, "Kuina: Trust me. I've done this before."),
        MockSubLine(598_000L, "Arisu: If we stop, we die. That's the game."),
        MockSubLine(754_000L, "Usagi: Then we don't stop. Not ever."),
        MockSubLine(920_000L, "Chishiya: The answer was in front of us all along."),
        MockSubLine(1_082_000L, "Arisu: One more game. Just one more."),
        MockSubLine(1_245_000L, "Usagi: Look — the gate is open."),
        MockSubLine(1_371_000L, "Arisu: This isn't over. It's just the start."),
    )
    val playlistEntries = listOf(
        MockPlaylistEntry("1080p HEVC 10-bit", "4.2 GB"),
        MockPlaylistEntry("1080p AVC", "3.1 GB"),
        MockPlaylistEntry("720p", "1.8 GB"),
    )

    var episode by remember { mutableStateOf(1) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(47_000L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactNonce by remember { mutableStateOf(0) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var seekFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var swipePreview by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var hud by remember { mutableStateOf<MockHud?>(null) }
    var brightness by remember { mutableStateOf(0.7f) }
    var volume by remember { mutableStateOf(0.6f) }
    var sheet by remember { mutableStateOf<MockSheet?>(null) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var subTrack by remember { mutableStateOf(1) }
    var audioTrack by remember { mutableStateOf(0) }
    var subDelayMs by remember { mutableStateOf(0) }
    var showRemaining by remember { mutableStateOf(false) }
    var aspectIndex by remember { mutableStateOf(0) }
    var nextUpDismissed by remember { mutableStateOf(false) }
    // Trimmer state (port of the app's).
    var showTrim by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableStateOf<Long?>(null) }
    var trimEndMs by remember { mutableStateOf<Long?>(null) }
    var trimStepFrame by remember { mutableStateOf(false) }
    var trimLoop by remember { mutableStateOf(false) }
    var trimSaving by remember { mutableStateOf(false) }
    var trimProgress by remember { mutableStateOf(0f) }
    // Subtitle appearance (drives the mock subtitle line live).
    var subSize by remember { mutableStateOf(0.06f) }
    var subStrokeOn by remember { mutableStateOf(true) }
    var subTextColor by remember { mutableStateOf(Color.White) }
    var subStrokeColor by remember { mutableStateOf(Color.Black) }
    var subPosition by remember { mutableStateOf(0f) }
    var qualityIndex by remember { mutableStateOf(0) }
    val bufferFrac = 0.35f // demo: buffered to ~8:24

    fun poke() {
        interactNonce++
        controlsVisible = true
    }
    fun seekBy(deltaMs: Long) {
        positionMs = (positionMs + deltaMs).coerceIn(0L, durationMs)
        poke()
    }
    fun nextEpisode() {
        if (episode < 8) {
            episode++
            positionMs = 0L
            nextUpDismissed = false
            isPlaying = true
            poke()
        }
    }
    fun nudgeTrimMark(isStart: Boolean, dir: Int) {
        val step = if (trimStepFrame) 41L else 1000L
        if (isStart) {
            val cur = trimStartMs ?: return
            val end = trimEndMs ?: durationMs
            val newStart = (cur + dir * step).coerceIn(0L, (end - 1000L).coerceAtLeast(0L))
            trimStartMs = newStart
            positionMs = newStart
        } else {
            val cur = trimEndMs ?: return
            val start = trimStartMs ?: 0L
            val newEnd = (cur + dir * step).coerceIn(start + 1000L, durationMs)
            trimEndMs = newEnd
            positionMs = newEnd
        }
    }
    fun applyTrimRange(range: ClosedFloatingPointRange<Float>) {
        trimStartMs = range.start.toLong()
        trimEndMs = range.endInclusive.toLong()
    }
    fun toggleTrimLoop() {
        if (!trimLoop) {
            trimStartMs?.let { positionMs = it }
            isPlaying = true
        }
        trimLoop = !trimLoop
    }

    // Fake playback clock.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(250)
            positionMs = (positionMs + 250).coerceAtMost(durationMs)
            // Loop preview: wrap back to the trim start at the trim end.
            if (trimLoop && trimStartMs != null && trimEndMs != null &&
                positionMs >= trimEndMs!!
            ) {
                positionMs = trimStartMs!!
            }
            if (positionMs >= durationMs) isPlaying = false
        }
    }
    // Fake clip save: progress bar, then done.
    LaunchedEffect(trimSaving) {
        if (trimSaving) {
            trimProgress = 0f
            while (trimProgress < 1f) {
                delay(100)
                trimProgress = (trimProgress + 0.05f).coerceAtMost(1f)
            }
            delay(200)
            trimSaving = false
            showTrim = false
            trimLoop = false
            trimStartMs = null
            trimEndMs = null
            toast = "Clip saved (mock)"
        }
    }
    // Auto-play next at the end (unless he hid the NextUp card).
    LaunchedEffect(positionMs) {
        if (positionMs >= durationMs && !nextUpDismissed && episode < 8) {
            nextEpisode()
        }
    }
    // Controls auto-hide 3s after the last interaction (while playing).
    // Not while trimming (the app keeps them up for the trimmer).
    LaunchedEffect(interactNonce, isPlaying, scrubMs, showTrim) {
        if (isPlaying && controlsVisible && scrubMs == null && !showTrim) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900)
            seekFeedback = null
        }
    }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(1500)
            hud = null
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2000)
            toast = null
        }
    }
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(4000)
            showUnlock = false
        }
    }

    val currentSegment = segments.firstOrNull { positionMs in it.startMs until it.endMs }
    val showSkip = currentSegment != null && !locked
    val showResume = resumeMs > 10_000L && positionMs < resumeMs - 10_000L && !locked
    val showNextUp = positionMs > durationMs - 90_000L && !nextUpDismissed && !locked && episode < 8
    val nextUpSecs = ((durationMs - positionMs) / 1000).toInt().coerceAtLeast(0)
    val subLine = when (subTrack) {
        2 -> "അരിസു: ഈ ലോകം... നമ്മൾ അറിഞ്ഞ ലോകമല്ല."
        else -> "Arisu: This world... it's not the one we knew."
    }

    // Per-look clearances: Current and Tube share the same bottom height
    // (a button row + the seekbar row), so pills and subtitles sit at the
    // same height in both; Rail's bottom is only the seekbar row, so they
    // sit lower — and its right-side pills shift left, clear of the rail.
    val subClearance = if (look == PlayerLook.RAIL) 84.dp else 132.dp
    val pillBottom = if (look == PlayerLook.RAIL) 56.dp else 104.dp
    val pillEnd = if (look == PlayerLook.RAIL) 84.dp else 16.dp
    val nextUpBottom = if (look == PlayerLook.RAIL) 120.dp else 168.dp

    // -- Control pieces, shared by all three layouts. Only the ARRANGEMENT
    // differs per look (Current/Tube/Rail below); every button here keeps
    // Current's styling — 45dp round buttons, same seekbar, same pills.
    // Local composables so they share this scope's state and callbacks.
    @Composable
    fun BackButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = { toast = "Back — closes the player in the app (mock)" },
        )
    }

    @Composable
    fun TitlePill() {
        Surface(
            onClick = { sheet = MockSheet.EPISODES },
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Text(
                text = "Alice in Borderland — S1 E$episode",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }

    @Composable
    fun EpisodesButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.PlaylistPlay,
            contentDescription = "Episodes",
            onClick = { sheet = MockSheet.EPISODES },
            enabled = !showTrim,
        )
    }

    @Composable
    fun PlaylistButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.List,
            contentDescription = "Playlist",
            onClick = { sheet = MockSheet.PLAYLIST },
            enabled = !showTrim,
        )
    }

    @Composable
    fun ScreenshotButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.PhotoCamera,
            contentDescription = "Screenshot",
            onClick = { toast = "Screenshot saved (mock)" },
            enabled = !showTrim,
        )
    }

    @Composable
    fun TrimButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.ContentCut,
            contentDescription = "Trim clip",
            onClick = {
                showTrim = !showTrim
                if (!showTrim) trimLoop = false
            },
            active = showTrim,
        )
    }

    @Composable
    fun LockButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.Lock,
            contentDescription = "Lock controls",
            onClick = {
                locked = true
                controlsVisible = false
                showUnlock = true
            },
            enabled = !showTrim,
        )
    }

    @Composable
    fun SubtitlesButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.ClosedCaption,
            contentDescription = "Subtitles",
            onClick = { sheet = MockSheet.SUBS },
            active = subTrack != 0,
            enabled = !showTrim,
        )
    }

    @Composable
    fun AudioButton() {
        MockPlayerIconButton(
            icon = Icons.Filled.Audiotrack,
            contentDescription = "Audio",
            onClick = { sheet = MockSheet.AUDIO },
            enabled = !showTrim,
        )
    }

    @Composable
    fun NextButton() {
        if (episode < 8) {
            MockPlayerIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next episode",
                onClick = { nextEpisode() },
                enabled = !showTrim,
            )
        }
    }

    @Composable
    fun AspectPill() {
        Surface(
            onClick = { aspectIndex = (aspectIndex + 1) % aspects.size },
            enabled = !showTrim,
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.height(45.dp).mockPressScale(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Icon(
                    Icons.Filled.AspectRatio,
                    contentDescription = "Aspect ratio",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    aspects[aspectIndex],
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }

    /** The five top-bar tools as one row (Current + Tube top bars). */
    @Composable
    fun TopCluster() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EpisodesButton()
            PlaylistButton()
            ScreenshotButton()
            TrimButton()
            LockButton()
        }
    }

    /** Center transport: -10s / play-pause / +10s (Current + Rail). */
    @Composable
    fun CenterTransport() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            MockPlayerIconButton(
                icon = Icons.Filled.Replay10,
                contentDescription = "Back 10s",
                onClick = {
                    seekBy(-10_000L)
                    seekFeedback = false to 10
                },
                size = 52.dp,
                iconSize = 32.dp,
            )
            Surface(
                onClick = {
                    isPlaying = !isPlaying
                    poke()
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.size(76.dp).mockPressScale(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            MockPlayerIconButton(
                icon = Icons.Filled.Forward10,
                contentDescription = "Forward 10s",
                onClick = {
                    seekBy(10_000L)
                    seekFeedback = true to 10
                },
                size = 52.dp,
                iconSize = 32.dp,
            )
        }
    }

    /** Current time + seekbar + total time (all three bottom bars). */
    @Composable
    fun SeekRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mockFormatTime(scrubMs ?: positionMs),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.width(64.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                MockSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferFrac = bufferFrac,
                    segments = segments,
                    onSeek = { positionMs = it; poke() },
                    onScrubChange = { scrubMs = it },
                    modifier = Modifier.fillMaxWidth(),
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                )
            }
            Text(
                if (showRemaining) {
                    "-${mockFormatTime((durationMs - positionMs).coerceAtLeast(0L))}"
                } else {
                    mockFormatTime(durationMs)
                },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { showRemaining = !showRemaining },
            )
        }
    }

    /** Current's bottom button row: Subs + Audio, Next + Aspect. */
    @Composable
    fun BottomButtonRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubtitlesButton()
                AudioButton()
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NextButton()
                AspectPill()
            }
        }
    }

    /** Tube's bottom transport row: Play, -10, +10, Next | Subs, Audio, Aspect. */
    @Composable
    fun TransportRow() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MockPlayerIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = {
                    isPlaying = !isPlaying
                    poke()
                },
            )
            MockPlayerIconButton(
                icon = Icons.Filled.Replay10,
                contentDescription = "Back 10s",
                onClick = {
                    seekBy(-10_000L)
                    seekFeedback = false to 10
                },
            )
            MockPlayerIconButton(
                icon = Icons.Filled.Forward10,
                contentDescription = "Forward 10s",
                onClick = {
                    seekBy(10_000L)
                    seekFeedback = true to 10
                },
            )
            NextButton()
            Spacer(Modifier.weight(1f))
            SubtitlesButton()
            AudioButton()
            AspectPill()
        }
    }

    /**
     * Rail's side capsule: all nine tools stacked top to bottom, centered
     * on the right edge. Scrollable, so on a short phone screen every
     * button stays reachable (on a tall screen it all fits at once).
     * Aspect is icon + its current value underneath — the pill is too
     * wide for the rail.
     */
    @Composable
    fun SideRail() {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxHeight(0.94f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                EpisodesButton()
                PlaylistButton()
                ScreenshotButton()
                TrimButton()
                LockButton()
                SubtitlesButton()
                AudioButton()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MockPlayerIconButton(
                        icon = Icons.Filled.AspectRatio,
                        contentDescription = "Aspect ratio",
                        onClick = { aspectIndex = (aspectIndex + 1) % aspects.size },
                        enabled = !showTrim,
                    )
                    Text(
                        aspects[aspectIndex],
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                NextButton()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked, sheet, showTrim) {
                detectTapGestures(
                    onTap = {
                        if (locked) {
                            showUnlock = true
                        } else if (sheet == null && !showTrim) {
                            if (controlsVisible) controlsVisible = false else poke()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!locked && sheet == null && !showTrim) {
                            val forward = offset.x > size.width / 2
                            // Consecutive double-taps accumulate (10> → 20> → 30>),
                            // like the app: while the feedback is still up, add 10.
                            val secs = if (seekFeedback?.first == forward) {
                                seekFeedback!!.second + 10
                            } else 10
                            seekBy(if (forward) secs * 1000L else -secs * 1000L)
                            seekFeedback = forward to secs
                        }
                    },
                )
            }
            .pointerInput(locked, sheet, showTrim) {
                // Vertical swipes: left half = brightness, right = volume.
                detectVerticalDragGestures { change, dragAmount ->
                    if (sheet == null && !locked && !showTrim) {
                        change.consume()
                        val delta = -dragAmount / 800f
                        if (change.position.x < size.width / 2f) {
                            brightness = (brightness + delta).coerceIn(0f, 1f)
                            hud = MockHud.Brightness(brightness)
                        } else {
                            volume = (volume + delta).coerceIn(0f, 1f)
                            hud = MockHud.Volume(volume)
                        }
                    }
                }
            }
            .pointerInput(locked, sheet, showTrim) {
                // Horizontal swipes: seek. Preview live, seek once on release.
                var accPx = 0f
                var startMs = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        accPx = 0f
                        startMs = positionMs
                        swipePreview = startMs to 0L
                    },
                    onDragEnd = {
                        val target = swipePreview?.first
                        swipePreview = null
                        if (target != null && sheet == null && !locked && !showTrim) {
                            positionMs = target
                            poke()
                        }
                    },
                    onDragCancel = { swipePreview = null },
                ) { change, dragAmount ->
                    if (sheet == null && !locked && !showTrim) {
                        change.consume()
                        accPx += dragAmount
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val target = (startMs + (accPx / widthPx * 90_000f).toLong())
                            .coerceIn(0L, durationMs)
                        swipePreview = target to (target - startMs)
                    }
                }
            },
    ) {
        MockVideoScene()

        // Subtitle line (mock text; Malayalam when that track is picked).
        // Appearance comes from the subtitle sheet, live: size, stroke,
        // colors, position.
        if (subTrack != 0) {
            Text(
                subLine,
                color = subTextColor,
                fontSize = (8 + subSize * 160).sp,
                fontWeight = FontWeight.SemiBold,
                style = if (subStrokeOn) {
                    TextStyle(shadow = Shadow(subStrokeColor, Offset(0f, 2f), 4f))
                } else {
                    TextStyle.Default
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) subClearance else 36.dp)
                    .padding(bottom = (subPosition * 300).dp)
                    .padding(horizontal = 48.dp),
            )
        }

        // Top bar: Current and Tube share it (back + title + cluster);
        // Rail keeps only back + title — the cluster lives in the rail.
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton()
                Spacer(Modifier.width(12.dp))
                TitlePill()
                if (look != PlayerLook.RAIL) {
                    Spacer(Modifier.weight(1f))
                    TopCluster()
                }
            }
        }

        // Center transport (Current + Rail): -10s / play-pause / +10s.
        // Tube has an EMPTY center — its transport lives in the bottom
        // bar instead (double-tap -10s/+10s still works: it is a gesture,
        // not a button).
        if (look != PlayerLook.TUBE) {
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                CenterTransport()
            }
        }

        // Double-tap feedback: low-opacity text only (moon's rule).
        // Arrows now, not +10s/-10s (moon's 2026-11-14).
        seekFeedback?.let { (forward, secs) ->
            Text(
                if (forward) "${secs}>" else "<${secs}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 64.dp),
            )
        }

        // Swipe-seek preview: target time + delta, centre, low-opacity text.
        swipePreview?.let { (target, delta) ->
            Text(
                "${mockFormatTime(target)} (${if (delta >= 0) "+" else "−"}${mockFormatTime(abs(delta))})",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 4f)),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Bottom cluster — this is where the layouts split:
        // Current: button row, then the seekbar row. Tube: transport
        // row (Play, -10, +10, Next | Subs, Audio, Aspect), then the
        // seekbar row. Rail: the seekbar row only, stopping short of
        // the rail (end padding); its buttons live in the rail.
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        ),
                    )
                    .padding(
                        start = 16.dp,
                        end = if (look == PlayerLook.RAIL) 84.dp else 16.dp,
                    )
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                when (look) {
                    PlayerLook.CURRENT -> {
                        BottomButtonRow()
                        Spacer(Modifier.height(4.dp))
                        SeekRow()
                    }
                    PlayerLook.TUBE -> {
                        TransportRow()
                        Spacer(Modifier.height(4.dp))
                        SeekRow()
                    }
                    PlayerLook.RAIL -> SeekRow()
                }
            }
        }

        // Rail (Rail only): the side capsule, vertically centered on
        // the right edge. Drawn after the bottom cluster so it stays
        // on top where they meet.
        if (look == PlayerLook.RAIL) {
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            ) {
                SideRail()
            }
        }

        // Skip pill (NEW style): above the controls when they're showing,
        // low when they're hidden.
        if (showSkip) {
            val seg = currentSegment!!
            MockSkipPill(
                label = "Skip ${seg.label}",
                icon = Icons.Filled.SkipNext,
                onClick = {
                    positionMs = seg.endMs
                    poke()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = pillEnd, bottom = if (controlsVisible) pillBottom else 28.dp),
            )
        }

        // Resume pill (NEW style), bottom-left mirroring Skip.
        if (showResume) {
            MockSkipPill(
                label = "Resume ${mockFormatTime(resumeMs)}",
                icon = Icons.Filled.History,
                onClick = {
                    positionMs = resumeMs
                    poke()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (controlsVisible) pillBottom else 28.dp),
            )
        }

        // NextUp card (NEW style), stacked above the Skip pill.
        if (showNextUp) {
            MockNextUpCard(
                episodeLabel = "S1 E${episode + 1} · Alice in Borderland",
                secsLeft = nextUpSecs,
                onPlayNow = { nextEpisode() },
                onHide = { nextUpDismissed = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(320.dp)
                    .padding(end = pillEnd, bottom = if (controlsVisible) nextUpBottom else 88.dp),
            )
        }

        // Gesture HUD (NEW style): slim pill under the top bar.
        hud?.let {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                MockGestureHud(
                    icon = when (it) {
                        is MockHud.Brightness -> Icons.Filled.BrightnessMedium
                        is MockHud.Volume -> Icons.Filled.VolumeUp
                    },
                    fraction = it.fraction,
                    modifier = Modifier.padding(top = if (controlsVisible) 76.dp else 16.dp),
                )
            }
        }

        // Slide-to-unlock pill while locked.
        if (locked && showUnlock) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                MockUnlockSlider(
                    onUnlock = {
                        locked = false
                        showUnlock = false
                        poke()
                    },
                    modifier = Modifier.padding(bottom = 64.dp),
                )
            }
        }

        // Sheets + drawers + trimmer.
        AnimatedVisibility(
            visible = sheet == MockSheet.SUBS,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockSubtitleSheet(
                options = subTracks,
                selected = subTrack,
                onSelect = { subTrack = it; sheet = null },
                onDismiss = { sheet = null },
                onLoadFromPhone = { toast = "Load subtitle from phone (mock)" },
                onAllLines = { sheet = MockSheet.SUBLINES },
                delayMs = subDelayMs,
                onAdjustDelay = { subDelayMs = (subDelayMs + it).coerceIn(-5000, 5000) },
                textSize = subSize,
                onTextSize = { subSize = it },
                strokeOn = subStrokeOn,
                onStrokeOn = { subStrokeOn = it },
                textColor = subTextColor,
                onTextColor = { subTextColor = it },
                strokeColor = subStrokeColor,
                onStrokeColor = { subStrokeColor = it },
                position = subPosition,
                onPosition = { subPosition = it },
            )
        }
        AnimatedVisibility(
            visible = sheet == MockSheet.SUBLINES,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockSubLinesSheet(
                lines = subLines,
                onJump = {
                    positionMs = it
                    sheet = null
                    poke()
                },
                onDismiss = { sheet = null },
            )
        }
        AnimatedVisibility(
            visible = sheet == MockSheet.AUDIO,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockTrackSheet(
                title = "Audio",
                options = audioTracks,
                selected = audioTrack,
                onSelect = { audioTrack = it; sheet = null },
                onDismiss = { sheet = null },
            )
        }
        AnimatedVisibility(
            visible = sheet == MockSheet.EPISODES,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockEpisodesDrawer(
                episodes = episodes,
                current = episode - 1,
                onSelect = {
                    episode = it + 1
                    positionMs = 0L
                    nextUpDismissed = false
                    isPlaying = true
                    sheet = null
                    poke()
                },
                onDismiss = { sheet = null },
            )
        }
        AnimatedVisibility(
            visible = sheet == MockSheet.PLAYLIST,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockPlaylistDrawer(
                entries = playlistEntries,
                current = qualityIndex,
                onSelect = {
                    qualityIndex = it
                    sheet = null
                    toast = "Switched to ${playlistEntries[it].label} (mock)"
                },
                onDismiss = { sheet = null },
            )
        }

        // Trimmer side drawer (the app's TrimDrawer, full height right).
        if (showTrim) {
            MockTrimDrawer(
                startMs = trimStartMs,
                endMs = trimEndMs,
                currentMs = positionMs,
                durationMs = durationMs,
                saving = trimSaving,
                progress = trimProgress,
                looping = trimLoop,
                stepIsFrame = trimStepFrame,
                onToggleStep = { trimStepFrame = !trimStepFrame },
                onNudgeStart = { dir -> nudgeTrimMark(true, dir) },
                onNudgeEnd = { dir -> nudgeTrimMark(false, dir) },
                onSetStart = { trimStartMs = positionMs },
                onSetEnd = { trimEndMs = positionMs },
                onJumpToStart = { trimStartMs?.let { positionMs = it } },
                onJumpToEnd = { trimEndMs?.let { positionMs = it } },
                onRangeChange = { range -> applyTrimRange(range) },
                onRangeChangeFinished = { trimStartMs?.let { positionMs = it } },
                onClear = { trimStartMs = null; trimEndMs = null; trimLoop = false },
                onClose = { showTrim = false; trimLoop = false },
                onToggleLoop = { toggleTrimLoop() },
                onSave = { trimSaving = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxHeight()
                    .width(300.dp),
            )
        }

        // Toast.
        toast?.let {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(bottom = 160.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
