# Changelog

All notable changes to MoonStream, newest first. Versions follow the tags
the CI build stamps onto the APK.

## Unreleased — 0.15.0: in-app streaming engine (no addon server needed)

Watching a video inside MoonStream used to require the addon server
(`MoonStreamServerService`) to be running, because the player was fed
`http://127.0.0.1:8081/stream/...`. The player now talks to Telegram itself
through the existing backend contract, so video **and** subtitles play with
the server switched off. The HTTP server is untouched and still serves
Stremio/external players exactly as before.

### Added

- **`ui/player/TdDataSource.kt`** — Media3 `DataSource` + factory for
  `tdfile://stream/<channelId>/<messageId>/<fileName>?size=<bytes>`.
  Reads are served from the local byte window; the open publishes a
  `Content-Type` derived from the file name (`video/x-matroska`,
  `video/mp4`, …) so Media3 uses the matching extractor first instead of
  sniffing FLV/FLAC/WAV/MP4 before Matroska. Small reads (Matroska index
  walks) are served from a 64 KB read-ahead buffer.
- **`ui/player/TdByteWindow.kt`** — the streaming window: fetches 8 MB
  chunks through `readFileRange` (which maps to
  `DownloadFile(offset, limit)` + `ReadFilePart`), writes them under
  `filesDir/tdstream/<channel>_<message>/`, serves Media3 from disk,
  prefetches 2 chunks ahead, and deletes chunks the playhead has passed.
  A stalled fetch fails after 90 s instead of hanging the player, and
  interrupted loads surface as `InterruptedIOException`.
- **`ui/player/LowStorageWindow.kt`** — the window policy as pure
  arithmetic: 8 MB chunks, 200 MB cap (+16 MB slack), ~2 MB kept behind
  the playhead, 2 chunks prefetched, files under 32 MB never rolled.
- **`ui/player/TdSubDataSource.kt`** — `tdsub://sub/<channel>/<message>/<file>?size=&delay=`
  loads a subtitle document through the same contract and returns it with
  the delay already applied, so subtitles no longer need the server.
  Shifted results are cached in memory per (message, delay).
- **`util/SubtitleTiming.kt`** — on-device SRT/WebVTT cue shifting, same
  rules as the server's private helper (only `-->` lines, clamped at zero,
  ASS/SSA passed through). Server copy is left alone for now.
- **`cache/TdStreamCache.kt`** — owner of `filesDir/tdstream`: measures it,
  clears it, and never clears a folder belonging to an active playback.

### Changed

- **`ui/screens/PlayerScreen.kt`** — the `ExoPlayer` is built with
  `DefaultMediaSourceFactory(MsDataSourceFactory(context))`; playback uses
  `tdfile://` whenever the DB row has a `fileSize`, otherwise the old HTTP
  URL. Subtitle URIs are built from the `SubtitleFileEntity` row (with the
  server URL as fallback). The load effect now waits for the Room row so
  playback never starts on the HTTP fallback and swaps sources mid-load.
  The open-log line reports which path is used.
- **`cache/CacheManager.kt`** — `streamCacheBytes` now reports Telegram's
  download dir **plus** the in-app window, so the Settings meter matches
  what the player actually leaves on the phone.
- **`ui/screens/SettingsScreen.kt`** — Clear Cache also wipes the player's
  chunks (and says "+ N MB player chunks"); the stream-cache explainer no
  longer claims a Telegram-module update is required.
- **`AGENT.md`** — records that this landed app-side with the contract
  untouched, so it isn't rebuilt in the TDLib lane; notes the lessons taken
  from TMPlayer (Content-Type on open, read-ahead for small reads, evict
  per chunk crossed rather than per read).

### Storage behaviour (what "low storage" means here)

- Chunks older than ~1 chunk behind the playhead are deleted as you watch
  forward; the window is capped at 200 MB oldest-first; a seek backwards
  re-downloads.
- Closing the player deletes that file's whole chunk folder. A crash/kill
  can leave one behind until the next Clear Cache.
- The window asks the module to drop Telegram's own copy
  (`deleteCachedFile`) every ~96 MB of deletions, under the download mutex.
  That contract method is still a no-op, so `filesDir/tdlib_files` keeps
  growing until it is implemented — the one open item in the TDLib lane.

### Known limitations

- Not device-tested: first-frame latency, seek behaviour and MKV playback
  over this path are compile/APK-build verified only.
- ASS/SSA subtitle delay is not applied (same restriction as the server).
- Subtitles larger than 8 MB, or rows without a size, fall back to the
  addon-server URL.
