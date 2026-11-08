package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.animation.core.FastOutSlowInEasing
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
    // Slide direction for the next screen switch (+1 = new screen comes
    // from the right, -1 = from the left, 0 = plain fade). Set by go /
    // goTab / back, read by the AnimatedContent transitionSpec. This is
    // how every screen — Splash, Login, Create, Player, Detail — gets a
    // directional slide, and every back gets the reverse slide.
    var slideDir by remember { mutableStateOf(0) }

    // Detail belongs to the Library tab; Create belongs to Channels; Logs to Settings.
    val selectedTab = when (current) {
        is AppScreen.Detail -> AppScreen.Library
        is AppScreen.Create -> AppScreen.Channels
        AppScreen.Logs -> AppScreen.Settings
        else -> current
    }

    // Tab index for a screen, for the directional slide: Detail -> Library,
    // Create -> Channels, Logs -> Settings; -1 for screens with no tab
    // (Splash/Login/Player), which keep the plain fade.
    fun AppScreen.tabIndex(): Int {
        val tab = when (this) {
            is AppScreen.Detail -> AppScreen.Library
            is AppScreen.Create -> AppScreen.Channels
            AppScreen.Logs -> AppScreen.Settings
            else -> this
        }
        return tabs.indexOfFirst { it.screen == tab }
    }

    // Back: the screen underneath slides in from the left.
    fun back() {
        slideDir = -1
        backStack.removeLast()
    }

    // No going back from Splash or Login.
    BackHandler(enabled = backStack.size > 1 && current !is AppScreen.Splash && current !is AppScreen.Login) {
        back()
    }

    fun go(screen: AppScreen) {
        // Forward push: the new screen slides in from the right.
        slideDir = 1
        backStack.add(screen)
    }

    fun goTab(screen: AppScreen) {
        // Tab switch: direction follows the pill (a tab to the right
        // comes from the right, to the left from the left). From a
        // non-tab screen (Login/Splash) it's a forward move.
        val fromIdx = current.tabIndex()
        val toIdx = screen.tabIndex()
        slideDir = when {
            fromIdx < 0 -> 1
            toIdx < 0 || fromIdx == toIdx -> 0
            toIdx > fromIdx -> 1
            else -> -1
        }
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
                    // Directional slide, iOS style but cheap for a low-end
                    // phone (moon's 2026-11-09): the new screen slides in
                    // from the side the navigation points to — a push or a
                    // tab to the right comes from the right, a back or a
                    // tab to the left comes from the left. slideDir is set
                    // by go / goTab / back, so Splash, Login, Create,
                    // Player and Detail all slide, and every back slides
                    // the other way. Only the INCOMING screen animates;
                    // the old one is dropped at once (ExitTransition.None),
                    // so only ONE heavy screen is alive during the switch.
                    // A true iOS slide keeps both screens drawing at once,
                    // which stuttered on this phone before; this keeps the
                    // direction without that cost.
                    val dir = slideDir
                    if (dir == 0) {
                        fadeIn(tween(110)) togetherWith ExitTransition.None
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> dir * fullWidth },
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(220))) togetherWith ExitTransition.None
                    }
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
                            onBack = { back() },
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
                            back()
                            go(AppScreen.Detail(id))
                        },
                        onCancel = { back() },
                    )
                    is AppScreen.Player -> PlayerScreen(
                        videoUri = android.net.Uri.parse(screen.videoUrl),
                        title = screen.title,
                        mediaTitle = screen.mediaTitle.ifBlank { screen.title },
                        mediaId = screen.mediaId,
                        streamFileId = screen.streamFileId,
                        onClose = { back() },
                        onSwitchPlayer = { url, t, mt, mid, fid ->
                            // Episode switch: replace this player with the new file's.
                            back()
                            go(AppScreen.Player(url, t, mid, fid, mt))
                        },
                    )
                    AppScreen.Settings -> SettingsScreen(
                        onOpenLogs = { go(AppScreen.Logs) },
                        onLoggedOut = {
                            // Logout: Login slides in from the right, like a fresh start.
                            slideDir = 1
                            backStack.clear()
                            backStack.add(AppScreen.Login)
                        },
                    )
                    AppScreen.Logs -> LogsScreen(onBack = { back() })
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
                // Full pill (moon's 2026-11-07: "more rounded like nuvio"):
                // 50dp clamps to half the bar height, so the ends are true
                // semicircles like Nuvio's bar — was 24dp, which left the
                // ends visibly flatter than a capsule.
                val pillShape = RoundedCornerShape(50)
                // Pill highlight — NUVIO geometry (moon's pick 2026-11-07):
                // the selection FILLS the whole selected tab slot, like
                // Nuvio's pill. The old versions failed because the bar
                // was only as wide as the tab contents (no fillMaxWidth,
                // no weight), so each slot was narrow and the capsule in
                // it read as a rounded square — small and pure next to
                // the bar. Now the bar spans the full width and the four
                // tabs share it equally (weight 1f each), so a slot is
                // ~75dp wide and ~46dp tall: the capsule filling it is a
                // proper horizontal stadium (50dp tall, bar height minus
                // 4dp top and bottom, fully rounded). Only its centre X
                // and its width animate (snap on first measure, spring
                // on later switches). It is vertically centered by the
                // container's alignment, so it can never drift high or
                // low the way the 0.15.123 orb did. Tap and swipe switch.
                val density = LocalDensity.current
                val tabBounds = remember {
                    mutableStateListOf<Rect>().apply {
                        repeat(tabs.size) { add(Rect.Zero) }
                    }
                }
                // One shared interaction source for the tabs, with NO ripple
                // (moon's 2026-11-07): the tab clickable's default ripple
                // drew a second rounded-rect "selection" around the capsule,
                // so the pill showed two selections at once. Only the
                // capsule is the selection now — taps show nothing else.
                val tabInteractionSource = remember { MutableInteractionSource() }
                // Scope for moving the capsule: the drag loop runs inside
                // awaitPointerEventScope (a RESTRICTED scope — it refuses
                // Animatable suspend calls like snapTo/animateTo), so the
                // capsule moves go through this launched scope instead.
                val pillScope = rememberCoroutineScope()
                val selIdx = tabs.indexOfFirst { it.screen == selectedTab }.coerceAtLeast(0)
                val selBounds = tabBounds.getOrNull(selIdx) ?: Rect.Zero
                // The tabs' bounds are measured relative to the Row's CONTENT
                // area (after its 4dp horizontal padding), but the capsule
                // is positioned relative to the bar (the Row's outer edge).
                // Without this +4dp the capsule sat 4dp left of its tab
                // (moon's 2026-11-07 screenshot: Channels icon at 653px,
                // capsule centre at 636px). Keep this in sync with the
                // Row's horizontal padding below.
                val rowHPaddingPx = with(density) { 4.dp.toPx() }
                // Drag threshold (moon's 2026-11-07): a tap must never trip
                // the drag. The old 24px (8dp) was so small that the tiny
                // finger movement of pressing a tab engaged the drag and the
                // capsule SNAPPED to the finger (teleport) instead of
                // sliding via the spring. 16dp gives a tap real headroom;
                // a deliberate drag still engages quickly and then follows
                // the finger exactly.
                val dragThresholdPx = with(density) { 16.dp.toPx() }
                // (Capsule height is 47dp — moon's 2026-11-09, compact
                // low-end. Set in the capsule's .size() below.)
                val pillCenterX = remember { Animatable(0f) }
                val indW = remember { Animatable(0f) }
                var indReady by remember { mutableStateOf(false) }
                // One running capsule animation at a time (moon's bug
                // 2026-11-09: the capsule landed on the wrong tab).
                // The slide-to-tab, the drag catch-up and the release
                // snap-back all move pillCenterX, and they run in
                // different scopes (LaunchedEffect vs pillScope), so a
                // stale drag animation could outlive the gesture and
                // drag the capsule back to the old tab after the
                // selection already changed. Every capsule animation
                // goes through this holder: a new one cancels the old,
                // so only the latest move runs.
                val capsuleAnim = remember { object { var job: Job? = null } }
                val navSlideSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
                // Snap into place on first measure, slide on every change.
                LaunchedEffect(selIdx, selBounds) {
                    if (selBounds == Rect.Zero) return@LaunchedEffect
                    val targetCenterX = selBounds.center.x + rowHPaddingPx
                    val targetW = selBounds.width
                    if (!indReady) {
                        pillCenterX.snapTo(targetCenterX)
                        indW.snapTo(targetW)
                        indReady = true
                    } else {
                        // Cancel any drag animation still running — a
                        // tap's catch-up or snap-back can outlive the
                        // gesture and fight this slide to the new tab.
                        capsuleAnim.job?.cancel()
                        capsuleAnim.job = launch { pillCenterX.animateTo(targetCenterX, navSlideSpec) }
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
                        .fillMaxWidth()
                        // Compact (moon's 2026-11-09, low-end): smaller
                        // footprint — 16dp sides, 14dp bottom (was 24/22).
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        .pointerInput(selectedTab) {
                            // Drag-the-capsule to switch (moon's 2026-11-07):
                            // press the pill and drag it to another tab — the
                            // capsule follows the finger, and on release it
                            // snaps to the nearest tab and switches to it.
                            // The old `draggable` never fired — it fought the
                            // `clickable` on each tab for the gesture, so
                            // swipes did nothing and only taps worked.
                            // Manual loop instead: track the finger, consume
                            // moves only once it's clearly a horizontal
                            // drag, decide on release. A plain tap consumes
                            // nothing, so the tab clickables still fire.
                            // Listen in the Initial pass (requireUnconsumed
                            // = false): the tab clickables eat the down in
                            // the Main pass, so a drag that starts ON a tab
                            // never reached this loop. In the Initial pass
                            // the parent sees the finger first.
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    var accum = 0f
                                    var dragging = false
                                    // The capsule's centre when the drag
                                    // starts (the selected tab's centre).
                                    val startCenterX = pillCenterX.value
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: continue
                                        if (!change.pressed) {
                                            // Finger lifted: stop any
                                            // catch-up slide, then snap to
                                            // the nearest tab and switch.
                                            // (Catch-up runs through the
                                            // shared capsuleAnim holder, so
                                            // the selection slide can
                                            // cancel it.)
                                            capsuleAnim.job?.cancel()
                                            capsuleAnim.job = null
                                            if (dragging) {
                                                var nearest = selIdx
                                                var best = Float.MAX_VALUE
                                                tabBounds.forEachIndexed { i, bounds ->
                                                    if (bounds == Rect.Zero) return@forEachIndexed
                                                    // Bounds are in Row-content coords;
                                                    // pillCenterX is in bar coords (+padding).
                                                    val d = kotlin.math.abs(
                                                        (bounds.center.x + rowHPaddingPx) - pillCenterX.value
                                                    )
                                                    if (d < best) {
                                                        best = d
                                                        nearest = i
                                                    }
                                                }
                                                if (nearest != selIdx) {
                                                    goTab(tabs[nearest].screen)
                                                } else {
                                                    // Same tab: spring the
                                                    // capsule back home.
                                                    // (Bounds are in Row-content
                                                    // coords; add the padding.)
                                                    val homeCenter = tabBounds.getOrNull(selIdx)?.center?.x
                                                        ?.plus(rowHPaddingPx) ?: startCenterX
                                                    capsuleAnim.job?.cancel()
                                                    capsuleAnim.job = pillScope.launch {
                                                        pillCenterX.animateTo(homeCenter, navSlideSpec)
                                                    }
                                                }
                                            }
                                            break
                                        }
                                        val dx = change.position.x - change.previousPosition.x
                                        accum += dx
                                        // Wait for a clear horizontal move
                                        // before claiming the gesture, so
                                        // taps (and vertical scrolls) still work.
                                        if (!dragging && kotlin.math.abs(accum) < dragThresholdPx) continue
                                        val wasDragging = dragging
                                        dragging = true
                                        change.consume()
                                        // Capsule follows the finger EXACTLY
                                        // (moon's 2026-11-07, mpvRx video):
                                        // its centre sits under the finger,
                                        // not offset from it. The old code
                                        // moved it by the finger's delta
                                        // (startCenterX + accum), so the
                                        // capsule kept its distance from the
                                        // finger — press on Channels and the
                                        // capsule on Home would slide in
                                        // parallel but never come under the
                                        // finger. Now the centre IS the
                                        // finger's X, clamped to the
                                        // first/last tab centres so it can't
                                        // leave the bar. A plain tap never
                                        // engages, so it still slides to the
                                        // tab with the spring.
                                        // (Bounds are in Row-content coords;
                                        // add the padding to match pillCenterX.)
                                        val firstCenter = tabBounds.firstOrNull()?.center?.x
                                        val lastCenter = tabBounds.lastOrNull()?.center?.x
                                        if (firstCenter != null && lastCenter != null
                                            && firstCenter != 0f && lastCenter != 0f
                                        ) {
                                            val minCenter = firstCenter + rowHPaddingPx
                                            val maxCenter = lastCenter + rowHPaddingPx
                                            val fingerX = change.position.x.coerceIn(minCenter, maxCenter)
                                            if (!wasDragging) {
                                                // First engage: if the capsule
                                                // is far from the finger, SLIDE
                                                // it there instead of snapping
                                                // (moon's 2026-11-07: he wants
                                                // to see it run from its tab
                                                // to his finger, not teleport).
                                                // A short distance snaps as
                                                // before (no visible difference).
                                                val dist = kotlin.math.abs(fingerX - pillCenterX.value)
                                                if (dist > dragThresholdPx) {
                                                    capsuleAnim.job?.cancel()
                                                    capsuleAnim.job = pillScope.launch {
                                                        pillCenterX.animateTo(fingerX, navSlideSpec)
                                                    }
                                                    continue
                                                }
                                            }
                                            // While the catch-up slide is still
                                            // running, retarget it to the
                                            // finger's latest spot instead of
                                            // ignoring the finger (the old code
                                            // skipped moves while the slide
                                            // ran, so the capsule lagged
                                            // behind a fast drag). Once the
                                            // finger stops, the slide lands
                                            // and exact follow resumes.
                                            if (capsuleAnim.job?.isActive == true) {
                                                capsuleAnim.job?.cancel()
                                                capsuleAnim.job = pillScope.launch {
                                                    pillCenterX.animateTo(fingerX, navSlideSpec)
                                                }
                                                continue
                                            }
                                            capsuleAnim.job = null
                                            capsuleAnim.job = pillScope.launch {
                                                pillCenterX.snapTo(fingerX)
                                            }
                                        }
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
                                        // AMOLED pick, blacker 2026-11-07):
                                        // darker base + stronger black tint
                                        // so the pill reads black-frosted,
                                        // with the blurred content still
                                        // showing through.
                                        backgroundColor = Color.Black.copy(alpha = 0.75f),
                                        tint = HazeTint(Color.Black.copy(alpha = 0.55f)),
                                        // Smaller radius (was 12dp, moon's
                                        // 2026-11-09 low-end): a tighter
                                        // blur kernel is cheaper on the GPU,
                                        // and the darker tint hides the
                                        // softer frost.
                                        blurRadius = 8.dp,
                                    )
                                    // LOW-SETTINGS blur (moon's 2026-11-07:
                                    // lag-free on his low-budget phone):
                                    // quarter-resolution blur input — only
                                    // 1/16th of the pixels to blur (was
                                    // half-resolution, 1/4th). That is the
                                    // main RAM/GPU saving; the darker tint
                                    // above keeps it looking black-frosted
                                    // even at this low resolution.
                                    inputScale = HazeInputScale.Fixed(0.25f)
                                }
                                .border(0.5.dp, Color.White.copy(alpha = 0.10f), pillShape),
                        )
                        // The sliding capsule (behind the tabs): it fills
                        // the whole selected slot — Nuvio's shape. Fully
                        // rounded, frosted white, 50dp tall (bar height
                        // minus 4dp top and bottom) so it reads as a wide
                        // horizontal capsule, never a rounded square.
                        if (indReady) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset {
                                        IntOffset(
                                            (pillCenterX.value - indW.value / 2).roundToInt(),
                                            0,
                                        )
                                    }
                                    .size(
                                        with(density) { indW.value.toDp() },
                                        // 47dp (moon's 2026-11-09, compact
                                        // low-end — was 64dp): the bar is
                                        // ~48dp now (Row padding 6dp, icon
                                        // 20dp, text 9sp), so the capsule
                                        // sits just 0.5dp from the bar's
                                        // top/bottom ends. Same full-pill
                                        // shape as the bar
                                        // (RoundedCornerShape 50), so its
                                        // curved ends nest inside the bar's
                                        // and never poke out. Library tabs
                                        // thumb stays 36dp — untouched.
                                        47.dp,
                                    )
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.22f)),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Compact padding (moon's 2026-11-09,
                                // low-end — was 6dp horizontal, 10dp
                                // vertical): tighter around the smaller
                                // icon/text, so the bar is ~48dp tall.
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        ) {
                        tabs.forEachIndexed { idx, tab ->
                            val selected = selectedTab == tab.screen
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        tabBounds[idx] = coords.boundsInParent()
                                    }
                                    // No ripple (moon's 2026-11-07): the
                                    // default ripple drew a second rounded
                                    // rect around the capsule. Only the
                                    // capsule is the selection now.
                                    .clickable(
                                        interactionSource = tabInteractionSource,
                                        indication = null,
                                    ) { goTab(tab.screen) }
                                    // Compact (moon's 2026-11-09, low-end —
                                    // was 12dp horizontal, 4dp vertical).
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    if (selected) tab.iconFilled else tab.iconOutlined,
                                    contentDescription = tab.label,
                                    tint = if (selected) MsColors.Accent else Color(0xFF98989F),
                                    // Compact (moon's 2026-11-09, low-end — was 24dp).
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = tab.label,
                                    // Compact (moon's 2026-11-09, low-end — was 10sp).
                                    fontSize = 9.sp,
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
