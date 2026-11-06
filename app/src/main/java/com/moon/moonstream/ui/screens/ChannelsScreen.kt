package com.moon.moonstream.ui.screens

import android.widget.Toast
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.components.IosBottomSheet
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.unfocusOnTapOutside
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.backend.BackendAuthState
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.backend.MsChannel
import com.moon.moonstream.index.db.ChannelConfigEntity
import com.moon.moonstream.index.db.ChannelBreakdown
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.StremioIds
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanMode
import com.moon.moonstream.index.scan.ScanService
import com.moon.moonstream.index.scan.ScanTarget
import com.moon.moonstream.index.scan.ScanUiState
import com.moon.moonstream.ui.components.AnimeBadge
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.IosActionSheet
import com.moon.moonstream.ui.components.IosContextMenu
import com.moon.moonstream.ui.components.IosSheetButton
import com.moon.moonstream.ui.components.rememberAnimeCheckIcon
import com.moon.moonstream.ui.components.rememberDeleteIcon
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Thousands separator for the scan panel stats. */
private fun formatScanCount(n: Int): String =
    String.format(Locale.US, "%,d", n)

/** "Synced just now" / "Synced 2 min ago" / "Synced 1 h ago" / "Synced yesterday". */
private fun formatSyncedAt(at: Long): String {
    if (at <= 0L) return "Never synced"
    val mins = (System.currentTimeMillis() - at) / 60_000L
    return when {
        mins < 1 -> "Synced just now"
        mins < 60 -> "Synced $mins min ago"
        mins < 120 -> "Synced 1 h ago"
        mins < 1440 -> "Synced ${mins / 60} h ago"
        mins < 2880 -> "Synced yesterday"
        else -> "Synced ${mins / 1440} days ago"
    }
}

/** One channel row's data: live channel info + Room config + library stats. */
private data class UiChannel(
    val id: Long,
    val title: String,
    val breakdown: ChannelBreakdown,
    val lastSyncedAt: Long,
    val isAnime: Boolean,
)

private val DestructiveRed = Color(0xFFFF6B6B)

/**
 * App-level cache of channel rows. Tab switches destroy this screen's
 * composition, and refetching channels + breakdowns on every switch felt
 * like lag. Rows are cached here and only reloaded when the cache is empty
 * or [ScanUiState.channelListVersion] moved (a scan finished elsewhere).
 */
private object ChannelRowCache {
    var rows: List<UiChannel>? = null
    var version: Int = -1
}

