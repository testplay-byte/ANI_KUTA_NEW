# 02-modules / `:app` — The main application module

> `:app` is the biggest module in the project (~1,400+ source files). It is the
> Android application itself: it owns the `Application` class, all activities,
> the entire Compose/Views UI, the reader and the player, the source/extension
> loaders, the download managers, the tracker integrations, the backup engine,
> the notification system, the crash handler, and the Injekt DI composition root.
> Every other module exists to be consumed by `:app`.

| | |
|---|---|
| **Path** | `../ANIYOMI/app/` |
| **Namespace** | `eu.kanade.tachiyomi` |
| **Application ID** | `xyz.jmir.tachiyomi.mi` (release) — `.dev` / `.debug` / `.benchmark` suffixes per build type |
| **Version** | `0.18.1.2` (code `131`) |
| **Package roots** | `eu.kanade.tachiyomi.*`, `eu.kanade.presentation.*`, `mihon.*`, `aniyomi.*`, `tachiyomi.*` |
| **Build script** | `../ANIYOMI/app/build.gradle.kts` |

## Purpose & role

`:app` is the **composition root** of Aniyomi. It is the only module that:

- Produces an installable APK (the `mihon.android.application` plugin).
- Declares an `Application` subclass (`App`) and a launcher activity
  (`MainActivity`).
- Wires the entire Injekt dependency graph (see
  [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)).
- Hosts the reader (`ui/reader/`) and the MPV-based anime player (`ui/player/`).
- Loads external extension APKs at runtime and registers them as sources.
- Owns all Android-specific infrastructure: notifications, foreground services,
  WorkManager jobs, FileProviders, Shizuku provider, receivers.

Everything in `:domain`, `:data`, `:core:*`, `:source-api`, `:source-local`,
`:i18n`, `:i18n-aniyomi`, `:presentation-core`, and `:presentation-widget` is
funnelled into `:app` and surfaced to the user through activities, screens,
and Compose composables.

## Build config

See [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) for
the project-wide Gradle setup. Highlights specific to `:app`:

### Build types

| Build type | Application ID suffix | Notes |
|---|---|---|
| `debug` | `.dev` | Pseudo-locales enabled; versionName suffix = commit count. |
| `release` | — | `minify` + `shrinkResources` per `Config.enableCodeShrink`. |
| `preview` | `.debug` | `initWith(release)` but debug-signed; uses `src/debug/res`. |
| `benchmark` | `.benchmark` | Non-debuggable, profileable; used by `:macrobenchmark` to produce baseline profiles. |

### ABI splits

