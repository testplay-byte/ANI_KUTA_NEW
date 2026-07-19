# Documentation (DOCS/)

This folder holds all project documentation for **ANI-KUTA**. It is pushed to
GitHub alongside `MEMORY/` as a backup so context survives sandbox resets.

## What lives here

| Path | Description |
|------|-------------|
| `README.md` | This file — overview of the DOCS/ folder. |
| `NAVIGATION-GUIDE.md` | Top-level map of every folder in the anikuta repo. Read this first. |
| `ARCHITECTURE/README.md` | Architecture principles: UI/logic separation, feature folders, reference-folder strategy, modular-complexity rule. |
| `REFERENCE-DOCS/` | **All documentation for the `REFERENCE/` (aniyomi) folder.** Start at `REFERENCE-DOCS/README.md`, then `NAVIGATION-GUIDE.md` → `MODULES.md` / `APP-STRUCTURE.md` / `ARCHITECTURE.md`. |

## Rules

- **Every code change must be documented here.** Update docs in the same step as
  the code change, not later.
- Keep each file small and focused — one topic per file (modular-complexity rule).
- New per-feature docs are added under DOCS/ as the project grows.
- When upstream (aniyomi) changes are reviewed and adopted, record the decision
  in `MEMORY/DECISIONS/` and link from the relevant DOCS/ file.

## Where to go next

- New to the repo? Start at `NAVIGATION-GUIDE.md`.
- New session? Read `MEMORY/SESSION-START-GUIDE.md` (if present) and
  `MEMORY/CORE-RULES.md` first, then come back here.
