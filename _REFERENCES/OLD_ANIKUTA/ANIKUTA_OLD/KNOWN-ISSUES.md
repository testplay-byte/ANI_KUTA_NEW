# ANI-KUTA — Known Issues

> **Status:** Documentation only — no fixes applied yet. To be triaged and addressed in future sessions.
> **Scope:** Issues found during the download-system analysis (Session 31+, `player-experiment` branch) + player code review (Session 44) + architecture review.
> **Last updated:** 2026-07-13
> **Branch context:** Issues on `player-experiment` unless noted. Player issues are on both `main` and `player-experiment`.

---

## How to use this file

Each issue has:
- **ID** — stable identifier for reference
- **Severity** — CRITICAL / HIGH / MEDIUM / LOW
- **Subsystem** — Player / Download / Architecture / Storage / Security
- **Description** — what's wrong
- **Impact** — how it affects the user / app
- **Location** — file + approximate line
- **Status** — Open / Investigating / Deferred

Do NOT start fixing these until we've discussed priority and sequencing.

---

## Download System Issues (player-experiment branch)

### D1 — Concurrency not actually concurrent [CRITICAL]

**Severity:** CRITICAL
**Subsystem:** Download
**Description:** `DownloadWorker.doWork()` uses a `Semaphore(maxConcurrent)` but processes downloads in a sequential `forEach` loop. `semaphore.acquire()` blocks the single coroutine before each `processDownload()` call, so downloads run **one at a time** — the semaphore never has more than 1 permit in flight. The `maxConcurrentDownloads` preference (default 2) is effectively ignored.
**Impact:** Downloads are serialized. A 12-episode queue takes 12× longer than intended. The concurrency setting in preferences does nothing.
**Location:** `app/src/main/java/app/anikuta/download/DownloadWorker.kt` ~L62-73
**Status:** Open
**Fix direction:** Wrap the `forEach` in `coroutineScope { pending.forEach { launch { semaphore.withPermit { processDownload(it) } } } }` so each download runs in its own child coroutine.

### D2 — No foreground service [CRITICAL]

**Severity:** CRITICAL
**Subsystem:** Download
**Description:** `DownloadWorker` is a `CoroutineWorker` but never calls `setForeground()`. The `AndroidManifest.xml` does not declare `FOREGROUND_SERVICE` or `FOREGROUND_SERVICE_DATA_SYNC` permissions.
**Impact:** On Android 12+ (API 31+), background work is killed after ~30 seconds. Downloads die when the user switches apps. This makes downloading anything longer than a short clip impossible unless the user keeps the app in the foreground the entire time.
**Location:** `app/src/main/java/app/anikuta/download/DownloadWorker.kt` (no `setForeground` call); `app/src/main/AndroidManifest.xml` (no permission declarations)
**Status:** Open
**Fix direction:** Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions to manifest. Call `setForeground(ForegroundInfo(...))` in `doWork()` with a progress notification. Requires notification channels (see D3).

### D3 — No notification channels / no download notifications [HIGH]

**Severity:** HIGH
**Subsystem:** Download
**Description:** `Notifications.kt` is a 5-line stub with only `CHANNEL_EXTENSION_UPDATES`. `App.kt` creates no notification channels. No `DownloadNotifier.kt` exists. No progress notification, no error notification, no "download complete" notification.
**Impact:** Even after D2 is fixed (foreground service added), the notification will silently fail to post because no channel exists. Users get zero feedback about download progress or errors outside the app.
**Location:** `app/src/main/java/app/anikuta/data/notification/Notifications.kt`; `app/src/main/java/app/anikuta/App.kt` (no `createNotificationChannel` calls)
**Status:** Open
**Fix direction:** Add `CHANNEL_DOWNLOADER_PROGRESS` + `CHANNEL_DOWNLOADER_ERROR` constants. Create channels in `App.onCreate()`. Create `DownloadNotifier.kt` to wrap notification updates. Wire into `DownloadWorker` for foreground service notification + progress updates.

