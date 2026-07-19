# 02-modules / source-api — The `:source-api` module

> The contract that **every** content source — built-in or external extension —
> implements. This is the boundary between the app and the rest of the internet.
> Small (~50 source files) but architecturally central: `:app`, `:data`,
> `:domain`, `:source-local`, `:core-metadata`, and every external extension APK
> depend on this module's interfaces.

## Purpose

`/home/z/.../ANIYOMI/source-api/` defines the API a *source* must satisfy to plug
into Aniyomi. A source is anything that can answer four questions:

* What entries (manga / anime) do you have?
* What are the details of a specific entry?
* What chapters / episodes does that entry have?
* What pages / videos does a given chapter / episode contain?

The app talks to sources **only** through these interfaces — it never references
the concrete classes inside an extension APK. That decoupling is what lets
extensions be loaded dynamically at runtime (see
[`../03-subsystems/source-system.md`](../03-subsystems/source-system.md)).

The module is **KMP** (`commonMain` + `androidMain`) because the same contract is
published to extension developers as a small library (`extensions-lib`), so the
non-Android parts of the API have to stay portable. The `androidMain` source set
fills in the few Android-specific typealiases.

## Build configuration

Source: `../ANIYOMI/source-api/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.injekt)
                api(libs.rxjava)
                api(libs.jsoup)
                api(aniyomilibs.nanohttpd)
                implementation(project.dependencies.platform(compose.bom))
                implementation(compose.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(libs.preferencektx)
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

android { namespace = "eu.kanade.tachiyomi.source" }
```

Notable points:

- **KMP** with a single Android target — `commonMain` is the published contract;
  `androidMain` provides the `actual` implementations of `expect` declarations.
- `commonMain` exposes (via `api`) the libraries an extension author needs:
  `kotlinx-serialization-json`, Injekt (DI), RxJava (legacy source API), Jsoup
  (HTML scraping), NanoHTTPD (extensions that spin up a local HTTP server).
- `androidMain` pulls in `:core:common` for the RxJava→coroutines bridge
  (`awaitSingle`) and `preferencektx` for `androidx.preference.PreferenceScreen`.
- `-Xexpect-actual-classes` lets `expect class PreferenceScreen` work as an
  `actual typealias` to `androidx.preference.PreferenceScreen`.
- The Android `namespace` is `eu.kanade.tachiyomi.source` — the manga root
  package. The anime root (`...animesource`) lives under the same namespace.

## KMP source set layout

```
source-api/src/
├── commonMain/kotlin/eu/kanade/tachiyomi/
│   ├── source/                       ← manga contract
│   │   ├── MangaSource.kt            ← root interface
│   │   ├── CatalogueSource.kt        ← adds browse/search
│   │   ├── SourceFactory.kt
│   │   ├── ConfigurableSource.kt
│   │   ├── UnmeteredSource.kt
│   │   ├── PreferenceScreen.kt       ← expect class
│   │   ├── online/
│   │   │   ├── HttpSource.kt         ← abstract OkHttp base
│   │   │   ├── ParsedHttpSource.kt   ← Jsoup-flavoured HttpSource
│   │   │   └── ResolvableSource.kt
│   │   └── model/                    ← SManga, SChapter, Page, MangasPage, Filter…
│   ├── animesource/                  ← anime contract (parallel to source/)
│   │   ├── AnimeSource.kt
│   │   ├── AnimeCatalogueSource.kt
│   │   ├── AnimeSourceFactory.kt
│   │   ├── ConfigurableAnimeSource.kt
│   │   ├── UnmeteredSource.kt
│   │   ├── PreferenceScreen.kt       ← expect class
│   │   ├── online/
│   │   │   ├── AnimeHttpSource.kt
│   │   │   ├── ParsedAnimeHttpSource.kt
│   │   │   └── ResolvableAnimeSource.kt
│   │   ├── model/                    ← SAnime, SEpisode, Video, Hoster, HttpServer…
│   │   └── utils/Preferences.kt
│   ├── torrentutils/
│   │   ├── TorrentUtils.kt
│   │   └── model/{TorrentFile.kt, TorrentInfo.kt}
│   └── util/
│       ├── JsoupExtensions.kt
│       ├── JsonExtensions.kt
│       ├── RxExtension.kt            ← expect fun Observable<T>.awaitSingle()
│       └── VideoInfo.kt
└── androidMain/kotlin/eu/kanade/tachiyomi/
    ├── source/PreferenceScreen.kt        ← actual typealias → androidx.preference.PreferenceScreen
    ├── animesource/PreferenceScreen.kt   ← actual typealias → androidx.preference.PreferenceScreen
    └── util/RxExtension.kt               ← actual fun → delegates to tachiyomi.core.common
```