Per-architecture APKs for `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, plus a
universal APK. Each APK only contains the native libraries for its ABI.

### Native libraries kept (`packaging.jniLibs.keepDebugSymbols`)

`libmpv`, `libavcodec` / `libavdevice` / `libavfilter` / `libavformat` /
`libavutil` / `libpostproc` / `libswresample` / `libswscale` (ffmpeg-kit),
`libffmpegkit*`, `libarchive-jni`, `libconscrypt_jni` (TLS 1.3 on Android < 10),
`libimagedecoder`, `libsqlite3x`, `libtorrserver`, `libxml2`, `libquickjs`,
`libplayer`, `liblibrary`. These come from the aniyomi-specific libraries
(`aniyomilibs.aniyomi.mpv`, `aniyomilibs.ffmpeg.kit`, `aniyomilibs.torrserver`)
and from `:core:archive`.

### Build config fields

```kotlin
buildConfigField("String",  "COMMIT_COUNT",    ...)
buildConfigField("String",  "COMMIT_SHA",      ...)
buildConfigField("String",  "BUILD_TIME",      ...)
buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")
```

Consumed by the About screen and the self-updater (`data/updater/`). The ACRA
crash-report fields (`ACRA_URI`, `ACRA_LOGIN`, `ACRA_PASSWORD`) are scaffolded
but commented out — see [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md).

### Build features

`viewBinding = true`, `buildConfig = true`. AIDL, RenderScript, and shaders are
explicitly disabled. The `shortcut-helper` plugin generates launcher shortcuts
from `app/shortcuts.xml`.

### Notable dependencies

- **Compose** (foundation, material3, icons, animation, animation.graphics).
- **Voyager 1.0.1** (`libs.bundles.voyager`) — navigation; see
  [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md).
- **Coil 3** + `subsamplingscaleimageview` + `image-decoder` — image loading.
- **Injekt** — DI.
- **OkHttp 5.0.0-alpha.14** + Conscrypt + Okio.
- **SQLDelight 2.0.2** (via `:data`).
- **WorkManager** — background jobs (library updates, backups, downloads).
- **Shizuku** (`libs.bundles.shizuku`) — elevated extension install/uninstall.
- **aniyomi-mpv-lib** + **ffmpeg-kit** + **Torrserver** + **seeker** +
  **true-type-parser** — anime player stack.
- **`rikka.shizuku.ShizukuProvider`** is registered in the manifest.

## Package layout

The `:app` module's source root is `app/src/main/java/`. The main package is
`eu.kanade.tachiyomi`, with supporting roots `eu.kanade.presentation` (Compose
UI shared between screens), `mihon.*` (Mihon-lineage features), `aniyomi.*`
(Aniyomi-only utilities), and `tachiyomi.*` (legacy helpers that mirror the
package names of the library modules).

### Top-level packages under `eu.kanade.tachiyomi/`

| Package | What it holds |
|---|---|
| `(root)` | `App.kt` (the `Application`), `AppInfo.kt` (extension-facing version info). |
| `crash/` | `CrashActivity`, `GlobalExceptionHandler` — uncaught-exception handling; see [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md). |
| `data/` | App-level data layer: backup, cache, coil, database, download, export, library, notification, preference, saver, torrent, track, updater. (Detailed below.) |
| `di/` | Injekt modules — `AppModule`, `PreferenceModule`. (Detailed below.) |
| `extension/` | External-APK extension loader. Split into `manga/` and `anime/` halves. |
| `network/` | `NetworkHelper` (OkHttp client), `NetworkPreferences`, `JavaScriptEngine` (QuickJS for extensions). |
| `source/` | `AndroidMangaSourceManager` / `AndroidAnimeSourceManager` — runtime source registry. |
| `ui/` | All screens, activities, view models, viewer/player code. (Detailed below.) |
| `util/` | App-only extension functions: `system/`, `view/`, `lang/`, `storage/`, `chapter/`, `episode/`, plus `CrashLogUtil`, `StorageUtil`, `AniChartApi`, `PkceUtil`. |
| `widget/` | Legacy Views widgets still used by the reader/player: `TachiyomiTextInputEditText`, `RevealAnimationView`, `ViewPagerAdapter`, `MinMaxNumberPicker`, and `widget/listener/`. (Not to be confused with `:presentation-widget` — that's the home-screen widget module.) |

### Supporting package roots

| Root | Holds |
|---|---|
| `eu.kanade.presentation.*` | Compose components & screens shared across features (`components/`, `more/`, `entries/`, `library/`, `track/`, `util/`, `crash/`). The migration from View-based UI to Compose is in progress; see [`../06-ui/compose-migration.md`](../06-ui/compose-migration.md). |
| `mihon.core.migration/` | The `Migrator` and `migrations/` — runs one-shot preference/data migrations on app version bumps. |
| `mihon.core.designsystem.utils/` | `WindowSize.kt` — window-size helper (single file; the bulk of the design system lives in `:presentation-core`). |
| `mihon.feature.upcoming/` | "Upcoming" calendar screen — manga and anime variants. |
| `aniyomi.util/` | `DataSaver.kt` — Aniyomi-only data-saver helper. |
| `tachiyomi.*` | Legacy package-name bridges into `:core:common`, `:domain`, etc. — mostly empty in `:app`, present for source compatibility. |

## The `App` class

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt`

```kotlin
class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory
```

`App` is the composition root. On `onCreate()` it performs, in order:

1. **`patchInjekt()`** — apply Injekt compatibility patches.
2. **`GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)`**
   — install the uncaught-exception handler that funnels crashes to
   `CrashActivity` (see [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md)).