/**
 * Channels — Phase 3, real data.
 *
 * Channel list comes from the backend contract; per-channel flags
 * (anime, removed, last synced) live in Room. Scans run in the foreground
 * [ScanService] so they survive navigation and backgrounding; this screen
 * only observes the app-level [ScanUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onCreateEntry: (UnmatchedFileEntity?) -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { MsDatabase.get(context) }

    // Channel rows are cached at app level (ChannelRowCache) so tab switches
    // don't refetch — that refetch-on-every-visit was the tab-switch lag.
    var channels by remember { mutableStateOf(ChannelRowCache.rows ?: emptyList()) }
    var loading by remember { mutableStateOf(ChannelRowCache.rows == null) }
    val listVersion by ScanUiState.channelListVersion.collectAsState()
    val unmatched by db.unmatchedDao().observeAll()
        .collectAsState(initial = emptyList())
    var unmatchedQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    // Bulk selection (moon's pick #5, 2026-11-01): select multiple unmatched
    // files and link them all to one title.
    var unmatchedSelectMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val unmatchedSelectedIds = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var linkFileBulk by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<List<UnmatchedFileEntity>>(emptyList())
    }
    val filteredUnmatched = androidx.compose.runtime.remember(unmatched, unmatchedQuery) {
        if (unmatchedQuery.isBlank()) unmatched
        else unmatched.filter { it.fileName.contains(unmatchedQuery, ignoreCase = true) }
    }

    val scanMap by ScanUiState.channels.collectAsState()
    val totals by ScanUiState.totals.collectAsState()
    val scanning by ScanUiState.scanning.collectAsState()
    val panelVisible by ScanUiState.panelVisible.collectAsState()
    val panelFinished by ScanUiState.panelFinished.collectAsState()

    var menuFor by remember { mutableStateOf<Long?>(null) }
    var confirmRemove by remember { mutableStateOf<UiChannel?>(null) }
    var linkFile by remember { mutableStateOf<UnmatchedFileEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    suspend fun reloadChannels(silent: Boolean = false) {
        if (!silent) loading = true
        try {
            val backend = BackendProvider.backend
            if (backend.authState.value != BackendAuthState.READY) {
                channels = emptyList()
                ChannelRowCache.rows = null
                ChannelRowCache.version = -1
                return
            }
            val list = backend.listChannels()
            val cfgDao = db.channelConfigDao()
            val configs = cfgDao.all().associateBy { it.channelId }
            val liveTitles = list.associate { it.id to it.title }
            val result = mutableListOf<UiChannel>()
            // Opt-in only: no auto-creation. Rows come from configs the user
            // explicitly added (removed=false), joined with live titles.
            for (cfg in configs.values) {
                if (cfg.removed) continue
                val liveTitle = liveTitles[cfg.channelId]
                var cur = cfg
                if (liveTitle != null && liveTitle != cfg.title) {
                    cur = cfg.copy(title = liveTitle)
                    cfgDao.upsert(cur)
                }
                val breakdown = db.mediaDao().breakdownByChannel(cur.channelId)
                result.add(
                    UiChannel(
                        id = cur.channelId,
                        title = cur.title,
                        breakdown = breakdown,
                        lastSyncedAt = cur.lastSyncedAt,
                        isAnime = cur.isAnime,
                    ),
                )
            }
            channels = result
            ChannelRowCache.rows = result
            ChannelRowCache.version = ScanUiState.channelListVersion.value
        } catch (e: Exception) {
            // Only toast if we have nothing to show (moon's bug 2026-11-04:
            // transient failure toasted even though retry succeeded seconds later).
            if (channels.isNullOrEmpty() && ChannelRowCache.rows.isNullOrEmpty()) {
                MsToast.show("Couldn't load channels")
            }
        } finally {
            if (!silent) loading = false
        }
    }

    // Load once; reuse the app-level cache on tab switches unless a scan
    // finished while we were away (version moved).
    LaunchedEffect(Unit) {
        val cached = ChannelRowCache.rows
        if (cached != null && ChannelRowCache.version == ScanUiState.channelListVersion.value) {
            channels = cached
            loading = false
        } else {
            reloadChannels()
        }
    }

    // When a run finishes: refresh counts, then auto-dismiss the panel.
    LaunchedEffect(panelFinished) {
        if (panelFinished) {
            reloadChannels(silent = true)
            delay(4000)
            if (ScanUiState.panelFinished.value) ScanUiState.dismissPanel()
        }
    }

    fun quickScan(ch: UiChannel) {
        ScanService.start(
            context,
            listOf(ScanTarget(ch.id, ch.title, ch.isAnime)),
            ScanMode.INCREMENTAL,
        )
    }

    fun rescanScratch(ch: UiChannel) {
        ScanService.start(
            context,
            listOf(ScanTarget(ch.id, ch.title, ch.isAnime)),
            ScanMode.FROM_SCRATCH,
        )
        MsToast.show("Full rescan started")
    }

    fun scanAll() {
        val targets = channels.map { ScanTarget(it.id, it.title, it.isAnime) }
        if (targets.isEmpty()) {
            MsToast.show("No channels to scan")
            return
        }
        ScanService.start(context, targets, ScanMode.INCREMENTAL)
    }

    /** Explicit opt-in add: create the config, then kick off its first scan. */
    fun addChannel(id: Long, title: String, isAnime: Boolean) {
        scope.launch {
            db.channelConfigDao().upsert(
                ChannelConfigEntity(
                    channelId = id,
                    title = title,
                    isAnime = isAnime,
                    removed = false,
                ),
            )
            reloadChannels(silent = true)
            ScanService.start(
                context,
                listOf(ScanTarget(id, title, isAnime)),
                ScanMode.INCREMENTAL,
            )
            MsToast.show("Added $title — scan started")
        }
    }

    fun toggleAnime(ch: UiChannel) {
        scope.launch {
            db.channelConfigDao().get(ch.id)?.let { cfg ->
                db.channelConfigDao().upsert(cfg.copy(isAnime = !cfg.isAnime))
            }
            channels = channels.map {
                if (it.id == ch.id) it.copy(isAnime = !it.isAnime) else it
            }
            MsToast.show(if (!ch.isAnime) "Marked as anime channel" else "Unmarked as anime channel")
        }
    }

    fun removeChannel(ch: UiChannel) {
        scope.launch {
            db.streamDao().deleteByChannel(ch.id)
            db.mediaDao().deleteEmpty()
            db.unmatchedDao().deleteByChannel(ch.id)
            db.scanCursorDao().clear(ch.id)
            db.channelConfigDao().get(ch.id)?.let { cfg ->
                db.channelConfigDao().upsert(cfg.copy(removed = true))
            }
            channels = channels.filter { it.id != ch.id }
            MsToast.show("Removed ${ch.title}")
        }
    }

    val doneCount = scanMap.values.count { it.state == ScanUiState.ChState.DONE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = AppUiState.restoreScroll("channels_list"),
        )
        DisposableEffect(Unit) {
            onDispose {
                AppUiState.saveScroll("channels_list", listState.firstVisibleItemIndex)
            }
        }
        // Tap outside the unmatched search box to unfocus it (moon's 2026-11-06).
        LazyColumn(
            modifier = Modifier.fillMaxSize().unfocusOnTapOutside(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Channels",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MsColors.TextPrimary,
                    )
                    ScanAllButton(
                        scanning = scanning,
                        doneCount = doneCount,
                        total = scanMap.size.coerceAtLeast(channels.size),
                        onClick = ::scanAll,
                    )
                }
            }

            if (loading && channels.isEmpty()) {
                item(key = "loading") {
                    Text(
                        "Loading channels…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(channels, key = { it.id }) { ch ->
                val prog = scanMap[ch.id]
                ChannelRow(
                    channel = ch,
                    progress = prog?.progress,
                    scanning = prog?.state == ScanUiState.ChState.SCANNING,
                    done = prog?.state == ScanUiState.ChState.DONE,
                    menuOpen = menuFor == ch.id,
                    onMenuOpen = { menuFor = ch.id },
                    onMenuClose = { menuFor = null },
                    onScan = { quickScan(ch) },
                    onRescanScratch = { rescanScratch(ch) },
                    onToggleAnime = { toggleAnime(ch) },
                    onCopyId = {
                        clipboard.setText(AnnotatedString(ch.id.toString()))
                        MsToast.show("Channel ID copied")
                    },
                    onRemove = { confirmRemove = ch },
                )
            }

            item(key = "refreshChannels") {
                LimePillButton(
                    text = "↻ Refresh channels",
                    onClick = { scope.launch { reloadChannels() } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "addChannel") {
                LimePillButton(
                    text = "+ Add Channel",
                    onClick = { showAddSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "addCustomContent") {
                LimePillButton(
                    text = "＋ Add Custom Content",
                    onClick = { onCreateEntry(null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "unmatchedSearch") {
                com.moon.moonstream.ui.components.MsTextField(
                    value = unmatchedQuery,
                    onValueChange = { unmatchedQuery = it },
                    hint = "Search unmatched files…",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "unmatchedHeader") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Unmatched content (${filteredUnmatched.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MsColors.TextPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp, end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Select button (moon's request 2026-11-04): enters select mode.
                    // Title uses weight + ellipsis so large counts (1000+) don't overlap.
                    if (unmatchedSelectMode) {
                        LimeTextButton(
                            text = "Done (${unmatchedSelectedIds.size})",
                            onClick = {
                                unmatchedSelectMode = false
                                unmatchedSelectedIds.clear()
                            },
                        )
                    } else {
                        LimeTextButton(
                            text = "Select",
                            onClick = { unmatchedSelectMode = true },
                        )
                    }
                }
                // Bulk actions when selecting (moon's pick #5).
                if (unmatchedSelectMode && unmatchedSelectedIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LimePillButton(
                            text = "Link ${unmatchedSelectedIds.size} to Title",
                            onClick = {
                                linkFileBulk = filteredUnmatched.filter {
                                    unmatchedSelectedIds.contains(it.id)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            items(filteredUnmatched, key = { it.id }) { f ->
                // Direct stream URL for files not in the library.
                val directUrl = "http://${AppUiState.currentIp}:${AppUiState.streamPort}/stream/direct/${f.channelId}/${f.messageId}"
                UnmatchedFileRow(
                    file = f,
                    selectMode = unmatchedSelectMode,
                    isSelected = unmatchedSelectedIds.contains(f.id),
                    onSelect = {
                        if (unmatchedSelectedIds.contains(f.id)) {
                            unmatchedSelectedIds.remove(f.id)
                            if (unmatchedSelectedIds.isEmpty()) unmatchedSelectMode = false
                        } else {
                            if (!unmatchedSelectMode) unmatchedSelectMode = true
                            unmatchedSelectedIds.add(f.id)
                        }
                    },
                    onCreateEntry = onCreateEntry,
                    onCopyLink = {
                        clipboard.setText(AnnotatedString(directUrl))
                        MsToast.show("Stream link copied")
                    },
                    onLinkToTitle = { linkFile = f },
                    onPlay = {
                        if (!AppUiState.serverOn) {
                            MsToast.show("Turn the server on first, then play")
                        } else {
                            val uri = android.net.Uri.parse(directUrl)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeForFile(f.fileName))
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(android.content.Intent.createChooser(intent, "Play with"))
                            } catch (e: android.content.ActivityNotFoundException) {
                                MsToast.show("No video player found on this phone")
                            }
                        }
                    },
                )
            }

            item(key = "bottomPad") {
                Spacer(Modifier.height(150.dp))
            }
        }

        ScanResultsPanel(
            visible = panelVisible,
            finished = panelFinished,
            stats = ScanStats(totals.indexed, totals.existing, totals.unmatched, totals.checked),
            onClose = { ScanUiState.dismissPanel() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp),
        )
    }

    confirmRemove?.let { ch ->
        IosActionSheet(
            onDismiss = { confirmRemove = null },
            title = "Remove channel?",
            message = "This deletes every indexed title, file, unmatched entry and the " +
                "scan history for \"${ch.title}\". The channel itself stays " +
                "in your Telegram account.",
            buttons = listOf(
                IosSheetButton(
                    label = "Remove",
                    isDestructive = true,
                    onClick = {
                        removeChannel(ch)
                        confirmRemove = null
                    },
                ),
            ),
        )
    }

    linkFile?.let { f ->
        MetadataFixSheet(
            mode = FixMode.LINK,
            currentTitle = f.fileName,
            isAnime = false,
            initialQuery = f.fileName,
            onDismiss = { linkFile = null },
            onApply = { picked ->
                scope.launch {
                    val norm = normalizeTitle(picked.title)
                    val now = System.currentTimeMillis()
                    val chAnime = channels.find { it.id == f.channelId }?.isAnime ?: false
                    val parsed = FilenameParser.parse(
                        f.fileName, chAnime, "",
                        ScanPrefs.isAnimeAbsolute(context),
                    )
                    // ONE path for every button: reuse the entry that already
                    // exists (same IMDb id anywhere, else same title in this
                    // channel) and only create a new one if there is none.
                    // Before this, "New entry/episode" always inserted a row,
                    // so linking created duplicates.
                    val existing = picked.imdbId?.let { db.mediaDao().findByImdb(it) }
                        ?: db.mediaDao().findByNormAnyChannel(norm)
                    // FIX 2026-11-05: if linking to an existing entry that lacks
                    // an IMDb ID, update it with the picked one (needed for Skip Intro)
                    if (existing != null && existing.imdbId.isNullOrBlank() && !picked.imdbId.isNullOrBlank()) {
                        db.mediaDao().updateImdbId(existing.id, picked.imdbId!!)
                    }
                    val mediaId = existing?.id ?: db.mediaDao().upsert(
                        MediaItemEntity(
                            channelId = f.channelId,
                            title = picked.title,
                            normalizedTitle = norm,
                            year = picked.year,
                            type = parsed.type, // movie vs series from the file itself
                            imdbId = picked.imdbId,
                            tmdbId = picked.tmdbId,
                            stremioId = StremioIds.forNewItem(
                                f.channelId, norm, picked.imdbId,
                            ),
                            posterUrl = picked.posterUrl,
                            overview = picked.overview,
                            totalSize = 0L,
                            episodeCount = 0,
                            fileCount = 0,
                            updatedAt = now,
                        ),
                    )
                    db.streamDao().insert(
                        StreamFileEntity(
                            mediaId = mediaId,
                            channelId = f.channelId,
                            messageId = f.messageId,
                            fileName = f.fileName,
                            fileSize = f.fileSize,
                            season = parsed.season,
                            episode = parsed.episode,
                            quality = parsed.qualityBadge,
                        ),
                    )
                    db.mediaDao().refreshAggregates(mediaId, now)
                    db.unmatchedDao().delete(f.id)
                    MsToast.show(
                        if (existing != null) "Linked to \u201c${existing.title}\u201d"
                        else "Created \u201c${picked.title}\u201d",
                    )
                    linkFile = null
                }
            },
        )
    }

    // Bulk link: all selected files -> one title (moon's pick #5).
    if (linkFileBulk.isNotEmpty()) {
        val first = linkFileBulk.first()
        MetadataFixSheet(
            mode = FixMode.LINK,
            currentTitle = first.fileName,
            isAnime = false,
            initialQuery = first.fileName,
            onDismiss = { linkFileBulk = emptyList() },
            onApply = { picked ->
                scope.launch {
                    val norm = normalizeTitle(picked.title)
                    val now = System.currentTimeMillis()
                    // Find or create ONE entry for all files.
                    val existing = picked.imdbId?.let { db.mediaDao().findByImdb(it) }
                        ?: db.mediaDao().findByNormAnyChannel(norm)
                    val mediaId = existing?.id ?: db.mediaDao().upsert(
                        MediaItemEntity(
                            channelId = first.channelId,
                            title = picked.title,
                            normalizedTitle = norm,
                            year = picked.year,
                            type = "series", // bulk link assumes series; user can fix
                            imdbId = picked.imdbId,
                            tmdbId = picked.tmdbId,
                            stremioId = StremioIds.forNewItem(
                                first.channelId, norm, picked.imdbId,
                            ),
                            posterUrl = picked.posterUrl,
                            overview = picked.overview,
                            totalSize = 0L,
                            episodeCount = 0,
                            fileCount = 0,
                            updatedAt = now,
                        ),
                    )
                    // Link every selected file.
                    var linked = 0
                    for (f in linkFileBulk) {
                        val chAnime = channels.find { it.id == f.channelId }?.isAnime ?: false
                        val parsed = FilenameParser.parse(
                            f.fileName, chAnime, "",
                            ScanPrefs.isAnimeAbsolute(context),
                        )
                        db.streamDao().insert(
                            StreamFileEntity(
                                mediaId = mediaId,
                                channelId = f.channelId,
                                messageId = f.messageId,
                                fileName = f.fileName,
                                fileSize = f.fileSize,
                                season = parsed.season,
                                episode = parsed.episode,
                                episodeEnd = parsed.episodeEnd,
                                quality = parsed.qualityBadge,
                            ),
                        )
                        db.unmatchedDao().delete(f.id)
                        linked++
                    }
                    db.mediaDao().refreshAggregates(mediaId, now)
                    MsToast.show("Linked $linked files to \u201c${picked.title}\u201d")
                    linkFileBulk = emptyList()
                    unmatchedSelectMode = false
                    unmatchedSelectedIds.clear()
                }
            },
        )
    }

    if (showAddSheet) {
        AddChannelSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { id, title, anime ->
                showAddSheet = false
                addChannel(id, title, anime)
            },
        )
    }
}

/** Live counters shown in the scan results panel (item 26). */
data class ScanStats(
    val indexed: Int,
    val existing: Int,
    val unmatched: Int,
    val checked: Int,
)

/**
 * Floating scan results panel, TeleStremio-style (item 26).
 * Eyebrow "SCAN", title "Scanning…" while live with four ticking stats,
 * flips to "Last scan finished" with final numbers, auto-dismisses after ~4s.
 * X button and horizontal swipe dismiss it manually. Coexists with the
 * per-channel progress bars (panel = totals, rows = per-channel).
 */
@Composable
private fun ScanResultsPanel(
    visible: Boolean,
    finished: Boolean,
    stats: ScanStats,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        var dragTotal by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        onDragEnd = { if (abs(dragTotal) > 80) onClose() },
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .background(MsColors.Surface)
                .border(1.dp, MsColors.Divider, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SCAN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MsColors.TextSecondary,
                        letterSpacing = 2.sp,
                    )
                    BareIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        onClick = onClose,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (finished) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MsColors.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MsColors.OnAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (finished) "Last scan finished" else "Scanning…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MsColors.TextPrimary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScanStatCell(
                        value = stats.indexed,
                        label = "Indexed",
                        modifier = Modifier.weight(1f),
                    )
                    ScanStatCell(
                        value = stats.existing,
                        label = "Existing",
                        modifier = Modifier.weight(1f),
                    )
                    ScanStatCell(
                        value = stats.unmatched,
                        label = "Unmatched",
                        modifier = Modifier.weight(1f),
                    )
                    ScanStatCell(
                        value = stats.checked,
                        label = "Checked",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatCell(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MsColors.Bg)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            formatScanCount(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.Accent,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MsColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScanAllButton(
    scanning: Boolean,
    doneCount: Int,
    total: Int,
    onClick: () -> Unit,
) {
    when {
        scanning -> {
            val progress = if (total > 0) doneCount.toFloat() / total else 0f
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MsColors.SurfaceAlt)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Scanning $doneCount/$total…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .width(110.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MsColors.Accent,
                    trackColor = MsColors.TrackOff,
                )
            }
        }
        else -> LimePillButton(
            text = "Scan All Now",
            onClick = onClick,
            fontSize = 14,
        )
    }
}

/**
 * One channel row: name + breakdown + sync time, scan button, ⋮ overflow
 * menu, and the slim progress bar while its scan runs.
 */
@Composable
private fun ChannelRow(
    channel: UiChannel,
    progress: Float?,
    scanning: Boolean,
    done: Boolean,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuClose: () -> Unit,
    onScan: () -> Unit,
    onRescanScratch: () -> Unit,
    onToggleAnime: () -> Unit,
    onCopyId: () -> Unit,
    onRemove: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channel.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MsColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (channel.isAnime) {
                            Spacer(Modifier.width(8.dp))
                            AnimeBadge()
                        }
                    }
                    Text(
                        "${channel.breakdown.movies} Movies · ${channel.breakdown.shows} Shows · ${channel.breakdown.episodes} Episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatSyncedAt(channel.lastSyncedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.TextSecondary,
                    )
                }
                when {
                    done -> DoneCheck()
                    scanning -> { /* progress bar below carries the state */ }
                    else -> BareIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Scan Now",
                        tint = MsColors.TextSecondary,
                        onClick = onScan,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    BareIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "Channel options",
                        tint = MsColors.TextSecondary,
                        onClick = onMenuOpen,
                    )
                    // iOS context menu popup (moon's request 2026-11-04)
                    val animeCheckIcon = rememberAnimeCheckIcon()
                    val deleteIcon = rememberDeleteIcon()
                    IosContextMenu(
                        expanded = menuOpen,
                        onDismissRequest = onMenuClose,
                        popupAlignment = Alignment.BottomEnd,
                        anchorHeight = 48.dp,
                        buttons = listOf(
                            IosSheetButton(
                                "Rescan from scratch",
                                icon = Icons.Filled.Refresh,
                                onClick = { onMenuClose(); onRescanScratch() },
                            ),
                            IosSheetButton(
                                label = if (channel.isAnime) "Unmark as anime channel" else "Mark as anime channel",
                                icon = animeCheckIcon,
                                onClick = { onMenuClose(); onToggleAnime() },
                            ),
                            IosSheetButton(
                                "Copy channel ID",
                                icon = Icons.Filled.ContentCopy,
                                onClick = { onMenuClose(); onCopyId() },
                            ),
                            IosSheetButton(
                                "Remove channel",
                                icon = deleteIcon,
                                isDestructive = true,
                                onClick = { onMenuClose(); onRemove() },
                            ),
                        ),
                    )
                }
            }

            AnimatedVisibility(
                visible = scanning && !done,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(250)),
            ) {
                val p = (progress ?: 0f).coerceIn(0f, 1f)
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Scanning…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MsColors.TextSecondary,
                        )
                        Text(
                            "${(p * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MsColors.Accent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = p,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MsColors.Accent,
                        trackColor = MsColors.TrackOff,
                    )
                }
            }
        }
    }
}

/** Lime check badge that pops in with a spring when a scan finishes. */
@Composable
private fun DoneCheck(modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "donePop",
    )
    Box(
        modifier = modifier
            .size(28.dp)
            .scale(scale)
            .background(MsColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Scan done",
            tint = MsColors.OnAccent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One unmatched file row — kept light for 400+ item lists. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun UnmatchedFileRow(
    file: UnmatchedFileEntity,
    selectMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCreateEntry: (UnmatchedFileEntity?) -> Unit,
    onCopyLink: () -> Unit,
    onLinkToTitle: () -> Unit,
    onPlay: () -> Unit,
) {
    MsCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectMode) onSelect() },
                onLongClick = onSelect,
            )
            .then(
                if (isSelected) Modifier.border(
                    2.dp, MsColors.Accent, RoundedCornerShape(12.dp)
                ) else Modifier
            ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(file.fileSize),
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            // Icon buttons in moon's order (2026-11-04): Play, Copy Link,
            // Create entry, Link to Title. Icons instead of text.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    onClick = onPlay,
                )
                BareIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Link",
                    onClick = onCopyLink,
                )
                BareIconButton(
                    icon = Icons.Filled.AddCircle,
                    contentDescription = "Create entry",
                    onClick = { onCreateEntry(file) },
                )
                BareIconButton(
                    icon = Icons.Filled.Link,
                    contentDescription = "Link to Title",
                    onClick = onLinkToTitle,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024
        u++
    }
    return if (u == 0) "$bytes B"
    else String.format(Locale.US, "%.1f %s", v, units[u])
}

