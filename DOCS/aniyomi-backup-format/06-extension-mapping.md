# 06 — Extension System Mapping

> How Aniyomi's extension-based source system maps to ANIKUTA's
> AniList-based system with extension support.

## The fundamental difference

### Aniyomi's model

```
Extension (APK) → Source (with a numeric ID) → Anime (identified by source+url)
                                                     ↓
                                               Episodes (identified by url)
```

- **Primary key:** `(sourceId, animeUrl)` — the extension's internal URL
- **AniList is optional:** Only used as a tracker (one of many)
- **Source IDs:** Generated from the extension's package name hash
- **Anime identity is tied to the extension** — switching extensions means losing track

### ANIKUTA's model

```
AniList (primary data source) → Anime (identified by anilistId)
                                     ↓
                               Extension Source (matched via SourceLinkStore)
                                     ↓
                               Episodes (identified by episodeUrl within the source)
```

- **Primary key:** `anilistId` — stable across extensions
- **AniList is primary:** Used for discovery, metadata, and identity
- **Extensions are secondary:** Used for streaming (episode lists + video URLs)
- **SourceLinkStore bridges them:** Maps `anilistId → (sourceId, animeUrl)`

## How source IDs work

### Aniyomi source ID generation

```kotlin
// In Aniyomi's extension loader
val sourceId = source.id  // Generated from package name
// e.g., "en.gogoanime" → 1234567890L
```

The source ID is a `Long` generated from the extension's package name. It's
**stable** — the same extension always generates the same source ID, even
across devices.

### ANIKUTA source ID

ANIKUTA uses the same Aniyomi-compatible extension system (ADR-029), so
source IDs are generated the same way. **If the same extension is installed
in both Aniyomi and ANIKUTA, the source IDs will match.**

### Matching strategy

1. **Direct match (best case):** The same extension is installed in ANIKUTA →
   source IDs match directly.

2. **Name match (fallback):** If the source ID doesn't match (different
   extension version, or extension not installed), match by source name.
   The Aniyomi backup includes `BackupAnimeSource(name, sourceId)` for each
   source.

3. **No match:** The extension isn't installed. The anime is still imported
   (with metadata from the backup), but episodes won't load until the user
   installs the matching extension.

## Anime identity translation

### Aniyomi → ANIKUTA

```
Aniyomi anime:
  source = 1234567890  (e.g., Gogoanime)
  url = "/category/some-anime"
  title = "Some Anime"
  tracking = [{ syncId: 2, mediaId: 101522 }]  (AniList)

↓ Translation ↓

ANIKUTA anime:
  anilistId = 101522  (from tracking)
  sourceId = 1234567890  (same, if extension installed)
  url = "/category/some-anime"  (same)
  title = "Some Anime"  (will be overwritten by AniList title)

SourceLinkStore:
  101522 → { sourceId: 1234567890, animeUrl: "/category/some-anime", title: "Some Anime" }
```

### Without tracker bindings

```
Aniyomi anime:
  source = 1234567890
  url = "/category/some-anime"
  title = "Some Anime"
  tracking = []  (no AniList binding)

↓ Translation ↓

1. AniList title search: "Some Anime" → found anilistId = 101522
2. ANIKUTA anime:
     anilistId = 101522
     sourceId = 1234567890
     url = "/category/some-anime"
3. SourceLinkStore: 101522 → { sourceId, url, title }
```

## Episode identity

### Aniyomi episodes

Episodes are identified by their URL within the source:

```
anime.source = 1234567890
anime.url = "/category/some-anime"
episode.url = "/watch/some-anime-episode-1"
```

### ANIKUTA episodes

Episodes are stored in the `episodes` SQLDelight table, keyed by `anime_id`
(the local DB id of the parent anime). The episode `url` is preserved from
the extension.

After Aniyomi backup restore:
1. The anime is inserted (with resolved `anilistId`)
2. The local `anime_id` is obtained
3. Episodes are inserted with `anime_id = localId` and `url = episode.url`

### Watch progress remapping

Aniyomi history keys episodes by URL:
```
history.url = "/watch/some-anime-episode-1"
history.lastRead = 1700000000
history.readDuration = 300
```

ANIKUTA's `WatchProgressStore` keys by `"anilistId:episodeUrl"`:
```
key = "101522:/watch/some-anime-episode-1"
value = { positionSeconds: 300, updatedAt: 1700000000, ... }
```

