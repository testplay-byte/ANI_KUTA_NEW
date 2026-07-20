# Session handoff — Bug fixes + prototype analysis + design language update

**Agent:** Z.ai Code (session 9)
**Task ID:** phase-2-bugfixes + prototype-analysis
**Session goal:** Fix 3 critical bugs, download + analyze the prototype reference,
update the design language with findings, add #B1F256 theme color, plan Phase 3.

## What I did

### Part 1: Fixed 3 critical bugs

1. **searchByName** — added `COLLATE NOCASE` for case-insensitive search.
2. **animetrack upsert** — added `UNIQUE(anime_id, tracker_id)` constraint +
   `upsert` query (INSERT OR REPLACE, SQLite 3.18 compatible).
3. **Hardcoded dispatchers** — created `DispatcherProvider` interface in
   `:core:common/di/`, injected it into all 3 repository implementations,
   wired it in Koin `RepositoryModule`. Dispatchers are now testable.

### Part 2: Downloaded + analyzed the prototype

- Downloaded `testplay-byte/ANDROID-PROTOTYPE/Android_app/Anime_App` (25 Kotlin files).
- Saved to `PROTOTYPE_REFERENCE/Anime_App/` (read-only, like other references).
- Wrote `PROTOTYPE_REFERENCE/ANALYSIS.md` — detailed analysis of:
  - Bottom nav (floating pill, active-expands, from `BottomNavBar.kt`).
  - CollapsingHeader (shrink-on-scroll title).
  - Schedule screen (day selector, airing rows, states).
  - Search screen (custom BasicTextField, collapsing search bar, filter sheet).
  - Settings screen (sectioned groups, custom toggles, segmented selectors).
  - Theme structure (surface tiers, M3 roles, dark default).

### Part 3: Updated design language

- **`DESIGN_LANGUAGE/04-screens/bottom-nav.md`** — completely rewritten with the
  verified implementation from the prototype (floating overlay, 28dp pill,
  42/58dp heights, active-expands/inactive-shrinks, AnimatedVisibility label,
  110dp content bottom padding, Voyager adaptation).
- **`DESIGN_LANGUAGE/03-themes/anikuta-palette.md`** — NEW. The full #B1F256-based
  palette: dark/light/AMOLED, surface tiers 1-5, text tiers, M3 color roles,
  functional colors, derivation notes, implementation example.

### Part 4: Planned Phase 3

- `PLANNING/03-phase-3-execution-plan.md` — detailed 5-step plan for building
  `:core:designsystem`: theme setup, 13 core components, edge-to-edge, wiring,
  CI verification.

## What is DONE

- 3 bugs fixed (searchByName, animetrack upsert, dispatchers).
- Prototype downloaded + saved to repo.
- Prototype analyzed (comprehensive ANALYSIS.md).
- Bottom-nav design spec updated with verified implementation.
- #B1F256 palette documented.
- Phase 3 plan written.

## What is NOT done

- Phase 3 implementation (design system & theme) — pending owner go-ahead.
- The bottom nav component is NOT yet implemented (Phase 3 Step 2).

## What the NEXT agent should do

1. Check CI passes with the bug fixes.
2. When the owner says "go for Phase 3", follow `PLANNING/03-phase-3-execution-plan.md`.
3. Phase 3 builds `:core:designsystem` with the #B1F256 theme + all components.

## Pointers

- `PROTOTYPE_REFERENCE/ANALYSIS.md` — the prototype analysis.
- `DESIGN_LANGUAGE/04-screens/bottom-nav.md` — the verified bottom nav spec.
- `DESIGN_LANGUAGE/03-themes/anikuta-palette.md` — the #B1F256 palette.
- `PLANNING/03-phase-3-execution-plan.md` — Phase 3 plan.
- `core/common/src/main/java/.../di/DispatcherProvider.kt` — the injected dispatchers.

## Dev environment notes

- All work done by me (no subagents) per owner's preference.
- Prototype saved read-only at `PROTOTYPE_REFERENCE/` (same rules as other references).
- No builds performed (CI-only, ADR-003).
