# 05 — Roadmap

> Phased plan. Each phase has a clear definition of done. We do not start a phase
> until the previous one's exit criteria are met.

## Phase 0 — Repository foundation ✅ (this commit)

**Goal:** a clean, navigable, documented monorepo with the Aniyomi reference in
place and the rules of engagement defined.

**Done when:**
- ✅ Repo structured: `ANIYOMI_REFRENCE/`, `ANIKUTA_PROJECT/`, `docs/`, `rules/`, `.github/`.
- ✅ Aniyomi reference source committed under `ANIYOMI_REFRENCE/ANIYOMI/`.
- ✅ Root `README.md` + `AGENTS.md` written.
- ✅ `docs/01..06` written.
- ✅ `rules/` written.
- ✅ CI placeholder workflow exists and passes a repo-sanity job.
- ✅ Build policy (CI-only) documented and enforced by convention.

## Phase 0b — Design & decisions (NEXT)

**Goal:** lock in the architecture and feature scope *before* writing app code.

**Tasks:**
- [ ] Walk through every "open decision" in `04-design-decisions.md` and resolve it.
- [ ] Finalize `02-target-architecture.md` (module list, dependency graph, data flow).
- [ ] Decide the feature scope for v1: which Aniyomi features are in, hidden, deferred.
- [ ] Decide the source/extension model (biggest open question).
- [ ] Produce a module-level "port plan": for each v1 feature, which reference
      module(s) inform it and where it will live in ANIKUTA.
- [ ] Record all outcomes as new ADRs in `04-design-decisions.md`.

**Done when:** `02-target-architecture.md` is finalized and every open decision
in `04-design-decisions.md` is checked off.

## Phase 1 — Scaffold the ANIKUTA Gradle project

**Goal:** an empty-but-compiling multi-module Gradle project under
`ANIKUTA_PROJECT/ANIKUTA/`, built by CI.

**Tasks:**
- [ ] Scaffold root `settings.gradle.kts`, `build.gradle.kts`, version catalogs.
- [ ] Create each module from the finalized architecture, each with a `README.md`.
- [ ] Wire the CI build job (commented out in `ci-placeholder.yml`) to compile a
      debug build. No releases yet.
- [ ] Add a trivial "hello world" app shell so CI has something to compile.

**Done when:** CI produces a green debug build of the empty shell.

## Phase 2 — Core domain & data

- [ ] Domain models (Manga, Anime, Chapter, Episode, …).
- [ ] Persistence layer + schema.
- [ ] Repository interfaces and a first in-memory/local implementation.

## Phase 3 — First source & browse

- [ ] Implement the chosen source model.
- [ ] One working source (likely `source-local` first, per Aniyomi's pattern).
- [ ] Library browse + add-to-library flow.

## Phase 4 — Reader & Player

- [ ] Manga reader (paged + continuous).
- [ ] Anime player (media3-based, per the architecture decision).

## Phase 5 — Downloads, backups, trackers

- [ ] Download manager.
- [ ] Backup/restore.
- [ ] Tracker integration (MAL/AniList/etc.) — scope per design decisions.

## Phase 6 — Polish & release

- [ ] UX redesign pass.
- [ ] Release signing via CI secrets.
- [ ] First tagged release via GitHub Actions.

---

> Phases 2–6 are intentionally coarse. They will be detailed as we approach them,
> and each will get its own breakdown in this file (or a per-phase doc under
> `docs/`).
