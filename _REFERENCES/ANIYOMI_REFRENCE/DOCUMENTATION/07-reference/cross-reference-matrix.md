# 07-reference / cross-reference-matrix — Subsystem × Module × Key-file

> A "if I'm working on subsystem X, which modules and files do I care about?"
> reference. Two parts: (1) a compact `✓` matrix of all 15 subsystems against
> all 13 Gradle modules; (2) per-subsystem detail blocks listing 1–3 key files
> per touched module. Paths are relative to `../ANIYOMI/`.

## How to use this doc

1. **Find your subsystem** in the matrix to see which modules are touched (✓).
2. **Jump to the per-subsystem detail block** below for the concrete file
   paths in each touched module.
3. Braces `{manga,anime}` in a path indicate the dual pattern — there are two
   near-identical files, one per side.

Module abbreviations used in the matrix (full names: see
[`../00-overview/03-module-map.md`](../00-overview/03-module-map.md)):

| Abbr | Module |
|---|---|
| `app` | `:app` |
| `com` | `:core:common` |
| `arc` | `:core:archive` |
| `md` | `:core-metadata` |
| `dat` | `:data` |
| `dom` | `:domain` |
| `i18` | `:i18n` |
| `i18a` | `:i18n-aniyomi` |
| `mb` | `:macrobenchmark` |
| `pc` | `:presentation-core` |
| `pw` | `:presentation-widget` |
| `sa` | `:source-api` |
| `sl` | `:source-local` |

---

## Part 1 — The compact matrix

Legend: **✓** = subsystem has code in this module (1–3 key files listed in
Part 2). Blank = not involved.

| Subsystem | `app` | `com` | `arc` | `md` | `dat` | `dom` | `i18` | `i18a` | `mb` | `pc` | `pw` | `sa` | `sl` |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Library management | ✓ | ✓ |  |  | ✓ | ✓ | ✓ | ✓ |  | ✓ | ✓ | ✓ |  |
| Manga reader | ✓ | ✓ | ✓ |  | ✓ | ✓ | ✓ |  |  | ✓ |  | ✓ | ✓ |
| Anime player | ✓ | ✓ |  |  | ✓ | ✓ | ✓ | ✓ |  | ✓ |  | ✓ | ✓ |
| Source system | ✓ | ✓ |  |  | ✓ |  |  |  |  |  |  | ✓ | ✓ |
| Download manager | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |  |  |  | ✓ |  |
| Trackers | ✓ | ✓ |  |  | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| Backup & restore | ✓ | ✓ | ✓ |  | ✓ | ✓ | ✓ | ✓ |  |  |  |  |  |
| History | ✓ |  |  |  | ✓ | ✓ | ✓ |  |  |  |  |  |  |
| Updates | ✓ | ✓ |  |  | ✓ | ✓ | ✓ | ✓ |  |  |  | ✓ |  |
| Search & discovery | ✓ | ✓ |  |  | ✓ | ✓ | ✓ | ✓ |  |  |  | ✓ | ✓ |
| Extensions update | ✓ | ✓ |  |  | ✓ | ✓ | ✓ |  |  |  |  | ✓ |  |
| Torrent streaming | ✓ | ✓ |  |  |  |  |  | ✓ |  |  |  | ✓ |  |
| Notifications | ✓ | ✓ |  |  |  |  | ✓ | ✓ |  |  |  |  |  |
| Storage & cache | ✓ | ✓ | ✓ |  | ✓ | ✓ | ✓ |  |  |  |  |  | ✓ |
| Updater (self-update) | ✓ | ✓ |  |  | ✓ |  | ✓ |  | ✓ |  |  |  |  |

Notes on the matrix:

- **`com` (`:core:common`) is touched by 14 of 15 subsystems** — it's the
  grab-bag of network, storage, preference, and Torrserver plumbing. The only
  subsystem that doesn't touch it is **History** (history is a pure domain +
  data + UI subsystem).
- **`sa` (`:source-api`) is touched by 9 subsystems** — anything that
  fetches content from the internet touches the source contract.
- **`sl` (`:source-local`) is touched by only 5 subsystems** — the local
  source is just another source from the app's perspective; most features
  don't care whether content came from local or remote.
- **`mb` (`:macrobenchmark`) is touched by 1 subsystem only** — Updater
  (it generates the baseline profile that ships with the APK).
