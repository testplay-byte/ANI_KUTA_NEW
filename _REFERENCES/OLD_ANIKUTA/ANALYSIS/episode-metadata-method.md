# OLD ANIKUTA — Per-Episode Metadata Method (4-Source Parallel Enrichment)

**Task ID:** E-1
**Scope:** How the old ANIKUTA project fetches, merges, caches, and displays
per-episode metadata (title, description, thumbnail, air date, filler status)
for an anime's episode list — when the extension itself doesn't provide this
data.

Deep-dive companion to `details-episodes-resolution-screens.md` §"Stage 3".
Feeds the new project's module spec at
`PLANNING/01-feature-specs/episode-metadata-module.md`.

---

## 1. TL;DR

The old project's per-episode metadata method is a **single class** —
`EpisodeMetadataFetcher` (458 lines) — that:

1. Takes an `AniListAnime` + `anilistId` + `episodeCount`.
2. Fans out **four parallel network calls** (one per source) inside a
   `coroutineScope { async { … } … }` block.
3. Waits for all four to resolve (failures collapse to `emptyMap()`).
4. Merges the four maps into one `Map<Int, EpisodeMetadata>` using a
   **fixed per-field priority order** (different priority per field).
5. Returns the merged map. The caller (`DetailViewModel`) creates **new**
   `SEpisode` objects (so Compose recomposes) and persists the result to disk
   via `EpisodeCacheStore`.

The four sources (kicked off in parallel; order below is just merge priority
context):

| # | Source          | Provides                                        | Auth      |
|---|-----------------|-------------------------------------------------|-----------|
| 1 | **Anikage.cc**  | titles + descriptions + thumbnails + air dates  | none      |
| 2 | **AniList** streaming episodes | titles + thumbnails (from anime obj) | none |
| 3 | **Jikan / MAL v4** | titles + air dates                          | none      |
| 4 | **Kitsu**       | titles + descriptions + thumbnails + air dates  | none      |

Filler status is **not** fetched by this class — it comes from the extension
source's `SEpisode.fillermark` field and is preserved (not overwritten) during
enrichment. See §8.

---

## 2. File paths in the old project

All paths relative to `OLD_ANIKUTA/ANIKUTA_OLD/`.

| Concern | Path |
|---|---|
| The fetcher itself | `app/src/main/java/app/anikuta/data/metadata/EpisodeMetadataFetcher.kt` (458 lines) |
| Caller / orchestrator | `app/src/main/java/app/anikuta/ui/detail/DetailViewModel.kt` lines 385–485 (`enrichEpisodesWithMetadata`) |
| Detail-screen UI binding (`isEnrichingMetadata` → "Fetching metadata…" pill) | `app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt` lines 64, 392–412, 516–536 |
| Disk cache for enriched episode lists | `app/src/main/java/app/anikuta/data/cache/EpisodeCacheStore.kt` (153 lines) |
| In-memory episode cache (`episodeCache` map) | `DetailViewModel.kt` line 130 |
| Per-field fetch toggles (preferences) | `app/src/main/java/app/anikuta/player/PlayerPreferences.kt` lines 131–146 |
| Settings screen for the toggles | `app/src/main/java/app/anikuta/ui/settings/MetadataSettingsScreen.kt` lines 44–49, 109–145 |
| AniList GraphQL client (used for `idMal` lookup) | `app/src/main/java/app/anikuta/data/anilist/repository/AniListRepository.kt` |
| AniList GraphQL queries (`streamingEpisodes` comes from here) | `app/src/main/java/app/anikuta/data/anilist/api/AniListQueries.kt` lines 114–140 |
| AniList data models (incl. `AniListAnime.streamingEpisodes`) | `app/src/main/java/app/anikuta/data/anilist/model/AniListModels.kt` |
| Episode row UI (displays `preview_url`, `summary`, `date_upload`) | `app/src/main/java/app/anikuta/ui/detail/components/EpisodeRowContent.kt` |
| Watched-episode store (separate concern, not metadata) | `app/src/main/java/app/anikuta/data/cache/EpisodeSeenStore.kt` |
| 3-tier cache (Local → Supabase → AniList) — used for **anime-level** metadata, NOT per-episode | `app/src/main/java/app/anikuta/data/cache/CacheManager.kt` |

