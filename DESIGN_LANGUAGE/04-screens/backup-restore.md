# Backup & Restore Screen

> Settings sub-screen for creating/restoring backups and configuring auto-backup.
> Reached from Settings → "Backup & Restore".

## Layout

```
┌─────────────────────────────────┐
│ Backup & Restore         (36sp) │  ← CollapsingHeader (shrinks to 26sp on scroll)
├─────────────────────────────────┤
│  BACKUP & RESTORE               │  ← Section label (11sp, uppercase, onSurfaceVariant)
│ ┌─────────────────────────────┐ │
│ │ ☁  Backup & Restore         │ │  ← SectionCard (surfaceVariant alpha 0.4f)
│ │    Create a backup or...    │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │     Create backup       │ │ │  ← Button (filled, primary)
│ │ └─────────────────────────┘ │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │    Restore from file    │ │ │  ← OutlinedButton
│ │ └─────────────────────────┘ │ │
│ └─────────────────────────────┘ │
│                                 │
│  AUTO-BACKUP                    │
│ ┌─────────────────────────────┐ │
│ │ ⏱  Automatic backups    [⊘] │ │  ← Toggle in header (Switch)
│ │    Periodically back up...  │ │
│ │                             │ │  (shown only when enabled)
│ │  FREQUENCY                  │ │
│ │ ┌─────────┬─────────┐       │ │
│ │ │Every 6h │Every 12h│       │ │  ← 2x2 grid (FrequencySelector)
│ │ ├─────────┼─────────┤       │ │
│ │ │Every 24h│ Weekly  │       │ │
│ │ └─────────┴─────────┘       │ │
│ │                             │ │
│ │  MAX BACKUPS TO KEEP        │ │
│ │ ┌──┬──┬──┬──┐               │ │
│ │ │ 1│ 2│ 3│ 4│               │ │  ← 4-cell grid (MaxBackupsSelector)
│ │ └──┴──┴──┴──┘               │ │
│ │                             │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │ ⚙ What to include (7)  │ │ │  ← Button → opens sub-sheet
│ │ └─────────────────────────┘ │ │
│ └─────────────────────────────┘ │
│                                 │
│  STORAGE                        │
│ ┌─────────────────────────────┐ │
│ │ 📁  Backup folder           │ │
│ │    Using 12 MB              │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │    Select folder        │ │ │
│ │ └─────────────────────────┘ │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

## Components

### CollapsingHeader
- Title: "Backup & Restore" — 36sp → 26sp on scroll (animated, tween 300ms).
- Wired to `LazyListState.firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 20`.

### SectionCard
- Background: `surfaceVariant.copy(alpha = 0.4f)`, `RoundedCornerShape(12.dp)`.
- Icon: 24dp, `primary` tint.
- Title: 16sp ExtraBold, `onSurface`.
- Subtitle: 12sp, `onSurfaceVariant`.
- Optional `toggle` slot (for the Auto-backup Switch — placed at the end of the header row).

### Create Backup flow
1. User taps "Create backup" button.
2. Bottom sheet opens (`AnikutaBottomSheet`, `dragHandle = null`).
3. **Backdrop**: dim (background alpha 0.6f) + blur (8dp) on API 31+.
4. Sheet shows: title, "X of Y categories selected", scrollable checkbox list, "Create backup" button.
5. User selects categories + taps "Create backup".
6. **Animation**: `CreateBackupAnimationOverlay` — pulsing cloud icon + breathing ring, "Creating your backup…".
7. **Success**: `BackupSuccessDialog` — grid-based popup with:
   - Highlighted total stats (items saved, categories) — `StatCard` with primary-tinted background.
   - 2-column grid of per-category breakdown — `CategoryCountCard`.
   - File name + date.
   - OK button.

### Restore flow
1. User taps "Restore from file" → file picker opens.
2. File selected → `RestoreSummaryDialog` (NOT a bottom sheet — a dialog):
   - Title with restore icon.
   - Format + date info.
   - Highlighted total items to restore.
   - 2-column grid of per-category item counts.
   - Restore + Cancel buttons.
3. User taps "Restore" → `RestoreAnimationOverlay`:
   - Rotating restore icon (360° every 1.2s).
   - Pulsing scale (0.85↔1.15 every 0.8s).
   - Breathing ring (alpha 0.3↔0.9 every 0.6s).
   - **Minimum 5 seconds** (even if restore finishes faster).
   - "Restoring your data…" + "Please wait…".
4. Restore completes → `RestoreCompleteDialog`:
   - Highlighted totals (Imported / Skipped / Errors).
   - 2-column grid of per-category imported counts.
   - OK button.
5. User taps "OK" → **redirected to Library page** (backup screen + settings closed).

### Auto-backup section
- **Toggle**: Switch in the section header (right side), not a separate row.
- When enabled, shows:
  - **Frequency**: 2x2 grid (`FrequencySelector`) — Every 6h / Every 12h / Every 24h / Weekly.
  - **Max backups to keep**: 4-cell grid (`MaxBackupsSelector`) — 1 / 2 / 3 / 4. Older auto-backups auto-deleted.
  - **What to include**: Button → opens `AutoIncludeSheet` (bottom sheet with checkbox list).

### StatCard (shared component)
- Background: `primary.copy(alpha = 0.12f)`, `RoundedCornerShape(12.dp)`.
- Value: 24sp ExtraBold, `primary` color.
- Label: 11sp, `onSurfaceVariant`.
- Used in: BackupSuccessDialog, RestoreSummaryDialog, RestoreCompleteDialog.

### CategoryCountCard (shared component)
- Background: `surfaceVariant.copy(alpha = 0.4f)`, `RoundedCornerShape(8.dp)`.
- Name: 11sp, `onSurface`.
- Count: 14sp ExtraBold, `primary`.
- Used in: grid layouts of all summary dialogs.

## Design rules
- **Primary color**: `MaterialTheme.colorScheme.primary` = #B1F256.
- **Font**: `RobotoFamily` for ALL text.
- **Title weight**: `FontWeight.ExtraBold` for screen title + section headers.
- **Card backgrounds**: `surfaceVariant.copy(alpha = 0.4f)`.
- **Section labels**: 11sp ExtraBold uppercase, `onSurfaceVariant`, letterSpacing 0.06.sp.
- **Bottom sheets**: `dragHandle = null` (principle #2).
- **Backdrop**: dim + blur when sheets open.
- **No indigo or blue colors.**
- **Animations**: smooth (tween 300ms), pulsing/breathing for loading states.

## File map

| Component | File |
|---|---|
| Main screen | `feature/backup/.../BackupSettingsScreen.kt` |
| ViewModel | `feature/backup/.../BackupViewModel.kt` |
| Category checkbox list | `components/BackupCategoryList.kt` |
| Section label | `components/BackupCategoryList.kt` (BackupSectionLabel) |
| Frequency 2x2 grid | `components/FrequencySelector.kt` |
| Max-backups 1-4 grid | `components/MaxBackupsSelector.kt` |
| Create backup sheet | `BackupSettingsScreen.kt` (CreateBackupSheet) |
| Auto-include sheet | `components/AutoIncludeSheet.kt` |
| Backup success dialog | `components/BackupSuccessDialog.kt` |
| Restore summary dialog | `components/RestoreSummaryDialog.kt` |
| Restore complete dialog | `components/RestoreCompleteDialog.kt` |
| Restore animation overlay | `components/RestoreAnimationOverlay.kt` |
| StatCard + CategoryCountCard | `components/BackupSuccessDialog.kt` (shared) |
