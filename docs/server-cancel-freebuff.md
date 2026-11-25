# Server downloads: need a real cancel (freebuff brief)

## Symptom (moon's 2026-11-25)

Moon played Bigg Boss on Nuvio (laptop, same WiFi) through the MoonStream
addon, it buffered for minutes, he closed it. The app's Telegram files
had grown to **1295 MB** afterwards — the whole file kept downloading
after he closed the player. The auto-cleanup freed it later (proven in
his 16:45 log: 5000 copies dropped, now 0 MB), but during and after a
short session the phone fills up.

Same root cause as the warmer leak (0.15.170): TDLib downloads the
whole file once any piece is asked for, and nothing in the app ever
tells it to stop.

## Proven facts (read from TDLib's own source, 2026-11-25)

File: `td/telegram/files/FileManager.cpp` (master).

- `FileManager::delete_file` (line 2916) only unlinks the local partial
  file (`FileLoadManager::unlink_file` is literally `unlink(path)`).
  It sends `DownloadManager::remove_file_if_finished` — only if the
  download already finished. **It never cancels the download.** The
  loader keeps running and keeps writing (to an unlinked inode).
- `FileManager::cancel_download` (line 3190) is the only way to stop
  one: it calls `do_cancel_download` (line 2164), which cancels the
  `FileDownloadManager` task and clears the node's download state.
  The public API is `TdApi.CancelDownloadFile(fileId, onlyIfPending)`.
- The docs agree: cancelDownloadFile "stops the downloading of a file.
  If a file has already been downloaded, does nothing"; deleteFile only
  "deletes a file from the TDLib file cache".

Consequence: the app's `deleteCachedFile` (DeleteFile) frees the
visible copy but does NOT stop the download. That gap affects two
places:
1. The warmer fix in 0.15.170 (StreamWarmer calls deleteCachedFile
   after warming) — it may only hide the file, not stop the download.
   Device verdict still pending.
2. The server (this brief) — the new end-of-stream drop below has the
   same gap until the cancel API exists.

## What is needed (backend, freebuff's lane)

Add to the backend (interface `MoonStreamBackend` + `TdlibMoonStreamBackend`):

```kotlin
/**
 * Stop TDLib downloading a message's file, then drop its local copy.
 * Cancel FIRST (only CancelDownloadFile stops the loader), then delete
 * (frees the partial). Returns true when nothing is left to download
 * or delete — including when the message holds no file (fileId 0).
 * False only when the fileId lookup or both calls fail.
 */
suspend fun cancelDownload(channelId: Long, messageId: Long): Boolean
```

Implementation sketch (mirror `deleteCachedFile` exactly):
- Resolve the fileId the same way: `messageFileIdCache` first, else
  `lookupFileId(channelId, messageId)` via GetMessage; cache it.
- If fileId == 0 (message holds no video/file), return true.
- Send `TdApi.CancelDownloadFile(fileId, false)` — `false` so it
  cancels even an actively-downloading file, not only a pending one.
  Ignore its result (it returns Ok even when nothing was downloading).
- Then send `TdApi.DeleteFile(fileId)` and return `res.isSuccess`.
- Log failures with AppLog, TDSTREAM tag, same style as
  deleteCachedFile ("CancelDownload failed for msg=...").

## Server side (already built, my lane — wire the call in when the API lands)

`server/ServedFiles.kt` now counts live streams:
- `beginServe(channelId, messageId)` on stream start (called from
  `serveMediaBytes` and `serveDirectBytes`, next to the existing
  `note()` call).
- `endServe(channelId, messageId)` in the `finally` of both stream
  loops — runs on normal completion AND on client disconnect (the
  write throws) AND on empty reads.
- When the count hits zero, `dropIfIdle` runs on the IO scope: it
  re-checks the count (a new stream may have started), skips files
  the in-app player holds (`TdStreamCache.isActive`, same guard as
  the sweep), then drops the copy.

When `cancelDownload` exists, change `dropIfIdle` to call it instead
of `deleteCachedFile` (one line). The log line stays:
`SERVER "client left — dropped Telegram copy for ..."`.

## Same fix for the warmer (my lane, after the API lands)

`ui/player/StreamWarmer.kt` `warm()` calls `deleteCachedFile` in its
`finally` (NonCancellable) block. Once `cancelDownload` exists, call
it there instead — same one-line swap. That closes the 0.15.170 gap.

## Constraints (same as the Clear Cache brief)

- Never delete files under `filesDir/tdlib_files` directly — only via
  the backend's TDLib calls.
- Don't touch auth/session, the library database, or the served list
  format.
- Log with AppLog (SERVER / TDSTREAM tags) so the next log file shows
  the cancel working: one line per cancelled file is enough, not one
  per chunk.
- The server's multi-client guard (active count) and the player guard
  (`TdStreamCache.isActive`) must stay — a disconnect must never pull
  a file out from under another client or the in-app player.
