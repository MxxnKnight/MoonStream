package com.moon.moonstream.backup

import android.content.Context
import android.util.Base64
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Second cloud backup: a PRIVATE GitHub Gist on moon's own account
 * (moon's pick 2026-11-06). The same gzipped library JSON as the Telegram
 * backup, base64-encoded into one gist file, updated in place on every
 * push (the gist id is saved after the first push — never a new gist).
 *
 * Why a gist and not MongoDB: a phone app cannot talk to MongoDB safely —
 * the database password would live inside the app file. A gist only needs
 * a personal access token with the `gist` scope, which moon creates at
 * github.com/settings/tokens and pastes into Settings. The token lives
 * only on this phone, in the same prefs file as the other API keys.
 *
 * No new dependency: plain HttpURLConnection, the same pattern as
 * PosterStyle.fetchFanartPoster. Pull always fetches the file via its
 * raw_url — the inline `content` in a GET /gists response is truncated
 * for large files.
 */
object GistBackup {

    const val PREF_LAST_GIST_BACKUP_AT = "last_gist_backup_at"
    const val PREF_LAST_GIST_HASH = "last_gist_hash"

    /** One stable file inside the gist, so PATCH updates the same file. */
    private const val GIST_FILE_NAME = "MoonStream_backup.json.gz.b64"
    private const val GIST_DESCRIPTION = "MoonStream library backup"

    private fun prefs(context: Context) =
        context.getSharedPreferences("moonstream_prefs", Context.MODE_PRIVATE)

    /**
     * Push the gzipped backup JSON to the saved gist (or create the gist
     * on the first push). Returns true on success. Never throws.
     */
    suspend fun push(context: Context, gz: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val token = ScanPrefs.getGistToken(context)
            if (token.isBlank()) {
                AppLog.w("BACKUP", "Gist push: no token saved — skipping")
                return@withContext false
            }
            val content = Base64.encodeToString(gz, Base64.NO_WRAP)
            val gistId = ScanPrefs.getGistId(context)
            val (code, body) = if (gistId.isBlank()) {
                val json = JSONObject().apply {
                    put("description", GIST_DESCRIPTION)
                    put("public", false)
                    put(
                        "files",
                        JSONObject().put(
                            GIST_FILE_NAME,
                            JSONObject().put("content", content),
                        ),
                    )
                }
                http("POST", "https://api.github.com/gists", token, json.toString())
            } else {
                val json = JSONObject().apply {
                    put(
                        "files",
                        JSONObject().put(
                            GIST_FILE_NAME,
                            JSONObject().put("content", content),
                        ),
                    )
                }
                http("PATCH", "https://api.github.com/gists/$gistId", token, json.toString())
            }
            if (code !in 200..299) {
                AppLog.e("BACKUP", "Gist push failed (HTTP $code): ${body?.take(200)}")
                return@withContext false
            }
            // First push: remember the new gist id so later pushes update it.
            if (gistId.isBlank()) {
                val newId = runCatching { JSONObject(body ?: "{}").optString("id") }.getOrDefault("")
                if (newId.isNotBlank()) ScanPrefs.setGistId(context, newId)
            }
            AppLog.i("BACKUP", "Gist push: backup saved to private gist (HTTP $code)")
            true
        }

    /**
     * Download the gzipped backup bytes from the saved gist, or null when
     * there is no token, no gist, or the download fails. Never throws.
     */
    suspend fun pull(context: Context): ByteArray? =
        withContext(Dispatchers.IO) {
            val token = ScanPrefs.getGistToken(context)
            val gistId = ScanPrefs.getGistId(context)
            if (token.isBlank() || gistId.isBlank()) {
                AppLog.w("BACKUP", "Gist pull: no token or gist saved")
                return@withContext null
            }
            val (code, body) = http("GET", "https://api.github.com/gists/$gistId", token, null)
            if (code !in 200..299 || body == null) {
                AppLog.e("BACKUP", "Gist pull failed (HTTP $code)")
                return@withContext null
            }
            val files = runCatching { JSONObject(body).optJSONObject("files") }.getOrNull()
            // The named file first; fall back to any file (e.g. a hand-made gist).
            val fileObj = files?.optJSONObject(GIST_FILE_NAME)
                ?: files?.let { f ->
                    val keys = f.keys()
                    if (keys.hasNext()) f.optJSONObject(keys.next()) else null
                }
            val rawUrl = fileObj?.optString("raw_url") ?: run {
                AppLog.e("BACKUP", "Gist pull: no file found in gist $gistId")
                return@withContext null
            }
            val (rawCode, rawBody) = http("GET", rawUrl, token, null)
            if (rawCode !in 200..299 || rawBody == null) {
                AppLog.e("BACKUP", "Gist pull: raw file download failed (HTTP $rawCode)")
                return@withContext null
            }
            runCatching { Base64.decode(rawBody.trim(), Base64.DEFAULT) }.getOrNull()?.also {
                AppLog.i("BACKUP", "Gist pull: downloaded ${it.size} bytes from gist")
            }
        }

    /** Minimal JSON-over-HTTP helper. Returns (status code, response body). */
    private fun http(method: String, url: String, token: String, body: String?): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.readText()
            code to text
        } catch (e: Exception) {
            AppLog.e("BACKUP", "Gist $method $url failed: ${e.message}")
            -1 to null
        } finally {
            conn?.disconnect()
        }
    }
}
