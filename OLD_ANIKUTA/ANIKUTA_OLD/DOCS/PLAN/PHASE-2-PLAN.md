# Phase 2 — Detailed Plan (AniList Data + 3-Step Cache + Home Page with Real Data)

> Phase 2 makes the home page show **real AniList data** using the 3-step caching
> system (Local SQLDelight → Supabase → AniList). Also fixes the Phase 1 UI bugs.

---

## Goals

1. **Home page shows real AniList data** — trending, popular, freshly updated, genres, schedule.
2. **3-step caching works** — Local → Supabase → AniList with fallbacks.
3. **Supabase cache tables created** — `homepage_cache`, `anime_cache`.
4. **Anime cards show real covers** — using Coil image loading.
5. **Tap a card → detail page** (placeholder for now, full detail in Phase 3).
6. **Fix Phase 1 UI bugs** — status bar padding (DONE), backup selection (DONE), real folder picker (later).

---

## Sub-steps

### Step 2.1 — Fix Phase 1 UI bugs (DONE this session)
- ✅ Status bar padding (`statusBarsPadding()`) — progress bar no longer under the notification bar.
- ✅ Backup restore — uses SAF file picker (`OpenDocument`), not a fake path. Shows "No backup selected" when skipped.
- ✅ Storage — auto-selects default folder on first display.
- TODO (later): real SAF folder picker for custom storage location.
- TODO (later): extension list (show all available, not just recommended).

### Step 2.2 — Supabase cache tables
- Create tables in the Supabase project:
  - `homepage_cache` — stores cached home page data (JSON blob) + TTL + version.
  - `anime_cache` — stores individual anime metadata (per AniList ID).
- Set up RLS policies (read: public, write: service_role only).
- Create SQL migration script in `DOCS/APP/STRUCTURE/supabase-schema.md`.
- Run the SQL via the Supabase dashboard or API.

### Step 2.3 — Supabase client (Android)
- Build the Supabase REST client in `app.anikuta.data.supabase`.
- Uses OkHttp (from `:core`) to call the Supabase REST API.
- Reads/writes the cache tables.
- Uses the anon key (safe to embed, protected by RLS).

### Step 2.4 — Local cache (SQLDelight)
- Add cache tables to the anime SQLDelight database:
  - `anilist_cache` — stores cached AniList responses (key, value JSON, timestamp, TTL).
- Add cache queries to the `.sq` files.
- Add a `CacheRepository` that reads/writes the local cache.

### Step 2.5 — 3-step cache layer
- Build the `CacheManager` in `app.anikuta.data.cache`:
  - `getOrFetch(key, ttl, fetchFn)` — tries Local → Supabase → AniList.
  - On AniList success: writes back to Local + Supabase.
  - Fallback: if AniList down → stale Supabase → stale Local.
  - TTLs: Local 5 min (home), 24 h (detail); Supabase 30 min.
- Wire in DI (AppModule).

### Step 2.6 — HomeViewModel
- Create `HomeViewModel` that uses the CacheManager + AniListRepository.
- Exposes StateFlow for each section: trending, popular, fresh, genres, schedule.
- Loading states (skeletons), error states, success states.
- Pull-to-refresh.

### Step 2.7 — Home page UI with real data
- Update `HomeScreen.kt` to use `HomeViewModel`.
- **Anime cards**: real cover images (using Coil), rating, episode count, genres, name.
- **Carousels**: horizontal scroll with real data.
- **Genre chips**: real genres from AniList.
- **Coming Up Next**: real airing schedule (today/tomorrow/next week).
- **Loading skeletons** → real loading states.
- **Error states**: "Couldn't load. Tap to retry."
- **Pull-to-refresh**: re-fetch from cache/AniList.
- Tap a card → navigate to detail page (placeholder for Phase 3).

### Step 2.8 — Coil image loading
- Add Coil 3 dependency (image loading library).
- Wire Coil `ImageLoader` in DI (uses OkHttp from NetworkHelper).
- Anime cards load cover images from AniList URLs.

### Step 2.9 — Phase 2 verification
- Install APK → home page shows real AniList data.
- Test: trending, popular, freshly updated, genres, schedule all load.
- Test: offline (Local cache works for 5 min).
- Test: AniList down (Supabase fallback works).
- Fix bugs.
- Phase 2 complete → Phase 3 (detail page).

---

## What Phase 2 does NOT include
- **No detail page** (Phase 3) — tapping a card goes to a placeholder.
- **No streaming/player** (Phase 4).
- **No the other 3 designs** (Phase 6) — Material 3 only.
- **No downloads** (Phase 7).
- **No extension loading** — the extension stubs stay; real loading in a later phase.
- **No real storage folder picker** — stays as default for now; SAF picker later.
- **No real extension installer** — stays as recommended-only for now.

---

## Dependencies to add
- **Coil 3** (`io.coil-kt.coil3:coil-compose`) — image loading.
- **Supabase client** — we build our own (OkHttp-based), no SDK needed.

---

## Open questions (ask the user)
1. **Coil version** — aniyomi uses Coil 3 (alpha). Use the same, or a stable Coil 2?
2. **Supabase tables** — create them now via the API, or do you want to review the schema first?
3. **Home page customization** — should the show/hide section toggles work in Phase 2, or defer?
4. **Card design** — do you want the full card (cover + rating + sub/dub counts + genres) now, or just cover + name + rating for starters?
5. **Pull-to-refresh** — add now, or later?
