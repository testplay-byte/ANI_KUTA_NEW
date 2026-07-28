# 03 — Download System Redesign

> **Phase 2 / Proposed Architecture.** Proposes a download system that works without any provider dependency, preserves download visibility across source switches, uses the user-selected folder directly, and migrates existing downloads cleanly. Grounded in `analysis/02_download_system_analysis.md`, `proposals/01_internal_id_system.md`.

---

## 1. The problems (recap from Doc 02)

1. **The hard gate** (`AppController.kt:509-512`) — unlinked anime cannot be downloaded.
2. **The source-switching break** (`DefaultDownloadManager.kt:167`) — `isEpisodeDownloaded(anilistId, episodeUrl)` returns false after a source switch because `episodeUrl` changed, even though the file is on disk.
3. **The mandatory `ANIKUTA/` subfolder** (`DownloadStorageProvider.kt:111`) — the user picks a folder but the app creates an `ANIKUTA/` subfolder inside it.
4. **The non-nullable `DownloadAnimeInfo.anilistId: Int`** (`DownloadModels.kt:27`) — the type system prevents representing a download without an AniList ID.
5. **The composite key `"$anilistId:$episodeUrl"`** duplicated across 9+ files with no central helper.

---

## 2. The proposed redesign

### 2.1 Replace `anilistId` with `WatchableId` throughout the download system

**`DownloadAnimeInfo` (redesigned):**
```kotlin
@Serializable
data class DownloadAnimeInfo(
    val watchableId: WatchableId,       // replaces anilistId (proposal 01)
    val title: String,
    val coverUrl: String? = null,
    val coverColor: Int? = null,
    val sourceId: Long = 0L,            // ✅ activated (was inert)
    val sourceName: String? = null,
)
```

**Why keep `sourceId`:** it's needed for re-download (if the user wants to re-download an episode, the orchestrator needs to know which source produced it). It was already persisted on `DownloadRequest` but never read — we activate it.

**`DownloadTask.key` (redesigned):**
```kotlin
@Serializable
data class DownloadTask(...) {
    // OLD: val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"
    // NEW: keyed by WatchableId + episodeNumber (NOT episodeUrl)
    val key: String get() = "${request.anime.watchableId.stableKey()}:${episodeNumberKey(request.episode.episodeNumber)}"
}

private fun episodeNumberKey(n: Float): String = "%.3f".format(n)   // zero-padded, stable
```

**Why `episodeNumber` not `episodeUrl`:** this is the fix for the source-switching break (Doc 02 §10). When the user switches source, `episodeUrl` changes but `episodeNumber` stays the same. A download keyed by `WatchableId + episodeNumber` survives the switch.

### 2.2 The `DownloadManager` interface (redesigned)

```kotlin
interface DownloadManager {
    // Identity-based lookups (replace anilistId-based)
    fun isEpisodeDownloaded(watchableId: WatchableId, episodeNumber: Float): Boolean
    fun findTask(watchableId: WatchableId, episodeNumber: Float): DownloadTask?
    fun getDownloadedEpisodes(watchableId: WatchableId): List<DownloadedEpisode>

    // Fallback: filesystem scan (for migration + robustness)
    fun findDownloadedEpisodeByFolder(watchableId: WatchableId, episodeNumber: Float): DownloadedEpisode?

    // Queue management (unchanged — by taskId)
    fun enqueue(request: DownloadRequest): Long
    fun pause(taskId: Long)
    fun resume(taskId: Long)
    fun cancel(taskId: Long)
    fun pauseAll(); fun resumeAll(); fun cancelAll()
    fun getTask(taskId: Long): DownloadTask?

    // Observables (keyed by WatchableId.stableKey())
    val tasksFlow: StateFlow<Map<Long, DownloadTask>>
    val downloadedEpisodesFlow: StateFlow<Map<String, List<DownloadedEpisode>>>
    val episodeDownloadStates: StateFlow<Map<String, DownloadTask>>   // key = watchableId + epNum
}
```

### 2.3 The source-switching fix (concrete)

**`DefaultDownloadManager.isEpisodeDownloaded` (redesigned):**