**Translation:** Replace the anime identity part of the key with the resolved
`anilistId`, keep the episode URL.

## Source/extension data in the backup

### What the backup includes

| Data | Field | Useful? |
|---|---|---|
| `backupAnimeSources` | List of `{name, sourceId}` | ✅ Yes — for name-based matching |
| `backupExtensions` | List of `{pkgName, apk}` | ❌ No — APK bundles are too heavy |
| `backupAnimeExtensionRepo` | List of repo URLs | ❌ No — we have our own repo system |

### What ANIKUTA does with source data

1. During restore, for each anime, check if its `source` ID matches an
   installed extension.
2. If not, try to match by `BackupAnimeSource.name`.
3. Build `SourceLinkStore` entries: `anilistId → (sourceId, animeUrl)`.
4. This allows the anime details page to immediately load episodes from
   the extension without re-searching.

## Extension installation status

After restoring an Aniyomi backup, some anime may reference source IDs that
aren't installed. ANIKUTA should:

1. **Show a warning:** "X anime reference extensions that aren't installed."
2. **List the missing sources:** By name (from `BackupAnimeSource.name`).
3. **Offer to open Extensions settings:** So the user can install them.
4. **Graceful degradation:** The anime still appears in the library (with
   AniList metadata), but episodes won't load until the extension is installed.

## Category mapping

Aniyomi categories are local DB IDs. ANIKUTA also uses local DB IDs, but
they won't match. The restore must:

1. **Match by name:** For each Aniyomi category, find or create a local
   category with the same name.
2. **Build a remap table:** `oldAniyomiCategoryId → newLocalCategoryId`.
3. **Remap anime→category links:** For each anime's `categories: List<Long>`,
   use the remap table to get the new category IDs.
4. **Insert `anime_category` rows:** With the local anime DB ID + remapped
   category ID.

### Default category

Both Aniyomi and ANIKUTA have a "Default" category (id=1 in ANIKUTA). If
the Aniyomi backup has a "Default" category, it's matched to our Default
(not duplicated).

## Tracker binding mapping

### AniList (syncId = 2)

```
Aniyomi tracking:
  syncId = 2
  mediaId = 101522  (AniList anime ID)

↓ ↓ ↓

ANIKUTA:
  - anime.anilistId = 101522 (already resolved)
  - animetrack row: { animeId, trackerId: 2, remoteId: 101522 }
  - AniList OAuth token: NOT in the Aniyomi backup (user must login separately)
```

**Note:** Aniyomi backup does NOT include OAuth tokens — only the tracker
bindings (mediaId + status + score). The user must log in to AniList/MAL
in ANIKUTA separately. After login, the restored bindings will sync.

### MAL (syncId = 1)

```
Aniyomi tracking:
  syncId = 1
  mediaId = 40748  (MAL anime ID)

↓ ↓ ↓

ANIKUTA:
  1. AniList API: query { Media(idMal: 40748) { id } } → anilistId = 101522
  2. anime.anilistId = 101522
  3. animetrack row: { animeId, trackerId: 1, remoteId: 40748 }
  4. animetrack row: { animeId, trackerId: 2, remoteId: 101522 } (auto-linked)
```

### Other trackers (syncId 3-7)

Kitsu, Simkl, Bangumi, Shikimori — ANIKUTA doesn't support these. The
tracker binding data is preserved in the backup (so it can be restored to
Aniyomi if needed), but ANIKUTA ignores it.

**Future:** If ANIKUTA adds support for these trackers, the data is
available in the `BackupEntry.Tracker` model (stored as `TrackerTrackItem`
with the original `trackerId`).

## Summary: translation pipeline

```
Aniyomi backup
    ↓
Decode protobuf
    ↓
For each anime:
    1. Resolve AniList ID (tracker → MAL lookup → title search)
    2. Remap source ID (direct → name match → unmapped)
    3. Build SourceLinkStore entry
    4. Remap episode URLs (keep as-is, keyed by anilistId)
    5. Remap watch progress (anilistId:episodeUrl)
    6. Remap category links (by name → remap IDs)
    7. Copy tracker bindings (with resolved remoteId)
    ↓
BackupContainer (ANIKUTA format)
    ↓
BackupManager.restoreBackup()
    ↓
Library populated with AniList IDs + extension links
```
