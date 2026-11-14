package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.StremioIds
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual metadata editor for CUSTOM entries (moon's 2026-11-14): the
 * refresh button is useless on a hand-made entry — no provider knows
 * it, and a re-match could overwrite his own words — so custom entries
 * get this instead. Title, year, description, poster (portrait) +
 * backdrop (landscape) URLs, each with a live preview, plus an
 * unmatched file picker so more episodes can be attached to the entry
 * right here. Saves via updateMetadata, keeping ids, type, genres,
 * language and country untouched; the Detail screen observes the
 * entry, so it refreshes on its own after a save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataSheet(
    media: MediaItemEntity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { MsDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(media.title) }
    var year by remember { mutableStateOf(media.year ?: "") }
    var desc by remember { mutableStateOf(media.overview ?: "") }
    var posterUrl by remember { mutableStateOf(media.posterUrl ?: "") }
    var backdropUrl by remember { mutableStateOf(media.backdropUrl ?: "") }
    var saving by remember { mutableStateOf(false) }
    // Unmatched files picked to attach to this entry on Save (moon's
    // 2026-11-14: add more episodes of a custom series from here).
    var showPicker by remember { mutableStateOf(false) }
    val pickedFiles = remember { mutableStateListOf<PrefillFile>() }
    val unmatched = remember { mutableStateListOf<UnmatchedFileEntity>() }

    // Load unmatched files for the picker (same as CreateScreen).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val all = MsDatabase.get(context).unmatchedDao().getAll()
            withContext(Dispatchers.Main) {
                unmatched.clear()
                unmatched.addAll(all)
            }
        }
    }

    val canSave = title.isNotBlank() && !saving

    fun save() {
        if (!canSave) return
        saving = true
        scope.launch(Dispatchers.IO) {
            try {
                val norm = normalizeTitle(title.trim())
                db.mediaDao().updateMetadata(
                    id = media.id,
                    title = title.trim(),
                    norm = norm,
                    year = year.trim().ifBlank { null },
                    imdbId = media.imdbId,
                    tmdbId = media.tmdbId,
                    posterUrl = posterUrl.trim().ifBlank { null },
                    backdropUrl = backdropUrl.trim().ifBlank { null },
                    overview = desc.trim().ifBlank { null },
                    stremioId = StremioIds.forNewItem(media.channelId, norm, media.imdbId),
                    type = media.type,
                    genres = media.genres,
                    originalLanguage = media.originalLanguage,
                    country = media.country,
                    now = System.currentTimeMillis(),
                )
                // Attach the picked files (same shape as the LINK flow:
                // season/episode parsed from the filename, quality badge
                // stored, unmatched row cleared). Movies get no S/E.
                var added = 0
                if (pickedFiles.isNotEmpty()) {
                    val isSeriesEntry = media.type == "series" || media.type == "anime"
                    val absoluteAnime = ScanPrefs.isAnimeAbsolute(context)
                    for (f in pickedFiles) {
                        if (db.streamDao().exists(f.channelId, f.messageId)) {
                            // Already attached (stale unmatched row) —
                            // just clear the leftover row.
                            db.unmatchedDao().deleteByMessage(f.channelId, f.messageId)
                            continue
                        }
                        val parsed = FilenameParser.parse(
                            f.fileName, media.type == "anime", "", absoluteAnime,
                        )
                        db.streamDao().insert(
                            StreamFileEntity(
                                mediaId = media.id,
                                channelId = f.channelId,
                                messageId = f.messageId,
                                fileName = f.fileName,
                                fileSize = f.fileSize,
                                season = if (isSeriesEntry) parsed.season else null,
                                episode = if (isSeriesEntry) parsed.episode else null,
                                quality = parsed.qualityBadge,
                            ),
                        )
                        db.unmatchedDao().deleteByMessage(f.channelId, f.messageId)
                        added++
                    }
                    db.mediaDao().refreshAggregates(media.id, System.currentTimeMillis())
                }
                withContext(Dispatchers.Main) {
                    saving = false
                    MsToast.show(
                        if (added > 0) "Saved — $added file${if (added == 1) "" else "s"} added"
                        else "Saved",
                    )
                    onDismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    saving = false
                    MsToast.show("Couldn't save — try again")
                }
            }
        }
    }

    IosBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        MsSheetContent(title = "Edit metadata", scrollable = true) {
            MsTextField(
                value = title,
                onValueChange = { title = it },
                hint = "Title",
                modifier = Modifier.fillMaxWidth(),
            )
            MsTextField(
                value = year,
                onValueChange = { year = it },
                hint = "Year (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            MsTextField(
                value = desc,
                onValueChange = { desc = it },
                hint = "Description (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            MsTextField(
                value = posterUrl,
                onValueChange = { posterUrl = it },
                hint = "Poster image URL — portrait (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            // Live poster preview (same pattern as CreateScreen).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
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
                value = backdropUrl,
                onValueChange = { backdropUrl = it },
                hint = "Backdrop image URL — landscape (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            // Live backdrop preview.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(MsRadii.Field))
                    .background(MsColors.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (backdropUrl.isBlank()) {
                    Text(
                        "Backdrop preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                } else {
                    AsyncImage(
                        model = backdropUrl.trim(),
                        contentDescription = "Backdrop preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            MsSectionLabel(if (media.type == "movie") "Add files" else "Add episodes")
            // Files picked from unmatched — attached to this entry on Save.
            pickedFiles.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MsRadii.Field))
                        .background(MsColors.SurfaceAlt)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        f.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    BareIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = MsColors.TextSecondary,
                        onClick = { pickedFiles.remove(f) },
                    )
                }
            }
            MsOutlinePill(
                text = if (pickedFiles.isEmpty()) "Add from unmatched"
                else "Add more from unmatched",
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            LimePillButton(
                text = if (saving) "Saving…" else "Save",
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Unmatched file picker (the same sheet Create uses) — a sibling
    // dialog, so dismissing it returns to this sheet with the form
    // untouched.
    if (showPicker) {
        FilePickerSheet(
            files = unmatched.filter { u ->
                pickedFiles.none { it.channelId == u.channelId && it.messageId == u.messageId }
            },
            subtitleMode = false,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                pickedFiles.addAll(picked)
                showPicker = false
            },
        )
    }
}
