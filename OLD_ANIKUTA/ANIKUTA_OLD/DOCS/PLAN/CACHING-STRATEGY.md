# Caching & Data Management Strategy

A factual extraction of the 3-tier (AniList → Supabase → local) data pattern used by the aniwatch reference website, and how it maps onto ANI-KUTA (Android).

## Source

Reference website: **aniwatch** (a Next.js 14 web app). Repository path (read-only analysis material): `/home/z/my-project/upload/website-ref/`.

Stack, as observed in source:

- **Framework**: Next.js (App Router, `'use client'` pages), TypeScript, Tailwind.
- **AniList**: GraphQL API at `https://graphql.anilist.co`. Wrapper in `src/shared/services/anilist-fetch.ts`.
- **Supabase**: Postgres + REST client created in `src/lib/supabase.ts` via `createClient(url, anonKey)`. Only instantiated in the browser (`typeof window !== 'undefined'`).
- **Local persistence**: the **browser's `localStorage`** — NOT Prisma. `prisma/schema.prisma` exists but contains only a leftover scaffold (`User`, `Post`, `ContactSubmission`) and `src/lib/db.ts` exports a `PrismaClient` that is **never imported anywhere in `src/`** (verified — only `src/lib/db.ts` references Prisma). The actual local cache layer in the reference app is `localStorage`.
- **MegaPlay**: a third-party stream-link provider probed at runtime to compute "availability" and "episode counts" that get baked into Supabase + localStorage. This enrichment step is aniwatch-specific (ANI-KUTA may or may not need it — see TODOs).

What we are learning from it: the **3-tier fallback pattern** (local → Supabase → AniList) and the **enrichment-on-write / skip-on-read** discipline.

## The 3-step data model

The reference app has three sources of anime data, used in this exact priority order at **read time** (local first, AniList last):

| # | Source | Role | TTL (homepage) | TTL (details page) | TTL (search) |
|---|---|---|---|---|---|
| 1 | **`localStorage`** (browser) | Fastest cache; per-tab/per-revisit | 5 min | 24 h (per-anime key) | 10 min (per-filter-hash key) |
| 2 | **Supabase** (`homepage_cache` table + `anime` table) | Cross-tab/cross-device cache; also the only place that stores enriched availability + episode counts durably | 30 min (admin-tunable via `cache_homepage_ttl_ms`) | 24 h staleness window on `anilist_updated_at` | n/a (search queries Supabase directly, no TTL) |
| 3 | **AniList** (GraphQL) | Source of truth for metadata; final fallback when all caches miss or fail | fresh | fresh | fresh |

Key role assignments:

- **Source of truth for metadata**: AniList. Supabase and localStorage only mirror AniList's data (titles, images, scores, etc.).
- **Source of truth for "enrichment" (availability + episode counts)**: MegaPlay probes, results persisted into Supabase. localStorage mirrors them.
- **Cache**: both localStorage (L1) and Supabase (L2) are caches. Neither is authoritative.
- **Fallback order at read time**: L1 (localStorage) → L2 (Supabase) → AniList. **When AniList fails**, the chain reverses to serve stale L2 → stale L1 → throw.

The user described the system as "AniList → Supabase → local"; the actual **write** order in the code is AniList → (enrichment pipeline) → Supabase + localStorage (both written in parallel after enrichment). The actual **read** order is the opposite: local → Supabase → AniList. This doc uses the read order because that is what the home page actually executes.

## How the home page fetches data

Entry point: `src/app/home/page.tsx` (`Home` component, `'use client'`).

```
Home()
  └─ useHomepageData()                                 [src/features/homepage/hooks/useHomepageData.ts]
       └─ loadData()
            └─ getHomepageData()                        [src/features/homepage/services/cache.service.ts]
                 ├─ Step 1: readFromLocalStorage()      → localStorage key 'aniwatch_homepage_cache'
                 │       (fresh if age < LOCALSTORAGE_MAX_AGE_MS = 5 min)
                 ├─ Step 2: readHomepageCache()         [src/features/homepage/services/homepage-cache/index.ts]
                 │       → Supabase table 'homepage_cache' (single row, id=1)
                 │       (fresh if age < getHomepageCacheTtlMs() = 30 min default, admin-tunable)
                 └─ Step 3: fetchFromAniList()
                         └─ fetchAllHomepageData()      [src/features/homepage/services/anilist.service.ts]
                              └─ Promise.all([fetchTrending(25), fetchFreshlyUpdated(15),
                                              fetchMostPopular(15), fetchComingUpNext()])
```

