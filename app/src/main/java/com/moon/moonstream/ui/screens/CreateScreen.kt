package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsDropdown
import com.moon.moonstream.ui.components.MsSectionLabel
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.model.subtitleLanguages
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii

/**
 * Phase 1: create custom content form with fake data.
 * TODO(Phase 3): parse t.me links, fetch file metadata, persist via Library.
 */
@Composable
fun CreateScreen() {
    val context = LocalContext.current
    var posterUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var isSeries by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf("") }
    var episode by remember { mutableStateOf("") }
    val fileLinks = remember { mutableStateListOf("") }
    val subtitleLinks = remember { mutableStateListOf("") }
    val subtitleLangs = remember { mutableStateListOf(subtitleLanguages.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Create Custom Content",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )

        MsTextField(
            value = posterUrl,
            onValueChange = { posterUrl = it },
            hint = "Poster image URL",
            modifier = Modifier.fillMaxWidth(),
        )
        // Poster preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(MsRadii.Field))
                .background(MsColors.SurfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (posterUrl.isBlank()) "Poster preview" else "Preview: $posterUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(12.dp),
            )
        }

        MsTextField(value = title, onValueChange = { title = it }, hint = "Title",
            modifier = Modifier.fillMaxWidth())
        MsTextField(value = desc, onValueChange = { desc = it }, hint = "Description",
            modifier = Modifier.fillMaxWidth(), minLines = 3, singleLine = false)
        MsTextField(value = year, onValueChange = { year = it }, hint = "Year",
            modifier = Modifier.fillMaxWidth())

        MsSectionLabel("Telegram file links")
        fileLinks.forEachIndexed { i, link ->
            MsTextField(
                value = link,
                onValueChange = { fileLinks[i] = it },
                hint = "File link ${i + 1} (t.me/…)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LimeTextButton(text = "+ Add another file link", onClick = { fileLinks.add("") })

        // Movie / Series segmented control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SegmentOption(
                label = "Movie",
                selected = !isSeries,
                onClick = { isSeries = false },
                modifier = Modifier.weight(1f),
            )
            SegmentOption(
                label = "Series",
                selected = isSeries,
                onClick = { isSeries = true },
                modifier = Modifier.weight(1f),
            )
        }
        if (isSeries) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MsTextField(value = season, onValueChange = { season = it }, hint = "Season",
                    modifier = Modifier.weight(1f))
                MsTextField(value = episode, onValueChange = { episode = it }, hint = "Episode",
                    modifier = Modifier.weight(1f))
            }
        }

        MsSectionLabel("Subtitle file links")
        subtitleLinks.forEachIndexed { i, link ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MsTextField(
                    value = link,
                    onValueChange = { subtitleLinks[i] = it },
                    hint = "Subtitle link ${i + 1}",
                    modifier = Modifier.fillMaxWidth(),
                )
                MsDropdown(
                    selected = subtitleLangs.getOrElse(i) { subtitleLanguages.first() },
                    options = subtitleLanguages,
                    onSelect = {
                        while (subtitleLangs.size <= i) subtitleLangs.add(subtitleLanguages.first())
                        subtitleLangs[i] = it
                    },
                    label = "Language",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LimeTextButton(
            text = "+ Add another subtitle",
            onClick = {
                subtitleLinks.add("")
                subtitleLangs.add(subtitleLanguages.first())
            },
        )

        Spacer(Modifier.height(4.dp))
        LimePillButton(
            text = "Create Entry",
            onClick = {
                Toast.makeText(context, "Entry created (fake)", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Room for the floating pill: last content scrolls fully into view.
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) MsColors.Accent else MsColors.SurfaceAlt)
            .then(
                if (!selected) Modifier.border(
                    1.dp,
                    MsColors.TextSecondary.copy(alpha = 0.4f),
                    CircleShape,
                ) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MsColors.OnAccent else MsColors.TextSecondary,
        )
    }
}
