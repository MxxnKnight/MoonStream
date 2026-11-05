package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.NeutralRippleTheme
import com.moon.moonstream.ui.screens.ChannelsScreen
import com.moon.moonstream.ui.screens.CreateScreen
import com.moon.moonstream.ui.screens.DetailScreen
import com.moon.moonstream.ui.screens.LibraryScreen
import com.moon.moonstream.ui.screens.PlayerScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.moon.moonstream.ui.screens.LoginScreen
import com.moon.moonstream.ui.screens.LogsScreen
import com.moon.moonstream.ui.screens.ServerScreen
import com.moon.moonstream.ui.screens.SettingsScreen
import com.moon.moonstream.ui.screens.SplashScreen
import com.moon.moonstream.ui.model.CreatePrefill
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MoonStreamTheme
import com.moon.moonstream.ui.theme.MsColors

/**
 * Phase 1: hand-rolled navigation (no navigation-compose in the offline mirror).
 * TODO(Phase 2): switch to navigation-compose when CI builds with network access.
 */
sealed interface AppScreen {
    data object Splash : AppScreen
    data object Login : AppScreen
    data object Server : AppScreen
    data object Library : AppScreen
    data class Detail(val mediaId: Long) : AppScreen
    data object Channels : AppScreen
    data class Create(val prefill: CreatePrefill? = null) : AppScreen
    data object Settings : AppScreen
    data object Logs : AppScreen
    data class Player(
        val videoUrl: String,
        val title: String,
        val mediaId: Long = 0L,
        val streamFileId: Long = 0L,
        val mediaTitle: String = "",
        /** Header Play = continue watching: skip resume prompt, play from saved spot. */
        val autoResume: Boolean = false,
    ) : AppScreen
}

private data class Tab(
    val screen: AppScreen,
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
)

private val tabs = listOf(
    Tab(AppScreen.Server, "Home", Icons.Filled.Dns, Icons.Outlined.Dns),
    Tab(AppScreen.Library, "Library", Icons.Filled.Archive, Icons.Outlined.Archive),
    Tab(AppScreen.Channels, "Channels", Icons.Filled.RssFeed, Icons.Outlined.RssFeed),
    Tab(AppScreen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    com.moon.moonstream.util.AppLog.init(this)
    AppUiState.init(this)
    BackendProvider.init(this)
    com.moon.moonstream.server.ServerTelemetry.load(this)

    // One-time cleanup: delete library entries with no streams and no subtitles
    // (moon's request 2026-11-04: "we don't need a entry without any streams").
    // Runs in background on every start; only touches truly empty entries.
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val db = com.moon.moonstream.index.db.MsDatabase.get(this@MainActivity)
            val n = db.mediaDao().deleteEmpty()
            if (n > 0) com.moon.moonstream.util.AppLog.i("LIB", "deleted $n empty entries")
        } catch (e: Exception) {
            com.moon.moonstream.util.AppLog.w("LIB", "empty-entry cleanup failed: ${e.message}")
        }
    }

    // Start TelegramManager early so it can restore the saved session
    val telegramManager = com.moon.moonstream.tdlib.TelegramManager.getInstance(this)
    telegramManager.start()

    setContent {
        MoonStreamTheme {
            CompositionLocalProvider(LocalRippleTheme provides NeutralRippleTheme) {
                MoonStreamApp(telegramManager = telegramManager)
            }
        }
    }
}
}

