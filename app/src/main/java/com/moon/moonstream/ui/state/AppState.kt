package com.moon.moonstream.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.moon.moonstream.ui.model.fakeTitles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 1: in-memory UI state shared across screens (fake data, no backend).
 * TODO(Phase 2+): replace with real ViewModels backed by Room + TDLib.
 */

/** Accent choices for Settings → Appearance. Persisted in SharedPreferences. */
enum class MsAccent(val label: String, val color: Color, val onColor: Color) {
    LIME("Lime", Color(0xFF7DD956), Color(0xFF0E1116)),
    COSMOS("Cosmos Blue", Color(0xFF6A9BFF), Color(0xFF0E1116)),
    EMBER("Ember Orange", Color(0xFFFF8A3D), Color(0xFF0E1116)),
    VIOLET("Violet", Color(0xFFB388FF), Color(0xFF0E1116)),
}

data class LogLine(val time: String, val tag: String, val message: String)

object AppUiState {
    var serverOn by mutableStateOf(false)
    var port by mutableStateOf(8080)

    // Appearance (persisted)
    var accent by mutableStateOf(MsAccent.LIME)
    var amoled by mutableStateOf(false)

    // Telegram login (fake for now; TODO Phase 2: real TDLib session)
    var loggedIn by mutableStateOf(false)

    // Library hide/unhide (fake id list acting as a set; TODO Phase 3: Room column)
    val hiddenIds = mutableStateListOf<String>()

    // Cache meter (fake; TODO Phase 4: real cache stats)
    var cacheUsedMb by mutableStateOf(312)
    var cacheMaxMb by mutableStateOf(2048)

    // Logs (fake lines; TODO Phase 4: real ring buffer from the server)
    val logLines = mutableStateListOf<LogLine>()

    var currentIp by mutableStateOf("127.0.0.1")

    val manifestLink: String
        get() = "http://$currentIp:$port/manifest.json"

    fun toggleServer(context: Context) {
        if (!serverOn) {
            val ip = com.moon.moonstream.server.MoonStreamServer.getLocalIpAddress()
            currentIp = ip
            com.moon.moonstream.server.MoonStreamServerService.start(context, port)
        } else {
            com.moon.moonstream.server.MoonStreamServerService.stop(context)
        }
    }

    private const val PREFS = "moonstream_prefs"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        accent = MsAccent.values().find { it.name == prefs.getString("accent", null) }
            ?: MsAccent.LIME
        amoled = prefs.getBoolean("amoled", false)
        loggedIn = prefs.getBoolean("logged_in", false)
        currentIp = com.moon.moonstream.server.MoonStreamServer.getLocalIpAddress()
        if (hiddenIds.isEmpty()) {
            hiddenIds.addAll(fallbackHiddenSeed())
        }
        if (logLines.isEmpty()) {
            seedFakeLogs()
        }
    }

    fun setAccent(a: MsAccent, context: Context) {
        accent = a
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("accent", a.name).apply()
    }

    fun setAmoled(on: Boolean, context: Context) {
        amoled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("amoled", on).apply()
    }

    fun setLoggedIn(context: Context) {
        loggedIn = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("logged_in", true).apply()
    }

    fun setLoggedOut(context: Context) {
        loggedIn = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("logged_in", false).apply()
    }

    fun addLog(tag: String, message: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logLines.add(LogLine(t, tag, message))
    }

    fun clearLogs() {
        logLines.clear()
    }

    private fun fallbackHiddenSeed(): List<String> =
        fakeTitles.filter { it.hidden }.map { it.id }

    private fun seedFakeLogs() {
        val seeds = listOf(
            "APP" to "MoonStream 0.5.0 started (fake)",
            "SCAN" to "Auto-scan scheduled every 30s (fake)",
            "MATCH" to "Indexed 7,142 files across 4 channels (fake)",
            "SERVER" to "Addon server idle — flip the switch to start (fake)",
            "CACHE" to "Cache usage 312 MB of 2048 MB (fake)",
        )
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val now = System.currentTimeMillis()
        seeds.forEachIndexed { i, (tag, msg) ->
            logLines.add(LogLine(fmt.format(Date(now - (seeds.size - i) * 4000L)), tag, msg))
        }
    }
}


