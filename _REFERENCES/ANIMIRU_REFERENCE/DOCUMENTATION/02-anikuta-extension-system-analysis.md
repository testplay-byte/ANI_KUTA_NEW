# ANIKUTA Extension System Analysis (ExtensionDetailScreen + Anime Linking)

> Task ID: **EXT-DETAILS-TASK3**
> Scope: `:core:source-api`, `:data:extension`, `:feature:anime-details` (data side), `:feature:search` (linking flow).
> Purpose: feed the "pluggable data translation layer" that will let the unified `AnimeDetailScreen` render extension-only anime (no AniList match) using the SAME view-model contract it uses for AniList-backed anime. `ExtensionDetailScreen.kt` is slated for removal once this translation layer exists.

All paths below are relative to repo root `/home/z/my-project/anikuta/`. Line numbers cite the exact source location at the time of analysis (branch `feature/extension-details-page`).

---

## 1. Extension data models

The source-api module is at `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/` (note: the package is `eu.kanade.tachiyomi.animesource`, NOT `app.confused.anikuta.core.sourceapi` — this is intentional for Aniyomi binary-compat per ADR-029). All models are `Serializable` so extension instances can be passed across the classloader boundary.

### 1.1 `SAnime` — extension-side anime metadata

Interface: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt:7-69`
Implementation: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SAnimeImpl.kt:5-32`

| # | Field | Type | Nullable | Default in `SAnimeImpl` | Source of value | Notes |
|---|---|---|---|---|---|---|
| 1 | `url` | `String` (lateinit var) | no (lateinit) | — | Extension parse (`searchAnimeFromElement` / `animeDetailsParse`). Stored **without scheme + domain** (see `AnimeHttpSource.setUrlWithoutDomain`, `AnimeHttpSource.kt:662-664`). | Stable source-local identifier; needed for `getEpisodeList` / `getAnimeDetails`. |
| 2 | `title` | `String` (lateinit var) | no (lateinit) | — | Extension parse. | The ONLY field that's always populated from search results. |
| 3 | `artist` | `String?` | yes | `null` | `animeDetailsParse` only — NOT populated from search. | Often null; few extensions populate it. |
| 4 | `author` | `String?` | yes | `null` | `animeDetailsParse` only. | Same as artist; rarely used. |
| 5 | `description` | `String?` | yes | `null` | `animeDetailsParse`. May also be partially set in `searchAnimeFromElement` if the listing page exposes it. | The synopsis. |
| 6 | `genre` | `String?` | yes | `null` | `animeDetailsParse`. Stored as **comma-separated** string (", "). | Use `SAnime.getGenres()` (line 35-38) to split into `List<String>?`. Splits on `", "`, trims, drops blanks, deduplicates. |
| 7 | `status` | `Int` | no | `0` (= `UNKNOWN`) | `animeDetailsParse`. | Companion-object constants in `SAnime.kt:56-63`: `UNKNOWN=0, ONGOING=1, COMPLETED=2, LICENSED=3, PUBLISHING_FINISHED=4, CANCELLED=5, ON_HIATUS=6`. **Mirrors `AnimeStatus` exactly** (`core/common/.../model/Anime.kt:67-75`). |
| 8 | `thumbnail_url` | `String?` | yes | `null` | Search (`searchAnimeFromElement`) and/or `animeDetailsParse`. | Cover image URL. |
| 9 | `background_url` | `String?` | yes | `null` | `animeDetailsParse` only. | Banner image. Most extensions leave it null. |
| 10 | `update_strategy` | `AnimeUpdateStrategy` | no | `ALWAYS_UPDATE` | `animeDetailsParse`. | Enum `AnimeUpdateStrategy` (`model/AnimeUpdateStrategy.kt:10-23`): `ALWAYS_UPDATE`, `ONLY_FETCH_ONCE`. Used by library-update logic, NOT by the detail page. |
| 11 | `fetch_type` | `FetchType` | no | `Episodes` | `animeDetailsParse`. | Enum `FetchType` (`model/FetchType.kt:10-20`): `Seasons` → call `getSeasonList`; `Episodes` → call `getEpisodeList`. Frozen after first init per KDoc. ANIKUTA always uses `Episodes`. |
| 12 | `season_number` | `Double` | no | `-1.0` | `animeDetailsParse`. | -1 = unknown. Used by multi-season sources; ANIKUTA ignores it. |
| 13 | `initialized` | `Boolean` | no | `false` | Set to `true` by `AnimeHttpSource.fetchAnimeDetails` after a successful details fetch (`AnimeHttpSource.kt:255`). | **Critical**: distinguishes the search-result stub (url+title only, initialized=false) from the enriched SAnime returned by `getAnimeDetails` (all fields populated, initialized=true). |

`SAnime.copy()` (`SAnime.kt:40-54`) produces a fresh `SAnimeImpl` with all 13 fields copied.

`SAnime.create()` returns a blank `SAnimeImpl` (used by extensions and by `AnimeDetailViewModel.kt:493-496, 516-519` when reconstructing an SAnime from `SourceLinkStore`).

### 1.2 `SEpisode` — extension-side episode metadata

Interface: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt:7-41`
Implementation: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisodeImpl.kt:5-22`

| # | Field | Type | Nullable | Default in `SEpisodeImpl` | Source of value | Notes |
|---|---|---|---|---|---|---|
| 1 | `url` | `String` (lateinit var) | no (lateinit) | — | `episodeFromElement` in `episodeListParse`. | Source-local identifier; needed for `getHosterList` / `getVideoList`. |
| 2 | `name` | `String` (lateinit var) | no (lateinit) | — | `episodeListParse`. | Display name; often raw ("Episode 1", "EP 1", "OVA 1"). `EpisodeTitleParser` (in `:core:episode-metadata`) strips the "Episode N -" / "Ep. N -" prefix for clean titles. |
| 3 | `date_upload` | `Long` | no | `0` | `episodeListParse`. Epoch **ms**. 0 = unknown. | Air date. Used to sort + to show relative time. |
| 4 | `episode_number` | `Float` | no | `-1f` | `episodeListParse`. -1 = unnumbered. | Supports fractional episodes (0.5, 12.5 for OVAs/specials). |
| 5 | `fillermark` | `Boolean` | no | `false` | `episodeListParse`. | True = filler episode. **Mapped to/from the DB `fillermark` column as `"filler"` / null** (see `ExtensionDetailViewModel.kt:191` and `AnimeDetailViewModel.kt:699, 764`). |
| 6 | `scanlator` | `String?` | yes | `null` | `episodeListParse`. | Sub-group / uploader name. |
| 7 | `summary` | `String?` | yes | `null` | `episodeListParse`. | Episode synopsis. Most extensions don't provide this. |
| 8 | `preview_url` | `String?` | yes | `null` | `episodeListParse`. | Episode thumbnail URL. ANIKUTA's EpisodeMetadataRepository overrides this with Jikan/Anikage/AniList thumbnails when available. |

