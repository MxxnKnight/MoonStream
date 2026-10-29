package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.model.subtitleLanguages

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Create Custom Content", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = posterUrl,
            onValueChange = { posterUrl = it },
            label = { Text("Poster image URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Poster preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (posterUrl.isBlank()) "Poster preview" else "Preview: $posterUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        OutlinedTextField(value = title, onValueChange = { title = it },
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = desc, onValueChange = { desc = it },
            label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value = year, onValueChange = { year = it },
            label = { Text("Year") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("Telegram file links", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold)
        fileLinks.forEachIndexed { i, link ->
            OutlinedTextField(
                value = link,
                onValueChange = { fileLinks[i] = it },
                label = { Text("File link ${i + 1} (t.me/…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = { fileLinks.add("") }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add another file link")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isSeries) "Series" else "Movie",
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = isSeries, onCheckedChange = { isSeries = it })
        }
        if (isSeries) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = season, onValueChange = { season = it },
                    label = { Text("Season") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = episode, onValueChange = { episode = it },
                    label = { Text("Episode") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }

        Text("Subtitle file links", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold)
        subtitleLinks.forEachIndexed { i, link ->
            OutlinedTextField(
                value = link,
                onValueChange = { subtitleLinks[i] = it },
                label = { Text("Subtitle link ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LanguagePicker(
                selected = subtitleLangs.getOrElse(i) { subtitleLanguages.first() },
                onSelect = {
                    while (subtitleLangs.size <= i) subtitleLangs.add(subtitleLanguages.first())
                    subtitleLangs[i] = it
                },
            )
        }
        TextButton(onClick = {
            subtitleLinks.add("")
            subtitleLangs.add(subtitleLanguages.first())
        }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add another subtitle")
        }

        Button(
            onClick = {
                Toast.makeText(context, "Entry created (fake)", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create Entry")
        }
    }
}

@Composable
private fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Language: $selected", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick language",
                tint = MaterialTheme.colorScheme.secondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            subtitleLanguages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang) },
                    onClick = { onSelect(lang); expanded = false },
                )
            }
        }
    }
}
