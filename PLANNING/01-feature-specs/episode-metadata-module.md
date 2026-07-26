# Feature Spec — Episode Metadata Module (`:core:episode-metadata`)

**Task ID:** E-1
**Status:** Draft (pending ADR-023 — see §1.3)
**Implements ADRs:** ADR-010 (AniList co-primary), ADR-011 (dual metadata
source + fallback), ADR-022 (extensible architecture).
**Proposes ADR:** ADR-023 — Per-episode metadata via pluggable source
registry (this spec is the basis for that ADR; see §1.3).
**Companion analysis:**
[`_REFERENCES/OLD_ANIKUTA/ANALYSIS/episode-metadata-method.md`](../../_REFERENCES/OLD_ANIKUTA/ANALYSIS/episode-metadata-method.md).

---

## 1. Purpose & owner requirements

### 1.1 What this module does

Fetches per-episode metadata (title, description, thumbnail, air date,
optionally filler status) for an anime's episode list, by querying multiple
external sources in parallel and merging them with a per-field priority.
Used to enrich episodes that the extension source left sparse (e.g., an
extension that only returns `"Episode 12"` and no thumbnail).

### 1.2 Owner requirements (verbatim from task E-1)

> - "use the exact same method as the old one but make sure that it is kept
>   in a separate module so that we can easily edit it and make changes"
> - "It will take in the info of the anime and it will give the info for the
>   metadata"
> - "Later on we could link the movie database [TMDB] and we could get that
>   info from there"

Translation into spec requirements:

1. **Same method as the old project** — 4 parallel sources (Anikage.cc /
   AniList streaming / Jikan-MAL / Kitsu), per-field merge priority, parallel
   `coroutineScope { async { … } }`, graceful degradation. See
   [analysis](../../_REFERENCES/OLD_ANIKUTA/ANALYSIS/episode-metadata-method.md) §5.
2. **Separate module** — lives in its own Gradle module so it can be edited
   in isolation. Named `:core:episode-metadata` (§2).
3. **Input = anime info, output = episode metadata** — a single
   `EpisodeMetadataRepository.fetch(request)` entry point (§3).
4. **Pluggable sources** — adding TMDB (or any future source) requires
   **zero edits** to existing fetcher code; only a new source class + DI
   registration (§4).

### 1.3 ADR reference & gap

The open-questions list in `DOCS/04-design-decisions.md` line 438 says:

> [ ] Episode metadata source (the owner will specify — see ADR per-episode
>   metadata).

No such ADR exists yet. This spec proposes **ADR-023 — Per-episode metadata
via pluggable source registry**. Until ADR-023 is recorded, this spec is a
**draft**. Once the owner approves, the ADR should be added to
`DOCS/04-design-decisions.md` and this spec marked `Status: Approved`.

This spec also **implements** (does not supersede):
- **ADR-010** — AniList is a co-primary data source. This module treats
  AniList streaming episodes as one of the 4 sources (Source 2).
- **ADR-011** — Dual metadata source with user preference + automatic
  fallback. ADR-011 is about *anime-level* metadata (cover, synopsis,
  status). This module is the *episode-level* analog — same fallback
  philosophy, finer granularity. A future `AnimeMetadataResolver` (for
  anime-level) and this `EpisodeMetadataRepository` (for episode-level)
  share the same source-registry pattern (§4) so they can be unified later
  if desired.
- **ADR-022** — Extensible architecture. The source-registry pattern (§4) is
  the extension point: new sources are declared, not wired.

---

## 2. Module name & location

```
ANIKUTA_PROJECT/ANIKUTA/core/episode-metadata/
├── build.gradle.kts
├── README.md                          # module purpose + public API summary
└── src/main/kotlin/app/anikuta/core/episodemetadata/
    ├── EpisodeMetadataRepository.kt   # public entry point (interface + impl)
    ├── EpisodeMetadataSource.kt       # the source contract (§4.1)
    ├── EpisodeMetadataSourceRegistry.kt
    ├── EpisodeMetadataMergeStrategy.kt
    ├── model/
    │   ├── EpisodeMetadata.kt         # the output data class (§3.2)
    │   ├── EpisodeMetadataRequest.kt  # the input data class (§3.1)
    │   ├── EpisodeMetadataResult.kt   # success/partial/error sealed type
    │   └── MetadataSourceId.kt        # value class for source identification
    ├── sources/
    │   ├── AnikageSource.kt           # Source 1
    │   ├── AniListStreamingSource.kt  # Source 2 (reads from request.anime)
    │   ├── JikanSource.kt             # Source 3
    │   └── KitsuSource.kt             # Source 4
    ├── cache/
    │   ├── EpisodeMetadataCache.kt    # interface
    │   └── DiskEpisodeMetadataCache.kt # impl (filesDir/episode_metadata/…)
    ├── di/
    │   └── EpisodeMetadataModule.kt   # DI registration (sources + registry + repo)
    └── internal/
        ├── HtmlStripper.kt
        ├── DateParser.kt
        └── RateLimiter.kt              # serializes Jikan/Kitsu calls globally
```

