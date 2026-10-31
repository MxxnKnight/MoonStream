package com.moon.moonstream.update

import android.os.Build
import com.moon.moonstream.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Over-the-air updates via GitHub Releases. The repo is public, so
 * `api.github.com/repos/MxxnKnight/MoonStream/releases/latest` is readable
 * with no token. The CI workflow attaches per-ABI APKs to each release;
 * we pick the one matching this device's ABI.
 */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
)

object UpdateChecker {
    private const val LATEST_URL =
        "https://api.github.com/repos/MxxnKnight/MoonStream/releases/latest"

    /** Null = already on the latest version. Throws on network/API errors. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = JSONObject(get(LATEST_URL))
        if (json.optBoolean("draft", false)) return@withContext null
        val tag = json.optString("tag_name", "")
        val version = tag.trimStart('v', 'V')
        if (version.isBlank() || !isNewer(version, BuildConfig.VERSION_NAME)) {
            return@withContext null
        }
        val apk = pickApkAsset(json) ?: return@withContext null
        UpdateInfo(
            version = version,
            tag = tag,
            notes = json.optString("body", ""),
            apkUrl = apk.optString("browser_download_url", ""),
            apkName = apk.optString("name", "MoonStream-$version.apk"),
        ).takeIf { it.apkUrl.isNotBlank() }
    }

    /** True when [latest] is a newer semantic version than [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Pick the APK asset matching this device's primary ABI. */
    private fun pickApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        // Prefer an exact ABI match, then any APK.
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true) && name.contains(abi, ignoreCase = true)) {
                return a
            }
        }
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) return a
        }
        return null
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API requires a User-Agent.
            setRequestProperty("User-Agent", "MoonStream/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw Exception("GitHub API returned $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
