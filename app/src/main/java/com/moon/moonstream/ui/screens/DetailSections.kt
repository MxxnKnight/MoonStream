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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.SubtitleFileEntity
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.QualityChip
import com.moon.moonstream.ui.model.formatBytes
import com.moon.moonstream.ui.theme.MsColors

/** t.me/c/<chat>/<msg> → (channelId, messageId). Username links can't be
 *  resolved without a contract method, so they return null. */
fun parseTgLink(link: String): Pair<Long, Long>? {
    val m = Regex("""t\.me/c/(\d+)/(\d+)""").find(link.trim()) ?: return null
    val channelId = ("-100" + m.groupValues[1]).toLongOrNull() ?: return null
    val messageId = m.groupValues[2].toLongOrNull() ?: return null
    return channelId to messageId
}

/** Season selector pills + episode cards for series/anime (real data). */
@Composable
fun SeriesSeasons(
    media: MediaItemEntity,
    files: List<StreamFileEntity>,
    subtitles: List<SubtitleFileEntity>,
    seasonIdx: Int,
    onSeason: (Int) -> Unit,
    onPlay: (StreamFileEntity) -> Unit,
    onCopy: (StreamFileEntity) -> Unit,
    onDelete: (StreamFileEntity) -> Unit,
    onFixFile: (StreamFileEntity) -> Unit,
    onUnlinkSub: (SubtitleFileEntity) -> Unit,
) {
    val bySeason = files.groupBy { it.season ?: 0 }
    val seasons = bySeason.keys.sorted()
    if (seasons.isEmpty()) {
        Text(
            "No files indexed for this entry yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MsColors.TextSecondary,
        )
        return
    }
    val season = seasons[seasonIdx.coerceIn(seasons.indices)]
    val seasonFiles = bySeason[season].orEmpty()
    val byEpisode = seasonFiles.groupBy { it.episode }
    val episodes = byEpisode.keys.sortedBy { it ?: Int.MAX_VALUE }

    if (seasons.size > 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            seasons.forEachIndexed { i, s ->
                val selected = i == seasonIdx.coerceIn(seasons.indices)
                val label = if (s == 0) "Other" else "Season $s"
                if (selected) {
                    LimePillButton(text = label, onClick = { onSeason(i) }, fontSize = 13)
                } else {
                    MsOutlinePill(text = label, onClick = { onSeason(i) }, fontSize = 13)
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (season == 0) "Other files" else "Season $season",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )
        val epCount = episodes.count { it != null }
        Text(
            "$epCount Episodes · ${formatBytes(seasonFiles.sumOf { it.fileSize })}",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }

    episodes.forEach { ep ->
        val epFiles = byEpisode[ep].orEmpty()
        val epSubs = subtitles.filter { it.season == season && it.episode == ep }
        EpisodeCard(
            season = season,
            episode = ep,
            files = epFiles,
            subtitles = epSubs,
            onPlay = onPlay,
            onCopy = onCopy,
            onDelete = onDelete,
            onFixFile = onFixFile,
            onUnlinkSub = onUnlinkSub,
        )
    }
}

/** Episode card: number badge, expandable file rows + its subtitles. */
@Composable
private fun EpisodeCard(
    season: Int,
    episode: Int?,
    files: List<StreamFileEntity>,
    subtitles: List<SubtitleFileEntity>,
    onPlay: (StreamFileEntity) -> Unit,
    onCopy: (StreamFileEntity) -> Unit,
    onDelete: (StreamFileEntity) -> Unit,
    onFixFile: (StreamFileEntity) -> Unit,
    onUnlinkSub: (SubtitleFileEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val qualities = files.map { it.quality }.distinct().take(3)

    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3A4356), Color(0xFF1E2532)),
                            ),
                        ),
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
                            "${episode ?: "–"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                    if (subtitles.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Subtitles,
                            contentDescription = "Has subtitles",
                            tint = MsColors.Accent,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (episode != null) "Episode $episode" else "Files",
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
                            "· ${formatBytes(files.sumOf { it.fileSize })}",
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

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { f ->
                        FileRow(
                            stream = f,
                            onPlay = { onPlay(f) },
                            onCopy = { onCopy(f) },
                            onDelete = { onDelete(f) },
                            onFixFile = { onFixFile(f) },
                        )
                    }
                    if (subtitles.isNotEmpty()) {
                        SubtitleSection(
                            title = "Subtitles",
                            subs = subtitles,
                            onUnlink = onUnlinkSub,
                        )
                    }
                }
            }
        }
    }
}

