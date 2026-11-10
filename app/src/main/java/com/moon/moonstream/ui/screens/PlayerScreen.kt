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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.IosSpinner
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSlider
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.player.MsDataSourceFactory
import com.moon.moonstream.ui.player.PlayerUrls
import com.moon.moonstream.ui.player.SubDelayPrefs
import com.moon.moonstream.ui.player.SubtitleCues
import com.moon.moonstream.ui.player.TrackLangPrefs
import com.moon.moonstream.ui.player.VideoTrimmer
import com.moon.moonstream.ui.player.TdDataSource
import com.moon.moonstream.ui.player.TdStreamUri
import com.moon.moonstream.ui.player.TdSubUri
import com.moon.moonstream.ui.state.LanguagePrefs
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.util.AnimeSkip
import com.moon.moonstream.util.AppLog
import com.moon.moonstream.util.AniSkip
import com.moon.moonstream.util.IntroDb
import com.moon.moonstream.util.SkipDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Which bottom sheet is open in the player. */
private enum class PlayerSheet { SUBS, AUDIO, PLAYLIST, EPISODES, SUBLINES }

/** One selectable track row. */
private data class TrackOpt(val group: TrackGroup, val index: Int, val label: String)

/** One skippable segment (intro, ending/outro, recap, preview) — the single
 * Skip button shows the label of whichever segment the playhead is inside. */
private data class SkipSegment(val startMs: Long, val endMs: Long, val label: String)

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
/**
 * Audio renderers for the in-app player.
 *
 * moon's phone has no hardware decoder for Dolby/DTS, so ExoPlayer's
 * MediaCodec path dies with DECODER_INIT_FAILED on EAC3 files. The bundled
 * tiny FFmpeg (docs/FFMPEG.md) decodes exactly eac3, ac3, dca and truehd;
 * nothing else was compiled into it, so every other codec (AAC, Opus, MP3…)
 * keeps using the hardware path and its battery saving.
 *
 * PREFER is safe because FfmpegAudioRenderer only claims a format when the
 * .so really has that decoder — for AAC it declines and MediaCodec wins.
 * If the .so is missing we fall back to hardware-only instead of crashing.
 */
