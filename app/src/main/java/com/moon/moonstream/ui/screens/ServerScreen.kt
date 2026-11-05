package com.moon.moonstream.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.moon.moonstream.ui.components.MsToast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.moon.moonstream.server.ServerTelemetry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.Doto
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.doto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Server home screen.
 *
 * One unified server card: gold gradient border (with a single slow sheen
 * sweep while ON — the only looping animation on this screen, so it stays
 * light on battery), the power switch, and — NoxMedia-style — the manifest
 * URL drawer that slides down INSIDE the same card when the server is on.
 * Tapping the manifest row copies the link. Everything below appears only
 * while the server is ON.
 */

/**
 * Signature glow palette for the server card — follows the active accent
 * (MsColors.GlowLight / GlowDeep / GlowDark), so the card's aura matches
 * the theme. Works with AMOLED on or off.
 */

@Composable
fun ServerScreen(
    onOpenLibrary: () -> Unit = {},
    onOpenDetail: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val on = AppUiState.serverOn
    // Uptime comes from ServerTelemetry's start timestamp — surviving navigation,
    // unlike the old remember counter that reset every time the screen recomposed.
    // Tick once a second just to redraw the label.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(on) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    // Uptime ticks every second while the server is on (moon's bug
    // 2026-11-01: uptime stayed 0:00 until leaving and returning to
    // the screen, because uptimeSecs() was read once, not as state).
    var uptimeSecs by remember(on) { mutableIntStateOf(if (on) ServerTelemetry.uptimeSecs() else 0) }
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        while (true) {
            delay(1000)
            uptimeSecs = ServerTelemetry.uptimeSecs()
        }
    }
    // Android 13+ needs the notification permission at runtime; without it the
    // foreground service runs but its notification (with the Stop button) is
    // silently hidden. Ask the moment the user turns the server on.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        AppUiState.toggleServer(context)
        if (!granted) {
            MsToast.show("Server starting — allow notifications to see its status and Stop button.")
        } else {
            MsToast.show("Starting server…")
        }
    }

    val onToggle = {
        // serverOn is written only by the foreground service, so the toggle
        // always reflects the real state — but it flips asynchronously, so
        // base the toast on the intended action, not the stale value.
        val turningOn = !AppUiState.serverOn
        if (turningOn && needsNotifPermission(context)) {
            // Android 13+: without this grant the service's notification is
            // silently suppressed — the exact "no notification" bug.
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AppUiState.toggleServer(context)
            MsToast.show(if (turningOn) "Starting server…" else "Stopping server…")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MoonStream",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )
            MsSectionEyebrow(if (on) "RUNNING" else "IDLE")
        }

        ServerCard(on = on, uptimeSecs = uptimeSecs, onToggle = onToggle)

        // Library + Recently Added: always visible, like NoxMedia —
        // they don't need the server running.
        MsSectionEyebrow("LIBRARY")
        LibraryDashboardCard(onOpenLibrary = onOpenLibrary)

        RecentlyAddedRow(onOpenLibrary = onOpenLibrary, onOpenDetail = onOpenDetail)

        ContinueWatchingRow(onOpenDetail = onOpenDetail)

        Text(
            "Paste the manifest URL into Stremio or Nuvio to browse your library.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        // Room for the floating pill: last content scrolls fully into view.
        Spacer(Modifier.height(106.dp))
    }
}

/** Small caps section eyebrow for the home screen hierarchy. */
@Composable
private fun MsSectionEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MsColors.TextSecondary,
        letterSpacing = 1.5.sp,
    )
}

/**
 * The unified server card.
 *
 * - Outer wrapper draws the gradient border (gold when ON, flat dark when
 *   OFF) via the background+padding trick — no Canvas needed.
 * - When ON, a single slow sheen sweeps across the gold border. This is the
 *   ONLY looping animation on the screen: one animated float driving one
 *   brush, GPU-composited, so it stays cheap on a budget phone.
 * - The manifest drawer slides down inside the same card (separator line +
 *   tap-to-copy row) when the server is on — NoxMedia style.
 */
