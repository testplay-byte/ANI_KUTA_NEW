# Download Folder Identity Refactor — Architecture Plan

> **Branch:** `feature/download-folder-identity-refactor` (DO NOT MERGE — for review)
> **Status:** Planning + Implementation
> **Date:** 2026-07-30

---

## 1. Problem Statement

The current download folder structure embeds the `contentId` in the folder name:

```
<root>/ANIKUTA/downloads/anime/<Title [al-154587]>/Episode 003/video.mp4
```

When the `contentId` changes (link to AniList, unlink from AniList, switch AniList entry, switch extension source), the folder can't be found because:
- `findAnimeDir(contentId)` does a suffix match on `[sanitized-contentId]`
- The folder still has the OLD contentId in its name
- The ContentIdMigrator explicitly skips downloads
- The "Transfer" option in the unlink dialog doesn't actually re-key or rename anything

**Result:** downloads become orphaned — the files are on disk but the app can't find them.

## 2. Proposed Architecture

### 2.1 Simple folder names (no ID in the name)

**Before:** `<Title [al-154587]>/Episode 003/`
**After:** `<Title>/Episode 003/`

The folder name is just the sanitized anime title. No ID, no brackets. The title is stable across link/unlink/switch operations (it comes from the extension or AniList, not from the identity).

### 2.2 `identity.json` — per-anime identity file

A new file at `<Title>/identity.json` carries ALL identity information:

```json
{
  "schemaVersion": 1,
  "contentId": "al:154587",
  "anilistId": 154587,
  "sourceId": 7240671891085951555,
  "sourceUrl": "https://gogoanime.gg/frieren-sousou-no-yakusoku",
  "title": "Frieren: Beyond Journey's End",
  "coverUrl": "https://...",
  "coverColor": "#B1F256",
  "extensionSystem": "aniyomi",
  "extensionName": "GogoAnime",
  "extensionPkgName": "eu.kanade.tachiyomi.animeextension.en.gogoanime",
  "extensionVersionName": "1.4.3",
  "extensionVersionCode": 143,
  "extensionLang": "en",
  "repoUrl": "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo",
  "createdAt": 1785365000000,
  "updatedAt": 1785365000000,
  "migrationHistory": [
    { "from": "aniyomi:7240:url", "to": "al:154587", "reason": "linked", "at": 1785365000000 },
    { "from": "al:154587", "to": "aniyomi:7240:url", "reason": "unlinked", "at": 1785366000000 }
  ]
}
```

### 2.3 How operations work with the new system

| Operation | What happens to the folder | What happens to identity.json |
|---|---|---|
| **Download episode** | Created if doesn't exist: `<Title>/Episode NNN/` | Created/updated with current identity |
| **Link to AniList** | **No change** (folder stays `<Title>/`) | `contentId` updated from `aniyomi:sid:url` → `al:X`; `anilistId` set; `migrationHistory` appended |
| **Unlink from AniList** | **No change** | `contentId` updated from `al:X` → `aniyomi:sid:url`; `anilistId` cleared; `migrationHistory` appended |
| **Switch AniList entry** | **No change** | `contentId` updated from `al:old` → `al:new`; `anilistId` updated; `migrationHistory` appended |
| **Switch extension source** | **No change** | `sourceId`, `sourceUrl`, `extensionName`, etc. updated; `migrationHistory` appended |
| **Extension uninstalled** | **No change** | `sourceId` stays (for "Unknown unavailable" display); identity.json is the source of truth |
| **Backup restore** | User picks the same folder | App scans for `identity.json` files → reads contentId → re-registers downloads |

### 2.4 How folder lookup works

**Before:** suffix match on `[sanitized-contentId]` → breaks when contentId changes
**After:** scan `anime/` folders → read `identity.json` → match by `contentId`

```kotlin
fun findAnimeDirByContentId(contentId: String): DocumentFile? {
    val animeDir = getAnimeBaseDir() ?: return null
    for (folder in animeDir.listFiles()) {
        if (!folder.isDirectory) continue
        val identity = readIdentityFile(folder)
        if (identity?.contentId == contentId) return folder
    }
    return null
}
```

**Fallback:** if no `identity.json` exists (legacy folder from before this refactor), fall back to the old suffix match `[sanitized-contentId]` → read + write `identity.json` on first access (migration).

### 2.5 No more "Transfer or Delete" prompt

With the new system, unlinking/linking/switching **never touches the folder or the files** — only `identity.json` is updated. Downloads are never orphaned. The prompt becomes unnecessary.

**Exception:** the "Delete" option is still useful for the user who wants to clean up. But "Transfer" is the default (and silent) behavior — no prompt needed.

## 3. New Module: `:core:download-identity`

A dedicated module for the identity file system:

```
:core:download-identity/
├── build.gradle.kts
└── src/main/java/app/confused/anikuta/core/downloadidentity/
    ├── DownloadIdentity.kt          — the @Serializable data class
    ├── DownloadIdentityStore.kt     — read/write identity.json to/from SAF
    └── DownloadIdentityManager.kt   — high-level: find folder, update identity, migrate
```

### Responsibilities:
- **`DownloadIdentity`** — the serializable data model (all fields from §2.2)
- **`DownloadIdentityStore`** — low-level SAF I/O: `read(folder): DownloadIdentity?`, `write(folder, identity)`, `delete(folder)`
- **`DownloadIdentityManager`** — high-level operations: `findAnimeDir(contentId)`, `updateIdentity(contentId, newIdentity)`, `migrateLegacyFolder(folder, contentId)`

### Dependencies:
- `:core:common` (for `ContentId`, `LocalId`, `ExtensionSystem`, `MetadataProviderId`)
- `:core:preferences` (for `PreferenceStore` — not needed, this is file-based)
- Android SAF (`androidx.documentfile:documentfile`)
- kotlinx-serialization

## 4. Migration Path

### 4.1 Existing folders (with `[contentId]` brackets)

On first launch post-update:
1. Scan `anime/` folder
2. For each folder, check if `identity.json` exists
3. If not: parse the `[contentId]` from the folder name → create `identity.json` with that contentId + the title (from the folder name, before the bracket)
4. **Do NOT rename the folder yet** — the old name works fine with the new lookup (scan + read JSON). The folder can be renamed on the next download (when a new episode is added, the folder is recreated with just the title).

### 4.2 New downloads (after this refactor)

New downloads create folders with just `<sanitized title>` (no brackets). `identity.json` is written at creation time.

### 4.3 Mixed state (old + new folders)

The lookup logic handles both:
- Folders with `identity.json` → read it
- Folders without `identity.json` → parse `[contentId]` from the name (legacy fallback)

## 5. Implementation Plan

### Phase 1: Create `:core:download-identity` module
- `DownloadIdentity.kt` — data model
- `DownloadIdentityStore.kt` — SAF read/write
- `DownloadIdentityManager.kt` — high-level operations
- Register in `settings.gradle.kts` + Koin

### Phase 2: Update `DownloadStorageProvider`
- `animeFolderName` → just `<sanitized title>` (no brackets, no contentId)
- `findAnimeDir(contentId)` → scan + read `identity.json` (with legacy fallback)
- `findEpisodeDirByNumber(contentId, episodeNumber)` → use new `findAnimeDir`
- `ensureEpisodeDir` → write `identity.json` on creation
- `deleteAnime` → use new `findAnimeDir`

### Phase 3: Update link/unlink/switch flows
- `AppController.unlinkFromAniList` → update `identity.json` (not rename folder)
- `AppController.onLinked` → update `identity.json`
- `AppController.switchAnilistAnime` → update `identity.json`
- `AnimeDetailViewModel.switchExtension` → update `identity.json`
- Remove the "Transfer or Delete" prompt (no longer needed — identity.json update is atomic + silent)

### Phase 4: Update ContentIdMigrator
- Add download re-keying via `DownloadIdentityManager.updateIdentity` (instead of skipping downloads)

### Phase 5: Migration on first launch
- Scan existing folders → create `identity.json` for legacy folders

### Phase 6: Update backup/restore
- Backup: `identity.json` is already in the download folder (not in the backup file itself — it's on disk)
- Restore: after restoring the library, the app scans the download folder for `identity.json` files → matches them to library entries by contentId → re-registers the downloads

## 6. Future-Proofing

The `identity.json` design supports:
- **Switching metadata providers** (AniList → MAL → TMDB): update `contentId` + `anilistId`/`malId`/`tmdbId` in the JSON
- **Switching extension systems** (Aniyomi → CloudStream): update `extensionSystem` + `sourceId` in the JSON
- **Switching extension repos**: update `repoUrl` in the JSON
- **Backup/restore across devices**: the folder structure is provider-agnostic (just `<Title>/Episode NNN/`); the `identity.json` carries all the provider-specific info

## 7. Trade-offs

### Pros
- Downloads NEVER become orphaned (folder name doesn't change)
- No "Transfer or Delete" prompt needed (identity update is silent)
- Backup/restore is simple (scan for `identity.json`)
- Future-proof (any identity change = JSON update, not folder rename)
- SAF rename (which is provider-dependent + can fail) is no longer needed

### Cons
- Folder lookup is slower (scan + read JSON vs. suffix match) — mitigated by caching
- Two folders with the same title would collide — mitigated by appending a short hash if a collision is detected
- `identity.json` could become stale if the user manually moves/renames folders — acceptable (the app can't control manual file operations)
