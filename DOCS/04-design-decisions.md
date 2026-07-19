# 04 — Design Decisions (ADR Log)

> Every non-trivial decision is recorded here so it is never re-litigated
> silently. Format: **ADR-NNN** — short title, context, decision, consequences.
> If a decision is superseded, mark it `~~struck~~` with a "Superseded by ADR-XXX
> on YYYY-MM-DD" note. **Do not delete entries.**

---

## ADR-001 — Monorepo with separated reference and project trees

- **Date:** Phase 0 (initial setup).
- **Context:** We are reimagining Aniyomi. We need (a) the original source for
  study, (b) our own code, (c) docs/rules for AI agents — all co-existing without
  cross-contamination.
- **Decision:** Use a single repository `ANI_KUTA_NEW` with two top-level trees:
  `ANIYOMI_REFRENCE/` (frozen reference) and `ANIKUTA_PROJECT/` (live project).
  Keep the root clean for navigation + `DOCS/` + `RULES/`.
- **Consequences:**
  - ✅ A new agent reads the root, immediately understands layout.
  - ✅ Reference can never be confused with our code.
  - ⚠️ Repo is larger than a pure project repo (the reference is committed).
    Acceptable: ~24 MB source-only, no history bloat.

## ADR-002 — Reference is a source-only tarball snapshot (no `.git`)

- **Date:** Phase 0.
- **Context:** The user stated the reference is "not a complete backup", just a
  local study copy. Including Aniyomi's `.git` would create a nested repository
  (messy, ambiguous, easy to accidentally push back to upstream).
- **Decision:** Snapshot Aniyomi via GitHub's `archive/refs/heads/main.tar.gz`
  tarball. Source files only. No `.git`, no history.
- **Consequences:**
  - ✅ Clean: no nested repo, no submodule complexity.
  - ✅ Small. No history bloat.
  - ⚠️ Cannot `git blame`/`log` the reference. Acceptable — it's a study copy,
    not a working clone. The upstream URL is recorded in
    `ANIYOMI_REFRENCE/README.md` for anyone who needs history.

## ADR-003 — Builds happen via GitHub Actions ONLY

- **Date:** Phase 0.
- **Context:** The user explicitly requires that APKs are never built locally;
  GitHub Actions is the build system.
- **Decision:** No contributor is expected to produce an APK locally. All
  compile/test/release flows live in `.github/workflows/`. Local work is limited
  to editing, type-checking where it doesn't produce an APK, and committing.
- **Consequences:**
  - ✅ Reproducible builds, clean environments.
  - ✅ No contributor needs an Android SDK/toolchain installed locally.
  - ⚠️ Slower feedback loop than local builds — mitigated by keeping CI fast and
    by doing static checks before pushing.
  - 📌 See `06-build-and-ci.md` for the workflow design.

## ADR-004 — License: Apache 2.0 (inherited from Aniyomi)

- **Date:** Phase 0 (default; revisit allowed).
- **Context:** Aniyomi is Apache-2.0 licensed. ANIKUTA reuses Aniyomi's concepts
  and (adapted) code. Apache-2.0 requires preserving attribution and license
  notices in derivatives.
- **Decision:** Inherit Apache-2.0 at the repo root (`LICENSE`). Preserve
  Aniyomi's NOTICE/attribution obligations when porting code.
- **Consequences:**
  - ✅ Legally safe default for an Apache-2.0 derivative.
  - ⚠️ If the owner later wants a different license, this must be revisited and
    any already-ported Aniyomi code re-evaluated for compatibility.
  - 📌 Marked as revisitable: change here if the owner decides otherwise.

## ADR-005 — Reference tree is strictly read-only

- **Date:** Phase 0.
- **Context:** Editing the reference would blur "what Aniyomi does" vs "what we
  do", defeating the purpose of a study snapshot.
- **Decision:** Nothing under `ANIYOMI_REFRENCE/` is ever modified, except to
  replace the whole snapshot with a newer upstream tarball (procedure in
  `ANIYOMI_REFRENCE/README.md`). To port an idea, copy code into
  `ANIKUTA_PROJECT/ANIKUTA/` and adapt there.
- **Consequences:**
  - ✅ The reference always reflects real upstream behavior.
  - ✅ `git diff ANIYOMI_REFRENCE/` is effectively always empty between refreshes.

## ADR-006 — AI-agent-first documentation strategy

