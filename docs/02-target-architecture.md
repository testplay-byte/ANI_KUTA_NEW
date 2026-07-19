# 02 — Target Architecture

> **Status: DRAFT — to be finalized in the design phase (Phase 0b / early Phase 1).**
>
> This document captures the *thinking* and the open questions. It is deliberately
> not a finished blueprint. Finalizing it is one of the very next things we do.

## Design principles (high confidence)

1. **Multi-module Gradle project.** Each module has one clear responsibility.
2. **Layered architecture.** `domain` (pure Kotlin, no Android deps) ← `data`
   (persistence, network) ← `presentation` (UI). Dependencies point inward.
3. **No business logic in the UI layer.** UI calls use cases / repositories.
4. **Kotlin-first, Coroutines + Flow** for async.
5. **Modular enough that an AI agent can reason about one module at a time.**
6. **Each module ships a README** describing its purpose, public API, dependencies.
7. **Builds via CI only** (see `06-build-and-ci.md`).

## Starting point: what Aniyomi does (for reference)

Aniyomi's current module split (see `03-reference-module-map.md` for detail):

```
app                  ← application, DI wiring, activities, navigation
core:common          ← shared utilities
core:archive         ← CBZ/CBR archive handling
core-metadata        ← manga/anime metadata
data                 ← database (SQLDelight/Room), repositories
domain               ← models, use cases (pure Kotlin)
i18n / i18n-aniyomi  ← strings / localization (Moko Resources)
macrobenchmark       ← Baseline profiles & benchmarks
presentation-core    ← shared Compose utilities
presentation-widget  ← home-screen widgets
source-api           ← the extension/source contract
source-local         ← reading local files as a "source"
```

This is a reasonable starting mental model. **We are not obliged to copy it
verbatim.** We will decide our own module boundaries in the design phase.

## Open architecture questions (to resolve in the design phase)

These are explicitly undecided. Do NOT silently assume an answer — record the
decision in `04-design-decisions.md` once made.

1. **Module granularity.** Do we keep Aniyomi's split, coarsen it, or go finer?
2. **DI framework.** Aniyomi uses `tachiyomi.injekt` / Koin-like. Do we keep, or
   switch to a mainstream DI (Hilt, Koin, metro)?
3. **Persistence.** Aniyomi uses SQLDelight + Room in places. Pick one.
4. **Source/extension system.** Do we keep Aniyomi's extension model (which brings
   in the wider extension ecosystem) or design a narrower first-party source model?
   This is a *big* decision affecting the whole app shape.
5. **Player.** Aniyomi uses a media3/ExoPlayer-based custom player. Keep vs. rework.
6. **Reader.** Aniyomi has a paged + continuous reader for manga. Keep vs. rework.
7. **Compose vs. legacy Views.** Aniyomi is mid-migration to Compose. Do we go
   Compose-only from the start?
8. **Min SDK / target SDK.** Decide based on the Android version policy we want.
9. **Kotlin Multiplatform?** Probably no for v1, but worth a one-line decision.
10. **Localization strategy.** Moko Resources (Aniyomi's choice) vs. plain strings.

## What this document will contain once finalized

- A module dependency graph (ASCII or image).
- A per-module responsibility table.
- Data-flow diagrams for the key user journeys (browse, read, watch, download).
- The chosen answers to every open question above, each linking to an entry in
  `04-design-decisions.md`.

Until then, treat anything in `ANIKUTA_PROJECT/ANIKUTA/` beyond the skeleton as
not-yet-decided.