### D4 — Progress estimation hardcoded to 24 minutes [HIGH]

**Severity:** HIGH
**Subsystem:** Download
**Description:** `DownloadWorker.executeFFmpeg()` hardcodes `estimatedDurationMs = 24 * 60 * 1000L` (24 minutes) as the video duration for progress percentage calculation. It does not read the actual video duration or `Content-Length`.
**Impact:** Progress bar is inaccurate for any episode that isn't exactly 24 minutes. A 12-minute episode shows ~50% when fully downloaded. A 48-minute episode shows ~200% (clamped to 99%).
**Location:** `app/src/main/java/app/anikuta/download/DownloadWorker.kt` ~L266
**Status:** Open
**Fix direction:** Use FFmpeg's `Statistics.getTime()` (microseconds of processed video) divided by the actual video duration. Duration can be obtained by a quick `FFprobeKit.getMediaInformation()` call before download starts, or from the first FFmpeg statistics callback after it processes enough frames. Alternatively, use `Content-Length` header for byte-based progress.

### D5 — File size (totalSize) never populated [HIGH]

**Severity:** HIGH
**Subsystem:** Download
**Description:** The `Download` model has `totalSize: Long` and `downloadedBytes: Long` fields, but `totalSize` is never set (stays at `-1L`). The UI doesn't display file size at all.
**Impact:** User cannot see "≈ 350 MB" estimated or "120 MB downloaded" during download. The user explicitly requested this feature in the planning discussion.
**Location:** `app/src/main/java/app/anikuta/download/Download.kt` L39-42 (fields exist); `DownloadWorker.kt` (never sets `download.totalSize`); `DownloadQueueScreen.kt` (no size display)
**Status:** Open
**Fix direction:** Do a `HEAD` request to the video URL to get `Content-Length` before download starts. For HLS/m3u8 (no Content-Length), use FFprobe to get the total stream size, or estimate from duration × bitrate. Update `downloadedBytes` from FFmpeg statistics callback (`statistics.getSize()` or calculate from `getTime()` × bitrate).

### D6 — No pause / resume [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Download
**Description:** `Download.State` enum has no `PAUSED` state (only NOT_DOWNLOADED, QUEUE, DOWNLOADING, DOWNLOADED, ERROR). `DownloadManager` has no `pauseDownload()` / `resumeDownload()` methods. The queue UI "Cancel" button cancels+removes; there's no way to pause without cancelling.
**Impact:** Users cannot temporarily pause downloads. They can only cancel (which removes from queue). The user requested pause/resume in the planning discussion.
**Location:** `app/src/main/java/app/anikuta/download/Download.kt` L47-53; `DownloadManager.kt` (no pause/resume methods)
**Status:** Open
**Fix direction:** Add `PAUSED` to the State enum. Add `pauseDownload()` / `resumeDownload()` / `pauseAll()` / `resumeAll()` to DownloadManager. In DownloadWorker, check for PAUSED state and skip/hold those downloads. Add pause/resume buttons to DownloadQueueScreen.

### D7 — No "Download all" button in UI [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Download UI
**Description:** `DetailViewModel.downloadAllEpisodes()` exists but there is no UI button to trigger it. No "Download all" button at the top of the episodes section, no filter (unwatched only, etc.).
**Impact:** Users must tap download on each episode individually for bulk downloads. The user explicitly requested a "Download all" button in the planning discussion.
**Location:** `app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt` (no "Download all" button); `DetailViewModel.kt` L829 (`downloadAllEpisodes` exists but unused from UI)
**Status:** Open

