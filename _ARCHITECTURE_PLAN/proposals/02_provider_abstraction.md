# 02 — Provider Abstraction Layer

> **Phase 2 / Proposed Architecture.** Proposes an architecture for pluggable metadata providers so new providers (MAL, TMDB, Kitsu, or others) can be added as pluggable modules without changes to core functionality. Grounded in `analysis/03_provider_coupling_map.md`.

---

## 1. The problem

AniList is the sole metadata provider for 10 systems with no fallback (Doc 03 §4): browse feed, airing schedule, AniList tracker, cross-device backup identity for unlinked anime, Aniyomi restore, episode metadata for linked anime, downloads for unlinked anime, tracker sync for unlinked anime, cover image refresh, and source matching.

The provider abstraction **already exists** for 4 systems (Doc 03 §1): the details page (`AnimeDetailsProvider`), the tracker layer (`Tracker` interface), episode metadata (`EpisodeMetadataSource`), and the download engine (`DownloadManager`). The pattern is proven — ADR-039 demonstrated it with two details providers.

**What's missing:** abstractions for `HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, and a consolidated `MetadataProvider` umbrella. Plus the `WatchableId` type (proposal 01) that makes all of this provider-agnostic.

---

## 2. The proposed architecture

### 2.1 The provider interface hierarchy

```
:core:provider-api (NEW module)
├── MetadataProvider              ← umbrella interface (identity + capabilities)
├── HomeFeedProvider              ← browse/home feed
├── SearchProvider                ← search (with filters)
├── AiringScheduleProvider        ← schedule tab
├── AnimeDetailsProvider          ← (EXISTS — move from :core:common to :core:provider-api)
├── EpisodeMetadataSource         ← (EXISTS — move from :core:episode-metadata to :core:provider-api)
├── CoverImageProvider            ← cover image + color refresh
└── MetadataProviderRegistry      ← resolves the active provider(s) per capability
```

### 2.2 The umbrella `MetadataProvider` interface

```kotlin
// :core:provider-api
interface MetadataProvider {
    val id: MetadataProviderId                // ANILIST, MAL, TMDB, KITSU, ...
    val displayName: String                   // "AniList", "MAL", ...
    val requiresAuth: Boolean                 // true if OAuth is needed
    val capabilities: Set<MetadataCapability> // which sub-interfaces it implements

    suspend fun isAvailable(): Boolean        // network + auth check
}

enum class MetadataProviderId(val key: String) { ANILIST("al"), MAL("mal"), TMDB("tmdb"), KITSU("kitsu") }
enum class MetadataCapability { HOME_FEED, SEARCH, SCHEDULE, DETAILS, EPISODE_METADATA, COVER_IMAGES }
```

Each sub-interface extends `MetadataProvider`:
```kotlin
interface HomeFeedProvider : MetadataProvider {
    suspend fun fetchTrending(page: Int, perPage: Int): List<UnifiedAnime>
    suspend fun fetchPopular(page: Int, perPage: Int): List<UnifiedAnime>
    // ... seasonal, recommended, etc.
}

interface SearchProvider : MetadataProvider {
    suspend fun search(query: String, page: Int, perPage: Int): List<UnifiedAnime>
    suspend fun searchWithFilters(filters: SearchFilters): List<UnifiedAnime>
}

interface AiringScheduleProvider : MetadataProvider {
    suspend fun fetchSchedule(ids: List<WatchableId>): List<AiringScheduleInfo>
}

interface CoverImageProvider : MetadataProvider {
    suspend fun fetchCover(watchableId: WatchableId): CoverImageInfo?  // url + color
}
```

### 2.3 The `MetadataProviderRegistry`

