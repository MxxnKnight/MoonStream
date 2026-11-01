package com.moon.moonstream.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.player.PlayerUrls
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which bottom sheet is open in the player. */
private enum class PlayerSheet { SUBS, AUDIO, PLAYLIST }

/** One selectable track row. */
private data class TrackOpt(val group: TrackGroup, val index: Int, val label: String)

/**
 * In-app video player (Phase 6).
 * moon's rules: landscape + true fullscreen, no PiP ever.
 * Streams from MoonStream's localhost HTTP server via plain MediaItem.fromUri().
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUri: Uri,
    title: String,
    mediaTitle: String = title,
    subtitleUris: List<Uri> = emptyList(),
    mediaId: Long = 0L,
    streamFileId: Long = 0L,
    onClose: () -> Unit,
    onSwitchPlayer: (
        videoUrl: String, title: String, mediaTitle: String,
        subtitleUrls: List<String>, mediaId: Long, streamFileId: Long,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { MsDatabase.get(context) }
    val ioScope = rememberCoroutineScope()

    // moon's rule: video plays in landscape + true fullscreen (like video players).
    DisposableEffect(Unit) {
        val prevOrientation =
            activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        insets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            activity?.requestedOrientation = prevOrientation
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Build the ExoPlayer instance.
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .setSubtitleConfigurations(
                    subtitleUris.mapIndexed { index, uri ->
                        MediaItem.SubtitleConfiguration.Builder(uri)
                            .setMimeType(
                                when {
                                    uri.toString().endsWith(".vtt") -> "text/vtt"
                                    uri.toString().endsWith(".ass") ||
                                    uri.toString().endsWith(".ssa") -> "text/x-ssa"
                                    else -> "application/x-subrip"
                                }
                            )
                            .setLanguage("und")
                            .setId("sub_$index")
                            .build()
                    }
                )
                .build()
            setMediaItem(mediaItem)
            prepare()
            // Starts paused — the resume check below decides play vs prompt.
            playWhenReady = false
        }
    }

    // Playlist data for series (episodes sheet + next-up card).
    val files by db.streamDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val allSubs by db.subtitleDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val orderedFiles = remember(files) {
        files.sortedWith(
            compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }, { it.id })
        )
    }
    val currentIndex = orderedFiles.indexOfFirst { it.id == streamFileId }
    val nextFile: StreamFileEntity? =
        if (currentIndex >= 0) orderedFiles.getOrNull(currentIndex + 1) else null
    val hasPlaylist = orderedFiles.size > 1

    fun episodeLabel(f: StreamFileEntity): String =
        if (f.season != null && f.episode != null) {
            "S${f.season} E${f.episode}" +
                (if (f.episodeEnd != null) "-${f.episodeEnd}" else "")
        } else {
            f.fileName.substringBeforeLast('.').take(40)
        }

    /** Switch playback to another file of the same entry. */
    fun switchEpisode(f: StreamFileEntity) {
        if (f.id == streamFileId) return
        val url = PlayerUrls.stream(mediaId, f.id)
        val subs = allSubs.map { PlayerUrls.sub(it.id) }
        AppLog.i("PLAYER", "Switching to ${episodeLabel(f)}")
        onSwitchPlayer(url, "$mediaTitle ${episodeLabel(f)}", mediaTitle, subs, mediaId, f.id)
    }

    /** Capture the current video frame into Pictures/MoonStream. */
    fun takeScreenshot() {
        val sv = playerViewRef?.videoSurfaceView as? SurfaceView
        if (sv == null || sv.width <= 0 || sv.height <= 0) {
            MsToast.show("Screenshot not ready yet")
            return
        }
        val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            sv, bmp,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    try {
                        val values = ContentValues().apply {
                            put(
                                MediaStore.Images.Media.DISPLAY_NAME,
                                "MoonStream_${System.currentTimeMillis()}.jpg",
                            )
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MoonStream")
                        }
                        val uri = context.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        )
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            MsToast.show("Screenshot saved")
                            AppLog.i("PLAYER", "Screenshot saved")
                        } else {
                            MsToast.show("Screenshot failed")
                        }
                    } catch (e: Exception) {
                        MsToast.show("Screenshot failed")
                    }
                } else {
                    MsToast.show("Screenshot failed")
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    // Resume state (moon's design 2026-11-02).
    var awaitingResumeChoice by remember { mutableStateOf(false) }
    var resumeFrom by remember { mutableLongStateOf(0L) }

    // Sheet + track state.
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var trackOptions by remember { mutableStateOf(listOf<TrackOpt>()) }
    var selectedOpt by remember { mutableStateOf<TrackOpt?>(null) }
    var subsOff by remember { mutableStateOf(false) }
    var nextUpDismissed by remember { mutableStateOf(false) }

    /** Snapshot the player's tracks and open the subs/audio sheet. */
    fun openTrackSheet(mode: PlayerSheet) {
        val type = if (mode == PlayerSheet.SUBS) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
        val snap = player.currentTracks
        val opts = mutableListOf<TrackOpt>()
        for (g in snap.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val label = f.label?.takeIf { it.isNotBlank() }
                    ?: f.language?.takeIf { it.isNotBlank() }
                    ?: "Track ${i + 1}"
                opts.add(TrackOpt(g.mediaTrackGroup, i, label))
            }
        }
        val off = mode == PlayerSheet.SUBS &&
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        subsOff = off
        // Selected option from the same snapshot (identity-safe).
        val selGroup = snap.groups.find { it.type == type && it.isSelected }
        selectedOpt = if (selGroup == null || off) {
            null
        } else {
            val override = player.trackSelectionParameters.overrides[selGroup.mediaTrackGroup]
            val idx = try {
                override?.trackIndices?.get(0) ?: 0
            } catch (e: Exception) {
                0
            }
            opts.find { it.group === selGroup.mediaTrackGroup && it.index == idx }
        }
        trackOptions = opts
        sheet = mode
    }

    /** Apply a track pick (null = subtitles Off). */
    fun selectTrack(opt: TrackOpt?, forSubs: Boolean) {
        val type = if (forSubs) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
        val b = player.trackSelectionParameters.buildUpon()
        if (opt == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(opt.group, opt.index))
        }
        player.trackSelectionParameters = b.build()
        if (forSubs) {
            subsOff = (opt == null)
            selectedOpt = opt
        }
        val name = opt?.label ?: "Off"
        AppLog.i("PLAYER", "Track picked (${if (forSubs) "subs" else "audio"}): $name")
        MsToast.show(name)
        sheet = null
    }

    // Load the saved position once; decide play vs resume prompt.
    LaunchedEffect(streamFileId, mediaId) {
        if (streamFileId == 0L) {
            player.play()
            return@LaunchedEffect
        }
        val saved = withContext(Dispatchers.IO) {
            try {
                db.watchProgressDao().get(mediaId, streamFileId)?.positionMs ?: 0L
            } catch (e: Exception) { 0L }
        }
        if (saved > 10_000L) {
            resumeFrom = saved
            awaitingResumeChoice = true
        } else {
            player.play()
        }
    }

    // Save position every 5s while playing (for Resume + "watched X ago").
    LaunchedEffect(streamFileId) {
        if (streamFileId == 0L) return@LaunchedEffect
        while (true) {
            delay(5000)
            val pos = player.currentPosition
            if (pos > 10_000L) {
                withContext(Dispatchers.IO) {
                    try {
                        db.watchProgressDao().updatePosition(
                            streamFileId, pos, System.currentTimeMillis(),
                        )
                    } catch (e: Exception) { /* never break playback */ }
                }
            }
        }
    }

    // Release on lifecycle destroy / dispose.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                // Don't auto-resume while the Resume prompt is up.
                Lifecycle.Event.ON_RESUME -> if (!awaitingResumeChoice) player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Final save: barely-started (<10s) or nearly finished (>=90%,
            // e.g. credits) clears the resume position — Kodi-style rules.
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            val finalPos = if (pos < 10_000L || (dur > 0 && pos >= dur * 9 / 10)) 0L else pos
            if (streamFileId != 0L) {
                ioScope.launch(Dispatchers.IO) {
                    try {
                        db.watchProgressDao().updatePosition(
                            streamFileId, finalPos, System.currentTimeMillis(),
                        )
                    } catch (e: Exception) { /* never break close */ }
                }
            }
            player.release()
        }
    }

    // Controls visibility state.
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // Playback state UI (moon's bug 2026-11-02: black screen, no feedback).
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Log what we're about to play (host:port + path, no query).
    LaunchedEffect(videoUri) {
        val portPart = if (videoUri.port != -1) ":${videoUri.port}" else ""
        AppLog.i("PLAYER", "Opening \"$title\" — ${videoUri.host}$portPart${videoUri.path}")
    }

    // Poll position/duration.
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                    isBuffering = false
                    playerError = null
                } else if (state == Player.STATE_BUFFERING) {
                    isBuffering = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val msg = "${error.errorCodeName}: ${error.message}"
                AppLog.e("PLAYER", "Playback failed — $msg")
                playerError = msg
                isBuffering = false
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                if (duration <= 0L) duration = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // Auto-hide controls after 3s.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Next-up card: last 30s, only when a next episode exists.
    val showNextUp = nextFile != null && !nextUpDismissed &&
        duration > 60_000L && duration - currentPosition in 1..30_000L
    val nextUpSecs = ((duration - currentPosition) / 1000).toInt().coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        // Double-tap left/right: ∓10s (TMPlayer pattern).
                        val forward = offset.x > size.width / 2
                        val newPos = (player.currentPosition + if (forward) 10_000 else -10_000)
                            .coerceIn(0L, player.duration.coerceAtLeast(0L))
                        player.seekTo(newPos)
                    },
                )
            }
    ) {
        // Video surface.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // We draw our own Compose controls.
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar: close + title.
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }

        // Center transport: -10s / play-pause / +10s.
        if (controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                }) {
                    Icon(
                        Icons.Filled.Replay10, "Back 10s",
                        tint = Color.White, modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White, modifier = Modifier.size(64.dp),
                    )
                }
                IconButton(onClick = {
                    player.seekTo(
                        (player.currentPosition + 10_000)
                            .coerceAtMost(duration.coerceAtLeast(0L))
                    )
                }) {
                    Icon(
                        Icons.Filled.Forward10, "Forward 10s",
                        tint = Color.White, modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        // Bottom cluster: seek bar + times + action buttons.
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (duration > 0L) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { player.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MsColors.Accent,
                            activeTrackColor = MsColors.Accent,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            formatTime(duration),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerActionButton(
                        icon = Icons.Filled.ClosedCaption,
                        label = "Subs",
                        onClick = { openTrackSheet(PlayerSheet.SUBS) },
                    )
                    PlayerActionButton(
                        icon = Icons.Filled.Audiotrack,
                        label = "Audio",
                        onClick = { openTrackSheet(PlayerSheet.AUDIO) },
                    )
                    PlayerActionButton(
                        icon = Icons.Filled.PhotoCamera,
                        label = "Shot",
                        onClick = { takeScreenshot() },
                    )
                    if (hasPlaylist) {
                        PlayerActionButton(
                            icon = Icons.Filled.PlaylistPlay,
                            label = "Episodes",
                            onClick = { sheet = PlayerSheet.PLAYLIST },
                        )
                    }
                }
            }
        }

        // Next-up card (TMPlayer pattern): last 30s of an episode.
        if (showNextUp && nextFile != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 150.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MsColors.Surface),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.TextSecondary,
                    )
                    Text(
                        episodeLabel(nextFile),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        "Starting in ${nextUpSecs}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nextUpDismissed = true }) {
                            Text("Hide", color = MsColors.TextSecondary)
                        }
                        Button(
                            onClick = { switchEpisode(nextFile) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MsColors.Accent,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Play now")
                        }
                    }
                }
            }
        }

        // Bottom sheets: subs / audio / playlist.
        if (sheet != null) {
            val mode = sheet!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { sheet = null },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MsColors.Surface,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* consume taps: don't dismiss */ }
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        when (mode) {
                            PlayerSheet.SUBS -> "Subtitles"
                            PlayerSheet.AUDIO -> "Audio"
                            PlayerSheet.PLAYLIST -> "Episodes"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    when (mode) {
                        PlayerSheet.SUBS, PlayerSheet.AUDIO -> {
                            val forSubs = mode == PlayerSheet.SUBS
                            if (forSubs) {
                                TrackRow(
                                    label = "Off",
                                    selected = subsOff && selectedOpt == null,
                                    onClick = { selectTrack(null, true) },
                                )
                            }
                            if (trackOptions.isEmpty()) {
                                Text(
                                    "No tracks found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MsColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                            trackOptions.forEach { opt ->
                                TrackRow(
                                    label = opt.label,
                                    selected = opt == selectedOpt,
                                    onClick = { selectTrack(opt, forSubs) },
                                )
                            }
                        }
                        PlayerSheet.PLAYLIST -> {
                            orderedFiles.forEach { f ->
                                val isCurrent = f.id == streamFileId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sheet = null
                                            switchEpisode(f)
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        episodeLabel(f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) MsColors.Accent
                                        else MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isCurrent) {
                                        Text(
                                            "Playing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MsColors.Accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Resume prompt (moon's design 2026-11-02): shown on open when this
        // file has a saved position.
        if (awaitingResumeChoice) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .background(MsColors.Surface, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Resume playback?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        "Continue from ${formatTime(resumeFrom)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                awaitingResumeChoice = false
                                player.seekTo(0L)
                                player.play()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MsColors.Bg,
                                contentColor = MsColors.TextPrimary,
                            ),
                        ) {
                            Text("Start over")
                        }
                        Button(
                            onClick = {
                                awaitingResumeChoice = false
                                player.seekTo(resumeFrom)
                                player.play()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MsColors.Accent,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Resume")
                        }
                    }
                }
            }
        }

        // Loading spinner while the stream buffers (moon's bug 2026-11-02:
        // black screen with zero feedback).
        if (isBuffering && playerError == null && !awaitingResumeChoice) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MsColors.Accent)
            }
        }

        // Playback error: show what failed + retry (never a silent black).
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Couldn't play this file",
                        style = MaterialTheme.typography.titleMedium,
                        color = MsColors.TextPrimary,
                    )
                    Text(
                        playerError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MsColors.Bg,
                                contentColor = MsColors.TextPrimary,
                            ),
                        ) {
                            Text("Close")
                        }
                        Button(
                            onClick = {
                                playerError = null
                                isBuffering = true
                                player.prepare()
                                player.play()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MsColors.Accent,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

/** Small labeled button for the player's bottom action row. */
@Composable
private fun PlayerActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** One row in the subs/audio picker sheet. */
@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MsColors.Accent else MsColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MsColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