`SEpisode.copyFrom(other)` (`SEpisode.kt:25-34`) copies all 8 fields from another `SEpisode` into `this` — used by some extensions to merge partial data.

`SEpisode.create()` returns a blank `SEpisodeImpl`.

### 1.3 `Video` and `Hoster` (brief — not the focus)

`Video` (`model/Video.kt:29-103`) — a resolved video stream:
- `videoUrl: String`, `videoTitle: String`, `resolution: Int?`, `bitrate: Int?`, `headers: Headers?`, `preferred: Boolean`, `subtitleTracks: List<Track>`, `audioTracks: List<Track>`, `timestamps: List<TimeStamp>`, `mpvArgs / ffmpegStreamArgs / ffmpegVideoArgs: List<Pair<String,String>>`, `internalData: String`, `initialized: Boolean`, `status: State` (QUEUE/LOAD_VIDEO/READY/ERROR).
- `SerializableVideo` companion provides JSON encode/decode (used by `Hoster.videoList` serialization).

`Hoster` (`model/Hoster.kt:9-50`) — a server/mirror containing videos:
- `hosterUrl: String`, `hosterName: String`, `videoList: List<Video>?` (null = lazy), `internalData: String`, `lazy: Boolean`, `status: State` (IDLE/LOADING/READY/ERROR).
- `Hoster.NO_HOSTER_LIST = "no_hoster_list"` sentinel: when an extension returns videos without a hoster wrapper, `List<Video>.toHosterList()` wraps them under a single Hoster with `hosterName = NO_HOSTER_LIST`.

### 1.4 `SAnime` vs AniList `AniListAnime` — what's present, what's absent

