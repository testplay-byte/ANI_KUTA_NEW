# 05 — Translation Plan (Aniyomi → ANIKUTA)

> How ANIKUTA will implement a proper Aniyomi backup restore.
> This is the blueprint for the next implementation phase.

## Current state

ANIKUTA has a basic `AniyomiBackupFormat` (`core/backup/format/AniyomiBackupFormat.kt`)
that:
- ✅ Decodes protobuf (modern + legacy formats)
- ✅ Maps anime/episodes/categories/tracking/history to `BackupContainer`
- ✅ Handles gzip decompression
- ❌ Does NOT resolve AniList IDs (anime are inserted without `anilistId`)
- ❌ Does NOT remap episode URLs to AniList-keyed progress
- ❌ Does NOT match source IDs (Aniyomi source IDs ≠ ANIKUTA source IDs)
- ❌ Does NOT do AniList title search for unmatched anime
- ❌ Does NOT fetch AniList metadata after restore

## Target architecture

```
AniyomiBackupFormat.read()
    ↓
AniyomiBackup (decoded protobuf)
    ↓
AniyomiBackupTranslator.translate()
    ├── 1. Resolve AniList IDs (tracker bindings → MAL lookup → title search)
    ├── 2. Remap source IDs (Aniyomi source → ANIKUTA extension source)
    ├── 3. Build SourceLinkStore entries (anilistId → sourceId+url)
    ├── 4. Remap watch progress keys (sourceId:url:epUrl → anilistId:epUrl)
    └── 5. Build BackupContainer (ready for provider import)
    ↓
BackupManager.restoreBackup()
    ↓ (existing provider-based restore)
Done
```

## New module: `AniyomiBackupTranslator`

**Location:** `core/backup/src/main/java/.../format/aniyomi/AniyomiBackupTranslator.kt`

**Responsibility:** Transform a decoded `AniyomiBackup` into a
`BackupContainer` with all AniList IDs resolved.

### Dependencies

- `AniListApi` (for title search + MAL→AniList lookup)
- `AnimeExtensionManager` (for source ID matching by name)
- `SourceLinkStore` (to cache anilistId → sourceId+url links)

### Translation steps

#### Step 1: Resolve AniList IDs

```kotlin
suspend fun resolveAnilistIds(anime: List<AniyomiBackupAnime>): Map<Int, AnilistResolution> {
    val results = mutableMapOf<Int, AnilistResolution>()

    anime.forEachIndexed { index, ani ->
        val resolution = when {
            // Strategy 1: AniList tracker binding
            ani.tracking.any { it.syncId == 2 && it.mediaId != 0L } -> {
                val anilistId = ani.tracking.first { it.syncId == 2 }.mediaId.toInt()
                AnilistResolution.Resolved(anilistId, "tracker")
            }

            // Strategy 2: MAL tracker binding → AniList lookup
            ani.tracking.any { it.syncId == 1 && it.mediaId != 0L } -> {
                val malId = ani.tracking.first { it.syncId == 1 }.mediaId.toInt()
                val anilistId = anilistApi.searchByMalId(malId)
                if (anilistId != null) {
                    AnilistResolution.Resolved(anilistId, "mal-lookup")
                } else {
                    AnilistResolution.NeedsSearch(ani.title)
                }
            }

            // Strategy 3: Title search (deferred — batch later)
            else -> AnilistResolution.NeedsSearch(ani.title)
        }
        results[index] = resolution
    }

    // Batch title search for all NeedsSearch entries
    val toSearch = results.filterValues { it is AnilistResolution.NeedsSearch }
    toSearch.forEach { (index, res) ->
        val title = (res as AnilistResolution.NeedsSearch).title
        val anilistId = anilistApi.searchByTitle(title)
        results[index] = if (anilistId != null) {
            AnilistResolution.Resolved(anilistId, "title-search")
        } else {
            AnilistResolution.Failed("No AniList match for '$title'")
        }
    }

    return results
}

sealed class AnilistResolution {
    data class Resolved(val anilistId: Int, val method: String) : AnilistResolution()
    data class NeedsSearch(val title: String) : AnilistResolution()
    data class Failed(val reason: String) : AnilistResolution()
}
```

#### Step 2: Remap source IDs

Aniyomi source IDs are generated from the extension's package name. ANIKUTA
uses the same extension system (Aniyomi-compatible), so the source IDs
**should match** if the same extension is installed.

If the source ID doesn't match any installed extension, we try to match by
source **name** (from `BackupAnimeSource.name`):