private fun mimeForFile(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "srt" -> "text/plain"
        else -> "video/*"
    }
}

/**
 * Add Channel bottom sheet: lists the account's channels that are not yet
 * added (opt-in), each row with an anime toggle and an Add button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelSheet(
    onDismiss: () -> Unit,
    onAdd: (id: Long, title: String, isAnime: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var channelId by remember { mutableStateOf("") }
    var isAnime by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    IosBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        MsSheetContent(title = "Add Channel") {
            Text(
                "Paste the channel ID (the number from a t.me/c/… link).",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            MsTextField(
                value = channelId,
                onValueChange = { channelId = it.filter { c -> c.isDigit() || c == '-' } },
                hint = "Channel ID, e.g. 1234567890",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Anime channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                )
                MsToggle(checked = isAnime, onCheckedChange = { isAnime = it })
            }
            LimePillButton(
                text = if (adding) "Adding…" else "Add channel",
                onClick = {
                    val id = channelId.trim().toLongOrNull()
                    if (id == null || adding) {
                        if (id == null) MsToast.show("Enter a valid channel ID")
                        return@LimePillButton
                    }
                    adding = true
                    scope.launch {
                        // Use the ID as the title; the scanner fills the real name.
                        onAdd(id, "Channel $id", isAnime)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