**What's in `commonMain`** (the published extension contract):
- All interfaces and abstract base classes for both manga and anime.
- All model classes (`SManga`, `SChapter`, `Page`, `SAnime`, `SEpisode`, `Video`…).
- `expect class PreferenceScreen` (so sources can declare a preference screen
  without depending on Android).
- `expect fun Observable<T>.awaitSingle()` — the bridge that lets the modern
  `suspend` API delegate to the legacy RxJava `fetch*` methods.
- The `torrentutils` package (used by anime sources that stream via Torrserver).

**What's in `androidMain`** (the actual implementations):
- `actual typealias PreferenceScreen = androidx.preference.PreferenceScreen`
  for both `source` and `animesource` packages.
- `actual suspend fun <T> Observable<T>.awaitSingle()` — delegates to
  `tachiyomi.core.common.util.lang.awaitSingle` (the real RxJava→coroutines
  bridge in [`:core:common`](core-common.md)).
- An empty `AndroidManifest.xml` (the module ships no components).

## The two parallel hierarchies

Aniyomi's dual manga/anime nature shows up here as **two complete, parallel
class hierarchies**. They are structurally identical but use different names and
live in different packages. Both must be understood to write or maintain any
source-side code.

```
MANGA  (eu.kanade.tachiyomi.source.*)            ANIME  (eu.kanade.tachiyomi.animesource.*)
─────────────────────────────────────────────    ─────────────────────────────────────────────
interface MangaSource                  ◄────┐    interface AnimeSource                  ◄────┐
  │  getMangaDetails / getChapterList  │      │  getAnimeDetails / getEpisodeList       │
  │  getPageList                       │      │  getVideoList / getSeasonList           │
  │  (suspend — modern API)            │      │  getHosterList / getVideoList(hoster)   │
  │  + deprecated Rx fetch* methods    │      │  + deprecated Rx fetch* methods         │
  ▼                                    │      ▼                                         │
interface CatalogueSource              │    interface AnimeCatalogueSource             │
  : MangaSource                        │      : AnimeSource                             │
  │  supportsLatest                    │      │  supportsLatest                          │
  │  getPopularManga / getSearchManga  │      │  getPopularAnime / getSearchAnime        │
  │  getLatestUpdates                  │      │  getLatestUpdates                        │
  │  getFilterList()                   │      │  getFilterList()                         │
  ▼                                    │      ▼                                         │
abstract class HttpSource              │    abstract class AnimeHttpSource             │
  : CatalogueSource                    │      : AnimeCatalogueSource                   │
  │  baseUrl, versionId, id (MD5)      │      │  baseUrl, versionId, id (MD5)            │
  │  client (OkHttp), headers          │      │  client (OkHttp), headers                │
  │  popularMangaRequest / Parse       │      │  server: HttpServer?  (ext-lib 17)       │
  │  searchMangaRequest / Parse        │      │  popularAnimeRequest / Parse             │
  │  latestUpdatesRequest / Parse      │      │  searchAnimeRequest / Parse              │
  │  mangaDetailsRequest / Parse       │      │  seasonListRequest / Parse (ext-lib 16)  │
  │  chapterListRequest / Parse        │      │  hosterListRequest / Parse (ext-lib 16)  │
  │  pageListRequest / Parse           │      │  animeDetailsRequest / Parse             │
  │  imageUrlRequest / imageUrlParse   │      │  episodeListRequest / Parse              │
  │  getImage(page): Response          │      │  videoListRequest(episode) / Parse       │
  │  setUrlWithoutDomain helpers       │      │  videoListRequest(hoster) / Parse        │
  │  prepareNewChapter                 │      │  videoUrlRequest / videoUrlParse         │
  ▼                                    │      │  resolveVideo / sortHosters / sortVideos │
abstract class ParsedHttpSource        │      │  getVideo / getVideoSize / videoRequest  │
  : HttpSource()                       │      │  prepareNewEpisode                       │
  │  popularMangaSelector/FromElement/ │      ▼                                         │
  │  NextPageSelector                  │    abstract class ParsedAnimeHttpSource       │
  │  searchMangaSelector/…             │      : AnimeHttpSource()                       │
  │  latestUpdatesSelector/…           │      │  popularAnimeSelector/FromElement/Next…  │
  │  mangaDetailsParse(Document)       │      │  searchAnimeSelector/…                   │
  │  chapterListSelector/chapterFrom…  │      │  latestUpdatesSelector/…                 │
  │  pageListParse(Document)           │      │  animeDetailsParse(Document)             │
  │  imageUrlParse(Document)           │      │  episodeListSelector/episodeFromElement  │
                                       │      │  seasonListSelector/seasonFromElement    │
                                       │      │  hosterListSelector/hosterFromElement    │
                                       │      │  videoListSelector/videoFromElement      │
                                       │      │  videoUrlParse(Document)                 │
                                       │      │                                          │
interface SourceFactory                │    interface AnimeSourceFactory               │
  fun createSources(): List<MangaSource>│     fun createSources(): List<AnimeSource>   │
                                       │                                               │
interface ConfigurableSource           │    interface ConfigurableAnimeSource          │
  : MangaSource                        │      : AnimeSource                            │
  setupPreferenceScreen(screen)        │      setupPreferenceScreen(screen)            │
  getSourcePreferences()               │      getSourcePreferences()                   │
                                       │                                               │
interface UnmeteredSource              │    interface UnmeteredSource                  │
  (marker — self-hosted, no rate limit)│      (marker — self-hosted, no rate limit)    │
                                       │                                               │
interface ResolvableSource             │    interface ResolvableAnimeSource            │
  : MangaSource                        │      : AnimeSource                            │
  getUriType / getManga / getChapter   │      getUriType / getAnime / getEpisode       │
                                       └────  (sealed UriType: Manga / Chapter /       └────
                                              Anime / Episode / Unknown)
```