```kotlin
fun remapSourceId(aniyomiSourceId: Long, sourceName: String?): Long {
    // 1. Check if aniyomiSourceId exists in our installed extensions
    val directMatch = extensionManager.getInstalledSources()
        .firstOrNull { it.id == aniyomiSourceId }
    if (directMatch != null) return directMatch.id

    // 2. Match by name
    if (sourceName != null) {
        val nameMatch = extensionManager.getInstalledSources()
            .firstOrNull { it.name.equals(sourceName, ignoreCase = true) }
        if (nameMatch != null) return nameMatch.id
    }

    // 3. No match — return original (anime will be stored but episodes won't load)
    return aniyomiSourceId
}
```

#### Step 3: Build SourceLinkStore entries

For each anime with a resolved AniList ID:

```kotlin
// SourceLinkStore: anilistId → (sourceId, animeUrl, animeTitle)
sourceLinkStore.saveLink(
    anilistId = resolvedAnilistId,
    sourceId = remappedSourceId,
    animeUrl = ani.url,
    animeTitle = ani.title,
)

// ExtensionLinkStore: "sourceId:animeUrl" → anilistId
extensionLinkStore.link(
    sourceId = remappedSourceId,
    animeUrl = ani.url,
    anilistId = resolvedAnilistId,
)
```

#### Step 4: Remap watch progress keys

Aniyomi history uses episode URLs. ANIKUTA's WatchProgressStore uses
`"anilistId:episodeUrl"` keys. After resolving AniList IDs:

```kotlin
aniyomiBackup.backupAnime.forEach { ani ->
    val anilistId = resolutions[index]?.anilistId ?: return
    ani.history.forEach { hist ->
        val key = "$anilistId:${hist.url}"
        progressEntries[key] = WatchProgressItem(
            positionSeconds = hist.readDuration.toInt(),
            durationSeconds = 0,
            title = ani.title,
            updatedAt = hist.lastRead,
            animeTitle = ani.title,
            coverUrl = ani.thumbnailUrl,
        )
    }
}
```

#### Step 5: Build BackupContainer

Assemble all translated data into a `BackupContainer` that the existing
`BackupManager.restoreBackup()` can process.

## AniList API additions needed

### `searchByMalId(malId: Int): Int?`

```graphql
query {
  Media(idMal: $malId, type: ANIME) {
    id
  }
}
```

### `searchByTitle(title: String): Int?`

```graphql
query {
  Page(search: $title, perPage: 5) {
    media(type: ANIME) {
      id
      title { romaji english native }
    }
  }
}
```

Match by: exact `romaji` or `english` title (case-insensitive). If no exact
match, return the first result (user can re-link later).

## UI flow for Aniyomi restore

```
1. User selects .tachibk file
2. Show progress: "Decoding Aniyomi backup..."
3. Show progress: "Resolving AniList IDs (X/Y)..."
   - This may take time (network calls for title search)
   - Show a progress bar + count
4. Show summary dialog:
   - "X anime resolved via tracker"
   - "Y anime resolved via title search"
   - "Z anime could not be matched" (with option to re-link later)
5. User clicks "Restore"
6. Show restore animation (5 sec min)
7. Show restore complete dialog
8. Redirect to Library
```

## Error handling

| Error | Handling |
|---|---|
| File not gzip/protobuf | "Corrupt backup file" error |
| AniList API failure (search) | Skip that anime, mark as "unresolved", continue |
| AniList API failure (MAL lookup) | Fall back to title search |
| No extensions installed | Source IDs won't remap, but anime still imported (episodes won't load until extension installed) |
| Duplicate anime (already in library) | Merge — keep newer progress |

## Testing plan

1. **Unit test:** Decode a sample Aniyomi backup → verify protobuf models
2. **Integration test:** Translate a backup with tracker bindings → verify AniList IDs resolved
3. **Integration test:** Translate a backup without tracker bindings → verify title search works
4. **E2E test:** Create backup in Aniyomi → restore in ANIKUTA → verify library populated
5. **Edge case:** Backup with manga-only anime (no tracker, obscure title) → verify graceful skip

## Future enhancements

- **Batch AniList search:** Use AniList's bulk query (up to 50 IDs per request) for MAL→AniList lookups
- **User confirmation:** For low-confidence title matches, show a "Did you mean...?" dialog
- **Re-link UI:** A settings page showing "unresolved anime from Aniyomi backup" with manual search/link
- **Source installation prompt:** If a source isn't installed, offer to open the extensions settings
