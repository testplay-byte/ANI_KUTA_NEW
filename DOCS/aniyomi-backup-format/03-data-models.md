# 03 — Data Models & ANIKUTA Mapping

> How each Aniyomi backup model maps to ANIKUTA's backup models.

## Overview

Aniyomi and ANIKUTA have fundamentally different architectures:

| Aspect | Aniyomi | ANIKUTA |
|---|---|---|
| Primary ID | Extension source + anime URL | AniList media ID |
| Anime identity | `(sourceId, url)` | `anilistId` |
| Episode identity | `(animeId, url)` | `(anilistId, episodeUrl)` |
| Source system | Extensions provide sources | Extensions provide sources (Aniyomi-compatible) |
| Tracker | Optional (MAL/AniList/Kitsu/etc.) | AniList is primary data source |
| Categories | Local DB IDs | Local DB IDs (but matched by name) |

**Key challenge:** Aniyomi anime are identified by `(source, url)`. ANIKUTA
anime are identified by `anilistId`. To restore an Aniyomi backup, we must
**resolve each anime to an AniList ID** — either via tracker bindings or
via AniList title search.

## Model mapping

### `BackupAnime` → `AnimeBackup`

| Aniyomi field | ANIKUTA field | Notes |
|---|---|---|
| `source` | `sourceId` | Direct copy (but source ID may differ — see [06-extension-mapping.md](06-extension-mapping.md)) |
| `url` | `url` | Direct copy (extension-specific URL) |
| `title` | `title` | Direct copy |
| `artist` | `artist` | Direct copy |
| `author` | `author` | Direct copy |
| `description` | `description` | Direct copy |
| `genre: List<String>` | `genre: String?` | Join with `,` (ANIKUTA stores comma-separated) |
| `status: Int` | `status: Long` | Cast to Long |
| `thumbnailUrl` | `coverUrl` | Direct copy (AniList CDN URL if from AniList tracker) |
| `favorite` | `favorite` | Direct copy |
| `dateAdded` | `dateAdded` | Direct copy |
| `viewer_flags` | `viewerFlags` | Direct copy |
| `episode_flags` | (not mapped) | ANIKUTA uses separate display preferences |
| `updateStrategy` | `updateStrategy` | Direct copy (Int → Long) |
| `lastModifiedAt` | `coverLastModified` | Direct copy |
| — | `anilistId` | **Resolved from tracking or AniList search** |
| — | `coverColor` | Not available (ANIKUTA extracts from cover) |
| — | `score` | Not available (ANIKUTA fetches from AniList) |
| — | `totalEpisodes` | Not available (ANIKUTA fetches from AniList) |
| — | `lastWatched` | Not available (derived from history) |
| — | `nextAiringEpisode` | Not available (ANIKUTA fetches from AniList) |
| `parentId`, `seasonNumber`, etc. | (not mapped) | Season grouping not supported yet |

### `BackupEpisode` → `EpisodeBackup`

| Aniyomi field | ANIKUTA field | Notes |
|---|---|---|
| `url` | `url` | Direct copy (extension-specific) |
| `name` | `name` | Direct copy |
| `scanlator` | `scanlator` | Direct copy |
| `seen` | `seen` | Direct copy |
| `bookmark` | `bookmark` | Direct copy |
| `lastSecondSeen` | `lastSecondSeen` | Direct copy (playback position) |
| `totalSeconds` | `totalSeconds` | Direct copy (episode duration) |
| `dateFetch` | `dateFetch` | Direct copy |
| `dateUpload` | `dateUpload` | Direct copy |
| `episodeNumber` | `episodeNumber` | Float → Double |
| `sourceOrder` | `sourceOrder` | Direct copy |
| `fillermark: Boolean` | `fillermark: String?` | `true` → `"filler"`, `false` → `null` |
| `summary` | `summary` | Direct copy |
| `previewUrl` | `previewUrl` | Direct copy |
| — | `animeId` | **Resolved from parent anime's local DB ID** |

### `BackupAnimeTracking` → `TrackerTrackItem`

| Aniyomi field | ANIKUTA field | Notes |
|---|---|---|
| `syncId` | `trackerId` | Direct copy (1=MAL, 2=AniList) |
| `mediaId` (field 100) | `remoteId` | **This is the AniList media ID if syncId==2** |
| `mediaIdInt` (field 3) | (fallback) | Used if `mediaId == 0` (legacy) |
| `trackingUrl` | `remoteUrl` | Direct copy |
| `lastEpisodeSeen` | `lastSeen` | Float → Long |
| `totalEpisodes` | `totalEpisodes` | Int → Long |
| `score` | `score` | Float → Double |
| `status` | `status` | Int → Long |
| — | `animeId` | **Resolved from parent anime's local DB ID** |
| — | `displayScore` | Not available |

