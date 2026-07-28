# EVID-05 — Extension System Architecture

**Task ID:** EVID-05-EXTENSIONS
**Agent:** Explore (research-only)
**Scope:** Deep analysis of the ANIKUTA extension system — how extensions are loaded, registered, managed, and how the core app interfaces with them. Identifies what's extension-specific vs. generic, and what would need to change to support OTHER extension types (e.g., CloudStream-style) without touching core.
**Project root:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**No code was modified.** Every claim below is cited `file:line`.

---

## 0. Headline summary

- **`:core:source-api` is Android-only, NOT KMP** — despite the README claim (`core/source-api/README.md:3`). The `build.gradle.kts` uses `id("anikuta.library")` (Android library), not `kotlin("multiplatform")` (`core/source-api/build.gradle.kts:1-8`). The Aniyomi reference IS KMP (`_REFERENCES/ANIYOMI_REFRENCE/DOCUMENTATION/02-modules/source-api.md:24-27`). ANIKUTA deviated by collapsing to a single Android source set. There is no `commonMain`/`androidMain` split; everything lives in `src/main/kotlin/`.
- **The contract is verbatim Aniyomi-compatible** (ADR-029) — same package `eu.kanade.tachiyomi.animesource.*`, same interface names, same method signatures, same model field names. **Only one ANIKUTA-specific addition**: `ExtensionAppHolder` (`core/source-api/.../ConfigurableAnimeSource.kt:46-53`) replaces Injekt's `Application` injection because ANIKUTA uses Koin (ADR-023) for its own DI but still needs Injekt for extension bytecode compat (see `app/.../App.kt:50-79`).
- **36 Kotlin files, ~2,775 lines** in `:core:source-api`. 30 of 36 live under `eu.kanade.tachiyomi.*` (the Aniyomi package).
- **Extension loading pipeline** (`:data:extension`, 23 files, 3,238 lines): PackageManager scan → lib-version validate → SHA-256 signature trust check → `ChildFirstPathClassLoader` → reflectively instantiate `AnimeSource`/`AnimeSourceFactory` → wrap as `AnimeExtension.Installed`. Verified at `data/extension/.../loader/AnimeExtensionLoader.kt:64-195`.
- **Source IDs are deterministic and stable across reinstalls**: `MD5("name.lowercase()/lang/versionId").takeLowest64Bits() and Long.MAX_VALUE` — see `core/source-api/.../online/AnimeHttpSource.kt:114-118`. This is what makes the DB ↔ registry mapping work after an extension is uninstalled+reinstalled.
- **`:core:common` does NOT import `:core:source-api`** (`core/common/build.gradle.kts:9-15`). The domain models (`Anime`, `Episode`, `UnifiedAnime`, `AnimeDetailsProvider`, `DetailsRequest`) are source-agnostic. Only `HtmlToPlainText.kt:84-92` has a `mapSAnimeStatus(status: Int)` helper that takes a raw `Int` (no source-api import) — it's a one-way mapper.
- **`:core:download` is also clean** — declares `:core:source-api` as a dep in `build.gradle.kts:35` but only references `SEpisode`/`Track` in **comments** (`core/download/.../DownloadModels.kt:55-60`, `core/download/.../DownloadRequest.kt:9`). The download engine is pure: it accepts an already-resolved `DownloadRequest` (with `videoUrl` + headers), so it doesn't know what an `AnimeSource` is. Verified by grep: zero `import eu.kanade` statements in `:core:download`.
- **`:core:backup` persists extension source IDs** in `SourceLinkBackupProvider.kt:17-85` (AniList ID → sourceId+url) and `ExtensionLinkStore` (sourceId:url → anilistId). But it persists them as opaque `Long` values — it never imports the source-api types. The coupling is by data shape, not by type.
- **The extension contract surface in feature/app code is WIDE** (12 files import `eu.kanade.tachiyomi.animesource.*`). The widest touch-points: `:app/navigation/AppController.kt` (6 imports + many `SEpisode`/`SAnime`/`AnimeSource` parameter types) and `:feature:anime-details/AnimeDetailViewModel.kt` (6 imports). The narrowest extension-aware module is `:feature:extensions-settings` (zero `eu.kanade` imports — talks only to `AnimeExtensionManager` + `AnimeExtension` data classes).
- **Two distinct source-API generations are supported simultaneously**:
  - **ext-lib 1.5** (legacy): `getVideoList(episode): List<Video>` — flat video list.
  - **ext-lib 16+** (modern): `getHosterList(episode): List<Hoster>` + `getVideoList(hoster): List<Video>` — two-tier hierarchy with optional lazy resolution.
  - `ResolverService` (`feature/video-resolver/.../ResolverService.kt:85-139`) tries the hoster API first, falls back to the flat API. Default method bodies throw `IllegalStateException("Not used")` (`core/source-api/.../AnimeSource.kt:69, 78`) so the resolver can detect non-support via try/catch.
- **Source matching = title normalization + Levenshtein** (`data/extension/.../matcher/SourceMatcher.kt:346-378`). Threshold 0.80. Sequential priority-ordered search with exact-match short-circuit (`SourceMatcher.kt:233-284`). No metadata-based matching (no AniList ID ↔ source ID mapping at the source level).
- **The unified details layer is the GENERIC abstraction point**. `AnimeDetailsProvider` (`core/common/.../details/AnimeDetailsProvider.kt`) + `UnifiedAnime` (`core/common/.../details/UnifiedAnime.kt`) + `DetailsRequest` (`core/common/.../details/DetailsRequest.kt`) are the boundary. Adding a third data source = one new class + one Koin line (`app/.../di/DetailsModule.kt:38-62`). The existing two providers (`AniListDetailsProvider`, `ExtensionDetailsProvider`) are already evidence the pattern works.
- **To support CloudStream-style extensions without touching core, you would need to**:
  1. Introduce a parallel `MediaSource`/`MediaContent` contract (CloudStream uses a different metadata model — seasons/episodes with explicit season+episode numbers, different `Video` resolution semantics).
  2. Add a `MediaDetailsProvider : AnimeDetailsProvider` that maps the CloudStream model into `UnifiedAnime`.
  3. Add a `MediaExtensionLoader` + `MediaExtensionManager` that mirror `AnimeExtensionLoader`/`AnimeExtensionManager` but recognize a different `<uses-feature>` flag (e.g., `cloudstream.extension` instead of `tachiyomi.animeextension`).
  4. Either (a) generalize `SourceMatcher` to be format-agnostic, or (b) keep `SourceMatcher` Aniyomi-only and add a parallel `MediaSourceMatcher`.
  5. The video resolver (`ResolverService`) would need a parallel path because CloudStream videos have a different shape (no `Hoster` concept, no `Video.videoTitle` convention).

---

## 1. The `:core:source-api` contract — full inventory

### 1.1 Build configuration

**Path:** `core/source-api/build.gradle.kts:1-51`

```kotlin
plugins {
    id("anikuta.library")              // Android library — NOT KMP
    kotlin("plugin.serialization")
}

android { namespace = "app.confused.anikuta.core.sourceapi" }

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")   // for context(Json) in parseAs
    }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")              // Headers is a public Video field
    implementation("org.jsoup:jsoup:1.19.1")                        // ParsedAnimeHttpSource
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.reactivex:rxjava:1.3.8")                    // legacy fetch* API compat
    implementation("io.reactivex:rxandroid:1.2.1")                 // AndroidSchedulers
    implementation("org.nanohttpd:nanohttpd:2.3.1")                // HttpServer model
    api("com.github.mihonapp:injekt:91edab2317")                   // extensions resolve via Injekt
    compileOnly("com.github.skydoves:compose-stable-marker:1.0.5") // @Stable annotation
    implementation("androidx.preference:preference-ktx:1.2.1")     // PreferenceScreen typealias
}
```

**Critical binary-compat requirements (ADR-029):**
- `OkHttp` MUST be `api` — `Video.headers` is a public field of type `okhttp3.Headers`, so consumers must see the type (`build.gradle.kts:21-24`).
- `Injekt` MUST be `api` — extension bytecode calls `Injekt.get<T>()` and `injectLazy()` (`build.gradle.kts:42-46`).
- `GET()` MUST live in `Requests.kt` (compiles to file-class `RequestsKt`) — extension bytecode references `Leu/kanade/tachiyomi/network/RequestsKt;` (`core/source-api/.../network/Requests.kt:22-31`).
- `NetworkHelper` MUST be a `class` (not `interface`) — extension bytecode uses `invokevirtual NetworkHelper.getClient()`, an interface would throw `IncompatibleClassChangeError` (`core/source-api/.../network/NetworkHelper.kt:14-22`).
- `ProgressListener.update` MUST be the method name — extension bytecode calls `progressListener.update(bytesRead, contentLength, done)` (`core/source-api/.../network/ProgressListener.kt:7-15`).

### 1.2 Source-set layout (single source set, NOT KMP)

```
core/source-api/src/main/kotlin/eu/kanade/tachiyomi/
├── animesource/
│   ├── AnimeSource.kt                    ← root interface
│   ├── AnimeCatalogueSource.kt           ← adds browse/search/latest
│   ├── AnimeSourceFactory.kt             ← multi-source factory
│   ├── ConfigurableAnimeSource.kt        ← preferences + ExtensionAppHolder (ANIKUTA-specific)
│   ├── UnmeteredSource.kt                ← marker (self-hosted, no rate limit)
│   ├── PreferenceScreen.kt               ← typealias → androidx.preference.PreferenceScreen
│   ├── model/
│   │   ├── SAnime.kt                     ← interface + companion (status constants)
│   │   ├── SAnimeImpl.kt                 ← impl
│   │   ├── SEpisode.kt                   ← interface
│   │   ├── SEpisodeImpl.kt               ← impl
│   │   ├── Video.kt                      ← data class + SerializableVideo
│   │   ├── Hoster.kt                     ← open class + SerializableHoster
│   │   ├── AnimesPage.kt
│   │   ├── AnimeFilter.kt                ← sealed filter taxonomy
│   │   ├── AnimeFilterList.kt
│   │   ├── AnimeUpdateStrategy.kt        ← enum (ALWAYS_UPDATE, ONLY_FETCH_ONCE)
│   │   ├── FetchType.kt                  ← enum (Seasons, Episodes) — ext-lib 16
│   │   ├── HttpServer.kt                 ← NanoHTTPD wrapper (ext-lib 17)
│   │   └── ThumbnailInfo.kt              ← seek-preview tiles (ext-lib 17)
│   └── online/
│       ├── AnimeHttpSource.kt            ← abstract OkHttp base (~720 lines)
│       ├── ParsedAnimeHttpSource.kt      ← Jsoup-flavored AnimeHttpSource
│       └── ResolvableAnimeSource.kt      ← deep-link resolution (UriType sealed)
├── network/
│   ├── NetworkHelper.kt                  ← real class, registered in Injekt
│   ├── Requests.kt                       ← GET/POST/PUT/DELETE + OkHttpClient.get/post
│   ├── OkHttpExtensions.kt               ← asObservable / awaitSuccess / parseAs (context receiver)
│   ├── ProgressResponseBody.kt
│   ├── ProgressListener.kt
│   └── interceptor/
│       ├── UncaughtExceptionInterceptor.kt
│       ├── UserAgentInterceptor.kt
│       ├── IgnoreGzipInterceptor.kt
│       ├── RateLimitInterceptor.kt       ← internal, exposed via OkHttpClient.Builder.rateLimit
│       └── SpecificHostRateLimitInterceptor.kt
└── util/
    ├── RxExtension.kt                    ← Observable.awaitSingle() coroutine bridge
    ├── JsonExtensions.kt                 ← defaultJson
    ├── JsoupExtensions.kt                ← asJsoup, selectText, selectInt, attrOrText
    └── VideoInfo.kt                      ← sealed class Video + VideoUrl (unrelated to animesource.model.Video)
```