Both sides share `UriType` as a sealed interface (with `Manga`/`Chapter` only
existing in `ResolvableSource.kt` and `Anime`/`Episode` only in
`ResolvableAnimeSource.kt`). They do **not** share a common base — `MangaSource`
and `AnimeSource` are independent interfaces.

### The `id` of a source

Both `HttpSource` and `AnimeHttpSource` derive `id` the same way:

```kotlin
override val id by lazy { generateId(name, lang, versionId) }

protected fun generateId(name: String, lang: String, versionId: Int): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
        .reduce(Long::or) and Long.MAX_VALUE   // sign bit cleared
}
```

So `id` is a stable, deterministic 63-bit hash of `name/lang/versionId`.
`versionId` lets a source "fork" itself if its URL scheme changes, without
breaking library entries that point at the old id. `LocalMangaSource` and
`LocalAnimeSource` deliberately override this with the constant `0L`.

## The `source.model` package (manga)

Located at `commonMain/kotlin/eu/kanade/tachiyomi/source/model/`.

| Class / file | Role |
|---|---|
| `SManga` (interface) + `SMangaImpl` | A manga entry. Mutable `var`s: `url`, `title`, `artist`, `author`, `description`, `genre`, `status`, `thumbnail_url`, `update_strategy`, `initialized`. Status constants: `UNKNOWN=0`, `ONGOING=1`, `COMPLETED=2`, `LICENSED=3`, `PUBLISHING_FINISHED=4`, `CANCELLED=5`, `ON_HIATUS=6`. `getGenres()` splits the comma-separated `genre` string. |
| `SChapter` (interface) + `SChapterImpl` | A chapter. `url`, `name`, `date_upload` (epoch ms), `chapter_number` (Float), `scanlator`. `copyFrom(other)` for partial updates. |
| `Page` (open class, `@Serializable`) | One page of a chapter. `index`, `url` (page-list URL), `imageUrl` (resolved image URL), `status: State` (`QUEUE`/`LOAD_PAGE`/`DOWNLOAD_IMAGE`/`READY`/`ERROR`), `progress: Int` (0–100). Exposes `statusFlow` and `progressFlow` as `StateFlow` for reactive UI. Implements `ProgressListener` so the download manager can update `progress` directly. |
| `MangasPage` | `data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)` — the return type of browse/search/latest. |
| `Filter<T>` (sealed) + `FilterList` | Filter taxonomy used by `CatalogueSource.getFilterList()`. Subtypes: `Header`, `Separator`, `Select<V>`, `Text`, `CheckBox`, `TriState`, `Group<V>`, `Sort`. `FilterList` is a `@Stable` Compose-friendly list wrapper. |
| `UpdateStrategy` (enum) | `ALWAYS_UPDATE` or `ONLY_FETCH_ONCE`. Controls library-update behaviour; honoured by `LibraryUpdateJob` in `:app`. |

