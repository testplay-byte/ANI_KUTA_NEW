# Download System — Implementation Plan (v2 — Refined)

> **Status:** Planning — NOT ready for execution yet. Awaiting user confirmation on open questions.
> **Branch:** `player-experiment`
> **Based on:** User's draft improvements + actual code analysis + user's latest feedback (resume requirement, modular design, UI redesign, bug reports)
> **Created:** 2026-07-13 (v2)
> **Supersedes:** v1 of this plan + `DOCS/PLAN/DOWNLOAD-PLAN.md`

---

## 0. The mpv-lib question (answered)

**User asked:** "What is different between our lib and the aniyomi lib?"

**Answer: There is NO difference. We use the exact same library.**

| | ANI-KUTA | aniyomi |
|---|---|---|
| Maven coordinate | `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` | identical |
| Version | `1.18.n` | identical |
| `Utils.findRealPath(fd)` | ✅ available + already used in `PlayerUtils.kt` | ✅ used |
| `Utils.PROTOCOLS` | ✅ available + already used in `PlayerUtils.kt` | ✅ used |

**The `fd://` offline playback path is already implemented and wired** at all 4 `loadfile` call sites in `PlayerActivity.kt` (lines 1259, 1645, 1875, 2014) via `resolveUrlForMpv()`. No spike needed.

---

## 1. Current state assessment (honest)

The download system exists on `player-experiment` but **is not functioning properly**. The user confirmed: "It does not handle the basic things properly. It does not manage the things as required."

### What's built (code exists but has bugs)

| Component | File | State |
|---|---|---|
| Download model | `download/Download.kt` | ⚠️ Missing `PAUSED` + `RESOLVING` states |
| Path scheme | `download/DownloadProvider.kt` | ✅ Works (SAF, `downloads/<source>/<anime>/<episode>/`) |
| Video resolver | `download/DownloadVideoResolver.kt` | ✅ Works (re-resolves expired links) |
| Offline playback | `player/PlayerUtils.kt` | ✅ Works (`fd://` via `openContentFd`) |
| Queue manager | `download/DownloadManager.kt` | ⚠️ Queue StateFlow doesn't emit on status changes (only on add/remove) — see bug B3 |
| Download engine | `download/DownloadWorker.kt` | ❌ Concurrency broken (D1), no foreground service (D2), no resume, hardcoded progress (D4) |
| Persistent store | `download/DownloadStore.kt` | ⚠️ Silent dedup (D17) |
| Queue UI | `ui/download/DownloadQueueScreen.kt` | ❌ Poor UI, no pause/resume, no size, no speed |
| Download button | `ui/detail/DetailScreen.kt` | ❌ Wrong placement, stationary spinner bug, no long-press |
| Notifications | `data/notification/Notifications.kt` | ❌ Stub — no channels, no notifications |
| Manifest permissions | `AndroidManifest.xml` | ❌ No `FOREGROUND_SERVICE` permissions |
| DI registration | `di/AppModule.kt` | ⚠️ Missing `DownloadNotifier`, `DownloadCache` |

### Specific bugs reported by the user (with root causes from code analysis)

#### B1 — Stationary spinner when clicking download

**Symptom:** Clicking the download button shows a stationary (not spinning) circular progress wheel.

**Root cause (confirmed from code):**
`DetailScreen.kt` lines 731-738:
```kotlin
app.anikuta.download.Download.State.QUEUE -> {
    IconButton(onClick = onDownload) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            progress = { 0.3f },  // ← DETERMINATE at 30%, NOT indeterminate
        )
    }
}
```
The `progress = { 0.3f }` parameter makes it a **determinate** indicator stuck at 30% — it looks stationary because it never changes. An indeterminate spinner omits the `progress` parameter entirely.

**Fix direction:** Remove `progress = { 0.3f }` so it becomes indeterminate during QUEUE/RESOLVING. Add a new `RESOLVING` state for the "checking if downloadable" phase. Show determinate progress only during DOWNLOADING.

#### B2 — Progress stays at 0% in downloads page

**Symptom:** Downloads page shows the episode is downloading but progress stays at 0% even though the download is happening in the background.

