# Clear Cache can't clear Telegram files — brief for freebuff

Moon's phone, 2026-11-20 evening: MoonStream 0.15.164, Telegram files stuck at
935 MB. Tapping Clear Cache (Settings → Streaming & Storage) does not reduce
it. The auto-cleanup janitor also frees 0 MB. The library itself is fine
(restored from Gist, 5.8 MB).

## What is proven (from the app's code and moon's logs)

- `deleteCachedFile` (TdlibMoonStreamBackend) is sound: the Logs show every
  GetMessage lookup succeeding and DeleteFile returning success.
- The janitor's own log line proves the empty-delete problem: "auto-cleanup:
  stream cache 935 MB over 500 MB — freed 0 MB (chunks 0 MB, 200 Telegram
  copies dropped)". DeleteFile succeeds trivially on files that were never
  downloaded, so the "dropped" count is meaningless.
- Both Clear Cache (SettingsScreen, CacheStorageBlock) and the janitor
  (StorageJanitor) only iterate files in the app's own library database
  (`streamDao().getAll()`). If the bytes on disk belong to files the
  database doesn't track, neither can ever free them — and that is the
  leading explanation for the stuck 935 MB.
- The breakdown measures `filesDir/tdlib_files` recursively (real disk size
  via st_blocks), so the 935 MB is real bytes on disk, not sparse
  overcounting.

## What to build

Use TDLib's own storage management instead of the per-file DeleteFile loop:

1. Add a backend method (new API on TdlibMoonStreamBackend, e.g.
   `clearTelegramCache()` / `getTelegramStorageStats()`) that:
   - calls `TdApi.GetStorageStatistics` first and logs what TDLib itself
     reports (total size, by chat / by file type) with the AppLog CACHE tag,
     so the numbers show in Settings → Logs;
   - then calls `TdApi.OptimizeStorage` to delete TDLib's downloaded files
     (whole cache, or all files older than the caller's threshold), and
     logs how much was freed.
2. Wire the Settings Clear Cache button to call it (the button lives in
   SettingsScreen's CacheStorageBlock; the current per-file loop can go —
   a single TDLib call replaces ~2000 GetMessage lookups, so it also
   finishes in seconds instead of minutes).

## Rules (do not break these)

- NEVER delete files under `filesDir/tdlib_files` directly — that corrupts
  TDLib's state. Only delete through TDLib APIs (DeleteFile / OptimizeStorage).
- Do not touch login, auth, or the session. Do not touch the library database.
- Log every step with AppLog so moon can see it in Settings → Logs.
- The player-close per-file deletes (PlayerScreen) stay as they are — they
  work for files the player just played. This task is only about the
  Clear Cache path and the stuck 935 MB.

## How to verify

On a phone with a large tdlib_files (moon's is 935 MB): tap Clear Cache,
stay on the screen, and confirm Telegram files drops toward 0 MB and the
Logs show the OptimizeStorage result. The library, watch progress, and
login must all survive untouched.
