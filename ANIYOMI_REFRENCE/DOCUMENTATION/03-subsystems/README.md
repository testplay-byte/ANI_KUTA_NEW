# 03-subsystems / Index

> Feature-level deep dives that span multiple modules. Each subsystem doc
> explains how a user-visible feature works end-to-end, names the key files,
> and cross-references the modules it cuts across.

## What's a "subsystem"?

A **subsystem** is a coherent feature slice that doesn't live in any single
Gradle module — it pulls code from `:app`, `:domain`, `:data`, `:source-api`,
`:core:common`, and (sometimes) external libraries. The per-module docs in
[`../02-modules/`](../02-modules/README.md) describe what each module *is*;
the subsystem docs here describe what the app *does*.

The two flagship subsystems — the manga reader and the anime player — are by
far the most complex code in the codebase and have the most detailed docs.

## Subsystem docs

| File | Subsystem | One-line description |
|---|---|---|
| [`library-management.md`](library-management.md) | Library management | Library entries, categories, favorites, sort/filter, badges. |
| [`manga-reader.md`](manga-reader.md) | **Manga reader** | View-based Activity with `subsampling-scale-image-view`/PhotoView, 6 reading modes, paged + continuous viewers, page decoding pipeline. |
| [`anime-player.md`](anime-player.md) | **Anime player** | View-based Activity wrapping an MPV JNI View, Compose controls, hoster/video resolution, Torrserver, PiP, media session, AniSkip, custom Lua buttons. |
| [`source-system.md`](source-system.md) | Source system | How sources/extensions are loaded, registered, and invoked; the manga/anime source duality. |
| [`download-manager.md`](download-manager.md) | Download manager | Download queue, providers, store layout, post-read/delete, download-ahead. |
| [`trackers.md`](trackers.md) | Trackers | MAL, AniList, Shikimori, Bangumi, … — auth, sync, delayed updates. |
| [`backup-restore.md`](backup-restore.md) | Backup & restore | Backup file format, schema migration, restore logic. |
| [`history.md`](history.md) | History | Recently-read/watched, resume points, session read/watch duration. *(planned)* |
| [`updates.md`](updates.md) | Updates | Update checking for library entries (new chapters/episodes) and extensions. *(planned)* |
| [`search-discovery.md`](search-discovery.md) | Search & discovery | Browse, search, global search across sources. |
| [`extensions-update.md`](extensions-update.md) | Extensions update | Extension repo management, update flow, APK installation. *(planned)* |
| [`torrent-streaming.md`](torrent-streaming.md) | Torrent streaming | Torrserver integration for anime; magnet/torrent playback. *(planned)* |
| [`notifications.md`](notifications.md) | Notifications | Notification channels, triggers, PendingIntent routing. |
| [`storage-and-cache.md`](storage-and-cache.md) | Storage & cache | SAF, the download/cache/image directories, disk layout. |
| [`updater.md`](updater.md) | Updater | App self-update mechanism (GitHub releases). *(planned)* |

*(Subsystems marked "planned" are listed in the master index
[`../README.md`](../README.md) but have not yet been written by a subagent.)*

## Subsystem × module mini-matrix

Each subsystem touches several modules. The matrix below is a quick
orientation; a full cross-reference (subsystem × module × key-file) lives at
[`../07-reference/cross-reference-matrix.md`](../07-reference/cross-reference-matrix.md).