### D8 — No long-press menu on episode rows [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Download UI
**Description:** The download button on episode rows is single-tap only. There is no long-press menu with options like "Download" / "Cancel download" / "Delete download" / "Play downloaded".
**Impact:** Users cannot access per-episode download context actions. The user explicitly requested long-press → bottom sheet with options in the planning discussion.
**Location:** `app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt` ~L386-390, L515-519 (DownloadButton is single-tap IconButton)
**Status:** Open
**Fix direction:** Add `Modifier.combinedClickable(onClick = ..., onLongClick = { showEpisodeDownloadSheet = true })` to the episode row. Create a `ModalBottomSheet` with download/cancel/delete/play-downloaded options.

### D9 — Download button placement not configurable [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Download UI
**Description:** The download button is rendered inline on each episode row at a fixed position. There is no setting to configure placement (e.g., inside synopsis area, left side, right side) or to hide the button and use long-press only.
**Impact:** Users cannot customize where the download button appears. The user explicitly requested configurable placement in the planning discussion (default: inside synopsis area; configurable in detail settings).
**Location:** `app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt`; `DownloadPreferences.kt` (no placement preference)
**Status:** Open

### D10 — No concurrent downloads selector in settings UI [LOW]

**Severity:** LOW
**Subsystem:** Download UI
**Description:** `DownloadPreferences.maxConcurrentDownloads()` exists (default 2) but `DownloadsSettingsScreen` has no UI selector for it (1-4 range).
**Impact:** Users cannot change the concurrency setting. (Also moot until D1 is fixed — the setting does nothing currently.)
**Location:** `app/src/main/java/app/anikuta/ui/settings/DownloadsSettingsScreen.kt` (no selector)
**Status:** Open