**File count:** 36 Kotlin files, 2,775 lines (per worklog AUDIT-FEATURES).

### 1.3 The `AnimeSource` interface — root contract

**Path:** `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt:13-113`

```kotlin
interface AnimeSource {
    val id: Long                                          // L18 — unique, deterministic
    val name: String                                      // L23
    val lang: String                                      // L25 — default ""

    // Modern suspend API (ext-lib 1.5+) — delegates to legacy fetch*
    suspend fun getAnimeDetails(anime: SAnime): SAnime               // L36
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>        // L48
    suspend fun getSeasonList(anime: SAnime): List<SAnime>           // L59 — ext-lib 16
    suspend fun getHosterList(episode: SEpisode): List<Hoster>       // L69 — ext-lib 16, default throws
    suspend fun getVideoList(hoster: Hoster): List<Video>            // L78 — ext-lib 16, default throws
    suspend fun getVideoList(episode: SEpisode): List<Video>         // L89 — legacy flat API

    // Deprecated RxJava API (ext-lib <1.5) — extensions override these
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime>         // L97
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>>  // L104
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>>   // L111
}
```

**Notable:** `getHosterList`/`getVideoList(hoster)` default to `throw IllegalStateException("Not used")` — this is how `ResolverService` detects whether a source implements the modern hoster API vs. only the legacy flat API.

### 1.4 `AnimeCatalogueSource` — adds browse/search

**Path:** `core/source-api/.../AnimeCatalogueSource.kt:8-80`

```kotlin
interface AnimeCatalogueSource : AnimeSource {
    override val lang: String                              // L13 — ISO 639-1 (required)
    val supportsLatest: Boolean                            // L18

    suspend fun getPopularAnime(page: Int): AnimesPage                            // L27
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage  // L40
    suspend fun getLatestUpdates(page: Int): AnimesPage                           // L51
    fun getFilterList(): AnimeFilterList                                          // L58

    // Deprecated RxJava equivalents (ext-lib <1.5)
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>                      // L65
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>  // L72
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>                     // L79
}
```

### 1.5 `AnimeHttpSource` — abstract OkHttp base

**Path:** `core/source-api/.../online/AnimeHttpSource.kt:35-722` (~720 lines)

Key members:

| Member | Line | Purpose |
|---|---|---|
| `protected val network: NetworkHelper by injectLazy()` | L51 | Resolves the shared OkHttp client via Injekt |
| `abstract val baseUrl: String` | L56 | Source's base URL (no trailing slash) |
| `open val versionId = 1` | L62 | Bumped when URL scheme changes — feeds `generateId` |
| `override val id by lazy { generateId(name, lang, versionId) }` | L74 | Deterministic MD5-based ID |
| `val headers: Headers by lazy { headersBuilder().build() }` | L79 | Default request headers |
| `open val client: OkHttpClient` | L84 | Defaults to `network.client` |
| `open val server: HttpServer? = null` | L95 | ext-lib 17 — local HTTP proxy for MPV |
| `protected fun generateId(name, lang, versionId): Long` | L114-118 | `MD5("name/lang/versionId").takeLowest64Bits and Long.MAX_VALUE` |
| `protected open fun headersBuilder()` | L123 | Adds default User-Agent |
| `abstract fun popularAnimeRequest(page: Int): Request` | L155 | |
| `abstract fun popularAnimeParse(response: Response): AnimesPage` | L162 | |
| `abstract fun searchAnimeRequest(page, query, filters): Request` | L198 | |
| `abstract fun searchAnimeParse(response: Response): AnimesPage` | L205 | |
| `abstract fun latestUpdatesRequest(page): Request` | L229 | |
| `abstract fun latestUpdatesParse(response): AnimesPage` | L236 | |
| `open fun animeDetailsRequest(anime: SAnime): Request` | L265 | `GET(baseUrl + anime.url, headers)` |
| `abstract fun animeDetailsParse(response: Response): SAnime` | L274 | |
| `protected open fun episodeListRequest(anime: SAnime): Request` | L303 | `GET(baseUrl + anime.url, headers)` |
| `abstract fun episodeListParse(response: Response): List<SEpisode>` | L312 | |
| `abstract fun episodeVideoParse(response: Response): SEpisode` | L319 | (unused — see ext-lib history) |
| `protected open fun seasonListRequest(anime): Request` | L345 | ext-lib 16 |
| `abstract fun seasonListParse(response: Response): List<SAnime>` | L356 | ext-lib 16 |
| `protected open fun hosterListRequest(episode): Request` | L382 | ext-lib 16 — `GET(baseUrl + episode.url)` |
| `abstract fun hosterListParse(response: Response): List<Hoster>` | L393 | ext-lib 16 |
| `protected open fun videoListRequest(hoster): Request` | L418 | ext-lib 16 — `GET(hoster.hosterUrl, headers)` |
| `abstract fun videoListParse(response: Response, hoster: Hoster): List<Video>` | L430 | ext-lib 16 |
| `open suspend fun resolveVideo(video: Video): Video?` | L440 | Hook for redirects/extract |
| `open suspend fun getVideoThumbnails(video: Video): ThumbnailInfo?` | L451 | ext-lib 17 |
| `open suspend fun getImageTile(url: String): Bitmap?` | L462 | ext-lib 17 |
| `override suspend fun getVideoList(episode: SEpisode): List<Video>` | L475 | Legacy flat API |
| `protected open fun videoListRequest(episode: SEpisode): Request` | L495 | `GET(baseUrl + episode.url)` |
| `abstract fun videoListParse(response: Response): List<Video>` | L504 | |
| `open fun List<Hoster>.sortHosters(): List<Hoster>` | L511 | Override for user prefs |
| `open fun List<Video>.sortVideos(): List<Video>` | L520 | Override for user prefs |
| `open suspend fun getVideoUrl(video: Video): String` | L541 | Resolved URL |
| `suspend fun getVideo(request, listener): Response` | L577 | Streaming download with progress |
| `fun getVideoSize(video, tries): Long` | L585 | Range request → Content-Range parse |
| `fun videoRequest(video, start, end): Request` | L614 | Range request builder |
| `fun safeVideoRequest(video): Request` | L640 | No-range request |
| `fun SEpisode.setUrlWithoutDomain(url)` | L652 | Strip scheme+host |
| `fun SAnime.setUrlWithoutDomain(url)` | L662 | Strip scheme+host |
| `open fun getAnimeUrl(anime: SAnime): String` | L694 | ext-lib 14 |
| `open fun getEpisodeUrl(episode: SEpisode): String` | L705 | ext-lib 14 |
| `open fun prepareNewEpisode(episode, anime)` | L716 | Hook for episode field overrides |
| `override fun getFilterList() = AnimeFilterList()` | L721 | Default empty filters |

### 1.6 Other source-api interfaces (small)

| File | Lines | Key API |
|---|---|---|
| `AnimeSourceFactory.kt:6-12` | 7 | `fun createSources(): List<AnimeSource>` |
| `ConfigurableAnimeSource.kt:15-53` | 39 | `fun setupPreferenceScreen(screen: PreferenceScreen)` + `getSourcePreferences()` + `ExtensionAppHolder` (ANIKUTA-specific). `preferenceKey()` = `"source_$id"` |
| `UnmeteredSource.kt:8` | 1 | Marker interface (no methods) |
| `ResolvableAnimeSource.kt:12-43` | 32 | `getUriType(uri): UriType`, `getAnime(uri): SAnime?`, `getEpisode(uri): SEpisode?`. Sealed `UriType { Anime, Episode, Unknown }` |
| `ParsedAnimeHttpSource.kt:17-261` | 245 | Adds Jsoup selector-based hooks for every abstract parse method |

### 1.7 The `model/` package — full field dump

#### `SAnime` (interface) — `core/source-api/.../model/SAnime.kt:7-69`

```kotlin
interface SAnime : Serializable {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?                              // comma-separated
    var status: Int                                 // 0..6 (see companion)
    var thumbnail_url: String?
    var background_url: String?
    var update_strategy: AnimeUpdateStrategy        // ALWAYS_UPDATE | ONLY_FETCH_ONCE
    var fetch_type: FetchType                       // Seasons | Episodes — ext-lib 16
    var season_number: Double                       // -1 = unspecified
    var initialized: Boolean                        // true after getAnimeDetails

    fun getGenres(): List<String>?                  // splits genre by ", "
    fun copy(): SAnime                              // shallow copy

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
        fun create(): SAnime = SAnimeImpl()
    }
}
```

#### `SEpisode` (interface) — `core/source-api/.../model/SEpisode.kt:7-41`

```kotlin
interface SEpisode : Serializable {
    var url: String
    var name: String
    var date_upload: Long                           // epoch ms
    var episode_number: Float                       // -1 = unspecified; .5 = special
    var fillermark: Boolean
    var scanlator: String?
    var summary: String?
    var preview_url: String?

    fun copyFrom(other: SEpisode)

    companion object { fun create(): SEpisode = SEpisodeImpl() }
}
```

#### `Video` (data class) — `core/source-api/.../model/Video.kt:29-103`

```kotlin
data class Video(
    var videoUrl: String = "",                      // the DIRECT video file URL (after resolution)
    val videoTitle: String = "",                    // human label, parsed by VideoTitleParser
    val resolution: Int? = null,                    // e.g. 1080
    val bitrate: Int? = null,
    val headers: Headers? = null,                   // OkHttp Headers (Referer, User-Agent, etc.)
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),  // chapter markers
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {
    @Deprecated val quality: String                 // L48 — backwards-compat (returns videoTitle)
    @Deprecated val url: String                     // L52 — backwards-compat (returns videoPageUrl)
    private var videoPageUrl: String = ""           // L56 — the source PAGE url (not the direct video)
    @Deprecated constructor(url, quality, videoUrl, ...) // L59-74 — legacy ctor
    @Deprecated constructor(url, quality, videoUrl, uri, ...) // L78-84 — legacy ctor

    @Transient @Volatile var status: State = State.QUEUE  // L88 — runtime state
    enum class State { QUEUE, LOAD_VIDEO, READY, ERROR }

    companion object { const val MPV_ARGS_TAG = "ANIYOMI_MPV_ARGS" }
}
```

