# Download Functionality — Implementation Plan

> Status: Planning phase
> Created: 2026-07-12 (Session 30)
> Branch: Will be implemented on `player-experiment`, merged to `main` when stable

---

## Overview

ANI-KUTA will have a robust download system that:
- Downloads episodes (video + subtitles) to the user-selected storage folder
- Uses FFmpeg to mux video + audio + subtitles into a single `.mkv` file
- Supports resume, retry, and expired-link re-resolution
- Shows a download queue page with progress, errors, and controls
- Enables offline playback via `ParcelFileDescriptor → fd://` (same approach as aniyomi)

## Technical foundation

### Dependencies (already in place — same versions as aniyomi)
- `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` — MPV native library + `Utils.findRealPath(fd)` for offline playback
- `com.github.jmir1:ffmpeg-kit:1.18` — FFmpeg for muxing video + subtitles
- `androidx.work:work-runtime-ktx:2.10.0` — WorkManager for background download jobs
- `com.github.tachiyomiorg:unifile:e0def6b3dc` — SAF file operations

### Storage infrastructure (already implemented)
- `StorageManager` — resolves SAF URI, creates `downloads/` subdir
- `StoragePreferences` — persists the user-selected folder URI
- `StorageSettingsScreen` — change folder post-onboarding

### Key technical facts
1. **mpv-lib is identical to aniyomi's** — same version `1.18.n`. `Utils.findRealPath(fd)` and `Utils.PROTOCOLS` are available for offline playback.
2. **FFmpegKit is identical to aniyomi's** — same version `1.18`. `FFmpegKitConfig.getSafParameter(context, uri, "rw")` enables writing to SAF URIs.
3. **Video URLs expire** — extension proxy URLs (`localhost:PORT/...`) die with the process. The download worker MUST re-resolve from the source.
4. **Subtitles come from the Video object** — `Video.subtitleTracks: List<Track(url, lang)>`. No separate fetch needed.

---

## User decisions (from planning discussion)

| Decision | Choice | Notes |
|----------|--------|-------|
| FFmpeg muxing | ✅ Yes | Handles HLS, embeds subtitles, matches aniyomi |
| Download button | Visible on all episodes + long-press options + "Download all" at top | Configurable: user can hide the button and use long-press only |
| Download button placement | Configurable in detail settings | Default: inside synopsis area (or left side when synopsis is "below") |
| Download queue page | "Downloads" in More section → shows queue page; Settings gear at top → download settings | Not configurable — fixed layout |
| Concurrent downloads | Default 2, configurable 1-4 in settings | |
| Auto-download new episodes | Deferred to v2 | |
| Auto-download next episode | Deferred to v2 | |
| Delete after watching | NOT auto-delete. Confirm with user. | Advanced: delete when watching next-next episode. 10-20s undo window. Deferred detail. |
| Subtitle handling | v1: mux into .mkv. Later: option to keep separate, join, or both | Configurable in settings in a future phase |
| Offline playback spike | Deferred — discuss first | mpv-lib is identical to aniyomi's, so `fd://` approach is proven |
| File size estimation | Show estimated + downloaded size, updating during download | Based on Content-Length header + FFmpeg progress callbacks |

---

## Implementation phases

### Phase 1: Foundation (data models + provider + resolver)

**Goal:** Create the data layer — no UI yet, no actual downloading. Just the plumbing.

**Files to create:**
1. `download/model/Download.kt` — data class with status enum, progress flow
2. `download/DownloadProvider.kt` — path scheme (`downloads/<source>/<anime>/<episode>/`)
3. `download/DownloadVideoResolver.kt` — re-resolve Video from source (handles expired links)
4. `util/storage/DiskUtil.kt` — filename sanitization (port from aniyomi)
5. `util/storage/FFmpegUtils.kt` — SAF URI → FFmpeg parameter string (port from aniyomi)

**Files to modify:**
6. `download/DownloadStore.kt` — rewrite to use new Download model + SharedPreferences persistence
7. `di/AppModule.kt` — register new singletons

**Verification:** Unit-test the provider path scheme + the resolver against a real source.

---

### Phase 2: Download engine (FFmpeg + WorkManager)

**Goal:** Actually download an episode end-to-end (no UI — trigger from code/ADB).

**Files to create:**
8. `download/DownloadDownloader.kt` — the download engine. Owns the queue, schedules concurrent downloads, calls FFmpeg, handles retry/resume.
9. `download/DownloadNotifier.kt` — progress + error notifications
10. `download/DownloadCache.kt` — in-memory index of downloaded episodes

**Files to modify:**
11. `download/DownloadManager.kt` — rewrite as public facade (enqueue, pause, resume, cancel, retry, query)
12. `download/DownloadWorker.kt` — rewrite as WorkManager CoroutineWorker + foreground service
13. `data/notification/Notifications.kt` — add download notification channels
14. `AndroidManifest.xml` — add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions
15. `App.kt` — create notification channels on startup

