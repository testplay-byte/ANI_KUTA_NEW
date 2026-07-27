# AGENT 2 (Downloads & Offline Playback) — IMPLEMENTATION PROMPT

## Credentials

```
REPO URL:   https://github.com/testplay-byte/ANI_KUTA_NEW
PAT TOKEN:  <INSERT_PAT_TOKEN_HERE>
YOUR BRANCH: feature/downloads
CLONE CMD:  git clone -b feature/downloads "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
NTFY:       https://ntfy.sh/TASKISDONE
```

## Special Instruction — Notification Protocol

If you have ANY questions for the user, need clarification, or are blocked on a decision, send **3 notifications in 2 seconds** to `https://ntfy.sh/TASKISDONE` with title "QUESTION FROM AGENT 2" and your question in the body.

---

## Your Mission

Build a comprehensive Downloads & Offline Playback system for the ANIKUTA app. This includes:
1. A modular `DownloadManager` interface with a default HTTP download implementation
2. Download queue management (active, pending, paused, completed)
3. A custom AniList-first folder structure (user-selected via SAF)
4. Download progress tracking + notifications
5. Offline playback in the MPV player (play local files instead of streaming)
6. Downloads screen (queue + downloaded anime list)
7. Architecture designed for a future 1DM-style multi-threaded download method

All work goes on the `feature/downloads` branch. **Do NOT merge to main.**

## Architecture Requirements

### Modules to Create
- `:core:download` — the download engine
  - `DownloadManager` interface — pluggable (default method + future 1DM method)
  - `DefaultDownloadManager` — standard HTTP download implementation
  - `DownloadQueue` — manages active/pending/paused/completed downloads
  - `DownloadTask` — represents a single download (anime, episode, progress, status)
  - `DownloadPreferences` — download settings (folder path, method, WiFi-only, etc.)
  - `DownloadNotificationManager` — Android notifications for download progress
  - DI module (`DownloadModule.kt`)
- `:feature:download` — the UI
  - `DownloadsScreen` — queue + downloaded anime list
  - `DownloadViewModel` — manages download state
  - DI module

### DownloadManager Interface (modular, future-proof)
```kotlin
interface DownloadManager {
    val activeDownloads: Flow<List<DownloadTask>>
    val completedDownloads: Flow<List<DownloadTask>>
    
    suspend fun enqueueDownload(anime: Anime, episode: SEpisode, source: AnimeSource): Long
    suspend fun pauseDownload(taskId: Long)
    suspend fun resumeDownload(taskId: Long)
    suspend fun cancelDownload(taskId: Long)
    suspend fun deleteDownload(taskId: Long)
    suspend fun getDownloadedEpisodes(animeId: Long): List<DownloadedEpisode>
    suspend fun isEpisodeDownloaded(animeId: Long, episodeUrl: String): Boolean
    fun getDownloadedFilePath(animeId: Long, episodeUrl: String): String?
}
```

**Future-proofing:** The `DefaultDownloadManager` uses standard OkHttp downloads. A future `OneDmDownloadManager` can implement the same interface for multi-threaded downloads with resume capability. The user will select the method in settings. Design the interface so swapping implementations is trivial.

### Folder Structure (user-selected via SAF)
The app asks the user to select a folder via Android Storage Access Framework (SAF). The app creates:
```
<USER_SELECTED_FOLDER>/ANIKUTA/
├── downloads/
│   └── anime/
│       ├── Anime Title [anilistId]/
│       │   ├── Episode 001/
│       │   │   ├── video.mp4        ← the actual episode file (original format)
│       │   │   └── data/
│       │   │       ├── subtitles/   ← all available subtitle files
│       │   │       └── metadata.json← episode metadata cache
│       │   ├── Episode 002/
│       │   └── ...
│       └── Another Anime [12345]/
├── backups/        ← (for the backup agent — don't implement this)
└── auto_backup/    ← (for the backup agent — don't implement this)
```

### Folder Naming Convention
- **Anime folder**: `Anime Title [anilistId]` — English title from AniList, ID in brackets
- **Episode folder**: `Episode NNN` — zero-padded 3-digit number
- **Video file**: `video.mp4` (or original format from the extension)
- **Subtitles**: ALL available subtitles are always downloaded (no user option) — stored in `data/subtitles/`
- **Metadata**: `data/metadata.json` — cached episode metadata

