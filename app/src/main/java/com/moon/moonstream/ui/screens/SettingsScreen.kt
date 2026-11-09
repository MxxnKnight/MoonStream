package com.moon.moonstream.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.util.AppLog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.moon.moonstream.backup.BackupManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.moon.moonstream.BuildConfig
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.backend.MsProfile
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.InfoDot
import com.moon.moonstream.ui.components.IosGroup
import com.moon.moonstream.ui.components.IosNavRow
import com.moon.moonstream.ui.components.IosDialog
import com.moon.moonstream.ui.components.IosSheetButton
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsGroupHeader
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSegmented
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.unfocusOnTapOutside
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.index.tools.LibraryToolsRunner
import com.moon.moonstream.ui.components.BackgroundToolButton
import com.moon.moonstream.ui.components.ScanActionButton
import com.moon.moonstream.ui.components.SettingRow
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.state.LanguagePrefs
import com.moon.moonstream.ui.state.MsAccent
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.index.scan.ScanPrefs
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.moon.moonstream.update.ApkInstaller
import com.moon.moonstream.update.UpdateChecker
import com.moon.moonstream.update.UpdateDownloadService
import com.moon.moonstream.update.UpdateInfo
import com.moon.moonstream.update.UpdateState
import com.moon.moonstream.cache.CacheManager
import com.moon.moonstream.cache.TdStreamCache

