package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.QualityChip
import com.moon.moonstream.ui.model.FakeEpisode
import com.moon.moonstream.ui.model.FakeStream
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.model.formatSizeMb
import com.moon.moonstream.ui.model.parseSizeMb
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors

/**
 * Title detail. Series/anime group streams by season: season selector pills,
 * episode cards (number badge, thumbnail placeholder, quality chips, size),
 * tap an episode to expand its file rows. Movies keep the flat stream list.
 * Header shows entry totals ("8 Episodes · 14.7 GB").
 * TODO(Phase 3): load title + streams from Room; wire Play/Copy/Delete,
 * Add Custom Stream, Add Subtitle, Fix Metadata to the real library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(titleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val title = fakeTitles.find { it.id == titleId } ?: fakeTitles.first()
    val isSeries = title.type == "series" || title.type == "anime"

    // Flat movie streams (mutable for delete/hide).
    val movieStreams = remember(titleId) {
        mutableStateListOf<FakeStream>().apply { addAll(title.streams) }
    }
    // Per-episode visible file lists, keyed "s{season}e{number}" — hide removes
    // the file from the visible list (fake).
    val epFiles = remember(titleId) {
        mutableStateMapOf<String, MutableList<FakeStream>>().apply {
            title.seasons.forEach { s ->
                s.episodes.forEach { ep ->
                    put(epKey(s.season, ep.number), ep.streams.toMutableList())
                }
            }
        }
    }

    var fixMode by remember { mutableStateOf<FixMode?>(null) }
    var fixFileName by remember { mutableStateOf("") }
    var showAddStream by remember { mutableStateOf(false) }
    var showAddSubtitle by remember { mutableStateOf(false) }
    var seasonIdx by remember { mutableStateOf(0) }

    val totalsLine = if (isSeries) {
        val eps = title.seasons.sumOf { it.episodes.size }
        "$eps Episodes · ${formatSizeMb(title.totalSizeMb())}"
    } else {
        val n = movieStreams.size
        "$n file${if (n == 1) "" else "s"} · ${formatSizeMb(movieStreams.sumOf { parseSizeMb(it.size) })}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Gradient poster header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.verticalGradient(listOf(title.colorA, title.colorB))),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    ),
            )
            BareIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            BareIconButton(
                icon = Icons.Filled.VisibilityOff,
                contentDescription = "Hide entry",
                tint = Color.White,
                onClick = {
                    AppUiState.hiddenIds.add(titleId)
                    Toast.makeText(context, "Hidden — find it in the Hidden tab (fake)", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
            Text(
                text = title.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${title.year} · ${title.type.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                // Entry totals (item 17), NoxMedia-style.
                Text(
                    totalsLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.Accent,
                )
                Text(
                    title.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                )
            }

            // Action row: one lime pill + two outlined pills, equal widths.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LimePillButton(
                    text = "Fix Metadata",
                    onClick = { fixMode = FixMode.FIX_TITLE },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
                MsOutlinePill(
                    text = "Add Custom Stream",
                    onClick = { showAddStream = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 12,
                )
                MsOutlinePill(
                    text = "Add Subtitle",
                    onClick = { showAddSubtitle = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 12,
                )
            }

            if (isSeries) {
                SeriesSeasons(
                    titleId = titleId,
                    seasonIdx = seasonIdx,
                    onSeason = { seasonIdx = it },
                    epFiles = epFiles,
                    onPlay = { s ->
                        Toast.makeText(context, "Playing (fake)…", Toast.LENGTH_SHORT).show()
                    },
                    onCopy = { s ->
                        val url = "${AppUiState.manifestLink}/stream/${s.id}"
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { key, s ->
                        epFiles[key]?.remove(s)
                        Toast.makeText(context, "Stream deleted (fake)", Toast.LENGTH_SHORT).show()
                    },
                    onHide = { key, s ->
                        epFiles[key]?.remove(s)
                        Toast.makeText(context, "Stream hidden (fake)", Toast.LENGTH_SHORT).show()
                    },
                    onFixFile = { fileName ->
                        fixFileName = fileName
                        fixMode = FixMode.FIX_FILE
                    },
                )
            } else {
                Text(
                    "Streams (${movieStreams.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                movieStreams.forEach { s ->
                    StreamRow(
                        stream = s,
                        onPlay = {
                            Toast.makeText(context, "Playing (fake)…", Toast.LENGTH_SHORT).show()
                        },
                        onCopy = {
                            val url = "${AppUiState.manifestLink}/stream/${s.id}"
                            clipboard.setText(AnnotatedString(url))
                            Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            movieStreams.remove(s)
                            Toast.makeText(context, "Stream deleted (fake)", Toast.LENGTH_SHORT).show()
                        },
                        onFixFile = {
                            fixFileName = s.fileName
                            fixMode = FixMode.FIX_FILE
                        },
                    )
                }
            }
        }
    }

    fixMode?.let { mode ->
        MetadataFixSheet(
            mode = mode,
            currentTitle = if (mode == FixMode.FIX_FILE) fixFileName else title.title,
            onDismiss = { fixMode = null },
            onApply = { picked ->
                Toast.makeText(context, "Merged into $picked (fake)", Toast.LENGTH_LONG).show()
                fixMode = null
            },
        )
    }

    if (showAddStream) {
        SimpleFormSheet(
            title = "Add Custom Stream",
            fields = listOf("Telegram file link (t.me/…)", "Quality label (e.g. 1080p)"),
            onDismiss = { showAddStream = false },
            onSave = {
                Toast.makeText(context, "Custom stream added (fake)", Toast.LENGTH_SHORT).show()
                showAddStream = false
            },
        )
    }

    if (showAddSubtitle) {
        SimpleFormSheet(
            title = "Add Subtitle",
            fields = listOf("Subtitle file link", "Language"),
            onDismiss = { showAddSubtitle = false },
            onSave = {
                Toast.makeText(context, "Subtitle added (fake)", Toast.LENGTH_SHORT).show()
                showAddSubtitle = false
            },
        )
    }
}

private fun epKey(season: Int, number: Int) = "s${season}e$number"

/** Season selector pills + episode cards for series/anime (item 16). */
@Composable
private fun SeriesSeasons(
    titleId: String,
    seasonIdx: Int,
    onSeason: (Int) -> Unit,
    epFiles: MutableMap<String, MutableList<FakeStream>>,
    onPlay: (FakeStream) -> Unit,
    onCopy: (FakeStream) -> Unit,
    onDelete: (String, FakeStream) -> Unit,
    onHide: (String, FakeStream) -> Unit,
    onFixFile: (String) -> Unit,
) {
    val title = fakeTitles.find { it.id == titleId } ?: return
    val seasons = title.seasons
    if (seasons.isEmpty()) return
    val season = seasons[seasonIdx.coerceIn(seasons.indices)]
    val seasonSizeMb = season.episodes.sumOf { ep ->
        (epFiles[epKey(season.season, ep.number)] ?: ep.streams).sumOf { parseSizeMb(it.size) }
    }

    // Season selector pills
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        seasons.forEachIndexed { i, s ->
            val selected = i == seasonIdx
            if (selected) {
                LimePillButton(
                    text = "Season ${s.season}",
                    onClick = { onSeason(i) },
                    fontSize = 13,
                )
            } else {
                MsOutlinePill(
                    text = "Season ${s.season}",
                    onClick = { onSeason(i) },
                    fontSize = 13,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Season ${season.season}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )
        Text(
            "${season.episodes.size} Episodes · ${formatSizeMb(seasonSizeMb)}",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }

    season.episodes.forEach { ep ->
        EpisodeCard(
            episode = ep,
            files = epFiles[epKey(season.season, ep.number)] ?: ep.streams,
            onPlay = onPlay,
            onCopy = onCopy,
            onDelete = { s -> onDelete(epKey(season.season, ep.number), s) },
            onHide = { s -> onHide(epKey(season.season, ep.number), s) },
            onFixFile = onFixFile,
        )
    }
}

/** Episode card: number badge, thumbnail placeholder, quality chips, expandable files. */
@Composable
private fun EpisodeCard(
    episode: FakeEpisode,
    files: List<FakeStream>,
    onPlay: (FakeStream) -> Unit,
    onCopy: (FakeStream) -> Unit,
    onDelete: (FakeStream) -> Unit,
    onHide: (FakeStream) -> Unit,
    onFixFile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fileSizeMb = files.sumOf { parseSizeMb(it.size) }
    val qualities = files.map { it.quality }.distinct()

    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Thumbnail placeholder with episode number badge
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3A4356), Color(0xFF1E2532)))),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .size(22.dp)
                            .background(Color(0x99000000), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${episode.number}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "E${episode.number} · ${episode.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        qualities.forEach { q -> QualityChip(q) }
                        Text(
                            "· ${formatSizeMb(fileSizeMb)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MsColors.TextSecondary,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MsColors.TextSecondary,
                )
            }

            // Expandable file rows
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { s ->
                        FileRow(
                            stream = s,
                            onPlay = { onPlay(s) },
                            onCopy = { onCopy(s) },
                            onDelete = { onDelete(s) },
                            onHide = { onHide(s) },
                            onFixFile = { onFixFile(s.fileName) },
                        )
                    }
                }
            }
        }
    }
}

