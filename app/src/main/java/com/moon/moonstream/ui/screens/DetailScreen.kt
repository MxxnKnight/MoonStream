package com.moon.moonstream.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.SubtitleFileEntity
import com.moon.moonstream.index.match.MetadataMatcher
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanManager
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MetaPick
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.QualityChip
import com.moon.moonstream.ui.model.formatBytes
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Title detail on REAL data (v0.7.0).
 *
 * Series/anime group streams by season: season pills → episode cards →
 * expandable file rows (copy link + delete per row). Movies keep the flat
 * file list. Subtitles indexed by the scanner show per episode/file with
 * their language; unlinked ones can be attached by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(mediaId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val db = remember { MsDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val media by db.mediaDao().observeById(mediaId).collectAsState(initial = null)
    val files by db.streamDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val subtitles by db.subtitleDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val channelId = media?.channelId
    val unlinkedSubs by (
        channelId?.let { db.subtitleDao().observeUnlinkedByChannel(it) }
            ?: flowOf(emptyList())
        ).collectAsState(initial = emptyList())

    var fixMode by remember { mutableStateOf<FixMode?>(null) }
    var fixFile by remember { mutableStateOf<StreamFileEntity?>(null) }
    var showAddStream by remember { mutableStateOf(false) }
    var showAddSubtitle by remember { mutableStateOf(false) }
    var attachSub by remember { mutableStateOf<SubtitleFileEntity?>(null) }
    var seasonIdx by remember { mutableStateOf(0) }

    val m = media
    if (m == null) {
        // Entry was deleted (e.g. channel removed) — nothing fake to show.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MsColors.Bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "This entry no longer exists.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MsColors.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                LimePillButton(text = "Back", onClick = onBack)
            }
        }
        return
    }

    val isSeries = m.type == "series" || m.type == "anime"
    val isAnime = m.type == "anime"
    val totalsLine = if (isSeries) {
        "${m.episodeCount} Episodes · ${formatBytes(m.totalSize)}"
    } else {
        "${m.fileCount} file${if (m.fileCount == 1) "" else "s"} · ${formatBytes(m.totalSize)}"
    }
    // Stream URLs served by the in-phone server: /stream/file/<mediaId>/<fileId>
    // (same shape the server advertises in its /stream responses).
    val streamBase = "http://${AppUiState.currentIp}:${AppUiState.port}"

    fun copyLink(file: StreamFileEntity) {
        clipboard.setText(AnnotatedString("$streamBase/stream/file/${m.id}/${file.id}"))
        Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
    }

    /** Open the system "Play with" sheet so the phone's own video players
     *  (VLC, mpvRx, …) handle the stream. Needs the server running. */
    fun playExternal(file: StreamFileEntity) {
        if (!AppUiState.serverOn) {
            Toast.makeText(context, "Turn the server on first, then play", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("$streamBase/stream/file/${m.id}/${file.id}")
        val mime = mimeForFile(file.fileName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Play with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No video player found on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFile(file: StreamFileEntity) {
        scope.launch {
            db.streamDao().deleteById(file.id)
            db.mediaDao().refreshAggregates(m.id, System.currentTimeMillis())
            Toast.makeText(context, "File removed", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleHide() {
        scope.launch {
            db.mediaDao().setHidden(m.id, !m.hidden)
            Toast.makeText(
                context,
                if (!m.hidden) "Hidden — find it in the Hidden tab" else "Unhidden",
                Toast.LENGTH_SHORT,
            ).show()
            if (!m.hidden) onBack()
        }
    }

    /** FIX_TITLE apply: re-run the matcher on the picked title, update the row. */
    fun applyFixTitle(picked: MetaPick) {
        scope.launch(Dispatchers.IO) {
            val key = ScanPrefs.getTmdbKey(context)
            val full = MetadataMatcher.match(picked.title, picked.year, m.type, isAnime, key)
                ?.let { MetadataMatcher.enrichFromCinemeta(it, m.type) }
            db.mediaDao().updateMetadata(
                id = m.id,
                title = full?.title ?: picked.title,
                norm = normalizeTitle(full?.title ?: picked.title),
                year = full?.year ?: picked.year,
                imdbId = full?.imdbId ?: picked.imdbId,
                tmdbId = full?.tmdbId ?: picked.tmdbId,
                posterUrl = full?.posterUrl ?: picked.posterUrl,
                overview = full?.overview ?: picked.overview,
                now = System.currentTimeMillis(),
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Metadata updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** FIX_FILE apply: move one file to the picked title (find or create). */
    fun applyFixFile(file: StreamFileEntity, picked: MetaPick) {
        scope.launch(Dispatchers.IO) {
            val norm = normalizeTitle(picked.title)
            val now = System.currentTimeMillis()
            var target = db.mediaDao().findByTitle(m.channelId, norm)
            if (target == null) {
                val key = ScanPrefs.getTmdbKey(context)
                val full = MetadataMatcher.match(picked.title, picked.year, m.type, isAnime, key)
                    ?.let { MetadataMatcher.enrichFromCinemeta(it, m.type) }
                val newId = db.mediaDao().upsert(
                    MediaItemEntity(
                        channelId = m.channelId,
                        title = full?.title ?: picked.title,
                        normalizedTitle = norm,
                        year = full?.year ?: picked.year,
                        type = m.type,
                        imdbId = full?.imdbId ?: picked.imdbId,
                        tmdbId = full?.tmdbId ?: picked.tmdbId,
                        posterUrl = full?.posterUrl ?: picked.posterUrl,
                        overview = full?.overview ?: picked.overview,
                        totalSize = 0L,
                        episodeCount = 0,
                        fileCount = 0,
                        updatedAt = now,
                    ),
                )
                target = db.mediaDao().findByTitle(m.channelId, norm) ?: return@launch
            }
            db.streamDao().moveToMedia(file.id, target.id)
            db.mediaDao().refreshAggregates(m.id, now)
            db.mediaDao().refreshAggregates(target.id, now)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "File moved to ${target.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Poster header (real art, or initial tile for title-only entries).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(MsColors.SurfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            if (m.posterUrl != null) {
                AsyncImage(
                    model = m.posterUrl,
                    contentDescription = m.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = m.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp,
                    color = MsColors.Accent.copy(alpha = 0.6f),
                )
            }
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
                icon = if (m.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (m.hidden) "Unhide entry" else "Hide entry",
                tint = Color.White,
                onClick = { toggleHide() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
            Text(
                text = m.title,
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
                    "${m.year ?: "—"} · ${m.type.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                Text(
                    totalsLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.Accent,
                )
                if (!m.overview.isNullOrBlank()) {
                    Text(
                        m.overview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                    )
                }
            }

            // Three equal pill buttons: Play / Copy Link / Add Subtitle.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LimePillButton(
                    text = "Play",
                    onClick = {
                        Toast.makeText(
                            context,
                            "In-app player is coming — use Copy Link with Stremio for now",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
                MsOutlinePill(
                    text = "Copy Link",
                    onClick = {
                        val first = files.firstOrNull()
                        if (first != null) copyLink(first)
                        else Toast.makeText(context, "No files to link", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
                MsOutlinePill(
                    text = "Add Subtitle",
                    onClick = { showAddSubtitle = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
            }

            // Secondary row: Fix Metadata + Add Custom Stream.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsOutlinePill(
                    text = "Fix Metadata",
                    onClick = { fixMode = FixMode.FIX_TITLE },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
                MsOutlinePill(
                    text = "Add Custom Stream",
                    onClick = { showAddStream = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
            }

            if (isSeries) {
                SeriesSeasons(
                    media = m,
                    files = files,
                    subtitles = subtitles,
                    seasonIdx = seasonIdx,
                    onSeason = { seasonIdx = it },
                    onPlay = { playExternal(it) },
                    onCopy = { copyLink(it) },
                    onDelete = { deleteFile(it) },
                    onFixFile = { fixFile = it; fixMode = FixMode.FIX_FILE },
                    onUnlinkSub = { sub ->
                        scope.launch {
                            db.subtitleDao().delete(sub.id)
                            Toast.makeText(context, "Subtitle removed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            } else {
                // Movies: flat file list + subtitles section.
                Text(
                    "Files (${files.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                if (files.isEmpty()) {
                    Text(
                        "No files indexed for this entry yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                }
                files.forEach { f ->
                    StreamRow(
                        stream = f,
                        onPlay = { playExternal(f) },
                        onCopy = { copyLink(f) },
                        onDelete = { deleteFile(f) },
                        onFixFile = { fixFile = f; fixMode = FixMode.FIX_FILE },
                    )
                }
                SubtitleSection(
                    title = "Subtitles",
                    subs = subtitles.filter { it.season == null && it.episode == null },
                    onUnlink = { sub ->
                        scope.launch {
                            db.subtitleDao().delete(sub.id)
                            Toast.makeText(context, "Subtitle removed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            // Unlinked subtitles for this channel — attach by hand.
            if (unlinkedSubs.isNotEmpty()) {
                Text(
                    "Unlinked subtitles (${unlinkedSubs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                Text(
                    "The scanner couldn't map these to an entry. Attach them by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
                unlinkedSubs.forEach { sub ->
                    SubtitleRow(
                        sub = sub,
                        actionLabel = "Attach",
                        onAction = { attachSub = sub },
                        onDelete = {
                            scope.launch {
                                db.subtitleDao().delete(sub.id)
                                Toast.makeText(context, "Subtitle removed", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }

    fixMode?.let { mode ->
        MetadataFixSheet(
            mode = mode,
            currentTitle = fixFile?.fileName ?: m.title,
            isAnime = isAnime,
            onDismiss = { fixMode = null; fixFile = null },
            onApply = { picked ->
                when (mode) {
                    FixMode.FIX_TITLE -> applyFixTitle(picked)
                    FixMode.FIX_FILE -> fixFile?.let { applyFixFile(it, picked) }
                    FixMode.LINK -> Unit // not used on this screen
                }
                fixMode = null
                fixFile = null
            },
        )
    }

    if (showAddStream) {
        AddStreamSheet(
            onDismiss = { showAddStream = false },
            onSave = { link, quality, name ->
                scope.launch(Dispatchers.IO) {
                    val parsed = parseTgLink(link)
                    if (parsed == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "That link didn't look like a t.me/c/…/… message link",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        return@launch
                    }
                    val (chId, msgId) = parsed
                    db.streamDao().insert(
                        StreamFileEntity(
                            mediaId = m.id,
                            channelId = chId,
                            messageId = msgId,
                            fileName = name.ifBlank { "Custom stream $msgId" },
                            fileSize = 0L,
                            season = null,
                            episode = null,
                            quality = quality.ifBlank { "1080p" },
                        ),
                    )
                    db.mediaDao().refreshAggregates(m.id, System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Custom stream added", Toast.LENGTH_SHORT).show()
                        showAddStream = false
                    }
                }
            },
        )
    }

    if (showAddSubtitle) {
        AddSubtitleSheet(
            unlinked = unlinkedSubs,
            onAttachExisting = { attachSub = it; showAddSubtitle = false },
            onDismiss = { showAddSubtitle = false },
            onSaveLink = { link, language ->
                scope.launch(Dispatchers.IO) {
                    val parsed = parseTgLink(link)
                    if (parsed == null || channelId == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "That link didn't look like a t.me/c/…/… message link",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        return@launch
                    }
                    val (chId, msgId) = parsed
                    val fileName = "subtitle_$msgId.srt"
                    val p = com.moon.moonstream.index.parse.FilenameParser.parse(fileName)
                    val mediaId = ScanManager.resolveSubtitleMedia(
                        db, chId, normalizeTitle(p.title), p.season, p.episode,
                    ) ?: m.id
                    db.subtitleDao().insert(
                        SubtitleFileEntity(
                            channelId = chId,
                            messageId = msgId,
                            fileName = fileName,
                            fileSize = 0L,
                            mediaId = mediaId,
                            season = p.season,
                            episode = p.episode,
                            language = language,
                        ),
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Subtitle added", Toast.LENGTH_SHORT).show()
                        showAddSubtitle = false
                    }
                }
            },
        )
    }

    attachSub?.let { sub ->
        AttachSubtitleDialog(
            sub = sub,
            isSeries = isSeries,
            onDismiss = { attachSub = null },
            onAttach = { season, episode ->
                scope.launch {
                    db.subtitleDao().attach(sub.id, m.id, season, episode)
                    Toast.makeText(context, "Subtitle attached", Toast.LENGTH_SHORT).show()
                    attachSub = null
                }
            },
        )
    }
}

/** Mime type for the ACTION_VIEW intent, guessed from the file extension. */
private fun mimeForFile(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "3gp", "3g2" -> "video/3gpp"
        "ts", "m2ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        else -> "video/*"
    }
}
