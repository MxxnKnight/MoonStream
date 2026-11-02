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
- Design system: bg `#0E1116`, cards `#161B24`, lime `#7DD956` pills with
  dark text, floating pill bottom nav. Generous spacing, professional
  polish — never cramped.
- No `Canvas` / `Stroke` / `nativeCanvas` in UI code (the offline build
  mirror's Compose AAR lacks them).
- Never deliver APKs as chat attachments (the user's phone saves them as
  `.zip`). Direct download link with a real `.apk` filename + expiry.
- Never commit secrets: no API ID/hash, no session strings, no keystore
  passwords. Ever.
- Commits: short imperative summary; push after committing (token is in
  the git credential store).

## Cloud backup (Tier 2) — instructions for moon's AI (Antigravity)

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