```kotlin
// :core:provider-api
class MetadataProviderRegistry(
    private val providers: List<MetadataProvider>,   // Koin multi-binding
    private val preferences: ProviderPreferences,    // user's active-provider selection
) {
    fun <T : MetadataProvider> forCapability(cap: MetadataCapability): T? {
        val active = preferences.activeProviderFor(cap)   // user-selected, or default
        return providers.filterIsInstance<T>()
            .firstOrNull { it.id == active && it.capabilities.contains(cap) }
    }

    fun <T : MetadataProvider> allForCapability(cap: MetadataCapability): List<T> =
        providers.filterIsInstance<T>().filter { it.capabilities.contains(cap) }

    fun fallbackChain(cap: MetadataCapability): List<MetadataProvider> {
        val active = preferences.activeProviderFor(cap)
        val ordered = mutableListOf<MetadataProvider>()
        providers.firstOrNull { it.id == active }?.let { ordered.add(it) }
        ordered.addAll(providers.filter { it.id != active })   // fallback order
        return ordered
    }
}
```

**Fallback behavior:** When a provider is unavailable (network down, not authenticated, rate-limited), the registry tries the next provider in the fallback chain. This is the "dual metadata source with user preference + auto fallback" pattern from ADR-011, generalized.

### 2.4 The active-provider preference

```kotlin
// :core:preferences
class ProviderPreferences(store: PreferenceStore) {
    fun activeProviderFor(cap: MetadataCapability): MetadataProviderId   // user-selected per capability
    fun setActiveProvider(cap: MetadataProviderId, provider: MetadataProviderId)
    fun fallbackOrder(cap: MetadataCapability): List<MetadataProviderId>
}
```

**Default:** AniList for all capabilities (backward compat). The user can change per-capability in Settings → Data & Storage → Metadata Providers.

---

## 3. The AniList provider (refactored)

The existing `:core:anilist` module becomes `:data:provider-anilist` (or stays as `:core:anilist` but implements the new interfaces):

```kotlin
// :data:provider-anilist
class AniListMetadataProvider(
    private val api: AniListApi,            // consolidated client (see §6)
    private val cache: LocalAniListCache,
) : MetadataProvider, HomeFeedProvider, SearchProvider, AiringScheduleProvider,
    AnimeDetailsProvider, CoverImageProvider {

    override val id = MetadataProviderId.ANILIST
    override val displayName = "AniList"
    override val requiresAuth = false       // browse is unauthenticated; auth enhances
    override val capabilities = setOf(
        HOME_FEED, SEARCH, SCHEDULE, DETAILS, COVER_IMAGES,
    )

    override suspend fun isAvailable() = /* network check */
    override suspend fun fetchTrending(...) = api.fetchTrending(...).map { it.toUnifiedAnime() }
    // ... etc.
}
```

**The existing `AniListApi` is wrapped, not rewritten.** The provider is a thin adapter. The GraphQL client, rate limiter, and cache stay.

---

## 4. Adding a new provider (e.g., MAL)

To add MAL as a metadata provider:

1. **New module:** `:data:provider-mal` (parallel to `:data:provider-anilist`).
2. **Implement the interfaces MAL supports:**
   ```kotlin
   class MALMetadataProvider(
       private val api: MALApi,
   ) : MetadataProvider, SearchProvider, AnimeDetailsProvider, CoverImageProvider {
       override val id = MetadataProviderId.MAL
       override val capabilities = setOf(SEARCH, DETAILS, COVER_IMAGES)
       // MAL doesn't have trending/popular the same way; don't implement HomeFeedProvider
       // MAL doesn't have airing schedule; don't implement AiringScheduleProvider
   }
   ```
3. **Register in Koin:** `app/.../di/ProviderModule.kt`:
   ```kotlin
   single<List<MetadataProvider>> {
       listOf(get<AniListMetadataProvider>(), get<MALMetadataProvider>())
   }
   ```
4. **Done.** The registry auto-discovers it. The user can select MAL as their active search/details provider in Settings.

**No changes to `:core:common`, `:core:download`, `:core:backup`, `:feature:*`.** This is the key property — adding a provider is additive and isolated.

---

## 5. How provider-specific data is handled

### 5.1 The `UnifiedAnime` model is the common contract

Every provider maps its native model into `UnifiedAnime` (which already exists — Doc 05 §6.3). Provider-specific fields that don't fit `UnifiedAnime` are dropped (or stored in a provider-specific extension table if needed — but this is rare).

