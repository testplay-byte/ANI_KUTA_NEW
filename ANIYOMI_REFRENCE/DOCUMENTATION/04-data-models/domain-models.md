# 04-data-models / `domain-models.md` — Domain models

> Field-by-field reference for every domain model declared in the `:domain`
> module. Source root: `../ANIYOMI/domain/src/main/java/`. The module has three
> package roots — `tachiyomi.domain.*` (most manga-side + shared), `mihon.domain.*`
> (Mihon-era additions: extension-repo, upcoming, two filter interactors), and
> `aniyomi.domain.*` (Aniyomi-only extras: `SeasonAnime`, `SeasonDisplayMode`).
> See [`../02-modules/domain.md`](../02-modules/domain.md) for the narrative.

## How to read this doc

Each model is a table of `field | type | meaning`. Fields are listed in
declaration order (which mirrors the SQLDelight `SELECT *` column order).
Computed properties and companion constants follow each table. Models are
grouped as **manga-side**, **anime-side**, **shared**, and **source-API
transport** (technically in `:source-api` but embedded in domain models via
`UpdateStrategy`/`AnimeUpdateStrategy`/`FetchType`).

---

## Manga-side models

### `Manga` — a library entry (manga)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/model/Manga.kt`
Annotations: `@Immutable`, `Serializable`.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `mangas`). `-1L` for an unsaved instance. |
| `source` | `Long` | Source id (MD5-derived; `0L` = local source). |
| `favorite` | `Boolean` | Whether this manga is in the library. |
| `lastUpdate` | `Long` | Epoch millis of last successful library-update fetch. |
| `nextUpdate` | `Long` | Scheduled next-update millis (used by `*FetchInterval`). |
| `fetchInterval` | `Int` | Days between library updates (adaptive). |
| `dateAdded` | `Long` | Epoch millis when added to the library. |
| `viewerFlags` | `Long` | Packed bitmask — reading-mode + orientation + per-manga overrides. |
| `chapterFlags` | `Long` | Packed bitmask of chapter display/sort/filter flags (see below). |
| `coverLastModified` | `Long` | Epoch millis; bumped when the cover image changes (cache-busts Coil). |
| `url` | `String` | Source-relative URL of the manga. |
| `title` | `String` | Display title. |
| `artist` | `String?` | Artist name. |
| `author` | `String?` | Author name. |
| `description` | `String?` | Synopsis. |
| `genre` | `List<String>?` | Genre tags (stored comma-separated in DB; `StringListColumnAdapter`). |
| `status` | `Long` | `SManga.UNKNOWN/ONGOING/COMPLETED/LICENSED/PUBLISHING_FINISHED/CANCELLED/ON_HIATUS` (1..6). |
| `thumbnailUrl` | `String?` | Cover-image URL. |
| `updateStrategy` | `UpdateStrategy` | `ALWAYS_UPDATE` or `ONLY_FETCH_ONCE` (from `:source-api`). |
| `initialized` | `Boolean` | Whether `getMangaDetails` has been called on this row. |
| `lastModifiedAt` | `Long` | Maintained by trigger — any row modification time (sync support). |
| `favoriteModifiedAt` | `Long?` | Maintained by trigger — last time `favorite` flipped (sync support). |
| `version` | `Long` | Maintained by trigger — increment on key-field changes (sync support). |

**Computed properties (all read from `chapterFlags`):**

| Property | Type | Source bits |
|---|---|---|
| `expectedNextUpdate` | `Instant?` | `nextUpdate` if `status != COMPLETED`, else null. |
| `sorting` | `Long` | `chapterFlags AND CHAPTER_SORTING_MASK` (source/number/upload/alphabet). |
| `displayMode` | `Long` | `chapterFlags AND CHAPTER_DISPLAY_MASK` (name or number). |
| `unreadFilterRaw` / `unreadFilter` | `Long` / `TriState` | bits 0x2 (show unread) / 0x4 (show read). |
| `downloadedFilterRaw` | `Long` | bits 0x8 / 0x10. |
| `bookmarkedFilterRaw` / `bookmarkedFilter` | `Long` / `TriState` | bits 0x20 / 0x40. |
| `sortDescending()` | `Boolean` | bit 0x1 (`CHAPTER_SORT_DESC` = 0, `CHAPTER_SORT_ASC` = 1). |

**Companion constants** (flag bitmasks): `SHOW_ALL`, `CHAPTER_SORT_DESC/ASC/_MASK`,
`CHAPTER_SHOW_UNREAD/READ/_MASK`, `CHAPTER_SHOW_DOWNLOADED/NOT_DOWNLOADED/_MASK`,
`CHAPTER_SHOW_BOOKMARKED/NOT_BOOKMARKED/_MASK`, `CHAPTER_SORTING_SOURCE/NUMBER/
UPLOAD_DATE/ALPHABET/_MASK`, `CHAPTER_DISPLAY_NAME/NUMBER/_MASK`.

### `MangaUpdate` — patch model for `Manga`

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/model/MangaUpdate.kt`

Same fields as `Manga` (minus `lastModifiedAt`/`favoriteModifiedAt`, which are
trigger-maintained), but **all nullable except `id`**. The `:data` `update`
query uses `coalesce(:field, field)` so a null field means "leave unchanged".
`Manga.toMangaUpdate()` produces a fully-populated patch from an instance.

### `MangaCover` — Coil cover-image identity

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/manga/model/MangaCover.kt`
Implements `EntryCover` (the shared marker interface).

