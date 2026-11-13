package com.moon.webmock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moon.webmock.screens.StatsScreen
import com.moon.webmock.theme.MoonStreamTheme
import com.moon.webmock.theme.MsColors

/**
 * Current mockup: the Watch Stats screen (moon's pick 2026-11-13), the
 * app's StatsScreen ported verbatim and fed by demo data. Shown in a
 * phone-width column (420dp) centred on the page, like the app on a
 * phone. Future mockups swap the screen shown here.
 */
@Composable
fun App() {
    MoonStreamTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MsColors.Bg),
            ) {
                StatsScreen(onBack = {})
            }
        }
    }
}