`getHomepageData()` returns a `HomepageCacheCheckResult`:

```ts
export interface HomepageCacheCheckResult {
  data: HomepageData;
  source: 'localStorage' | 'Supabase' | 'AniList';
  enriched: boolean;          // true ⇒ skip availability + episode-count checks
  availabilityMap?: Map<number, AvailabilityCheckResult>;
  episodeCountMap?: Map<number, EpisodeCountResult>;
}
```

The `enriched` flag is the key optimization. Two paths:

- **Cache hit with `enriched: true`** (Supabase always writes enriched; localStorage writes enriched after the pipeline completes): the hook reconstructs `availabilityMap` + `episodeCountMap` from the cached data via `reconstructAvailabilityMapFromData()` / `reconstructEpisodeCountMapFromData()` and **skips the entire availability + episode-count pipeline**. UI renders immediately.
- **Cache hit with `enriched: false`** (raw AniList data just written to localStorage as a fast-revisit placeholder): the hook calls `startAvailabilityCheck(data)` which runs `runAvailabilityCheck()` (MegaPlay EP1 probe for every unique anime), then `useEpisodeCounts` watches the availability map and runs episode-count probes. Once both complete, `saveEnrichedCache()` writes the merged result back to both Supabase and localStorage with `enriched: true`.

Write side (`useHomepageData.saveEnrichedCache`):

```
saveHomepageCache(hero, trending, freshlyUpdated, mostPopular, comingUpNext,
                  availabilityMap, episodeCountMap)
    → supabase.from('homepage_cache').upsert({ id: 1, cache_data: payload,
                                               cache_version: 1, anime_count },
                                              { onConflict: 'id' })     // atomic UPSERT
enrichHomepageData(homepageData, availMap, epCountMap)
writeToLocalStorage(enrichedData, 'AniList', enriched=true)
```

Write is **gated** by verification guards in the `useEffect` at lines 380–455 of `useHomepageData.ts`: it refuses to persist if more than 50% of anime are missing availability data, or more than 30% of "available" anime are missing episode counts, or any available anime lacks an episode-count entry. This prevents half-processed data from being cached as "enriched".

Manual refresh (`refresh()` in the hook) aborts in-flight availability checks, clears localStorage (`clearLocalStorage()`), deletes the Supabase row (`invalidateHomepageCache()`), and re-runs `loadData()`.

## How the detail page fetches data

Entry point: `src/features/details/services/anime-details/index.ts` — `getAnimeDetails(anilistId, abortSignal?, onCachedData?)`. This is the cleanest 3-service example in the codebase (explicit step functions, explicit fallback chain).

```
getAnimeDetails(anilistId)
  ├─ Step 1: readDetailsFromLocalStorage(anilistId)             [cache.service.ts]
  │       key = `aniwatch_details_{anilistId}`  (per-anime)
  │       TTL = DETAILS_LOCALSTORAGE_TTL_MS = 24 h
  │       if entry.enriched → serveFromEnrichedLocalStorage() and RETURN
  │
  ├─ Step 2: readDetailsFromSupabase(anilistId)                 [supabase.service.ts]
  │       table = 'anime'  (one row per anilist_id, primary key)
  │       stale if age(anilist_updated_at) > DETAILS_SUPABASE_STALE_MS = 24 h
  │       enriched = last_availability_check != null
  │                && last_episode_count_check != null
  │                && (sub_episodes_available != null || dub_episodes_available != null)
  │                && consistencyCheckPasses
  │       if supabaseResult.enriched → serveFromEnrichedSupabase() and RETURN
  │
  ├─ (stale-while-revalidate hook)
  │   if onCachedData callback provided and we have any cached (non-enriched) data,
  │   fire onCachedData() so the UI can paint cached data immediately while Step 3 runs
  │
  └─ Step 3: fetchAndEnrichFromAniList(anilistId, localEntry, supabaseResult)
            ├─ fetchAnimeDetails(anilistId)                     [anilist.service.ts]
            │     GraphQL single-Media query (full fields incl. relations, tags, rankings)
            ├─ merge non-enriched Supabase availability into AniList data (if any)
            ├─ writeDetailsToLocalStorage(anilistId, anilistData, 'AniList', enriched=false)
            ├─ runAvailabilityCheck(single anime)               [shared/availability.service.ts]
            ├─ if available && !hasAltLinks:
            │     probeEpisodeCount()                           [shared/episode-count/prober.ts]
            ├─ verifyDetailsConsistency(enrichedData, availResult, epCountResult)
            └─ persistEnrichedData():
                  ├─ if verification.valid:
                  │     batchUpsertAnimeRows([mapAnimeToSupabaseRow(...)])   → Supabase 'anime'
                  │     updateEpisodeCounts(epCountResult)                   → Supabase 'anime'
                  │     writeDetailsToLocalStorage(..., enriched=true, enrichedAt=now)
                  └─ else:
                        batchUpsertAnimeRows(...) for availability only
                        writeDetailsToLocalStorage(..., enriched=false)   // forces re-check next visit
```