Supporting types: `Track(url, lang)` (L10), `ChapterType` enum (L13-19), `TimeStamp(start, end, name, type)` (L22-27), `SerializableVideo` (L106-169) for JSON serialization.

#### `Hoster` (open class) — `core/source-api/.../model/Hoster.kt:9-50`

```kotlin
open class Hoster(
    val hosterUrl: String = "",                     // the hoster's page URL (for getVideoList(hoster))
    val hosterName: String = "",                    // human label — used as "server" name
    val videoList: List<Video>? = null,             // if non-null, lazy=false (pre-loaded)
    val internalData: String = "",
    val lazy: Boolean = false,                      // true → caller must call getVideoList(hoster)
) {
    @Transient @Volatile var status: State = State.IDLE
    enum class State { IDLE, LOADING, READY, ERROR }

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"  // sentinel for sources w/o hosters
        fun List<Video>.toHosterList(): List<Hoster> // wraps a flat video list as a single pseudo-hoster
    }
}
```

`SerializableHoster` (L53-86) mirrors it for JSON serialization (because `Video.headers` is non-serializable).

#### Other models

| Class | File:line | Fields |
|---|---|---|
| `AnimesPage` | `model/AnimesPage.kt:3` | `animes: List<SAnime>, hasNextPage: Boolean` |
| `AnimeFilter<T>` | `model/AnimeFilter.kt:3-46` | Sealed: `Header`, `Separator`, `Select<V>`, `Text`, `CheckBox`, `TriState`, `Group<V>`, `Sort` |
| `AnimeFilterList` | `model/AnimeFilterList.kt:4-15` | `data class AnimeFilterList(val list: List<AnimeFilter<*>>) : List<AnimeFilter<*>> by list` |
| `AnimeUpdateStrategy` | `model/AnimeUpdateStrategy.kt:10-23` | enum: `ALWAYS_UPDATE`, `ONLY_FETCH_ONCE` |
| `FetchType` | `model/FetchType.kt:10-20` | enum: `Seasons`, `Episodes` (ext-lib 16) |
| `HttpServer` | `model/HttpServer.kt:11-35` | `NanoHTTPD(0)` subclass; `url` = `http://localhost:$listeningPort`; ext-lib 17 |
| `ThumbnailInfo` + `TileInfo` | `model/ThumbnailInfo.kt:9-31` | Seek-preview thumbnails (ext-lib 17) |

### 1.8 ANIKUTA-specific additions on top of Aniyomi

1. **`ExtensionAppHolder`** (`ConfigurableAnimeSource.kt:46-53`):
   ```kotlin
   object ExtensionAppHolder {
       lateinit var app: Application
           private set
       fun init(application: Application) { app = application }
   }
   ```
   Replaces `Injekt.get<Application>()` for `ConfigurableAnimeSource.getSourcePreferences()`. The app calls `ExtensionAppHolder.init(this)` in `App.kt:48` BEFORE Koin starts.

2. **`AnimeLoadResult.UnrecognizedExtension`** (`data/extension/.../model/AnimeLoadResult.kt:20`):
   ```kotlin
   sealed interface AnimeLoadResult {
       data class Success(val extension: AnimeExtension.Installed) : AnimeLoadResult
       data class Untrusted(val extension: AnimeExtension.Untrusted) : AnimeLoadResult
       data object Error : AnimeLoadResult
       data object UnrecognizedExtension : AnimeLoadResult   // ANIKUTA addition
   }
   ```
   The Aniyomi reference has only three variants; ANIKUTA adds the fourth to make "this isn't an extension at all" (missing `<uses-feature>`) explicit.

3. **`NetworkHelper` is a real `class`, not an interface** (`network/NetworkHelper.kt:37-77`). Aniyomi's reference declares it as a class too — but ANIKUTA's KDoc explicitly calls out the binary-compat reason. The `cloudflareClient` field (L69) is the SAME as `client` because ANIKUTA doesn't have the WebView-based Cloudflare bypass yet.

4. **`defaultJson` is a top-level `val`** (`util/JsonExtensions.kt:13-16`), not `Injekt.get<Json>()`. ANIKUTA registers the Json singleton in Injekt from `App.kt:70-75`.

---

## 2. Extension loading pipeline (`:data:extension`)

### 2.1 Module overview

**Path:** `data/extension/`
**Files:** 23 Kotlin, 3,238 lines
**build.gradle.kts dependencies:** `:core:common`, `:core:source-api`, `:core:preferences`, `:core:update-checker`, `:core:anilist`, `:core:episode-metadata`, `:core:designsystem` + OkHttp + serialization + RxJava 1.x (`data/extension/build.gradle.kts:10-40`)

### 2.2 The loading pipeline (step-by-step)

```
App.kt onCreate
  ├─ ExtensionAppHolder.init(this)                         ← App.kt:48
  ├─ Injekt.addSingleton<Application>(this)                ← App.kt:56
  ├─ Injekt.addSingleton<NetworkHelper>(NetworkHelper(this)) ← App.kt:62-63
  ├─ Injekt.addSingletonFactory<Json>(...)                 ← App.kt:70-75
  └─ startKoin { modules(..., extensionModule, ...) }      ← App.kt:82-113
        ↓
extensionModule (app/.../di/ExtensionModule.kt:41-91) wires:
  single { TrustExtension(get<Context>()) }
  single { AnimeExtensionLoader(get()) }
  single { ExtensionRepoRepository(get<Context>()) }
  single { ExtensionRepoApi(get(named("extensionRepo")), get(named("extensionJson"))) }
  single { AnimeExtensionApi(get(), get()) }
  single { AnimeExtensionInstaller(get<Context>(), get(named("extensionRepo"))) }
  single { AnimeExtensionManager(get(), get(), get(), get(), get()) }
  single { SourceMatcher(get()) }
  single { ExtensionLinkStore(get()) }
  single { SourceLinkStore(get()) }
  single { DetailsViewPreferenceStore(get()) }
  single<EpisodeFetchGateway> { EpisodeFetchGatewayImpl(get()) }
        ↓
AnimeExtensionManager construction (data/extension/.../AnimeExtensionManager.kt:48-78):
  init {
      initExtensions()                                    ← L76
      ExtensionInstallReceiver(InstallationListener()).register(context)   ← L77
  }
  - initExtensions() calls loader.loadExtensions(context)   ← L96
  - partitions results into installedMap / untrustedMap    ← L97-100
  - sets _isInitialized = true                            ← L101
        ↓
AnimeExtensionLoader.loadExtensions(context) (loader/AnimeExtensionLoader.kt:64-81):
  1. pkgManager.getInstalledPackages(GET_CONFIGURATIONS | GET_META_DATA | GET_SIGNATURES | GET_SIGNING_CERTIFICATES)  ← L68-73
  2. extPkgs = installedPkgs.filter { isPackageAnExtension(it) }    ← L75
     - isPackageAnExtension: pkgInfo.reqFeatures.any { it.name == "tachiyomi.animeextension" }   ← L237-239
  3. runBlocking { extPkgs.map { async { loadExtension(context, it) } }.awaitAll() }    ← L78-80
        ↓
AnimeExtensionLoader.loadExtension(context, pkgInfo) (loader/AnimeExtensionLoader.kt:102-195):
  Step 1: Extract extName = appLabel.substringAfter("Aniyomi: ")    ← L110
  Step 2: Extract versionName, versionCode                            ← L111-112
  Step 3: Validate versionName non-null/non-empty                    ← L114-117
  Step 4: Parse libVersion = versionName.substringBeforeLast('.').toDouble()
          Reject if null or outside [12, 16]                          ← L120-124
          (LIB_VERSION_MIN=12, LIB_VERSION_MAX=16 — L270-271)
  Step 5: signatures = getSignatures(pkgInfo) — SHA-256 hex          ← L126
          getSignatures: signingInfo.apkContentsSigners OR signingCertificateHistory  ← L242-255
  Step 6: If !trustExtension.isTrusted(pkgInfo, signatures)          ← L131
            → return AnimeLoadResult.Untrusted(...)                  ← L132-141
  Step 7: Read isNsfw / isTorrent from manifest meta-data             ← L144-149
          If isNsfw && !trustExtension.loadNsfwSources() → Error
  Step 8: classLoader = ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)  ← L151-156
          (on exception → Error)
  Step 9: sourceClasses = appInfo.metaData.getString("tachiyomi.animeextension.class")  ← L158-162
          Split by ";", trim, resolve relative FQCNs (prefix with pkgName)
  Step 10: For each FQCN, instantiateSource(fqcn, appInfo, context, extName)  ← L164-167
  Step 11: Build AnimeExtension.Installed(...) with sources, pkgFactory, icon, isShared=true  ← L180-193
  Step 12: Return AnimeLoadResult.Success(installed)                  ← L194
        ↓
AnimeExtensionLoader.instantiateSource (loader/AnimeExtensionLoader.kt:198-234):
  Try ChildFirstPathClassLoader first:
    Class.forName(fqcn, false, cl).getDeclaredConstructor().newInstance()
    if AnimeSource → listOf(obj)
    if AnimeSourceFactory → obj.createSources()
    else → emptyList
  Catch LinkageError:
    Fall back to plain dalvik.system.PathClassLoader (parent-first)
    Same instantiation logic
  Catch Throwable:
    Log error, return emptyList
```

### 2.3 `ChildFirstPathClassLoader` — child-first class resolution

**Path:** `data/extension/.../loader/ChildFirstPathClassLoader.kt:27-54`

```kotlin
internal class ChildFirstPathClassLoader(
    dexPath: String,
    libraryPath: String?,
    parent: ClassLoader?,
) : PathClassLoader(dexPath, libraryPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            findClass(name)                    // L44 — extension's DEX first
        } catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)     // L46 — fall back to parent (app classpath)
        }
    }
}
```

**Purpose:** lets an extension bundle its own Jsoup/OkHttp/RxJava versions without clashing with the app's — they only need to be binary-compatible at the `:core:source-api` boundary. Matches the Aniyomi reference's `eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader`.

### 2.4 Extension discovery — package receiver

**Path:** `data/extension/.../installer/ExtensionInstallReceiver.kt:28-89`

- Registered dynamically by `AnimeExtensionManager.init { ... }` (`AnimeExtensionManager.kt:77`) — needs a `Listener` constructor arg, can't be manifest-declared.
- Listens for `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REPLACED` / `ACTION_PACKAGE_REMOVED` with `dataScheme="package"` (`ExtensionInstallReceiver.kt:47-53`).
- On any event → `listener.onPackageChanged(pkgName)` → manager re-scans all packages (`AnimeExtensionManager.kt:227-246`).
- `EXTRA_REPLACING` flag suppresses the duplicate ADDED+REMOVED pair during a package replace (`ExtensionInstallReceiver.kt:83-84`).

### 2.5 Trust / signature verification