**Gradle path:** `:core:episode-metadata`
**Package:** `app.anikuta.core.episodemetadata`
**Visibility:** All public API in the root + `model/` packages. Everything
under `sources/`, `cache/`, `di/`, `internal/` is `internal` (Kotlin
visibility) — only the DI module exposes them for wiring.

### 2.1 Dependencies (what this module needs)

| Dependency | Why |
|---|---|
| `:core:network` (or `:core:http`) | OkHttp client (`NetworkHelper` equivalent). Each source uses the shared client. |
| `:core:json` | kotlinx.serialization `Json` instance (shared, configured with `ignoreUnknownKeys = true`). |
| `:core:preferences` | `PreferenceStore` for the per-field toggles (§7). |
| `:core:anilist` (or `:data:anilist`) | Read-only access to `AniListAnime` model + the GraphQL client for the `idMal` lookup. The source-of-truth AniList data is fetched by the detail/anime module; this module only *reads* it from the request. |
| `:core:database` (SQLDelight, deferred — see §6.4) | For the provenance cache. Phase 1 uses disk JSON; SQLDelight is the Phase 2 target per the open ADR on persistence. |
| `kotlinx-coroutines-core` | `coroutineScope`, `async`, `withTimeout`. |
| `kotlinx-serialization-json` | Source response models. |

### 2.2 Who depends on THIS module

| Caller | Why |
|---|---|
| `:feature:anime-detail` | The detail ViewModel calls `fetch()` after loading episodes (primary caller). |
| `:feature:watch` | The watch page reads cached metadata to show the current episode's description below the mini-player (per ADR-012). |
| `:feature:episode-notifications` (ADR-014) | Queries air dates to schedule notifications. |
| `:feature:auto-download` (ADR-020) | Queries air dates to trigger downloads. |
| `:feature:settings` | The metadata settings screen reads/writes the toggles (§7). |

---

## 3. Public API

### 3.1 Input — `EpisodeMetadataRequest`

```kotlin
package app.anikuta.core.episodemetadata.model

/**
 * The anime info needed to fetch episode metadata.
 *
 * Constructed by the caller (typically :feature:anime-detail) from whatever
 * data it already has: the cached AniListAnime, the episode list loaded from
 * the extension, and the user's per-field fetch toggles.
 */
data class EpisodeMetadataRequest(
    /** AniList anime ID. Required — used by Anikage source. */
    val anilistId: Int,

    /** MyAnimeList anime ID. nullable — if absent, Jikan + Kitsu sources skip
     *  and the repository attempts an idMal lookup via AniList GraphQL. */
    val malId: Int?,

    /** The number of episodes to fetch metadata for (1..episodeCount). */
    val episodeCount: Int,

    /** Pre-fetched AniList streaming episodes (titles + thumbnails). The
     *  AniListStreamingSource reads these without an extra network call.
     *  Pass emptyList() if not available. */
    val anilistStreamingEpisodes: List<AniListStreamingEpisode>,

    /** Fallback thumbnail used when no source has a real thumbnail but at
     *  least one source returned *some* data. Typically anime.bannerImage
     *  ?: anime.coverImage.best(). */
    val fallbackThumbnailUrl: String?,

    /** Per-field fetch toggles (mirror of PlayerPreferences in the old
     *  project). The caller reads these from PreferenceStore; the repository
     *  does NOT read preferences directly (keeps it testable). */
    val fetchThumbnails: Boolean,
    val fetchTitles: Boolean,
    val fetchSummaries: Boolean,
    val fetchAirDates: Boolean,
)

data class AniListStreamingEpisode(
    val title: String?,
    val thumbnail: String?,
    val url: String?,
)
```

### 3.2 Output — `EpisodeMetadata` + `EpisodeMetadataResult`

