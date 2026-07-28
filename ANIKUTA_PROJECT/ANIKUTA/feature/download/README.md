# app.confused.anikuta.feature.download

The Downloads UI — queue + downloaded library + settings.

**Module path:** `feature/download`
**Type:** Android library (Compose)
**Status:** Implemented (DEFAULT download method).

## What it does

- Shows the live download queue (active/pending/paused/errored) with progress
  bars + pause/resume/cancel/retry.
- Shows completed downloads grouped by anime (expandable cards with per-episode
  delete + delete-all).
- Empty state: folder-setup prompt (SAF picker) or "No downloads yet".
- Download settings bottom sheet (`dragHandle = null`): folder, Wi-Fi-only,
  concurrency, show-download-button toggle.
- `DownloadsMoreEntries` — the More screen entry point (wired in `MainActivity`).

## Architecture

```
DownloadsScreen (Compose) → DownloadViewModel → DownloadManager (:core:download)
                                                → DownloadPreferences (:core:download)
```

- The ViewModel observes `DownloadManager.activeDownloads` + `completedDownloads`
  + the folder-URI pref, combining them into a single `DownloadUiState`.
- User actions (pause/resume/cancel/delete/retry) forward to `DownloadManager`.
- Enqueue orchestration (resolve video URL → enqueue) does NOT live here — it's
  in `:app`'s `DownloadOrchestrator` (which depends on `:feature:video-resolver`
  + `:core:download`; this module cannot import `:feature:video-resolver` per
  Rule §14 — feature isolation).

## Design

Per `DESIGN_LANGUAGE/`:
- `CollapsingHeader(title = "Downloads")` with a settings gear action.
- `surfaceVariant.copy(alpha = 0.4f)` cards, `RoundedCornerShape(12.dp)`.
- RobotoFamily, `FontWeight.ExtraBold` titles.
- Section headers: ExtraBold 11sp uppercase, `onSurfaceVariant`, letterSpacing 0.06sp.
- Bottom sheets: `dragHandle = null` (principle #2).
- Single `LazyColumn` (NO nested LazyColumn — expanded episode lists are plain
  `Column`s, per the hard rules).
- #B1F256 primary; no indigo/blue.

## Files

- `DownloadsScreen.kt` — the screen (CollapsingHeader + Queue + Downloaded + empty).
- `DownloadViewModel.kt` — state + actions.
- `DownloadUiState.kt` — UI state + grouping key.
- `DownloadsMoreEntries.kt` — More screen entry.
- `DownloadPreferencesSheet.kt` — settings bottom sheet (dragHandle=null).
- `components/QueueRow.kt` — a queue row (cover + progress + actions).
- `components/DownloadedAnimeCard.kt` — an anime card (expandable + delete-all).
- `components/DownloadsEmptyState.kt` — the empty state.
- `di/DownloadModule.kt` — Koin (`downloadFeatureModule`).
