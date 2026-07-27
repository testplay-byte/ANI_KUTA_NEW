# ANIKUTA_PROJECT/

This directory holds **our actual project** — the ANIKUTA app — as opposed to the
read-only backup snapshots in `../_REFERENCES/`.

## What's inside

```
ANIKUTA_PROJECT/
└── ANIKUTA/            ← the live app codebase (41 Gradle modules — see its own README)
```

## Principles

- **All new code lives here.** Nothing under `../_REFERENCES/` is ever edited (see `../_REFERENCES/README.md`).
- **Modular by design.** The app is split into well-defined Gradle modules
  (`:core:*`, `:feature:*`, `:data:*`, `:app`). Each module has a clear single
  responsibility and a short README. See `../ARCHITECTURE.md` §3 for the full list.
- **Documented as we go.** Every module ships with a `README.md` describing its
  purpose, public API surface, and dependencies. No module is "undocumented".
- **Built only via CI.** See `../DOCS/06-build-and-ci.md` (ADR-003).

## Current state

**Feature-complete (Phase 8+).** `ANIKUTA/` has 41 Gradle modules, builds debug
APKs via CI, and has all major features working: browse, search, anime details,
watch (MPV), library, history, updates, profile, trackers, backup/restore,
downloads/offline playback, and episode settings. See `../DOCS/05-roadmap.md`
for the full status and what's planned next (Phase 9).
