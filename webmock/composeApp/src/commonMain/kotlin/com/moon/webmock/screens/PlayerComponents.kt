package com.moon.webmock.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.webmock.components.LimePillButton
import com.moon.webmock.components.MsOutlinePill
import com.moon.webmock.theme.MsColors
import com.moon.webmock.theme.doto
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Player mockup components (moon's 2026-11-14: improve the player UI in the
// webmock first; the app only changes if he likes it here). Ports of the
// app's PlayerScreen pieces, with the redesign deltas marked NEW.
// ---------------------------------------------------------------------------

/** mm:ss (or h:mm:ss). Manual padding — String.format isn't in common code. */
fun mockFormatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun two(n: Long) = if (n < 10) "0$n" else "$n"
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}

/**
 * iOS-style press shrink (NEW): the button scales to 0.9 while pressed.
 * Tracks presses WITHOUT consuming them (requireUnconsumed = false, never
 * consume), so the real click underneath still fires.
 */
@Composable
fun Modifier.mockPressScale(): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var up = false
                    while (!up) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: continue
                        if (!change.pressed) up = true
                    }
                    pressed = false
                }
            }
        }
}

/** Port of the app's PlayerIconButton (45dp circle, white 12% + border). */
@Composable
fun MockPlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 45.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.size(size).mockPressScale(),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) MsColors.Accent
                    else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** A skippable range on the seekbar (intro/outro). */
data class MockSeekSegment(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val color: Color,
)

/**
 * The app's MoonSeekBar, plus the two redesign deltas (NEW):
 * 1. Segment markers — intro/outro ranges drawn as tinted blocks ON the
 *    bar, so he can see them before scrubbing.
 * 2. Scrub bubble — while dragging, the target time floats above the
 *    thumb (with the segment name when inside one), instead of only the
 *    small bottom-left timer changing.
 * Same behaviour as the app otherwise: 4dp at rest, 10dp while scrubbing,
 * played/buffered/remaining shades, NO thumb dot (moon's rule), seek
 * lands once on finger lift.
 */