| Field | Type | Meaning |
|---|---|---|
| `mangaId` | `Long` | Manga id. |
| `sourceId` | `Long` | Source id (used to build the image-request domain). |
| `isMangaFavorite` | `Boolean` | Library state (affects cover badge). |
| `url` | `String?` | `thumbnailUrl`. |
| `lastModified` | `Long` | `coverLastModified` — cache-bust key. |

`Manga.asMangaCover()` is the convenience extractor. Used as the
`coverData` field of `LibraryManga`, `MangaHistoryWithRelations`, and
`MangaUpdatesWithRelations` so the Coil pipeline can fetch covers without
pulling the entire `Manga` row.

### `Chapter` — a chapter of a manga

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/chapter/model/Chapter.kt`

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `chapters`). |
| `mangaId` | `Long` | FK → `mangas._id` (cascade delete). |
| `read` | `Boolean` | Read flag. |
| `bookmark` | `Boolean` | User bookmark. |
| `lastPageRead` | `Long` | Last page read (0-indexed). |
| `dateFetch` | `Long` | When this chapter was last fetched from the source. |
| `sourceOrder` | `Long` | Order as returned by the source (for stable sort). |
| `url` | `String` | Source-relative chapter URL. |
| `name` | `String` | Display name. |
| `dateUpload` | `Long` | Upload date from the source (epoch millis). |
| `chapterNumber` | `Double` | Parsed chapter number (`-1.0` if unrecognised). |
| `scanlator` | `String?` | Scanlator group name. |
| `lastModifiedAt` | `Long` | Trigger-maintained modification time. |
| `version` | `Long` | Trigger-maintained sync version. |

Computed: `isRecognizedNumber` (`chapterNumber >= 0`). Method `copyFrom(other)`
merges the source-fetched fields (`name`, `url`, `dateUpload`, `chapterNumber`,
`scanlator`) onto the local instance (used by chapter refresh).

### `ChapterUpdate` — patch model

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/chapter/model/ChapterUpdate.kt`

Same fields as `Chapter` minus `lastModifiedAt` (trigger-maintained), all
nullable except `id`. `Chapter.toChapterUpdate()` builds a full patch.

### `MangaTrack` — tracker binding for a manga

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/track/manga/model/MangaTrack.kt`
Implements `Serializable`. One row per `(mangaId, trackerId)` pair.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `manga_sync`). |
| `mangaId` | `Long` | FK → `mangas._id`. |
| `trackerId` | `Long` | Tracker id (MAL=1, AniList=2, Shikimori=3, Bangumi=4, …). |
| `remoteId` | `Long` | Id on the tracker's side. |
| `libraryId` | `Long?` | Unused legacy column (always null in modern code). |
| `title` | `String` | Title as known on the tracker. |
| `lastChapterRead` | `Double` | Last read chapter number. |
| `totalChapters` | `Long` | Total chapter count on the tracker. |
| `status` | `Long` | Tracker status (reading/plan-to-read/completed/…). |
| `score` | `Double` | User score (0..10-ish, tracker-dependent). |
| `remoteUrl` | `String` | Tracker-side URL. |
| `startDate` | `Long` | Tracking start date (epoch). |
| `finishDate` | `Long` | Tracking finish date (epoch). |
| `private` | `Boolean` | Hide this progress on the tracker (added in migration 32). |

### Manga history — `MangaHistory`, `MangaHistoryUpdate`, `MangaHistoryWithRelations`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/history/manga/model/*.kt`

**`MangaHistory`** (one row per chapter — `chapter_id` is `UNIQUE`):

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `history`). |
| `chapterId` | `Long` | FK → `chapters._id`. Unique. |
| `readAt` | `Date?` | Last-read timestamp (nullable). |
| `readDuration` | `Long` | Accumulated read time (millis) — `upsert` adds to it. |

**`MangaHistoryUpdate`** — patch sent to `upsert`:

| Field | Type | Meaning |
|---|---|---|
| `chapterId` | `Long` | Target chapter. |
| `readAt` | `Date` | New `last_read` value. |
| `sessionReadDuration` | `Long` | Milliseconds read this session (added to `time_read`). |

**`MangaHistoryWithRelations`** — denormalised row joining `history → chapters → mangas`,
used by the "Recently read" screen:

| Field | Type | Meaning |
|---|---|---|
| `id`, `chapterId`, `readAt`, `readDuration` | — | from `history`. |
| `mangaId` | `Long` | from `chapters`. |
| `title` | `String` | from `mangas`. |
| `chapterNumber` | `Double` | from `chapters`. |
| `coverData` | `MangaCover` | assembled for Coil (mangaId/sourceId/favorite/url/lastModified). |

### `MangaUpdatesWithRelations` — Updates-tab row (manga)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/updates/manga/model/MangaUpdatesWithRelations.kt`

| Field | Type | Meaning |
|---|---|---|
| `mangaId` | `Long` | |
| `mangaTitle` | `String` | |
| `chapterId` | `Long` | |
| `chapterName` | `String` | |
| `scanlator` | `String?` | |
| `read` | `Boolean` | |
| `bookmark` | `Boolean` | |
| `lastPageRead` | `Long` | |
| `sourceId` | `Long` | |
| `dateFetch` | `Long` | when the chapter was fetched. |
| `coverData` | `MangaCover` | |

### `LibraryManga` — library row with chapter aggregates (manga)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/manga/LibraryManga.kt`

Wraps a full `Manga` plus the aggregated counts that the `libraryView` SQL view
computes (total/read/bookmark/latest upload/last read/fetched).