```kotlin
/** One episode's merged metadata. All fields nullable — null means "no
 *  source had this field." The caller decides whether to overwrite the
 *  extension's value (typically: only overwrite if the extension left it
 *  blank, matching the old project's behavior). */
data class EpisodeMetadata(
    val title: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val airDate: Long?,           // epoch millis
    val filler: Boolean?,         // NEW vs old project. null = unknown.
    /** Which source contributed each field. For re-resolution when the
     *  user changes preferences (ADR-011). Empty by default; populated by
     *  the merge strategy. */
    val provenance: MetadataProvenance = MetadataProvenance(),
)

data class MetadataProvenance(
    val titleSource: MetadataSourceId? = null,
    val descriptionSource: MetadataSourceId? = null,
    val thumbnailSource: MetadataSourceId? = null,
    val airDateSource: MetadataSourceId? = null,
    val fillerSource: MetadataSourceId? = null,
)

@JvmInline
value class MetadataSourceId(val value: String)   // e.g. "anikage", "jikan", "tmdb"

/**
 * The result of EpisodeMetadataRepository.fetch(). Sealed so callers can
 * distinguish "all sources empty" from "some sources returned data" from
 * "the whole thing blew up".
 */
sealed interface EpisodeMetadataResult {
    /** All sources returned empty. The caller should leave the episode list
     *  untouched (no enrichment happened). */
    object NoData : EpisodeMetadataResult

    /** At least one source returned data. [metadata] is keyed by 1-based
     *  episode number. Episodes with no metadata from any source are absent
     *  from the map. */
    data class Success(val metadata: Map<Int, EpisodeMetadata>) : EpisodeMetadataResult

    /** The repository itself threw (e.g., coroutineScope was cancelled, or
     *  the cache layer failed unexpectedly). Individual source failures are
     *  NOT errors — they collapse to emptyMap internally and contribute to
     *  Success or NoData. */
    data class Error(val message: String, val cause: Throwable? = null) : EpisodeMetadataResult
}
```

### 3.3 The entry point — `EpisodeMetadataRepository`

```kotlin
package app.anikuta.core.episodemetadata

interface EpisodeMetadataRepository {
    /**
     * Fetch episode metadata for one anime. Thread-safe; can be called
     * concurrently for different anime.
     *
     * Contract:
     *  - Never throws. Always returns an [EpisodeMetadataResult].
     *  - Respects [EpisodeMetadataRequest.fetchThumbnails] etc. — sources
     *    still run, but fields the user opted out of are nulled in the merge.
     *  - Checks the cache first. On cache hit (fresh), returns immediately
     *    without network calls.
     *  - On cache miss or stale: fans out to all registered sources in
     *    parallel, merges, writes back to cache, returns.
     *  - Individual source failures do NOT fail the call — they contribute
     *    emptyMap and the other sources fill in what they can.
     *
     * @param request the anime info + per-field toggles.
     * @return [EpisodeMetadataResult].
     */
    suspend fun fetch(request: EpisodeMetadataRequest): EpisodeMetadataResult

    /** Invalidate the cache for one anime. Call when the user pulls to
     *  refresh or manually clears cache. */
    suspend fun invalidate(anilistId: Int)

    /** Prefetch metadata for an anime without waiting for it. Used by the
     *  notifications scheduler (ADR-014) to warm the cache before the
     *  scheduled release time. Fire-and-forget. */
    fun prefetch(request: EpisodeMetadataRequest)
}
```

### 3.4 Usage example (from `:feature:anime-detail`)

```kotlin
class AnimeDetailViewModel(
    private val episodeMetadataRepo: EpisodeMetadataRepository,
    private val playerPrefs: PlayerPreferences,
    // ...
) : ViewModel() {

    fun onEpisodesLoaded(episodes: List<SEpisode>, anime: AniListAnime) {
        viewModelScope.launch {
            val request = EpisodeMetadataRequest(
                anilistId = anime.id,
                malId = anime.idMal,
                episodeCount = episodes.size,
                anilistStreamingEpisodes = anime.streamingEpisodes
                    .map { AniListStreamingEpisode(it.title, it.thumbnail, it.url) },
                fallbackThumbnailUrl = anime.bannerImage ?: anime.coverImage.best(),
                fetchThumbnails = playerPrefs.fetchMetadataThumbnails().get(),
                fetchTitles = playerPrefs.fetchMetadataTitles().get(),
                fetchSummaries = playerPrefs.fetchMetadataSummaries().get(),
                fetchAirDates = true,   // air dates have no toggle in old project; keep on
            )
            when (val result = episodeMetadataRepo.fetch(request)) {
                is EpisodeMetadataResult.Success -> applyMetadata(episodes, result.metadata)
                is EpisodeMetadataResult.NoData -> { /* leave episodes as-is */ }
                is EpisodeMetadataResult.Error -> Log.w(TAG, "metadata fetch failed", result.cause)
            }
        }
    }
}
```

---

## 4. Source architecture (pluggable sources)

This is the core design decision that distinguishes the new module from the
old `EpisodeMetadataFetcher`. In the old project, adding a 5th source meant
editing the fetcher class (new `async { … }` block, new merge line, new
response model). In the new project, adding a source means **one new file +
one DI registration line**.

### 4.1 The source contract — `EpisodeMetadataSource`

