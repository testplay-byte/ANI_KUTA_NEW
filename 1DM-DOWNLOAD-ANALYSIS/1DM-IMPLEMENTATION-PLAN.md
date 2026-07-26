# 1DM Download Method — Analysis & Implementation Plan

> **Status:** Analysis complete. Implementation pending.
> **Date:** 2026-07-25
> **Agent:** Agent 2 (Downloads & Offline Playback)
> **Reference:** `ANIYOMI_REFRENCE/ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt`

---

## 1. Executive Summary

The 1DM download method is an **external downloader** approach: instead of
implementing multi-threaded downloading + HLS/ffmpeg support ourselves, the app
delegates the actual download to an external Android app (1DM, 1DM+, ADM) via
Android Intents. The external app handles:

- Multi-threaded downloading (parallel connections)
- Resume capability (download continuation after interruption)
- HLS/MPEG-DASH support (1DM has ffmpeg built in)
- Speed optimization (connection pooling, bandwidth management)
- Encrypted HLS (AES-128 decryption)

This is the same approach Aniyomi uses. It's simpler + more reliable than
bundling our own ffmpeg binary, and leverages the user's existing download
manager (which they may already prefer).

---

## 2. Aniyomi Reference Analysis

### 2.1 Architecture

Aniyomi's `AnimeDownloader.kt` has a `downloadVideoExternal()` method that:

1. Creates a `_tmp.mkv` file in the temp directory (placeholder).
2. Looks up the user's selected external downloader package name from
   `preferences.externalDownloaderSelection()`.
3. Constructs an Android `Intent` specific to the downloader:
   - **1DM** (`idm.internet.download.manager`):
     - Component: `idm.internet.download.manager.Downloader`
     - Action: `ACTION_VIEW` with the video URL as data
     - Extras: `extra_filename` (String), `extra_headers` (Bundle of HTTP headers)
   - **ADM** (`com.dv.adm`):
     - Component: `$pkgName.AEditor`
     - Action: `ACTION_VIEW`
     - Extras: `com.dv.get.ACTION_LIST_ADD` (URL + filename), `com.dv.get.ACTION_LIST_PATH` (directory), `android.media.intent.extra.HTTP_HEADERS` (Bundle)
4. Launches the intent via `context.startActivity(intent)`.
5. Polls for completion by checking if the temp directory has a non-tmp file.

### 2.2 Supported External Downloaders

| Package Name | App | Notes |
|---|---|---|
| `idm.internet.download.manager` | 1DM | Free version |
| `idm.internet.download.manager.plus` | 1DM+ | Paid version |
| `idm.internet.download.manager.adm.lite` | 1DM ADM Lite | Lite version |
| `com.dv.adm` | Advanced Download Manager | Alternative |

### 2.3 Settings

Aniyomi's `SettingsDownloadScreen.kt` has an `getExternalDownloaderGroup()`:

- `useExternalDownloader()` — toggle: internal vs external downloader
- `externalDownloaderSelection()` — the selected package name (dropdown of
  installed supported downloaders, detected via `PackageManager.getInstalledPackages()`)

### 2.4 Limitations of Aniyomi's Approach

1. **Fire-and-forget** — Aniyomi doesn't monitor 1DM's download progress. It
   polls for the file's existence in the temp dir.
2. **File location** — 1DM downloads to its own directory; Aniyomi expects the
   file to appear in the temp dir (the `_tmp.mkv` path is passed to 1DM, but
   1DM may save elsewhere).
3. **No progress in the app** — the download queue shows "downloading" but no
   byte-level progress while 1DM runs.
4. **Manual completion check** — the user has to wait + Aniyomi checks if the
   file exists.

---

## 3. ANIKUTA's 1DM Implementation Plan

### 3.1 Phase 1: External Downloader Integration (MVP)

**Goal:** match Aniyomi's functionality — delegate to 1DM/ADM via Intent.

**Modules:**
- `:core:download` — `ExternalDownloaderManager` (implements `DownloadManager`)
- `:feature:download` — settings UI for selecting the external downloader

