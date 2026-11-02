package com.moon.moonstream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.moon.moonstream.ui.theme.MsColors

/**
 * A settings row: label + gray outline ⓘ that expands a plain-words
 * explanation inline, with the actual control right-aligned.
 */
@Composable
fun SettingRow(
    title: String,
    info: String,
    control: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                InfoDot(onClick = { expanded = !expanded })
            }
            Spacer(Modifier.width(12.dp))
            control()
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = false }
                    .padding(start = 2.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Collapsible: collapsed groups don't compose their content at all,
    // which is also what fixes the settings scroll lag. State persists
    // across app restarts.
    var expanded by remember {
        mutableStateOf(
            com.moon.moonstream.ui.state.SettingsPrefs.getBool(
                context, "grp_$title", false
            )
        )
    }
    // Card-style section (moon's pick 2026-11-02): the whole section is a
    // div-like card — header and content live inside it, expanding in place.
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        com.moon.moonstream.ui.state.SettingsPrefs.setBool(
                            context, "grp_$title", expanded
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MsColors.Accent,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "grpChevron"
                ).value.let { rot ->
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MsColors.TextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = rot },
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically() +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() +
                    androidx.compose.animation.fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MsColors.TextSecondary.copy(alpha = 0.15f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
