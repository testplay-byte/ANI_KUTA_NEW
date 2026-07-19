# 07-reference / file-index — Key file locator

> "Where do I find X?" — a task/question → file(s) table. All paths are
> relative to `../ANIYOMI/` (the source-tree root). Braces `{manga,anime}`
> indicate the dual pattern: two near-identical files exist, one per side.
> Grouped by category. Use `Ctrl-F` to jump.

## How to read this table

| Column | Meaning |
|---|---|
| **Task / question** | What you're trying to do or find. |
| **File(s)** | Where it lives. Paths are clickable relative links into the source tree. |
| **Doc** | The documentation file that explains it in depth (relative link). |

When a single concept has a manga and an anime twin, both are listed. When
only one side exists (e.g. the anime player has no manga counterpart), only
the relevant side is listed.

---

## 1. Build / Config

| Task / question | File(s) | Doc |
|---|---|---|
| Root `settings.gradle.kts` (module declarations) | `../ANIYOMI/settings.gradle.kts` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| Root `build.gradle.kts` | `../ANIYOMI/build.gradle.kts` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| Gradle properties | `../ANIYOMI/gradle.properties` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| Version catalogs | `../ANIYOMI/gradle/libs.versions.toml`, `aniyomi.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `kotlinx.versions.toml` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| Custom build plugins (convention plugins) | `../ANIYOMI/buildSrc/src/main/kotlin/*.gradle.kts`, `mihon/buildlogic/*.kt` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| `AndroidConfig` (SDK levels, app ID, version) | `../ANIYOMI/buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| `BuildConfig` flags (`enableUpdater`, `enableCodeShrink`, …) | `../ANIYOMI/buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| `:app` build script (build types, ABI splits, deps) | `../ANIYOMI/app/build.gradle.kts` | [`../02-modules/app.md`](../02-modules/app.md) |
| AndroidManifest | `../ANIYOMI/app/src/main/AndroidManifest.xml` | [`../02-modules/app.md`](../02-modules/app.md) |
| ProGuard rules | `../ANIYOMI/app/proguard-rules.pro`, `proguard-android-optimize.txt` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |
| Launcher shortcuts | `../ANIYOMI/app/shortcuts.xml` | [`../02-modules/app.md`](../02-modules/app.md) |
| Baseline profile (shipped) | `../ANIYOMI/app/src/main/baseline-prof.txt` | [`../02-modules/macrobenchmark.md`](../02-modules/macrobenchmark.md) |
| Baseline profile generator | `../ANIYOMI/macrobenchmark/src/main/java/tachiyomi/macrobenchmark/BaselineProfileGenerator.kt` | [`../02-modules/macrobenchmark.md`](../02-modules/macrobenchmark.md) |
| Startup benchmark | `../ANIYOMI/macrobenchmark/src/main/java/tachiyomi/macrobenchmark/StartupBenchmark.kt` | [`../02-modules/macrobenchmark.md`](../02-modules/macrobenchmark.md) |
| Gradle wrapper | `../ANIYOMI/gradle/wrapper/gradle-wrapper.properties` | [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) |

## 2. App core (Application, DI, lifecycle)

| Task / question | File(s) | Doc |
|---|---|---|
| Application class (composition root, Injekt import, Coil, migrator) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` | [`../02-modules/app.md`](../02-modules/app.md) |
| Extension-facing version info | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/AppInfo.kt` | [`../02-modules/app.md`](../02-modules/app.md) |
| DI composition root (AppModule) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` | [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md) |
| DI preferences module | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt` | [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md) |
| Main activity (launcher, Voyager Navigator host, splash) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` | [`../05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) |
| Root Voyager screen (bottom-nav tabs) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt` | [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) |
| Base activity + theming delegate | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/base/` | [`../06-ui/screens.md`](../06-ui/screens.md) |
| App-lock (SecureActivityDelegate) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/base/delegate/SecureActivityDelegate.kt` | [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |
| Crash handler | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/crash/GlobalExceptionHandler.kt`, `CrashActivity.kt` | [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |
| App version-bump migrations (Migrator + list) | `../ANIYOMI/app/src/main/java/mihon/core/migration/Migrator.kt`, `migrations/Migrations.kt` | [`../02-modules/app.md`](../02-modules/app.md) |
| Network helper (OkHttp client, interceptors) | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt` | [`../02-modules/core-common.md`](../02-modules/core-common.md) |
| Network preferences (DOH, verbose log, user agent) | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkPreferences.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| JavaScript engine (QuickJS for extensions) | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/JavaScriptEngine.kt` | [`../02-modules/core-common.md`](../02-modules/core-common.md) |
| Cloudflare interceptor | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Preference store (Android impl) | `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| Preference store (interface) | `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/PreferenceStore.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| Shared-prefs ↔ DataStore bridge | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/preference/SharedPreferencesDataStore.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |

## 3. UI (Compose / Voyager screens)

| Task / question | File(s) | Doc |
|---|---|---|
| Library tab (manga + anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/library/{manga,anime}/` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Library screen models | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/library/{manga,anime}/{Manga,Anime}LibraryScreenModel.kt` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Library settings dialog | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/library/{manga,anime}/{Manga,Anime}LibrarySettingsDialog.kt` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Updates tab | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/updates/{manga,anime}/` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| History tab | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/history/{manga,anime}/` | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| Browse tab (sources + extensions + migration) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/{manga,anime}/` | [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Global search screen | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/{manga,anime}/source/globalsearch/Global{Manga,Anime}SearchScreen.kt` | [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Browse-source screen (paging) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/{manga,anime}/source/browse/Browse{Manga,Anime}SourceScreen.kt` | [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Per-entry screen (manga + anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/{manga,anime}/{Manga,Anime}Screen.kt` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Track info dialog | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/{manga,anime}/track/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Categories tab | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/category/{manga,anime}/` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Downloads queue UI | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/download/{manga,anime}/` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Storage tab | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/storage/{manga,anime}/` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Stats tab | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/stats/{manga,anime}/` | [`../06-ui/screens.md`](../06-ui/screens.md) |
| More tab + onboarding | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/more/` | [`../06-ui/screens.md`](../06-ui/screens.md) |
| Settings root | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsScreen.kt` | [`../06-ui/screens.md`](../06-ui/screens.md) |
| Settings screens (per-section) | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/` | [`../06-ui/screens.md`](../06-ui/screens.md) |
| Reader settings screen | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Player settings screens | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Extension-repos settings screen | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/{Manga,Anime}ExtensionReposScreen.kt` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| WebView activity (in-app browser) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Deep-link entry points (global search) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/{manga,anime}/DeepLink{Manga,Anime}Activity.kt` | [`../05-key-flows/browse-catalog.md`](../05-key-flows/browse-catalog.md) |
| Unlock activity (app-lock) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/security/UnlockActivity.kt` | [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |
| Shared Compose theme | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt` | [`../06-ui/theme-design.md`](../06-ui/theme-design.md) |
| Color schemes | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/colorscheme/` | [`../06-ui/theme-design.md`](../06-ui/theme-design.md) |
| Compose primitives (shared) | `../ANIYOMI/presentation-core/src/main/java/` | [`../02-modules/presentation-core.md`](../02-modules/presentation-core.md) |
| Home-screen widgets | `../ANIYOMI/presentation-widget/src/main/java/` | [`../02-modules/presentation-widget.md`](../02-modules/presentation-widget.md) |

## 4. Reader & Player

| Task / question | File(s) | Doc |
|---|---|---|
| Manga reader activity | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Manga reader view model | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Chapter loader (dispatches to a PageLoader) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Page loaders (HTTP, downloaded, directory, archive, EPUB) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/{Http,Download,Directory,Archive,Epub}PageLoader.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Reader viewers (paged + webtoon) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Reader preferences | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Reader settings screen model | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsScreenModel.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Reader navigation overlay (tap zones) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Save-image notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/SaveImageNotifier.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |
| Anime player activity (PiP, MPV host) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Anime player view model | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| MPV view wrapper | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| MPV observer (callbacks bridge) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Episode loader | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Hoster loader (resolves a `Hoster` → `List<Video>`) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Player controls (Compose overlays) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Player preferences | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/{Player,Gesture,Decoder,Audio,Subtitle,AdvancedPlayer}Preferences.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| AniSkip API (intro skip) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| PiP actions | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PipActions.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| External intents (hand-off to 1DM/ADM) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Image saver (reader/player → SAF) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/saver/ImageSaver.kt` | [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |

## 5. Sources & Extensions

| Task / question | File(s) | Doc |
|---|---|---|
| Source contract (manga) | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| Source contract (anime) | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| `MangaSource` / `CatalogueSource` / `HttpSource` / `ParsedHttpSource` / `SourceFactory` | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/{MangaSource,CatalogueSource}.kt`, `online/{Http,Parsed}HttpSource.kt`, `SourceFactory.kt` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| `AnimeSource` / `AnimeCatalogueSource` / `AnimeHttpSource` / `AnimeSourceFactory` | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/{AnimeSource,AnimeCatalogueSource}.kt`, `online/{Anime,ParsedAnime}HttpSource.kt`, `AnimeSourceFactory.kt` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| `SManga` / `SChapter` / `Page` / `MangasPage` / `Filter` | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| `SAnime` / `SEpisode` / `Video` / `Hoster` / `AnimesPage` / `AnimeFilter` | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/` | [`../02-modules/source-api.md`](../02-modules/source-api.md) |
| Torrent utils (model + helper, anime side) | `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/` | [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) |
| Source manager (manga runtime registry) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Source manager (anime runtime registry) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension loader (manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionLoader.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension loader (anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/anime/util/AnimeExtensionLoader.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension manager (manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/manga/MangaExtensionManager.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension manager (anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/anime/AnimeExtensionManager.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension installer backends | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/installer/{Installer{Manga,Anime},PackageInstallerInstaller{Manga,Anime},ShizukuInstaller{Manga,Anime}}.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension install service (foreground) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/util/{Manga,Anime}ExtensionInstallService.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension install activity (UI prompt) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/util/{Manga,Anime}ExtensionInstallActivity.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension install receiver (system broadcasts) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/util/{Manga,Anime}ExtensionInstallReceiver.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension API (fetches index.min.json) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/api/{Manga,Anime}ExtensionApi.kt` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Extension models | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/model/{Manga,Anime}Extension.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension install state enum | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/InstallStep.kt` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Extension-update notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionUpdateNotifier.kt` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Source preferences (trusted exts, lang filter) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/{manga,anime}/interactor/SourcePreferences.kt` (and app-side companion) | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Local source (manga) | `../ANIYOMI/source-local/src/androidMain/kotlin/.../LocalMangaSource.kt` | [`../02-modules/source-local.md`](../02-modules/source-local.md) |
| Local source (anime) | `../ANIYOMI/source-local/src/androidMain/kotlin/.../LocalAnimeSource.kt` | [`../02-modules/source-local.md`](../02-modules/source-local.md) |

## 6. Data / DB

| Task / question | File(s) | Doc |
|---|---|---|
| Manga DB schema (`.sq`) | `../ANIYOMI/data/src/main/sqldelight/data/*.sq` | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| Anime DB schema (`.sq`) | `../ANIYOMI/data/src/main/sqldelightanime/dataanime/*.sq` | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| Manga DB views | `../ANIYOMI/data/src/main/sqldelight/view/*.sq` (`libraryView`, `historyView`, `updatesView`) | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| Anime DB views | `../ANIYOMI/data/src/main/sqldelightanime/view/*.sq` (8 views incl. `animelibView`, `animeseasonsView`, `animeupdatesView`) | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| Manga DB migrations | `../ANIYOMI/data/src/main/sqldelight/migrations/*.sqm` (1..32) | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| Anime DB migrations | `../ANIYOMI/data/src/main/sqldelightanime/migrations/*.sqm` (113..135) | [`../04-data-models/database-schema.md`](../04-data-models/database-schema.md) |
| `:data` build script (two SQLDelight databases declared) | `../ANIYOMI/data/build.gradle.kts` | [`../02-modules/data.md`](../02-modules/data.md) |
| Database adapter (driver + handler factory) | `../ANIYOMI/data/src/main/java/tachiyomi/data/DatabaseAdapter.kt` | [`../02-modules/data.md`](../02-modules/data.md) |
| Manga DB handler | `../ANIYOMI/data/src/main/java/tachiyomi/data/handlers/manga/{Manga,AndroidManga}DatabaseHandler.kt` | [`../02-modules/data.md`](../02-modules/data.md) |
| Anime DB handler | `../ANIYOMI/data/src/main/java/tachiyomi/data/handlers/anime/{Anime,AndroidAnime}DatabaseHandler.kt` | [`../02-modules/data.md`](../02-modules/data.md) |
| Repository impls (manga) | `../ANIYOMI/data/src/main/java/tachiyomi/data/...` (e.g. `entries/manga/MangaRepositoryImpl.kt`, `items/chapter/ChapterRepositoryImpl.kt`, `track/manga/MangaTrackRepositoryImpl.kt`, `history/manga/MangaHistoryRepositoryImpl.kt`, `category/manga/MangaCategoryRepositoryImpl.kt`, `updates/manga/MangaUpdatesRepositoryImpl.kt`, `source/manga/Manga{Source,StubSource}RepositoryImpl.kt`, `source/manga/MangaSourcePagingSource.kt`) | [`../02-modules/data.md`](../02-modules/data.md) |
| Repository impls (anime) | `../ANIYOMI/data/src/main/java/tachiyomi/data/...` (mirror with `anime`/`Anime`/`Episode`) | [`../02-modules/data.md`](../02-modules/data.md) |
| Extension-repo repository impl (manga) | `../ANIYOMI/data/src/main/java/mihon/data/repository/manga/MangaExtensionRepoRepositoryImpl.kt` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Extension-repo repository impl (anime) | `../ANIYOMI/data/src/main/java/mihon/data/repository/anime/AnimeExtensionRepoRepositoryImpl.kt` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Release service impl (GitHub self-update) | `../ANIYOMI/data/src/main/java/tachiyomi/data/release/ReleaseServiceImpl.kt`, `GithubRelease.kt` | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |
| Custom-button repository impl | `../ANIYOMI/data/src/main/java/tachiyomi/data/custombutton/CustomButtonRepositoryImpl.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Domain models | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/` (`entries/{manga,anime}/model/`, `items/{chapter,episode}/model/`, `track/{manga,anime}/model/`, `category/model/`, `history/{manga,anime}/model/`, `updates/{manga,anime}/model/`, `library/`, `source/`) | [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md) |
| Repository interfaces | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/...` (one `*Repository.kt` per model) | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Interactors (use cases) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/.../interactor/` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Domain preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/` (`LibraryPreferences`, `DownloadPreferences`, `StoragePreferences`, `BackupPreferences`, `TrackPreferences` in their feature packages) | [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md) |
| Pure logic helpers (ChapterRecognition, EpisodeRecognition, sorters, missing-chapters) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/.../service/` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Extension-repo domain layer | `../ANIYOMI/domain/src/main/java/mihon/domain/extensionrepo/` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Upcoming calendar domain layer | `../ANIYOMI/domain/src/main/java/mihon/domain/upcoming/` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Legacy DB model interfaces (still referenced) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/database/models/{manga,anime}/` | [`../02-modules/data.md`](../02-modules/data.md) |

## 7. Downloads

| Task / question | File(s) | Doc |
|---|---|---|
| Manga download manager | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Anime download manager | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Manga downloader (per-page fetcher + CBZ archiver) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloader.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Anime downloader (FFmpeg remux + torrent + external) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download provider (SAF path resolver) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadProvider.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download store (queue persistence) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadStore.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download cache (3-level filesystem mirror, manga only) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadCache.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadNotifier.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download job (WorkManager worker) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadJob.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download pending deleter | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/{Manga,Anime}DownloadPendingDeleter.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download model | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/{manga,anime}/model/{Manga,Anime}Download.kt` | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Download preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/download/` (per side) | [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |

## 8. Trackers

| Task / question | File(s) | Doc |
|---|---|---|
| Tracker interface hierarchy | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/{Tracker,BaseTracker,MangaTracker,AnimeTracker,EnhancedMangaTracker,EnhancedAnimeTracker,DeletableMangaTracker,DeletableAnimeTracker}.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Tracker manager (registry) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/TrackerManager.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| MAL | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/{MyAnimeList,MyAnimeListApi,MyAnimeListInterceptor,MyAnimeListUtils}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| AniList | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/{Anilist,AnilistApi,AnilistInterceptor,AnilistUtils}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Shikimori | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/shikimori/{Shikimori,ShikimoriApi,ShikimoriInterceptor,ShikimoriUtils}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Bangumi | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/bangumi/{Bangumi,BangumiApi,BangumiInterceptor,BangumiUtils}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Kitsu | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/kitsu/{Kitsu,KitsuApi,KitsuInterceptor,KitsuUtils,KitsuDateHelper}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Simkl | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/simkl/{Simkl,SimklApi,SimklInterceptor,SimklUtils}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| MangaUpdates | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/mangaupdates/{MangaUpdates,MangaUpdatesApi,MangaUpdatesInterceptor}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Komga (enhanced, manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/komga/{Komga,KomgaApi,KomgaModels}.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Kavita (enhanced, manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/kavita/{Kavita,KavitaApi,KavitaInterceptor,KavitaModels}.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Suwayomi (enhanced, manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/suwayomi/{Suwayomi,SuwayomiApi,SuwayomiModels}.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Jellyfin (enhanced, anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/jellyfin/{Jellyfin,JellyfinApi,JellyfinInterceptor}.kt`, `dto/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Tracker search-result models | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/model/{Manga,Anime}TrackSearch.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Tracker domain models + repo + interactors | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/track/{manga,anime}/` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Track preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/track/TrackPreferences.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| OAuth login activity | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/setting/track/{BaseOAuthLoginActivity,TrackLoginActivity}.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Delayed tracking store + replay job | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/` (Delayed{Manga,Anime}TrackingStore.kt, Delayed{Manga,Anime}TrackingUpdateJob.kt) | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| App-level track interactors | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/` (`AddMangaTracks`/`AddAnimeTracks`, `TrackChapter`/`TrackEpisode`, `SyncChapterProgressWithTrack`) | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |

## 9. Backup & Restore

| Task / question | File(s) | Doc |
|---|---|---|
| Backup creator (serialise → gzip) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup create job (periodic WorkManager) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreateJob.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup options (what to include) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup per-section creators | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/*.kt` (Manga/Anime/Categories/Sources/Extensions/Repos/Preferences/CustomButton) | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup restore engine | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup restore job | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreJob.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Restore options | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/RestoreOptions.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Per-section restorers | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/*.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup proto DTOs | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/*.kt` (`Backup`, `BackupManga`, `BackupAnime`, `BackupChapter`, `BackupEpisode`, `BackupCategory`, `BackupHistory`, `BackupAnimeHistory`, `BackupTracking`, `BackupAnimeTracking`, `BackupSource`, `BackupAnimeSource`, `BackupExtension`, `BackupExtensionRepos`, `BackupExtensionPreferences`, `BackupPreference`, `BackupCustomButtons`) | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup file validator | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup decoder (gzip + protobuf) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup format detector | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDetector.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupNotifier.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Legacy backup models | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/full/models/` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Backup preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/backup/BackupPreferences.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Create/restore backup UI | `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/data/{CreateBackup,RestoreBackup}Screen.kt` | [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |

## 10. Library update & notifications

| Task / question | File(s) | Doc |
|---|---|---|
| Manga library update job (WorkManager) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateJob.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Anime library update job | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateJob.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Manga library update notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateNotifier.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Anime library update notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateNotifier.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Manga metadata update job (cover refresh) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaMetadataUpdateJob.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Anime metadata update job | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeMetadataUpdateJob.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Library preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/LibraryPreferences.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Notification channel + ID constants | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt` | [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) |
| Notification receiver (action callbacks) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt` | [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) |
| Notification handler (PendingIntent routing) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationHandler.kt` | [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) |
| Incognito-mode disable receiver | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` (inner `DisableIncognitoReceiver`) | [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) |

## 11. Storage / Cache / Torrent / Updater / Misc

| Task / question | File(s) | Doc |
|---|---|---|
| Storage manager (well-known subdirs under SAF base) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/storage/service/StorageManager.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Storage preferences | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/storage/StoragePreferences.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Storage folder provider (legacy fallback) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/storage/AndroidStorageFolderProvider.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Chapter disk cache (page images + page lists, 100 MiB) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Manga cover cache | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/MangaCoverCache.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Anime cover cache | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/AnimeCoverCache.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Anime background cache | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/AnimeBackgroundCache.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Coil cover fetcher (manga) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Coil cover fetcher (anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/coil/AnimeImageFetcher.kt` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Coil image decoder (custom) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/coil/TachiyomiImageDecoder.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Data saver (URL-rewriting image-compression proxy, manga) | `../ANIYOMI/app/src/main/java/aniyomi/util/DataSaver.kt` | [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| Disk / file / FFmpeg utilities | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/storage/{DiskUtil,FileExtensions,FFmpegUtils}.kt` | [`../02-modules/core-common.md`](../02-modules/core-common.md) |
| Torrserver service (foreground) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/torrent/service/TorrentServerService.kt` | [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) |
| Torrserver API client | `../ANIYOMI/core/common/src/main/java/.../TorrentServerApi.kt` (under `tachiyomi.core.common.torrent`) | [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) |
| Torrent preferences | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/` (`TorrentPreferences.kt` lives in domain `tachiyomi.domain.torrent`) | [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) |
| App self-update checker (GitHub API) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |
| App update download job | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateDownloadJob.kt` | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |
| App update notifier | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateNotifier.kt` | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |
| `UPDATER_ENABLED` flag wiring | `../ANIYOMI/buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`, `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/BuildConfig.kt` | [`../03-subsystems/updater.md`](../03-subsystems/updater.md) |
| Library exporter (CSV/external) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt` | [`../02-modules/app.md`](../02-modules/app.md) |
| Core archive (CBZ/CBR/ZIP/RAR + EPUB) | `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/{ArchiveReader,EpubReader,ZipWriter}.kt` | [`../02-modules/core-archive.md`](../02-modules/core-archive.md) |
| Core metadata (ComicInfo.xml + JSON sidecars) | `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/` | [`../02-modules/core-metadata.md`](../02-modules/core-metadata.md) |
| i18n (Mihon strings, Moko) | `../ANIYOMI/i18n/src/commonMain/moko-resources/base/strings.xml` (and per-locale subdirs) | [`../02-modules/i18n.md`](../02-modules/i18n.md) |
| i18n-aniyomi (Aniyomi-only strings, Moko) | `../ANIYOMI/i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (and per-locale subdirs) | [`../02-modules/i18n-aniyomi.md`](../02-modules/i18n-aniyomi.md) |

## 12. Misc (util, widgets, security, history, search)

| Task / question | File(s) | Doc |
|---|---|---|
| History tables (manga + anime) | `../ANIYOMI/data/src/main/sqldelight/data/history.sq`, `../ANIYOMI/data/src/main/sqldelightanime/dataanime/animehistory.sq` | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| History interactors (GetNextChapters / GetNextEpisodes) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/history/{manga,anime}/interactor/GetNext{Chapters,Episodes}.kt` | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| History view models (manga + anime) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/history/{manga,anime}/{Manga,Anime}HistoryScreenModel.kt` | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| Reader writes history | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` (`updateHistory`) | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| Player writes history | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` (`saveEpisodeHistory`, `saveEpisodeProgress`, `saveWatchingProgress`) | [`../03-subsystems/history.md`](../03-subsystems/history.md) |
| Updates view (SQL view, manga + anime) | `../ANIYOMI/data/src/main/sqldelight/view/updatesView.sq`, `../ANIYOMI/data/src/main/sqldelightanime/view/animeupdatesView.sq` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Updates view models | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/updates/{manga,anime}/{Manga,Anime}UpdatesScreenModel.kt` | [`../03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Categories tables (manga + anime) | `../ANIYOMI/data/src/main/sqldelight/data/categories.sq`, `../ANIYOMI/data/src/main/sqldelightanime/dataanime/categories.sq` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Categories ↔ entries junction tables | `../ANIYOMI/data/src/main/sqldelight/data/mangas_categories.sq`, `../ANIYOMI/data/src/main/sqldelightanime/dataanime/animes_categories.sq` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Category interactors (12 per side) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/category/{manga,anime}/interactor/` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Chapter recognition (parsing chapter num from title) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/chapter/service/ChapterRecognition.kt` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Episode recognition | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/service/EpisodeRecognition.kt` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Season sort (anime-only concept) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/items/episode/service/SeasonSorter.kt` | [`../02-modules/domain.md`](../02-modules/domain.md) |
| Stub-source DB tables (manga + anime) | `../ANIYOMI/data/src/main/sqldelight/data/sources.sq`, `../ANIYOMI/data/src/main/sqldelightanime/dataanime/animesources.sq` | [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Excluded-scanlators table | `../ANIYOMI/data/src/main/sqldelight/data/excluded_scanlators.sq` | [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Extension-repos table | `../ANIYOMI/data/src/main/sqldelight/data/extension_repos.sq`, `../ANIYOMI/data/src/main/sqldelightanime/dataanime/extension_repos.sq` | [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Custom-buttons table (MPV Lua scripts) | `../ANIYOMI/data/src/main/sqldelightanime/dataanime/custom_buttons.sq` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| Custom-button repository + UI | `../ANIYOMI/data/src/main/java/tachiyomi/data/custombutton/CustomButtonRepositoryImpl.kt`, `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/` | [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |
| App-side util extensions | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/{system,view,lang,storage,chapter,episode}/` | [`../02-modules/app.md`](../02-modules/app.md) |
| Core util extensions | `../ANIYOMI/core/common/src/main/java/{eu/kanade/tachiyomi/util,tachiyomi/core/common/util}/` | [`../02-modules/core-common.md`](../02-modules/core-common.md) |
| Crash log util | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/CrashLogUtil.kt` | [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |
| PKCE util (OAuth code challenge) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/PkceUtil.kt` | [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| AniChart API util (legacy upcoming chart) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/AniChartApi.kt` | [`../02-modules/app.md`](../02-modules/app.md) |
| Legacy Views widgets (still used by reader/player) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/widget/` | [`../02-modules/app.md`](../02-modules/app.md) |
| Presentation-core shared composables | `../ANIYOMI/presentation-core/src/main/java/tachiyomi/presentation/core/` | [`../02-modules/presentation-core.md`](../02-modules/presentation-core.md) |
| Presentation-widget managers (instantiated by App) | `../ANIYOMI/presentation-widget/src/main/java/.../{Manga,Anime}WidgetManager.kt` | [`../02-modules/presentation-widget.md`](../02-modules/presentation-widget.md) |
| Security preferences (app-lock, incognito) | `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/core/security/SecurityPreferences.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| Base preferences (extension installer, theme mode, …) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/base/BasePreferences.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |
| UI preferences (theme, tab order, …) | `../ANIYOMI/domain/src/main/java/tachiyomi/domain/ui/UiPreferences.kt` | [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) |

---

## See also

- [`README.md`](README.md) — this folder's index.
- [`glossary.md`](glossary.md) — definitions for every term used above.
- [`cross-reference-matrix.md`](cross-reference-matrix.md) — subsystem × module × key-file matrix.
- [`../02-modules/README.md`](../02-modules/README.md) — per-module deep dives (each module doc has its own key-files table).
- [`../03-subsystems/README.md`](../03-subsystems/README.md) — per-subsystem deep dives (each subsystem doc has its own key-files table).
- [`../04-data-models/`](../04-data-models/) — domain models, DB schema, preferences catalog.