### D11 — No partial download resume [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** On retry, the tmp directory is deleted and download restarts from scratch. FFmpeg cannot resume mid-mux. There is no checkpoint or partial-file mechanism.
**Impact:** A download that fails at 90% must restart from 0% on retry. For large episodes this wastes significant time and bandwidth.
**Location:** `app/src/main/java/app/anikuta/download/DownloadWorker.kt` ~L148-151 (tmp dir cleanup on failure)
**Status:** Open (deferred — aniyomi has the same limitation; FFmpeg's `-c copy` muxing doesn't support resume)
**Note:** The user requested "retry the download properly from where it was left" — this is architecturally difficult with FFmpeg copy-muxing. May need to reconsider the download approach (e.g., download segments separately then mux, or use FFmpeg's segment support).

### D12 — No storage-revoked handling [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** If the user revokes the SAF folder permission (via system settings, or reinstalls), `StorageManager.getDownloadsDirectory()` returns null. `DownloadProvider` methods return null. No UI banner or re-prompt.
**Impact:** Downloads silently fail with "Could not create anime directory" errors. User gets no guidance to re-select a folder.
**Location:** `app/src/main/java/app/anikuta/download/DownloadProvider.kt` (all methods return null on revoked storage)
**Status:** Open

### D13 — No network-change handling [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** If the user set "WiFi only" and switches from WiFi to mobile mid-download, the active download is not paused. WorkManager constraints only apply at enqueue time, not during execution.
**Impact:** A download started on WiFi continues on mobile data if WiFi drops, contradicting the "WiFi only" setting.
**Location:** `app/src/main/java/app/anikuta/download/DownloadWorker.kt` (no network monitoring during execution)
**Status:** Open

### D14 — No download speed indicator [LOW]

**Severity:** LOW
**Subsystem:** Download UI
**Description:** The queue screen shows progress percentage but no download speed (e.g., "2.3 MB/s").
**Impact:** Users can't tell if a download is stalling or progressing at a reasonable speed.
**Location:** `app/src/main/java/app/anikuta/ui/download/DownloadQueueScreen.kt` (no speed display)
**Status:** Open

### D15 — No delete-after-watching implementation [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** `DownloadPreferences.deleteAfterWatching()` exists (default false) but no logic implements it. No confirmation dialog, no undo snackbar, no advanced rules.
**Impact:** Feature is a no-op. The user requested: NO auto-delete; confirm with user; 10-20s undo window; advanced rules (delete when watching next-next episode) deferred.
**Location:** `app/src/main/java/app/anikuta/download/DownloadPreferences.kt` L79; no consumer anywhere
**Status:** Open (deferred to v2 per user decision)

### D16 — No DownloadCache for fast lookups [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** No `DownloadCache.kt` (in-memory index of downloaded episodes). `isEpisodeDownloaded()` calls `DownloadProvider.isEpisodeDownloaded()` which does a SAF directory listing every time — slow.
**Impact:** Checking download status on every episode row in a long episode list may cause UI jank due to repeated SAF I/O.
**Location:** Missing file; `DownloadManager.isEpisodeDownloaded()` delegates to `DownloadProvider` (SAF listing)
**Status:** Open
**Note:** `DownloadManager.queue` StateFlow partially serves this for in-queue items, but completed-downloaded episodes that have been cleared from the queue still require a SAF lookup.

### D17 — DownloadStore.add() silent dedup [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** `DownloadStore.add()` silently skips if an entry with the same `episodeUrl` already exists. No feedback to the caller.
**Impact:** Re-tapping download on an already-queued episode is a silent no-op. User gets no feedback that their tap did nothing.
**Location:** `app/src/main/java/app/anikuta/download/DownloadStore.kt` L66-73
**Status:** Open

### D18 — DownloadWorker Range-header bug (legacy, may be irrelevant) [LOW]

**Severity:** LOW
**Subsystem:** Download
**Description:** The old `DownloadWorker` (on `main`) had a Range-header corruption bug (server returns 200 instead of 206 → `raf.seek()` skips bytes → corrupt file). The new FFmpeg-based `DownloadWorker` on `player-experiment` doesn't use Range headers, so this bug may be gone. Needs verification.
**Impact:** If the old code path is still reachable, downloaded files could be corrupt. If fully replaced by FFmpeg approach, this is a non-issue.
**Location:** Legacy code on `main`; verify it's fully replaced on `player-experiment`
**Status:** Investigating

---

## Player Issues (both branches — from Session 44 code review)

### P1 — autoSelectAudioTrack DUB picks last() track [HIGH]

**Severity:** HIGH
**Subsystem:** Player
**Description:** `autoSelectAudioTrack` for DUB audio picks `last()` track instead of using language matching. Should match `preferredAudioLanguages` (jpn, eng) like subtitle selection does.
**Impact:** On multi-audio episodes, the wrong audio track may be auto-selected for DUB versions.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P2 — autoSelectSubtitleTrack resets userDisabledSubtitles [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Player
**Description:** `autoSelectSubtitleTrack` resets `userDisabledSubtitles` when MPV auto-selects a subtitle track, potentially re-enabling subtitles the user explicitly turned off.
**Impact:** User turns off subtitles → MPV auto-selects → subtitles come back on without user action.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P3 — setOptionString("sub-font") won't apply live [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Player
**Description:** `sub-font` is set via `setOptionString` (init-time only). Changing the font in subtitle settings requires a player restart to take effect.
**Impact:** Font changes in subtitle settings don't apply until the user exits and re-enters the player.
**Location:** `app/src/main/java/app/anikuta/player/AnikutaMPVView.kt`
**Status:** Deferred

### P4 — SpeedSheet doesn't persist speed [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Player
**Description:** The speed sheet changes playback speed live but doesn't persist the value to preferences. Next episode resets to 1.0×.
**Impact:** Users must re-set their preferred speed every episode.
**Location:** `app/src/main/java/app/anikuta/player/controls/sheets/PlayerSheets.kt` (SpeedSheet)
**Status:** Deferred

### P5 — isFirstFileLoad resume bug [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Player
**Description:** `isFirstFileLoad` is only true on the first file load of the Activity. Switching to a different (previously half-watched) episode from the in-player list starts from 0, not the saved position.
**Impact:** Resume position only works for the first episode played in a session. Switching episodes in the player doesn't resume from where the user left off.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt` ~L548
**Status:** Deferred

### P6 — mpvInitialized flag never checked [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** A `mpvInitialized` flag exists but is never checked before calling MPV commands. If MPV isn't initialized yet, commands silently fail or crash.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P7 — cycleAudioDelay dead branch [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `cycleAudioDelay` has a dead code branch that can never execute.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P8 — syncToAniList uses MainScope() instead of lifecycleScope [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `syncToAniList` creates a `MainScope()` instead of using `lifecycleScope`, which could leak if the activity is destroyed before the sync completes.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P9 — SubtitleStatusPill is dead code [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `SubtitleStatusPill` is defined in `PlayerScreen.kt` but never invoked from the main composable.
**Location:** `app/src/main/java/app/anikuta/player/PlayerScreen.kt` ~L947-1010
**Status:** Deferred

### P10 — PlayerControls.kt appears unused [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `PlayerControls.kt` (265 lines) is a standalone basic overlay that `PlayerScreen` doesn't use (it uses `FullscreenControls` and `MinimizedControls` instead). Likely dead code from the early single-mode player.
**Location:** `app/src/main/java/app/anikuta/player/controls/PlayerControls.kt`
**Status:** Deferred

### P11 — destroy() via reflection [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `onDestroy` calls `destroy()` via reflection (try view's method, then `MPVLib.destroy()`). Fragile — aniyomi calls `player.destroy()` directly.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P12 — Deprecated audio focus API [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** Uses deprecated `requestAudioFocus(listener, stream, hint)` API. aniyomi uses modern `AudioFocusRequestCompat` which also handles `LOSS_TRANSIENT_CAN_DUCK` (duck volume).
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt`
**Status:** Deferred

### P13 — No PiP remote actions [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** PiP window has no play/pause/next/prev/skip buttons. aniyomi has full `PipActions.kt` with `RemoteAction`s + a `BroadcastReceiver`.
**Location:** `app/src/main/java/app/anikuta/player/PlayerActivity.kt` (enterPiP / onUserLeaveHint)
**Status:** Deferred

### P14 — PlayerActivity is 2,360 lines [LOW]

**Severity:** LOW
**Subsystem:** Player
**Description:** `PlayerActivity.kt` concentrates ~90% of player logic in one file. Should be extracted into a `PlayerController` + `VideoResolver` to reduce Activity weight and improve testability.
**Status:** Deferred

---

## Architecture / Security Issues

### A1 — Hardcoded Supabase service_role JWT [CRITICAL]

**Severity:** CRITICAL
**Subsystem:** Security
**Description:** The Supabase service_role JWT is hardcoded in source (`SupabaseClient.kt:46`). Anyone with the APK can extract it and bypass Row-Level Security on the shared Supabase cache table.
**Impact:** A malicious user could read/write/delete all shared cache data for all ANI-KUTA users. The service_role key bypasses all RLS policies.
**Location:** `app/src/main/java/app/anikuta/data/supabase/SupabaseClient.kt` ~L46
**Status:** Open
**Fix direction:** Move the key to `BuildConfig` from a gitignored `local.properties` file, or proxy all writes through a serverless function (Cloudflare Worker / Supabase Edge Function) that validates the request before using the service_role key server-side.

### A2 — Duplicate DownloadPreferences classes [MEDIUM]

**Severity:** MEDIUM
**Subsystem:** Architecture
**Description:** Two classes named `DownloadPreferences` exist:
1. `app.anikuta.download.DownloadPreferences` — Phase 7 priority lists, registered in DI
2. `app.anikuta.domain.download.service.DownloadPreferences` — aniyomi port, NOT registered in DI
They share the name but live in different packages, expose different prefs, and use different preference keys. `FilterEpisodesForDownload` and `DeleteAnimeCategory` reference the unregistered one.
**Impact:** Confusing. If any future code calls `Injekt.get<DownloadPreferences>()` expecting the domain one, it'll get the app one (or crash). Silent DI failures possible when Phase 6 (auto-download) lights up.
**Location:** `app/src/main/java/app/anikuta/download/DownloadPreferences.kt`; `domain/src/main/java/app/anikuta/domain/download/service/DownloadPreferences.kt`
**Status:** Open
**Fix direction:** Consolidate into a single class, or rename the domain one to `AnimeDownloadPreferences` to avoid collision.

### A3 — No R8 / minification [LOW]

**Severity:** LOW
**Subsystem:** Build
**Description:** `isMinifyEnabled = false` for all build types (debug, release, release-debuggable). No R8/ProGuard.
**Impact:** APK is larger than necessary; dead-code elimination is off; no code obfuscation (minor security benefit for the Supabase key issue A1).
**Location:** `app/build.gradle.kts`
**Status:** Deferred

### A4 — No release signing [LOW]

**Severity:** LOW
**Subsystem:** Build
**Description:** `release` build type uses AGP's default debug signing. The planned `anikuta-debug` keystore is documented but not implemented.
**Impact:** Update-over-install across machines won't work. Users must uninstall + reinstall to update.
**Location:** `app/build.gradle.kts`; `SETUP/README.md` (documents the plan)
**Status:** Deferred

### A5 — Single ABI (arm64-v8a only) [LOW]

**Severity:** LOW
**Subsystem:** Build
**Description:** Only `arm64-v8a` ABI is built. No x86 / armeabi-v7a.
**Impact:** Won't run on x86 emulators or older 32-bit devices. Intentional for APK size, but limits the test matrix.
**Location:** `app/build.gradle.kts` L23
**Status:** Deferred (intentional)

### A6 — DetailViewModel is 1,018+ lines [LOW]

**Severity:** LOW
**Subsystem:** Architecture
**Description:** `DetailViewModel.kt` has in-memory caches (`episodeCache`, `videoCache`) that bypass the SQLDelight layer. Should eventually consolidate into `EpisodeRepository` + `AnimeHistoryRepository`.
**Status:** Deferred

### A7 — EpisodeMetadataFetcher not DI-injected [LOW]

**Severity:** LOW
**Subsystem:** Architecture
**Description:** `EpisodeMetadataFetcher` is instantiated directly in `DetailViewModel` rather than going through DI. Should be a singleton.
**Location:** `app/src/main/java/app/anikuta/ui/detail/DetailViewModel.kt` ~L412
**Status:** Deferred

### A8 — FileProvider authority mismatch [LOW]

**Severity:** LOW
**Subsystem:** Storage
**Description:** `util/storage/FileExtensions.kt` `File.getUriCompat()` uses `context.packageName + ".provider"` as the FileProvider authority, but `AndroidManifest.xml` registers it as `${applicationId}.fileprovider`. These don't match — `getUriCompat()` would fail if called.
**Location:** `app/src/main/java/app/anikuta/util/storage/FileExtensions.kt`; `app/src/main/AndroidManifest.xml`
**Status:** Deferred (pre-existing, not blocking downloads)

---

## Summary by severity

| Severity | Count | IDs |
|---|---|---|
| CRITICAL | 3 | D1, D2, A1 |
| HIGH | 4 | D3, D4, D5, P1 |
| MEDIUM | 8 | D6, D7, D8, D9, P2, P3, P4, P5, A2 |
| LOW | 16 | D10-D18, P6-P14, A3-A8 |

## Priority recommendation (for discussion)

**Must fix before downloads are usable:**
1. D1 (concurrency bug) — downloads are 1-at-a-time despite the setting
2. D2 (foreground service) — downloads die when app is backgrounded
3. D3 (notifications) — no user feedback without these

**Should fix for good UX:**
4. D4 + D5 (progress + file size) — user explicitly requested these
5. D7 + D8 (download all + long-press) — user explicitly requested these
6. D6 (pause/resume) — user explicitly requested this

**Security:**
7. A1 (Supabase JWT) — should be addressed before any public release

**Everything else:** defer to after the download system is solid.
