package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.ui.components.AnimeBadge
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.ui.model.FakeChannel
import com.moon.moonstream.ui.model.UnmatchedFile
import com.moon.moonstream.ui.model.fakeChannels
import com.moon.moonstream.ui.model.fakeUnmatched
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Thousands separator for the scan panel stats. */
private fun formatScanCount(n: Int): String =
    String.format(Locale.US, "%,d", n)

/**
 * Channels with fake data. The whole screen is ONE LazyColumn with stable keys
 * so the unmatched list stays smooth even with 400+ items.
 * TODO(Phase 3): real channel list, scan jobs, unmatched index from Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onCreateEntry: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val channels = remember { mutableStateListOf<FakeChannel>().apply { addAll(fakeChannels) } }
    val unmatched = remember { mutableStateListOf<UnmatchedFile>().apply { addAll(fakeUnmatched) } }

    // Premium scan treatment (v0.3.1): each active scan owns an Animatable
    // progress 0→1; the row reads anim.value so the bar + % tick smoothly.
    val scanAnims = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanAllDone by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var linkFile by remember { mutableStateOf<UnmatchedFile?>(null) }

    // Scan results panel (item 26, TeleStremio-style): bottom card with live
    // Indexed / Existing / Unmatched / Checked stats while scanning, flips to
    // "Last scan finished" with final numbers, auto-dismisses after ~4s.
    // scanGen guards the auto-dismiss against a newer scan starting meanwhile.
    var panelVisible by remember { mutableStateOf(false) }
    var panelFinished by remember { mutableStateOf(false) }
    var scanStats by remember { mutableStateOf(ScanStats(0, 0, 0, 0)) }
    var scanGen by remember { mutableStateOf(0) }

    fun fakeScan(id: String?, label: String) {
        val targets = if (id != null) listOf(id) else channels.map { it.id }
        if (targets.isEmpty()) return
        val gen = scanGen + 1
        scanGen = gen
        targets.forEach { tid -> scanAnims[tid] = Animatable(0f) }

        // Panel: show live, reset stats, tick toward fake targets over 2.4s.
        panelFinished = false
        scanStats = ScanStats(0, 0, 0, 0)
        panelVisible = true
        val n = targets.size
        val tIndexed = n * 17 + 5
        val tExisting = n * 34 + 12
        val tUnmatched = n * 2 + 1
        val tChecked = n * 53 + 9
        scope.launch {
            val steps = 24
            repeat(steps) { i ->
                delay(100)
                if (scanGen != gen) return@launch
                val f = (i + 1) / steps.toFloat()
                scanStats = ScanStats(
                    (tIndexed * f).toInt(),
                    (tExisting * f).toInt(),
                    (tUnmatched * f).toInt(),
                    (tChecked * f).toInt(),
                )
            }
        }

        scope.launch {
            targets.map { tid ->
                launch {
                    scanAnims[tid]?.animateTo(1f, tween(2400, easing = LinearEasing))
                    doneIds = doneIds + tid
                    delay(1300)
                    doneIds = doneIds - tid
                    scanAnims.remove(tid)
                }
            }.joinAll()
            if (id == null) {
                scanAllDone = true
                delay(1600)
                scanAllDone = false
            }
            if (scanGen != gen) return@launch
            scanStats = ScanStats(tIndexed, tExisting, tUnmatched, tChecked)
            panelFinished = true
            delay(4000)
            if (scanGen == gen && panelFinished) {
                panelVisible = false
                panelFinished = false
            }
        }
    }

    val anyScanning = scanAnims.isNotEmpty()
    val total = channels.size
    val doneCount = doneIds.size

    // The list lives in a Box so the floating scan results panel (item 26)
    // can overlay its bottom edge without blocking the row progress bars.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                    scanning = anyScanning,
                    doneCount = doneCount,
                    total = total,
                    showDone = scanAllDone,
                    onClick = { fakeScan(null, "All channels") },
                )
            }
        }

        items(channels, key = { it.id }) { ch ->
            ChannelRow(
                channel = ch,
                progress = scanAnims[ch.id]?.value,
                done = doneIds.contains(ch.id),
                onToggle = { ch.enabled = !ch.enabled },
                onScan = { fakeScan(ch.id, ch.name) },
            )
        }

        item(key = "addChannel") {
            LimePillButton(
                text = "+ Add Channel",
                onClick = { showAdd = true },
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

        // Dedicated entry point for custom content, separate from per-file actions.
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
                    clipboard.setText(AnnotatedString("${AppUiState.manifestLink}/stream/${f.id}"))
                    Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                },
                onLinkToTitle = { linkFile = f },
            )
        }

        item(key = "bottomPad") {
            // Room for the floating scan panel so it never covers content.
            Spacer(Modifier.height(150.dp))
        }
    }

    ScanResultsPanel(
        visible = panelVisible,
        finished = panelFinished,
        stats = scanStats,
        onClose = {
            panelVisible = false
            panelFinished = false
        },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }

    if (showAdd) {
        AddChannelSheet(
            onDismiss = { showAdd = false },
            onAdd = { name, anime ->
                channels.add(FakeChannel("c${channels.size + 1}", name, 0, anime, true))
                Toast.makeText(context, "Channel added (fake)", Toast.LENGTH_SHORT).show()
                showAdd = false
            },
        )
    }

    linkFile?.let { f ->
        MetadataFixSheet(
            mode = FixMode.LINK,
            currentTitle = f.fileName,
            onDismiss = { linkFile = null },
            onApply = { picked ->
                unmatched.remove(f)
                Toast.makeText(context, "Linked to $picked (fake)", Toast.LENGTH_LONG).show()
                linkFile = null
            },
        )
    }
}

/**
 * Scan All Now pill: idle → collective progress ("Scanning 2/4…" with a slim
 * determinate bar) → satisfying "Done" pop. No raw spinner anywhere.
 */

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
    showDone: Boolean,
    onClick: () -> Unit,
) {
    when {
        showDone -> DonePill(text = "All scanned")
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

@Composable
private fun ChannelRow(
    channel: FakeChannel,
    progress: Float?,
    done: Boolean,
    onToggle: () -> Unit,
    onScan: () -> Unit,
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
                            channel.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MsColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (channel.isAnime) {
                            Spacer(Modifier.width(8.dp))
                            AnimeBadge()
                        }
                    }
                    Text(
                        "${channel.files} files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
                // Right side: the progress lives UNDER the name (below), so
                // here we only swap scan button → done check.
                when {
                    done -> DoneCheck()
                    progress == null -> BareIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Scan Now",
                        tint = MsColors.TextSecondary,
                        onClick = onScan,
                    )
                    // while scanning, the toggle stays put; progress is below
                }
                Spacer(Modifier.width(4.dp))
                MsToggle(checked = channel.enabled, onCheckedChange = { onToggle() })
            }

            // Sleek progress treatment under the channel name: % label +
            // slim accent bar, morphing into the checkmark when done.
            AnimatedVisibility(
                visible = progress != null && !done,
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

@Composable
private fun DonePill(text: String) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "donePillPop",
    )
    Row(
        modifier = Modifier
            .scale(scale)
            .background(MsColors.Accent, CircleShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MsColors.OnAccent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.OnAccent,
        )
    }
}

/** One unmatched file row — kept light for 400+ item lists. */
@Composable
private fun UnmatchedFileRow(
    file: UnmatchedFile,
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
                file.size,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelSheet(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var anime by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Add Channel") {
            MsTextField(
                value = name,
                onValueChange = { name = it },
                hint = "Username, link or channel ID",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MsToggle(checked = anime, onCheckedChange = { anime = it })
                Spacer(Modifier.width(10.dp))
                Text(
                    "This is an anime channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                )
            }
            LimePillButton(
                text = "Add",
                onClick = { onAdd(name.ifBlank { "New Channel" }, anime) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
