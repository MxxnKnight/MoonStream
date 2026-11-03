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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.player.MsDataSourceFactory
import com.moon.moonstream.ui.player.PlayerUrls
import com.moon.moonstream.ui.player.SubDelayPrefs
import com.moon.moonstream.ui.player.TdStreamUri
import com.moon.moonstream.ui.player.TdSubUri
import com.moon.moonstream.ui.state.LanguagePrefs
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.util.AppLog
import com.moon.moonstream.util.AniSkip
import com.moon.moonstream.util.IntroDb
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
    // Orientation: landscape while playing. Keyed on streamFileId so it
    // re-applies when switching episodes (moon's bug 2026-11-02: Next
    // episode opened in portrait). On dispose, always restore portrait —
    // not the captured value, which could be landscape from a previous
    // player instance (moon's bug: closing left the app in landscape).
    DisposableEffect(streamFileId) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        insets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MsDataSourceFactory(context)))
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
    val chosenSubLang = remember(allSubs, subFirst, subSecond) {
        LanguagePrefs.chooseSubtitle(allSubs.map { it.language }, subFirst, subSecond)
    }

    // Subtitle tracks — mime type comes from the real file name, language from
    // the indexer. Straight from Telegram when we know the size (so subtitles
    // work with the addon server off, delay applied on-device); the server
    // route stays as a fallback for odd rows. Preferred language sorts first,
    // which is what Media3 picks.
    val subPairs = remember(allSubs, delayMs, chosenSubLang) {
        allSubs
            .sortedByDescending { sub ->
                chosenSubLang?.let { LanguagePrefs.matches(sub.language, it) } ?: false
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

    // Resume state (moon's design 2026-11-02).
    var awaitingResumeChoice by remember { mutableStateOf(false) }
    var resumeFrom by remember { mutableLongStateOf(0L) }

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
        val name = opt?.label ?: "Off"
        AppLog.i("PLAYER", "Track picked (${if (forSubs) "subs" else "audio"}): $name")
        MsToast.show(name)
        sheet = null
    }

    var mediaReady by remember { mutableStateOf(false) }

    // Load / rebuild the media item. First run: resume check (prompt, or
    // straight to the saved spot for the header Play button). Later runs
    // (subs arrived, delay changed): rebuild keeping position + play state.
    LaunchedEffect(playUri, subPairs, filesLoaded, chosenSubLang) {
        // The Room row can land a frame after the screen opens. Wait for it,
        // otherwise playback would start on the HTTP fallback and swap
        // sources (and lose the resume choice) mid-load.
        if (streamFileId != 0L && !filesLoaded) return@LaunchedEffect
        val isFirst = !mediaReady
        val pos = if (isFirst) 0L else player.currentPosition.coerceAtLeast(0L)
        val keepPlaying = if (isFirst) false else player.isPlaying
        player.setMediaItem(buildMediaItem(playUri, subPairs), pos)
        // Language picks are applied per media item (a new item resets tracks).
        // Audio keeps a real first/second order; text uses the language we
        // found on this title, so "Mal, else Eng" doesn't need two passes.
        val audioLangs = listOfNotNull(
            LanguagePrefs.iso(audioFirst),
            LanguagePrefs.iso(audioSecond),
        ).distinct()
        val textLang = chosenSubLang?.let { LanguagePrefs.iso(it) }
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
                val pct = if (dur > 0) (pos * 100 / dur).toInt() else 0
                ioScope.launch(Dispatchers.IO) {
                    AppLog.i(
                        "PLAYER",
                        "closed at ${pos / 1000}s of ${dur / 1000}s ($pct%) — " +
                            if (finished) "watched to the end" else "resume position saved",
                    )
                    try {
                        db.watchProgressDao().updatePosition(
                            streamFileId, finalPos, System.currentTimeMillis(),
                        )
                    } catch (e: Exception) { /* never break close */ }
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
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
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
                if (duration <= 0L) duration = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // Auto-hide controls after 3s.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !controlsLocked) {
            delay(3000)
            controlsVisible = false
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
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        // Double-tap left/right: ∓10s with animated feedback.
                        // Consecutive double-taps accumulate (+20s, +30s...).
                        // Disabled while locked.
                        if (!controlsLocked) {
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
            .pointerInput(sheet, controlsLocked) {
                // Vertical swipes: left half = brightness, right half = volume
                // (video-player style). Skipped while a sheet is open or locked.
                detectVerticalDragGestures(
                    onDragEnd = { /* HUD hides on its own timer */ },
                    onDragCancel = { },
                ) { change, dragAmount ->
                    if (sheet == null && !controlsLocked) {
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
                    PlayerIconButton(
                        icon = Icons.Filled.Lock,
                        contentDescription = "Lock controls",
                        onClick = {
                            controlsLocked = true
                            controlsVisible = false
                            showUnlockPill = true
                        },
                    )
                }
            }
        }

        // Center transport: -10s / play-pause / +10s spread across the width
        // (moon's request 2026-11-02: -10s left edge, +10s right edge).
        if (controlsVisible && !controlsLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                PlayerIconButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = "Back 10s",
                    onClick = {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                    },
                    size = 52.dp,
                    iconSize = 24.dp,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                // Big play/pause: 76dp circle, no border (mpvex-style).
                Surface(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(76.dp)
                        .align(Alignment.Center),
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
                    modifier = Modifier.align(Alignment.CenterEnd),
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
                        // Next episode (moon's pick 2026-11-02).
                        val nextFile = nextGroup?.files?.firstOrNull()
                        if (nextFile != null) {
                            PlayerIconButton(
                                icon = Icons.Filled.SkipNext,
                                contentDescription = "Next episode",
                                onClick = { switchEpisode(nextFile) },
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
                            )
                        }
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
                                    resizeModes[resizeIndex].third,
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
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 56.dp)
                            .background(
                                Color.Black.copy(alpha = 0.62f),
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.2f),
                                CircleShape,
                            )
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${if (forward) "+" else "-"}${secs}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
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
        // at bottom-right. Anime → AniSkip (Kitsu ID), series/movies → IntroDB
        // (IMDb ID). No manual fallback — no API data = no button.
        var skipTimes by remember { mutableStateOf<AniSkip.SkipTimes?>(null) }
        var introSegs by remember { mutableStateOf<IntroDb.Segments?>(null) }
        var skipFetched by remember { mutableStateOf(false) }

        // Fetch skip data once per episode.
        LaunchedEffect(mediaId, currentGroup?.episode) {
            if (skipFetched) return@LaunchedEffect
            skipFetched = true
            val ep = currentGroup?.episode
            val media = try {
                withContext(Dispatchers.IO) { db.mediaDao().getById(mediaId) }
            } catch (e: Exception) { null }
            val isAnime = media?.type == "anime"
            // Anime → AniSkip via AniList ID (mapped from Kitsu at index).
            if (isAnime && ep != null && !media?.anilistId.isNullOrBlank()) {
                val durSecs = duration.coerceAtLeast(1L) / 1000
                skipTimes = AniSkip.getSkipTimes(media!!.anilistId!!, ep, durSecs)
                return@LaunchedEffect
            }
            // Series/movies → IntroDB via IMDb ID.
            val imdb = media?.imdbId
            if (!imdb.isNullOrBlank()) {
                introSegs = IntroDb.getSegments(
                    imdbId = imdb,
                    season = currentGroup?.season,
                    episode = ep,
                )
            } else {
                AppLog.i("PLAYER", "Skip: no anilistId/imdbId, no button")
            }
        }

        // Resolve the intro window from whichever API answered.
        val apiEndMs: Long? = skipTimes?.opEnd?.let { (it * 1000).toLong() }
            ?: introSegs?.introEndSec?.let { (it * 1000).toLong() }
        val apiStartMs: Long = skipTimes?.opStart?.let { (it * 1000).toLong() }
            ?: introSegs?.introStartSec?.let { (it * 1000).toLong() }
            ?: 0L
        val apiSource = if (skipTimes?.opEnd != null) "AniSkip"
            else if (introSegs?.introEndSec != null) "IntroDB" else null
        // Show ONLY during the actual intro from the API. No data = no button.
        val showSkipIntro = apiEndMs != null &&
            currentPosition in apiStartMs until apiEndMs &&
            !controlsLocked
        if (showSkipIntro) {
            // apiEndMs is non-null here (showSkipIntro requires it).
            val endMs = apiEndMs!!
            Button(
                onClick = {
                    player.seekTo(endMs)
                    AppLog.i("PLAYER", "Skip intro: ${currentPosition/1000}s → ${endMs/1000}s ($apiSource)")
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
                Text("Skip Intro", fontWeight = FontWeight.Bold)
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
                                        else MsColors.Surface,
                                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                                    )
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                                    .offset { IntOffset(0, sheetOffsetY.roundToInt()) }
                                    .background(
                                        MsColors.Surface,
                                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                                    Slider(
                                        value = subPosition,
                                        onValueChange = {
                                            subPosition = it
                                            SubDelayPrefs.setPosition(context, mediaId, it)
                                            applySubtitleStyle()
                                        },
                                        valueRange = 0f..0.4f,
                                        modifier = Modifier.weight(1.6f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MsColors.Accent,
                                            activeTrackColor = MsColors.Accent,
                                        ),
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
                // Material3 loader, mpvex-style 96dp (replaces the small default).
                CircularProgressIndicator(
                    modifier = Modifier.size(96.dp),
                    color = MsColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    strokeWidth = 6.dp,
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
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.size(size),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
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
