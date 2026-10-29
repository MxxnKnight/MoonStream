package com.moon.moonstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moon.moonstream.ui.screens.ChannelsScreen
import com.moon.moonstream.ui.screens.CreateScreen
import com.moon.moonstream.ui.screens.DetailScreen
import com.moon.moonstream.ui.screens.LibraryScreen
import com.moon.moonstream.ui.screens.ServerScreen
import com.moon.moonstream.ui.screens.SettingsScreen
import com.moon.moonstream.ui.theme.MoonStreamTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("server", "Home", Icons.Filled.Home),
    Tab("library", "Library", Icons.Filled.List),
    Tab("channels", "Channels", Icons.Filled.Add),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoonStreamTheme {
                MoonStreamApp()
            }
        }
    }
}

@Composable
fun MoonStreamApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "server",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("server") { ServerScreen() }
            composable("library") { LibraryScreen() }
            composable("detail") { DetailScreen() }
            composable("channels") { ChannelsScreen() }
            composable("create") { CreateScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