**Tasks:**
1. Add `DownloadMethod.EXTERNAL_1DM` to the `DownloadMethod` enum.
2. Create `ExternalDownloaderManager` (:core:download):
   - `enqueueDownload()` → resolve video URL → launch 1DM Intent
   - Poll for the file in the expected directory (every 2s, timeout 10min)
   - On file found → move to the ANIKUTA SAF folder → mark COMPLETED
   - On timeout → mark ERROR
3. Detect installed downloaders via `PackageManager` in the settings screen.
4. Add "External downloader" section to `DownloadSettingsScreen`:
   - Toggle: use external downloader (when ON, the method pref switches to EXTERNAL_1DM)
   - Dropdown: select which installed downloader (1DM, 1DM+, ADM)
5. Wire the `DownloadMethod` pref to the Koin binding: when `EXTERNAL_1DM` is
   selected, Koin returns `ExternalDownloaderManager` instead of `DefaultDownloadManager`.

**Intent construction (1DM):**
```kotlin
val intent = pm.getLaunchIntentForPackage(pkgName)!!.apply {
    component = ComponentName(pkgName, "idm.internet.download.manager.Downloader")
    action = Intent.ACTION_VIEW
    data = Uri.parse(videoUrl)
    putExtra("extra_filename", "$filename.mkv")
    putExtra("extra_headers", headersBundle)
}
context.startActivity(intent)
```

**Intent construction (ADM):**
```kotlin
val intent = Intent().apply {
    component = ComponentName(pkgName, "$pkgName.AEditor")
    action = Intent.ACTION_VIEW
    putExtra("com.dv.get.ACTION_LIST_ADD", "$videoUrl<info>$filename.mkv")
    putExtra("com.dv.get.ACTION_LIST_PATH", targetDirPath)
    putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)
}
context.startActivity(intent)
```

### 3.2 Phase 2: Enhanced Integration (beyond Aniyomi)

**Goal:** real-time progress monitoring + auto-move to the ANIKUTA folder.

**Tasks:**
1. **1DM Content Provider** — 1DM exposes a content provider for download
   status. Query it to get real-time progress (bytes downloaded, speed, ETA).
2. **Progress in the download queue** — show 1DM's progress in the ANIKUTA
   Downloads screen (not just "downloading").
3. **Auto-move** — when 1DM reports completion, automatically move the file
   from 1DM's directory to the ANIKUTA SAF folder (no manual step).
4. **Error handling** — detect 1DM failures (network error, cancelled) via the
   content provider and surface them as ERROR in the queue.

### 3.3 Phase 3: Internal Multi-Threaded Downloader (fallback)

**Goal:** if the user doesn't have 1DM/ADM installed, offer an internal
multi-threaded downloader with resume + HLS/ffmpeg.

**Tasks:**
1. **Ranged requests** — HTTP `Range` header for partial downloads.
2. **Multi-threaded** — N parallel range requests (configurable, default 4).
3. **Resume** — save per-segment progress to `DownloadStore`; resume from the
   last byte on restart.
4. **HLS via ffmpeg** — bundle a minimal ffmpeg binary (or use `ffmpeg-kit`) to
   download + mux HLS streams. This is heavy (~15MB APK size increase) but
   makes the internal downloader fully self-contained.
5. **Encrypted HLS** — AES-128 decryption (the key URI is in the playlist).

### 3.4 Phase 4: Smart Download Features

**Goal:** quality-of-life features beyond basic downloading.

**Tasks:**
1. **Auto-download new episodes** — ADR-020: when a new episode releases +
   matches the user's preferences, auto-download it.
2. **Wi-Fi-only** — already implemented (pause on mobile data).
3. **Download scheduling** — only download during specific hours (e.g. overnight).
4. **Storage management** — auto-delete old downloads when storage is low.
5. **Batch download** — download all episodes of an anime at once.
6. **Download speed limit** — cap the download speed (for bandwidth management).

---

## 4. Architecture (Phase 1)

