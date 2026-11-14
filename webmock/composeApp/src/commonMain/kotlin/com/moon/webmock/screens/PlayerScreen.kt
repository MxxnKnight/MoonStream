package com.moon.webmock.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.clip
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
// ---------------------------------------------------------------------------

private enum class MockSheet { SUBS, AUDIO, EPISODES }

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
)

@Composable
fun PlayerScreen() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (maxWidth < maxHeight) {
            PlayerPortraitNotes()
        } else {
            PlayerLandscapeMock()
        }
    }
}

/** Portrait: what changed + how to try it (the player is landscape-only). */
@Composable
private fun PlayerPortraitNotes() {
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
                    "to seek, and open Subtitles, Audio and Episodes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerLandscapeMock() {
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

    // Fake playback clock.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(250)
            positionMs = (positionMs + 250).coerceAtMost(durationMs)
            if (positionMs >= durationMs) isPlaying = false
        }
    }
    // Auto-play next at the end (unless he hid the NextUp card).
    LaunchedEffect(positionMs) {
        if (positionMs >= durationMs && !nextUpDismissed && episode < 8) {
            nextEpisode()
        }
    }
    // Controls auto-hide 3s after the last interaction (while playing).
    LaunchedEffect(interactNonce, isPlaying, scrubMs) {
        if (isPlaying && controlsVisible && scrubMs == null) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked, sheet) {
                detectTapGestures(
                    onTap = {
                        if (locked) {
                            showUnlock = true
                        } else if (sheet == null) {
                            if (controlsVisible) controlsVisible = false else poke()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!locked && sheet == null) {
                            val forward = offset.x > size.width / 2
                            seekBy(if (forward) 10_000L else -10_000L)
                            seekFeedback = forward to 10
                        }
                    },
                )
            }
            .pointerInput(locked, sheet) {
                // Vertical swipes: left half = brightness, right = volume.
                detectVerticalDragGestures { change, dragAmount ->
                    if (sheet == null && !locked) {
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
            .pointerInput(locked, sheet) {
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
                        if (target != null && sheet == null && !locked) {
                            positionMs = target
                            poke()
                        }
                    },
                    onDragCancel = { swipePreview = null },
                ) { change, dragAmount ->
                    if (sheet == null && !locked) {
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
        if (subTrack != 0) {
            Text(
                subLine,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 4f)),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 132.dp else 36.dp)
                    .padding(horizontal = 48.dp),
            )
        }

        // Top bar: back + title pill (Inter, borderless — NEW) + cluster.
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
                MockPlayerIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = { toast = "Back — closes the player in the app (mock)" },
                )
                Spacer(Modifier.width(12.dp))
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
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MockPlayerIconButton(
                        icon = Icons.Filled.PlaylistPlay,
                        contentDescription = "Episodes",
                        onClick = { sheet = MockSheet.EPISODES },
                    )
                    MockPlayerIconButton(
                        icon = Icons.Filled.PhotoCamera,
                        contentDescription = "Screenshot",
                        onClick = { toast = "Screenshot saved (mock)" },
                    )
                    MockPlayerIconButton(
                        icon = Icons.Filled.ContentCut,
                        contentDescription = "Trim clip",
                        onClick = { toast = "Trimmer opens here — coming to the mockup next" },
                    )
                    MockPlayerIconButton(
                        icon = Icons.Filled.Lock,
                        contentDescription = "Lock controls",
                        onClick = {
                            locked = true
                            controlsVisible = false
                            showUnlock = true
                        },
                    )
                }
            }
        }

        // Center transport: -10s / play-pause / +10s (moon's layout, kept).
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
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

        // Double-tap feedback: low-opacity text only (moon's rule).
        seekFeedback?.let { (forward, secs) ->
            Text(
                if (forward) "+${secs}s" else "-${secs}s",
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

        // Bottom cluster: buttons above the seekbar (kept); the History
        // icon is GONE (NEW) — the floating Resume pill covers that job.
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
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MockPlayerIconButton(
                            icon = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = { sheet = MockSheet.SUBS },
                            active = subTrack != 0,
                        )
                        MockPlayerIconButton(
                            icon = Icons.Filled.Audiotrack,
                            contentDescription = "Audio",
                            onClick = { sheet = MockSheet.AUDIO },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (episode < 8) {
                            MockPlayerIconButton(
                                icon = Icons.Filled.SkipNext,
                                contentDescription = "Next episode",
                                onClick = { nextEpisode() },
                            )
                        }
                        Surface(
                            onClick = { aspectIndex = (aspectIndex + 1) % aspects.size },
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
                }
                Spacer(Modifier.height(4.dp))
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
                    .padding(end = 16.dp, bottom = if (controlsVisible) 104.dp else 28.dp),
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
                    .padding(start = 16.dp, bottom = if (controlsVisible) 104.dp else 28.dp),
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
                    .padding(end = 16.dp, bottom = if (controlsVisible) 168.dp else 88.dp),
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

        // Sheets + drawer.
        AnimatedVisibility(
            visible = sheet == MockSheet.SUBS,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            MockTrackSheet(
                title = "Subtitles",
                options = subTracks,
                selected = subTrack,
                onSelect = { subTrack = it; sheet = null },
                onDismiss = { sheet = null },
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Delay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    MockSmallRoundIcon(
                        Icons.Filled.Remove, "Delay -100ms",
                    ) { subDelayMs = (subDelayMs - 100).coerceAtLeast(-5000) }
                    Text(
                        "${if (subDelayMs >= 0) "+" else ""}${subDelayMs}ms",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MsColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp),
                    )
                    MockSmallRoundIcon(
                        Icons.Filled.Add, "Delay +100ms",
                    ) { subDelayMs = (subDelayMs + 100).coerceAtMost(5000) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { toast = "Load subtitle from phone (mock)" }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MsColors.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Load subtitle from phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                    )
                }
            }
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