> **Two unrelated "episode" caches:** `EpisodeCacheStore` persists the
> **episode list** (URL + name + number + scanlator + preview_url + summary +
> date_upload) as one JSON file per anime. `EpisodeSeenStore` persists the
> **watched flag** (boolean per episode URL) as one big preference set.
> Neither is a per-field metadata cache — the metadata is *merged into* the
> `SEpisode` list before it gets handed to `EpisodeCacheStore`. There is no
> separate "metadata cache" with its own TTL.

---

## 3. The data model

### 3.1 The fetcher's output type — `EpisodeMetadataFetcher.EpisodeMetadata`

Defined inline as a private nested data class (line 51):

```kotlin
data class EpisodeMetadata(
    val title: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val airDate: Long?,     // epoch millis
)
```

This is the **intermediate** type — the per-source result. All four sources
produce this same shape, then the merge step produces one final
`EpisodeMetadata` per episode number.

### 3.2 The final output type — `Map<Int, EpisodeMetadata>`

Keyed by **1-based episode number** (Int). Episodes with no metadata from any
source are simply absent from the map — the caller handles the "no metadata"
case by leaving the original `SEpisode` untouched.

### 3.3 The `SEpisode` it enriches (extension-side type)

`SEpisode` is the source-API episode interface. Relevant fields (mirrored by
`EpisodeCacheStore.CachedEpisode`):

| Field            | Type     | Set by metadata fetcher? |
|------------------|----------|---------------------------|
| `url`            | String   | no — from extension |
| `name`           | String   | **yes** (if blank or "Episode N") |
| `episode_number` | Float    | no — from extension |
| `scanlator`      | String?  | no — from extension |
| `preview_url`    | String?  | **yes** (if blank) |
| `summary`        | String?  | **yes** (if blank) |
| `date_upload`    | Long     | **yes** (if ≤ 0) |
| `fillermark`     | Boolean  | **no** — preserved from extension (see §8) |

### 3.4 The `AniListAnime` input

The fetcher takes the cached AniList anime object as input. It uses:

- `anime.idMal` — to look up Jikan + Kitsu (the MAL-ID-bridge sources). If
  `idMal` is missing, it does a one-shot AniList GraphQL `Media(id, type)`
  lookup to fetch just `idMal`.
- `anime.streamingEpisodes` — list of `{ title, thumbnail, url }` already
  fetched by the detail page's AniList call. Source 2 reads these directly
  without another network call.
- `anime.bannerImage` (fallback) and `anime.coverImage.best()` (fallback of
  fallback) — used as the per-episode thumbnail when *no* source has a real
  thumbnail but at least one source returned *some* data.

---

## 4. The four sources, in detail

### 4.1 Source 1 — Anikage.cc (TheTVDB-based) — PRIMARY

- **Endpoint:** `GET https://anikage.cc/api/media/anime/{anilistId}/episodes`
- **Headers:** `User-Agent: Mozilla/5.0 (Linux; Android 14) …`, `Accept: application/json`
- **Auth:** none. Not behind Cloudflare — direct OkHttp works.
- **Provides:** title, description, thumbnail (image URL), air date.
- **Key field:** `number` (1-based Int) — used as the map key. Episodes with
  a null `number` are skipped.
- **Post-processing:** description is HTML-stripped via `stripHtml()` (regex
  `<[^>]+>` → `""`); air date parsed as `"${airDate}T00:00:00+00:00"` →
  epoch millis via `java.time.Instant.parse`.
- **Error handling:** any exception → log + return `emptyMap()`. HTTP non-2xx
  → log + return `emptyMap()`.
- **Why "primary":** the only source that provides *all four* fields (title +
  description + thumbnail + air date) in a single call.

### 4.2 Source 2 — AniList `streamingEpisodes`

- **Endpoint:** none — reads from the already-fetched `anime.streamingEpisodes`
  list (no extra network call).
- **Provides:** title + thumbnail only. Description and air date are `null`.
- **Key field:** **list index + 1** is used as the episode number. **Known
  fragility:** if AniList's streaming episodes don't align 1:1 with the
  extension's episode numbering (e.g., a recap episode exists on AniList but
  not on the source), metadata will be off-by-one. The old project accepts
  this risk because AniList streaming episodes are mostly used for the
  *thumbnail*, which is visually tolerant of misalignment.
- **Error handling:** can't fail (no network). Empty list → empty map.

### 4.3 Source 3 — Jikan / MyAnimeList v4

