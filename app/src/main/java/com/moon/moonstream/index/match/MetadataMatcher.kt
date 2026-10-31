package com.moon.moonstream.index.match

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Phase 3 metadata matcher — written fresh for MoonStream.
 *
 * Resolution order (learned from NoxMedia / TeleStremio patterns):
 *  1. TMDB search — only when the user pasted an API key in Settings.
 *  2. Kitsu — anime channels, no key needed.
 *  3. Cinemeta — free IMDb-id lookup, no key needed (only when an IMDb id
 *     is already known, e.g. from TMDB's external ids).
 *  4. Nothing matched → null, and the caller still indexes the entry with
 *     its parsed title/year (title-only entry).
 *
 * Uses only HttpURLConnection + org.json — no extra dependencies.
 * Never throws for network trouble: any failure returns null.
 */
data class EnrichedMeta(
    val title: String,
    val year: String?,
    val overview: String?,
    val posterUrl: String?,
    val imdbId: String?,
    val tmdbId: Int?,
)

object MetadataMatcher {
    private const val TAG = "MetadataMatcher"
    private const val UA = "MoonStream/1.0"
    private const val TIMEOUT_MS = 8_000
    private const val TMDB_IMG = "https://image.tmdb.org/t/p/w500"

    /**
     * Best-effort metadata for a parsed title.
     * @param type "movie", "series" or "anime"
     */
    fun match(
        title: String,
        year: String?,
        type: String,
        isAnime: Boolean,
        tmdbKey: String,
    ): EnrichedMeta? {
        if (title.isBlank()) return null
        return try {
            // Anime first: Kitsu needs no key and knows anime best.
            if (isAnime || type == "anime") {
                tryKitsu(title)?.let { return it }
            }
            if (tmdbKey.isNotBlank()) {
                tryTmdb(title, year, type, tmdbKey)?.let { return it }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "match failed for '$title': ${e.message}")
            null
        }
    }

    // ---------- TMDB ----------

    private fun tryTmdb(
        title: String,
        year: String?,
        type: String,
        key: String,
    ): EnrichedMeta? {
        val kind = if (type == "movie") "movie" else "tv"
        val q = URLEncoder.encode(title, "UTF-8")
        val yearParam = if (!year.isNullOrBlank()) "&year=$year" else ""
        val url =
            "https://api.themoviedb.org/3/search/$kind?api_key=$key&query=$q$yearParam&language=en-US"
        val root = getJson(url) ?: return null
        val results = root.optJSONArray("results") ?: return null

        var best: JSONObject? = null
        var bestScore = 0.55 // similarity floor
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val name = obj.optString(if (kind == "movie") "title" else "name")
            val score = similarity(title, name)
            // Prefer exact-year matches when we know the year.
            val adjusted = if (!year.isNullOrBlank()) {
                val d = obj.optString(if (kind == "movie") "release_date" else "first_air_date")
                if (d.startsWith(year)) score + 0.15 else score
            } else score
            if (adjusted > bestScore) {
                bestScore = adjusted
                best = obj
            }
        }
        best ?: return null

        val tmdbId = best.optInt("id", -1).takeIf { it > 0 } ?: return null
        val name = best.optString(if (kind == "movie") "title" else "name")
        val date = best.optString(if (kind == "movie") "release_date" else "first_air_date")
        val poster = best.optString("poster_path").takeIf { it.isNotBlank() }
            ?.let { "$TMDB_IMG$it" }

        // Resolve the IMDb id for the Cinemeta/Stremio side of the world.
        val imdbId = getJson(
            "https://api.themoviedb.org/3/$kind/$tmdbId/external_ids?api_key=$key",
        )?.optString("imdb_id")?.takeIf { it.startsWith("tt") }

        return EnrichedMeta(
            title = name.ifBlank { title },
            year = Regex("""^(\d{4})""").find(date)?.groupValues?.get(1) ?: year,
            overview = best.optString("overview").takeIf { it.isNotBlank() },
            posterUrl = poster,
            imdbId = imdbId,
            tmdbId = tmdbId,
        )
    }

    // ---------- Kitsu (anime, no key) ----------

    /**
     * Search-as-you-type results for the Metadata Fix sheet. Returns up to
     * [limit] candidates with posters; never throws.
     */
    fun searchTitles(
        query: String,
        type: String,
        isAnime: Boolean,
        tmdbKey: String,
        limit: Int = 8,
    ): List<EnrichedMeta> {
        if (query.isBlank()) return emptyList()
        return try {
            if (isAnime) searchKitsu(query, limit)
            else if (tmdbKey.isNotBlank()) searchTmdb(query, type, tmdbKey, limit)
            else emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun searchTmdb(
        title: String,
        type: String,
        key: String,
        limit: Int,
    ): List<EnrichedMeta> {
        val kind = if (type == "movie") "movie" else "tv"
        val q = URLEncoder.encode(title, "UTF-8")
        val root =
            getJson("https://api.themoviedb.org/3/search/$kind?api_key=$key&query=$q&language=en-US")
                ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until minOf(results.length(), limit)).mapNotNull { i ->
            val obj = results.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString(if (kind == "movie") "title" else "name")
            if (name.isBlank()) return@mapNotNull null
            val date = obj.optString(if (kind == "movie") "release_date" else "first_air_date")
            val poster = obj.optString("poster_path").takeIf { it.isNotBlank() }
                ?.let { "$TMDB_IMG$it" }
            EnrichedMeta(
                title = name,
                year = Regex("""^(\d{4})""").find(date)?.groupValues?.get(1),
                overview = obj.optString("overview").takeIf { it.isNotBlank() },
                posterUrl = poster,
                imdbId = null, // resolved on Apply via match(), not per-row
                tmdbId = obj.optInt("id", -1).takeIf { it > 0 },
            )
        }
    }

    private fun searchKitsu(query: String, limit: Int): List<EnrichedMeta> {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = getJson(
            "https://kitsu.io/api/edge/anime?filter[text]=$q&page[limit]=$limit",
            mapOf("Accept" to "application/vnd.api+json"),
        ) ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until minOf(data.length(), limit)).mapNotNull { i ->
            val attr = data.optJSONObject(i)?.optJSONObject("attributes")
                ?: return@mapNotNull null
            val name = attr.optString("canonicalTitle").ifBlank { return@mapNotNull null }
            val poster = attr.optJSONObject("posterImage")?.optString("large")
                ?.takeIf { it.isNotBlank() }
            EnrichedMeta(
                title = name,
                year = Regex("""^(\d{4})""").find(attr.optString("startDate"))
                    ?.groupValues?.get(1),
                overview = attr.optString("synopsis").takeIf { it.isNotBlank() },
                posterUrl = poster,
                imdbId = null,
                tmdbId = null,
            )
        }
    }

    private fun tryKitsu(title: String): EnrichedMeta? {
        val q = URLEncoder.encode(title, "UTF-8")
        val url = "https://kitsu.io/api/edge/anime?filter[text]=$q&page[limit]=5"
        val root = getJson(url, mapOf("Accept" to "application/vnd.api+json")) ?: return null
        val data = root.optJSONArray("data") ?: return null

        var best: JSONObject? = null
        var bestScore = 0.55
        var bestTitle = ""
        for (i in 0 until data.length()) {
            val attr = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val candidates = listOfNotNull(
                attr.optString("canonicalTitle"),
                attr.optJSONObject("titles")?.optString("en"),
                attr.optJSONObject("titles")?.optString("en_us"),
                attr.optJSONObject("titles")?.optString("en_jp"),
            ).filter { it.isNotBlank() }
            for (c in candidates) {
                val s = similarity(title, c)
                if (s > bestScore) {
                    bestScore = s
                    best = attr
                    bestTitle = attr.optString("canonicalTitle").ifBlank { c }
                }
            }
        }
        best ?: return null

        val startDate = best.optString("startDate")
        val poster = best.optJSONObject("posterImage")?.optString("large")
            ?.takeIf { it.isNotBlank() }
        return EnrichedMeta(
            title = bestTitle.ifBlank { title },
            year = Regex("""^(\d{4})""").find(startDate)?.groupValues?.get(1),
            overview = best.optString("synopsis").takeIf { it.isNotBlank() },
            posterUrl = poster,
            imdbId = null,
            tmdbId = null,
        )
    }

    // ---------- Cinemeta (free IMDb-id enrichment, no key) ----------

    /** Fills poster/overview from Cinemeta when an IMDb id is known. */
    fun enrichFromCinemeta(meta: EnrichedMeta, type: String): EnrichedMeta {
        val imdb = meta.imdbId ?: return meta
        val kind = if (type == "movie") "movie" else "series"
        return try {
            val root = getJson("https://v3-cinemeta.strem.io/meta/$kind/$imdb.json")
                ?.optJSONObject("meta") ?: return meta
            meta.copy(
                posterUrl = meta.posterUrl
                    ?: root.optString("poster").takeIf { it.isNotBlank() },
                overview = meta.overview
                    ?: root.optString("description").takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            Log.d(TAG, "cinemeta enrich failed: ${e.message}")
            meta
        }
    }

    // ---------- helpers ----------

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) null else JSONObject(body)
        } catch (e: Exception) {
            Log.d(TAG, "GET failed $url: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 0.0–1.0 title similarity: half token-overlap, half edit distance.
     * Forgiving of word order ("Avengers Endgame" vs "Endgame Avengers").
     */
    fun similarity(a: String, b: String): Double {
        val x = normalizeTitle(a)
        val y = normalizeTitle(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val xt = x.split(" ").toSet()
        val yt = y.split(" ").toSet()
        val jaccard = xt.intersect(yt).size.toDouble() / xt.union(yt).size
        val lev = 1.0 - levenshtein(x, y).toDouble() / maxOf(x.length, y.length)
        return jaccard * 0.5 + lev * 0.5
    }

    private fun normalizeTitle(t: String): String =
        t.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
