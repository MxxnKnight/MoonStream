package com.moon.moonstream.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * IntroDB — free community database of TV/movie intro/outro timestamps.
 * https://introdb.app — reads need no API key.
 *
 * Used for non-anime content (anime uses AniSkip). We already store IMDb
 * IDs in the DB, so this plugs straight in.
 */
object IntroDb {
    private const val TAG = "IntroDb"
    private const val BASE = "https://api.introdb.app"

    data class Segments(
        val introStartSec: Double?,
        val introEndSec: Double?,
        val outroStartSec: Double?,
        val outroEndSec: Double?,
    )

    /**
     * Fetches intro/outro segments.
     * @param imdbId e.g. "tt0944947"
     * @param season Season number (null for movies)
     * @param episode Episode number (null for movies)
     */
    suspend fun getSegments(
        imdbId: String,
        season: Int?,
        episode: Int?,
    ): Segments? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$BASE/segments?imdb_id=$imdbId")
                if (season != null) append("&season=$season")
                if (episode != null) append("&episode=$episode")
            }
            AppLog.i(TAG, "Fetching: $url")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "MoonStream")
            }
            try {
                if (conn.responseCode == 404) {
                    AppLog.i(TAG, "IntroDB: no data for $imdbId S$season E$episode")
                    return@withContext null
                }
                if (conn.responseCode != 200) {
                    AppLog.w(TAG, "IntroDB HTTP ${conn.responseCode}")
                    return@withContext null
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val intro = json.optJSONObject("intro")
                val outro = json.optJSONObject("outro")
                val segs = Segments(
                    introStartSec = intro?.optDouble("start_sec")?.takeIf { !it.isNaN() },
                    introEndSec = intro?.optDouble("end_sec")?.takeIf { !it.isNaN() },
                    outroStartSec = outro?.optDouble("start_sec")?.takeIf { !it.isNaN() },
                    outroEndSec = outro?.optDouble("end_sec")?.takeIf { !it.isNaN() },
                )
                AppLog.i(TAG, "IntroDB: intro=${segs.introStartSec}-${segs.introEndSec}")
                segs
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "IntroDB failed: ${e.message}")
            null
        }
    }
}