| Concept | `SAnime` (extension) | `AniListAnime` (`core/anilist/.../model/AniListAnime.kt:13-37`) |
|---|---|---|
| Unique ID | `url` (source-local string) | `id: Int` (global AniList media ID) |
| Title (single) | `title: String` | `title: AniListTitle` (romaji/english/native triple) |
| Description | `description: String?` | `description: String?` (HTML — must be stripped) |
| Cover | `thumbnail_url: String?` | `coverImage: AniListCoverImage?` (medium/large/extraLarge + `color`) |
| Banner | `background_url: String?` (rarely populated) | `bannerImage: String?` (usually populated) |
| Genres | `genre: String?` (comma-joined) | `genres: List<String>?` |
| Status | `status: Int` (0-6, Aniyomi enum) | `status: String?` ("FINISHED","RELEASING","NOT_YET_RELEASED","CANCELLED","HIATUS") |
| Score | — | `averageScore: Int?`, `meanScore: Int?` |
| Popularity | — | `popularity: Int?`, `favourites: Int?` |
| Format | — | `format: String?` ("TV","MOVIE","OVA","ONA","SPECIAL","MUSIC","NOVEL","ONE_SHOT") |
| Total episodes | — | `episodes: Int?` |
| Season | — | `season: String?` ("WINTER","SPRING","SUMMER","FALL") |
| Season year | — | `seasonYear: Int?` |
| Start date | — | `startDate: AniListFuzzyDate?` (year/month/day) |
| End date | — | `endDate: AniListFuzzyDate?` |
| Studios | — | `studios: AniListStudioConnection?` (with `isAnimationStudio`) |
| Next airing | — | `nextAiringEpisode: AniListAiringSchedule?` (`airingAt`, `timeUntilAiring`, `episode`) |
| Source (manga/original) | — | `source: String?` |
| Country | — | `countryOfOrigin: String?` |
| Adult flag | — | `isAdult: Boolean?` |
| MAL ID | — | `idMal: Int?` |
| Author/artist | `author: String?`, `artist: String?` | — (AniList doesn't expose this on the Media type) |
| Update strategy | `update_strategy` | — |
| Fetch type | `fetch_type` | — |
| Season number | `season_number: Double` | — (AniList models seasons as separate Media entries) |

**Headline gap:** `SAnime` carries only what a scraping extension can extract from a streaming site — typically `title`, `url`, `thumbnail_url`, `description`, `genre`, `status`. It has **no numeric ID, no score, no episode count, no airing schedule, no format/season/year, no studio, no MAL ID, no recommendations/relations**. The unified page must hide or substitute these.

---

## 2. Extension API flow (Search → list → detail → episodes)

The extension contract is the four-level hierarchy:

```
AnimeSource                  (base — any source, online or local)
  └─ AnimeCatalogueSource    (adds search/popular/latest + filters)
       └─ AnimeHttpSource    (HTTP-backed base implementation)
            └─ ParsedAnimeHttpSource  (Jsoup-based convenience subclass)
```

Plus optional interfaces: `ConfigurableAnimeSource` (preference screen), `ResolvableAnimeSource` (URI deep-link), `UnmeteredSource` (self-hosted), `AnimeSourceFactory` (one extension package providing multiple sources).

### 2.1 `AnimeSource` (base interface)

File: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt:13-113`

| Member | Signature | Since | Notes |
|---|---|---|---|
| `id` | `val id: Long` | base | Generated by `AnimeHttpSource.generateId` (MD5 of `"$name.lowercase()/$lang/$versionId"`, sign bit cleared). |
| `name` | `val name: String` | base | Display name. |
| `lang` | `open val lang: String = ""` | base | ISO-639-1 (lowercased). Empty on the base; overridden by `AnimeCatalogueSource`. |
| `getAnimeDetails` | `suspend fun getAnimeDetails(anime: SAnime): SAnime` | ext-lib 1.5 | Default impl calls deprecated `fetchAnimeDetails(anime).awaitSingle()`. **Returns an ENRICHED SAnime** (the input is the search stub; the output has `description`, `genre`, `status`, `thumbnail_url`, etc. populated). |
| `getEpisodeList` | `suspend fun getEpisodeList(anime: SAnime): List<SEpisode>` | ext-lib 1.5 | Default impl calls deprecated `fetchEpisodeList(anime).awaitSingle()`. |
| `getSeasonList` | `suspend fun getSeasonList(anime: SAnime): List<SAnime>` | ext-lib 16 | For multi-season sources (ANIKUTA doesn't call this). |
| `getHosterList` | `suspend fun getHosterList(episode: SEpisode): List<Hoster>` | ext-lib 16 | **Throws `IllegalStateException("Not used")` by default** — extensions must override. |
| `getVideoList(hoster)` | `suspend fun getVideoList(hoster: Hoster): List<Video>` | ext-lib 16 | Same default throw. |
| `getVideoList(episode)` | `suspend fun getVideoList(episode: SEpisode): List<Video>` | ext-lib 1.5 | **Legacy** (pre-ext-lib-16). Used as the fallback when an extension doesn't implement `getHosterList`. |
| `fetchAnimeDetails` | `@Deprecated fun fetchAnimeDetails(anime): Observable<SAnime>` | legacy RxJava | Throws by default; overridden by `AnimeHttpSource`. |
| `fetchEpisodeList` | `@Deprecated fun fetchEpisodeList(anime): Observable<List<SEpisode>>` | legacy RxJava | Same. |
| `fetchVideoList` | `@Deprecated fun fetchVideoList(episode): Observable<List<Video>>` | legacy RxJava | Same. |

### 2.2 `AnimeCatalogueSource`

File: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeCatalogueSource.kt:8-80`

Adds:
- `override val lang: String` (now abstract / required).
- `val supportsLatest: Boolean`.
- `suspend fun getPopularAnime(page: Int): AnimesPage` — popular/browse listing.
- `suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage` — search.
- `suspend fun getLatestUpdates(page: Int): AnimesPage` — latest updates listing.
- `fun getFilterList(): AnimeFilterList` — source-specific filters.
- Deprecated RxJava `fetch*` variants for backward compat.

`AnimesPage` (`model/AnimesPage.kt:3`): `data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean)`. Note: the SAnime instances returned here are **stubs** (typically only `url` + `title` + `thumbnail_url`, with `initialized = false`). The caller must invoke `getAnimeDetails(sAnime)` to enrich them.

### 2.3 `AnimeHttpSource` (the abstract base most extensions extend)

File: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt:35-722`

Constructor-injected deps (via Injekt — binary-compat with reference Aniyomi):
- `protected val network: NetworkHelper by injectLazy()` (line 51) — shared OkHttp client + default UA. Registered as a singleton in `App.kt`.

Abstract / open members (full surface an extension overrides):

| Member | Line | Purpose |
|---|---|---|
| `abstract val baseUrl: String` | 56 | Site root, no trailing slash. Used to prefix `anime.url`. |
| `open val versionId: Int = 1` | 62 | Bump when URLs change format (forces a new source ID). |
| `override val id: Long by lazy { generateId(...) }` | 74 | MD5-derived. |
| `open val client: OkHttpClient` | 84 | Defaults to `network.client`; override for per-source client. |
| `open val server: HttpServer?` | 95 | ext-lib 17. Local HTTP server (for proxy-style sources). |
| `protected open fun headersBuilder()` | 123 | Default: `User-Agent: <network.defaultUserAgentProvider()>`. |
| `abstract popularAnimeRequest(page: Int): Request` | 155 | Build the popular-listing HTTP request. |
| `abstract popularAnimeParse(response: Response): AnimesPage` | 162 | Parse it. |
| `abstract searchAnimeRequest(page, query, filters): Request` | 198 | Build the search request. |
| `abstract searchAnimeParse(response): AnimesPage` | 205 | Parse search results. |
| `abstract latestUpdatesRequest(page): Request` | 229 | Build latest-updates request. |
| `abstract latestUpdatesParse(response): AnimesPage` | 236 | Parse latest updates. |
| `open fun animeDetailsRequest(anime: SAnime): Request` | 265 | Default `GET(baseUrl + anime.url, headers)`. Override for POST / custom URL. |
| `abstract animeDetailsParse(response): SAnime` | 274 | Parse the details page. |
| `open fun episodeListRequest(anime: SAnime): Request` | 303 | Default `GET(baseUrl + anime.url, headers)`. |
| `abstract episodeListParse(response): List<SEpisode>` | 312 | Parse episodes. |
| `abstract episodeVideoParse(response): SEpisode` | 319 | Parse a single SEpisode (used by some legacy paths). |
| `open fun seasonListRequest(anime): Request` | 345 | ext-lib 16. |
| `abstract seasonListParse(response): List<SAnime>` | 356 | ext-lib 16. |
| `open fun hosterListRequest(episode): Request` | 382 | ext-lib 16. Default `GET(baseUrl + episode.url, headers)`. |
| `abstract hosterListParse(response): List<Hoster>` | 393 | ext-lib 16. |
| `open fun videoListRequest(hoster): Request` | 418 | ext-lib 16. Default `GET(hoster.hosterUrl, headers)`. |
| `abstract videoListParse(response, hoster): List<Video>` | 430 | ext-lib 16. |
| `open suspend fun resolveVideo(video: Video): Video?` | 440 | Resolve a video URL (for indirect embeds). Default: returns input unchanged. |
| `open suspend fun getVideoThumbnails(video): ThumbnailInfo?` | 451 | ext-lib 17. Seek-preview thumbnails. |
| `open suspend fun getImageTile(url: String): Bitmap?` | 462 | ext-lib 17. Tile image decoder. |
| `open fun videoListRequest(episode): Request` | 495 | Legacy ext-lib 1.5 path. |
| `abstract videoListParse(response): List<Video>` | 504 | Legacy path. |
| `open fun List<Hoster>.sortHosters(): List<Hoster>` | 511 | Per-source hoster sort hook. |
| `open fun List<Video>.sortVideos(): List<Video>` | 520 | Per-source video sort hook. |
| `open suspend fun getVideoUrl(video): String` | 541 | Resolve a single video URL. |
| `protected abstract fun videoUrlParse(response): String` | 567 | Parse video URL from response. |
| `suspend fun getVideo(request, listener): Response` | 577 | Cacheless download with progress. |
| `fun getVideoSize(video, tries): Long` | 585 | Probe via `Range: bytes=0-1`, parse `Content-Range`. |
| `fun videoRequest(video, start, end): Request` | 614 | Range request builder. |
| `fun safeVideoRequest(video): Request` | 640 | Non-range request builder. |
| `fun SEpisode.setUrlWithoutDomain(url)` | 652 | Helper: strip scheme + host from episode URL. |
| `fun SAnime.setUrlWithoutDomain(url)` | 662 | Same for anime URL. |
| `open fun getAnimeUrl(anime): String` | 694 | Full anime URL (used by the "open in browser" action). |
| `open fun getEpisodeUrl(episode): String` | 705 | Full episode URL. |
| `open fun prepareNewEpisode(episode, anime)` | 716 | Hook to mutate episode fields before DB insert. |
| `override fun getFilterList(): AnimeFilterList` | 721 | Default empty. |

### 2.4 `getAnimeDetails` enrichment pattern (CRITICAL for the data-mapping layer)

`AnimeHttpSource.fetchAnimeDetails` (`AnimeHttpSource.kt:251-257`):

```kotlin
override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
    return client.newCall(animeDetailsRequest(anime))
        .asObservableSuccess()
        .map { response ->
            animeDetailsParse(response).apply { initialized = true }
        }
}
```

So `getAnimeDetails(inputStub)` returns a **fresh SAnime** from `animeDetailsParse` (the input is NOT mutated), with `initialized = true` set on the result. The caller is expected to use the returned SAnime, not the input.

ANIKUTA does NOT currently call `getAnimeDetails` from either `AnimeDetailViewModel` or `ExtensionDetailViewModel`. Both VMs use the search-result stub directly (only `url` + `title`). This is a **gap**: the unified translation layer should call `getAnimeDetails` to enrich the stub before rendering (so `description`, `genre`, `status`, `thumbnail_url` come from the source's details page rather than just the search listing).

### 2.5 `ParsedAnimeHttpSource` (Jsoup convenience subclass)

File: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/online/ParsedAnimeHttpSource.kt:17-261`

