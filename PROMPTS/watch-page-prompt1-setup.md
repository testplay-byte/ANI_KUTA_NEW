# Prompt 1 — Watch Page & Player: Setup & Analysis

You are a new AI agent joining the ANIKUTA Android project. This is your SETUP prompt — do NOT write any code yet. Your task is to read, understand, and analyze the project so you're fully prepared for the implementation prompt that follows.

## What is ANIKUTA?

ANIKUTA is an anime-first Android app (manga deferred) that combines:
1. An extension-based content system (Aniyomi-compatible — ADR-029)
2. AniList as a co-primary data source (ADR-010)
3. A custom M3-inspired design language (ADR-015) with #B1F256 lime green theme
4. Jetpack Compose UI, Koin DI, SQLDelight database, Voyager navigation (declared but not yet used — currently state-based)

The app is built with: Kotlin 2.2.0, AGP 8.9.1, Compose BOM 2025.03.00, Material3, RobotoFamily (ExtraBold 800 for headings), SQLDelight 2.0.2, Koin 4.0.0, Coil 3.1.0, OkHttp 5.0.0-alpha.14.

## Your task

You will implement the **Watch Page + Video Player** — the most critical missing feature. The app can currently: browse anime → open details → load episodes → resolve video sources (servers, audio versions, qualities). But tapping a video quality just shows a Toast. Your job is to build the full watch experience: a YouTube-style watch page with an embedded MPV player, episode list, episode description, fullscreen mode, subtitles, gestures, watch progress, and crash-safe error handling.

## Step 1: Clone the repository

```bash
git clone https://github.com/testplay-byte/ANI_KUTA_NEW.git
cd ANI_KUTA_NEW
```

The repo is a monorepo. Your code goes under `ANIKUTA_PROJECT/ANIKUTA/`. The old ANIKUTA project (your primary reference) is under `OLD_ANIKUTA/ANIKUTA_OLD/`. The Aniyomi reference is under `ANIYOMI_REFRENCE/` (read-only).

## Step 2: Read these files IN THIS ORDER (mandatory)

1. `AGENT_CONTEXT/START_HERE.md` — project onboarding
2. `RULES/ai-agent-rules.md` — the 14-section ruleset (FOLLOW STRICTLY)
3. `RULES/project-conventions.md` — ANIKUTA-specific rules (CI-only builds, reference boundary, etc.)
4. `RULES/notifications.md` — ntfy.sh notification format (MUST send notifications on task completion)
5. `ARCHITECTURE.md` — the single source of truth
6. `DOCS/04-design-decisions.md` — ADRs (especially 010, 011, 012, 015, 022, 023, 024, 025, 029)
7. `DOCS/06-build-and-ci.md` — CI-only build policy (ADR-003)

## Step 3: Read the design language (CRITICAL — every screen must follow these)

1. `DESIGN_LANGUAGE/01-principles/core-principles.md` — 12 design principles (edge-to-edge, NO drag handle on bottom-up menus, floating bottom nav, etc.)
2. `DESIGN_LANGUAGE/04-screens/watch-page.md` — the watch page spec (YOUR PRIMARY DESIGN DOC)
3. `DESIGN_LANGUAGE/04-screens/player.md` — the fullscreen player spec
4. `DESIGN_LANGUAGE/04-screens/video-resolver.md` — how the resolver sheet hands off to the player
5. `DESIGN_LANGUAGE/02-components/components.md` — existing components
6. `DESIGN_LANGUAGE/03-themes/themes-and-colors.md` — color scheme + dynamic cover-color theming

## Step 4: Study the OLD ANIKUTA project's player (CRITICAL — this is your primary reference)

The old project has a **complete, working** player implementation. You will port from it with improvements. Read ALL of these files in `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/`:

**Core player:**
- `PlayerActivity.kt` (2533 lines — the god-object; owns MPV lifecycle, all switching logic, progress saving)
- `PlayerScreen.kt` (1049 lines — Compose host with MINIMIZED/FULLSCREEN branches)
- `PlayerViewModel.kt` (437 lines — pure StateFlow state holder, no MPV calls)
- `AnikutaMPVView.kt` (375 lines — MPV wrapper extending `is.xyz.mpv.BaseMPVView`)
- `MpvConfigManager.kt` — writes default `mpv.conf` + `input.conf`
- `PlayerObserver.kt` — MPV event bridge
- `PlayerUtils.kt` — `resolveUrlForMpv`, content:// → fd://
- `PlayerEnums.kt` — `PlayerMode { MINIMIZED, FULLSCREEN }`, `VideoTrack`, loading states
- `PlayerPreferences.kt` (326 lines — ALL player settings)
- `PlayerEpisodePreferences.kt` — episode list display prefs (separate from detail page)
- `WatchProgressStore.kt` (160 lines — watch progress via PreferenceStore JSON)
- `PlaybackStateStore.kt` (108 lines — video URL + server + audio + quality + tracks for resume)

**Controls:**
- `controls/MinimizedControls.kt` (524 lines — portrait overlay: timestamp, sub+quality icons, play/pause, seekbar, fullscreen button, double-tap gestures)
- `controls/FullscreenControls.kt` (358 lines — landscape overlay: lock, title, server/sub/audio/quality/more icons, replay10/play-pause/forward10, seekbar, speed, rotate, skip, minimize, PiP)
- `controls/PlayerGestureHandler.kt` (169 lines — fullscreen gestures: tap, double-tap seek, horizontal seek, vertical brightness/volume, pinch zoom)
- `controls/EpisodeListView.kt` (851 lines — episode list for the watch page)
- `controls/EpisodeSwitchingOverlay.kt` — loading overlay during switches
- `controls/ServerVersionDropdowns.kt` — server + audio version dropdowns
- `controls/NumericKeypad.kt` — custom numeric keypad for precise input
- `controls/ColorPickerDialog.kt` — color picker for subtitle colors
- `controls/SubtitleSettingsPanel.kt` (522 lines — full subtitle styling panel)
- `controls/FirstTimePlayerPrompt.kt` — first-launch dialog
- `controls/sheets/PlayerSheets.kt` — SubtitleTracksSheet, AudioTracksSheet, SubtitleSettingsSheet, QualitySheet, SpeedSheet, ServerSheet, MoreSheet

