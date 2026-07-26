# ANIKUTA — Master Startup Prompt for AI Agents

> **Give this entire file to a new AI agent as its first message.**
> It contains everything needed to set up, understand, and start working on the project.

---

## Credentials

```
REPO URL:   https://github.com/testplay-byte/ANI_KUTA_NEW
PAT TOKEN:  <INSERT_PAT_TOKEN_HERE>
CLONE CMD:  git clone -b main "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
NTFY:       https://ntfy.sh/TASKISDONE
```

## Special Instruction — Notification Protocol

If you have ANY questions for the user, need clarification, or are blocked on a decision, send **3 notifications in 2 seconds** to `https://ntfy.sh/TASKISDONE` with the title "QUESTION FROM AGENT" and your question in the body. This alerts the user immediately.

---

## Your Setup Task

You are a new AI agent joining the **ANIKUTA** Android project. Complete this setup task fully before requesting any implementation work.

### What is ANIKUTA?

ANIKUTA is an anime-first Android app built with Jetpack Compose, Koin DI, SQLDelight, and a custom design language (primary color #B1F256 lime green, RobotoFamily font). It uses an Aniyomi-compatible extension system and AniList as a co-primary data source.

The app is **feature-complete** with 41 Gradle modules covering: browse, search, anime details, watch (MPV player), library, history, updates (schedule + calendar), profile (stats + charts), trackers (AniList + MAL OAuth), backup/restore, downloads/offline playback, episode settings, and an extension-only details page.

It is **NOT** a fork of Aniyomi. The Aniyomi source is a read-only reference at `ANIYOMI_REFRENCE/`. All code lives in `ANIKUTA_PROJECT/ANIKUTA/`.

### Step 1: Clone the repository

```bash
git clone -b main "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
cd anikuta
git config user.email "anikuta-bot@users.noreply.github.com"
git config user.name "anikuta-bot"
```

### Step 2: Read the mandatory onboarding files (in this exact order)

1. `AGENT_CONTEXT/START_HERE.md` — the 60-second brief + current state + cheat sheet
2. `AGENT_CONTEXT/PROJECT_STARTUP.md` — detailed startup steps (module structure, build, navigation, DI, data flows)
3. `ARCHITECTURE.md` — the single source of truth
4. `RULES/ai-agent-rules.md` — the 14-section ruleset (NON-NEGOTIABLE)
5. `RULES/project-conventions.md` — ANIKUTA-specific conventions
6. `DOCS/04-design-decisions.md` — all ADRs (design decisions)
7. `DESIGN_LANGUAGE/` — UI/UX spec (read `01-principles/core-principles.md` + `02-components/components.md` first)
8. `DOCS/episode-settings-architecture.md` — episode row design + settings system

### Step 3: Read the worklog

Read `/home/z/my-project/worklog.md` — read the LAST 5 sections for recent context. This file has every prior agent's work log.

### Step 4: Understand the module structure

Read `ANIKUTA_PROJECT/ANIKUTA/settings.gradle.kts` — 41 modules. Key ones:

**Core**: `:core:common` (models/repos), `:core:designsystem` (theme/components), `:core:preferences` (PreferenceStore), `:core:database` (SQLDelight), `:core:anilist` (API + cache), `:core:player` (MPV), `:core:episode-metadata` (sources + cache), `:core:source-api` (interfaces), `:core:tracker` (OAuth), `:core:update-checker`, `:core:backup`, `:core:download`

**Feature**: `:feature:browse`, `:feature:search`, `:feature:anime-details`, `:feature:watch`, `:feature:library`, `:feature:episode-settings`, `:feature:history`, `:feature:updates`, `:feature:my`, `:feature:trackers`, `:feature:backup`, `:feature:download`, `:feature:video-resolver`, `:feature:extensions-settings`

**Data**: `:data:anime` (repos), `:data:extension` (manager + matcher + stores)

**App**: `:app` — `MainActivity.kt` (hand-rolled state-machine navigation), `App.kt` (Koin startup), `di/` (Koin modules)

### Step 5: Understand the build system

- Convention plugins in `buildSrc/`: `anikuta.library` (no Compose), `anikuta.library.compose` (adds Compose)
- CI: `.github/workflows/ci.yml` — APKs built EXCLUSIVELY via GitHub Actions. No local builds.
- Push to `main` → auto-trigger. Feature branches → manual `workflow_dispatch`.

### Step 6: Understand the navigation

`MainActivity.kt` uses a **hand-rolled state-machine** (NOT Voyager, NOT Compose Nav). State flags (`currentRoute`, `detailAnimeId`, `showSettings`, `showHistory`, `showDownloads`, etc.) drive a `when` block. New screens: add state var + `when` branch + `BackHandler` case.

### Step 7: Understand Koin DI

Modules in `app/.../di/`. Use `koinInject<>()` in composables, `get<>()` in constructors.

### Step 8: Understand the design language

- **Primary**: #B1F256 (lime green) — `MaterialTheme.colorScheme.primary`
- **Font**: `RobotoFamily` for ALL text
- **Cards**: `surfaceVariant.copy(alpha = 0.4f)`
- **Pills**: `outlineVariant`, 6dp corners, 9sp font, 11sp lineHeight
- **Switches**: Material3 `Switch`
- **Bottom sheets**: `dragHandle = null`
- **No indigo/blue colors.**
- Use `CollapsingHeader` for page titles.
- Reference screens: `UpdatesScreen.kt`, `ProfileScreen.kt`, `LibraryScreen.kt`

### Step 9: Understand key data flows

- **Episode persistence**: saved to SQLDelight DB after fetch → loaded from DB on re-open (offline)
- **Source links**: `SourceLinkStore` persists sourceId + SAnime URL + title
- **Episode metadata**: `EpisodeMetadataCache` persists to PreferenceStore (24h TTL)
- **Video resolution**: `ResolverService` → `getHosterList()` → check `hoster.videoList` first → `VideoTitleParser` (smart audio/server detection) → `ResolverStrategyPicker` (structured vs raw)
- **Extension-only details**: `ExtensionDetailScreen` for anime not on AniList — same UI, "A" re-link button
- **Home page cache**: `LocalAniListCache` — 24h TTL, daily refresh, pull-to-refresh

### Step 10: Read UI reference screens

Read these for UI design patterns:
- `feature/updates/src/main/java/.../UpdatesScreen.kt` — tab strip, pull-to-refresh, cards
- `feature/updates/src/main/java/.../ScheduleTabContent.kt` — list + calendar views
- `feature/my/src/main/java/.../ProfileScreen.kt` — settings sections, charts, toggle rows
- `feature/library/src/main/java/.../LibraryScreen.kt` — grid/list, categories, settings sheet

### Step 11: Append your setup confirmation

Append to `/home/z/my-project/worklog.md`:
```
---
Task ID: AGENT-SETUP
Agent: <your name>
Task: Environment setup + repo orientation.

Work Log:
- <what you read>
- <what you understood>

Stage Summary:
- <key findings>
- <ready for implementation work>
```

## Hard Rules (NON-NEGOTIABLE — from RULES/ai-agent-rules.md)

1. **No blind guesses.** If unsure, read the code first.
2. **No building APKs locally.** CI-only (ADR-003).
3. **Send ntfy.sh notification** on every task completion to `https://ntfy.sh/TASKISDONE`.
4. **Use the design language.** Primary #B1F256. RobotoFamily font. Follow `DESIGN_LANGUAGE/`.
5. **All network calls on `Dispatchers.IO`.**
6. **No nested LazyColumn** inside another LazyColumn.
7. **All ModalBottomSheet: `dragHandle = null`.**
8. **Push to feature branches only.** Never push to `main`. Do NOT merge — the main agent handles merging.
9. **Wait for CI to complete** before reporting done. Verify the APK.
10. **Append worklog entries** to `/home/z/my-project/worklog.md` for every task.
11. **Plan with 50+ todo entries** before starting implementation on large tasks.

## After Setup

Report back: "Setup complete. I've read [list of files]. I understand the module structure, navigation pattern, DI system, design language, and key data flows. Ready for my implementation task."