/**
 * Server card v4 — cinematic "Swipe to awaken" (moon's design 2026-11-04,
 * v4 polish 2026-11-04: soft wipe, URL from behind, progress-tied glow).
 *
 * - Fixed card height (no OFF/ON jump).
 * - SOFT WIPE: the pill is a light edge. ON content fades in left-to-right
 *   as the pill moves (feathered, not hard-clipped — no mid-letter cuts).
 *   OFF content fades out as the pill approaches. No ghosting.
 * - URL is PART OF THE CARD: slides down from behind, rounded bottom
 *   corners, top tucked behind the main card.
 * - Static mesh-style gradient inside. Outline sheen runs only when
 *   waking/wake, intensity tied to swipe progress (0=dark, 1=full glow).
 * - Metrics (uptime/speed/session/total) clean layout, visible when ON.
 * - 70% snap threshold both ways; left 20% magnetic catch. Swipe-only.
 * - States: IDLE → STARTING → RUNNING → STOPPING → IDLE.
 */
private enum class ServerCardState { IDLE, STARTING, RUNNING, STOPPING }

/** Soft horizontal wipe: content fades in left of [revealX], out right of it.
 *  Offscreen compositing isolates the DstIn blend so it can't punch through
 *  to the card background (moon's 2026-11-05: recents thumbnail showed the
 *  wallpaper through the card). */
private fun Modifier.softWipe(revealX: Float, featherPx: Float, widthPx: Float) =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
        drawContent()
        if (widthPx <= 0f) return@drawWithContent
        val startFrac = ((revealX - featherPx) / widthPx).coerceIn(0f, 1f)
        val endFrac = ((revealX + featherPx) / widthPx).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                startFrac to Color.Black,
                endFrac to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Inverse soft wipe: content fades in right of [revealX], out left of it.
 *  Offscreen compositing isolates the DstIn blend (same recents fix). */