**Path:** `data/extension/.../trust/TrustExtension.kt:19-84`

- Backed by SharedPreferences `anikuta_extension_trust` (`L68`).
- Key `trusted_extensions` holds a `Set<String>` of `"pkgName:versionCode:signatureHash"` entries.
- `isTrusted(pkgInfo, signatures)`: any signature in the trusted set matches `(pkgName, versionCode, sig)` (`L32-39`). Version code MUST match — an updated extension with a new version code must be re-trusted.
- `trust(pkgName, versionCode, signatureHash)`: adds entry (`L45-50`).
- `untrust(pkgName)`: removes ALL entries starting with `"pkgName:"` (`L57-62`).
- `loadNsfwSources()`: Boolean pref, default true (`L65`).

### 2.6 Extension metadata format — `AnimeExtension` sealed class

**Path:** `data/extension/.../model/AnimeExtension.kt:23-119`

```kotlin
sealed class AnimeExtension {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean
    abstract val isTorrent: Boolean

    data class Installed(
        ...,
        val pkgFactory: String?,              // meta-data "tachiyomi.animeextension.factory"
        val sources: List<AnimeSource>,       // LIVE source instances
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,                // true = system-installed
        val repoUrl: String? = null,
    ) : AnimeExtension()

    data class Available(
        ...,
        val sources: List<AnimeSourceMetadata>,   // NOT live — metadata only
        val apkName: String,                       // filename in <repo>/apk/
        val iconUrl: String,                       // <repo>/icon/<pkg>.png
        val repoUrl: String,
    ) : AnimeExtension() {
        data class AnimeSourceMetadata(val id: Long, val lang: String, val name: String, val baseUrl: String)
    }

    data class Untrusted(
        ...,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val isTorrent: Boolean = false,
    ) : AnimeExtension()
}
```

### 2.7 Source IDs — deterministic + stable

**Algorithm:** `core/source-api/.../online/AnimeHttpSource.kt:114-118`
```kotlin
protected fun generateId(name: String, lang: String, versionId: Int): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}
```

- 63-bit positive Long (sign bit cleared) — `Long.MAX_VALUE` mask.
- Stable across reinstalls as long as `name`/`lang`/`versionId` don't change.
- `versionId` lets a source "fork" itself if its URL scheme changes (bump versionId → new ID → library entries pointing at the old ID become orphans).
- This is what makes the DB ↔ registry mapping work after an extension is uninstalled+reinstalled.

### 2.8 Extension installer — PackageInstaller backend

**Path:** `data/extension/.../installer/`

- `AnimeExtensionInstaller` (`AnimeExtensionInstaller.kt:38-159`): downloads APK via OkHttp, dispatches to `ExtensionInstallService`. Serial installs via `Mutex` (L44). Temp APK always deleted on success/failure (L88, L119).
- `ExtensionInstallService` (`ExtensionInstallService.kt:43-150`): foreground service, one install per `startService`. Calls `startForeground` immediately (Android 12+ requirement, L60).
- `PackageInstallerBackend` (`PackageInstallerBackend.kt:35-160`): `PackageInstaller.Session` API with `MODE_FULL_INSTALL`. Streams APK via `openWrite`+`copyTo`. Listens on `BroadcastReceiver` for `STATUS_PENDING_USER_ACTION`/`STATUS_SUCCESS`/`STATUS_FAILURE_*`. `USER_ACTION_NOT_REQUIRED` on Android S+ for silent installs (L112-114).
- `InstallStep` enum (`InstallStep.kt:12-33`): `Idle, Pending, Downloading, Installing, Installed, Error`.

**Deferred installer backends** (per `:data:extension/README.md:87-88`): Legacy (ACTION_INSTALL_PACKAGE), Private (`.ext` files), Shizuku. Only PackageInstaller is implemented.

---

## 3. Source registration & lifecycle

### 3.1 How a loaded extension registers its sources

**There is NO separate "SourceManager" registry.** Unlike Aniyomi (which has `AndroidAnimeSourceManager` with a `ConcurrentHashMap<Long, AnimeSource>` — per `_REFERENCES/ANIYOMI_REFRENCE/DOCUMENTATION/03-subsystems/source-system.md:186-225`), ANIKUTA's sources live directly inside `AnimeExtension.Installed.sources: List<AnimeSource>` (`model/AnimeExtension.kt:57`).

The manager exposes three `StateFlow<List<...>>` (`AnimeExtensionManager.kt:66-73`):
- `installedExtensionsFlow: StateFlow<List<AnimeExtension.Installed>>`
- `availableExtensionsFlow: StateFlow<List<AnimeExtension.Available>>`
- `untrustedExtensionsFlow: StateFlow<List<AnimeExtension.Untrusted>>`

Sources are looked up by **flattening** the installed extensions:
```kotlin
// SourceMatcher.kt:330-334
private fun getCatalogueSources(): List<AnimeCatalogueSource> {
    return extensionManager.getInstalledExtensions()
        .flatMap { it.sources }
        .filterIsInstance<AnimeCatalogueSource>()
}
```

**`getSourceById(sourceId: Long)`** (`SourceMatcher.kt:340-342`):
```kotlin
fun getSourceById(sourceId: Long): AnimeCatalogueSource? {
    return getCatalogueSources().firstOrNull { it.id == sourceId }
}
```

**`getExtensionPackage(sourceId: Long): String?`** (`AnimeExtensionManager.kt:88-89`):
```kotlin
fun getExtensionPackage(sourceId: Long): String? =
    installedMap.value.values.firstOrNull { ext -> ext.sources.any { it.id == sourceId } }?.pkgName
```

### 3.2 What happens when an extension is uninstalled?

1. System fires `ACTION_PACKAGE_REMOVED` → `ExtensionInstallReceiver.onReceive` (`ExtensionInstallReceiver.kt:69-73`).
2. `listener.onPackageChanged(pkgName)` → manager re-scans (`AnimeExtensionManager.kt:230-234`).
3. The extension is no longer in the installed list → its sources are no longer in `getCatalogueSources()`.
4. If the package is truly gone, `trustExtension.untrust(pkgName)` is called (`AnimeExtensionManager.kt:241-243`).
5. **DB rows persist**: the `animes` table's `source_id` column still references the old ID. `AnimeRepository.getBySourceAndUrl(sourceId, url)` will return the row, but `SourceMatcher.getSourceById(sourceId)` returns null.
6. **`SourceLinkStore` entries persist** (`cache/SourceLinkStore.kt:22-69`): the AniList ID → (sourceId, animeUrl, animeTitle) mapping stays in SharedPreferences. On re-open, `ExtensionDetailsProvider.load` (`details/ExtensionDetailsProvider.kt:134-138`) logs `"Extension source $sourceId not installed — cannot load"` and returns null.
7. **Downloads do NOT break** — they're keyed by `anilistId` + `episodeUrl` (`core/download/.../DownloadModels.kt:25-31`), not by source ID. The `sourceId` is stored on `DownloadRequest` (`DownloadRequest.kt:39`) but only for logging + future re-download.
8. **Reinstall path**: when the user reinstalls the same extension, `generateId(name, lang, versionId)` produces the SAME source ID (deterministic), so all DB rows + source links + extension links resolve back to the live source automatically.

### 3.3 The trust lifecycle

```
User installs APK (via PackageInstaller)
  → ACTION_PACKAGE_ADDED → manager re-scans
  → loader.loadExtension returns Untrusted (signature not in trusted set)
  → manager moves it to untrustedExtensionsFlow

User taps "Trust" in ExtensionsSettingsScreen
  → extensionManager.trust(extension)  (AnimeExtensionManager.kt:173-194)
  → trustExtension.trust(pkgName, versionCode, signatureHash) writes to SharedPreferences
  → untrustedMap.value = untrustedMap.value - extension.pkgName
  → loader.loadExtensionFromPkgName(context, pkgName) re-runs
    → this time isTrusted returns true → sources load
  → installedMap.value += (pkgName → installed)

User taps "Untrust" on an installed extension
  → extensionManager.untrust(extension)  (AnimeExtensionManager.kt:200-217)
  → trustExtension.untrust(pkgName) removes from SharedPreferences
  → installedMap.value -= pkgName
  → untrustedMap.value += (pkgName → Untrusted(...))  // reconstructed
```

---

## 4. The interface between core app and extensions

### 4.1 Where the core touches extension code — interface-surface table

**Question:** does `:core:common` import from `:core:source-api`?

**Answer: NO.** Verified by:
- `core/common/build.gradle.kts:9-15` — only depends on `kotlinx.coroutines.core` + test libs.
- Grep for `import eu.kanade` in `core/common/` → 0 matches.

The 7 files in `:core:common` that mention `sourceId`/`SAnime`/`SEpisode`/`AnimeSource`/`Hoster`/`Video`:
- `repository/AnimeRepository.kt:25,33` — `sourceId: Long` parameters (typed as `Long`, NOT `AnimeSource`).
- `model/Anime.kt:32` — `val sourceId: Long` field.
- `model/details/AnimeDetailsProvider.kt:8,15,18,27` — KDoc references to "extension `SAnime`" but no actual import.
- `model/details/DataSource.kt:11` — KDoc references to "Aniyomi-compatible `AnimeCatalogueSource`".
- `model/details/HtmlToPlainText.kt:84-92` — `mapSAnimeStatus(status: Int): UnifiedStatus` takes a raw `Int` (the source-api `SAnime.status` constant). No import.
- `model/details/DetailsRequest.kt:9-12` — KDoc says "carries only primitive identity fields so the [AnimeDetailsProvider] interface can live in :core:common without a dependency on :core:source-api".
- `model/details/UnifiedAnime.kt:24,61,84,98,99` — KDoc references to "extension `SAnime`".

**Conclusion:** `:core:common` is **fully decoupled** from the extension contract. The `UnifiedAnime` model is source-agnostic; the translation happens in `:data:anime` (AniListDetailsProvider) and `:data:extension` (ExtensionDetailsProvider).

### 4.2 Interface-surface table — every file that imports `eu.kanade.tachiyomi.animesource.*`

Grep results for `import eu.kanade.tachiyomi.animesource` across the codebase (30 files total):

#### Core / data layer (5 files)