Stale-while-revalidate: the `onCachedData` callback lets the details page UI render with stale cached data instantly, then update once Step 3 completes — useful when only non-enriched cache is available.

Detail-page specifics worth noting:

- Supabase `anime` table does **not** store detail-only fields (trailer, externalLinks, rankings, relations, tags). When serving from Supabase, those fields are `null` unless merged in from localStorage or re-fetched from AniList (`refreshDetailFieldsIfNeeded()`).
- Schedule (`nextAiringEpisode`) is also not in Supabase. If a RELEASING anime is missing schedule, `refreshScheduleIfNeeded()` calls the lightweight `refreshAiringSchedule()` AniList query.
- `hasAltLinks` (alternative stream sources beyond MegaPlay) overrides the availability check: if true, MegaPlay probing is skipped and episode counts are pulled from the `anime_stream_links` table instead.

## What gets cached, and for how long

All TTLs are quoted from source constants. Verbatim.

**Homepage** (`src/shared/types/cache.ts`, `src/features/homepage/services/homepage-cache/config.ts`):

| Cache | Key / row | TTL constant | Value | Invalidation |
|---|---|---|---|---|
| localStorage | `'aniwatch_homepage_cache'` (single key) | `LOCALSTORAGE_MAX_AGE_MS` | `5 * 60 * 1000` (5 min) | TTL expiry, manual `refresh()`, or `clearLocalStorage()` |
| Supabase | table `homepage_cache`, row `id=1` | `getHomepageCacheTtlMs()` (admin setting `cache_homepage_ttl_ms`) | `30 * 60 * 1000` (30 min) default | TTL expiry, `invalidateHomepageCache()` (deletes the row), or schema version bump |
| AniList | n/a | n/a | fresh | n/a |

Cache schema versioning: `HOMEPAGE_CACHE_VERSION = 1` (in `src/features/homepage/types/homepage-cache.ts`). On read, `row.cache_version < HOMEPAGE_CACHE_VERSION` is treated as stale (cache miss). Increment to invalidate all clients at once.

`isCacheDataValid(data)` (`src/shared/types/cache.ts`): returns false if `hero + trending + freshlyUpdated + mostPopular` are all empty (comingUpNext excluded — it can legitimately be 0). Invalid cache is deleted, not served.

**Detail page** (`src/features/details/types/anime-details.ts`):

| Cache | Key / row | TTL constant | Value | Notes |
|---|---|---|---|---|
| localStorage | `aniwatch_details_{anilistId}` (per-anime) | `DETAILS_LOCALSTORAGE_TTL_MS` | `24 * 60 * 60 * 1000` (24 h) | Per-anime key — invalidating one anime does not affect others |
| Supabase | table `anime`, row `anilist_id = <id>` | `DETAILS_SUPABASE_STALE_MS` | `24 * 60 * 60 * 1000` (24 h) | Staleness measured against `anilist_updated_at` column |
| AniList | n/a | n/a | fresh | n/a |

Details cache entry shape:

```ts
export interface DetailsCacheEntry {
  data: AnimeDetailsData;
  timestamp: number;              // when written to localStorage (epoch ms)
  enrichedAt: number | null;      // when episode counts were last verified (epoch ms)
  source: 'localStorage' | 'Supabase' | 'AniList';
  enriched: boolean;
}
```

Consistency check at read time (`cache.service.ts` lines 80–88): if entry claims `enriched=true` but `subAvailable=true` while `subEpisodesAvailable` is 0 or null (or the same for dub), the entry is **downgraded to `enriched=false`** so the page re-runs the episode-count check. Stale-but-structural data is never deleted — only its enrichment flag is cleared.

**Search** (`src/features/search/types/search.ts`, `src/features/search/services/search-cache.ts`):