Replaces `*Parse(response: Response)` overrides with Jsoup `*Parse(document: Document)` + `*Selector()` + `*FromElement(element: Element)` + `*NextPageSelector()` methods. The `*Parse(response)` methods are pre-implemented to call `response.asJsoup()` then delegate. Most real-world extensions extend this.

### 2.6 `ConfigurableAnimeSource`

File: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/ConfigurableAnimeSource.kt:15-53`

```kotlin
interface ConfigurableAnimeSource : AnimeSource {
    fun getSourcePreferences(): SharedPreferences =
        ExtensionAppHolder.app.getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
```

- `preferenceKey()` = `"source_$id"`.
- `ExtensionAppHolder` (line 46-53) is a static `Application` holder, set at app startup. Replaces the reference's `Injekt.get<Application>()` (ADR-023 — Koin, not Injekt).
- `PreferenceScreen` is a `typealias` for `androidx.preference.PreferenceScreen` (`PreferenceScreen.kt:3`).

### 2.7 Other source-api types (brief)

- `AnimeSourceFactory` (`AnimeSourceFactory.kt:6-12`): `createSources(): List<AnimeSource>` — for extension packages that ship multiple sources.
- `ResolvableAnimeSource` (`online/ResolvableAnimeSource.kt:12-43`): `getUriType(uri)`, `getAnime(uri)`, `getEpisode(uri)` — for deep-linking from a URL.
- `UnmeteredSource` (`UnmeteredSource.kt:8`): marker interface for self-hosted sources (no rate limiting).
- `AnimeUpdateStrategy` (`model/AnimeUpdateStrategy.kt:10-23`): `ALWAYS_UPDATE`, `ONLY_FETCH_ONCE`.
- `FetchType` (`model/FetchType.kt:10-20`): `Seasons`, `Episodes`.
- `AnimesPage` (`model/AnimesPage.kt:3`): `(animes: List<SAnime>, hasNextPage: Boolean)`.
- `AnimeFilterList` / `AnimeFilter` (`model/AnimeFilter.kt`, `model/AnimeFilterList.kt`): source-specific search filters.
- `ThumbnailInfo` (`model/ThumbnailInfo.kt`), `HttpServer` (`model/HttpServer.kt`): ext-lib 17 — seek thumbnails + local HTTP server.

---

## 3. Current `ExtensionDetailScreen` — data source, architecture, shortcomings

### 3.1 File map

- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/ExtensionDetailScreen.kt` (430 lines) — Compose UI.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/ExtensionDetailViewModel.kt` (308 lines) — ViewModel + `ExtensionAnime` data class + `ExtensionDetailState` sealed interface + `toExtensionSEpisode()` extension.
- Both are slated for removal once the unified `AnimeDetailScreen` + translation layer can render extension-only anime.

### 3.2 Data source — SAnime directly, NO AniList

The screen is constructed with `(source: AnimeCatalogueSource, sAnime: SAnime, onBack, onOpenEpisode, onRelinkAnilist)` (`ExtensionDetailScreen.kt:86-92`). The `sAnime` is the search-result stub passed from `:feature:search` via `ExtensionLinkingState.GoWithoutLinking` (`ExtensionLinkingViewModel.kt:57-60, 168-170`).

`ExtensionDetailViewModel.loadExtensionAnime()` (`ExtensionDetailViewModel.kt:86-110`) maps the `SAnime` into a local `ExtensionAnime` data class (`ExtensionDetailViewModel.kt:283-293`):

| `ExtensionAnime` field | Mapped from | Line |
|---|---|---|
| `title` | `sAnime.title` | 90 |
| `description` | `sAnime.description` | 91 |
| `genre` | `sAnime.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()` | 92 |
| `coverUrl` | `sAnime.thumbnail_url` | 93 |
| `backgroundUrl` | `sAnime.background_url` | 94 |
| `status` | `sAnime.status` (Int, 0-6) | 95 |
| `sourceName` | `source.name` | 96 |
| `url` | `sAnime.url` | 97 |
| `sourceId` | `source.id` | 98 |

**`getAnimeDetails` is NEVER called.** The stub's `description`/`genre`/`status` are taken as-is from the search result; if the search listing doesn't expose them, they remain null/empty.

### 3.3 UI sections (DATA-side summary — full UI breakdown is the other agent's job)

Per `ExtensionDetailScreen.kt:175-226`, the LazyColumn has exactly 4 items:

1. **`ExtensionDetailBanner`** (line 181-188, def at 240-360) — 360dp blurred cover (`anime.coverUrl`), gradient overlay, action row (Back, Save bookmark, "A" relink button), cover thumbnail + title + meta.
   - Meta row (line 340-347): only `status` (1→"ongoing", 2→"completed"; everything else hidden) and `sourceName`. **No score, no episode count, no season/year, no format.**
   - `coverColor` is hardcoded to `MaterialTheme.colorScheme.surfaceVariant` (line 247) — no dynamic theming (SAnime has no cover color).
2. **`ExtensionGenresRow`** (line 192, def at 364-388) — horizontal chip row, only rendered if `anime.genre.isNotEmpty()`.
3. **`SynopsisSection`** (line 197) — reused from the normal details page; 2-line collapsed + "Show more". Only rendered if `!anime.description.isNullOrBlank()`.
4. **`EpisodesSection`** (line 201-225) — reused from the normal details page. **Critically**, `episodeMetadata` is passed as `emptyMap()` (line 207) and `showMetadataLoading = false` (line 223) — no episode-title/thumbnail enrichment is possible because `EpisodeMetadataRepository.fetchAll` requires an AniList ID + MAL ID.

### 3.4 Architecture

`ExtensionDetailViewModel` (`ExtensionDetailViewModel.kt:41-273`):

- **Init** (line 68-84): calls `loadExtensionAnime()` then launches a coroutine to check `animeRepository.getBySourceAndUrl(source.id, sAnime.url)`. If the anime is already in the DB, loads its episodes from `episodeRepository.getByAnimeId(existing.id)` and converts them to SEpisodes via `Episode.toExtensionSEpisode()` (line 296-307).
- **`loadExtensionAnime()`** (line 86-110): sets `_animeState = Success(ExtensionAnime(...))`, sets `_currentMatch = SourceMatch(source, sAnime, 1.0)` (score 1.0 = exact, since the user picked this source), then either loads episodes from the DB or calls `loadEpisodesFromSource()`.
- **`loadEpisodesFromSource()`** (line 112-132): `withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }`. On success: `EpisodeState.Loaded(episodes, source.name)` + `saveEpisodesToDb(episodes)`. On throw: `EpisodeState.Error(...)` + Toast.
- **`saveEpisodesToDb(episodes)`** (line 134-202): if no DB anime row exists, creates a minimal one (`Anime(...)` with `anilistId = null`, `sourceId = source.id`, `url = sAnime.url`, `score = null`, `totalEpisodes = null`, `nextAiringEpisode = null`, etc.). Then `episodeRepository.deleteByAnimeId(dbAnime.id)` + per-episode `upsert(...)` with `sourceOrder = index`. `fillermark` is mapped `"filler" if ep.fillermark else null` (line 191).
- **`toggleSave()`** (line 204-258): toggles `favorite` on the existing DB row, or creates a new row with `favorite = true` and assigns it to `Category.DEFAULT_ID`.
- **`refresh()`** (line 260-268): sets `_isRefreshing = true`, calls `loadEpisodesFromSource()`, then clears refreshing after 500ms.

### 3.5 Shortcomings (why the owner is unsatisfied)

This list is the input to the data-mapping layer design (doc 04):

1. **Two detail screens diverge.** `AnimeDetailScreen` and `ExtensionDetailScreen` share the same `LazyColumn` layout but consume different data classes (`AniListAnime` vs `ExtensionAnime`). Any UI change must be made twice.
2. **No `getAnimeDetails` enrichment.** The screen relies entirely on the search stub. If the search listing only returns `url + title`, the user sees an empty synopsis, empty genres, status=0.
3. **No AniList metadata at all:**
   - No `averageScore` / `meanScore` → banner meta row has no star rating.
   - No `format` (TV/Movie/OVA), `episodes` (total count), `season`, `seasonYear`, `startDate`/`endDate`, `source` (manga/original), `countryOfOrigin`, `studios` → the "Info" section (`DetailInfo.kt:126-133` in the AniList screen) is entirely absent.
   - No `nextAiringEpisode` → no "EP N in 2d 5h" countdown.
   - No `idMal` → episode-metadata fetch (`EpisodeMetadataRepository.fetchAll`) cannot use Jikan (MAL) as a source.
4. **No tracker sync.** DB row is created with `anilistId = null` (`ExtensionDetailViewModel.kt:161`). `TrackSyncManager.start()` groups watch-progress by anilistId extracted from the WatchProgressStore key `"$anilistId:$episodeUrl"` (`App.kt:111-112` + `TrackSyncManager`) — progress on an `anilistId=null` anime is silently dropped.
5. **No episode metadata enrichment.** `EpisodeMetadataRepository.fetchAll` requires `EpisodeMetadataRequest(animeId = anilistId, malId = ...)` (see `AnimeDetailViewModel.kt:725-732`). With `anilistId = null`, the request can't be built → `episodeMetadata = emptyMap()` (hardcoded at `ExtensionDetailScreen.kt:207`).
6. **No dynamic cover-color theming.** `coverColor` is hardcoded to `surfaceVariant` (`ExtensionDetailScreen.kt:247`). AniList's `coverImage.color` is the input to `generateDynamicScheme` (`AnimeDetailScreen.kt:99-126`).
7. **No source-switcher / manual-search.** The screen has only one match (`_currentMatch = SourceMatch(source, sAnime, 1.0)`); `EpisodesSection` is called with `availableSources = emptyList()`, `hasSearched = false`, `allMatches = listOfNotNull(currentMatch)` (line 205, 213, 220-221). The user cannot switch sources without going back to search.
8. **Stub-only SAnime.** Because `getAnimeDetails` isn't called, fields like `description`, `genre`, `status`, `thumbnail_url` may be missing even when the source's details page WOULD expose them. The owner's quote (`ExtensionDetailViewModel.kt:31-33`): "the extension provides quite a lot of details too, like the title, the cover, the genres, the synopsis" — implies the owner EXPECTS these to be populated, but they often aren't from search alone.
9. **Relink flow is a stub.** `onRelinkAnilist` is a no-op lambda by default (`ExtensionDetailScreen.kt:91, 148`). There's no UI to actually re-link to AniList from this screen.
10. **`background_url` is read but rarely populated.** Most extensions don't expose it; AniList's `bannerImage` is far more reliable.

---

## 4. Anime linking (extension ↔ AniList)

Two complementary stores persist the link in opposite directions. Both live in `:data:extension` (per `RULES/ai-agent-rules.md` §4 — shared code in `:core`/`:data`, not a feature module) and are Koin singletons (`di/ExtensionModule.kt`).

### 4.1 `SourceLinkStore` — AniList → extension direction

File: `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/SourceLinkStore.kt:22-69`

| Aspect | Value |
|---|---|
| Purpose | Persist the source match for an AniList anime so the detail page can skip re-searching on every app open. |
| Storage | `PreferenceStore.getObject(...)` (JSON-serialized `Map<String, SourceLink>`). |
| Pref key | `"pref_source_links"` (line 67). |
| Map key | `anilistId.toString()` (String). |
| Map value | `SourceLink(sourceId: Long, animeUrl: String, animeTitle: String)` (line 27-32, `@Serializable`). |

Public API:

| Method | Signature | Line | Purpose |
|---|---|---|---|
| `getLink` | `fun getLink(anilistId: Int): SourceLink?` | 45 | Read the saved link. |
| `saveLink` | `fun saveLink(anilistId: Int, sourceId: Long, animeUrl: String, animeTitle: String)` | 48-52 | Insert / overwrite. |
| `removeLink` | `fun removeLink(anilistId: Int)` | 55-59 | Delete (used when the saved source goes missing — `AnimeDetailViewModel.kt:528`). |
| `getAll` | `fun getAll(): Map<String, SourceLink>` | 62 | Snapshot for backup. |
| `changes` | `val changes: Flow<Map<String, SourceLink>>` | 64 | Reactive stream. |

### 4.2 `ExtensionLinkStore` — extension → AniList direction (reverse lookup)

File: `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/ExtensionLinkStore.kt:34-115`

| Aspect | Value |
|---|---|
| Purpose | Cache the AniList ID for an extension anime so the search-page can skip the linking sheet on subsequent taps; also provides reverse-lookup for `AnimeDetailViewModel` to prefer the originally-used source. |
| Storage | `PreferenceStore.getObject(...)` (JSON-serialized `Map<String, Int>` via `MapSerializer(String.serializer(), Int.serializer())`). |
| Pref key | `"pref_extension_anilist_links"` (line 113). |
| Map key | `"$sourceId:$animeUrl"` (String, built by `key(sourceId, animeUrl)` at line 62). |
| Map value | `anilistId: Int` (the linked AniList media ID). |

Public API:

| Method | Signature | Line | Purpose |
|---|---|---|---|
| `getAniListId` | `fun getAniListId(sourceId: Long, animeUrl: String): Int?` | 68-70 | Forward lookup (extension → anilist). Used by `ExtensionLinkingViewModel.attemptLink()` line 105 to short-circuit the sheet. |
| `getPreferredSourceForAnilist` | `fun getPreferredSourceForAnilist(anilistId: Int): Long?` | 84-90 | **Reverse lookup** (anilist → sourceId). Iterates `store.get().entries`, finds the first entry whose `value == anilistId`, then parses the sourceId out of the key (`k.substringBefore(':').toLongOrNull()`). Used by `AnimeDetailViewModel.findAndLoadEpisodes` line 557 to pick the originally-used source from `matchAll` results. |
| `getAll` | `fun getAll(): Map<String, Int>` | 93 | Snapshot for backup. |
| `link` | `fun link(sourceId: Long, animeUrl: String, anilistId: Int)` | 96-100 | Insert / overwrite. |
| `unlink` | `fun unlink(sourceId: Long, animeUrl: String)` | 103-107 | Delete one entry. |
| `changes` | `val changes: Flow<Map<String, Int>>` | 110 | Reactive stream. |

**Note on `getPreferredSourceForAnilist`** (line 84-90): the implementation uses `firstOrNull { it.value == anilistId }` — so if the same AniList ID is linked to multiple `(sourceId, animeUrl)` pairs, only the FIRST inserted one wins (PreferenceStore `Map` iteration order = insertion order, modulo JSON round-trip). The `AnimeDetailViewModel` honors this BEFORE consulting the explicit per-anime pref (`AnimeDetailViewModel.kt:556-562`):

```kotlin
val explicitPrefId = sourcePrefs.getLong(sourcePrefKey(anilistId), -1L)   // user's manual switch
val linkedPrefId = extensionLinkStore.getPreferredSourceForAnilist(anilistId)  // first link
val preferredSourceId = when {
    explicitPrefId != -1L -> explicitPrefId
    linkedPrefId != null -> linkedPrefId
    else -> -1L
}
val selected = all.firstOrNull { it.source.id == preferredSourceId } ?: all.first()
```

### 4.3 End-to-end linking flow

The flow is driven by `:feature:search` when the user taps an extension search result.

**Files:**
- `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/ExtensionLinkingViewModel.kt` (175 lines).
- `feature/search/src/main/java/app/confused/anikuta/feature/search/ui/ExtensionLinkingSheet.kt` (438 lines).

**State machine** (`ExtensionLinkingViewModel.kt:32-61`):

```
Loading  ──[cache hit]──►  Linked(anilistId, wasCached=true)        [no sheet shown]
   │
   ├──[auto-search ok, ≥1 result]──►  Linked(best.id, wasCached=false)  [sheet may flash for <400ms then close]
   │                                   └─ linkStore.link(source.id, sAnime.url, best.id)
   │
   ├──[auto-search 0 results]──►  NeedsManualLink(results=[], error=null)   [sheet shown]
   │
   └──[auto-search throws]─────►  NeedsManualLink(results=[], error=msg)    [sheet shown]
                                       │
                                       ├── user taps result → selectManual(anime)
                                       │     └─ linkStore.link(source.id, sAnime.url, anime.id) → Linked(anime.id)
                                       │
                                       ├── user types query → manualSearch(q) → NeedsManualLink(newResults)
                                       │
                                       └── user taps "go without linking" → goWithoutLinking() → GoWithoutLinking(source, sAnime)
