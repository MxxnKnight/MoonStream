package com.moon.moonstream.backup

import android.content.Context
import android.util.Base64
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Second cloud backup: a PRIVATE GitHub Gist on moon's own account
 * (moon's pick 2026-11-06). The same gzipped library JSON as the Telegram
 * backup, base64-encoded into one gist file, updated in place on every
 * push (the gist id is saved after the first push — never a new gist).
 * If the saved id is lost (fresh install / data wiped), push and pull
 * find the existing gist by listing the account's gists instead of
 * creating a duplicate or giving up (moon's bug 2026-11-20).
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

    /** Result of a pull: the bytes, or why the pull failed. */
    sealed class PullResult {
        data class Success(val bytes: ByteArray) : PullResult()
        object NoToken : PullResult()
        object NoGist : PullResult()
        object AuthFailed : PullResult()
        object NetworkError : PullResult()
    }

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
            // No id saved (fresh install / data wiped): find the existing
            // backup gist on the account instead of creating a duplicate.
            var gistId = ScanPrefs.getGistId(context)
            if (gistId.isBlank()) {
                val found = discoverGistId(token)
                if (found != null) {
                    gistId = found
                    ScanPrefs.setGistId(context, found)
                    AppLog.i("BACKUP", "Gist push: found existing backup gist after wipe")
                }
            }
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
            // (Discovery already saved the id when it found one, so this
            // only fires for a genuinely new gist.)
            if (ScanPrefs.getGistId(context).isBlank()) {
                val newId = runCatching { JSONObject(body ?: "{}").optString("id") }.getOrDefault("")
                if (newId.isNotBlank()) ScanPrefs.setGistId(context, newId)
            }
            AppLog.i("BACKUP", "Gist push: backup saved to private gist (HTTP $code)")
            true
        }

    /**
     * Download the gzipped backup bytes from the gist. Reports WHY it
     * failed instead of just null, so Settings can toast the real cause.
     * When no gist id is saved (fresh install / data wiped) the backup
     * gist is found by listing the account's gists — a wipe no longer
     * makes restore impossible. Never throws.
     */
    suspend fun pull(context: Context): PullResult =
        withContext(Dispatchers.IO) {
            val token = ScanPrefs.getGistToken(context)
            if (token.isBlank()) {
                AppLog.w("BACKUP", "Gist pull: no token saved")
                return@withContext PullResult.NoToken
            }
            var gistId = ScanPrefs.getGistId(context)
            if (gistId.isBlank()) {
                val found = discoverGistId(token)
                if (found == null) {
                    AppLog.w("BACKUP", "Gist pull: no MoonStream backup gist on this account")
                    return@withContext PullResult.NoGist
                }
                gistId = found
                ScanPrefs.setGistId(context, found)
            }
            // Fetch the gist; one retry if the saved id 404s (gist deleted,
            // or the token belongs to a different account now).
            var code = -1
            var body: String? = null
            for (attempt in 0..1) {
                val res = http("GET", "https://api.github.com/gists/$gistId", token, null)
                code = res.first
                body = res.second
                if (code == 401 || code == 403) {
                    AppLog.e("BACKUP", "Gist pull: token rejected (HTTP $code)")
                    return@withContext PullResult.AuthFailed
                }
                if (code == 404 && attempt == 0) {
                    val found = discoverGistId(token)
                    if (found == null) {
                        AppLog.w("BACKUP", "Gist pull: saved gist gone, no backup gist on this account")
                        return@withContext PullResult.NoGist
                    }
                    gistId = found
                    ScanPrefs.setGistId(context, found)
                    continue
                }
                break
            }
            if (code !in 200..299 || body == null) {
                AppLog.e("BACKUP", "Gist pull failed (HTTP $code)")
                return@withContext PullResult.NetworkError
            }
            val files = runCatching { JSONObject(body).optJSONObject("files") }.getOrNull()
            // The named file first; fall back to any file (e.g. a hand-made gist).
            val fileObj = files?.optJSONObject(GIST_FILE_NAME)
                ?: files?.let { f ->
                    val keys = f.keys()
                    if (keys.hasNext()) f.optJSONObject(keys.next()) else null
                }
            val rawUrl = fileObj?.optString("raw_url")?.takeIf { it.isNotBlank() } ?: run {
                AppLog.e("BACKUP", "Gist pull: no file found in gist $gistId")
                return@withContext PullResult.NoGist
            }
            val (rawCode, rawBody) = http("GET", rawUrl, token, null)
            if (rawCode == 401 || rawCode == 403) {
                AppLog.e("BACKUP", "Gist pull: raw download rejected (HTTP $rawCode)")
                return@withContext PullResult.AuthFailed
            }
            if (rawCode !in 200..299 || rawBody == null) {
                AppLog.e("BACKUP", "Gist pull: raw file download failed (HTTP $rawCode)")
                return@withContext PullResult.NetworkError
            }
            val bytes = runCatching { Base64.decode(rawBody.trim(), Base64.DEFAULT) }.getOrNull()
            if (bytes == null) {
                AppLog.e("BACKUP", "Gist pull: backup file is not valid base64")
                return@withContext PullResult.NetworkError
            }
            AppLog.i("BACKUP", "Gist pull: downloaded ${bytes.size} bytes from gist")
            PullResult.Success(bytes)
        }

    /**
     * Find the MoonStream backup gist by listing the user's gists.
     * Matches the backup file name, falling back to the description;
     * when several match, the most recently updated wins (ISO timestamps
     * compare correctly as strings). Up to 3 pages of 100 gists, stops
     * early on a short page. Returns the gist id, or null when there is
     * no matching gist or the list call fails.
     */
    private fun discoverGistId(token: String): String? {
        var bestId: String? = null
        var bestUpdated = ""
        for (page in 1..3) {
            val (code, body) = http(
                "GET",
                "https://api.github.com/gists?per_page=100&page=$page",
                token,
                null,
            )
            if (code !in 200..299 || body == null) {
                AppLog.w("BACKUP", "Gist discovery: list failed (HTTP $code)")
                return null
            }
            val gists = runCatching { JSONArray(body) }.getOrNull() ?: return null
            for (i in 0 until gists.length()) {
                val gist = gists.optJSONObject(i) ?: continue
                val id = gist.optString("id")
                if (id.isBlank()) continue
                val files = gist.optJSONObject("files")
                val isOurs = (files != null && files.has(GIST_FILE_NAME)) ||
                    gist.optString("description") == GIST_DESCRIPTION
                if (!isOurs) continue
                val updated = gist.optString("updated_at")
                if (bestId == null || updated > bestUpdated) {
                    bestId = id
                    bestUpdated = updated
                }
            }
            if (gists.length() < 100) break
        }
        if (bestId != null) AppLog.i("BACKUP", "Gist discovery: found backup gist $bestId")
        return bestId
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