| File | Imports | What it does |
|---|---|---|
| `core/source-api/.../AnimeSource.kt` | (defines) | The contract itself |
| `core/source-api/.../AnimeCatalogueSource.kt` | (defines) | The contract itself |
| `core/source-api/.../online/AnimeHttpSource.kt` | (defines) | The contract itself |
| `core/source-api/.../online/ParsedAnimeHttpSource.kt` | (defines) | The contract itself |
| `core/source-api/.../online/ResolvableAnimeSource.kt` | (defines) | The contract itself |
| `core/source-api/.../model/Hoster.kt` | (defines) | The model itself |
| `data/extension/.../loader/AnimeExtensionLoader.kt:14-16` | `AnimeCatalogueSource`, `AnimeSource`, `AnimeSourceFactory` | Loads + instantiates source classes |
| `data/extension/.../model/AnimeExtension.kt:4` | `AnimeSource` | `Installed.sources: List<AnimeSource>` field type |
| `data/extension/.../matcher/SourceMatcher.kt:5-7` | `AnimeCatalogueSource`, `AnimeFilterList`, `SAnime` | Searches sources by title |
| `data/extension/.../details/SAnimeMapper.kt:7` | `SAnime` | Maps `SAnime` → `UnifiedAnime` |
| `data/extension/.../details/ExtensionDetailsProvider.kt:18-20` | `AnimeCatalogueSource`, `SAnime`, `SAnimeImpl` | The extension→UnifiedAnime provider |
| `data/anime/.../details/AniListDetailsProvider.kt:20-21` | `SAnimeImpl`, `SEpisodeImpl` | Reconstructs SAnime from saved links to call `source.getEpisodeList` |

#### Feature layer (8 files)

| File | Imports | What it does |
|---|---|---|
| `feature/anime-details/.../AnimeDetailViewModel.kt:25-30` | `AnimeCatalogueSource`, `AnimeSource`, `SAnime`, `SAnimeImpl`, `SEpisode`, `SEpisodeImpl` | Holds current match, builds `DetailsRequest.ByExtension`, converts DB `Episode` → `SEpisode` for the UI |
| `feature/anime-details/.../EpisodesSection.kt:49-52` | `AnimeCatalogueSource`, `AnimeSource`, `SAnime`, `SEpisode` | Episode list UI — callbacks take `SEpisode`/`AnimeSource` |
| `feature/anime-details/.../AnimeDetailScreen.kt:27-30` | `AnimeCatalogueSource`, `AnimeSource`, `SAnime`, `SEpisode` | Top-level screen signature — `extensionSAnime: SAnime?` parameter |
| `feature/anime-details/.../DetailContent.kt:22-25` | `AnimeCatalogueSource`, `AnimeSource`, `SAnime`, `SEpisode` | Content body — callback signatures |
| `feature/search/.../viewmodel/ExtensionLinkingViewModel.kt:10-11` | `AnimeCatalogueSource`, `SAnime` | Holds the tapped extension result for AniList linking |
| `feature/search/.../viewmodel/SearchViewModel.kt:12-13` | `AnimeCatalogueSource`, `SAnime` | `SearchResult.Extension(source, sAnime, sourceName)` sealed variant |
| `feature/search/.../ui/ResultAnimeCard.kt:32` | `SAnime` | Renders an extension result card |
| `feature/search/.../ui/ExtensionLinkingSheet.kt:65-66` | `AnimeCatalogueSource`, `SAnime` | The linking sheet signature |
| `feature/search/.../ui/ExtensionResultsView.kt:38` | `SAnime` | Renders extension search results |
| `feature/video-resolver/.../ResolverService.kt:4-7` | `AnimeSource`, `Hoster`, `SEpisode`, `Video` | Resolves videos from a source |
| `feature/video-resolver/.../VideoResolverStrategy.kt:4` | `Video` | Groups videos into the 3-tier hierarchy |
| `feature/video-resolver/.../VideoTitleParser.kt:3-4` | `Hoster`, `Video` | Parses video titles for audio/quality |
| `feature/watch/.../WatchScreen.kt:91` | `SEpisode` | Episode-switching UI uses `SEpisode` |
| `feature/watch/.../WatchRequest.kt:5-6,29,33` | `AnimeSource`, `SEpisode` | `WatchRequest.source: AnimeSource?`, `episodeList: List<SEpisode>` |

#### App layer (4 files)

| File | Imports | What it does |
|---|---|---|
| `app/.../download/DownloadOrchestrator.kt:18-19` | `AnimeSource`, `SEpisode` | `enqueueDownload(anime, episode: SEpisode, source: AnimeSource)` |
| `app/.../navigation/Destinations.kt:40-41,160` | `AnimeCatalogueSource`, `SAnime` | `ExtensionAnimeDetailDestination(source, sAnime, anilistId)` Voyager screen |
| `app/.../navigation/AppController.kt:36-39` | `AnimeCatalogueSource`, `AnimeSource`, `SEpisode`, `SAnime` | Central state holder — `linkingTarget`, `resolveTarget`, `pendingExtensionSAnime` |
| `app/.../App.kt:20-21` | `ExtensionAppHolder`, `NetworkHelper` | Initializes Injekt singletons for extension compat |

#### Summary

- **30 files** import from `eu.kanade.tachiyomi.animesource.*`.
- **5 of them are the contract itself** (`:core:source-api`).
- **12 are in feature/app layers** — these are the touch-points that would need to be generalized to support a second extension format.
- **`:core:common` and `:core:download` are CLEAN** (0 imports each).
- **`:core:backup` is clean by type** but persists `sourceId: Long` values opaquely (5+ files reference `sourceId`, but none import source-api types).

### 4.3 Is the interface narrow or wide?

**Wide.** The extension contract (`SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeSource`) leaks into:
- 4 feature modules (`anime-details`, `search`, `video-resolver`, `watch`)
- 2 app modules (`navigation/AppController`, `download/DownloadOrchestrator`)
- 2 data modules (`data:anime/details/AniListDetailsProvider`, `data:extension/*`)

The narrow abstraction is `AnimeDetailsProvider` + `UnifiedAnime` + `DetailsRequest` (in `:core:common`) — but only `AnimeDetailViewModel` and the two providers actually USE that abstraction. The watch flow, download flow, video-resolver flow, and search flow all thread raw `SAnime`/`SEpisode`/`AnimeSource` types directly through their signatures.

---

## 5. Source types — what exists today

### 5.1 One source type, two API generations

There is **only one source "type"** today: `AnimeCatalogueSource` (the Aniyomi-compatible HTTP source). There is no "Cloudflare source" or "torrent source" subclass — those are behaviors implemented inside individual source classes, not separate types.

However, the source-api supports **two API generations** for video resolution:

**Generation 1 (ext-lib 1.5, "flat API"):**
```kotlin
suspend fun getVideoList(episode: SEpisode): List<Video>
```
The source returns a flat list of `Video` objects for an episode. No hoster indirection.

**Generation 2 (ext-lib 16+, "hoster API"):**
```kotlin
suspend fun getHosterList(episode: SEpisode): List<Hoster>      // first step
suspend fun getVideoList(hoster: Hoster): List<Video>            // second step (lazy hosters only)
```
The source returns a list of `Hoster` objects (e.g., "Vidstream", "Mp4Upload"). Each hoster can either:
- Have `videoList: List<Video>?` pre-populated (non-lazy), OR
- Be `lazy: Boolean = true` — the caller must invoke `getVideoList(hoster)` to fetch the videos.

### 5.2 How videos are resolved (the resolver tries these in order)

**Path:** `feature/video-resolver/.../ResolverService.kt:85-139`

```
ResolverService.resolve(source, episode)
  ↓
resolveVideoEntries(source, episode):
  1. Try source.getHosterList(episode) with 30s timeout    ← L87-97
     - Catch IllegalStateException → "source doesn't support getHosterList, falling back"
     - Catch Exception → log + empty list
  2. If hosters.isNotEmpty():                               ← L99-126
     For each hoster:
       a. If hoster.videoList != null && non-empty → use those videos directly  ← L104-109
       b. Else (lazy hoster) → call source.getVideoList(hoster) with 30s timeout  ← L111-123
     Return VideoEntry list (each has Video + hosterName)
  3. Else (no hosters):                                     ← L128-138
     Fall back to source.getVideoList(episode) with 30s timeout
     Return VideoEntry list with hosterName=null
  ↓
Filter out entries where video.videoUrl.isBlank()            ← L42
  ↓
Pick strategy via ResolverStrategyPicker.pick(videos, hasHosterNames):  ← L57
  - If hasHosterNames → StructuredResolverStrategy
  - Else if ≥50% of videos have detectable structure (server/audio/quality tokens) → Structured
  - Else → RawResolverStrategy (flat list, no forced formatting)
  ↓
strategy.resolve(videos, hosterNames) → List<ResolverServer>
  - StructuredResolverStrategy: VideoTitleParser.groupVideosByServer → 3-tier hierarchy (Server → Audio → Quality)
  - RawResolverStrategy: flat list under "All Videos" / "Default"
  ↓
If structured returns empty → fall back to raw              ← L62-69
```

**`ResolverServer` / `ResolverAudioVersion` / `ResolverVideo`** (`feature/video-resolver/.../VideoResolverState.kt:28-51`):
```kotlin
data class ResolverServer(val name: String, val audioVersions: List<ResolverAudioVersion>)
data class ResolverAudioVersion(val label: String, val videos: List<ResolverVideo>)
data class ResolverVideo(
    val quality: String,          // e.g. "1080p"
    val url: String,              // direct video URL
    val videoTitle: String = "",
    val videoHeaders: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<SubtitleTrack> = emptyList(),
)
```

This is a **translation layer** — the source-api `Video`/`Hoster` types never leave `:feature:video-resolver`. The watch flow and download flow receive `ResolverVideo`/`ResolverServer` instead. This is the second clean abstraction point (after `UnifiedAnime`).

---

## 6. Extension-specific assumptions baked into core

### 6.1 `:core:common` — CLEAN ✅

- `build.gradle.kts:9-15` — no source-api dependency.
- 0 `import eu.kanade` statements.
- The `UnifiedAnime`/`AnimeDetailsProvider`/`DetailsRequest`/`AnimeDetailsProviderRegistry` types are source-agnostic.
- `HtmlToPlainText.mapSAnimeStatus(status: Int)` takes a raw `Int` — it's a one-way mapper, no source-api import. The int constants it accepts (0–6) happen to match `SAnime.UNKNOWN`..`SAnime.ON_HIATUS` but the function doesn't know that.

### 6.2 `:data:anime` (the anime repository) — COUPLED ⚠️

- `build.gradle.kts:20-21` — depends on `:core:source-api` AND `:data:extension`.
- `details/AniListDetailsProvider.kt:20-21` — imports `SAnimeImpl`, `SEpisodeImpl`.
- The provider reconstructs `SAnime` from a saved `SourceLinkStore` link (just URL + title) so it can call `source.getEpisodeList(sAnime)` (`AniListDetailsProvider.kt:101-105, 113-117, 153-156`).
- `EpisodeMapper.kt` and `AnimeMapper.kt` map DB rows ↔ domain `Episode`/`Anime` — they do NOT touch source-api types.
- `AnimeRepositoryImpl`, `EpisodeRepositoryImpl`, `CategoryRepositoryImpl` — pure SQLDelight, no source-api.

**Conclusion:** The coupling in `:data:anime` is **isolated to `AniListDetailsProvider`**. The repository layer is clean.

### 6.3 `:core:download` — CLEAN by type, COUPLED by comment ⚠️→✅

