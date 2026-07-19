# ANIKUTA_PROJECT/

This directory holds **our actual project** — the ANIKUTA app — as opposed to the
read-only Aniyomi reference in `../ANIYOMI_REFRENCE/`.

## What's inside

```
ANIKUTA_PROJECT/
└── ANIKUTA/            ← the live app codebase (see its own README)
```

## Principles

- **All new code lives here.** Nothing under `ANIYOMI_REFRENCE/` is ever edited.
- **Modular by design.** As the app grows, it will be split into well-defined
  Gradle modules (see `../DOCS/02-target-architecture.md`). Each module must have
  a clear single responsibility and a short README.
- **Documented as we go.** Every module ships with a `README.md` describing its
  purpose, public API surface, and dependencies. No module is "undocumented".
- **Built only via CI.** See `../DOCS/06-build-and-ci.md`.

## Current state

`ANIKUTA/` is currently a **skeleton** (placeholder README + project intent).
The real codebase will be scaffolded in the next phase, after architecture
decisions are finalized in `../DOCS/`. See `../DOCS/05-roadmap.md`.