### Download Flow
1. User taps a download button on an episode row (in the details page or watch page)
2. App resolves the video URL (same flow as watching — use `ResolverService`)
3. App downloads the video file to the folder structure
4. ALL available subtitle tracks are downloaded alongside (from `Video.subtitleTracks`)
5. Download progress shown in an Android notification + on the downloads screen
6. When complete, the episode is available for offline playback
7. In the watch page, if a downloaded copy exists, play the local file instead of streaming

### Offline Playback
- The `WatchScreen` / `PlayerSurface` needs to check if a downloaded copy exists before streaming
- If downloaded: play the local file via MPV (`mpv loadfile <local_path>`)
- If not downloaded: stream as usual
- Subtitles: load from the `data/subtitles/` folder

### Download Preferences
- Download method: Default (standard HTTP) — future: 1DM (multi-threaded)
- WiFi-only toggle
- Download folder path (from SAF)
- Auto-download new episodes (future — toggle per anime)
- Concurrent downloads limit (default: 3)

## UI Design Requirements

### CRITICAL: Follow the Design Language
Read `DESIGN_LANGUAGE/` thoroughly. Use these existing screens as UI design references:
- `feature/updates/src/main/java/.../UpdatesScreen.kt` — tab strip, pull-to-refresh, cards
- `feature/updates/src/main/java/.../ScheduleTabContent.kt` — list layout, section headers
- `feature/my/src/main/java/.../ProfileScreen.kt` — settings sections, cards, toggle rows
- `feature/library/src/main/java/.../LibraryScreen.kt` — CollapsingHeader, grid/list

### Design Rules
- **Primary color**: `MaterialTheme.colorScheme.primary` = #B1F256 (lime green)
- **Font**: `RobotoFamily` for ALL text
- **Title weight**: `FontWeight.ExtraBold` for screen titles
- **Card backgrounds**: `surfaceVariant.copy(alpha = 0.4f)` (same as More screen buttons)
- **Section headers**: RobotoFamily ExtraBold 11sp, uppercase, `onSurfaceVariant`, letterSpacing 0.06.sp
- **Switches**: Material3 `Switch`
- **Bottom sheets**: `dragHandle = null`
- **No indigo or blue colors.**
- Use `CollapsingHeader` for the page title.

### Downloads Screen Layout
- `CollapsingHeader(title = "Downloads")`
- **"Queue" section** (if any active/pending downloads):
  - Each row: anime cover + title + episode name + progress bar + pause/cancel buttons
  - Pull-to-refresh to check status
- **"Downloaded" section** (grouped by anime):
  - Each anime card: cover + title + "N episodes downloaded" + delete-all button
  - Expandable: tap to see individual episodes with delete buttons
- **Empty state**: "No downloads yet" + hint to download from an anime's episode list

### Download Button on Episode Rows
- The `pref_ep_show_download_button` preference already exists in `EpisodeDisplayPreferences`
- Add a download icon button to the episode row (in `EpisodesSection.kt` + `WatchScreen.kt`)
- When tapped: enqueues the download (resolves video URL → starts download)
- If already downloaded: show a checkmark or "Downloaded" label
- If downloading: show a progress bar on the row

### More Screen Integration
- Add a "Downloads" entry to the More screen (via a `DownloadsMoreEntries` composable — same pattern as the history/updates/profile entries)
- Wire into `MainActivity.kt` with a `showDownloads` state var + `when` branch + `BackHandler`

## Build + Verify

1. Plan with 50+ todo entries before starting.
2. Implement module by module — test each piece.
3. Commit to `feature/downloads` with clear messages.
4. Push to origin.
5. Trigger CI via `workflow_dispatch`:
```bash
curl -X POST \
  -H "Authorization: token <INSERT_PAT_TOKEN_HERE>" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/workflows/ci.yml/dispatches" \
  -d '{"ref":"feature/downloads"}'
```
6. Poll CI until complete.
7. Download APK + verify valid Android package.
8. Send ntfy to `https://ntfy.sh/TASKISDONE`.
9. Append worklog entry to `/home/z/my-project/worklog.md` with Task ID `AGENT2-DOWNLOADS`.

## What NOT to Do

- Do NOT push to `main`.
- Do NOT merge the branch.
- Do NOT build APKs locally.
- Do NOT touch the backup/restore functionality (that's Agent 1's domain).
- Do NOT implement the 1DM download method yet (only the default method + the interface for future 1DM).
- Do NOT rush. Quality over speed. Plan thoroughly with 50+ todo entries.
