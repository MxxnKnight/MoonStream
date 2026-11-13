package com.moon.webmock.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.webmock.theme.MsColors
import com.moon.webmock.theme.RedactionFamily
import com.moon.webmock.theme.doto

// ---------------------------------------------------------------------------
// The Library hero card, ported from ServerScreen.kt's LibraryDashboardCard.
// Demo data instead of the Room database — the visuals are the same code.
// ---------------------------------------------------------------------------

@Composable
fun LibraryHeroCard(
    titles: Int = 3135,
    files: Int = 19312,
    onClick: () -> Unit = {},
) {
    // Count-up from 0 on first load (1100ms tween, like the app).
    val titlesAnim = remember { Animatable(0f) }
    val filesAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        titlesAnim.animateTo(titles.toFloat(), tween(1100))
        filesAnim.animateTo(files.toFloat(), tween(1100))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MsColors.GlowLight,
                        MsColors.GlowDeep,
                        MsColors.GlowDeep.copy(alpha = 0.7f),
                        MsColors.Bg,
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(1.5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.5.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MsColors.GlowDeep.copy(alpha = 0.75f),
                            MsColors.GlowDeep.copy(alpha = 0.35f),
                            MsColors.Bg,
                            MsColors.Bg,
                        ),
                        center = Offset(0.15f, 0.1f),
                        radius = 900f,
                    ),
                )
                .padding(16.dp),
        ) {
            Column {
                Row {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleMedium.doto(),
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                    Text(
                        text = "  ›",
                        style = MaterialTheme.typography.titleLarge.doto(),
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${titlesAnim.value.toInt()}",
                    style = MaterialTheme.typography.displaySmall.doto(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "titles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            text = "${filesAnim.value.toInt()}",
                            style = MaterialTheme.typography.titleMedium.doto(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "streams",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = RedactionFamily(),
                            ),
                            color = MsColors.TextSecondary,
                        )
                    }
                    Column {
                        Text(
                            text = "18.3 TB",
                            style = MaterialTheme.typography.titleMedium.doto(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "indexed",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = RedactionFamily(),
                            ),
                            color = MsColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