- **`pw` (`:presentation-widget`) is touched by 1 subsystem only** — Library
  management (the home-screen widgets show library updates).
- **`i18a` (`:i18n-aniyomi`) is touched by 8 subsystems** — anything
  Aniyomi-added (anime player, downloads anime side, torrent, notifications
  for anime, storage for mpv-config, etc.) pulls Aniyomi-only strings.

---

## Part 2 — Per-subsystem detail blocks

### 1. Library management

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/ui/library/{manga,anime}/{Manga,Anime}LibraryScreenModel.kt`, `app/src/main/java/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/core/Constants.kt`, `core/common/src/main/java/tachiyomi/core/common/preference/PreferenceStore.kt` |
| `dat` | `data/src/main/sqldelight/data/{mangas,categories,mangas_categories,excluded_scanlators}.sq`, `data/src/main/sqldelight/view/libraryView.sq`, `data/src/main/sqldelightanime/dataanime/{animes,animes_categories}.sq`, `data/src/main/sqldelightanime/view/animelibView.sq` |
| `dom` | `domain/src/main/java/tachiyomi/domain/library/LibraryPreferences.kt`, `domain/src/main/java/tachiyomi/domain/category/{manga,anime}/interactor/`, `domain/src/main/java/tachiyomi/domain/entries/{manga,anime}/interactor/UpdateManga.kt` (and `UpdateAnime.kt`) |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (library/category strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-library strings) |
| `pc` | `presentation-core/src/main/java/tachiyomi/presentation/core/components/` (LazyGrid, Badges, Pill) |
| `pw` | `presentation-widget/src/main/java/.../{Manga,Anime}WidgetManager.kt` (drives widget refresh) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/model/{SManga,SAnime}.kt` (cover URL fields) |

### 2. Manga reader

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`, `ReaderViewModel.kt`, `loader/ChapterLoader.kt`, `viewer/`, `setting/ReaderPreferences.kt` |
| `com` | `core/common/src/main/java/tachiyomi/core/common/util/image/ImageUtil.kt`, `core/common/src/main/java/.../network/NetworkHelper.kt` (for `HttpPageLoader`) |
| `arc` | `core/archive/src/main/kotlin/mihon/core/archive/ArchiveReader.kt` (used by `ArchivePageLoader`), `EpubReader.kt` |
| `dat` | `data/src/main/sqldelight/data/chapters.sq`, `data/src/main/java/tachiyomi/data/items/chapter/ChapterRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/items/chapter/`, `domain/src/main/java/tachiyomi/domain/items/chapter/service/ChapterRecognition.kt`, `ChapterSorter.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (reader strings) |
| `pc` | `presentation-core/src/main/java/.../` (ColorFilter, Slider, AdaptiveSheet used by reader settings dialogs) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt` (page fetch), `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt` |
| `sl` | `source-local/src/androidMain/kotlin/.../LocalMangaSource.kt` (for local-chapter reading) |

### 3. Anime player

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`, `PlayerViewModel.kt`, `AniyomiMPVView.kt`, `loader/EpisodeLoader.kt`, `loader/HosterLoader.kt`, `settings/PlayerPreferences.kt`, `utils/AniSkipApi.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/util/storage/FFmpegUtils.kt`, `core/common/src/main/java/.../network/NetworkHelper.kt` |
| `dat` | `data/src/main/sqldelightanime/dataanime/episodes.sq`, `data/src/main/sqldelightanime/view/animeseasonsView.sq`, `data/src/main/java/tachiyomi/data/items/episode/EpisodeRepositoryImpl.kt`, `data/src/main/java/tachiyomi/data/custombutton/CustomButtonRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/items/episode/`, `domain/src/main/java/tachiyomi/domain/items/episode/service/EpisodeRecognition.kt`, `SeasonSorter.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (common player strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (the bulk of the player-UI strings) |
| `pc` | `presentation-core/src/main/java/.../` (Slider, sheets, code-editor for custom buttons) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt`, `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/{Video,Hoster,FetchType}.kt` |
| `sl` | `source-local/src/androidMain/kotlin/.../LocalAnimeSource.kt` |