@Composable
fun MockSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferFrac: Float,
    segments: List<MockSeekSegment>,
    onSeek: (Long) -> Unit,
    onScrubChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    /** Trimmer selection: the marked clip, drawn as an Accent band. */
    trimStartMs: Long? = null,
    trimEndMs: Long? = null,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val barHeight by animateDpAsState(
        targetValue = if (isScrubbing) 10.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "seekBarHeight",
    )
    val posFrac = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    val displayFrac = if (isScrubbing && scrubMs != null && durationMs > 0L) {
        (scrubMs!!.toFloat() / durationMs).coerceIn(0f, 1f)
    } else posFrac
    val scrubSeg = scrubMs?.let { ms ->
        segments.firstOrNull { ms in it.startMs until it.endMs }
    }
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(durationMs) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        isScrubbing = true
                        down.consume()
                        val w = size.width.toFloat()
                        fun targetMs(x: Float): Long? {
                            if (w <= 0f || durationMs <= 0L) return null
                            return ((x / w).coerceIn(0f, 1f) * durationMs).toLong()
                        }
                        scrubMs = targetMs(down.position.x)
                        onScrubChange(scrubMs)
                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: continue
                            if (!change.pressed) {
                                dragging = false
                                scrubMs?.let { onSeek(it) }
                            } else {
                                change.consume()
                                scrubMs = targetMs(change.position.x)
                                onScrubChange(scrubMs)
                            }
                        }
                        scrubMs = null
                        onScrubChange(null)
                        isScrubbing = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Base track (remaining).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.20f)),
        )
        // Buffer fill.
        if (bufferFrac > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(bufferFrac.coerceIn(0f, 1f))
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.62f)),
            )
        }
        // Played fill (follows the scrub preview while dragging).
        if (displayFrac > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(displayFrac)
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MsColors.Accent),
            )
        }
        // Segment markers (NEW): drawn above the played fill so they stay
        // visible even after the segment has played.
        segments.forEach { seg ->
            if (durationMs > 0L && seg.endMs > seg.startMs) {
                val startFrac = (seg.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                val endFrac = (seg.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
                if (endFrac > startFrac) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(endFrac - startFrac)
                            .offset {
                                IntOffset(
                                    (startFrac * constraints.maxWidth).roundToInt(), 0
                                )
                            }
                            .height(barHeight + 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(seg.color.copy(alpha = 0.9f)),
                    )
                }
            }
        }
        // Trimmer range band (port of the app's): the marked clip, drawn
        // above everything so the cut is visible even before playback.
        if (durationMs > 0L && trimStartMs != null && trimEndMs != null && trimEndMs > trimStartMs) {
            val startFrac = (trimStartMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFrac = (trimEndMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (endFrac > startFrac) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(endFrac - startFrac)
                        .offset {
                            IntOffset(
                                (startFrac * constraints.maxWidth).roundToInt(), 0
                            )
                        }
                        .height(barHeight + 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MsColors.Accent.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50)),
                )
            }
        }
        // Scrub bubble (NEW): target time above the thumb.
        if (isScrubbing && scrubMs != null && durationMs > 0L) {
            val frac = (scrubMs!!.toFloat() / durationMs).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val maxW = constraints.maxWidth
                        val bw = 76.dp.toPx()
                        val x = (frac * maxW - bw / 2)
                            .coerceIn(0f, (maxW - bw).coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), (-54.dp).toPx().roundToInt())
                    }
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        mockFormatTime(scrubMs!!),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (scrubSeg != null) {
                        Text(
                            scrubSeg.label,
                            color = scrubSeg.color,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating capsule pill (NEW): Skip Intro / Resume as a soft translucent
 * capsule with an icon — the app's is a solid Accent block button.
 */
@Composable
fun MockSkipPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = modifier.mockPressScale(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Gesture HUD, redesigned (NEW): a slim horizontal pill (icon + thin bar
 * + %) that sits just under the top bar — the app's is a big vertical bar
 * pinned to the screen edge.
 */
@Composable
fun MockGestureHud(
    icon: ImageVector,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MsColors.Accent, RoundedCornerShape(50)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${(fraction * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.width(36.dp),
        )
    }
}

/** Stand-in "video": a night scene (gradient + moon + hills). */
@Composable
fun MockVideoScene() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2A4A), Color(0xFF0A0E1A), Color(0xFF05070D)),
                ),
            ),
    ) {
        // Moon glow + moon.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 36.dp, end = 56.dp)
                .size(140.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 92.dp)
                .size(68.dp)
                .background(Color(0xFFE8ECF5), CircleShape),
        )
        // Hills.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = 60.dp)
                .size(420.dp, 180.dp)
                .background(Color(0xFF070B14), RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = 80.dp)
                .size(380.dp, 160.dp)
                .background(Color(0xFF0A0F1D), RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)),
        )
    }
}