**Root causes (3 compounding issues):**
1. **Hardcoded duration estimate:** `DownloadWorker.kt` line 266 — `estimatedDurationMs = 24 * 60 * 1000L` (24 minutes). If the actual video isn't exactly 24 minutes, progress is wrong.
2. **Statistics callback may not fire:** `FFmpegKitConfig.enableStatisticsCallback` fires on a separate thread. If FFmpeg processes slowly or the callback registration timing is off, progress never updates.
3. **Queue StateFlow doesn't re-emit on progress change:** `DownloadManager.queue` is a `MutableStateFlow<List<Download>>`. It only emits when the list reference changes (add/remove). When `download.progress` changes via the internal `progressFlow`, the queue list itself doesn't change, so the queue flow doesn't re-emit. The `DownloadQueueScreen` collects `download.progressFlow` directly (so it SHOULD work), but the `DetailViewModel.downloadStatus` map (derived from `queue`) does NOT update on progress changes.

**Fix direction:** Use segment-based progress (accurate, not time-estimated). Ensure progress updates flow correctly to both the queue UI and the detail screen.

#### B3 — Spinning circle only appears after re-entering the detail page

**Symptom:** Click download → nothing visible happens. Leave the detail page and come back → THEN the spinning circle appears.

**Root cause:**
`DetailViewModel.kt` lines 800-806:
```kotlin
val downloadStatus: StateFlow<Map<String, Download.State>> =
    downloadManager?.queue?.let { queueFlow ->
        MutableStateFlow<Map<String, Download.State>>(emptyMap()).also { state ->
            viewModelScope.launch {
                queueFlow.collect { downloads ->
                    state.value = downloads.associate { it.episodeName to it.status }
                }
            }
        }
    }
```
The `downloadStatus` map is derived from `downloadManager.queue`, which only emits when the **list structure** changes (add/remove). When a download's `status` changes from QUEUE → DOWNLOADING (via the Download object's internal `statusFlow`), the queue list doesn't change, so `queueFlow.collect` doesn't fire, so `downloadStatus` doesn't update.

When you leave and re-enter, the ViewModel is recreated, the flow is re-collected, and the current state is read fresh — so the spinner appears.

**Fix direction:** Merge the per-download `statusFlow` into the queue observation. Instead of deriving `downloadStatus` from the queue list, observe each download's `statusFlow` and combine them. Or: make `DownloadManager.queue` re-emit when any download's status/progress changes (using `flatMapLatest` + `combine`).

#### B4 — No verification before downloading

**Symptom:** The download button immediately starts downloading without checking if the stream is actually available.

**Root cause:** There's no `RESOLVING` state. The download goes straight from QUEUE to DOWNLOADING. The `DownloadVideoResolver.resolve()` runs inside `DownloadWorker.processDownload()`, but the UI shows QUEUE (stationary spinner) the whole time — no feedback that verification is happening.

**Fix direction:** Add a `RESOLVING` state. Before downloading, verify the video URL is accessible (HEAD request or quick FFprobe). Show an indeterminate spinner with "Checking..." during this phase. If the stream isn't available, show an error immediately — don't waste time trying to download.

#### B5 — Downloads die when app is backgrounded

**Symptom:** Background the app during a download → download stops.

**Root cause:** `DownloadWorker` doesn't call `setForeground()`. On Android 12+, background work is killed after ~30 seconds. No `FOREGROUND_SERVICE` permission in the manifest. No notification channels exist.

**Fix direction:** Add foreground service with progress notification. (Issue D2 + D3 in KNOWN-ISSUES.md.)

#### B6 — Downloads run one at a time despite concurrency setting

**Symptom:** Even with `maxConcurrentDownloads = 2`, downloads are serialized.

**Root cause:** `DownloadWorker.kt` lines 62-73:
```kotlin
pending.forEach { download ->
    semaphore.acquire()      // ← blocks the single coroutine
    try {
        processDownload(download)
    } finally {
        semaphore.release()
    }
}
```
The `forEach` runs in a single coroutine. `semaphore.acquire()` blocks that coroutine before each download. So downloads run sequentially — the semaphore never has more than 1 permit in flight.

**Fix direction:** Launch each download in its own child coroutine:
```kotlin
coroutineScope {
    pending.forEach { download ->
        launch {
            semaphore.withPermit {
                processDownload(download)
            }
        }
    }
}
```

---

## 2. Agreed decisions (from user's draft improvements + latest feedback)

### 2.1 Download button behavior
- **Single tap** → immediately starts downloading (after verification)
- **Long press** → bottom sheet: Download / Cancel / Delete / Play downloaded
- **"Download all"** → deferred (will be in a 3-dots overflow menu at top-right of detail page, configured later)
- **Configurable:** user can hide the button (use long-press only)

### 2.2 Download button placement — **CLARIFIED**