| Field | Type | Meaning |
|---|---|---|
| `manga` | `Manga` | Full manga row. |
| `category` | `Long` | First category id bound to this manga (0 = "Default"). |
| `totalChapters` | `Long` | Count of non-excluded chapters. |
| `readCount` | `Long` | Count of `read = 1` chapters. |
| `bookmarkCount` | `Long` | Count of `bookmark = 1` chapters. |
| `latestUpload` | `Long` | Max `date_upload`. |
| `chapterFetchedAt` | `Long` | Max `date_fetch`. |
| `lastRead` | `Long` | Max `history.last_read`. |

Computed: `id` (= `manga.id`), `unreadCount` (= `totalChapters - readCount`),
`hasBookmarks`, `hasStarted` (readCount > 0).

### `Source` / `StubMangaSource` / `MangaSourceWithCount` / `Pin`/`Pins`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/manga/model/*.kt`

**`Source`** — domain-side view of an installed or stub manga source:

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Source id (`0L` = local). |
| `lang` | `String` | ISO language code. |
| `name` | `String` | Display name. |
| `supportsLatest` | `Boolean` | Source exposes a "latest" tab. |
| `isStub` | `Boolean` | True if the extension is not installed (registry-only). |
| `pin` | `Pins` | Pinned / unpinned (pinned appear first in browse). |
| `isUsedLast` | `Boolean` | "Last used" flag for ordering. |
| `isExcludedFromDataSaver` | `Boolean` | Tachiyomi-SY: skip data-saver for this source. |

Computed: `visualName` ("Name (LANG)" or "Name"), `key()` (id string, with
`-lastused` suffix if `isUsedLast`).

**`StubMangaSource`** — implements `eu.kanade.tachiyomi.source.MangaSource`;
every method throws `SourceNotInstalledException`. Exists so library manga whose
extension was uninstalled can still be displayed without crashing.

**`MangaSourceWithCount`** — `(Source, Long count)` wrapper for the browse tab
"library counts per source" display.

**`Pin`** (sealed) + **`Pins`** (data class) — a 2-bit packed enum:
`Pin.Unpinned (0b00)`, `Pin.Pinned (0b01)`, `Pin.Actual (0b10)`. `Pins.unpinned`
and `Pins.pinned` are the two common values. Bitwise operators `+` and `-` are
provided. The anime side declares an identical `Pin`/`Pins` in its own package.

---

## Anime-side models

### `Anime` — a library entry (anime)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/anime/model/Anime.kt`
Annotations: `@Immutable`, `Serializable`.

Mirrors `Manga` plus **anime-only fields** (`backgroundLastModified`,
`backgroundUrl`, `fetchType`, `parentId`, `seasonFlags`, `seasonNumber`,
`seasonSourceOrder`) and an anime `viewerFlags` that packs a *skip-intro length*,
a *next-airing episode number*, a *next-airing time*, and an *intro-disable* bit.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `animes`). |
| `source` | `Long` | Source id (`0L` = local anime). |
| `favorite` | `Boolean` | Library flag. |
| `lastUpdate` | `Long` | Last library-update fetch. |
| `nextUpdate` | `Long` | Scheduled next update. |
| `fetchInterval` | `Int` | Days between updates. |
| `dateAdded` | `Long` | When added to library. |
| `viewerFlags` | `Long` | Packed bitmask — see computed properties below. |
| `episodeFlags` | `Long` | Packed bitmask of episode display/sort/filter flags. |
| `coverLastModified` | `Long` | Cover cache-bust. |
| `backgroundLastModified` | `Long` | **Anime-only.** Background-image cache-bust. |
| `url` | `String` | Source-relative URL. |
| `title` | `String` | Display title. |
| `artist` | `String?` | |
| `author` | `String?` | |
| `description` | `String?` | |
| `genre` | `List<String>?` | |
| `status` | `Long` | `SAnime.UNKNOWN..ON_HIATUS` (1..6). |
| `thumbnailUrl` | `String?` | Cover URL. |
| `backgroundUrl` | `String?` | **Anime-only.** Background image URL. |
| `updateStrategy` | `AnimeUpdateStrategy` | `ALWAYS_UPDATE` or `ONLY_FETCH_ONCE`. |
| `initialized` | `Boolean` | Details fetched from source? |
| `lastModifiedAt` | `Long` | Trigger-maintained. |
| `favoriteModifiedAt` | `Long?` | Trigger-maintained. |
| `version` | `Long` | Trigger-maintained. |
| `fetchType` | `FetchType` | **Anime-only.** `Episodes` or `Seasons` (ext-lib 16). |
| `parentId` | `Long?` | **Anime-only.** Set when this row is a *season child* of a parent anime. |
| `seasonFlags` | `Long` | **Anime-only.** Packed bitmask of season display/sort/filter flags. |
| `seasonNumber` | `Double` | **Anime-only.** Season number under the parent (`-1.0` if root). |
| `seasonSourceOrder` | `Long` | **Anime-only.** Order within the parent. |

**Computed properties — episode flags** (mirror manga's chapter flags but
with the addition of `fillermark` and the previews/summaries toggles):

| Property | Bits | Notes |
|---|---|---|
| `sorting` | `EPISODE_SORTING_MASK` | source/number/upload/alphabet. |
| `displayMode` | `EPISODE_DISPLAY_MASK` | name or number. |
| `unseenFilterRaw` / `unseenFilter` | bits 0x2/0x4 | `TriState`. |
| `downloadedFilterRaw` | bits 0x8/0x10 | |
| `bookmarkedFilterRaw` / `bookmarkedFilter` | bits 0x20/0x40 | `TriState`. |
| `fillermarkedFilterRaw` / `fillermarkedFilter` | bits 0x80/0x100 | `TriState` — anime-only. |
| `showPreviewsRaw` / `showPreviews()` | bit 0x800 | show episode preview thumbnails. |
| `showSummariesRaw` / `showSummaries()` | bit 0x1000 | show episode text summaries. |
| `sortDescending()` | bit 0x1 | |