3. **Conscrypt** — for Android < 10, insert the Conscrypt provider at position 1
   so OkHttp can negotiate TLS 1.3.
4. **WebView data-dir suffix** — on Android Pie+, give each process its own
   WebView data directory to avoid crashes in multi-process scenarios.
5. **Injekt module import** — the heart of DI:
   ```kotlin
   Injekt.importModule(PreferenceModule(this))
   Injekt.importModule(AppModule(this))
   Injekt.importModule(DomainModule())
   Injekt.importModule(SYDomainModule())   // Aniyomi SY-extension domain module
   ```
6. **`setupNotificationChannels()`** — `Notifications.createChannels(this)`.
7. **`ProcessLifecycleOwner` observation** — `App` registers itself so it can
   invoke `SecureActivityDelegate.onApplicationStart()` / `onApplicationStopped()`
   for the app-lock feature.
8. **Incognito-mode notification** — observes `basePreferences.incognitoMode()`
   and posts/cancels an ongoing notification; a `DisableIncognitoReceiver`
   (inner class) lets the user tap to disable incognito mode.
9. **Hardware bitmap threshold** — seeds `ImageUtil.hardwareBitmapThreshold` from
   `GLUtil.DEVICE_TEXTURE_LIMIT` if unset.
10. **Theme mode** — applies `UiPreferences.themeMode()` to the AppCompat
    delegate so the splash and activities match.
11. **Widget init** — instantiates `MangaWidgetManager` and
    `AnimeWidgetManager` (from `:presentation-widget`) and starts observing
    updates so the home-screen widgets refresh (see
    [`presentation-widget.md`](presentation-widget.md)).
12. **Verbose logcat** — installs `AndroidLogcatLogger` if
    `networkPreferences.verboseLogging()` is on.
13. **`initializeMigrator()`** — compares the persisted `last_version_code`
    preference to `BuildConfig.VERSION_CODE` and runs the `migrations` list
    from `mihon.core.migration.migrations`.

`App` also implements `SingletonImageLoader.Factory` — `newImageLoader()`
builds the global Coil 3 `ImageLoader` with the OkHttp client from
`NetworkHelper`, the custom `TachiyomiImageDecoder`, the `MangaCoverFetcher`
and `AnimeImageFetcher` (so cover URLs from `data/coil/` work), the
`MangaKeyer`/`AnimeKeyer`/`MangaCoverKeyer`/`AnimeCoverKeyer` cache keyers,
crossfade scaled by `animatorDurationScale`, RGB565 on low-RAM devices, and
limited-parallelism dispatchers (8 fetchers, 3 decoders).

Finally, `App` overrides `getPackageName()` to spoof the package name when
called from Chromium's `BuildInfo` — this rewrites the `X-Requested-With`
header WebView sends.

## The `di/` package

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/`

Two `InjektModule` classes register everything the app needs at runtime. They
are imported by `App.onCreate()` (see above). For the general DI pattern see
[`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md).

### `PreferenceModule`

`PreferenceModule.kt` registers the `PreferenceStore` and **every** preference
holder used across the app:

`AndroidPreferenceStore`, `NetworkPreferences`, `SourcePreferences`,
`SecurityPreferences`, `LibraryPreferences`, `ReaderPreferences`,
`PlayerPreferences`, `GesturePreferences`, `DecoderPreferences`,
`SubtitlePreferences`, `AudioPreferences`, `TorrentPreferences`,
`AdvancedPlayerPreferences`, `TrackPreferences`, `DownloadPreferences`,
`BackupPreferences`, `StoragePreferences`, `UiPreferences`, `BasePreferences`.

### `AppModule`

`AppModule.kt` is the big registry. Notably it:

- Builds **both** SQLDelight drivers and databases (the dual manga/anime
  pattern, see [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)):
  - `tachiyomi.db` ← `Database` (manga schema, `:data`).
  - `tachiyomi.animedb` ← `AnimeDatabase` (anime schema, `:data`).
  - Both opened with WAL, foreign keys, synchronous=NORMAL; uses
    `FrameworkSQLiteOpenHelperFactory` on debug+R+ for the DB inspector,
    `RequerySQLiteOpenHelperFactory` otherwise.
  - Exposes them via `MangaDatabaseHandler` / `AnimeDatabaseHandler`.
