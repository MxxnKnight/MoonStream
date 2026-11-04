# Changelog

All notable changes to MoonStream, newest first. Versions follow the tags
the CI build stamps onto the APK.

## Unreleased — FFmpeg audio decoders actually load now

The FFmpeg already in the app was **never loading**: `libffmpegJNI.so` had a
`NEEDED libc++_shared.so` entry, that library isn't shipped in the APK, so
`dlopen` failed and the player quietly used the hardware path — which is why
Dolby/EAC3 still failed after the first attempt. Both ABIs are relinked with
`-nostdlib++` (the shim and FFmpeg are plain C, so no runtime is needed at
all), `--no-undefined` so a missing symbol breaks the build instead of the
phone, and the build script now fails if `libc++_shared` ever comes back.

- `libffmpegJNI.so` needs only `libc`, `libm`, `libdl`, `liblog`; 16 KB-aligned
  load segments; all 9 JNI methods exported; no C++ symbols.
- 1.2 MB arm64 / 1.0 MB armv7 (slightly smaller than before).
- Docs: `docs/FFMPEG.md` gained the linker trap and the three `readelf` checks
  to run on any future rebuild.

## 0.15.101 and earlier — FFmpeg audio decoders (EAC3/AC3/DTS/TrueHD)

Phones without a Dolby/DTS hardware decoder (moon's POCO M4 Pro is one) made
the in-app player fail on EAC3 audio with
`ERROR_CODE_DECODER_INIT_FAILED / format_supported=NO_UNSUPPORTED_TYPE`. The
app now bundles a **tiny, audio-only FFmpeg** and gives it priority for those
exactly four codecs; everything else still decodes in hardware.

### Added

- `app/src/main/jniLibs/arm64-v8a/libffmpegJNI.so` and
  `.../armeabi-v7a/libffmpegJNI.so` — FFmpeg 6.1 built with **only**
  `eac3`, `ac3`, `dca` (DTS) and `truehd`; no video, no demuxers, no
  network, no AAC/MP3/Opus/FLAC. 1.3 MB arm64 / 1.1 MB armv7, so the APK
  grows by about a megabyte per ABI.
- `app/src/main/java/androidx/media3/decoder/ffmpeg/` — media3 1.4.1's
  `decoder_ffmpeg` classes, vendored (Apache-2.0, headers kept). Media3
  does **not** publish the FFmpeg extension to Maven — the coordinate in
  the old AGENT.md note does not exist — so this is the only way to ship it.
  `FfmpegLibrary` uses `@Nullable` instead of `@MonotonicNonNull` because
  checker-qual is not on the app's javac classpath.
- `PlayerScreen` builds the player with a `DefaultRenderersFactory` in
  `EXTENSION_RENDERER_MODE_PREFER` when `FfmpegLibrary.isAvailable()`, and
  `OFF` when the `.so` is missing (x86 emulators), so a missing library can
  never break playback. The path taken and the FFmpeg version are logged as
  `PLAYER` lines on the Logs screen.
- `tools/ffmpeg/build_ffmpeg.sh` + `docs/FFMPEG.md` — the exact reproducible
  build, plus the two traps hit while making it: `--disable-everything` must
  precede the `--enable-decoder` flags, and 32-bit ARM needs
  `--disable-vulkan`.

### Fixed

- `update/ApkInstaller.kt` did not compile on `main` (`kotlinx.coroutines.async
  / await` unresolved in the 4-way parallel APK download). It now uses
  `coroutineScope { async(...) }` with the imports, so `assemblePerf` builds
  again.

## 0.15.0: in-app streaming engine (no addon server needed)

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
- **Backend (`TDSTREAM`):** ids resolved through `GetMessage`, `DeleteFile`
  failures with their reason, and messages that hold no file at all.

### Fixed — Clear Cache after an app restart

Clear Cache asked Telegram to delete the copies of every file in the stream
list, but the file id was only known for files read since the app started —
so after a restart it "succeeded" without deleting anything and the folder
kept its size. `deleteCachedFile` now looks the id up with
`TdApi.GetMessage(channelId, messageId)` when it isn't cached, remembers it,
and only answers "nothing to clean" when the message genuinely has no
video/document. A lookup that fails (offline) answers false, so Clear Cache
reports a count of what it could not do instead of a fake success.
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