**Computed properties — `viewerFlags`** packs airing metadata:

| Property | Bits | Meaning |
|---|---|---|
| `skipIntroLength` | `ANIME_INTRO_MASK` (low 8 bits) | Skip-intro seconds (0..255). |
| `skipIntroDisable` | `ANIME_INTRO_DISABLE_MASK` (bit 56) | Skip-intro disabled. |
| `nextEpisodeToAir` | `ANIME_AIRING_EPISODE_MASK` (bits 8..19) | Next airing episode number. |
| `nextEpisodeAiringAt` | `ANIME_AIRING_TIME_MASK` (bits 24..55) | Next airing epoch seconds. |

**Computed properties — season flags** (only meaningful when `parentId != null`):
`seasonDownloadedFilterRaw` (bits 0x2/0x4), `seasonUnseenFilterRaw`/
`seasonUnseenFilter` (bits 0x8/0x10), `seasonStartedFilterRaw`/
`seasonStartedFilter` (bits 0x20/0x40), `seasonCompletedFilterRaw`/
`seasonCompletedFilter` (bits 0x80/0x100), `seasonBookmarkedFilterRaw`/
`seasonBookmarkedFilter` (bits 0x200/0x400), `seasonFillermarkedFilterRaw`/
`seasonFillermarkedFilter` (bits 0x800/0x1000), `seasonSorting`
(`SEASON_SORT_MASK`: source/season/upload/alphabet/count/last-seen/fetched),
`seasonSortDescending()` (bit 0x1), `seasonDisplayGridMode` (bits 16..17,
maps to `SeasonDisplayMode`), `seasonDisplayGridSize` (bits 18..21), and
five boolean overlay toggles `seasonDownloadedOverlay` / `seasonUnseenOverlay`
/ `seasonLocalOverlay` / `seasonLangOverlay` / `seasonContinueOverlay`
(bits 22..26), plus `seasonDisplayMode` (bit 27, source vs number).

The full bitmask constant table lives in the `Anime` companion object
(`EPISODE_*`, `SEASON_*`, `ANIME_*` constants).

### `AnimeUpdate` — patch model

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/anime/model/AnimeUpdate.kt`

Same fields as `Anime` minus `lastModifiedAt`/`favoriteModifiedAt`, all
nullable except `id`. `Anime.toAnimeUpdate()` produces a full patch.

### `AnimeCover` — Coil cover identity (anime)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/anime/model/AnimeCover.kt`
Implements `EntryCover`. Identical shape to `MangaCover` with `animeId` /
`isAnimeFavorite` instead.

### `Episode` — an episode of an anime

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/model/Episode.kt`

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `episodes`). |
| `animeId` | `Long` | FK → `animes._id` (cascade). |
| `seen` | `Boolean` | **Anime-only.** "Seen" (≈ `read` on the chapter side). |
| `bookmark` | `Boolean` | |
| `fillermark` | `Boolean` | **Anime-only.** Filler flag. |
| `lastSecondSeen` | `Long` | **Anime-only.** Resume position in seconds. |
| `totalSeconds` | `Long` | **Anime-only.** Episode length in seconds. |
| `dateFetch` | `Long` | |
| `sourceOrder` | `Long` | |
| `url` | `String` | |
| `name` | `String` | |
| `dateUpload` | `Long` | |
| `episodeNumber` | `Double` | |
| `scanlator` | `String?` | Subgroup / uploader. |
| `summary` | `String?` | **Anime-only.** Episode text summary. |
| `previewUrl` | `String?` | **Anime-only.** Episode preview thumbnail URL. |
| `lastModifiedAt` | `Long` | Trigger-maintained. |
| `version` | `Long` | Trigger-maintained. |

`copyFrom(other)` merges source-fetched fields including `fillermark`,
`summary`, `previewUrl`.

### `EpisodeUpdate` — patch model

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/model/EpisodeUpdate.kt`

Same fields minus `lastModifiedAt`, all nullable except `id`.

### `AnimeTrack` — tracker binding for an anime

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/track/anime/model/AnimeTrack.kt`

Identical shape to `MangaTrack` with `animeId` / `lastEpisodeSeen` /
`totalEpisodes` instead. Backed by the `anime_sync` table.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `anime_sync`). |
| `animeId` | `Long` | FK → `animes._id`. |
| `trackerId` | `Long` | Tracker id (MAL=1, AniList=2, …). |
| `remoteId` | `Long` | |
| `libraryId` | `Long?` | |
| `title` | `String` | |
| `lastEpisodeSeen` | `Double` | |
| `totalEpisodes` | `Long` | |
| `status` | `Long` | |
| `score` | `Double` | |
| `remoteUrl` | `String` | |
| `startDate` | `Long` | |
| `finishDate` | `Long` | |
| `private` | `Boolean` | |

### Anime history — `AnimeHistory`, `AnimeHistoryUpdate`, `AnimeHistoryWithRelations`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/history/anime/model/*.kt`

**`AnimeHistory`** (one row per episode):

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `animehistory`). |
| `episodeId` | `Long` | FK → `episodes._id`. Unique. |
| `seenAt` | `Date?` | Last-seen timestamp. |

> Note: unlike `MangaHistory`, `AnimeHistory` does **not** track cumulative
> watch duration — only the last-seen timestamp. The manga side's `time_read`
> column has no anime equivalent.

**`AnimeHistoryUpdate`**:

| Field | Type | Meaning |
|---|---|---|
| `episodeId` | `Long` | |
| `seenAt` | `Date` | New `last_seen`. |

