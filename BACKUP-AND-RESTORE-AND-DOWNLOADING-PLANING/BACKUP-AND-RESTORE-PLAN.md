# Backup & Restore — Requirements & Plan (Updated)

## User Decisions (from clarification questions)

### Cover Images
- **Option A**: Downloaded fresh from AniList URLs and bundled in the backup file (fully self-contained).

### Auto-Backup
- User configures the frequency: every 6h, 12h, 24h, or weekly.
- User can always create a backup manually.
- User can configure what gets saved in the auto-backup.

### Backup Format
- Two formats: ANIKUTA format (recommended, our own) + Aniyomi format (compatibility).
- Modular + future-proof — new formats can be added without rewriting the core.
- Restore is highly capable — handles missing data gracefully (skip + continue).
- User selects what data to back up (granular checkboxes).

### Data to Back Up (user-selectable)
1. Library anime (with cover images — downloaded from AniList URLs)
2. Full details page data (synopsis, genres, scores, etc.)
3. Episodes list metadata (thumbnails, titles, release dates, summaries)
4. Watch progress / history (WatchProgressStore data)
5. AniList-extension links (SourceLinkStore + ExtensionLinkStore)
6. Tracker tokens + bindings (animetrack table + OAuth tokens)
7. Categories (user's custom categories + anime-category links)
8. Preferences (all app preferences)
9. Downloaded episode data (metadata + subtitles, NOT the video files themselves)

### Restore Flow
1. User selects a backup file
2. App determines the format (ANIKUTA vs Aniyomi)
3. App parses the anime entries
4. App processes each episode individually
5. App shows a summary of what will be restored
6. User confirms → restore executes
7. Missing data is skipped gracefully (not a hard failure)

### Architecture
- `:core:backup` — the backup/restore engine (format-agnostic, modular)
- `:feature:backup` — the UI (create backup, restore, auto-backup settings)
- Backup providers: each module provides its own backup/restore interface
- Multiple format support via a `BackupFormat` interface

## Folder Structure
See FOLDER-STRUCTURE-PLAN.md — backup files go in the user-selected folder under `ANIKUTA/backups/` (manual) and `ANIKUTA/auto_backup/` (automatic).