/** Small round icon button for sheet rows (delay stepper). */
@Composable
fun MockSmallRoundIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        modifier = Modifier.size(32.dp).mockPressScale(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * iOS bottom sheet for Subtitles / Audio: drag handle, title, track rows
 * with a check on the selected one, optional extra content (delay stepper,
 * load-from-phone row). Backdrop tap dismisses.
 */
@Composable
fun MockTrackSheet(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MsColors.Surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MockSmallRoundIcon(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { i, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == selected) MsColors.Accent else MsColors.TextPrimary,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (i == selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MsColors.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            extra?.invoke()
        }
    }
}

/** Right-side episodes drawer (the app's EPISODES drawer, mock data). */
@Composable
fun MockEpisodesDrawer(
    episodes: List<String>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .background(MsColors.Surface)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MockSmallRoundIcon(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Season 1 · Alice in Borderland",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                itemsIndexed(episodes) { i, label ->
                    val isCurrent = i == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MsColors.Accent.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                            .clickable { onSelect(i) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp, 64.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1B2A4A), Color(0xFF05070D)),
                                    ),
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "E${i + 1}",
                                style = MaterialTheme.typography.titleSmall.doto(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) MsColors.Accent else MsColors.TextPrimary,
                            )
                            Text(
                                "24:00",
                                style = MaterialTheme.typography.bodySmall,
                                color = MsColors.TextSecondary,
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Playing",
                                tint = MsColors.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * NextUp card, restyled (NEW): poster thumb + cleaner layout + accent
 * "Play now" pill. The app's is text-only with a TextButton + Button.
 */
@Composable
fun MockNextUpCard(
    episodeLabel: String,
    secsLeft: Int,
    onPlayNow: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MsColors.Surface,
        border = BorderStroke(1.dp, MsColors.CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp, 72.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1B2A4A), Color(0xFF05070D)),
                        ),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "A",
                    style = MaterialTheme.typography.titleLarge.doto(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
                Text(
                    episodeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    "Starting in ${secsLeft}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onPlayNow,
                        shape = RoundedCornerShape(50),
                        color = MsColors.Accent,
                        modifier = Modifier.mockPressScale(),
                    ) {
                        Text(
                            "Play now",
                            color = MsColors.OnAccent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Hide",
                        color = MsColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onHide() },
                    )
                }
            }
        }
    }
}

/**
 * Slide-to-unlock pill (the app's UnlockSlider, simplified for the
 * mockup): drag the thumb right past 70% to unlock, else it springs back.
 */
