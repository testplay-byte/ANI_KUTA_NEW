# app.confused.anikuta.core.download

The download engine — a modular, future-proof system for downloading anime
episodes for offline playback.

**Module path:** `core/download`
**Type:** Android library (no Compose — pure logic + SAF I/O + notifications)
**Status:** Production-ready for unencrypted HLS + direct video. Encrypted HLS / DASH requires FFmpegKit (documented as next step).

## What it does

- Downloads episodes (video + ALL subtitles + metadata) to a user-selected
  SAF folder in an AniList-first structure.
- Supports TWO download methods:
  - **Normal**: single-threaded OkHttp streaming. Works for direct video + HLS.
  - **Advanced**: multi-threaded Range-request download with resume + auto-retry.
    Falls back to Normal for HLS + unsupported servers.
- Handles HLS (.m3u8) streams: parses playlists, downloads segments, strips
  PNG anti-scraping headers, concatenates into .ts.
- Validates downloads: file-size check, magic-byte verification (rejects HTML
  error pages + images masquerading as video).
- Persists the queue across app restarts with resume capability (Advanced).
- Posts Android notifications (progress, completion, error).

## Architecture

```
DownloadManager (interface)          ← pluggable contract
└── DefaultDownloadManager           ← wires everything
    ├── DownloadQueue                ← state machine + Semaphore concurrency
    │   └── HttpDownloader           ← routes: Normal → streaming; Advanced → multi-threaded; HLS → HlsDownloader
    │       ├── HlsDownloader        ← HLS playlist parsing + segment download + PNG stripping
    │       └── AdvancedHttpDownloader ← multi-threaded Range requests + resume + retry
    │           └── DownloadResumeManager ← per-chunk resume metadata
    ├── DownloadStore                ← persists the queue (PreferenceStore JSON)
    ├── DownloadStorageProvider      ← SAF folder structure (AniList-first) + publish
    ├── TempDownloadCache            ← internal cache for partial downloads
    ├── DownloadPreferences          ← all download settings
    ├── ServerDiscoveryStore         ← caches discovered server names per source
    ├── DynamicProgressTracker       ← smart progress estimation (50MB-ahead, 90% cap)
    ├── DownloadNotificationManager  ← Android notifications
    └── DownloadLogger               ← uniform tag (AnikutaDownload)
```

## PNG Anti-Scraping (Critical Discovery)

Some CDNs (e.g. megaplay.buzz / kotocdn.site) prepend PNG image headers to
HLS segments to prevent direct downloading. The extension's LocalProxyServer
strips these headers before serving to MPV. Our `HlsDownloader` does the same:
`stripPngHeader()` finds the IEND marker, skips to the MPEG-TS sync byte,
and writes only the clean video data. Without this, downloads would produce
files starting with PNG magic bytes → falsely rejected as "corrupt."

## Download Flow

1. User taps download → `DownloadOrchestrator` resolves video URL via
   `ResolverService` → selects best server/audio/quality based on preferences
2. `DownloadManager.enqueueDownload()` → `DownloadQueue` queues the task
3. `HttpDownloader.download()`:
   a. Download to internal cache (`TempDownloadCache`)
   b. If file is small + starts with `#EXTM3U` → re-download via `HlsDownloader`
   c. `HlsDownloader` parses playlist, downloads segments (stripping PNG headers),
      concatenates into `.ts`
   d. Validate: file-size check + magic-byte check (HTML/PNG/JPEG rejection)
   e. Publish to SAF (`DownloadStorageProvider.publishToUserFolder`)
   f. Clean up temp cache
4. Progress tracked by `DynamicProgressTracker` (50MB-ahead estimate, 90% cap)

## Format Support

| Format | Supported | How |
|---|---|---|
| Direct video (mp4/mkv/webm/etc.) | ✅ | OkHttp streaming (Normal) or Range requests (Advanced) |
| HLS (.m3u8, unencrypted) | ✅ | `HlsDownloader` — segment parsing + concatenation + PNG stripping |
| HLS (proxy URLs without .m3u8) | ✅ | Post-download `#EXTM3U` detection → re-download via HlsDownloader |
| Encrypted HLS (AES-128) | ❌ | Needs FFmpegKit — documented as next step |
| DASH (.mpd) | ❌ | Needs FFmpegKit — documented as next step |
| HTML error pages | ❌ Rejected | Magic-byte check |

## Logging

All logs use the tag `AnikutaDownload`. Filter: `adb logcat -s AnikutaDownload:V`

## Next Step: FFmpegKit

Encrypted HLS + DASH support requires FFmpegKit (the owner approved the APK
size increase). The plan is documented in `1DM-DOWNLOAD-ANALYSIS/`. The
FFmpegKit integration would add a `FfmpegDownloadEngine` that uses a single
FFmpeg call (`-c copy -f matroska`) to handle ALL formats — same approach as
the OLD_ANIKUTA project + Aniyomi.

## Dependencies

- `:core:preferences`, `:core:source-api`, `:core:common`
- `androidx.documentfile` (SAF), `okhttp`, `kotlinx-serialization-json`,
  `kotlinx-coroutines`, Koin