private fun Modifier.softWipeInverse(revealX: Float, featherPx: Float, widthPx: Float) =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
        drawContent()
        if (widthPx <= 0f) return@drawWithContent
        val startFrac = ((revealX - featherPx) / widthPx).coerceIn(0f, 1f)
        val endFrac = ((revealX + featherPx) / widthPx).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                startFrac to Color.Transparent,
                endFrac to Color.Black,
                1f to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun ServerCard(on: Boolean, uptimeSecs: Int, onToggle: () -> Unit) {
    val sheen = rememberInfiniteTransition(label = "goldSheen")
    val sheenX by sheen.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sheenX",
    )

    var cardState by remember { mutableStateOf(ServerCardState.IDLE) }
    LaunchedEffect(on) {
        cardState = if (on) ServerCardState.RUNNING else ServerCardState.IDLE
    }

    // Swipe-pill state.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var pillOffsetPx by remember { mutableFloatStateOf(0f) }
    val pillWidthPx = with(density) { 84.dp.toPx() }
    val featherPx = with(density) { 48.dp.toPx() }
    val maxOffset = (trackWidthPx - pillWidthPx).coerceAtLeast(0f)

    // Progress 0f (OFF) → 1f (ON), driven by the pill.
    val progress = if (maxOffset > 0f) (pillOffsetPx / maxOffset).coerceIn(0f, 1f) else 0f
    val widthPxF = trackWidthPx.toFloat()

    // Sync pill if server flips externally (notification Stop button).
    LaunchedEffect(on, maxOffset) {
        if (maxOffset <= 0f) return@LaunchedEffect
        val target = if (on) maxOffset else 0f
        if (pillOffsetPx != target && cardState != ServerCardState.STARTING && cardState != ServerCardState.STOPPING) {
            Animatable(pillOffsetPx).animateTo(target, tween(300)) { pillOffsetPx = value }
        }
    }

    val dragState = rememberDraggableState { delta ->
        pillOffsetPx = (pillOffsetPx + delta).coerceIn(0f, maxOffset)
    }

    // Border: dark when OFF, animated sheen when waking/ON.
    // Intensity follows the swipe progress (moon's point 3).
    val borderBrush: Brush = if (progress > 0.02f) {
        val c = sheenX
        val a = progress.coerceIn(0f, 1f)
        Brush.linearGradient(
            0.00f to MsColors.GlowDark.copy(alpha = a),
            (c - 0.30f).coerceIn(0.001f, 0.999f) to MsColors.GlowDeep.copy(alpha = a),
            c.coerceIn(0.001f, 0.999f) to MsColors.GlowLight.copy(alpha = a),
            (c + 0.30f).coerceIn(0.001f, 0.999f) to MsColors.GlowDeep.copy(alpha = a),
            1.00f to MsColors.GlowDark.copy(alpha = a),
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF26262C), Color(0xFF26262C)))
    }

    // Fixed card height.
    val cardHeight = 240.dp
    // URL visibility (moon's 2026-11-05): part of the card, ONLY visible
    // on pill swipe — appears as the pill moves past halfway, fades in
    // with progress (not a pop-in at 85%).
    val urlVisible = progress > 0.5f || cardState == ServerCardState.RUNNING

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main card (z-index above URL).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(borderBrush)
                .padding(1.5.dp)
                .clip(RoundedCornerShape(26.5.dp))
                .background(Color(0xFF131316)),
        ) {
            // Inner gradient when ON (moon's 2026-11-05: "color effect is only
            // outside" — was too subtle at 0.45/0.15). Fills the card, not just border.
            if (progress > 0.02f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                MsColors.GlowDeep.copy(alpha = 0.65f * progress),
                                MsColors.GlowDeep.copy(alpha = 0.30f * progress),
                                Color.Transparent,
                            ),
                            center = Offset(0.5f, 0.0f),
                            radius = 900f,
                        ),
                    ),
                )
            }

            // The swipe track + URL inside the card (moon's 2026-11-05:
            // old style — URL below a divider, part of the card, not a
            // separate box sliding from behind).
            Column(modifier = Modifier.fillMaxWidth()) {
            // The swipe track.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .onSizeChanged { trackWidthPx = it.width },
            ) {
                if (trackWidthPx > 0) {
                    // OFF layer: fades out as the pill approaches (inverse wipe).
                    // Overall alpha also tied to (1-progress) — no ghosting.
                    // NOTE: "Swipe to awaken" hint is now OUTSIDE this guard (below),
                    // so it shows even if trackWidthPx is 0.
                    if (cardState != ServerCardState.IDLE || progress >= 0.1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .softWipeInverse(pillOffsetPx, featherPx, widthPxF)
                                .alpha((1f - progress).coerceIn(0f, 1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val label = when (cardState) {
                                ServerCardState.STARTING -> "Starting…"
                                ServerCardState.STOPPING -> "Stopping…"
                                else -> null
                            }
                            if (label != null) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Doto),
                                    color = MsColors.TextSecondary,
                                    modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                                )
                            }
                        }
                    }

                    // ON layer: fades in left-to-right as the pill moves (soft wipe).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .softWipe(pillOffsetPx, featherPx, widthPxF)
                            .alpha(progress.coerceIn(0f, 1f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp)
                                .padding(end = 84.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val statusLabel = when (cardState) {
                                ServerCardState.STARTING -> "Server Starting"
                                ServerCardState.STOPPING -> "Server Stopping"
                                else -> "Server Awaked"
                            }
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = Doto),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        formatUptime(uptimeSecs),
                                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Doto),
                                        color = Color.White,
                                    )
                                    Text("uptime", style = MaterialTheme.typography.bodySmall, color = MsColors.TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        formatBytes(ServerTelemetry.averageSpeedBps()) + "/s",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Doto),
                                        color = Color.White,
                                    )
                                    Text("speed", style = MaterialTheme.typography.bodySmall, color = MsColors.TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // Single-line pill (moon's 2026-11-05: old style —
                            // "session 0 B · all-time 1022 MB" on one line, centered).
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "session ${formatBytes(ServerTelemetry.sessionBytes)}  ·  all-time ${formatBytes(ServerTelemetry.totalBytes)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // The pill — the light edge.
                // Color blends slowly from white to accent (moon's point 4).
                val pillColor = androidx.compose.ui.graphics.lerp(
                    Color.White.copy(alpha = 0.92f),
                    MsColors.Accent,
                    progress.coerceIn(0f, 1f),
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillOffsetPx.roundToInt(), 0) }
                        .width(84.dp)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(pillColor)
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                val fraction = if (maxOffset > 0f) pillOffsetPx / maxOffset else 0f
                                scope.launch {
                                    if (cardState == ServerCardState.IDLE) {
                                        if (fraction >= 0.7f) {
                                            cardState = ServerCardState.STARTING
                                            Animatable(pillOffsetPx).animateTo(maxOffset, tween(300)) { pillOffsetPx = value }
                                            onToggle()
                                        } else {
                                            val duration = if (fraction <= 0.2f) 150 else 300
                                            Animatable(pillOffsetPx).animateTo(0f, tween(duration)) { pillOffsetPx = value }
                                        }
                                    } else if (cardState == ServerCardState.RUNNING) {
                                        if (fraction <= 0.3f) {
                                            cardState = ServerCardState.STOPPING
                                            Animatable(pillOffsetPx).animateTo(0f, tween(300)) { pillOffsetPx = value }
                                            onToggle()
                                        } else {
                                            Animatable(pillOffsetPx).animateTo(maxOffset, tween(300)) { pillOffsetPx = value }
                                        }
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // > icon that rotates to < when ON (moon's point 2).
                    // Rotation follows the swipe progress: 0° (>) → 180° (<).
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
                        contentDescription = if (progress > 0.5f) "Swipe to stop" else "Swipe to awaken",
                        tint = if (progress > 0.5f) Color.White else Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                rotationZ = progress * 180f
                            },
                    )
                }
            }

            // Swipe hint overlay (moon's 2026-11-05): shown when IDLE, outside the
            // trackWidthPx guard so it always appears.
            // Uses softWipeInverse (like the ON text uses softWipe) so it wipes
            // progressively as the pill covers it — NOT a whole-alpha fade.
            // Arrow removed (moon's 2026-11-05: Doto doesn't render it, text only).
            if (cardState == ServerCardState.IDLE || cardState == ServerCardState.STARTING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 100.dp, end = 16.dp)
                        .softWipeInverse(pillOffsetPx, featherPx, widthPxF)
                        .alpha((1f - progress).coerceIn(0f, 1f)),
                ) {
                    Text(
                        "Swipe to awaken",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = Doto),
                        color = MsColors.TextPrimary,
                    )
                }
            }
        }

            // URL row INSIDE the card, below a divider (old style).
            // Shows when ON, fades in — no slide-from-behind.
            AnimatedVisibility(
                visible = urlVisible,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = progress.coerceIn(0f, 1f) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    ManifestDrawer()
                }
            }
            } // end Column (track + URL)
    }
}


