# Steps 1.9–1.14 — Material 3 theme, AniList client, onboarding, navigation, home page, APK download

> Steps 1.9 through 1.14 complete the user-facing parts of Phase 1.

---

## Step 1.9 — Material 3 theme ✅

Already done in Step 1.1 (`AnikutaTheme` with Material 3 + Monet dynamic color +
emerald brand seed). No changes needed.

Files: `app/src/main/java/app/anikuta/ui/theme/Theme.kt`, `Color.kt`

## Step 1.10 — AniList client ✅

Our own code (NOT from aniyomi). AniList is our discovery layer.

Files:
- `data/anilist/api/AniListQueries.kt` — GraphQL queries for: trending, popular,
  freshly updated, genres, browse by genre, airing schedule, anime details.
- `data/anilist/model/AniListModels.kt` — data models (AniListAnime, AniListTitle,
  AniListCoverImage, AniListNextAiring, AniListAiringSchedule, GraphQLRequest, etc.)
- `data/anilist/repository/AniListRepository.kt` — the client. Uses OkHttp (from :core)
  to POST GraphQL queries to `https://graphql.anilist.co`. Direct fetch for now
  (3-step cache = Phase 2).

Wired in AppModule: `addSingletonFactory { AniListRepository(get()) }`.

## Step 1.11 — Onboarding wizard (7 steps) ✅

Files:
- `onboarding/OnboardingState.kt` — state management (current step, completion,
  canProceed, next/previous). Required steps: Storage (2), Extension (3), Design (5).
- `onboarding/OnboardingScreen.kt` — the 7-step Compose wizard:
  1. Welcome — app name + tagline
  2. Permissions — notifications + storage
  3. Storage — folder picker (required)
  4. Extension — AniKoto 180 recommended (required)
  5. Backup restore — optional
  6. Design — Material 3 only (others "coming soon") (required)
  7. All set — quick tips + "Start Watching"

State persisted via SharedPreferences (`onboarding_complete`).

## Step 1.12 — Navigation shell ✅

Files:
- `navigation/AnikutaNavGraph.kt` — Compose Navigation with bottom nav:
  - Home, Library, History, Search, More (5 tabs)
  - Library/History/Search/More are placeholder screens for now
  - Home navigates to the home screen

## Step 1.13 — Home page UI ✅

Files:
- `ui/home/HomeScreen.kt` — 6 sections (Material 3):
  1. Hero — app name + tagline
  2. Trending Now — carousel (data from AniList, loading skeleton for now)
  3. Freshly Updated — carousel
  4. Browse by Genre — top 5 genre chips
  5. Most Popular — carousel
  6. Coming Up Next — schedule (placeholder)
- `ui/library/LibraryScreen.kt` — placeholder
- `ui/history/HistoryScreen.kt` — placeholder
- `ui/search/SearchScreen.kt` — placeholder
- `ui/settings/MoreScreen.kt` — placeholder

## Step 1.14 — APK download page ✅

The live preview's `/#build` page already has an APK download section. Updated to
link to the latest GitHub Actions artifact.

## MainActivity updated

Now uses onboarding → navigation flow:
- First boot: shows OnboardingScreen
- After onboarding: shows AnikutaNavGraph (home page + bottom nav)

## SQLDelight fix (also this session)

Fixed the `null!!` workaround in `AnimeDatabaseFactory`:
- Root cause: SQLDelight generates table classes in a package matching the
  directory name relative to srcDir (`dataanime/` → package `dataanime`), NOT
  prefixed with the packageName.
- Fix: `import dataanime.Animehistory` + `import dataanime.Animes`
  (matching aniyomi's pattern).

## Manga removal notes (for re-adding later)

All manga code was removed cleanly:
- Manga SQLDelight DB (`sqldelight/`) — NOT copied. To re-add: copy the `.sq`
  files + create a second `Database` in the SQLDelight config.
- Manga domain (`*/manga/` subdirs) — NOT copied. To re-add: copy the
  `tachiyomi.domain.*/manga/` packages.
- Manga repos — NOT copied. To re-add: copy `tachiyomi.data.*/manga/` packages.
- Manga source-api — NOT copied. To re-add: copy `eu.kanade.tachiyomi.source/`
  from `:source-api`.
- LibraryPreferences — manga methods removed. To re-add: restore the manga
  methods from aniyomi.
- DatabaseAdapter — MangaUpdateStrategyColumnAdapter removed. To re-add: restore
  the adapter + the `UpdateStrategy` import.
- AppModule — manga bindings not present. To re-add: add manga DB, repos, source
  manager, extension manager bindings.

All removals are documented in `DOCS/APP/STRUCTURE/*.md`.
