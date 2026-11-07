package com.moon.moonstream.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * SkipDB — free, open, crowdsourced intro/recap/outro/preview timestamps.
 * https://skipdb.tv — reading needs no API key (an X-API-Key header is
 * only for submitting/editing).
 *
 * Fallback for non-anime content when IntroDB has no data for an episode.
 * Keyed by IMDb id only (there is no TMDB-id parameter). Times are in
 * milliseconds — converted to seconds here to match IntroDb.Segments.
 */
object SkipDb {
    private const val TAG = "SkipDb"
    private const val BASE = "https://api.skipdb.tv/api/segments"

    data class Segments(
        val introStartSec: Double?,
        val introEndSec: Double?,
        val outroStartSec: Double?,
        val outroEndSec: Double?,
    )

    /**
     * Fetches intro/outro segments.
     * @param imdbId e.g. "tt0903747"
     * @param season Season number (null for movies — omitted from the query)
     * @param episode Episode number (null for movies — omitted from the query)
     * @param isMovie If true, season/episode are omitted (movies are keyed
     *                by IMDb id alone).
     */
    suspend fun getSegments(
        imdbId: String,
        season: Int?,
        episode: Int?,
        isMovie: Boolean = false,
    ): Segments? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("$BASE?imdb_id=$imdbId")
                if (!isMovie) {
                    if (season != null) append("&season=$season")
                    if (episode != null) append("&episode=$episode")
                }
            }
            AppLog.i(TAG, "Fetching: $url")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "MoonStream")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode == 404) {
                    AppLog.i(TAG, "SkipDB: no data for $imdbId S$season E$episode")
                    return@withContext null
                }
                if (conn.responseCode != 200) {
                    AppLog.w(TAG, "SkipDB HTTP ${conn.responseCode}")
                    return@withContext null
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val segments = json.optJSONObject("segments") ?: return@withContext null
                val segs = Segments(
                    introStartSec = segmentStartSec(segments.optJSONObject("intro")),
                    introEndSec = segmentEndSec(segments.optJSONObject("intro")),
                    outroStartSec = segmentStartSec(segments.optJSONObject("outro")),
                    outroEndSec = segmentEndSec(segments.optJSONObject("outro")),
                )
                AppLog.i(TAG, "SkipDB: intro=${segs.introStartSec}-${segs.introEndSec}")
                segs
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "SkipDB failed: ${e.message}")
            null
        }
    }

    /**
     * One segment's start in seconds, or null when it is absent.
     * SkipDB marks a segment unusable as `match == "out-of-range"`,
     * as (0, 0) (confirmed absent), or with end <= start.
     */
    private fun segmentStartSec(seg: JSONObject?): Double? {
        if (seg == null) return null
        if (seg.optString("match") == "out-of-range") return null
        val startMs = seg.optLong("start_ms", -1)
        val endMs = seg.optLong("end_ms", -1)
        if (startMs < 0 || endMs < 0) return null
        if (startMs == 0L && endMs == 0L) return null
        if (endMs <= startMs) return null
        return startMs / 1000.0
    }

    private fun segmentEndSec(seg: JSONObject?): Double? {
        if (seg == null) return null
        if (seg.optString("match") == "out-of-range") return null
        val startMs = seg.optLong("start_ms", -1)
        val endMs = seg.optLong("end_ms", -1)
        if (startMs < 0 || endMs < 0) return null
        if (startMs == 0L && endMs == 0L) return null
        if (endMs <= startMs) return null
        return endMs / 1000.0
    }
}
