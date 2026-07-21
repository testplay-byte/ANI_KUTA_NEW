# Prompt 1 — Search Page: Setup & Analysis

You are a new AI agent joining the ANIKUTA Android project. This is your SETUP prompt — do NOT write any code yet. Your task is to read, understand, and analyze the project so you're fully prepared for the implementation prompt that follows.

## What is ANIKUTA?

ANIKUTA is an anime-first Android app (manga deferred) that combines:
1. An extension-based content system (Aniyomi-compatible — ADR-029)
2. AniList as a co-primary data source (ADR-010)
3. A custom M3-inspired design language (ADR-015) with #B1F256 lime green theme
4. Jetpack Compose UI, Koin DI, SQLDelight database

The app is built with: Kotlin 2.2.0, AGP 8.9.1, Compose BOM 2025.03.00, Material3, RobotoFamily (ExtraBold 800 for headings), SQLDelight 2.0.2, Koin 4.0.0, Coil 3.1.0.

**Current state:** The app has a working browse page (AniList trending/seasonal), anime details page (with source matching, episode list, video resolver), and a fully functional watch page (MPV player). The library page is being built in parallel by another agent. Your task is the **Search Page**.

## Your task

You will implement the **Search Page** — a dual-source search experience that lets users search anime via AniList OR via installed extensions. This includes: the collapsing top bar with source toggle, search bar with debounce, filter sheet, sort options, recent searches, results grid, and the extension-to-AniList linking flow.

## Step 1: Clone the repository

```bash
git clone https://github.com/testplay-byte/ANI_KUTA_NEW.git
cd ANI_KUTA_NEW
```

The repo is a monorepo. Your code goes under `ANIKUTA_PROJECT/ANIKUTA/`. A prototype app with a beautiful search page is under `PROTOTYPE_REFERENCE/Anime_App/` (read-only reference — **copy-paste the UI and animations exactly**).

## Step 2: Read these files IN THIS ORDER (mandatory)

1. `AGENT_CONTEXT/START_HERE.md` — project onboarding
2. `RULES/ai-agent-rules.md` — the 14-section ruleset (FOLLOW STRICTLY)
3. `RULES/project-conventions.md` — ANIKUTA-specific rules (CI-only builds, reference boundary, etc.)
4. `RULES/notifications.md` — ntfy.sh notification format
5. `ARCHITECTURE.md` — the single source of truth
6. `DOCS/04-design-decisions.md` — ADRs

## Step 3: Read the design language (CRITICAL)

1. `DESIGN_LANGUAGE/01-principles/core-principles.md` — 12 design principles:
   - #1: Edge-to-edge top bar
   - #2: **No drag handle on bottom-up menus** (ALL ModalBottomSheets must set `dragHandle = null`)
   - #6: Accent-colored, left-aligned section headers
   - #11: Custom M3-inspired design language (not stock M3)
2. `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` — #B1F256 color scheme

## Step 4: Study the prototype app's search page (PRIMARY REFERENCE — copy-paste UI)

Read these files THOROUGHLY — you will copy-paste the UI and animations:

- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/ui/screens/SearchScreen.kt` (803 lines)
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/ui/components/FilterSheet.kt` (964 lines)
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/data/AniListClient.kt`
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/model/Anime.kt`
- `PROTOTYPE_REFERENCE/Anime_App/app/src/main/java/com/testplaybyte/animeapp/ui/components/AnimeCard.kt`

Key patterns to study and replicate EXACTLY:
- **Collapsing top bar**: When scrolled, the title shrinks (36sp → 26sp), the source toggle fades+shrinks to 0 width, and the search bar moves BESIDE the title (same row, fills remaining space). All animated with `tween(300, easing = FastOutSlowInEasing)`.
- **Source toggle**: AniList / Extension toggle (pill-shaped, primaryContainer for active). Fades out when collapsed.
- **Search bar**: Two sizes — full (52dp, below title when expanded) and compact (44dp, beside title when collapsed). RoundedCornerShape(50), surfaceVariant background.
- **Quick row**: Filters button (left) + Sort dropdown (right). Slides out (fadeOut + shrinkVertically) when collapsed.
- **Recent searches card**: Collapsible, with individual delete, "Show more/less", "Clear all". In a surfaceVariant card with RoundedCornerShape(20dp).
- **Results grid**: 3-column chunked rows in a surfaceVariant card with RoundedCornerShape(20dp). Section header with label + count.
- **Filter sheet**: Two view modes — Accordion (5 expandable sections, one open at a time) and Flat (tab row + content panel). Bottom actions: "Clear all" + "Apply filters". `dragHandle = null`.

## Step 5: Study the NEW project's current state

Read these files to understand what exists and what you'll integrate with:

- `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/api/AniListApi.kt` — has `searchAnime(query)`, `fetchTrending()`, `fetchPopular()`, `fetchById(id)`
- `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt` — the anime model
- `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt` — has `searchOneSource(sourceId, query)` for extension search, `getAvailableSources()` for listing sources
- `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/AnimeExtensionManager.kt` — manages installed + trusted extensions
- `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/MainActivity.kt` — navigation (state-based, bottom nav)
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeCatalogueSource.kt` — has `getPopularAnime(page)`, `getSearchAnime(page, query, filters)`, `getLatestUpdates(page)`
- `ANIKUTA_PROJECT/ANIKUTA/feature/browse/src/main/java/app/confused/anikuta/feature/browse/BrowseScreen.kt` — existing browse page (for reference on how AniList results are displayed)
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/` — Theme, Color (#B1F256), Type (RobotoFamily)

## Step 6: Study the old project's SourceLinkingScreen (for extension-to-AniList linking)

Read this file for the linking flow pattern:
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/SourceLinkingScreen.kt`
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/data/cache/ExtensionLinkStore.kt`

The old project has a working extension-to-AniList linking system:
1. User taps an extension anime result
2. Searches AniList by the anime title
3. If found → auto-links → opens detail page
4. If not found → shows search results + manual search field + "go without linking" option

## Step 7: After reading everything

Send a notification to confirm you're done analyzing:
```bash
curl -s -H "Title: ANIKUTA Agent — Search Page Analysis Complete" -d "Analysis complete. Ready for search page implementation prompt." https://ntfy.sh/TASKISDONE
```

Report back with:
1. A summary of the prototype's search page UI/UX patterns + animations
2. A summary of the extension-to-AniList linking flow
3. A list of all files you'll need to create (with module paths)
4. A list of all files you'll need to modify
5. Any questions or concerns
6. Confirmation that you understand the 12 design principles

DO NOT write any code in this step. Wait for Prompt 2.
