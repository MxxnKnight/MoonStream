# AGENT.md — MoonStream AI briefing

Read this file before making any change to this repo. It tells you what the
app is, who owns which part, and what is already done. Follow it.

## What MoonStream is

A personal Telegram media library for Android (Kotlin + Jetpack Compose).
The user connects their own Telegram account; the app scans their channels
for movies and TV shows, builds a personal library, and serves it to the
Stremio video player as a local streaming addon running on the phone.

Package: `com.moon.moonstream`. Repo: `MxxnKnight/MoonStream` (private).

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

## Current status (2026-10-31)

- Phase 1 — UI shell: DONE (all screens per the approved mockup).
- Phase 2 — Telegram login (TDLib): DONE by moon.
- Phase 3 — Scan/index/library: NEXT, assistant's work, built against the
  contract (`listChannels` / `fetchMessages`).
- Phase 4 — In-phone Stremio server: basic version DONE by moon's AI
  (`server/MoonStreamServer.kt` + foreground service); needs review and
  hardening (separate addon/stream ports, rebind on network change).
- Phase 5 — Streaming extras (range seeking, split joining, ZIP entries,
  per-stream Copy Link, subtitles, cache limits, backup, OTA): pending.

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
