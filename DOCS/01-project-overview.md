# 01 — Project Overview

## What is ANIKUTA?

ANIKUTA is an Android application for reading manga and watching anime. It is a
**reimagined, restructured** version of [Aniyomi](https://github.com/aniyomiorg/aniyomi):
we reuse Aniyomi's core concepts and functionality, but rebuild the app from scratch
with our own architecture, design, and feature priorities.

> **ANIKUTA is not a fork of Aniyomi.** The Aniyomi source is kept under
> `ANIYOMI_REFRENCE/` as a read-only study reference. All new code is written
> under `ANIKUTA_PROJECT/ANIKUTA/`.

## Goals

- A clean, modular, maintainable Android codebase that an AI agent (or human) can
  navigate and extend confidently.
- All core Aniyomi functionality, eventually: library management, source/extension
  system, trackers, manga reader, anime player, downloads, backups.
- A redesigned UX tailored to our preferences.
- Robust CI: every build & release produced by GitHub Actions — never locally.
- High-quality documentation that survives agent/handoff boundaries.

## Non-goals (at least initially)

- Feature parity with Aniyomi on day one. Some features will be **hidden or
  deferred** and re-enabled later. (Which ones? To be decided in the design phase —
  see `04-design-decisions.md`.)
- Maintaining compatibility with the Aniyomi extension ecosystem byte-for-byte.
  We will assess source/extension compatibility as an explicit design decision.
- Backwards compatibility with Aniyomi backups (assessed in design phase).

## Relationship to Aniyomi

| Aspect | Approach |
|---|---|
| Source code | Read-only snapshot in `ANIYOMI_REFRENCE/ANIYOMI/`. Study, don't edit. |
| Architecture | Redesigned. Aniyomi's module split is *inspiration*, not a template to copy verbatim. |
| Features | Ported/adapted one at a time, each documented as a decision. |
| License | Apache 2.0 (inherited; see `04-design-decisions.md`). |

## Stakeholders

- **Owner:** `testplay-byte` (GitHub).
- **Contributors:** AI agents (primary, during this phase) + the owner.

## Out of scope for this repository

- Anything unrelated to building ANIKUTA. The root directory is kept clean so it
  can also hold AI-agent session rules and handoff notes (see `../RULES/`).
