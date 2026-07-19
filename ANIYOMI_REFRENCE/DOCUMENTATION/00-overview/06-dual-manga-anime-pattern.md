# 00-overview / 06 — The Dual Manga/Anime Pattern (Consolidated Guide)

> The single most important architectural fact about Aniyomi: **almost every
> concept exists twice** — once for manga, once for anime. This guide consolidates
> the parallel mappings in one place so you can always find the counterpart.
>
> This pattern is mentioned throughout the docs; this doc is the authoritative
> cross-reference.

## Why the duality exists

Aniyomi is a fork of Tachiyomi (manga-only). Aniyomi added a full **anime** side
on top: an MPV player, anime sources, anime library, anime downloads, anime
tracking. Rather than merge the two into a unified model, Aniyomi **duplicated**
the manga architecture for anime. The result: pervasive, intentional parallelism.

## The core concept pairs

| Manga concept | Anime concept | Notes |
|---|---|---|
| **Manga** | **Anime** | A library entry. |
| **Chapter** | **Episode** | A readable/watchable unit. |
| **Page** | **Video** | A sub-unit (one image / one playable URL). |
| **MangaSource** / `Source` | **AnimeSource** | The site/extension. |
| `reader` | `player` | The Activity that displays content. |
| `ReaderActivity` / `ReaderViewModel` | `PlayerActivity` / `PlayerViewModel` | |
| **Chapter read** | **Episode seen** | The "consumed" flag. |
| `last_page_read` | `last_second_seen` | Resume position. |

## Package mappings (verified against the snapshot)

### Domain layer (`:domain`)

| Manga package | Anime package |
|---|---|
| `tachiyomi.domain.entries.manga` | `tachiyomi.domain.entries.anime` |
| `tachiyomi.domain.items.chapter` | `tachiyomi.domain.items.episode` |
| `tachiyomi.domain.source.manga` | `tachiyomi.domain.source.anime` |
| `tachiyomi.domain.track.manga` | `tachiyomi.domain.track.anime` |
| `tachiyomi.domain.download.manga` | `tachiyomi.domain.download.anime` |
| `tachiyomi.domain.history.manga` | `tachiyomi.domain.history.anime` |
| `tachiyomi.domain.category.manga` | `tachiyomi.domain.category.anime` |
| `tachiyomi.domain.library.manga` | `tachiyomi.domain.library.anime` |
| `tachiyomi.domain.updates.manga` | `tachiyomi.domain.updates.anime` |

> Anime-only additions: `aniyomi.domain.anime` (incl. `SeasonAnime`),
> `tachiyomi.domain.items.season`. Manga-only: `excluded_scanlators`.

### Data layer (`:data`)

| Manga | Anime |
|---|---|
| `data/src/main/sqldelight/` | `data/src/main/sqldelightanime/` |
| `data/src/main/sqldelight/data/*.sq` | `data/src/main/sqldelightanime/dataanime/*.sq` |
| `data/src/main/sqldelight/migrations/` (1.sqm–32.sqm) | `data/src/main/sqldelightanime/migrations/` (113.sqm–135.sqm) |
| `data/src/main/sqldelight/view/` | `data/src/main/sqldelightanime/view/` |
| `tachiyomi.data.entries.manga` | `tachiyomi.data.entries.anime` |
| `tachiyomi.data.source.manga` | `tachiyomi.data.source.anime` |
| `tachiyomi.data.track.manga` | `tachiyomi.data.track.anime` |
| `tachiyomi.data.history.manga` | `tachiyomi.data.history.anime` |
| `tachiyomi.data.category.manga` | `tachiyomi.data.category.anime` |
| `tachiyomi.data.handlers.manga` | `tachiyomi.data.handlers.anime` |
| Database: `tachiyomi.db` (v32) | Database: `tachiyomi.animedb` (v135) |

