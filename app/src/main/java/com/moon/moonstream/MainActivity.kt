package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.NeutralRippleTheme
import com.moon.moonstream.ui.screens.ChannelsScreen
import com.moon.moonstream.ui.screens.CreateScreen
import com.moon.moonstream.ui.screens.DetailScreen
import com.moon.moonstream.ui.screens.LibraryScreen
import com.moon.moonstream.ui.screens.PlayerScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import kotlin.math.roundToInt

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
    // RippleTheme is error-deprecated in Compose 1.7, but its replacement
    // (RippleConfiguration) only exists from 1.8 — suppressed on this
    // experiment branch; the theme still works exactly as before.
    @Suppress("DEPRECATION_ERROR")
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

@OptIn(ExperimentalHazeApi::class) // HazeEffectScope.inputScale is experimental
@Composable
fun MoonStreamApp(telegramManager: com.moon.moonstream.tdlib.TelegramManager) {
    val authState by telegramManager.authState.collectAsState()
    val context = LocalContext.current

    // Haze blur state (experiment branch): the screens are the blur source,
    // the bottom pill is the blurred overlay.
    val hazeState = remember { HazeState() }

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
                        } else {
                            // No enabled channels — nothing to scan.
                            com.moon.moonstream.util.AppLog.i("SCAN", "Auto-scan on open: no channels")
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
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
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
                            onPlayInApp = { videoUrl, title, mediaTitle, mediaId, streamFileId ->
                                go(
                                    AppScreen.Player(
                                        videoUrl, title,
                                        mediaId, streamFileId, mediaTitle,
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
                val pillShape = RoundedCornerShape(24.dp)
                // Pill highlight (moon's pick 2026-11-06, Nuvio-style):
                // a frosted capsule that fills the selected tab's slot and
                // slides between tabs. Learned from Nuvio's
                // FloatingNavigationBar (jellyPillPath): the pill is a
                // fully-rounded rect nearly as tall as the bar, inset a few
                // dp — NOT a circle. The orb circle (0.15.123) rendered
                // small and high on moon's phone — its zero-size anchor
                // trick misplaces it — so this reuses the 0.15.122 highlight
                // positioning, which tracked the tab correctly, with the
                // capsule shape instead. Tap and swipe both switch.
                val density = LocalDensity.current
                val tabBounds = remember {
                    mutableStateListOf<Rect>().apply {
                        repeat(tabs.size) { add(Rect.Zero) }
                    }
                }
                val selIdx = tabs.indexOfFirst { it.screen == selectedTab }.coerceAtLeast(0)
                val selBounds = tabBounds.getOrNull(selIdx) ?: Rect.Zero
                val pillInsetPx = with(density) { 4.dp.toPx() }
                val indX = remember { Animatable(0f) }
                val indW = remember { Animatable(0f) }
                var indReady by remember { mutableStateOf(false) }
                val navSlideSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
                // Snap into place on first measure, slide on every change.
                LaunchedEffect(selIdx, selBounds) {
                    if (selBounds == Rect.Zero) return@LaunchedEffect
                    val targetX = selBounds.left - pillInsetPx
                    val targetW = selBounds.width + pillInsetPx * 2
                    if (!indReady) {
                        indX.snapTo(targetX)
                        indW.snapTo(targetW)
                        indReady = true
                    } else {
                        launch { indX.animateTo(targetX, navSlideSpec) }
                        indW.animateTo(targetW, navSlideSpec)
                    }
                }
                // Bottom pill (Nuvio-style capsule, moon's pick 2026-11-06):
                // a black-tinted blurred bar; the selected tab sits in a
                // frosted capsule that slides between tabs. Tap and swipe
                // both switch. The capsule is drawn as a child of the
                // container, behind the tabs, above the blurred bar.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 22.dp)
                        .pointerInput(selectedTab) {
                            // Swipe-to-switch (moon's 2026-11-06): the old
                            // `draggable` never fired — it fought the
                            // `clickable` on each tab for the gesture, so
                            // swipes did nothing and only taps worked.
                            // Manual loop instead: track the finger, consume
                            // moves only once it's clearly a horizontal
                            // drag, decide on release. A plain tap consumes
                            // nothing, so the tab clickables still fire.
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    var accum = 0f
                                    var dragging = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: continue
                                        if (!change.pressed) {
                                            // Finger lifted: a real swipe switches tabs.
                                            if (dragging) {
                                                val idx = tabs.indexOfFirst { it.screen == selectedTab }
                                                if (accum < -80 && idx < tabs.size - 1) {
                                                    goTab(tabs[idx + 1].screen)
                                                } else if (accum > 80 && idx > 0) {
                                                    goTab(tabs[idx - 1].screen)
                                                }
                                            }
                                            break
                                        }
                                        val dx = change.position.x - change.previousPosition.x
                                        accum += dx
                                        // Wait for a clear horizontal move
                                        // before claiming the gesture, so
                                        // taps (and vertical scrolls) still work.
                                        if (!dragging && kotlin.math.abs(accum) < 24f) continue
                                        dragging = true
                                        change.consume()
                                    }
                                }
                            }
                        },
                    ) {
                        // Pill background: blurred, black-tinted, bordered.
                        // matchParentSize so it sizes to the tabs Row.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(pillShape)
                                .hazeEffect(hazeState) {
                                    style = HazeStyle(
                                        // Black-tinted backdrop blur (moon's
                                        // AMOLED pick): darker base + stronger
                                        // black tint so the pill reads
                                        // black-frosted, with the blurred
                                        // content still showing through.
                                        backgroundColor = Color.Black.copy(alpha = 0.68f),
                                        tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
                                        blurRadius = 18.dp,
                                    )
                                    // Half-resolution blur input (25% of the
                                    // pixels to blur) — the main RAM/GPU
                                    // saving on low-end phones.
                                    inputScale = HazeInputScale.Fixed(0.5f)
                                }
                                .border(0.5.dp, Color.White.copy(alpha = 0.10f), pillShape),
                        )
                        // The sliding capsule (behind the tabs): fully
                        // rounded, frosted white, nearly as tall as the bar.
                        if (indReady) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            indX.value.roundToInt(),
                                            (selBounds.top - pillInsetPx).roundToInt(),
                                        )
                                    }
                                    .size(
                                        with(density) { indW.value.toDp() },
                                        with(density) { (selBounds.height + pillInsetPx * 2).toDp() },
                                    )
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.22f)),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                        tabs.forEachIndexed { idx, tab ->
                            val selected = selectedTab == tab.screen
                            Column(
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        tabBounds[idx] = coords.boundsInParent()
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { goTab(tab.screen) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
            }
            // In-app toast overlay (above content, below nothing — it's last).
            com.moon.moonstream.ui.components.MsToastHost(
                state = com.moon.moonstream.ui.components.MsToast.host,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
