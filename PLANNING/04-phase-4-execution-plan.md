# Phase 4 — Execution Plan: First Source & Browse

> Detailed plan for Phase 4. Per `PLANNING/PHASED_PLAN.md`.
> **Approach:** pragmatic — port the essential source-api contract (anime-side
> only), build a minimal AniList client for real data, and create visible UI
> (Browse screen + Extensions settings). The full extension loader is deferred
> to Phase 4B (it's very complex and the user wants visible progress first).

## Goal

1. The source-api contract exists (anime-side) — foundation for extensions.
2. A minimal AniList client fetches real anime data (trending/popular/search).
3. The Browse screen shows real anime content in a grid.
4. The Extensions settings screen shows the 3-category layout (with empty states).
5. The floating bottom nav switches between Home, Browse, and Settings.

## Steps

### Step 1: Minimal source-api (`:core:source-api`)
Port the ESSENTIAL anime-side interfaces from the reference (not all 51 files —
just the core contract that extensions implement and the app uses):
- `AnimeSource` — the base interface.
- `AnimeCatalogueSource` — adds browse/search.
- `AnimeHttpSource` — the HTTP base class (suspend API only, no RxJava).
- `SAnime` / `SAnimeImpl` — the source-side anime model.
- `SEpisode` / `SEpisodeImpl` — the source-side episode model.
- `Video` — the playable video model.
- `AnimesPage` — search/browse result page.
- `AnimeFilter` / `AnimeFilterList` — search filters.
- `AnimeUpdateStrategy` — update strategy enum.
- `FetchType` — anime fetch type enum.

Package: `eu.kanade.tachiyomi.animesource.*` (EXACT match for extension compat, ADR-029).
NOT KMP — Android-only library (simpler, works for extension compat).

### Step 2: Minimal AniList client (`:core:anilist`)
Raw HTTP (OkHttp) + kotlinx-serialization (per ADR-030):
- `AniListApi` — GraphQL queries: trending, popular, search, by-id.
- `AniListModels` — response data classes.
- `AniListRepository` — the interface (per ADR-010).
- `AniListRepositoryImpl` — the impl.
- Rate limiting (90 req/min) + simple in-memory cache.

### Step 3: Browse screen (`:feature:browse`)
- `BrowseScreen` — CollapsingHeader + grid of anime cards.
- `BrowseScreenModel` — calls AniListRepository, holds state.
- `AnimeCard` composable — cover image (Coil) + title.
- Shows trending/popular from AniList.
- Search bar (custom BasicTextField pill, per prototype).
- Loading/error/empty states.

### Step 4: Extensions settings screen (`:feature:extensions-settings`)
- 3-category layout (trusted → installed → available), per ADR-016.
- Anime/manga toggle at the top.
- Empty states (no extensions loaded yet — the loader comes in Phase 4B).
- Uses the design system components (CollapsingHeader, SettingsGroupCard).

### Step 5: Wire navigation in `:app`
- Update MainActivity to show real screens (Home placeholder, Browse, Settings).
- The floating bottom nav switches between them.
- Koin wiring for AniListRepository.

## NOT in Phase 4 (deferred)
- Extension loader (ChildFirstPathClassLoader, installers) → Phase 4B.
- Local source (`:core:source-local`) → Phase 4B.
- Paging (PagingSource) → Phase 4B.
- Manga source-api → Phase 4B (hidden per ADR-009).

## ADRs referenced
- ADR-010: AniList as co-primary data source.
- ADR-029: Keep Aniyomi extension compat (same package names).
- ADR-030: AniList = raw HTTP + serialization.