**`AnimeHistoryWithRelations`** — denormalised row for "Recently watched":

| Field | Type | Meaning |
|---|---|---|
| `id`, `episodeId`, `seenAt` | — | from `animehistory`. |
| `animeId` | `Long` | from `episodes`. |
| `title` | `String` | from `animes`. |
| `episodeNumber` | `Double` | from `episodes`. |
| `coverData` | `AnimeCover` | |

### `AnimeUpdatesWithRelations` — Updates-tab row (anime)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/updates/anime/model/AnimeUpdatesWithRelations.kt`

| Field | Type | Meaning |
|---|---|---|
| `animeId` | `Long` | |
| `animeTitle` | `String` | |
| `episodeId` | `Long` | |
| `episodeName` | `String` | |
| `scanlator` | `String?` | |
| `seen` | `Boolean` | |
| `bookmark` | `Boolean` | |
| `fillermark` | `Boolean` | **Anime-only.** |
| `lastSecondSeen` | `Long` | |
| `totalSeconds` | `Long` | |
| `sourceId` | `Long` | |
| `dateFetch` | `Long` | |
| `coverData` | `AnimeCover` | |

### `LibraryAnime` — library row with episode aggregates

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/anime/LibraryAnime.kt`

Wraps a full `Anime` plus the aggregated counts from `animelibView` (which,
recall, branches on `fetch_type` to compute counts from episodes or seasons).

| Field | Type | Meaning |
|---|---|---|
| `anime` | `Anime` | Full anime row. |
| `category` | `Long` | First bound category id (0 = "Default"). |
| `totalCount` | `Long` | `total` from `episodestatsView`, or `child_count` from `animeseasonstatsView`. |
| `seenCount` | `Long` | `seenCount` or `fully_seen_seasons`. |
| `bookmarkCount` | `Long` | |
| `fillermarkCount` | `Long` | **Anime-only.** |
| `latestUpload` | `Long` | |
| `episodeFetchedAt` | `Long` | |
| `lastSeen` | `Long` | |

Computed: `id`, `unseenCount`, `hasBookmarks`, `hasStarted`.

### `SeasonAnime` — a season row with aggregated counts

File: `../ANIYOMI/domain/src/main/java/aniyomi/domain/anime/SeasonAnime.kt`

A `SeasonAnime` is the row returned by `AnimeRepository.getAnimeSeasonsById(parentId)`.
It wraps a child `Anime` plus the same aggregated counts as `LibraryAnime`.

| Field | Type | Meaning |
|---|---|---|
| `anime` | `Anime` | The child anime row (`parentId != null`). |
| `totalCount` | `Long` | Episode count (or season-child count if `fetch_type=Seasons`). |
| `seenCount` | `Long` | |
| `bookmarkCount` | `Long` | |
| `fillermarkCount` | `Long` | |
| `latestUpload` | `Long` | |
| `fetchedAt` | `Long` | |
| `lastSeen` | `Long` | |

Computed: `id`, `seen` (= `totalCount == seenCount`), `unseenCount`,
`hasStarted`, `hasBookmarks`, `hasFillermarks`. `toLibraryAnime()` adapts
the row to a `LibraryAnime` (with `category = -1L`) for uniform UI rendering.

### `AnimeSource` / `StubAnimeSource` / `AnimeSourceWithIds` / `DeletableAnime` / `Pin`/`Pins`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/anime/model/*.kt`

**`AnimeSource`** — anime twin of `Source`. Lacks `isExcludedFromDataSaver`
(no anime data-saver). Otherwise identical (`id`, `lang`, `name`,
`supportsLatest`, `isStub`, `pin`, `isUsedLast`, `visualName`, `key()`).

**`StubAnimeSource`** — implements `eu.kanade.tachiyomi.animesource.AnimeSource`;
every method throws `AnimeSourceNotInstalledException`. Notably also throws on
`getSeasonList` — the seasons API is anime-only.

**`AnimeSourceWithIds`** — `(AnimeSource, List<Long> ids, List<Long> orphaned)`
wrapper for the browse tab. `count` is `ids.size`.

**`DeletableAnime`** — anime-only. Returned by `getDeletableParentAnime()` —
parent animes (`parent_id IS NULL`) that are not favorites, candidates for
cleanup.

| Field | Type | Meaning |
|---|---|---|
| `animeId` | `Long` | |
| `sourceId` | `Long` | |
| `fetchType` | `FetchType` | |

**`Pin`/`Pins`** — anime-side duplicate of the manga `Pin`/`Pins`. Identical
2-bit packed enum.

---

## Shared models

### `Category` / `CategoryUpdate`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/category/model/Category.kt`, `CategoryUpdate.kt`

A `Category` is shared by manga and anime — **the same domain class backs both
the manga `categories` table and the anime `categories` table**. They are two
physically separate SQLite tables with identical schema.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key. `UNCATEGORIZED_ID = 0L` is the system "Default" category. |
| `name` | `String` | Category name. |
| `order` | `Long` | Sort order (the DB column is `sort`). |
| `flags` | `Long` | Packed bitmask of per-category display + sort flags (see `MangaLibrarySort` / `AnimeLibrarySort`). |
| `hidden` | `Boolean` | Hidden from the category tab. |

Computed: `isSystemCategory` (`id == UNCATEGORIZED_ID`). The system category
cannot be deleted (the `system_category_delete_trigger` blocks it).

`CategoryUpdate` — patch model with `id` required, others nullable.

### `EntryCover` (marker interface)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/EntryCover.kt`

Empty marker interface implemented by both `MangaCover` and `AnimeCover` so the
Coil image pipeline can treat them polymorphically.