## The `animesource.model` package (anime)

Located at `commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/`. Note that
**`SAnime` here is a different class from `SManga`** in the manga model package
— they share a name pattern but live in separate packages and have diverging
fields.

| Class / file | Role |
|---|---|
| `SAnime` (interface) + `SAnimeImpl` | An anime entry. Same fields as `SManga` plus `background_url`, `season_number: Double`, and `fetch_type: FetchType`. Same status constants as `SManga`. |
| `SEpisode` (interface) + `SEpisodeImpl` | An episode. `url`, `name`, `date_upload`, `episode_number`, `fillermark: Boolean`, `scanlator`, `summary`, `preview_url`. `copyFrom(other)` for partial updates. |
| `Video` (data class) | A playable video. `videoUrl`, `videoTitle`, `resolution`, `bitrate`, `headers` (OkHttp `Headers`), `preferred`, `subtitleTracks: List<Track>`, `audioTracks: List<Track>`, `timestamps: List<TimeStamp>`, `mpvArgs`, `ffmpegStreamArgs`, `ffmpegVideoArgs`, `internalData`, `initialized`. Has a deprecated `url`/`quality` constructor for backwards compatibility with old extensions. `companion object` holds the `MPV_ARGS_TAG` constant. |
| `SerializableVideo` / `SerializableHoster` | JSON-serialisable mirrors of `Video` / `Hoster` (because `Video` carries non-serialisable OkHttp `Headers`). The `serialize()` / `toVideoList()` round-trip is used to pass video lists across IPC / save them. |
| `Track` (`@Serializable data class`) | A subtitle or audio track: `url`, `lang`. |
| `TimeStamp` (`@Serializable data class`) + `ChapterType` (enum) | A chapter marker inside a video: `start`, `end`, `name`, `type` (`Opening`/`Ending`/`Recap`/`MixedOp`/`Other`). Used by the player for skip-opening / chapter UI. |
| `AnimesPage` | `data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean)`. |
| `AnimeFilter<T>` + `AnimeFilterList` | Parallel to the manga `Filter`/`FilterList`. Same subtypes. |
| `AnimeUpdateStrategy` (enum) | Parallel to `UpdateStrategy`. |
| `FetchType` (enum, ext-lib 16) | `Seasons` or `Episodes` — declared on `SAnime` to choose whether the source will be queried via `getSeasonList` or `getEpisodeList`. Set once at initialisation. |
| `Hoster` (open class) + `SerializableHoster` | A video hoster (ext-lib 16). `hosterUrl`, `hosterName`, `videoList: List<Video>?`, `internalData`, `lazy: Boolean`. `status: State` (`IDLE`/`LOADING`/`READY`/`ERROR`). The `NO_HOSTER_LIST` sentinel is used by `List<Video>.toHosterList()` to wrap a flat video list as a single pseudo-hoster for sources that don't natively distinguish hosters. |
| `ThumbnailInfo` + `TileInfo` | (ext-lib 17) Metadata for generating seek-bar preview thumbnails. `tileInfo: List<TileInfo>` describes which `imageTileUrls` entry and crop rectangle to use for each preview position. |
| `HttpServer` (open class) | A `NanoHTTPD(0)` (random port) subclass. `url` is `http://localhost:$listeningPort`. Sources that need to expose local content over HTTP (e.g. for MPV) override `server` on `AnimeHttpSource`. The extension is responsible for starting it; the app closes it. |

## The `source.online` package (manga HTTP base classes)

`HttpSource` (`commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`)
is the abstract base for every HTTP-based manga source. It:

