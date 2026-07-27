# 02 — Protobuf Schema

> Complete protobuf field reference for all Aniyomi backup models.
> Field numbers match `_REFERENCES/ANIYOMI_REFRENCE/.../data/backup/models/Backup*.kt`.

## Root: `Backup` (modern format)

| Field # | Name | Type | Default | Description |
|---|---|---|---|---|
| 1 | backupManga | `List<BackupManga>` | `[]` | Manga library entries |
| 2 | backupCategories | `List<BackupCategory>` | `[]` | Manga categories |
| 101 | backupSources | `List<BackupSource>` | `[]` | Manga sources (name + ID) |
| 104 | backupPreferences | `List<BackupPreference>` | `[]` | App preferences |
| 105 | backupSourcePreferences | `List<BackupSourcePreferences>` | `[]` | Per-source preferences |
| 106 | backupMangaExtensionRepo | `List<BackupExtensionRepos>` | `[]` | Manga extension repos |
| 500 | isLegacy | `Boolean` | `true` | Legacy detection flag |
| 501 | backupAnime | `List<BackupAnime>` | `[]` | **Anime library entries** |
| 502 | backupAnimeCategories | `List<BackupCategory>` | `[]` | Anime categories |
| 503 | backupAnimeSources | `List<BackupAnimeSource>` | `[]` | Anime sources (name + ID) |
| 504 | backupExtensions | `List<BackupExtension>` | `[]` | Extension APK bundles |
| 505 | backupAnimeExtensionRepo | `List<BackupExtensionRepos>` | `[]` | Anime extension repos |
| 506 | backupCustomButton | `List<BackupCustomButtons>` | `[]` | Custom buttons |

## Root: `LegacyBackup` (older format)

Same fields but anime at field **3** (not 501). Anime categories at field **4**
(not 502). Anime sources at field **103** (not 503).

## `BackupAnime`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | source | `Long` | `0` | `AnimeBackup.sourceId` |
| 2 | url | `String` | `""` | `AnimeBackup.url` |
| 3 | title | `String` | `""` | `AnimeBackup.title` |
| 4 | artist | `String?` | `null` | `AnimeBackup.artist` |
| 5 | author | `String?` | `null` | `AnimeBackup.author` |
| 6 | description | `String?` | `null` | `AnimeBackup.description` |
| 7 | genre | `List<String>` | `[]` | `AnimeBackup.genre` (joined with `,`) |
| 8 | status | `Int` | `0` | `AnimeBackup.status` |
| 9 | thumbnailUrl | `String?` | `null` | `AnimeBackup.coverUrl` |
| 13 | dateAdded | `Long` | `0` | `AnimeBackup.dateAdded` |
| 16 | episodes | `List<BackupEpisode>` | `[]` | `BackupEntry.Episodes` |
| 17 | categories | `List<Long>` | `[]` | anime→category links |
| 18 | tracking | `List<BackupAnimeTracking>` | `[]` | `BackupEntry.Tracker` |
| 100 | favorite | `Boolean` | `true` | `AnimeBackup.favorite` |
| 101 | episode_flags | `Int` | `0` | `AnimeBackup.viewerFlags` (sort/filter) |
| 103 | viewer_flags | `Int` | `0` | `AnimeBackup.viewerFlags` |
| 104 | history | `List<BackupAnimeHistory>` | `[]` | `BackupEntry.WatchProgress` |
| 105 | updateStrategy | `Int` | `0` | `AnimeBackup.updateStrategy` |
| 106 | lastModifiedAt | `Long` | `0` | `AnimeBackup.coverLastModified` |
| 107 | favoriteModifiedAt | `Long?` | `null` | (not used) |
| 109 | version | `Long` | `0` | (conflict resolution) |
| 500 | backgroundUrl | `String?` | `null` | (not used) |
| 502 | parentId | `Long?` | `null` | (season grouping — not used) |
| 503 | id | `Long?` | `null` | (season ID — not used) |
| 504 | seasonFlags | `Long` | `0` | (not used) |
| 505 | seasonNumber | `Double` | `-1.0` | (not used) |
| 506 | seasonSourceOrder | `Long` | `0` | (not used) |
| 507 | fetchType | `Int` | `0` | (not used) |

## `BackupEpisode`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | url | `String` | `""` | `EpisodeBackup.url` |
| 2 | name | `String` | `""` | `EpisodeBackup.name` |
| 3 | scanlator | `String?` | `null` | `EpisodeBackup.scanlator` |
| 4 | seen | `Boolean` | `false` | `EpisodeBackup.seen` |
| 5 | bookmark | `Boolean` | `false` | `EpisodeBackup.bookmark` |
| 6 | lastSecondSeen | `Long` | `0` | `EpisodeBackup.lastSecondSeen` |
| 7 | dateFetch | `Long` | `0` | `EpisodeBackup.dateFetch` |
| 8 | dateUpload | `Long` | `0` | `EpisodeBackup.dateUpload` |
| 9 | episodeNumber | `Float` | `0` | `EpisodeBackup.episodeNumber` |
| 10 | sourceOrder | `Long` | `0` | `EpisodeBackup.sourceOrder` |
| 11 | lastModifiedAt | `Long` | `0` | (conflict resolution) |
| 12 | version | `Long` | `0` | (conflict resolution) |
| 16 | totalSeconds | `Long` | `0` | `EpisodeBackup.totalSeconds` |
| 501 | fillermark | `Boolean` | `false` | `EpisodeBackup.fillermark` ("filler" if true) |
| 502 | summary | `String?` | `null` | `EpisodeBackup.summary` |
| 503 | previewUrl | `String?` | `null` | `EpisodeBackup.previewUrl` |