```
DownloadManager (interface)          ← already exists
├── DefaultDownloadManager           ← already exists (OkHttp + HLS)
└── ExternalDownloaderManager        ← NEW (Phase 1)
    ├── IntentLauncher               ← constructs + launches the 1DM/ADM Intent
    ├── CompletionPoller             ← polls for the file in 1DM's directory
    └── FileMover                    ← moves the file to the ANIKUTA SAF folder
```

### Koin binding (in DownloadModule.kt):
```kotlin
single<DownloadManager> {
    when (get<DownloadPreferences>().method().get()) {
        DownloadMethod.DEFAULT -> DefaultDownloadManager(...)
        DownloadMethod.EXTERNAL_1DM -> ExternalDownloaderManager(...)
        DownloadMethod.ONEDM -> DefaultDownloadManager(...) // placeholder until Phase 3
    }
}
```

### Module boundaries:
- `:core:download` — `ExternalDownloaderManager`, `IntentLauncher`,
  `CompletionPoller`, `FileMover`. No `:feature:*` imports (Rule §14).
- `:feature:download` — settings UI for selecting the external downloader.
  Detects installed downloaders via `PackageManager`.
- `:app` — `DownloadOrchestrator` stays the same (it already passes resolved
  video URLs to the manager; the manager decides how to download).

---

## 5. File Structure (planned)

```
ANIKUTA_PROJECT/ANIKUTA/
├── core/download/
│   └── src/main/java/.../core/download/
│       ├── ExternalDownloaderManager.kt    ← Phase 1
│       ├── external/
│       │   ├── IntentLauncher.kt           ← Phase 1
│       │   ├── CompletionPoller.kt         ← Phase 1
│       │   ├── FileMover.kt                ← Phase 1
│       │   └── SupportedDownloaders.kt     ← Phase 1 (package-name constants)
│       └── (existing files...)
├── feature/download/
│   └── src/main/java/.../feature/download/
│       ├── ExternalDownloaderSettings.kt   ← Phase 1 (settings section)
│       └── (existing files...)
```

---

## 6. Key Decisions

1. **External-first** — we match Aniyomi's approach (delegate to 1DM/ADM) for
   the MVP. This is simpler + more reliable than bundling ffmpeg.
2. **HLS support** — the default downloader already handles unencrypted HLS
   (via `HlsDownloader`). The 1DM method will handle encrypted HLS (1DM has
   ffmpeg). DASH will also be handled by 1DM.
3. **Progress monitoring** — Phase 1 polls for completion (like Aniyomi). Phase
   2 adds real-time progress via 1DM's content provider.
4. **File location** — 1DM downloads to its own directory. Phase 1 moves the
   file to the ANIKUTA folder after completion. Phase 2 does this
   automatically via the content provider.
5. **Fallback** — if no external downloader is installed, the app falls back to
   `DefaultDownloadManager` (OkHttp + HLS). Phase 3 adds an internal
   multi-threaded fallback.

---

## 7. Open Questions (for the owner)

1. **1DM package** — does the owner have 1DM or 1DM+ installed? (Affects which
   package name to use in the Intent.)
2. **File location** — should the app try to configure 1DM to download directly
   to the ANIKUTA folder (if 1DM supports it), or always move the file
   afterward?
3. **Progress monitoring** — is Phase 1's poll-based completion check
   acceptable, or does the owner want real-time progress from the start
   (Phase 2)?
4. **Internal multi-threaded** — does the owner want Phase 3 (internal
   multi-threaded with ffmpeg), or is the external approach sufficient?

---

## 8. Next Steps

1. **Implement Phase 1** — `ExternalDownloaderManager` + settings UI + Intent
   construction + completion polling.
2. **Test with a real 1DM installation** — verify the Intent is received
   correctly + the file appears in the expected location.
3. **Implement Phase 2** — 1DM content provider for real-time progress.
4. **Implement Phase 3** — internal multi-threaded fallback (if the owner
   wants it).

---

*This document is the 1DM implementation plan. It will be updated as each
phase is implemented.*
