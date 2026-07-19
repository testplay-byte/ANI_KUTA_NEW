# ANIKUTA

> The actual app codebase for the ANIKUTA project.
> A reimagined, restructured version of [Aniyomi](https://github.com/aniyomiorg/aniyomi).

---

## ⚠️ Status: SKELETON (Phase 0)

This folder is intentionally **empty of code** right now. It will be scaffolded in
**Phase 1** of the roadmap (see `../../DOCS/05-roadmap.md`), after the target
architecture and feature scope are finalized in the design phase.

Placing a real Gradle project here prematurely — before architecture decisions are
locked in `../../DOCS/02-target-architecture.md` — would create rework. We resist
that urge deliberately.

---

## What ANIKUTA will be

- A manga & anime reader for Android.
- Built on the **concepts** of Aniyomi (library, sources, trackers, player, reader),
  but with a **complete redesign and restructure**.
- Some Aniyomi features will be **hidden or deferred** initially; all core
  functionality will eventually be ported/adapted.
- Modular Gradle architecture (target layout TBD in `../../DOCS/02-target-architecture.md`).

---

## Where the ideas come from

The original Aniyomi source is available read-only at
`../../ANIYOMI_REFRENCE/ANIYOMI/`. Use it to study how a feature was implemented,
then re-implement it here in the ANIKUTA style. **Never edit the reference.**

A map of Aniyomi's modules and what they do is at
`../../DOCS/03-reference-module-map.md` — start there when porting.

---

## When you (the next agent) are ready to scaffold

1. Confirm `../../DOCS/02-target-architecture.md` has a finalized module layout.
2. Confirm `../../DOCS/05-roadmap.md` says we are in Phase 1.
3. Scaffold the Gradle project here following the architecture doc.
4. Add a `README.md` per module as you create it.
5. Wire up the CI workflow at `../../.github/workflows/` to compile it (no local builds).
6. Update `../../DOCS/04-design-decisions.md` with any decisions made during scaffolding.
7. Leave a session handoff note in `../../RULES/sessions/`.
