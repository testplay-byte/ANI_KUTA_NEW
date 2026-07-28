# PLAN/ — Project Planning

> This folder holds the **planning documentation** for ANI-KUTA — what we're
> building, how each screen works, how we integrate aniyomi, the customization
> system, and the phased roadmap. Created BEFORE development starts.
>
> These docs evolve as we discuss and refine. Once development begins, the
> "how we built it" docs go in `DOCS/APP/`.

---

## Files

| File | What it covers |
|------|----------------|
| `README.md` | This file — index. |
| `PRODUCT-VISION.md` | The high-level product vision: what ANI-KUTA is, the core experience, the principles. |
| `HOME-PAGE.md` | Detailed spec of the home page (sections, data, layout, interactions). |
| `DETAIL-PAGE.md` | Detailed spec of the anime detail page. |
| `OTHER-PAGES.md` | List + brief spec of all other pages (player, search, library, history, settings, extensions). |
| `CUSTOMIZATION.md` | The 4 starting designs, the theming system, and the limited custom-theming options. |
| `ANIYOMI-INTEGRATION.md` | How aniyomi works behind the scenes: what we reuse, how the extension system feeds our UI. |
| `UPSTREAM-TRACKING.md` | The monthly upstream comparison process: what we track, how we diff, how we adopt. |
| `ROADMAP.md` | The phased build plan: what we build in each phase, dependencies, milestones. |

## How to use this folder

1. **Read `PRODUCT-VISION.md` first** — it's the north star.
2. **Drill into page specs** (`HOME-PAGE.md`, `DETAIL-PAGE.md`, `OTHER-PAGES.md`) for the UI plan.
3. **Read `CUSTOMIZATION.md`** for the design/theming system.
4. **Read `ANIYOMI-INTEGRATION.md`** for the backend plan.
5. **Read `ROADMAP.md`** for the build sequence.
6. **Read `UPSTREAM-TRACKING.md`** for the long-term maintenance process.

## Status

**Draft (Session 8).** Initial plan based on the user's vision. Will be refined
as we discuss and answer open questions. Not yet finalized — no code written.

## Related

- `DOCS/APP/` — our app's build documentation (filled as we build).
- `DOCS/REFERENCE-DOCS/` — aniyomi reference docs (what we're building on).
- `MEMORY/DECISIONS/` — the 4 build decisions + all other decisions.
