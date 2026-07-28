# Downloads / Offline Playback — Requirements & Plan (Updated)

## User Decisions (from clarification questions)

### Storage Permission
- User selects a folder via Android Storage Access Framework (SAF).
- App creates the `ANIKUTA/` folder structure inside the user-selected location.

### Video Format
- Downloaded in the ORIGINAL format from the extension (no re-muxing).

### Subtitles
- ALL available subtitles are always downloaded alongside the video (no user option).
- User does not get a choice — subtitles are always included.

### Download Methods (two, user-selectable)
1. **Default method (Aniyomi-style)** — standard HTTP download. Simple, reliable.
2. **1DM-style method (future)** — multi-threaded, resume capability, faster. Added AFTER the default method works.

### Architecture
- `:core:download` — the download engine (DownloadManager interface, queue, progress, file I/O)
- `:feature:download` — the UI (download queue, downloaded anime list, settings)
- Modular: `DownloadManager` interface → `DefaultDownloadManager` impl → future `OneDmDownloadManager`

### Download Flow
1. User taps a download button on an episode row
2. App resolves the video URL (same flow as watching)
3. App downloads the video file to the folder structure
4. All available subtitles are downloaded alongside
5. Download progress shown in a notification + a downloads screen
6. When complete, the episode is available for offline playback
7. In the watch page, if a downloaded copy exists, play the local file

### Folder Structure
See FOLDER-STRUCTURE-PLAN.md — downloads go in `<USER_FOLDER>/ANIKUTA/downloads/anime/Anime Title [anilistId]/Episode NNN/`
