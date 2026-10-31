package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.StremioIds
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.SubtitleFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FilePickerSheet
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsSectionLabel
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.model.CreatePrefill
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.ui.model.subtitleLanguages
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create Custom Content — the real thing. Builds a library entry by hand:
 * title, poster, description, and Telegram files picked from the unmatched
 * list (files the scanner couldn't identify — class videos, personal
 * rips, anything that's not a real show).
 *
 * The entry is stored like any other library item (so the server, backup
 * and Nuvio all see it), flagged [MediaItemEntity.isCustom] so it is never
 * auto-matched, and gets a stable synthetic tg ID for Stremio clients.
 */
@Composable
fun CreateScreen(
    prefill: CreatePrefill = CreatePrefill(),
    onDone: (mediaId: Long) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var posterUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var year by remember {
        mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString())
    }
    var isSeries by remember { mutableStateOf(false) }

    val files = remember {
        mutableStateListOf<PrefillFile>().apply {
            addAll(prefill.files.filter { !it.isSubtitle })
        }
    }
    val subtitles = remember {
        mutableStateListOf<PrefillFile>().apply {
            addAll(prefill.files.filter { it.isSubtitle })
        }
    }
    val subtitleLangs = remember {
        mutableStateListOf<String>().apply {
            addAll(prefill.files.filter { it.isSubtitle }.map { subtitleLanguages.first() })
        }
    }

    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }
    val unmatched = remember { mutableStateListOf<UnmatchedFileEntity>() }
    var saving by remember { mutableStateOf(false) }

    // Load unmatched files for the picker.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val all = MsDatabase.get(context).unmatchedDao().getAll()
            withContext(Dispatchers.Main) {
                unmatched.clear()
                unmatched.addAll(all)
            }
        }
    }

    val titleOk = title.isNotBlank()
    val filesOk = files.isNotEmpty()
    val canSave = titleOk && filesOk && !saving

    fun save() {
        if (!canSave) return
        saving = true
        scope.launch(Dispatchers.IO) {
            try {
                val db = MsDatabase.get(context)
                val channelId = files.first().channelId
                val norm = normalizeTitle(title.trim())
                val mediaId = db.mediaDao().upsert(
                    MediaItemEntity(
                        channelId = channelId,
                        title = title.trim(),
                        normalizedTitle = norm,
                        year = year.trim().ifBlank { null },
                        type = if (isSeries) "series" else "movie",
                        imdbId = null,
                        tmdbId = null,
                        stremioId = StremioIds.forNewItem(channelId, norm, null),
                        posterUrl = posterUrl.trim().ifBlank { null },
                        overview = desc.trim().ifBlank { null },
                        totalSize = 0L,
                        episodeCount = 0,
                        fileCount = 0,
                        updatedAt = System.currentTimeMillis(),
                        isCustom = true,
                    ),
                )
                files.forEachIndexed { i, f ->
                    val season = if (isSeries) f.season ?: 1 else null
                    val episode = if (isSeries) f.episode ?: (i + 1) else null
                    if (f.existingStreamId != null) {
                        // The per-file "wrong title?" flow: this row already
                        // exists — move it to the new entry, don't duplicate.
                        val oldMediaId = db.streamDao().getById(f.existingStreamId)?.mediaId
                        db.streamDao().updateSeasonEpisode(f.existingStreamId, season, episode)
                        db.streamDao().moveToMedia(f.existingStreamId, mediaId)
                        if (oldMediaId != null && oldMediaId != mediaId) {
                            db.mediaDao().refreshAggregates(oldMediaId, System.currentTimeMillis())
                        }
                    } else {
                        val quality = try {
                            FilenameParser.parse(f.fileName).qualityBadge
                        } catch (_: Exception) {
                            ""
                        }
                        db.streamDao().insert(
                            StreamFileEntity(
                                mediaId = mediaId,
                                channelId = f.channelId,
                                messageId = f.messageId,
                                fileName = f.fileName,
                                fileSize = f.fileSize,
                                season = season,
                                episode = episode,
                                quality = quality,
                            ),
                        )
                        db.unmatchedDao().deleteByMessage(f.channelId, f.messageId)
                    }
                }
                subtitles.forEachIndexed { i, s ->
                    db.subtitleDao().insert(
                        SubtitleFileEntity(
                            channelId = s.channelId,
                            messageId = s.messageId,
                            fileName = s.fileName,
                            fileSize = s.fileSize,
                            mediaId = mediaId,
                            season = if (isSeries) s.season else null,
                            episode = if (isSeries) s.episode else null,
                            language = subtitleLangs.getOrElse(i) { subtitleLanguages.first() },
                        ),
                    )
                    db.unmatchedDao().deleteByMessage(s.channelId, s.messageId)
                }
                db.mediaDao().refreshAggregates(mediaId, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Entry created", Toast.LENGTH_SHORT).show()
                    onDone(mediaId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    saving = false
                    Toast.makeText(
                        context,
                        "Couldn't save: ${e.message ?: "unknown error"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    pickerMode?.let { mode ->
        FilePickerSheet(
            files = unmatched.filter { u ->
                files.none { it.channelId == u.channelId && it.messageId == u.messageId } &&
                    subtitles.none { it.channelId == u.channelId && it.messageId == u.messageId }
            },
            subtitleMode = mode == PickerMode.SUBTITLES,
            onDismiss = { pickerMode = null },
            onPick = { picked ->
                if (mode == PickerMode.FILES) files.addAll(picked)
                else {
                    subtitles.addAll(picked)
                    repeat(picked.size) { subtitleLangs.add(subtitleLanguages.first()) }
                }
                pickerMode = null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Create Custom Content",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )
        Text(
            "For videos that aren't real shows — class recordings, personal " +
                "rips, anything the scanner can't identify. Pick the files, " +
                "name it, done.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )

        MsTextField(
            value = posterUrl,
            onValueChange = { posterUrl = it },
            hint = "Poster image URL (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        // Live poster preview.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(MsRadii.Field))
                .background(MsColors.SurfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl.isBlank()) {
                Text(
                    "Poster preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            } else {
                AsyncImage(
                    model = posterUrl.trim(),
                    contentDescription = "Poster preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        MsTextField(
            value = title,
            onValueChange = { title = it },
            hint = "Title (required)",
            modifier = Modifier.fillMaxWidth(),
        )
        MsTextField(
            value = desc,
            onValueChange = { desc = it },
            hint = "Description (optional)",
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            singleLine = false,
        )
        MsTextField(
            value = year,
            onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
            hint = "Year",
            modifier = Modifier.fillMaxWidth(),
        )

        // Movie / Series segmented control.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SegmentOption(
                label = "Movie",
                selected = !isSeries,
                onClick = { isSeries = false },
                modifier = Modifier.weight(1f),
            )
            SegmentOption(
                label = "Series",
                selected = isSeries,
                onClick = { isSeries = true },
                modifier = Modifier.weight(1f),
            )
        }

        MsSectionLabel("Files (${files.size})")
        files.forEachIndexed { i, f ->
            PickedFileRow(
                file = f,
                isSeries = isSeries,
                onSeasonChange = { v -> files[i] = f.copy(season = v.toIntOrNull()) },
                onEpisodeChange = { v -> files[i] = f.copy(episode = v.toIntOrNull()) },
                onRemove = { files.removeAt(i) },
            )
        }
        LimeTextButton(
            text = "+ Add files",
            onClick = { pickerMode = PickerMode.FILES },
        )

        MsSectionLabel("Subtitles (${subtitles.size})")
        subtitles.forEachIndexed { i, s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickedFileRow(
                    file = s,
                    isSeries = false,
                    onSeasonChange = {},
                    onEpisodeChange = {},
                    onRemove = {
                        subtitles.removeAt(i)
                        subtitleLangs.removeAt(i)
                    },
                )
                MsDropdown(
                    selected = subtitleLangs.getOrElse(i) { subtitleLanguages.first() },
                    options = subtitleLanguages,
                    onSelect = { subtitleLangs[i] = it },
                    label = "Language",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LimeTextButton(
            text = "+ Add subtitles",
            onClick = { pickerMode = PickerMode.SUBTITLES },
        )

        if (!titleOk || !filesOk) {
            Text(
                buildString {
                    if (!titleOk) append("Title is required.")
                    if (!titleOk && !filesOk) append(" ")
                    if (!filesOk) append("Add at least one file.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LimeTextButton(
                text = "Cancel",
                onClick = onCancel,
            )
            LimePillButton(
                text = if (saving) "Saving…" else "Save Content",
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        }
        // Room for the floating pill: last content scrolls fully into view.
        Spacer(Modifier.height(120.dp))
    }
}

private enum class PickerMode { FILES, SUBTITLES }

/** A picked file with a remove button; series files get season/episode inputs. */
@Composable
private fun PickedFileRow(
    file: PrefillFile,
    isSeries: Boolean,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MsColors.SurfaceAlt)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
            }
            BareIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Remove",
                onClick = onRemove,
            )
        }
        if (isSeries) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MsTextField(
                    value = file.season?.toString() ?: "",
                    onValueChange = onSeasonChange,
                    hint = "Season",
                    modifier = Modifier.weight(1f),
                )
                MsTextField(
                    value = file.episode?.toString() ?: "",
                    onValueChange = onEpisodeChange,
                    hint = "Episode",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) MsColors.Accent else MsColors.SurfaceAlt)
            .then(
                if (!selected) Modifier.border(
                    1.dp,
                    MsColors.TextSecondary.copy(alpha = 0.4f),
                    CircleShape,
                ) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MsColors.OnAccent else MsColors.TextSecondary,
        )
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
