package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moon.moonstream.BuildConfig
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSlider
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.ui.components.SettingRow
import com.moon.moonstream.ui.components.SettingsGroup
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors

/**
 * Phase 1: settings with fake data. Every setting has an info tooltip.
 * TODO(Phase 2+): persist to DataStore/prefs and apply to the real services.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var portText by remember { mutableStateOf(AppUiState.port.toString()) }
    var tailscale by remember { mutableStateOf(false) }
    var publicHost by remember { mutableStateOf("") }
    var autoScan by remember { mutableStateOf(true) }
    var scanInterval by remember { mutableStateOf("30") }
    var confidence by remember { mutableFloatStateOf(0.75f) }
    var animeMode by remember { mutableStateOf(true) }
    var cacheMb by remember { mutableStateOf("2048") }
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
            SettingRow(
                title = "Match confidence",
                info = "How sure the app must be before it files a video under a title automatically. Higher means fewer wrong matches, but more files land in Unmatched for you to link by hand.",
            ) {
                MsSlider(
                    value = confidence,
                    onValueChange = { confidence = it; fakeSaved() },
                    valueRange = 0.5f..0.99f,
                    modifier = Modifier.width(170.dp),
                )
            }
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
                    onValueChange = { cacheMb = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
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
                title = "Clear Cache",
                info = "Deletes all stored video chunks and image thumbnails right now. Your library and channels are not affected.",
            ) {
                MsOutlinePill(
                    text = "Clear",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "Cache cleared (fake)", Toast.LENGTH_SHORT).show()
                    },
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
                title = "Theme",
                info = "Dark is the default look. More themes can be added later without changing anything else.",
            ) {
                Text(
                    "Dark",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MsColors.TextPrimary,
                )
            }
            SettingRow(
                title = "Duplicate protection",
                info = "When on, the app ignores a file it has already indexed, even if it appears again in another channel.",
            ) {
                MsToggle(checked = dupProtection, onCheckedChange = { dupProtection = it; fakeSaved() })
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
