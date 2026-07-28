# 04 — Data Model Mapping: AniList vs Extension vs Unified

> **Purpose.** This is the critical synthesis document. It defines the exact field
> contract the **unified details page** (`AnimeDetailScreen`) will consume, and maps
> every field to its AniList source, its extension source, its fallback behavior, and
> the UI section that renders it.
>
> It is built directly on the three analysis docs:
> - `01-anikuta-details-page-analysis.md` — what `AnimeDetailScreen` renders today.
> - `02-anikuta-extension-system-analysis.md` — what `SAnime` / `SEpisode` provide.
> - `03-animiru-details-page-analysis.md` — how Animiru maps `SAnime` → its `Anime`.

---

## 0. The unified data model — `UnifiedAnime`

The translation layer produces a single immutable value the details page renders:

```kotlin
// Proposed (do NOT implement yet — this is the design contract)
data class UnifiedAnime(
    // Identity
    val anilistId: Int?,          // null in pure-extension mode
    val malId: Int?,              // null in pure-extension mode
    val sourceId: Long?,          // null in pure-AniList mode
    val sourceName: String?,      // "AniList" or the extension display name
    val url: String,              // AniList URL or SAnime.url (source-relative)

    // Display
    val title: String,
    val coverUrl: String?,
    val coverColorHex: String?,   // AniList: coverImage.color; Extension: Palette-extracted
    val bannerUrl: String?,

    // Metadata
    val description: String?,
    val genres: List<String>,     // empty list if none
    val status: UnifiedStatus,    // enum: FINISHED / RELEASING / NOT_YET_RELEASED / CANCELLED / UNKNOWN
    val format: String?,          // "TV", "MOVIE", ... (AniList only)
    val episodeCount: Int?,       // AniList total; extension: null (derive from episode list)
    val averageScore: Int?,       // AniList only
    val season: String?,          // AniList: "WINTER"/... (year in seasonYear)
    val seasonYear: Int?,
    val startDate: String?,       // ISO-ish "YYYY-MM-DD"
    val studios: List<String>,    // AniList only
    val nextAiringEpisode: NextAiringEpisode?,  // AniList only

    // Source provenance
    val dataSource: DataSource,   // ANILIST | EXTENSION
)

data class NextAiringEpisode(val episode: Int, val airingAt: Long, val timeUntilAiring: Long)

enum class UnifiedStatus { FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, UNKNOWN }
enum class DataSource { ANILIST, EXTENSION }
```

Episodes reuse the **existing** `Episode` domain model (`core/common/.../model/Episode.kt`) and `SEpisode` from `:core:source-api` — these are already unified. No new episode model is needed.

---

## Table 1 — AniList vs Extension: field-by-field availability

> **Legend.** ✅ = natively provided. ⚠️ = derivable / partial. ❌ = not provided.
> "AniList source" cites `AniListAnime.kt`; "Extension source" cites `SAnime.kt` (see doc 02).