```

**`ExtensionLinkingViewModel.attemptLink()`** (`ExtensionLinkingViewModel.kt:103-138`):

1. **Cache check** (line 105-110): `linkStore.getAniListId(source.id, sAnime.url)`. If non-null → emit `Linked(cached, wasCached = true)` and **return immediately** (no sheet, no network call, no toast — per owner request at line 41-44: "it should not show that always").
2. **Auto-search** (line 113-137): `withContext(Dispatchers.IO) { anilistApi.searchAnime(sAnime.title, perPage = 10) }`.
   - If results non-empty: take `results.first()` (AniList's `SEARCH_MATCH` sort already orders by relevance), call `linkStore.link(source.id, sAnime.url, best.id)` (line 123), emit `Linked(best.id)` (wasCached defaults to false).
   - If empty: emit `NeedsManualLink(results = emptyList())`.
   - On exception: emit `NeedsManualLink(results = emptyList(), error = e.message ?: "Search failed")`.

**Sheet rendering delay** (`ExtensionLinkingSheet.kt:75-159`):

- `MIN_SHEET_DELAY_MS = 400L` (line 75).
- A `LaunchedEffect(state)` (line 133-143) waits 400ms when state is `Loading` before showing the sheet. If the state transitions to `Linked` / `GoWithoutLinking` within 400ms, the sheet NEVER appears — the user goes straight to the detail page (eliminates the "split-second sheet flash" the owner reported).
- A second `LaunchedEffect(state)` (line 146-152) auto-routes on terminal states: `Linked → onLinked(anilistId, wasCached)`, `GoWithoutLinking → onGoWithoutLinking(source, sAnime)`.

**`selectManual(anime)`** (`ExtensionLinkingViewModel.kt:161-165`): writes `linkStore.link(source.id, sAnime.url, anime.id)`, emits `Linked(anime.id)`.

**`goWithoutLinking()`** (`ExtensionLinkingViewModel.kt:168-170`): emits `GoWithoutLinking(source, sAnime)`. The caller (`SearchScreen` / `AppController`) opens `ExtensionDetailScreen` with these.

### 4.4 Where the OTHER store (`SourceLinkStore`) gets written

`SourceLinkStore` is written from `AnimeDetailViewModel` (the AniList-side VM), NOT from `ExtensionLinkingViewModel`. Two write sites:

1. **Auto-match save** — `AnimeDetailViewModel.findAndLoadEpisodes` line 572-578:
   ```kotlin
   sourceLinkStore.saveLink(
       anilistId = anilistId,
       sourceId = selected.source.id,
       animeUrl = selected.sAnime.url,
       animeTitle = selected.sAnime.title,
   )
   ```
   After `sourceMatcher.matchAll(title)` picks a winner.

2. **Manual-link save** — `AnimeDetailViewModel.linkManual` line 422-432:
   ```kotlin
   fun linkManual(source, sAnime) {
       val match = SourceMatcher.SourceMatch(source, sAnime, 1.0)
       _currentMatch.value = match
       _allMatches.value = listOf(match)
       sourcePrefs.edit().putLong(sourcePrefKey(anilistId), source.id).apply()
       sourceLinkStore.saveLink(anilistId, source.id, sAnime.url, sAnime.title)
       Toast.makeText(appContext, "Linked to ${source.name}", Toast.LENGTH_SHORT).show()
       loadEpisodes(match)
   }
   ```

**So on a successful AniList-side link, BOTH stores are written:**
- `SourceLinkStore.saveLink(anilistId, sourceId, animeUrl, animeTitle)` — for next-time fast-path on the AniList detail page.
- `ExtensionLinkStore.link(sourceId, animeUrl, anilistId)` — written from `ExtensionLinkingViewModel` (search-side) when the user FIRST taps the extension result, BEFORE the AniList detail page even opens.

**On link failure** (auto-search throws AND user dismisses the sheet, OR user picks "go without linking"):
- Neither store is written.
- The anime opens in `ExtensionDetailScreen` with `anilistId = null`.
- A DB row is created on first episode-load / first save with `anilistId = null` (`ExtensionDetailViewModel.kt:161, 243`).

### 4.5 Three-tier episode load (DB-first → saved link → fresh search)

`AnimeDetailViewModel.findAndLoadEpisodes(anime: AniListAnime)` (`AnimeDetailViewModel.kt:470-587`) is the canonical 3-stage load. Stages:

1. **DB-first** (line 475-506): `animeRepository.getByAnilistId(anilistId)` + `episodeRepository.getByAnimeId(dbAnime.id)`. If episodes non-empty → convert via `Episode.toSEpisode()` (line 754-767) → `EpisodeState.Loaded(sEpisodes, sourceName)` immediately. Background: `searchAllSourcesInBackground(displayTitle)` (for the switcher) + `fetchEpisodeMetadata(episodeCount)`. Return — no source matching needed.

2. **Saved-link** (line 509-530): if DB has no episodes, check `sourceLinkStore.getLink(anilistId)`. If found, resolve `sourceMatcher.getSourceById(savedLink.sourceId)`. If the source still exists, reconstruct a minimal `SAnimeImpl { url = savedLink.animeUrl; title = savedLink.animeTitle }` (line 516-519), set as `_currentMatch` + `_allMatches`, then `loadEpisodes(match)` (which calls `source.getEpisodeList(sAnime)`). If the source is gone, `sourceLinkStore.removeLink(anilistId)` and fall through to stage 3.

3. **Fresh search** (line 532-586): `_episodeState = Searching` → `sourceMatcher.matchAll(title)` → pick winner via the `explicitPrefId / linkedPrefId / -1L` priority (see §4.2 above) → `sourceLinkStore.saveLink(...)` → `loadEpisodes(selected)`.

`loadEpisodes(match)` (line 604-632) calls `source.getEpisodeList(match.sAnime)` on `Dispatchers.IO`, sets `EpisodeState.Loaded`, calls `saveEpisodesToDb(episodes)` and `fetchEpisodeMetadata(episodes.size)`.

### 4.6 Where extension data (SAnime) is ALREADY merged into the Anime domain model today

**Short answer: it isn't, except for episode persistence.** Specifically:

- The `Anime` domain row in the DB (`AnimeRepositoryImpl.upsert`, `AnimeRepositoryImpl.kt:69-133`) is created from `AniListAnime` fields (`displayTitle`, `description`, `genres`, `coverImage`, `averageScore`, `episodes`, `nextAiringEpisode.episode`) — see `AnimeDetailViewModel.kt:646-674` and `AnimeDetailViewModel.kt:280-308`. The SAnime's `description`/`genre`/`status` are NEVER merged into this row.
- The `Episode` rows (`EpisodeRepositoryImpl.upsert`, `EpisodeRepositoryImpl.kt:35-74`) ARE created from `SEpisode` fields (`url`, `name`, `episode_number`, `scanlator`, `date_upload`, `fillermark`, `summary`, `preview_url`) — see `AnimeDetailViewModel.kt:683-704` and `ExtensionDetailViewModel.kt:175-196`. This is the ONLY place where extension data crosses into the domain layer today.
- The `currentMatch` (`SourceMatcher.SourceMatch`) holds the live `SAnime` instance, but it's only consumed by `EpisodesSection` for the source-switcher UI and by `loadEpisodes` for the next `getEpisodeList` call. It's never projected into the anime-detail banner / info section.

**Implication for the translation layer (doc 04):** the unified VM must construct an `AniListAnime`-shaped (or unified-shape) view-state where AniList fields come from AniList when available, and SAnime fields backfill the gaps when the anime is extension-only. The episode list already flows through SEpisode uniformly.

---

## 5. Data gap analysis — AniList provides X, extensions DON'T

This table is the direct input to doc 04 (data mapping). For each field the unified `AnimeDetailScreen` reads today (via `DetailBanner.kt`, `DetailInfo.kt`, `DetailContent.kt`, `EpisodesSection`), it lists: (a) AniList source, (b) extension (`SAnime`/`SEpisode`) source, (c) recommended unified-page behavior when only extension data is available.

| Field | AniList provides | Extension provides | Recommended unified-page behavior (extension-only path) | Reason |
|---|---|---|---|---|
| Primary ID | `id: Int` (anilistId) | `url: String` (source-local) + `sourceId: Long` | Use `(sourceId, animeUrl)` as the composite key for DB / cache / link stores; `anilistId = null`. | Already done in `ExtensionDetailViewModel` (DB row keyed by sourceId+url via `getBySourceAndUrl`). |
| MAL ID | `idMal: Int?` | — | Hide. Episode metadata fetcher falls back to title-only search on Jikan (slower) or skips Jikan entirely. | EpisodeMetadataRequest.malId is nullable. |
| Title (display) | `title.display` (english > romaji > native) | `title: String` (single) | Use `SAnime.title` directly. | Already done. |
| Title (alt) | `title.romaji`, `title.native` | — | Hide the "alternative titles" section. | SAnime has no concept of multi-script titles. |
| Cover image | `coverImage.best` (extraLarge > large > medium) | `thumbnail_url: String?` | Use `SAnime.thumbnail_url`. Fall back to a placeholder if null. | Already done. |
| Banner image | `bannerImage: String?` | `background_url: String?` (rarely populated) | Try `SAnime.background_url`; if null, fall back to `thumbnail_url` blurred (as the AniList screen already does). | Already partially done — but `getAnimeDetails` should be called to give the source a chance to populate `background_url`. |
| Cover color (dynamic theming) | `coverImage.color` (hex) | — | Disable dynamic theming; use the user's selected palette. | SAnime has no color field. A future enhancement could extract the color client-side via Palette from the cover bitmap (the codebase already has the disabled Palette extraction code per ANALYSIS-D / AGENT-SETUP). |
| Description / synopsis | `description: String?` (HTML) | `description: String?` (plain text) | Use `SAnime.description`. Call `getAnimeDetails` first to ensure it's populated. | Strip AniList HTML when merging; extension text is already plain. |
| Genres | `genres: List<String>?` | `genre: String?` (comma-joined) | Split `SAnime.genre` via `SAnime.getGenres()`. | Already done in `ExtensionDetailViewModel.kt:92`. |
| Status | `status: String?` ("FINISHED","RELEASING","NOT_YET_RELEASED","CANCELLED","HIATUS") | `status: Int` (0-6) | Map Int→string for display: 1→"RELEASING", 2→"FINISHED", 5→"CANCELLED", 6→"HIATUS", else→hide. **Note**: AniList's `NOT_YET_RELEASED` has no equivalent in SAnime's enum — the unified VM must keep the AniList status when available, since `NOT_YET_RELEASED` gates the "skip source search" behavior (`AnimeDetailViewModel.kt:447-449`). | SAnime's enum is more granular but doesn't include "not yet released". |
| Average score | `averageScore: Int?` (0-100) | — | Hide the "★ N%" badge. | SAnime has no score field. |
| Mean score | `meanScore: Int?` | — | Hide. | Same. |
| Popularity | `popularity: Int?` | — | Hide. | Same. |
| Favourites | `favourites: Int?` | — | Hide. | Same. |
| Format | `format: String?` ("TV","MOVIE","OVA","ONA","SPECIAL","MUSIC","NOVEL") | — | Show "Unknown" in the InfoRow, OR hide the row entirely. | SAnime has no format field. |
| Total episodes | `episodes: Int?` | — | Hide; the episode LIST count is the only signal (and may include fillers/OVAs). | SAnime has no total-episode-count field. |
| Season | `season: String?` ("WINTER","SPRING","SUMMER","FALL") | — | Hide the InfoRow. | SAnime has no season field. |
| Season year | `seasonYear: Int?` | — | Hide (could be partially derived from `SEpisode.date_upload` of the earliest episode — but unreliable). | SAnime has no year field. |
| Start date | `startDate: AniListFuzzyDate?` | — | Hide. | SAnime has no start-date field. |
| End date | `endDate: AniListFuzzyDate?` | — | Hide. | SAnime has no end-date field. |
| Studios | `studios.mainStudio.name` | `author: String?`, `artist: String?` (rarely populated) | Hide the "Studio" InfoRow. Optionally show `author` / `artist` if non-null (rare). | SAnime's `author`/`artist` are usually null; semantics differ from AniList studios. |
| Next airing episode | `nextAiringEpisode: AniListAiringSchedule?` (`airingAt`, `timeUntilAiring`, `episode`) | — | Hide the "EP N in 2d 5h" countdown. | SAnime has no airing schedule. Could be partially derived from `SEpisode.date_upload` for unaired episodes, but unreliable. |
| Source (manga/original/etc.) | `source: String?` | — | Hide. | SAnime has no AniList-style source field. (Note: `SAnime.source` doesn't exist — the `source` field is on `AniListAnime` only.) |
| Country of origin | `countryOfOrigin: String?` | — | Hide. | SAnime has no country field. |
| Is adult | `isAdult: Boolean?` | — | Hide the badge; the source extension may mark itself NSFW at the package level (`AnimeExtension.isNsfw`) but not per-anime. | SAnime has no per-anime adult flag. |
| Recommendations | AniList query (not in `AniListAnime` — separate API call) | — | Hide the "Recommendations" section. | SAnime has no concept of recommendations. |
| Relations | AniList `relations` query (not in `AniListAnime`) | `season_number: Double` + `getSeasonList()` (ext-lib 16) | Hide the "Relations" section. ANIKUTA does not implement multi-season sources. | `getSeasonList` is unsupported (default throws). |
| Author / artist | — (AniList doesn't expose on Media) | `author: String?`, `artist: String?` | Show as an InfoRow if non-null (rare). | Extension-only enrichment. |
| Update strategy | — | `update_strategy: AnimeUpdateStrategy` | Hide. Used internally by library-update logic, not by the detail page. | Already not shown on the AniList screen. |
| Episode list | — (AniList only has `episodes` count) | `getEpisodeList(sAnime): List<SEpisode>` | Use SEpisode list as the source of truth. | Already done. |
| Episode title | `EpisodeMetadata.title` (from Jikan/Anikage/AniList) | `SEpisode.name` | Show `SEpisode.name`. Run `EpisodeTitleParser` to strip "Episode N -" prefixes for cleaner titles. Episode metadata fetch is SKIPPED (no anilistId/malId) — `episodeMetadata = emptyMap()`. | Already done. |
| Episode thumbnail | `EpisodeMetadata.thumbnailUrl` | `SEpisode.preview_url: String?` | Use `SEpisode.preview_url`. If null, show placeholder. | Already done. |
| Episode air date | `EpisodeMetadata.airDate` | `SEpisode.date_upload: Long` (epoch ms, 0=unknown) | Use `SEpisode.date_upload`. Format via `RelativeTime`. | Already done. |
| Episode description | `EpisodeMetadata.description` | `SEpisode.summary: String?` | Use `SEpisode.summary` (usually null). | Already done. |
| Episode filler flag | `EpisodeMetadata.isFiller` | `SEpisode.fillermark: Boolean` | Use `SEpisode.fillermark`. | Already done. |
| Episode scanlator | — | `SEpisode.scanlator: String?` | Show if non-null. | Already done. |

### 5.1 Summary of behavioral rules for the unified page (extension-only path)

1. **Hide sections that have no extension equivalent:** score badge, total-episodes count, format/season/seasonYear/start-date/end-date/studio/source/country InfoRows, "Next airing" countdown, recommendations, relations, alternative titles.
2. **Show "Unknown" / fallback for fields the section can't be hidden:** format InfoRow ("Unknown"), episodes InfoRow (use the list size, not a total).
3. **Substitute extension data where possible:** cover (thumbnail_url), banner (background_url or blurred cover), synopsis (description), genres (getGenres()), status (map Int→display string).
4. **Call `getAnimeDetails` before rendering** — the search stub is too sparse. This is a NEW behavior the translation layer must add (neither current VM does this today).
5. **Skip episode-metadata fetch** when `anilistId == null` AND `malId == null` (the `EpisodeMetadataRequest` can't be built). Episode rows render with `SEpisode.name` / `preview_url` / `date_upload` / `summary` only.
6. **Skip tracker sync** for `anilistId == null` anime — `TrackSyncManager` already drops these silently, but the UI should NOT show a "tracking" toggle.
7. **Disable dynamic cover-color theming** — fall back to the user's palette.
8. **Keep the source-switcher / manual-search UI** — extension-only anime SHOULD be able to switch sources too (today `ExtensionDetailScreen` hardcodes `availableSources = emptyList()`; the unified page should NOT).
9. **Keep the relink-to-AniList affordance** — today this is a stub "A" button. The unified page should wire it to `ExtensionLinkingSheet` so the user can retroactively link an extension-only anime to AniList (which would then migrate the DB row from `anilistId=null` to the real ID — a migration the translation layer must also handle).

---

End of document. Total source files read for this analysis: 22 (SAnime.kt, SAnimeImpl.kt, SEpisode.kt, SEpisodeImpl.kt, Video.kt, Hoster.kt, AnimeSource.kt, AnimeHttpSource.kt, AnimeCatalogueSource.kt, ConfigurableAnimeSource.kt, ParsedAnimeHttpSource.kt, ResolvableAnimeSource.kt, AnimeSourceFactory.kt, PreferenceScreen.kt, UnmeteredSource.kt, AnimesPage.kt, AnimeUpdateStrategy.kt, FetchType.kt, AnimeExtensionManager.kt, SourceMatcher.kt, SourceLinkStore.kt, ExtensionLinkStore.kt, EpisodeFetchGatewayImpl.kt, AnimeExtensionLoader.kt, AnimeRepositoryImpl.kt, EpisodeRepositoryImpl.kt, AnimeRepository.kt, EpisodeRepository.kt, Anime.kt, Episode.kt, AniListAnime.kt, ExtensionDetailScreen.kt, ExtensionDetailViewModel.kt, AnimeDetailViewModel.kt, ExtensionLinkingViewModel.kt, ExtensionLinkingSheet.kt, EpisodeStates.kt).