- **Date:** Phase 0.
- **Context:** Much of the ongoing work will be done by AI agents that lack
  prior context. Continuity depends on documentation.
- **Decision:**
  - Root `README.md` + `AGENTS.md` are the entry points.
  - `DOCS/04-design-decisions.md` (this file) is the decision log.
  - `DOCS/05-roadmap.md` tracks phase/progress.
  - `RULES/` holds operating conventions + session handoff notes.
  - Every Gradle module in `ANIKUTA_PROJECT/ANIKUTA/` ships a `README.md`.
- **Consequences:**
  - ✅ Any agent can resume work by reading root + `RULES/` + latest session note.
  - ⚠️ Documentation overhead per change — accepted as the cost of continuity.

## ADR-007 — Second read-only reference: the OLD ANIKUTA project

- **Date:** Phase 0 (second setup task).
- **Context:** A previous attempt at ANIKUTA exists at
  `https://github.com/testplay-byte/anikuta`. It reached a working state but had
  structural/modularity issues and unpolished UI — the reasons we are starting
  fresh. However, it contains valuable prior research (Aniyomi subsystem analysis,
  30+ session logs, a modularization assessment) that we should not throw away.
- **Decision:** Add a second read-only reference tree `OLD_ANIKUTA/ANIKUTA_OLD/`
  containing a source-only tarball snapshot of the old project. Treat it with the
  same read-only discipline as `ANIYOMI_REFRENCE/` (ADR-005). Mine it for insights
  during the design phase; do not edit it.
- **Consequences:**
  - ✅ Prior research (especially `DOCS/REFERENCE-DOCS/SUBSYSTEMS/`) is preserved
    and reusable — saves redoing the Aniyomi analysis from scratch.
  - ✅ Lessons-learned (`MODULARIZATION-ASSESSMENT.md`) inform our architecture
    choices so we avoid the old project's pitfalls.
  - ⚠️ The old project shipped its own Aniyomi snapshot at
    `OLD_ANIKUTA/ANIKUTA_OLD/REFERENCE/` (commit `2f5cf77`). This duplicates our
    top-level `ANIYOMI_REFRENCE/` (newer `main`) at an older commit. We keep it
    intact for fidelity — the old project's docs cite line numbers against that
    specific commit. Prefer our top-level snapshot when studying Aniyomi directly.
  - ⚠️ Repo grows by ~37 MB (old project + its nested reference). Acceptable.

## ADR-008 — Task-completion notifications via ntfy.sh

- **Date:** Phase 0 (second setup task).
- **Context:** The owner requires that every completed task — small or big —
  triggers a notification via ntfy.sh to topic `TASKISDONE`, so progress is
  visible without watching the terminal. The owner specified a fixed format
  (8 colored emojis then the message) and a color semantics (green=success,
  red=error, blue=stopped/needs-input, orange=processing).
- **Decision:** Every agent MUST send an ntfy.sh notification on task completion
  (and optionally on starting a long task). The exact format, colors, and curl
  command are specified in `RULES/notifications.md`. This is a hard rule, added
  to `AGENTS.md` §4 and `RULES/agent-conventions.md`.
- **Consequences:**
  - ✅ Owner gets push notifications for every task outcome without polling.
  - ✅ Color coding communicates outcome at a glance.
  - ⚠️ Agents must remember to send the notification even on small tasks.
    Mitigated by documenting it as a hard rule in multiple places.
  - ⚠️ The ntfy.sh topic is public (no auth). Do not include secrets in the
    message body. Documented in `RULES/notifications.md`.

---

## ADRs from the vision-clarification session (Phase 0b)

The following decisions were made during the owner's vision briefing. They
supersede the corresponding open questions listed at the bottom of this file.

## ADR-009 — Anime-first; manga deferred but architecture-ready

- **Date:** Phase 0b (vision clarification).
- **Context:** The owner wants to focus on anime first, with manga coming later.
  The reference (Aniyomi) shows the dual manga/anime pattern is pervasive.
