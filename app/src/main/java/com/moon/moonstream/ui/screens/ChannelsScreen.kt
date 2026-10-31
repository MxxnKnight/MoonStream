package com.moon.moonstream.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanMode
import com.moon.moonstream.index.scan.ScanService
import com.moon.moonstream.index.scan.ScanTarget
import com.moon.moonstream.index.scan.ScanUiState
import com.moon.moonstream.ui.components.AnimeBadge
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
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
 * Channels — Phase 3, real data.
 *
 * Channel list comes from the backend contract; per-channel flags
 * (anime, removed, last synced) live in Room. Scans run in the foreground
 * [ScanService] so they survive navigation and backgrounding; this screen
 * only observes the app-level [ScanUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onCreateEntry: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { MsDatabase.get(context) }

    var channels by remember { mutableStateOf<List<UiChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val unmatched by db.unmatchedDao().observeAll()
        .collectAsState(initial = emptyList())

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
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't load channels", Toast.LENGTH_SHORT).show()
        } finally {
            if (!silent) loading = false
        }
    }

    LaunchedEffect(Unit) { reloadChannels() }

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
        Toast.makeText(context, "Full rescan started", Toast.LENGTH_SHORT).show()
    }

    fun scanAll() {
        val targets = channels.map { ScanTarget(it.id, it.title, it.isAnime) }
        if (targets.isEmpty()) {
            Toast.makeText(context, "No channels to scan", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Added $title — scan started", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(
                context,
                if (!ch.isAnime) "Marked as anime channel" else "Unmarked as anime channel",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun removeChannel(ch: UiChannel) {
        scope.launch {
            db.mediaDao().deleteByChannel(ch.id)
            db.streamDao().deleteByChannel(ch.id)
            db.unmatchedDao().deleteByChannel(ch.id)
            db.scanCursorDao().clear(ch.id)
            db.channelConfigDao().get(ch.id)?.let { cfg ->
                db.channelConfigDao().upsert(cfg.copy(removed = true))
            }
            channels = channels.filter { it.id != ch.id }
            Toast.makeText(context, "Removed ${ch.title}", Toast.LENGTH_SHORT).show()
        }
    }

    val doneCount = scanMap.values.count { it.state == ScanUiState.ChState.DONE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                    onRefreshAvatar = {
                        Toast.makeText(
                            context,
                            "Avatars aren't exposed by the current backend",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onCopyId = {
                        clipboard.setText(AnnotatedString(ch.id.toString()))
                        Toast.makeText(context, "Channel ID copied", Toast.LENGTH_SHORT).show()
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

            item(key = "unmatchedHeader") {
                Text(
                    "Unmatched content (${unmatched.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item(key = "addCustomContent") {
                LimePillButton(
                    text = "＋ Add Custom Content",
                    onClick = onCreateEntry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            items(unmatched, key = { it.id }) { f ->
                UnmatchedFileRow(
                    file = f,
                    onCreateEntry = onCreateEntry,
                    onCopyLink = {
                        clipboard.setText(
                            AnnotatedString("${AppUiState.manifestLink}/stream/${f.channelId}/${f.messageId}"),
                        )
                        Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                    },
                    onLinkToTitle = { linkFile = f },
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
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove channel?") },
            text = {
                Text(
                    "This deletes every indexed title, file, unmatched entry and the " +
                        "scan history for \"${ch.title}\". The channel itself stays " +
                        "in your Telegram account.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeChannel(ch)
                        confirmRemove = null
                    },
                ) { Text("Remove", color = DestructiveRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            },
            containerColor = MsColors.Surface,
            titleContentColor = MsColors.TextPrimary,
            textContentColor = MsColors.TextSecondary,
        )
    }

    linkFile?.let { f ->
        MetadataFixSheet(
            mode = FixMode.LINK,
            currentTitle = f.fileName,
            isAnime = false,
            onDismiss = { linkFile = null },
            onApply = { picked ->
                scope.launch {
                    val norm = normalizeTitle(picked.title)
                    val media = db.mediaDao().findByTitle(f.channelId, norm)
                    if (media != null) {
                        val parsed = FilenameParser.parse(f.fileName)
                        db.streamDao().insert(
                            StreamFileEntity(
                                mediaId = media.id,
                                channelId = f.channelId,
                                messageId = f.messageId,
                                fileName = f.fileName,
                                fileSize = f.fileSize,
                                season = parsed.season,
                                episode = parsed.episode,
                                quality = parsed.qualityBadge,
                            ),
                        )
                        db.mediaDao().refreshAggregates(media.id, System.currentTimeMillis())
                        db.unmatchedDao().delete(f.id)
                        Toast.makeText(context, "Linked to ${media.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No library entry matches — use Create entry",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    linkFile = null
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
    onRefreshAvatar: () -> Unit,
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
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = onMenuClose,
                        modifier = Modifier.background(MsColors.Surface),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rescan from scratch") },
                            onClick = { onMenuClose(); onRescanScratch() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (channel.isAnime) "Unmark as anime channel"
                                    else "Mark as anime channel",
                                )
                            },
                            onClick = { onMenuClose(); onToggleAnime() },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh channel avatar") },
                            onClick = { onMenuClose(); onRefreshAvatar() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy channel ID") },
                            onClick = { onMenuClose(); onCopyId() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove channel", color = DestructiveRed) },
                            onClick = { onMenuClose(); onRemove() },
                            colors = MenuDefaults.itemColors(textColor = DestructiveRed),
                        )
                    }
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
private fun UnmatchedFileRow(
    file: UnmatchedFileEntity,
    onCreateEntry: () -> Unit,
    onCopyLink: () -> Unit,
    onLinkToTitle: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LimeTextButton(text = "Create entry", onClick = onCreateEntry)
                LimeTextButton(text = "Copy Link", onClick = onCopyLink)
                LimeTextButton(text = "Link to Title", onClick = onLinkToTitle)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MsDatabase.get(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var candidates by remember { mutableStateOf<List<MsChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var animeFor by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var addedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(Unit) {
        try {
            val backend = BackendProvider.backend
            if (backend.authState.value == BackendAuthState.READY) {
                val added = db.channelConfigDao().all()
                    .filter { !it.removed }
                    .map { it.channelId }
                    .toSet()
                candidates = backend.listChannels().filter { it.id !in added }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't load channels", Toast.LENGTH_SHORT).show()
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Add Channel") {
            when {
                loading -> Text(
                    "Loading channels…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                candidates.isEmpty() -> Text(
                    "All your channels are already added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.id }) { ch ->
                        val added = ch.id in addedIds
                        val anime = animeFor[ch.id] == true
                        MsCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        ch.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MsColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MsToggle(
                                            checked = anime,
                                            onCheckedChange = {
                                                animeFor = animeFor + (ch.id to it)
                                            },
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Anime",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MsColors.TextSecondary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                if (added) {
                                    Text(
                                        "Added",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MsColors.Accent,
                                    )
                                } else {
                                    LimePillButton(
                                        text = "Add",
                                        fontSize = 13,
                                        onClick = {
                                            addedIds = addedIds + ch.id
                                            scope.launch { onAdd(ch.id, ch.title, anime) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
