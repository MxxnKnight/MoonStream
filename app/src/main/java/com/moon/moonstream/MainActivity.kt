package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.ui.components.NeutralRippleTheme
import com.moon.moonstream.ui.screens.ChannelsScreen
import com.moon.moonstream.ui.screens.CreateScreen
import com.moon.moonstream.ui.screens.DetailScreen
import com.moon.moonstream.ui.screens.LibraryScreen
import com.moon.moonstream.ui.screens.LoginScreen
import com.moon.moonstream.ui.screens.LogsScreen
import com.moon.moonstream.ui.screens.ServerScreen
import com.moon.moonstream.ui.screens.SettingsScreen
import com.moon.moonstream.ui.screens.SplashScreen
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
    data class Detail(val titleId: String) : AppScreen
    data object Channels : AppScreen
    data object Create : AppScreen
    data object Settings : AppScreen
    data object Logs : AppScreen
}

private data class Tab(
    val screen: AppScreen,
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
)

private val tabs = listOf(
    Tab(AppScreen.Server, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(AppScreen.Library, "Library", Icons.Filled.List, Icons.Outlined.List),
    Tab(AppScreen.Channels, "Channels", Icons.Filled.Tv, Icons.Outlined.Tv),
    Tab(AppScreen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    AppUiState.init(this)
    BackendProvider.init(this)

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

    // Automatically install real backend whenever Telegram becomes Ready
    LaunchedEffect(authState) {
        if (authState is com.moon.moonstream.tdlib.AuthState.Ready) {
            val realBackend = com.moon.moonstream.backend.TdlibMoonStreamBackend(
                context = context.applicationContext,
                telegramManager = telegramManager
            )
            com.moon.moonstream.backend.BackendProvider.install(realBackend)
        }
    }
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Splash) }
    val current = backStack.last()

    // Detail belongs to the Library tab; Create belongs to Channels; Logs to Settings.
    val selectedTab = when (current) {
        is AppScreen.Detail -> AppScreen.Library
        AppScreen.Create -> AppScreen.Channels
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
    // not the old edge-to-edge bar. Hidden on Splash and Login.
    val showNav = current !is AppScreen.Splash && current !is AppScreen.Login

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
            Box(
                Modifier.fillMaxSize(),
            ) {
                when (val screen = current) {
                    AppScreen.Splash -> SplashScreen(
    onDone = {
        // Give TDLib a moment to restore the session
        // For now we still use the old loggedIn flag.
        // Later we can make it smarter.
        if (AppUiState.loggedIn) {
            // Try to install real backend if session is already Ready
            if (telegramManager.authState.value is com.moon.moonstream.tdlib.AuthState.Ready) {
                val realBackend = com.moon.moonstream.backend.TdlibMoonStreamBackend(
                    context = this@MainActivity,
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
                    AppScreen.Server -> ServerScreen()
                    AppScreen.Library ->
                        LibraryScreen(onOpenDetail = { id -> go(AppScreen.Detail(id)) })
                    is AppScreen.Detail ->
                        DetailScreen(
                            titleId = screen.titleId,
                            onBack = { backStack.removeLast() },
                        )
                    AppScreen.Channels ->
                        ChannelsScreen(onCreateEntry = { go(AppScreen.Create) })
                    AppScreen.Create -> CreateScreen()
                    AppScreen.Settings -> SettingsScreen(onOpenLogs = { go(AppScreen.Logs) })
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
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 32.dp, end = 32.dp, bottom = 22.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(MsColors.Surface)
                        .border(1.dp, MsColors.Divider, RoundedCornerShape(30.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab.screen
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (selected) MsColors.Accent else MsColors.Accent.copy(alpha = 0f),
                                )
                                .clickable { goTab(tab.screen) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (selected) tab.iconFilled else tab.iconOutlined,
                                contentDescription = tab.label,
                                tint = if (selected) MsColors.OnAccent else MsColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
