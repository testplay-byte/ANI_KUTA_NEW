# Session handoff — add OLD_ANIKUTA reference + ntfy notification rule

**Agent:** Z.ai Code (session 2)
**Task ID:** setup-phase-0-task-2
**Session goal:**
1. Persist the ntfy.sh task-completion notification rule (topic `TASKISDONE`).
2. Download the old ANIKUTA project (`github.com/testplay-byte/anikuta`) and save
   it as a second read-only reference under `OLD_ANIKUTA/ANIKUTA_OLD/`.

## What I did

- Pulled latest repo state (was already up to date on `main`).
- Sent an "orange/processing" ntfy.sh notification to confirm the rule works.
- Wrote `RULES/notifications.md` documenting the mandatory notification rule
  (topic, format, color semantics: 🟩/🟥/🟦/🟧, examples).
- Downloaded the old ANIKUTA source tarball (`archive/refs/heads/main.tar.gz`,
  source-only, no `.git`).
- Extracted into `OLD_ANIKUTA/ANIKUTA_OLD/` (37 MB, 2580 files). Kept the old
  project's own `REFERENCE/` folder intact for fidelity (it duplicates our
  `ANIYOMI_REFRENCE/` at an older Aniyomi commit `2f5cf77`).
- Wrote `OLD_ANIKUTA/README.md` (provenance, rules, note on the duplicated
  Aniyomi copy, what's valuable to mine).
- Updated root `README.md` (layout + status now mention OLD_ANIKUTA + ntfy rule).
- Updated `AGENTS.md` (cheat sheet row, hard rule #1 now covers both references,
  hard rule #8 added for notifications, §5 rewritten to cover both references).
- Added ADR-007 (second read-only reference) and ADR-008 (ntfy notifications) to
  `DOCS/04-design-decisions.md`.
- Updated `RULES/README.md` and `RULES/agent-conventions.md` (new rule #11) to
  reference `notifications.md`.
- Updated `.github/CODEOWNERS` and `.github/workflows/ci-placeholder.yml` to
  cover the new reference + the new `RULES/notifications.md` doc.

## What is DONE

- ntfy.sh notification rule persisted and demonstrated (sent a real notification).
- Old ANIKUTA project saved as a read-only reference, fully documented.
- All navigation docs (root README, AGENTS.md, RULES/) updated to point at it.
- CI sanity job updated to verify both references + the notifications doc.

## What is NOT done

- No analysis of the old project's contents yet (deferred — owner will direct the
  Aniyomi analysis approach next).
- No code written under `ANIKUTA_PROJECT/ANIKUTA/` (still Phase 1, after design).

## What the NEXT agent should do

- **Wait for the owner's instructions** on how to do the Aniyomi reference
  analysis. The owner explicitly said they will guide this — do not start it
  autonomously.
- When directed, a good first read is
  `OLD_ANIKUTA/ANIKUTA_OLD/DOCS/REFERENCE-DOCS/SUBSYSTEMS/` — the old project
  already did per-subsystem Aniyomi analysis. We can review/validate it rather
  than redo it from scratch.
- Also read `OLD_ANIKUTA/ANIKUTA_OLD/DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md`
  for lessons learned before finalizing our `DOCS/02-target-architecture.md`.

## Pointers (files to read first)

- `/OLD_ANIKUTA/README.md` — the new reference's rules + provenance.
- `/RULES/notifications.md` — the mandatory notification rule.
- `/DOCS/04-design-decisions.md` — ADR-007 and ADR-008 (new).
- `/AGENTS.md` — updated cheat sheet + hard rules.

## Dev environment notes

- Working directory: `/home/z/ani_kuta_workspace/ANI_KUTA_NEW`.
- The ntfy.sh topic `TASKISDONE` is public (no auth) — never put secrets in
  notification bodies.
- Repo remains **public** (confirmed by owner).