@OptIn(UnstableApi::class)
private fun buildRenderersFactory(context: android.content.Context): DefaultRenderersFactory {
    val ffmpegAvailable = try {
        FfmpegLibrary.isAvailable()
    } catch (t: Throwable) {
        AppLog.w("PLAYER", "FFmpeg extension failed to load: ${t.message}")
        false
    }
    if (ffmpegAvailable) {
        AppLog.i(
            "PLAYER",
            "FFmpeg audio decoders ready (${FfmpegLibrary.getVersion()}) — " +
                "eac3/ac3/dca/truehd in software",
        )
    } else {
        // Diagnostic: why did FFmpeg fail to load? (moon's bug 2026-11-04)
        try {
            System.loadLibrary("ffmpegJNI")
            AppLog.w("PLAYER", "FFmpeg .so loads manually but FfmpegLibrary.isAvailable() is false — version check failed")
        } catch (e: UnsatisfiedLinkError) {
            AppLog.e("PLAYER", "FFmpeg .so load failed: ${e.message}")
        } catch (e: Exception) {
            AppLog.e("PLAYER", "FFmpeg load error: ${e.message}")
        }
        AppLog.w("PLAYER", "FFmpeg extension not available — hardware decoders only")
    }
    return DefaultRenderersFactory(context)
        .setExtensionRendererMode(
            if (ffmpegAvailable) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
        )
        // Low-end pass (moon's 2026-11-09): cheap Android boxes and old phones
        // often advertise a decoder that then fails on the first frame. With
        // fallback on, Media3 quietly moves to the next decoder for that
        // track instead of showing a playback error.
        .setEnableDecoderFallback(true)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUri: Uri,
    title: String,
    mediaTitle: String = title,
    mediaId: Long = 0L,
    streamFileId: Long = 0L,
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

    // System back gesture/button closes the player (moon's bug 2026-11-04:
    // only the in-player back button worked).
    BackHandler(onBack = onClose)

    // Track if we're switching episodes (not closing) to avoid portrait flicker.
    var isSwitching by remember { mutableStateOf(false) }

    // Scrub preview (moon's 2026-11-07): while the finger drags the
    // seekbar, this holds the exact target time. The player seeks only
    // once, when the finger lifts — live-seeking on every move
    // re-buffered the stream each time, so the player could only land
    // on a few coarse points and precise seeking was impossible.
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    // moon's rule: video plays in landscape + true fullscreen (like video players).
    // Orientation: landscape while playing. Keyed on streamFileId so it
    // re-applies when switching episodes (moon's bug 2026-11-02: Next
    // episode opened in portrait). On dispose, restore portrait ONLY if not
    // switching episodes (moon's bug 2026-11-04: Next caused portrait).
    // Orientation: landscape while playing. Keyed on streamFileId so it
    // re-applies when switching episodes (moon's bug 2026-11-02: Next
    // episode opened in portrait). On dispose, restore portrait ONLY if not
    // switching episodes — otherwise the old player's dispose fires after the
    // new player's landscape, leaving it stuck in portrait (moon's bug 2026-11-04).
    DisposableEffect(streamFileId) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        insets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            if (!isSwitching) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
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
    // tdfile:// streams come straight from Telegram through our own
    // DataSource (no addon server needed); http/file URIs still work through
    // Media3's defaults. See ui/player/TdDataSource.kt.
    //
    // Player buffer (Settings → "Player buffer (seconds)", 2026-11-09).
    // Media3's defaults when nothing is configured: 50 s kept ahead, 2.5 s
    // needed to start, 5 s needed to resume after a stall, 64 MB allocator.
    // This setting moves the 50 s figure only — the start/resume thresholds
    // stay small so the first frame still appears quickly. Read once here:
    // each AppScreen.Player builds a fresh player, so a change applies to
    // the next video opened.
    val playerBufferS = remember {
        com.moon.moonstream.ui.state.AppUiState.playerBufferS.coerceIn(10, 300)
    }
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                playerBufferS * 1000,
                playerBufferS * 1000,
                1_500,
                3_000,
            )
            .build()
        ExoPlayer.Builder(context, buildRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(MsDataSourceFactory(context)))
            .setLoadControl(loadControl)
            .build().apply {
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
    var subPosition by remember {
        mutableFloatStateOf(SubDelayPrefs.getPosition(context, mediaId))
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
        // Vertical position: bottom-padding fraction (moon's pick 2026-11-02 —
        // keeps subtitles visible when the video is zoomed).
        sv.setBottomPaddingFraction(subPosition)
    }

    // Re-apply when the view arrives or any appearance pref changes.
    LaunchedEffect(
        playerViewRef, subTextSize, subStrokeOn, subTextColor, subStrokeColor,
        subPosition
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
    // Float accumulator for smooth volume gestures (see fix below).
    var volumeFloat by remember {
        mutableStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) /
                maxVolume.coerceAtLeast(1).toFloat()
        )
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

    // In-app playback goes straight to Telegram (low-storage byte window)
    // when we know the file's size; otherwise fall back to the addon server
    // URL exactly as before.
    val filesLoaded = files.isNotEmpty()
    // The file actually playing (for the trimmer + skip fetch).
    val currentFile = remember(orderedFiles, streamFileId) {
        orderedFiles.firstOrNull { it.id == streamFileId }
    }
    val playUri = remember(orderedFiles, streamFileId, videoUri) {
        val f = orderedFiles.firstOrNull { it.id == streamFileId }
        if (f != null && f.fileSize > 0L) {
            TdStreamUri.build(f.channelId, f.messageId, f.fileSize, f.fileName)
        } else {
            videoUri
        }
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

    // Language preference (Settings → Languages). First choice wins; the
    // second is used only when the first isn't on this title at all.
    val subFirst = remember { LanguagePrefs.subtitleFirst(context) }
    val subSecond = remember { LanguagePrefs.subtitleSecond(context) }
    val audioFirst = remember { LanguagePrefs.audioFirst(context) }
    val audioSecond = remember { LanguagePrefs.audioSecond(context) }
    // Per-entry remembered language (moon's pick 2026-11-10): the track
    // language picked once for this entry wins over the global Settings
    // choice on every later episode.
    val entrySubIso = remember(mediaId) { TrackLangPrefs.getSubLang(context, mediaId) }
    val entryAudioIso = remember(mediaId) { TrackLangPrefs.getAudioLang(context, mediaId) }
    val chosenSubLang = remember(allSubs, subFirst, subSecond) {
        LanguagePrefs.chooseSubtitle(allSubs.map { it.language }, subFirst, subSecond)
    }

    // Subtitle tracks — mime type comes from the real file name, language from
    // the indexer. Straight from Telegram when we know the size (so subtitles
    // work with the addon server off, delay applied on-device); the server
    // route stays as a fallback for odd rows. Preferred language sorts first,
    // which is what Media3 picks.
    val subPairs = remember(allSubs, delayMs, chosenSubLang, entrySubIso) {
        allSubs
            .sortedByDescending { sub ->
                // Remembered per-entry language sorts first; otherwise the
                // global Settings choice, as before.
                val subIso = LanguagePrefs.iso(sub.language)
                when {
                    entrySubIso != null && subIso != null && subIso == entrySubIso -> true
                    chosenSubLang?.let { LanguagePrefs.matches(sub.language, it) } ?: false -> true
                    else -> false
                }
            }
            .map { sub ->
                val uri = if (sub.fileSize in 1..TdSubUri.MAX_BYTES) {
                    TdSubUri.build(
                        sub.channelId, sub.messageId, sub.fileSize, sub.fileName, delayMs,
                    )
                } else {
                    Uri.parse(PlayerUrls.sub(sub.id, delayMs))
                }
                SubTrack(uri, sub.fileName, sub.language)
            }
    }

    // Subtitle file loaded from the phone (moon's pick 2026-11-10): a
    // .srt/.ass/.vtt picked with the system file picker. It joins the
    // media item's subtitle list (first, so Media3 picks it when its
    // language matches the preference). The subtitle delay does NOT shift
    // it — the delay only works on the Telegram/server subtitle sources.
    var externalSub by remember { mutableStateOf<SubTrack?>(null) }
    val subPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = contentResolverDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("srt", "ass", "ssa", "vtt", "sub")) {
            MsToast.show("Not a subtitle file (.srt, .ass, .vtt)")
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Read permission for this session is enough; persist is best-effort.
            AppLog.w("PLAYER", "External sub persist permission failed: ${e.message}")
        }
        // Language: the preferred subtitle language, so Media3 picks this
        // file automatically. "und" when nothing is preferred.
        val lang = entrySubIso ?: chosenSubLang?.let { LanguagePrefs.iso(it) } ?: "und"
        externalSub = SubTrack(uri, name, lang)
        AppLog.i("PLAYER", "External subtitle loaded: $name ($lang)")
        MsToast.show(name)
    }

    // The media item's subtitle list: a phone-loaded file first (so
    // Media3 picks it when its language matches), then the channel subs.
    val mediaSubs = remember(subPairs, externalSub) {
        listOfNotNull(externalSub) + subPairs
    }

    fun episodeLabel(f: StreamFileEntity): String =
        if (f.season != null && f.episode != null) {
            "S${f.season} E${f.episode}" +
                (if (f.episodeEnd != null) "-${f.episodeEnd}" else "")
        } else {
            com.moon.moonstream.index.parse.FilenameParser.cleanDisplayName(
                f.fileName.substringBeforeLast('.')
            ).take(40)
        }

    /** Switch playback to another file of the same entry. */
    fun switchEpisode(f: StreamFileEntity) {
        if (f.id == streamFileId) return
        val url = PlayerUrls.stream(mediaId, f.id)
        AppLog.i("PLAYER", "Switching to ${episodeLabel(f)}")
        isSwitching = true
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
    // Essential three only: Fit (letterbox), Fill/Stretch (fill, may distort),
    // Zoom (crop to fill, no distortion).
    val resizeModes = remember {
        // Triple(resizeMode, fixedAspectRatio or 0f for video's natural, label)
        listOf(
            Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f, "Fit"),
            Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f, "Stretch"),
            Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f, "Zoom"),
        )
    }
    var resizeIndex by remember { mutableIntStateOf(0) }
    fun findAspectFrame(): AspectRatioFrameLayout? {
        val pv = playerViewRef ?: return null
        for (i in 0 until pv.childCount) {
            val child = pv.getChildAt(i)
            if (child is AspectRatioFrameLayout) return child
        }
        return null
    }
    fun cycleResize() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        val (mode, ratio, label) = resizeModes[resizeIndex]
        playerViewRef?.let { pv ->
            // Set the resize mode on the PlayerView (it forwards to its
            // internal AspectRatioFrameLayout). Only call setAspectRatio()
            // for fixed ratios; 0f means "use the video's natural ratio"
            // and calling it can interfere with the mode change.
            pv.resizeMode = mode
            if (ratio != 0f) {
                findAspectFrame()?.setAspectRatio(ratio)
            }
        }
        MsToast.show(label)
        AppLog.i("PLAYER", "Resize mode: $label")
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

    // Sheet + track state.
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var trackOptions by remember { mutableStateOf(listOf<TrackOpt>()) }
    var selectedOpt by remember { mutableStateOf<TrackOpt?>(null) }
    var subsOff by remember { mutableStateOf(false) }
    var nextUpDismissed by remember { mutableStateOf(false) }

    /** A raw track label is "clean" if it doesn't look like uploader junk. */
    fun isCleanLabel(label: String): Boolean {
        val lower = label.lowercase()
        return !lower.contains("@") && !lower.contains("~") &&
            !lower.contains("[") && !lower.contains("http") &&
            !lower.contains("www.") && label.length < 40
    }

    /** Human language name from an ISO-639 code ("tam" → "Tamil"). */
    fun langName(code: String?): String {
        return when (code?.lowercase()?.take(3)) {
            "tam" -> "Tamil"
            "tel" -> "Telugu"
            "hin" -> "Hindi"
            "mal" -> "Malayalam"
            "kan" -> "Kannada"
            "eng" -> "English"
            "jpn", "jap" -> "Japanese"
            "kor" -> "Korean"
            "spa" -> "Spanish"
            "fre", "fra" -> "French"
            "ger", "deu" -> "German"
            "ita" -> "Italian"
            "por" -> "Portuguese"
            "rus" -> "Russian"
            "ara" -> "Arabic"
            "und" -> "Unknown"
            else -> code?.uppercase()?.take(3) ?: "Unknown"
        }
    }

    /** Short codec name from a MIME type ("audio/eac3" → "EAC3"). */
    fun codecName(mime: String?): String {
        return when {
            mime == null -> ""
            mime.contains("eac3") -> "EAC3"
            mime.contains("ac3") -> "AC3"
            mime.contains("aac") -> "AAC"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("opus") -> "Opus"
            mime.contains("vorbis") -> "Vorbis"
            mime.contains("flac") -> "FLAC"
            mime.contains("dts") -> "DTS"
            mime.contains("truehd") -> "TrueHD"
            mime.contains("srt") || mime.contains("subrip") -> "SRT"
            mime.contains("vtt") || mime.contains("webvtt") -> "VTT"
            mime.contains("ass") -> "ASS"
            mime.contains("ssa") -> "SSA"
            mime.contains("pgs") -> "PGS"
            else -> mime.substringAfter("/").uppercase().take(8)
        }
    }

    /** "Tamil • EAC3 • 5.1" from a track Format. */
    fun buildTrackLabel(f: androidx.media3.common.Format, type: Int): String {
        val parts = mutableListOf<String>()
        val lang = langName(f.language)
        if (lang != "Unknown") parts.add(lang)
        val codec = codecName(f.sampleMimeType)
        if (codec.isNotEmpty()) parts.add(codec)
        if (type == C.TRACK_TYPE_AUDIO) {
            val ch = when (f.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> if (f.channelCount > 0) "${f.channelCount}ch" else ""
            }
            if (ch.isNotEmpty()) parts.add(ch)
        }
        return if (parts.isEmpty()) "Track" else parts.joinToString(" • ")
    }

    /** Snapshot the player's tracks and open the subs/audio sheet. */
    fun openTrackSheet(mode: PlayerSheet) {
        val type = if (mode == PlayerSheet.SUBS) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
        val snap = player.currentTracks
        val opts = mutableListOf<TrackOpt>()
        for (g in snap.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                // Clean label (moon's request 2026-11-02): the raw MKV track
                // title is usually uploader junk ("@MoviezTamizha ~ [DDP5.1]"),
                // so build "Language • Codec • Channels" from the format
                // fields instead. Only use the raw label if it looks clean
                // (no @, ~, [, or http).
                val rawLabel = f.label?.takeIf { it.isNotBlank() }
                val label = if (rawLabel != null && isCleanLabel(rawLabel)) {
                    rawLabel
                } else {
                    buildTrackLabel(f, type)
                }
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
        // Remember the picked language for this entry (moon's pick
        // 2026-11-10): the next episode opens with the same language.
        // "Off" clears the remembered subtitle language.
        val pickedLang = opt?.let {
            try { it.group.getFormat(it.index).language } catch (e: Exception) { null }
        }
        if (forSubs) TrackLangPrefs.setSubLang(context, mediaId, pickedLang)
        else TrackLangPrefs.setAudioLang(context, mediaId, pickedLang)
        val name = opt?.label ?: "Off"
        AppLog.i("PLAYER", "Track picked (${if (forSubs) "subs" else "audio"}): $name")
        MsToast.show(name)
        sheet = null
    }

    // Subtitle list (moon's pick 2026-11-10): all lines of the subtitle
    // that's playing, tap one to jump there. Null = not loaded yet.
    var subCues by remember { mutableStateOf<List<com.moon.moonstream.ui.player.SubCue>?>(null) }
    var subCuesLoading by remember { mutableStateOf(false) }
    fun openSubLines() {
        if (subCuesLoading) return
        // The active subtitle track: the one Media3 selected (its id is
        // "sub_<index>" into mediaSubs), else the first subtitle we have.
        val active = run {
            val snap = player.currentTracks
            val selGroup = snap.groups.find { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            val format = selGroup?.let { g ->
                val override = player.trackSelectionParameters.overrides[g.mediaTrackGroup]
                val idx = try { override?.trackIndices?.get(0) ?: 0 } catch (e: Exception) { 0 }
                try { g.getTrackFormat(idx) } catch (e: Exception) { null }
            }
            val subIdx = format?.id?.removePrefix("sub_")?.toIntOrNull()
            if (subIdx != null && subIdx in mediaSubs.indices) mediaSubs[subIdx]
            else mediaSubs.firstOrNull()
        }
        if (active == null) {
            MsToast.show("No subtitles on this video")
            return
        }
        subCues = null
        subCuesLoading = true
        sheet = PlayerSheet.SUBLINES
        ioScope.launch(Dispatchers.IO) {
            val bytes = readSubtitleBytes(context, active.uri)
            val cues = bytes?.let { SubtitleCues.parse(it, active.fileName) } ?: emptyList()
            withContext(Dispatchers.Main) {
                subCues = cues
                subCuesLoading = false
                if (cues.isEmpty()) MsToast.show("Couldn't read these subtitles")
            }
        }
    }

    var mediaReady by remember { mutableStateOf(false) }

    // Load / rebuild the media item. First run: resume check (prompt, or
    // straight to the saved spot for the header Play button). Later runs
    // (subs arrived, delay changed): rebuild keeping position + play state.
    LaunchedEffect(playUri, mediaSubs, filesLoaded, chosenSubLang) {
        // The Room row can land a frame after the screen opens. Wait for it,
        // otherwise playback would start on the HTTP fallback and swap
        // sources (and lose the resume choice) mid-load.
        if (streamFileId != 0L && !filesLoaded) return@LaunchedEffect
        val isFirst = !mediaReady
        // Resume fix (moon's bug 2026-11-09): load the saved spot BEFORE
        // preparing and pass it as the media item's start position.
        // ExoPlayer then seeks to it natively once the timeline is ready.
        // The old separate seekTo() after prepare() fired while the source
        // was still loading (slow first chunk, cue reads) and got lost, so
        // playback started at 0. A start position can't get lost that way.
        val saved = if (isFirst && streamFileId != 0L) {
            withContext(Dispatchers.IO) {
                try {
                    db.watchProgressDao().get(mediaId, streamFileId)?.positionMs ?: 0L
                } catch (e: Exception) { 0L }
            }
        } else 0L
        // Auto-resume (moon's pick 2026-11-06): any saved spot over 10s
        // jumps straight to it on open — no prompt box. To start over,
        // drag the seekbar back to the beginning.
        val pos = if (isFirst) {
            if (saved > 10_000L) saved else 0L
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        val keepPlaying = if (isFirst) false else player.isPlaying
        player.setMediaItem(buildMediaItem(playUri, mediaSubs), pos)
        // Language picks are applied per media item (a new item resets tracks).
        // Audio keeps a real first/second order; text uses the language we
        // found on this title, so "Mal, else Eng" doesn't need two passes.
        // A language remembered for this entry (moon's pick 2026-11-10)
        // leads the list, ahead of the global Settings choice.
        val audioLangs = listOfNotNull(
            entryAudioIso,
            LanguagePrefs.iso(audioFirst),
            LanguagePrefs.iso(audioSecond),
        ).distinct()
        val textLang = entrySubIso ?: chosenSubLang?.let { LanguagePrefs.iso(it) }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (audioLangs.isNotEmpty()) setPreferredAudioLanguages(*audioLangs.toTypedArray())
                if (textLang != null) setPreferredTextLanguage(textLang)
                // No matching subtitle language → stay off instead of guessing.
                setSelectUndeterminedTextLanguage(false)
                if (subsOff) setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                // Prefer phone-decodable audio (AAC/MP3/Opus/Vorbis) over
                // EAC3/AC3/DTS, which the Helio G96 can't decode in hardware.
                // If the only track is EAC3, it still gets picked (better than
                // silence) and the error screen explains why it fails.
                setPreferredAudioMimeTypes(
                    "audio/aac", "audio/mpeg", "audio/opus", "audio/vorbis",
                    "audio/eac3", "audio/ac3",
                )
            }
            .build()
        player.prepare()
        if (isFirst) {
            mediaReady = true
            if (saved > 10_000L) {
                AppLog.i("PLAYER", "Resume: starting at ${saved / 1000}s (saved spot)")
            }
            player.play()
        } else if (keepPlaying) {
            player.play()
        }
    }

    // Create the watch-progress row on playback start (moon's bug 2026-11-04:
    // updatePosition is UPDATE-only, so without this the row never exists and
    // Continue Watching stays empty).
    LaunchedEffect(streamFileId) {
        if (streamFileId == 0L || mediaId == 0L) {
            com.moon.moonstream.util.AppLog.i("PLAYER", "ContinueWatching: SKIP upsert (mediaId=$mediaId, streamFileId=$streamFileId)")
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val media = db.mediaDao().getById(mediaId)
                val file = orderedFiles.firstOrNull { it.id == streamFileId }
                val season = currentGroup?.season ?: file?.season
                val episode = currentGroup?.episode ?: file?.episode
                val epLabel = if (season != null && episode != null) {
                    "S%02dE%02d".format(season, episode)
                } else null
                // Only create if it doesn't exist yet (don't overwrite position).
                if (db.watchProgressDao().get(mediaId, streamFileId) == null) {
                    db.watchProgressDao().upsert(
                        com.moon.moonstream.index.db.WatchProgressEntity(
                            mediaId = mediaId,
                            streamFileId = streamFileId,
                            title = media?.title ?: mediaTitle,
                            episodeLabel = epLabel,
                            posterUrl = media?.posterUrl,
                            playedAt = System.currentTimeMillis(),
                            positionMs = 0L,
                            durationMs = 0L,
                        )
                    )
                    com.moon.moonstream.util.AppLog.i("PLAYER", "ContinueWatching: INSERTED row (mediaId=$mediaId, streamFileId=$streamFileId)")
                } else {
                    com.moon.moonstream.util.AppLog.i("PLAYER", "ContinueWatching: row already exists (mediaId=$mediaId, streamFileId=$streamFileId)")
                }
            } catch (e: Exception) {
                com.moon.moonstream.util.AppLog.e("PLAYER", "ContinueWatching: upsert FAILED: ${e.message}")
            }
        }
    }

    // Save position every 5s while playing (for Resume + "watched X ago").
    LaunchedEffect(streamFileId) {
        if (streamFileId == 0L) return@LaunchedEffect
        while (true) {
            delay(5000)
            val pos = player.currentPosition
            // FIX 2026-11-05: capture duration on main thread (ExoPlayer is main-thread only)
            val dur5 = player.duration.coerceAtLeast(0L)
            if (pos > 10_000L) {
                withContext(Dispatchers.IO) {
                    try {
                        db.watchProgressDao().updatePosition(
                            streamFileId, pos, dur5, System.currentTimeMillis(),
                        )
                    } catch (e: Exception) { /* never break playback */ }
                }
            }
        }
    }

    // Remember whether the video was playing when the app went to the
    // background, so a MANUAL pause survives re-entry (moon's 2026-11-07:
    // ON_RESUME used to play() unconditionally, restarting a video the
    // user had paused on purpose). playWhenReady is the user's intent:
    // false when they paused, true when it was playing (even while
    // buffering).
    var wasPlayingBeforePause by remember { mutableStateOf(false) }

    // Release on lifecycle destroy / dispose.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforePause = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforePause) player.play()
                }
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
                val pct = if (dur > 0) (pos * 100 / dur).toInt() else 0
                ioScope.launch(Dispatchers.IO) {
                    AppLog.i(
                        "PLAYER",
                        "closed at ${pos / 1000}s of ${dur / 1000}s ($pct%) — " +
                            if (finished) "watched to the end" else "resume position saved",
                    )
                    try {
                        // FIX 2026-11-05: use captured 'dur', NOT player.duration
                        // (ExoPlayer must be accessed on main thread only)
                        val rows = db.watchProgressDao().updatePosition(
                            streamFileId, finalPos, dur, System.currentTimeMillis(),
                        )
                        AppLog.i("PLAYER", "updatePosition: streamFileId=$streamFileId finalPos=$finalPos rowsAffected=$rows")
                    } catch (e: Exception) {
                        AppLog.e("PLAYER", "updatePosition FAILED: ${e.message}")
                    }
                    // Auto-delete the downloaded bytes on every close
                    // (moon's call 2026-11-02: no 90% rule — always clear).
                    try {
                        val f = files.find { it.id == streamFileId }
                        if (f != null) {
                            val deleted = BackendProvider.backend
                                .deleteCachedFile(f.channelId, f.messageId)
                            AppLog.i(
                                "PLAYER",
                                "closed — Telegram copy of '${f.fileName}' " +
                                    if (deleted) "deleted" else "NOT deleted (backend said no)",
                            )
                        }
                    } catch (e: Exception) { /* never break close */ }
                }
            }
            player.release()
        }
    }

    // Double-tap seek feedback (moon's pick 2026-11-02): shows +10s/-10s
    // with animation; consecutive double-taps accumulate (+20s, +30s...).
    var seekFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var lastSeekTapTime by remember { mutableStateOf(0L) }
    var seekTapCount by remember { mutableStateOf(0) }
    // Horizontal swipe-to-seek (moon's pick 2026-11-10): drag across the
    // video to move, target time shown live, seek lands on release.
    // Pair = target position ms, delta ms from where the drag started.
    var swipeSeekPreview by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // Video trimmer (moon's pick 2026-11-10): mark a start and an end
    // while watching, save just that range as a new clip. The cut runs
    // in the background (see VideoTrimmer) and lands in Downloads.
    var showTrimPanel by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableStateOf<Long?>(null) }
    var trimEndMs by remember { mutableStateOf<Long?>(null) }
    var trimSaving by remember { mutableStateOf(false) }
    var trimProgress by remember { mutableFloatStateOf(0f) }
    // Loop preview (moon's pick 2026-11-10): while on, the marked range
    // plays on repeat so the cut can be checked before saving.
    var trimLoop by remember { mutableStateOf(false) }
    // Nudge step (moon's pick 2026-11-11): false = 1 second per tap,
    // true = 1 frame (~33ms) per tap. Toggled from the panel header.
    var trimStepFrame by remember { mutableStateOf(false) }

    /** Nudge one trim mark by the current step (moon's pick 2026-11-11).
     * Clamped so start stays before end and both stay inside the video.
     * Seeks the playhead to the new mark so the frame is visible right away.
     * Declared AFTER the trim state above — a local fun can only see
     * state declared before it in this scope. */
    fun nudgeTrimMark(isStart: Boolean, dir: Int) {
        val stepMs = if (trimStepFrame) 33L else 1000L
        val delta = stepMs * dir
        // player.duration (not the `duration` state — that is declared
        // later in this scope, so it is not visible here).
        val dur = player.duration.coerceAtLeast(0L)
        if (isStart) {
            val cur = trimStartMs ?: return
            val end = trimEndMs ?: dur
            val newStart = (cur + delta).coerceIn(0L, (end - 1L).coerceAtLeast(0L))
            trimStartMs = newStart
            player.seekTo(newStart)
        } else {
            val cur = trimEndMs ?: return
            val start = trimStartMs ?: 0L
            val newEnd = (cur + delta).coerceIn(start + 1L, dur)
            trimEndMs = newEnd
            player.seekTo(newEnd)
        }
    }

    /** Apply a dragged range from the trim drawer slider (moon's pick
     * 2026-11-11 — side drawer). Dragging a handle sets that mark (even
     * when it was unset). Keeps at least 1ms between the marks and both
     * inside the video; ignores a change that would break that at an edge.
     * Uses player.duration (the `duration` state is declared later). */
    fun applyTrimRange(range: ClosedFloatingPointRange<Float>) {
        val dur = player.duration.coerceAtLeast(0L)
        if (dur <= 0L) return
        val oldS = trimStartMs ?: 0L
        val oldE = trimEndMs ?: dur
        var s = range.start.roundToLong().coerceIn(0L, dur)
        var e = range.endInclusive.roundToLong().coerceIn(0L, dur)
        if (e <= s) {
            if (s != oldS) e = (s + 1L).coerceAtMost(dur)
            else s = (e - 1L).coerceAtLeast(0L)
            if (e <= s) return
        }
        trimStartMs = s
        trimEndMs = e
    }

    /** After a slider drag ends, jump the video to the clip start so the
     * frame there is visible right away. */
    fun finishTrimRange() {
        trimStartMs?.let { player.seekTo(it) }
    }

    /** Save the marked trim range as a clip in Downloads (moon's pick 2026-11-10).
     * Declared AFTER the trim state above — a local fun can only see
     * state declared before it in this scope. */
    fun saveTrimClip() {
        val start = trimStartMs
        val end = trimEndMs
        val file = currentFile
        if (trimSaving) return
        if (start == null || end == null) {
            MsToast.show("Set a start and an end first")
            return
        }
        if (end <= start) {
            MsToast.show("Set an end after the start")
            return
        }
        if (file == null || file.fileSize <= 0L) {
            MsToast.show("Can't trim this stream")
            return
        }
        trimSaving = true
        trimProgress = 0f
        MsToast.show("Saving clip…")
        ioScope.launch(Dispatchers.IO) {
            val uri = VideoTrimmer.trimToDownloads(
                context = context,
                backend = BackendProvider.backend,
                channelId = file.channelId,
                messageId = file.messageId,
                fileSize = file.fileSize,
                fileName = file.fileName,
                startMs = start,
                endMs = end,
                onProgress = { p -> trimProgress = p },
            )
            withContext(Dispatchers.Main) {
                trimSaving = false
                trimProgress = 0f
                if (uri != null) {
                    MsToast.show("Clip saved to Downloads")
                    AppLog.i("PLAYER", "Trim saved: $uri")
                    showTrimPanel = false
                    trimLoop = false
                    trimStartMs = null
                    trimEndMs = null
                } else {
                    MsToast.show("Couldn't save the clip")
                }
            }
        }
    }
    // Auto-hide the feedback after 1s.
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1000)
            seekFeedback = null
            seekTapCount = 0
        }
    }

    // Controls visibility state.
    var controlsVisible by remember { mutableStateOf(true) }
    // Lock state (mpvex-style, moon's pick 2026-11-02): when locked, all
    // controls hide and only the slide-to-unlock pill appears on tap.
    var controlsLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    // Set when playback reaches the end (auto-play next reads this).
    var isEnded by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var bufferFrac by remember { mutableFloatStateOf(0f) }
    // Playback state UI (moon's bug 2026-11-02: black screen, no feedback).
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Log what we're about to play (source + path, no query).
    LaunchedEffect(playUri) {
        val portPart = if (playUri.port != -1) ":${playUri.port}" else ""
        val where = if (playUri.scheme == TdStreamUri.SCHEME) {
            "Telegram (direct, low-storage window)"
        } else {
            "${playUri.host}$portPart${playUri.path}"
        }
        AppLog.i("PLAYER", "Opening \"$title\" — $where")
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
                // Auto-play next (moon's 2026-11-07): track ENDED so the
                // auto-play effect below can start the next episode.
                isEnded = (state == Player.STATE_ENDED)
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Save the spot the moment the user seeks (moon's
                // 2026-11-07: the 5s loop alone could lose the spot if
                // the player closed right after a seek). Covers every
                // seek path at once — seekbar, ±10s buttons, double-tap.
                // Only real seeks, and only past 10s (same rule as the
                // 5s loop); the close handler still does the final save.
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    if (streamFileId != 0L && pos > 10_000L) {
                        ioScope.launch(Dispatchers.IO) {
                            try {
                                db.watchProgressDao().updatePosition(
                                    streamFileId, pos, dur, System.currentTimeMillis(),
                                )
                            } catch (e: Exception) { /* never break playback */ }
                        }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val raw = "${error.errorCodeName}: ${error.message}"
                AppLog.e("PLAYER", "Playback failed — $raw")
                // Friendly message for unsupported audio codecs (EAC3/Dolby).
                // The phone can't decode these in hardware; suggest switching
                // audio tracks if the file has more than one.
                playerError = if (raw.contains("audio/eac3", ignoreCase = true) ||
                    raw.contains("audio/ac3", ignoreCase = true)) {
                    "This audio format (Dolby EAC3) isn't supported by your phone. " +
                    "Try a different audio track from the Audio button — look for AAC or MP3."
                } else {
                    raw
                }
                isBuffering = false
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
                // Buffer band (moon's 2026-11-06): prefer the bytes actually
                // downloaded ahead (mpv-style) over ExoPlayer's ~50s sliver;
                // fall back to ExoPlayer for http/server streams.
                val td = TdDataSource.current
                val winFrac = td?.downloadedAheadFraction(currentPosition, duration)
                val exoFrac = if (duration > 0L) {
                    (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
                } else 0f
                val posFrac = if (duration > 0L) {
                    (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                } else 0f
                bufferFrac = (winFrac ?: exoFrac).coerceAtLeast(posFrac).coerceIn(0f, 1f)
                if (duration <= 0L) duration = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // Auto-hide controls after 3s. Skipped while the trim panel is open
    // (moon's bug 2026-11-10: the trimmer vanished after a few seconds
    // like the normal UI — while trimming, the panel must stay put).
    LaunchedEffect(controlsVisible, isPlaying, showTrimPanel) {
        if (controlsVisible && isPlaying && !controlsLocked && !showTrimPanel) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Trim loop preview (moon's pick 2026-11-10): while on, the marked
    // range plays on repeat. Jumps back to the start when the playhead
    // passes the end, checked every 100ms so the loop is tight. The
    // effect restarts (and re-seeks) if the marks move while looping.
    LaunchedEffect(trimLoop, trimStartMs, trimEndMs) {
        if (!trimLoop) return@LaunchedEffect
        val start = trimStartMs ?: return@LaunchedEffect
        val end = trimEndMs ?: return@LaunchedEffect
        if (end <= start) return@LaunchedEffect
        player.seekTo(start)
        player.playWhenReady = true
        while (true) {
            delay(100)
            if (player.currentPosition >= end) {
                player.seekTo(start)
            }
        }
    }

    // Unlock pill auto-hides after 2s (mpvex-style).
    LaunchedEffect(showUnlockPill) {
        if (showUnlockPill) {
            delay(2000)
            showUnlockPill = false
        }
    }

    // Tap the right timer to toggle total ↔ remaining (mpvex pattern).
    var showRemaining by remember { mutableStateOf(false) }

    // Saved resume position for the Resume button (moon's pick 2026-11-02).
    var savedResumePos by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaId, streamFileId) {
        savedResumePos = withContext(Dispatchers.IO) {
            try {
                if (streamFileId != 0L) {
                    db.watchProgressDao().get(mediaId, streamFileId)?.positionMs ?: 0L
                } else 0L
            } catch (e: Exception) { 0L }
        }
    }

    // Next-up card: last 30s, only when a next episode exists.
    val nextUpFile = nextGroup?.files?.firstOrNull()
    val showNextUp = nextUpFile != null && !nextUpDismissed &&
        duration > 60_000L && duration - currentPosition in 1..30_000L
    val nextUpSecs = ((duration - currentPosition) / 1000).toInt().coerceAtLeast(0)

    // Auto-play next episode (moon's 2026-11-07): when the video ends,
    // start the next episode by itself — unless the user hid the NextUp
    // card. The card already counts down ("Starting in Xs"); reaching 0
    // is the video ending, so this fires right then. Movies and last
    // episodes have no next file, so nothing happens for them.
    LaunchedEffect(isEnded) {
        if (!isEnded) return@LaunchedEffect
        val next = nextUpFile
        if (next != null && !nextUpDismissed) {
            AppLog.i("PLAYER", "Auto-playing next episode: ${next.fileName}")
            switchEpisode(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(controlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (controlsLocked) {
                            // Locked: tap reveals the slide-to-unlock pill.
                            showUnlockPill = true
                        } else if (!showTrimPanel) {
                            // While trimming, a tap must not hide the panel
                            // (moon's bug 2026-11-10).
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        // Double-tap left/right: ∓10s with animated feedback.
                        // Consecutive double-taps accumulate (+20s, +30s...).
                        // Disabled while locked or trimming.
                        if (!controlsLocked && !showTrimPanel) {
                            val forward = offset.x > size.width / 2
                            val now = System.currentTimeMillis()
                            seekTapCount =
                                if (now - lastSeekTapTime < 900) seekTapCount + 1 else 1
                            lastSeekTapTime = now
                            val secs = seekTapCount * 10
                            val newPos = (player.currentPosition +
                                if (forward) secs * 1000L else -secs * 1000L)
                                .coerceIn(0L, player.duration.coerceAtLeast(0L))
                            player.seekTo(newPos)
                            seekFeedback = forward to secs
                        }
                    },
                )
            }
            .pointerInput(sheet, controlsLocked, showTrimPanel) {
                // Vertical swipes: left half = brightness, right half = volume
                // (video-player style). Skipped while a sheet is open, locked,
                // or trimming (moon's bug 2026-11-10).
                detectVerticalDragGestures(
                    onDragEnd = { /* HUD hides on its own timer */ },
                    onDragCancel = { },
                ) { change, dragAmount ->
                    if (sheet == null && !controlsLocked && !showTrimPanel) {
                        change.consume()
                        // Up = increase; a full-height swipe ≈ the full range.
                        val delta = -dragAmount / 800f
                        if (change.position.x < size.width / 2f) {
                            setBrightness(brightness + delta)
                            gestureHud = GestureHud.Brightness(brightness)
                        } else {
                            // Volume fix (moon's report 2026-11-02): the old
                            // code did (delta * maxVolume).toInt(), so small
                            // swipes truncated to 0 and did nothing. Use a
                            // float accumulator for smooth steps.
                            volumeFloat = (volumeFloat + delta).coerceIn(0f, 1f)
                            val newVol = (volumeFloat * maxVolume).roundToInt()
                                .coerceIn(0, maxVolume)
                            val cur =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (newVol != cur) {
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC, newVol, 0
                                )
                            }
                            gestureHud = GestureHud.Volume(volumeFloat)
                        }
                    }
                }
            }
            .pointerInput(sheet, controlsLocked, showTrimPanel) {
                // Horizontal swipes: seek (moon's pick 2026-11-10). A full
                // screen-width swipe moves 90s — small drags nudge a few
                // seconds, big drags jump. The target time shows live in
                // the centre HUD; the seek itself lands once, on release,
                // so a network stream isn't re-buffered mid-drag.
                // Skipped while a sheet is open, locked, or trimming
                // (moon's bug 2026-11-10).
                var accPx = 0f
                var startMs = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        accPx = 0f
                        startMs = player.currentPosition
                        swipeSeekPreview = startMs to 0L
                    },
                    onDragEnd = {
                        val target = swipeSeekPreview?.first
                        swipeSeekPreview = null
                        if (target != null && sheet == null && !controlsLocked && !showTrimPanel) {
                            player.seekTo(target)
                        }
                    },
                    onDragCancel = { swipeSeekPreview = null },
                ) { change, dragAmount ->
                    if (sheet == null && !controlsLocked && !showTrimPanel) {
                        change.consume()
                        accPx += dragAmount
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val target = (startMs + (accPx / widthPx * 90_000f).toLong())
                            .coerceIn(0L, player.duration.coerceAtLeast(0L))
                        swipeSeekPreview = target to (target - startMs)
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
        if (controlsVisible && !controlsLocked) {
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
                            enabled = !showTrimPanel,
                        )
                    }
                    if (showPlaylistBtn) {
                        PlayerIconButton(
                            icon = Icons.Filled.List,
                            contentDescription = "Playlist",
                            onClick = { sheet = PlayerSheet.PLAYLIST },
                            enabled = !showTrimPanel,
                        )
                    }
                    PlayerIconButton(
                        icon = Icons.Filled.PhotoCamera,
                        contentDescription = "Screenshot",
                        onClick = { takeScreenshot() },
                        enabled = !showTrimPanel,
                    )
                    PlayerIconButton(
                        icon = Icons.Filled.ContentCut,
                        contentDescription = "Trim clip",
                        onClick = {
                            showTrimPanel = !showTrimPanel
                            controlsVisible = true
                        },
                        active = showTrimPanel,
                    )
                    PlayerIconButton(
                        icon = Icons.Filled.Lock,
                        contentDescription = "Lock controls",
                        onClick = {
                            controlsLocked = true
                            controlsVisible = false
                            showUnlockPill = true
                        },
                        enabled = !showTrimPanel,
                    )
                }
            }
        }

        // Center transport: -10s / play-pause / +10s grouped near center
        // (moon's request 2026-11-04: seek buttons flanking the play button,
        // not at the screen edges).
        // Hidden when buffering so it doesn't clash with the loader (moon's fix 2026-11-04).
        if (controlsVisible && !controlsLocked && !isBuffering) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(64.dp),
            ) {
                // Seek buttons with round overlay like other player icons
                // (moon's request 2026-11-04: too close to pause, add the circle).
                PlayerIconButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = "Back 10s",
                    onClick = {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                    },
                    size = 52.dp,
                    iconSize = 32.dp,
                )
                // Big play/pause: 76dp circle, no border (mpvex-style).
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
                // Seek buttons with round overlay like other player icons
                // (moon's request 2026-11-04: too close to pause, add the circle).
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
                    iconSize = 32.dp,
                )
            }
        }

        // Bottom cluster (mpvex-style): corner button clusters above the
        // seekbar, seekbar pinned to the bottom edge with monospace timers.
        if (controlsVisible && !controlsLocked) {
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
                // The trimmer now lives in a side drawer on the right
                // (moon's pick 2026-11-11, image 3) — see TrimDrawer below.
                // It no longer sits above the seekbar.
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
                            enabled = !showTrimPanel,
                        )
                        PlayerIconButton(
                            icon = Icons.Filled.Audiotrack,
                            contentDescription = "Audio",
                            onClick = { openTrackSheet(PlayerSheet.AUDIO) },
                            enabled = !showTrimPanel,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Next episode (moon's pick 2026-11-02).
                        val nextFile = nextGroup?.files?.firstOrNull()
                        if (nextFile != null) {
                            PlayerIconButton(
                                icon = Icons.Filled.SkipNext,
                                contentDescription = "Next episode",
                                onClick = { switchEpisode(nextFile) },
                                enabled = !showTrimPanel,
                            )
                        }
                        // Resume from saved position (moon's pick 2026-11-02):
                        // shown when there's a saved spot and we're far from it.
                        if (savedResumePos > 10_000L &&
                            kotlin.math.abs(currentPosition - savedResumePos) > 10_000L
                        ) {
                            PlayerIconButton(
                                icon = Icons.Filled.History,
                                contentDescription = "Resume from saved position",
                                onClick = { player.seekTo(savedResumePos) },
                                enabled = !showTrimPanel,
                            )
                        }
                        // Aspect button shows the current mode as its label
                        // (mpvex-style stateful button).
                        Surface(
                            onClick = { cycleResize() },
                            enabled = !showTrimPanel,
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
                                    tint = Color.White.copy(alpha = if (showTrimPanel) 0.35f else 1f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    resizeModes[resizeIndex].third,
                                    color = Color.White.copy(alpha = if (showTrimPanel) 0.35f else 1f),
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
                            // While scrubbing, show the exact target time
                            // (not the current position) so the seek lands
                            // precisely where the finger is pointing.
                            formatTime(scrubMs ?: currentPosition),
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
                            // Mpv-style seekbar (moon's 2026-11-06): thin at rest,
                            // thick while seeking; played / buffered / remaining
                            // as three distinct shades. The buffer is its own
                            // segment now — the old Slider's track is what kept
                            // hiding it.
                            MoonSeekBar(
                                positionMs = currentPosition,
                                durationMs = duration,
                                bufferFrac = bufferFrac,
                                onSeek = { player.seekTo(it) },
                                onScrubChange = { scrubMs = it },
                                modifier = Modifier.fillMaxWidth(),
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs,
                            )
                        }
                        Text(
                            if (showRemaining) {
                                "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                            } else {
                                formatTime(duration)
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { showRemaining = !showRemaining },
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }

        // Trimmer side drawer (moon's pick 2026-11-11, image 3): a
        // right-side panel that replaces the old bottom trim panel.
        // Full height on the right; the video stays visible on the
        // left. The X in its header (or the scissors button) closes it.
        // While it is open the trim lockout (above) already disables the
        // gestures and the other buttons, so touches here stay here.
        if (showTrimPanel) {
            TrimDrawer(
                startMs = trimStartMs,
                endMs = trimEndMs,
                currentMs = currentPosition,
                durationMs = duration,
                saving = trimSaving,
                progress = trimProgress,
                looping = trimLoop,
                stepIsFrame = trimStepFrame,
                onToggleStep = { trimStepFrame = !trimStepFrame },
                onNudgeStart = { dir -> nudgeTrimMark(true, dir) },
                onNudgeEnd = { dir -> nudgeTrimMark(false, dir) },
                onSetStart = { trimStartMs = currentPosition },
                onSetEnd = { trimEndMs = currentPosition },
                onJumpToStart = { trimStartMs?.let { player.seekTo(it) } },
                onJumpToEnd = { trimEndMs?.let { player.seekTo(it) } },
                onRangeChange = { range -> applyTrimRange(range) },
                onRangeChangeFinished = { finishTrimRange() },
                onClear = { trimStartMs = null; trimEndMs = null; trimLoop = false },
                onClose = { showTrimPanel = false; trimLoop = false },
                onToggleLoop = { trimLoop = !trimLoop },
                onSave = { saveTrimClip() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxHeight()
                    .width(300.dp),
            )
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

        // Slide-to-unlock pill (mpvex-style): shown on tap while locked,
        // auto-hides after 2s via the LaunchedEffect above.
        if (controlsLocked && showUnlockPill) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                UnlockSlider(
                    onUnlock = {
                        controlsLocked = false
                        showUnlockPill = false
                        controlsVisible = true
                    },
                    modifier = Modifier.padding(bottom = 64.dp),
                )
            }
        }

        // Double-tap seek feedback: animated +10s/-10s pill on the tapped side.
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(animationSpec = tween(150)) +
                scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            seekFeedback?.let { (forward, secs) ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment =
                        if (forward) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    // Text only, no background or outline (moon's request 2026-11-04).
                    Text(
                        "${if (forward) "+" else "-"}${secs}s",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                }
            }
        }

        // Swipe-to-seek preview (moon's pick 2026-11-10): centre pill with
        // the target time + how far it moves, while the finger drags.
        // Text only, same as the double-tap feedback (moon's 2026-11-04).
        swipeSeekPreview?.let { (targetMs, deltaMs) ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val sign = if (deltaMs >= 0) "+" else "−"
                val deltaSecs = kotlin.math.abs(deltaMs) / 1000
                Text(
                    "${formatTime(targetMs)}  ($sign${deltaSecs}s)",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        // Next-up card (TMPlayer pattern): last 30s of an episode.
        // Hidden while controls are locked.
        if (showNextUp && nextUpFile != null && nextGroup != null && !controlsLocked) {
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

        // Skip Intro button (moon's request 2026-11-02): Netflix-style pill
        // at bottom-right. Anime → AniSkip, then Anime Skip (both need the
        // AniList id; Anime Skip also needs its client id in Settings →
        // Metadata). Series/movies → IntroDB, then SkipDB (both need the
        // IMDb id; SkipDB needs no key). No manual fallback — no API data
        // = no button.
        var skipTimes by remember { mutableStateOf<AniSkip.SkipTimes?>(null) }
        var introSegs by remember { mutableStateOf<IntroDb.Segments?>(null) }
        var skipSource by remember { mutableStateOf<String?>(null) }
        var skipFetched by remember { mutableStateOf(false) }

        // Fetch skip data once per episode.
        // Race fix (moon's bug 2026-11-09, Alice in Borderland S1E1): the file
        // list loads from the DB a moment AFTER the player opens (it starts
        // empty), so the first run saw season/episode null and skipped the
        // fetch — and skipFetched then blocked the retry once the list
        // arrived. So wait for the list: return WITHOUT marking fetched, and
        // the effect re-runs when filesLoaded flips true.
        LaunchedEffect(mediaId, currentGroup?.episode, filesLoaded) {
            if (skipFetched) return@LaunchedEffect
            if (!filesLoaded) return@LaunchedEffect
            skipFetched = true
            // Season/episode fallback chain (moon's fix 2026-11-04): currentGroup
            // can be null (e.g. movies have no season/episode), so fall back to
            // the stream file's own values from the DB.
            val season = currentGroup?.season ?: currentFile?.season
            val ep = currentGroup?.episode ?: currentFile?.episode
            val media = try {
                withContext(Dispatchers.IO) { db.mediaDao().getById(mediaId) }
            } catch (e: Exception) { null }
            val isAnime = media?.type == "anime"
            val isMovie = media?.type == "movie"
            // Anime → AniSkip via AniList ID (mapped from Kitsu at index),
            // then Anime Skip when AniSkip has no opening. A provider wins
            // when it has ANY skippable segment (intro, ending, recap,
            // preview) — not just an intro (moon's 2026-11-10: an episode
            // with only an ending must not be discarded).
            if (isAnime && ep != null && !media?.anilistId.isNullOrBlank()) {
                val durSecs = duration.coerceAtLeast(1L) / 1000
                val aniskip = AniSkip.getSkipTimes(media!!.anilistId!!, ep, durSecs)
                if (aniskip != null && (aniskip.opEnd != null || aniskip.edEnd != null || aniskip.recapEnd != null)) {
                    skipTimes = aniskip
                    skipSource = "AniSkip"
                } else {
                    val clientId = ScanPrefs.getAnimeSkipClientId(context)
                    val animeskip = AnimeSkip.getSkipTimes(media.anilistId!!, season, ep, clientId)
                    if (animeskip != null && (animeskip.opEnd != null || animeskip.edEnd != null || animeskip.recapEnd != null || animeskip.previewEnd != null)) {
                        skipTimes = AniSkip.SkipTimes(animeskip.opStart, animeskip.opEnd, animeskip.edStart, animeskip.edEnd, animeskip.recapStart, animeskip.recapEnd, animeskip.previewStart, animeskip.previewEnd)
                        skipSource = "AnimeSkip"
                    }
                }
                return@LaunchedEffect
            }
            // Series/movies → IntroDB via IMDb ID, then SkipDB when IntroDB
            // has no segment at all. Same "any segment wins" rule: an
            // IntroDB answer with only an outro must not fall through and
            // lose it.
            val imdb = media?.imdbId
            if (!imdb.isNullOrBlank()) {
                if (!isMovie && (season == null || ep == null)) {
                    // Can't query IntroDB for TV without season+episode —
                    // it would 400. Log why instead of a silent failure.
                    AppLog.i("PLAYER", "Skip: no season/episode for series, IntroDB skipped (S=$season E=$ep)")
                } else {
                    val introdb = IntroDb.getSegments(
                        imdbId = imdb,
                        season = if (isMovie) null else season,
                        episode = if (isMovie) null else ep,
                        isMovie = isMovie,
                    )
                    if (introdb != null && (introdb.introEndSec != null || introdb.outroEndSec != null)) {
                        introSegs = introdb
                        skipSource = "IntroDB"
                    } else {
                        val skipdb = SkipDb.getSegments(
                            imdbId = imdb,
                            season = if (isMovie) null else season,
                            episode = if (isMovie) null else ep,
                            isMovie = isMovie,
                        )
                        if (skipdb != null && (skipdb.introEndSec != null || skipdb.outroEndSec != null || skipdb.recapEndSec != null || skipdb.previewEndSec != null)) {
                            introSegs = IntroDb.Segments(skipdb.introStartSec, skipdb.introEndSec, skipdb.outroStartSec, skipdb.outroEndSec, skipdb.recapStartSec, skipdb.recapEndSec, skipdb.previewStartSec, skipdb.previewEndSec)
                            skipSource = "SkipDB"
                        }
                    }
                }
            } else {
                AppLog.i("PLAYER", "Skip: no anilistId/imdbId, no button")
            }
        }

        // One Skip button for EVERY skippable segment (moon's 2026-11-10):
        // intro, ending/outro, recap, preview — the label follows whichever
        // segment the playhead is inside. The data already came from the
        // APIs above; this just turns it into one list in play order.
        // A segment is kept only when it has a real end after its start.
        val skipSegments = remember(skipTimes, introSegs) {
            fun seg(startSec: Double?, endSec: Double?, label: String): SkipSegment? {
                val s = startSec?.let { (it * 1000).toLong() } ?: return null
                val e = endSec?.let { (it * 1000).toLong() } ?: return null
                if (e <= s) return null
                return SkipSegment(s, e, label)
            }
            buildList {
                skipTimes?.let { t ->
                    seg(t.opStart, t.opEnd, "Skip Intro")?.let { add(it) }
                    seg(t.edStart, t.edEnd, "Skip Ending")?.let { add(it) }
                    seg(t.recapStart, t.recapEnd, "Skip Recap")?.let { add(it) }
                    seg(t.previewStart, t.previewEnd, "Skip Preview")?.let { add(it) }
                }
                introSegs?.let { g ->
                    seg(g.introStartSec, g.introEndSec, "Skip Intro")?.let { add(it) }
                    seg(g.outroStartSec, g.outroEndSec, "Skip Outro")?.let { add(it) }
                    seg(g.recapStartSec, g.recapEndSec, "Skip Recap")?.let { add(it) }
                    seg(g.previewStartSec, g.previewEndSec, "Skip Preview")?.let { add(it) }
                }
            }.sortedBy { it.startMs }
        }
        // Show ONLY while the playhead is inside a segment. No data = no button.
        val currentSegment = skipSegments.firstOrNull { currentPosition in it.startMs until it.endMs }
        val showSkip = currentSegment != null && !controlsLocked
        if (showSkip) {
            // currentSegment is non-null here (showSkip requires it).
            val seg = currentSegment!!
            Button(
                onClick = {
                    player.seekTo(seg.endMs)
                    AppLog.i("PLAYER", "Skip ${seg.label}: ${currentPosition/1000}s → ${seg.endMs/1000}s ($skipSource)")
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MsColors.Accent,
                    contentColor = MsColors.OnAccent,
                ),
            ) {
                Text(seg.label, fontWeight = FontWeight.Bold)
            }
        }

        // Resume button (moon's request 2026-11-09): bottom-left, above the
        // seekbar, mirroring Skip Intro on the right. Shows when there's a
        // saved spot and we're still near the start — so even if auto-resume
        // didn't jump, one tap takes him to where he left off. Hides once
        // he's at/past the spot (or taps it).
        if (savedResumePos > 10_000L &&
            currentPosition < savedResumePos - 10_000L &&
            !controlsLocked
        ) {
            Button(
                onClick = {
                    player.seekTo(savedResumePos)
                    AppLog.i("PLAYER", "Resume button: ${currentPosition / 1000}s → ${savedResumePos / 1000}s")
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 100.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MsColors.Accent,
                    contentColor = MsColors.OnAccent,
                ),
            ) {
                Text("Resume ${formatTime(savedResumePos)}", fontWeight = FontWeight.Bold)
            }
        }

        // Bottom sheets: subs / audio / playlist / episodes.
        // Pull down on the sheet (from the top, list at top) to close it.
        if (sheet != null) {
            val mode = sheet!!
            // Drawers (moon's pick 2026-11-02): EPISODES, PLAYLIST, SUBS open
            // as a right-side drawer; AUDIO stays a bottom sheet. SUBS uses
            // low opacity so subtitles stay visible while adjusting.
            val isDrawer = mode != PlayerSheet.AUDIO
            var sheetOffsetY by remember(mode) { mutableFloatStateOf(0f) }
            val sheetScroll = rememberScrollState()
            // Season filter for the Episodes drawer.
            val drawerSeasons = remember(groups) {
                groups.mapNotNull { it.season }.distinct().sorted()
            }
            var drawerSeason by remember(mode, groups) {
                mutableStateOf(currentGroup?.season ?: drawerSeasons.firstOrNull())
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { sheet = null },
                contentAlignment = if (isDrawer) Alignment.CenterEnd else Alignment.BottomCenter,
            ) {
                // Slide-in animation for drawers.
                val drawerSlide by animateFloatAsState(
                    targetValue = if (sheet != null) 1f else 0f,
                    animationSpec = tween(280),
                    label = "drawerSlide",
                )
                Column(
                    modifier = Modifier
                        .then(
                            if (isDrawer) {
                                Modifier
                                    .fillMaxHeight()
                                    .width(360.dp)
                                    .offset { IntOffset(((1f - drawerSlide) * 360.dp.toPx()).roundToInt(), 0) }
                                    .background(
                                        // Low opacity for SUBS so the video/
                                        // subtitles show through while editing.
                                        if (mode == PlayerSheet.SUBS)
                                            Color.Black.copy(alpha = 0.72f)
                                        else Color(0xFF1C1C1E),
                                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                    )
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                                    .offset { IntOffset(0, sheetOffsetY.roundToInt()) }
                                    .background(
                                        Color(0xFF1C1C1E),
                                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                    )
                            }
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
                    // Drag handle: only for bottom sheets (AUDIO). Drawers
                    // dismiss via the scrim tap.
                    if (!isDrawer) {
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
                    } // end if (!isDrawer) — drag handle only for bottom sheets
                    Text(
                        when (mode) {
                            PlayerSheet.SUBS -> "Subtitles"
                            PlayerSheet.AUDIO -> "Audio"
                            PlayerSheet.PLAYLIST -> "Playlist"
                            PlayerSheet.EPISODES -> "Episodes"
                            PlayerSheet.SUBLINES -> "Subtitle lines"
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
                            // Load a subtitle file from the phone (moon's
                            // pick 2026-11-10). Subtitles only — the Audio
                            // sheet has no subtitle rows.
                            if (forSubs) {
                                TrackRow(
                                    label = if (externalSub != null)
                                        "Phone file: ${externalSub!!.fileName}"
                                    else "Load from phone…",
                                    selected = false,
                                    onClick = {
                                        subPicker.launch(arrayOf("*/*"))
                                    },
                                )
                                // All subtitle lines (moon's pick 2026-11-10):
                                // jump to a moment by its dialogue.
                                TrackRow(
                                    label = "All lines…",
                                    selected = false,
                                    onClick = { openSubLines() },
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
                                    MsOutlinePill(
                                        text = "−250ms",
                                        onClick = { adjustDelay(-250) },
                                        fontSize = 13,
                                        // Compact padding: the default 14dp
                                        // vertical padding needs ~46dp, and
                                        // forcing height(36.dp) on it clipped
                                        // the text (moon's 2026-11-10).
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                    Text(
                                        formatDelay(delayMs),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(80.dp),
                                    )
                                    MsOutlinePill(
                                        text = "+250ms",
                                        onClick = { adjustDelay(250) },
                                        fontSize = 13,
                                        // Compact padding, same as above.
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                                // Appearance (moon's pick 2026-11-02).
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Appearance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MsColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                // Size as a slider (moon's 2026-11-10): the
                                // S/M/L buttons only gave three fixed sizes;
                                // a slider fine-tunes it. Range covers S
                                // (0.045) through L (0.08) with room either
                                // side. Same iOS slider as Position.
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
                                    MsSlider(
                                        // Coerced: a value saved before the
                                        // slider existed is always S/M/L
                                        // (0.045/0.06/0.08, all in range),
                                        // but never trust a stored float.
                                        value = subTextSize.coerceIn(0.03f, 0.10f),
                                        onValueChange = {
                                            subTextSize = it
                                            SubDelayPrefs.setTextSize(context, mediaId, it)
                                            applySubtitleStyle()
                                        },
                                        valueRange = 0.03f..0.10f,
                                        modifier = Modifier.weight(1.6f),
                                    )
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
                                // Position: vertical placement (moon's pick 2026-11-02 —
                                // raise subtitles when the video is zoomed).
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Position",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MsColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    MsSlider(
                                        value = subPosition,
                                        onValueChange = {
                                            subPosition = it
                                            SubDelayPrefs.setPosition(context, mediaId, it)
                                            applySubtitleStyle()
                                        },
                                        valueRange = 0f..0.4f,
                                        modifier = Modifier.weight(1.6f),
                                    )
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
                            // Season pills (moon's pick 2026-11-02).
                            if (drawerSeasons.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    drawerSeasons.forEach { s ->
                                        val selected = s == drawerSeason
                                        Surface(
                                            onClick = { drawerSeason = s },
                                            shape = RoundedCornerShape(50),
                                            color = if (selected) MsColors.Accent
                                            else Color.White.copy(alpha = 0.12f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) MsColors.Accent
                                                else Color.White.copy(alpha = 0.2f),
                                            ),
                                            modifier = Modifier.height(36.dp),
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            ) {
                                                Text(
                                                    "Season $s",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selected) Color.Black else Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val visibleGroups = if (drawerSeason != null)
                                groups.filter { it.season == drawerSeason }
                            else groups
                            visibleGroups.forEach { g ->
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
                        PlayerSheet.SUBLINES -> {
                            // All subtitle lines (moon's pick 2026-11-10).
                            // Tap a line to jump to that moment; the line
                            // playing now is highlighted in Accent.
                            if (subCuesLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IosSpinner()
                                }
                            } else if (subCues.isNullOrEmpty()) {
                                Text(
                                    "No lines found in these subtitles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MsColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            } else {
                                val cues = subCues!!
                                cues.forEach { cue ->
                                    val isCurrent =
                                        currentPosition in cue.startMs until cue.endMs
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sheet = null
                                                player.seekTo(cue.startMs)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            formatTime(cue.startMs),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                            color = if (isCurrent) MsColors.Accent
                                            else MsColors.TextSecondary,
                                            modifier = Modifier.width(64.dp),
                                        )
                                        Text(
                                            cue.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) MsColors.Accent
                                            else MsColors.TextPrimary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
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

        // Loading spinner while the stream buffers (moon's bug 2026-11-02:
        // Full-screen buffering loader (moon's request 2026-11-03):
        // shows during initial load AND when seeks take time. Uses a small
        // delay (400ms) so quick seeks don't flicker the loader.
        var showBufferLoader by remember { mutableStateOf(false) }
        LaunchedEffect(isBuffering) {
            if (isBuffering) {
                kotlinx.coroutines.delay(400)
                // Re-check: still buffering after the delay?
                if (isBuffering) showBufferLoader = true
            } else {
                showBufferLoader = false
            }
        }
        if (showBufferLoader && playerError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                // iOS spinner loader (moon's pick 2026-11-04 — replaces Expressive morphing).
                IosSpinner(
                    modifier = Modifier.size(48.dp),
                    color = Color.White,
                    size = 32.dp,
                )
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

/** One subtitle track: where the bytes come from, its name, its language. */
internal data class SubTrack(val uri: Uri, val fileName: String, val language: String)

/** Display name of a content:// Uri (system file picker), or null. */
private fun contentResolverDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }
}

/** Read a subtitle file's raw bytes through the player's data source
 * (tdsub://, http:// server, or content:// phone file), or null. */
private suspend fun readSubtitleBytes(context: android.content.Context, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            val ds = com.moon.moonstream.ui.player.MsDataSourceFactory(context).createDataSource()
            ds.open(androidx.media3.datasource.DataSpec(uri))
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = ds.read(buf, 0, buf.size)
                if (n == -1) break
                out.write(buf, 0, n)
            }
            try { ds.close() } catch (ignored: Exception) { /* ignore */ }
            out.toByteArray()
        } catch (e: Exception) {
            AppLog.w("PLAYER", "Subtitle list read failed: ${e.message}")
            null
        }
    }

/** Build the player's media item: video + subtitle tracks (mime from real file names). */
@OptIn(UnstableApi::class)
private fun buildMediaItem(video: Uri, subs: List<SubTrack>): MediaItem =
    MediaItem.Builder()
        .setUri(video)
        .setSubtitleConfigurations(
            subs.mapIndexed { index, sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.uri)
                    .setMimeType(
                        when {
                            sub.fileName.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
                            sub.fileName.endsWith(".ass", ignoreCase = true) ||
                                sub.fileName.endsWith(".ssa", ignoreCase = true) -> "text/x-ssa"
                            else -> "application/x-subrip"
                        },
                    )
                    // Real language so Media3 can pick by preference; "und"
                    // when the indexer had no hint (those stay manual).
                    .setLanguage(LanguagePrefs.iso(sub.language) ?: "und")
                    .setLabel(sub.fileName)
                    .setId("sub_$index")
                    .build()
            },
        )
        .build()

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
    modifier: Modifier = Modifier,
    /** Disabled (dimmed, taps ignored) — used while trimming, so only
     *  Back, Trim, the seekbar and transport stay live (moon's bug
     *  2026-11-10). */
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.size(size),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) MsColors.Accent
                    else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Mpv-style seekbar (moon's 2026-11-06): thin (4dp) at rest, thick (10dp)
 * while pressing/dragging to seek, with three distinct shades — played
 * (Accent), buffered ahead (white 0.62, from the bytes actually downloaded
 * ahead) and remaining (white 0.20). The buffer is drawn as its own segment,
 * never hidden under a Slider track (the old Slider's active track is why
 * the buffer kept looking invisible). Tap anywhere to seek; drag to scrub.
 *
 * Precise seeking (moon's 2026-11-07): the bar does NOT seek live while
 * the finger moves. Every live seek re-buffered the stream, so the player
 * could only land on a few coarse points (05:16 → 09:06 → 23:07) and
 * precise seeking was impossible. Instead, dragging only moves a preview:
 * the played fill follows the finger and onScrubChange reports the exact
 * target time, and the player seeks ONCE when the finger lifts.
 */
@Composable
private fun MoonSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferFrac: Float,
    onSeek: (Long) -> Unit,
    onScrubChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    /** Trimmer selection (moon's pick 2026-11-10): the chosen clip range,
     * drawn as an Accent band so the cut is visible on the bar. */
    trimStartMs: Long? = null,
    trimEndMs: Long? = null,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val barHeight by animateDpAsState(
        targetValue = if (isScrubbing) 10.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "seekBarHeight",
    )
    val posFrac = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    // While scrubbing, the played fill follows the finger's target, not
    // the actual playback position — so the bar shows exactly where the
    // seek will land before the finger lifts.
    val displayFrac = if (isScrubbing && scrubMs != null && durationMs > 0L) {
        (scrubMs!!.toFloat() / durationMs).coerceIn(0f, 1f)
    } else posFrac
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(durationMs) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        isScrubbing = true
                        down.consume()
                        val w = size.width.toFloat()
                        fun targetMs(x: Float): Long? {
                            if (w <= 0f || durationMs <= 0L) return null
                            return ((x / w).coerceIn(0f, 1f) * durationMs).toLong()
                        }
                        // Finger down: preview only, do NOT seek yet.
                        scrubMs = targetMs(down.position.x)
                        onScrubChange(scrubMs)
                        // Track the drag manually: awaitHorizontalDragOrCancellation
                        // takes no drag callback in this Compose version, so read
                        // pointer events until this finger lifts or cancels.
                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: continue
                            if (!change.pressed) {
                                // Finger lifted: seek ONCE to the previewed spot.
                                dragging = false
                                scrubMs?.let { onSeek(it) }
                            } else {
                                change.consume()
                                scrubMs = targetMs(change.position.x)
                                onScrubChange(scrubMs)
                            }
                        }
                        scrubMs = null
                        onScrubChange(null)
                        isScrubbing = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Base track (remaining).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.20f)),
        )
        // Buffer fill, from the start to the downloaded-ahead point.
        if (bufferFrac > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(bufferFrac.coerceIn(0f, 1f))
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.62f)),
            )
        }
        // Played fill (follows the scrub preview while dragging).
        if (displayFrac > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(displayFrac)
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MsColors.Accent),
            )
        }
        // Trimmer range band (moon's pick 2026-11-10): the marked clip,
        // drawn above the base track so it's visible even before any
        // playback. Only when both ends are set and in order.
        if (durationMs > 0L && trimStartMs != null && trimEndMs != null && trimEndMs > trimStartMs) {
            val startFrac = (trimStartMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFrac = (trimEndMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (endFrac > startFrac) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(endFrac - startFrac)
                        .offset { IntOffset((startFrac * constraints.maxWidth).roundToInt(), 0) }
                        .height(barHeight + 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MsColors.Accent.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50)),
                )
            }
        }
    }
}

/**
 * Trimmer panel (moon's pick 2026-11-10): mark a start and an end while
 * watching, save just that range as a clip. Sits above the seekbar so the
 * bar stays usable for finding the exact cut points.
 */
@Composable
/**
 * Trimmer side drawer (moon's pick 2026-11-11, image 3): a right-side
 * panel that replaces the old bottom TrimPanel. Same marks, same engine
 * (VideoTrimmer) — only the shape changed: Start/End steppers, a
 * dual-handle range slider, Set start/end, Preview, Reset, Save Clip.
 * Full height on the right; the video stays visible on the left.
 * Compact + scrollable so it fits a landscape phone's short height.
 * The -1s/+1s buttons from the image are NOT duplicated here — the
 * < > arrows on the Start/End boxes already nudge by the header step
 * (1s or 1 frame), which is the same job.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrimDrawer(
    startMs: Long?,
    endMs: Long?,
    currentMs: Long,
    durationMs: Long,
    saving: Boolean,
    progress: Float,
    looping: Boolean,
    stepIsFrame: Boolean,
    onToggleStep: () -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onJumpToStart: () -> Unit,
    onJumpToEnd: () -> Unit,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onRangeChangeFinished: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onToggleLoop: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = startMs != null && endMs != null && endMs > startMs
    val lengthMs = if (valid) endMs!! - startMs!! else 0L
    // Slider range: unset marks show as the full video; dragging a
    // handle sets that mark (see applyTrimRange).
    val durF = durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue: ClosedFloatingPointRange<Float> =
        (startMs?.toFloat() ?: 0f).coerceIn(0f, durF)..(endMs?.toFloat() ?: durF).coerceIn(0f, durF)
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Header: scissors + title + step toggle + close.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ContentCut,
                contentDescription = null,
                tint = MsColors.Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Trim Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            MsOutlinePill(
                text = if (stepIsFrame) "Step: 1f" else "Step: 1s",
                onClick = onToggleStep,
                fontSize = 12,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            BareIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close trim",
                onClick = onClose,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Start / End steppers: < time > each; the < > nudge by the
        // header step (1s or 1 frame) and jump the video there.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrimMarkBox(
                label = "Start",
                timeMs = startMs,
                onNudge = onNudgeStart,
                onJump = onJumpToStart,
                modifier = Modifier.weight(1f),
            )
            TrimMarkBox(
                label = "End",
                timeMs = endMs,
                onNudge = onNudgeEnd,
                onJump = onJumpToEnd,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Set start / Set end: capture the playhead into each mark.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MsOutlinePill(
                text = "Set start",
                onClick = onSetStart,
                fontSize = 13,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            )
            MsOutlinePill(
                text = "Set end",
                onClick = onSetEnd,
                fontSize = 13,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Dual-handle range slider: drag either handle to move that
        // mark (it sets the mark if it was unset). Disabled until the
        // video length is known.
        RangeSlider(
            value = sliderValue,
            onValueChange = onRangeChange,
            onValueChangeFinished = onRangeChangeFinished,
            valueRange = 0f..durF,
            enabled = durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MsColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // Duration + where the video is now (tap a time above to jump).
        Text(
            if (valid) "Duration: ${formatTime(lengthMs)}  ·  Now: ${formatTime(currentMs)}"
            else "Mark a start and an end  ·  Now: ${formatTime(currentMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MsColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        if (saving) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MsColors.Accent,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Saving clip… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MsColors.TextSecondary,
            )
        } else {
            // Loop preview: plays the marked range on repeat.
            if (looping) {
                LimePillButton(
                    text = "Stop preview",
                    onClick = onToggleLoop,
                    fontSize = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MsOutlinePill(
                    text = "Preview",
                    onClick = onToggleLoop,
                    fontSize = 14,
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MsOutlinePill(
                    text = "Reset",
                    onClick = onClear,
                    fontSize = 14,
                    modifier = Modifier.weight(1f),
                )
                LimePillButton(
                    text = "Save Clip",
                    onClick = onSave,
                    fontSize = 14,
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One Start/End stepper box: < time > with the label above the time.
 * The < > nudge by the panel step; tapping the time jumps there. */
@Composable
private fun TrimMarkBox(
    label: String,
    timeMs: Long?,
    onNudge: (Int) -> Unit,
    onJump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1C1C1E),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            TrimNudgeButton(
                icon = Icons.Filled.ChevronLeft,
                contentDescription = "Move $label earlier",
                enabled = timeMs != null,
                onClick = { onNudge(-1) },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = timeMs != null) { onJump() },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
                Text(
                    timeMs?.let { formatTime(it) } ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (timeMs != null) MsColors.Accent else MsColors.TextSecondary,
                )
            }
            TrimNudgeButton(
                icon = Icons.Filled.ChevronRight,
                contentDescription = "Move $label later",
                enabled = timeMs != null,
                onClick = { onNudge(1) },
            )
        }
    }
}

/** Small round − / + button that nudges one trim mark by the panel's
 * step (1s or 1 frame). Dimmed and inert until its mark is set. */
@Composable
private fun TrimNudgeButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Slide-to-unlock pill (mpvex-style): drag the thumb right to unlock.
 * Shown when controls are locked and the user taps the screen.
 */
@Composable
private fun UnlockSlider(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val thumbSize = 48.dp
    val thumbPx = with(density) { thumbSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .height(56.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val maxSlide = (widthPx - thumbPx - 8.dp.toPx()).coerceAtLeast(0f)
                        if (maxSlide > 0 && offsetX >= maxSlide * 0.75f) onUnlock()
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                ) { change, dragAmount ->
                    val maxSlide = (widthPx - thumbPx - 8.dp.toPx()).coerceAtLeast(0f)
                    offsetX = (offsetX + dragAmount).coerceIn(0f, maxSlide)
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Background hint: lock icon + text.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Slide to unlock",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Draggable thumb.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSize)
                .background(MsColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Slide",
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
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