### 4. Source system

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/{Manga,Anime}ExtensionManager.kt`, `util/{Manga,Anime}ExtensionLoader.kt`, `installer/`, `app/src/main/java/eu/kanade/tachiyomi/source/{manga,anime}/Android{Manga,Anime}SourceManager.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`, `core/common/src/main/java/eu/kanade/tachiyomi/network/JavaScriptEngine.kt` |
| `dat` | `data/src/main/sqldelight/data/sources.sq`, `data/src/main/sqldelightanime/dataanime/animesources.sq`, `data/src/main/java/tachiyomi/data/source/{manga,anime}/{Manga,Anime}StubSourceRepositoryImpl.kt` |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/` (manga contract), `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/` (anime contract) |
| `sl` | `source-local/src/androidMain/kotlin/.../Local{Manga,Anime}Source.kt` (the built-in registered source at id `0`) |

### 5. Download manager

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadManager.kt`, `{Manga,Anime}Downloader.kt`, `{Manga,Anime}DownloadProvider.kt`, `{Manga,Anime}DownloadJob.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/util/storage/{DiskUtil,FFmpegUtils}.kt` |
| `arc` | `core/archive/src/main/kotlin/mihon/core/archive/ZipWriter.kt` (CBZ packaging for manga) |
| `md` | `core-metadata/src/main/java/tachiyomi/core/metadata/` (writes `ComicInfo.xml` into CBZ) |
| `dat` | `data/src/main/java/tachiyomi/data/items/{chapter,episode}/{Chapter,Episode}RepositoryImpl.kt` (status updates) |
| `dom` | `domain/src/main/java/tachiyomi/domain/download/` (per-side `DownloadPreferences`), `domain/src/main/java/tachiyomi/domain/items/{chapter,episode}/interactor/Filter{Chapters,Episodes}ForDownload.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (download notifier strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-download strings) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/model/{Page,Video}.kt`, `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/` (anime torrent downloads) |

### 6. Trackers

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/track/{Tracker,BaseTracker,MangaTracker,AnimeTracker,Enhanced*Tracker,Deletable*Tracker,TrackerManager}.kt`, per-service packages (`myanimelist/`, `anilist/`, `shikimori/`, `bangumi/`, `kitsu/`, `simkl/`, `mangaupdates/`, `komga/`, `kavita/`, `suwayomi/`, `jellyfin/`), `app/src/main/java/eu/kanade/tachiyomi/ui/setting/track/{BaseOAuthLoginActivity,TrackLoginActivity}.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt`, `OkHttpExtensions.kt` |
| `dat` | `data/src/main/sqldelight/data/manga_sync.sq`, `data/src/main/sqldelightanime/dataanime/anime_sync.sq`, `data/src/main/java/tachiyomi/data/track/{manga,anime}/{Manga,Anime}TrackRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/track/{manga,anime}/` (model, repository, interactors `AddMangaTracks`/`AddAnimeTracks`, `TrackChapter`/`TrackEpisode`, `SyncChapterProgressWithTrack`), `domain/src/main/java/tachiyomi/domain/track/TrackPreferences.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (tracker names, status labels) |

### 7. Backup & restore

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`, `create/BackupCreateJob.kt`, `restore/BackupRestorer.kt`, `restore/BackupRestoreJob.kt`, `models/*.kt` (proto DTOs), `restore/restorers/*.kt` |
| `com` | `core/common/src/main/java/tachiyomi/core/common/preference/PreferenceStore.kt` (to enumerate prefs), `core/common/src/main/java/.../network/` (legacy import path detection) |
| `arc` | `core/archive/src/main/kotlin/mihon/core/archive/ZipWriter.kt` (not used directly, but `Backup` is gzip — included for parity) |
| `dat` | `data/src/main/sqldelight/` (manga schema, all tables), `data/src/main/sqldelightanime/` (anime schema, all tables), `data/src/main/java/tachiyomi/data/...` (all repository impls needed by restorers) |
| `dom` | `domain/src/main/java/tachiyomi/domain/backup/BackupPreferences.kt`, all domain interactors used during restore (`SetMangaCategories`, `UpdateManga`, `InsertEpisodes`, …) |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (backup/restore UI strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-side restore strings) |

