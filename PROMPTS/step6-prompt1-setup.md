# Prompt 1 — Context Setup & Analysis (Step 6: Video Resolver Module)

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
6. `DOCS/04-design-decisions.md` — ADRs (especially 012, 022, 029)

## Step 3: Read the design language

1. `DESIGN_LANGUAGE/01-principles/core-principles.md` — 12 design principles (especially #2: no drag handle, #3: partial height)
2. `DESIGN_LANGUAGE/04-screens/video-resolver.md` — the video resolver screen spec
3. `DESIGN_LANGUAGE/04-screens/player.md` — the player screen spec (for context)

## Step 4: Study the old ANIKUTA project (CRITICAL — this is the primary reference)

Read these files in `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/`:

**Video resolver (the "complete module" the owner wants):**
- `ui/detail/VideoPickerSheet.kt` (362 lines) — the resolver bottom sheet UI
- `ui/detail/VideoTitleParser.kt` (146 lines) — parses server/audio/quality from video titles
- `ui/detail/DetailViewModel.kt` — search for `playEpisode`, `resolveVideos`, `VideoPickerState`

**Key patterns from the old project:**
- The resolver appears AFTER tapping an episode but BEFORE the player opens
- It shows: Server → Audio Version → Quality (3-tier hierarchy)
- The VideoTitleParser extracts server name, audio version (SUB/DUB/HSUB), and quality from the video title string
- If only 1 video is available, it auto-plays (skips the picker)
- State machine: Hidden → Resolving → Cached/Show → NoSources

## Step 5: Study the current ANIKUTA project

**Current video resolver (basic skeleton):**
- `ANIKUTA_PROJECT/ANIKUTA/feature/video-resolver/src/main/java/app/confused/anikuta/feature/videoresolver/VideoResolverSheet.kt`
- `ANIKUTA_PROJECT/ANIKUTA/feature/video-resolver/src/main/java/app/confused/anikuta/feature/videoresolver/VideoResolverState.kt`

**Source API (how sources provide videos):**
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt` — `getVideoList()`, `getHosterList()`
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt` — the Video model
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/Hoster.kt` — the Hoster model
- `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt` — the episode model

**Extension manager (how to get the matched source):**
- `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/AnimeExtensionManager.kt`

**Design system:**
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/component/AnikutaBottomSheet.kt` — bottom sheet (no drag handle, partial height)
- `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/RobotoFamily.kt` (in Type.kt)

## Step 6: Key requirements for Step 6

The owner wants the video resolver to be a **dedicated module** that:
- Properly determines the names of servers
- Properly sorts the servers out
- Properly sorts audio versions based on some templates (SUB/DUB/HSUB)
- Determines and sorts video resolution/quality
- Shows all necessary data appropriately
- Uses smart techniques for various kinds of extensions
- Is easily extensible for new extension types

**Design language rules:**
- NO drag handle on the bottom sheet (principle #2)
- Partial height (principle #3) — doesn't cover the full screen
- RobotoFamily + ExtraBold for bold text
- #B1F256 theme color
- Proper logging (tag: `AnikutaResolver`)

## Step 7: After reading everything

Send a notification:
```bash
curl -s -H "Title: ANIKUTA Agent — Step 6 Analysis Complete" -d "🟩🟩🟩🟩🟩🟩🟩🟩

Analysis complete. Ready for Step 6 implementation prompt." https://ntfy.sh/TASKISDONE
```

Report back with a summary. DO NOT write any code in this step.