- Injects `NetworkHelper` via Injekt (`injectLazy()`) — sources get the shared
  OkHttp client, cookie jar, and default user-agent.
- Holds `baseUrl`, `versionId`, derived `id`, `headers` (built once via
  `headersBuilder()`, default user-agent added), and the `client` (default
  `network.client`; override per source for custom timeouts / interceptors).
- Implements the modern `suspend` API by delegating to the deprecated RxJava
  `fetch*` methods and calling `awaitSingle()` on the resulting `Observable`.
- Declares **abstract** request/parse pairs that subclasses must provide:

  | Operation | Request method | Parse method |
  |---|---|---|
  | Popular | `popularMangaRequest(page)` | `popularMangaParse(response)` |
  | Search | `searchMangaRequest(page, query, filters)` | `searchMangaParse(response)` |
  | Latest | `latestUpdatesRequest(page)` | `latestUpdatesParse(response)` |
  | Manga details | `mangaDetailsRequest(manga)` (open) | `mangaDetailsParse(response)` |
  | Chapter list | `chapterListRequest(manga)` (open) | `chapterListParse(response)` |
  | Page list | `pageListRequest(chapter)` (open) | `pageListParse(response)` |
  | Image URL | `imageUrlRequest(page)` (open) | `imageUrlParse(response)` |
  | Image bytes | `imageRequest(page)` (open) | — (returns `Response` directly) |

- Provides helpers: `setUrlWithoutDomain(url)` on `SChapter`/`SManga`,
  `getMangaUrl(manga)`, `getChapterUrl(chapter)`, `prepareNewChapter(chapter, manga)`,
  and `getFilterList()` returning an empty `FilterList`.

`ParsedHttpSource` (`online/ParsedHttpSource.kt`) is a Jsoup-flavoured subclass
that replaces each `*Parse(response)` method with selector-based hooks. Instead
of parsing the response body yourself, you supply:

- `popularMangaSelector()`, `popularMangaFromElement(element)`,
  `popularMangaNextPageSelector()` (and the parallel `searchManga*`/`latestUpdates*` triplets).
- `mangaDetailsParse(document: Document)` (note: takes a Jsoup `Document`, not a `Response`).
- `chapterListSelector()`, `chapterFromElement(element)`.
- `pageListParse(document: Document)`.
- `imageUrlParse(document: Document)`.

The base class handles `response.asJsoup()` for you. Roughly 95% of real-world
manga extensions extend `ParsedHttpSource` rather than `HttpSource` directly.

`ResolvableSource` (ext-lib 1.5) is an opt-in interface for sources that can
turn an external URL (e.g. a deep link or share intent) into an `SManga` or
`SChapter`. The app's deep-link handler iterates registered sources calling
`getUriType(uri)`; if it returns `UriType.Manga` / `UriType.Chapter`, the app
calls `getManga(uri)` / `getChapter(uri)` to resolve the entry.

## The `animesource.online` package (anime HTTP base classes)

`AnimeHttpSource` (`online/AnimeHttpSource.kt`) is the anime-side analogue of
`HttpSource` with the additions required for video:

- Same `network`/`baseUrl`/`versionId`/`id`/`headers`/`client` setup.
- `open val server: HttpServer? = null` (ext-lib 17) — a source may start a
  local HTTP server (e.g. to proxy or rewrite URLs for MPV).
- Browse/search/latest follow the same request/parse pattern as manga.
- Adds the **seasons** and **hosters** workflows (ext-lib 16):

  ```
  getSeasonList(anime)  ──▶  seasonListRequest(anime)  ──▶  seasonListParse(response)
  getHosterList(episode)──▶  hosterListRequest(episode)──▶  hosterListParse(response)
  getVideoList(hoster)  ──▶  videoListRequest(hoster)  ──▶  videoListParse(response, hoster)
  ```

- Also retains the **legacy flat `getVideoList(episode)`** path that returns a
  `List<Video>` directly (no hoster indirection) for sources that haven't
  migrated to the hoster API.
- Video fetching helpers: `getVideoUrl(video)` (resolved URL), `getVideo(request, listener)`
  (streaming download with progress), `getVideoSize(video, tries)` (uses a `Range:
  bytes=0-1` request and parses `Content-Range`), `videoRequest(video, start, end)`
  and `safeVideoRequest(video)` for ranged chunked requests.