### 8. History

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/ui/history/{manga,anime}/{Manga,Anime}HistoryScreenModel.kt`, `HistoriesTab.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` (`updateHistory`), `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` (`saveEpisodeHistory`, `saveEpisodeProgress`, `saveWatchingProgress`) |
| `dat` | `data/src/main/sqldelight/data/history.sq`, `data/src/main/sqldelight/view/historyView.sq`, `data/src/main/sqldelightanime/dataanime/animehistory.sq`, `data/src/main/sqldelightanime/view/animehistoryView.sq`, `data/src/main/java/tachiyomi/data/history/{manga,anime}/{Manga,Anime}HistoryRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/history/{manga,anime}/` (model, repository, `GetNext{Chapters,Episodes}` interactors, `GetHistory{,ById}`) |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (history-tab strings) |

### 9. Updates (library update checking)

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/library/{manga,anime}/{Manga,Anime}LibraryUpdateJob.kt`, `{Manga,Anime}LibraryUpdateNotifier.kt`, `{Manga,Anime}MetadataUpdateJob.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/updates/{manga,anime}/{Manga,Anime}UpdatesScreenModel.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt`, `core/common/src/main/java/.../util/system/DeviceUtil.kt` (Wi-Fi check) |
| `dat` | `data/src/main/sqldelight/view/updatesView.sq`, `data/src/main/sqldelightanime/view/animeupdatesView.sq`, `data/src/main/java/tachiyomi/data/updates/{manga,anime}/{Manga,Anime}UpdatesRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/library/LibraryPreferences.kt` (update interval, restrictions), `domain/src/main/java/tachiyomi/domain/updates/{manga,anime}/`, `domain/src/main/java/tachiyomi/domain/entries/{manga,anime}/service/{Manga,Anime}FetchInterval.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (update-notification strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-update strings) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/online/HttpSource.kt` (fetch chapter/episode list) |

