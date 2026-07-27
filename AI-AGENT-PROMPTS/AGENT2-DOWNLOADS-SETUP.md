# AGENT 2 (Downloads & Offline Playback) — SETUP PROMPT

## Credentials

```
REPO URL:   https://github.com/testplay-byte/ANI_KUTA_NEW
PAT TOKEN:  <INSERT_PAT_TOKEN_HERE>
YOUR BRANCH: feature/downloads (create from main)
CLONE CMD:  git clone "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
NTFY:       https://ntfy.sh/TASKISDONE
```

## Special Instruction — Notification Protocol

If you have ANY questions for the user, need clarification, or are blocked on a decision, you MUST send **3 notifications in 2 seconds** to `https://ntfy.sh/TASKISDONE` with the title "QUESTION FROM AGENT 2" and your question in the body. This alerts the user immediately.

---

## Your Setup Task

You are a new AI agent joining the ANIKUTA Android project. Complete this setup task fully before requesting any implementation work.

### What is ANIKUTA?

ANIKUTA is an anime-first Android app built with Jetpack Compose, Koin DI, SQLDelight, and a custom design language (primary color #B1F256 lime green, RobotoFamily font). It's NOT a fork of Aniyomi — the Aniyomi source is a read-only reference at `_REFERENCES/ANIYOMI_REFRENCE/`. The app uses an Aniyomi-compatible extension system.

### Step 1: Clone the Repository

```bash
git clone "https://testplay-byte:<INSERT_PAT_TOKEN_HERE>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
cd anikuta
git config user.email "anikuta-bot@users.noreply.github.com"
git config user.name "anikuta-bot"
git checkout main
git pull origin main
git checkout -b feature/downloads
```

### Step 2: Read the Mandatory Onboarding Files (in this exact order)

1. `AGENT_CONTEXT/START_HERE.md` — the 60-second brief + read order
2. `AGENT_CONTEXT/PROJECT_STARTUP.md` — startup instructions
3. `ARCHITECTURE.md` — the single source of truth
4. `RULES/ai-agent-rules.md` — the 14-section ruleset (NON-NEGOTIABLE)
5. `RULES/project-conventions.md` — ANIKUTA-specific conventions
6. `RULES/notifications.md` — ntfy.sh notification format
7. `DOCS/04-design-decisions.md` — all ADRs (design decisions)
8. `DESIGN_LANGUAGE/` — the UI/UX spec (read `01-principles/core-principles.md` + `02-components/components.md` first)

### Step 3: Read the Worklog

Read `/home/z/my-project/worklog.md` — this has every prior agent's work log. Read the LAST 5 sections to understand what's been built. The latest state is on `main`.

### Step 4: Understand the Module Structure

Read `ANIKUTA_PROJECT/ANIKUTA/settings.gradle.kts`. Key modules (same as Agent 1's list — read the file for the full list). Key modules for your task:
- `:core:source-api` — `Video` model (videoUrl, subtitleTracks, audioTracks), `SEpisode`, `AnimeSource`
- `:core:player` — MPV player, `WatchProgressStore`, `EpisodeListItem`
- `:core:preferences` — `PreferenceStore` (for download preferences)
- `:feature:video-resolver` — `ResolverVideo`, `ResolverService` (resolves video URLs from episodes)
- `:feature:watch` — `WatchScreen`, `WatchRequest` (how the player gets video data)
- `:data:extension` — `AnimeExtensionManager`, `SourceMatcher`, sources

### Step 5: Understand the Build System

Read `ANIKUTA_PROJECT/ANIKUTA/buildSrc/src/main/kotlin/`:
- `anikuta.library` — base Android library (no Compose)
- `anikuta.library.compose` — adds Compose
- CI: `.github/workflows/ci.yml` — APKs built EXCLUSIVELY via GitHub Actions. No local builds.

### Step 6: Understand the Navigation

`MainActivity.kt` uses state flags in a `when` block. New screens are added by: (1) adding a state var, (2) adding a `when` branch, (3) adding a `BackHandler` case.

### Step 7: Understand Koin DI

Modules in `app/src/main/java/.../di/`. Use `koinInject<>()` in composables, `get<>()` in constructors.

### Step 8: Read the Downloads Planning Docs

Read `BACKUP-AND-RESTORE-AND-DOWNLOADING-PLANING/DOWNLOADS-PLAN.md` and `BACKUP-AND-RESTORE-AND-DOWNLOADING-PLANING/FOLDER-STRUCTURE-PLAN.md`.

### Step 9: Read the Video Resolution Flow

Read `feature/video-resolver/src/main/java/.../` to understand how video URLs are resolved from episodes. The download flow needs to resolve the video URL first (same flow as watching), then download the file.

### Step 10: Read the Aniyomi Download Reference

Read `_REFERENCES/ANIYOMI_REFRENCE/ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/` for the Aniyomi download implementation. Take notes on the architecture but DO NOT copy it directly — our architecture is different (AniList-first folder structure, modular DownloadManager interface).

### Step 11: Read the UI Design Language

Read `DESIGN_LANGUAGE/` thoroughly. Also read these existing screens for UI design reference:
- `feature/updates/src/main/java/.../UpdatesScreen.kt` — updates page UI
- `feature/updates/src/main/java/.../ScheduleTabContent.kt` — schedule page UI  
- `feature/my/src/main/java/.../ProfileScreen.kt` — profile page UI
- `feature/library/src/main/java/.../LibraryScreen.kt` — library page UI

These are your UI design references. Follow the same patterns, colors, fonts, spacing.

### Step 12: Append Your Setup Confirmation

Append to `/home/z/my-project/worklog.md`:
```
---
Task ID: AGENT2-DOWNLOADS-SETUP
Agent: Agent 2 (Downloads & Offline Playback)
Task: Environment setup + repo orientation.

Work Log:
- <what you read>
- <what you understood>

Stage Summary:
- <key findings>
- <ready for implementation work>
```

## Hard Rules (NON-NEGOTIABLE)

- **No blind guesses.** If unsure, read the code first.
- **No building APKs locally.** CI-only (ADR-003).
- **Send ntfy.sh notification** on every task completion to `https://ntfy.sh/TASKISDONE`.
- **Use the design language.** Primary #B1F256. RobotoFamily font. Follow `DESIGN_LANGUAGE/`.
- **All network calls on `Dispatchers.IO`.**
- **No nested LazyColumn.**
- **All ModalBottomSheet: `dragHandle = null`.**
- **Push to `feature/downloads` ONLY.** Never push to `main`. Do NOT merge.
- **Wait for CI to complete** before reporting done.
- **Append worklog entries** to `/home/z/my-project/worklog.md`.
- **Plan with 50+ todo entries** before starting implementation.

## After Setup

Report back: "Setup complete. I've read [list]. Ready for the implementation prompt."
