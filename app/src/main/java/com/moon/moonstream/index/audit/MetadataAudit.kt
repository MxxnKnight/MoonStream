package com.moon.moonstream.index.audit

import android.content.Context
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.match.MetadataMatcher
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.delay

/**
 * Finds badly-mapped library entries and moves them to unmatched for
 * bulk fixing. moon: "what if i wanna do wrong title for thousands of
 * files!.. Its better to get those bad meta data files in unmatched!"
 *
 * Two checks (no API calls for the first, API for the second):
 * 1. Structural: a "movie" entry whose files have episode numbers is
 *    always wrong — movies don't have episodes. (e.g. Psycho-Pass E11
 *    mapped to "Psycho-Pass: The Movie".)
 * 2. Confidence: re-run the matcher on the filename; if the top result
 *    differs from the current title with high confidence (>= 0.75), or
 *    the match confidence is low (< 0.75), flag it.
 */
object MetadataAudit {

    data class Report(
        val checked: Int,
        val moved: Int,
        val details: List<String>,
    )

    /**
     * @param structuralOnly when true, only runs check 1 (no network).
     * @param onProgress called with (done, total) for UI updates.
     */
    suspend fun run(
        context: Context,
        structuralOnly: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        val db = MsDatabase.get(context)
        val tmdbKey = ScanPrefs.getTmdbKey(context)
        val items = db.mediaDao().listVisible()
        val details = mutableListOf<String>()
        var moved = 0
        var checked = 0

        for ((idx, media) in items.withIndex()) {
            onProgress(idx + 1, items.size)
            val files = db.streamDao().listByMedia(media.id)
            if (files.isEmpty()) continue

            for (f in files) {
                checked++
                val parsed = try {
                    FilenameParser.parse(f.fileName, media.type == "anime")
                } catch (e: Exception) { continue }

                var badReason: String? = null

                // Check 1 (structural, no API): movie with episode numbers.
                if (media.type == "movie" && parsed.episode != null) {
                    badReason = "movie has episode E${parsed.episode}"
                }

                // Check 2 (API): re-match and compare.
                if (badReason == null && !structuralOnly) {
                    try {
                        val type = if (media.type == "anime") "anime" else parsed.type
                        val m = MetadataMatcher.match(
                            parsed.title, parsed.year, type,
                            media.type == "anime", tmdbKey,
                            hasEpisode = parsed.episode != null,
                        )
                        if (m == null || m.confidence < 0.75) {
                            badReason = "low confidence (${m?.let { "%.2f".format(it.confidence) } ?: "no match"})"
                        } else if (!titlesMatch(m.title, media.title)) {
                            badReason = "re-match gives '${m.title}' (was '${media.title}')"
                        }
                    } catch (e: Exception) {
                        AppLog.w("AUDIT", "re-match failed for '${f.fileName}': ${e.message}")
                    }
                    // Be nice to the APIs.
                    delay(300)
                }

                if (badReason != null) {
                    // Move the file to unmatched.
                    db.unmatchedDao().insert(
                        UnmatchedFileEntity(
                            channelId = f.channelId,
                            messageId = f.messageId,
                            fileName = f.fileName,
                            fileSize = f.fileSize,
                        )
                    )
                    db.streamDao().deleteById(f.id)
                    moved++
                    val line = "'${f.fileName.take(40)}' ← '${media.title.take(30)}' ($badReason)"
                    details.add(line)
                    AppLog.w("AUDIT", "Moved to unmatched: $line")
                }
            }

            // If the media item has no files left, delete it.
            if (db.streamDao().listByMedia(media.id).isEmpty()) {
                db.mediaDao().deleteById(media.id)
                AppLog.i("AUDIT", "Deleted empty entry '${media.title}'")
            }
        }

        return Report(checked, moved, details)
    }

    private fun titlesMatch(a: String, b: String): Boolean {
        val na = a.lowercase().replace(Regex("[^a-z0-9]"), "")
        val nb = b.lowercase().replace(Regex("[^a-z0-9]"), "")
        return na == nb || na.contains(nb) && nb.length > 5 || nb.contains(na) && na.length > 5
    }
}
