# Prompt 1 — Context Setup & Analysis (Step 5: Anime Details with Real Episodes)

You are a new AI agent joining the ANIKUTA Android project. This is your SETUP prompt — do NOT write any code yet. Your task is to read, understand, and analyze the project so you're fully prepared for the implementation prompt that follows.

## What is ANIKUTA?

ANIKUTA is an anime-first Android app (manga deferred) that combines:
1. An extension-based content system (Aniyomi-compatible — ADR-029)
2. AniList as a co-primary data source (ADR-010)
3. A custom M3-inspired design language (ADR-015)
4. Unique features: watch page, per-episode metadata, dual-mode notifications

The app is built with: Kotlin, Jetpack Compose, Koin DI, SQLDelight, Voyager navigation, Coil images, #B1F256 theme color.

## Step 1: Clone the repository

```bash
git clone https://github.com/testplay-byte/ANI_KUTA_NEW.git
cd ANI_KUTA_NEW
```

## Step 2: Read these files IN THIS ORDER (mandatory)

1. `AGENT_CONTEXT/START_HERE.md` — project onboarding
2. `RULES/ai-agent-rules.md` — the 14-section ruleset (FOLLOW STRICTLY)
3. `RULES/project-conventions.md` — ANIKUTA-specific rules
4. `RULES/notifications.md` — ntfy.sh notification format (MUST send notifications)
5. `ARCHITECTURE.md` — the single source of truth
6. `DOCS/04-design-decisions.md` — ADRs (especially 010, 011, 022, 023, 024, 029)

## Step 3: Read the design language

1. `DESIGN_LANGUAGE/01-principles/core-principles.md` — 12 design principles
2. `DESIGN_LANGUAGE/04-screens/anime-details.md` — the anime details screen spec
3. `DESIGN_LANGUAGE/04-screens/episode-list.md` — the episode list spec

## Step 4: Study the old ANIKUTA project (CRITICAL — this is the primary reference)

Read these files in `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/`:

**Source matching (how the old project finds the right source for an anime):**
- Search for files containing "SourceMatch" or "findMatch" or "matchSource" or "searchSource"
- The old project searches trusted extensions by anime title to find a matching source

**Episode list:**
- `ui/detail/components/EpisodeRow.kt` — episode row with watched grayscale effect
- `ui/detail/components/EpisodeRowContent.kt` — rich/simple layout
- `ui/detail/components/Grayscale.kt` — watched = grayscale + blur (RenderEffect)
- `ui/detail/DetailScreen.kt` — the full details screen
- `ui/detail/DetailViewModel.kt` — the ViewModel that fetches episodes

**Source switching:**
- Search for "source" or "switchSource" or "changeSource" in the detail ViewModel
- The old project lets users switch which source provides episodes for an anime

## Step 5: Study the current ANIKUTA project

Read these files:

**Current anime details screen:**
- `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailScreen.kt`

**Extension manager (how to get loaded sources):**
- `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/AnimeExtensionManager.kt`
- `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/model/AnimeExtension.kt`

**Source API (the contract that sources implement):**
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt`
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeCatalogueSource.kt`
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt`
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt`

**AniList API (for anime metadata):**
- `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/api/AniListApi.kt`
- `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt`

**App navigation:**
- `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/MainActivity.kt`

## Step 6: Key requirements for Step 5

- **Priority-based source search**: Trusted sources are searched in order (top = highest priority). The first source that has the anime wins.
- **Source switching**: User can switch sources on the details page. The selected source is sticky per-anime (persisted in SharedPreferences).
- **Dual metadata**: AniList provides metadata (title, description, cover, score); the extension provides episodes.
- **Toast notifications**: Errors must show Toast messages to the user.
- **Logging**: Use `android.util.Log` with tags like `AnikutaSourceMatcher`, `AnikutaDetailUI`.
- **No local builds**: CI only (ADR-003).
- **Koin DI**: Inject via constructor (ADR-023).
- **Design language**: Follow the #B1F256 theme, RobotoFamily ExtraBold, edge-to-edge, CollapsingHeader, etc.

## Step 7: After reading everything

Send a notification to confirm you're done analyzing:
```bash
curl -s -H "Title: ANIKUTA Agent — Step 5 Analysis Complete" -d "🟩🟩🟩🟩🟩🟩🟩🟩

Analysis complete. Ready for Step 5 implementation prompt." https://ntfy.sh/TASKISDONE
```

Report back with a summary of what you learned. DO NOT write any code in this step.