| Cache | Key | TTL | Notes |
|---|---|---|---|
| localStorage | `aniwatch_search_cache_{sorted-JSON-of-filters}` (deterministic hash of filter state) | `SEARCH_CACHE_TTL_MS = 10 * 60 * 1000` (10 min) | Key built by `getSearchCacheKey(filters)` — sorted arrays ensure stability |
| localStorage (genre/tag list) | `aniwatch_genre_cache` | `GENRE_TAG_CACHE_TTL_MS = 24 * 60 * 60 * 1000` (24 h) | Only 19 genres, very stable |
| Supabase | `anime` table (queried directly, no separate cache table) | n/a | Live query every search |

`clearSearchCache()` removes every key with the `aniwatch_search_cache` prefix.

## Supabase's role

Supabase plays **two distinct roles** in this codebase:

1. **L2 cache for AniList metadata + enrichment data** (the role relevant to ANI-KUTA).
2. **Source of truth for app-specific data AniList doesn't have** (alt stream links, popups, feedback, analytics, contact submissions, anime requests, etc.) — out of scope for this doc.

Relevant tables for the 3-tier pattern:

- **`homepage_cache`** (`DOCUMENTATION/SUPABASE/schema/homepage-cache-table.md`): single-row table (`id=1`), columns `cache_data JSONB`, `cache_version INT`, `anime_count INT`, `created_at`, `updated_at`. Stores the fully-enriched homepage payload (`HomepageCachePayload`) — one JSONB blob covering hero/trending/freshlyUpdated/mostPopular/comingUpNext. Atomic UPSERT on write. Written only by `saveHomepageCache()` after the full pipeline completes. Read by `readHomepageCache()`.
- **`anime`** (`DOCUMENTATION/SUPABASE/schema/anime-table.md`): one row per `anilist_id`. Stores AniList-sourced metadata (titles, images, genres, studios as JSONB, etc.) **plus** enrichment columns owned by separate services:
  - Availability: `availability_status`, `sub_available` (0/1), `dub_available` (0/1), `last_availability_check`
  - Episode counts: `sub_episodes_available`, `dub_episodes_available`, `last_episode_count_check`, `last_anilist_episodes`
  - Sync: `anilist_updated_at` (used as the staleness clock)
  - Custom: `custom_cover`, `anikoto_id`, `has_alt_links`

**Sync direction: AniList → Supabase, one-way.** Supabase never pushes data back to AniList. The sync is performed by `mapAnimeToSupabaseRow()` + `batchUpsertAnimeRows()` in `src/shared/services/supabase-sync.service.ts`, called from:

- The homepage enrichment pipeline (via `saveHomepageCache` indirectly — actually no: homepage writes to `homepage_cache`, not `anime`. The `anime` table is written by the **details page** pipeline via `persistEnrichedData` → `batchUpsertAnimeRows([row])`).
- The detail page enrichment pipeline (`persistEnrichedData`).
- The episode-count persister (`updateEpisodeCounts`).

There is **no scheduled/cron sync** in the codebase — Supabase is updated opportunistically whenever a user's visit triggers the AniList → enrichment pipeline.

Supabase is **never a fallback for live queries** on the homepage (it is only read via the `homepage_cache` table). For search, however, Supabase is queried live on every search (`searchSupabase()`) — it acts as a **first-class data source** for search, not just a cache, because it can filter on `sub_available` / `dub_available` which AniList cannot.

## Local DB (Prisma) role

**Correction to the task framing**: the reference website does **not** use Prisma as its local DB for the caching pattern. Prisma is configured (`prisma/schema.prisma`, `src/lib/db.ts`) but the schema only contains the leftover scaffold models `User`, `Post`, `ContactSubmission`, and `db.ts` is **never imported by any file under `src/`** (verified by grep). It is dead code.

The actual local persistence layer is the **browser's `localStorage`**. Its role:

- **L1 cache** for homepage payload, per-anime details, search results, genre list.
- Stores JSON-serialized `CacheEntry<T>` / `DetailsCacheEntry` / `SearchCacheEntry` / `GenreTagCache` objects.
- Per-anime keys (`aniwatch_details_{anilistId}`) allow granular invalidation.
- Not a database — no queries, no indexes. Just key→JSON-string lookups.

Relationship to Supabase: localStorage mirrors whatever Supabase (or AniList) last served, with a shorter TTL. On a cache hit, localStorage is the fast path; on a miss, the app falls through to Supabase, then to AniList, and writes the result back to both localStorage and Supabase.

For ANI-KUTA, the **conceptual** equivalent of this localStorage layer is a local on-device database (SQLDelight), but the reference website itself does not implement that with Prisma — it uses raw `localStorage`. See "Adaptation" below.