- `build.gradle.kts:35` — declares `implementation(projects.core.sourceApi)`.
- But grep for `import eu.kanade` in `:core:download` → **0 matches**.
- The `SEpisode` references in `DownloadModels.kt:55-60` and `DownloadRequest.kt:9` are **comments only** — "Carries the [SEpisode]-equivalent fields" / "Video URL resolution is orchestrated by :app's DownloadOrchestrator".
- `DownloadModels.kt:36-39` explicitly says: "We use our own serializable type so `:core:download` does not leak the source-api `Track` into its persisted store".
- **The `:core:source-api` dependency in `build.gradle.kts:35` is for the OkHttp `api` exposure** (transitive — comes via source-api). The download engine uses OkHttp directly but gets it through source-api's `api("com.squareup.okhttp3:okhttp")`.

**Conclusion:** `:core:download` is **functionally clean** — it accepts an already-resolved `DownloadRequest` and never imports source-api types. The build.gradle dep is for OkHttp transitively. **Rule §14 is satisfied** — `DownloadOrchestrator` lives in `:app` (`app/.../download/DownloadOrchestrator.kt`) precisely to keep the resolve→enqueue orchestration out of the download engine.

### 6.4 `:core:backup` — CLEAN by type, persists `sourceId: Long` opaquely ✅

- `build.gradle.kts` — does NOT depend on `:core:source-api` (verified via grep — no `sourceApi` in the deps).
- `provider/SourceLinkBackupProvider.kt:9-10` imports `ExtensionLinkStore` and `SourceLinkStore` from `:data:extension` — but only to read/write the `Map<String, SourceLink>` / `Map<String, Int>` shapes.
- `model/SourceLinkBackup.kt:18-30`:
  ```kotlin
  data class SourceLinkBackup(
      val sourceLinks: Map<String, SourceLinkEntry>,      // AniList ID → (sourceId, animeUrl, animeTitle)
      val extensionLinks: Map<String, Int>,               // "$sourceId:$animeUrl" → AniList ID
  )
  data class SourceLinkEntry(val anilistId: Int, val sourceId: Long, val animeUrl: String, val animeTitle: String)
  ```
  The `sourceId` is a raw `Long` — no source-api type. If the extension format changed, the backup would still restore (the source ID would just be a stale number that no installed source matches).
- The Aniyomi backup format (`format/aniyomi/AniyomiBackupModels.kt:147-149`) has `AniyomiBackupAnimeSource(sourceId: Long, name: String, lang: String)` — same pattern, opaque Long.

**Conclusion:** `:core:backup` is **fully decoupled** from the source-api contract. It persists source IDs as opaque Longs. ✅

### 6.5 Summary of extension-specific assumptions in `:core:*`

| Module | Imports source-api? | Persists source IDs? | Coupling level |
|---|---|---|---|
| `:core:common` | ❌ No | ✅ Yes (via `Anime.sourceId: Long`) | **None** — source-agnostic by design |
| `:core:source-api` | (defines it) | ❌ No | **The contract itself** |
| `:core:download` | ⚠️ Build dep only (for OkHttp transitive) | ✅ Yes (`DownloadRequest.sourceId: Long`) | **None** — accepts resolved URLs |
| `:core:backup` | ❌ No | ✅ Yes (opaque Long in SourceLinkBackup) | **None** — persists opaque Longs |
| `:core:database` | ❌ No | ✅ Yes (`animes.source_id INTEGER`) | **None** — SQLDelight schema |
| `:core:anilist` | ❌ No | ❌ No | **None** |
| `:core:player` | ❌ No | ❌ No | **None** |
| `:core:tracker` | ❌ No | ❌ No | **None** |
| `:core:episode-metadata` | ❌ No | ❌ No | **None** |
| `:core:update-checker` | ❌ No | ❌ No (uses `EpisodeFetchGateway` interface) | **None** — the gateway is implemented in `:data:extension` (`EpisodeFetchGatewayImpl`) |

**The only `:core:*` module that COUPLES to extension specifics is `:core:source-api` itself** — which IS the contract. Every other core module is clean.

---

## 7. Source matching & linking

### 7.1 `SourceMatcher` — title-based fuzzy matching

**Path:** `data/extension/.../matcher/SourceMatcher.kt:46-384`

**Public API:**
- `match(title: String): Result` — sequential priority-ordered search, returns first match (`L186-205`)
- `matchAll(title: String): List<SourceMatch>` — sequential search, exact-match short-circuits, returns ranked matches (`L233-284`)
- `searchOneSource(sourceId: Long, query: String): SourceSearchOutcome<ManualSearchResult>` — single-source search for manual selection (`L149-177`)
- `getAvailableSources(): List<SourceInfo>` — lightweight (id + name) list for UI source picker (`L134-136`)
- `getSourceById(sourceId: Long): AnimeCatalogueSource?` — direct lookup by ID (`L340-342`)

**Title normalization** (`L346-355`):
```kotlin
private fun normalizeTitle(title: String): String {
    return title.lowercase()
        .replace(Regex("""\b\d+(st|nd|rd|th)?\s+season\b"""), "")  // "2nd season"
        .replace(Regex("""\bseason\s+\d+\b"""), "")                 // "season 2"
        .replace(Regex("""\([^)]*\)"""), "")                        // parentheticals
        .replace(Regex("""\[[^]]*]"""), "")                         // bracketed
        .replace(Regex("""[^\w\s]"""), "")                          // non-alphanumerics
        .trim()
        .replace(Regex("""\s+"""), " ")                             // collapse whitespace
}
```

**Similarity scoring** (`L357-364`):
- Exact match → 1.0
- Substring match → 0.95
- Otherwise → `1.0 - levenshtein(a, b) / maxLen`

**Threshold:** 0.80 (`L382`).

**`matchAll` algorithm** (sequential priority search, `L233-284`):
1. Get all catalogue sources in installed-extensions order (the "trusted sources list").
2. For each source, call `searchSourceDetailed(source, title)`:
   - Call `source.getSearchAnime(1, query, AnimeFilterList(emptyList()))` on `Dispatchers.IO`.
   - Normalize the query + each result title, compute similarity score.
   - Filter results with `score >= 0.80`.
3. If any match has `score >= 1.0` (exact match), **stop searching** remaining sources.
4. Collect per-source errors in `lastMatchAllErrors` (for UI display).
5. Sort all matches by score descending.

**Key design choice:** the search is **sequential**, not concurrent. This is intentional (`L207-232` KDoc): faster when the first source has an exact match, more predictable, less load on extension servers.

### 7.2 `SourceLinkStore` — AniList ID → extension source link

**Path:** `data/extension/.../cache/SourceLinkStore.kt:22-69`

- Backed by `PreferenceStore.getObject(key="pref_source_links", ...)` (`L34-42`).
- Stores `Map<String, SourceLink>` where key = `anilistId.toString()`.
- `SourceLink(sourceId: Long, animeUrl: String, animeTitle: String)` (`L27-32`).
- `getLink(anilistId: Int): SourceLink?` (`L45`)
- `saveLink(anilistId, sourceId, animeUrl, animeTitle)` (`L48-52`)
- `removeLink(anilistId)` (`L55-59`)
- `getAll(): Map<String, SourceLink>` (`L62`) — for backup.
- `changes: Flow<Map<String, SourceLink>>` (`L64`) — reactive.

**Purpose:** lets the details page skip re-searching on every app open. On first match, the link is saved; on re-open, `ExtensionDetailsProvider.load` (`details/ExtensionDetailsProvider.kt:91-99`) reads the saved link and calls `source.getEpisodeList(sAnime)` directly.

### 7.3 `ExtensionLinkStore` — extension anime → AniList ID (reverse lookup)

**Path:** `data/extension/.../cache/ExtensionLinkStore.kt:34-115`

- Backed by `PreferenceStore.getObject(key="pref_extension_anilist_links", ...)`.
- Stores `Map<String, Int>` where key = `"$sourceId:$animeUrl"`, value = `anilistId`.
- `getAniListId(sourceId, animeUrl): Int?` (`L68-70`) — used by Search page to skip the linking sheet on cache hit.
- `getPreferredSourceForAnilist(anilistId): Long?` (`L84-90`) — reverse lookup: which source did the user originally link this AniList anime to? Fixes the owner's report: "it does not load the episodes from the exact same extension from which I went to the details page."
- `link(sourceId, animeUrl, anilistId)` (`L96-100`)
- `unlink(sourceId, animeUrl)` (`L103-107`)
- `getAll(): Map<String, Int>` (`L93`) — for backup.

### 7.4 What happens when a user manually links via `ExtensionLinkingSheet`

**Path:** `feature/search/.../ui/ExtensionLinkingSheet.kt:100-439` + `viewmodel/ExtensionLinkingViewModel.kt:83-175`

```
User taps an extension search result on SearchScreen
  ↓
AppController.startLinking(source, sAnime)               ← app/.../navigation/AppController.kt:295-297
  → linkingTarget = source to sAnime
  ↓
AnikutaRoot renders ExtensionLinkingSheet                ← app/.../navigation/AnikutaRoot.kt:181-198
  ↓
ExtensionLinkingViewModel constructed with (source, sAnime, anilistApi, linkStore)
  ↓
attemptLink()                                            ← ExtensionLinkingViewModel.kt:103-138
  1. linkStore.getAniListId(source.id, sAnime.url)       ← cache check (L105)
     If cached → emit Linked(anilistId, wasCached=true) → skip sheet entirely
  2. Else → anilistApi.searchAnime(sAnime.title, perPage=10) on Dispatchers.IO  ← L116-118
     - If results → auto-link the first → linkStore.link(source.id, sAnime.url, best.id)
       → emit Linked(best.id, wasCached=false)
     - If no results → emit NeedsManualLink(results=emptyList()) → show sheet
     - On error → emit NeedsManualLink(results=emptyList(), error=msg) → show sheet
  ↓
If sheet shows (NeedsManualLink):
  - User can type a different query → manualSearch(query) → re-search → NeedsManualLink with new results
  - User can tap a result → selectManual(anime) → linkStore.link(...) → emit Linked(anime.id)
  - User can tap "Go without linking" → goWithoutLinking() → emit GoWithoutLinking(source, sAnime)
  ↓
onLinked(anilistId, wasCached):
  → AppController.onLinked                              ← (AppController.kt, not shown but referenced from AnikutaRoot.kt:190-192)
  → pushDetail(anilistId) → opens AnimeDetailScreen in AniList mode
  → ExtensionDetailsProvider will later merge AniList metadata with the extension's episodes

onGoWithoutLinking(extSource, extSAnime):
  → AppController.onGoWithoutLinking                    ← AppController.kt:698-704
  → pendingExtensionSource = extSource; pendingExtensionSAnime = extSAnime
  → pushExtensionDetail(source, sAnime, anilistId=null) → opens AnimeDetailScreen in Extension mode
```

**The link is persisted in BOTH stores:**
- `ExtensionLinkStore` gets `"$sourceId:$animeUrl" → anilistId` (so Search page can skip the sheet next time).
- `SourceLinkStore` gets `anilistId → (sourceId, animeUrl, animeTitle)` (so Details page can skip re-matching next time) — this is written by `AniListDetailsProvider.loadEpisodes` (`data/anime/.../details/AniListDetailsProvider.kt:185-190`) on the first successful episode fetch.