**Verification:** Trigger a download via ADB / test code. Verify the .mkv file appears in the SAF folder with embedded subtitles.

**Key technical details:**
- FFmpeg command: `-i <videoUrl> -i <sub1.vtt> -i <sub2.vtt> -map 0:v -map 0:a? -map 1:s? -c copy <output.mkv>`
- For HLS streams: FFmpeg handles the .m3u8 natively (no need to parse segments)
- Progress: `FFmpegKitConfig.enableStatisticsCallback` → `statistics.getTime()` → percentage based on video duration
- File size estimation: `Content-Length` header from the video URL (if available) + FFmpeg statistics
- Resume: if a partial `.mkv` exists in the tmp dir, delete it and restart (FFmpeg can't resume mid-mux)
- Retry: 3 attempts with 2/4/8s backoff. On final failure, mark as ERROR + notification.
- Expired link: `DownloadVideoResolver.resolve()` re-calls `source.getHosterList(episode)` + applies priority prefs
- Concurrency: `Semaphore(maxConcurrentDownloads)` — default 2, configurable 1-4
- Disk space check: 200 MB minimum free space before starting

---

### Phase 3: Offline playback

**Goal:** Play a downloaded episode without network.

**Files to create:**
16. `player/PlayerUtils.kt` — `Uri.resolveUri(context)` + `Uri.openContentFd(context)` (port from aniyomi)

**Files to modify:**
17. `player/PlayerActivity.kt` — resolve `content://` URIs via `openContentFd` before `loadfile`; skip video re-resolution for downloaded episodes; skip `loadExternalTracks()` if .mkv has embedded subtitles
18. `ui/detail/DetailViewModel.kt` — check `isEpisodeDownloaded` before resolving videos; build `PlayRequest` with local URI if downloaded

**Verification:** Download an episode → turn off network → play it → subtitles appear.

**Key technical details:**
- `Uri.openContentFd(context)` → `contentResolver.openFileDescriptor(uri, "r")` → `detachFd()` → `Utils.findRealPath(fd)` → returns `"fd://<fd>"` or a real file path
- MPV plays `fd://<fd>` natively (same as aniyomi)
- If the .mkv has embedded subtitles (from FFmpeg muxing), MPV auto-loads them — no `sub-add` needed
- Add `EXTRA_IS_DOWNLOADED` to PlayerActivity intent to skip re-resolution

---

### Phase 4: Download UI (buttons + queue page)

**Goal:** User-facing download functionality.

**Files to create:**
19. `ui/download/DownloadQueueScreen.kt` — queue page with progress bars, pause/resume/cancel/retry
20. `ui/download/DownloadQueueViewModel.kt` — queue state, wraps DownloadManager
21. `ui/download/DownloadBadge.kt` — download icon for episode rows (queued/downloading/done/error states)

**Files to modify:**
22. `ui/detail/DetailScreen.kt` — add DownloadBadge to episode rows + "Download all" button + long-press menu
23. `ui/detail/DetailViewModel.kt` — add `downloadEpisode()`, `downloadAllEpisodes()`, `cancelDownload()`, `deleteDownload()`, `isEpisodeDownloaded()` + download status flows
24. `ui/settings/MoreScreen.kt` / `SettingsHomeScreen.kt` — "Downloads" routes to queue page (not settings)
25. `navigation/AnikutaNavGraph.kt` — add download queue route
26. `ui/settings/DownloadsSettingsScreen.kt` — add gear icon at top linking to settings subpage; add concurrent downloads selector (1-4)

**Verification:** Full end-to-end: tap download → see progress in queue → download completes → play offline → subtitles work.

**UI details:**
- Episode row: small download icon (⬇) on the right side. States: not-downloaded (gray), queued (gray pulse), downloading (blue with %), downloaded (green ✓), error (red !).
- Long-press episode row → bottom sheet: "Download" / "Cancel download" / "Delete download" / "Play downloaded"
- "Download all" button at top of episodes section → downloads all (or filtered: unwatched only)
- Download queue page: grouped by anime, each row shows episode title + status + progress bar + cancel/pause/retry
- Queue page top bar: "Pause all" / "Resume all" / "Clear completed" + Settings gear icon
- Settings gear → DownloadsSettingsScreen (existing priority lists + concurrent downloads selector + wifi-only)

---

### Phase 5: Polish + edge cases

**Goal:** Handle all the edge cases and polish the UX.

**Tasks:**
- Handle partial downloads (half-downloaded video): delete tmp dir on retry, restart from scratch
- Handle storage revoked (user changed folder): show "Storage not available" banner, prompt re-select
- Handle network changes (wifi → mobile): pause if wifi-only is set, resume when wifi reconnects
- Handle app kill during download: WorkManager restores the job, DownloadStore restores the queue
- File size estimation: show "≈ 350 MB" estimated + "120 MB downloaded" updating in real-time
- Download notifications: progress bar in notification, "Downloading: <anime> - Episode <n>", tap to open queue
- Error notifications: "Download failed: <episode>" with "Retry" action
- Download speed indicator: "2.3 MB/s" in the queue row
- .nomedia file in downloads dir (already handled by StorageManager)

---

### Phase 6 (v2): Advanced features

**Deferred to v2:**
- Auto-download new episodes (with category filters)
- Auto-download next episode while watching
- Delete after watching (confirm dialog + advanced rules: delete when watching next-next episode)
- Subtitle handling options (separate .vtt files vs. muxed vs. both)
- External downloader integration (1DM/ADM)
- Download scheduler (only download during specific hours)
- Download over charging only

---

## File summary

### New files (21)

| # | File | Phase | Responsibility |
|---|------|-------|----------------|
| 1 | `download/model/Download.kt` | 1 | Data class + status enum + progress flow |
| 2 | `download/DownloadProvider.kt` | 1 | Path scheme: `downloads/<source>/<anime>/<episode>/` |
| 3 | `download/DownloadVideoResolver.kt` | 1 | Re-resolve Video from source (expired links) |
| 4 | `util/storage/DiskUtil.kt` | 1 | Filename sanitization |
| 5 | `util/storage/FFmpegUtils.kt` | 1 | SAF URI → FFmpeg parameter |
| 6 | `download/DownloadDownloader.kt` | 2 | Download engine (FFmpeg + queue + retry) |
| 7 | `download/DownloadNotifier.kt` | 2 | Progress + error notifications |
| 8 | `download/DownloadCache.kt` | 2 | In-memory downloaded-episodes index |
| 9 | `player/PlayerUtils.kt` | 3 | `Uri.resolveUri()` + `openContentFd()` for offline playback |
| 10 | `ui/download/DownloadQueueScreen.kt` | 4 | Queue page UI |
| 11 | `ui/download/DownloadQueueViewModel.kt` | 4 | Queue state |
| 12 | `ui/download/DownloadBadge.kt` | 4 | Download icon for episode rows |

### Modified files (14)

| # | File | Phase | Changes |
|---|------|-------|---------|
| 13 | `download/DownloadStore.kt` | 1 | Rewrite: new Download model + persistence |
| 14 | `di/AppModule.kt` | 1 | Register new singletons |
| 15 | `download/DownloadManager.kt` | 2 | Rewrite: public facade |
| 16 | `download/DownloadWorker.kt` | 2 | Rewrite: WorkManager CoroutineWorker |
| 17 | `data/notification/Notifications.kt` | 2 | Add download channels |
| 18 | `AndroidManifest.xml` | 2 | Foreground service permissions |
| 19 | `App.kt` | 2 | Create notification channels |
| 20 | `player/PlayerActivity.kt` | 3 | `fd://` resolution + skip re-resolution for downloaded |
| 21 | `ui/detail/DetailViewModel.kt` | 3+4 | Download actions + offline playback path |
| 22 | `ui/detail/DetailScreen.kt` | 4 | Download badges + long-press menu + "Download all" |
| 23 | `ui/settings/MoreScreen.kt` | 4 | Downloads → queue page |
| 24 | `navigation/AnikutaNavGraph.kt` | 4 | Download queue route |
| 25 | `ui/settings/DownloadsSettingsScreen.kt` | 4 | Gear icon + concurrent downloads selector |
| 26 | `download/DownloadPreferences.kt` | 4 | Add `concurrentDownloads()` pref (1-4) |

---

## Rules for implementation

1. **Analyze** each component before implementing
2. **Verify** against the aniyomi reference code (same dependencies, same patterns)
3. **Build incrementally** — each phase should produce a testable APK
4. **One issue at a time** — don't batch unrelated changes
5. **Test on `player-experiment`** — merge to `main` only when stable
6. **Document** in WORKLOG.md after each phase
9. **ntfy.sh** notification on each build completion

---

## mpv-lib comparison

| Aspect | ANI-KUTA | Aniyomi |
|--------|----------|---------|
| Dependency | `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` | Same |
| FFmpegKit | `com.github.jmir1:ffmpeg-kit:1.18` | Same |
| `Utils.findRealPath(fd)` | ✅ Available | ✅ Used for offline playback |
| `Utils.PROTOCOLS` | ✅ Available | ✅ Used for URI scheme detection |
| `BaseMPVView` | ✅ Same | ✅ Same |
| `MPVLib.command` | ✅ Same | ✅ Same |

**Conclusion:** Our mpv-lib is identical to aniyomi's. The `fd://` offline playback approach is proven to work with this exact library version. No spike needed — we can proceed with confidence.
