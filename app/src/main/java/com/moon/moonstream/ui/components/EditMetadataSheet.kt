package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.StremioIds
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.parse.normalizeTitle
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
 * backdrop (landscape) URLs, each with a live preview. Saves via
 * updateMetadata, keeping ids, type, genres, language and country
 * untouched; the Detail screen observes the entry, so it refreshes
 * on its own after a save.
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
                withContext(Dispatchers.Main) {
                    saving = false
                    MsToast.show("Saved")
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
            LimePillButton(
                text = if (saving) "Saving…" else "Save",
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
