# AGENT.md — MoonStream AI briefing

Read this file before making any change to this repo. It tells you what the
app is, who owns which part, and what is already done. Follow it.

## Bug checklist — READ THIS FIRST

**`docs/BUG_CHECKLIST.md`** is the live bug tracker. moon reports bugs there;
check items off only when the fix is pushed. Read it before starting work so
you don't re-fix what's done or miss what's open.

## What MoonStream is

A personal Telegram media library for Android (Kotlin + Jetpack Compose).
The user connects their own Telegram account; the app scans their channels
for movies and TV shows, builds a personal library, and serves it to the
Stremio video player as a local streaming addon running on the phone.

Package: `com.moon.moonstream`. Repo: `MxxnKnight/MoonStream` (public — matters for OTA update checks).

## The ownership split — IMPORTANT, do not cross it

| Area | Owner | Notes |
|---|---|---|
| `app/src/main/java/com/moon/moonstream/tdlib/` (TdlibClient, TelegramManager, …) | moon + his AI | The TDLib login/auth implementation. **Nobody else touches these files.** |
| `app/src/main/jniLibs/` | moon + his AI | Native TDLib `.so` files. Don't touch. |
| `LoginScreen.kt` auth flow, `MainActivity.kt` auth wiring, `backend/TdlibMoonStreamBackend.kt` | moon + his AI | The wiring that plugs the real backend in. Don't touch. |
| `backend/MoonStreamBackend.kt` (the contract), `backend/FakeMoonStreamBackend.kt`, `backend/BackendProvider.kt` | Assistant (Muse) | The contract is frozen — changing it needs both sides to agree. |
| All screens (`ui/`), design system, docs | Assistant (Muse) | |
| `server/` (MoonStreamServer, foreground service) | moon's AI built the first version; assistant reviews/hardens | |

If you are moon's AI: stay in your rows. If you are the assistant: stay in
yours. When in doubt, update this file instead of guessing.

## Architecture

- Screens NEVER talk to Telegram directly. They talk only to the
  `MoonStreamBackend` contract (6 promises: authState, listChannels,
  fetchMessages, readFileRange, getMe, logout).
- The real implementation (`TdlibMoonStreamBackend`) is installed once at
  startup via `BackendProvider.install(...)` when TDLib reports Ready —
  including on cold start when a saved session restores silently.
- `FakeMoonStreamBackend` is kept for previews/tests only.

## Current status (2026-11-02) — v0.15.0

- Phase 1 — UI shell: DONE.
- Phase 2 — Telegram login (TDLib): DONE by moon.
- Phase 3 — Scan/index/library: DONE (caption-first parser, TMDB/Cinemeta/Kitsu
  matching with ≥0.75 confidence rule, episode grouping, library tools,
  manual set-episode, Fix Metadata).
- Phase 4 — In-phone Stremio server: DONE (ports 8080/8081, Range 206/416,
  foreground service).
- Phase 5 — Streaming extras: DONE. In-app player (ExoPlayer/media3) with
  TMPlayer-style UI, resume positions, subtitle delay/appearance/position,
  audio switch, gestures, lock, screenshot, NextUp — plus the custom
  `tdfile://` streaming engine (see below) so the player works with the
  addon server OFF. OTA updates, Tier 1 backup, Clear Cache all live.

## Build

- CI: `.github/workflows/build.yml` is MANUAL ONLY (Actions tab →
  "Run workflow"). It uploads one artifact per ABI, 14-day retention.
- ABI splits are on (`arm64-v8a`, `armeabi-v7a`, `x86_64`, + universal).
  The POCO M4 Pro takes the **arm64-v8a** APK (~40 MB, not the 100+ MB
  universal one).
- Debug builds for testing. The release keystore is gitignored and stays
  with the assistant — CI does not do release signing.
- Bump `versionCode`/`versionName` in `app/build.gradle` for every release.

## Conventions

- ALWAYS bundle the Inter font and force it theme-wide — the user's
  handwriting system font hijacks unlabeled UIs otherwise.
