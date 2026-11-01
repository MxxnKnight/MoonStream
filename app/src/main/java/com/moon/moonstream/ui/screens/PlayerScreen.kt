package com.moon.moonstream.ui.screens

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app video player (Phase 6, item 1).
 * Streams from MoonStream's localhost HTTP server via plain MediaItem.fromUri().
 * No custom DataSource needed — the :8081 server handles Range requests.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUri: Uri,
    title: String,
    subtitleUris: List<Uri> = emptyList(),
    mediaId: Long = 0L,
    streamFileId: Long = 0L,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { MsDatabase.get(context) }
    val ioScope = rememberCoroutineScope()

    // Build the ExoPlayer instance.
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

    // Resume state (moon's design 2026-11-02): if this file has a saved
    // position, opening the player asks "Resume from 12:34?" instead of
    // just restarting.
    var awaitingResumeChoice by remember { mutableStateOf(false) }
    var resumeFrom by remember { mutableLongStateOf(0L) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        // Double-tap left/right: ∓10s (TMPlayer pattern).
                        // We use screen width to decide side.
                        // offset.x is available; simplified: left half = back, right = forward.
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

        // Bottom controls: play/pause + seek bar + time.
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                // Seek bar.
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
                // Play/pause + 10s skip buttons.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                    }) {
                        Icon(Icons.Filled.Replay10, "Back 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = {
                        if (isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = {
                        player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
                    }) {
                        Icon(Icons.Filled.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
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

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
