# Phase 3 — Detailed Plan (Anime Detail Page)

> Phase 3 builds the anime detail page — where the user lands after tapping an
> anime card. This is where AniList metadata + aniyomi extension episodes merge.

---

## Goals

1. **Detail page shows full anime info** — banner, cover, title, rating, genres, description, status, season, episodes.
2. **Episode list from extensions** — the aniyomi extension system provides available episodes.
3. **AniList + extension data merge** — AniList tells us "this anime has 24 episodes"; the extension tells us "here's where you can stream them."
4. **Save/share buttons** — add to library, share.
5. **Navigation from home** — tap a card → detail page.
6. **3-step cache for detail** — Local (24h) → Supabase (24h) → AniList.
7. **Modern Material 3 UI** — consistent with the redesigned home page.

---

## Sub-steps

### Step 3.1 — DetailViewModel + AniList detail fetch
- `DetailViewModel` that takes an AniList anime ID.
- Uses `AniListRepository.getAnimeDetails(id)` via the CacheManager (24h TTL).
- Exposes StateFlow for: anime metadata, loading, error.
- Also calls the extension system to get available episodes (if extensions are loaded).

### Step 3.2 — Detail page UI (Material 3)
- **Collapsing header** — banner image (backdrop) that collapses on scroll, cover art overlaps.
- **Title + quick info row** — rating · status · episodes · season + year.
- **Genre pills** — horizontal scroll of genre chips.
- **Description** — expandable text (show more / show less).
- **Action row** — Save (add to library), Share, (Watch button?).
- **Episode list** — list of episodes with thumbnail, number, title, description.
- **Sources selector** — which extensions can stream this anime (dropdown or section).

### Step 3.3 — Navigation: home → detail
- Add navigation route: `detail/{anilistId}`.
- Anime card tap → navigate to detail page with the AniList ID.
- Back button → returns to home.

### Step 3.4 — Extension episode lookup (AniyomiSourceBridge)
- When the detail page loads, search installed extensions for the anime title.
- If a match is found, fetch the episode list from the extension.
- Merge AniList episode count with extension episode list.
- If no extension match → show "No streaming source available" + AniList episode list only.

### Step 3.5 — Save to library
- "Save" button adds the anime to the local SQLDelight database (animes table).
- Saved anime appear in the Library tab (Phase 5).
- Heart/bookmark icon toggles saved state.

### Step 3.6 — Share
- "Share" button opens the system share sheet with the AniList URL.

### Step 3.7 — Detail page caching
- AniList metadata cached in Local (24h) + Supabase anime_cache table (24h).
- Extension episode list cached in Local (shorter TTL — 30min) since episodes can change.

### Step 3.8 — Phase 3 verification
- Tap a card on home → detail page loads.
- Banner + cover + title + rating + genres + description all show.
- Episode list shows (if extension is available).
- Save/share work.
- Back button returns to home.
- Fix bugs → Phase 3 complete → Phase 4 (player).

---

## What Phase 3 does NOT include
- **No streaming/player** (Phase 4) — episodes are listed but can't be played yet.
- **No downloads** (Phase 7).
- **No related/recommendations** (deferred).
- **No tracker integration** (Phase 8) — AniList login deferred.
- **No the other 3 designs** (Phase 6) — Material 3 only.

---

## Open questions (CONFIRMED by user)

1. **Episode list layout** — THREE options: list, grid, + one more. User can configure:
   - Layout type (list / grid / compact)
   - Whether to show thumbnails
   - Whether to show descriptions
   - Properly handles missing descriptions (graceful fallback)

2. **Multiple seasons** — separate detail pages per season, linked together.
   Other seasons + related content shown on the detail page. Layout details
   refined after implementation.

3. **Sources selector** — section below episodes (current approach). User wants
   customizability to pick other options later.

4. **Empty extension state** — show just AniList data + "Install an extension
   to stream" message. Confirmed.

5. **Back navigation** — remembers where the user came from (home/search/library).
   Not always home. Confirmed.