/** One file row inside an expanded episode. */
@Composable
private fun FileRow(
    stream: FakeStream,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onFixFile: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MsColors.Bg)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stream.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityChip(stream.quality)
                Spacer(Modifier.width(8.dp))
                Text(
                    stream.size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                BareIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    onClick = onPlay,
                )
                BareIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Link",
                    onClick = onCopy,
                )
                BareIconButton(
                    icon = Icons.Filled.VisibilityOff,
                    contentDescription = "Hide stream",
                    onClick = onHide,
                )
                BareIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    onClick = onDelete,
                )
            }
            LimeTextButton(
                text = "wrong title?",
                onClick = onFixFile,
                fontSize = 12,
            )
        }
    }
}

@Composable
private fun StreamRow(
    stream: FakeStream,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFixFile: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityChip(stream.quality)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stream.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stream.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                BareIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    onClick = onPlay,
                )
                BareIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Link",
                    onClick = onCopy,
                )
                BareIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    onClick = onDelete,
                )
            }
            LimeTextButton(
                text = "wrong title?",
                onClick = onFixFile,
                fontSize = 13,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleFormSheet(
    title: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember { fields.associateWith { mutableStateOf("") } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = title) {
            fields.forEach { f ->
                MsTextField(
                    value = values[f]!!.value,
                    onValueChange = { values[f]!!.value = it },
                    hint = f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LimePillButton(
                text = "Save",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
