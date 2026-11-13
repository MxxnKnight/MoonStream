package com.moon.webmock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.webmock.components.LibraryHeroCard
import com.moon.webmock.components.LimePillButton
import com.moon.webmock.components.MsOutlinePill
import com.moon.webmock.components.MsToggle
import com.moon.webmock.theme.MoonStreamTheme
import com.moon.webmock.theme.MsColors
import com.moon.webmock.theme.Redaction35
import com.moon.webmock.theme.doto

/**
 * Demo screen proving the pipeline: real MoonStream theme + components,
 * rendered by Compose Multiplatform for web. Future mockups replace this
 * content with the screen being designed — the code then pastes straight
 * into the app.
 */
@Composable
fun App() {
    MoonStreamTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MsColors.Bg),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displaySmall.doto(),
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                Text(
                    text = "3,135 titles · 19,312 streams indexed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = Redaction35,
                    ),
                    color = MsColors.TextSecondary,
                )

                LibraryHeroCard()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LimePillButton(
                        text = "Link 1 to Title",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        fontSize = 14,
                    )
                    LimePillButton(
                        text = "Custom Content",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        fontSize = 14,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MsOutlinePill(
                        text = "Fix Metadata",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        fontSize = 13,
                    )
                    MsOutlinePill(
                        text = "Add Custom Stream",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        fontSize = 13,
                    )
                }

                var toggleOn by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Auto-scan on open",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                    )
                    MsToggle(checked = toggleOn, onCheckedChange = { toggleOn = it })
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Compose Multiplatform mockup — the same code as the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}
