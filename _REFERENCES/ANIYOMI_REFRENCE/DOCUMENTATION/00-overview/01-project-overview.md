# 00-overview / 01 — Project Overview

## What is Aniyomi?

Aniyomi is a free, open-source Android application for **reading manga** and
**watching anime**. It is a fork of [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)
(a manga-only reader) extended with a full **anime** side: an MPV-based video
player, anime sources, anime library, anime downloads, and anime tracking.

Key characteristics:

- **Dual nature.** The app treats manga and anime as parallel first-class
  concepts. Almost every subsystem has a manga variant and an anime variant
  (e.g. `Chapter` ↔ `Episode`, `MangaSource` ↔ `AnimeSource`,
  `MangaReader` ↔ `AnimePlayer`). This duality is the single most important
  architectural fact about Aniyomi.
- **Source/extension model.** Aniyomi itself ships **no content**. All content
  comes from user-installed "extensions" (APKs) that implement the `source-api`
  contract. This is how it stays legally clean while supporting hundreds of sites.
- **Offline-capable.** Chapters/episodes can be downloaded for offline reading/
  watching. Local files can also be imported as a "source".
- **Tracker integration.** Progress can be synced to MyAnimeList, AniList,
  Shikimori, Bangumi, MangaUpdates, Kitsu.
- **Material 3, Jetpack Compose (migrating).** The UI is mid-migration from
  classic Android Views to Jetpack Compose.

## App identity (from `app/build.gradle.kts`)

| Field | Value |
|---|---|
| Application namespace | `eu.kanade.tachiyomi` |
| Application ID | `xyz.jmir.tachiyomi.mi` |
| Version name | `0.18.1.2` |
| Version code | `131` |
| Inherited from | Tachiyomi (the `eu.kanade.tachiyomi` package root) |

The `eu.kanade` package is Tachiyomi's historical namespace (Kanade = the original
author's handle). Aniyomi keeps it for compatibility with extensions written
against the Tachiyomi source API.

## High-level capabilities

| Area | Manga | Anime |
|---|---|---|
| Browse source catalogs | ✅ | ✅ |
| Library / favorites / categories | ✅ | ✅ |
| Read / watch | paged + continuous reader | MPV-based player |
| Download for offline | ✅ (chapter pages) | ✅ (episode video, incl. torrent via Torrserver) |
| History / resume | ✅ | ✅ |
| Trackers | ✅ | ✅ |
| Backup / restore | ✅ (combined manga+anime) | ✅ (combined) |
| Local source | ✅ (CBZ/CBR/ZIP/RAR/folders) | ✅ (video files) |
| Global search | ✅ | ✅ |
| Updates feed | ✅ | ✅ |
| Extensions ecosystem | ✅ | ✅ |

## Project lineage

```
Tachiyomi (manga only)
   └── Mihon (Tachiyomi successor, manga only)  ← Aniyomi reuses Mihon's build logic & some modules
        └── Aniyomi (manga + anime)              ← this reference
```

This lineage matters because:
- The build-logic plugins under `buildSrc/` use the `mihon.*` package namespace.
- Some modules (e.g. `core:common`, `domain`) retain `tachiyomi.*` package roots.
- The dual manga/anime pattern is **Aniyomi's** addition on top of the Mihon base.

## What Aniyomi is NOT

- Not a content host. It has no servers; it fetches from third-party sites via
  extensions.
- Not a tracker itself. It syncs *to* trackers but doesn't store user accounts.
- Not cloud-based. Library/progress data lives on-device (or in user-made backups).

## Where to look in the source

| You want to... | Start at |
|---|---|
| See the app entry point | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` |
| See the main activity | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/` |
| Understand the source contract | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/` |
| See the manga reader | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/` |
| See the anime player | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/` |
| See the database schema | `../ANIYOMI/data/src/main/sqldelight/` and `.../sqldelightanime/` |
| See domain models | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/` |

## See also

- [`02-tech-stack.md`](02-tech-stack.md) — the libraries behind all of the above.
- [`03-module-map.md`](03-module-map.md) — how the code is split into modules.
- `../../04-data-models/domain-models.md` — the manga/anime/chapter/episode models.
- `../../07-reference/glossary.md` — terminology.
