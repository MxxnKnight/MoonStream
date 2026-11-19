package com.moon.webmock.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
// 2026-11-19: three looks (PlayerLooks.kt) — Current, Stremio, Nuvio —
// three full DESIGNS: Current is the approved one, Nuvio is based on
// the real NuvioMobile player source, Stremio is inspired by Stremio's
// web player + its purple accent (our take on its style, not its code);
// switch them in App.kt's Look row. The 13
// functions never change — no speed, no PiP, no extras — but each look
// has its own button treatments, icons and seekbar. Compact mode
// (maxHeight < 480dp, phone landscape) shrinks the transport and pulls
// the floating bits down so nothing overlaps the top bar — the fixed
// desktop offsets collided there.
// ---------------------------------------------------------------------------

private enum class MockSheet { SUBS, AUDIO, EPISODES, SUBLINES, PLAYLIST }

private sealed interface MockHud {
    val fraction: Float
    data class Brightness(override val fraction: Float) : MockHud
    data class Volume(override val fraction: Float) : MockHud
}

// Stremio-inspired colors. Only the purple accent is Stremio's real
// one: --primary-accent-color in stremio-web App/styles.less is
// rgb(123, 91, 245) = #7B5BF5. The REST is our take on its style, not
// its code: Stremio's Android player is not open source, and its web
// player (ControlBar.js/SeekBar.js) is a bottom bar with a SOLID
// accent seekbar WITH a thumb. No top pill, no glass, no gradient in
// Stremio's own code — the pill, the glowing play button and the
// purple-gradient seekbar here are invented for this mockup.
private val stremioAccent = Color(0xFF7B5BF5)
private val stremioGlass = Color(0xB72A2935)
private val stremioMenuBg = Color(0xEE141422)
private val stremioScrim = Color(0xE60A0A14)
private val stremioGradient = listOf(Color(0xFF7B5BF5), Color(0xFFC084FC), Color(0xFFFF8A65))
private val stremioGradientGlow = stremioGradient.map { it.copy(alpha = 0.15f) }
private val stremioGlowPlaying = Color(0x338B5CF6)
private val stremioGlowPaused = Color(0x3310B981)

