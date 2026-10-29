package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.moon.moonstream.ui.screens.ServerScreen
import com.moon.moonstream.ui.screens.SettingsScreen
import com.moon.moonstream.ui.theme.MoonStreamTheme

/**
 * Phase 1: hand-rolled navigation (no navigation-compose in the offline mirror).
 * TODO(Phase 2): switch to navigation-compose when CI builds with network access.
 */
sealed interface AppScreen {
    data object Server : AppScreen
    data object Library : AppScreen
    data class Detail(val titleId: String) : AppScreen
    data object Channels : AppScreen
    data object Create : AppScreen
    data object Settings : AppScreen
}

private data class Tab(val screen: AppScreen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(AppScreen.Server, "Home", Icons.Filled.Home),
    Tab(AppScreen.Library, "Library", Icons.Filled.List),
    Tab(AppScreen.Channels, "Channels", Icons.Filled.Add),
    Tab(AppScreen.Settings, "Settings", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Server) }
    val current = backStack.last()

    // Detail belongs to the Library tab for bottom-bar highlighting.
    val selectedTab = when (current) {
        is AppScreen.Detail -> AppScreen.Library
        else -> current
    }

    BackHandler(enabled = backStack.size > 1) {
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
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab == tab.screen,
                        onClick = { goTab(tab.screen) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (val screen = current) {
                AppScreen.Server -> ServerScreen()
                AppScreen.Library ->
                    LibraryScreen(onOpenDetail = { id -> go(AppScreen.Detail(id)) })
                is AppScreen.Detail ->
                    DetailScreen(
                        titleId = screen.titleId,
                        onBack = { backStack.removeLast() },
                    )
                AppScreen.Channels -> ChannelsScreen()
                AppScreen.Create -> CreateScreen()
                AppScreen.Settings -> SettingsScreen()
            }
        }
    }
}