**Per-episode download button** goes **inside each episode's description/synopsis area**, on the **right side**. NOT in the top anime synopsis — inside the **episode description**.

- **When episode has a description/synopsis:** download button is placed inside the description area, on the right side, in a dedicated section
- **When episode has NO description:** download button is placed inside the episode row at a consistent fallback position (top-right corner of the episode row) — this fallback is the SAME for all episodes without descriptions (constant, not dynamic)
- **Configurable** in detail settings (user can choose placement / hide the button)
- **Current placement (button rendered after the episode row) is incorrect** — must be moved INTO the episode row's description area
- **This is temporary** — may be revisited in the future

**Note:** The episode "synopsis" may be referred to as "episode description" — same thing. Each episode can optionally have a summary/description shown in its row.

### 2.3 FFmpeg muxing + subtitle handling
- **v1 (now):** Mux video + audio + subtitles into one `.mkv` (current behavior)
- **v2 (later):** User can choose: keep subtitles separate / join / both
- **Deferred** to a future planning phase

### 2.4 Storage
- `downloads/<source>/<anime>/<episode>/` via SAF — **keep as-is**
- May revisit later

### 2.5 Offline playback
- `fd://` via `ParcelFileDescriptor` → `Utils.findRealPath` — **already works**
- mpv-lib is identical to aniyomi's

### 2.6 Download queue page
- **More → Downloads** → shows the **queue page** (NOT settings) by default
- **Settings** accessible from within the queue page (NOT a big old-style button — needs better UI)
- **NOT configurable** — fixed layout

### 2.7 Resume / retry — **KEY REQUIREMENT**
- **100% resume capability required** — user explicitly stated this is non-negotiable
- If user pauses → resume from exact same point
- If network drops → after hours, resume from exact same point
- If data is corrupt → delete the corrupt part, resume from the last good point
- **This requires a new download architecture** (segment-based, not single-pass FFmpeg) — see §3

### 2.8 Notifications
- Foreground service notification with progress
- Error notifications with retry action
- Must be **beautiful and proper** (Material 3 style)

### 2.9 File size estimation
- Show **estimated total** + **downloaded so far**, updating in real-time
- Based on Content-Length + segment sizes

### 2.10 Concurrent downloads
- **Default: 2**, **configurable: 1-4**

### 2.11 Auto-download (new episodes + next episode)
- **Deferred to v2** — will discuss in a future planning phase

### 2.12 Delete after watching
- **NO auto-delete**
- Confirm with user: "Do you want to delete this episode?"
- **10-20 second undo window** (NOT 5 seconds)
- Advanced rules (delete when watching next-next episode) — deferred
- **Deferred** to a later phase entirely

### 2.13 Modular design — **KEY REQUIREMENT**
- User explicitly wants: easy to edit, easy to manage, easy to change without breaking things
- The current `DownloadWorker` does too much in one file — must be split into focused modules
- See §4 for the proposed architecture

### 2.14 Downloads page UI redesign
- Current UI is **not good** — old design style, poor layout
- Must use **Material 3 Expressive** design language
- Match the style of existing settings screens (`SettingsSubpageScaffold`, `SettingsGroupCard`, etc.)
- Empty state must also look good (not just "No downloads" text)
- See §5 for the proposed design

### 2.15 Console logging
- **Proper console logging** at each step so we can debug
- User will provide logs to help diagnose issues

---

## 3. Partial download resume — architecture design

### 3.1 The problem

The current `DownloadWorker` uses FFmpeg with `-c copy` to mux video + subtitles into one `.mkv` in a single pass. **FFmpeg cannot resume mid-mux** — if it fails at 90%, the output file is incomplete/corrupt and must be restarted from 0%.

The user requires 100% resume capability. This means we need a fundamentally different approach.

### 3.2 Proposed architecture: Segment-based download with manifest

**Core idea:** Split the video into small segments (e.g., 10-second chunks). Download each segment independently. Track completion in a manifest file. On resume, skip completed segments and download only missing ones. Once all segments are done, mux them into the final `.mkv`.

**Directory structure:**
```
downloads/<source>/<anime>/<episode>/
├── manifest.json              ← tracks segment state (the key to resume)
├── tmp/
│   ├── segments/
│   │   ├── seg_000.ts         ← 10-second segment
│   │   ├── seg_001.ts
│   │   ├── seg_002.ts
│   │   └── ...
│   └── subtitles/
│       ├── en.vtt             ← downloaded once, no resume needed
│       └── ...
└── video.mkv                  ← final muxed file (created only when all segments done)
```

