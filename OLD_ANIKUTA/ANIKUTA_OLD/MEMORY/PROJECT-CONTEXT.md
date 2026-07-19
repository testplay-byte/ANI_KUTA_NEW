# Project Context — ANI-KUTA

> High-level, non-technical summary of what we're building.
> Updated as we learn more from the user.

---

## What we're building

- **Name:** ANI-KUTA (display name).
- **GitHub repo:** https://github.com/testplay-byte/anikuta
- **App ID (applicationId):** `app.anikuta`
- **Type:** Android application — anime streaming.
- **Origin:** Built **on the foundations of** the open-source app **aniyomi**
  (https://github.com/aniyomiorg/aniyomi). ANI-KUTA reuses aniyomi's extension
  system, SQLDelight DB pattern, Injekt DI, and MPV player — but uses its own
  techniques in most places (extension trust, source preferences, the AniList +
  Supabase data layer, the entire Compose UI). It is NOT a copy of aniyomi.
- **Three-source architecture:** AniList (discovery/metadata/tracking) + aniyomi
  extensions (streaming sources) + MPV (video player).
- **Reference strategy:** We keep aniyomi as a read-only reference in `REFERENCE/`
  (commit `2f5cf77`) and build our own working code in `app/`, `core/`, `data/`,
  `domain/`, `source-api/`.

## How we handle upstream updates

- `REFERENCE/` = stable, read-only copy (our trusted snapshot).
- `REFERENCE-STAGING/` = where fresh upstream copies land for review.
- Diff staging vs reference → decide what to adopt → apply to our working code.
- (See `MEMORY/CORE-RULES.md` §5 for the full flow.)

## Backup & continuity

- `MEMORY/` and `DOCS/` are pushed to GitHub regularly.
- **Secrets (GitHub token) are gitignored** — never committed.
- If the sandbox resets, the user re-links the GitHub repo and I read `MEMORY/`
  to restore full context. The token must be re-pasted (it's local-only).

## APK signing (current state)

- **Debug:** A committed debug keystore (`app/debug.keystore`, alias `debug`,
  password `android`) is used. This allows installing new debug builds over
  previous ones on a device without uninstalling. Safe — it's a debug key.
- **Release:** UNSIGNED (no `signingConfig` yet). A real release signing flow
  (GitHub Actions secret + gitignored keystore) will be set up when we're ready
  to publish GitHub releases.
- **Distribution target:** GitHub releases (NOT Play Store).
- Full detail: see `SETUP/README.md` → "APK signing".

## Visual progress

- A live web page (route `/`) shows folders, mind map, rules, and status.
- Purpose: help the (non-coder) user see & understand architecture visually.
- (Note: this is the Next.js project in the sandbox, separate from the ANI-KUTA
  Android repo.)

## Account note

- The repo currently lives on a **test account** (`testplay-byte`).
- May migrate to a permanent account later — keep this in mind for any
  hardcoded URLs or remotes (they may need updating on migration).
- GitHub token: fine-grained PAT. Stored locally at
  `MEMORY/CREDENTIALS/github-token.txt` (gitignored).

## Current status

> **For the live, always-current status, read `DOCS/CURRENT-STATE.md`.**
> The summary below may lag.

- **Phases:** 0–7 complete on `main`. Player user-verified on-device (Session 20).
  Full download system merged. Phase 7.5 (episode list enhancements) is next.
- **Build:** GitHub Actions (`.github/workflows/build-apk.yml`). JDK 17 + Android SDK.
  ntfy.sh notifications on completion (topic: `TASKISDONE`). Debug APK, arm64-v8a, 90-day retention.
- **Branches:** Only `main` (active) + `live-preview-dashboard` (temporary).
  The old `player-experiment` branch was merged into `main` (commit `a05d07c`) and deleted.
- **Subtitles:** ✅ Working. Root cause was a fake HTML `subfont.ttf` — replaced with real DejaVu Sans TTF. See `SUBTITLES_FIX.md`.
- **Storage:** ✅ SAF folder selection implemented. See `STORAGE.md`.
- **Settings:** ✅ Reorganized into hub + subpages.
- **Next:** Phase 7.5 (episode list enhancements), then Phase 8 (statistics),
  Phase 9 (4 designs + theming), Phase 10 (final polish). See `DOCS/PLAN/ROADMAP.md`.

## Open questions (resolved)

- [x] Target Android min SDK / compile SDK? → minSdk 26, compileSdk 35
- [x] Languages: Kotlin only? → Yes
- [x] Build system: Gradle KTS? → Yes
- [x] Design language preference for the app UI? → Material 3 Expressive
- [x] Release target? → GitHub releases (NOT Play Store). Temporary signing for now.
- [ ] When to migrate off the test account? — not yet decided.
- [ ] PlayerActivity refactor timing? — deferred; will revisit. See `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P0.4.

---

_Last updated: 2026-07-16 (discovery + documentation cleanup). For live status, read `DOCS/CURRENT-STATE.md`._