## Fallback / failure handling

The fallback chain is explicit and well-commented in both the homepage and detail services.

**Homepage — AniList failure** (`src/features/homepage/services/cache.service.ts`, `getHomepageData()` lines 360–408):

```ts
try {
  const data = await fetchFromAniList();
  return { data, source: 'AniList', enriched: false };
} catch (anilistError) {
  // Fallback 1: stale Supabase (allowStale=true, ignores TTL)
  const staleSupabase = await readHomepageCache(true);
  if (staleSupabase && isCacheDataValid(staleSupabase.data)) {
    writeToLocalStorage(staleSupabase.data, 'Supabase', true);
    return { data: staleSupabase.data, source: 'Supabase', enriched: true, ... };
  }
  // Fallback 2: stale localStorage (even if past TTL)
  if (localEntry && isCacheDataValid(localEntry.data)) {
    return { data: localEntry.data, source: 'localStorage', enriched: localEntry.enriched, ... };
  }
  // No fallback available — throw the original error
  throw anilistError;
}
```

**Detail page — AniList failure** (`findFallbackData()` lines 496–576). The fallback priority is more elaborate:

1. Stale Supabase (`readDetailsFromSupabase(id, allowStale=true)`) — ignore 24 h TTL.
2. Stale localStorage (`readDetailsFromLocalStorage(id, allowStale=true)`) — ignore 24 h TTL.
3. Non-stale Supabase result from the earlier read (Step 2).
4. Non-stale localStorage entry from the earlier read (Step 1).
5. Re-throw the original error.

`AbortError` is special-cased and re-thrown immediately (it is intentional cancellation, not a failure).

**Detail page — AniList returns null (anime not found)** (`handleAniListNotFound()` lines 395–423): falls back to whatever Supabase had for that ID and returns it with `enriched: false`. If Supabase also has nothing, returns `{ data: null, source: 'AniList', enriched: false, ... }` so the UI can render a 404.

**Detail page — Supabase unreachable**: `isSupabaseAvailable()` returns false when env vars are missing or the client couldn't be created. `readDetailsFromSupabase()` and `readHomepageCache()` both short-circuit to `null` in that case, and the chain falls through to AniList. Supabase outages are non-fatal.

**Detail page — local DB (localStorage) empty**: `readDetailsFromLocalStorage()` returns `null`. The flow simply skips Step 1 and goes to Step 2 (Supabase). Same for search cache and genre cache — `null` is the miss signal, the caller proceeds to the next tier.

**Search — AniList failure** (`src/features/search/services/index.ts` lines 117–148):

1. AbortError → re-throw.
2. Try stale search cache (`readSearchCache(filters, allowStale=true)`).
3. Otherwise return Supabase-only results.

**Search — Supabase failure**: not explicitly caught; Supabase query errors return an empty `SupabaseSearchResult` and the orchestrator proceeds to query AniList (the `supabaseHasEnough` check fails, `needsAniList` becomes true).

**Consistency self-healing**: both pages detect inconsistent enriched data (e.g. `subAvailable=true` but `subEpisodesAvailable=0`) and **downgrade** the entry to `enriched=false` rather than deleting it. The next visit re-runs only the missing checks, preserving the still-valid metadata.

## Adaptation for ANI-KUTA (Android)

| aniwatch (web) | ANI-KUTA (Android) | Keep / change |
|---|---|---|
| AniList GraphQL (`anilist-fetch.ts`) | AniList GraphQL via Ktor/Retrofit | **Keep** — same source of truth |
| Supabase `homepage_cache` (JSONB blob) + `anime` table | Supabase (same) — or our own backend if we want to drop Supabase | **Keep** — same L2 cache role |
| `localStorage` (browser KV) | **SQLDelight** (on-device SQLite) | **Change** — replace raw KV with a typed DB; gives us indexes, queries, transactions |
| MegaPlay availability/episode-count probes | TBD — depends on whether ANI-KUTA streams from MegaPlay | **TODO** — see open questions |
| TTL: 5 min (L1) / 30 min (L2) / 24 h (details) | Same values are reasonable starting points | **Keep** — tune later |
| `enriched` flag to skip pipeline | Same — a boolean column on each cached row | **Keep** — core optimization |
| Atomic UPSERT for L2 writes | SQLDelight transaction + Supabase upsert | **Keep** — never delete-then-insert |
| Stale-while-revalidate via `onCachedData` callback | Kotlin coroutine that emits cached `Flow` first, then fresh | **Keep** — same UX |
| 5-step fallback chain on AniList failure | Same chain, expressed as a `when`/sealed-result | **Keep** — the chain is the asset |

