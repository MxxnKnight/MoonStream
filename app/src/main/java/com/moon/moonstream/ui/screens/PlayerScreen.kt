package com.moon.moonstream.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.player.PlayerUrls
import com.moon.moonstream.ui.player.SubDelayPrefs
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Which bottom sheet is open in the player. */
private enum class PlayerSheet { SUBS, AUDIO, PLAYLIST, EPISODES }

/** One selectable track row. */
private data class TrackOpt(val group: TrackGroup, val index: Int, val label: String)

/** Brightness/volume gesture HUD state. */
private sealed class GestureHud(val fraction: Float) {
    class Brightness(f: Float) : GestureHud(f)
    class Volume(f: Float) : GestureHud(f)
}

/** One episode = all its stream files (moon's playlist model 2026-11-02). */
private data class EpisodeGroup(
    val season: Int?,
    val episode: Int?,
    val files: List<StreamFileEntity>,
) {
    fun label(): String =
        if (season != null && episode != null) {
            val first = files.firstOrNull()
            val end = first?.episodeEnd
            "S$season E$episode" + (if (end != null && end != episode) "-$end" else "")
        } else {
            files.firstOrNull()?.fileName?.substringBeforeLast('.')?.take(40) ?: "Streams"
        }
}

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
    mediaId: Long = 0L,
    streamFileId: Long = 0L,
    /** Header Play = continue watching: skip the resume prompt, play from saved spot. */
    autoResume: Boolean = false,
    onClose: () -> Unit,
    onSwitchPlayer: (
        videoUrl: String, title: String, mediaTitle: String,
        mediaId: Long, streamFileId: Long,
    ) -> Unit = { _, _, _, _, _ -> },
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
            // Restore system brightness (gestures override it while playing).
            try {
                activity?.window?.attributes?.let { lp ->
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity?.window?.attributes = lp
                }
            } catch (e: Exception) { /* never break close */ }
        }
    }

    // Build the ExoPlayer instance (media item is set by the load effect below,
    // so subtitle changes / delay can rebuild it while preserving position).
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // Never autoplay before the load effect below decides (resume check).
            playWhenReady = false
        }
    }

    // Subtitle delay (moon's pick 2026-11-02): per-entry ms, server shifts
    // SRT/VTT cue timestamps via ?delayMs=.
    var delayMs by remember { mutableLongStateOf(SubDelayPrefs.get(context, mediaId)) }

    // Subtitle appearance (moon's pick 2026-11-02): all in the player.
    var subTextSize by remember {
        mutableFloatStateOf(SubDelayPrefs.getTextSize(context, mediaId))
    }
    var subStrokeOn by remember {
        mutableStateOf(SubDelayPrefs.getStrokeOn(context, mediaId))
    }
    var subTextColor by remember {
        mutableIntStateOf(SubDelayPrefs.getTextColor(context, mediaId))
    }
    var subStrokeColor by remember {
        mutableIntStateOf(SubDelayPrefs.getStrokeColor(context, mediaId))
    }

    /** Push the appearance prefs into the player's SubtitleView. */
    fun applySubtitleStyle() {
        val sv: SubtitleView = try {
            playerViewRef?.subtitleView ?: return
        } catch (e: Exception) {
            return
        }
        val edge = if (subStrokeOn) CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else CaptionStyleCompat.EDGE_TYPE_NONE
        sv.setStyle(
            CaptionStyleCompat(
                subTextColor, android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT, edge, subStrokeColor, null,
            )
        )
        sv.setFractionalTextSize(subTextSize)
    }

    // Re-apply when the view arrives or any appearance pref changes.
    LaunchedEffect(
        playerViewRef, subTextSize, subStrokeOn, subTextColor, subStrokeColor
    ) {
        applySubtitleStyle()
    }

    // Brightness/volume gestures (video-player style): left half = brightness,
    // right half = volume. A small HUD shows while adjusting.
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    var brightness by remember {
        val cur = try {
            activity?.window?.attributes?.screenBrightness ?: -1f
        } catch (e: Exception) {
            -1f
        }
        mutableFloatStateOf(if (cur >= 0f) cur else 0.5f)
    }
    var gestureHud by remember { mutableStateOf<GestureHud?>(null) }

    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.05f, 1f)
        try {
            activity?.window?.attributes?.let { lp ->
                lp.screenBrightness = brightness
                activity?.window?.attributes = lp
            }
        } catch (e: Exception) { /* never break playback */ }
    }

    // Hide the gesture HUD shortly after the last adjustment.
    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(1200)
            gestureHud = null
        }
    }

    // Playlist data for series (episodes sheet + next-up card).
    // moon's model 2026-11-02: Episodes = episode list; Playlist = all streams
    // of the current episode (series) or all streams of the entry (movie).
    val files by db.streamDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val allSubs by db.subtitleDao().observeByMedia(mediaId).collectAsState(initial = emptyList())
    val orderedFiles = remember(files) {
        files.sortedWith(
            compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }, { it.id })
        )
    }
    val groups = remember(orderedFiles) {
        orderedFiles.groupBy { Pair(it.season, it.episode) }
            .map { (key, fs) -> EpisodeGroup(key.first, key.second, fs) }
    }
    val currentGroupIdx = groups.indexOfFirst { g -> g.files.any { it.id == streamFileId } }
    val currentGroup = groups.getOrNull(currentGroupIdx)
    val nextGroup = groups.getOrNull(currentGroupIdx + 1)
    val showEpisodesBtn = groups.size > 1
    /** Playlist sheet content: this episode's streams (series) or all streams (movie). */
    val playlistFiles: List<StreamFileEntity> = when {
        currentGroup != null && showEpisodesBtn -> currentGroup.files
        else -> orderedFiles
    }
    val showPlaylistBtn = playlistFiles.size > 1

    // Subtitle (uri, fileName) pairs — mime type comes from the real file name.
    val subPairs = remember(allSubs, delayMs) {
        allSubs.map { sub -> Uri.parse(PlayerUrls.sub(sub.id, delayMs)) to sub.fileName }
    }

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
        AppLog.i("PLAYER", "Switching to ${episodeLabel(f)}")
        onSwitchPlayer(url, "$mediaTitle ${episodeLabel(f)}", mediaTitle, mediaId, f.id)
    }

    /** Adjust subtitle delay (250ms steps, ±10s), saved per entry. */
    fun adjustDelay(delta: Long) {
        val new = (delayMs + delta).coerceIn(-10_000L, 10_000L)
        if (new == delayMs) return
        delayMs = new
        SubDelayPrefs.set(context, mediaId, new)
        AppLog.i("PLAYER", "Subtitle delay ${if (new >= 0) "+" else ""}${new}ms")
        // subPairs recomputes from delayMs; the load effect below rebuilds.
    }

    // Aspect-ratio cycle (moon's pick 2026-11-02): each tap → next mode.
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        )
    }
    var resizeIndex by remember { mutableIntStateOf(0) }
    fun cycleResize() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        playerViewRef?.resizeMode = resizeModes[resizeIndex].first
        MsToast.show(resizeModes[resizeIndex].second)
        AppLog.i("PLAYER", "Resize mode: ${resizeModes[resizeIndex].second}")
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

    var mediaReady by remember { mutableStateOf(false) }

    // Load / rebuild the media item. First run: resume check (prompt, or
    // straight to the saved spot for the header Play button). Later runs
    // (subs arrived, delay changed): rebuild keeping position + play state.
    LaunchedEffect(videoUri, subPairs) {
        val isFirst = !mediaReady
        val pos = if (isFirst) 0L else player.currentPosition.coerceAtLeast(0L)
        val keepPlaying = if (isFirst) false else player.isPlaying
        player.setMediaItem(buildMediaItem(videoUri, subPairs), pos)
        player.prepare()
        if (isFirst) {
            mediaReady = true
            if (streamFileId == 0L) {
                player.play()
                return@LaunchedEffect
            }
            val saved = withContext(Dispatchers.IO) {
                try {
                    db.watchProgressDao().get(mediaId, streamFileId)?.positionMs ?: 0L
                } catch (e: Exception) { 0L }
            }
            when {
                // Header Play = continue watching: straight to the saved spot.
                autoResume && saved > 10_000L -> {
                    player.seekTo(saved)
                    player.play()
                }
                saved > 10_000L -> {
                    resumeFrom = saved
                    awaitingResumeChoice = true
                }
                else -> player.play()
            }
        } else if (keepPlaying) {
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
            val finished = dur > 0 && pos >= dur * 9 / 10
            val finalPos = if (pos < 10_000L || finished) 0L else pos
            if (streamFileId != 0L) {
                ioScope.launch(Dispatchers.IO) {
                    try {
                        db.watchProgressDao().updatePosition(
                            streamFileId, finalPos, System.currentTimeMillis(),
                        )
                    } catch (e: Exception) { /* never break close */ }
                    // Auto-delete the downloaded file after watching
                    // (moon's pick 2026-11-02): watched-to-the-end ⇒ the
                    // local bytes are removed so storage doesn't grow.
                    if (finished) {
                        try {
                            val f = files.find { it.id == streamFileId }
                            if (f != null) {
                                val deleted = BackendProvider.backend
                                    .deleteCachedFile(f.channelId, f.messageId)
                                AppLog.i(
                                    "PLAYER",
                                    "Auto-delete stream cache for '${f.fileName}': $deleted",
                                )
                            }
                        } catch (e: Exception) { /* never break close */ }
                    }
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
    val nextUpFile = nextGroup?.files?.firstOrNull()
    val showNextUp = nextUpFile != null && !nextUpDismissed &&
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
            .pointerInput(sheet) {
                // Vertical swipes: left half = brightness, right half = volume
                // (video-player style). Skipped while a sheet is open.
                detectVerticalDragGestures(
                    onDragEnd = { /* HUD hides on its own timer */ },
                    onDragCancel = { },
                ) { change, dragAmount ->
                    if (sheet == null) {
                        change.consume()
                        // Up = increase; a full-height swipe ≈ the full range.
                        val delta = -dragAmount / 800f
                        if (change.position.x < size.width / 2f) {
                            setBrightness(brightness + delta)
                            gestureHud = GestureHud.Brightness(brightness)
                        } else {
                            val cur =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val newVol = (cur + (delta * maxVolume).toInt())
                                .coerceIn(0, maxVolume)
                            if (newVol != cur) {
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC, newVol, 0
                                )
                            }
                            gestureHud =
                                GestureHud.Volume(newVol / maxVolume.toFloat())
                        }
                    }
                }
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

        // Top bar (mpvex-style): back + title pill on the left,
        // quick-action cluster on the right.
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onClose,
                )
                Spacer(Modifier.width(12.dp))
                // Title pill: tappable → playlist/episodes sheet when available.
                Surface(
                    onClick = {
                        if (showEpisodesBtn) sheet = PlayerSheet.EPISODES
                        else if (showPlaylistBtn) sheet = PlayerSheet.PLAYLIST
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Top-right cluster.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showEpisodesBtn) {
                        PlayerIconButton(
                            icon = Icons.Filled.PlaylistPlay,
                            contentDescription = "Episodes",
                            onClick = { sheet = PlayerSheet.EPISODES },
                        )
                    }
                    if (showPlaylistBtn) {
                        PlayerIconButton(
                            icon = Icons.Filled.List,
                            contentDescription = "Playlist",
                            onClick = { sheet = PlayerSheet.PLAYLIST },
                        )
                    }
                    PlayerIconButton(
                        icon = Icons.Filled.PhotoCamera,
                        contentDescription = "Screenshot",
                        onClick = { takeScreenshot() },
                    )
                }
            }
        }

        // Center transport: -10s / play-pause / +10s (mpvex-style: 64dp play).
        if (controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = "Back 10s",
                    onClick = {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                    },
                    size = 52.dp,
                    iconSize = 24.dp,
                )
                // Big play/pause: 64dp circle, no border (mpvex-style).
                Surface(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                PlayerIconButton(
                    icon = Icons.Filled.Forward10,
                    contentDescription = "Forward 10s",
                    onClick = {
                        player.seekTo(
                            (player.currentPosition + 10_000)
                                .coerceAtMost(duration.coerceAtLeast(0L))
                        )
                    },
                    size = 52.dp,
                    iconSize = 24.dp,
                )
            }
        }

        // Bottom cluster (mpvex-style): corner button clusters above the
        // seekbar, seekbar pinned to the bottom edge with monospace timers.
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                // Button row: left cluster (Subs, Audio) / right cluster (Aspect).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerIconButton(
                            icon = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = { openTrackSheet(PlayerSheet.SUBS) },
                        )
                        PlayerIconButton(
                            icon = Icons.Filled.Audiotrack,
                            contentDescription = "Audio",
                            onClick = { openTrackSheet(PlayerSheet.AUDIO) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Aspect button shows the current mode as its label
                        // (mpvex-style stateful button).
                        Surface(
                            onClick = { cycleResize() },
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.height(45.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AspectRatio,
                                    contentDescription = "Aspect ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    resizeModes[resizeIndex].second,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Seekbar row: timer — bar — timer (monospace, mpvex-style).
                if (duration > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.width(64.dp),
                        )
                        // Wide touch target: extra vertical padding around the slider.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
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
                        }
                        Text(
                            formatTime(duration),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }

        // Brightness/volume gesture HUD (mpvex-style): slim vertical slider
        // at the screen edge — left edge for brightness, right for volume.
        gestureHud?.let { hud ->
            val edge = if (hud is GestureHud.Brightness) Alignment.CenterStart
            else Alignment.CenterEnd
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = edge,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Icon(
                        when (hud) {
                            is GestureHud.Brightness -> Icons.Filled.BrightnessMedium
                            is GestureHud.Volume -> Icons.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(120.dp)
                            .background(
                                Color.White.copy(alpha = 0.25f),
                                RoundedCornerShape(3.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(hud.fraction.coerceIn(0f, 1f))
                                .align(Alignment.BottomCenter)
                                .background(MsColors.Accent, RoundedCornerShape(3.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(hud.fraction * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                    )
                }
            }
        }

        // Next-up card (TMPlayer pattern): last 30s of an episode.
        if (showNextUp && nextUpFile != null && nextGroup != null) {
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
                        nextGroup.label(),
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
                            onClick = { switchEpisode(nextUpFile) },
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

        // Bottom sheets: subs / audio / playlist / episodes.
        // Pull down on the sheet (from the top, list at top) to close it.
        if (sheet != null) {
            val mode = sheet!!
            var sheetOffsetY by remember(mode) { mutableFloatStateOf(0f) }
            val sheetScroll = rememberScrollState()
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
                        .heightIn(max = 520.dp)
                        .offset { IntOffset(0, sheetOffsetY.roundToInt()) }
                        .background(
                            MsColors.Surface,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        )
                        // NOTE: drag-to-close lives ONLY on the drag handle
                        // below (generous touch target). A sheet-wide drag
                        // detector was here before and fought the vertical
                        // scroll for the same gestures — scrolling felt stuck
                        // and the handle needed pixel-perfect touches.
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* consume taps: don't dismiss */ }
                        .padding(16.dp)
                        .verticalScroll(sheetScroll),
                ) {
                    // Drag handle: full-width, 32dp-tall touch target with the
                    // visual bar centered — easy to grab, no precise aiming.
                    // The drag-to-close gesture lives here only.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        // Pulled far enough → close, else snap back.
                                        if (sheetOffsetY > 140) sheet = null
                                        else sheetOffsetY = 0f
                                    },
                                    onDragCancel = { sheetOffsetY = 0f },
                                ) { change, dragAmount ->
                                    sheetOffsetY =
                                        (sheetOffsetY + dragAmount).coerceAtLeast(0f)
                                    change.consume()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .background(
                                    Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                    Text(
                        when (mode) {
                            PlayerSheet.SUBS -> "Subtitles"
                            PlayerSheet.AUDIO -> "Audio"
                            PlayerSheet.PLAYLIST -> "Playlist"
                            PlayerSheet.EPISODES -> "Episodes"
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
                            // Subtitle delay (moon's pick 2026-11-02): ±250ms steps.
                            if (forSubs) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Delay",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { adjustDelay(-250) }) {
                                        Icon(
                                            Icons.Filled.Remove,
                                            contentDescription = "Delay -250ms",
                                            tint = MsColors.TextPrimary,
                                        )
                                    }
                                    Text(
                                        formatDelay(delayMs),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.Accent,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(80.dp),
                                    )
                                    IconButton(onClick = { adjustDelay(250) }) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Delay +250ms",
                                            tint = MsColors.TextPrimary,
                                        )
                                    }
                                }
                                // Appearance (moon's pick 2026-11-02).
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Appearance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MsColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Size",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SheetOptionBtn("S", subTextSize == 0.045f) {
                                        subTextSize = 0.045f
                                        SubDelayPrefs.setTextSize(context, mediaId, 0.045f)
                                        applySubtitleStyle()
                                    }
                                    SheetOptionBtn("M", subTextSize == 0.06f) {
                                        subTextSize = 0.06f
                                        SubDelayPrefs.setTextSize(context, mediaId, 0.06f)
                                        applySubtitleStyle()
                                    }
                                    SheetOptionBtn("L", subTextSize == 0.08f) {
                                        subTextSize = 0.08f
                                        SubDelayPrefs.setTextSize(context, mediaId, 0.08f)
                                        applySubtitleStyle()
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Stroke",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SheetOptionBtn("On", subStrokeOn) {
                                        subStrokeOn = true
                                        SubDelayPrefs.setStrokeOn(context, mediaId, true)
                                        applySubtitleStyle()
                                    }
                                    SheetOptionBtn("Off", !subStrokeOn) {
                                        subStrokeOn = false
                                        SubDelayPrefs.setStrokeOn(context, mediaId, false)
                                        applySubtitleStyle()
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Text color",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ColorDot(Color.White, subTextColor == Color.White.toArgb()) {
                                        subTextColor = Color.White.toArgb()
                                        SubDelayPrefs.setTextColor(context, mediaId, subTextColor)
                                        applySubtitleStyle()
                                    }
                                    ColorDot(Color.Yellow, subTextColor == Color.Yellow.toArgb()) {
                                        subTextColor = Color.Yellow.toArgb()
                                        SubDelayPrefs.setTextColor(context, mediaId, subTextColor)
                                        applySubtitleStyle()
                                    }
                                    ColorDot(Color(0xFF00E5FF), subTextColor == 0xFF00E5FF.toInt()) {
                                        subTextColor = 0xFF00E5FF.toInt()
                                        SubDelayPrefs.setTextColor(context, mediaId, subTextColor)
                                        applySubtitleStyle()
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Stroke color",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ColorDot(Color.Black, subStrokeColor == Color.Black.toArgb()) {
                                        subStrokeColor = Color.Black.toArgb()
                                        SubDelayPrefs.setStrokeColor(context, mediaId, subStrokeColor)
                                        applySubtitleStyle()
                                    }
                                    ColorDot(Color.White, subStrokeColor == Color.White.toArgb()) {
                                        subStrokeColor = Color.White.toArgb()
                                        SubDelayPrefs.setStrokeColor(context, mediaId, subStrokeColor)
                                        applySubtitleStyle()
                                    }
                                    ColorDot(Color.Yellow, subStrokeColor == Color.Yellow.toArgb()) {
                                        subStrokeColor = Color.Yellow.toArgb()
                                        SubDelayPrefs.setStrokeColor(context, mediaId, subStrokeColor)
                                        applySubtitleStyle()
                                    }
                                }
                            }
                        }
                        PlayerSheet.PLAYLIST -> {
                            // All streams of this episode (series) / entry (movie).
                            playlistFiles.forEach { f ->
                                val isCurrent = f.id == streamFileId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sheet = null
                                            switchEpisode(f)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            f.quality.ifBlank {
                                                f.fileName.substringBeforeLast('.').take(40)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) MsColors.Accent
                                            else MsColors.TextPrimary,
                                        )
                                        if (f.fileSize > 0) {
                                            Text(
                                                formatSize(f.fileSize),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MsColors.TextSecondary,
                                            )
                                        }
                                    }
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
                        PlayerSheet.EPISODES -> {
                            groups.forEach { g ->
                                val isCurrent = g.files.any { it.id == streamFileId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sheet = null
                                            switchEpisode(g.files.first())
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        g.label(),
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

/** Build the player's media item: video + subtitle tracks (mime from real file names). */
@OptIn(UnstableApi::class)
private fun buildMediaItem(video: Uri, subs: List<Pair<Uri, String>>): MediaItem =
    MediaItem.Builder()
        .setUri(video)
        .setSubtitleConfigurations(
            subs.mapIndexed { index, (uri, fileName) ->
                MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(
                        when {
                            fileName.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
                            fileName.endsWith(".ass", ignoreCase = true) ||
                                fileName.endsWith(".ssa", ignoreCase = true) -> "text/x-ssa"
                            else -> "application/x-subrip"
                        },
                    )
                    .setLanguage("und")
                    .setId("sub_$index")
                    .build()
            },
        )
        .build()

/** Small labeled button for the player's bottom action row. */
@Composable
/**
 * mpvex-style player button: 45dp circle, subtle white background +
 * 1dp border, 20dp glyph. No heavy backgrounds, no labels.
 */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 45.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) MsColors.Accent else Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Small pill option button for the player sheets (size, stroke on/off). */
@Composable
private fun SheetOptionBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MsColors.Accent else MsColors.Bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MsColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Color dot for the subtitle color pickers. */
@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(34.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MsColors.Accent
                else Color.White.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .padding(4.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
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

private fun formatDelay(ms: Long): String =
    (if (ms >= 0) "+" else "") + "$ms ms"

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%d MB".format(mb.toInt())
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