- **Endpoint:** `GET https://api.jikan.moe/v4/anime/{malId}/episodes`
- **Auth:** none (free, no API key). Rate-limited (~3 req/sec, 60 req/min).
- **Provides:** title + air date only. Description and thumbnail are `null`
  (Jikan v4's `/episodes` endpoint doesn't include synopsis or thumbnails).
- **Key field:** `malId` (the MAL episode ID — an Int) is used as the episode
  number. Relies on MAL numbering episodes 1, 2, 3, … (usually true).
- **Retry / rate-limit handling:**
  - 500 ms courtesy delay before the call.
  - On HTTP 429 or 504: the outer `fetchFromJikanWithRetry` catches the
    thrown exception and returns `emptyMap()` — **no actual retry happens**
    despite the method name. The `JIKAN_MAX_RETRIES = 5` and
    `JIKAN_RETRY_DELAY_MS = 3000L` constants are declared but unused
    (dead code). Known wart.
  - On other non-2xx: log + return `emptyMap()`.
- **idMal prerequisite:** requires `anime.idMal`. If the cached anime object
  doesn't have it, the fetcher calls `lookupMalId(anilistId)` — a one-shot
  AniList GraphQL `Media(id, type) { idMal }` query. If *that* fails, both
  Jikan and Kitsu are skipped.

### 4.4 Source 4 — Kitsu

- **Endpoints (two-step):**
  1. `GET https://kitsu.app/api/edge/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]={malId}&include=item`
     → returns the Kitsu anime ID via the `included` array.
  2. `GET https://kitsu.app/api/edge/anime/{kitsuId}/episodes?page[limit]=20&sort=number`
     → returns the episode list (paginated; **only page 1 is fetched**, capped
     at 20 episodes — a known limitation for long-running series).
- **Auth:** none. Headers: `Accept: application/vnd.api+json`.
- **Provides:** title (`canonicalTitle`), description (`synopsis`),
  thumbnail (`thumbnail`), air date (`airdate`).
- **Key field:** `attributes.number` (Int) — used as the map key.
- **Rate limiting:** 500 ms delay before *each* of the two calls (~1 sec total
  courtesy delay for Kitsu).