- Registers `Json`, `XML`, `ProtoBuf` singletons for serialization.
- Registers caches: `ChapterCache`, `MangaCoverCache`, `AnimeCoverCache`,
  `AnimeBackgroundCache`.
- Registers `NetworkHelper`, `JavaScriptEngine`.
- Registers source managers: `MangaSourceManager` → `AndroidMangaSourceManager`,
  `AnimeSourceManager` → `AndroidAnimeSourceManager`.
- Registers extension managers: `MangaExtensionManager`,
  `AnimeExtensionManager`.
- Registers the dual download stack: `MangaDownloadProvider` /
  `MangaDownloadManager` / `MangaDownloadCache` and the anime triplet.
- Registers `TrackerManager`, `DelayedMangaTrackingStore`,
  `DelayedAnimeTrackingStore`.
- Registers `ImageSaver`, `AndroidStorageFolderProvider`, local-source
  filesystems & cover/background managers (manga + anime), `StorageManager`,
  `ExternalIntents`, `TorrentServerApi`, `TorrentServerUtils`.
- **Async warm-up**: at the end of `registerInjectables()` it kicks the main
  executor to instantiate `NetworkHelper`, both source managers, both
  databases, and both download managers — so the cold start happens off the
  critical path.

## The `ui/` package tree

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/`

Aniyomi uses Voyager 1.0.1 for navigation; the home screen hosts tabs (Library,
Updates, History, Browse, More, Stats, Storage, Downloads, Categories). The UI
is mid-migration from Views to Compose — older screens still use RecyclerView
adapters (`*Adapter`, `*Holder`, `*Item`), newer ones use Compose `*Screen` +
`*ScreenModel`.

| Sub-package | Holds | See also |
|---|---|---|
| `base/` | `BaseActivity`, `base/delegate/` (`SecureActivityDelegate` for app-lock, `ThemingDelegate`). | [`../06-ui/screens.md`](../06-ui/screens.md) |
| `browse/` | Browse tab + source/extension lists, global search, migration. Split into `manga/` and `anime/` with `source/`, `extension/`, `migration/`. | [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md), [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| `category/` | Categories tab — `manga/` + `anime/` `*Tab`, `*ScreenModel`. | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| `deeplink/` | `DeepLinkMangaActivity` / `DeepLinkAnimeActivity` — global-search entry points (manifest-registered). | [`../05-key-flows/browse-catalog.md`](../05-key-flows/browse-catalog.md) |
| `download/` | Download queue UI — `manga/` + `anime/` `*Tab`, `*Screen`, `*ScreenModel`, `*Adapter`, `*Holder`, `*Item`. | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| `entries/` | Per-entry screens — `manga/MangaScreen*`, `anime/AnimeScreen*`, `track/` (track info dialogs). | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md), [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| `history/` | History tab — `manga/` + `anime/` `*Tab`, `*ScreenModel`, plus `HistoriesTab` aggregator. | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| `home/` | `HomeScreen.kt` — root Voyager screen that hosts the bottom-nav tabs. | [`../05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) |
| `library/` | Library tab — `manga/` + `anime/` `*Tab`, `*ScreenModel`, `*Item`, `*SettingsScreenModel`. | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| `main/` | `MainActivity.kt` — the launcher activity, Voyager `Navigator` host, splash, intent dispatch, app-state banners. | [`../05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) |
| `more/` | `MoreTab`, `OnboardingScreen`, `NewUpdateScreen`. | — |
| `player/` | The anime player. `PlayerActivity`, `PlayerViewModel`, `AniyomiMPVView`, `PlayerObserver`, `loader/` (`EpisodeLoader`, `HosterLoader`), `settings/` (`PlayerPreferences`, `DecoderPreferences`, `AudioPreferences`, `SubtitlePreferences`, `GesturePreferences`, `AdvancedPlayerPreferences`), `controls/` (top/middle/bottom-left/right control rows, `GestureHandler`, `PlayerDialogs`, `PlayerSheets`, `PlayerPanels`, `components/` with `sheets/`, `dialogs/`, `panels/`), `utils/` (`AniSkipApi`, `ChapterUtils`, `TrackSelect`). Plus `ExternalIntents`, `PipActions`, `PlayerEnums`, `PlayerUtils`. | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| `reader/` | The manga reader. `ReaderActivity`, `ReaderViewModel`, `loader/` (`PageLoader` + `HttpPageLoader`, `DownloadPageLoader`, `DirectoryPageLoader`, `EpubPageLoader`, `ArchivePageLoader`, `ChapterLoader`), `viewer/` (`Viewer`, `ViewerConfig`, `ViewerNavigation` + 5 navigation modes in `navigation/`, plus `pager/` (PagerViewer, PagerConfig, PagerViewerAdapter, PagerPageHolder, PagerTransitionHolder, Pager, PagerViewers) and `webtoon/` (WebtoonViewer + 10 supporting classes)), `model/` (`ReaderPage`, `ReaderChapter`, `ChapterTransition`, `InsertPage`, `ViewerChapters`), `setting/` (`ReaderPreferences`, `ReaderSettingsScreenModel`, `ReadingMode`, `ReaderOrientation`). Plus `SaveImageNotifier`, `ReaderNavigationOverlayView`. | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| `security/` | `UnlockActivity` — biometric/app-lock unlock screen. | [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |
| `setting/` | `SettingsScreen`, `PlayerSettingsScreen`, `setting/track/` (`BaseOAuthLoginActivity`, `TrackLoginActivity` — the OAuth callback activity). | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| `stats/` | Stats tab — `StatsTab` + `manga/` + `anime/` `*Tab`, `*ScreenModel`. | — |
| `storage/` | Storage tab — `StorageTab`, `CommonStorageScreenModel`, `manga/` + `anime/` `*Tab`, `*ScreenModel`. | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| `updates/` | Updates tab — `UpdatesTab` + `manga/` + `anime/` `*Tab`, `*ScreenModel`. | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| `webview/` | `WebViewActivity`, `WebViewScreen`, `WebViewScreenModel` — in-app browser used by sources and trackers. | — |

## The `data/` package

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/`

App-level data infrastructure (the SQLDelight schema itself lives in `:data`;
this package holds the *Android-side* managers, caches, notifiers, and jobs).

| Sub-package | Holds | See also |
|---|---|---|
| `backup/` | Backup engine: `BackupCreator`, `BackupCreateJob`, `BackupOptions`, `BackupDecoder`, `BackupDetector`, `BackupFileValidator`, `BackupNotifier`, `restore/` (`BackupRestorer`, `BackupRestoreJob`, `RestoreOptions`, `restorers/` for manga+anime+categories+extensions+repos+preferences+custom-buttons), `models/` (protobuf backup DTOs — `Backup`, `BackupManga`, `BackupAnime`, `BackupChapter`, `BackupEpisode`, `BackupCategory`, `BackupHistory`, `BackupTracking`, `BackupAnimeTracking`, `BackupSource`, `BackupAnimeSource`, `BackupExtension`, `BackupExtensionRepos`, `BackupExtensionPreferences`, `BackupPreference`, `BackupCustomButtons`), `full/models/`. | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| `cache/` | `MangaCoverCache`, `AnimeCoverCache`, `AnimeBackgroundCache`, `ChapterCache` — disk caches. | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| `coil/` | `MangaCoverFetcher`, `AnimeImageFetcher`, `MangaCoverKeyer`, `AnimeCoverKeyer`, `MangaKeyer`, `AnimeKeyer`, `TachiyomiImageDecoder`, `BufferedSourceFetcher`, `Utils`. Registered with Coil by `App.newImageLoader()`. | — |
| `database/models/` | Legacy DB model interfaces still referenced by app code: `manga/` (`Chapter`, `ChapterImpl`, `MangaTrack`, `MangaTrackImpl`), `anime/` (`Episode`, `EpisodeImpl`, `AnimeTrack`, `AnimeTrackImpl`). The new schema lives in `:data`. | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| `download/` | The dual download stack: `manga/` (`MangaDownloadManager`, `MangaDownloader`, `MangaDownloadProvider`, `MangaDownloadCache`, `MangaDownloadStore`, `MangaDownloadJob`, `MangaDownloadNotifier`, `MangaDownloadPendingDeleter`, `model/MangaDownload`) and the parallel `anime/`. | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| `export/` | `LibraryExporter` — exports the library for external use. | — |
| `library/` | Library update jobs + notifiers: `manga/` (`MangaLibraryUpdateJob`, `MangaLibraryUpdateNotifier`, `MangaMetadataUpdateJob`) and `anime/`. | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| `notification/` | `Notifications` (channel + ID constants), `NotificationReceiver`, `NotificationHandler`. | [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) |
| `preference/` | `SharedPreferencesDataStore` — bridges `SharedPreferences` to the `PreferenceStore`. | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| `saver/` | `ImageSaver` — saves reader/player images to SAF. | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| `torrent/` | `service/TorrentServerService` — foreground service hosting the bundled Torrserver. | [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) |
| `track/` | Tracker implementations: `Tracker`, `BaseTracker`, `MangaTracker`, `AnimeTracker`, `EnhancedMangaTracker`, `EnhancedAnimeTracker`, `DeletableMangaTracker`, `DeletableAnimeTracker`, `TrackerManager`, `model/` (`MangaTrackSearch`, `AnimeTrackSearch`), and per-service packages: `myanimelist/`, `anilist/`, `shikimori/`, `bangumi/`, `kitsu/`, `simkl/`, `mangaupdates/`, `komga/`, `kavita/`, `jellyfin/`, `suwayomi/` (each with its `*Api`, `*Interceptor`, `*Utils`, and `dto/`). | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| `updater/` | App self-updater: `AppUpdateChecker`, `AppUpdateDownloadJob`, `AppUpdateNotifier`. | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |

## The `source/` package

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/`

Holds the **runtime source managers** (the source contract lives in
`:source-api`; see [`source-api.md`](source-api.md)). The dual manga/anime
pattern is explicit:

| File | Role |
|---|---|
| `manga/AndroidMangaSourceManager.kt` | Implements `MangaSourceManager`. Holds a `ConcurrentHashMap<Long, MangaSource>` keyed by source ID. Seeds `LocalMangaSource` (id `LocalMangaSource.ID`). Subscribes to `MangaExtensionManager.installedExtensionsFlow` and rebuilds the map as extensions come and go. Maintains `StubMangaSource` entries for uninstalled-but-referenced sources. |
| `manga/MangaSourceExtensions.kt` | Helpers on `MangaSource` (e.g. fetching manga/chapter list). |
| `anime/AndroidAnimeSourceManager.kt` | Mirror of the above for `AnimeSource` / `LocalAnimeSource`. |
| `anime/AnimeSourceExtensions.kt` | Mirror helpers for anime. |

These are wired in `AppModule`:

```kotlin
addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }
```

See [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) for
the full source/extension lifecycle.

## The `extension/` package

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/`