/**
 * Phase 1: settings with fake data. Every setting has an info tooltip.
 * TODO(Phase 2+): persist to DataStore/prefs and apply to the real services.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenLogs: () -> Unit = {}, onLoggedOut: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Server state ---
    var portText by remember { mutableStateOf(AppUiState.port.toString()) }
    var preferTailscale by remember { mutableStateOf(false) }
    var publicHost by remember { mutableStateOf("") }
    var startOnBoot by remember { mutableStateOf(false) }
    var autoSleep by remember { mutableStateOf(false) }
    var serverOffMode by remember { mutableStateOf(false) }

    // --- Scanning & Matching state ---
    var autoScan by remember { mutableStateOf(ScanPrefs.isAutoScan(context)) }
    var scanInterval by remember { mutableStateOf("30") }
    var animeMode by remember { mutableStateOf(true) }
    var showCatalogs by remember { mutableStateOf(true) }
    var latestPopular by remember { mutableStateOf(true) }
    var indexNew by remember { mutableStateOf(true) }
    var replaceSameQuality by remember { mutableStateOf(true) }
    var liveSearchFallback by remember { mutableStateOf(true) }

    // --- Metadata state (persisted via ScanPrefs so the scanner uses them) ---
    var tmdbKey by remember { mutableStateOf(ScanPrefs.getTmdbKey(context)) }
    var tvdbKey by remember { mutableStateOf(ScanPrefs.getTvdbKey(context)) }
    var omdbKey by remember { mutableStateOf(ScanPrefs.getOmdbKey(context)) }
    var animeSkipClientId by remember { mutableStateOf(ScanPrefs.getAnimeSkipClientId(context)) }
    var fanartKey by remember { mutableStateOf(ScanPrefs.getFanartKey(context)) }
    // The key that is actually saved — the Fanart.tv option is gated on
    // this, not on the (possibly unsaved) text field above.
    var fanartKeySaved by remember { mutableStateOf(ScanPrefs.getFanartKey(context)) }
    var posterTemplate by remember { mutableStateOf(ScanPrefs.getPosterTemplate(context)) }
    var posterStyle by remember { mutableStateOf(ScanPrefs.getPosterStyle(context)) }
    var animeNumbering by remember { mutableStateOf(if (ScanPrefs.isAnimeAbsolute(context)) 1 else 0) }

    // --- Streaming & Storage state ---
    var cacheMb by remember { mutableStateOf(AppUiState.cacheMaxMb.toString()) }
    var bufferMb by remember { mutableStateOf(AppUiState.bufferMb.toString()) }
    var playerBufferS by remember { mutableStateOf(AppUiState.playerBufferS.toString()) }
    var maxSizeMb by remember { mutableStateOf("") }
    var minSearchMb by remember { mutableStateOf("") }
    var maxSearchMb by remember { mutableStateOf("") }
    var searchAllChats by remember { mutableStateOf(false) }

    // --- Playback state ---
    var prefQuality by remember { mutableStateOf("1080p") }
    var filterCamTs by remember { mutableStateOf(true) }
    val qualityPriority = remember { mutableStateListOf("4K", "1080p", "720p") }

    // --- Language state (in-app player) ---
    var subLangFirst by remember { mutableStateOf(LanguagePrefs.subtitleFirst(context)) }
    var subLangSecond by remember { mutableStateOf(LanguagePrefs.subtitleSecond(context)) }
    var audioLangFirst by remember { mutableStateOf(LanguagePrefs.audioFirst(context)) }
    var audioLangSecond by remember { mutableStateOf(LanguagePrefs.audioSecond(context)) }

    // --- Appearance state ---
    var dupProtection by remember { mutableStateOf(true) }

    // --- Backup state ---
    // backupToTelegram removed — Telegram is the only destination now.
    var autoBackupSync by remember {
        mutableStateOf(
            context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("auto_backup_sync", true)
        )
    }

    // Backup state — declared before the push/pull functions that use it
    // (Kotlin local declarations must come before their first use).
    var backupWorking by remember { mutableStateOf(false) }
    var lastBackupAt by remember {
        mutableStateOf(
            context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                .getLong("last_backup_at", 0),
        )
    }
    // GitHub Gist second backup (moon's 2026-11-06): token typed in Settings,
    // gist id saved on first push. Kept next to the Telegram backup state.
    var gistToken by remember { mutableStateOf(ScanPrefs.getGistToken(context)) }
    var gistTokenSaved by remember { mutableStateOf(ScanPrefs.getGistToken(context)) }
    var lastGistBackupAt by remember {
        mutableStateOf(
            context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                .getLong(com.moon.moonstream.backup.GistBackup.PREF_LAST_GIST_BACKUP_AT, 0),
        )
    }

    fun markBackedUp() {
        lastBackupAt = System.currentTimeMillis()
        context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putLong("last_backup_at", lastBackupAt).apply()
    }

    fun lastBackupText(): String {
        if (lastBackupAt == 0L) return "Last backup: never"
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        return "Last backup: ${fmt.format(Date(lastBackupAt))}"
    }

    fun markGistBackedUp() {
        lastGistBackupAt = System.currentTimeMillis()
        context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putLong(com.moon.moonstream.backup.GistBackup.PREF_LAST_GIST_BACKUP_AT, lastGistBackupAt).apply()
    }

    fun lastGistBackupText(): String {
        if (lastGistBackupAt == 0L) return "Last Gist backup: never"
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        return "Last Gist backup: ${fmt.format(Date(lastGistBackupAt))}"
    }

    fun pushBackupToTelegram(onDone: () -> Unit = {}) {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val json = BackupManager.buildBackupJson(context)
                val gz = BackupManager.gzip(json.toString().toByteArray(Charsets.UTF_8))
                val name = BackupManager.backupFileName()
                val result = BackupManager.uploadBackupReplacingOld(
                    BackendProvider.backend, gz, name,
                )
                withContext(Dispatchers.Main) {
                    if (result.messageId != null && result.verified) {
                        // Remember this content so the next auto-backup can
                        // skip re-uploading the identical library.
                        context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString(BackupManager.PREF_LAST_BACKUP_HASH, BackupManager.contentHash(json)).apply()
                        markBackedUp()
                        MsToast.show("Cloud backup pushed to Saved Messages")
                        onDone()
                    } else if (result.messageId != null) {
                        // Upload reported success but the file isn't findable
                        // in Saved Messages — old backups were kept on purpose.
                        MsToast.show("Backup uploaded but not found in Saved Messages — old backups kept")
                    } else {
                        MsToast.show("Telegram cloud backup failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Backup error: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    fun pullBackupFromTelegram() {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val ref = BackendProvider.backend.findLatestBackup()
                if (ref == null) {
                    withContext(Dispatchers.Main) {
                        MsToast.show("No backup found in Saved Messages")
                    }
                    return@launch
                }
                val bytes = BackendProvider.backend.downloadBackup(ref)
                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        MsToast.show("Could not download backup file from Telegram")
                    }
                    return@launch
                }
                val json = BackupManager.parseBackup(bytes)
                val summary = BackupManager.restore(context, json)
                com.moon.moonstream.index.scan.ScanUiState.bumpChannelListVersion()
                withContext(Dispatchers.Main) {
                    markBackedUp()
                    MsToast.show("Restored from Telegram: ${summary.channels} channels, ${summary.media} titles, ${summary.files} files, ${summary.progress} resume spots")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Restore error: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    /** Push the library backup to the private GitHub Gist (second backup). */
    fun pushBackupToGist() {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val json = BackupManager.buildBackupJson(context)
                val gz = BackupManager.gzip(json.toString().toByteArray(Charsets.UTF_8))
                val ok = com.moon.moonstream.backup.GistBackup.push(context, gz)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString(com.moon.moonstream.backup.GistBackup.PREF_LAST_GIST_HASH, BackupManager.contentHash(json)).apply()
                        markGistBackedUp()
                        MsToast.show("Gist backup pushed")
                    } else {
                        MsToast.show("Gist backup failed — check the token in Settings")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Gist backup error: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    /** Pull the library backup back from the private GitHub Gist. */
    fun pullBackupFromGist() {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = com.moon.moonstream.backup.GistBackup.pull(context)
                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        MsToast.show("No Gist backup found — check the token in Settings")
                    }
                    return@launch
                }
                val json = BackupManager.parseBackup(bytes)
                val summary = BackupManager.restore(context, json)
                com.moon.moonstream.index.scan.ScanUiState.bumpChannelListVersion()
                withContext(Dispatchers.Main) {
                    markGistBackedUp()
                    MsToast.show("Restored from Gist: ${summary.channels} channels, ${summary.media} titles, ${summary.files} files, ${summary.progress} resume spots")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Gist restore error: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }
    var earlyBeta by remember { mutableStateOf(false) }

    /** Build the backup, gzip it, and hand it to the Android share sheet. */
    fun shareBackup() {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val json = BackupManager.buildBackupJson(context)
                val gz = BackupManager.gzip(json.toString().toByteArray(Charsets.UTF_8))
                val file = File(context.cacheDir, BackupManager.backupFileName())
                file.writeBytes(gz)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file,
                )
                withContext(Dispatchers.Main) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/gzip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Save backup"))
                    markBackedUp()
                    MsToast.show("Backup ready — pick where to save it")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Backup failed: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    /** Save the backup straight to Downloads/MoonStream. */
    fun exportBackupToDownloads() {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val json = BackupManager.buildBackupJson(context)
                val gz = BackupManager.gzip(json.toString().toByteArray(Charsets.UTF_8))
                val name = BackupManager.backupFileName()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/MoonStream")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                    ) ?: throw IllegalStateException("Could not create download entry")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(gz) }
                        ?: throw IllegalStateException("Could not write download")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "MoonStream",
                    )
                    dir.mkdirs()
                    File(dir, name).writeBytes(gz)
                }
                withContext(Dispatchers.Main) {
                    markBackedUp()
                    MsToast.show("Saved to Downloads/MoonStream/$name")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Export failed: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    /** Read a picked file and merge it into the library. */
    fun restoreFromUri(uri: android.net.Uri) {
        if (backupWorking) return
        backupWorking = true
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
                val json = BackupManager.parseBackup(bytes)
                val summary = BackupManager.restore(context, json)
                // Fresh counts on screen.
                com.moon.moonstream.index.scan.ScanUiState.bumpChannelListVersion()
                withContext(Dispatchers.Main) {
                    markBackedUp()
                    MsToast.show("Restored: ${summary.channels} channels, ${summary.media} titles, " + "${summary.files} files, ${summary.subtitles} subtitles, ${summary.progress} resume spots")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MsToast.show("Restore failed: ${e.message}")
                }
            } finally {
                backupWorking = false
            }
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) restoreFromUri(uri)
    }

    var showWhatsNew by remember { mutableStateOf(false) }

    // --- OTA update state ---
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    // 0.15.4: observe the foreground download service (survives screen changes).
    val updateState by UpdateDownloadService.state.collectAsState()

    fun checkForUpdates() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateError = null
        updateInfo = null
        scope.launch(Dispatchers.IO) {
            try {
                val info = UpdateChecker.checkForUpdate()
                withContext(Dispatchers.Main) {
                    checkingUpdate = false
                    if (info != null) updateInfo = info
                    else MsToast.show("You're on the latest version")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    checkingUpdate = false
                    updateError = e.message ?: "Couldn't reach GitHub"
                }
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        // 0.15.4: download runs in a foreground service so it survives
        // screen changes and app backgrounding (moon's request).
        UpdateDownloadService.start(context, info)
        MsToast.show("Downloading v${info.version} — continues in background")
    }

    fun saved(key: String) = MsToast.show("Saved")
    // Shorthand: persist a boolean setting and toast.
    fun saveBool(key: String, value: Boolean) {
        com.moon.moonstream.ui.state.SettingsPrefs.setBool(context, key, value)
        MsToast.show("Saved")
    }
    fun fakeVerify() = MsToast.show("API key looks valid")

    var verifyingTmdb by remember { mutableStateOf(false) }
    var tmdbVerified by remember { mutableStateOf(false) }

    /** Real TMDB key check: /configuration answers 200 only for a valid key. */
    fun verifyTmdb() {
        val key = tmdbKey.trim()
        if (key.isEmpty()) {
            MsToast.show("Paste a TMDB key first")
            return
        }
        verifyingTmdb = true
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val conn = URL("https://api.themoviedb.org/3/configuration?api_key=$key")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    try {
                        conn.responseCode == HttpURLConnection.HTTP_OK
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                false
            }
            verifyingTmdb = false
            tmdbVerified = ok
            MsToast.show(if (ok) "TMDB key valid ✓" else "TMDB key invalid — check the key")
        }
    }

    val settingsScroll = rememberScrollState(AppUiState.restoreScroll("settings"))
    // Save position when leaving so Back restores it.
    DisposableEffect(Unit) {
        onDispose { AppUiState.saveScroll("settings", settingsScroll.value) }
    }
    // --- Sub-screen navigation (moon's request 2026-11-03): each settings
    // --- section opens as its own screen with a back button, instead of
    // --- collapsing groups in one long list. Null = main list.
    // AppUiState.settingsRoute lives in AppUiState so it survives tab switches
    // (moon's bug 2026-11-04: back from Library returned to main settings).

    // System back gesture in a sub-screen returns to main settings
    // (moon's bug 2026-11-04: only the arrow button worked).
    BackHandler(enabled = AppUiState.settingsRoute != null) {
        AppUiState.settingsRoute = null
    }

    // Tappable row for the main settings list.
    @Composable
    fun SettingsNavRow(title: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MsColors.TextSecondary,
            )
        }
    }

    @Composable
    fun ServerSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
                MsToggle(checked = startOnBoot, onCheckedChange = { startOnBoot = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Auto Sleep",
                info = "Turns the server off by itself after a while with no activity, to save battery. Any new request wakes it up again.",
            ) {
                MsToggle(checked = autoSleep, onCheckedChange = { autoSleep = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Server Off Mode",
                info = "Extra quiet mode for when the server is off: stops background work too, so nothing runs until you switch the server back on.",
            ) {
                MsToggle(checked = serverOffMode, onCheckedChange = { serverOffMode = it; MsToast.show("Saved") })
            }
            SettingBlock(
                title = "Public host",
                info = "For access outside Wi-Fi: paste a Cloudflare Tunnel or Tailscale hostname here and the manifest link will use it instead of your local address.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = publicHost,
                        onValueChange = { publicHost = it },
                        hint = "auto",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(text = "Save", fontSize = 13, onClick = { MsToast.show("Saved") })
                }
            }
            SettingRow(
                title = "Prefer Tailscale",
                info = "When on, the app advertises your Tailscale address instead of the local Wi-Fi address, so Stremio on another device can reach your phone from anywhere.",
            ) {
                MsToggle(checked = preferTailscale, onCheckedChange = { preferTailscale = it; MsToast.show("Saved") })
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
    }

    @Composable
    fun ScanningSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingRow(
                title = "Auto-scan new content",
                info = "When on, the app quietly checks your channels for new files in the background and adds them to the library by itself.",
            ) {
                MsToggle(checked = autoScan, onCheckedChange = { autoScan = it; ScanPrefs.setAutoScan(context, it); MsToast.show("Saved") })
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
                MsToggle(checked = animeMode, onCheckedChange = { animeMode = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Show catalogs in Stremio",
                info = "Lists your library as browsable rows (catalogs) inside Stremio and Nuvio, not just through search.",
            ) {
                MsToggle(checked = showCatalogs, onCheckedChange = { showCatalogs = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Latest and Popular rows",
                info = "Adds 'Latest' and 'Popular' rows at the top of your catalogs in Stremio, based on what was indexed recently.",
            ) {
                MsToggle(checked = latestPopular, onCheckedChange = { latestPopular = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Index new posts automatically",
                info = "Adds files to the library the moment they appear in your channels, without waiting for the next scan.",
            ) {
                MsToggle(checked = indexNew, onCheckedChange = { indexNew = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Replace same quality",
                info = "When the same file appears again in the same quality, the newer copy replaces the older one in the library. Your Telegram files are never deleted.",
            ) {
                MsToggle(checked = replaceSameQuality, onCheckedChange = { replaceSameQuality = it; MsToast.show("Saved") })
            }
            SettingRow(
                title = "Live search fallback",
                info = "When a title isn't in your library, search Telegram live instead of giving up — slower, but finds more.",
            ) {
                MsToggle(checked = liveSearchFallback, onCheckedChange = { liveSearchFallback = it; MsToast.show("Saved") })
            }
            // "Skip exact duplicates" not added: "Duplicate protection" in
            // Appearance & Library already does exactly this.
        }
    }

    @Composable
    fun MetadataSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Movies: TMDB → Cinemeta → OMDb · Series: TMDB → TVmaze → Cinemeta → OMDb · Anime: Kitsu → Jikan → TMDB → Cinemeta → OMDb. The app tries each source in order until one knows the title.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingBlock(
                title = "TMDB API key",
                info = "Your personal key for TMDB, the movie database. Lets the app look up posters and details without shared limits.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it; tmdbVerified = false; ScanPrefs.setTmdbKey(context, it) },
                        hint = "Paste key",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = if (verifyingTmdb) "Checking…" else "Verify",
                        fontSize = 13,
                        onClick = { verifyTmdb() },
                    )
                    if (tmdbVerified) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            SettingBlock(
                title = "TVDB API key",
                info = "Your personal key for TVDB, the series database. Same idea as the TMDB key, but for shows.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = tvdbKey,
                        onValueChange = { tvdbKey = it; ScanPrefs.setTvdbKey(context, it) },
                        hint = "Paste key",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(text = "Verify", fontSize = 13, onClick = { fakeVerify() })
                }
            }
            SettingBlock(
                title = "OMDb API key",
                info = "Free key from omdbapi.com — the last-resort metadata lookup when nothing else knows a title. Free tier is 1,000 calls a day, so the app only asks it after TMDB, TVmaze and Cinemeta have all missed. Also returns IMDb, Rotten Tomatoes and Metacritic ratings.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = omdbKey,
                        onValueChange = { omdbKey = it; ScanPrefs.setOmdbKey(context, it) },
                        hint = "Paste key",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = "Save",
                        fontSize = 13,
                        onClick = { ScanPrefs.setOmdbKey(context, omdbKey); MsToast.show("Saved") },
                    )
                }
            }
            SettingBlock(
                title = "Anime Skip client ID",
                info = "Free client ID from anime-skip.com (create an account, then API Clients) — an extra anime skip-times source in the player. When AniSkip has no opening/ending times for an episode, the player checks Anime Skip next.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = animeSkipClientId,
                        onValueChange = { animeSkipClientId = it; ScanPrefs.setAnimeSkipClientId(context, it) },
                        hint = "Paste client ID",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = "Save",
                        fontSize = 13,
                        onClick = { ScanPrefs.setAnimeSkipClientId(context, animeSkipClientId); MsToast.show("Saved") },
                    )
                }
            }
            Text(
                "Kitsu, Cinemeta, TVmaze, Jikan and SkipDB need no key — they work out of the box.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            SettingBlock(
                title = "Poster style",
                info = "Which posters the app library and Stremio show. Provider: the metadata source's own posters (TMDB / Cinemeta / Kitsu). BetterPosters: cleaner posters with ratings baked in — free, no key. Fanart.tv: community posters — needs your free key below; pick it only after saving one. Fanart applies to newly scanned movies; BetterPosters changes every poster instantly.",
            ) {
                MsSegmented(
                    options = listOf("Provider", "BetterPosters", "Fanart.tv"),
                    selected = posterStyle,
                    // Fanart.tv is greyed out until a key is saved (moon's 2026-11-06).
                    disabled = if (fanartKeySaved.isBlank()) setOf(2) else emptySet(),
                    onSelect = {
                        posterStyle = it
                        ScanPrefs.setPosterStyle(context, it)
                        MsToast.show("Saved")
                    },
                )
            }
            SettingBlock(
                title = "Anime numbering",
                info = "How anime episodes are matched: Seasonal (season + episode, the way TMDB and TVDB list them) or Absolute (one running number, like episode 1123).",
            ) {
                MsSegmented(
                    options = listOf("Seasonal (TMDB+TVDB)", "Absolute"),
                    selected = animeNumbering,
                    onSelect = {
                        animeNumbering = it
                        ScanPrefs.setAnimeNumbering(context, it == 1)
                    },
                )
            }
            SettingBlock(
                title = "BetterPosters custom style",
                info = "Optional: your own poster style address from btttr.cc/configure (AIOMetadata / Other Addon) — it must contain {imdb_id}. The app swaps each title's IMDb number into it. Leave it empty to use the default BetterPosters look. Only used when Poster style is BetterPosters.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = posterTemplate,
                        onValueChange = { posterTemplate = it },
                        hint = "https://btttr.cc/poster-…/{imdb_id}.jpg",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = "Save",
                        fontSize = 13,
                        onClick = {
                            val t = posterTemplate.trim()
                            if (t.isEmpty()) {
                                ScanPrefs.setPosterTemplate(context, "")
                                MsToast.show("Saved — using the default BetterPosters look")
                            } else if (com.moon.moonstream.util.PosterStyle.isValidTemplate(t)) {
                                ScanPrefs.setPosterTemplate(context, t)
                                MsToast.show("Saved")
                            } else {
                                MsToast.show("That address needs {imdb_id} in it — copy the pattern from btttr.cc/configure")
                            }
                        },
                    )
                }
            }
            SettingBlock(
                title = "Fanart.tv key",
                info = "Free personal key from fanart.tv/get-an-api-key — unlocks the Fanart.tv poster style above. Without a key that option stays greyed out.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = fanartKey,
                        onValueChange = { fanartKey = it },
                        hint = "Paste key",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = "Save",
                        fontSize = 13,
                        onClick = {
                            ScanPrefs.setFanartKey(context, fanartKey)
                            fanartKeySaved = fanartKey.trim()
                            if (fanartKeySaved.isBlank() && posterStyle == 2) {
                                posterStyle = 0
                                ScanPrefs.setPosterStyle(context, 0)
                            }
                            MsToast.show("Saved")
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun StreamingSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingRow(
                title = "Cache MB",
                info = "How much phone storage the app may use for its own cache (update downloads, thumbnails). Video you play in the app is downloaded by Telegram and stored separately — see Stream cache below.",
            ) {
                MsTextField(
                    value = cacheMb,
                    onValueChange = {
                        cacheMb = it
                        it.toIntOrNull()?.let { mb ->
                            if (mb >= 0) AppUiState.setCacheMaxMb(mb, context)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                )
            }
            SettingRow(
                title = "Buffer MB",
                info = "How much video the app downloads ahead from Telegram while you watch, in 8 MB chunks (so 20 means two chunks, 16 MB). Bigger means smoother playback on slow connections, but uses more data upfront. Saved, and picked up mid-video.",
            ) {
                MsTextField(
                    value = bufferMb,
                    onValueChange = {
                        bufferMb = it
                        it.toIntOrNull()?.let { mb ->
                            if (mb >= 0) AppUiState.setBufferMb(mb, context)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                )
            }
            SettingRow(
                title = "Player buffer (seconds)",
                info = "How many seconds of video the player itself holds ready in memory. This is the safety margin for a slow or jumpy Telegram connection: at 50 a network hiccup under a minute doesn't stop the picture. Higher = smoother but more memory and a longer wait before the first frame; 10–300, applies to the next video you open.",
            ) {
                MsTextField(
                    value = playerBufferS,
                    onValueChange = {
                        playerBufferS = it
                        it.toIntOrNull()?.let { s ->
                            if (s in 10..300) AppUiState.setPlayerBufferS(s, context)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                )
            }
            CacheStorageBlock()
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
            SettingBlock(
                title = "Live search filters",
                info = "When live-searching Telegram, only consider files between these sizes. Leave empty for no limit.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = minSearchMb,
                        onValueChange = { minSearchMb = it },
                        hint = "Min MB",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsTextField(
                        value = maxSearchMb,
                        onValueChange = { maxSearchMb = it },
                        hint = "Max MB",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SettingRow(
                title = "Search all joined chats",
                info = "Live search also looks through every Telegram chat you've joined, not just the channels you added here.",
            ) {
                MsToggle(checked = searchAllChats, onCheckedChange = { searchAllChats = it; MsToast.show("Saved") })
            }
            // Dead-link and duplicate scans live in the Library Tools group below.
        }
    }

    @Composable
    fun PlaybackSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingBlock(
                title = "Preferred quality",
                info = "When a title has several versions, Stremio lists this quality first. You can still pick any other version.",
            ) {
                MsDropdown(
                    selected = prefQuality,
                    options = listOf("4K", "1080p", "720p", "480p"),
                    onSelect = { prefQuality = it; MsToast.show("Saved") },
                    modifier = Modifier.fillMaxWidth(),
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MsColors.Accent else Color(0xFF1C1C1E))
                                .clickable {
                                    if (sel) qualityPriority.remove(q) else qualityPriority.add(q)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = q,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) MsColors.OnAccent else Color(0xFF98989F),
                            )
                        }
                    }
                }
            }
            SettingRow(
                title = "Filter CAM & TS",
                info = "Hides cinema-recorded (CAM) and telesync (TS) versions from the stream list. They are usually the worst quality.",
            ) {
                MsToggle(checked = filterCamTs, onCheckedChange = { filterCamTs = it; MsToast.show("Saved") })
            }
        }
    }

    @Composable
    fun LanguagesSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingBlock(
                title = "Subtitle language",
                info = "The in-app player turns on this subtitle by itself. If the title has no file in the first language, the second one is used. Anything else stays in the player's subtitle menu.",
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MsDropdown(
                        selected = subLangFirst,
                        options = LanguagePrefs.CHOICES,
                        onSelect = {
                            subLangFirst = it
                            LanguagePrefs.setSubtitleFirst(context, it)
                            MsToast.show("Subtitles: $it first")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MsDropdown(
                        selected = subLangSecond,
                        options = LanguagePrefs.CHOICES + LanguagePrefs.NONE,
                        onSelect = {
                            subLangSecond = it
                            LanguagePrefs.setSubtitleSecond(context, it)
                            MsToast.show(if (it == LanguagePrefs.NONE) "No fallback subtitle" else "Fallback: $it")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SettingBlock(
                title = "Audio language",
                info = "Which audio track the player prefers when a file carries several. The second one is used when the first isn't in the file.",
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MsDropdown(
                        selected = audioLangFirst,
                        options = LanguagePrefs.CHOICES,
                        onSelect = {
                            audioLangFirst = it
                            LanguagePrefs.setAudioFirst(context, it)
                            MsToast.show("Audio: $it first")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MsDropdown(
                        selected = audioLangSecond,
                        options = LanguagePrefs.CHOICES + LanguagePrefs.NONE,
                        onSelect = {
                            audioLangSecond = it
                            LanguagePrefs.setAudioSecond(context, it)
                            MsToast.show(if (it == LanguagePrefs.NONE) "No fallback audio" else "Fallback: $it")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    @Composable
    fun AppearanceSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
                title = "AMOLED card borders",
                info = "Thin outline around cards in AMOLED mode so they don't melt into the black background.",
            ) {
                MsToggle(
                    checked = AppUiState.amoledCardBorders,
                    onCheckedChange = { AppUiState.setAmoledCardBorders(it, context) },
                )
            }
            SettingRow(
                title = "Duplicate protection",
                info = "When on, the app ignores a file it has already indexed, even if it appears again in another channel.",
            ) {
                MsToggle(checked = dupProtection, onCheckedChange = { dupProtection = it; MsToast.show("Saved") })
            }
        }
    }

    @Composable
    fun LibraryToolsSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingRow(
                title = "Dead links",
                info = "Finds entries whose Telegram message was deleted while the app was off. Runs in the background — you can leave this screen.",
            ) {
                BackgroundToolButton(
                    toolId = LibraryToolsRunner.TOOL_DEAD_LINKS,
                    idleText = "Check now",
                    onStart = {
                        LibraryToolsRunner.startDeadLinks(context.applicationContext)
                    },
                )
            }
            SettingRow(
                title = "Duplicates",
                info = "Spots files with similar name and size on one title or episode, so you can clean up doubles. Runs in the background — you can leave this screen.",
            ) {
                BackgroundToolButton(
                    toolId = LibraryToolsRunner.TOOL_DUPLICATES,
                    idleText = "Find duplicates",
                    onStart = {
                        LibraryToolsRunner.startDuplicates(context.applicationContext)
                    },
                )
            }
            SettingRow(
                title = "Bad mappings",
                info = "Finds files mapped to the wrong title (e.g. episode files stuck on a movie) and moves them to unmatched for bulk fixing. Runs in the background — you can leave this screen.",
            ) {
                BackgroundToolButton(
                    toolId = LibraryToolsRunner.TOOL_AUDIT,
                    idleText = "Audit now",
                    onStart = {
                        LibraryToolsRunner.startAudit(
                            context.applicationContext, structuralOnly = true,
                        )
                    },
                )
            }
            SettingRow(
                title = "Deep audit (slower)",
                info = "Re-checks every mapping against the metadata providers. Catches wrong titles the fast check misses. Takes a while on big libraries. Runs in the background — you can leave this screen.",
            ) {
                BackgroundToolButton(
                    toolId = LibraryToolsRunner.TOOL_DEEP_AUDIT,
                    idleText = "Deep audit",
                    onStart = {
                        LibraryToolsRunner.startAudit(
                            context.applicationContext, structuralOnly = false,
                        )
                    },
                )
            }
        }
    }

    @Composable
    fun BackupSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // "Back up to Telegram" toggle removed (moon's call 2026-11-03):
            // Telegram is the only destination now, so the toggle was redundant.
            // Auto-backup goes to Telegram, period.
            SettingRow(
                title = "Auto-Backup & Manifest Sync",
                info = "Backs up automatically and keeps the manifest link in sync after every change, with no taps needed.",
            ) {
                MsToggle(
                    checked = autoBackupSync,
                    onCheckedChange = {
                        autoBackupSync = it
                        context.getSharedPreferences("moonstream_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("auto_backup_sync", it).apply()
                    }
                )
            }
            SettingRow(
                title = "Backup destination",
                info = "Where your backups are stored. Saved Messages is a private chat only you can see; GitHub Gist below is the second copy on your own GitHub account.",
            ) {
                Text(
                    "Saved Messages",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MsColors.TextPrimary,
                )
            }
            Text(
                lastBackupText(),
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (backupWorking) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    color = MsColors.Accent,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MsOutlinePill(
                    text = "Push to Saved Messages",
                    fontSize = 13,
                    modifier = Modifier.weight(1f),
                    onClick = { pushBackupToTelegram() },
                )
                MsOutlinePill(
                    text = "Pull from Saved Messages",
                    fontSize = 13,
                    modifier = Modifier.weight(1f),
                    onClick = { pullBackupFromTelegram() },
                )
            }
            SettingBlock(
                title = "GitHub Gist (second backup)",
                info = "A second, free backup on your own GitHub account as a private gist — only you can see it. Needs a personal access token with the gist permission: create one at github.com/settings/tokens, tick gist, and paste it below. The token lives only on this phone, never in chat.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MsTextField(
                        value = gistToken,
                        onValueChange = { gistToken = it },
                        hint = "Paste token (ghp_…)",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    MsOutlinePill(
                        text = "Save",
                        fontSize = 13,
                        onClick = {
                            ScanPrefs.setGistToken(context, gistToken)
                            gistTokenSaved = gistToken.trim()
                            MsToast.show("Saved")
                        },
                    )
                }
                Text(
                    lastGistBackupText(),
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
                        text = "Push to Gist",
                        fontSize = 13,
                        enabled = gistTokenSaved.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { pushBackupToGist() },
                    )
                    MsOutlinePill(
                        text = "Pull from Gist",
                        fontSize = 13,
                        enabled = gistTokenSaved.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { pullBackupFromGist() },
                    )
                }
            }
            // Phone-storage backup removed (moon's call 2026-11-03):
            // backup/restore is cloud only now (Telegram + GitHub Gist).
            Text(
                "If you reinstall the app, log in with the same Telegram account and hit Restore — your catalog comes back. A Gist backup restores the same way from the Gist buttons above.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    @Composable
    fun UpdateSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingRow(
                title = "Check for updates",
                info = "Looks for a newer MoonStream release on GitHub and offers to install it.",
            ) {
                MsOutlinePill(
                    text = if (checkingUpdate) "Checking…" else "Check",
                    fontSize = 13,
                    enabled = !checkingUpdate,
                    onClick = ::checkForUpdates,
                )
            }
            if (updateError != null) {
                Text(
                    updateError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    @Composable
    fun AppSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
    }

    @Composable
    fun AboutSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Update content
            SettingRow(
                title = "Check for updates",
                info = "Looks for a newer MoonStream release on GitHub and offers to install it.",
            ) {
                MsOutlinePill(
                    text = if (checkingUpdate) "Checking…" else "Check",
                    fontSize = 13,
                    enabled = !checkingUpdate,
                    onClick = ::checkForUpdates,
                )
            }
            if (updateError != null) {
                Text(
                    updateError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // App content (version info)
            SettingRow(
                title = "Version",
                info = "Your current MoonStream version.",
            ) {
                Text(
                    text = com.moon.moonstream.BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            }
            // Support content
            SettingRow(
                title = "Support the developer",
                info = "MoonStream is free. If it saves you time, a small tip keeps it maintained.",
            ) {
                BareIconButton(
                    icon = Icons.Filled.Favorite,
                    contentDescription = "Support the developer",
                    tint = Color(0xFFF44336),
                    onClick = {
                        MsToast.show("Thank you for the support!")
                    },
                )
            }
        }
    }

    @Composable
    fun SupportSection() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingRow(
                title = "Support the developer",
                info = "MoonStream is free. If it saves you time, a small tip keeps it maintained.",
            ) {
                BareIconButton(
                    icon = Icons.Filled.Favorite,
                    contentDescription = "Support the developer",
                    tint = Color(0xFFF44336),
                    onClick = {
                        MsToast.show("Thank you for the support!")
                    },
                )
            }
        }
    }

    // Directional slide between the main list and a section (moon's
    // 2026-11-09): a section slides in from the right, back to the list
    // slides in from the left. Only the incoming content animates (the
    // old one is dropped at once), so it stays light on a low-end phone.
    AnimatedContent(
        targetState = AppUiState.settingsRoute,
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            // 165ms slide, no fade (2026-11-09 speed pass, matches the
            // screen switch): the incoming page used to slide AND fade, and
            // the full-screen alpha fade is the expensive half on a low-end
            // GPU. Pure movement only.
            slideInHorizontally(
                initialOffsetX = { fullWidth -> dir * fullWidth },
                animationSpec = tween(165, easing = FastOutSlowInEasing),
            ) togetherWith ExitTransition.None
        },
        label = "settingsRoute",
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        if (route == null) {
            // Main settings list: one tappable row per section.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MsColors.Bg)
                .verticalScroll(settingsScroll)
                .padding(16.dp)
                .padding(bottom = 100.dp), // clear the floating nav pill
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )

            AccountCard(onLoggedOut = onLoggedOut)

            // iOS-style grouped settings (moon's iOS rebuild 2026-11-04).
            IosGroup {
                IosNavRow("Server") { AppUiState.settingsRoute = "Server" }
                IosNavRow("Scanning & Matching") { AppUiState.settingsRoute = "Scanning & Matching" }
                IosNavRow("Metadata") { AppUiState.settingsRoute = "Metadata" }
                IosNavRow("Streaming & Storage") { AppUiState.settingsRoute = "Streaming & Storage" }
                IosNavRow("Playback") { AppUiState.settingsRoute = "Playback" }
                IosNavRow("Languages") { AppUiState.settingsRoute = "Languages" }
                IosNavRow("Appearance & Library") { AppUiState.settingsRoute = "Appearance & Library" }
                IosNavRow("Library Tools") { AppUiState.settingsRoute = "Library Tools" }
                IosNavRow("Cloud Backup") { AppUiState.settingsRoute = "Cloud Backup" }
                IosNavRow("Logs") { onOpenLogs() }
                IosNavRow("About", showDivider = false) { AppUiState.settingsRoute = "About" }
            }

            // Version footer (moon's request 2026-11-03): small, one line at bottom.
            Text(
                "MoonStream v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    } else {
        // Sub-screen: fixed header with back button + section content.
        // Tap outside a text field to unfocus it (moon's 2026-11-06): the
        // outline + keyboard go away. Taps on buttons/fields themselves
        // are consumed by those children, so they still work normally.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MsColors.Bg)
                .unfocusOnTapOutside(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = { AppUiState.settingsRoute = null },
                )
                Text(
                    route!!,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
            }
            // Section content in a weighted Box so it fills correctly
            // (fixes sub-screen overlap/ghosting, moon's video 2026-11-04).
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f),
            ) {
                when (route) {
                    "Server" -> ServerSection()
                    "Scanning & Matching" -> ScanningSection()
                    "Metadata" -> MetadataSection()
                    "Streaming & Storage" -> StreamingSection()
                    "Playback" -> PlaybackSection()
                    "Languages" -> LanguagesSection()
                    "Appearance & Library" -> AppearanceSection()
                    "Library Tools" -> LibraryToolsSection()
                    "Cloud Backup" -> BackupSection()
                    "About" -> AboutSection()
                }
            }
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

    // --- OTA update dialog ---
    updateInfo?.let { info ->
        // iOS dialog (moon's request 2026-11-04) — dynamic buttons for Download → Install flow.
        val dialogButtons = buildList<IosSheetButton> {
            add(IosSheetButton(
                label = "Later",
                onClick = { if (updateState !is UpdateState.Downloading) updateInfo = null },
            ))
            when (val s = updateState) {
                is UpdateState.Done -> add(IosSheetButton(
                    label = "Install",
                    onClick = {
                        ApkInstaller.install(context, s.apk)
                        updateInfo = null
                    },
                ))
                is UpdateState.Downloading -> add(IosSheetButton(
                    label = "Downloading…",
                    onClick = {},
                ))
                else -> add(IosSheetButton(
                    label = "Download",
                    onClick = { downloadAndInstall(info) },
                ))
            }
        }
        IosDialog(
            onDismiss = { if (updateState !is UpdateState.Downloading) updateInfo = null },
            title = "Update available: ${info.version}",
            buttons = dialogButtons,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Changelog (moon's request 2026-11-03): show release notes,
                    // not install instructions. No separate changelog button needed.
                    if (info.notes.isNotBlank()) {
                        Text(
                            info.notes.take(800),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                        )
                    }
                    if (updateState is UpdateState.Downloading) {
                        val p = (updateState as UpdateState.Downloading).progress
                        Text(
                            "Downloading… $p%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                        )
                        // iOS-style progress bar (moon's pick 2026-11-04): blue, rounded, clean.
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = p / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MsColors.Accent,
                            trackColor = Color(0xFF38383A),
                        )
                    } else if (updateState is UpdateState.Done) {
                        Text(
                            "Download complete. Tap Install when ready.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.Accent,
                        )
                    } else if (updateState is UpdateState.Failed) {
                        Text(
                            "Download failed: ${(updateState as UpdateState.Failed).message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF44336),
                        )
                    }
                }
            },
        )
    }
}

/**
 * Account profile card: real Telegram identity (via the backend contract's
 * getMe()) + logout. Logging out ends the real TDLib session, clears the
 * logged-in flag, and returns to the Login screen.
 */
@Composable
private fun AccountCard(onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogout by remember { mutableStateOf(false) }
    var showLogoutFinal by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<MsProfile?>(null) }

    LaunchedEffect(Unit) {
        profile = try {
            BackendProvider.backend.getMe()
        } catch (e: Exception) {
            null
        }
    }

    val displayName = profile?.firstName ?: "…"
    val displayHandle = profile?.username?.let { "@${it.trimStart('@')}" } ?: "…"
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "•"

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
                        initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MsColors.OnAccent,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        displayHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
            AccountDetailRow(label = "Telegram ID", value = profile?.id?.toString() ?: "…")
            AccountDetailRow(label = "Phone", value = profile?.phone ?: "…")
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
            LimePillButton(
                text = "Log out of Telegram Account",
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Room for the floating pill: last content scrolls fully into view.
        Spacer(Modifier.height(116.dp))
    }
    Spacer(Modifier.height(4.dp))

    if (showLogout) {
        IosDialog(
            onDismiss = { showLogout = false },
            title = "Log out?",
            buttons = listOf(
                IosSheetButton(label = "Cancel", onClick = { showLogout = false }),
                IosSheetButton(
                    label = "Yes, log out",
                    isDestructive = true,
                    onClick = {
                        showLogout = false
                        showLogoutFinal = true
                    },
                ),
            ),
        ) {
            Text(
                "You'll need to enter your phone number again next time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
        }
    }
    // Second confirmation (moon's request 2026-11-04): double-confirm logout.
    if (showLogoutFinal) {
        AlertDialog(
            onDismissRequest = { showLogoutFinal = false },
            title = {
                Text(
                    "Really log out?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
            },
            text = {
                Text(
                    "This will stop the server and delete your local library. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            },
            confirmButton = {
                LimePillButton(
                    text = "Log out",
                    fontSize = 14,
                    onClick = {
                        scope.launch {
                            try {
                                BackendProvider.backend.logout()
                            } catch (e: Exception) {
                                // Session teardown is best-effort; the local
                                // state must still reset so the app can't get
                                // stuck half-logged-in.
                            }
                            // Stop the server and wipe the old account's
                            // library so a different login can't see it.
                            try {
                                if (AppUiState.serverOn) AppUiState.toggleServer(context)
                            } catch (_: Exception) {}
                            try {
                                val db = MsDatabase.get(context)
                                db.clearAllTables()
                            } catch (_: Exception) {}
                            AppUiState.setLoggedOut(context)
                            showLogoutFinal = false
                            MsToast.show("Logged out")
                            onLoggedOut()
                        }
                    },
                )
            },
            dismissButton = {
                MsOutlinePill(text = "Cancel", fontSize = 14, onClick = { showLogoutFinal = false })
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
            .padding(vertical = 12.dp),
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
    val streamMb = AppUiState.streamCacheMb
    val bar = remember { Animatable(used.toFloat() / max) }

    // Measure the real cache dir size when this block appears.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        AppUiState.refreshCacheUsed(context)
    }

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
                    scope.launch(Dispatchers.IO) {
                        // 1. App cache (works now).
                        val freed = CacheManager.clear(context)
                        // 1b. In-app player byte window (tdfile:// chunks).
                        // Ours to delete, unlike Telegram's own download dir.
                        val chunksFreed = TdStreamCache.clear(context)
                        AppUiState.refreshCacheUsed(context)
                        // 2. Stream cache via the backend contract (moon's pick
                        // 2026-11-02). Currently a no-op until the Telegram
                        // module implements deleteCachedFile — then this
                        // clears it safely via TdApi.DeleteFile.
                        var streamCleared = 0
                        var streamTotal = 0
                        var streamFailed = 0
                        val beforeStream = CacheManager.streamCacheBytes(context)
                        try {
                            val db = MsDatabase.get(context)
                            val backend = BackendProvider.backend
                            val files = db.streamDao().getAll()
                            streamTotal = files.size
                            for ((i, f) in files.withIndex()) {
                                try {
                                    if (backend.deleteCachedFile(f.channelId, f.messageId)) {
                                        streamCleared++
                                    } else {
                                        streamFailed++
                                    }
                                } catch (e: Exception) {
                                    streamFailed++
                                }
                                // Each file may need a Telegram lookup now, so
                                // show that the loop is alive.
                                if ((i + 1) % 25 == 0) {
                                    AppLog.i("CACHE", "stream cache: ${i + 1}/$streamTotal files checked")
                                }
                            }
                            AppUiState.refreshCacheUsed(context)
                        } catch (e: Exception) {
                            AppLog.w("CACHE", "stream cache clear failed: ${e.message}")
                        }
                        val afterStream = CacheManager.streamCacheBytes(context)
                        AppLog.i(
                            "CACHE",
                            "Clear Cache: app ${freed / (1024 * 1024)} MB, player chunks " +
                                "${chunksFreed / (1024 * 1024)} MB, Telegram copies " +
                                "$streamCleared/$streamTotal deleted ($streamFailed failed) — " +
                                "stream cache ${beforeStream / (1024 * 1024)} → " +
                                "${afterStream / (1024 * 1024)} MB",
                        )
                        withContext(Dispatchers.Main) {
                            val msg = buildString {
                                append(if (freed > 0) "Cache cleared" else "Cache is already empty")
                                if (chunksFreed > 0) {
                                    append(" + ${chunksFreed / (1024 * 1024)} MB player chunks")
                                }
                                if (streamTotal > 0) {
                                    if (streamCleared > 0) {
                                        append(" + $streamCleared stream file(s) cleared")
                                    } else {
                                        append(" (stream cache needs the Telegram module update)")
                                    }
                                }
                            }
                            MsToast.show(msg)
                        }
                    }
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
        // Stream cache (moon's request 2026-11-02): everything the in-app
        // player leaves behind — Telegram's own download dir plus our
        // low-storage byte window (filesDir/tdstream). "Clear Cache" above
        // empties the window and asks the Telegram module to drop its copy.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stream cache",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Text(
                text = "$streamMb MB",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
            )
        }
        Text(
            text = "Video and subtitles you play in the app stream straight from Telegram — no addon server needed. Only the part you're watching is kept, watched parts are deleted as you go, and the rest goes when you close the player. Clear Cache above wipes whatever is left.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }
}
