package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.BuildConfig
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.InfoDot
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsGroupHeader
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSegmented
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenLogs: () -> Unit = {}) {
    val context = LocalContext.current

    // --- Server state ---
    var portText by remember { mutableStateOf(AppUiState.port.toString()) }
    var preferTailscale by remember { mutableStateOf(false) }
    var publicHost by remember { mutableStateOf("") }
    var startOnBoot by remember { mutableStateOf(false) }
    var autoSleep by remember { mutableStateOf(false) }
    var serverOffMode by remember { mutableStateOf(false) }

    // --- Scanning & Matching state ---
    var autoScan by remember { mutableStateOf(true) }
    var scanInterval by remember { mutableStateOf("30") }
    var animeMode by remember { mutableStateOf(true) }
    var showCatalogs by remember { mutableStateOf(true) }
    var latestPopular by remember { mutableStateOf(true) }
    var indexNew by remember { mutableStateOf(true) }
    var replaceSameQuality by remember { mutableStateOf(true) }
    var liveSearchFallback by remember { mutableStateOf(true) }

    // --- Metadata state ---
    var tmdbKey by remember { mutableStateOf("") }
    var tvdbKey by remember { mutableStateOf("") }
    var fanartKey by remember { mutableStateOf("") }
    var posterTemplate by remember { mutableStateOf("") }
    var posterStyle by remember { mutableStateOf(0) }
    var animeNumbering by remember { mutableStateOf(0) }

    // --- Streaming & Storage state ---
    var cacheMb by remember { mutableStateOf(AppUiState.cacheMaxMb.toString()) }
    var bufferMb by remember { mutableStateOf("20") }
    var maxSizeMb by remember { mutableStateOf("") }
    var minSearchMb by remember { mutableStateOf("") }
    var maxSearchMb by remember { mutableStateOf("") }
    var searchAllChats by remember { mutableStateOf(false) }

    // --- Playback state ---
    var prefQuality by remember { mutableStateOf("1080p") }
    var filterCamTs by remember { mutableStateOf(true) }
    val qualityPriority = remember { mutableStateListOf("4K", "1080p", "720p") }

    // --- Appearance state ---
    var dupProtection by remember { mutableStateOf(true) }

    // --- Backup state ---
    var backupToTelegram by remember { mutableStateOf(false) }
    var autoBackupSync by remember { mutableStateOf(false) }
    var earlyBeta by remember { mutableStateOf(false) }

    var showWhatsNew by remember { mutableStateOf(false) }

    fun fakeSaved() = Toast.makeText(context, "Saved (fake)", Toast.LENGTH_SHORT).show()
    fun fakeVerify() = Toast.makeText(context, "API key looks valid (fake)", Toast.LENGTH_SHORT).show()

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

        AccountCard()

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
                title = "Start on boot",
                info = "Starts the Stremio server automatically when your phone restarts, so you don't have to open the app and flip the switch yourself.",
            ) {
                MsToggle(checked = startOnBoot, onCheckedChange = { startOnBoot = it; fakeSaved() })
            }
            SettingRow(
                title = "Auto Sleep",
                info = "Turns the server off by itself after a while with no activity, to save battery. Any new request wakes it up again.",
            ) {
                MsToggle(checked = autoSleep, onCheckedChange = { autoSleep = it; fakeSaved() })
            }
            SettingRow(
                title = "Server Off Mode",
                info = "Extra quiet mode for when the server is off: stops background work too, so nothing runs until you switch the server back on.",
            ) {
                MsToggle(checked = serverOffMode, onCheckedChange = { serverOffMode = it; fakeSaved() })
            }
            SettingRow(
                title = "Public host",
                info = "For access outside Wi-Fi: paste a Cloudflare Tunnel or Tailscale hostname here and the manifest link will use it instead of your local address.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = publicHost,
                        onValueChange = { publicHost = it },
                        hint = "auto",
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsOutlinePill(text = "Save", fontSize = 13, onClick = { fakeSaved() })
                }
            }
            SettingRow(
                title = "Prefer Tailscale",
                info = "When on, the app advertises your Tailscale address instead of the local Wi-Fi address, so Stremio on another device can reach your phone from anywhere.",
            ) {
                MsToggle(checked = preferTailscale, onCheckedChange = { preferTailscale = it; fakeSaved() })
            }
            SettingRow(
                title = "Use Tailscale address",
                info = "Shows your Tailscale address here when Tailscale is detected on this phone, so other devices can reach the server over the internet.",
            ) {
                Text(
                    "Tailscale not detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
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
            SettingRow(
                title = "Show catalogs in Stremio",
                info = "Lists your library as browsable rows (catalogs) inside Stremio and Nuvio, not just through search.",
            ) {
                MsToggle(checked = showCatalogs, onCheckedChange = { showCatalogs = it; fakeSaved() })
            }
            SettingRow(
                title = "Latest and Popular rows",
                info = "Adds 'Latest' and 'Popular' rows at the top of your catalogs in Stremio, based on what was indexed recently.",
            ) {
                MsToggle(checked = latestPopular, onCheckedChange = { latestPopular = it; fakeSaved() })
            }
            SettingRow(
                title = "Index new posts automatically",
                info = "Adds files to the library the moment they appear in your channels, without waiting for the next scan.",
            ) {
                MsToggle(checked = indexNew, onCheckedChange = { indexNew = it; fakeSaved() })
            }
            SettingRow(
                title = "Replace same quality",
                info = "When the same file appears again in the same quality, the newer copy replaces the older one in the library. Your Telegram files are never deleted.",
            ) {
                MsToggle(checked = replaceSameQuality, onCheckedChange = { replaceSameQuality = it; fakeSaved() })
            }
            SettingRow(
                title = "Live search fallback",
                info = "When a title isn't in your library, search Telegram live instead of giving up — slower, but finds more.",
            ) {
                MsToggle(checked = liveSearchFallback, onCheckedChange = { liveSearchFallback = it; fakeSaved() })
            }
            // "Skip exact duplicates" not added: "Duplicate protection" in
            // Appearance & Library already does exactly this.
        }

        SettingsGroup("Metadata") {
            Text(
                "Movies: TMDB → Cinemeta · Series: TVDB → Cinemeta → TMDB · Anime: Kitsu → TVDB → TMDB → Cinemeta. The app tries each source in order until one knows the title.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingRow(
                title = "TMDB API key",
                info = "Your personal key for TMDB, the movie database. Lets the app look up posters and details without shared limits.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        hint = "Paste key",
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsOutlinePill(text = "Verify", fontSize = 13, onClick = { fakeVerify() })
                }
            }
            SettingRow(
                title = "TVDB API key",
                info = "Your personal key for TVDB, the series database. Same idea as the TMDB key, but for shows.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = tvdbKey,
                        onValueChange = { tvdbKey = it },
                        hint = "Paste key",
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsOutlinePill(text = "Verify", fontSize = 13, onClick = { fakeVerify() })
                }
            }
            Text(
                "Kitsu and Cinemeta need no key — they work out of the box.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            SettingBlock(
                title = "Poster style in Stremio",
                info = "Which artwork Stremio shows for your titles: the metadata provider's own posters, RPDB's posters with ratings baked in, or the BetterPosters set.",
            ) {
                MsSegmented(
                    options = listOf("Provider", "RPDB ratings", "BetterPosters"),
                    selected = posterStyle,
                    onSelect = { posterStyle = it; fakeSaved() },
                )
            }
            SettingBlock(
                title = "Anime numbering",
                info = "How anime episodes are matched: Seasonal (season + episode, the way TMDB and TVDB list them) or Absolute (one running number, like episode 1123).",
            ) {
                MsSegmented(
                    options = listOf("Seasonal (TMDB+TVDB)", "Absolute"),
                    selected = animeNumbering,
                    onSelect = { animeNumbering = it; fakeSaved() },
                )
            }
            SettingRow(
                title = "Custom poster template",
                info = "Optional: a URL pattern for your own poster source. {title} and {year} get replaced for each lookup.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = posterTemplate,
                        onValueChange = { posterTemplate = it },
                        hint = "https://…",
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsOutlinePill(
                        text = "See Template",
                        fontSize = 13,
                        onClick = {
                            Toast.makeText(
                                context,
                                "Example: https://my-posters.example/{title}-{year}.jpg (fake)",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }
            SettingRow(
                title = "Fanart.tv key",
                info = "Optional key for Fanart.tv — extra backgrounds and logos for your library artwork.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = fanartKey,
                        onValueChange = { fanartKey = it },
                        hint = "Paste key",
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsOutlinePill(text = "Save", fontSize = 13, onClick = { fakeSaved() })
                }
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
            SettingRow(
                title = "Live search filters",
                info = "When live-searching Telegram, only consider files between these sizes. Leave empty for no limit.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = minSearchMb,
                        onValueChange = { minSearchMb = it },
                        hint = "Min MB",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    MsTextField(
                        value = maxSearchMb,
                        onValueChange = { maxSearchMb = it },
                        hint = "Max MB",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                }
            }
            SettingRow(
                title = "Search all joined chats",
                info = "Live search also looks through every Telegram chat you've joined, not just the channels you added here.",
            ) {
                MsToggle(checked = searchAllChats, onCheckedChange = { searchAllChats = it; fakeSaved() })
            }
            // Dead-link and duplicate scans live in the Library Tools group below.
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
            SettingBlock(
                title = "Preferred Quality Priority",
                info = "Tap to pick which qualities the app tries first, in order. The first selected quality wins when a title has several versions.",
            ) {
                val qualities = listOf("4K", "1080p", "720p", "480p")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    qualities.forEach { q ->
                        val sel = qualityPriority.contains(q)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) MsColors.Accent else MsColors.SurfaceAlt)
                                .border(
                                    1.dp,
                                    if (sel) MsColors.Accent else MsColors.TextSecondary.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    if (sel) qualityPriority.remove(q) else qualityPriority.add(q)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = q,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) MsColors.OnAccent else MsColors.TextSecondary,
                            )
                        }
                    }
                }
            }
            SettingRow(
                title = "Filter CAM & TS",
                info = "Hides cinema-recorded (CAM) and telesync (TS) versions from the stream list. They are usually the worst quality.",
            ) {
                MsToggle(checked = filterCamTs, onCheckedChange = { filterCamTs = it; fakeSaved() })
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

        SettingsGroup("Library Tools") {
            SettingRow(
                title = "Dead links",
                info = "Finds entries whose Telegram message was deleted while the app was off.",
            ) {
                MsOutlinePill(
                    text = "Check now",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "No dead links found (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            SettingRow(
                title = "Duplicates",
                info = "Spots files with similar name and size on one title or episode, so you can clean up doubles.",
            ) {
                MsOutlinePill(
                    text = "Find duplicates",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "No duplicates found (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        SettingsGroup("Backup") {
            SettingRow(
                title = "Back up to Telegram",
                info = "Saves a copy of your catalog to your Saved Messages whenever something changes, so you never lose your library.",
            ) {
                MsToggle(checked = backupToTelegram, onCheckedChange = { backupToTelegram = it; fakeSaved() })
            }
            SettingRow(
                title = "Auto-Backup & Manifest Sync",
                info = "Backs up automatically and keeps the manifest link in sync after every change, with no taps needed.",
            ) {
                MsToggle(checked = autoBackupSync, onCheckedChange = { autoBackupSync = it; fakeSaved() })
            }
            SettingRow(
                title = "Backup destination",
                info = "Where your backups are stored. Saved Messages is a private chat only you can see.",
            ) {
                Text(
                    "Saved Messages",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MsColors.TextPrimary,
                )
            }
            Text(
                "Last backup: never",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MsOutlinePill(
                    text = "Back up now",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "Backup started (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
                MsOutlinePill(
                    text = "Restore",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "Nothing to restore yet (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MsOutlinePill(
                    text = "Export file",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "Export started (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
                MsOutlinePill(
                    text = "Import file",
                    fontSize = 13,
                    onClick = {
                        Toast.makeText(context, "Pick a backup file (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            Text(
                "If you reinstall the app, log in with the same Telegram account and hit Restore — your catalog comes back.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
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
                title = "Installed version",
                info = "The exact version installed on this phone — handy when reporting a problem.",
            ) {
                Text(
                    BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MsColors.TextPrimary,
                )
            }
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
                title = "What's New",
                info = "See what changed in this version.",
            ) {
                MsOutlinePill(
                    text = "View",
                    fontSize = 13,
                    onClick = { showWhatsNew = true },
                )
            }
            SettingRow(
                title = "Early Beta Access",
                info = "Get test builds before everyone else. They may be rough around the edges.",
            ) {
                MsToggle(checked = earlyBeta, onCheckedChange = { earlyBeta = it; fakeSaved() })
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
            // v0.5.0: logout moved to the Account group at the top.
        }

        SettingsGroup("Support") {
            SettingRow(
                title = "Support the developer",
                info = "MoonStream is free. If it saves you time, a small tip keeps it maintained.",
            ) {
                BareIconButton(
                    icon = Icons.Filled.Favorite,
                    contentDescription = "Support the developer",
                    tint = Color(0xFFF44336),
                    onClick = {
                        Toast.makeText(context, "Thank you! (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    if (showWhatsNew) {
        AlertDialog(
            onDismissRequest = { showWhatsNew = false },
            title = {
                Text(
                    "What's New in ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
            },
            text = {
                Text(
                    "• Library card: total size moved to the top-right, episode count never cut off\n" +
                        "• New Account section with profile and logout\n" +
                        "• New Metadata section: provider order, API keys, poster styles, anime numbering\n" +
                        "• More scanning options and live-search filters\n" +
                        "• Dead-link and duplicate finders\n" +
                        "• Telegram backup, export and import\n" +
                        "• Server: start on boot, auto sleep, remote access\n" +
                        "• Early beta access toggle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            },
            confirmButton = {
                LimePillButton(text = "Nice", fontSize = 14, onClick = { showWhatsNew = false })
            },
            containerColor = MsColors.Surface,
            titleContentColor = MsColors.TextPrimary,
            textContentColor = MsColors.TextSecondary,
        )
    }
}

/**
 * Account profile card: fake Telegram identity + logout.
 * TODO(Phase 2): fill from the real TDLib session; logout ends the real session.
 */
@Composable
private fun AccountCard() {
    val context = LocalContext.current
    var showLogout by remember { mutableStateOf(false) }

    MsGroupHeader("Account")
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MsColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "M",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MsColors.OnAccent,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "moon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        "@moon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
            AccountDetailRow(label = "Telegram ID", value = "123456789")
            AccountDetailRow(label = "Phone", value = "+91 ••••• •••••")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4CAF50),
                )
            }
            Text(
                "Fake profile for now — the real Telegram account details arrive with the real login (Phase 2).",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            LimePillButton(
                text = "Log out of Telegram Account",
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(4.dp))

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = {
                Text(
                    "Log out?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
            },
            text = {
                Text(
                    "You'll need to enter your phone number again next time. (Fake for now.)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            },
            confirmButton = {
                LimePillButton(
                    text = "Log out",
                    fontSize = 14,
                    onClick = {
                        AppUiState.setLoggedOut(context)
                        showLogout = false
                        Toast.makeText(context, "Logged out (fake)", Toast.LENGTH_SHORT).show()
                    },
                )
            },
            dismissButton = {
                MsOutlinePill(text = "Cancel", fontSize = 14, onClick = { showLogout = false })
            },
            containerColor = MsColors.Surface,
            titleContentColor = MsColors.TextPrimary,
            textContentColor = MsColors.TextSecondary,
        )
    }
}

@Composable
private fun AccountDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MsColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MsColors.TextPrimary,
        )
    }
}

/**
 * A settings block: label + gray outline ⓘ that expands a plain-words
 * explanation inline, with full-width custom content below (for controls
 * too wide to sit next to the label, like chip rows or segmented controls).
 */
@Composable
private fun SettingBlock(
    title: String,
    info: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.width(10.dp))
            InfoDot(onClick = { expanded = !expanded })
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = false }
                    .padding(start = 2.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
            )
        }
        content()
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
