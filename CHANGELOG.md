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

### Added — follow-up: language preference + rate limiting

- **`ui/state/LanguagePrefs.kt`** and a new **Settings → Languages** group:
  a first and second choice for subtitle and audio language (defaults Mal
  then Eng). The player opens the wanted subtitle by itself, uses the second
  language only when the title has nothing in the first, and leaves foreign
  tracks in the manual menu. Subtitle tracks now carry their real language
  tag (`mal`, `eng`, …) instead of `und`, which is what makes auto-selection
  possible at all — before this, subtitles always started Off.
- **Rate limiting in the chunk fetcher** (`TdByteWindow`): the backend reports
  a throttled request as "no bytes", so empty replies are now treated as
  backpressure — a 200 ms minimum gap between requests, exponential backoff
  with jitter (0.4 s → 8 s), a per-run prefetch that stops on its first
  failure, and a blocked reader that always outranks prefetch. The give-up
  threshold rose to 6 empty replies, which spends roughly 20 s waiting —
  enough for a Telegram flood-wait to pass without showing an error.

### Added — logs detailed enough to diagnose storage

- **Engine (`TDLOCAL`):** window open (file size, chunk count, window cap),
  every chunk stored with KB + elapsed ms + request count, every eviction
  (chunks deleted, KB freed, window size, MB since the last Telegram purge),
  every purge attempt and its result, empty replies with the backoff chosen,
  and window close.
- **Deletes (`CACHE`):** leftover chunk folders removed by Clear Cache, one
  file's chunks deleted on close, app-cache MB freed, and a full Clear Cache
  summary — including the stream cache measured before and after, so it is
  visible whether Telegram's own folder actually shrank.
- **Backend (`TDSTREAM`):** `DeleteFile` failures, and the case where no
  fileId is known for a message (after a restart nothing is known, so Clear
  Cache cannot delete Telegram's copy yet).
- **Player (`PLAYER`):** close position and percentage, whether the file was
  watched to the end, and whether Telegram's copy was deleted or kept for
  resume.
- **`APP` boundary lines:** one at launch and one when you press Clear, so it
  is always clear where the current log starts. Clear now also empties the
  on-disk mirror (`filesDir/logs/app.log`) — before this it only emptied the
  screen and the old lines came back on the next launch.

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

- Not device-tested: first-frame latency, seek behaviour, subtitle
  auto-selection and MKV playback over this path are compile/APK-build
  verified only.
- Audio falls back to the file's default track when neither preference is
  present; the backend still cannot report a Telegram flood-wait as such, so
  the player backs off blindly rather than for the exact wait time.
- ASS/SSA subtitle delay is not applied (same restriction as the server).
- Subtitles larger than 8 MB, or rows without a size, fall back to the
  addon-server URL.
