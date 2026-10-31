package com.moon.moonstream.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.moon.moonstream.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 1: in-memory UI state shared across screens (fake data, no backend).
 * TODO(Phase 2+): replace with real ViewModels backed by Room + TDLib.
 */

/**
 * Accent choices for Settings → Appearance. Persisted in SharedPreferences.
 *
 * Each accent carries its full color family:
 * - color / onColor: the UI accent (buttons, switches, progress) + text on it.
 * - glowLight / glowDeep: the signature gradient family — drives the server
 *   card border + inner glow and the splash screen ambient animation, so the
 *   whole app's "aura" follows the accent.
 * - logoRes: per-accent transparent splash logo (null = default app logo).
 */
enum class MsAccent(
    val label: String,
    val color: Color,
    val onColor: Color,
    val glowLight: Color,
    val glowDeep: Color,
    val logoRes: Int? = null,
) {
    LIME(
        "Lime", Color(0xFF7DD956), Color(0xFF0E1116),
        glowLight = Color(0xFFB8F59B), glowDeep = Color(0xFF2E7A1E),
        logoRes = R.drawable.logo_lime,
    ),
    COSMOS(
        "Cosmos Blue", Color(0xFF6A9BFF), Color(0xFF0E1116),
        glowLight = Color(0xFFA8C6FF), glowDeep = Color(0xFF2A4A9B),
    ),
    EMBER(
        "Ember Orange", Color(0xFFFF8A3D), Color(0xFF0E1116),
        glowLight = Color(0xFFFFB37D), glowDeep = Color(0xFF9B4A1A),
    ),
    VIOLET(
        "Violet", Color(0xFFB388FF), Color(0xFF0E1116),
        glowLight = Color(0xFFD3B8FF), glowDeep = Color(0xFF5E35B1),
    ),
    MOON_BLUE(
        "Moon Blue", Color(0xFF3B82F6), Color(0xFF0E1116),
        glowLight = Color(0xFF7DB4FF), glowDeep = Color(0xFF0B3FA0),
        logoRes = R.drawable.logo_moon_blue,
    ),
    MOON_GOLD(
        "Moon Gold", Color(0xFFFFAA33), Color(0xFF0E1116),
        glowLight = Color(0xFFFFD27D), glowDeep = Color(0xFF8A5A18),
        logoRes = R.drawable.logo_moon_gold,
    ),
    MOON_SILVER(
        "Moon Silver", Color(0xFFC9D4E3), Color(0xFF0E1116),
        glowLight = Color(0xFFFFFFFF), glowDeep = Color(0xFF6B7686),
        logoRes = R.drawable.logo_moon_silver,
    ),
}

data class LogLine(val time: String, val tag: String, val message: String)

object AppUiState {
    var serverOn by mutableStateOf(false)
    var port by mutableStateOf(8080)
    /** Separate port for raw file/subtitle bytes (PLAN.md Phase 4). */
    var streamPort by mutableStateOf(8081)

    // Appearance (persisted)
    var accent by mutableStateOf(MsAccent.LIME)
    var amoled by mutableStateOf(false)

    // Telegram login state is driven by the real backend's authState;
    // loggedIn here mirrors it for the splash router.
    var loggedIn by mutableStateOf(false)

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
            com.moon.moonstream.server.MoonStreamServerService.start(context, port, streamPort)
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
        if (logLines.isEmpty()) {
            seedFakeLogs()
        }
    }

    fun setAccent(a: MsAccent, context: Context) {
        accent = a
        // commit() (synchronous) — the theme must be on disk before this
        // returns, so a force-stop right after picking can never lose it.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("accent", a.name).commit()
    }

    fun setAmoled(on: Boolean, context: Context) {
        amoled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("amoled", on).commit()
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