External-APK extension loader. The brief model (`MangaExtensionManager` and
`AnimeExtensionManager`) is the public face; the `util/` and `installer/`
sub-packages do the heavy lifting.

```
extension/
├── InstallStep.kt                  ← enum: Pending, Downloading, Installing, Installed, Error
├── ExtensionUpdateNotifier.kt      ← notifications for available extension updates
├── manga/
│   ├── MangaExtensionManager.kt    ← registry of installed manga extensions
│   ├── api/MangaExtensionApi.kt    ← fetches the available-extensions JSON from the repo
│   ├── model/MangaExtension.kt     ← MangaExtension.{Installed, Available, Untrusted} models
│   ├── model/MangaLoadResult.kt
│   ├── installer/InstallerManga.kt          ← installer interface
│   ├── installer/PackageInstallerInstallerManga.kt  ← Android PackageInstaller path
│   ├── installer/ShizukuInstallerManga.kt   ← Shizuku elevated path
│   └── util/
│       ├── MangaExtensionLoader.kt          ← DexClassLoader + signature-trust check
│       ├── MangaExtensionInstaller.kt       ← picks installer, drives lifecycle
│       ├── MangaExtensionInstallService.kt  ← foreground shortService for installs
│       ├── MangaExtensionInstallActivity.kt ← translucent activity for user prompts
│       └── MangaExtensionInstallReceiver.kt ← system install/uninstall broadcasts
└── anime/   ← mirror of the above (AnimeExtensionManager, api/, model/, installer/, util/)
```