> The two databases are **independent**: separate `.db` files, separate version
> counters, separate migrations. The anime schema was bootstrapped at v113 (when
> the manga DB was at v112), so they share a baseline shape but then diverged.

### Source API (`:source-api`, KMP)

| Manga | Anime |
|---|---|
| `eu.kanade.tachiyomi.source` | `eu.kanade.tachiyomi.animesource` |
| `Source` / `MangaSource` / `CatalogueSource` | `AnimeSource` / `AnimeCatalogueSource` |
| `HttpSource` / `ParsedHttpSource` | `AnimeHttpSource` / `ParsedAnimeHttpSource` |
| `source.model.SManga` / `SChapter` / `Page` | `animesource.model.SAnime` / `SEpisode` / `Video` |

> The two hierarchies have **no shared base interface** — they're fully
> independent lattices. See `../02-modules/source-api.md`.

### App UI (`:app`)

| Manga | Anime |
|---|---|
| `ui/reader/` (ReaderActivity) | `ui/player/` (PlayerActivity) |
| `ui/library/` (manga half) | `ui/library/` (anime half) |
| `ui/entries/manga/` (MangaScreen) | `ui/entries/anime/` (AnimeScreen) |
| `ui/updates/manga/` | `ui/updates/anime/` |
| `ui/history/` (manga) | `ui/history/` (anime) |
| `ui/download/manga/` | `ui/download/anime/` |
| `data/download/manga/` | `data/download/anime/` |
| `data/track/manga/` (none — shared `data/track/`) | (shared) |

### Extensions

| Manga | Anime |
|---|---|
| `extension/manga/` (MangaExtensionManager, MangaExtensionLoader) | `extension/anime/` (AnimeExtensionManager, AnimeExtensionLoader) |
| `LIB_VERSION_MIN = 1.4`, `LIB_VERSION_MAX = 1.5` | `LIB_VERSION_MIN = 12`, `LIB_VERSION_MAX = 16` |

> Note the **different version ranges**: manga extensions use a 1.x semver scheme
> (Tachiyomi lineage); anime extensions use an integer scheme (Aniyomi's own).

### Preferences

Most `*Preferences` classes cover **both** sides via paired methods rather than
duplicate classes. Examples (verified):
- `LibraryPreferences`: `mangaSortingMode()` / `animeSortingMode()`,
  `filterUnread()` / `filterUnseen()`, `downloadNewChapters()` / `downloadNewEpisodes()`.
- `DownloadPreferences`: single class, covers both.

## The divergence-tracking reality

The two sides are **not perfectly symmetric**. Notable asymmetries:

| Aspect | Manga | Anime |
|---|---|---|
| History `readDuration` | ✅ (`history.time_read` accumulates) | ❌ (only `last_seen`) |
| Episode/Chapter extra fields | `scanlator`, `excluded_scanlators` table | `fillermark`, `summary`, `preview_url`, `season_*` |
| Custom buttons (Lua) | ❌ | ✅ (`custom_buttons` table, MPV) |
| Seasons | ❌ | ✅ (`animeseasons`, `SeasonAnime`) |
| Schema views | fewer | 5 extra (episodestats, seasonstats, etc.) |
| Auto-track gate | `!incognito && autoUpdateTrack` | adds `|| !hasTrackers` |
| Download-ahead threshold | 25% of chapter | 35% of episode |

## Maintenance rule (for anyone porting/extending)

> **When you add or change a manga feature, find and update the anime counterpart
> — and vice versa.** The two sides must stay in step. The asymmetries above are
> intentional; new asymmetries should be deliberate and documented.

A practical check: after touching any file under a `manga/` package, search for
the parallel `anime/` package and confirm whether the same change applies.

## See also

- [`05-project-conventions.md`](05-project-conventions.md) — §2 has the concept-pair table.
- [`03-module-map.md`](03-module-map.md) — §"dual manga/anime pattern".
- [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md) — field-by-field comparison.
- [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) — the two parallel schemas.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the two source hierarchies.
