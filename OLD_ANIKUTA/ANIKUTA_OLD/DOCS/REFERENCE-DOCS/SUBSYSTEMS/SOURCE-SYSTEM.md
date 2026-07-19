# Source & Extension System

Aniyomi's plugin architecture: each website/local-source is implemented as an external APK ("extension") that is loaded at runtime via a `PathClassLoader` and exposed to the app through `AnimeSourceManager` / `MangaSourceManager`; the source contract itself lives in the `:source-api` Gradle module.

## Where it lives

- `REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/` — manga source contract (interfaces, models, online helpers).
- `REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/` — anime source contract (parallel package, anime-specific).
- `REFERENCE/source-api/src/androidMain/kotlin/eu/kanade/tachiyomi/{source,animesource}/PreferenceScreen.kt` — `expect`/`actual` `PreferenceScreen` type used by configurable sources.
- `REFERENCE/source-api/src/androidMain/AndroidManifest.xml` — empty manifest (module is a library, no components).
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt` — Android impl of the anime `SourceManager`.
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt` — Android impl of the manga `SourceManager`.
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/source/anime/AnimeSourceExtensions.kt` and `.../source/manga/MangaSourceExtensions.kt` — small helper fns (icon lookup, naming, stub conversion).
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/extension/anime/` — anime extension manager, model, API, loader, installer, install receiver/service/activity.
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/extension/manga/` — manga equivalents (parallel structure, file-by-file mirror).
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/extension/InstallStep.kt`, `.../ExtensionUpdateNotifier.kt` — shared between anime and manga.
- `REFERENCE/domain/src/main/java/tachiyomi/domain/source/anime/service/AnimeSourceManager.kt` and `.../manga/service/MangaSourceManager.kt` — domain-side interfaces the app consumes.
- `REFERENCE/source-local/src/.../entries/anime/LocalAnimeSource.kt` and `.../entries/manga/LocalMangaSource.kt` — the always-present "Local" source (registered by hand in the managers, not via an extension).
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` — Injekt wiring that registers both managers + both extension managers as singletons.

## What it does

The source/extension system lets the user install third-party APKs (each implementing one or more `AnimeSource` or `MangaSource` classes) that teach the app how to talk to a specific website or local directory. Responsibilities:

- Define the **contract** between the app and any source (`Source`, `CatalogueSource`, `HttpSource`, `ParsedHttpSource`, plus the anime-side parallels).
- Define the **DTO models** that flow across the contract (`SManga`/`SChapter`/`Page` for manga; `SAnime`/`SEpisode`/`Video`/`Hoster` for anime; `MangasPage`/`AnimesPage`; `FilterList`/`AnimeFilterList`; `UpdateStrategy`/`AnimeUpdateStrategy`).
- **Discover, install, update, uninstall, and trust** external APKs at runtime (`AnimeExtensionManager`, `MangaExtensionManager`, plus per-side loader/installer/api/install-receiver).
- **Load extension APKs at runtime** via a `ChildFirstPathClassLoader` (fallback `PathClassLoader`), instantiate the source classes declared in the APK's `<meta-data>`, and register them keyed by their stable MD5-derived `Long` id.
- Expose the live `Map<Long, AnimeSource>` / `Map<Long, MangaSource>` to the rest of the app via the managers, and persist "stub" source metadata in the DB so the UI can still show library entries whose extension is no longer installed.
- Provide a **catalogue browsing** flow: `getPopularManga`/`getPopularAnime`, `getSearchManga`/`getSearchAnime`, `getLatestUpdates` (paginated, filterable).
- Provide a **series/episode/chapter fetching** flow: `getMangaDetails`/`getAnimeDetails`, `getChapterList`/`getEpisodeList`, `getPageList` (manga) / `getVideoList` + `getHosterList` + `getVideoList(hoster)` (anime, since ext-lib 16).

## Key types & files

### Source contract — manga (`eu.kanade.tachiyomi.source`)
- `MangaSource` — base interface: `id: Long`, `name: String`, `lang: String`, plus suspend `getMangaDetails / getChapterList / getPageList` (and deprecated Rx `fetch*` variants).
- `CatalogueSource : MangaSource` — adds `supportsLatest`, `getFilterList()`, suspend `getPopularManga / getSearchManga / getLatestUpdates` (and deprecated Rx `fetch*`).
- `online.HttpSource` (abstract) — concrete `CatalogueSource` for HTTP sites; provides `baseUrl`, `versionId`, lazy `id` via MD5 of `"${name.lowercase()}/$lang/$versionId"`, `client: OkHttpClient` from `NetworkHelper`, `headers`, abstract `*Request`/`*Parse` methods for popular/search/latest/details/chapters/pages/image, plus `getImage`, `setUrlWithoutDomain`, `prepareNewChapter`, `getMangaUrl`, `getChapterUrl`. Uses `rx.Observable`-based `fetch*` internally, wrapped into suspend fns via `awaitSingle()`.
- `online.ParsedHttpSource : HttpSource` — Jsoup-based skeleton; implements all `*Parse(Response)` methods in terms of abstract `*Selector()` / `*FromElement()` / `*NextPageSelector()` / `*Parse(Document)` hooks.
- `online.ResolvableSource : MangaSource` — opt-in: `getUriType(uri): UriType` + `getManga(uri)` / `getChapter(uri)` for deep-linking a URL back to a series/chapter. Sealed `UriType { Manga, Chapter, Unknown }`.
- `ConfigurableSource : MangaSource` — opt-in: `setupPreferenceScreen(screen: PreferenceScreen)` + `getSourcePreferences()` (SharedPreferences scoped to `"source_$id"`).
- `SourceFactory` — `fun createSources(): List<MangaSource>`. An extension APK declares either a single `MangaSource` subclass or a `SourceFactory` in its `<meta-data>`.
- `UnmeteredSource` — marker interface (self-hosted sources, exempt from metered-network warnings).
- `model.SManga` / `SChapter` / `Page` / `MangasPage` / `FilterList` / `Filter` (sealed) / `UpdateStrategy` (enum: `ALWAYS_UPDATE`, `ONLY_FETCH_ONCE`).

### Source contract — anime (`eu.kanade.tachiyomi.animesource`)
- `AnimeSource` — base interface; mirrors `MangaSource` but with `getAnimeDetails / getEpisodeList / getVideoList`, plus since ext-lib 16 `getSeasonList(anime): List<SAnime>`, `getHosterList(episode): List<Hoster>`, `getVideoList(hoster): List<Video>`. The hoster/season methods default to `throw IllegalStateException("Not used")` so older extensions still compile.
- `AnimeCatalogueSource : AnimeSource` — `getPopularAnime / getSearchAnime / getLatestUpdates`, `getFilterList(): AnimeFilterList`, `supportsLatest`.
- `online.AnimeHttpSource` (abstract) — anime parallel of `HttpSource`. Adds the hoster/season plumbing (`seasonListRequest/Parse`, `hosterListRequest/Parse`, `videoListRequest(hoster)/Parse`, `videoListRequest(episode)/Parse`, `videoUrlRequest/Parse`, `resolveVideo`, `getVideo`, `getVideoSize`, `videoRequest`/`safeVideoRequest` for ranged byte requests, `List<Hoster>.sortHosters()`, `List<Video>.sortVideos()`, `prepareNewEpisode`, `getAnimeUrl`, `getEpisodeUrl`).
- `online.ParsedAnimeHttpSource : AnimeHttpSource` — Jsoup skeleton with `*Selector`/`*FromElement` hooks for popular/search/latest/details/episodes/seasons/hosters/videos/videoUrl.
- `online.ResolvableAnimeSource : AnimeSource` — `getUriType` + `getAnime(uri)` + `getEpisode(uri)`; `UriType { Anime, Episode, Unknown }`.
- `ConfigurableAnimeSource : AnimeSource` — `setupPreferenceScreen` + `getSourcePreferences()`.
- `AnimeSourceFactory` — `fun createSources(): List<AnimeSource>`.
- `UnmeteredSource` — identical marker interface (anime package).
- `model.SAnime` / `SEpisode` / `Video` / `Hoster` / `AnimesPage` / `AnimeFilterList` / `AnimeFilter` / `AnimeUpdateStrategy` / `FetchType` (enum: `Seasons`, `Episodes`, since ext-lib 16; decides whether `getSeasonList` or `getEpisodeList` is called for a given `SAnime`).
- `model.Video` — data class with `videoUrl`, `videoTitle`, `resolution`, `bitrate`, `headers`, `subtitleTracks: List<Track>`, `audioTracks: List<Track>`, `timestamps: List<TimeStamp>`, `mpvArgs`, `ffmpegStreamArgs`, `ffmpegVideoArgs`, `internalData`, plus `@Transient var status: State { QUEUE, LOAD_VIDEO, READY, ERROR }`. `SerializableVideo` provides JSON serialization for cross-process passing.
- `model.Hoster` — `hosterUrl`, `hosterName`, `videoList: List<Video>?`, `internalData`, `lazy: Boolean`, `status: State { IDLE, LOADING, READY, ERROR }`. `Hoster.NO_HOSTER_LIST` is the magic name used when a source returns videos directly without a hoster layer (`List<Video>.toHosterList()` wraps them in a single synthetic `Hoster`).

### Models shared between anime and manga
- The `S*` interfaces are NOT shared by inheritance — `SManga` and `SAnime` are two separate `interface … : Serializable` types. They have nearly identical fields (url, title, artist, author, description, genre, status, thumbnail_url, initialized, update_strategy) but `SAnime` adds `background_url`, `fetch_type`, `season_number`, and uses `AnimeUpdateStrategy`; `SManga` uses `UpdateStrategy`.
- Same for `SChapter` vs `SEpisode` — same shape (url, name, date_upload, scanlator) but `SEpisode` adds `fillermark`, `summary`, `preview_url` and renames `chapter_number` to `episode_number`.
- `Filter` (manga) and `AnimeFilter` (anime) are duplicated sealed classes with identical structure (`Header`, `Separator`, `Select`, `Text`, `CheckBox`, `TriState`, `Group`, `Sort`).
- `FilterList` and `AnimeFilterList` are duplicated `@Stable` data classes with `equals(other) = false` (deliberate — they are compared by reference for recomposition).

### Domain interfaces (`tachiyomi.domain.source.*.service`)
- `AnimeSourceManager` / `MangaSourceManager` — interfaces defining `isInitialized: StateFlow<Boolean>`, `catalogueSources: Flow<List<*CatalogueSource>>`, `get(sourceKey)`, `getOrStub(sourceKey)`, `getOnlineSources()`, `getCatalogueSources()`, `getStubSources()`. The Android implementations (`AndroidAnimeSourceManager`, `AndroidMangaSourceManager`) are the only concrete impls.

### Extension layer (`eu.kanade.tachiyomi.extension.*`)
- `AnimeExtensionManager` / `MangaExtensionManager` — owns the three state flows `installedExtensionsFlow`, `availableExtensionsFlow`, `untrustedExtensionsFlow`; calls `loadExtensions(context)` on init; registers an `AnimeExtensionInstallReceiver` to react to system install/uninstall/update events; provides `installExtension`, `updateExtension`, `cancelInstallUpdateExtension`, `uninstallExtension`, `trust`, `setInstalling`, `updateInstallStep`, `getExtensionPackage`, `getExtensionPackageAsFlow`, `getAppIconForSource`, `getSourceData`. Singletons wired in `AppModule`.
- `AnimeExtensionLoader` / `MangaExtensionLoader` — `internal object`s that: scan installed packages + private `filesDir/exts/*.ext` files for those declaring the extension feature flag, pick the higher version-code one when both exist, validate lib-version range (`LIB_VERSION_MIN`..`LIB_VERSION_MAX`; anime is 12..16, manga is 1.4..1.5), check signatures against `Trust*Extension`, build a `ChildFirstPathClassLoader` (fallback `dalvik.system.PathClassLoader`) on `appInfo.sourceDir`, read the `tachiyomi.animeextension.class` (`tachiyomi.extension.class` for manga) meta-data which is a `;`-separated list of source class FQNs, instantiate each via `Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()`, dispatch on `is AnimeSource` / `is AnimeSourceFactory` (`is MangaSource` / `is SourceFactory` for manga), and return a `List<*LoadResult>`. Also exposes `installPrivateExtensionFile`, `uninstallPrivateExtension`, `loadExtensionFromPkgName`.
- `AnimeLoadResult` / `MangaLoadResult` — sealed: `Success(extension)`, `Untrusted(extension)`, `Error`.
- `AnimeExtension` / `MangaExtension` — sealed: `Installed` (carries `sources: List<AnimeSource>`, `pkgFactory`, `icon`, `hasUpdate`, `isObsolete`, `isShared`, `repoUrl`), `Available` (carries `apkName`, `iconUrl`, `repoUrl`, `sources: List<*Source>` of lightweight stub data), `Untrusted` (carries `signatureHash`).
- `AnimeExtensionApi` / `MangaExtensionApi` — fetches `<repoBaseUrl>/index.min.json` from every repo in the `ExtensionRepo` table, deserializes into `*ExtensionJsonObject`, filters by lib-version range, returns `List<*Extension.Available>`. Also `checkForUpdates` (rate-limited to 1/day) and `getApkUrl`.
- `AnimeExtensionInstaller` / `MangaExtensionInstaller` — `internal class`es that drive `android.app.DownloadManager`, poll its status into a `Flow<InstallStep>`, and on completion dispatch to one of three installer strategies from `BasePreferences.ExtensionInstaller`:
  - `LEGACY` → start `*ExtensionInstallActivity` (system package-installer intent flow).
  - `PRIVATE` → copy APK into `filesDir/exts/<pkg>.ext` via `*ExtensionLoader.installPrivateExtensionFile` (no system installer needed; Android 14+ friendly).
  - else → `*ExtensionInstallService` foreground service running `installer.Installer*` (concrete impls: `PackageInstallerInstaller*` using Android's `PackageInstaller` API, or `ShizukuInstaller*` using Shizuku for privileged install).
- `installer.InstallerAnime` / `installer.InstallerManga` — abstract base for the service-driven installers; manages a queue, `ready` flag, cancel broadcast, and entry lifecycle. Concrete subclasses (`PackageInstallerInstaller*`, `ShizukuInstaller*`) implement the actual install/uninstall.
- `AnimeExtensionInstallReceiver` / `MangaExtensionInstallReceiver` — broadcast receivers (also expose static `notifyAdded/Replaced/Removed` for the private-install path) that translate system package-installer events back into `Manager.registerNewExtension / registerUpdatedExtension / onExtensionUntrusted / onPackageUninstalled`.
- `extension.InstallStep` (shared enum, `Idle/Pending/Downloading/Installing/Installed/Error`, with `isCompleted()`) and `extension.ExtensionUpdateNotifier` (shared notification helper that distinguishes anime vs manga via an `anime: Boolean` flag).

## How it works

### Installation lifecycle (anime example; manga is identical modulo class names)
1. UI calls `AnimeExtensionManager.findAvailableExtensions()` which calls `AnimeExtensionApi.findExtensions()` → fetches each repo's `index.min.json`, produces `List<AnimeExtension.Available>`, stores them in `availableExtensionsMapFlow`, and refreshes `hasUpdate` flags on already-installed extensions.
2. User taps "Install": `AnimeExtensionManager.installExtension(extension: Available): Flow<InstallStep>` → `AnimeExtensionInstaller.downloadAndInstall(url, extension)` enqueues a `DownloadManager.Request` for the APK URL `${repoUrl}/apk/${apkName}`.
3. `AnimeExtensionInstaller.downloadStatusFlow(id)` polls `DownloadManager` every 1s, mapping `STATUS_PENDING`→`Pending`, `STATUS_RUNNING`→`Downloading`.
4. On `ACTION_DOWNLOAD_COMPLETE`, the inner `DownloadCompletionReceiver` reads the local URI from the cursor and calls `installApk(downloadId, uri)`.
5. `installApk` branches on `BasePreferences.extensionInstaller()`:
   - `LEGACY` → starts `AnimeExtensionInstallActivity` with the APK URI and MIME `application/vnd.android.package-archive`.
   - `PRIVATE` → copies APK to `cacheDir/temp_<id>`, calls `AnimeExtensionLoader.installPrivateExtensionFile(context, tempFile)` (which validates it is an extension, refuses downgrade, requires same signature if upgrading, then `copyAndSetReadOnlyTo` into `filesDir/exts/<pkg>.ext` and fires `AnimeExtensionInstallReceiver.notifyAdded/Replaced`). On success, marks `InstallStep.Installed`.
   - otherwise → starts `AnimeExtensionInstallService` (foreground), which delegates to `PackageInstallerInstallerAnime` or `ShizukuInstallerAnime`.
6. Whichever path completes successfully fires `AnimeExtensionInstallReceiver` (either via real system broadcast or `notifyAdded/Replaced`). The receiver, configured with the manager's `AnimeInstallationListener`, calls `onExtensionInstalled/Updated`, which calls `Manager.registerNewExtension/UpdatedExtension` → updates `installedExtensionsMapFlow`.

### Discovery & loading at app startup
1. `AppModule` adds singletons `AnimeExtensionManager(app)` and `MangaExtensionManager(app)`. Construction triggers `initAnimeExtensions()` / `initMangaExtensions()`.
2. `init*Extensions` calls `*ExtensionLoader.loadExtensions(context)`, which:
   - Lists all installed packages via `PackageManager.getInstalledPackages(...)` and the private `filesDir/exts/*.ext` files via `getPackageArchiveInfo(...)`.
   - Filters by `pkgInfo.reqFeatures` containing `tachiyomi.animeextension` (anime) or `tachiyomi.extension` (manga).
   - De-duplicates by package name; when a package exists both as a shared system install and a private `.ext`, picks the higher `versionCode`.
   - For each surviving package, `loadExtension(context, extensionInfo)`:
     a. Extracts `extName` from the application label (strips `"Aniyomi: "` prefix), `versionName`, `versionCode`.
     b. Parses `libVersion` from `versionName.substringBeforeLast('.')` and validates against `LIB_VERSION_MIN..LIB_VERSION_MAX` (anime: 12..16; manga: 1.4..1.5).
     c. Computes SHA-256 of each signing certificate and consults `Trust*Extension.isTrusted`. If not trusted, returns `LoadResult.Untrusted(extension)` — the manager stores it in `untrustedExtensionsMapFlow` and the user must explicitly `Manager.trust(extension)`, which writes `(pkgName, versionCode, signatureHash)` via `Trust*Extension` and re-attempts `loadExtensionFromPkgName`.
     d. Reads `METADATA_NSFW` (`tachiyomi.animeextension.nsfw` / `tachiyomi.extension.nsfw`); if `1` and the user hasn't enabled `showNsfwSource`, returns `LoadResult.Error`.
     e. Builds a `ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)` — a child-first classloader so extension classes take precedence over the app's, with `PathClassLoader` as a `LinkageError` fallback.
     f. Reads `METADATA_SOURCE_CLASS` (`tachiyomi.animeextension.class` / `tachiyomi.extension.class`), splits on `;`, and for each FQN instantiates it: if the result `is AnimeSource` (resp. `MangaSource`) wraps it in a 1-element list; if `is AnimeSourceFactory` (resp. `SourceFactory`) calls `createSources()`.
     g. Aggregates the resulting `List<AnimeSource>` into an `AnimeExtension.Installed` (with `pkgFactory` from `METADATA_SOURCE_FACTORY`).
   - All packages are loaded concurrently via `runBlocking { extPkgs.map { async { loadExtension(context, it) } }.awaitAll() }`.
3. The manager partitions the results: `Success` → `installedExtensionsMapFlow`, `Untrusted` → `untrustedExtensionsMapFlow`.

### From extension → Source (the manager wiring)
`AndroidAnimeSourceManager` (and its manga twin) constructor:
- Subscribes to `extensionManager.installedExtensionsFlow`.
- On every emission, builds a fresh `ConcurrentHashMap<Long, AnimeSource>` seeded with `LocalAnimeSource.ID → LocalAnimeSource(...)` (built-in local source, registered by hand — not an extension).
- For each extension, iterates `extension.sources` and puts each into the map keyed by its `id`. Each source is also converted to a `StubAnimeSource` and upserted into `AnimeStubSourceRepository` (so library entries survive extension uninstall).
- The map is published via `sourcesMapFlow`, and `isInitialized` flips to `true`.
- A second coroutine subscribes to `sourceRepository.subscribeAllAnime()` and keeps `stubSourcesMap` in sync (used by `getOrStub` to return a placeholder for library entries whose real source is not currently loaded).

### How the app calls a source
UI/ViewModel code obtains a source via `Injekt.get<AnimeSourceManager>().get(sourceId)` (or `getOrStub` for library-safe lookups). The result is cast to the most specific interface the call site needs (`AnimeCatalogueSource` for browsing, `AnimeHttpSource` for HTTP-specific behavior like `getAnimeUrl`, `ConfigurableAnimeSource` for the settings screen, `ResolvableAnimeSource` for deep links).

Typical catalogue browse → watch flow (anime):
- `BrowseAnimeSourceScreenModel` calls `source.getSearchAnime(page, query, filters)` (or `getPopularAnime`/`getLatestUpdates`) which, in `AnimeHttpSource`, delegates through the deprecated `fetchSearchAnime(page, query, filters)` RxObservable → `client.newCall(searchAnimeRequest(...)).asObservableSuccess().map { searchAnimeParse(it) }` → `.awaitSingle()`. Result: `AnimesPage(animes: List<SAnime>, hasNextPage)`.
- Tapping a series triggers `source.getAnimeDetails(anime)` → fills the rest of the `SAnime` fields (description, genre, status, etc.).
- `source.getEpisodeList(anime)` → `List<SEpisode>` (or, if `anime.fetch_type == FetchType.Seasons`, `source.getSeasonList(anime)` is called first and the user picks a season, which is itself an `SAnime` whose `fetch_type` is `Episodes`).
- For each episode the player flow calls `source.getVideoList(episode)` → `List<Video>`. With ext-lib 16 sources, the flow is instead `source.getHosterList(episode)` → `List<Hoster>`, then `source.getVideoList(hoster)` → `List<Video>`. The hoster abstraction lets a source expose multiple mirrors per episode (the first hoster is "preferred"); older sources that don't use hosters wrap their videos via `List<Video>.toHosterList()` (a single `Hoster` named `Hoster.NO_HOSTER_LIST`).
- The player then calls `source.resolveVideo(video)` (optional), and uses `video.videoUrl` plus `video.headers`/`subtitleTracks`/`audioTracks`/`mpvArgs` to drive MPV. `source.videoRequest(video, start, end)` and `source.safeVideoRequest(video)` build ranged GET requests for chunked download/streaming.

Typical catalogue browse → read flow (manga) is the same shape: `getSearchManga` → `getMangaDetails` → `getChapterList` → `getPageList(chapter): List<Page>` → `getImageUrl(page)` → `getImage(page): Response`.

### ID generation
Both `HttpSource` and `AnimeHttpSource` derive `id` lazily from `generateId(name, lang, versionId)`:
```
val key = "${name.lowercase()}/$lang/$versionId"
val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
(0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
```
The `and Long.MAX_VALUE` clears the sign bit so the id fits in a non-negative `Long` column. `versionId` (default 1) lets a source bump its id without changing name/lang (used when a site's URL scheme changes irreversibly).

## Dependencies

- `:source-api` depends on: `:core:common` (for `tachiyomi.core.common.util.lang.awaitSingle`/`awaitSuccess`), the `network` package (`NetworkHelper`, `GET`, `asObservableSuccess`, `awaitSuccess`, `newCachelessCallWithProgress`), `okhttp3`, `jsoup`, `rxjava` (for the deprecated-but-still-wired `fetch*` methods), `kotlinx-serialization-json`, `androidx.compose.runtime` (for `@Stable` on `FilterList`/`AnimeFilterList`), and `Injekt` (used by `ConfigurableSource`/`ConfigurableAnimeSource` to fetch the `Application`).
- The app's extension loader depends on: `:core:common` (`ChildFirstPathClassLoader`, `logcat`, `withUIContext`), `:domain` (`Stub*Source`, `*StubSourceRepository`, `*SourceManager`, `ExtensionRepo` interactors), `:source-api` (obviously), `:source-local` (the local sources), `Trust*Extension` interactors, `SourcePreferences`, `androidx.core:core-ktx` (`PackageInfoCompat`), and the platform `PackageManager`/`DownloadManager`/`PackageInstaller` APIs.
- What depends on the source system: every UI screen under `ui/browse/{anime,manga}/source/`, `ui/entries/{anime,manga}/`, `ui/library/{anime,manga}/`, `ui/player/`, `ui/reader/`, `data/library/{anime,manga}/` (library update jobs), `data/download/{anime,manga}/`, `data/backup/` (sources participate in backup/restore), `data/coil/` (cover fetchers call into `HttpSource`/`AnimeHttpSource` for headers).

## Anime vs manga

This is the central architectural question. The answer: **the system is fully duplicated per type, with no shared base interface and no shared loader. The two sides are decoupled enough that the manga side can be removed without breaking the anime side, but the cost is a non-trivial amount of duplicated code.**

Specifics:

- **Two parallel package trees.** Everything in `eu.kanade.tachiyomi.source` has a sibling in `eu.kanade.tachiyomi.animesource`: `MangaSource` ↔ `AnimeSource`, `CatalogueSource` ↔ `AnimeCatalogueSource`, `HttpSource` ↔ `AnimeHttpSource`, `ParsedHttpSource` ↔ `ParsedAnimeHttpSource`, `ResolvableSource` ↔ `ResolvableAnimeSource`, `ConfigurableSource` ↔ `ConfigurableAnimeSource`, `SourceFactory` ↔ `AnimeSourceFactory`, `UnmeteredSource` (defined twice, identical marker interface), `SManga`/`SChapter`/`Page`/`MangasPage`/`FilterList`/`Filter`/`UpdateStrategy` ↔ `SAnime`/`SEpisode`/`Video`/`Hoster`/`AnimesPage`/`AnimeFilterList`/`AnimeFilter`/`AnimeUpdateStrategy`. The `Filter`/`AnimeFilter` and `FilterList`/`AnimeFilterList` classes are line-for-line copies with just the name changed.
- **No common base.** `AnimeSource` and `MangaSource` are both top-level interfaces with `id: Long` + `name: String` + `lang: String`. There is no `interface Source` supertype. Code that needs to handle both (very rare — essentially only the source-icon and naming helpers, which are themselves duplicated) does so via overload or via `Any`. The `UpdateStrategy` and `AnimeUpdateStrategy` enums are also duplicated (identical bodies, different names).
- **Anime-specific extras.** `SAnime` adds `background_url`, `fetch_type: FetchType`, `season_number: Double` (none of which exist on `SManga`). `SEpisode` adds `fillermark`, `summary`, `preview_url`. `AnimeHttpSource` adds the entire hoster/season layer (`getSeasonList`, `getHosterList`, `getVideoList(hoster)`, `resolveVideo`, `getVideoSize`, `videoRequest`/`safeVideoRequest`, `sortHosters`/`sortVideos`) plus the `Video` model with subtitle/audio tracks, MPV args, and timestamps — none of which has any manga equivalent.
- **Extension APKs declare their type via the Android `<uses-feature>` name.** Anime extensions set `tachiyomi.animeextension`; manga extensions set `tachiyomi.extension`. The `<meta-data>` keys follow the same prefix (`tachiyomi.animeextension.class` vs `tachiyomi.extension.class`, etc.). The loader only scans for its own feature flag, so an anime loader will never pick up a manga extension and vice versa — they are completely disjoint APK ecosystems.
- **Two independent managers, two independent loaders, two independent installers.** `AnimeExtensionManager` ↔ `MangaExtensionManager`; `AnimeExtensionLoader` ↔ `MangaExtensionLoader`; `AnimeExtensionInstaller` ↔ `MangaExtensionInstaller`; `AnimeExtensionApi` ↔ `MangaExtensionApi`; `AnimeExtensionInstallReceiver` ↔ `MangaExtensionInstallReceiver`; `AnimeExtensionInstallService` ↔ `MangaExtensionInstallService`; `PackageInstallerInstallerAnime` ↔ `PackageInstallerInstallerManga`; `ShizukuInstallerAnime` ↔ `ShizukuInstallerManga`; `AndroidAnimeSourceManager` ↔ `AndroidMangaSourceManager`. They are wired in `AppModule` as separate singletons and never reference each other.
- **The only things actually shared between the two sides** are: `extension/InstallStep.kt` (enum), `extension/ExtensionUpdateNotifier.kt` (notification helper with an `anime: Boolean` param), the `:source-local` module (which itself has parallel `entries/anime/` and `entries/manga/` trees), and the underlying platform primitives (`PackageManager`, `DownloadManager`, `PackageInstaller`).
- **The lib-version ranges are different.** Anime extensions target `LIB_VERSION_MIN=12, LIB_VERSION_MAX=16`; manga extensions target `1.4..1.5`. This means an anime extension cannot be loaded by the manga loader (and vice versa) even if the feature flag were wrong — the version parsing would reject it.
- **Decoupling verdict for an anime-only build.** Yes, the manga side can be dropped cleanly: remove `:source-api/.../source/` (the manga package), `:source-local/.../entries/manga/`, `app/.../source/manga/`, `app/.../extension/manga/`, the `MangaSourceManager` singleton + `MangaExtensionManager` singleton from `AppModule`, the manga-related interactors/repos in `:domain`/`:data`, the manga UI screens, and the manga download/library/backup machinery. None of the anime-side classes import from `eu.kanade.tachiyomi.source` (only from `eu.kanade.tachiyomi.animesource`) — confirmed by reading the imports in `AnimeSource.kt`, `AnimeCatalogueSource.kt`, `AnimeHttpSource.kt`, `ParsedAnimeHttpSource.kt`, `AnimeExtensionLoader.kt`, `AndroidAnimeSourceManager.kt`. The reverse (anime imports in manga files) is also false. The cost of removal is mostly mechanical deletion plus dropping a handful of `Injekt.addSingletonFactory` lines; no interface refactor is required. The duplicated `Filter`/`AnimeFilter` etc. would become single copies under whatever package name we choose.
- **One caveat.** `SourcePreferences` (in `eu.kanade.domain.source.service`) is shared by both sides and has methods like `animeExtensionUpdatesCount()` and `mangaExtensionUpdatesCount()` side by side. Dropping one side means pruning the corresponding preferences (low-risk).

## Relationships

- Cross-references: this doc is the subsystem deep-dive called for by `DOCS/REFERENCE-DOCS/ARCHITECTURE.md` (the "Source & extension plugin system" section) and `DOCS/REFERENCE-DOCS/MODULES.md` (the `:source-api` and `:source-local` rows). It sits alongside (to be written) `DATA-LAYER.md` (the `tachiyomi.animedb` SQLDelight schema and `AnimeDatabaseHandler`), `DOWNLOAD-MANAGER.md` (`AnimeDownloadManager`, `AnimeDownloader`, `AnimeDownloadProvider` — which consume `AnimeHttpSource.getVideo` / `videoRequest` / `safeVideoRequest`), `PLAYER.md` (MPV integration that consumes `Video` and calls `resolveVideo`), and `BACKUP.md` (which serializes `AnimeExtension.Installed` metadata and per-source preferences).
- Module-level reference: see `DOCS/REFERENCE-DOCS/MODULES.md` for the full module map; the source system is the contract surface of `:source-api`, with `:source-local` providing the always-present `LocalAnimeSource`/`LocalMangaSource` impls, `:app` providing the loader/manager/installer, and `:domain` providing the `*SourceManager` interfaces + `Stub*Source` models + `*StubSourceRepository`.
- App structure: see `DOCS/REFERENCE-DOCS/APP-STRUCTURE.md` rows for `extension/anime/`, `extension/manga/`, `source/anime/`, `source/manga/`, `ui/browse/anime/source/`, `ui/browse/anime/extension/`.

## Notes for our build (anime-first)

Because anikuta is anime-first (per MEMORY/PROJECT-CONTEXT.md) and the manga side is fully duplicated and decoupled, we can build anime-only without touching the source contract:

- **Reuse as-is:** `eu.kanade.tachiyomi.animesource.*` (the whole anime source contract), `AnimeExtensionManager`, `AnimeExtensionLoader`, `AnimeExtensionInstaller`, `AnimeExtensionApi`, `AnimeExtensionInstallReceiver/Service/Activity`, `PackageInstallerInstallerAnime`, `ShizukuInstallerAnime`, `AndroidAnimeSourceManager`, `AnimeSourceExtensions`, and the anime half of `:source-local` (`LocalAnimeSource`, `LocalAnimeFetchTypeManager`, `LocalAnimeSourceFileSystem`, `LocalAnimeCoverManager`, `LocalAnimeBackgroundManager`, `LocalEpisodeThumbnailManager`). All wired by Injekt in `AppModule` and ready to go.
- **Watch out for:**
  1. `AppModule` also constructs `MangaSourceManager` / `MangaExtensionManager` / `MangaDownloadManager` etc. — these need to be removed, but the construction is in clearly delimited `addSingletonFactory` blocks, easy to delete.
  2. `SourcePreferences` (in `:domain`-adjacent `eu.kanade.domain.source.service`) mixes anime and manga prefs in one class — prune the `*manga*` methods when removing the manga side, or split into `AnimeSourcePreferences`.
  3. `ExtensionUpdateNotifier` is shared; it takes an `anime: Boolean` param. Keep it, just never call it with `false`.
  4. `InstallStep` is shared — keep it.
  5. The `ChildFirstPathClassLoader` util lives in `app/.../util/system/` — make sure that util isn't manga-coupled (it isn't; it's generic).
  6. The anime lib-version range is `12..16` — when bumping extensions-lib in the future, both bounds in `AnimeExtensionLoader` and the filter in `AnimeExtensionApi.toExtensions` need updating in lockstep.
  7. The `tachiyomi.animeextension.*` meta-data keys and the `tachiyomi.animeextension` `<uses-feature>` are the contract any third-party anime extension APK must declare. If we change the package or the meta-data names (e.g., to `app.anikuta.animeextension`), we break compatibility with the existing aniyomi extension ecosystem — a major decision, not a refactor.
  8. `LocalAnimeSource` is hand-registered (not loaded as an extension) and has `LocalAnimeSource.ID` as its source id — preserve that constant if we want library entries from local source to survive migration.
- **Anime-first simplification opportunities (post-MVP, not now):**
  - Collapse the duplicated `Filter`/`AnimeFilter` → keep `AnimeFilter`.
  - Rename `AnimeSource` → `Source` if we want the "Source" name back without the manga baggage.
  - Consider whether `ParsedAnimeHttpSource` is worth keeping (most modern sources use JSON/JSON-LD, not HTML scraping). Keep for now.

## TODOs / open questions

- The ext-lib 16 "hoster" model (`getHosterList`/`getVideoList(hoster)`, `FetchType.Seasons`/`Episodes`, `getSeasonList`) is described in the source as `@since extensions-lib 16`. The `LIB_VERSION_MAX = 16` in `AnimeExtensionLoader` confirms this is the current top of the supported range. TODO: when ext-lib 17 ships, audit `AnimeHttpSource` and `ParsedAnimeHttpSource` for newly-added abstract methods and bump `LIB_VERSION_MAX` in both `AnimeExtensionLoader` and `AnimeExtensionApi`.
- The `Video.url` / `Video.quality` fields are explicitly `// TODO(1.6): Remove after ext lib bump` — they exist only for binary compat with extensions built against older ext-libs. They should be removable once `LIB_VERSION_MIN` is raised above the version that introduced `videoTitle` / `videoPageUrl`.
- `ResolvableAnimeSource`'s `UriType` is defined in the same file as the interface (a sealed interface with three data objects). The manga `ResolvableSource` has its own `UriType` (also `Manga`/`Chapter`/`Unknown`). TODO: confirm whether deep-link UI code (`ui/deeplink/anime/DeepLinkAnime*`) iterates `getOnlineSources()` and calls `getUriType` on each — needs a separate read of the deeplink subsystem.
- The `AnimeExtensionApi` fetches `index.min.json` from each repo in the `ExtensionRepo` table. The repo table is managed by `mihon.domain.extensionrepo.anime.interactor.*`. TODO: document the repo-management UI and the JSON schema expected from `index.min.json` (currently inferred only from `AnimeExtensionJsonObject`).
- The `ChildFirstPathClassLoader` lives at `app/.../util/system/ChildFirstPathClassLoader.kt` (referenced by the loader but not read for this doc). TODO: read it to confirm it is a standard parent-last classloader and whether it has any class-filtering / security behavior we should document.
- The `Trust*Extension` interactors (`eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension`) decide whether a signature is trusted. TODO: document the trust store (where trusted hashes are persisted) and how the user-trust prompt flow works.
- The manga `MangaExtensionLoader` uses `LIB_VERSION_MIN = 1.4, LIB_VERSION_MAX = 1.5` while anime uses `12..16`. This means the two ecosystems are on entirely different extension-lib version schemes. TODO: confirm whether any code (backup/restore, repo sync) ever handles both kinds in one place — likely not, but worth a grep before declaring "fully separable" in an anime-only build.
