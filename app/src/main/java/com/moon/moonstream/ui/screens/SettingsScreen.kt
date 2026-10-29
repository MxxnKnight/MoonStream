package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moon.moonstream.BuildConfig
import com.moon.moonstream.ui.components.InfoDot
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.ui.components.SettingRow
import com.moon.moonstream.ui.components.SettingsGroup
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.state.MsAccent
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.launch

/**
 * Phase 1: settings with fake data. Every setting has an info tooltip.
 * TODO(Phase 2+): persist to DataStore/prefs and apply to the real services.
 */
@Composable
fun SettingsScreen(onOpenLogs: () -> Unit = {}) {
    val context = LocalContext.current
    var portText by remember { mutableStateOf(AppUiState.port.toString()) }
    var tailscale by remember { mutableStateOf(false) }
    var publicHost by remember { mutableStateOf("") }
    var autoScan by remember { mutableStateOf(true) }
    var scanInterval by remember { mutableStateOf("30") }
    var animeMode by remember { mutableStateOf(true) }
    var cacheMb by remember { mutableStateOf(AppUiState.cacheMaxMb.toString()) }
    var bufferMb by remember { mutableStateOf("20") }
    var maxSizeMb by remember { mutableStateOf("") }
    var prefQuality by remember { mutableStateOf("1080p") }
    var hideCam by remember { mutableStateOf(true) }
    var dupProtection by remember { mutableStateOf(true) }

    fun fakeSaved() = Toast.makeText(context, "Saved (fake)", Toast.LENGTH_SHORT).show()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )

        SettingsGroup("Server") {
            SettingRow(
                title = "Server port",
                info = "The port the Stremio addon server listens on. Change it only if another app already uses this port. The manifest link on the Home screen updates automatically.",
            ) {
                MsTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        it.toIntOrNull()?.let { p -> if (p in 1..65535) AppUiState.port = p }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            SettingRow(
                title = "Prefer Tailscale",
                info = "When on, the app advertises your Tailscale address instead of the local Wi-Fi address, so Stremio on another device can reach your phone from anywhere.",
            ) {
                MsToggle(checked = tailscale, onCheckedChange = { tailscale = it; fakeSaved() })
            }
            SettingRow(
                title = "Public host",
                info = "Optional. A fixed address (like a domain or Tailscale IP) to put in the manifest link instead of the auto-detected one.",
            ) {
                MsTextField(
                    value = publicHost,
                    onValueChange = { publicHost = it },
                    hint = "auto",
                    modifier = Modifier.width(170.dp),
                )
            }
        }

        SettingsGroup("Scanning & Matching") {
            SettingRow(
                title = "Auto-scan new content",
                info = "When on, the app quietly checks your channels for new files in the background and adds them to the library by itself.",
            ) {
                MsToggle(checked = autoScan, onCheckedChange = { autoScan = it; fakeSaved() })
            }
            SettingRow(
                title = "Scan interval (seconds)",
                info = "How often the app checks channels for new files. Lower is fresher but uses more battery and data.",
            ) {
                MsTextField(
                    value = scanInterval,
                    onValueChange = { scanInterval = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            // v0.3.1: the "Match confidence" slider was removed — moon noted no
            // other app has it; matching strictness is handled internally.
            SettingRow(
                title = "Anime handling",
                info = "When on, channels marked as anime use episode-number matching (like episode 1123) instead of season/episode matching.",
            ) {
                MsToggle(checked = animeMode, onCheckedChange = { animeMode = it; fakeSaved() })
            }
        }

        SettingsGroup("Streaming & Storage") {
            SettingRow(
                title = "Cache MB",
                info = "How much phone storage the app may use to keep recently watched video chunks, so replays and seeks are instant. Oldest chunks are deleted first when full.",
            ) {
                MsTextField(
                    value = cacheMb,
                    onValueChange = {
                        cacheMb = it
                        it.toIntOrNull()?.let { mb ->
                            if (mb >= 0) AppUiState.cacheMaxMb = mb
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            // Mockup-style cache meter: "312 MB of 2048 MB" + bar + Clear.
            CacheStorageBlock()
            SettingRow(
                title = "Buffer MB",
                info = "How much video the app downloads ahead while you watch. Bigger means smoother playback on slow connections, but uses more data upfront.",
            ) {
                MsTextField(
                    value = bufferMb,
                    onValueChange = { bufferMb = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            SettingRow(
                title = "Max file size (MB)",
                info = "Skip files bigger than this during scans. Leave empty for no limit. Useful if you only want smaller files indexed.",
            ) {
                MsTextField(
                    value = maxSizeMb,
                    onValueChange = { maxSizeMb = it },
                    hint = "no limit",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        SettingsGroup("Playback") {
            SettingRow(
                title = "Preferred quality",
                info = "When a title has several versions, Stremio lists this quality first. You can still pick any other version.",
            ) {
                MsDropdown(
                    selected = prefQuality,
                    options = listOf("4K", "1080p", "720p", "480p"),
                    onSelect = { prefQuality = it; fakeSaved() },
                    modifier = Modifier.width(150.dp),
                )
            }
            SettingRow(
                title = "Hide CAM",
                info = "Hides cinema-recorded (CAM) versions from the stream list. They are usually the worst quality.",
            ) {
                MsToggle(checked = hideCam, onCheckedChange = { hideCam = it; fakeSaved() })
            }
        }

        SettingsGroup("Appearance & Library") {
            SettingRow(
                title = "Accent color",
                info = "The app's highlight color — buttons, switches, progress bars and the selected tab follow it. Applies instantly.",
            ) {
                MsDropdown(
                    selected = AppUiState.accent.label,
                    options = MsAccent.values().map { it.label },
                    onSelect = { label ->
                        MsAccent.values().find { it.label == label }?.let {
                            AppUiState.setAccent(it, context)
                        }
                    },
                    modifier = Modifier.width(170.dp),
                )
            }
            SettingRow(
                title = "Pure AMOLED mode",
                info = "Switches backgrounds and cards to true black. On AMOLED screens black pixels are fully off, so this saves battery and looks deeper in the dark.",
            ) {
                MsToggle(
                    checked = AppUiState.amoled,
                    onCheckedChange = { AppUiState.setAmoled(it, context) },
                )
            }
            SettingRow(
                title = "Duplicate protection",
                info = "When on, the app ignores a file it has already indexed, even if it appears again in another channel.",
            ) {
                MsToggle(checked = dupProtection, onCheckedChange = { dupProtection = it; fakeSaved() })
            }
        }
        SettingsGroup("Diagnostics") {
            SettingRow(
                title = "View logs",
                info = "Opens the app's diagnostic log — useful when something misbehaves and you want to see what the app was doing.",
            ) {
                MsOutlinePill(
                    text = "Open",
                    fontSize = 13,
                    onClick = onOpenLogs,
                )
            }
        }
        SettingsGroup("App") {
            SettingRow(
                title = "Check for updates",
                info = "Looks for a newer MoonStream release on GitHub and offers to install it. Needs the releases to be reachable.",
            ) {
                MsOutlinePill(
                    text = "Check",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "You're on the latest version (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            SettingRow(
                title = "About",
                info = "The app name, version and package. Useful when reporting a problem.",
            ) {
                Text(
                    "MoonStream ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * Mockup-style cache meter: "312 MB of 2048 MB" + progress bar + Clear Cache.
 * Clear animates the usage down to 0 (fake).
 * TODO(Phase 4): drive from real cache stats.
 */
@Composable
private fun CacheStorageBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val used = AppUiState.cacheUsedMb
    val max = AppUiState.cacheMaxMb.coerceAtLeast(1)
    val bar = remember { Animatable(used.toFloat() / max) }

    // Keep the bar in sync when the numbers change (e.g. after Clear).
    androidx.compose.runtime.LaunchedEffect(used, max) {
        bar.animateTo(used.toFloat() / max, tween(600))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Cache storage",
                style = MaterialTheme.typography.bodyLarge,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.width(10.dp))
            InfoDot(onClick = {
                Toast.makeText(
                    context,
                    "Video chunks and thumbnails stored on your phone. Clearing never touches your library or channels.",
                    Toast.LENGTH_LONG,
                ).show()
            })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$used MB of $max MB",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
            )
            MsOutlinePill(
                text = "Clear Cache",
                fontSize = 13,
                onClick = {
                    scope.launch {
                        val from = AppUiState.cacheUsedMb
                        val anim = Animatable(from.toFloat())
                        anim.animateTo(0f, tween(700)) { AppUiState.cacheUsedMb = value.toInt() }
                    }
                    Toast.makeText(context, "Cache cleared (fake)", Toast.LENGTH_SHORT).show()
                },
            )
        }
        LinearProgressIndicator(
            progress = bar.value.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MsColors.Accent,
            trackColor = MsColors.TrackOff,
        )
    }
}
