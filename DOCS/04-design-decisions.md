# 04 — Design Decisions (ADR Log)

> Every non-trivial decision is recorded here so it is never re-litigated
> silently. Format: **ADR-NNN** — short title, context, decision, consequences.
> If a decision is superseded, mark it `~~struck~~` with a "Superseded by ADR-XXX
> on YYYY-MM-DD" note. **Do not delete entries.**

---

## ADR-001 — Monorepo with separated reference and project trees

- **Date:** Phase 0 (initial setup).
- **Context:** We are reimagining Aniyomi. We need (a) the original source for
  study, (b) our own code, (c) docs/rules for AI agents — all co-existing without
  cross-contamination.
- **Decision:** Use a single repository `ANI_KUTA_NEW` with two top-level trees:
  `ANIYOMI_REFRENCE/` (frozen reference) and `ANIKUTA_PROJECT/` (live project).
  Keep the root clean for navigation + `DOCS/` + `RULES/`.
- **Consequences:**
  - ✅ A new agent reads the root, immediately understands layout.
  - ✅ Reference can never be confused with our code.
  - ⚠️ Repo is larger than a pure project repo (the reference is committed).
    Acceptable: ~24 MB source-only, no history bloat.

## ADR-002 — Reference is a source-only tarball snapshot (no `.git`)

- **Date:** Phase 0.
- **Context:** The user stated the reference is "not a complete backup", just a
  local study copy. Including Aniyomi's `.git` would create a nested repository
  (messy, ambiguous, easy to accidentally push back to upstream).
- **Decision:** Snapshot Aniyomi via GitHub's `archive/refs/heads/main.tar.gz`
  tarball. Source files only. No `.git`, no history.
- **Consequences:**
  - ✅ Clean: no nested repo, no submodule complexity.
  - ✅ Small. No history bloat.
  - ⚠️ Cannot `git blame`/`log` the reference. Acceptable — it's a study copy,
    not a working clone. The upstream URL is recorded in
    `ANIYOMI_REFRENCE/README.md` for anyone who needs history.

## ADR-003 — Builds happen via GitHub Actions ONLY

- **Date:** Phase 0.
- **Context:** The user explicitly requires that APKs are never built locally;
  GitHub Actions is the build system.
- **Decision:** No contributor is expected to produce an APK locally. All
  compile/test/release flows live in `.github/workflows/`. Local work is limited
  to editing, type-checking where it doesn't produce an APK, and committing.
- **Consequences:**
  - ✅ Reproducible builds, clean environments.
  - ✅ No contributor needs an Android SDK/toolchain installed locally.
  - ⚠️ Slower feedback loop than local builds — mitigated by keeping CI fast and
    by doing static checks before pushing.
  - 📌 See `06-build-and-ci.md` for the workflow design.

## ADR-004 — License: Apache 2.0 (inherited from Aniyomi)

- **Date:** Phase 0 (default; revisit allowed).
- **Context:** Aniyomi is Apache-2.0 licensed. ANIKUTA reuses Aniyomi's concepts
  and (adapted) code. Apache-2.0 requires preserving attribution and license
  notices in derivatives.
- **Decision:** Inherit Apache-2.0 at the repo root (`LICENSE`). Preserve
  Aniyomi's NOTICE/attribution obligations when porting code.
- **Consequences:**
  - ✅ Legally safe default for an Apache-2.0 derivative.
  - ⚠️ If the owner later wants a different license, this must be revisited and
    any already-ported Aniyomi code re-evaluated for compatibility.
  - 📌 Marked as revisitable: change here if the owner decides otherwise.

## ADR-005 — Reference tree is strictly read-only

- **Date:** Phase 0.
- **Context:** Editing the reference would blur "what Aniyomi does" vs "what we
  do", defeating the purpose of a study snapshot.
- **Decision:** Nothing under `ANIYOMI_REFRENCE/` is ever modified, except to
  replace the whole snapshot with a newer upstream tarball (procedure in
  `ANIYOMI_REFRENCE/README.md`). To port an idea, copy code into
  `ANIKUTA_PROJECT/ANIKUTA/` and adapt there.
- **Consequences:**
  - ✅ The reference always reflects real upstream behavior.
  - ✅ `git diff ANIYOMI_REFRENCE/` is effectively always empty between refreshes.

## ADR-006 — AI-agent-first documentation strategy

- **Date:** Phase 0.
- **Context:** Much of the ongoing work will be done by AI agents that lack
  prior context. Continuity depends on documentation.
