# PLANNING/

Detailed planning documents for ANIKUTA. These go deeper than the high-level
`DOCS/` and translate the vision (ADRs in `DOCS/04`) into concrete specs that
`ARCHITECTURE.md` and the implementation will follow.

> **Status:** Being populated. Many files are placeholders until the
> architecture-planning phase completes.

## Folder structure

| Folder | Contents |
|---|---|
| [`01-feature-specs/`](01-feature-specs/) | Per-feature specs: dual-metadata, notifications, auto-download, trackers, settings-system, etc. |
| [`02-screen-specs/`](02-screen-specs/) | Per-screen specs: Home, Library, MY, Details, Watch page, Player, Extensions settings, etc. |
| [`03-data-model/`](03-data-model/) | Database schema design, domain models, AniList data mapping. |
| [`04-module-architecture/`](04-module-architecture/) | Gradle module plan, dependency graph, DI wiring. Feeds into `ARCHITECTURE.md`. |

## Relationship to other folders

- `DOCS/04-design-decisions.md` — the ADRs (the "what" and "why").
- `PLANNING/` — the specs (the "how" — translates ADRs into implementable detail).
- `ARCHITECTURE.md` — the single source of truth (synthesized from `PLANNING/04`).
- `DESIGN_LANGUAGE/` — the UI/UX spec (per-screen design, components, themes).
- `RULES/` — the operating rules (how agents work, not what the app does).