Key behaviours:

- **Signature trust**: extensions must be signed; untrusted extensions are
  loaded only after the user accepts them (see `TrustMangaExtension` /
  `TrustAnimeExtension` interactors in `:domain`).
- **Install strategies**: tries `PackageInstallerInstaller*` first, falls back
  to `ShizukuInstaller*` if Shizuku is running.
- **`ChildFirstPathClassLoader`** (in `util/system/`) is used to load extension
  dexes with a child-first classloading strategy so extensions can ship their
  own dependency versions.

See [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md)
and [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md).

## The `crash/` package

`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/crash/`

| File | Role |
|---|---|
| `GlobalExceptionHandler.kt` | Installs itself as the `Thread.setDefaultUncaughtExceptionHandler`. On an uncaught exception it serializes the throwable's stack trace to JSON, launches `CrashActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, then delegates to the previous handler. |
| `CrashActivity.kt` | A `BaseActivity` that renders `CrashScreen` (a Compose composable from `eu.kanade.presentation.crash`) showing the exception, with "Restart" and "Copy" actions. Runs in its own `:error_handler` process so a crash in the main process can still show it. |

Wired in `App.onCreate()` via
`GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)`.
See [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md).

## Manifest highlights

`../ANIYOMI/app/src/main/AndroidManifest.xml`

