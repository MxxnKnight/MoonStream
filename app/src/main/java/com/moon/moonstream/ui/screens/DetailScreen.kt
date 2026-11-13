package com.moon.moonstream.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.moon.moonstream.R
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.player.PlayerUrls
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.moon.moonstream.backend.BackendProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.moon.moonstream.ui.components.IosDialog
import com.moon.moonstream.ui.components.IosSheetButton
import com.moon.moonstream.ui.components.IosSpinner
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MetaPick
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.QualityChip
import com.moon.moonstream.ui.model.formatBytes
import com.moon.moonstream.ui.model.CreatePrefill
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.index.StremioIds
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
fun DetailScreen(
    mediaId: Long,
    onBack: () -> Unit,
    onOpenCreate: (CreatePrefill) -> Unit = {},
    onPlayInApp: (
        videoUrl: String, title: String, mediaTitle: String,
        mediaId: Long, streamFileId: Long,
    ) -> Unit = { _, _, _, _, _ -> },
) {
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
    // "watched X ago" per file (moon's design 2026-11-02) — newest entry wins.
    val watchList by db.watchProgressDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val watchedAt = remember(watchList) {
        watchList.groupBy { it.streamFileId }.mapValues { (_, rows) -> rows.maxOf { it.playedAt } }
    }

    var fixMode by remember { mutableStateOf<FixMode?>(null) }
    var fixFile by remember { mutableStateOf<StreamFileEntity?>(null) }
    // Manual episode assignment dialog (moon's pick 2026-11-02).
    var setEpisodeFile by remember { mutableStateOf<StreamFileEntity?>(null) }
    var showAddStream by remember { mutableStateOf(false) }
    var showAddSubtitle by remember { mutableStateOf(false) }
    var attachSub by remember { mutableStateOf<SubtitleFileEntity?>(null) }
    var seasonIdx by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

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
    // Landscape backdrop for the header (moon's 2026-11-06): Cinemeta
    // `background`, fetched once per entry and cached in the DB — the header
    // then recomposes from the observeById flow. Free, no key, and never
    // re-fetched once stored.
    LaunchedEffect(m.id, m.imdbId, m.backdropUrl) {
        if (m.backdropUrl != null || m.imdbId.isNullOrBlank()) return@LaunchedEffect
        val url = withContext(Dispatchers.IO) {
            MetadataMatcher.fetchBackdrop(m.imdbId!!, m.type)
        }
        if (url != null) db.mediaDao().updateBackdropUrl(m.id, url)
    }

    // Stream URLs served by the in-phone server: /stream/file/<mediaId>/<fileId>
    // (same shape the server advertises in its /stream responses).
    val streamBase = "http://${AppUiState.currentIp}:${AppUiState.port}"

    fun copyLink(file: StreamFileEntity) {
        clipboard.setText(AnnotatedString("$streamBase/stream/file/${m.id}/${file.id}"))
        MsToast.show("Stream link copied")
    }

    /** Open the system "Play with" sheet so the phone's own video players
     *  (VLC, mpvRx, …) handle the stream. Needs the server running.
     *  Custom-added subtitles are passed via intent extras so mpvRx/VLC
     *  can load them (moon's question 2026-11-01). */
    fun playTitle(file: StreamFileEntity): String =
        if (file.season != null && file.episode != null) {
            "${m.title} S${file.season}E${file.episode}"
        } else {
            m.title
        }

    /**
     * Record the watch — WITHOUT losing the saved position (resume fix
     * 2026-11-09). This used to be delete-then-insert, which zeroed the row at
     * the same moment the player was reading it: every play from Detail
     * started at 0:00 and the Resume button never showed. It is also not
     * container-related — MKV saves exactly like MP4, the position was simply
     * being wiped a few milliseconds after being read.
     */
    fun recordWatch(file: StreamFileEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val epLabel = if (file.season != null && file.episode != null) {
                    "S${file.season} E${file.episode}" +
                        (if (file.episodeEnd != null) "-${file.episodeEnd}" else "")
                } else null
                val dao = db.watchProgressDao()
                val now = System.currentTimeMillis()
                val touched = dao.touchOpened(
                    streamFileId = file.id, mediaId = m.id, title = m.title,
                    episodeLabel = epLabel, posterUrl = m.posterUrl, playedAt = now,
                )
                // First ever play of this file: create the row (position 0).
                if (touched == 0) {
                    dao.upsert(
                        com.moon.moonstream.index.db.WatchProgressEntity(
                            mediaId = m.id,
                            streamFileId = file.id,
                            title = m.title,
                            episodeLabel = epLabel,
                            posterUrl = m.posterUrl,
                            playedAt = now,
                        )
                    )
                }
            } catch (e: Exception) { /* never break playback for logging */ }
        }
    }

    /** Per-file play: the player auto-resumes when a position is saved
     *  (moon's pick 2026-11-06 — no prompt box).
     *  0.15.0: plays via tdfile:// when the DB knows the size — no server needed. */
    fun playInApp(file: StreamFileEntity) {
        onPlayInApp(
            PlayerUrls.stream(m.id, file.id), playTitle(file), m.title,
            m.id, file.id,
        )
        recordWatch(file)
    }

    /**
     * Header Play = continue watching (moon's pick 2026-11-02): opens the
     * episode/file he last watched and resumes exactly where he left —
     * no prompt, the button itself means "continue".
     */
    fun playContinue() {
        // 0.15.0: in-app playback via tdfile:// needs no server.
        scope.launch(Dispatchers.IO) {
            val latest = try {
                db.watchProgressDao().latestForMedia(m.id)
            } catch (e: Exception) { null }
            val target = latest?.let { rec -> files.find { it.id == rec.streamFileId } }
                ?: files.firstOrNull()
            withContext(Dispatchers.Main) {
                if (target == null) {
                    MsToast.show("No files to play yet")
                    return@withContext
                }
                onPlayInApp(
                    PlayerUrls.stream(m.id, target.id), playTitle(target), m.title,
                    m.id, target.id,
                )
                recordWatch(target)
            }
        }
    }

    fun playExternal(file: StreamFileEntity) {
        if (!AppUiState.serverOn) {
            MsToast.show("Turn the server on first, then play")
            return
        }
        val uri = Uri.parse("$streamBase/stream/file/${m.id}/${file.id}")
        val mime = mimeForFile(file.fileName)
        // Build subtitle URIs for external players (mpvRx, VLC).
        // Only custom-added subtitles (not MKV-embedded) — those come from
        // our /subtitles endpoint.
        val subUris = subtitles.mapNotNull { sub ->
            try {
                Uri.parse("$streamBase/sub/${sub.id}")
            } catch (e: Exception) { null }
        }.toTypedArray()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // mpv-android / mpvRx: "subs" extra (array of Uri).
            if (subUris.isNotEmpty()) {
                putExtra("subs", subUris)
                // VLC: also try "subtitles" extra.
                putExtra("subtitles", subUris)
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Play with"))
            // Record the watch (one row per file — delete-then-insert).
            scope.launch(Dispatchers.IO) {
                try {
                    val epLabel = if (file.season != null && file.episode != null) {
                        "S${file.season} E${file.episode}" +
                            (if (file.episodeEnd != null) "-${file.episodeEnd}" else "")
                    } else null
                    val dao = db.watchProgressDao()
                    dao.deleteByStreamFile(file.id)
                    dao.upsert(
                        com.moon.moonstream.index.db.WatchProgressEntity(
                            mediaId = m.id,
                            streamFileId = file.id,
                            title = m.title,
                            episodeLabel = epLabel,
                            posterUrl = m.posterUrl,
                            playedAt = System.currentTimeMillis(),
                        )
                    )
                } catch (e: Exception) { /* never break playback for logging */ }
            }
        } catch (e: ActivityNotFoundException) {
            MsToast.show("No video player found on this phone")
        }
    }

    fun deleteFile(file: StreamFileEntity) {
        scope.launch {
            db.streamDao().deleteById(file.id)
            db.mediaDao().refreshAggregates(m.id, System.currentTimeMillis())
            MsToast.show("File removed")
        }
    }

    fun toggleHide() {
        scope.launch {
            db.mediaDao().setHidden(m.id, !m.hidden)
            MsToast.show(if (!m.hidden) "Hidden — find it in the Hidden tab" else "Unhidden")
            if (!m.hidden) onBack()
        }
    }

    /** FIX_TITLE apply: re-run the matcher on the picked title, update the row. */
    fun applyFixTitle(picked: MetaPick) {
        scope.launch(Dispatchers.IO) {
            val key = ScanPrefs.getTmdbKey(context)
            val omdbKey = ScanPrefs.getOmdbKey(context)
            // Use the PICKED type (the tab the result came from), not the
            // entry's old type — fixing a wrongly-typed entry (e.g. a series
            // indexed as a movie) must re-match as the correct type,
            // otherwise the matcher finds the old wrong title again and the
            // update appears to do nothing (moon's #Love case 2026-11-02).
            val targetType = picked.type ?: m.type
            val full = MetadataMatcher.match(picked.title, picked.year, targetType, isAnime, key, omdbKey = omdbKey)
                ?.let { MetadataMatcher.enrichFromCinemeta(it, targetType) }
            db.mediaDao().updateMetadata(
                id = m.id,
                title = full?.title ?: picked.title,
                norm = normalizeTitle(full?.title ?: picked.title),
                year = full?.year ?: picked.year,
                imdbId = full?.imdbId ?: picked.imdbId,
                tmdbId = full?.tmdbId ?: picked.tmdbId,
                posterUrl = full?.posterUrl ?: picked.posterUrl,
                backdropUrl = full?.backdropUrl ?: m.backdropUrl,
                overview = full?.overview ?: picked.overview,
                // A fixed title may now have a real tt ID — the Stremio ID
                // follows it; otherwise keep the stable synthetic one.
                stremioId = StremioIds.forNewItem(
                    m.channelId,
                    normalizeTitle(full?.title ?: picked.title),
                    full?.imdbId ?: picked.imdbId,
                ),
                type = targetType,
                genres = full?.genres?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: m.genres,
                originalLanguage = full?.originalLanguage ?: m.originalLanguage,
                country = full?.country ?: m.country,
                now = System.currentTimeMillis(),
            )
            withContext(Dispatchers.Main) {
                MsToast.show(
                    if (targetType != m.type) "Metadata updated — now listed as ${targetType.replaceFirstChar { it.uppercase() }}"
                    else "Metadata updated"
                )
            }
        }
    }

    /** Refresh button: re-run the matcher on THIS entry's own title and
     *  fill in whatever is missing (genre, language, country, fresher
     *  poster/overview). Files untouched, type untouched. Passing the
     *  stored IMDb id makes the matcher do a direct lookup when it has one
     *  (more accurate, fewer calls). A field the lookup has nothing for
     *  keeps its current value — refresh never wipes data. */
    fun refreshMetadata() {
        if (refreshing) return
        refreshing = true
        scope.launch(Dispatchers.IO) {
            val key = ScanPrefs.getTmdbKey(context)
            val omdbKey = ScanPrefs.getOmdbKey(context)
            val full = MetadataMatcher.match(
                m.title, m.year, m.type, isAnime, key,
                imdbId = m.imdbId, omdbKey = omdbKey,
            )?.let { MetadataMatcher.enrichFromCinemeta(it, m.type) }
            if (full == null) {
                withContext(Dispatchers.Main) {
                    refreshing = false
                    MsToast.show("No fresh data found")
                }
                return@launch
            }
            db.mediaDao().updateMetadata(
                id = m.id,
                title = full.title,
                norm = normalizeTitle(full.title),
                year = full.year ?: m.year,
                imdbId = full.imdbId ?: m.imdbId,
                tmdbId = full.tmdbId ?: m.tmdbId,
                posterUrl = full.posterUrl ?: m.posterUrl,
                backdropUrl = full.backdropUrl ?: m.backdropUrl,
                overview = full.overview ?: m.overview,
                stremioId = StremioIds.forNewItem(
                    m.channelId,
                    normalizeTitle(full.title),
                    full.imdbId ?: m.imdbId,
                ),
                type = m.type,
                genres = full.genres?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: m.genres,
                originalLanguage = full.originalLanguage ?: m.originalLanguage,
                country = full.country ?: m.country,
                now = System.currentTimeMillis(),
            )
            withContext(Dispatchers.Main) {
                refreshing = false
                MsToast.show("Metadata refreshed")
            }
        }
    }

    /** Move this entry to another tab (Series <-> Anime). Just flips the
     *  type — title, poster and files stay exactly as they are. The
     *  Library (which watches the DB) moves the card to the new tab
     *  by itself. */
    fun moveToType(newType: String) {
        if (newType == m.type) return
        scope.launch(Dispatchers.IO) {
            db.mediaDao().updateType(m.id, newType)
            withContext(Dispatchers.Main) {
                MsToast.show("Moved to ${newType.replaceFirstChar { it.uppercase() }}")
            }
        }
    }

    /** FIX_FILE apply: move one file to the picked title (find or create). */
    fun applyFixFile(file: StreamFileEntity, picked: MetaPick) {
        scope.launch(Dispatchers.IO) {
            val norm = normalizeTitle(picked.title)
            val now = System.currentTimeMillis()
            // Use the picked type, not the source entry's type (same fix as
            // applyFixTitle — a file moved to a series must match as series).
            val targetType = picked.type ?: m.type
            var target = db.mediaDao().findByNormAnyChannel(norm)
            // FIX 2026-11-05: if target exists but lacks IMDb ID, update it
            // (needed for Skip Intro via IntroDB)
            if (target != null && target.imdbId.isNullOrBlank() && !picked.imdbId.isNullOrBlank()) {
                db.mediaDao().updateImdbId(target.id, picked.imdbId!!)
                target = db.mediaDao().findByNormAnyChannel(norm)
            }
            if (target == null) {
                val key = ScanPrefs.getTmdbKey(context)
                val omdbKey = ScanPrefs.getOmdbKey(context)
                val full = MetadataMatcher.match(picked.title, picked.year, targetType, isAnime, key, omdbKey = omdbKey)
                    ?.let { MetadataMatcher.enrichFromCinemeta(it, targetType) }
                val newId = db.mediaDao().upsert(
                    MediaItemEntity(
                        channelId = m.channelId,
                        title = full?.title ?: picked.title,
                        normalizedTitle = norm,
                        year = full?.year ?: picked.year,
                        type = targetType,
                        imdbId = full?.imdbId ?: picked.imdbId,
                        tmdbId = full?.tmdbId ?: picked.tmdbId,
                        stremioId = StremioIds.forNewItem(
                            m.channelId, norm, full?.imdbId ?: picked.imdbId,
                        ),
                        posterUrl = full?.posterUrl ?: picked.posterUrl,
                        backdropUrl = full?.backdropUrl,
                        overview = full?.overview ?: picked.overview,
                        totalSize = 0L,
                        episodeCount = 0,
                        fileCount = 0,
                        updatedAt = now,
                    ),
                )
                target = db.mediaDao().findByNormAnyChannel(norm) ?: return@launch
            }
            db.streamDao().moveToMedia(file.id, target.id)
            db.mediaDao().refreshAggregates(m.id, now)
            db.mediaDao().refreshAggregates(target.id, now)
            // Bug (moon's 2026-11-10): when the moved file was the entry's
            // only stream, the old entry stayed in the library with zero
            // files. Drop entries left empty by the move.
            db.mediaDao().deleteEmpty()
            withContext(Dispatchers.Main) {
                MsToast.show("File moved to ${target.title}")
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
            // Landscape backdrop for the header (moon's 2026-11-06): the
            // portrait poster cropped into this wide box is what made the
            // zoomed look. Backdrop first, portrait poster as fallback.
            if (m.backdropUrl != null || m.posterUrl != null) {
                AsyncImage(
                    model = m.backdropUrl ?: com.moon.moonstream.util.PosterStyle.displayUrl(
                        m.posterUrl, m.imdbId,
                        com.moon.moonstream.index.scan.ScanPrefs.getPosterStyle(context),
                        com.moon.moonstream.index.scan.ScanPrefs.getPosterTemplate(context),
                    ),
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
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IosSpinner(size = 22.dp)
                    }
                } else {
                    BareIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Refresh metadata",
                        tint = Color.White,
                        onClick = { refreshMetadata() },
                    )
                }
                BareIconButton(
                    icon = if (m.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (m.hidden) "Unhide entry" else "Hide entry",
                    tint = Color.White,
                    onClick = { toggleHide() },
                )
            }
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
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        m.overview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        // No ripple on tap (moon's fix 2026-11-12): the
                        // plot expands on tap, it shouldn't flash.
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { expanded = !expanded },
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
                    onClick = { playContinue() },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                    // Lucide icons (moon's 2026-11-10 picks): circle-play.
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_circle_play),
                )
                MsOutlinePill(
                    text = "Copy Link",
                    onClick = {
                        val first = files.firstOrNull()
                        if (first != null) copyLink(first)
                        else MsToast.show("No files to link")
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                    // Lucide icons (moon's 2026-11-10 picks): copy.
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_copy),
                )
                MsOutlinePill(
                    // "Subtitle" not "Add Subtitle" (moon's 2026-11-10):
                    // the label was already clipping at 13sp, and the icon
                    // needs the room.
                    text = "Subtitle",
                    onClick = { showAddSubtitle = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                    // Lucide icons (moon's 2026-11-10 picks): captions.
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_captions),
                )
            }

            // Secondary actions (moon's 2026-11-07 + 2026-11-09 icons):
            // Fix Metadata / Add Custom Stream share one row, the move-between-
            // tabs pill gets its own full-width row. Three pill labels plus
            // w icons cannot fit across a 360–410dp phone, and "Add Custom
            // Stream" was already being clipped there, so this gives every
            // label room instead of shrinking the text further.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsOutlinePill(
                    text = "Fix Metadata",
                    onClick = { fixMode = FixMode.FIX_TITLE },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                    icon = Icons.Outlined.AutoFixHigh,
                )
                MsOutlinePill(
                    text = "Add Custom Stream",
                    onClick = { showAddStream = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                    icon = Icons.Outlined.AddLink,
                )
            }
            // Move between tabs (moon's 2026-11-07: anime in a non-anime
            // channel lands in Series — this moves one title to Anime).
            MsOutlinePill(
                text = if (isAnime) "Move to Series" else "Move to Anime",
                onClick = { moveToType(if (isAnime) "series" else "anime") },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 13,
                icon = Icons.Outlined.SwapHoriz,
            )

            // Thin gray divider above the season list (moon's 2026-11-06):
            // 1dp, no extra spacing — it rides the column's normal gaps.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            if (isSeries) {
                SeriesSeasons(
                    media = m,
                    files = files,
                    subtitles = subtitles,
                    seasonIdx = seasonIdx,
                    onSeason = { seasonIdx = it },
                    onPlay = { playInApp(it) },
                    onCopy = { copyLink(it) },
                    onDelete = { deleteFile(it) },
                    onFixFile = { fixFile = it; fixMode = FixMode.FIX_FILE },
                    onOpenExternal = { playExternal(it) },
                    onUnlinkSub = { sub ->
                        scope.launch {
                            db.subtitleDao().delete(sub.id)
                            MsToast.show("Subtitle removed")
                        }
                    },
                    watchedAt = watchedAt,
                    onSetEpisode = { setEpisodeFile = it },
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
                        onPlay = { playInApp(f) },
                        onCopy = { copyLink(f) },
                        onDelete = { deleteFile(f) },
                        onFixFile = { fixFile = f; fixMode = FixMode.FIX_FILE },
                        onOpenExternal = { playExternal(f) },
                        watchedAt = watchedAt[f.id],
                        onSetEpisode = { setEpisodeFile = f },
                    )
                }
                SubtitleSection(
                    title = "Subtitles",
                    subs = subtitles.filter { it.season == null && it.episode == null },
                    onUnlink = { sub ->
                        scope.launch {
                            db.subtitleDao().delete(sub.id)
                            MsToast.show("Subtitle removed")
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
                                MsToast.show("Subtitle removed")
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
            mediaType = m.type,
            initialQuery = fixFile?.fileName ?: m.title,
            onApply = { picked ->
                when (mode) {
                    FixMode.FIX_TITLE -> applyFixTitle(picked)
                    FixMode.FIX_FILE -> fixFile?.let { applyFixFile(it, picked) }
                    FixMode.LINK -> Unit // not used on this screen
                }
                fixMode = null
                fixFile = null
            },
            onCustomContent = {
                // This file isn't a real show — open Create with it
                // pre-filled; Create moves the row on save.
                fixFile?.let { f ->
                    onOpenCreate(
                        CreatePrefill(
                            files = listOf(
                                PrefillFile(
                                    channelId = f.channelId,
                                    messageId = f.messageId,
                                    fileName = f.fileName,
                                    fileSize = f.fileSize,
                                    season = f.season,
                                    episode = f.episode,
                                    existingStreamId = f.id,
                                ),
                            ),
                        ),
                    )
                }
                fixMode = null
                fixFile = null
            },
        )
    }

    // Manual episode assignment (moon's pick 2026-11-02): link a file in
    // "Other files"/"Files" to a specific season + episode.
    setEpisodeFile?.let { f ->
        var seasonText by remember(f.id) {
            mutableStateOf(f.season?.toString() ?: "1")
        }
        var episodeText by remember(f.id) {
            mutableStateOf(f.episode?.toString() ?: "")
        }
        IosDialog(
            onDismiss = { setEpisodeFile = null },
            title = "Set episode",
            buttons = listOf(
                IosSheetButton(
                    label = "Save",
                    onClick = {
                        val season = seasonText.toIntOrNull()
                        val episode = episodeText.toIntOrNull()
                        if (episode == null) {
                            MsToast.show("Enter an episode number")
                            return@IosSheetButton
                        }
                        scope.launch(Dispatchers.IO) {
                            try {
                                db.streamDao().updateSeasonEpisode(
                                    f.id, season, episode
                                )
                                withContext(Dispatchers.Main) {
                                    MsToast.show("Linked to S${season ?: 1} E$episode")
                                    setEpisodeFile = null
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    MsToast.show("Failed: ${e.message}")
                                }
                            }
                        }
                    },
                ),
                IosSheetButton(
                    label = "Cancel",
                    onClick = { setEpisodeFile = null },
                ),
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    com.moon.moonstream.index.parse.FilenameParser.cleanDisplayName(f.fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF98989F),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MsTextField(
                        value = seasonText,
                        onValueChange = { seasonText = it.filter { c -> c.isDigit() } },
                        hint = "Season",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                    )
                    MsTextField(
                        value = episodeText,
                        onValueChange = { episodeText = it.filter { c -> c.isDigit() } },
                        hint = "Episode",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                    )
                }
                Text(
                    "The file moves into that episode's list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF98989F),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showAddStream) {
        AddStreamSheet(
            onDismiss = { showAddStream = false },
            onFetchFileName = { link ->
                val parsed = parseTgLink(link) ?: return@AddStreamSheet null
                val (chId, msgId) = parsed
                // TDLib history excludes fromMessageId — request msgId+1 to include it.
                (BackendProvider.backend.fetchMessages(chId, msgId + 1, 1) +
                    BackendProvider.backend.fetchMessages(chId, msgId, 1))
                    .firstOrNull { it.id == msgId }?.fileName
            },
            isSeries = isSeries,
            onSave = { link, quality, name, season, episode ->
                scope.launch(Dispatchers.IO) {
                    val parsed = parseTgLink(link)
                    if (parsed == null) {
                        withContext(Dispatchers.Main) {
                            MsToast.show("That link didn't look like a t.me/c/…/… message link")
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
                            season = season,
                            episode = episode,
                            quality = quality.ifBlank { "1080p" },
                        ),
                    )
                    db.mediaDao().refreshAggregates(m.id, System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        MsToast.show("Custom stream added")
                        showAddStream = false
                    }
                }
            },
        )
    }

    if (showAddSubtitle) {
        AddSubtitleSheet(
            unlinked = unlinkedSubs,
            isSeries = isSeries,
            onAttachExisting = { attachSub = it; showAddSubtitle = false },
            onDismiss = { showAddSubtitle = false },
            onFetchFileName = { link ->
                val parsed = parseTgLink(link) ?: return@AddSubtitleSheet null
                val (chId, msgId) = parsed
                // Pull the real filename from Telegram via the backend.
                BackendProvider.backend.fetchMessages(chId, msgId, 1)
                    .firstOrNull { it.id == msgId }?.fileName
            },
            onSaveLink = { link, language, fileName, season, episode ->
                scope.launch(Dispatchers.IO) {
                    val parsed = parseTgLink(link)
                    if (parsed == null || channelId == null) {
                        withContext(Dispatchers.Main) {
                            MsToast.show("That link didn't look like a t.me/c/…/… message link")
                        }
                        return@launch
                    }
                    val (chId, msgId) = parsed
                    val p = com.moon.moonstream.index.parse.FilenameParser.parse(
                        fileName, m.type == "anime", "",
                        com.moon.moonstream.index.scan.ScanPrefs.isAnimeAbsolute(context),
                    )
                    val useSeason = season ?: p.season
                    val useEpisode = episode ?: p.episode
                    val mediaId = ScanManager.resolveSubtitleMedia(
                        db, chId, normalizeTitle(p.title), useSeason, useEpisode,
                    ) ?: m.id
                    db.subtitleDao().insert(
                        SubtitleFileEntity(
                            channelId = chId,
                            messageId = msgId,
                            fileName = fileName,
                            fileSize = 0L,
                            mediaId = mediaId,
                            season = useSeason,
                            episode = useEpisode,
                            language = language,
                        ),
                    )
                    withContext(Dispatchers.Main) {
                        MsToast.show("Subtitle added")
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
                    MsToast.show("Subtitle attached")
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
