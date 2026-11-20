# MoonStream Bug Checklist

Every bug you report goes here. I check them off only when the fix is built, pushed, and (where I can) verified. Updated 2026-11-20.

## 🔨 For freebuff (Telegram module — TdlibMoonStreamBackend)

- [ ] Telegram restore misses backups older than the newest 60 Saved Messages: `recentBackupDocs` reads only GetChatHistory(limit=60) and `searchBackupDocs` relies on the hashtag search index. Page through Saved Messages history (GetChatHistory with from_message_id pagination, 100 per page, up to ~20 pages) until a MoonStream_backup document is found or history is exhausted. Keep the hashtag search as the fast path. Log the attempt with AppLog BACKUP tag (messages scanned, found or not) so it shows in the Logs screen. (moon's 2026-11-20: restore said "No backup found" after Clear data)

## 🔨 Done in this build (0.15.164 — build from workflow to get it)

- [x] Telegram files stayed at 1230 MB after a Stremio/Nuvio session — the sweep only ran on app start or player close, and the app almost never restarts → the server now sweeps on a timer (first check after 2 min, then every 15 min) while it runs
- [x] The sweep forgot everything it served when the app restarted → the served list is now saved on the phone and reloaded on launch (capped at the 1000 most recent, entries older than a day dropped)
- [x] The "0 MB of 2048 MB" meter didn't count Telegram files at all → the meter and the Cache MB cap now count the whole cache (app cache + Telegram files), and the cap trims the app cache first, then drops Telegram copies of files unplayed 7+ days

## 🔨 Done in this build (0.15.163 — build from workflow to get it)

- [x] Detail header showed 5 Episodes while Season 1 showed 6 (same 10.3 GB) → Set-episode save now refreshes the stored count, plus a one-time repair on first launch after updating
- [x] "Copy Link" and "Add Custom Stream" buttons clipped → shortened to "Copy" and "Add Stream"
- [x] Instant play: opening a Detail page quietly warms the first + last chunks (~2 MB) so the first frame comes from local disk; warmed chunks deleted the moment you leave the page without playing
- [x] Logs screen never showed Detail actions → Set episode, Fix Metadata, Refresh metadata, Move to, Add Stream, Subtitle, Delete file, Move to unmatched, Custom content now log one DETAIL line each
- [x] App grew to 248 MB → poster cache capped 50 MB, server-streamed bytes swept after 10 min idle, auto-cleanup over 500 MB, Cache MB cap enforced, storage breakdown rows in Settings, Lite posters switch (needs restart)
- [x] Gist restore failed after Clear data (saved gist id wiped, app never searched) → pull/push now list your gists and find the MoonStream one; toasts say the real reason (no token / no gist / token rejected / no connection)

## ✅ Done (pushed to main)

- [x] Server card glow looked like a "ball" → now a vertical wash
- [x] Fake 1.33 GB / uptime resetting → real telemetry (session/all-time/speed)
- [x] Streams loaded but never played → wrong-file bug fixed (you confirmed: plays in mpvRx ✅)
- [x] System toasts → custom MsToast
- [x] Library card not clickable + no latest entries → clickable + "New:" line
- [x] Episodes stuck in "Other files" → parser keeps [S01 E24]
- [x] Description no expand/collapse → tap to expand
- [x] Add Subtitle: no season/episode, no fetch → rewritten with S/E + fetch
- [x] Metadata search empty → was my wrong Cinemeta URL, fixed (needs new build)
- [x] Create screen: no series default, no auto-parse → defaults + auto-parse
- [x] Subtitle language filter → added (Malayalam/English/...)
- [x] Split-file toggle in Create → added
- [x] Channels: removed refresh-avatar, Add Channel simplified to ID + toggle
- [x] Unmatched files: Play button added, copy-link fixed
- [x] Subtitle fetch failure trapped user → falls back to manual add
- [x] Settings: @@moon, Early Beta removed, Support cut off fixed
- [x] Dead links / Duplicates finders → now real
- [x] Real logging → AppLog (scan/match/server/bridge in Logs screen)

## 🔨 Done in this build (9d48577 — build from workflow to get it)

- [x] Add Custom Stream: pasting a t.me link auto-fetches the filename from Telegram and fills it in; quality auto-detected
- [x] Create screen: Season/Episode boxes visible as soon as Series selected (even with 0 files)
- [x] Create screen: "+ Add by link" for files (link + auto-fetch + S/E + quality)
- [x] Create screen: "+ Add by link" for subtitles (link + auto-fetch + language)
- [x] Create screen: per-file quality picker (Auto/4K/1080p/720p/480p/360p)
- [x] Create screen: close (X) button at top-right

## ⏳ Still needs your new build

- [ ] Metadata fix sheet empty → fixed in 0ad6518, but you're still on 0.8.0. Build from the workflow!

## ⏳ Parked (you said later)

- [ ] OTA auto-check badge
- [ ] Audio-language / dub-sub-dual / streaming-app filters (needs DB migration)
- [ ] Tier 2 cloud backup (Antigravity built it; you deferred)
- [ ] Android TV (you declined)

## ❓ Needs your device test

- [ ] Build the new APK from the workflow (you're still on 0.8.0 — everything above needs the new build)

## 🔨 Done in this build (FFmpeg audio decoders)

- [x] EAC3/AC3/DTS/TrueHD audio failed with `DECODER_INIT_FAILED` on phones without a Dolby/DTS hardware decoder → a tiny audio-only FFmpeg (1.3 MB) now decodes exactly those four in software; every other codec still uses hardware
