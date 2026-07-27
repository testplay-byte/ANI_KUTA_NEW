# Phase 5 — Execution Plan: Anime Details + Episode List + Video Resolver

> Per `PLANNING/PHASED_PLAN.md` Phase 5.
> **Approach:** Build the core anime browsing → details → resolve flow.
> Uses AniList for real data; episode list and resolver use AniList + placeholder states.

## Goal

1. Tapping an anime in Browse opens the **anime details screen** (blurred cover + gradient).
2. The details screen shows metadata (title, score, genres, description, episodes).
3. An **episode list** is shown on the details screen (from AniList data).
4. Tapping an episode opens the **video resolver** (placeholder: "No sources" since no extensions loaded yet).
5. The **episode metadata module** (`:core:episode-metadata`) is scaffolded (pluggable, per ADR-022).

## Steps

### Step 1: Anime Details screen (`:feature:anime-details`)
- `AnimeDetailScreen` — full screen with:
  - Blurred cover header + gradient darkening overlay (design language principle #4, component §7).
  - Cover image (overlapping the blurred header).
  - Title (ExtraBold, large), score, format, episodes, status, genres (chips).
  - Description (expandable).
  - Episode list below.
  - Back button (top-left).
  - Uses AniList `fetchById` for real data.
- `AnimeDetailScreenModel` — holds state, calls AniListApi.

### Step 2: Episode list component (`:feature:episode-list`)
- `EpisodeList` composable — shows episodes in a list.
- `EpisodeRow` — each row: episode number, title, air date, watched status.
- Watched = grayscale + blur (design language principle #5, component §6).
- For now: episodes come from AniList (the `episodes` field + estimated episode list).
- Gestures: tap (open resolver), long-press (options). Swipe deferred.

### Step 3: Video resolver (`:feature:video-resolver`)
- `VideoResolverSheet` — bottom sheet (no drag handle, partial height — principles #2, #3).
- Shows: "Resolving..." → "No video sources available" (since no extensions loaded).
- The 3-tier hierarchy (Server → Audio → Quality) is scaffolded but shows empty state.
- Extensible for when extensions are loaded (Phase 4B).

### Step 4: Episode metadata module (`:core:episode-metadata`)
- Scaffold the pluggable module per `PLANNING/01-feature-specs/episode-metadata-module.md`.
- `EpisodeMetadataSource` interface + `EpisodeMetadataSourceRegistry`.
- Placeholder implementation (no real sources yet — the 4 sources come in a later phase).
- `EpisodeMetadataRepository` interface + a no-op impl.

### Step 5: Wire navigation
- BrowseScreen `onOpenAnime(id)` → opens AnimeDetailScreen.
- AnimeDetailScreen episode tap → opens VideoResolverSheet.
- Back navigation.

## NOT in Phase 5
- Actual video playback (Phase 6 — watch page + player).
- Extension-loaded episodes (Phase 4B — extension loader).
- Real episode metadata fetching (deferred — sources need setup).

## ADRs referenced
- ADR-010: AniList as co-primary data source.
- ADR-011: Dual metadata source with fallback.
- ADR-012: Watch page (Phase 6, not this phase).
- ADR-022: Extensible architecture (episode metadata pluggable).
- ADR-025: Compose-first.
