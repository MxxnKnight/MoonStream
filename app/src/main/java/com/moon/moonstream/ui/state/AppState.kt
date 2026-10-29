package com.moon.moonstream.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Phase 1: in-memory UI state shared across screens (fake data, no backend).
 * TODO(Phase 2+): replace with real ViewModels backed by Room + TDLib.
 */
object AppUiState {
    var serverOn by mutableStateOf(false)
    var port by mutableStateOf(8080)

    val manifestLink: String
        get() = "http://192.168.1.8:$port/manifest.json"
}
