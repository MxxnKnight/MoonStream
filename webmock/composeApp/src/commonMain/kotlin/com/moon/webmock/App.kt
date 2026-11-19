package com.moon.webmock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.webmock.screens.PlayerLooks
import com.moon.webmock.screens.PlayerScreen
import com.moon.webmock.screens.StatsScreen
import com.moon.webmock.theme.MoonStreamTheme
import com.moon.webmock.theme.MsColors
import com.moon.webmock.theme.doto

/**
 * Mockup switcher (moon's 2026-11-14): Stats stays (he wants to improve
 * it too), and the new Player redesign sits beside it. Stats shows in a
 * phone-width column (420dp) like the app on a phone; the Player is
 * landscape, so it takes the full page width below the switcher bar.
 */
private enum class MockScreen(val label: String) {
    STATS("Stats"),
    PLAYER("Player"),
}

@Composable
fun App() {
    MoonStreamTheme {
        var screen by remember { mutableStateOf(MockScreen.PLAYER) }
        var look by remember { mutableStateOf(PlayerLooks.current) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Switcher bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Mockups",
                    style = MaterialTheme.typography.titleMedium.doto(),
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .background(MsColors.SurfaceAlt, RoundedCornerShape(50))
                        .padding(4.dp),
                ) {
                    MockScreen.entries.forEach { s ->
                        val selected = screen == s
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MsColors.OnAccent else MsColors.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MsColors.Accent else Color.Transparent,
                                )
                                .clickable { screen = s }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            // Look picker (player only): same buttons, different face.
            if (screen == MockScreen.PLAYER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Look",
                        style = MaterialTheme.typography.bodyMedium.doto(),
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .background(MsColors.SurfaceAlt, RoundedCornerShape(50))
                            .padding(4.dp),
                    ) {
                        PlayerLooks.all.forEach { option ->
                            val selected = look.id == option.id
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MsColors.OnAccent else MsColors.TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) MsColors.Accent else Color.Transparent,
                                    )
                                    .clickable { look = option }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (screen) {
                    MockScreen.STATS -> Box(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(MsColors.Bg),
                    ) {
                        StatsScreen(onBack = {})
                    }
                    MockScreen.PLAYER -> PlayerScreen(look)
                }
            }
        }
    }
}