@Composable
fun MoonStreamApp(telegramManager: com.moon.moonstream.tdlib.TelegramManager) {
    val authState by telegramManager.authState.collectAsState()
    val context = LocalContext.current

    // Warm the library list at launch so the Library tab opens instantly.
    LaunchedEffect(Unit) { com.moon.moonstream.index.db.LibraryCache.items(context) }

    // Auto-scan on app open (moon's pick 2026-11-06, option 1): once per
    // launch, when Telegram is Ready, quietly run an incremental scan of all
    // channels so new files appear in the library by themselves. Guarded by
    // the Settings toggle (ScanPrefs.isAutoScan, default on) and by
    // ScanService's own running flag, so a second start is ignored.
    var autoScanDone by remember { mutableStateOf(false) }

    // Automatically install real backend whenever Telegram becomes Ready
    LaunchedEffect(authState) {
        if (authState is com.moon.moonstream.tdlib.AuthState.Ready) {
            val realBackend = com.moon.moonstream.backend.TdlibMoonStreamBackend(
                context = context.applicationContext,
                telegramManager = telegramManager
            )
            com.moon.moonstream.backend.BackendProvider.install(realBackend)
            if (!autoScanDone && com.moon.moonstream.index.scan.ScanPrefs.isAutoScan(context)) {
                autoScanDone = true
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val db = com.moon.moonstream.index.db.MsDatabase.get(context)
                        val targets = db.channelConfigDao().all()
                            .filter { !it.removed }
                            .map {
                                com.moon.moonstream.index.scan.ScanTarget(
                                    it.channelId, it.title, it.isAnime,
                                )
                            }
                        if (targets.isNotEmpty()) {
                            com.moon.moonstream.util.AppLog.i(
                                "SCAN", "Auto-scan on open: ${targets.size} channel(s)",
                            )
                            com.moon.moonstream.index.scan.ScanService.start(
                                context, targets,
                                com.moon.moonstream.index.scan.ScanMode.INCREMENTAL,
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "auto-scan failed: ${e.message}")
                    }
                }
            }
        }
    }
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Splash) }
    val current = backStack.last()

    // Detail belongs to the Library tab; Create belongs to Channels; Logs to Settings.
    val selectedTab = when (current) {
        is AppScreen.Detail -> AppScreen.Library
        is AppScreen.Create -> AppScreen.Channels
        AppScreen.Logs -> AppScreen.Settings
        else -> current
    }

    // No going back from Splash or Login.
    BackHandler(enabled = backStack.size > 1 && current !is AppScreen.Splash && current !is AppScreen.Login) {
        backStack.removeLast()
    }

    fun go(screen: AppScreen) {
        backStack.add(screen)
    }

    fun goTab(screen: AppScreen) {
        backStack.clear()
        backStack.add(screen)
    }

    // Floating pill nav (v0.4.0, NoxMedia-style): detached from the edges,
    // not the old edge-to-edge bar. Hidden on Splash, Login and Player
    // (a video player is immersive — no nav pill over it).
    val showNav = current !is AppScreen.Splash && current !is AppScreen.Login &&
        current !is AppScreen.Player

    Scaffold(
        containerColor = MsColors.Bg,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Content runs full-bleed behind the floating pill (NoxMedia-style);
            // edge fades below make the scroll-under-pill effect readable.
            // Screens keep their own bottom insets inside their scrollables
            // so the last item can still scroll fully into view.
            // AnimatedContent gives fast, fluid tab switches (180ms slide).
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    // Cheap switch: the new screen fades in quickly and the old
                    // one is dropped at once, so only ONE heavy screen is alive
                    // during the animation (the old slide+fade composed and
                    // drew two full screens at the same time).
                    fadeIn(tween(110)) togetherWith ExitTransition.None
                },
                label = "screenSwitch",
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                when (screen) {
                    AppScreen.Splash -> SplashScreen(
    onDone = {
        // Give TDLib a moment to restore the session
        // For now we still use the old loggedIn flag.
        // Later we can make it smarter.
        if (AppUiState.loggedIn) {
            // Try to install real backend if session is already Ready
            if (telegramManager.authState.value is com.moon.moonstream.tdlib.AuthState.Ready) {
                val realBackend = com.moon.moonstream.backend.TdlibMoonStreamBackend(
                    context = context.applicationContext,
                    telegramManager = telegramManager
                )
                com.moon.moonstream.backend.BackendProvider.install(realBackend)
            }
            goTab(AppScreen.Server)
        } else {
            go(AppScreen.Login)
        }
    },
)
                    AppScreen.Login -> LoginScreen(onLoggedIn = { goTab(AppScreen.Server) })
                    AppScreen.Server -> ServerScreen(
                        onOpenLibrary = { go(AppScreen.Library) },
                        onOpenDetail = { id -> go(AppScreen.Detail(id)) },
                    )
                    AppScreen.Library ->
                        LibraryScreen(onOpenDetail = { id -> go(AppScreen.Detail(id)) })
                    is AppScreen.Detail ->
                        DetailScreen(
                            mediaId = screen.mediaId,
                            onBack = { backStack.removeLast() },
                            onOpenCreate = { prefill -> go(AppScreen.Create(prefill)) },
                            onPlayInApp = { videoUrl, title, mediaTitle, mediaId, streamFileId, autoResume ->
                                go(
                                    AppScreen.Player(
                                        videoUrl, title,
                                        mediaId, streamFileId, mediaTitle, autoResume,
                                    )
                                )
                            },
                        )
                    AppScreen.Channels ->
                        ChannelsScreen(
                            onCreateEntry = { file ->
                                go(
                                    AppScreen.Create(
                                        if (file == null) null
                                        else CreatePrefill(
                                            files = listOf(
                                                PrefillFile(
                                                    channelId = file.channelId,
                                                    messageId = file.messageId,
                                                    fileName = file.fileName,
                                                    fileSize = file.fileSize,
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            },
                        )
                    is AppScreen.Create -> CreateScreen(
                        prefill = screen.prefill ?: CreatePrefill(),
                        onDone = { id ->
                            // Swap Create for the new entry's Detail screen.
                            backStack.removeLast()
                            go(AppScreen.Detail(id))
                        },
                        onCancel = { backStack.removeLast() },
                    )
                    is AppScreen.Player -> PlayerScreen(
                        videoUri = android.net.Uri.parse(screen.videoUrl),
                        title = screen.title,
                        mediaTitle = screen.mediaTitle.ifBlank { screen.title },
                        mediaId = screen.mediaId,
                        streamFileId = screen.streamFileId,
                        autoResume = screen.autoResume,
                        onClose = { backStack.removeLast() },
                        onSwitchPlayer = { url, t, mt, mid, fid ->
                            // Episode switch: replace this player with the new file's.
                            backStack.removeLast()
                            go(AppScreen.Player(url, t, mid, fid, mt))
                        },
                    )
                    AppScreen.Settings -> SettingsScreen(
                        onOpenLogs = { go(AppScreen.Logs) },
                        onLoggedOut = {
                            backStack.clear()
                            backStack.add(AppScreen.Login)
                        },
                    )
                    AppScreen.Logs -> LogsScreen(onBack = { backStack.removeLast() })
                }
            }

            if (showNav) {
                // Bottom fade: content glides behind the pill instead of
                // clipping hard. Drawn under the pill, above the content;
                // it never consumes touches.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MsColors.Bg,
                            ),
                        ),
                )
                // Top fade: slight, softens content reaching the top edge.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to MsColors.Bg.copy(alpha = 0.55f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
                // iOS-style tab bar (moon's iOS rebuild 2026-11-04): icon + label,
                // blue selected, gray unselected. Keeps floating pill + swipe.
                // Basic black blur look (moon's request 2026-11-04): simple dark
                // translucent pill like the reference — no GPU blur, light on RAM.
                var dragAccum by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                val pillShape = RoundedCornerShape(24.dp)
                // Plain black pill (hotfix 2026-11-05: BlurView crashed on Android 12).
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 22.dp)
                        .clip(pillShape)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.10f), pillShape)
                        .draggable(
                            state = rememberDraggableState { delta ->
                                    dragAccum += delta
                                },
                                orientation = Orientation.Horizontal,
                                onDragStopped = {
                                    val idx = tabs.indexOfFirst { it.screen == selectedTab }
                                if (dragAccum < -80 && idx < tabs.size - 1) {
                                    goTab(tabs[idx + 1].screen)
                                } else if (dragAccum > 80 && idx > 0) {
                                    goTab(tabs[idx - 1].screen)
                                }
                                dragAccum = 0f
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab.screen
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { goTab(tab.screen) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                if (selected) tab.iconFilled else tab.iconOutlined,
                                contentDescription = tab.label,
                                tint = if (selected) MsColors.Accent else Color(0xFF98989F),
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MsColors.Accent else Color(0xFF98989F),
                            )
                        }
                    }
                }
            }
            // In-app toast overlay (above content, below nothing — it's last).
            com.moon.moonstream.ui.components.MsToastHost(
                state = com.moon.moonstream.ui.components.MsToast.host,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
