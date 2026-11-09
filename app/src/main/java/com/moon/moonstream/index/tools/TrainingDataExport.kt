// TEMPORARY — ONE BUILD ONLY (moon's 2026-11-09).
// A per-channel training-data export for the assistant: the raw Telegram
// filename + the caption + what the app filed it as, one JSON object per
// line. Moon runs it once per channel from the channel's 3-dot menu, sends
// the file, and then THIS FILE AND ITS MENU ENTRY MUST BE REMOVED in the
// next commit (moon's order: "after the next build you should remove it
// and its operations"). Do not extend it, do not make it permanent.
//
// Filenames come from the app database (stream_files / unmatched_files —
// stored raw, never cleaned). Captions are NOT stored anywhere, so they are
// fetched from Telegram this once by paging the channel (same paging as
// the scanner: newest first, 100 per page, 350ms between pages) and only
// keeping the caption for messages the database already has. Nothing is
// re-indexed and nothing is written back to the database.
package com.moon.moonstream.index.tools

import com.moon.moonstream.backend.MoonStreamBackend
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.util.AppLog
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object TrainingDataExport {

    private const val PAGE_SIZE = 100
    // One-time manual export, not a background scan: a short pause is
    // enough (the scanner's 350ms is for polite background work).
    private const val PAGE_DELAY_MS = 100L
    // A single page fetch must never hang the whole export: TDLib reads
    // from its local cache, so 20s is generous. On timeout the export
    // finishes with the captions found so far instead of sitting forever.
    private const val PAGE_TIMEOUT_MS = 20_000L

    /**
     * Writes one JSON object per line for every indexed file in [channelId]
     * and returns the file. Fields per line: messageId, fileName (raw),
     * caption ("" when Telegram has none / the message is gone), matched,
     * title ("" when unmatched), season, episode, episodeEnd, quality,
     * fileSize. The caller shares the returned file.
     *
     * [onProgress] is called with (captions found, captions needed) after
     * each page, so the caller can show the export is actually moving.
     */
    suspend fun exportChannel(
        db: MsDatabase,
        backend: MoonStreamBackend,
        channelId: Long,
        channelTitle: String,
        cacheDir: File,
        onProgress: (found: Int, needed: Int) -> Unit = { _, _ -> },
    ): File {
        val streams = db.streamDao().getAllByChannel(channelId)
        val unmatched = db.unmatchedDao().getAllByChannel(channelId)
        val needed = (streams.map { it.messageId } + unmatched.map { it.messageId }).toSet()
        val titles = db.mediaDao().getAllByChannel(channelId).associate { it.id to it.title }

        // Captions live only on Telegram: page the channel newest-first and
        // keep the caption for messages the database already has. Stop as
        // soon as every needed caption is found, so a channel whose files
        // are all recent does not walk its whole history.
        val captions = mutableMapOf<Long, String>()
        if (needed.isNotEmpty()) {
            // The oldest message the database has: once a page's last
            // (oldest) message is below it, nothing older can be needed,
            // so stop. Without this, a channel with a deleted message
            // never reaches "every caption found" and pages its whole
            // history, looking stuck (moon's big channel, ~8000 files).
            val oldestNeeded = needed.minOrNull() ?: Long.MAX_VALUE
            var fromId = Long.MAX_VALUE
            var pages = 0
            while (captions.size < needed.size) {
                val page = withTimeoutOrNull(PAGE_TIMEOUT_MS) {
                    try {
                        backend.fetchMessages(channelId, fromId, PAGE_SIZE)
                    } catch (e: Exception) {
                        AppLog.e("TRAIN", "fetchMessages ch=$channelId failed: ${e.message}")
                        null
                    }
                }
                if (page == null) {
                    // Timed out or failed: finish with what we have
                    // instead of hanging forever (moon's 2026-11-09:
                    // "only toast appears, then nothing").
                    AppLog.e("TRAIN", "export ch=$channelId: page fetch timed out after ${pages} pages, finishing with ${captions.size}/${needed.size} captions")
                    break
                }
                if (page.isEmpty()) break
                for (msg in page) {
                    if (msg.id in needed) captions[msg.id] = msg.caption
                }
                fromId = page.last().id
                pages++
                onProgress(captions.size, needed.size)
                if (captions.size >= needed.size) break
                if (fromId < oldestNeeded) break
                delay(PAGE_DELAY_MS)
            }
        }

        val out = StringBuilder()
        for (f in streams) {
            val obj = JSONObject()
            obj.put("messageId", f.messageId)
            obj.put("fileName", f.fileName)
            obj.put("caption", captions[f.messageId] ?: "")
            obj.put("matched", true)
            obj.put("title", titles[f.mediaId] ?: "")
            if (f.season != null) obj.put("season", f.season)
            if (f.episode != null) obj.put("episode", f.episode)
            if (f.episodeEnd != null) obj.put("episodeEnd", f.episodeEnd)
            obj.put("quality", f.quality)
            obj.put("fileSize", f.fileSize)
            out.append(obj.toString()).append('\n')
        }
        for (u in unmatched) {
            val obj = JSONObject()
            obj.put("messageId", u.messageId)
            obj.put("fileName", u.fileName)
            obj.put("caption", captions[u.messageId] ?: "")
            obj.put("matched", false)
            obj.put("title", "")
            obj.put("quality", "")
            obj.put("fileSize", u.fileSize)
            out.append(obj.toString()).append('\n')
        }

        val safe = channelTitle.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
        val file = File(cacheDir, "training-$safe-$channelId.jsonl")
        file.writeText(out.toString(), Charsets.UTF_8)
        AppLog.i(
            "TRAIN",
            "export ch=$channelId: ${streams.size} matched + ${unmatched.size} unmatched, " +
                "${captions.size}/${needed.size} captions -> ${file.name}",
        )
        return file
    }
}
