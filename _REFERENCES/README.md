# `_REFERENCES/` — Off-Limits Backup Snapshots

> ⛔ **STOP. Read this first.**
>
> The two folders inside here are **frozen backup snapshots**. They are **NOT**
> part of the live ANIKUTA project. **Do not read, scan, search, or build inside
> them unless you have a specific, explicit reason to cross-reference something.**

---

## What's in here

| Folder | What it is | Origin |
|---|---|---|
| `ANIYOMI_REFRENCE/` | Source-only snapshot of the original [Aniyomi](https://github.com/aniyomiorg/aniyomi) app (branch `main`), plus a 68-doc analysis of it under `ANIYOMI_REFRENCE/DOCUMENTATION/`. | ADR-002, ADR-005 |
| `OLD_ANIKUTA/` | Source-only snapshot of our previous ANIKUTA attempt (`testplay-byte/anikuta`), plus screen analysis under `OLD_ANIKUTA/ANALYSIS/`. Kept for prior research and lessons-learned. | ADR-007 |

## The rules (NON-NEGOTIABLE)

1. 🔒 **READ-ONLY.** Never edit, rename, reorganize, or delete anything in here.
2. 🚫 **Do not build here.** No `./gradlew`, no Android Studio, nothing that
   produces build output. These are source-only snapshots with no `.git` history.
3. 🚫 **Do not scan or search these folders during normal work.** AI agents
   doing feature work, bug fixes, or documentation updates should **not** grep
   or glob into `_REFERENCES/`. The main project lives at `ANIKUTA_PROJECT/ANIKUTA/`.
4. 📋 **Copying ideas is allowed** — but only when you have a specific reason.
   To port a concept: read the specific file you need here, then implement it
   fresh under `ANIKUTA_PROJECT/ANIKUTA/`. Do **not** edit the reference in place.
5. 📖 **Prefer the documentation over the source.** If you need to understand
   how Aniyomi does something, read `ANIYOMI_REFRENCE/DOCUMENTATION/` (the
   68-doc analysis) rather than reverse-engineering the source.

## Why these exist

ANIKUTA is **NOT a fork of Aniyomi**. These snapshots exist purely as study
references for the design and architecture phases. The Aniyomi source informs
our extension compatibility (ADR-029) and some ported concepts. The old ANIKUTA
project informs our design language (ADR-015) and helps us avoid its
structural pitfalls (ADR-007).

## When you SHOULD come in here

- You need to cross-reference a specific Aniyomi subsystem behavior that isn't
  covered in `ANIYOMI_REFRENCE/DOCUMENTATION/`.
- You need to look up a specific line-number citation from the old project's
  docs (they cite `OLD_ANIKUTA/ANIKUTA_OLD/REFERENCE/` line numbers per ADR-007).
- The owner explicitly asks you to study a specific reference screen for a
  design task.

**If none of the above apply, stay out.** The main project at
`ANIKUTA_PROJECT/ANIKUTA/` is where 100% of active work happens.

---

*See `DOCS/04-design-decisions.md` (ADRs 002, 005, 007) for the full reasoning
behind these reference snapshots.*