```kotlin
package app.anikuta.core.episodemetadata

/**
 * One external source of per-episode metadata. Implementations live in
 * `sources/` and are registered in [EpisodeMetadataModule] (DI).
 *
 * A source is responsible for:
 *  - Deciding whether it can handle a given request ([supports]).
 *  - Fetching its data (with retries, rate limiting, etc. — see §5).
 *  - Returning a Map<Int, EpisodeMetadata> keyed by 1-based episode number.
 *    Fields the source doesn't provide should be null.
 *
 * A source is NOT responsible for:
 *  - Merging with other sources (the [EpisodeMetadataMergeStrategy] does that).
 *  - Caching (the [EpisodeMetadataRepository] does that).
 *  - Deciding per-field priority (declared via [fieldPriorities]).
 */
interface EpisodeMetadataSource {
    /** Stable identifier. Used in [MetadataProvenance] and in logs. */
    val id: MetadataSourceId

    /** Human-readable name for the settings UI / logs. */
    val displayName: String

    /** Whether this source can handle the given request. E.g., JikanSource
     *  returns false if [EpisodeMetadataRequest.malId] is null (and the
     *  repo-level idMal lookup hasn't happened yet — see §5.3). */
    suspend fun supports(request: EpisodeMetadataRequest): Boolean

    /** Fetch the metadata. Must NOT throw — on failure, return emptyMap.
     *  The repository wraps each call in withTimeout (§5.2) but sources
     *  should still be well-behaved. */
    suspend fun fetch(request: EpisodeMetadataRequest): Map<Int, EpisodeMetadata>

    /** This source's priority for each field. Lower number = higher priority
     *  (1 = first choice). Absent = this source doesn't provide that field.
     *  The merge strategy sorts sources per-field by this number. */
    fun fieldPriorities(): Map<MetadataField, Int>
}

enum class MetadataField { TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE, FILLER }
```

### 4.2 The default field priorities (matching the old project)

These are declared per-source in `fieldPriorities()`. The numbers below
reproduce the old project's merge order exactly (analysis §5.3):

| Source \ Field      | TITLE | DESCRIPTION | THUMBNAIL | AIR_DATE | FILLER |
|---------------------|-------|-------------|-----------|----------|--------|
| `JikanSource`       | 1     | —           | —         | 1        | —      |
| `AnikageSource`     | 2     | 1           | 1         | 2        | —      |
| `KitsuSource`       | 3     | 2           | 3         | 3        | —      |
| `AniListStreamingSource` | 4 | —         | 2         | —        | —      |
| `TmdbSource` (future) | —   | 3           | 4         | 4        | 1      |

> `—` means the source doesn't provide that field. The future `TmdbSource`
> is shown for illustration — it's the only source that provides `FILLER`,
> so it gets priority 1 for that field by default.

### 4.3 The registry — `EpisodeMetadataSourceRegistry`

```kotlin
package app.anikuta.core.episodemetadata

/**
 * Holds all registered [EpisodeMetadataSource]s. Sources are added via DI
 * (see [EpisodeMetadataModule]). The registry is read-only at runtime —
 * adding a source is a compile-time + DI change, not a runtime change.
 */
class EpisodeMetadataSourceRegistry(
    private val sources: List<EpisodeMetadataSource>,
) {
    /** All registered sources, in registration order. */
    fun all(): List<EpisodeMetadataSource> = sources

    /** Sources that support the given request, in registration order. */
    suspend fun supporting(request: EpisodeMetadataRequest): List<EpisodeMetadataSource> =
        sources.filter { it.supports(request) }
}
```

### 4.4 The merge strategy — `EpisodeMetadataMergeStrategy`

Replaces the old project's inline merge loop (analysis §5.5). Reads
`fieldPriorities()` from each source and produces one `EpisodeMetadata` per
episode number.

```kotlin
class EpisodeMetadataMergeStrategy {
    suspend fun merge(
        request: EpisodeMetadataRequest,
        perSource: Map<MetadataSourceId, Map<Int, EpisodeMetadata>>,
        sources: List<EpisodeMetadataSource>,
    ): EpisodeMetadataResult {
        // 1. For each MetadataField, sort sources by fieldPriorities()[field].
        // 2. For each episode number 1..episodeCount:
        //    - For each field, walk the sorted source list and take the first
        //      non-null value. Record the source in MetadataProvenance.
        // 3. Apply the "hasAnyRealData" guard for the fallback thumbnail
        //    (analysis §5.4).
        // 4. Null out fields the user opted out of (fetchThumbnails etc.).
        // 5. Return Success(metadata) or NoData if all sources were empty.
    }
}
```

### 4.5 Adding a new source (e.g., TMDB) — the extension point

To add TMDB as a 5th source, an implementer does exactly **two** things:

1. **Create the source class** (`sources/TmdbSource.kt`) implementing
   `EpisodeMetadataSource`. The class:
   - Declares `id = MetadataSourceId("tmdb")`, `displayName = "TMDB"`.
   - `supports(request)`: returns true if `request.malId != null` or a
     cached AniList→TMDB ID mapping exists.
   - `fetch(request)`: looks up the TMDB series ID (via AniList `idMal` →
     TMDB search, or a cached mapping), then `GET
     https://api.themoviedb.org/3/tv/{tmdbId}/season/{n}/episodes`. Maps
     the response to `Map<Int, EpisodeMetadata>`. TMDB provides `name`,
     `overview`, `still_path` (thumbnail), `air_date`, AND `episode_type`
     ("filler" flag) — the only source that exposes filler.
   - `fieldPriorities()`: returns `DESCRIPTION=3, THUMBNAIL=4, AIR_DATE=4,
     FILLER=1` (TMDB is the filler authority).

2. **Register it in DI** (`di/EpisodeMetadataModule.kt`): add `TmdbSource(...)`
   to the `sources` list provided by the module.

**No edit** to `EpisodeMetadataRepository`, `EpisodeMetadataMergeStrategy`,
`EpisodeMetadataSourceRegistry`, or any existing source. The merge strategy
picks up TMDB's `fieldPriorities()` automatically and uses it for the fields
TMDB declares.

This is the concrete realization of ADR-022 (extensible architecture) for
this subsystem.

---

## 5. Fetching pipeline (order, parallelism, fallback)

The repository's `fetch()` implementation follows the old project's shape
(analysis §5.1) with the improvements identified in analysis §12.

### 5.1 Pipeline steps

```
EpisodeMetadataRepository.fetch(request)
  │
  ├─ 1. Check cache (§6). On fresh hit → return Success(cached).
  │     On stale hit → return Success(cached) immediately AND trigger a
  │     background refresh (cache-aside-with-stale-return).
  │
  ├─ 2. (If request.malId == null) attempt idMal lookup via AniList GraphQL.
  │     On success → mutate the request to fill malId. On failure → leave
  │     null; JikanSource + KitsuSource will report supports() = false.
  │
  ├─ 3. registry.supporting(request) → List<EpisodeMetadataSource>
  │
  ├─ 4. coroutineScope {
  │       for each source in parallel:
  │         async {
  │           withTimeout(SOURCE_TIMEOUT_MS) {        // NEW vs old (§12 #7)
  │             try { source.fetch(request) }
  │             catch (e: Exception) {
  │               log.warn("$id failed: ${e.message}")
  │               emptyMap()
  │             }
  │           }
  │         }
  │     }
  │
  ├─ 5. mergeStrategy.merge(request, perSourceResults, sources)
  │     → EpisodeMetadataResult.Success or NoData
  │
  ├─ 6. On Success: write back to cache (§6).
  │
  └─ 7. Return the result.
```

### 5.2 Per-source timeout

Each source is wrapped in `withTimeout(SOURCE_TIMEOUT_MS)`. Default
`SOURCE_TIMEOUT_MS = 15_000` (15 sec). Configurable via `PreferenceStore`
(advanced setting, hidden in simple mode per ADR-018). On timeout, the source
contributes `emptyMap()` — same as a network failure. This fixes the old
project's weakness #7 (analysis §12).

### 5.3 The idMal lookup

Same as the old project (analysis §4.3): if `request.malId == null`, the
repository does a one-shot AniList GraphQL `Media(id, type) { idMal }` query
before fanning out to sources. If it fails, Jikan and Kitsu are silently
skipped (they return `supports() = false`). The lookup result is cached so
repeat visits for the same anime don't re-query.

### 5.4 Rate-limit coordination (NEW vs old project)

