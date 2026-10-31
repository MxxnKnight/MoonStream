package com.moon.moonstream.index

import com.moon.moonstream.index.db.MediaItemEntity
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Stremio-facing ID scheme, mirroring Telegram-Stremio's approach so clients
 * like Nuvio accept every library item:
 *
 * - Real IMDb IDs (`tt...`) are used as-is when the metadata matcher found one.
 * - Everything else gets a stable synthetic `tg<digits>` ID, derived
 *   deterministically from (channelId, normalizedTitle) — so rescans,
 *   backups and restores all resolve to the same ID instead of duplicating.
 *
 * The addon's manifest declares `idPrefixes: ["tt", "tg", "ms_"]`, telling
 * Stremio clients to route tg-prefixed IDs back to this addon. Meta and
 * stream routes key on these IDs (`{id}` / `{id}:{season}:{episode}`).
 */
object StremioIds {
    /** Deterministic synthetic ID for items without a real IMDb ID. */
    fun synthetic(channelId: Long, normalizedTitle: String): String {
        val seed = "$channelId:$normalizedTitle"
        val md5 = MessageDigest.getInstance("MD5")
            .digest(seed.toByteArray(Charsets.UTF_8))
        // First 4 bytes as unsigned int, like Telegram-Stremio's md5()[:8].
        val n = ((md5[0].toLong() and 0xFF) shl 24) or
            ((md5[1].toLong() and 0xFF) shl 16) or
            ((md5[2].toLong() and 0xFF) shl 8) or
            (md5[3].toLong() and 0xFF)
        return "tg${abs(n)}"
    }

    /**
     * The ID this media item is known by to Stremio clients. Prefers the
     * stored value (set at index time), falling back to the deterministic
     * synthetic ID so rows created before the scheme existed still work.
     */
    fun forMedia(m: MediaItemEntity): String {
        val real = m.imdbId?.takeIf { it.startsWith("tt") }
        if (real != null) return real
        val stored = m.stremioId?.takeIf { it.isNotBlank() }
        if (stored != null) return stored
        return synthetic(m.channelId, m.normalizedTitle)
    }

    /** The ID the indexer stores for a fresh item. */
    fun forNewItem(channelId: Long, normalizedTitle: String, imdbId: String?): String =
        imdbId?.takeIf { it.startsWith("tt") } ?: synthetic(channelId, normalizedTitle)
}
