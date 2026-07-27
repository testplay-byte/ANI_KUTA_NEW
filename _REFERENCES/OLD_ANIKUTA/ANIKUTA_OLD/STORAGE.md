# ANI-KUTA Storage / Folder Selection

> Status: Foundation implemented (onboarding SAF picker + StorageManager + DI)
> Downloads/backups migration: next phase (see §4)

## Overview

ANI-KUTA lets the user choose where app data is stored via Android's Storage
Access Framework (SAF). During onboarding, the user picks a folder using the
system's `ACTION_OPEN_DOCUMENT_TREE` picker. The app persists the folder URI
and creates a standard subdirectory structure inside it.

## Directory structure

```
<user-selected folder>/
  ├── downloads/   ← downloaded episodes (.mp4/.mkv)
  ├── data/        ← app data (screenshots, mpv config, structured data)
  ├── backups/     ← backup files (auto + manual)
  ├── cache/       ← temporary data with cleanup
  └── downloads/.nomedia  ← prevents Media Scanner indexing
```

If the user picks "Use default", the app-private external storage is used:
`/storage/emulated/0/Android/data/app.anikuta/files/`

## Architecture

### Core classes

| Class | Location | Responsibility |
|---|---|---|
| `FolderProvider` | `core/.../storage/FolderProvider.kt` | Interface: returns default app-private path |
| `AndroidStorageFolderProvider` | `core/.../storage/AndroidStorageFolderProvider.kt` | Impl: `context.getExternalFilesDir(null)` |
| `StoragePreferences` | `app/.../storage/StoragePreferences.kt` | Persists the SAF URI via `Preference.appStateKey("storage_dir")` |
| `StorageManager` | `app/.../storage/StorageManager.kt` | Resolves URI → UniFile, creates subdirs, exposes `getDownloadsDirectory()` etc. |

### DI registration (AppModule.kt)

```kotlin
addSingletonFactory { AndroidStorageFolderProvider(get<Context>()) }
addSingletonFactory { StoragePreferences(get<...>(), get<PreferenceStore>()) }
addSingletonFactory { StorageManager(get<Context>(), get<StoragePreferences>()) }
```

### Onboarding flow (OnboardingScreen.kt — `ExpressiveStorageStep`)

1. User taps "Select Folder" → `rememberLauncherForActivityResult(OpenDocumentTree())`
2. System shows the SAF folder picker
3. User navigates their filesystem, creates/selects a folder
4. On selection:
   - `contentResolver.takePersistableUriPermission(uri, READ|WRITE)` — survives reboots
   - `StoragePreferences.baseStorageDirectory().set(uri.toString())` — persists
   - `onFolderSelected(uri.toString())` — updates in-wizard state
5. The "Use default" button skips SAF and uses the app-private path.

## SAF mechanics

### Persisting the URI permission

```kotlin
val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
context.contentResolver.takePersistableUriPermission(uri, flags)
```

Without this call, the URI permission is revoked when the process dies.
Wrapped in `try/catch SecurityException` — some OEMs don't implement
persistable grants but still allow raw URI access.

### Resolving the URI to a UniFile

```kotlin
val base = UniFile.fromUri(context, uri)   // SAF URI
// or
val base = UniFile.fromPath(path)          // file path (default fallback)
```

`StorageManager.resolveBaseDir()` handles both cases.

### Creating subdirectories

```kotlin
base?.createDirectory("downloads")   // idempotent — returns existing if present
base?.createDirectory("data")
base?.createDirectory("backups")
base?.createDirectory("cache")
base?.createDirectory("downloads")?.createFile(".nomedia")
```

### Reading/writing files

```kotlin
val file = base?.createDirectory("downloads")?.createFile("video.mp4")
val outputStream = file?.openOutputStream(false)  // false = not append
val inputStream = file?.openInputStream()
val length = file?.length()
file?.delete()
```

All wrap `ContentResolver` + `DocumentsContract` internally.

## Edge cases

- **URI revoked** (user cleared app data, reinstalled, or revoked via Settings):
  `UniFile.fromUri()` returns null. `StorageManager.get*Directory()` returns null.
  Callers must handle null — fall back to app-private storage or re-prompt.
- **OEMs without persistable grants**: handled by try/catch on
  `takePersistableUriPermission`. The URI still works within the session.
- **Migration for existing users**: if `storage_dir` pref is unset on first
  launch after upgrade, `StoragePreferences` defaults to
  `AndroidStorageFolderProvider.path()` — no data loss.

## What this unblocks

| Feature | Status |
|---------|--------|
| **Downloads** | Infrastructure ready; `DownloadStore` migration needed (replace `File` with `UniFile`) |
| **Backup creation** | `backups/` dir ready; backup job not yet implemented |
| **Backup restore** | `data/` dir ready; restore logic not yet implemented |
| **Cache cleanup** | `cache/` dir ready; `EpisodeCacheStore` migration + cleanup method needed |

## Player playback limitation (flagged for next phase)

The mpv-lib takes a **file path**, not a Uri. Offline playback of SAF-stored
downloads requires either:
- (a) `ParcelFileDescriptor` → fd → mpv `--stream-fd`, or
- (b) Copy to private cache before playback.

This is deferred to the download-playback phase.