### Permissions

- **Network**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`.
- **Storage**: `MANAGE_EXTERNAL_STORAGE` (legacy; for SAF fallback paths).
- **Background**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Extension management**: `REQUEST_INSTALL_PACKAGES`,
  `REQUEST_DELETE_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`,
  `QUERY_ALL_PACKAGES` (so extensions can be enumerated on Android 11+).
- **UI**: `POST_NOTIFICATIONS` (Android 13+), `READ_APP_SPECIFIC_LOCALES`.

### `<application>` flags

`allowBackup="false"`, `enableOnBackInvokedCallback="true"`,
`hardwareAccelerated="true"`, `largeHeap="true"`,
`preserveLegacyExternalStorage="true"`, `requestLegacyExternalStorage="true"`,
`supportsRtl="true"`, `localeConfig="@xml/locales_config"`,
`networkSecurityConfig="@xml/network_security_config"`,
`theme="@style/Theme.Tachiyomi"`. The application class is `.App`.

### Activities

| Activity | Notes |
|---|---|
| `.ui.main.MainActivity` | Launcher (LAUNCHER intent-filter). Handles deep links for `tachiyomi://add-repo` and `aniyomi://add-repo`, opens `.tachibk` backup files. Splash theme. Has the `android.app.shortcuts` metadata. |
| `.crash.CrashActivity` | Runs in `:error_handler` process. Not exported. |
| `.ui.deeplink.anime.DeepLinkAnimeActivity` | Global anime search entry. NoDisplay theme. Handles `android.intent.action.SEARCH`, `eu.kanade.tachiyomi.ANIMESEARCH`, and `SEND` of `text/plain`. |
| `.ui.deeplink.manga.DeepLinkMangaActivity` | Mirror for manga (`SEARCH`, `ANIMESEARCH`, `SEND`). |
| `.ui.reader.ReaderActivity` | `singleTask`. Registers Samsung S-Pen remote actions (`@xml/s_pen_actions`). |
| `.ui.player.PlayerActivity` | `singleTask`, supports Picture-in-Picture, `autoRemoveFromRecents`, handles orientation/screenSize config changes. S-Pen actions. |
| `.ui.security.UnlockActivity` | App-lock unlock screen. |
| `.ui.webview.WebViewActivity` | In-app browser. |
| `.extension.manga.util.MangaExtensionInstallActivity` | Translucent activity for extension install prompts. |
| `.extension.anime.util.AnimeExtensionInstallActivity` | Mirror for anime. |
| `.ui.setting.track.TrackLoginActivity` | OAuth callback. Registers `aniyomi://` scheme for hosts `myanimelist-auth`, `anilist-auth`, `bangumi-auth`, `shikimori-auth`, `simkl-auth`. |