- **Decision:**
  - Root `README.md` + `AGENTS.md` are the entry points.
  - `DOCS/04-design-decisions.md` (this file) is the decision log.
  - `DOCS/05-roadmap.md` tracks phase/progress.
  - `RULES/` holds operating conventions + session handoff notes.
  - Every Gradle module in `ANIKUTA_PROJECT/ANIKUTA/` ships a `README.md`.
- **Consequences:**
  - ✅ Any agent can resume work by reading root + `RULES/` + latest session note.
  - ⚠️ Documentation overhead per change — accepted as the cost of continuity.

## ADR-007 — Second read-only reference: the OLD ANIKUTA project

- **Date:** Phase 0 (second setup task).
- **Context:** A previous attempt at ANIKUTA exists at
  `https://github.com/testplay-byte/anikuta`. It reached a working state but had
  structural/modularity issues and unpolished UI — the reasons we are starting
  fresh. However, it contains valuable prior research (Aniyomi subsystem analysis,
  30+ session logs, a modularization assessment) that we should not throw away.
- **Decision:** Add a second read-only reference tree `OLD_ANIKUTA/ANIKUTA_OLD/`
  containing a source-only tarball snapshot of the old project. Treat it with the
  same read-only discipline as `ANIYOMI_REFRENCE/` (ADR-005). Mine it for insights
  during the design phase; do not edit it.
- **Consequences:**
  - ✅ Prior research (especially `DOCS/REFERENCE-DOCS/SUBSYSTEMS/`) is preserved
    and reusable — saves redoing the Aniyomi analysis from scratch.
  - ✅ Lessons-learned (`MODULARIZATION-ASSESSMENT.md`) inform our architecture
    choices so we avoid the old project's pitfalls.
  - ⚠️ The old project shipped its own Aniyomi snapshot at
    `OLD_ANIKUTA/ANIKUTA_OLD/REFERENCE/` (commit `2f5cf77`). This duplicates our
    top-level `ANIYOMI_REFRENCE/` (newer `main`) at an older commit. We keep it
    intact for fidelity — the old project's docs cite line numbers against that
    specific commit. Prefer our top-level snapshot when studying Aniyomi directly.
  - ⚠️ Repo grows by ~37 MB (old project + its nested reference). Acceptable.

## ADR-008 — Task-completion notifications via ntfy.sh

- **Date:** Phase 0 (second setup task).
- **Context:** The owner requires that every completed task — small or big —
  triggers a notification via ntfy.sh to topic `TASKISDONE`, so progress is
  visible without watching the terminal. The owner specified a fixed format
  (8 colored emojis then the message) and a color semantics (green=success,
  red=error, blue=stopped/needs-input, orange=processing).
- **Decision:** Every agent MUST send an ntfy.sh notification on task completion
  (and optionally on starting a long task). The exact format, colors, and curl
  command are specified in `RULES/notifications.md`. This is a hard rule, added
  to `AGENTS.md` §4 and `RULES/agent-conventions.md`.
- **Consequences:**
  - ✅ Owner gets push notifications for every task outcome without polling.
  - ✅ Color coding communicates outcome at a glance.
  - ⚠️ Agents must remember to send the notification even on small tasks.
    Mitigated by documenting it as a hard rule in multiple places.
  - ⚠️ The ntfy.sh topic is public (no auth). Do not include secrets in the
    message body. Documented in `RULES/notifications.md`.

---

## Open decisions (to be made in the design phase — Phase 0b)

These are *not yet decided*. Listed here so they are not forgotten. Each becomes
an ADR when decided.

- [ ] Module granularity & final module list (see `02-target-architecture.md`).
- [ ] DI framework (Injekt/Koin vs Hilt vs Metro vs …).
- [ ] Persistence (SQLDelight vs Room vs …).
- [ ] Source/extension system shape (Aniyomi-compatible vs first-party-only).
- [ ] Compose-only vs mixed Views.
- [ ] Min/target SDK.
- [ ] Localization tooling (Moko Resources vs stock).
- [ ] Player & reader implementation approach.
- [ ] Which Aniyomi features are hidden/deferred initially.
- [ ] Backup format & Aniyomi-backup compatibility.

## How to add a new ADR

1. Use the next free `ADR-NNN` number.
2. Copy the format above (Date / Context / Decision / Consequences).
3. Commit with message `docs(adr): ADR-NNN <short title>`.
4. If it supersedes an earlier ADR, update the earlier one with a strikethrough
   note pointing to the new one.
