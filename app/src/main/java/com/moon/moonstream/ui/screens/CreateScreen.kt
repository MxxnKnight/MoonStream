package com.moon.moonstream.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.components.MsToggle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
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
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsSectionLabel
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.model.CreatePrefill
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Supported subtitle languages (was in FakeData.kt, it's real data). */
val subtitleLanguages = listOf("English", "Malayalam", "Hindi", "Tamil", "Spanish", "Arabic")

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
    var isSeries by remember {
        // Default to Series when the prefilled files already carry
        // season/episode info (e.g. from the "wrong title?" flow).
        mutableStateOf(prefill.files.any { it.season != null || it.episode != null })
    }

    val files = remember {
        mutableStateListOf<PrefillFile>().apply {
            addAll(prefill.files.filter { !it.isSubtitle }.map { f ->
                // Auto-parse season/episode from the filename when missing,
                // so the boxes are pre-filled but still editable.
                if (f.season == null && f.episode == null) {
                    val p = com.moon.moonstream.index.parse.FilenameParser.parse(f.fileName)
                    if (p.season != null || p.episode != null) f.copy(season = p.season, episode = p.episode)
                    else f
                } else f
            })
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

    var isSplit by remember { mutableStateOf(false) }

    // Global season/episode defaults (shown when Series is selected).
    var globalSeason by remember { mutableStateOf("1") }
    var globalEpisode by remember { mutableStateOf("") }

    // Add-by-link state for files.
    var showFileLink by remember { mutableStateOf(false) }
    var fileLink by remember { mutableStateOf("") }
    var fileLinkSeason by remember { mutableStateOf("") }
    var fileLinkEpisode by remember { mutableStateOf("") }
    var fileLinkQuality by remember { mutableStateOf("Auto") }
    var fileLinkFetching by remember { mutableStateOf(false) }
    var fileLinkName by remember { mutableStateOf<String?>(null) }
    var fileLinkSize by remember { mutableStateOf(0L) }

    // Add-by-link state for subtitles.
    var showSubLink by remember { mutableStateOf(false) }
    var subLink by remember { mutableStateOf("") }
    var subLinkLang by remember { mutableStateOf(subtitleLanguages.first()) }
    var subLinkFetching by remember { mutableStateOf(false) }
    var subLinkName by remember { mutableStateOf<String?>(null) }
    val splitLinks = remember { mutableStateListOf<String>() }
    val splitFetching = remember { mutableStateListOf<Int>() }

    fun fetchSplitLink(i: Int) {
        val link = splitLinks.getOrNull(i)?.trim().orEmpty()
        if (link.isBlank() || splitFetching.contains(i)) return
        splitFetching.add(i)
        scope.launch(Dispatchers.IO) {
            try {
                val parsed = com.moon.moonstream.ui.screens.parseTgLink(link)
                if (parsed == null) {
                    withContext(Dispatchers.Main) {
                        MsToast.show("That link didn't look like a t.me/c/…/… message link")
                    }
                    return@launch
                }
                val (chId, msgId) = parsed
                val msg = com.moon.moonstream.backend.BackendProvider.backend
                    .fetchMessages(chId, msgId, 1).firstOrNull { it.id == msgId }
                if (msg == null) {
                    withContext(Dispatchers.Main) { MsToast.show("Couldn't read that link") }
                    return@launch
                }
                val p = com.moon.moonstream.index.parse.FilenameParser.parse(msg.fileName)
                val entry = com.moon.moonstream.ui.model.PrefillFile(
                    channelId = chId,
                    messageId = msgId,
                    fileName = msg.fileName,
                    fileSize = msg.fileSize,
                    season = if (isSeries) p.season else null,
                    episode = if (isSeries) p.episode else null,
                )
                withContext(Dispatchers.Main) {
                    files.add(entry)
                    splitLinks[i] = ""
                    MsToast.show("Part ${i + 1} added: ${msg.fileName.take(40)}")
                }
            } finally {
                withContext(Dispatchers.Main) { splitFetching.remove(i) }
            }
        }
    }

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
                        val quality = f.quality ?: try {
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
                    MsToast.show("Entry created")
                    onDone(mediaId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    saving = false
                    MsToast.show("Couldn't save: ${e.message ?: "unknown error"}")
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Create Custom Content",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            BareIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onCancel,
            )
        }
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

        // Global season/episode: visible as soon as Series is selected,
        // even with zero files. Used as defaults for added files.
        if (isSeries) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MsTextField(
                    value = globalSeason,
                    onValueChange = { globalSeason = it.filter { c -> c.isDigit() }.take(2) },
                    hint = "Season",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                MsTextField(
                    value = globalEpisode,
                    onValueChange = { globalEpisode = it.filter { c -> c.isDigit() }.take(4) },
                    hint = "Episode (next)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "New files use this season; episode auto-increments.",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }

        MsSectionLabel("Files (${files.size})")
        files.forEachIndexed { i, f ->
            PickedFileRow(
                file = f,
                isSeries = isSeries,
                onSeasonChange = { v -> files[i] = f.copy(season = v.toIntOrNull()) },
                onEpisodeChange = { v -> files[i] = f.copy(episode = v.toIntOrNull()) },
                onQualityChange = { q -> files[i] = f.copy(quality = q) },
                onRemove = { files.removeAt(i) },
            )
        }
        LimeTextButton(
            text = "+ Add files",
            onClick = { pickerMode = PickerMode.FILES },
        )
        LimeTextButton(
            text = "+ Add by link",
            onClick = { showFileLink = !showFileLink },
        )
        if (showFileLink) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsTextField(
                    value = fileLink,
                    onValueChange = {
                        fileLink = it
                        // Auto-fetch filename when a valid link is pasted.
                        val trimmed = it.trim()
                        if (trimmed.isNotBlank() && fileLinkName == null && !fileLinkFetching) {
                            val parsed = try {
                                com.moon.moonstream.ui.screens.parseTgLink(trimmed)
                            } catch (e: Exception) { null }
                            if (parsed != null) {
                                fileLinkFetching = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val (chId, msgId) = parsed
                                        val backend = com.moon.moonstream.backend.BackendProvider.backend
                                        // TDLib history is exclusive of fromMessageId — request
                                        // msgId+1 to include the target message itself.
                                        val fetched = (backend.fetchMessages(chId, msgId + 1, 1) +
                                            backend.fetchMessages(chId, msgId, 1))
                                            .firstOrNull { m -> m.id == msgId }
                                        val fetchedName = fetched?.fileName
                                        val fetchedSize = fetched?.fileSize ?: 0L
                                        withContext(Dispatchers.Main) {
                                            if (!fetchedName.isNullOrBlank()) {
                                                fileLinkName = fetchedName
                                                fileLinkSize = fetchedSize
                                                // Auto-parse S/E and quality.
                                                val p = com.moon.moonstream.index.parse.FilenameParser.parse(fetchedName)
                                                if (p.season != null) fileLinkSeason = p.season.toString()
                                                if (p.episode != null) fileLinkEpisode = p.episode.toString()
                                                val q = p.qualityBadge
                                                if (!q.isNullOrBlank()) fileLinkQuality = q
                                            }
                                            fileLinkFetching = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { fileLinkFetching = false }
                                    }
                                }
                            }
                        }
                    },
                    hint = "Paste t.me/c/…/… link",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fileLinkFetching) {
                    Text(
                        "Reading filename…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
                if (fileLinkName != null) {
                    Text(
                        "File: $fileLinkName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextPrimary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isSeries) {
                        MsTextField(
                            value = fileLinkSeason,
                            onValueChange = { fileLinkSeason = it.filter { c -> c.isDigit() }.take(2) },
                            hint = "Season",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        MsTextField(
                            value = fileLinkEpisode,
                            onValueChange = { fileLinkEpisode = it.filter { c -> c.isDigit() }.take(4) },
                            hint = "Episode",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MsDropdown(
                        selected = fileLinkQuality,
                        options = listOf("Auto", "4K", "1080p", "720p", "480p", "360p"),
                        onSelect = { fileLinkQuality = it },
                        label = "Quality",
                        modifier = Modifier.weight(1f),
                    )
                }
                LimePillButton(
                    text = "Add file",
                    onClick = {
                        val trimmed = fileLink.trim()
                        val parsed = try {
                            com.moon.moonstream.ui.screens.parseTgLink(trimmed)
                        } catch (e: Exception) { null }
                        if (parsed == null) {
                            MsToast.show("That link didn't look like a t.me/c/…/… message link")
                            return@LimePillButton
                        }
                        val (chId, msgId) = parsed
                        val name = fileLinkName ?: "file_$msgId"
                        val quality = if (fileLinkQuality == "Auto") null else fileLinkQuality
                        files.add(
                            com.moon.moonstream.ui.model.PrefillFile(
                                channelId = chId,
                                messageId = msgId,
                                fileName = name,
                                fileSize = fileLinkSize,
                                season = if (isSeries) fileLinkSeason.toIntOrNull() ?: globalSeason.toIntOrNull() else null,
                                episode = if (isSeries) fileLinkEpisode.toIntOrNull() ?: globalEpisode.toIntOrNull() else null,
                                isSubtitle = false,
                                quality = quality,
                            )
                        )
                        // Reset for next.
                        fileLink = ""
                        fileLinkName = null
                        fileLinkSize = 0L
                        fileLinkSeason = ""
                        fileLinkEpisode = ""
                        fileLinkQuality = "Auto"
                        showFileLink = false
                        MsToast.show("File added")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Split-file toggle: when ON, link boxes appear for pasting each
        // part's t.me link (part 1, part 2, ...). The server joins them
        // into one stream.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Split file",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                )
                Text(
                    "Multiple parts (.001, .002…) play as one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }
            MsToggle(
                checked = isSplit,
                onCheckedChange = {
                    isSplit = it
                    if (it && splitLinks.isEmpty()) {
                        splitLinks.add("")
                        splitLinks.add("")
                    }
                },
            )
        }
        if (isSplit) {
            splitLinks.forEachIndexed { i, link ->
                SplitLinkRow(
                    index = i,
                    link = link,
                    fetching = splitFetching.contains(i),
                    onLinkChange = { splitLinks[i] = it },
                    onFetch = { fetchSplitLink(i) },
                    onRemove = { if (splitLinks.size > 1) splitLinks.removeAt(i) },
                )
            }
            LimeTextButton(
                text = "+ Add part",
                onClick = { splitLinks.add("") },
            )
        }

        MsSectionLabel("Subtitles (${subtitles.size})")
        subtitles.forEachIndexed { i, s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickedFileRow(
                    file = s,
                    isSeries = false,
                    onSeasonChange = {},
                    onEpisodeChange = {},
                    onQualityChange = {},
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
        LimeTextButton(
            text = "+ Add by link",
            onClick = { showSubLink = !showSubLink },
        )
        if (showSubLink) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsTextField(
                    value = subLink,
                    onValueChange = {
                        subLink = it
                        // Auto-fetch filename when a valid link is pasted.
                        val trimmed = it.trim()
                        if (trimmed.isNotBlank() && subLinkName == null && !subLinkFetching) {
                            val parsed = try {
                                com.moon.moonstream.ui.screens.parseTgLink(trimmed)
                            } catch (e: Exception) { null }
                            if (parsed != null) {
                                subLinkFetching = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val (chId, msgId) = parsed
                                        val backend = com.moon.moonstream.backend.BackendProvider.backend
                                        // TDLib history is exclusive of fromMessageId — request
                                        // msgId+1 to include the target message itself.
                                        val fetched = (backend.fetchMessages(chId, msgId + 1, 1) +
                                            backend.fetchMessages(chId, msgId, 1))
                                            .firstOrNull { m -> m.id == msgId }
                                            ?.fileName
                                        withContext(Dispatchers.Main) {
                                            if (!fetched.isNullOrBlank()) subLinkName = fetched
                                            subLinkFetching = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { subLinkFetching = false }
                                    }
                                }
                            }
                        }
                    },
                    hint = "Paste t.me/c/…/… subtitle link",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (subLinkFetching) {
                    Text(
                        "Reading filename…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
                if (subLinkName != null) {
                    Text(
                        "File: $subLinkName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextPrimary,
                    )
                }
                MsDropdown(
                    selected = subLinkLang,
                    options = subtitleLanguages,
                    onSelect = { subLinkLang = it },
                    label = "Language",
                    modifier = Modifier.fillMaxWidth(),
                )
                LimePillButton(
                    text = "Add subtitle",
                    onClick = {
                        val trimmed = subLink.trim()
                        val parsed = try {
                            com.moon.moonstream.ui.screens.parseTgLink(trimmed)
                        } catch (e: Exception) { null }
                        if (parsed == null) {
                            MsToast.show("That link didn't look like a t.me/c/…/… message link")
                            return@LimePillButton
                        }
                        val (chId, msgId) = parsed
                        val name = subLinkName ?: "subtitle_$msgId.srt"
                        subtitles.add(
                            com.moon.moonstream.ui.model.PrefillFile(
                                channelId = chId,
                                messageId = msgId,
                                fileName = name,
                                fileSize = fileLinkSize,
                                season = null,
                                episode = null,
                                isSubtitle = true,
                            )
                        )
                        subtitleLangs.add(subLinkLang)
                        subLink = ""
                        subLinkName = null
                        subLinkLang = subtitleLanguages.first()
                        showSubLink = false
                        MsToast.show("Subtitle added")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

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
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF0A84FF),
                )
            }
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

/** One split-part link box: paste the t.me link, Fetch resolves and adds it. */
@Composable
private fun SplitLinkRow(
    index: Int,
    link: String,
    fetching: Boolean,
    onLinkChange: (String) -> Unit,
    onFetch: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MsColors.SurfaceAlt)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Part ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            BareIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Remove part",
                onClick = onRemove,
            )
        }
        MsTextField(
            value = link,
            onValueChange = onLinkChange,
            hint = "Part ${index + 1} link (t.me/c/…/…)",
            modifier = Modifier.fillMaxWidth(),
        )
        MsOutlinePill(
            text = if (fetching) "Fetching…" else "Fetch part",
            onClick = onFetch,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A picked file with a remove button; series files get season/episode inputs. */
@Composable
private fun PickedFileRow(
    file: PrefillFile,
    isSeries: Boolean,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onQualityChange: (String?) -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isSeries) {
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
            MsDropdown(
                selected = file.quality ?: "Auto",
                options = listOf("Auto", "4K", "1080p", "720p", "480p", "360p"),
                onSelect = { onQualityChange(if (it == "Auto") null else it) },
                label = "Quality",
                modifier = Modifier.weight(1f),
            )
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
    // iOS segmented control style (moon's bug 2026-11-04: old UI).
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF0A84FF) else Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Color(0xFF98989F),
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