Concrete mapping for the home screen:

```
HomeViewModel.getHomepageData():
  1. homepageDao.getCache()                       // SQLDelight, replaces readFromLocalStorage
     if fresh(<5min) && enriched → return
  2. supabase.homepageCacheApi.get()              // Supabase REST/RPC
     if fresh(<30min) → write to homepageDao, return
  3. anilistApi.fetchAllHomepageData()            // GraphQL
     on success: run enrichment pipeline, write to Supabase + homepageDao
     on failure: stale Supabase → stale SQLDelight → throw
```

What to **keep** verbatim from the reference:

- The 3-tier read order: local → Supabase → AniList.
- The "enriched" flag pattern — write enriched data once, skip the pipeline on subsequent reads.
- TTL constants (5 min / 30 min / 24 h) as starting values.
- Cache schema versioning (`HOMEPAGE_CACHE_VERSION`) to force-invalidate all clients.
- Stale-cache fallback when AniList fails.
- Per-item cache keys for details (granular invalidation).
- Consistency self-healing (downgrade `enriched` to `false` instead of deleting).
- Verification guards before persisting enriched data (refuse to write if >50% missing availability, >30% inconclusive episode counts).

What to **change**:

- Replace `localStorage` with SQLDelight — gives us proper queries (e.g. "give me all cached anime with `enriched=0`") instead of parsing JSON blobs.
- Replace the React hook state machine with Kotlin coroutines/Flow.
- Replace `requestAnimationFrame`-batched availability-result queueing with a `Flow` collector.
- Add a periodic `WorkManager` job to refresh the Supabase cache in the background (the web app relies on a user visiting; an Android app can pre-warm).
- Decide whether to keep MegaPlay enrichment or replace with our own source availability (see TODOs).

## Key takeaways

- **Read order is local → Supabase → AniList.** Write order is the reverse (AniList fetch → enrich → write Supabase + local in parallel). The user's "AniList → Supabase → local" framing matches the write order; the read order is what the home page actually executes first.
- **The `enriched` boolean is the entire optimization.** A cache hit with `enriched=true` skips the slow availability + episode-count pipeline entirely. Write enriched data only after verification guards pass; never write half-enriched data.
- **Stale cache is better than no cache.** When AniList fails, the reference app serves stale Supabase/localStorage data past TTL rather than showing an error. The `allowStale=true` parameter is the mechanism.
- **Per-item keys + schema versioning** give granular invalidation (one anime's cache can be cleared without touching others) and global invalidation (bump `HOMEPAGE_CACHE_VERSION` to invalidate every client at once).
- **Prisma is a red herring** in the reference codebase — it is scaffolded but unused. The real local cache is `localStorage`. ANI-KUTA's SQLDelight plays the role `localStorage` plays in aniwatch, not the role Prisma was supposed to play.

## TODOs / open questions

- **MegaPlay enrichment**: aniwatch's "availability" and "episode counts" come from probing MegaPlay, which is an external stream provider. Does ANI-KUTA stream from MegaPlay? If not, what is our equivalent enrichment source? If we have none, the `enriched` flag and the entire episode-count pipeline can be dropped from the adaptation — Supabase would simply mirror AniList metadata with no extra fields.
- **Supabase vs. own backend**: the user mentioned "Supabase or our backend". Which one? The pattern is identical either way (L2 is a remote Postgres), but the client SDK differs.
- **Search behavior**: aniwatch uses Supabase as a first-class search source (not just a cache) because it can filter on `sub_available`/`dub_available`. Do we need the same? If our enrichment source differs, our search filters will differ.
- **Background refresh**: should ANI-KUTA pre-warm the Supabase cache via a `WorkManager` job, or only refresh on user visit like the web app? The web app's pattern means the first user to visit after a TTL expiry pays the full pipeline cost.
- **Conflict resolution**: aniwatch has no concurrent-write conflict story (single-row UPSERT, last-write-wins). Is that acceptable for ANI-KUTA, or do we need PR-style conflict resolution if multiple devices sync to the same Supabase row?
- **Cache version bumps**: who decides when to increment `HOMEPAGE_CACHE_VERSION` in production? The web app hardcodes it; we should define a policy.
- **`hasAltLinks` / alt-source enrichment**: aniwatch has a parallel `anime_stream_links` table for alternative stream sources and overrides MegaPlay availability with it. Do we have an equivalent?