@Composable
fun MockUnlockSlider(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val travel = remember(widthPx, density) {
        (widthPx - with(density) { 56.dp.toPx() }).coerceAtLeast(0f)
    }
    Box(
        modifier = modifier
            .width(280.dp)
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .onGloballyPositioned { widthPx = it.size.width.toFloat() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Slide to unlock",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(4.dp)
                .size(48.dp)
                .background(Color.White, CircleShape)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (travel > 0f && offsetX > travel * 0.7f) onUnlock()
                            else offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(0f, travel)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LockOpen,
                contentDescription = "Unlock",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Trimmer drawer (moon's 2026-11-14: the missing piece). Port of the app's
// TrimDrawer: Start/End steppers, Set start/end, dual-handle range slider,
// loop preview, Reset, Save Clip. Sits full-height on the right.
// ---------------------------------------------------------------------------

@Composable
fun MockTrimDrawer(
    startMs: Long?,
    endMs: Long?,
    currentMs: Long,
    durationMs: Long,
    saving: Boolean,
    progress: Float,
    looping: Boolean,
    stepIsFrame: Boolean,
    onToggleStep: () -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onJumpToStart: () -> Unit,
    onJumpToEnd: () -> Unit,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRangeChangeFinished: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onToggleLoop: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = startMs != null && endMs != null && endMs > startMs
    val lengthMs = if (valid) endMs!! - startMs!! else 0L
    val durF = durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue: ClosedFloatingPointRange<Float> =
        (startMs?.toFloat() ?: 0f).coerceIn(0f, durF)..(endMs?.toFloat() ?: durF).coerceIn(0f, durF)
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Header: scissors + title + step toggle + close.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ContentCut,
                contentDescription = null,
                tint = MsColors.Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Trim Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            MsOutlinePill(
                text = if (stepIsFrame) "Step: 1f" else "Step: 1s",
                onClick = onToggleStep,
                fontSize = 12,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            MockSmallRoundIcon(Icons.Filled.Close, "Close trim") { onClose() }
        }
        Spacer(Modifier.height(10.dp))
        // Start / End steppers: < time > each; the < > nudge by the
        // header step (1s or 1 frame) and jump the video there.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MockTrimMarkBox(
                label = "Start",
                timeMs = startMs,
                onNudge = onNudgeStart,
                onJump = onJumpToStart,
                modifier = Modifier.weight(1f),
            )
            MockTrimMarkBox(
                label = "End",
                timeMs = endMs,
                onNudge = onNudgeEnd,
                onJump = onJumpToEnd,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Set start / Set end: capture the playhead into each mark.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MsOutlinePill(
                text = "Set start",
                onClick = onSetStart,
                fontSize = 13,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            )
            MsOutlinePill(
                text = "Set end",
                onClick = onSetEnd,
                fontSize = 13,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Dual-handle range slider: drag either handle to move that mark.
        RangeSlider(
            value = sliderValue,
            onValueChange = onRangeChange,
            onValueChangeFinished = onRangeChangeFinished,
            valueRange = 0f..durF,
            enabled = durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MsColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (valid) "Duration: ${mockFormatTime(lengthMs)}  ·  Now: ${mockFormatTime(currentMs)}"
            else "Mark a start and an end  ·  Now: ${mockFormatTime(currentMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MsColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        if (saving) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MsColors.Accent,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Saving clip… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MsColors.TextSecondary,
            )
        } else {
            if (looping) {
                LimePillButton(
                    text = "Stop preview",
                    onClick = onToggleLoop,
                    fontSize = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MsOutlinePill(
                    text = "Preview",
                    onClick = onToggleLoop,
                    fontSize = 14,
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsOutlinePill(
                    text = "Reset",
                    onClick = onClear,
                    fontSize = 14,
                    modifier = Modifier.weight(1f),
                )
                LimePillButton(
                    text = "Save Clip",
                    onClick = onSave,
                    fontSize = 14,
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One Start/End stepper box: < time > with the label above the time. */
@Composable
private fun MockTrimMarkBox(
    label: String,
    timeMs: Long?,
    onNudge: (Int) -> Unit,
    onJump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1C1C1E),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MockTrimNudgeButton(
                icon = Icons.Filled.ChevronLeft,
                contentDescription = "Move $label earlier",
                enabled = timeMs != null,
                onClick = { onNudge(-1) },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = timeMs != null) { onJump() },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
                Text(
                    timeMs?.let { mockFormatTime(it) } ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (timeMs != null) MsColors.Accent else MsColors.TextSecondary,
                )
            }
            MockTrimNudgeButton(
                icon = Icons.Filled.ChevronRight,
                contentDescription = "Move $label later",
                enabled = timeMs != null,
                onClick = { onNudge(1) },
            )
        }
    }
}

/** Small round nudge button: dimmed and inert until its mark is set. */
@Composable
private fun MockTrimNudgeButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Full subtitle sheet (moon's 2026-11-14): tracks + Load from phone + All
// lines + Delay (±250ms pills) + Appearance (Size slider, Stroke On/Off,
// Text/Stroke color dots, Position slider). Port of the app's SUBS drawer
// content, kept as a bottom sheet — it scrolls, since landscape is short.
// The appearance controls drive the mock subtitle line live.
// ---------------------------------------------------------------------------

/** One color dot in the Text/Stroke color rows. */
@Composable
private fun MockColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = BorderStroke(
            2.dp,
            if (selected) MsColors.Accent else Color.White.copy(alpha = 0.25f),
        ),
        modifier = Modifier.size(28.dp).mockPressScale(),
    ) {}
}

/** Small On/Off option pill (Stroke row). */
@Composable
private fun MockSheetOptionBtn(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MsColors.Accent else Color.White.copy(alpha = 0.10f),
        modifier = Modifier.mockPressScale(),
    ) {
        Text(
            label,
            color = if (selected) MsColors.OnAccent else MsColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** iOS slider (the app's MsSlider): white thumb, Accent track. */
@Composable
private fun MockMsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = MsColors.Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
        ),
        modifier = modifier,
    )
}

@Composable
fun MockSubtitleSheet(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLoadFromPhone: () -> Unit,
    onAllLines: () -> Unit,
    delayMs: Int,
    onAdjustDelay: (Int) -> Unit,
    textSize: Float,
    onTextSize: (Float) -> Unit,
    strokeOn: Boolean,
    onStrokeOn: (Boolean) -> Unit,
    textColor: Color,
    onTextColor: (Color) -> Unit,
    strokeColor: Color,
    onStrokeColor: (Color) -> Unit,
    position: Float,
    onPosition: (Float) -> Unit,
) {
    fun formatDelay(ms: Int): String {
        if (ms == 0) return "0ms"
        val sign = if (ms > 0) "+" else "−"
        return "$sign${kotlin.math.abs(ms)}ms"
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(
                    MsColors.Surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MockSmallRoundIcon(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(options.size) { i ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(i) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            options[i],
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (i == selected) MsColors.Accent else MsColors.TextPrimary,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (i == selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MsColors.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLoadFromPhone() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Load from phone…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAllLines() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "All lines…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Delay: ±250ms pills (app parity).
                item {
                    Spacer(Modifier.height(4.dp))
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
                        MsOutlinePill(
                            text = "−250ms",
                            onClick = { onAdjustDelay(-250) },
                            fontSize = 13,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            formatDelay(delayMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(80.dp),
                        )
                        MsOutlinePill(
                            text = "+250ms",
                            onClick = { onAdjustDelay(250) },
                            fontSize = 13,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MockMsSlider(
                            value = textSize.coerceIn(0.03f, 0.10f),
                            onValueChange = onTextSize,
                            valueRange = 0.03f..0.10f,
                            modifier = Modifier.weight(1.6f),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Stroke",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MockSheetOptionBtn("On", strokeOn) { onStrokeOn(true) }
                        Spacer(Modifier.width(8.dp))
                        MockSheetOptionBtn("Off", !strokeOn) { onStrokeOn(false) }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Text color",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MockColorDot(Color.White, textColor == Color.White) {
                            onTextColor(Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        MockColorDot(Color.Yellow, textColor == Color.Yellow) {
                            onTextColor(Color.Yellow)
                        }
                        Spacer(Modifier.width(10.dp))
                        MockColorDot(Color(0xFF00E5FF), textColor == Color(0xFF00E5FF)) {
                            onTextColor(Color(0xFF00E5FF))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Stroke color",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MockColorDot(Color.Black, strokeColor == Color.Black) {
                            onStrokeColor(Color.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        MockColorDot(Color.White, strokeColor == Color.White) {
                            onStrokeColor(Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        MockColorDot(Color.Yellow, strokeColor == Color.Yellow) {
                            onStrokeColor(Color.Yellow)
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Position",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MockMsSlider(
                            value = position,
                            onValueChange = onPosition,
                            valueRange = 0f..0.4f,
                            modifier = Modifier.weight(1.6f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Subtitle lines (the app's SUBLINES view): jump to a moment by dialogue.
// ---------------------------------------------------------------------------

data class MockSubLine(val timeMs: Long, val text: String)

@Composable
fun MockSubLinesSheet(
    lines: List<MockSubLine>,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(
                    MsColors.Surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Subtitle lines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MockSmallRoundIcon(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(lines) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onJump(line.timeMs) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            mockFormatTime(line.timeMs),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MsColors.Accent,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Playlist drawer (the app's PLAYLIST drawer): all streams of the episode.
// ---------------------------------------------------------------------------

data class MockPlaylistEntry(val label: String, val size: String)

@Composable
fun MockPlaylistDrawer(
    entries: List<MockPlaylistEntry>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .background(MsColors.Surface)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                MockSmallRoundIcon(Icons.Filled.Close, "Close") { onDismiss() }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Streams of this episode",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                itemsIndexed(entries) { i, entry ->
                    val isCurrent = i == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(i) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MsColors.Accent else MsColors.TextPrimary,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                entry.size,
                                style = MaterialTheme.typography.labelSmall,
                                color = MsColors.TextSecondary,
                            )
                        }
                        if (isCurrent) {
                            Text(
                                "Playing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MsColors.Accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