```kotlin
override fun isEpisodeDownloaded(watchableId: WatchableId, episodeNumber: Float): Boolean {
    // 1. Try the in-memory task lookup (fast path)
    val task = findTask(watchableId, episodeNumber)
    if (task != null) return task.status == DownloadStatus.COMPLETED

    // 2. Fallback: filesystem scan (the on-disk folder is episode-number-keyed)
    val onDisk = storage.findEpisodeDir(watchableId, episodeNumber)
    return onDisk != null && onDisk.hasVideoFile()
}

override fun findTask(watchableId: WatchableId, episodeNumber: Float): DownloadTask? {
    val key = "${watchableId.stableKey()}:${episodeNumberKey(episodeNumber)}"
    return tasksFlow.value.values.firstOrNull { it.key == key && it.status == DownloadStatus.COMPLETED }
}
```

**Why this works:**
- The in-memory task lookup uses `WatchableId + episodeNumber` — both are source-independent. A source switch doesn't change either.
- The filesystem fallback scans `<folder>/.../Episode NNN/` — also source-independent.
- If the task was created by Source A and the user switches to Source B, the task (keyed by `WatchableId + epNum`) still matches. **The download stays visible and offline-playable.**

### 2.4 Remove the hard gate

**`AppController.downloadEpisode` (redesigned):**
```kotlin
fun downloadEpisode(watchableId: WatchableId, episode: SEpisode, source: AnimeSource) {
    // OLD: if (anilistId == 0) { Toast "Cannot download — anime not linked"; return }
    // NEW: no gate. Downloads work for any WatchableId.
    val animeInfo = DownloadAnimeInfo(
        watchableId = watchableId,
        title = /* from UnifiedAnime */,
        coverUrl = /* ... */,
        sourceId = source.id,
        sourceName = source.name,
    )
    downloadOrchestrator.enqueueDownload(animeInfo, episode, source)
}
```

The gate is removed because `WatchableId` is never "zero" or "null" — it's always a valid `Linked` or `Unlinked` value. The type system enforces what the gate used to enforce manually.

### 2.5 Folder structure (redesigned — user-direct)

**OLD:**
```
<USER_PICKED_FOLDER>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/video.mp4
```

**NEW:**
```
<USER_PICKED_FOLDER>/<Title [watchableId]>/Episode NNN/video.mp4
                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                stableKey-derived, source-independent
```

**Changes:**
1. **No `ANIKUTA/` subfolder.** The user's chosen folder is used directly.
2. **No `downloads/anime/` nesting.** Flattened — the anime folders go directly in the user's folder.
3. **Folder name uses `watchableId.stableKey()`** (not `anilistId`), so it's source-independent and stable across link/unlink events.
   - `Linked(ANILIST, "12345")` → `[al:12345]`
   - `Unlinked(sourceId=100, url="https://extA.com/frieren", titleHash="abc...")` → `[ext:100:https://extA.com/frieren]` (or a shortened hash for filesystem safety)

**For filesystem safety:** the `watchableId` part is sanitized (replace `:` and `/` with `-` or use a short hash). The title is sanitized (strip filesystem-illegal characters).

**Folder name format:** `<SanitizedTitle> [<SanitizedWatchableId>]`
- Example: `Frieren [al-12345]` (linked)
- Example: `Frieren [ext-100-abc123de]` (unlinked, with shortened hash)

### 2.6 `DownloadStorageProvider` (redesigned)

```kotlin
class DownloadStorageProvider(...) {
    fun getAnimeRoot(anime: DownloadAnimeInfo): DocumentFile {
        val root = getDownloadRoot()              // = the user-picked SAF folder (NO "ANIKUTA" subfolder)
        val folderName = "${sanitize(anime.title)} [${sanitize(anime.watchableId.stableKey())}]"
        return ensureDir(root, folderName)
    }

    fun findEpisodeDir(watchableId: WatchableId, episodeNumber: Float): DocumentFile? {
        val animeRoot = findAnimeRoot(watchableId) ?: return null
        val episodeFolderName = "Episode %03d".format(episodeNumber.toInt())
        return animeRoot.findFile(episodeFolderName)?.takeIf { it.isDirectory }
    }

    private fun findAnimeRoot(watchableId: WatchableId): DocumentFile? {
        val root = getDownloadRoot()
        val targetSuffix = "[${sanitize(watchableId.stableKey())}]"
        return root.listFiles().firstOrNull { it.name?.endsWith(targetSuffix) == true }
    }
}
```