**manifest.json structure:**
```json
{
  "downloadId": "dl_123_1_1234567890",
  "videoUrl": "<resolved URL at resolution time>",
  "videoHeaders": "...",
  "totalDurationMs": 1440000,
  "segmentDurationSec": 10,
  "totalSegments": 144,
  "segments": [
    { "index": 0, "status": "done", "file": "seg_000.ts", "sizeBytes": 1234567 },
    { "index": 1, "status": "done", "file": "seg_001.ts", "sizeBytes": 1234567 },
    { "index": 2, "status": "partial", "file": "seg_002.ts", "sizeBytes": 500000 },
    { "index": 3, "status": "pending", "file": "seg_003.ts", "sizeBytes": 0 }
  ],
  "subtitles": [
    { "url": "...", "lang": "en", "downloaded": true, "file": "subtitles/en.vtt" },
    { "url": "...", "lang": "es", "downloaded": false, "file": "subtitles/es.vtt" }
  ],
  "totalSizeBytes": 350000000,
  "downloadedBytes": 240000000,
  "createdAt": 1234567890,
  "updatedAt": 1234567890
}
```

### 3.3 Download flow (state machine)

```
RESOLVING (5-10s)
  │  1. Resolve video URL via DownloadVideoResolver
  │  2. Run FFprobe to get total duration + stream info
  │  3. Calculate segment count = ceil(duration / 10s)
  │  4. Create episode dir + manifest.json + tmp/segments/
  │  5. Download subtitle files (small, fast)
  │
  ▼
DOWNLOADING (main phase)
  │  For each segment (0 → totalSegments-1):
  │    - Read manifest → if "done", skip
  │    - If "partial" → delete partial file, set "pending"
  │    - Download segment: ffmpeg -ss <startTime> -t 10 -i <url> -c copy seg_XXX.ts
  │    - Verify segment (size > 0, FFprobe can read it)
  │    - On success: update manifest → "done", update downloadedBytes
  │    - On failure: retry 3×, then mark segment as "error"
  │    - Progress = completedSegments / totalSegments × 100
  │    - If PAUSED: stop after current segment, save manifest
  │
  ▼
MUXING (5-15s)
  │  1. Create concat list: file 'seg_000.ts' \n file 'seg_001.ts' ...
  │  2. ffmpeg -f concat -safe 0 -i concat.txt -i subtitles/en.vtt ...
  │     -map 0:v -map 0:a? -map 1:s? -c copy video.mkv
  │  3. Verify final .mkv (FFprobe can read it, duration matches)
  │  4. On success: delete tmp/ directory, keep video.mkv
  │  5. Mark download as DOWNLOADED
  │
  ▼
DOWNLOADED ✅
```

### 3.4 Resume flow

When the app starts or a download is retried:
1. Read `manifest.json` from the episode directory
2. If manifest doesn't exist → fresh download (RESOLVING)
3. If manifest exists:
   - Count segments with status "done" → these are skipped
   - Find first "partial" segment → delete its file, mark "pending"
   - Resume from the first "pending" segment
   - If all segments are "done" but no `video.mkv` exists → go to MUXING
   - If `video.mkv` exists → already DOWNLOADED

### 3.5 Corruption handling

- Before skipping a "done" segment, verify its file exists and size > 0
- If a segment file is missing or zero-size → mark "pending", re-download
- Optionally: FFprobe each segment before muxing to verify it's valid
- If the final `video.mkv` is corrupt → delete it, go back to MUXING (segments are still there)

### 3.6 Why segment-based (not HTTP byte-range)

| Approach | Direct URLs | HLS/m3u8 | Resume | Complexity |
|---|---|---|---|---|
| HTTP byte-range | ✅ | ❌ (HLS is segment-based natively) | ✅ | Medium |
| Segment-based (FFmpeg -ss -t) | ✅ | ✅ | ✅ | Higher |
| Single-pass FFmpeg (current) | ✅ | ✅ | ❌ | Low |

Most extension video URLs are either direct file URLs or HLS. The segment-based approach works for **both** uniformly, which is why it's the recommended choice. HTTP byte-range would be faster for direct URLs but doesn't work for HLS, requiring a hybrid approach (more complex to maintain).

### 3.7 Segment duration tradeoff

| Duration | Pros | Cons |
|---|---|---|
| 5 seconds | Very granular resume | Many files, more manifest I/O |
| 10 seconds | Good balance (recommended) | — |
| 30 seconds | Fewer files, less overhead | Less granular resume (up to 30s re-download) |