### `TriState` enum + `applyFilter` helper

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/entries/TriState.kt`
(Actual enum lives in `:core:common`: `tachiyomi.core.common.preference.TriState`.)

A three-state filter enum (`DISABLED`, `ENABLED_IS`, `ENABLED_NOT`) used for
manga/anime/season filter flags. `applyFilter(filter, predicate)` returns
`true` if the filter is `DISABLED`, the predicate's value if `ENABLED_IS`,
or the negation if `ENABLED_NOT`.

### `LibraryDisplayMode` (sealed interface)

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/model/LibraryDisplayMode.kt`

Four display modes for the library grid: `CompactGrid` (default),
`ComfortableGrid`, `List`, `CoverOnlyGrid`. Has a `Serializer` object with
`serialize()`/`deserialize()` for preference storage.

### `Flag` / `Mask` / `FlagWithMask` interfaces

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/model/Flag.kt`

The tiny bit-packing helpers used by `MangaLibrarySort` and `AnimeLibrarySort`
to encode `Type` + `Direction` into a single `Long` flag with a mask. Provides
`Long.contains(Flag)` and `Long + Flag` operators.

### `MangaLibrarySort` / `AnimeLibrarySort`

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/{manga,anime}/model/*LibrarySortMode.kt`

Each is a `data class (Type, Direction) : FlagWithMask` representing the sort
mode of one category.

**Manga `Type` values** (`mask = 0b00111100`): `Alphabetical`, `LastRead`,
`LastUpdate`, `UnreadCount`, `TotalChapters`, `LatestChapter`,
`ChapterFetchDate`, `DateAdded`, `TrackerMean`, `Random`. Default = `Alphabetical / Ascending`.

**Anime `Type` values**: same set **plus** `LastSeen` (instead of `LastRead`),
`UnseenCount` (instead of `UnreadCount`), `TotalEpisodes`, `LatestEpisode`,
`EpisodeFetchDate`, and **`AiringTime`** (anime-only). Default = `Alphabetical / Ascending`.

`Direction` (`mask = 0b01000000`): `Ascending` or `Descending`.

Both have `Serializer.serialize()`/`deserialize()` returning strings like
`"ALPHABETICAL,ASCENDING"` for preference storage. Extension property
`Category?.sort: MangaLibrarySort` / `: AnimeLibrarySort` reads the flag
off a `Category.flags` field.

### `Release` — app self-update info

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/release/model/Release.kt`

| Field | Type | Meaning |
|---|---|---|
| `version` | `String` | New version string. |
| `info` | `String` | Release notes body. |
| `releaseLink` | `String` | GitHub release URL. |
| `downloadLink` | `String` | APK asset URL. |

Produced by `ReleaseServiceImpl` (in `:data`) hitting GitHub's releases API;
consumed by `GetApplicationRelease` (in `:domain`) with 3-day throttling.

### `ExtensionRepo` — extension-repo registry entry

File: `../ANIYOMI/domain/src/main/java/mihon/domain/extensionrepo/model/ExtensionRepo.kt`

Stored in `extension_repos` (which exists in both schemas). Each row is an
HTTP endpoint serving `index.min.json` + `repo.json`.

| Field | Type | Meaning |
|---|---|---|
| `baseUrl` | `String` | Repo base URL (PK). |
| `name` | `String` | Display name (from `repo.json`). |
| `shortName` | `String?` | Optional short name. |
| `website` | `String` | Repo website. |
| `signingKeyFingerprint` | `String` | Repo's signing key fingerprint (UNIQUE). |

### `CustomButton` / `CustomButtonUpdate` — MPV Lua buttons (anime-only)

Files: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/custombuttons/model/*.kt`

User-defined buttons shown in the anime player. Each button carries **three
Lua scripts**: one for tap, one for long-press, and one for player startup.

| Field | Type | Meaning |
|---|---|---|
| `id` | `Long` | Primary key (`_id` in `custom_buttons`). |
| `name` | `String` | Button label. |
| `isFavorite` | `Boolean` | Pinned to the controls bar. |
| `sortIndex` | `Long` | Order in the button list. |
| `content` | `String` | Lua source for tap. |
| `longPressContent` | `String` | Lua source for long-press. |
| `onStartup` | `String` | Lua source executed on player startup (e.g. `mp.observe_property`). |

Methods `getButtonContent(primaryId)`, `getButtonLongPressContent(primaryId)`,
`getButtonOnStartup(primaryId)` substitute `$id` and `$isPrimary` into the Lua
source so each button knows its own id and whether it's the primary button.

`CustomButtonUpdate` — patch model with `id` required, others nullable.