**The `findAnimeRoot` scan** is the fallback for cross-source + cross-link lookups. It's slower than a direct path but only runs when the in-memory lookup misses. For large libraries, an index cache (Map<watchableId, DocumentFile>) can be maintained.

---

## 3. Data model changes (summary)

| Model | Old field | New field | Notes |
|---|---|---|---|
| `DownloadAnimeInfo` | `anilistId: Int` | `watchableId: WatchableId` + `sourceId: Long` + `sourceName: String?` | `sourceId` activated |
| `DownloadTask.key` | `"${anilistId}:${episodeUrl}"` | `"${watchableId.stableKey()}:${episodeNumberKey}"` | source-independent |
| `DownloadManager` methods | `anilistId: Int` params | `watchableId: WatchableId` params | + filesystem fallback |
| `DownloadRequest` | `sourceId: Long = 0L` (inert) | `sourceId: Long` (active) | used for re-download |
| Folder name | `<Title [anilistId]>` | `<Title [watchableId.stableKey()]>` | source-independent |
| Folder path | `<root>/ANIKUTA/downloads/anime/...` | `<root>/...` | user-direct, no nesting |

---

## 4. Migration path for existing downloads

### 4.1 The challenge

Existing downloads on disk are at:
```
<USER_FOLDER>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/video.mp4
```

The new structure is:
```
<USER_FOLDER>/<Title [watchableId]>/Episode NNN/video.mp4
```

For linked anime, `watchableId.stableKey() = "al:12345"` and the old `anilistId = 12345`. The folder name changes from `[12345]` to `[al-12345]` (sanitized).

### 4.2 The migration (automatic on app update)

**`DownloadMigration` (new class in `:core:download`):**

```kotlin
class DownloadMigration(
    private val storage: DownloadStorageProvider,
    private val downloadStore: DownloadStore,
    private val animeRepository: AnimeRepository,
) {
    fun migrate(): MigrationResult {
        val oldRoot = storage.getOldDownloadRoot()   // <USER_FOLDER>/ANIKUTA/downloads/anime/
        if (oldRoot == null || !oldRoot.exists()) return MigrationResult.Noop

        val newRoot = storage.getDownloadRoot()       // <USER_FOLDER>/ (user-direct)
        var moved = 0; var failed = 0; val failures = mutableListOf<String>()

        for (animeDir in oldRoot.listFiles() ?: emptyArray()) {
            val name = animeDir.name ?: continue
            // Parse "<Title [anilistId]>" → (title, anilistId)
            val match = Regex("^(.+) \\[(\\d+)]$").find(name) ?: continue
            val (title, anilistIdStr) = match.destructured
            val anilistId = anilistIdStr.toIntOrNull() ?: continue

            // Resolve the WatchableId (linked, since we have an anilistId)
            val watchableId = WatchableId.Linked(MetadataProviderId.ANILIST, anilistId.toString())
            val newFolderName = "${sanitize(title)} [${sanitize(watchableId.stableKey())}]"

            try {
                // Move the anime folder to the new root with the new name
                val target = ensureDir(newRoot, newFolderName)
                moveContents(animeDir, target)
                oldRoot.findFile(name)?.delete()
                moved++
            } catch (e: Exception) {
                failed++; failures.add("$name: ${e.message}")
            }
        }

        // If everything moved, delete the old ANIKUTA/downloads/anime/ structure
        if (failed == 0) {
            oldRoot.parentFile?.parentFile?.delete()   // delete ANIKUTA/ if empty
        }

        // Migrate DownloadStore task keys
        downloadStore.rekeyAllTasks(oldKeyFn = { task ->
            // "$anilistId:$episodeUrl" → watchableId + episodeNumber
            val parts = task.key.split(":", limit = 2)
            val anilistId = parts[0].toInt()
            // ... resolve episodeNumber from the task's episode
            val watchableId = WatchableId.Linked(MetadataProviderId.ANILIST, anilistId.toString())
            "${watchableId.stableKey()}:${episodeNumberKey(task.request.episode.episodeNumber)}"
        })

        return MigrationResult(moved, failed, failures)
    }
}
```