**Recommendation: 10 seconds** — a 24-minute episode = 144 segments. Each segment is ~2-3 MB. Re-downloading on interruption wastes at most 10 seconds of progress.

### 3.8 Potential issues + mitigations

| Issue | Mitigation |
|---|---|
| Keyframe alignment (segments may not start exactly at keyframe) | FFmpeg `-ss` with `-c copy` seeks to nearest keyframe — playback is seamless after concat. Test with real episodes. |
| Segment concatenation timeline gaps | Use FFmpeg `-f concat` demuxer which handles timestamps correctly. |
| Manifest corruption (app crash during write) | Write manifest atomically: write to `manifest.json.tmp`, then rename. |
| Disk space (segments + final file) | Check disk space before starting. Segments are deleted after muxing. Peak usage ≈ 2× final file size. |
| Extension proxy URLs (localhost:PORT) | These die with the app process. On resume, `DownloadVideoResolver` re-resolves from the source. The manifest stores `videoUrl` but the resolver ignores it and fetches a fresh one. |

---

## 4. Modular architecture design

### 4.1 Principle
The user wants a design that is "easy to edit in the future, easy to manage, and easy to make changes without affecting functionality." This means **single-responsibility modules** with clear interfaces.

### 4.2 Proposed file structure

```
download/
├── Download.kt                        ← data model + state enum (add RESOLVING, PAUSED)
├── DownloadManager.kt                 ← public facade (enqueue, pause, resume, cancel, retry)
├── DownloadStore.kt                   ← persistent queue state (PreferenceStore)
├── DownloadProvider.kt                ← path scheme (directories) — unchanged
├── DownloadPreferences.kt             ← download settings — unchanged + new prefs
├── DownloadVideoResolver.kt           ← re-resolve video from source — unchanged
├── DownloadNotifier.kt                ← notification management (NEW)
├── DownloadCache.kt                   ← in-memory index for fast isEpisodeDownloaded() (NEW)
│
├── engine/
│   ├── DownloadEngine.kt              ← interface: start, pause, resume, cancel, getProgress
│   ├── SegmentDownloadEngine.kt       ← segment-based implementation (§3)
│   └── DownloadManifest.kt            ← manifest read/write/validate (NEW)
│
├── worker/
│   └── DownloadWorker.kt              ← thin WorkManager wrapper (foreground service + delegation)
│
└── progress/
    └── ProgressTracker.kt             ← progress calculation + reporting (NEW)
```

### 4.3 Responsibility split

| Module | Responsibility | Does NOT do |
|---|---|---|
| `DownloadManager` | Public API. Enqueue/pause/resume/cancel/retry. Holds the live queue. | Does NOT download anything. Does NOT touch FFmpeg. |
| `DownloadWorker` | WorkManager lifecycle. Foreground service. Delegates to `DownloadEngine`. | Does NOT contain download logic. Does NOT manage segments. |
| `DownloadEngine` (interface) | The actual download strategy. `start()`, `pause()`, `resume()`, `cancel()`. | Does NOT manage WorkManager. Does NOT manage notifications. |
| `SegmentDownloadEngine` | Segment-based download implementation. Manifest management. FFmpeg segment calls. Final mux. | Does NOT manage WorkManager. Does NOT manage the queue. |
| `DownloadManifest` | Read/write/validate the manifest JSON. Segment state queries. | Does NOT download anything. |
| `ProgressTracker` | Calculates progress from segment count + downloaded bytes. Reports via Flow. | Does NOT download anything. |
| `DownloadNotifier` | Notification channel management. Progress/error/success notifications. | Does NOT download anything. |
| `DownloadCache` | In-memory `episodeUrl → downloadState` map. Fast lookups. | Does NOT download anything. |
| `DownloadStore` | Persistent queue state. CRUD on `DownloadStoreEntry`. | Does NOT download anything. |
| `DownloadProvider` | Directory paths. File lookups. | Does NOT download anything. |
| `DownloadVideoResolver` | Re-resolve video URL from source. Priority prefs. | Does NOT download anything. |

### 4.4 Why this matters

If we later want to:
- **Change the download strategy** (e.g., add HTTP byte-range for direct URLs) → implement a new `DownloadEngine`, swap it in DI. Nothing else changes.
- **Change the notification UI** → edit `DownloadNotifier`. Nothing else changes.
- **Change the progress calculation** → edit `ProgressTracker`. Nothing else changes.
- **Change the queue persistence** → edit `DownloadStore`. Nothing else changes.
- **Add a new state** (e.g., "muxing") → add to `Download.State`, update `ProgressTracker` + UI. Nothing else changes.