/** One file row inside an expanded episode / movie list. */
@Composable
fun FileRow(
    stream: StreamFileEntity,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFixFile: () -> Unit,
) {
    val context = LocalContext.current
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
                    formatBytes(stream.fileSize),
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

/** Flat file row for movies. */
@Composable
fun StreamRow(
    stream: StreamFileEntity,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFixFile: () -> Unit,
) {
    val context = LocalContext.current
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
                        formatBytes(stream.fileSize),
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

/** Linked subtitles with language labels. */
@Composable
fun SubtitleSection(
    title: String,
    subs: List<SubtitleFileEntity>,
    onUnlink: (SubtitleFileEntity) -> Unit,
) {
    if (subs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$title (${subs.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextSecondary,
        )
        subs.forEach { sub ->
            SubtitleRow(sub = sub, actionLabel = null, onAction = {}, onDelete = { onUnlink(sub) })
        }
    }
}

/** One subtitle row: language chip + filename + optional action + optional delete. */
@Composable
fun SubtitleRow(
    sub: SubtitleFileEntity,
    actionLabel: String?,
    onAction: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MsColors.SurfaceAlt)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MsColors.Accent.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                sub.language,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MsColors.Accent,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            sub.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            LimeTextButton(text = actionLabel, fontSize = 13, onClick = onAction)
        }
        if (onDelete != null) {
            BareIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Remove subtitle",
                onClick = onDelete,
            )
        }
    }
}

/** Attach dialog: pick the season/episode an unlinked subtitle belongs to. */
@Composable
fun AttachSubtitleDialog(
    sub: SubtitleFileEntity,
    isSeries: Boolean,
    onDismiss: () -> Unit,
    onAttach: (Int?, Int?) -> Unit,
) {
    var season by remember { mutableStateOf(sub.season?.toString() ?: "") }
    var episode by remember { mutableStateOf(sub.episode?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MsColors.Surface,
        titleContentColor = MsColors.TextPrimary,
        textContentColor = MsColors.TextSecondary,
        title = { Text("Attach subtitle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    sub.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSeries) {
                    MsTextField(
                        value = season,
                        onValueChange = { season = it.filter(Char::isDigit).take(2) },
                        hint = "Season",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MsTextField(
                        value = episode,
                        onValueChange = { episode = it.filter(Char::isDigit).take(4) },
                        hint = "Episode",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Attaches to this movie.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            LimeTextButton(
                text = "Attach",
                fontSize = 14,
                onClick = {
                    onAttach(
                        season.toIntOrNull(),
                        episode.toIntOrNull(),
                    )
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MsColors.TextSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStreamSheet(
    onDismiss: () -> Unit,
    onSave: (link: String, quality: String, name: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var link by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("1080p") }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Add Custom Stream") {
            Text(
                "Paste a t.me/c/…/… message link from a channel. The file streams through the existing message id.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            MsTextField(
                value = link, onValueChange = { link = it },
                hint = "Telegram message link (t.me/c/…/…)",
                modifier = Modifier.fillMaxWidth(),
            )
            MsTextField(
                value = name, onValueChange = { name = it },
                hint = "File name (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            MsTextField(
                value = quality, onValueChange = { quality = it },
                hint = "Quality label (e.g. 1080p)",
                modifier = Modifier.fillMaxWidth(),
            )
            LimePillButton(
                text = "Add stream",
                onClick = { onSave(link, quality, name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubtitleSheet(
    unlinked: List<SubtitleFileEntity>,
    onAttachExisting: (SubtitleFileEntity) -> Unit,
    onDismiss: () -> Unit,
    onSaveLink: (link: String, language: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var link by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Mal") }
    val langOptions = listOf("Mal" to "Malayalam", "Eng" to "English", "Hin" to "Hindi", "Tam" to "Tamil", "Tel" to "Telugu")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Add Subtitle") {
            if (unlinked.isNotEmpty()) {
                Text(
                    "Unlinked in this channel (${unlinked.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextSecondary,
                )
                unlinked.take(8).forEach { sub ->
                    SubtitleRow(
                        sub = sub,
                        actionLabel = "Attach",
                        onAction = { onAttachExisting(sub) },
                        onDelete = null,
                    )
                }
                if (unlinked.size > 8) {
                    Text(
                        "…and ${unlinked.size - 8} more (see the Unlinked section below).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Or add via Telegram link",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextSecondary,
            )
            MsTextField(
                value = link, onValueChange = { link = it },
                hint = "Subtitle message link (t.me/c/…/…)",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                langOptions.forEach { (code, label) ->
                    val sel = language == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) MsColors.Accent else MsColors.SurfaceAlt)
                            .clickable { language = code }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) MsColors.OnAccent else MsColors.TextSecondary,
                        )
                    }
                }
            }
            LimePillButton(
                text = "Add subtitle",
                onClick = { onSaveLink(link, language) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
