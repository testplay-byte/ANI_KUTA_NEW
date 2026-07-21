# Prompt 1 — Library Page: Setup & Analysis

You are a new AI agent joining the ANIKUTA Android project. This is your SETUP prompt — do NOT write any code yet. Your task is to read, understand, and analyze the project so you're fully prepared for the implementation prompt that follows.

## What is ANIKUTA?

ANIKUTA is an anime-first Android app (manga deferred) that combines:
1. An extension-based content system (Aniyomi-compatible — ADR-029)
2. AniList as a co-primary data source (ADR-010)
3. A custom M3-inspired design language (ADR-015) with #B1F256 lime green theme
4. Jetpack Compose UI, Koin DI, SQLDelight database

The app is built with: Kotlin 2.2.0, AGP 8.9.1, Compose BOM 2025.03.00, Material3, RobotoFamily (ExtraBold 800 for headings), SQLDelight 2.0.2, Koin 4.0.0, Coil 3.1.0.

**Current state:** The app has a working browse page (AniList trending/seasonal), anime details page (with source matching, episode list, video resolver), and a fully functional watch page (MPV player with episode switching, subtitles, quality selection, watch progress). The library page is the next major feature.

## Your task

You will implement the **Library Page** — the user's personal anime collection. This includes: saving/removing anime, categories (Default + user-created), grid/list views, sort/filter, search-within-library, continue-watching section, and a beautiful UI following the design language.

## Step 1: Clone the repository

```bash
git clone https://github.com/testplay-byte/ANI_KUTA_NEW.git
cd ANI_KUTA_NEW
```

The repo is a monorepo. Your code goes under `ANIKUTA_PROJECT/ANIKUTA/`. The Aniyomi reference is under `ANIYOMI_REFRENCE/` (read-only). A prototype app with a beautiful library page is under `PROTOTYPE_REFERENCE/Anime_App/` (read-only reference for UI/UX).

## Step 2: Read these files IN THIS ORDER (mandatory)

1. `AGENT_CONTEXT/START_HERE.md` — project onboarding
2. `RULES/ai-agent-rules.md` — the 14-section ruleset (FOLLOW STRICTLY)
3. `RULES/project-conventions.md` — ANIKUTA-specific rules (CI-only builds, reference boundary, etc.)
4. `RULES/notifications.md` — ntfy.sh notification format (MUST send notifications on task completion)
5. `ARCHITECTURE.md` — the single source of truth
6. `DOCS/04-design-decisions.md` — ADRs

## Step 3: Read the design language (CRITICAL — every screen must follow these)

1. `DESIGN_LANGUAGE/01-principles/core-principles.md` — 12 design principles:
   - #1: Edge-to-edge top bar (no inset under status bar)
   - #2: **No drag handle on bottom-up menus** (ALL ModalBottomSheets must set `dragHandle = null`)
   - #3: Bottom-up menus are partial-height (not full-screen)
   - #6: Accent-colored, left-aligned section headers
   - #8: Multi-way toggles (3-way and 2-way)
   - #9: Floating bottom nav (not edge-to-edge)
   - #11: Custom M3-inspired design language (not stock M3)
2. `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` — color scheme + dynamic cover-color theming
3. `DESIGN_LANGUAGE/02-components/components.md` — existing components

## Step 4: Study the Aniyomi reference's library implementation (PRIMARY REFERENCE)

Read these files in `ANIYOMI_REFRENCE/ANIYOMI/`:

**Anime library presentation:**
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryPager.kt`
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryContent.kt`
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryComfortableGrid.kt`
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryCompactGrid.kt`
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryList.kt`
- `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibrarySettingsDialog.kt`

**Library components:**
- `app/src/main/java/eu/kanade/presentation/library/components/CommonEntryItem.kt`
- `app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt`
- `app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt`
- `app/src/main/java/eu/kanade/presentation/library/components/LazyLibraryGrid.kt`
- `app/src/main/java/eu/kanade/presentation/library/components/LibraryToolbar.kt`

**Library screen models:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibrarySettingsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryItem.kt`

**Domain models:**
- `domain/src/main/java/tachiyomi/domain/library/anime/model/AnimeLibrarySortMode.kt`
- `domain/src/main/java/tachiyomi/domain/library/model/LibraryDisplayMode.kt`
- `domain/src/main/java/tachiyomi/domain/library/model/Flag.kt`