**Example:** AniList has `trailer`, `externalLinks`, `studios` (detailed). MAL has `studios`, `licensors`, `source` (manga/light novel/original). TMDB has `imdb_id`, `tmdb_id`, `collection`. The `UnifiedAnime` model captures the common fields (title, cover, genres, score, status, episodes, next airing). Provider-specific extras go into an optional `providerExtras: Map<String, String>` on `UnifiedAnime` for forward-compat.

### 5.2 The `WatchableId` ties it together

A `UnifiedAnime` carries a `WatchableId` (proposal 01). When the user switches the details-page provider via `SourceSwitcherMenu`, the `WatchableId` stays the same — only the metadata displayed changes. This is already how ADR-039 works; the provider abstraction generalizes it to all capabilities.

### 5.3 Per-provider auth

Each provider manages its own auth:
- AniList: OAuth (for tracker) + unauthenticated (for browse).
- MAL: OAuth (PKCE) — already implemented for the tracker.
- TMDB: API key.
- Kitsu: OAuth.

Auth state is stored per-provider in `ProviderPreferences`. A provider that `requiresAuth` and isn't authenticated returns `isAvailable() = false`, and the registry falls back.

---

## 6. Consolidating the three AniList HTTP clients

Doc 03 §6 identified three separate AniList HTTP clients. The provider abstraction is the right time to consolidate:

```kotlin
// :data:provider-anilist
class AniListClient(
    private val httpClient: OkHttpClient,
    private val rateLimiter: AniListRateLimiter,
    private val cache: LocalAniListCache,
    private val authStore: AniListAuthStore,     // holds the OAuth token (null = unauthenticated)
) {
    suspend fun fetchTrending(...)       // unauthenticated
    suspend fun search(...)              // unauthenticated
    suspend fun fetchById(...)           // unauthenticated
    suspend fun fetchAiringSchedule(...) // unauthenticated
    suspend fun updateProgress(...)      // authenticated (throws if no token)
    suspend fun updateStatus(...)        // authenticated
    // ...
}
```

`AniListMetadataProvider` (browse) and `AniListTracker` (tracker) both wrap `AniListClient`. The Aniyomi restore translator also uses it. One client, one rate limiter, one cache.

---

## 7. Fallback behavior per capability

| Capability | Primary | Fallback | Notes |
|---|---|---|---|
| Home feed | AniList (trending/popular) | Extension "popular" (future) | Today: AniList-only. Future: extensions can contribute. |
| Search | User-selected (AniList default) | Other providers in fallback order | The dual-source search (AniList + extension) stays as-is; the abstraction adds MAL/TMDB as additional tabs. |
| Airing schedule | AniList | None (today) / TMDB (future) | TMDB has season/episode air dates. |
| Details | User-selected (AniList default) | Extension (ADR-039 unlinked) | Already works this way. |
| Episode metadata | Jikan/MAL → Anikage → AniList (priority order) | None | Already multi-source; just needs `WatchableId` keying. |
| Cover images | Active details provider | AniList | AniList is the most reliable for covers. |

**When a provider is unavailable:** the registry logs a warning, tries the next provider, and if all fail, the UI shows a "provider unavailable" state (not a crash). Caches serve stale data where possible.

---

## 8. User-facing configuration

**Settings → Data & Storage → Metadata Providers** (new screen):
- List of installed providers (AniList always present; MAL/TMDB/Kitsu if their modules are installed).
- Per-capability active-provider selector (Home feed: AniList ▾; Search: AniList ▾; Schedule: AniList ▾; etc.).
- Per-provider auth status (Logged in / Not logged in / Log in).
- Per-provider fallback order (drag-reorder).
- Simple mode: hides per-capability selectors; uses a single "default provider" for everything.

**The source-switcher menu** (on the details page, per ADR-039) already lets users switch the details provider per-anime. This stays.

---

## 9. Module structure

```
:core:provider-api (NEW)              ← interfaces + registry + WatchableId (from proposal 01)
:core:common                          ← (keeps Anime, Episode, UnifiedAnime, but delegates provider interfaces to :core:provider-api)
:data:provider-anilist (NEW, or rename :core:anilist)  ← AniList impl
:data:provider-mal (NEW, future)      ← MAL impl
:data:provider-tmdb (NEW, future)     ← TMDB impl
:core:tracker                         ← (keeps Tracker interface; AniListTracker wraps AniListClient)
:core:episode-metadata                ← (keeps EpisodeMetadataSource; sources migrate to WatchableId)
```