## `BackupCategory`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | name | `String` | `""` | `CategoryBackup.name` |
| 2 | order | `Long` | `0` | `CategoryBackup.order` |
| 3 | id | `Long` | `0` | `CategoryBackup._id` (remapped on restore) |
| 100 | flags | `Long` | `0` | `CategoryBackup.flags` |

## `BackupAnimeTracking`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | syncId | `Int` | `0` | `TrackerTrackItem.trackerId` (1=MAL, 2=AniList) |
| 2 | libraryId | `Long` | `0` | (not used — local DB ID) |
| 3 | mediaIdInt | `Int` | `0` | (deprecated — use mediaId) |
| 4 | trackingUrl | `String` | `""` | `TrackerTrackItem.remoteUrl` |
| 5 | title | `String` | `""` | (anime title for display) |
| 6 | lastEpisodeSeen | `Float` | `0` | `TrackerTrackItem.lastSeen` |
| 7 | totalEpisodes | `Int` | `0` | `TrackerTrackItem.totalEpisodes` |
| 8 | score | `Float` | `0` | `TrackerTrackItem.score` |
| 9 | status | `Int` | `0` | `TrackerTrackItem.status` |
| 10 | startedWatchingDate | `Long` | `0` | (not used) |
| 11 | finishedWatchingDate | `Long` | `0` | (not used) |
| 12 | private | `Boolean` | `false` | (not used) |
| 100 | mediaId | `Long` | `0` | `TrackerTrackItem.remoteId` (AniList media ID) |

### Tracker syncId values

| syncId | Tracker | ANIKUTA has? |
|---|---|---|
| 1 | MyAnimeList (MAL) | ✅ Yes |
| 2 | AniList | ✅ Yes (primary) |
| 3 | Kitsu | ❌ No |
| 4 | Simkl | ❌ No |
| 5 | Bangumi | ❌ No |
| 6 | Shikimori | ❌ No |
| 7 | MangaUpdates | ❌ No (manga-only) |

**For AniList ID resolution:** `syncId == 2 && mediaId != 0` gives us the
AniList anime ID directly. This is the **primary matching strategy**.

## `BackupAnimeHistory`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | url | `String` | `""` | Episode URL (for key matching) |
| 2 | lastRead | `Long` | `0` | `WatchProgressItem.updatedAt` |
| 3 | readDuration | `Long` | `0` | `WatchProgressItem.positionSeconds` |

## `BackupAnimeSource`

| Field # | Name | Type | Default | ANIKUTA maps to |
|---|---|---|---|---|
| 1 | name | `String` | `""` | Source display name (for matching) |
| 2 | sourceId | `Long` | `0` | Aniyomi source ID (needs remapping) |

## `BackupPreference`

| Field # | Name | Type | Default |
|---|---|---|---|
| 1 | key | `String` | `""` |
| 2 | value | `PreferenceValue` | (sealed class) |

### `PreferenceValue` (sealed class)

| Subclass | Proto type | Description |
|---|---|---|
| `IntPreferenceValue` | message with field 1: `Int` | Integer preference |
| `LongPreferenceValue` | message with field 2: `Long` | Long preference |
| `FloatPreferenceValue` | message with field 3: `Float` | Float preference |
| `StringPreferenceValue` | message with field 4: `String` | String preference |
| `BooleanPreferenceValue` | message with field 5: `Boolean` | Boolean preference |
| `StringSetPreferenceValue` | message with field 6: `Set<String>` | String set preference |

**Note:** ANIKUTA does NOT import Aniyomi preferences (different app, different
keys). We skip this entire section by not declaring it in our minimal models.

## `BackupExtension`

| Field # | Name | Type | Default |
|---|---|---|---|
| 1 | pkgName | `String` | `""` |
| 2 | apk | `ByteArray` | `[]` |

**Note:** Extensions are bundled as full APK bytes. ANIKUTA does NOT import
these — we re-match by source name instead.

## `BackupExtensionRepos`

| Field # | Name | Type | Default |
|---|---|---|---|
| 1 | baseUrl | `String` | `""` |
| 2 | name | `String` | `""` |
| 3 | shortName | `String?` | `null` |
| 4 | website | `String` | `""` |
| 5 | signingKeyFingerprint | `String` | `""` |

## `BackupCustomButtons`

| Field # | Name | Type | Default |
|---|---|---|---|
| 1 | name | `String` | `""` |
| 2 | isFavorite | `Boolean` | `false` |
| 3 | sortIndex | `Long` | `0` |
| 4 | content | `String` | `""` |
| 5 | longPressContent | `String` | `""` |
| 6 | onStartup | `String` | `""` |