- **Error handling:** any HTTP non-2xx or parse failure → log + return
  `emptyMap()`. The MAL→Kitsu mapping step failing (common — Kitsu's catalog
  is smaller than MAL's) returns empty without attempting the episodes call.
- **Post-processing:** the `jsonObject["…"]?.toString()?.trim('"')` pattern
  extracts string fields, with a `?.takeIf { it != "null" }` guard because
  kotlinx.serialization's `toString()` on a JSON-null literal yields the
  string `"null"`.

---

## 5. The fetching pipeline (order, parallelism, fallback)

### 5.1 Order & parallelism

All four sources are launched **in parallel** inside a single
`coroutineScope { … }` block (lines 78–152). Each source is wrapped in
`async { try { … } catch (e: Exception) { emptyMap() } }`. The fetcher then
`await()`s all four before merging. There is no streaming of partial
results — the caller sees nothing until *all four* sources have resolved or
failed.

This means user-perceived latency is `max(anikage, anilist, jikan, kitsu)`
plus the merge step, not the sum. In practice AniList streaming is
instantaneous (no network), so the bottleneck is the slowest of the three
network sources.

### 5.2 Fallback strategy

The fallback strategy is **per-field, not per-source**. There is no "if
Anikage fails, use Jikan for everything" logic. Each field is resolved
independently from whichever source has it, in a fixed priority order (§5.3).
If a source is completely down, it contributes `emptyMap()` and the other
sources fill in what they can.

### 5.3 Merge priority (the heart of the method)

After all four `await()`s, the fetcher iterates `for (i in 1..episodeCount)`
and builds one `EpisodeMetadata` per episode number by coalescing fields in
this order (lines 145–150):

| Field        | Priority order (first non-null wins)             |
|--------------|--------------------------------------------------|
| `title`      | Jikan → Anikage → Kitsu → AniList streaming      |
| `description`| Anikage → Kitsu                                  |
| `thumbnail`  | Anikage → AniList streaming → Kitsu → banner fallback |
| `airDate`    | Jikan → Anikage → Kitsu                          |

**Why these priorities** (documented in the file header as "matching the
AniKoto extension's behavior" — i.e., inherited from an extension's
battle-tested behavior, not empirically re-tested):

- **Title prefers Jikan** because MAL titles are the most canonical /
  internationally recognized. Anikage (TheTVDB) is second because TVDB titles
  can be English-localized in ways that don't match what users expect.
- **Description prefers Anikage** because Jikan doesn't provide descriptions
  at all (so Anikage is effectively the only source — Kitsu is the fallback).
- **Thumbnail prefers Anikage** because TVDB thumbnails are episode-specific
  screenshots. AniList streaming thumbnails are also episode-specific but
  lower resolution. Kitsu is third. The anime's `bannerImage` is the last
  resort (same image for every episode — only used if at least one source
  returned *some* data, so the user gets *something* visual).
- **Air date prefers Jikan** because MAL air dates are typically the most
  reliable for Japanese broadcast dates.

### 5.4 The "hasAnyRealData" guard

```kotlin
val hasAnyRealData = anikage.isNotEmpty() || jikan.isNotEmpty() ||
                     anilist.isNotEmpty() || kitsu.isNotEmpty()
```

If *all four* sources returned empty, the banner-fallback thumbnail is **not**
applied — the episode keeps its original (likely null) thumbnail. This avoids
the visual oddity of every episode showing the same banner image when there's
genuinely no per-episode data anywhere.

### 5.5 Reference: exact merge code (lines 138–151)

```kotlin
for (i in 1..episodeCount) {
    val ag = anikage[i]; val a = anilist[i]; val j = jikan[i]; val k = kitsu[i]
    val thumb = ag?.thumbnailUrl ?: a?.thumbnailUrl ?: k?.thumbnailUrl ?:
                if (hasAnyRealData) fallbackThumbnail else null
    results[i] = EpisodeMetadata(
        title       = j?.title ?: ag?.title ?: k?.title ?: a?.title,
        description = ag?.description ?: k?.description,
        thumbnailUrl = thumb,
        airDate     = j?.airDate ?: ag?.airDate ?: k?.airDate,
    )
}
```

---

## 6. Caching strategy

### 6.1 What is cached

The merged `EpisodeMetadata` is **not** cached on its own. After the merge,
the caller creates new `SEpisode` objects with the enriched fields baked in,
then persists the **entire enriched episode list** to disk via
`EpisodeCacheStore.save(...)`. So the cache unit is
`(anilistId) → CachedEpisodes { sourceName, timestamp, List<CachedEpisode> }`,
where each `CachedEpisode` already contains the merged `previewUrl`,
`summary`, `dateUpload`.

### 6.2 Where it's cached

| Layer | Location | Survives | TTL |
|---|---|---|---|
| In-memory | `DetailViewModel.episodeCache: MutableMap<Int, Pair<List<SEpisode>, String>>` (line 130) | navigation within the app | none (cleared on VM destroy) |
| Disk | `EpisodeCacheStore` → `app/files/episode_cache/{anilistId}.json` | app restart | none (explicit invalidation only) |

### 6.3 What is NOT cached

- **No Supabase tier for episode metadata.** The 3-tier `CacheManager`
  (Local → Supabase → AniList) is used for **anime-level** metadata
  (trending, popular, the detail-page header). It is **not** wired into the
  per-episode enrichment path. Notable asymmetry — the old project invested
  in a shared cloud cache for anime-level data but left per-episode metadata
  as a per-device disk file.
- **No TTL on the disk cache.** `EpisodeCacheStore` has no expiry logic.
  Episodes are refreshed when (a) the user pulls to refresh (3rd stage of
  `ThreeStagePullRefresh`), (b) a `REFRESH_GUARD_MS = 5 min` throttle has
  elapsed since the last background refresh, or (c) the user manually clears
  cache.
- **No per-field provenance tracking.** The cache stores the *merged* result
  only — there's no record of which source contributed which field. If the
  user changes their preferred source (the new project's ADR-011), the old
  project has no way to re-resolve just the affected fields. Gap the new
  project's spec must address.

### 6.4 Cache invalidation

- `EpisodeCacheStore.clear(anilistId)` — clears one anime.
- `EpisodeCacheStore.clearAll()` — clears everything.
- Implicit: pulling to refresh re-runs the extension fetch + re-runs
  enrichment (which overwrites the cache file).

---

## 7. How it's triggered & displayed

### 7.1 Trigger conditions (DetailViewModel lines 390–412)

`enrichEpisodesWithMetadata(eps, anime)` is called **after** the episode list
has been loaded from the extension (or from disk cache). It runs only if all
of these are true:

1. `PlayerPreferences.enableInAppMetadataFetch()` is `true` (default: true).
2. At least one episode is missing a field the user has opted into fetching:
   - `fetchMetadataThumbnails()` (default: true) AND `ep.preview_url` is blank.
   - `fetchMetadataSummaries()` (default: true) AND `ep.summary` is blank.
   - `fetchMetadataTitles()` (default: true) AND (`ep.name` is blank OR
     `ep.name` matches `Regex("(?i)episode\\s*\\d+")` — i.e., the extension
     only gave a generic "Episode 12" name).
3. If no episode is missing anything the user wants → the fetcher is skipped
   entirely (early return). This is the most important optimization: an
   extension that already provides rich metadata pays zero enrichment cost.

### 7.2 The "Fetching metadata…" indicator

While enrichment runs, `DetailViewModel._isEnrichingMetadata` is `true`. The
`DetailScreen` reads this via `collectAsState()` and shows a small
`surfaceVariant` pill beside the "Episodes" header:

```
[ Episodes ]  [⏳ Fetching metadata…]
```

Shown in two places (lines 392–412 and 516–536 — the latter is the
desktop/tablet layout variant). This is the **only** user-visible signal
that enrichment is happening. The episode list is already displayed (with
placeholder thumbnails / generic names); when enrichment completes, the list
is swapped for the enriched version and Compose recomposes the visible rows.

### 7.3 The enrichment swap (DetailViewModel lines 426–478)

Critical implementation detail — the code creates **new** `SEpisode` objects
rather than mutating the existing list in place. The comment at line 427–431
explains why:

> CRITICAL: create NEW SEpisode objects instead of mutating in place.
> Compose's LazyColumn skips recomposition when it receives the same object
> references (equality check passes). By creating new objects, Compose
> detects the change and recomposes visible items immediately — no need to
> scroll to trigger a refresh.

This is a Compose-identity quirk specific to `SEpisode` (an interface, not a
data class — referential equality is the only equality). The new project's
spec must preserve this pattern or use a stable ID-based keying scheme.

After the swap:
- `episodeCache[anilistId]` (in-memory) is updated.
- `episodeCacheStore.save(anilistId, sourceName, enrichedEpisodes)` writes
  the enriched list to disk.
- `_episodes.value = EpisodeState.Loaded(enrichedEpisodes, sourceName)`
  triggers the UI update.

### 7.4 Per-field enrichment rules (DetailViewModel lines 440–454)

Each field is enriched **only if the extension left it blank**:

| Field | Enriched when | Enriched value |
|---|---|---|
| `preview_url` | `fetchThumbnails && ep.preview_url.isNullOrBlank() && meta.thumbnailUrl != null` | `meta.thumbnailUrl` |
| `summary` | `fetchSummaries && ep.summary.isNullOrBlank() && meta.description != null` | `meta.description` |
| `name` | `fetchTitles && (ep.name.isBlank() OR matches "Episode N") && meta.title != null` | `"Episode $epNum - ${meta.title}"` |
| `date_upload` | `ep.date_upload <= 0 && meta.airDate != null && meta.airDate > 0` | `meta.airDate` |

The `name` enrichment prepends `"Episode N - "` so the user sees both the
number and the title (e.g., `"Episode 12 - The Final Battle"`). The original
extension name is preserved if it's anything other than a bare
`"Episode N"` string.

### 7.5 Where it's displayed

`EpisodeRowContent.kt` (rich layout):
- Thumbnail: `AsyncImage(episode.preview_url)` — shown if
  `settings.showThumbnails && !episode.preview_url.isNullOrBlank()`.
- Title: `episode.name` (already enriched).
- Summary: `episode.summary` — collapsible (`summaryExpanded` toggle), shown
  if `settings.showSummaries && !episode.summary.isNullOrBlank()`.
- Air date: `formatDate(episode.date_upload)` — shown as a pill if
  `settings.showDates && episode.date_upload > 0`.

There is **no** separate watch-page display of episode metadata beyond what
the episode list shows. The watch page (mini-player + episode description +
episode list, per ADR-012) reuses the same `EpisodeRowContent` component.

---

## 8. Filler status — important finding

**The `EpisodeMetadataFetcher` does NOT fetch filler status.** Key finding
that corrects a possible misconception.

### 8.1 Where filler status comes from

`fillermark: Boolean` is a field on `SEpisode` (extension API) and `Episode`
(domain model). It is set by the **extension source** when it builds the
episode list — typically from the source's own data (e.g., a Crunchyroll
extension might mark recaps as filler) or from a hardcoded list per anime.

### 8.2 How it's preserved during enrichment

In `DetailViewModel` line 466, the new `SEpisode` is created with
`fillermark = ep.fillermark` — i.e., the original extension value is copied
through. The metadata fetcher's `EpisodeMetadata` type doesn't even have a
`filler` field, so there's nothing to merge.

### 8.3 How filler is used elsewhere

- **Filtering:** `LibraryPreferences.filterEpisodeByFillermarked()` is a
  `TriState` (SHOW_ALL / SHOW_FILLERMARKED / SHOW_NOT_FILLERMARKED) per-anime
  setting.
- **Downloads:** `DownloadPreferences.downloadFillermarkedItems()` (default:
  `false`) — when off, filler episodes are excluded from auto-download.
- **Library badges:** `LibraryAnime.fillermarkCount` and
  `SeasonAnime.fillermarkCount` show a count badge on library/season entries.

### 8.4 Implication for the new project

If the new project wants per-episode filler status from a metadata source
(the old project doesn't do this), it would need to be added as a **fifth
field** on `EpisodeMetadata` and a **fifth source** (or a new field on an
existing source). AniList has a `media.externalLinks` field but no
per-episode filler flag. TheTVDB (via Anikage) does have filler flags but the
Anikage endpoint shown above doesn't expose them. This is a future
enhancement, not a porting concern — TMDB (the owner's planned future
source) does expose per-episode filler flags, so adding TMDB later would
naturally fill this gap.

---

## 9. Error handling summary

| Failure | Behavior |
|---|---|
| Anikage HTTP non-2xx | log + return `emptyMap()` |
| Anikage throws | caught in `async { try { … } catch { emptyMap() } }` |
| AniList streaming empty | `emptyMap()` (not an error) |
| AniList `idMal` lookup fails | `malId = null` → Jikan + Kitsu both skipped (logged as warning) |
| Jikan HTTP 429/504 | `fetchFromJikan` throws → caught in `fetchFromJikanWithRetry` → returns `emptyMap()`. **No retry despite method name.** |
| Jikan other non-2xx | log + return `emptyMap()` |
| Kitsu mapping fails | log + return `emptyMap()` (skip episodes call) |
| Kitsu episodes HTTP non-2xx | log + return `emptyMap()` |
| Kitsu JSON parse fails | log + return whatever was parsed so far |
| All four sources empty | `hasAnyRealData = false` → no banner fallback → episodes keep original (null) metadata |
| `enrichEpisodesWithMetadata` throws | caught at line 479 → "In-app metadata enrichment failed (keeping original data)" — UI keeps showing the original episode list |
| Network entirely down | each source's OkHttp call throws → caught → `emptyMap()` → merge produces nothing → `_episodes.value` unchanged |

The net behavior is **graceful degradation**: enrichment never breaks the
episode list. The worst case is "no enrichment happened", which leaves the
user with the extension's original (possibly sparse) data.

---

## 10. Settings (user-facing)

The metadata fetch is user-configurable via `PlayerPreferences`
(`MetadataSettingsScreen.kt`):

| Preference key | Default | Effect |
|---|---|---|
| `pref_in_app_metadata_fetch` | `true` | Master toggle. Off → enrichment never runs. |
| `pref_fetch_metadata_thumbnails` | `true` | Fetch episode preview images. |
| `pref_fetch_metadata_titles` | `true` | Fetch episode titles (only when extension gave "Episode N"). |
| `pref_fetch_metadata_summaries` | `true` | Fetch episode descriptions. |

The settings screen has a live preview at the top showing an example episode
row, so the user can see the effect of the toggles before applying them. This
matches the owner's "live preview is perfect, beautiful" feedback from D-3.

There is **no** per-source toggle (the user cannot disable just Anikage or
just Kitsu) and **no** source-priority customization (the merge order is
hardcoded). This is a candidate for the new project's "extensible
architecture" ADR-022 — exposing source priority as a user setting.

---

## 11. Strengths to keep

1. **Parallel fetch with `coroutineScope { async { … } }`** — clean, simple,
   latency is `max(sources)` not `sum(sources)`. Keep verbatim.
2. **Per-field merge priority** — different fields preferring different
   sources is correct (Anikage for description, Jikan for air date, etc.).
   Keep the priority order; make it configurable.
3. **The `needsEnrichment` early-return** — if the extension already provides
   everything, skip the fetch entirely. Essential for performance.
4. **The "create new SEpisode objects" trick** — required for Compose
   recomposition. Keep (or use a stable-key scheme).
5. **The "Fetching metadata…" pill** — gives the user feedback that the page
   is alive after the episode list first appears. The owner explicitly
   flagged this as a must-preserve (D-2).
6. **Per-field fetch toggles** — lets the user opt out of, say, thumbnail
   fetching on metered networks. Keep, extend to per-source.
7. **Graceful degradation** — enrichment failures never break the episode
   list. Keep.

## 12. Weaknesses to fix in the new project

1. **No per-source registry** — adding a fifth source (e.g., TMDB per the
   owner's stated plan) requires editing `EpisodeMetadataFetcher.kt` directly
   (new `async { … }` block, new merge line, new response model). The new
   project must make sources pluggable.
2. **No per-field provenance** — the cache stores merged results only. When
   the user changes preferences, there's no way to re-resolve selectively.
   The new project should track per-field source.
3. **No Supabase tier** — per-episode metadata is per-device only. A shared
   cloud cache (like the anime-level `CacheManager` uses) would cut
   redundant API calls across devices. Optional for the new project.
4. **Dead retry code** — `JIKAN_MAX_RETRIES` and `JIKAN_RETRY_DELAY_MS` are
   declared but unused. The new project should either implement real retry
   (with exponential backoff) or remove the constants.
5. **Kitsu pagination bug** — only fetches page 1 (20 episodes). Long series
   (e.g., One Piece, 1000+ episodes) get truncated Kitsu data. The new
   project should paginate.
6. **AniList streaming off-by-one risk** — using list-index+1 as the episode
   number is fragile. The new project should at least log a warning when
   AniList streaming count != extension episode count.
7. **No per-source timeout** — a slow source (e.g., Anikage taking 30s)
   blocks the whole enrichment. The new project should wrap each source in
   `withTimeout`.
8. **No rate-limit coordination** — Jikan and Kitsu are both rate-limited but
   the fetcher doesn't coordinate across anime. If the user opens 5 anime
   quickly, 5 Jikan calls fire and may hit the limit. The new project should
   serialize Jikan calls globally (or use a rate-limiter).
9. **Hardcoded merge priority** — inherited from AniKoto extension. The new
   project should make this configurable per ADR-022 (extensibility) and
   ADR-011 (dual metadata source with user preference).
10. **Filler status gap** — the old project has no per-episode filler flag
    from metadata sources. Adding TMDB later (per the owner's plan) would
    naturally fill this gap.

---

## 13. Sequence diagram (text form)

```
DetailViewModel
   │
   │  episodes loaded from extension (or disk cache)
   │  + AniListAnime available
   ▼
enrichEpisodesWithMetadata(eps, anime)
   │
   ├─ check prefs.enableInAppMetadataFetch()         ── false → return
   ├─ check needsEnrichment (per-field toggles)      ── false → return
   ├─ _isEnrichingMetadata = true  (UI shows "Fetching metadata…")
   │
   ▼  viewModelScope.launch
EpisodeMetadataFetcher.fetch(anime, anilistId, episodeCount)
   │
   ├─ (if anime.idMal == null) lookupMalId(anilistId)  → AniList GraphQL
   │
   ├─ coroutineScope {
   │     async { fetchFromAnikage(anilistId) }    ─┐
   │     async { fetchFromAniList(anime) }        ─┤  parallel
   │     async { fetchFromJikanWithRetry(malId) } ─┤
   │     async { fetchFromKitsu(malId) }          ─┘
   │  }
   │
   ├─ merge by per-field priority → Map<Int, EpisodeMetadata>
   ▼
DetailViewModel
   ├─ for each episode: create NEW SEpisode with enriched fields
   ├─ episodeCache[anilistId] = (enrichedEpisodes, sourceName)  [in-memory]
   ├─ episodeCacheStore.save(anilistId, sourceName, enrichedEpisodes)  [disk]
   ├─ _episodes.value = EpisodeState.Loaded(enrichedEpisodes, sourceName)
   └─ _isEnrichingMetadata = false
```

---

**End of analysis.** This file feeds:
`PLANNING/01-feature-specs/episode-metadata-module.md` (the new module spec).
