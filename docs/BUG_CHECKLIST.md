# MoonStream Bug Checklist

Every bug you report goes here. I check them off only when the fix is built, pushed, and (where I can) verified. Updated 2026-11-20.

## 🔨 For freebuff (Telegram module — TdlibMoonStreamBackend)

- [ ] Telegram restore misses backups older than the newest 60 Saved Messages: `recentBackupDocs` reads only GetChatHistory(limit=60) and `searchBackupDocs` relies on the hashtag search index. Page through Saved Messages history (GetChatHistory with from_message_id pagination, 100 per page, up to ~20 pages) until a MoonStream_backup document is found or history is exhausted. Keep the hashtag search as the fast path. Log the attempt with AppLog BACKUP tag (messages scanned, found or not) so it shows in the Logs screen. (moon's 2026-11-20: restore said "No backup found" after Clear data)
- [x] Clear Cache cannot clear Telegram files (stuck at 935 MB on moon's phone, 2026-11-20): both Clear Cache and the janitor only iterate the library database, but the bytes may belong to files the database doesn't track — and DeleteFile succeeds trivially on never-downloaded files, so the loop counts empty deletes. Use TDLib's own storage API instead: add a backend method that reports `GetStorageStatistics` and clears with `OptimizeStorage`, and wire the Settings Clear Cache button to it. Full brief: docs/clear-cache-freebuff.md. Never delete files under filesDir/tdlib_files directly. — DONE in 0.15.165 (see below).

## 🔨 Done in this build (0.15.170 — build from workflow to get it)

- [x] Detail pages quietly downloaded whole movies in the background (moon's 2026-11-23, Telegram files 0 → 709 MB in an hour, zero playback): opening a Detail page warms chunk 0 for instant play, and that request makes TDLib download the whole file in the background — nothing ever cancelled it. Proof from his 999-line log: the app itself requested only ~6 MB (7 "chunks stored" lines) while ~700 MB arrived; the 8 Beautiful windows at 23:42 stored zero chunks through the app yet the download started; zero STREAM/SERVER lines; temp/100 grew 93 → 450 MB in 4 minutes after the last log line. That's also why older versions never did this — pre-0.15.163 Detail opens never touched file bytes → the warmer now drops TDLib's copy when done (even on cancellation, via NonCancellable), so the background download stops; the warmed chunks stay in the tdstream cache, so instant play still works

## 🔨 Done in this build (0.15.169 — build from workflow to get it)

- [x] Unlinked subtitles list missing on entries whose channel held no subtitles (moon's 2026-11-22, WALL·E): the list was scoped to the entry's own channel, so it showed nothing on WALL·E's page (neither under the files nor in the Subtitle sheet) while entries from the manual channel showed all 7 → the list now covers every channel, so every entry shows the same unlinked subtitles and moon picks by hand
- [x] Attached subtitle invisible on the entry it was attached to (moon's 2026-11-22, WALL·E subtitle attached to Tenet for a test): the movie subtitles list hid any subtitle carrying a season/episode number, and the WALL·E subtitle's stored episode (2008, misread from "WALL.E.2008" by the parser) kept it hidden, so it couldn't be seen or deleted → movie pages now show every attached subtitle
- [x] Stats delta pill showed absurd percentages after a near-zero week ("↑ 3048% vs previous", moon's 2026-11-22) → rises of 10x and up now read as a multiple ("↑ 31x vs previous"); smaller changes still show a percent, drops unchanged
- [x] Log window too short to catch a mystery download (moon's 2026-11-22, Beautiful 1246 MB with no playback): the 200-line screen buffer and 512 KB / 300-line disk file lost the evidence — one Clear Cache run filled the whole window → screen buffer 1000 lines, disk cap 4 MB, rotation keeps 5000 lines
- [x] Fix Metadata could not correct a same-title-same-year mismatch (moon's 2026-11-22 video, Beautiful 2011: entry matched to the French short tt2282713 instead of the Malayalam feature tt2133191): (1) Fix Title re-searched by title+year and re-found the same wrong film, so "Metadata updated" changed nothing → the picked IMDb id is now passed to the matcher for a direct lookup; (2) same for the Fix File create path; (3) resolvePick's title+year fallback ignored IMDb ids, so Fix File said "Already under Beautiful" → the fallbacks now skip an entry carrying a different IMDb id, so the move creates the right entry instead

## 🔨 Done in this build (0.15.168 — build from workflow to get it)

- [x] Delta updates (moon's 2026-11-22): updates download only the changed part (~3 MB) instead of the whole 41 MB APK — CI builds a bsdiff patch per ABI against the previous release (tools/make_patch.py, MSPATCH1 container: bsdiff streams re-packed as gzip so the app needs no bzip2 library), the updater applies it to the installed APK and verifies the SHA-256, then installs the byte-identical rebuilt APK. Any failure falls back to the full download; no patch when the user skipped a version. First usable delta is the update AFTER this build (0.15.167 can't apply patches). Verified in the sandbox: the Kotlin applier rebuilt the real 0.15.167 APK byte-identical from 0.15.166 + patch, and a corrupt patch is rejected with no partial APK left

## 🔨 Done in this build (0.15.167 — build from workflow to get it)

- [x] Movie files indexed as anime even from non-anime channels ("Who Am I" 1998/2014 movies filed under the 2012 anime MMD video) — the anime auto-detect checked Kitsu on title alone, with no year check, so any movie sharing its title with any anime flipped to the Anime tab → the detect now requires the Kitsu entry's year to be within 1 of the filename year, and the per-title cache is keyed by year too
- [x] "Wrong title?" (Fix Metadata — This File) said "File moved" but the file stayed put — the move target was found by title alone, so picking the 1998 movie resolved to the same anime entry the file was already under, and moving a file onto its own entry is a no-op → the target is now resolved by identity (exact row, IMDb id, then title+YEAR), and picking the entry the file is already under says "Already under ..." instead of a fake "moved" toast. Same fix in Link to Title (single + bulk)
- [x] "In Library" badge showed on provider results for works not in the library — the badge keyed on the bare title, so any same-title result inherited it → the badge now keys on the identity that matched (IMDb id when the result has one)
- [x] Fix Metadata — This File opened on the Anime tab for a movie file — the sheet took the entry's type, which is what's being fixed → it now opens on the file's own parsed type (a series file in an anime entry still opens on Anime)

## 🔨 Done in this build (0.15.166 — build from workflow to get it)

- [x] Brightness lowered by itself after some time of watching, and the screen turned off during long playback so the phone needed unlocking — the player never held the screen on, so the system timeout dimmed the display and then slept it (same bug twice) → the player now keeps the screen on while open
- [x] Brightness jumped back to system brightness on every episode switch — leaving the player restored system brightness, and the switch disposed the player first → the restore is skipped on episode switches, so the brightness you set carries over
- [x] Double-tap to play/pause showed "Playing"/"Paused" text in the middle — removed (the center button still shows the state)
- [x] Player buttons had a thin white ring (outline) while the title pill had none — the ring is off every player button now (round icon buttons, Aspect, trim nudge buttons, Skip/Resume pills); the soft round fill stays, the big play/pause was already ring-free
- [x] Bottom sheets dropped down when scrolling inside them — sheets now close ONLY by holding the top bar and dragging it down (~80dp); the grab strip is full-width so it's easy to hold, and content drags can never dismiss the sheet. Tapping outside and the back button still close it
- [x] Posters in Fix Metadata / Link to Title were too small to see (52x74) — now 68x96, about a third bigger

## 🔨 Open — bottom-sheet slide speed (needs moon's call)

- [ ] Bottom sheets open/close slowly — the slide speed is fixed inside the Material3 1.3.0 sheet component: verified from the real AAR, it has no animation setting and no gesture toggle in its public API. Speeding up the slide needs either a Material3 update (1.4+, a toolchain bump) or a custom sheet. The dismiss fix (top-bar drag only) shipped in 0.15.166.

## 🔨 Done in this build (0.15.165 — build from workflow to get it)

- [x] 0.15.164 update download failed with ENOENT on the APK path — your phone storage was so full that Android deleted the download folder (cache dirs are reclaimable), and the updater never checked the folder was really there → the updater now verifies the folder exists after creating it and before merging the parts, and says plainly that storage is likely full instead of showing a bare file path
- [x] The cache trimmer walked the whole cache dir and would have deleted a staged update (it only spared the poster cache) → it now skips the updates folder entirely
- [x] The "Largest Telegram files" list showed nothing even with 935 MB on disk — it only listed the top level of tdlib_files, but TDLib shards downloads into subdirectories → the list now walks recursively and shows paths relative to tdlib_files
- [x] The cache still grew to 935 MB with 2048 MB set, and Clear Cache could not bring it down — the only delete path was per-message (`DeleteFile` for files the library still maps to a channel message), so every file TDLib downloaded without a matching library row (served to Stremio/Nuvio, row deleted, interrupted download) stayed on disk forever → the backend now trims TDLib's own cache with `OptimizeStorage` (what Telegram's "Clear cache" uses), applied on player close, by the auto-cleanup, by the Cache MB cap and by Clear Cache
- [x] The cache should be cleared the moment you quit the player — player close now runs `StorageJanitor.clearPlaybackCache`: it drops the played file's copies (as before), sweeps warmed chunks, and shrinks TDLib's video/document/audio cache to zero (moon's pick), so nothing a playback downloaded stays on the phone
- [x] Clear Cache felt slow on a big library — it asked Telegram about every library row before doing the bulk work → TDLib's trim now runs first, and when it reports no video/document/audio files left the per-file lookups are skipped entirely (nothing they could delete)
- [x] Posters must not be thrown away with the video cache → the trim only considers video, document and audio files, so library artwork is kept and never re-downloads after a playback

## 🔨 Done in this build (0.15.164 — build from workflow to get it)

- [x] Telegram files stayed at 1230 MB after a Stremio/Nuvio session — the sweep only ran on app start or player close, and the app almost never restarts → the server now sweeps on a timer (first check after 2 min, then every 15 min) while it runs
- [x] The sweep forgot everything it served when the app restarted → the served list is now saved on the phone and reloaded on launch (capped at the 1000 most recent, entries older than a day dropped)
- [x] The "0 MB of 2048 MB" meter didn't count Telegram files at all → the meter and the Cache MB cap now count the whole cache (app cache + Telegram files), and the cap trims the app cache first, then drops Telegram copies of files unplayed 7+ days
- [x] Auto-cleanup dropped 200 files and freed 0 MB (your log) — it stopped at the first 200 files, most of which never held bytes → it now keeps passing (up to 25 passes of 200) until the cache is under 500 MB or nothing is left to drop
- [x] Watched files were never deleted on player close — the deletes launched in a scope that died with the player screen → close cleanup now runs in a scope that outlives the player, so the position save, Telegram deletes, chunk sweep and janitor run all complete
- [x] The breakdown total could not say WHICH files held the 935 MB → Streaming & Storage now lists the largest Telegram files by real disk size under the breakdown

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