**Key MPV integration details to memorize:**
- `AnikutaMPVView` is inflated from `R.layout.mpv_view` (real XML, NOT AndroidView factory — faking XML crashes with ClassCastException)
- `copyAssets` copies `cacert.pem` + `subfont.ttf` to MPV config-dir **ROOT** (NOT `fonts/` — this was a subtitle rendering bug)
- The MPV view is **NEVER recreated** on mode switches — only the overlay layout changes
- `sub-add` commands for external tracks are sent **only after** `MPV_EVENT_FILE_LOADED` (sending before causes silent drops)
- `sid`/`aid` are read as **strings** not ints (MPV returns "node"/"string", `getPropertyInt` returns "unsupported format")
- Demuxer cache is 256 MB (intentional, user requested 2-10 min buffer)
- `keep-open=true` so seeking works after EOF

## Step 5: Study the NEW project's current state

Read these files to understand what exists and what you'll integrate with:

**Current navigation + resolver handoff:**
- `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/MainActivity.kt` — state-based navigation (NOT NavHost). The `onVideoSelected` callback at ~line 234 currently just shows a Toast. You will change this to open the watch page.

**Existing modules (37 Gradle modules total):**
- `:core:player` — **empty skeleton** (you will fill this with the MPV wrapper)
- `:feature:player` — **empty skeleton** (you will fill this with fullscreen player)
- `:feature:watch` — **empty skeleton** (you will fill this with the watch page)
- `:core:episode-metadata` — exists but has no concrete source implementations
- `:feature:video-resolver` — has `VideoResolverSheet`, `ResolverService`, `VideoResolverState`, `VideoTitleParser`
- `:core:designsystem` — theme (#B1F256, RobotoFamily), components (BottomNavBar, CollapsingHeader, etc.)
- `:core:database` — SQLDelight with `episodes` table (has `last_second_seen`, `total_seconds`, `seen`, `summary`, `preview_url` columns)
- `:data:anime` — `EpisodeRepository` (has `updateSeen`, `getByAnimeId`, etc.)
- `:data:history` — `HistoryRepository` (has `upsert`, `observeAll`)

**Design system:**
- `core/designsystem/.../theme/Theme.kt` — `AnikutaTheme(darkTheme = true, amoled = false)`
- `core/designsystem/.../theme/Color.kt` — primary #B1F256, 5 surface tonal tiers, AMOLED support
- `core/designsystem/.../theme/Type.kt` — RobotoFamily (ExtraBold 800 for headings)
- `core/designsystem/.../component/` — BottomNavBar, CollapsingHeader, SegmentedToggles, etc.

**MPV dependencies (declared in catalog, NOT yet consumed):**
- `gradle/anikuta.versions.toml` declares: `aniyomi-mpv-lib:1.18.n`, `ffmpeg-kit:1.18`, `seeker:1.2.2`, `nanohttpd:2.3.1`, `mediasession:1.7.0`, `truetypeparser:2.1.4`
- You MUST add these to `:core:player`'s `build.gradle.kts`
- You MUST enable ABI splits (arm64-v8a per ADR-032) in `app/build.gradle.kts` (currently disabled — comment says "enable when MPV is added")

## Step 6: Key gaps you will need to fill

1. **Handoff data**: `onVideoSelected` only passes `ResolverVideo(quality, url, videoTitle)`. You need to thread animeId, anime title, cover URL, full `SEpisode`, `AnimeSource`, and the full `Video` (for subtitle/audio tracks + headers) through to the watch page.

2. **Player code**: All three player modules are empty. Port from the old project.

3. **Navigation wiring**: Add `watchTarget` state + `when` branch + `BackHandler` extension + nested fullscreen `BackHandler` inside the watch screen.

4. **Episode metadata DI**: `EpisodeMetadataRepository` + `EpisodeMetadataSourceRegistry` are not wired in Koin. Create a module or instantiate manually.

5. **Progress tracking**: 80% threshold for "watched" status isn't enforced. `EpisodeRepository.updateSeen` + `HistoryRepository.upsert` already exist.

6. **Cover-color theming**: `watch-page.md §7` requires dynamic cover-color theming. No helper exists yet in `:core:designsystem`.

7. **Missing design-system components**: `MinimalSeekbar`, `TransparentIconButton`, `NumericKeypad`, `SheetOption`, `MinimizedControls`, `FullscreenControls`, `EpisodeSwitchingOverlay`. Build them or port from old project.

8. **ABI splits**: Currently disabled. You MUST enable arm64-v8a splits when adding MPV.

## Step 7: After reading everything

Send a notification to confirm you're done analyzing:
```bash
curl -s -H "Title: ANIKUTA Agent — Watch Page Analysis Complete" -d "Analysis complete. Ready for watch page + player implementation prompt." https://ntfy.sh/TASKISDONE
```

Report back with:
1. A summary of what you learned about the old project's player architecture
2. A list of all files you'll need to create (with module paths)
3. A list of all files you'll need to modify
4. Any questions or concerns before starting implementation
5. Confirmation that you understand the 12 design principles (especially: NO drag handle on bottom-up menus, edge-to-edge, #B1F256 theme)

DO NOT write any code in this step. Wait for Prompt 2.