The table seeds a default `+85 s` skip-intro button on first creation (see
[`database-schema.md`](database-schema.md#custom_buttons-sq-anime-only)).

### `SeasonDisplayMode` (sealed interface)

File: `../ANIYOMI/domain/src/main/java/aniyomi/domain/anime/SeasonDisplayMode.kt`

Anime-only. Four grid-display modes for the seasons grid: `CompactGrid`,
`ComfortableGrid`, `CoverOnlyGrid`, `List`. Has `toLong()`/`fromLong()` for
packing into the `seasonFlags` bitmask (bits 16..17).

### Exceptions

| Class | File | Meaning |
|---|---|---|
| `NoChaptersException` | `items/chapter/model/NoChaptersException.kt` | Thrown when a manga has no chapters after fetch. |
| `NoEpisodesException` | `items/episode/model/NoEpisodesException.kt` | Thrown when an anime has no episodes. |
| `NoSeasonsException` | `entries/anime/model/NoSeasonsException.kt` | Thrown when an anime has no seasons. |
| `SourceNotInstalledException` | `source/manga/model/StubMangaSource.kt` | Thrown by `StubMangaSource` methods. |
| `AnimeSourceNotInstalledException` | `source/anime/model/StubAnimeSource.kt` | Thrown by `StubAnimeSource` methods. |
| `SaveExtensionRepoException` | `mihon/domain/extensionrepo/exception/SaveExtensionRepoException.kt` | Wraps SQLite conflicts in extension-repo writes. |
| `SaveCustomButtonException` | `custombuttons/exception/SaveCustomButtonException.kt` | Wraps SQLite conflicts in custom-button writes. |

---

## Source-API transport models

These live in `:source-api` (package `eu.kanade.tachiyomi.source.model` and
`eu.kanade.tachiyomi.animesource.model`) but are embedded in the domain models
via `UpdateStrategy`/`AnimeUpdateStrategy`/`FetchType`. They are the **wire
format** between extensions and the app — every source-returned manga/anime/
chapter/episode is one of these before being mapped to a domain model.

See [`../02-modules/source-api.md`](../02-modules/source-api.md) for the full
source-api contract. The transport-model field reference is below.

### `SManga` — manga as returned by a source

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt`
Interface; impl `SMangaImpl`.

| Field | Type | Meaning |
|---|---|---|
| `url` | `String` | Source-relative URL. |
| `title` | `String` | Display title. |
| `artist` | `String?` | |
| `author` | `String?` | |
| `description` | `String?` | |
| `genre` | `String?` | Comma-separated genre string (`getGenres()` splits it). |
| `status` | `Int` | `UNKNOWN/ONGOING/COMPLETED/LICENSED/PUBLISHING_FINISHED/CANCELLED/ON_HIATUS` (0..6). |
| `thumbnail_url` | `String?` | Cover URL. |
| `update_strategy` | `UpdateStrategy` | `ALWAYS_UPDATE` / `ONLY_FETCH_ONCE`. |
| `initialized` | `Boolean` | Has details been fetched? |

### `SAnime` — anime as returned by a source

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt`
Interface; impl `SAnimeImpl`. **Separate class from `SManga`** despite the
parallel name. Adds anime-only fields.

| Field | Type | Meaning |
|---|---|---|
| `url`, `title`, `artist`, `author`, `description`, `genre`, `status`, `thumbnail_url`, `update_strategy`, `initialized` | — | Same as `SManga`. |
| `background_url` | `String?` | **Anime-only.** Background image URL. |
| `fetch_type` | `FetchType` | **Anime-only.** `Episodes` or `Seasons`. |
| `season_number` | `Double` | **Anime-only.** Season number under parent. |

### `SChapter` — chapter as returned by a source

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SChapter.kt`

| Field | Type | Meaning |
|---|---|---|
| `url` | `String` | |
| `name` | `String` | |
| `date_upload` | `Long` | |
| `chapter_number` | `Float` | Parsed by `ChapterRecognition`. |
| `scanlator` | `String?` | |

`copyFrom(other)` merges fields from another `SChapter`.

### `SEpisode` — episode as returned by a source

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt`

Adds anime-only fields vs `SChapter`.

| Field | Type | Meaning |
|---|---|---|
| `url`, `name`, `date_upload`, `episode_number`, `scanlator` | — | Same as `SChapter`. |
| `fillermark` | `Boolean` | **Anime-only.** Filler flag. |
| `summary` | `String?` | **Anime-only.** Episode text summary. |
| `preview_url` | `String?` | **Anime-only.** Episode preview thumbnail. |

### `Page` — a single manga page (the reader sub-unit)

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt`
Annotations: `@Serializable`. Open class.

| Field | Type | Meaning |
|---|---|---|
| `index` | `Int` | 0-indexed page position. `number` = `index + 1`. |
| `url` | `String` | Page-list URL (the URL the source returns in `getPageList`). |
| `imageUrl` | `String?` | Resolved image URL (set after the source's `fetchImageUrl`). |
| `uri` | `Uri?` | Deprecated but kept for extension compatibility. |

Reactive state via private `MutableStateFlow`s exposed as `StateFlow`s:
- `statusFlow` / `status: State` — `QUEUE`, `LOAD_PAGE`, `DOWNLOAD_IMAGE`,
  `READY`, `ERROR`.
- `progressFlow` / `progress: Int` — 0..100 download progress (`-1` if
  unknown content-length).

`Page` implements `ProgressListener` (`update(bytesRead, contentLength, done)`)
so OkHttp's response body can drive the progress flow.

### `Video` — a single anime video (the player sub-unit)

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt`

A rich data class carrying not just a URL but also subtitles, audio tracks,
chapter timestamps, MPV/FFmpeg args, and a reactive status field.

| Field | Type | Meaning |
|---|---|---|
| `videoUrl` | `String` | Direct playable URL (or `"null"` if absent). |
| `videoTitle` | `String` | Display label (was `quality` in older ext-libs). |
| `resolution` | `Int?` | Vertical resolution (e.g. 720, 1080). |
| `bitrate` | `Int?` | |
| `headers` | `Headers?` | OkHttp headers (NOT serializable — see `SerializableVideo`). |
| `preferred` | `Boolean` | Hint to the player to pick this one first. |
| `subtitleTracks` | `List<Track>` | Subtitle tracks (`Track(url, lang)`). |
| `audioTracks` | `List<Track>` | Audio tracks. |
| `timestamps` | `List<TimeStamp>` | Chapter timestamps for the seek bar / skip button. |
| `mpvArgs` | `List<Pair<String,String>>` | Extra `--mpv-arg` overrides. |
| `ffmpegStreamArgs` | `List<Pair<String,String>>` | FFmpeg stream-filter args. |
| `ffmpegVideoArgs` | `List<Pair<String,String>>` | FFmpeg video-filter args. |
| `internalData` | `String` | Opaque per-source data. |
| `initialized` | `Boolean` | Has the URL been resolved? |
| `status` | `State` (volatile) | `QUEUE`, `LOAD_VIDEO`, `READY`, `ERROR`. |

**Companion types in the same file:**

- `Track(url: String, lang: String)` — subtitle/audio track descriptor.
  `@Serializable`.
- `ChapterType` enum — `Opening`, `Ending`, `Recap`, `MixedOp`, `Other`.
  `@Serializable`.
- `TimeStamp(start, end, name, type=Other)` — chapter marker.
  `@Serializable`.
- `SerializableVideo` — `@Serializable` mirror of `Video` with `Headers`
  flattened to `List<Pair<String,String>>`. The companion's
  `List<Video>.serialize()` / `String.toVideoList()` round-trip the two.

### `Hoster` / `SerializableHoster` — lazy multi-hoster container

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Hoster.kt`

Ext-lib 16. A `Hoster` represents one of possibly multiple video hosters for
an episode (e.g. a primary stream + a backup stream). Lazy hosters don't
populate `videoList` until the user taps them.

| Field | Type | Meaning |
|---|---|---|
| `hosterUrl` | `String` | Hoster identifier URL. |
| `hosterName` | `String` | Display name. |
| `videoList` | `List<Video>?` | Resolved videos (null if lazy). |
| `internalData` | `String` | Opaque data. |
| `lazy` | `Boolean` | True → videos not yet fetched. |
| `status` | `State` (volatile) | `IDLE`, `LOADING`, `READY`, `ERROR`. |

`Hoster.NO_HOSTER_LIST = "no_hoster_list"` and the extension function
`List<Video>.toHosterList()` wrap a flat video list as a single pseudo-hoster
for sources that haven't migrated to the multi-hoster API. `SerializableHoster`
mirrors `Hoster` for serialization (with `videoList` serialized via
`SerializableVideo`).

### `ThumbnailInfo` / `TileInfo` — episode preview thumbnails

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/ThumbnailInfo.kt`

A source can return a `ThumbnailInfo` describing where preview thumbnails live
(tile spritesheet images + per-tile positions). Used to render episode preview
thumbnails in the anime episode list.

| `ThumbnailInfo` field | Type | Meaning |
|---|---|---|
| `tileInfo` | `List<TileInfo>` | Per-tile metadata. |
| `imageTileUrls` | `List<String>` | Spritesheet image URLs. |

| `TileInfo` field | Type | Meaning |
|---|---|---|
| `imageIndex` | `Int` | Index into `imageTileUrls`. |
| `timeMs` | `Long` | Preview time (ms). |
| `x`, `y`, `width`, `height` | `Int` | Tile rect within the spritesheet. |

### `FetchType` enum (anime-only)

File: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FetchType.kt`

Two values: `Seasons` (source will only call `getSeasonList`) or `Episodes`
(source will only call `getEpisodeList`). Set once at anime-init time and
doesn't change. Column adapter `FetchTypeColumnAdapter` maps it to a `Long`
ordinal in `animes.fetch_type`.

### `UpdateStrategy` / `AnimeUpdateStrategy` enums

Files: `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/model/*UpdateStrategy.kt`

Identical enums, declared separately for the two hierarchies. Two values:
`ALWAYS_UPDATE` (default; included in library updates) and `ONLY_FETCH_ONCE`
(skipped during library updates — used for one-shots and finished series with
single chapters). The `*UpdateStrategyColumnAdapter` maps it to a `Long`
ordinal in `mangas.update_strategy` / `animes.update_strategy`.

---

## See also

- [`database-schema.md`](database-schema.md) — the SQLDelight `.sq` files that
  persist these models (column-by-column).
- [`preferences-catalog.md`](preferences-catalog.md) — the preference keys that
  configure per-user display/sort/filter behaviour (and which default values
  feed back into the `chapterFlags`/`episodeFlags`/`seasonFlags` bitmasks via
  `LibraryPreferences.setChapterSettingsDefault()` / `setEpisodeSettingsDefault()`
  / `setSeasonSettingsDefault()`).
- [`../02-modules/domain.md`](../02-modules/domain.md) — narrative description
  of `:domain`, including repository interfaces, the Interactor pattern, and
  the dual manga/anime package map.
- [`../02-modules/data.md`](../02-modules/data.md) — narrative description of
  `:data`, including the mappers that turn SQLDelight rows into these models.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the source
  contract that defines `SManga`/`SAnime`/`SChapter`/`SEpisode`/`Page`/`Video`/
  `Hoster` and the `MangaSource`/`AnimeSource` interface hierarchies.
- [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
  — how `LibraryManga` / `LibraryAnime` are surfaced in the library screen.
- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — how
  `Chapter` + `Page` drive the reader.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — how
  `Episode` + `Video` + `Hoster` drive the player, and how `CustomButton`
  wires into MPV's Lua scripting.
- [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) — how
  `MangaTrack` / `AnimeTrack` are pushed to trackers.
- [`../03-subsystems/history.md`](../03-subsystems/history.md) — how the
  history models back the "Recently read/watched" screens.
- [`../03-subsystems/updates.md`](../03-subsystems/updates.md) — how the
  `*UpdatesWithRelations` rows feed the Updates tab.
- [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)
  — how the `ExtensionRepo` model is used.
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — where these models sit in the layered architecture.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
  — the dual manga/anime pattern at the project level.