### Receivers, services, providers

- **Receiver**: `.data.notification.NotificationReceiver` (notification action
  callbacks).
- **Services**:
  - `.extension.manga.util.MangaExtensionInstallService` (`shortService`).
  - `.extension.anime.util.AnimeExtensionInstallService` (`shortService`).
  - `androidx.appcompat.app.AppLocalesMetadataHolderService` (locale storage).
  - `androidx.work.impl.foreground.SystemForegroundService` (merged;
    `dataSync` foreground type — used by WorkManager jobs for library updates,
    backups, downloads).
  - `.data.torrent.service.TorrentServerService` (`dataSync` — the Torrserver
    host).
- **Providers**:
  - `androidx.core.content.FileProvider` (authority `${applicationId}.provider`).
  - `rikka.shizuku.ShizukuProvider` (authority `${applicationId}.shizuku`,
    exported, requires `INTERACT_ACROSS_USERS_FULL`).
- **Meta-data**: `WebView.EnableSafeBrowsing=false`,
  `WebView.MetricsOptOut=true`.

## Key files table

| File | Why it matters |
|---|---|
| `../ANIYOMI/app/build.gradle.kts` | Build types, ABI splits, native-lib keep list, all dependencies. |
| `../ANIYOMI/app/src/main/AndroidManifest.xml` | Activities, services, receivers, providers, permissions. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` | The `Application` class — composition root, Injekt import, Coil setup, migrator, widget init. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/AppInfo.kt` | Extension-facing version info (consumed by external APKs). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` | Big Injekt module: dual SQLDelight DBs, source managers, extension managers, download stack, trackers, caches. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt` | Injekt module for `PreferenceStore` and all preference holders. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` | Launcher activity — Voyager `Navigator` host, splash, intent dispatch, app-state banners. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt` | Root Voyager screen hosting the bottom-nav tabs. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | Manga reader entry activity. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` | Reader state machine. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` | Anime player entry activity (PiP, MPV host). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` | Player state machine. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt` | The MPV `View` subclass that drives playback. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/manga/MangaExtensionManager.kt` | Manga extension registry. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/anime/AnimeExtensionManager.kt` | Anime extension registry. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt` | Manga source registry (ext ↔ source mapping). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt` | Anime source registry. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt` | Manga download queue + worker. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt` | Anime download queue + worker. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt` | Backup serialization engine. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` | Backup deserialization + restoration engine. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/TrackerManager.kt` | Tracker registry. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt` | Notification channel + ID constants. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/torrent/service/TorrentServerService.kt` | Torrserver foreground service. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/crash/GlobalExceptionHandler.kt` | Installs the uncaught-exception handler. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/crash/CrashActivity.kt` | Crash UI. |
| `../ANIYOMI/app/src/main/java/mihon/core/migration/Migrator.kt` | Runs version-bump migrations. |
| `../ANIYOMI/app/src/main/java/mihon/core/migration/migrations/Migrations.kt` | The ordered list of migrations. |

## See also

- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — module roster.
- [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) — Gradle/build details.
- [`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md) — the dual manga/anime pattern.
- [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md) — Injekt wiring.
- [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — Voyager.
- [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) — crash handling.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) — sources & extensions.
- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) and
  [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — reader/player internals.
- [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md),
  [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md),
  [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md),
  [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md).
- [`presentation-core.md`](presentation-core.md),
  [`presentation-widget.md`](presentation-widget.md),
  [`data.md`](data.md),
  [`domain.md`](domain.md),
  [`source-api.md`](source-api.md).