### `BackupAnimeHistory` → `WatchProgressItem`

| Aniyomi field | ANIKUTA field | Notes |
|---|---|---|
| `url` | (in key) | Episode URL — used in the progress key |
| `lastRead` | `updatedAt` | Direct copy (timestamp) |
| `readDuration` | `positionSeconds` | Direct copy (seconds watched) |
| — | `durationSeconds` | Not available (0) |
| — | `title` | From parent anime |
| — | `animeTitle` | From parent anime |
| — | `coverUrl` | From parent anime |
| — | `episodeNumber` | Not available (-1) |
| — | `thumbnailUrl` | Not available |

**Key format:** `"${anime.source}:${anime.url}:${history.url}"` — on restore,
we need to match this to our `"$anilistId:$episodeUrl"` format by resolving
the anime's AniList ID and finding the matching episode URL.

### `BackupCategory` → `CategoryBackup`

| Aniyomi field | ANIKUTA field | Notes |
|---|---|---|
| `name` | `name` | Direct copy (used for matching — categories merge by name) |
| `order` | `order` | Direct copy |
| `id` | `_id` | **Remapped on restore** (old ID → new local ID) |
| `flags` | `flags` | Direct copy |
| — | `hidden` | Not available (ANIKUTA-specific) |

### Anime→Category links (`BackupAnime.categories`)

Each `BackupAnime` has a `categories: List<Long>` field — the list of category
IDs the anime belongs to. On restore:
1. The category IDs are remapped (old Aniyomi ID → new ANIKUTA ID, matched by name)
2. The anime's local DB ID is resolved (after the anime is inserted)
3. `anime_category` junction rows are inserted

## Missing data (not in Aniyomi backup)

These ANIKUTA-specific fields are NOT available in Aniyomi backups:

| ANIKUTA field | How we handle it |
|---|---|
| `anilistId` | **Resolved via tracking or AniList title search** |
| `coverColor` | Extracted from cover image after restore (or left null) |
| `score` | Fetched from AniList after resolve |
| `totalEpisodes` | Fetched from AniList after resolve |
| `nextAiringEpisode` | Fetched from AniList after resolve |
| `lastWatched` | Derived from history entries |
| `releaseDate` | Fetched from AniList after resolve |
| `lastRefresh` | Set to current time on restore |
| `lastMetadataFetch` | Set to 0 (will fetch on next details page open) |
| `nextEpisodeCheck` | Set to 0 |
| Episode metadata (enriched) | Not available (re-fetched from our metadata sources) |
| Preferences | Not imported (different app) |
| Source links | Re-built from the anime's `(sourceId, url)` + resolved AniList ID |

## AniList ID resolution strategies

This is the **core challenge** of Aniyomi backup restore. Strategies, in
priority order:

### Strategy 1: Tracker bindings (best)

If the anime has a tracking entry with `syncId == 2` (AniList), the `mediaId`
field IS the AniList anime ID. This is 100% reliable.

```
Aniyomi anime → tracking.find { it.syncId == 2 } → mediaId = AniList ID
```

### Strategy 2: MAL ID → AniList ID lookup

If the anime has a tracking entry with `syncId == 1` (MAL), the `mediaId` is
a MAL anime ID. We can use the AniList API's `Media(idMal: $malId)` query to
resolve the AniList ID.

```
Aniyomi anime → tracking.find { it.syncId == 1 } → mediaId = MAL ID
→ AniList API: query { Media(idMal: $malId) { id } } → AniList ID
```

### Strategy 3: Title-based AniList search (fallback)

If no tracker bindings exist, we search AniList by title:

```
Aniyomi anime.title → AniList API: query { Page(search: $title) { media { id title } } }
→ Match by title (romaji/english/native) → AniList ID
```

This is less reliable (title mismatches, multiple results) but works for most
popular anime. We should show the user a confirmation dialog if the match
confidence is low.

### Strategy 4: Source URL matching (last resort)

If the anime's extension source is installed in ANIKUTA, we can try to match
by `(sourceId, url)`:

```
Aniyomi anime (sourceId, url) → our extension's anime list
→ find anime with matching URL → if it has an AniList link, use that ID
```

This requires the same extension to be installed and the anime to be in its
catalog.

## Post-restore AniList enrichment

After resolving the AniList ID, ANIKUTA should fetch fresh metadata from
AniList:
- `score`, `totalEpisodes`, `nextAiringEpisode`
- `coverColor` (extracted from cover via Palette)
- `releaseDate`, `genres`, `description` (if missing)
- Episode metadata (titles, air dates, thumbnails)

This can be done lazily (when the user opens the anime's details page) or
eagerly (batch job after restore).