This is the modular design the user requested.

### 4.5 State flow (reactive)

The current bug B3 (status changes don't propagate to the UI) is fixed by making the queue reactive at the individual download level:

```kotlin
// DownloadManager
val queue: StateFlow<List<Download>>  // list reference changes on add/remove

// BUT each Download has:
val statusFlow: StateFlow<State>      // emits on status change
val progressFlow: StateFlow<Int>      // emits on progress change

// DetailViewModel should observe BOTH:
val downloadStatus: StateFlow<Map<String, Download.State>> =
    combine(
        downloadManager.queue,
        // re-collect when any download's status changes
    ) { ... }
```

The exact reactive pattern will be designed during implementation. The key principle: **status/progress changes must propagate to ALL observers (detail screen, queue screen, notifications) without requiring a page re-entry.**

---

## 5. Downloads page UI redesign (Material 3 Expressive)

### 5.1 Problems with current UI

1. **Settings button** is a `FilledTonalButton` with text + icon — looks like an old-style action button, not a modern app bar action
2. **Stat chips** (downloading/queued/done/failed counts) on the left — looks like a dashboard, not a modern download manager
3. **Flat list** of downloads — not grouped by anime, hard to scan
4. **Download cards** are basic — no depth, no visual hierarchy
5. **Empty state** is minimal — just an icon + two lines of text
6. **No pause/resume** buttons
7. **No file size or speed** display

### 5.2 Design principles (matching existing app style)

- Use `SettingsSubpageScaffold` pattern for the top bar (back button + title + action icons)
- Use card-based grouping (`SettingsGroupCard` style)
- Use tonal containers for status indicators
- Material 3 typography scale (titleLarge, bodyMedium, labelSmall)
- Rounded corners (12-16dp)
- Proper spacing (16dp horizontal padding, 8dp vertical gaps)
- Color-coded status (primary for downloading, tertiary for done, error for failed)

### 5.3 Proposed layout

```
┌─────────────────────────────────────────────┐
│ ←  Downloads                    ⏸  ⚙        │  ← Top bar: back, pause-all, settings gear
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────────────────────────┐   │  ← Summary card (only when there are downloads)
│  │  📥 Downloading 2 of 5              │   │
│  │  ━━━━━━━━━━━━━━━━━━━━ 40%          │   │  ← Overall progress bar
│  │  120 MB / 350 MB · 2.3 MB/s         │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  ┌─ Solo Leveling ─────────────────────┐   │  ← Grouped by anime
│  │  ┌─ Episode 1 ───────────────────┐  │   │
│  │  │  ━━━━━━━━━━━━━━━━━━━━ 75%     │  │   │  ← Per-episode progress
│  │  │  260 MB / 350 MB · 1.2 MB/s   │  │   │
│  │  │  ⏸  ✕                          │  │   │  ← Pause + cancel buttons
│  │  └────────────────────────────────┘  │   │
│  │  ┌─ Episode 2 ───────────────────┐  │   │
│  │  │  Queued                        │  │   │
│  │  │  ⏸  ✕                          │  │   │
│  │  └────────────────────────────────┘  │   │
│  └───────────────────────────────────────┘   │
│                                             │
│  ┌─ Frieren ────────────────────────────┐   │
│  │  ┌─ Episode 12 ──────────────────┐   │   │
│  │  │  ✅ Done                       │   │   │
│  │  │  340 MB                        │   │   │
│  │  │  🗑                              │   │   │  ← Delete button
│  │  └────────────────────────────────┘   │   │
│  └───────────────────────────────────────┘   │
│                                             │
└─────────────────────────────────────────────┘

Empty state:
┌─────────────────────────────────────────────┐
│ ←  Downloads                    ⚙           │
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│              📥                              │
│         (large icon,                        │
│          tonal container)                    │
│                                             │
│        No downloads yet                     │
│   Download episodes from the anime           │
│          detail page                         │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

### 5.4 UI components to create/modify

| Component | Purpose |
|---|---|
| `DownloadQueueScreen` (rewrite) | Main screen with top bar + summary + grouped list |
| `DownloadSummaryCard` (new) | Overall progress card (downloading X of Y, total progress, speed) |
| `DownloadGroupCard` (new) | Per-anime grouping with header |
| `DownloadQueueItem` (rewrite) | Per-episode card with progress, size, speed, actions |
| `DownloadEmptyState` (new) | Empty state with large icon + helpful text |

### 5.5 Settings access — **redesigned per user feedback**

NOT a simple gear icon. The user wants a **prominent settings button** with:
- **Rectangular shape** with **rounded edges**
- **Background elements** (a tonal bar/container, not transparent)
- **Depth effects** (shadow elevation + tonal elevation)
- Visible "Settings" label + icon

**Implementation approach:**
- Use a `Surface` or `FilledTonalButton` with:
  - `RoundedCornerShape(8-12dp)`
  - `tonalElevation = 2-3dp` (depth)
  - `shadowElevation = 1-2dp` (shadow)
  - Tonal background color (e.g., `surfaceContainerHigh` or `primaryContainer` with low alpha)
  - Settings icon + "Settings" text
- Placed in the top bar's action area (right side)
- This gives it a distinct, tactile, premium feel — not a flat icon button

---

## 6. Confirmed decisions (all questions answered)

### Q1 — Download button placement ✅ ANSWERED

**Clarified:** The download button goes **inside each episode's description/synopsis area**, on the right side — NOT in the top anime synopsis.

- Each episode has its own description (sometimes called synopsis/summary)
- Download button is placed inside that episode description area, right side, in a dedicated section
- If an episode has NO description → fallback to a consistent position (top-right of the episode row) — same for all episodes without descriptions
- This is temporary, may be revisited later
- See §2.2 for full details

### Q2 — Segment duration ✅ ANSWERED

**Confirmed: 10-second segments.**
- 24-minute episode = 144 segments
- If interrupted, re-download at most 10 seconds
- Each segment ~2-3 MB

### Q3 — Console logging depth ✅ ANSWERED

**Confirmed: Verbose (option c).**
- Everything: key milestones + FFmpeg command strings + per-segment details + manifest reads/writes + timing info
- Full detail for maximum error management and verification capability
- Log tag: `DownloadEngine`, `DownloadManifest`, `DownloadWorker`, etc. (per-module tags for easy filtering)

### Q4 — Phase sequencing ✅ CONFIRMED

**User approved the proposed order:**
1. Phase 1 — Architecture restructure (modular split, new states, reactive queue fix)
2. Phase 2 — Resume-capable segment engine + foreground service + notifications
3. Phase 3 — UI bug fixes (B1-B4) + download button move to episode description + long-press menu
4. Phase 4 — Downloads page UI redesign (Material 3 Expressive)
5. Phase 5 — Polish (configurable placement, DownloadCache, network handling, storage-revoked)
6. Phase 6 (v2) — Auto-download, delete-after-watching, subtitle options, overflow menu

**Principle: architecture first, then features, then polish.**

### Q5 — Console logs ✅ DECIDED

**Decision: Proceed with Phase 1 without waiting for current logs.**

Reasoning:
- The bugs (B1-B4) have already been root-caused from code analysis — current logs would mostly confirm symptoms we already understand
- Phase 1 (architecture restructure) and Phase 2 (new segment engine) are new code that doesn't depend on the current buggy behavior
- The most useful logs will come AFTER the new architecture is in place — to verify the new implementation works
- User is welcome to share logs at any point if they want me to look at something specific
- After Phase 2, I'll ask the user to test and share fresh logs from the new implementation

---

## 7. Segment-based progress (confirmed approach)

The user suggested using segment count as a progress indicator. This aligns exactly with the plan in §3.3:

**Progress = (completed segments / total segments) × 100**

This is a good approach because:
- **Accurate** — not time-estimated (fixes bug B2's hardcoded 24-minute issue)
- **Granular** — 144 segments for a 24-min episode ≈ 0.7% per segment
- **Naturally reflects resume state** — skipped (already-done) segments count as complete
- **Doesn't depend on FFmpeg statistics callbacks** — which were unreliable (bug B2 root cause #2)

**Phase-by-phase progress display:**
| State | Progress display |
|---|---|
| RESOLVING | Indeterminate spinner ("Checking...") — total segments unknown |
| DOWNLOADING | Determinate: completedSegments / totalSegments × 100 |
| PAUSED | Last known progress %, greyed out |
| MUXING | Indeterminate spinner ("Finalizing...") — segments done, muxing in progress |
| DOWNLOADED | 100%, green checkmark |
| ERROR | 0% or last known %, red error indicator |

---

## 8. Notifications (confirmed requirement)

The user explicitly requested notification capability with progress display. This is already included in the plan:

- **Foreground service notification** (Phase 2) — required for Android 12+ background downloads
  - Shows download progress (segment-based %)
  - Shows current episode + anime title
  - Tap to open the downloads queue page
- **Error notification** (Phase 2) — when a download fails after all retries
  - Shows error message + episode name
  - "Retry" action button
- **Completion notification** (Phase 2) — optional, when a download finishes
  - "Download complete: <episode name>"
  - Tap to play the episode

**Notification channels** (created in `App.onCreate()`):
- `CHANNEL_DOWNLOADER_PROGRESS` — low importance (progress updates)
- `CHANNEL_DOWNLOADER_ERROR` — high importance (errors)
- `CHANNEL_DOWNLOADER_COMPLETE` — default importance (completions)

**Implementation:** `DownloadNotifier.kt` (new file, Phase 2) wraps all notification logic. `DownloadWorker` calls it to update the foreground service notification during download.

---

## 9. Rules & principles (unchanged)

### 9.1 Decision-making rules
1. **No rash decisions** — analyze before acting
2. **No decisions without proper understanding** — research first, confirm with user on big changes
3. **No decisions without verification** — verify assumptions against the codebase
4. **One issue at a time** — verify, fix, document, verify again
5. **Build incrementally** — each phase should produce a testable APK

### 9.2 Error-handling protocol (when something breaks)
1. **Determine what the error is** — reproduce, read logs, understand the symptom
2. **Determine how it is affecting us** — what breaks for the user? Severity?
3. **Determine the root cause** — not the symptom, the actual cause
4. **Analyze the codebase** — understand how the root cause fits into the larger system
5. **Plan the fix** — write down what files to change, what the change is, how to verify
6. **Execute the fix** — implement, then verify (build + test)

### 9.3 Branch strategy
- All download work on `player-experiment` — **never push code to `main`**
- `main` is for documentation only (KNOWN-ISSUES.md, etc.)
- Merge to `main` only when user approves + build is verified

### 9.4 Build & verification
- **Build:** GitHub Actions (triggered on push to `player-experiment`)
- **Notification:** `ntfy.sh` topic `TASKISDONE` after build completes
- **Verification:** After each phase, build must succeed + APK must install without crashing

### 9.5 Documentation
- Update `WORKLOG.md` / `worklog.md` after each completed task
- Keep this `plan.md` updated as decisions change
- New known issues → add to `KNOWN-ISSUES.md` (on `main`)

---

## 10. What happens next

All questions are answered. The plan is complete.

**Awaiting user's final go-ahead.** Once confirmed:

1. **I begin Phase 1** (architecture restructure) — one module at a time, following the rules:
   - Create `DownloadEngine` interface
   - Create `DownloadManifest` (manifest read/write/validate)
   - Create `ProgressTracker` (segment-based progress)
   - Create `DownloadNotifier` (notification wrapper)
   - Add `RESOLVING` + `PAUSED` + `MUXING` states to `Download.kt`
   - Fix the reactive queue (B3) — make status/progress changes propagate to all observers
   - Split `DownloadWorker` into a thin WorkManager wrapper that delegates to `DownloadEngine`
   - Register new modules in DI
   - Verbose logging throughout (per-module log tags)

2. **After each task:** build → verify → `ntfy.sh` → update worklog → move to next task

3. **After Phase 1 complete:** user reviews → approve or adjust → move to Phase 2

4. **Phase 2** (segment engine + foreground service + notifications) → test → user shares fresh logs

**I will NOT start implementing until the user gives the final go-ahead.**

---

## 11. Summary of confirmed decisions

| Decision | Confirmed value |
|---|---|
| Download button placement | Inside each episode's description area, right side; fallback to top-right of episode row when no description |
| Segment duration | 10 seconds |
| Console logging | Verbose (everything, per-module tags) |
| Phase sequencing | Architecture → Engine → UI fixes → UI redesign → Polish → v2 |
| Resume capability | 100% required (segment-based with manifest) |
| Modular architecture | Required (single-responsibility modules) |
| Downloads page UI | Material 3 Expressive, grouped by anime, prominent settings button with depth |
| Notifications | Foreground service progress + error + completion notifications |
| Progress indicator | Segment-based (completed/total × 100) |
| Concurrency | Default 2, configurable 1-4 |
| Auto-download | Deferred to v2 |
| Delete after watching | Deferred (no auto-delete, confirm dialog, 10-20s undo) |
| Subtitle handling options | Deferred to v2 |
| Console logs from user | Not needed now; will request fresh logs after Phase 2 |
