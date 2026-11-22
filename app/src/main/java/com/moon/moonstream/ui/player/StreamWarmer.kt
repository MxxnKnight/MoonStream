package com.moon.moonstream.ui.player

import android.content.Context
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Instant play (moon's 2026-11-19): while the user is looking at a
 * title's Detail page, quietly download the first and last chunk of the
 * file they are most likely to play, so ExoPlayer's first reads hit
 * local disk instead of waiting on Telegram for the first frame.
 *
 * Reuses the player's own download path: a throwaway [TdByteWindow]
 * writes the same chunk files into the same folder the player will
 * read, so warmed chunks are picked up with no extra work (the window
 * skips chunks already on disk). Silent and best effort — any failure
 * just means playback fetches the chunk itself, as today.
 *
 * Warmed folders are swept the moment the Detail page closes without
 * playing ([TdStreamCache.clearWarmed]); player close is the backstop.
 * So bytes for a title never played do not linger. The player's own
 * close rule (both caches cleared on every close) is untouched: a
 * warmed file that IS played has its chunks cleared by the normal
 * close handler.
 *
 * The warmer also drops TDLib's own copy when done (moon's
 * 2026-11-23): the chunk-0 request starts a full background download
 * inside TDLib that nothing cancels — the app asked for ~6 MB and
 * ~700 MB arrived in an hour, with zero playback. The warmed chunks
 * live in the tdstream cache, so instant play still works; dropping
 * TDLib's copy only stops Telegram downloading the rest.
 */
object StreamWarmer {

    /**
     * Warms [file]'s head and tail chunks (at most ~2 MB). Cancellable:
     * leaving the Detail page cancels the in-flight download. Never
     * throws except on cancellation. Always drops TDLib's copy when
     * done — even on cancellation — so the background download the
     * chunk-0 request started stops instead of running to the whole
     * file (moon's 2026-11-23).
     */
    suspend fun warm(context: Context, file: StreamFileEntity) {
        if (file.fileSize <= 0L) return
        withContext(Dispatchers.IO) {
            val window = TdByteWindow(context, file.channelId, file.messageId, file.fileSize)
            try {
                // Mark first: even a half-finished warm leaves chunks
                // behind, and the sweep must find them.
                TdStreamCache.markWarmed(file.channelId, file.messageId)
                window.warmEdges()
                AppLog.i("TDLOCAL", "warmed '${file.fileName}' for instant play")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.i("TDLOCAL", "warm-up skipped for '${file.fileName}': ${e.message}")
            } finally {
                window.release()
                // NonCancellable: a cancelled warm must still stop the
                // download — a finally block in a cancelled coroutine
                // can't suspend otherwise. A failed delete must never
                // break the warm, so it runs inside runCatching.
                withContext(NonCancellable) {
                    val dropped = runCatching {
                        BackendProvider.backend.deleteCachedFile(
                            file.channelId,
                            file.messageId,
                        )
                    }.getOrDefault(false)
                    AppLog.d(
                        "TDLOCAL",
                        "warmer dropped TDLib copy for '${file.fileName}': $dropped",
                    )
                }
            }
        }
    }
}
