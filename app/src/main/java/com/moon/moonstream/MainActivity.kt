package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.moon.moonstream.ui.components.NeutralRippleTheme
import com.moon.moonstream.ui.screens.ChannelsScreen
import com.moon.moonstream.ui.screens.CreateScreen
import com.moon.moonstream.ui.screens.DetailScreen
import com.moon.moonstream.ui.screens.LibraryScreen
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
        AppUiState.init(this) // load persisted accent + AMOLED before first frame
        setContent {
            MoonStreamTheme {
                // Neutral ripple everywhere: no blue tap highlight (mockup requirement).
                CompositionLocalProvider(LocalRippleTheme provides NeutralRippleTheme) {
                    MoonStreamApp()
                }
            }
        }
    }
}

@Composable
fun MoonStreamApp() {
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Splash) }
    val current = backStack.last()

    // Detail belongs to the Library tab; Create belongs to Channels; Logs to Settings.
    val selectedTab = when (current) {
        is AppScreen.Detail -> AppScreen.Library
        AppScreen.Create -> AppScreen.Channels
        AppScreen.Logs -> AppScreen.Settings
        else -> current
    }

    BackHandler(enabled = backStack.size > 1 && current !is AppScreen.Splash) {
        backStack.removeLast()
    }

    fun go(screen: AppScreen) {
        backStack.add(screen)
    }

    fun goTab(screen: AppScreen) {
        backStack.clear()
        backStack.add(screen)
    }

    Scaffold(
        containerColor = MsColors.Bg,
        bottomBar = {
            // No bottom bar on the splash screen.
            if (current !is AppScreen.Splash) {
                NavigationBar(
                    containerColor = MsColors.Surface,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab.screen
                        NavigationBarItem(
                            // Filled icon when selected, outline when not — the
                            // icon is always visible. Selected icon is dark on
                            // a SOLID accent pill (v0.3.0 bug: lime icon on a
                            // faint lime pill read as an empty pill).
                            icon = {
                                Icon(
                                    if (selected) tab.iconFilled else tab.iconOutlined,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            selected = selected,
                            onClick = { goTab(tab.screen) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MsColors.OnAccent,
                                selectedTextColor = MsColors.Accent,
                                unselectedIconColor = MsColors.TextSecondary,
                                unselectedTextColor = MsColors.TextSecondary,
                                indicatorColor = MsColors.Accent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (val screen = current) {
                AppScreen.Splash -> SplashScreen(onDone = { goTab(AppScreen.Server) })
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
    }
}