**Category system:**
- `domain/src/main/java/tachiyomi/domain/category/anime/` — all files (repository, interactors)

## Step 5: Study the prototype reference app's library page (UI/UX REFERENCE)

Read this file thoroughly — it has a beautiful, well-designed library page that we want to take inspiration from:

- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/ui/screens/LibraryScreen.kt` (1371 lines)
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/data/LibraryRepository.kt`
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/model/` — LibraryItem, LibraryStatus, LibraryLayout, AppSettings, etc.

Key UI patterns from the prototype to study:
- CollapsingHeader with gear button for customize sheet
- Status tabs (All/Watching/Completed/Plan to Watch) with underline on active
- Grid mode (non-lazy chunked rows) + List mode
- Selection mode with floating action bar (Cancel / Category / Delete)
- Customize sheet (layout, columns, text placement, cover details)
- Category sheet (move to category)
- Empty state with icon + description
- CardCell with long-press for selection

## Step 6: Study the NEW project's current state

Read these files to understand what exists and what you'll integrate with:

- `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/MainActivity.kt` — navigation (state-based, bottom nav with Home/Library/More tabs)
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/Theme.kt` — AnikutaTheme
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/Color.kt` — #B1F256 color scheme
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/Type.kt` — RobotoFamily
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/component/` — existing components (BottomNavBar, CollapsingHeader, etc.)
- `ANIKUTA_PROJECT/ANIKUTA/core/database/src/main/sqldelight/` — SQLDelight schema (animes, episodes, animehistory, categories, anime_category tables)
- `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/` — AniList API for fetching anime metadata
- `ANIKUTA_PROJECT/ANIKUTA/core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt` — watch progress (for continue-watching section)
- `ANIKUTA_PROJECT/ANIKUTA/feature/library/` — empty skeleton module (you will fill this)

## Step 7: Key requirements for the library page

1. **Default category**: There is always a "Default" category. If the user has not created any custom categories, the Default category is shown. If the user has created custom categories but hasn't added any anime to the Default category, the Default category is hidden (only custom categories with anime are shown).

2. **Custom categories**: Users can create categories with any name. Special keyword suggestions (case-insensitive): if the user types "watching", "completed", "paused", "dropped", or "planning" (or any 3-letter prefix that matches), a suggestion bubble appears that the user can tap to auto-complete. The suggestion matches the user's typing case (all lowercase → lowercase suggestion, first-letter-capital → capitalized suggestion, ALL CAPS → ALL CAPS suggestion). Suggestions only appear after 3+ characters are typed.

3. **Save flow**: 
   - Short press the save/bookmark button on the detail page → saves to the Default category
   - Long press → opens a bottom-up sheet showing all categories + "Add new category" option. User picks a category to save to.

4. **Grid view + List view**: Both must be implemented. Grid shows covers in a grid (configurable columns). List shows covers (small) + title + metadata.

5. **Sort options**: Title (A-Z, Z-A), Date added (newest, oldest), Last watched, Progress, Total episodes. Match Aniyomi's sort options.

6. **Filter options**: By status (watching, completed, paused, dropped, planning), by category, by source. Match Aniyomi's filter options.

7. **Search within library**: A search bar that filters the library by anime title.

8. **Continue watching**: A section at the top (or a separate tab) showing recently-watched anime with progress bars + episode info, read from WatchProgressStore.

9. **Selection mode**: Long-press an anime to enter selection mode. Floating action bar with: Cancel, Move to Category, Delete.

10. **Design language**: #B1F256 theme, RobotoFamily ExtraBold for headings, no drag handle on any bottom-up menu, edge-to-edge, floating bottom nav.

## Step 8: After reading everything

Send a notification to confirm you're done analyzing:
```bash
curl -s -H "Title: ANIKUTA Agent — Library Page Analysis Complete" -d "Analysis complete. Ready for library page implementation prompt." https://ntfy.sh/TASKISDONE
```

Report back with:
1. A summary of what you learned about Aniyomi's library architecture
2. A summary of the prototype's library UI/UX patterns
3. A list of all files you'll need to create (with module paths)
4. A list of all files you'll need to modify
5. Any questions or concerns before starting implementation
6. Confirmation that you understand the 12 design principles

DO NOT write any code in this step. Wait for Prompt 2.