/**
 * URL text with a fast scramble/decode animation (moon's request 2026-11-04):
 * random chars resolve left-to-right into the real URL, ~600ms.
 */
@Composable
private fun ScrambleUrlText(url: String, modifier: Modifier = Modifier) {
    var display by remember { mutableStateOf(url) }
    LaunchedEffect(url) {
        if (url.isEmpty()) { display = ""; return@LaunchedEffect }
        // Scramble ALL chars from "http:" onward (moon's 2026-11-05: the old
        // version kept : / . fixed, so only the numbers scrambled — and it
        // was too fast at 10x60ms). Now 14 steps x 100ms = 1.4s, every char
        // randomizes until its turn to resolve.
        val pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val steps = 14
        repeat(steps) { step ->
            val progress = (step + 1) / steps.toFloat()
            val resolved = (url.length * progress).toInt()
            display = buildString {
                url.forEachIndexed { i, c ->
                    if (i < resolved) append(c) else append(pool.random())
                }
            }
            delay(100)
        }
        display = url
    }
    Text(
        "URL: $display",
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Doto),
        color = MsColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The manifest URL row. The WHOLE row is tappable and copies the link
 * (not just an icon) — a short gold flash confirms the copy.
 */
@Composable
private fun ManifestDrawer() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val url = AppUiState.manifestLink
    var copied by remember { mutableStateOf(false) }
    val flash by animateColorAsState(
        targetValue = if (copied) MsColors.GlowLight.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(250),
        label = "copyFlash",
    )
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f))
            .background(flash)
            .clickable {
                clipboard.setText(AnnotatedString(url))
                copied = true
                MsToast.show("Manifest URL copied")
            }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrambleUrlText(url = url, modifier = Modifier.weight(1f))
        // NoxMedia-style "copied" feedback (moon's request 2026-11-01).
        AnimatedVisibility(
            visible = copied,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            Text(
                "copied",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.GlowLight,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (!copied) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy URL",
                tint = MsColors.TextSecondary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}


private data class LibraryStats(
    val titles: Int = 0,
    val files: Int = 0,
    val sizeBytes: Long = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0,
)

/**
 * Library dashboard card: purple gradient with a gradient outline, fed by
 * REAL Room stats (no more fake numbers). The count-up runs once on load —
 * no looping animation, so it costs nothing after the first second.
 */
@Composable
private fun LibraryDashboardCard(onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(LibraryStats()) }
    var latest by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val db = MsDatabase.get(context)
            val movies = db.mediaDao().countByType("movie")
            val series = db.mediaDao().countByType("series")
            val anime = db.mediaDao().countByType("anime")
            LibraryStats(
                titles = db.mediaDao().countAll(),
                files = db.streamDao().countAll(),
                sizeBytes = db.streamDao().totalSize(),
                movies = movies,
                shows = series + anime,
                episodes = db.mediaDao().episodeTotal(),
            ) to db.mediaDao().latestTitles()
        }
        stats = loaded.first
        latest = loaded.second
    }

    // One-shot count-ups toward the real values.
    val titles = remember { Animatable(0f) }
    val files = remember { Animatable(0f) }
    LaunchedEffect(stats) {
        if (stats.titles > 0 || stats.files > 0) {
            titles.animateTo(stats.titles.toFloat(), tween(1100))
            files.animateTo(stats.files.toFloat(), tween(1100))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                // Richer "mesh-like" gradient: layered radial glows over a
                // linear base (moon's request 2026-11-03). True mesh gradients
                // aren't in Compose, this is the closest.
                Brush.linearGradient(
                    listOf(
                        MsColors.GlowLight,
                        MsColors.GlowDeep,
                        MsColors.GlowDeep.copy(alpha = 0.7f),
                        MsColors.Bg,
                    ),
                ),
            )
            .clickable(onClick = onOpenLibrary)
            .padding(1.5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.5.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MsColors.GlowDeep.copy(alpha = 0.75f),
                            MsColors.GlowDeep.copy(alpha = 0.35f),
                            MsColors.Bg,
                            MsColors.Bg,
                        ),
                        center = androidx.compose.ui.geometry.Offset(0.15f, 0.1f),
                        radius = 900f,
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.titleMedium.doto(),
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge.doto(),
                            fontWeight = FontWeight.Light,
                            color = MsColors.TextSecondary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text(
                        text = formatBytes(stats.sizeBytes),
                        style = MaterialTheme.typography.bodyMedium.doto(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatCount(files.value.toInt()),
                        style = MaterialTheme.typography.displaySmall.doto(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "files",
                        style = MaterialTheme.typography.bodyMedium.doto(),
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${formatCount(titles.value.toInt())} titles",
                        style = MaterialTheme.typography.bodyMedium.doto(),
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                // Breakdown owns the FULL width now — nothing competes with it.
                Text(
                    text = "${formatCount(stats.movies)} Movies  ·  " +
                        "${formatCount(stats.shows)} Shows  ·  " +
                        "${formatCount(stats.episodes)} Ep",
                    style = MaterialTheme.typography.bodySmall.doto(),
                    color = MsColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Latest indexed titles — one line, NoxMedia-style.
                if (latest.isNotEmpty()) {
                    Text(
                        text = "New: " + latest.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun formatCount(n: Int): String =
    String.format(Locale.US, "%,d", n)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb)
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.0f KB", kb)
}

private fun formatUptime(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun needsNotifPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

/**
 * Recently Added row: horizontal poster strip, always visible (like NoxMedia).
 */
@Composable
private fun RecentlyAddedRow(
    onOpenLibrary: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<com.moon.moonstream.index.db.MediaItemEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = com.moon.moonstream.index.db.MsDatabase.get(context)
            val recent = db.mediaDao().listVisible().take(10)
            withContext(Dispatchers.Main) { items = recent }
        }
    }
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recently Added",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Doto),
                color = MsColors.TextPrimary,
            )
            Text(
                "view all →",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenLibrary)
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { m ->
                Column(modifier = Modifier.width(110.dp)) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MsColors.SurfaceAlt)
                            .clickable(onClick = { onOpenDetail(m.id) }),
                    ) {
                        if (!m.posterUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = m.posterUrl,
                                contentDescription = m.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    m.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = Doto),
                                    color = MsColors.TextSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${m.year ?: ""} · ${formatBytes(m.totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.Accent.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Continue Watching row: horizontal poster strip with a progress line and an
 * SxxExx chip on the poster, under Recently Added (moon's request 2026-11-04).
 *
 * Fixed 2026-11-05 — see docs/FIXES-2026-11-05.md:
 * - LIVE data: collected from Room as a Flow. The old version reloaded only on
 *   the *Activity's* ON_RESUME, which never fires here (screens are swapped
 *   inside one Activity) — the strip could stay stale or empty after watching.
 * - One poster per SHOW, not per file (E01 + E02 no longer both appear).
 * - Poster/title come from a JOIN, so a re-index can't leave a blank tile.
 * - SxxExx moved onto the poster (top-left chip), as designed.
 * - Progress line always has a visible track, even before duration is known.
 */
@Composable
private fun ContinueWatchingRow(
    onOpenDetail: (Long) -> Unit,
) {
    val context = LocalContext.current
    val db = remember(context) { com.moon.moonstream.index.db.MsDatabase.get(context) }
    val rows by remember(db) {
        db.watchProgressDao().observeContinueWatching()
    }.collectAsState(initial = emptyList())

    // Started (>10s) and not finished (<90%), newest file per show, 10 max.
    // Rows arrive playedAt DESC, so distinctBy keeps the most recent file.
    val items = remember(rows) {
        // DIAGNOSTIC (moon's 2026-11-05): row exists in DB but not showing
        com.moon.moonstream.util.AppLog.i("CW", "Flow returned ${rows.size} rows")
        rows.forEach {
            com.moon.moonstream.util.AppLog.i("CW", "Row: mediaId=${it.mediaId} pos=${it.positionMs} dur=${it.durationMs} title='${it.title}'")
        }
        val filtered = rows.filter {
            it.positionMs > 10_000L && (it.durationMs <= 0L || it.positionMs < it.durationMs * 0.9f)
        }.distinctBy { it.mediaId }.take(10)
        com.moon.moonstream.util.AppLog.i("CW", "After filter: ${filtered.size} items")
        filtered
    }
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Continue watching",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Doto),
            color = MsColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { w ->
                Column(modifier = Modifier.width(110.dp)) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MsColors.SurfaceAlt)
                            .clickable(onClick = { onOpenDetail(w.mediaId) }),
                    ) {
                        if (!w.posterUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = w.posterUrl,
                                contentDescription = w.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    w.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = Doto),
                                    color = MsColors.TextSecondary,
                                )
                            }
                        }
                        // SxxExx chip, top-left of the poster (moon's design
                        // 2026-11-04; it used to be a grey line under the
                        // title, where it was easy to miss).
                        val ep = w.episodeLabel
                        if (!ep.isNullOrBlank()) {
                            Text(
                                ep,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                        // Progress line at the bottom of the poster. Track is
                        // white 0.30 alpha so it's visible on dark posters
                        // (moon's 2026-11-05: black track was invisible).
                        // 4dp thick, accent fill shows watched fraction.
                        val progress = if (w.durationMs > 0L) {
                            (w.positionMs.toFloat() / w.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.30f)),
                        ) {
                            if (progress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(MsColors.Accent),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        w.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
