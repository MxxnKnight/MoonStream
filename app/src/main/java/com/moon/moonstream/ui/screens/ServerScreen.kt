package com.moon.moonstream.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.moon.moonstream.ui.components.PowerSwitch
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
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

// Gold palette for the server card (matches moon's reference).
private val GoldLight = Color(0xFFFFD27D)
private val Gold = Color(0xFFFFAA33)
private val GoldDeep = Color(0xFF8A5A18)
private val GoldDark = Color(0xFF5C3F12)

@Composable
fun ServerScreen() {
    val context = LocalContext.current
    val on = AppUiState.serverOn
    var uptimeSecs by remember { mutableStateOf(0) }

    LaunchedEffect(on) {
        if (on) {
            while (true) {
                delay(1000)
                uptimeSecs++
            }
        } else {
            uptimeSecs = 0
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
            Toast.makeText(
                context,
                "Server starting — allow notifications to see its status and Stop button.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(context, "Starting server…", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(
                context,
                if (turningOn) "Starting server…" else "Stopping server…",
                Toast.LENGTH_SHORT,
            ).show()
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

        // Everything below only exists while the server is ON.
        AnimatedVisibility(
            visible = on,
            enter = fadeIn(tween(300)) + expandVertically(tween(350)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(300)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ServerStatsCard(uptimeSecs = uptimeSecs)

                MsSectionEyebrow("LIBRARY")
                LibraryDashboardCard()

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
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheenX",
    )

    val borderBrush: Brush = if (on) {
        // Bright band sweeps 0 -> 1 across the border; stops are clamped so
        // the gradient stays valid at the sweep edges.
        val c = sheenX
        Brush.linearGradient(
            0.00f to GoldDark,
            (c - 0.30f).coerceIn(0.001f, 0.999f) to GoldDeep,
            c.coerceIn(0.001f, 0.999f) to GoldLight,
            (c + 0.30f).coerceIn(0.001f, 0.999f) to GoldDeep,
            1.00f to GoldDark,
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF26262C), Color(0xFF26262C)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(borderBrush)
            .padding(1.5.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.5.dp))
                .background(
                    if (on) {
                        Brush.radialGradient(
                            0.0f to Color(0xFF2B2113),
                            0.7f to Color(0xFF17150F),
                            1.0f to Color(0xFF131316),
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color(0xFF15151B), Color(0xFF15151B)),
                        )
                    },
                ),
        ) {
            // --- Server section ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (on) Gold else Color(0xFFF44336), CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (on) "Server ON" else "Server OFF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) "Running · ${formatUptime(uptimeSecs)}" else "Tap the switch to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                Spacer(Modifier.height(22.dp))

                PowerSwitch(on = on, onToggle = onToggle)

                Spacer(Modifier.height(10.dp))
                Text(
                    "Port ${AppUiState.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }

            // --- Manifest drawer: slides down inside the same card ---
            AnimatedVisibility(
                visible = on,
                enter = expandVertically(tween(320)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(280)) + fadeOut(tween(200)),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    ManifestDrawer()
                }
            }
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
        targetValue = if (copied) GoldLight.copy(alpha = 0.14f) else Color.Transparent,
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
            .background(flash)
            .clickable {
                clipboard.setText(AnnotatedString(url))
                copied = true
                Toast.makeText(context, "Manifest URL copied", Toast.LENGTH_SHORT).show()
            }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "MANIFEST URL",
                style = MaterialTheme.typography.labelSmall,
                color = MsColors.TextSecondary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E24))
                .padding(8.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy manifest URL",
                tint = if (copied) Color(0xFF22C55E) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Server telemetry card (ON only), NoxMedia-style information architecture:
 * two large metrics (uptime, speed), a session/all-time shelf.
 * All values fake for now.
 * TODO(Phase 4): feed from the real server telemetry.
 */
@Composable
private fun ServerStatsCard(uptimeSecs: Int) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargeMetric(value = formatUptime(uptimeSecs), label = "uptime", modifier = Modifier.weight(1f))
                LargeMetric(value = "0 B/s", label = "speed", modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MsColors.Bg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallMetric(label = "session", value = "0.0 MB")
                Box(
                    Modifier
                        .size(3.dp)
                        .background(MsColors.Divider, CircleShape),
                )
                SmallMetric(label = "all-time", value = "1.33 GB")
            }
        }
    }
}

@Composable
private fun LargeMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }
}

@Composable
private fun SmallMetric(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MsColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextPrimary,
        )
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
private fun LibraryDashboardCard() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(LibraryStats()) }

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
            )
        }
        stats = loaded
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
                Brush.linearGradient(
                    listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95), Color(0xFF2A1A5E)),
                ),
            )
            .padding(1.5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.5.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF5B21B6), Color(0xFF3B0F8A), Color(0xFF1E1B4B)),
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF3E8FF),
                        )
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Light,
                            color = Color(0xCCE9D5FF),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text(
                        text = formatBytes(stats.sizeBytes),
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${formatCount(titles.value.toInt())} titles",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                // Breakdown owns the FULL width now — nothing competes with it.
                Text(
                    text = "${formatCount(stats.movies)} Movies  ·  " +
                        "${formatCount(stats.shows)} Shows  ·  " +
                        "${formatCount(stats.episodes)} Ep",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDDD6FE),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
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