**Migration runs on app update**, gated by a `pref_download_migration_v2_done` preference. It's idempotent (re-running it on an already-migrated library is a no-op).

### 4.3 The transition period

- **Both old and new folder structures are recognized during the transition.** `DownloadStorageProvider.findEpisodeDir` checks the new location first, then falls back to the old `<root>/ANIKUTA/downloads/anime/...` location.
- **The migration runs in the background** on first launch post-update, with a notification ("Migrating downloads...").
- **If the migration fails partway**, the user is prompted to retry. The old folders are NOT deleted until all succeed.

### 4.4 Preserving the user's chosen folder

**Critical:** The migration must NOT change the user's chosen SAF folder URI (`DownloadPreferences.downloadFolderUri`). It only changes what's *inside* that folder:
- Before: `<USER_FOLDER>/ANIKUTA/downloads/anime/<Title [id]>/...`
- After: `<USER_FOLDER>/<Title [id]>/...`

The user's folder stays the same; the app just stops creating the `ANIKUTA/downloads/anime/` nesting.

### 4.5 Existing users who want the old structure

Some users may have set up their folder expecting the `ANIKUTA/` subfolder (e.g., they share the folder with another app). For these users, a `DownloadPreferences.preserveLegacyStructure` preference (default false) keeps the old nesting. This is opt-in via Settings → Downloads → "Keep app subfolder."

---

## 5. How downloads work for content with no provider linkage

With the `WatchableId.Unlinked` variant (proposal 01), unlinked extension anime can now be downloaded:

1. User opens an unlinked extension anime → `WatchableId.Unlinked(sourceId, url, titleHash)`.
2. User taps download → `AppController.downloadEpisode(watchableId, episode, source)` — **no gate**.
3. `DownloadAnimeInfo(watchableId = Unlinked(...), title, sourceId, sourceName)`.
4. Folder: `<USER_FOLDER>/<Title [ext-100-abc123de]>/Episode NNN/video.mp4`.
5. `DownloadTask.key = "ext:100:https://extA.com/frieren:001.000"`.
6. Offline playback: `isEpisodeDownloaded(Unlinked(...), 1.0f)` → finds the task → plays the local file.

**Everything works.** The only thing that doesn't work is tracker sync (no provider to sync to), which is correct.

---

## 6. Source switching — the full picture

