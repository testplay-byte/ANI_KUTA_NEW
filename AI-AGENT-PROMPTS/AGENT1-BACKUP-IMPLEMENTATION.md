# AGENT 1 (Backup & Restore) — IMPLEMENTATION PROMPT

## Credentials

```
REPO URL:   https://github.com/testplay-byte/ANI_KUTA_NEW
PAT TOKEN:  <INSERT_PAT_TOKEN_HERE>
YOUR BRANCH: feature/backup-restore
CLONE CMD:  git clone -b feature/backup-restore "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
NTFY:       https://ntfy.sh/TASKISDONE
```

## Special Instruction — Notification Protocol

If you have ANY questions for the user, need clarification, or are blocked on a decision, send **3 notifications in 2 seconds** to `https://ntfy.sh/TASKISDONE` with title "QUESTION FROM AGENT 1" and your question in the body.

---

## Your Mission

Build a comprehensive, modular Backup & Restore system for the ANIKUTA app. This includes:
1. A custom ANIKUTA backup format (recommended)
2. Aniyomi backup format compatibility (restore from Aniyomi backups)
3. Granular user-selectable data categories
4. Auto-backup with configurable frequency
5. Graceful restore that handles missing data

All work goes on the `feature/backup-restore` branch. **Do NOT merge to main.**

## Architecture Requirements

### Modules to Create
- `:core:backup` — the backup/restore engine (format-agnostic, modular)
  - `BackupManager` — orchestrates backup creation + restoration
  - `BackupFormat` interface — pluggable format support (ANIKUTA format + Aniyomi format)
  - `AnikutaBackupFormat` — our custom JSON-based format
  - `AniyomiBackupFormat` — reads Aniyomi protobuf/JSON backups
  - `BackupProvider` interface — each data module implements this
  - `BackupEntry` — sealed class for each data type
  - DI module (`BackupModule.kt`)
- `:feature:backup` — the UI
  - `BackupSettingsScreen` — create backup, restore backup, auto-backup config
  - `BackupViewModel` — manages backup/restore state
  - DI module

### Data to Back Up (user-selectable via checkboxes)
1. **Library anime** — all anime in the user's library (from `animes` table where `favorite=1`)
2. **Cover images** — downloaded from AniList URLs and bundled in the backup file (fully self-contained)
3. **Anime details** — full anime details (description, genres, scores, etc. from DB)
4. **Episodes list** — all episodes per anime (from `episodes` table: name, number, url, summary, preview_url, date_upload)
5. **Episode metadata** — enriched metadata (from `EpisodeMetadataCache`: titles, descriptions, thumbnails, air dates)
6. **Watch progress / history** — `WatchProgressStore` data (position, duration, timestamps)
7. **AniList-extension links** — `SourceLinkStore` + `ExtensionLinkStore` data
8. **Tracker tokens + bindings** — OAuth tokens (AniList + MAL) + `animetrack` table
9. **Categories** — user's custom categories + `anime_category` links
10. **Preferences** — all app preferences (display settings, episode settings, etc.)

### Backup Providers
Each data source implements `BackupProvider`:
```kotlin
interface BackupProvider {
    val id: String
    suspend fun export(): BackupEntry
    suspend fun import(entry: BackupEntry): Boolean
}
```
Create providers for: `LibraryBackupProvider`, `EpisodeBackupProvider`, `EpisodeMetadataBackupProvider`, `WatchProgressBackupProvider`, `SourceLinkBackupProvider`, `TrackerBackupProvider`, `CategoryBackupProvider`, `PreferencesBackupProvider`.

### Auto-Backup
- User configures frequency: every 6h, 12h, 24h, or weekly
- User can configure what gets saved in auto-backup
- User can always create a backup manually
- Use `WorkManager` for periodic background backup
- Auto-backup files go to `<USER_FOLDER>/ANIKUTA/auto_backup/`
- Manual backups go to `<USER_FOLDER>/ANIKUTA/backups/`

### Folder Structure (user-selected via SAF)
The app asks the user to select a folder via Android Storage Access Framework (SAF). The app creates the `ANIKUTA/` folder structure inside it:
```
<USER_SELECTED_FOLDER>/ANIKUTA/
├── auto_backup/     ← automatic backup files
├── backups/         ← manual backup files
├── downloads/       ← (for the downloads agent — don't implement this)
└── ...
```

### Restore Flow
1. User selects a backup file
2. App determines the format (ANIKUTA vs Aniyomi)
3. App parses the data
4. App processes each data type individually
5. App shows a summary of what will be restored
6. User confirms → restore executes
7. Missing data is skipped gracefully (not a hard failure)

### Aniyomi Compatibility
- Read the Aniyomi backup format reference in `_REFERENCES/ANIYOMI_REFRENCE/ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/`
- Our restore should handle Aniyomi backups: process each anime entry, match by AniList ID, import episodes/progress/categories
- We do NOT need to EXPORT in Aniyomi format (only our own format + restore from Aniyomi)

## UI Design Requirements

### CRITICAL: Follow the Design Language
Read `DESIGN_LANGUAGE/` thoroughly. Use these existing screens as UI design references:
- `feature/updates/src/main/java/.../UpdatesScreen.kt` — tab strip, pull-to-refresh, cards
- `feature/updates/src/main/java/.../ScheduleTabContent.kt` — list layout, section headers
- `feature/my/src/main/java/.../ProfileScreen.kt` — settings sections, cards, toggle rows
- `feature/library/src/main/java/.../LibraryScreen.kt` — CollapsingHeader, MoreRow pattern

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

### Backup Settings Screen Layout
- `CollapsingHeader(title = "Backup & Restore")`
- **"Backup" section**: 
  - "Create backup" button (exports to user-selected folder)
  - Checkbox list for what to include (10 data categories)
  - Format selector: ANIKUTA format (recommended) / Aniyomi format (compatibility)
- **"Restore" section**:
  - "Restore from file" button (opens file picker)
  - On file selected: show format detection + summary + confirm
- **"Auto-backup" section**:
  - Switch: Enable auto-backup
  - Frequency selector: 6h / 12h / 24h / weekly (segmented control)
  - "What to include" — same checkbox list (separate from manual backup)
  - Folder selector: shows the user-selected SAF folder
- **"Storage" section**:
  - Current folder path display
  - "Select folder" button (opens SAF)
  - Storage usage display (size of backups)

## Build + Verify

1. Plan with 50+ todo entries before starting.
2. Implement module by module — test each piece.
3. Commit to `feature/backup-restore` with clear messages.
4. Push to origin.
5. Trigger CI via `workflow_dispatch`:
```bash
curl -X POST \
  -H "Authorization: token <INSERT_PAT_TOKEN_HERE>" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/workflows/ci.yml/dispatches" \
  -d '{"ref":"feature/backup-restore"}'
```
6. Poll CI until complete.
7. Download APK + verify valid Android package.
8. Send ntfy to `https://ntfy.sh/TASKISDONE`.
9. Append worklog entry to `/home/z/my-project/worklog.md` with Task ID `AGENT1-BACKUP-RESTORE`.

## What NOT to Do

- Do NOT push to `main`.
- Do NOT merge the branch.
- Do NOT build APKs locally.
- Do NOT touch the downloads functionality (that's Agent 2's domain).
- Do NOT add a heavy charting library.
- Do NOT rush. Quality over speed. Plan thoroughly with 50+ todo entries.
