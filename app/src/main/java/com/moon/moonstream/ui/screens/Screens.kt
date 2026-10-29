package com.moon.moonstream.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Phase 1 placeholder. Real UI from the approved mockup lands here screen by screen. */
@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title\n(Phase 1 UI coming here)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable fun ServerScreen() = PlaceholderScreen("Server")
@Composable fun LibraryScreen() = PlaceholderScreen("Library")
@Composable fun DetailScreen() = PlaceholderScreen("Detail")
@Composable fun ChannelsScreen() = PlaceholderScreen("Channels")
@Composable fun CreateScreen() = PlaceholderScreen("Create Custom Content")
@Composable fun SettingsScreen() = PlaceholderScreen("Settings")