- `resolveVideo(video): Video?` — hook for sources that need to follow
  redirects / extract real URLs from a player page.
- `getVideoThumbnails(video): ThumbnailInfo?` and `getImageTile(url): Bitmap?`
  (ext-lib 17) — for sources that can provide seek-preview thumbnails.
- `List<Hoster>.sortHosters()` and `List<Video>.sortVideos()` — overrides let
  sources apply user preference (e.g. preferred resolution / hoster order).
- `prepareNewEpisode(episode, anime)` — analogous to `prepareNewChapter`.

`ParsedAnimeHttpSource` mirrors `ParsedHttpSource`: it provides selector-based
hooks for browse, search, latest, details, episode list, **season list**,
**hoster list**, video list, and `videoUrlParse(document)`. Sources that scrape
HTML almost always extend this class.

`ResolvableAnimeSource` is the anime parallel of `ResolvableSource` (resolves
external URIs to `SAnime` / `SEpisode`).

## The `torrentutils` package

Located at `commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/`. Used by anime
sources that stream over BitTorrent via the bundled Torrserver (see
[`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)).

- `TorrentUtils.getTorrentInfo(url, title): TorrentInfo` — calls
  `TorrentServerApi.addTorrent(...)` (injected from `:core:common`), maps the
  response into a `TorrentInfo`. Throws `DeadTorrentException` on socket
  timeout, `DisabledTorrServerException` (from `:core:common`) on other errors.
- `model/TorrentInfo` — `title`, `files: List<TorrentFile>`, `hash`, `size`,
  `trackers`. `setTrackers(trackers)` returns a copy.
- `model/TorrentFile` — `path`, `indexFile`, `size`, plus private `torrentHash`
  and `trackers`. `toMagnetURI()` builds a `magnet:?xt=urn:btih:<hash>&tr=<…>`
  URI with `&index=<indexFile>` so the player can pick the right file inside a
  multi-file torrent.

## Utility code (`util/` and `animesource.utils/`)

| File | Role |
|---|---|
| `util/JsoupExtensions.kt` | `Element.selectText(css, default)`, `Element.selectInt(css, default)`, `Element.attrOrText(css)`, `Response.asJsoup(html?)` — turns an OkHttp `Response` into a Jsoup `Document` with the request URL as base. |
| `util/JsonExtensions.kt` | `val defaultJson: Json` — the app-wide `Json { ignoreUnknownKeys = true; explicitNulls = false }`, fetched from Injekt. (ext-lib 16) |
| `util/RxExtension.kt` | `expect suspend fun <T> Observable<T>.awaitSingle(): T` — bridges RxJava `fetch*` to coroutines. The `actual` in `androidMain` delegates to `tachiyomi.core.common.util.lang.awaitSingle`. |
| `util/VideoInfo.kt` | Small `sealed class Video` with `VideoUrl(url)` — utility for sources, unrelated to the `animesource.model.Video` data class. |
| `animesource/utils/Preferences.kt` | `AnimeSource.sourcePreferences()` / `sourcePreferences(key)` / `preferencesKey(id)` — the anime-side equivalent of the manga `ConfigurableSource.sourcePreferences()` helper. Lets any anime source get a `SharedPreferences` scoped to its id. (ext-lib 16) |

## How a source is implemented

A typical **manga** online source extends `ParsedHttpSource` (and usually
implements `ConfigurableSource` for preferences). Required overrides:

```kotlin
class MySource : ParsedHttpSource(), ConfigurableSource {
    override val name = "My Site"
    override val baseUrl = "https://example.com"
    override val lang = "en"
    override val supportsLatest = true

    // Browse
    override fun popularMangaRequest(page: Int): Request
    override fun popularMangaSelector(): String
    override fun popularMangaFromElement(e: Element): SManga
    override fun popularMangaNextPageSelector(): String?

    // Search (same triplet + request takes query + filters)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    override fun searchMangaSelector(): String
    override fun searchMangaFromElement(e: Element): SManga
    override fun searchMangaNextPageSelector(): String?

    // Latest (same triplet)
    override fun latestUpdatesRequest(page: Int): Request
    override fun latestUpdatesSelector(): String
    override fun latestUpdatesFromElement(e: Element): SManga
    override fun latestUpdatesNextPageSelector(): String?

    // Details / chapters / pages
    override fun mangaDetailsParse(document: Document): SManga
    override fun chapterListSelector(): String
    override fun chapterFromElement(e: Element): SChapter
    override fun pageListParse(document: Document): List<Page>
    override fun imageUrlParse(document: Document): String

    // Optional
    override fun getFilterList(): FilterList = …
    override fun setupPreferenceScreen(screen: PreferenceScreen) { … }
}
```

A typical **anime** online source extends `ParsedAnimeHttpSource`. The override
list is the same shape with `Anime`/`Episode` substituted for `Manga`/`Chapter`,
plus (optionally) the seasons / hosters workflow:

```kotlin
class MyAnimeSource : ParsedAnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "My Anime Site"
    override val baseUrl = "https://example.org"
    override val lang = "en"
    override val supportsLatest = true

    // Browse / details / episodes — parallel to manga
    override fun popularAnimeRequest(page: Int): Request
    override fun popularAnimeSelector(): String
    override fun popularAnimeFromElement(e: Element): SAnime
    override fun popularAnimeNextPageSelector(): String?
    // … searchAnime* / latestUpdates* / animeDetailsParse / episodeListSelector /
    //   episodeFromElement / videoListSelector / videoFromElement / videoUrlParse …

    // Optional seasons workflow (ext-lib 16)
    override fun seasonListSelector(): String
    override fun seasonFromElement(e: Element): SAnime

    // Optional hosters workflow (ext-lib 16)
    override fun hosterListRequest(episode: SEpisode): Request
    override fun hosterListSelector(): String
    override fun hosterFromElement(e: Element): Hoster
    override fun videoListRequest(hoster: Hoster): Request
    override fun videoListParse(response: Response, hoster: Hoster): List<Video>
    override fun resolveVideo(video: Video): Video?
    override fun List<Hoster>.sortHosters(): List<Hoster>
    override fun List<Video>.sortVideos(): List<Video>
}
```

A source is published either as a single class or via a `SourceFactory` /
`AnimeSourceFactory` that returns multiple sources from one extension APK. The
app's `ExtensionManager` (in `:app`) instantiates the factory and registers
each `MangaSource` / `AnimeSource` with the appropriate `SourceManager`.

The contract is **dual-API**: every browse/details/list method exists as a
modern `suspend fun get*` (which the app calls) and a deprecated
`fun fetch*: Observable<...>` (which `HttpSource`/`AnimeHttpSource` implement
under the hood and which old extensions may still override directly). New code
should only override the `*Request`/`*Parse`/selector hooks and let the base
classes wire the suspend API on top.

## The `androidMain`-only helpers

The Android source set is intentionally thin — most of the contract is portable.
What lives there:

- `actual typealias PreferenceScreen = androidx.preference.PreferenceScreen`
  (in both `source/` and `animesource/` packages) — this is what makes
  `ConfigurableSource.setupPreferenceScreen(screen: PreferenceScreen)` work.
  Extensions build their preference UI against the AndroidX Preference API;
  the `commonMain` `expect class` keeps the contract module Android-free.
- `actual suspend fun <T> Observable<T>.awaitSingle(): T` — delegates to
  `tachiyomi.core.common.util.lang.awaitSingle` (in `:core:common`). This is
  the single point that lets `commonMain`'s `getMangaDetails` / `getAnimeDetails`
  / etc. delegate to the deprecated `fetch*` methods without `commonMain`
  depending on `:core:common` directly.
- `AndroidManifest.xml` is empty — the module ships no components, only code.

## Key files table

| File | Purpose |
|---|---|
| `../ANIYOMI/source-api/build.gradle.kts` | KMP module config; `commonMain` deps: serialization, Injekt, RxJava, Jsoup, NanoHTTPD. `androidMain` deps: `:core:common`, preferencektx. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/MangaSource.kt` | Root manga interface. Declares `id`, `name`, `lang`, and the suspend `getMangaDetails` / `getChapterList` / `getPageList` (+ deprecated `fetch*`). |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt` | Adds browse/search/latest + `supportsLatest` + `getFilterList()`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt` | Abstract OkHttp base. `id` generation, request/parse abstract pairs, image fetching, URL helpers. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/ParsedHttpSource.kt` | Jsoup selector–based subclass. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/ResolvableSource.kt` | URI → `SManga` / `SChapter` resolver + `sealed interface UriType`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/SourceFactory.kt` | `fun createSources(): List<MangaSource>` — for multi-source extensions. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/ConfigurableSource.kt` | `setupPreferenceScreen(screen)` + `getSourcePreferences()` helpers. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/UnmeteredSource.kt` | Marker interface for self-hosted sources (no rate-limit warnings). |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/PreferenceScreen.kt` | `expect class PreferenceScreen`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SManga.kt` | Manga model interface + status constants. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/SChapter.kt` | Chapter model interface. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Page.kt` | Page model with `status`/`progress` `StateFlow`s; `@Serializable`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/MangasPage.kt` | `data class MangasPage(mangas, hasNextPage)`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Filter.kt` | `sealed class Filter<T>` taxonomy. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/FilterList.kt` | `@Stable` list wrapper. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/UpdateStrategy.kt` | `ALWAYS_UPDATE` / `ONLY_FETCH_ONCE`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt` | Root anime interface. Adds `getSeasonList`, `getHosterList`, `getVideoList(hoster)`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeCatalogueSource.kt` | Browse/search/latest + `getFilterList()`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt` | Abstract OkHttp base for anime. Adds seasons, hosters, video fetching, ranged requests, `HttpServer`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/ParsedAnimeHttpSource.kt` | Jsoup subclass for anime. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/online/ResolvableAnimeSource.kt` | URI → `SAnime` / `SEpisode` resolver. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/AnimeSourceFactory.kt` | Multi-source factory. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/ConfigurableAnimeSource.kt` | Anime-side preference source. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt` | Anime model interface (adds `background_url`, `season_number`, `fetch_type`). |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt` | Episode model (adds `fillermark`, `summary`, `preview_url`). |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt` | The `Video` data class + `Track`, `TimeStamp`, `ChapterType`, `SerializableVideo`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Hoster.kt` | `Hoster` + `SerializableHoster` + `List<Video>.toHosterList()`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/HttpServer.kt` | `NanoHTTPD(0)` wrapper for sources that need a local HTTP server. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/ThumbnailInfo.kt` | Seek-preview thumbnail metadata (ext-lib 17). |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/FetchType.kt` | `Seasons` / `Episodes`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/TorrentUtils.kt` | Torrserver bridge for anime torrent sources. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/model/TorrentFile.kt` | `toMagnetURI()` builder. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/model/TorrentInfo.kt` | Torrent metadata + `DeadTorrentException`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/util/JsoupExtensions.kt` | `Response.asJsoup()`, `Element.selectText/selectInt/attrOrText`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/util/JsonExtensions.kt` | `defaultJson` from Injekt. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/util/RxExtension.kt` | `expect fun Observable<T>.awaitSingle()`. |
| `../ANIYOMI/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/utils/Preferences.kt` | Anime `sourcePreferences()` helpers. |
| `../ANIYOMI/source-api/src/androidMain/kotlin/eu/kanade/tachiyomi/source/PreferenceScreen.kt` | `actual typealias` to `androidx.preference.PreferenceScreen`. |
| `../ANIYOMI/source-api/src/androidMain/kotlin/eu/kanade/tachiyomi/animesource/PreferenceScreen.kt` | Same, anime side. |
| `../ANIYOMI/source-api/src/androidMain/kotlin/eu/kanade/tachiyomi/util/RxExtension.kt` | `actual fun` delegating to `:core:common`. |

## See also

- [`source-local.md`](source-local.md) — the built-in `:source-local` module
  that implements both `CatalogueSource` and `AnimeCatalogueSource` for files
  on disk.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how `:app`'s `ExtensionManager` and `SourceManager` load extensions and
  dispatch calls through these interfaces.
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)
  — how the `torrentutils` package is used to stream anime over BitTorrent.
- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — the
  consumer of `Page` and `getImage`.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — the
  consumer of `Video`, `Hoster`, `Track`, `TimeStamp`.
- [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md)
  — how `getFilterList()` drives the browse UI.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — the
  higher-level module map.
- [`../01-architecture/01-architecture-overview.md`](../01-architecture/01-architecture-overview.md)
  — where `:source-api` sits in the layered architecture.