---

## 8. CloudStream comparison & generic-extension-format assessment

### 8.1 What a CloudStream-style extension looks like (based on general knowledge)

CloudStream 3.x is a similar Android app for streaming anime/movies/TV. Its extension model differs from Aniyomi's in several ways:

**Metadata model:**
- CloudStream uses `MediaContent` / `MediaContentSearchResponse` (not `SAnime`).
- Episodes are modeled as `Episode(name, data, posterUrl, date, rating, description)` where `data` is an opaque string passed back to the source for video resolution.
- Episodes have explicit **season** and **episode number** fields (`season: Int`, `episode: Int`) — first-class, not inferred from `episode_number: Float`.
- CloudStream supports **movies**, **TV series**, **anime**, and **live streams** as distinct `MediaType` values — Aniyomi only has anime (and manga, but ANIKUTA doesn't support manga per ADR-009).

**Source interface:**
- `MainAPI` is the abstract base (not `AnimeHttpSource`).
- Methods: `search(query): List<SearchResponse>`, `load(url): MediaContent`, `loadLinks(data, isCasting): List<EpisodeLink>`.
- `EpisodeLink(url, name, type, label, referer, headers, extractorType)` — different shape from Aniyomi's `Video`/`Hoster`.
- Resolution is `Int` (e.g., 1080) not a parsed `String` quality label.
- No `Hoster` concept — videos are flat `List<EpisodeLink>`.
- No `getAnimeDetails` enrichment — `load(url)` returns the full `MediaContent` in one call.

**Loading:**
- CloudStream extensions are also APKs loaded via `DexClassLoader`, but they declare `<uses-feature android:name="cloudstream.extension" />` (not `tachiyomi.animeextension`).
- Source classes are registered in a `MainAPI` subclass — discovered via `Class.forName` on a metadata-declared FQCN (similar pattern).

### 8.2 What in the current `:core:source-api` contract is Aniyomi-specific vs. generic

| Contract element | Aniyomi-specific? | Notes |
|---|---|---|
| `AnimeSource.id: Long` (MD5 of name/lang/versionId) | **Aniyomi-specific** | The ID algorithm is baked into `AnimeHttpSource.generateId`. CloudStream uses a different ID scheme (often just the class name or a hash of the package). |
| `AnimeSource.lang: String` | **Generic** | Any source has a language. |
| `AnimeSource.name: String` | **Generic** | |
| `SAnime.url/title/description/genre/status/thumbnail_url` | **Mostly generic** | CloudStream's `MediaContent` has the same fields under different names (`url`, `name`, `description`, `genres`, `posterUrl`). |
| `SAnime.artist/author` | **Aniyomi-specific** | Manga-leftover; CloudStream doesn't have these. |
| `SAnime.background_url` | **Aniyomi-specific** | |
| `SAnime.update_strategy: AnimeUpdateStrategy` | **Aniyomi-specific** | CloudStream doesn't have library-update semantics. |
| `SAnime.fetch_type: FetchType` (Seasons/Episodes) | **Aniyomi-specific** | CloudStream always has seasons+episodes for TV, flat for movies. |
| `SAnime.season_number: Double` | **Aniyomi-specific** | CloudStream models seasons as a list, not a field. |
| `SEpisode.url/name/date_upload/episode_number/scanlator/summary/preview_url` | **Mostly generic** | CloudStream's `Episode` has `name`, `data` (≈ url), `date`, `description`, `posterUrl`. `fillermark` is anime-specific. |
| `SEpisode.fillermark: Boolean` | **Aniyomi-specific** | |
| `Video` (videoUrl, videoTitle, resolution, bitrate, headers, subtitleTracks, audioTracks, timestamps, mpvArgs, ffmpegStreamArgs, ffmpegVideoArgs, internalData, initialized) | **Half generic** | The core fields (videoUrl, resolution, headers, subtitleTracks, audioTracks) are generic. The `mpvArgs`/`ffmpegStreamArgs`/`ffmpegVideoArgs` are MPV/FFmpeg-specific. `videoTitle` is Aniyomi's convention for encoding server+audio+quality. |
| `Hoster` (hosterUrl, hosterName, videoList, internalData, lazy) | **Aniyomi-specific (ext-lib 16)** | CloudStream has no hoster indirection — `loadLinks` returns a flat list. |
| `AnimeCatalogueSource` (getPopularAnime, getSearchAnime, getLatestUpdates, getFilterList) | **Mostly generic** | CloudStream has `search`, `mainPage` (≈ popular), `homePage`. `getFilterList` is Aniyomi-specific. |
| `AnimeHttpSource` (baseUrl, versionId, client, headers, request/parse pairs) | **Aniyomi-specific** | The request/parse pattern is Jsoup-centric. CloudStream sources can use any HTTP client + parser. |
| `ParsedAnimeHttpSource` (Jsoup selector hooks) | **Aniyomi-specific** | CloudStream doesn't prescribe Jsoup. |
| `ConfigurableAnimeSource` (setupPreferenceScreen) | **Aniyomi-specific** | CloudStream has its own preference system. |
| `ResolvableAnimeSource` (getUriType/getAnime/getEpisode) | **Generic concept** | Deep-link resolution. CloudStream has similar. |
| `AnimeSourceFactory` (createSources) | **Generic** | Multi-source extensions. |

### 8.3 What would need to be abstracted to support both Aniyomi AND CloudStream

**The good news:** ANIKUTA already has **two clean abstraction layers** that make this tractable:

1. **`UnifiedAnime` + `AnimeDetailsProvider` + `DetailsRequest`** (`:core:common/.../details/`) — the details page is source-agnostic. Adding a CloudStream provider = one new class implementing `AnimeDetailsProvider` that maps `MediaContent` → `UnifiedAnime`.
2. **`ResolverServer` + `ResolverAudioVersion` + `ResolverVideo`** (`:feature:video-resolver/.../VideoResolverState.kt:28-51`) — the watch/download flows are source-agnostic. Adding a CloudStream video resolver = one new strategy that maps `List<EpisodeLink>` → `List<ResolverServer>`.

**What needs to change to support CloudStream-style extensions without touching core:**

| Change | Where | Touches core? |
|---|---|---|
| 1. Define a new contract `:core:media-source-api` (parallel to `:core:source-api`) with `MediaSource`/`MediaContent`/`MediaEpisode`/`MediaVideo` interfaces | New module | ❌ No — new module, core untouched |
| 2. Add `MediaExtensionLoader` + `MediaExtensionManager` (mirror `AnimeExtensionLoader`/`AnimeExtensionManager`) | New `:data:media-extension` module | ❌ No |
| 3. Add `MediaDetailsProvider : AnimeDetailsProvider` that maps `MediaContent` → `UnifiedAnime` | `:data:media-extension` or `:data:anime` | ❌ No — just a new entry in `detailsModule`'s `listOf(...)` (`app/.../di/DetailsModule.kt:41-59`) |
| 4. Add `MediaResolverStrategy : VideoResolverStrategy` that maps `List<MediaVideo>` → `List<ResolverServer>` | `:feature:video-resolver` or new `:feature:media-resolver` | ⚠️ Minor — `ResolverService` would need a new branch (or a new sibling service) |
| 5. Add `DataSource.MEDIA_EXTENSION` enum value | `:core:common/.../details/DataSource.kt:16-22` | ✅ Yes — one-line enum addition (acceptable) |
| 6. Generalize `SourceMatcher` OR add `MediaSourceMatcher` | `:data:extension` or new `:data:media-extension` | ❌ No if parallel; ⚠️ Yes if generalized |
| 7. Add `MediaLinkStore` (parallel to `SourceLinkStore`/`ExtensionLinkStore`) | New `:data:media-extension` | ❌ No |
| 8. Update `ExtensionsSettingsScreen` to show both extension types | `:feature:extensions-settings` | ⚠️ Minor UI change |
| 9. Update `WatchRequest` to be source-agnostic (replace `source: AnimeSource?` with a sealed wrapper) | `:feature:watch` | ⚠️ Minor refactor |
| 10. Update `AppController` linking/resolve flows to handle both types | `:app/navigation` | ⚠️ Moderate refactor |

**The cleanest path is the "parallel module" approach (items 1-3, 6-7 as new modules):**
- Don't touch `:core:source-api` — it stays Aniyomi-compatible.
- Add `:core:media-source-api` with the CloudStream-style contract.
- Add `:data:media-extension` with loader/manager/matcher/stores/provider.
- Register the new provider in `detailsModule`.
- The unified details page + watch flow + download flow already work source-agnostically via `UnifiedAnime` + `ResolverServer` + `DownloadRequest`.

**The harder refactor (if you want ONE unified extension system):**
- Generalize `AnimeSource` → `ContentSource` with a `kind: SourceKind` discriminator.
- Generalize `SAnime` → `Content` with optional season/episode fields.
- Generalize `Video`/`Hoster` → a unified `VideoSource` model.
- This would break Aniyomi extension compatibility (extensions import `eu.kanade.tachiyomi.animesource.AnimeSource` by FQCN) unless you keep both contracts alive side-by-side — which is the parallel-module approach above.

**Recommendation (preliminary, feeds the Phase 2 extension-evolution proposal):** take the parallel-module approach. The unified details page (`AnimeDetailScreen` via `UnifiedAnime`) and the video resolver (`ResolverService` via `ResolverServer`) are ALREADY the abstraction layer — they were designed for exactly this. Adding CloudStream support = adding a parallel contract module + parallel data module + one new provider + one new resolver strategy. Zero changes to `:core:common`, `:core:download`, `:core:backup`. The only core change is adding `DataSource.MEDIA_EXTENSION` (one line).

---

## 9. Extension installation UI (`:feature:extensions-settings`)

### 9.1 `ExtensionsSettingsScreen` — what's implemented vs. stubbed

**Path:** `feature/extensions-settings/.../ExtensionsSettingsScreen.kt:63-445` (445 lines)

**README claim** (`feature/extensions-settings/README.md:7-11`): "UI scaffold — 3-category structure (Trusted Sources → Installed → Available) with an Anime/Manga `TwoWayToggle` on top and per-section empty-state copy. Real data binding (ViewModel + Repository + extension repo fetching + drag-reorderable trusted sources) lands in a later phase."

**Actual status (file is 445 lines, fully implemented):**

| Feature | Status | Evidence |
|---|---|---|
| 3-category layout (Trusted Sources / Untrusted / Available) | ✅ Implemented | `ExtensionsSettingsScreen.kt:145-226` |
| Anime/Manga TwoWayToggle | ✅ Implemented | `L131-136` — but filters by `pkgName.contains("animeextension")` vs `"mangaextension"` (`L98-114`); manga is always empty because ANIKUTA doesn't support manga extensions |
| Fetch available extensions from repos | ✅ Implemented | `L82-92` — `LaunchedEffect(repos.size) { extensionManager.findAvailableExtensions() }` |
| Installed extensions list with version/lang/NSFW badges | ✅ Implemented | `L232-297` (InstalledExtensionRow) |
| Untrust + Uninstall buttons on installed | ✅ Implemented | `L279-295` |
| Untrusted extensions with Trust + Uninstall | ✅ Implemented | `L299-349` (UntrustedExtensionRow) |
| Available extensions with Install button | ✅ Implemented | `L351-407` (AvailableExtensionRow) |
| Install flow with progress | ⚠️ Partial | `L214-220` — collects `InstallStep` but only logs; no progress UI |
| Refresh spinner | ✅ Implemented | `L193-204` |
| Empty states per section | ✅ Implemented | `L146-147, L190-191, L205-206` |
| Extension icons | ✅ Implemented (AsyncImage) | `L364-371` |
| Trusted-sources drag-reorder | ❌ NOT implemented | (README says "lands in a later phase") |
| Per-source settings (ConfigurableAnimeSource preference screen) | ❌ NOT implemented | No code in this module |
| ViewModel | ❌ NOT implemented | Screen calls `extensionManager` directly (no VM) |

**Conclusion:** the README is **outdated** — the screen is substantially more than a "scaffold". It's a functional extensions browser with install/uninstall/trust. What's missing: install progress UI, drag-reorderable trusted sources, per-source preference screens, and a ViewModel abstraction.

### 9.2 `ExtensionRepoSettingsScreen` — fully implemented

**Path:** `feature/extensions-settings/.../ExtensionRepoSettingsScreen.kt:53-295` (295 lines)

| Feature | Status | Evidence |
|---|---|---|
| List configured repos | ✅ Implemented | `L75-110` |
| Add repo with URL verification | ✅ Implemented | `L125-248` — calls `repoApi.verifyRepo(url)` which fetches `index.min.json`/`index.json` + `repo.json` |
| Delete repo | ✅ Implemented | `L85-89` |
| FAB to add | ✅ Implemented | `L113-122` |
| Verification spinner + error display | ✅ Implemented | `L147-185` |
| Empty state | ✅ Implemented | `L92-109` |

**Conclusion:** this screen is fully implemented, not a stub.

---

## 10. Aniyomi reference — how the source system works (summary)

**Sources:** `_REFERENCES/ANIYOMI_REFRENCE/DOCUMENTATION/02-modules/source-api.md` + `_REFERENCES/ANIYOMI_REFRENCE/DOCUMENTATION/03-subsystems/source-system.md`

### 10.1 Aniyomi's source-api is KMP; ANIKUTA's is not

- Aniyomi: `commonMain` (published contract) + `androidMain` (actual typealiases for `PreferenceScreen` + `awaitSingle` bridge). Uses `kotlin("multiplatform")` plugin. The contract is published as the `extensions-lib` library for extension developers.
- ANIKUTA: single Android source set. Uses `id("anikuta.library")`. The contract is NOT published separately — extensions compiled against the Aniyomi reference's `extensions-lib` are loaded at runtime via `ChildFirstPathClassLoader` and binary-compatible at the `eu.kanade.tachiyomi.animesource.*` package boundary.

### 10.2 Aniyomi has a `SourceManager`; ANIKUTA does not

- Aniyomi: `AndroidMangaSourceManager` / `AndroidAnimeSourceManager` hold `ConcurrentHashMap<Long, MangaSource>` / `ConcurrentHashMap<Long, AnimeSource>`. They register sources by ID on extension-load, and serve `getOrStub(id)` which returns a `StubMangaSource` if the extension is uninstalled (so library entries still display). Stubs are mirrored in SQLDelight (`mangasources` / `animesources` tables).
- ANIKUTA: no `SourceManager`. Sources live inside `AnimeExtension.Installed.sources`. Lookup is via `SourceMatcher.getSourceById(id)` which flattens `installedExtensions.flatMap { it.sources }`. There are NO stub sources — if an extension is uninstalled, `getSourceById` returns null and the UI shows "Source no longer installed" (`AppController.kt:211-218`). This is a **functional gap** vs. Aniyomi (no graceful degradation for uninstalled extensions in the library).

### 10.3 Aniyomi has 4 installer backends; ANIKUTA has 1

- Aniyomi: `LEGACY` (ACTION_INSTALL_PACKET), `PRIVATE` (`.ext` files in `filesDir/exts/`), `PACKAGEINSTALLER` (PackageInstaller.Session), `SHIZUKU` (privileged shell). Selected at runtime by preference.
- ANIKUTA: only `PACKAGEINSTALLER` (`data/extension/.../installer/PackageInstallerBackend.kt`). The other three are documented as deferred (`data/extension/README.md:87-88`).

### 10.4 Aniyomi has manga + anime parallel pipelines; ANIKUTA is anime-only

- Aniyomi: `MangaExtensionManager` + `AnimeExtensionManager`, `MangaSource` + `AnimeSource`, `SManga` + `SAnime`, `SChapter` + `SEpisode`, etc. Two complete parallel hierarchies.
- ANIKUTA: anime-only (ADR-009). The `ExtensionsSettingsScreen` has an Anime/Manga toggle (`ExtensionsSettingsScreen.kt:131-136`) but the manga side is always empty because no manga extensions are loadable (the loader only recognizes `tachiyomi.animeextension`, not `tachiyomi.extension` — `AnimeExtensionLoader.kt:273`).

### 10.5 Aniyomi has WebView integration; ANIKUTA does not

- Aniyomi: `WebViewActivity` for Cloudflare bypass, source login, captcha solving. The `cloudflareClient` in `NetworkHelper` is a real separate OkHttp client with a `CloudflareInterceptor`.
- ANIKUTA: `NetworkHelper.cloudflareClient` is the SAME as `client` (`core/source-api/.../network/NetworkHelper.kt:69`) — "We don't have the WebView-based Cloudflare bypass yet". No `WebViewActivity`. Sources requiring Cloudflare will fail.

---

## 11. Key findings summary

1. **The extension contract (`:core:source-api`) is verbatim Aniyomi-compatible** (ADR-029). Same package, same interface names, same method signatures. Only ANIKUTA-specific addition: `ExtensionAppHolder` (replaces Injekt for Application injection in `ConfigurableAnimeSource`).

2. **The core domain (`:core:common`) is fully decoupled from the extension contract.** `UnifiedAnime`/`AnimeDetailsProvider`/`DetailsRequest`/`AnimeDetailsProviderRegistry` are source-agnostic. Zero `import eu.kanade` in `:core:common`. The translation happens in `:data:anime` (AniListDetailsProvider) and `:data:extension` (ExtensionDetailsProvider).

3. **The download engine (`:core:download`) is functionally decoupled.** It accepts an already-resolved `DownloadRequest` with a direct `videoUrl`. The `:core:source-api` build dep is for OkHttp transitive exposure only. The resolve→enqueue orchestration lives in `:app/DownloadOrchestrator` (Rule §14).

4. **The backup system (`:core:backup`) is decoupled by type.** It persists `sourceId: Long` as an opaque Long. No source-api imports.

5. **The extension contract leaks into 12 feature/app files** (4 feature modules + 2 app modules + 2 data provider files + 4 contract files). The widest touch-points: `AppController.kt` (6 imports), `AnimeDetailViewModel.kt` (6 imports), `EpisodesSection.kt`/`AnimeDetailScreen.kt`/`DetailContent.kt` (4 imports each), `WatchRequest.kt`/`WatchScreen.kt` (2 imports), `DownloadOrchestrator.kt` (2 imports).

6. **There are TWO clean abstraction layers already in place:**
   - `UnifiedAnime` + `AnimeDetailsProvider` for the details page (source-agnostic).
   - `ResolverServer` + `ResolverVideo` for the watch/download flow (source-agnostic).
   These are the seams where a second extension format (CloudStream-style) would plug in.

7. **Source IDs are deterministic and stable** (`MD5("name/lang/versionId").takeLowest64Bits and Long.MAX_VALUE`). This is what makes DB rows + source links + extension links survive extension uninstall+reinstall automatically.

8. **Source matching is title-based fuzzy Levenshtein** (threshold 0.80, sequential priority search with exact-match short-circuit). No metadata-based matching. `SourceLinkStore` + `ExtensionLinkStore` cache the results so re-open is instant.

9. **Two video-resolution API generations are supported simultaneously** (ext-lib 1.5 flat `getVideoList(episode)` + ext-lib 16 hoster-based `getHosterList`/`getVideoList(hoster)`). `ResolverService` tries hoster first, falls back to flat.

10. **The extensions-settings UI is more complete than the README claims** — install/uninstall/trust/repo-management all work. Missing: install progress UI, drag-reorderable trusted sources, per-source preference screens, ViewModel abstraction.

11. **Functional gaps vs. Aniyomi**: no `SourceManager` (no stub sources for uninstalled extensions in the library — UI shows "Source no longer installed"), no WebView/Cloudflare bypass, no private/Shizuku/Legacy installer backends, no manga extensions.

12. **To support CloudStream-style extensions without touching core**: add a parallel `:core:media-source-api` + `:data:media-extension` + `MediaDetailsProvider` + `MediaResolverStrategy`. The unified details page and video resolver already work source-agnostically. The only core change needed is adding `DataSource.MEDIA_EXTENSION` (one line). This is the recommended path for the Phase 2 extension-evolution proposal.

---

## Next actions (for the extension-evolution proposal)

1. **Draft an ADR** for the parallel-module approach: `:core:media-source-api` + `:data:media-extension` alongside the existing `:core:source-api` + `:data:extension`.
2. **Prototype `MediaDetailsProvider`** that maps a CloudStream `MediaContent` → `UnifiedAnime` — prove the abstraction holds.
3. **Generalize `ResolverService`** to accept a `ResolverSource` interface (not `AnimeSource`) so both Aniyomi and CloudStream sources can feed it.
4. **Add `DataSource.MEDIA_EXTENSION`** to the enum (`:core:common/.../details/DataSource.kt:16-22`).
5. **Refactor `WatchRequest.source: AnimeSource?`** (`feature/watch/.../WatchRequest.kt:29`) to a sealed `WatchSource` wrapper so the watch flow is source-format-agnostic.
6. **Refactor `AppController` linking/resolve flows** to handle both `AnimeCatalogueSource` and `MediaSource` — likely via a sealed `ExtensionSource` wrapper.
7. **Add a stub-source registry** (Aniyomi parity) so uninstalled-extension library entries show "Source: <name> (uninstalled)" instead of "Source no longer installed".
8. **Refresh the `:feature:extensions-settings` README** — it claims "UI scaffold" but the screen is substantially implemented.
9. **Add install progress UI** to `ExtensionsSettingsScreen` — currently `InstallStep` is collected but only logged (`ExtensionsSettingsScreen.kt:214-220`).
10. **Add per-source preference screen** for `ConfigurableAnimeSource` extensions — the contract exists (`setupPreferenceScreen`) but no UI invokes it.
