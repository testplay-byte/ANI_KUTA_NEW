# Architecture

High-level architecture documentation for the **ANI-KUTA** Android app.

ANI-KUTA is an Android anime streaming app built **on the foundations of
[aniyomi](https://github.com/aniyomiorg/aniyomi)** — it reuses aniyomi's
extension system, SQLDelight DB pattern, Injekt DI, and MPV player — but uses
its own techniques in most places (extension trust, source preferences, the
AniList + Supabase data layer, the entire Compose UI). We keep aniyomi as a
read-only reference in `REFERENCE/` and build our own working code
(`app/`, `core/`, `data/`, `domain/`, `source-api/`) on top of the lessons and
reusable parts we identify there.

> **For the full, verified technical map** (modules, packages, build, CI, data
> flows), read `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`. This file covers the
> *principles*; that file covers the *reality*.

---

## Core principles

### 1. UI / Logic separation (very important)

Backend logic, processing, and core workings are **fully decoupled** from the
UI/UX layer. The same logic must be reusable anywhere, and the UI must be
swappable independently. The long-term goal is a highly customizable UI
(multiple design languages, themes) without touching the logic layer.

**How this is enforced:**
- The 5-module Gradle split makes the separation a **compile-time guarantee**,
  not just a convention. The UI (`:app`) cannot directly import SQLDelight
  internals from `:data` — the module boundary prevents it.
- **Within `:app`**, the pattern is: `ui/<feature>/` holds Compose Screens +
  ViewModels; the ViewModel calls domain interactors or repositories; the
  Screen calls the ViewModel. The Screen never fetches data directly.
- **Exception (known debt):** the player subsystem currently inverts this —
  `PlayerActivity` holds all the logic and `PlayerViewModel` is just a state
  bag. This is flagged as a P0 modularization issue
  (`DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P0.4) and will be refactored.

When reviewing any new code, ask: *"Could I reuse this logic with a completely
different UI?"* If the answer is no, refactor.

### 2. Feature-based folders

Each feature gets its own folder under the relevant top-level module. A
"feature" is a coherent slice of user-facing functionality (e.g. library,
player, tracker, downloads). Cross-feature dependencies go through clearly
defined interfaces, not direct imports of internal classes.

**Current state:** `:app` is a single module with feature folders
(`ui/library/`, `ui/search/`, `ui/detail/`, `player/`, `download/`, etc.).
A future step may extract these into `:feature:*` Gradle submodules for
stronger boundaries (see `MODULARIZATION-ASSESSMENT.md` P1.3).

### 3. Reference-folder strategy

- **`REFERENCE/`** — a pristine, READ-ONLY copy of the aniyomi upstream source
  (commit `2f5cf77`, 2025-11-05, 1,988 files). **Never edited, never built
  from.** When we refresh upstream, the whole copy is replaced.
- **`REFERENCE-STAGING/`** — landing zone for a fresh copy of aniyomi when
  reviewing upstream updates. Currently empty (`.gitkeep`). We diff
  `REFERENCE-STAGING/` against `REFERENCE/`, review the changes, and promote
  the parts we want into our working code (NOT into `REFERENCE/`). After the
  review, `REFERENCE/` is replaced wholesale with the new version.

This keeps a clear "upstream source vs. our work" separation.

### 4. Modular-complexity rule

No single file should be too large. Each file has one clear responsibility
("this file does X"). Long or complex tasks are split into multiple
functions/files. Everything is documented.

**Current exceptions (tracked debt):** `PlayerActivity.kt` (2,430 LoC),
`DetailScreen.kt` (1,695 LoC), `SearchScreen.kt` (1,363 LoC),
`DetailViewModel.kt` (1,313 LoC), `LibraryScreen.kt` (1,199 LoC),
`PlayerScreen.kt` (1,048 LoC). See `MODULARIZATION-ASSESSMENT.md`.

### 5. GitHub backup strategy

`MEMORY/` and `DOCS/` are pushed to GitHub regularly so context survives
sandbox resets. If the sandbox resets, the user re-links the GitHub repo and
the agent reads `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` → `DOCS/CURRENT-STATE.md`
→ `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` to restore full context and resume work.

`MEMORY/CREDENTIALS/` (including the GitHub PAT) is gitignored and never
committed.

---

## Module dependency graph

```
app → core, data, domain, source-api
data → core, domain, source-api
domain → core, source-api
source-api → core
core → (no internal deps)
```

A visual diagram will be added here. For now, see `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`
§4 for the full per-module breakdown (file counts, LoC, responsibility, key classes).

---

## Where to go next

- `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` — **read this first** if you're new.
- `DOCS/CURRENT-STATE.md` — where the project is right now.
- `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` — the comprehensive verified technical map.
- `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` — what's structurally fragile and why.
- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map.
- `MEMORY/CORE-RULES.md` — the rules this architecture is built on.