**The `AnimeDetailsProvider` + `DetailsRequest` + `UnifiedAnime` move from `:core:common` to `:core:provider-api`** (or `:core:common` depends on `:core:provider-api`). This is a clean separation: `:core:provider-api` defines the contracts; `:core:common` defines the domain models that the contracts produce.

---

## 10. Migration path (high-level)

1. **Phase A:** Create `:core:provider-api`. Move `AnimeDetailsProvider`, `UnifiedAnime`, `DetailsRequest` there. Add `WatchableId` (proposal 01). Add `MetadataProvider`, `HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, `CoverImageProvider`, `MetadataProviderRegistry`.
2. **Phase B:** Refactor `:core:anilist` to implement the new interfaces (wrap, don't rewrite). Consolidate the three HTTP clients.
3. **Phase C:** Migrate `:feature:browse` to use `HomeFeedProvider` (via the registry). Migrate `:feature:search` AniList tab to use `SearchProvider`. Migrate `:feature:updates` schedule to use `AiringScheduleProvider`.
4. **Phase D:** Migrate the cross-cutting stores to `WatchableId` (proposal 01). Remove the anilistId hard gate on downloads.
5. **Phase E:** Add the Settings → Metadata Providers screen.
6. **Phase F:** (Future) Add `:data:provider-mal`, `:data:provider-tmdb`.

(Detailed in `plan/01_phased_implementation.md`.)

---

## 11. Trade-offs accepted

1. **AniList remains the default.** For backward compat + because it's the most complete anime metadata source today. The abstraction enables adding others; it doesn't require removing AniList.

2. **`UnifiedAnime` is a lowest-common-denominator.** Provider-specific fields are dropped (or put in `providerExtras`). We accept this because the unified model is what enables provider switching; preserving every provider-specific field would bloat it.

3. **The registry adds an indirection.** Every metadata call goes through `registry.forCapability(...)` instead of directly to `AniListApi`. We accept this because the indirection is what enables fallback + provider switching.

4. **Per-capability active-provider selection adds UI complexity.** We accept this (with a simple-mode toggle that hides it) because power users want it and it's the whole point of the abstraction.

---

## 12. Conditions for revisiting

- If the `MetadataProviderRegistry` fallback logic proves too slow (every call checks availability), cache availability status with a TTL.
- If providers frequently return incompatible data (e.g., different episode counts for the same anime), add a `MetadataResolver` merge layer (ADR-011 generalized) that picks the "best" field per attribute.
- If the provider-modules proliferate (5+ providers), consider dynamic module loading (DI-style) instead of compile-time deps.

---

## 13. Summary

**Recommendation:** Create `:core:provider-api` with a `MetadataProvider` umbrella + sub-interfaces (`HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, `AnimeDetailsProvider`, `EpisodeMetadataSource`, `CoverImageProvider`) + a `MetadataProviderRegistry` with fallback. Refactor `:core:anilist` to implement these (wrap, don't rewrite). Consolidate the three AniList HTTP clients. Add a Settings → Metadata Providers screen.

**Why this approach:**
- The pattern is proven (`AnimeDetailsProvider` already works this way — ADR-039).
- Adding a provider is additive and isolated (one module + one Koin line).
- No changes to `:core:download`, `:core:backup`, `:feature:*` (they consume `UnifiedAnime` + `WatchableId`, which are provider-agnostic).
- AniList remains the default (backward compat); others are opt-in.

**Driven by evidence:** Doc 03 (the 4 existing abstractions + 6 missing ones + 3 duplicate HTTP clients), Doc 05 (the `AnimeDetailsProvider` + `UnifiedAnime` pattern is the template).

---

*Related: `proposals/01_internal_id_system.md` (`WatchableId` is the identity foundation), `proposals/04_extension_evolution.md` (extensions are a parallel provider type).*