The old project fires Jikan + Kitsu calls per anime with no cross-anime
coordination (analysis §12 #8). The new module adds a `RateLimiter` in
`internal/`:

- `JikanSource` uses a shared `RateLimiter` keyed `"jikan"` with a 3-req/sec
  budget. Calls block (suspend) until the budget allows.
- `KitsuSource` uses a shared `RateLimiter` keyed `"kitsu"` with a 1-req/sec
  budget (Kitsu is stricter).
- Anikage, AniList streaming, and TMDB are not rate-limited (their APIs
  either have no limit or are local).

This is invisible to the source implementation — the `RateLimiter` is
injected and called at the start of `fetch()`.

### 5.5 Jikan retry (fixed vs old project)

The old project declared `JIKAN_MAX_RETRIES = 5` but never used it (analysis
§4.3, §12 #4). The new `JikanSource` implements **real** retry with
exponential backoff:

- On HTTP 429 or 504: wait `baseDelay * 2^attempt` (base 2 sec, cap 30 sec),
  up to 3 attempts.
- On other non-2xx: no retry, return `emptyMap()`.
- On exception: no retry, return `emptyMap()`.

---

## 6. Caching design

### 6.1 Cache layers

| Layer | Location | Survives | TTL | Phase |
|---|---|---|---|---|
| In-memory | `ConcurrentHashMap<Int, CachedEntry>` in the repository | app process | 5 min (matching old `REFRESH_GUARD_MS`) | Phase 1 |
| Disk | `filesDir/episode_metadata/{anilistId}.json` | app restart | 24h (matching old `TTL_DETAIL_LONG`) | Phase 1 |
| Cloud (Supabase) | shared `episode_metadata` table | device wipe | 7 days | **Phase 2 (deferred)** |

The cloud tier is **optional** and deferred. The old project had a 3-tier
cache for anime-level metadata but never extended it to per-episode (analysis
§6.3). The new project's architecture should support adding it later without
changing the repository interface — the `EpisodeMetadataCache` interface
(§6.2) is designed for a `CloudEpisodeMetadataCache` decorator to slot in.

### 6.2 The cache interface

```kotlin
interface EpisodeMetadataCache {
    /** Returns the cached entry if fresh, or null if missing/stale. */
    suspend fun get(anilistId: Int): CachedMetadata?

    /** Returns the cached entry regardless of staleness (for stale-return). */
    suspend fun getStale(anilistId: Int): CachedMetadata?

    /** Writes the merged result + provenance. */
    suspend fun put(anilistId: Int, entry: CachedMetadata)

    /** Removes one anime's cache. */
    suspend fun invalidate(anilistId: Int)

    /** Removes everything. */
    suspend fun invalidateAll()
}

data class CachedMetadata(
    val anilistId: Int,
    val timestamp: Long,        // epoch millis — written by put()
    val metadata: Map<Int, EpisodeMetadata>,
)
```

### 6.3 Per-field provenance (NEW vs old project)

The cache stores `EpisodeMetadata` including its `MetadataProvenance` (§3.2).
This fixes the old project's weakness #2 (analysis §12 #2): when the user
changes their preferred source (ADR-011), the repository can selectively
re-resolve only the affected fields by reading provenance and re-fetching
from a different source.

### 6.4 Persistence backend

Phase 1: JSON files on disk (mirrors the old `EpisodeCacheStore` exactly —
analysis §6.2). Simple, no schema migration burden.

Phase 2 (once the persistence ADR is decided): migrate to SQLDelight (or
Room) with a table keyed by `(anilist_id, episode_number)` and one column
per field + one column per provenance field + a `fetched_at` timestamp. The
`EpisodeMetadataCache` interface doesn't change — only the impl swaps.

---

## 7. Settings (user-facing)

Mirrors the old project (analysis §10) + adds per-source toggles (ADR-022).

| Preference key | Default | Effect |
|---|---|---|
| `pref_in_app_metadata_fetch` | `true` | Master toggle. |
| `pref_fetch_metadata_thumbnails` | `true` | Fetch episode preview images. |
| `pref_fetch_metadata_titles` | `true` | Fetch episode titles. |
| `pref_fetch_metadata_summaries` | `true` | Fetch episode descriptions. |
| `pref_fetch_metadata_air_dates` | `true` | **NEW** — fetch air dates. |
| `pref_fetch_metadata_filler` | `false` | **NEW** — fetch filler status (only TMDB provides it; off by default until TMDB is added). |
| `pref_episode_metadata_source_timeout_ms` | `15000` | **NEW, advanced** — per-source timeout. Hidden in simple mode (ADR-018). |
| `pref_episode_metadata_enabled_sources` | `["anikage","anilist","jikan","kitsu"]` | **NEW** — per-source enable/disable. JSON array of source IDs. Lets the user turn off, e.g., Kitsu if it's slow. |
| `pref_episode_metadata_cloud_cache` | `false` | **NEW, Phase 2** — opt-in to the Supabase shared cache. Off until Supabase tier is built. |

These live in `:core:preferences` (read by `:feature:settings` and passed
into `EpisodeMetadataRequest` by callers). The repository itself does NOT
read preferences — it only reads what the caller put in the request. This
keeps the module testable and preference-store-agnostic.

### 7.1 Source priority customization (future)

ADR-011 mentions a global "preferred metadata source" for anime-level
metadata. For episode-level, the per-source `fieldPriorities()` (§4.2) is
the equivalent — but it's compile-time, not user-configurable. Making it
user-configurable is a **future enhancement** (post-Phase 1) and would
require storing a per-user override map in preferences and applying it in
the merge strategy. Out of scope for the initial implementation.

---

## 8. Error handling

### 8.1 Per-source failures

Every source's `fetch()` is wrapped in:

```kotlin
async {
    try {
        withTimeout(SOURCE_TIMEOUT_MS) {
            source.fetch(request)
        }
    } catch (e: Exception) {
        log.warn("Source ${source.id} failed: ${e.message}")
        emptyMap()
    }
}
```

A source failing never propagates — it contributes `emptyMap()` and the
other sources fill in what they can. This matches the old project's graceful
degradation (analysis §9).

### 8.2 All sources fail / return empty

If every source returns `emptyMap()`, the merge strategy returns
`EpisodeMetadataResult.NoData`. The caller leaves the episode list
untouched (no enrichment). The "Fetching metadata…" pill in the UI hides.

### 8.3 The repository itself fails

If the `coroutineScope` is cancelled (e.g., the user navigates away) or the
cache layer throws unexpectedly, `fetch()` catches it and returns
`EpisodeMetadataResult.Error(message, cause)`. The caller logs it but does
NOT crash the UI — the episode list is already displayed.

### 8.4 Network entirely down

Each source's OkHttp call throws an `IOException` → caught per-source →
`emptyMap()`. If all sources throw → `NoData`. The caller may optionally
serve stale cache (the repository does this automatically via the
stale-return path in §5.1 step 1).

### 8.5 Rate limit hit (Jikan/Kitsu)

The `RateLimiter` (§5.4) blocks (suspends) until the budget allows. If the
budget is exhausted for too long (e.g., 60 sec), the `withTimeout` (§5.2)
fires and the source contributes `emptyMap()`. The user is not informed of
rate-limiting specifically — it manifests as "this source had no data this
time" and the other sources cover.

### 8.6 Partial data

A source returning metadata for episodes 1–10 but the anime has 24 episodes
is normal (e.g., Kitsu's 20-episode page-1 cap — analysis §4.4). Episodes
11–24 simply have no entry from that source; the merge strategy handles this
by checking each source's map per-episode-number. The old project's Kitsu
pagination bug (analysis §12 #5) should be fixed in the new `KitsuSource` by
paginating through all pages — but the partial-data handling is robust
either way.

---

## 9. Integration points

### 9.1 `:feature:anime-detail` (primary caller)

The detail ViewModel calls `episodeMetadataRepo.fetch(request)` after
loading episodes from the extension. While fetching, it sets
`isEnrichingMetadata = true` so the UI shows the "Fetching metadata…"
pill (the owner flagged this as must-preserve — analysis §11 #5, D-2).

On `Success`: the ViewModel creates **new** `SEpisode` objects with the
enriched fields (preserving the Compose-recomposition trick — analysis
§7.3) and updates `_episodes.value`.

On `NoData` or `Error`: the ViewModel leaves the episode list as-is and
hides the pill.

### 9.2 `:feature:watch` (ADR-012 watch page)

The watch page reads the **cached** episode metadata for the current
episode to display its description below the mini-player. It calls
`episodeMetadataRepo.fetch(request)` with the same request shape, but
relies on the cache hit (the detail page already warmed it). No new network
calls.

If the cache is cold (user deep-linked to the watch page), the watch page
triggers a fetch and shows a placeholder description until it resolves.

### 9.3 `:feature:episode-notifications` (ADR-014)

The notification scheduler queries `EpisodeMetadataResult` for air dates to
schedule release notifications. It uses `prefetch(request)` (§3.3) to warm
the cache before the scheduled release time, so the actual notification
firing doesn't depend on network availability.

### 9.4 `:feature:auto-download` (ADR-020)

Same as notifications — queries air dates to trigger downloads at the
right time.

### 9.5 `:feature:settings`

The metadata settings screen reads/writes the preferences in §7. It does
NOT call the repository directly. The preferences are read by the callers
(§9.1–9.4) and passed into `EpisodeMetadataRequest`.

---

## 10. What this module does NOT do (scope boundaries)

To prevent scope creep, explicitly out of scope:

1. **Anime-level metadata** (cover image, synopsis, status, genres) — that's
   the future `AnimeMetadataResolver` per ADR-011. This module is
   episode-level only.
2. **Episode list fetching from the extension** — that's the extension
   source's job (`AnimeHttpSource.getEpisodeList`). This module only
   *enriches* the list the extension returned.
3. **Watched-episode tracking** — that's `EpisodeSeenStore` (or its new
   equivalent). This module doesn't know or care whether an episode was
   watched.
4. **Video resolution** — that's `:feature:video-resolver` (per the design
   language analysis). This module doesn't resolve video URLs.
5. **User-facing source priority customization** — deferred (§7.1).
6. **The Supabase cloud cache tier** — Phase 2 (§6.1).

---

## 11. Implementation phases

| Phase | Scope | Deliverable |
|---|---|---|
| **Phase 1a** | Port the old `EpisodeMetadataFetcher` verbatim into the new module shape: 4 sources, registry, merge strategy, disk cache, repository. No new features beyond the structure. | Working drop-in replacement for the old fetcher. |
| **Phase 1b** | Add the improvements from analysis §12: per-source `withTimeout`, real Jikan retry, Kitsu pagination, `RateLimiter`, per-field provenance in cache. | Production-quality. |
| **Phase 1c** | Wire into `:feature:anime-detail` (replace the old `enrichEpisodesWithMetadata` call). Preserve the "Fetching metadata…" pill. | User-visible feature parity with old project. |
| **Phase 2** | Add `TmdbSource` (the owner's stated future plan). Add the Supabase cloud cache tier. Add per-source enable/disable preference. | TMDB integration + cross-device cache. |
| **Phase 3** | Add user-configurable source priority (§7.1). | Power-user feature. |

---

## 12. Test plan

| Test | What it verifies |
|---|---|
| `fetch_returnsSuccess_whenAnikageHasData` | The primary path. |
| `fetch_returnsNoData_whenAllSourcesEmpty` | The graceful-degradation path. |
| `fetch_returnsError_whenCacheThrows` | The repository-level catch. |
| `fetch_skipsJikan_whenMalIdIsNull_andIdMalLookupFails` | The idMal prerequisite. |
| `fetch_respectsFetchThumbnailsToggle` | Per-field toggles work. |
| `fetch_appliesMergePriority_titleFromJikanOverAnikage` | The priority order matches the old project. |
| `fetch_appliesFallbackThumbnail_whenHasAnyRealData` | The `hasAnyRealData` guard. |
| `fetch_doesNotApplyFallbackThumbnail_whenAllEmpty` | The negative case of the guard. |
| `fetch_timesOutSource_after15s` | The `withTimeout` improvement. |
| `fetch_retriesJikan_on429` | The real-retry improvement. |
| `fetch_paginatesKitsu_forLongSeries` | The pagination fix. |
| `fetch_writesProvenance_perField` | The provenance tracking. |
| `fetch_returnsStaleCache_whenNetworkDown` | The stale-return path. |
| `TmdbSource_canBeAdded_withoutEditingFetcher` | The extensibility guarantee — add the source, register in DI, fetch returns TMDB data. |
| `RateLimiter_serializesJikanCalls_acrossConcurrentRequests` | The rate-limit coordination. |

---

## 13. Open questions (for the owner)

1. **ADR-023 approval.** This spec proposes ADR-023 (per-episode metadata via
   pluggable source registry). The owner should approve / amend / reject it
   before implementation begins. Until then, this spec is a draft.

2. **TMDB API key.** When TMDB is added (Phase 2), where does the API key
   come from? Options: (a) hardcoded in `BuildConfig` (simple, but the key
   ships in the APK — extractable), (b) user-entered in settings (more
   setup friction), (c) a shared project key hosted behind a proxy service
   (most secure, most infra). Recommendation: (a) for Phase 2, (c) for
   production scale.

3. **Supabase cloud cache opt-in.** Should the Supabase tier (§6.1 Phase 2)
   be on-by-default or opt-in? On-by-default saves API calls across devices
   but sends episode-metadata hashes to the Supabase instance. Recommendation:
   opt-in (default off) until the owner decides on the Supabase data policy.

4. **Filler status default.** When TMDB is added, should filler fetching be
   on by default? The old project had no filler-from-metadata feature at all.
   Recommendation: on by default once TMDB is wired, because the owner
   explicitly mentioned wanting filler status (the task description lists
   "filler status" as a metadata field to fetch).

5. **Persistence backend.** Phase 1 uses disk JSON (mirrors old project).
   Phase 2 migrates to SQLDelight (or Room) once the persistence ADR is
   decided. Does the owner have a preference between SQLDelight and Room?
   (This is the open question in `DOCS/04-design-decisions.md` line 431.)

---

## 14. References

- **Analysis (this task's companion):**
  [`_REFERENCES/OLD_ANIKUTA/ANALYSIS/episode-metadata-method.md`](../../_REFERENCES/OLD_ANIKUTA/ANALYSIS/episode-metadata-method.md)
- **Old project fetcher:**
  `_REFERENCES/OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/data/metadata/EpisodeMetadataFetcher.kt`
- **Old project caller:**
  `_REFERENCES/OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/DetailViewModel.kt` lines 385–485
- **Old project disk cache:**
  `_REFERENCES/OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/data/cache/EpisodeCacheStore.kt`
- **Old project design-language extraction (Stage 3):**
  `_REFERENCES/OLD_ANIKUTA/ANALYSIS/details-episodes-resolution-screens.md` §"Stage 3"
- **ADRs:** `DOCS/04-design-decisions.md` — ADR-010, ADR-011, ADR-022, and
  the open question on line 438 that this spec proposes to close as ADR-023.
- **Module conventions:** `RULES/ai-agent-rules.md` §4 (modularity),
  `ARCHITECTURE.md` (stub — `:core` placement for shared code).

---

**End of spec.** Implementation should not begin until ADR-023 is recorded.