| # | Field | AniList source | Extension source | Both? | Notes |
|---|---|---|---|---|---|
| 1 | **anilistId** | ✅ `AniListAnime.id: Int` (`core/anilist/.../AniListAnime.kt`) | ❌ (only if linked via `ExtensionLinkStore`) | ⚠️ | Extension mode has it ONLY after linking. Unlinked extension anime = null. |
| 2 | **malId** | ✅ `AniListAnime.idMal: Int?` | ❌ | ❌ | Extension cannot provide. Needed for Jikan metadata fallback. |
| 3 | **sourceId** | ❌ (AniList is not a "source" in the extension sense) | ✅ `AnimeCatalogueSource.id: Long` | ❌ | Null in pure-AniList mode. |
| 4 | **sourceName** | ⚠️ hardcoded `"AniList"` | ✅ `AnimeSource.name: String` | ✅ | Display label only. |
| 5 | **url** | ⚠️ AniList site URL `https://anilist.co/anime/{id}` | ✅ `SAnime.url: String` | ✅ | Different schemes; the translation layer normalizes to a display URL. |
| 6 | **title** | ✅ `AniListAnime.title.romaji` (or `.english`/`.native`) | ✅ `SAnime.title: String` | ✅ | AniList has structured title; extension is a single string. |
| 7 | **coverUrl** | ✅ `AniListAnime.coverImage.large` | ✅ `SAnime.thumbnail_url: String?` | ✅ | Different URLs/resolutions; both load via Coil. |
| 8 | **bannerUrl** | ✅ `AniListAnime.bannerImage: String?` | ⚠️ `SAnime.background_url: String?` (rarely populated) | ⚠️ | Extension `background_url` is technically a different concept (page background) but usable as banner fallback. |
| 9 | **coverColorHex** | ✅ `AniListAnime.coverColorHex: String?` (e.g. `"#7a5190"`) | ❌ | ❌ | **The key Phase 9 integration point.** Extension mode must derive via `PaletteExtraction.extractCoverColor(coverUrl)`. |
| 10 | **description** | ✅ `AniListAnime.description: String?` (HTML) | ✅ `SAnime.description: String?` (plain or HTML, source-dependent) | ✅ | Both need HTML→plain-text normalization. See §2. |
| 11 | **genres** | ✅ `AniListAnime.genres: List<String>` | ⚠️ `SAnime.genre: String?` (comma-joined; `SAnime.getGenres(): List<String>` splits on `", "`) | ✅ | Extension may be empty or partial. |
| 12 | **status** | ✅ `AniListAnime.status: String` (`"FINISHED"`, `"RELEASING"`, `"NOT_YET_RELEASED"`, `"CANCELLED"`) | ⚠️ `SAnime.status: Int` (0–6; `0=UNKNOWN, 1=ONGOING/RELEASING, 2=COMPLETED/FINISHED, 3=LICENSED, 4=PUBLISHING_FINISHED, 5=CANCELLED, 6=ON_HIATUS`) | ✅ | Translation: `SAnime.status` Int → `UnifiedStatus`. Mapping table in §3. |
| 13 | **format** | ✅ `AniListAnime.format: String` (`"TV"`, `"MOVIE"`, `"OVA"`, ...) | ❌ | ❌ | Hide section in extension mode. |
| 14 | **episodeCount** (total) | ✅ `AniListAnime.episodes: Int?` | ⚠️ derive from `getEpisodeList(sAnime).size` | ⚠️ | Extension count is "as-fetched", not authoritative total. |
| 15 | **averageScore** | ✅ `AniListAnime.averageScore: Int?` (0–100) | ❌ | ❌ | Hide section in extension mode. |
| 16 | **season** | ✅ `AniListAnime.season: String` (`"WINTER"`, `"SPRING"`, `"SUMMER"`, `"FALL"`) | ❌ | ❌ | Hide section in extension mode. |
| 17 | **seasonYear** | ✅ `AniListAnime.seasonYear: Int?` | ❌ | ❌ | Hide section in extension mode. |
| 18 | **startDate** | ✅ `AniListAnime.startDate` (`{year,month,day}`) | ❌ | ❌ | Hide section in extension mode. |
| 19 | **studios** | ✅ `AniListAnime.studios: List<Studio>` | ❌ | ❌ | Hide section in extension mode. |
| 20 | **nextAiringEpisode** | ✅ `AniListAnime.nextAiringEpisode: NextAiringEpisode?` (`{episode, airingAt, timeUntilAiring}`) | ❌ | ❌ | Hide countdown in extension mode. |
| 21 | **source** (original work source) | ✅ `AniListAnime.source: String` (`"ORIGINAL"`, `"MANGA"`, `"LIGHT_NOVEL"`, ...) | ❌ | ❌ | Different concept from `sourceId`. Hide in extension mode. |
| 22 | **countryOfOrigin** | ✅ `AniListAnime.countryOfOrigin: String` | ❌ | ❌ | Hide in extension mode. |
| 23 | **isAdult** | ✅ `AniListAnime.isAdult: Boolean` | ❌ | ❌ | Hide in extension mode. |
| 24 | **recommendations** | ✅ `AniListAnime.recommendations: List<...>` | ❌ | ❌ | Hide "Recommendations" section in extension mode (no data source). |
| 25 | **relations** | ✅ `AniListAnime.relations: List<...>` | ❌ | ❌ | Hide "Relations" section in extension mode. |
| 26 | **alt titles** | ✅ `AniListAnime.title.english`/`.native` + `synonyms: List<String>` | ❌ | ❌ | Hide alt-titles section in extension mode. |
| 27 | **author / artist** | ❌ (AniList has `staff` but not surfaced on the details page today) | ✅ `SAnime.author: String?`, `SAnime.artist: String?` | ⚠️ | Extension-only field; consider showing in extension mode (Animiru shows author/artist). |
| 28 | **update_strategy** | ❌ | ✅ `SAnime.update_strategy: AnimeUpdateStrategy` | ❌ | Internal — controls fetch scheduling, not displayed. |
| 29 | **fetch_type** | ❌ | ✅ `SAnime.fetch_type: FetchType` | ❌ | Internal — controls season vs episode list rendering. |
| 30 | **season_number** (of show) | ❌ (AniList uses `season` enum + `seasonYear`) | ✅ `SAnime.season_number: Double` | ❌ | Extension-only metadata; rarely displayed. |
| 31 | **initialized** | n/a (AniList data is always "initialized") | ✅ `SAnime.initialized: Boolean` | ❌ | Flag set by `AnimeHttpSource.fetchAnimeDetails`. If false → `getAnimeDetails` should be called to enrich. **ANIKUTA currently never calls `getAnimeDetails`** — a gap (doc 02). |