| Subsystem | `:app` | `:domain` | `:data` | `:source-api` | `:source-local` | `:core:common` | `:core:archive` | `:core-metadata` | external libs |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|
| Library management | ● | ● | ● | ● | ○ | ○ | — | — | — |
| **Manga reader** | ● | ● | ● | ● | ● | ● | ● | — | subsampling-scale-image-view, PhotoView, Coil 3 |
| **Anime player** | ● | ● | ● | ● | ● | ● | — | — | aniyomi-mpv-lib, ffmpeg-kit, Torrserver, NanoHTTPD, seeker, truetypeparser |
| Source system | ● | ○ | ○ | ● | ● | ● | — | — | — |
| Download manager | ● | ● | ● | ● | ○ | ● | ● | ○ | — |
| Trackers | ● | ● | ● | — | — | ● | — | — | — |
| Backup & restore | ● | ● | ● | ○ | ○ | ● | ○ | ○ | — |
| History | ● | ● | ● | — | — | ○ | — | — | — |
| Updates | ● | ● | ● | ● | — | ● | — | — | — |
| Search & discovery | ● | ● | ● | ● | ● | ● | — | — | — |
| Extensions update | ● | ● | ● | ● | — | ● | — | — | — |
| Torrent streaming | ● | ○ | — | ● | — | ● | — | — | Torrserver |
| Notifications | ● | ○ | — | — | — | ● | — | — | — |
| Storage & cache | ● | ○ | ○ | — | ● | ● | ○ | — | — |
| Updater | ● | ○ | — | — | — | ● | — | — | — |

Legend: **●** primary code in this module · **○** secondary/touches · **—** not
involved.

## How subsystems cross-cut modules

Subsystems are **vertical slices** through the layered architecture (see
[`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)).
For example, the manga reader:

```
 ReaderActivity  ──────── :app  ────────  (View + Compose UI)
 ReaderViewModel ──────── :app  ────────  (androidx.lifecycle.ViewModel)
 ChapterLoader   ──────── :app  ────────  (loader dispatch)
 HttpPageLoader  ──────── :app  ────────  (uses :source-api HttpSource + :core:common ChapterCache)
 ArchivePageLoader ────── :app  ────────  (uses :core:archive ArchiveReader)
 SubsamplingScaleImageView  ───────────  (external View, in :app)
 ReaderPreferences ────── :app  ────────  (uses :core:common PreferenceStore)
 GetChaptersByMangaId ─── :domain ──────  (interactor)
 ChapterRepository ────── :data ────────  (SQLDelight impl)
 chapters.sq ─────────── :data ────────  (schema)
 MangaSource / HttpSource  ─ :source-api  (contract, implemented by extensions)
 LocalMangaSource ──────── :source-local  (built-in local-files source)
```

When reading any subsystem doc, keep this layered picture in mind: the
subsystem doc focuses on the **`:app`-side orchestration** and links down to
the per-module docs for the lower-layer details.

## The dual manga/anime pattern

Almost every subsystem exists in two parallel halves — a manga side and an
anime side — because Aniyomi inherited Tachiyomi's manga codebase and bolted
an anime layer on top with a near-identical shape. Where this duplication
matters for a subsystem, the doc calls it out explicitly. See
[`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)
for the convention overview.

Examples:

- **Reader ↔ Player** — two parallel Activities with parallel ViewModels,
  parallel loaders (`ChapterLoader` ↔ `EpisodeLoader`), parallel models
  (`ReaderChapter`/`ReaderPage` ↔ (no anime equivalent — the player uses the
  source `Video` directly), parallel preference catalogs (`ReaderPreferences`
  ↔ six `*Preferences` classes), and parallel progress-reporting hooks.
- **Library management** — manga library vs anime library, each with their
  own categories, filters, badges.
- **Download manager** — `MangaDownloadManager` vs `AnimeDownloadManager`,
  each with their own queue, provider, and store layout.
- **Trackers** — manga `TrackChapter` vs anime `TrackEpisode`, both writing
  through the same tracker implementations (MAL, AniList, Shikimori, Bangumi).
- **History** — `MangaHistory` vs `AnimeHistory`, each with their own SQLDelight
  table.

## See also

- [`../README.md`](../README.md) — the master documentation index.
- [`../02-modules/README.md`](../02-modules/README.md) — per-module deep
  dives (the horizontal counterpart to this vertical view).
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — the layered architecture these subsystems slice through.
- [`../05-key-flows/`](../05-key-flows/README.md) — end-to-end user journeys
  that exercise multiple subsystems.
- [`../07-reference/cross-reference-matrix.md`](../07-reference/cross-reference-matrix.md)
  — full subsystem × module × key-file matrix.
