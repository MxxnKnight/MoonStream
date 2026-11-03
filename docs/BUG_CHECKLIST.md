# MoonStream Bug Checklist

Every bug you report goes here. I check them off only when the fix is built, pushed, and (where I can) verified. Updated 2026-11-01.

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