### Episode-list fields (`SEpisode`)

| # | Field | Extension source | AniList-equivalent | Notes |
|---|---|---|---|---|
| E1 | **url** | ✅ `SEpisode.url: String` | n/a (AniList doesn't expose playable URLs) | Required for resolution + download. |
| E2 | **name** | ✅ `SEpisode.name: String` | n/a | Parsed by `EpisodeTitleParser`. |
| E3 | **episode_number** | ✅ `SEpisode.episode_number: Float` (-1=unknown) | n/a | Used for "Episode N" display + sort. |
| E4 | **date_upload** | ✅ `SEpisode.date_upload: Long` (epoch ms, 0=unknown) | n/a | Used for relative-time display + schedule. |
| E5 | **fillermark** | ✅ `SEpisode.fillermark: Boolean` | n/a | "Filler" tag. |
| E6 | **scanlator** | ✅ `SEpisode.scanlator: String?` | n/a | Optional sub-group label. |
| E7 | **summary** | ✅ `SEpisode.summary: String?` | n/a | Episode synopsis (rarely populated). |
| E8 | **preview_url** | ✅ `SEpisode.preview_url: String?` | n/a | Thumbnail. |

> Episode metadata (title, air date, thumbnail, description) is **enriched** by
> `EpisodeMetadataRepository` (Jikan/MAL + Anikage.cc + AniList streaming). This
> enrichment requires `anilistId` or `malId` — so it is **skipped** in pure-extension
> mode. See §5.

---

## Table 2 — Data translation requirements (per unified field)

For each field the unified page needs: where it comes from in AniList mode, where it
comes from in Extension mode, what happens when absent, and any transformation.

| Field | AniList mode | Extension mode | When absent | Transformation |
|---|---|---|---|---|
| anilistId | `AniListAnime.id` | `ExtensionLinkStore.getAniListId(sourceId, url)` (if linked) | null → disable tracker buttons, skip metadata enrichment | none |
| malId | `AniListAnime.idMal` | null | null → Jikan source unavailable | none |
| sourceId | null | `AnimeCatalogueSource.id` | null → disable source-switcher menu | none |
| sourceName | `"AniList"` (literal) | `AnimeSource.name` | — | none |
| url | `https://anilist.co/anime/{id}` | `SAnime.url` | — | none |
| title | `AniListAnime.title.romaji` (fallback `.english`, `.native`) | `SAnime.title` | required | none |
| coverUrl | `AniListAnime.coverImage.large` | `SAnime.thumbnail_url` | null → show placeholder | none |
| bannerUrl | `AniListAnime.bannerImage` | `SAnime.background_url` | null → no banner | none |
| coverColorHex | `AniListAnime.coverColorHex` | **`PaletteExtraction.extractCoverColor(coverUrl)`** (Phase 9) | null → default theme | Hex string normalization |
| description | `AniListAnime.description` (HTML) | `SAnime.description` (HTML or plain) | null → hide `SynopsisSection` | **HTML→plain text** (see §2) |
| genres | `AniListAnime.genres` | `SAnime.getGenres()` (splits `genre` on `", "`) | empty → hide `GenresRow` | trim, dedupe |
| status | map `AniListAnime.status` String → `UnifiedStatus` | map `SAnime.status` Int → `UnifiedStatus` (see §3) | `UNKNOWN` → hide status badge | enum mapping |
| format | `AniListAnime.format` | null | null → hide format chip | none |
| episodeCount | `AniListAnime.episodes` | `getEpisodeList(sAnime).size` (after fetch) | null → hide count chip | none |
| averageScore | `AniListAnime.averageScore` | null | null → hide score badge | none |
| season / seasonYear | `AniListAnime.season` + `.seasonYear` | null | null → hide season chip | none |
| startDate | `AniListAnime.startDate` | null | null → hide start-date row | format to "YYYY-MM-DD" |
| studios | `AniListAnime.studios` (filter `isAnimation=true`) | null | null → hide studios row | join names |
| nextAiringEpisode | `AniListAnime.nextAiringEpisode` | null | null → hide countdown | none |
| author / artist | null (AniList staff not surfaced today) | `SAnime.author`, `SAnime.artist` | null → hide | none |
| episodes list | fetch via `SourceMatcher` + `source.getEpisodeList(sAnime)` (current Stage 3) | fetch via `source.getEpisodeList(sAnime)` | empty → `EpisodeState.NoEpisodes` | map `SEpisode` → `Episode` domain (existing mapper) |

---

## Table 3 — UI section visibility matrix

| # | UI Section | AniList mode | Extension mode | Visibility logic |
|---|---|:---:|:---:|---|
| 1 | `DetailBanner` (cover + actions + title) | ✅ | ✅ | Always shown. Cover from respective source. |
| 2 | Title | ✅ | ✅ | Always shown. |
| 3 | Status badge | ✅ | ✅ | Shown if `status != UNKNOWN` (both sources can provide). |
| 4 | Score badge | ✅ | ❌ | Hidden if `averageScore == null`. |
| 5 | Format chip (TV/MOVIE/...) | ✅ | ❌ | Hidden if `format == null`. |
| 6 | Season chip (e.g. "Spring 2024") | ✅ | ❌ | Hidden if `seasonYear == null`. |
| 7 | Episode count chip | ✅ | ⚠️ | Shown if `episodeCount != null` (extension derives after fetch). |
| 8 | `GenresRow` | ✅ | ✅ | Shown if `genres.isNotEmpty()`. |
| 9 | `SynopsisSection` | ✅ | ✅ | Shown if `description.isNotBlank()`. |
| 10 | Next-episode countdown | ✅ | ❌ | Hidden if `nextAiringEpisode == null`. |
| 11 | `EpisodesSection` | ✅ | ✅ | Always shown (fetched from extension in BOTH modes — AniList has no playable episodes). |
| 12 | Episode metadata enrichment (per-episode title/date/thumb) | ✅ (via Jikan/Anikage/AniList) | ⚠️ (skipped — no anilistId/malId) | Skip enrichment in pure-extension mode; show raw `SEpisode.name` + `date_upload`. |
| 13 | Studios row (`InfoSection`) | ✅ | ❌ | Hidden if `studios.isEmpty()`. |
| 14 | Start date row (`InfoSection`) | ✅ | ❌ | Hidden if `startDate == null`. |
| 15 | Author/artist row (`InfoSection`) | ❌ | ✅ | Shown if `SAnime.author != null` (extension-only bonus). |
| 16 | Add-to-library button | ✅ | ✅ | Always shown. Library entry keyed by `anilistId` (linked) OR `sourceId+url` (unlinked). |
| 17 | Track (AniList) button | ✅ | ⚠️ | Enabled if `anilistId != null`; disabled-with-explain in pure-extension mode ("Link to AniList to track"). |
| 18 | Track (MAL) button | ✅ | ❌ | Hidden if `malId == null`. |
| 19 | Share button | ✅ | ✅ | Always shown. Shares the display URL. |
| 20 | Web-search button | ✅ | ✅ | Always shown. Queries `title` on the web. |
| 21 | **Three-dot menu → "View from AniList"** | (current mode) | ✅ (if linked) | Shown if `anilistId != null` AND current mode is EXTENSION. |
| 22 | **Three-dot menu → "View from Extension"** | ✅ (if linked) | (current mode) | Shown if `sourceId != null` AND current mode is ANILIST. |
| 23 | **Three-dot menu → "Switch extension"** | ❌ | ✅ | Extension mode only — opens `ManualSearchSheet` to pick another source for the same title. |
| 24 | Source-switcher (manual search sheet) | ⚠️ (via ManualSearchSheet today) | ✅ | Always available; current `ManualSearchSheet` already does this for AniList mode. |
| 25 | Download buttons (per episode) | ✅ | ✅ | Always shown (download flow uses the extension in both modes). |
| 26 | Adaptive theme (`MaterialTheme(dynamicScheme)`) | ✅ (coverColorHex from AniList) | ✅ (coverColorHex from Palette — Phase 9) | Falls back to default theme if `coverColorHex == null`. |

---

## §2 — HTML → plain text normalization

Both AniList and extension descriptions can contain HTML. AniList uses `<br>`, `<b>`,
`<i>`, `<a href>`, `~~`. Extensions vary. A single normalizer is needed:

```
Input:  "<b>Frieren</b> is an elf...<br><br>After the demon king..."
Output: "Frieren is an elf...\n\nAfter the demon king..."
```

**Recommended:** a small `HtmlToPlainText` util in `:core:common` (or reuse the
existing stripping in `AnikageCcSource.kt` which already strips HTML — doc 02 §3).
Links: keep the anchor text, drop the URL (or render as a footnote). The
`SynopsisSection` already renders plain text in a `Text` composable, so the normalizer
outputs a plain string with `\n` line breaks.

> **Action item for the plan:** check whether `feature/anime-details` already strips
> HTML before rendering `description`. If yes, reuse; if no, add the util.

---

## §3 — Status enum mapping (`SAnime.status: Int` → `UnifiedStatus`)

| `SAnime.status` Int | Aniyomi constant | `UnifiedStatus` | AniList equivalent String |
|---|---|---|---|
| 0 | `UNKNOWN` | `UNKNOWN` | (n/a) |
| 1 | `ONGOING` | `RELEASING` | `"RELEASING"` |
| 2 | `COMPLETED` | `FINISHED` | `"FINISHED"` |
| 3 | `LICENSED` | `UNKNOWN` | (n/a — AniList doesn't have "licensed") |
| 4 | `PUBLISHING_FINISHED` | `FINISHED` | `"FINISHED"` |
| 5 | `CANCELLED` | `CANCELLED` | `"CANCELLED"` |
| 6 | `ON_HIATUS` | `UNKNOWN` (or new `HIATUS`) | (n/a) |

> AniList has fewer states. `LICENSED` and `ON_HIATUS` collapse to `UNKNOWN` unless we
> extend `UnifiedStatus`. Recommendation: keep `UNKNOWN` for now (don't over-engineer);
> revisit if extension-only anime commonly surface these.

---

## §4 — The `getAnimeDetails` enrichment gap

**Critical finding from doc 02:** `AnimeHttpSource.getAnimeDetails(sAnime): SAnime` is
the extension contract for **enriching** a partial `SAnime` (from search results) with
full details (longer description, genres, status, thumbnail). `SAnime.initialized`
starts `false` and is set `true` by `AnimeHttpSource.fetchAnimeDetails`.

**ANIKUTA currently NEVER calls `getAnimeDetails`.** Search returns partial SAnime;
the details page renders whatever the search result contained. This means extension
data shown today is often incomplete even when the extension COULD provide more.

**The translation layer MUST call `getAnimeDetails(sAnime)` before rendering** when
`SAnime.initialized == false`. This is a behavior change, not just a refactor. It adds
one network round-trip on first open of an extension-sourced anime (cached afterward
via the existing DB persistence in `AnimeDetailViewModel.saveEpisodesToDb`).

---

## §5 — Episode metadata enrichment in extension mode

`EpisodeMetadataRepository.fetchAll(request)` (doc 02) takes an
`EpisodeMetadataRequest(animeId, malId, title)` and queries Jikan (needs `malId`),
Anikage.cc (needs `anilistId`), and AniList streaming (needs `anilistId`).

**Pure-extension mode** (unlinked anime): `anilistId == null && malId == null` → all
three sources `supports(request) == false` → `fetchAll` returns empty → no per-episode
metadata. The episode list shows raw `SEpisode.name` + `date_upload` only.

**Linked extension mode** (extension anime linked to AniList): `anilistId != null` →
Anikage.cc + AniList streaming sources active → enrichment works (same as AniList
mode). This is the incentive for users to link.

> No new code needed here — the existing `EpisodeMetadataRepository` already handles
> this correctly via `supports()`. The translation layer just passes through whatever
> `anilistId`/`malId` it has (possibly null).

---

## §6 — Library persistence keying (the Animiru lesson)

Animiru bakes `source: Long` into the `Anime` DB row (`animes.sq:9`), forcing its
"Migrate" feature to create a NEW anime row when switching sources (doc 03 §4). This
is the architecture ANIKUTA must AVOID.

**ANIKUTA's current schema** (`core/database/.../animes.sq`) keys the library by
`_id` (auto-increment) + `anilist_id` (nullable Int). `ExtensionDetailScreen` saves
unlinked extension anime with `anilist_id = null` and identifies them by
`source_id + url` — which makes them invisible to the library/history (doc 01 §8
shortcoming #3).

**Recommendation for the unified page:**
- **Linked anime** (anilistId != null): library row keyed by `anilist_id`. Switching
  the displayed source does NOT change the library row — only the `SourceLinkStore`
  preferred-source pointer changes. This is in-place switching, exactly the UX we want.
- **Unlinked extension anime**: keep the current `anilist_id = null` + `source_id +
  url` identification BUT add a dedicated `anime_source_links` table (or extend
  `SourceLinkStore`) so the row is discoverable from the library. When the user later
  links the anime to AniList, the row's `anilist_id` is back-filled (the existing
  `ExtensionLinkStore` → `SourceLinkStore` flow already supports this).

> This is a SCHEMA consideration — the plan (doc 05) will note whether a migration is
> needed or whether the current schema is sufficient. **Do not implement yet.**

---

## §7 — Phase 9 (adaptive colors) integration

Phase 9 (`feature/voyager-navigation` branch) adds:
- `core/designsystem/.../PaletteExtraction.kt` — `extractCoverColor(url): Int?`
  (currently a documented skeleton returning null; commits `4cd3e66`, `fd985f4`,
  `fa9ba8a`).
- `ThemePreferences` + `AppearanceScreen` — user-selectable mode/accent/AMOLED.
- `AnimeDetailScreen.kt:88-94, 138-160, 207-212` — subscribes to
  `adaptiveColorsDetails`/`themeMode`/`amoled`, computes `coverColorArgb`, calls
  `generateDynamicScheme(...)`, wraps the screen in `MaterialTheme(dynamicScheme)`.

**Integration point for extension mode:**
- AniList mode: `coverColorHex` comes from `AniListAnime.coverColorHex` (already wired).
- Extension mode: `coverColorHex` must come from
  `PaletteExtraction.extractCoverColor(coverUrl)` — the translation layer calls this
  when building `UnifiedAnime` and `coverColorHex` is otherwise null.

**Constraint:** `PaletteExtraction` lives in `:core:designsystem` (no Coil/OkHttp
deps — they were explicitly removed in commit `4cd3e66`). The translation layer
(which lives in a feature/data module with Coil access) is responsible for loading
the cover bitmap and passing it to Palette. The exact API contract between the
translation layer and `PaletteExtraction` is a Phase 9 detail to confirm with the
Phase 9 agent — **do not assume**; flag as an integration question in doc 05.

---

## §8 — Open questions (flagged for owner decision)

1. **Status enum granularity.** Should `UnifiedStatus` include `HIATUS` and
   `LICENSED`, or collapse to AniList's 4 states? (§3) — Recommendation: collapse for
   v1.
2. **Unlinked extension anime in library.** Do we add a dedicated
   `anime_source_links` table, or extend `SourceLinkStore` to also index unlinked
   rows? (§6) — Recommendation: extend `SourceLinkStore` (less schema churn).
3. **`getAnimeDetails` caching.** After calling `getAnimeDetails` to enrich an
   `SAnime`, where do we persist the enriched fields? The existing `animes.sq` row
   has columns for some SAnime fields (description, genre, status, thumbnail_url) but
   not all (author, artist, update_strategy). (§4) — Recommendation: persist what
   fits; re-fetch the rest on each open (cheap, cached by source HTTP layer).
4. **PaletteExtraction API contract.** Does the Phase 9 `PaletteExtraction` accept a
   URL (and loads the bitmap internally) or a pre-loaded `Bitmap`? This affects where
   the translation layer calls it. (§7) — **Must confirm with Phase 9 agent.**
5. **Three-dot menu placement.** The current `DetailBanner.kt:116` three-dot button
   is a no-op stub (doc 01). Confirm this is the intended home for the source-switcher
   menu. — Recommendation: yes, wire it here.
6. **Recommendations/Relations sections.** AniList mode could show these but the
   current `AnimeDetailScreen` does not render them. Are they in scope for the
   unified page, or deferred? — Recommendation: deferred (out of scope for this
   phase; the unified page matches current behavior).

---

*End of doc 04. Next: `05-IMPLEMENTATION_PLAN.md` turns these mappings into a
concrete, ordered implementation plan — pending owner approval.*
