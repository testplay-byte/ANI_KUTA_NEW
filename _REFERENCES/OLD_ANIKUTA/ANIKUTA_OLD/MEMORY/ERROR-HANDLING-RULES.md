# Error Handling Rules — ANI-KUTA Project

> **Read this before fixing ANY error, build failure, or bug.**
> This is the single source of truth for how we resolve issues.
> Backed up to GitHub so it survives sandbox resets.
> Last updated: Session 31 (library / history / search improvement work).

---

## The Golden Rule

**Never assume. Never guess. Never rush. One issue at a time.**

When we hit an error — a build failure, a runtime crash, a broken feature,
a lint error, anything — we follow this exact 7-step workflow. We do not
skip steps. We do not try to fix multiple issues at once. We do not chase
the first plausible cause without verifying it.

---

## The 7-Step Error Resolution Workflow

### Step 1 — Understand the error
- **Read the error message completely.** Do not skim.
- Figure out *what* the error is, in plain language.
- Is it a compile error? A runtime crash? A build failure? A logical bug?
  A missing dependency? A type mismatch? A null pointer?
- Where does it occur? (build time, runtime, which screen, which file/line)
- **Do NOT jump to a fix yet.** Make sure you actually understand what
  the error is saying before moving on.

### Step 2 — Find the cause
- Trace *where* the error is coming from.
- What is the **root cause** — not just the symptom?
- What is the error **affecting**? (which feature, which screen, which flow)
- Is it caused by recent changes, or pre-existing?
- Look at the relevant code. Read it carefully.
- **Do NOT assume the cause.** Verify it by reading the actual code/log.

### Step 3 — Analyze the fix
- Determine what the fix **will be**.
- Think it through: after applying this fix, will everything be resolved?
  Or only mostly? Or will it introduce new problems?
- Consider side effects — what else depends on the code you're changing?
- If the fix is risky or unclear, say so. Ask the user before proceeding
  on a big/breaking change (per Core Rule 3).
- **Do NOT apply the fix yet.** Plan it first.

### Step 4 — Apply the fix
- Implement the fix you planned in Step 3.
- Keep it small and focused — one issue, one fix.
- Follow the modular-complexity rule (Core Rule 2): small, focused changes.

### Step 5 — Test & verify
- Rebuild / re-run / re-trigger the failing flow.
- **Did it resolve the issue?**
  - **Yes, fully** → go to Step 7 (move on).
  - **No** → do NOT go down a wrong rabbit hole. Stop. Re-analyze.
    Go back to Step 1 with the new information. Try a *different*, better
    approach. Do not keep hammering the same broken fix.
  - **Partially** → go to Step 6.

### Step 6 — Evaluate partial fixes
- If the issue was **slightly fixed** (improved but not fully resolved):
  - Decide: is "slightly better" good enough to move forward, or do we
    need a proper fix?
  - If moving forward: note the remaining gap. We'll come back to it.
- If the fix **caused MORE issues** than it solved:
  - **Stop. Question the decision.** Was the approach correct?
  - Revert if necessary. Re-think from Step 1.
  - Do not pile more fixes on top of a bad fix.

### Step 7 — Move on (one at a time)
- If there are **multiple issues**, resolve them **one step at a time**.
- Do **not** try to fix all issues at once.
- One issue → full 7-step workflow → resolved → next issue.
- Document each fix (Core Rule 4, step 6: document the issue and the fix).

---

## Reminders

- **One issue at a time.** Even if a build has 10 errors, fix them one by
  one, re-building between each, so you know which fix worked.
- **Verify before fixing.** (Core Rule 4, step 3.) If the issue doesn't
  actually exist, don't "fix" it.
- **Document the fix.** (Core Rule 4, step 6.) Note what was wrong, what
  you changed, and why.
- **Verify the fix works.** (Core Rule 4, step 7.) Don't assume.
- **Don't go down rabbit holes.** (Step 5.) If a fix isn't working after
  a genuine attempt, stop and try a different approach.
- **Ask the user** before any big/breaking change (Core Rule 3).

---

## When this applies

- Build failures (Gradle, GitHub Actions APK build).
- Compile errors (Kotlin, TypeScript).
- Runtime crashes (app force-close, player crash).
- Logical bugs (feature doesn't work as expected).
- Lint / type-check errors.
- Any "it's broken" situation.

---

_This file is the contract for how we fix things. Update only with user
agreement._
