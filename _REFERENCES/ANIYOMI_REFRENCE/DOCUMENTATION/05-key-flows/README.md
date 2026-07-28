# 05-key-flows / End-to-end user journeys

> Each flow traces a single user action from UI tap through ScreenModel /
> ViewModel, interactor, repository, source / database, and back to a rendered
> screen — citing the actual classes and files at every hop so a reader can
> follow in the source.

## Why this folder exists

The first four documentation folders describe the codebase along four separate
axes:

| Folder | Axis |
|---|---|
| [`00-overview/`](../00-overview/) | What Aniyomi is and the tech stack. |
| [`01-architecture/`](../01-architecture/) | Cross-cutting patterns (DI, navigation, state). |
| [`02-modules/`](../02-modules/) | Per-Gradle-module breakdown. |
| [`03-subsystems/`](../03-subsystems/) | Feature-level deep dives (reader, player, …). |

This folder is the **vertical slice**: it picks one user journey at a time and
shows how those four axes come together in practice. A reader who has read
the architecture and subsystem docs can use these flows to verify their mental
model; a reader who hasn't can use them as a guided tour.

Each flow is dual-aware: it covers both the manga and anime paths where they
diverge, in keeping with the [dual manga/anime pattern](../00-overview/05-project-conventions.md).
Where the manga and anime sides are structurally identical, one side is used
as the running example and the other is summarised in a side-by-side table.

## How to read a flow doc

Every flow doc follows the same shape:

1. A short **Overview** with an ASCII call-graph of the whole flow.
2. A **Step-by-step** section breaking the flow into numbered phases, each
   citing the actual class name and file path.
3. A **Sequence diagram** restating the flow as a vertical timeline.
4. A **See also** section linking to the `03-subsystems/` deep dive that
   expands on the same machinery, plus neighbouring flow docs.

If you only have time for one, read the overview diagram; if you have
time for two, read the overview and the sequence diagram.

## Index

| # | File | Flow |
|---|---|---|
| 1 | [`app-startup.md`](app-startup.md) | Process start → `App.onCreate()` → `MainActivity` → Voyager navigator → default tab → Library ScreenModel loads from SQLDelight → Compose renders. |
| 2 | [`browse-catalog.md`](browse-catalog.md) | Browse tab → source picker → `BrowseMangaSourceScreen` → `SourcePagingSource` → `CatalogueSource.fetchPopularManga` → network → Compose list. Also covers filters, search, and global search. |
| 3 | [`read-manga.md`](read-manga.md) | Tap chapter → `ReaderActivity` → `ChapterLoader` picks a `PageLoader` → pages fetched/decoded → `Viewer` renders → `onPageSelected` → history + tracker write. |
| 4 | [`watch-anime.md`](watch-anime.md) | Tap episode → `PlayerActivity` → `EpisodeLoader` resolves hosters/videos → MPV plays → `PlayerObserver` → progress → history + tracker write. |
| 5 | [`add-to-library.md`](add-to-library.md) | Detail screen → tap favorite → `UpdateManga.awaitUpdateFavorite` / `UpdateAnime.awaitUpdateFavorite` → `favorite = 1` + `dateAdded` set → Library feed re-emits → category assignment prompt. |
| 6 | [`download-chapter.md`](download-chapter.md) | Tap download icon → `*DownloadManager` enqueues → `*Downloader` worker loop → manga page-fetch+CBZ or anime FFmpeg-remux/torrent → notification → queue status. |
| 7 | [`track-progress.md`](track-progress.md) | Chapter read / episode watched → `TrackChapter` / `TrackEpisode` → tracker API call (e.g. AniList GraphQL) → DB track row. Also covers first-time OAuth login. |
| 8 | [`backup-flow.md`](backup-flow.md) | Create: trigger → `BackupCreator` → serialize manga+anime+categories+history+tracks+prefs → gzip protobuf → `.tachibk`. Restore: pick file → `BackupRestorer` → upsert → `PreferenceRestorer` re-arms jobs. |

## Conventions used in these docs

- **Source paths** are relative to `ANIYOMI_REFRENCE/ANIYOMI/`, written as
  `../ANIYOMI/app/src/main/java/...`.
- **Numbered step lists** describe the linear flow; **ASCII sequence diagrams**
  show the call graph when the hops are non-trivial.
- **"See also"** at the end of each doc links to the relevant `03-subsystems/`
  deep dive and to neighbouring architecture docs.
- Each flow covers **both manga and anime** sides — the dual pattern is
  pervasive and the docs do not silently pick one side.

## See also

- [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — the Voyager navigator that several of these flows push onto.
- [`03-subsystems/README.md`](../03-subsystems/README.md) — the subsystem index each flow cross-links into.