- Design system (iOS rebuild, merged 2026-11-04 — supersedes the old lime/Material3 look):
  bg `#0E1116`, iOS grouped cards `#1C1C1E` (14dp), iOS blue `#0A84FF` actions,
  iOS switches (51×31, green `#30D158`), iOS segmented tabs (gray),
  iOS spinner loader, iOS action sheets, iOS bottom sheets (grabber),
  iOS tab bar (icon + label). Generous spacing, professional polish — never cramped.
- **UI plan: `docs/UI_PLAN.md`** (2026-11-03). It predates the 2026-11-04 iOS
  restyle, so ignore its lime/Material3 screen styling — but its core finding
  still stands: there is no shape/size scale (`MsRadii` used by 3 of 12 UI
  files, 15 literal corner radii, `MaterialTheme.shapes` unset). Reusable
  rules: radii from one scale, spacing from 4/8/12/16/20/24/32, control
  heights 48 / 44 / 36, no drop shadows, gradients derived from `Bg`/
  `Surface` so the AMOLED toggle keeps working.
- No `Canvas` / `Stroke` / `nativeCanvas` in UI code (the offline build
  mirror's Compose AAR lacks them).
- Never deliver APKs as chat attachments (the user's phone saves them as
  `.zip`). Direct download link with a real `.apk` filename + expiry.
- Never commit secrets: no API ID/hash, no session strings, no keystore
  passwords. Ever.
- Commits: short imperative summary; push after committing (token is in
  the git credential store).

## iOS restyle (2026-11-04) — component reference

moon picked a full Cupertino/iOS look for the Android app. All components
live in `ui/components/MsControls.kt` unless noted:

| Component | iOS spec | Notes |
|---|---|---|
| `IosSpinner` | 12-blade fading Apple spinner | Replaces all loaders (player buffering). Custom — no M3 equivalent. |
| `MsToggle` | iOS switch 51×31dp, `#30D158` when on | Drop-in replacement, same signature. |
| `MsSegmentedControl` (LibraryScreen) | iOS gray `#1C1C1E` track, `#636366` thumb | Was lime — now authentic iOS gray. |
| `LimePillButton` | iOS blue `#0A84FF`, 14dp rounded | Name is legacy; style is iOS now. |
| `MsOutlinePill` | iOS gray `#1C1C1E` bg, blue text | Was outlined lime. |
| `IosGroup` + `IosNavRow` | iOS grouped settings, thin dividers, `›` chevron | Used in SettingsScreen main list. |
| `IosBottomSheet` | Grabber handle, 16dp top radius, `#1C1C1E` | Wraps M3 ModalBottomSheet; needs `@OptIn(ExperimentalMaterial3Api::class)`. |
| `IosActionSheet` + `IosSheetButton` | Bottom action sheet, destructive red option | For confirm dialogs (e.g. remove-channel). |
| `MsToast` (MsToast.kt) | iOS-style dark pill `#1C1C1E` | Already pill-shaped; refined. |
| `MsCard` | iOS grouped card 14dp, `#1C1C1E` | Was `#161B24`. |
| Progress bar (SettingsScreen) | iOS blue `#0A84FF`, 8dp, rounded | NO wavy progress (moon: "Not wavy!"). M3 1.1.1 takes Float, not lambda. |
| Bottom tab bar (MainActivity) | iOS icon + label, blue selected | Keeps floating pill + swipe-to-switch. |

Material3 1.3.x is BROKEN upstream (POM references non-existent
`material3-android:1.6.1`) — stay on 1.1.1. The Expressive restyle was
abandoned in favor of this iOS rebuild per moon's pick.


## Cloud backup (Tier 2) — DONE (verified on main, 2026-11-03)

**Status: implemented, nothing left to build.** The contract methods
(`uploadBackup`, `findLatestBackup`, `downloadBackup`, `BackupRef`) exist in
`backend/MoonStreamBackend.kt`, `TdlibMoonStreamBackend` implements them via
Saved Messages (`CreatePrivateChat(me)` → `SendMessage` with a document and
the `#MoonStreamBackup` caption → `SearchChatMessages` newest-first →
`DownloadFile`), `FakeMoonStreamBackend` stubs them, and the app side is
complete: `backup/BackupManager.kt` (build/gzip/parse/restore, 6 Room tables
+ settings), Settings → "Push to / Pull from Saved Messages", plus an
automatic push after each scan when `auto_backup_sync` and
`backup_to_telegram` are on (`ScanService`). It just needs a device test. The
spec below is kept as the record of what those methods must do.

Local file backup (Tier 1) is already in the app. This is the spec for the
cloud tier: push/pull the backup through the user's Telegram Saved Messages.
It needs new methods on the backend contract — this section IS the
both-sides agreement the contract freeze requires (moon approved it
2026-10-31).

### What to add to `backend/MoonStreamBackend.kt`

```kotlin
/** A backup document found in Saved Messages. */
data class BackupRef(val messageId: Long, val fileId: Int, val date: Long)

/** Upload raw bytes as a document to Saved Messages. Returns the sent
 *  message id, or null on failure. The caption MUST contain #MoonStreamBackup
 *  so findLatestBackup() can locate it later. */
suspend fun uploadBackup(bytes: ByteArray, fileName: String): Long?

/** The newest #MoonStreamBackup document in Saved Messages, or null. */
suspend fun findLatestBackup(): BackupRef?

/** Download a backup document's full bytes. */
suspend fun downloadBackup(ref: BackupRef): ByteArray?
```

Then implement all three in `backend/TdlibMoonStreamBackend.kt`.

### How to implement it in TDLib

- **Saved Messages chat id**: the chat with the user themself. Get it from
  `getMe().id` and open/resolve the chat for that user id
  (`TdApi.CreatePrivateChat` / `TdApi.GetChat`). Do NOT hardcode any id.
- **uploadBackup**: write `bytes` to a temp file in the app cache dir, then
  `TdApi.SendMessage(chatId = me, inputMessageContent =
  TdApi.InputMessageDocument(document = TdApi.InputFileLocal(tempPath),
  caption = TdApi.FormattedText("#MoonStreamBackup MoonStream library backup",
  emptyList())))`. Wait for the message response; return its `id`.
  Delete the temp file afterwards. Return null on any TDLib error.
- **findLatestBackup**: `TdApi.SearchMessages` (or `SearchChatMessages`)
  with `chatId = me`, `query = "#MoonStreamBackup"`, limit 1, newest first.
  Map the result's document to `BackupRef(messageId, fileId, date)`.
- **downloadBackup**: `TdApi.DownloadFile(ref.fileId, priority = 1, ...)`,
  wait until the file is complete, then read all bytes from
  `local.path`. Return null on failure.

### How the whole thing works (so you know what calls your code)

1. The app (assistant's side) serializes the library — 6 Room tables
   (`channel_config`, `media_items`, `stream_files`, `subtitle_files`,
   `unmatched_files`, `scan_cursors`) plus selected `AppUiState` settings —
   into JSON, gzips it. Auth/session data (API ID, API hash, session
   strings) is NEVER included; your methods only ever see opaque bytes.
2. Backup: app calls `uploadBackup(gzippedBytes, "MoonStream_backup_<ts>.json.gz")`.
3. Restore: app calls `findLatestBackup()` → `downloadBackup(ref)` →
   gunzips → parses → merges into Room (upsert by natural keys,
   `updatedAt`-wins; never wipes local data).
4. The Settings screen will get "Push to Saved Messages" / "Pull from Saved
   Messages" buttons once your methods exist — the assistant wires the UI.

### Rules

- These three methods are the ONLY contract addition for this feature.
  Don't change existing methods' signatures.
- Never log or persist the backup bytes' contents beyond the temp file
  needed for upload.
- Compile via the normal CI workflow after implementing; the assistant
  reviews before merge.

## Stream cache auto-delete — DONE (2026-11-02)

moon picked (2026-11-02): after he finishes watching a video in the in-app
player, the app auto-deletes the locally downloaded bytes so storage
doesn't grow. Implemented by moon's AI (freebuff) in
`backend/TdlibMoonStreamBackend.kt`:

- `deleteCachedFile(channelId, messageId)` override: looks up the TDLib
  fileId from `messageFileIdCache`, falling back to `TdApi.GetMessage` when
  unknown (e.g. after app restart — this was the Clear Cache leak, now
  fixed). Calls `TdApi.DeleteFile(fileId)`. Returns false (not fake true)
  on lookup/delete failure so callers can report honestly.
- Called by the player on close of a finished video (>=90% watched), by the
  streaming window every ~96 MB of evicted chunks during playback, and by
  Settings → Clear Cache for every stream file in the DB.

### Rules

- Never log file paths or contents — a success/failure boolean is enough.

---

## Custom TDLib DataSource + sliding-window cache — for moon's AI

moon picked (2026-11-02): TMPlayer-style low-storage streaming for the
in-app player. Instead of the current HTTP-server path
(`http://127.0.0.1:8081/stream/...` → `readFileRange`), the player should
stream **directly from TDLib** via a custom Media3 `DataSource`, with a
sliding download window so a 2 GB video never holds more than ~200 MB on
disk. Reference: TMPlayer repo (GPL-3.0) — study the *patterns*, do not
copy-paste code.

### Why

The HTTP path forces TDLib to accumulate chunks in `tdlib_files` with no
way to evict old ones (TDLib has no partial-file delete). A custom
`DataSource` controls downloads directly: when playback moves past the
window, delete the whole file via `TdApi.DeleteFile` and re-download only
the new window. moon's phone is low-storage; this is the fix.

### What to build

**1. `tdfile://` DataSource** (`player/TdDataSource.kt`, new file)

- A Media3 `DataSource` (extend `BaseDataSource`) that understands
  `tdfile://<fileId>?type=<mime>` URIs.
- `open(DataSpec)`: resolve the TDLib fileId from the URI, ask TDLib for
  the file (`TdApi.GetFile` / download), block until bytes are available.
- `read(byte[], offset, length)`: read from the partial file on disk as
  TDLib fills it; block (with timeout) while waiting for bytes.
- `close()`: release.
- MIME type: infer from the `type` query param, else from file extension
  (`mkv` → `video/x-matroska`, `mp4` → `video/mp4`, etc.). Media3 picks
  extractors by Content-Type first — wrong/missing type causes slow
  extractor probing.

**2. Byte-window manager** (`player/TdByteWindow.kt`, new file)

- One instance per playing file. Holds: fileId, file size, local path,
  the confirmed-downloaded byte range.
- `watch(fileId)`: point TDLib at the file (`TdApi.DownloadFile` with
  offset/limit).
- Reads come off the partial file on disk as bytes land; no full download
  before playback starts. Seeking = re-open at new offset = re-point
  TDLib (no re-download of the whole file).

**3. Sliding-window cache** (`player/LowStorageWindow.kt`, new file)

Constants (from TMPlayer, tune as needed):
- `WINDOW_BYTES = 200 MB` — max held on disk per video.
- `MIN_WINDOW_BYTES = 32 MB` — below this, don't bother rolling.
- `BACK_BYTES = 2 MB` — kept behind the read position (small rewinds free).
- `ROLL_MARGIN_BYTES = 4 MB` — roll the window when reads get this close
  to the window end.
- `SLACK_BYTES = 16 MB` — headroom so extractor index reads at file end
  don't evict the start.

Logic:
- Track downloaded ranges per fileId.
- Before each download request: if `ranges.total + newBytes > WINDOW + SLACK`,
  set `deleteFirst = true` → call `TdApi.DeleteFile(fileId)`, then
  re-request only `[readPosition - BACK_BYTES, readPosition + WINDOW]`
  (aligned to 1 MB part boundaries).
- Never evict a file the user explicitly downloaded (check download state).

**4. Backend contract additions** (`MoonStreamBackend`, additive only)

```kotlin
/** TDLib fileId for a channel message, or null if unknown. */
suspend fun getFileId(channelId: Long, messageId: Int): Int?

/** Low-level TDLib send for the DataSource (or expose what's needed). */
suspend fun tdSend(query: TdApi.Function<*>): TdApi.Object
```

The app side will construct `tdfile://` URIs from `getFileId()` and pass
a `DataSource.Factory` into ExoPlayer. Keep the HTTP server path working
as fallback (external players still use it).

### Integration (app side, my lane — do NOT touch)

Once the above lands, I will:
- Add `TdDataSource.Factory` to the ExoPlayer builder in `PlayerScreen`.
- Switch `PlayerUrls` in-app playback from `http://127.0.0.1:8081/...` to
  `tdfile://<fileId>`.
- Add the Settings toggle "Low storage mode" (default ON, like TMPlayer).

### Status (2026-11-02): fully implemented and merged (v0.15.0)

Implemented by moon's AI (freebuff), reviewed and merged. No new TdApi
surface was needed: the DataSource drives `readFileRange` (which already
maps to DownloadFile/ReadFilePart with an offset), so nothing in `tdlib/`
changed and no new contract methods were needed. The `deleteCachedFile`
override (section above) is also done, so the window's ~96 MB purges
actually delete TDLib's copy now.

- `ui/player/TdDataSource.kt` — `tdfile://stream/<channelId>/<messageId>/<file>?size=…`
  DataSource + factory; non-tdfile URIs fall through to Media3 defaults.
- `ui/player/TdByteWindow.kt` — 8 MB chunk fetch via the backend, blocking
  reads for Media3, eviction of watched chunks, best-effort prefetch.
- `ui/player/LowStorageWindow.kt` — window constants + policy (progress
  counts as "watched", so it is deleted).
- `ui/player/TdSubDataSource.kt` + `util/SubtitleTiming.kt` — `tdsub://`
  subtitles: read through the contract, cue shift applied on-device, so
  subtitles also work with the server off (ASS/SSA passed through).
- `cache/TdStreamCache.kt` — filesDir/tdstream owner: size + clear (never
  clears a folder an active playback holds).

Lessons taken from TMPlayer's engine (read, rewritten fresh): Content-Type
on the open so Media3 picks the right extractor first; a 64 KB read-ahead
for small reads (a Matroska index is walked a byte at a time); eviction
checked per chunk crossed rather than per read.
- `PlayerScreen` now builds the player with this factory and plays
  `tdfile://` whenever the DB knows the file size; HTTP stays as fallback
  and for external players. Settings' Clear Cache also wipes the window.
- Follow-ups in the same branch: `deleteCachedFile` with GetMessage
  fallback (fixes Clear Cache after restart), detailed stream-engine
  logging, Settings → Languages (subtitle/audio first+second preference,
  defaults Mal → Eng), Telegram rate limiting for the chunk fetcher.

### Rules

- Patterns only from TMPlayer — fresh code, never copy-paste (GPL-3.0).
- Do NOT touch `ui/`, `server/`, `index/`, or the HTTP server. Backend
  contract additions must be additive with safe defaults.
- The `deleteCachedFile` override from the section above stays as the
  manual/auto clear path; the window is the *streaming* path.

---

## FFmpeg audio decoders (tiny build) — DONE (2026-11-03)

moon wants (2026-11-03): the in-app player should decode EAC3/AC3/DTS
in software, like mpvRx does. His phone (Helio G96) has no hardware
decoder for these, so ExoPlayer fails with `ERROR_CODE_DECODER_INIT_FAILED`
(`format_supported=NO_UNSUPPORTED_TYPE`).

### Status: built and wired (assistant)

Full write-up: `docs/FFMPEG.md`; rebuild script: `tools/ffmpeg/build_ffmpeg.sh`.

- FFmpeg **6.1**, audio only: `eac3`, `ac3`, `dca`, `truehd`. Verified in the
  linked `.so` with `llvm-nm` (`ff_ac3_decoder`, `ff_eac3_decoder`,
  `ff_dca_decoder`, `ff_truehd_decoder`; no `ff_aac_decoder`).
- `libffmpegJNI.so` is **1.3 MB arm64-v8a / 1.1 MB armeabi-v7a**, in
  `app/src/main/jniLibs/<abi>/`. x86/x86_64 have none and fall back cleanly.
- The Java side is **vendored** in
  `app/src/main/java/androidx/media3/decoder/ffmpeg/` (Apache-2.0) — the
  Gradle coordinate in the spec below **does not exist on Maven**; Google
  never published the FFmpeg extension, so it has to be compiled and
  vendored. The file id loads as `ffmpegJNI`.
- `PlayerScreen` uses `DefaultRenderersFactory` with
  `EXTENSION_RENDERER_MODE_PREFER` when `FfmpegLibrary.isAvailable()`, else
  `OFF`. PREFER is safe: the renderer only claims formats whose decoder is
  really compiled in, so AAC/Opus/MP3 still go to MediaCodec.
- **v1 shipped broken (fixed 2026-11-04).** The first `.so` was linked with
  clang++'s default dynamic C++ runtime, so it needed `libc++_shared.so`,
  which the APK does not contain: `dlopen` failed → `isAvailable()` false →
  silent fallback to MediaCodec → Dolby still dead. The link now uses
  `-nostdlib++ -Wl,--no-undefined -Wl,--exclude-libs,ALL` (plain C, no runtime
  needed) and `tools/ffmpeg/build_ffmpeg.sh` refuses to finish if
  `libc++_shared` reappears. **Never debug this class of bug from Java alone:**
  `readelf -d libffmpegJNI.so | grep NEEDED` first.
- Atmos caveat: the `eac3` decoder decodes the E-AC3 core (5.1). The Atmos
  object/height layer is ignored — you get audio, not object audio.
- Still not device-verified: needs moon to play an EAC3 episode and confirm
  audio with the `PLAYER` log line "FFmpeg audio decoders ready".

**Correction to the spec below:** step 1's `media3-exoplayer-ffmpeg`
dependency returns 404 on both google() and mavenCentral(); ignore it and use
`tools/ffmpeg/build_ffmpeg.sh` instead. Steps 2–3 (renderers factory +
`isAvailable()` guard) are exactly what was implemented.

### What to build

The **ExoPlayer/Media3 FFmpeg audio extension**, minimal build — audio
decoders ONLY, no video. This is the "tiny" FFmpeg (3–5 MB per ABI),
not a full build.

**Decoders to enable** (the ones phones lack):
- `eac3` (Dolby Digital Plus — moon's files are all EAC3)
- `ac3` (Dolby Digital)
- `dca` (DTS)
- `truehd` (Dolby TrueHD)

**Decoders to SKIP** (phone already does these in hardware):
- aac, mp3, opus, vorbis, flac, wav — do NOT include, keeps the .so small.

### How to build the native library

From the media3 repo (`libraries/decoder_ffmpeg`):

```sh
cd decoder_ffmpeg
./build_ffmpeg.sh \
  --enable-decoder=eac3 \
  --enable-decoder=ac3 \
  --enable-decoder=dca \
  --enable-decoder=truehd \
  --disable-everything \
  --disable-avdevice --disable-avformat --disable-swscale \
  --disable-swresample --disable-postproc \
  --disable-network --disable-doc
```

This produces `libffmpeg.so` per ABI. Copy to
`app/src/main/jniLibs/<abi>/libffmpeg.so`
(arm64-v8a is the priority; armeabi-v7a optional).

Target size: **under 5 MB per ABI**. If it's bigger, a decoder slipped
in — check with `nm -D libffmpeg.so | grep -i decoder`.

### How to wire it in the app

1. ~~Add the Gradle dependency~~ — **this artifact does not exist** (404 on
   google() and mavenCentral()). The extension is built locally and vendored;
   see `docs/FFMPEG.md` and `tools/ffmpeg/build_ffmpeg.sh`.
   ```gradle
   // NOT valid: implementation "androidx.media3:media3-exoplayer-ffmpeg:1.4.1"
   ```

2. In `PlayerScreen.kt`, where the ExoPlayer is built, use a
   `DefaultRenderersFactory` that prefers the FFmpeg extension for audio:
   ```kotlin
   val renderersFactory = DefaultRenderersFactory(context).apply {
       setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
   }
   val player = ExoPlayer.Builder(context, renderersFactory).build()
   ```

3. Guard with `FfmpegLibrary.isAvailable()` — if the .so fails to load,
   fall back to the default factory (hardware only). Log which path was taken.

### Rules

- Audio decoders ONLY. No video decoders in the build.
- The 4 decoders above, nothing else. Every extra decoder is APK bloat.
- Test on moon's POCO M4 Pro: play an EAC3 file in-app, confirm audio plays.
- If the .so exceeds 5 MB per ABI, strip it down before shipping.
- This touches `ui/player/` (my lane) for the wiring — coordinate the
  Gradle + jniLibs + PlayerScreen changes as one PR, don't land half.