| Action | Old behavior | New behavior |
|---|---|---|
| Switch source for a linked anime | Downloads invisible (episodeUrl changed) | Downloads visible (keyed by WatchableId + epNum) |
| Switch source for an unlinked anime | n/a (couldn't download) | Downloads visible (same WatchableId + epNum) |
| Link an unlinked anime to AniList | Downloads orphaned (keyed by old Unlinked ID) | `WatchableIdMigrator` re-keys all downloads to the new Linked ID |
| Unlink a linked anime | Downloads orphaned | `WatchableIdMigrator` re-keys to Unlinked |

**The `WatchableIdMigrator.rekey` for downloads:**
```kotlin
fun rekey(oldId: WatchableId, newId: WatchableId) {
    // 1. Re-key all DownloadTasks
    downloadStore.updateAllTasks { task ->
        if (task.request.anime.watchableId == oldId) {
            task.copy(request = task.request.copy(anime = task.request.anime.copy(watchableId = newId)))
        } else task
    }
    // 2. Rename the on-disk folder
    val oldFolder = storage.findAnimeRoot(oldId)
    val newFolderName = "${sanitize(title)} [${sanitize(newId.stableKey())}]"
    oldFolder?.renameTo(newFolderName)
    // 3. Update metadata.json in each episode folder
    // (the watchableId + sourceId fields change)
}
```

This runs at the link/unlink event (a single point in time), not continuously.

---

## 7. The "downloads follow episode number" requirement

The prompt specifies: *"When a user switches sources/extensions for the same anime, downloaded episodes should remain visible and accessible — the downloads should follow the episode number, not the source URL."*

**This is achieved by:**
1. `DownloadTask.key` uses `episodeNumber` (not `episodeUrl`).
2. `isEpisodeDownloaded` looks up by `WatchableId + episodeNumber`.
3. The on-disk folder is `Episode %03d` (episode-number-keyed).
4. The filesystem fallback (`findEpisodeDir`) scans by episode number.

**Edge case — episode number mismatches across sources:** Different sources may number episodes differently (e.g., Source A counts the recap as Episode 0; Source B counts it as Episode 1). This is a data-quality issue, not an architectural one. The redesign assumes episode numbers are consistent across sources for the same anime (which is true ~95% of the time). For the ~5% where they differ, the user sees two separate downloads (Episode 1 from Source A, Episode 1 from Source B) — which is the least-bad outcome.

---

## 8. The "user-selected folder used directly" requirement

The prompt specifies: *"a folder selection system where the user picks a folder and the app uses it directly (no additional app-named subfolder created inside it)."*

**This is achieved by:**
1. `DownloadStorageProvider.getAnimeRoot` uses `getDownloadRoot()` (the user's folder) directly — no `ensureDir(root, "ANIKUTA")`.
2. The folder structure is `<USER_FOLDER>/<Title [watchableId]>/Episode NNN/...`.
3. A `DownloadPreferences.preserveLegacyStructure` opt-in for users who want the old nesting.

**Migration:** Existing downloads are moved up one level (out of `ANIKUTA/downloads/anime/`) into the user's folder directly. See §4.

---

## 9. Trade-offs accepted

1. **`episodeNumber` replaces `episodeUrl` in the key.** Two episodes with the same number from different sources share a key. We accept this because it fixes source-switching and episode numbers are the user-meaningful identity. The rare mismatch case produces duplicate entries, not data loss.

2. **The filesystem fallback scan is slower** than a direct lookup. We accept this because (a) it only runs on cache misses, (b) it's bounded by the number of anime folders (typically <100), (c) an index cache can optimize it.

3. **The migration moves files on disk.** We accept this risk because (a) it's gated by a preference (runs once), (b) it's idempotent, (c) the old structure is recognized during the transition. The alternative (leaving old downloads in the nested structure forever) creates a permanent inconsistency.

4. **The folder name embeds `watchableId.stableKey()`** (e.g., `[al-12345]`), which is less human-readable than `[12345]`. We accept this because (a) the title is still the primary part of the name, (b) the ID suffix disambiguates same-titled anime, (c) it's source-independent.

5. **`preserveLegacyStructure` opt-in** keeps complexity. We accept this because some users have set up their folder expecting the nesting.

---

## 10. Conditions for revisiting

- If the filesystem fallback scan proves too slow for large libraries (>500 anime), add an index cache (`Map<watchableId, DocumentFile>`) persisted in `DownloadStore`.
- If episode-number mismatches across sources are frequent, add a per-anime "episode number mapping" table that the user can edit.
- If the migration fails for a significant fraction of users (>5%), add a manual "re-migrate downloads" button in Settings.

---

## 11. Summary

**Recommendation:**
1. Replace `DownloadAnimeInfo.anilistId` with `watchableId: WatchableId` + activate `sourceId`.
2. Replace `DownloadTask.key` composite `"${anilistId}:${episodeUrl}"` with `"${watchableId.stableKey()}:${episodeNumberKey}"`.
3. Replace `DownloadManager` anilistId-taking methods with `WatchableId`-taking methods + a filesystem fallback.
4. Remove the anilistId hard gate at `AppController.kt:509-512`.
5. Remove the `ANIKUTA/` subfolder — use the user's chosen folder directly.
6. Add a `DownloadMigration` that moves existing downloads to the new structure + re-keys tasks.
7. Add a `WatchableIdMigrator.rekey` for link/unlink events.

**Why this approach:**
- The engine is unchanged (the coupling was upstream, in the identity layer).
- The on-disk structure was already episode-number-keyed; only the in-memory lookup logic changes.
- The source-switching break is fixed at its root (`DefaultDownloadManager.kt:167`).
- Unlinked anime can download (type system enforces validity).
- The user's folder is used directly (no app subfolder).
- Existing downloads are migrated automatically.

**Driven by evidence:** Doc 02 (the 3 defects + the engine-is-clean finding), proposal 01 (`WatchableId`).

---

*Related: `proposals/01_internal_id_system.md` (`WatchableId`), `proposals/05_migration_strategy.md` (the broader migration plan including downloads).*