### 10. Search & discovery

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/ui/browse/{manga,anime}/source/browse/Browse{Manga,Anime}SourceScreen.kt`, `source/globalsearch/Global{Manga,Anime}SearchScreen.kt`, `ui/deeplink/{manga,anime}/DeepLink{Manga,Anime}Activity.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt` |
| `dat` | `data/src/main/java/tachiyomi/data/source/{manga,anime}/{Manga,Anime}SourcePagingSource.kt`, `data/src/main/java/tachiyomi/data/source/{manga,anime}/{Manga,Anime}SourceRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/tachiyomi/domain/source/{manga,anime}/interactor/GetRemote{Manga,Anime}.kt`, `SearchManga.kt` / `SearchAnime.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (search/browse strings) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-browse strings) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/model/{MangasPage,AnimesPage}.kt`, `Filter.kt` / `AnimeFilter.kt`, `FilterList.kt` / `AnimeFilterList.kt` |
| `sl` | `source-local/src/androidMain/kotlin/.../Local{Manga,Anime}Source.kt` (local source is searchable like any other) |

### 11. Extensions update

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/api/{Manga,Anime}ExtensionApi.kt`, `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionUpdateNotifier.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` (the `LaunchedEffect(Unit)` that triggers the check), `app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/{Manga,Anime}ExtensionReposScreen.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt` |
| `dat` | `data/src/main/sqldelight/data/extension_repos.sq`, `data/src/main/sqldelightanime/dataanime/extension_repos.sq`, `data/src/main/java/mihon/data/repository/{manga,anime}/{Manga,Anime}ExtensionRepoRepositoryImpl.kt` |
| `dom` | `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/` (model, repository, 6 interactors per side: `Create*`, `Update*`, `Replace*`, `Delete*`, `Get*`, `Subscribe*`), `domain/src/main/java/mihon/domain/extensionrepo/service/` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (extension-repo UI strings) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/` (the `extensions-lib` version this checks against) |

### 12. Torrent streaming (anime only)

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/torrent/service/TorrentServerService.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` (`torrentLinkHandler`), `app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt` (`torrentDownload`) |
| `com` | `core/common/src/main/java/.../TorrentServerApi.kt`, `TorrentServerUtils.kt`, `TorrentPreferences.kt` (under `tachiyomi.core.common.torrent`) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (torrent-error strings) |
| `sa` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/TorrentUtils.kt`, `model/{TorrentInfo,TorrentFile}.kt`, `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/HttpServer.kt` (per-source NanoHTTPD field) |

### 13. Notifications

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/notification/{Notifications,NotificationReceiver,NotificationHandler}.kt`, per-feature `*Notifier.kt` (`data/backup/BackupNotifier.kt`, `data/download/{manga,anime}/{Manga,Anime}DownloadNotifier.kt`, `data/library/{manga,anime}/{Manga,Anime}LibraryUpdateNotifier.kt`, `data/updater/AppUpdateNotifier.kt`, `extension/ExtensionUpdateNotifier.kt`, `ui/reader/SaveImageNotifier.kt`), `app/src/main/java/eu/kanade/tachiyomi/App.kt` (inner `DisableIncognitoReceiver`) |
| `com` | `core/common/src/main/java/tachiyomi/core/common/util/system/` (Android version helpers, channel-importance helpers) |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (channel names, common action labels) |
| `i18a` | `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (anime-side notification strings) |

### 14. Storage & cache

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/cache/{MangaCoverCache,AnimeCoverCache,AnimeBackgroundCache,ChapterCache}.kt`, `app/src/main/java/eu/kanade/tachiyomi/data/coil/{MangaCoverFetcher,AnimeImageFetcher,TachiyomiImageDecoder}.kt`, `app/src/main/java/eu/kanade/tachiyomi/data/storage/AndroidStorageFolderProvider.kt` (legacy fallback), `app/src/main/java/aniyomi/util/DataSaver.kt` |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/util/storage/{DiskUtil,FileExtensions}.kt` |
| `arc` | `core/archive/src/main/kotlin/mihon/core/archive/` (used for archive-style local source) |
| `dat` | `data/src/main/sqldelight/` (any table that holds a cover URL or file path) |
| `dom` | `domain/src/main/java/tachiyomi/domain/storage/{StorageManager,StoragePreferences}.kt` |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (storage-error strings) |
| `sl` | `source-local/src/androidMain/kotlin/.../` (owns the `local/` and `localanime/` subdirs) |

### 15. Updater (app self-update)

| Module | Key files |
|---|---|
| `app` | `app/src/main/java/eu/kanade/tachiyomi/data/updater/{AppUpdateChecker,AppUpdateDownloadJob,AppUpdateNotifier}.kt`, `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` (`CheckForUpdates`), `app/src/main/java/eu/kanade/presentation/more/NewUpdateScreen.kt`, `app/src/main/java/eu/kanade/tachiyomi/util/system/BuildConfig.kt` (`updaterEnabled`) |
| `com` | `core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt` |
| `dat` | `data/src/main/java/tachiyomi/data/release/{ReleaseServiceImpl,GithubRelease}.kt` (the GitHub API call) |
| `i18` | `i18n/src/commonMain/moko-resources/base/strings.xml` (update-prompt strings) |
| `mb` | `macrobenchmark/src/main/java/tachiyomi/macrobenchmark/BaselineProfileGenerator.kt` (the baseline profile that ships with the APK — independent of the update flow, but related to "new APK performance") |

---

## Notes on reading the matrix

- **"Touched" means real source code, not transitive dependencies.** A
  subsystem that merely *uses* a string from `:i18n` is marked ✓; a
  subsystem that has zero files of its own in a module is left blank.
- **The dual manga/anime pattern is visible in every row.** Wherever `data`,
  `domain`, or `app` is touched, look for parallel `{manga,anime}` paths.
- **`com` (`:core:common`) shows up almost everywhere because it owns the
  preference store and the OkHttp client** — both are foundational. Don't
  over-index on its ✓: the per-subsystem detail block tells you *which* file
  in `com` actually matters.
- **`sa` (`:source-api`) shows up in any subsystem that fetches content.**
  The contract surface is small (~50 files); the detail block names the
  specific interface or model that subsystem cares about.
- **`mb` (`:macrobenchmark`) shows up only under Updater** because the
  baseline-profile generator produces the file shipped inside the APK. The
  macrobenchmark module does not interfere with the self-updater at runtime.

## See also

- [`README.md`](README.md) — this folder's index.
- [`file-index.md`](file-index.md) — flat "where do I find X?" lookup.
- [`glossary.md`](glossary.md) — terminology definitions.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) —
  module roster + dependency graph.
- [`../02-modules/README.md`](../02-modules/README.md) — per-module deep dives.
- [`../03-subsystems/README.md`](../03-subsystems/README.md) — per-subsystem
  deep dives (with its own mini subsystem × module matrix at the top of that
  README).
