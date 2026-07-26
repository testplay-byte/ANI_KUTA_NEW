# app.confused.anikuta.feature.backup

Backup & Restore UI for ANIKUTA.

## Architecture

Provides `BackupSettingsScreen` — a full-page settings screen with four sections:

1. **Backup** — category checkboxes + "Create backup" button
2. **Restore** — file picker + format detection + summary + confirm
3. **Auto-backup** — enable switch + frequency selector + category checkboxes
4. **Storage** — SAF folder selector + storage usage display

### Components

| Component | File | Role |
|---|---|---|
| `BackupViewModel` | `BackupViewModel.kt` | State management (sealed `BackupUiState`) |
| `BackupSettingsScreen` | `BackupSettingsScreen.kt` | Main screen (4 sections + state overlays) |
| `BackupCategoryList` | `components/BackupCategoryList.kt` | Reusable checkbox list |
| `RestoreConfirmSheet` | `components/RestoreConfirmSheet.kt` | Restore summary bottom sheet (dragHandle=null) |
| `FrequencySelector` | `components/FrequencySelector.kt` | 4-option auto-backup frequency control |

### Navigation

The screen is reached from `SettingsScreen` (in `:app`) via a "Backup & Restore"
row under a "Data" section. It's wired as a `showBackup` state flag in
`MainActivity.kt` (the hand-rolled state-machine navigation).

### Design

Follows the ANIKUTA design language:
- `#B1F256` primary via `MaterialTheme.colorScheme.primary`
- `RobotoFamily` font for all text
- `surfaceVariant.copy(alpha = 0.4f)` card backgrounds
- `CollapsingHeader` for the page title
- `ModalBottomSheet` with `dragHandle = null` (design principle #2)
- No indigo/blue colors

## Dependencies

- `:core:backup` (engine)
- `:core:designsystem` (CollapsingHeader, AnikutaBottomSheet, RobotoFamily)
- `:core:preferences`
- Lifecycle + ViewModel + Compose
- Koin

## Status

Phase: **Implementation complete** (Agent 1 — Backup & Restore).