- **Decision:** Build the module architecture to hold **both** anime and manga
  from day 1 (so we don't paint ourselves into a corner), but only **implement**
  anime in Phase 1. Manga modules exist as scaffolding and are **hidden behind
  the UI** (toggleable off) until a later phase.
- **Consequences:**
  - ✅ No rework when manga arrives — the structure already accommodates it.
  - ✅ The user can toggle manga visibility in settings (it's hidden by default).
  - ⚠️ Slightly more upfront structure (some empty manga modules). Accepted.
  - 📌 See `../../RULES/ai-agent-rules.md` §4 (modularity) for the module pattern.

## ADR-010 — AniList as a co-primary data source (not just a tracker)

- **Date:** Phase 0b.
- **Context:** In Aniyomi, AniList is only a progress tracker. In ANIKUTA,
  AniList feeds: Home (discovery/trending), MY (personalized dashboard),
  anime/episode metadata, and (optionally) tracker sync.
- **Decision:** AniList is a **first-class data layer** with its own
  `AniListRepository`, alongside source/extension repositories. A
  `MetadataResolver` merges/falls-back between AniList and extension data per
  ADR-011. AniList is also one of the available trackers (per ADR-013).
- **Consequences:**
  - ✅ Home and MY screens have rich data without requiring extensions.
  - ✅ Metadata is richer than extensions alone provide.
  - ⚠️ More complex data model (two sources for the same concept). Mitigated by
    the MetadataResolver (ADR-011).
  - ⚠️ AniList API rate limits (90 req/min) must be respected — needs caching.

## ADR-011 — Dual metadata source with user preference + automatic fallback

- **Date:** Phase 0b.
- **Context:** The user wants to choose their preferred metadata source (AniList
  vs extension) for anime info (cover, title, description, stats). If the
  preferred source lacks data, fall back to the other; if both lack it, skip.
- **Decision:**
  - A global setting: preferred metadata source = `ANILIST` | `EXTENSION`.
  - A `MetadataResolver` component resolves each metadata field by trying the
    preferred source first, then the alternative, then skipping.
  - The preference is **global** (one setting for all anime), not per-anime.
- **Consequences:**
  - ✅ User gets consistent metadata from their preferred source.
  - ✅ Graceful degradation when one source is incomplete.
  - ⚠️ The resolver must track per-field provenance (which source filled which
    field) for debugging and for re-resolution when the preference changes.

## ADR-012 — Watch page: YouTube-style (minimized player + episodes below, maximizable)

- **Date:** Phase 0b.
- **Context:** The owner wants a "watch page" between the details page and the
  fullscreen player. The player sits at the top (minimized), episodes + episode
  description below. The user can maximize the player to fullscreen. This is
  YouTube-like behavior.
- **Decision:** Implement a `WatchScreen` (Voyager/Compose) that:
  - Hosts an **embedded mini-player** at the top (MPV wrapped in Compose).
  - Shows the current episode's description below the player.
  - Shows the episode list below that.
  - Has a **maximize** control that transitions to a fullscreen `PlayerActivity`
    (or a fullscreen Compose overlay — to be decided in the player architecture
    spec).
- **Consequences:**
  - ✅ Matches the owner's vision exactly.
  - ✅ User can browse episodes while the player runs (mini-player).
  - ⚠️ Embedding MPV (a View) in Compose with proper lifecycle/PiP is
    non-trivial. The old ANIKUTA project has a working implementation — study it
    (see `DESIGN_LANGUAGE/04-screens/watch-page.md` once written).
  - ⚠️ This is a structural departure from Aniyomi (which has no watch page).
  - 📌 The exact player-embedding approach will be decided in the player
    architecture spec (`PLANNING/02-screen-specs/watch-page.md`).

## ADR-013 — AniList: public API primary, authentication enhances

- **Date:** Phase 0b.
- **Context:** AniList feeds Home, MY, and metadata. Should auth be required?
- **Decision:** Use the **public AniList API** (GraphQL, no auth) as the primary
  mode — works for trending, seasonal, basic metadata. **Authentication
  (OAuth)** is optional and **enhances** the experience (personalized MY feed,
  user's list, tracker sync). The app is fully functional without an AniList
  account.
- **Consequences:**
  - ✅ Lower friction — no account required to use the app.
  - ✅ Auth enhances rather than gates — matches modern app patterns.
  - ⚠️ Two modes to support (public vs authenticated). Mitigated by a clean
    `AniListRepository` that transparently adds auth headers when available.

## ADR-014 — Episode release notifications: dual mode (AniList scheduled / extension verified)

- **Date:** Phase 0b.
- **Context:** The owner wants episode-release notifications with two modes:
  1. **By AniList:** at the scheduled release time, notify without verification.
  2. **By extension:** at the scheduled time, check the source; if not yet
     released, recheck in 10 min, then 20 min, until released.
  The release time comes from AniList if the extension doesn't provide one;
  after the first release, the average interval is used for scheduling.
  User can configure sub vs dub, and opt in/out per-anime. Global setting with
  per-series override.
- **Decision:**
  - A `NotificationScheduler` (WorkManager) that supports both modes.
  - Mode is configurable globally and per-series (per-series overrides global).
  - Sub/dub preference: user picks which to be notified about.
  - AniList mode: fire-and-forget at the scheduled time.
  - Extension mode: poll at the scheduled time, retry with backoff (10 min, 20
    min) until the episode appears.
  - Release-time source: AniList first; after first release, use average
    interval for subsequent episodes.
- **Consequences:**
  - ✅ Flexible — user picks the mode that suits their sources.
  - ⚠️ Extension mode is battery-sensitive (polling). Mitigated by the
    10/20-min backoff and only checking anime whose scheduled time has arrived.
  - ⚠️ Needs a reliable "scheduled release time" data point. AniList provides
    this; extension may not.
  - 📌 Detailed spec in `PLANNING/01-feature-specs/episode-notifications.md`.

## ADR-015 — Custom M3-inspired design language (not stock Material 3 Expressive)

- **Date:** Phase 0b.
- **Context:** The owner finds stock Material 3 Expressive insufficient and has
  specific design preferences (documented in `DESIGN_LANGUAGE/`). The old ANIKUTA
  project has screens the owner likes and wants to use as design references.
- **Decision:**
  - Create a **custom design language** inspired by M3 but with the owner's
    specific preferences (documented in `DESIGN_LANGUAGE/`).
  - The design language covers: layout principles, components, color, typography,
    motion, and per-screen specs.
  - Multiple theme options (color palettes) are user-selectable.
  - The old ANIKUTA project's screens are the **primary design reference** (only
    the screens the owner explicitly flagged — see `DESIGN_LANGUAGE/`).
- **Consequences:**
  - ✅ The app looks and feels unique, not stock.
  - ✅ Clear design spec prevents inconsistency.
  - ⚠️ More design upfront work. Accepted — the `DESIGN_LANGUAGE/` folder is
    that work.
  - 📌 See `DESIGN_LANGUAGE/` for the full spec.

## ADR-016 — Extension categories: video / image-manga (no series/movies split)

- **Date:** Phase 0b.
- **Context:** The owner wants extensions sorted into two top-level categories:
  video extensions and image/manga extensions. The future series/movies split is
  deferred (and may live on the anime entry, not the extension).
- **Decision:**
  - Two extension categories: **Video** and **Image/Manga**.
  - The extensions UI shows both categories, with an anime/manga toggle at the
    top (mirroring the old ANIKUTA project's extensions settings page).
  - No series/movies sub-classification for now. If added later, it classifies
    the **anime entry** (via AniList metadata), not the extension.
- **Consequences:**
  - ✅ Simple, clear categorization.
  - ✅ Future series/movies split doesn't require extension changes.
  - 📌 See `DESIGN_LANGUAGE/04-screens/extensions-settings.md`.

## ADR-017 — Bottom nav: configurable (3–7 tabs, rearrange, fixed "More")

- **Date:** Phase 0b.
- **Context:** The owner wants a configurable bottom nav. The reference's bottom
  nav is ugly and bad — needs a complete redo (floating bar style).
- **Decision:**
  - Bottom nav has **3–7 tabs** (min 3, max ~7).
  - The user can **rearrange** tabs.
  - One tab is always fixed: the **"More"** tab (name TBD). This cannot be
    removed or repositioned by the user.
  - The bar is a **floating** design (not edge-to-edge), per the owner's
    preference.
  - Available tabs include: Home, Library, Updates, History, Browse, MY, More.
    The user picks which to show and in what order.
- **Consequences:**
  - ✅ Highly customizable.
  - ✅ The floating-bar redesign fixes the reference's ugly bottom nav.
  - ⚠️ Some tabs conflict if both shown (e.g., Home + Browse both do discovery).
    Mitigated by letting the user choose.
  - 📌 See `DESIGN_LANGUAGE/04-screens/bottom-nav.md`.

## ADR-018 — Feature parity with customizable defaults + simple mode

- **Date:** Phase 0b.
- **Context:** The owner wants all original Aniyomi anime features to work, but
  with the ability to: (a) customize default settings, (b) hide settings to
  simplify, (c) offer a "simple mode" where most settings are hidden.
- **Decision:**
  - All Aniyomi anime settings are implemented (feature parity).
  - A **settings-visibility system** lets the owner hide specific settings from
    users.
  - A **simple mode** toggle hides most advanced settings, showing only
    essentials.
  - The owner can set **custom defaults** for settings (so the app's out-of-box
    experience matches the owner's preferences).
- **Consequences:**
  - ✅ Power users get all options; casual users get simplicity.
  - ⚠️ Every setting needs a "simple-mode-visible" flag. Accepted overhead.
  - 📌 See `PLANNING/01-feature-specs/settings-system.md` (to be written).

## ADR-019 — Trackers: AniList is one of several; user picks

- **Date:** Phase 0b.
- **Context:** The owner wants multiple trackers (like Aniyomi's 11). AniList is
  also a co-primary data source (ADR-010) AND a tracker.
- **Decision:**
  - Implement multiple trackers (MAL, AniList, Shikimori, Bangumi, Simkl, etc.).
  - AniList serves a **dual role**: data source (ADR-010) AND tracker. If the
    user links their AniList account, it can be used for both.
  - The user picks which tracker(s) to use per anime (or globally).
- **Consequences:**
  - ✅ Flexibility for users on different tracker platforms.
  - ⚠️ AniList's dual role must be clear in the UI (data source vs tracker).
  - 📌 Tracker architecture spec in `PLANNING/01-feature-specs/trackers.md`.

## ADR-020 — Auto-download new episodes (preference-matched)

- **Date:** Phase 0b.
- **Context:** When a new episode releases and matches the user's preferences
  (audio version, etc.), auto-download it.
- **Decision:**
  - A global setting (on/off) with **per-series override** (opt in/out per
    anime, like notifications per ADR-014).
  - Preferences include: audio version (sub/dub), quality (if configurable).
  - Triggered by the same release-detection pipeline as notifications (ADR-014).
  - Download uses the download manager (feature parity with Aniyomi).
- **Consequences:**
  - ✅ "Download and watch later" workflow.
  - ⚠️ Storage and battery sensitive. Mitigated by per-series opt-in and
    Wi-Fi-only default.
  - 📌 Spec in `PLANNING/01-feature-specs/auto-download.md`.

## ADR-021 — MY screen (customizable name)

- **Date:** Phase 0b.
- **Context:** The owner wants a personalized dashboard screen. The name "MYANI"
  was voice-to-text; the actual name is "MY" and is user-customizable in
  settings.
- **Decision:**
  - A `MyScreen` (Voyager/Compose) that shows: user's watch status, release
    schedule for tracked anime, recommendations — based on library + AniList.
  - The screen's tab name is user-customizable (default: "MY").
  - The screen is **toggleable** (can be hidden from the bottom nav per ADR-017).
- **Consequences:**
  - ✅ Personalized experience.
  - ⚠️ Requires AniList auth for full personalization (ADR-013). Without auth,
    shows general recommendations + library-based schedule.
  - 📌 Spec in `PLANNING/02-screen-specs/my-screen.md`.

## ADR-022 — Extensible architecture: add features to any screen without rework

- **Date:** Phase 0b.
- **Context:** The owner wants the freedom to add unique features to any screen
  later, and to handle new extension features, without rework.
- **Decision:**
  - Follow `RULES/ai-agent-rules.md` §4 (modularity) + §8 (future-proofing):
    loose coupling, single entry point per module, clear data contracts.
  - Each screen has a **feature-slot system**: the screen defines extension
    points where additional UI/features can be plugged in without modifying the
    screen's core.
  - Extension features are handled via a **capability declaration** system:
    extensions declare what features they support; the app renders UI for
    declared capabilities. New extension features = new capability declarations,
    not app changes.
- **Consequences:**
  - ✅ The owner can add features later without rework.
  - ✅ New extension features are handled declaratively.
  - ⚠️ Upfront design discipline (define contracts, extension points). Accepted.
  - 📌 See `ARCHITECTURE.md` (to be finalized) for the module + extension-point
    layout.

---

## ADRs from the architecture-decision session (Phase 0b finalization)

The following decisions resolve the remaining open questions. The architecture is
now fully specified and `ARCHITECTURE.md` can be finalized.

## ADR-023 — DI framework: Koin

- **Date:** Phase 0b (architecture finalization).
- **Context:** Need to choose a DI framework. Options: Koin (pure Kotlin, no
  codegen), Hilt (compile-safe, codegen), Injekt (what Aniyomi uses — niche).
- **Decision:** Use **Koin**. It's Kotlin-first, Compose-friendly, no codegen, and
  simple. We will **properly isolate the DI setup** from the reference project's
  Injekt — our Koin modules are defined fresh in `:app` + per-feature `di/`
  packages, not copied from Aniyomi.
- **Consequences:**
  - ✅ Simple, readable DI setup. No annotation processing.
  - ✅ Compose integration via `koin-androidx-compose`.
  - ⚠️ Runtime errors (no compile-time graph validation). Mitigated by Koin's
    `verify()` test that checks the graph at test time.
  - 📌 The extension system does NOT force Injekt on us — extensions implement
    the `:source-api` interface, not DI. So Koin is safe.

## ADR-024 — Persistence: SQLDelight (with status-tracking columns)

- **Date:** Phase 0b.
- **Context:** Need a database. Options: SQLDelight (matches reference, KMP),
  Room (mainstream, IDE support). The owner had **MPV/subtitle errors** in the
  previous project that were "cumbersome" — these were likely related to state
  management, not the DB, but we choose the most reliable approach.
- **Decision:** Use **SQLDelight** (matches the reference, KMP-friendly).
  The schema **must include status-tracking columns** on the anime/episode tables:
  - `release_date` — when the anime/episode was released.
  - `last_refresh` — last time the library entry was refreshed from the source.
  - `last_metadata_fetch` — last time metadata was fetched (AniList/extension).
  - `next_episode_check` — when to next check for a new episode (for ADR-014).
  These support the notification/auto-download features and let us debug stale data.
- **Consequences:**
  - ✅ Matches reference (already documented in `ANIYOMI_REFRENCE/DOCUMENTATION/`).
  - ✅ KMP-friendly (future-proofs for potential desktop).
  - ✅ Status columns enable robust notification scheduling + debugging.
  - ⚠️ Less IDE support than Room. Accepted.
  - 📌 The MPV/subtitle issues from the old project will be handled carefully in
    the player module (ADR-012) — proper lifecycle, single MPV instance, subtitle
    track management. See `DESIGN_LANGUAGE/04-screens/player.md`.

## ADR-025 — Compose-first; AndroidView for MPV only

- **Date:** Phase 0b.
- **Context:** How much Compose vs legacy Views? The watch page (ADR-012) and
  player need MPV, which is a View.
- **Decision:** **Compose-first** for everything. The only `AndroidView` interop
  is the MPV player surface. The watch page's mini-player and the fullscreen
  player share a single MPV instance (maximize = swap Compose overlay, not
  recreate the surface — per the old project's working approach).
- **Consequences:**
  - ✅ Modern, consistent UI. Matches the old project's working approach.
  - ✅ No legacy Views to maintain (except the MPV wrapper).
  - ⚠️ MPV lifecycle management is critical — the old project's subtitle issues
    were likely from improper MPV state handling. We handle this carefully.
  - 📌 See `DESIGN_LANGUAGE/04-screens/watch-page.md` and `player.md`.

## ADR-026 — Min SDK 26, Target SDK 36

- **Date:** Phase 0b.
- **Context:** Need to set Android SDK levels.
- **Decision:** **MIN SDK 26** (Android 8.0) — covers ~95% of active devices.
  **TARGET SDK 36** (Android 16) — current latest. Same as Aniyomi.
- **Consequences:**
  - ✅ Broad device support + latest Android features.
  - ⚠️ Some blur effects (`Modifier.blur`) require API 31+ — graceful fallback
    for API 26-30 (alpha-only, per the old project's approach).

## ADR-027 — Localization: Moko Resources, English-only initially

- **Date:** Phase 0b.
- **Context:** Need a localization system. Options: Moko Resources (KMP,
  type-safe), stock Android strings.
- **Decision:** Use **Moko Resources** (matches reference, KMP, type-safe).
  **English-only for starters** — no other locales shipped initially. The
  structure supports adding locales later without code changes.
- **Consequences:**
  - ✅ Type-safe string access (`MR.strings.foo` / `AYMR.strings.foo`).
  - ✅ KMP-friendly.
  - ✅ Adding locales later = just adding `strings.xml` files.
  - ⚠️ English-only means non-English users see English until we add locales.
    Accepted for Phase 1.

## ADR-028 — Backup format: gzipped protobuf (own schema)

- **Date:** Phase 0b.
- **Context:** Need a backup format.
- **Decision:** **Gzipped protobuf** with our **own schema** (not Aniyomi's
  `.tachibk` format — we define our own `.anikuta` backup). Compact, fast, proven.
  We can restore our own backups; Aniyomi backup compat is NOT a goal.
- **Consequences:**
  - ✅ Compact, fast, schema is ours to evolve.
  - ✅ Easy to change later if needed.
  - ⚠️ Users can't import Aniyomi backups (we'd need a converter if ever wanted).
  - 📌 No encryption initially (matches Aniyomi). Can add later.

## ADR-029 — Extension compatibility: keep Aniyomi extension format exactly

- **Date:** Phase 0b.
- **Context:** Should we keep Aniyomi extension compat or design our own model?
- **Decision:** **Keep Aniyomi extension compatibility exactly as-is.** Existing
  Aniyomi anime extensions work out of the box. We can **add our own capabilities**
  (per ADR-022) via the source-api without breaking compat — extensions declare
  what they support; the app renders UI for declared capabilities.
- **Consequences:**
  - ✅ Instant extension ecosystem (existing Aniyomi anime extensions work).
  - ✅ Custom extensions can add features via capability declarations.
  - ⚠️ Locked into the source-api shape (acceptable — it's well-designed).
  - 📌 See `ANIYOMI_REFRENCE/DOCUMENTATION/02-modules/source-api.md` for the contract.

## ADR-030 — AniList client: raw HTTP + kotlinx-serialization (swappable)

- **Date:** Phase 0b.
- **Context:** How to talk to AniList's GraphQL API? Options: raw HTTP +
  kotlinx-serialization (lighter), Apollo GraphQL (type-safe, codegen).
- **Decision:** **Raw HTTP + kotlinx-serialization** for starters. Kept in a
  **separate `:core:anilist` module** so it's swappable — if we later want Apollo
  or a different approach, we change only that module. The module exposes an
  `AniListRepository` interface; the rest of the app doesn't know the implementation.
- **Consequences:**
  - ✅ Lighter weight (no Apollo codegen dependency).
  - ✅ Uses existing OkHttp + serialization stack.
  - ✅ Swappable — isolated in `:core:anilist`.
  - ⚠️ Less type-safe than Apollo (manual schema mapping). Mitigated by careful
    data classes + tests.

---

## Open decisions (FINAL — all resolved)

All previously-open decisions are now resolved by ADRs 009–030:

- [x] ~DI framework~ → Koin (ADR-023).
- [x] ~Persistence~ → SQLDelight with status columns (ADR-024).
- [x] ~Compose-only vs mixed Views~ → Compose-first, AndroidView for MPV (ADR-025).
- [x] ~Min/target SDK~ → 26 / 36 (ADR-026).
- [x] ~Localization tooling~ → Moko Resources, English-only initially (ADR-027).
- [x] ~Backup format~ → gzipped protobuf, own schema (ADR-028).
- [x] ~Final module list + dependency graph~ → see `ARCHITECTURE.md` + `PLANNING/04`.
- [x] ~Player embedding approach~ → single MPV instance, Compose overlay swap (ADR-025).
- [x] ~Episode metadata source~ → old project's 4-source method, pluggable module (ADR-022 + `PLANNING/01-feature-specs/episode-metadata-module.md`).
- [x] ~Design language specifics~ → see `DESIGN_LANGUAGE/`.
- [x] ~Extension compatibility~ → keep Aniyomi format (ADR-029).
- [x] ~AniList client~ → raw HTTP + serialization, swappable (ADR-030).

**The architecture is now fully specified.** `ARCHITECTURE.md` can be finalized.

## How to add a new ADR

1. Use the next free `ADR-NNN` number.
2. Copy the format above (Date / Context / Decision / Consequences).
3. Commit with message `docs(adr): ADR-NNN <short title>`.
4. If it supersedes an earlier ADR, update the earlier one with a strikethrough
   note pointing to the new one.
