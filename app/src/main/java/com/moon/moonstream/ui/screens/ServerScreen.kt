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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.moon.moonstream.server.ServerTelemetry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.withContext
import java.util.Locale

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
@Composable
private fun ServerCard(on: Boolean, uptimeSecs: Int, onToggle: () -> Unit) {
    // Looping sheen — created only to be read when ON.
    val sheen = rememberInfiniteTransition(label = "goldSheen")
    val sheenX by sheen.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse, // smooth back-and-forth, no jump
        ),
        label = "sheenX",
    )
    // Slow drifting background glow.
    val drift = rememberInfiniteTransition(label = "bgDrift")
    val driftY by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driftY",
    )

    val borderBrush: Brush = if (on) {
        // Bright band sweeps 0 -> 1 across the border; stops are clamped so
        // the gradient stays valid at the sweep edges.
        val c = sheenX
        Brush.linearGradient(
            0.00f to MsColors.GlowDark,
            (c - 0.30f).coerceIn(0.001f, 0.999f) to MsColors.GlowDeep,
            c.coerceIn(0.001f, 0.999f) to MsColors.GlowLight,
            (c + 0.30f).coerceIn(0.001f, 0.999f) to MsColors.GlowDeep,
            1.00f to MsColors.GlowDark,
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF26262C), Color(0xFF26262C)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(borderBrush)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(26.5.dp))
            .clickable(onClick = onToggle),
    ) {
        // Premium ambient background: dark base, soft glow washing down from
        // the top (not a centered ball), plus a thin top-edge highlight.
        // matchParentSize so the layers cover the content Column exactly.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(26.5.dp))
                .background(Color(0xFF131316)),
        ) {
            if (on) {
                // Soft top-down ambient wash (vertical gradient fades evenly —
                // it can't form a concentrated "ball" like a centered radial).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to MsColors.GlowDeep.copy(alpha = 0.30f),
                                (0.30f + driftY * 0.15f) to MsColors.GlowDeep.copy(alpha = 0.12f),
                                (0.65f + driftY * 0.15f) to MsColors.GlowDeep.copy(alpha = 0.03f),
                                1.00f to Color.Transparent,
                            ),
                        ),
                )
                // Thin top highlight line.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.5f to MsColors.GlowLight.copy(alpha = 0.55f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
        if (!on) {
            // NoxMedia-style sleep state: the whole card is the button.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "In Sleep mode",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Doto),
                    color = MsColors.TextSecondary,
                )
            }
        } else {
            // Parent Column so URL sits BELOW content (was Box sibling → overlap).
            Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                // Top row: title left, "sleep" (stop) right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Server Awaked",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = Doto),
                        color = Color.White,
                    )
                    Text(
                        "sleep",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggle)
                            .padding(8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Uptime + speed, NoxMedia-style big Doto numbers.
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatUptime(uptimeSecs),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Doto),
                            color = Color.White,
                        )
                        Text(
                            "uptime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MsColors.TextSecondary,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatBytes(ServerTelemetry.speedBps()) + "/s",
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Doto),
                            color = Color.White,
                        )
                        Text(
                            "speed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MsColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Session / all-time in a darker inner box.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "session ${formatBytes(ServerTelemetry.sessionBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextSecondary,
                        )
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextSecondary,
                        )
                        Text(
                            "all-time ${formatBytes(ServerTelemetry.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextSecondary,
                        )
                    }
                }
            }
            // URL section: darker, pushed below a separator.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            ManifestDrawer()
            } // parent Column
        }
    }
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
        Text(
            "URL: $url",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Doto),
            color = MsColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
 * Continue Watching row: horizontal poster strip with progress bars,
 * under Recently Added (moon's request 2026-11-04).
 */
@Composable
private fun ContinueWatchingRow(
    onOpenDetail: (Long) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<com.moon.moonstream.index.db.WatchProgressEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = com.moon.moonstream.index.db.MsDatabase.get(context)
            // Only items with actual progress (position > 10s, not finished)
            val recent = db.watchProgressDao().recent(15).filter {
                it.positionMs > 10_000L && (it.durationMs <= 0L || it.positionMs < it.durationMs * 0.9f)
            }.take(10)
            withContext(Dispatchers.Main) { items = recent }
        }
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
                        // Progress line at bottom of poster (moon's design)
                        val progress = if (w.durationMs > 0L) {
                            (w.positionMs.toFloat() / w.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        if (progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            ) {
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
                    if (!w.episodeLabel.isNullOrBlank()) {
                        Text(
                            w.episodeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MsColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