// Nuvio's accent gradient (NuvioMedia/NuvioMobile,
// CustomThemeColors): violet -> pink -> peach. The timeline
// fill is this gradient; the buffer is the first stop at 35%.
private val nuvioGradient = listOf(Color(0xFFB75AFF), Color(0xFFEC70A9), Color(0xFFFFB37A))
private val nuvioAccent = Color(0xFFB75AFF)

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
            // Short screen (phone landscape): compact mode shrinks the
            // transport and pulls the floating bits down, so nothing
            // overlaps the top bar (moon's 2026-11-19 screenshot).
            PlayerLandscapeMock(look, compact = maxHeight < 480.dp)
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
                "Three full player designs — the current one, one in " +
                    "Stremio's style, one in Nuvio's. Same 13 buttons, no " +
                    "extras, just each app's own look. Switch them in the " +
                    "Look row above the player. Playback keeps going when " +
                    "you switch.",
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
private fun PlayerLandscapeMock(look: PlayerLook, compact: Boolean) {
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
    // Stremio look: the top pill's overflow menu (Subtitles, Audio and
    // the rest live there — Stremio's web player has the same kind of
    // overflow menu). Closes when the controls auto-hide, when a sheet
    // opens, or when the look changes.
    var stremioMenuOpen by remember { mutableStateOf(false) }
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
    // The Stremio menu closes with them, so it never reappears stale.
    LaunchedEffect(interactNonce, isPlaying, scrubMs, showTrim) {
        if (isPlaying && controlsVisible && scrubMs == null && !showTrim) {
            delay(3000)
            controlsVisible = false
            stremioMenuOpen = false
        }
    }
    // A look switch closes the Stremio menu (it only exists there).
    LaunchedEffect(look) {
        stremioMenuOpen = false
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

    // Per-look clearances: the bottom block's height decides where the
    // subtitle, pills and NextUp sit. Current: button row + seekbar row.
    // Stremio: the glass card (~90dp). Nuvio: title + seekbar + action
    // row (~160dp) — the tallest, so its bits sit highest; in compact
    // mode Nuvio drops the release-info line to save 20dp.
    // Compact (phone landscape, ~215-280dp tall): shrink the fixed
    // offsets FIRST — that is what collided in moon's screenshot — and
    // the transport second. The 45dp buttons stay 45dp everywhere.
    val subClearance = when {
        look == PlayerLook.NUVIO && compact -> 140.dp
        look == PlayerLook.NUVIO -> 184.dp
        look == PlayerLook.STREMIO && compact -> 96.dp
        look == PlayerLook.STREMIO -> 128.dp
        compact -> 96.dp
        else -> 132.dp
    }
    val pillBottom = when {
        look == PlayerLook.NUVIO && compact -> 136.dp
        look == PlayerLook.NUVIO -> 188.dp
        look == PlayerLook.STREMIO && compact -> 100.dp
        look == PlayerLook.STREMIO -> 132.dp
        compact -> 96.dp
        else -> 104.dp
    }
    val pillEnd = 16.dp
    val nextUpBottom = when {
        look == PlayerLook.NUVIO && compact -> 196.dp
        look == PlayerLook.NUVIO -> 248.dp
        look == PlayerLook.STREMIO && compact -> 156.dp
        look == PlayerLook.STREMIO -> 192.dp
        compact -> 152.dp
        else -> 168.dp
    }
    // Stremio's HUD sits below the top pill; Nuvio/Current keep the old spot.
    val hudTop = when {
        look == PlayerLook.STREMIO && compact -> 80.dp
        look == PlayerLook.STREMIO -> 84.dp
        compact -> 68.dp
        else -> 76.dp
    }
    val toastBottom = when {
        look == PlayerLook.NUVIO && compact -> 170.dp
        look == PlayerLook.NUVIO -> 220.dp
        compact -> 120.dp
        else -> 160.dp
    }
    val playSize = if (compact) 56.dp else 76.dp
    val playIconSize = if (compact) 28.dp else 38.dp
    val seek10Size = if (compact) 44.dp else 52.dp
    val seek10IconSize = if (compact) 26.dp else 32.dp
    val centerSpacing = if (compact) 40.dp else 64.dp
    val topBarVPadding = if (compact) 8.dp else 12.dp
    val bottomTopPadding = if (compact) 6.dp else 8.dp
    val bottomBottomPadding = if (compact) 8.dp else 12.dp

    // -- Control pieces. The first group is Current's: 45dp round
    // buttons, the title pill, the same seekbar and pills. Stremio and
    // Nuvio get their own pieces below (glass pill + menu + glowing
    // play; bare icons + bottom title block) — same 13 functions, each
    // app's own styling. Local composables so they share this scope's
    // state and callbacks.
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

    /** The five top-bar tools as one row (Current's top bar). */
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

    /** Center transport: -10s / play-pause / +10s (Current). */
    @Composable
    fun CenterTransport() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(centerSpacing),
        ) {
            MockPlayerIconButton(
                icon = Icons.Filled.Replay10,
                contentDescription = "Back 10s",
                onClick = {
                    seekBy(-10_000L)
                    seekFeedback = false to 10
                },
                size = seek10Size,
                iconSize = seek10IconSize,
            )
            Surface(
                onClick = {
                    isPlaying = !isPlaying
                    poke()
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.size(playSize).mockPressScale(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(playIconSize),
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
                size = seek10Size,
                iconSize = seek10IconSize,
            )
        }
    }

    /** Current time + seekbar + total time (Current's bottom bar). */
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

    // -- Stremio-inspired look: a glass top pill, glass center
    // buttons with a glowing play button, a glass bottom card holding
    // only the purple-gradient seekbar, and the other tools in the
    // pill's overflow menu (our take on its style, not its code).

    /** The glass top pill: back, title, more-menu button. */
    @Composable
    fun StremioTopPill() {
        Surface(
            shape = RoundedCornerShape(50),
            color = stremioGlass,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MockPlayerIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = { toast = "Back — closes the player in the app (mock)" },
                    size = 40.dp,
                    iconSize = 22.dp,
                    fillAlpha = 0f,
                    border = false,
                )
                Text(
                    text = "Alice in Borderland — S1 E$episode",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .clickable {
                            stremioMenuOpen = false
                            sheet = MockSheet.EPISODES
                        },
                )
                MockPlayerIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    onClick = {
                        stremioMenuOpen = !stremioMenuOpen
                        poke()
                    },
                    size = 40.dp,
                    iconSize = 22.dp,
                    fillAlpha = 0f,
                    border = false,
                )
            }
        }
    }

    /** One row of the overflow menu: icon + label, closes on tap. */
    @Composable
    fun StremioMenuRow(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        active: Boolean = false,
        enabled: Boolean = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) {
                    stremioMenuOpen = false
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) stremioAccent
                    else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    /**
     * The overflow menu: every tool that isn't Back, the transport or
     * the seekbar — Subtitles, Audio, Episodes, Playlist, Screenshot,
     * Trim, Lock, Next episode, Aspect. Scrollable, so it fits a short
     * phone screen too.
     */
    @Composable
    fun StremioMenu() {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = stremioMenuBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .width(240.dp)
                .heightIn(max = if (compact) 220.dp else 320.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp),
            ) {
                StremioMenuRow(
                    icon = Icons.Filled.ClosedCaption,
                    label = "Subtitles",
                    onClick = { sheet = MockSheet.SUBS },
                    active = subTrack != 0,
                    enabled = !showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.Audiotrack,
                    label = "Audio",
                    onClick = { sheet = MockSheet.AUDIO },
                    enabled = !showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.PlaylistPlay,
                    label = "Episodes",
                    onClick = { sheet = MockSheet.EPISODES },
                    enabled = !showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.List,
                    label = "Playlist",
                    onClick = { sheet = MockSheet.PLAYLIST },
                    enabled = !showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.PhotoCamera,
                    label = "Screenshot",
                    onClick = { toast = "Screenshot saved (mock)" },
                    enabled = !showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.ContentCut,
                    label = "Trim clip",
                    onClick = {
                        showTrim = !showTrim
                        if (!showTrim) trimLoop = false
                    },
                    active = showTrim,
                )
                StremioMenuRow(
                    icon = Icons.Filled.Lock,
                    label = "Lock controls",
                    onClick = {
                        locked = true
                        controlsVisible = false
                        showUnlock = true
                    },
                    enabled = !showTrim,
                )
                if (episode < 8) {
                    StremioMenuRow(
                        icon = Icons.Filled.SkipNext,
                        label = "Next episode",
                        onClick = { nextEpisode() },
                        enabled = !showTrim,
                    )
                }
                StremioMenuRow(
                    icon = Icons.Filled.AspectRatio,
                    label = "Aspect: ${aspects[aspectIndex]}",
                    onClick = { aspectIndex = (aspectIndex + 1) % aspects.size },
                    enabled = !showTrim,
                )
            }
        }
    }

    /**
     * Stremio's center: 38dp glass circles with OUTLINED rewind/forward
     * icons, and the 58dp glass play button inside a 92dp radial glow —
     * violet while playing, emerald while paused.
     */
    @Composable
    fun StremioCenterTransport() {
        val sideSize = if (compact) 36.dp else 38.dp
        val playSizeS = if (compact) 52.dp else 58.dp
        val glowSize = if (compact) 84.dp else 92.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MockPlayerIconButton(
                icon = Icons.Outlined.FastRewind,
                contentDescription = "Back 10s",
                onClick = {
                    seekBy(-10_000L)
                    seekFeedback = false to 10
                },
                size = sideSize,
                iconSize = 20.dp,
                fillAlpha = 0.10f,
                borderAlpha = 0.25f,
            )
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (isPlaying) stremioGlowPlaying else stremioGlowPaused,
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = size.width / 2,
                            ),
                            radius = size.width / 2,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = {
                        isPlaying = !isPlaying
                        poke()
                    },
                    shape = CircleShape,
                    color = Color(0x16000000),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(playSizeS).mockPressScale(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (compact) 26.dp else 28.dp),
                        )
                    }
                }
            }
            MockPlayerIconButton(
                icon = Icons.Outlined.FastForward,
                contentDescription = "Forward 10s",
                onClick = {
                    seekBy(10_000L)
                    seekFeedback = true to 10
                },
                size = sideSize,
                iconSize = 20.dp,
                fillAlpha = 0.10f,
                borderAlpha = 0.25f,
            )
        }
    }

    /**
     * Stremio's bottom card: just the timeline — the 4dp
     * purple-gradient seekbar with its glow underlay, current time
     * left and remaining time right below it. No thumb dot (moon's
     * rule — the real Stremio bar has a thumb; this one doesn't).
     */
    @Composable
    fun StremioBottomCard() {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = stremioGlass,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                    restHeight = 4.dp,
                    scrubHeight = 6.dp,
                    trackAlpha = 0.15f,
                    bufferAlpha = 0.30f,
                    fillColors = stremioGradient,
                    glowColors = stremioGradientGlow,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        mockFormatTime(scrubMs ?: positionMs),
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "-${mockFormatTime((durationMs - (scrubMs ?: positionMs)).coerceAtLeast(0L))}",
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    // -- Nuvio look (PlayerControlsShell, modern layout): bare white
    // icons with no circles, back + lock in a top-right toolbar, and
    // the title, gradient seekbar and a scrollable action row stacked
    // at the bottom. Next episode comes first in the row — and big.

    /** Nuvio's top-right toolbar: lock, then back at the far right. */
    @Composable
    fun NuvioToolbar() {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MockBareIconButton(
                icon = Icons.Rounded.Lock,
                contentDescription = "Lock controls",
                onClick = {
                    locked = true
                    controlsVisible = false
                    showUnlock = true
                },
                enabled = !showTrim,
            )
            MockBareIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = { toast = "Back — closes the player in the app (mock)" },
            )
        }
    }

    /**
     * Nuvio's center: bare ROUNDED replay/forward icons and the
     * play-pause, 34dp, 56dp apart, no circles anywhere. Lifted above
     * the center at the call site, clear of the bottom block.
     */
    @Composable
    fun NuvioCenterTransport() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 40.dp else 56.dp),
        ) {
            MockBareIconButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Back 10s",
                onClick = {
                    seekBy(-10_000L)
                    seekFeedback = false to 10
                },
                size = if (compact) 52.dp else 54.dp,
                iconSize = if (compact) 30.dp else 34.dp,
            )
            MockBareIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = {
                    isPlaying = !isPlaying
                    poke()
                },
                size = if (compact) 56.dp else 60.dp,
                iconSize = if (compact) 30.dp else 34.dp,
            )
            MockBareIconButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Forward 10s",
                onClick = {
                    seekBy(10_000L)
                    seekFeedback = true to 10
                },
                size = if (compact) 52.dp else 54.dp,
                iconSize = if (compact) 30.dp else 34.dp,
            )
        }
    }

    /**
     * Nuvio's bottom block (PlayerTimelineDetails + PlayerTimeline +
     * PlayerControlActions): title, SxxExx, release info (dropped in
     * compact mode to save height), the gradient seekbar — no thumb,
     * 6dp at rest, 10dp while scrubbing — and the action row: Next
     * first and big (40dp icon), then Subtitles, Audio, Episodes,
     * Playlist, Screenshot, Trim, Aspect, with the runtime pinned at
     * the right end (tap it to switch to remaining time).
     */
    @Composable
    fun NuvioBottomBlock() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Alice in Borderland",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 16.sp else 18.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Text(
                "S1 E$episode",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = if (compact) 12.sp else 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            if (!compact) {
                Text(
                    "${playlistEntries[qualityIndex].label} · ${playlistEntries[qualityIndex].size}",
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
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
                restHeight = 6.dp,
                scrubHeight = 10.dp,
                trackAlpha = 0.30f,
                bufferTint = nuvioAccent.copy(alpha = 0.35f),
                fillColors = nuvioGradient,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (episode < 8) {
                        MockBareIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Next episode",
                            onClick = { nextEpisode() },
                            iconSize = 40.dp,
                            enabled = !showTrim,
                        )
                    }
                    MockBareIconButton(
                        icon = Icons.Filled.ClosedCaption,
                        contentDescription = "Subtitles",
                        onClick = { sheet = MockSheet.SUBS },
                        active = subTrack != 0,
                        activeColor = nuvioAccent,
                        enabled = !showTrim,
                    )
                    MockBareIconButton(
                        icon = Icons.Filled.Audiotrack,
                        contentDescription = "Audio",
                        onClick = { sheet = MockSheet.AUDIO },
                        enabled = !showTrim,
                    )
                    MockBareIconButton(
                        icon = Icons.Filled.PlaylistPlay,
                        contentDescription = "Episodes",
                        onClick = { sheet = MockSheet.EPISODES },
                        enabled = !showTrim,
                    )
                    MockBareIconButton(
                        icon = Icons.Filled.List,
                        contentDescription = "Playlist",
                        onClick = { sheet = MockSheet.PLAYLIST },
                        enabled = !showTrim,
                    )
                    MockBareIconButton(
                        icon = Icons.Filled.PhotoCamera,
                        contentDescription = "Screenshot",
                        onClick = { toast = "Screenshot saved (mock)" },
                        enabled = !showTrim,
                    )
                    MockBareIconButton(
                        icon = Icons.Filled.ContentCut,
                        contentDescription = "Trim clip",
                        onClick = {
                            showTrim = !showTrim
                            if (!showTrim) trimLoop = false
                        },
                        active = showTrim,
                        activeColor = nuvioAccent,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MockBareIconButton(
                            icon = Icons.Filled.AspectRatio,
                            contentDescription = "Aspect ratio",
                            onClick = { aspectIndex = (aspectIndex + 1) % aspects.size },
                            size = 40.dp,
                            iconSize = 22.dp,
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
                }
                Text(
                    if (showRemaining) {
                        "−${mockFormatTime((durationMs - positionMs).coerceAtLeast(0L))}"
                    } else {
                        "${mockFormatTime(positionMs)} / ${mockFormatTime(durationMs)}"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.clickable {
                        showRemaining = !showRemaining
                        poke()
                    },
                )
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
                        } else if (stremioMenuOpen) {
                            // A tap with the Stremio menu open closes
                            // the menu instead of hiding the controls.
                            stremioMenuOpen = false
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

        // Per-look scrims (Stremio: one full-height navy gradient;
        // Nuvio: a 160dp top fade + a 260dp bottom fade). Current keeps
        // its own gradients on the top bar and bottom cluster.
        if (look == PlayerLook.STREMIO || look == PlayerLook.NUVIO) {
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (look == PlayerLook.STREMIO) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            stremioScrim,
                                            Color.Transparent,
                                            stremioScrim,
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f),
                                        ),
                                    ),
                                ),
                        )
                    }
                }
            }
        }

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

        // Top bar: Current keeps back + title pill + cluster; Stremio
        // gets the glass pill (back + title + menu); Nuvio gets the
        // top-right toolbar (lock + back) — its title lives at the
        // bottom.
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            when (look) {
                PlayerLook.CURRENT -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = topBarVPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackButton()
                        Spacer(Modifier.width(12.dp))
                        TitlePill()
                        Spacer(Modifier.weight(1f))
                        TopCluster()
                    }
                }
                PlayerLook.STREMIO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 18.dp,
                                vertical = if (compact) 12.dp else 18.dp,
                            ),
                    ) {
                        StremioTopPill()
                    }
                }
                PlayerLook.NUVIO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = if (compact) 8.dp else 12.dp,
                            ),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        NuvioToolbar()
                    }
                }
            }
        }

        // Center transport: Current's round buttons, Stremio's glass +
        // glowing play, Nuvio's bare icons lifted above the center so
        // they clear the tall bottom block (36dp on a short screen —
        // the subtitle sits between the block and the transport there).
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    y = if (look == PlayerLook.NUVIO) {
                        if (compact) -36.dp else -28.dp
                    } else {
                        0.dp
                    },
                ),
        ) {
            when (look) {
                PlayerLook.CURRENT -> CenterTransport()
                PlayerLook.STREMIO -> StremioCenterTransport()
                PlayerLook.NUVIO -> NuvioCenterTransport()
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

        // Bottom cluster — this is where the designs split:
        // Current: button row, then the seekbar row. Stremio: the glass
        // card with the purple-gradient seekbar only. Nuvio: title + seekbar +
        // action row stacked at the bottom.
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            when (look) {
                PlayerLook.CURRENT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                ),
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = bottomTopPadding, bottom = bottomBottomPadding),
                    ) {
                        BottomButtonRow()
                        Spacer(Modifier.height(4.dp))
                        SeekRow()
                    }
                }
                PlayerLook.STREMIO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = if (compact) 12.dp else 20.dp),
                    ) {
                        StremioBottomCard()
                    }
                }
                PlayerLook.NUVIO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = if (compact) 12.dp else 16.dp),
                    ) {
                        NuvioBottomBlock()
                    }
                }
            }
        }

        // Stremio's overflow menu: a tap below the pill closes it (the
        // pill itself stays tappable, so the more button can close it
        // too); a row runs its action and closes. Only while the
        // controls are up, drawn after the bottom cluster so it stays
        // on top.
        if (look == PlayerLook.STREMIO && stremioMenuOpen && controlsVisible && !locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (compact) 64.dp else 76.dp)
                    .clickable { stremioMenuOpen = false },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (compact) 64.dp else 76.dp,
                        end = 18.dp,
                    ),
            ) {
                StremioMenu()
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
                    modifier = Modifier.padding(top = if (controlsVisible) hudTop else 16.dp),
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
                        .padding(bottom = toastBottom)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
