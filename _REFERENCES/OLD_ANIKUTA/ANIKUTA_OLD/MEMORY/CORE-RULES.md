# Core Rules — ANI-KUTA Project

> This file is the **single source of truth** for how we work together.
> Read it at the start of every session. Never delete it.
> Backed up to GitHub so context survives sandbox resets.

---

## 0. Project Snapshot

> **For the live, always-current status, read `DOCS/CURRENT-STATE.md`.**
> The table below is a quick orientation only; it may lag behind reality.

| Field | Value |
|-------|-------|
| Project name | **ANI-KUTA** (display name) |
| GitHub repo | https://github.com/testplay-byte/anikuta |
| App ID | `app.anikuta` |
| Origin | Built **on the foundations of** aniyomi (https://github.com/aniyomiorg/aniyomi) — reuses aniyomi's extension system, SQLDelight DB pattern, Injekt DI, and MPV player. NOT a copy of aniyomi; uses its own techniques in most places. |
| Reference folders | `REFERENCE/` (pristine aniyomi snapshot, commit `2f5cf77`, READ-ONLY) + `REFERENCE-STAGING/` (empty; for upstream review) |
| Backup | GitHub (`MEMORY/` + `DOCS/` pushed regularly; secrets gitignored) |
| APK signing | Debug: committed `app/debug.keystore` (alias `debug`, password `android`). Release: unsigned (not yet set up). Distribution target: GitHub releases (NOT Play Store). |
| Account | Currently a **test account** (`testplay-byte`); may migrate later |
| Status | **Phases 0–7 complete on `main`.** Player user-verified on-device. Full download system merged. Phase 7.5 (episode list enhancements) is next. See `DOCS/CURRENT-STATE.md`. |

---

## 1. The Four Core Rules

### Rule 1 — Understand before acting
- Analyze → research → comprehend → confirm.
- Look into related files, sites, resources fully.
- **Never assume. Never guess.** Ask the user to confirm anything unclear.
- If unsure about a project module, stop and ask.

### Rule 2 — Modular complexity
- Long/complex tasks → split into multiple functions/files.
- No single file should be too large. Small, manageable, documented.
- Each file = one clear responsibility. "This file does X."
- Split long tasks into parts where possible.

### Rule 3 — Summary after completion
- Provide a to-the-point summary of what was done.
- Do **not** exaggerate. Do **not** leave out key details.
- Before any big/breaking change → **clarify with user first**.

### Rule 4 — Issue / Fix / Improvement workflow (step by step)
Follow in order. Never skip. Never rush. One issue at a time.
Do **not** hand issue-fixing to sub-agents — do it yourself.

1. **Pick one issue** (if multiple, pick one).
2. **Understand it fully** — cause, effect, scope, related code.
3. **Verify the issue actually exists.** If it doesn't, don't fix it.
4. **Plan the fix** — how, where, what could break.
5. **Implement the fix.**
6. **Document the issue and the fix.**
7. **Verify the fix works.**
8. Only then move to the next issue.

---

## 2. Communication Rules

- Ask as many questions as needed — but in **batches of 5**.
  - Ask 5 → user answers → ask next 5 → and so on.
- Keep wording **simple, short, easy**. User is not a coding expert.
- Clarify anything unclear directly with the user.
- Proactively highlight concerns, limits, risks.
- Guide the user through problems/constraints.
- **Do not sugarcoat. Do not blindly agree.**
- Be honest and direct at all times.
- If something can't be done → say so clearly, explain why.
- Don't force a fix that could cause other issues.

---

## 3. Architecture Rules

### UI / Logic separation (very important)
- **Backend logic, processing, core workings** = fully separate from UI/UX.
- The same logic must be **reusable anywhere**.
- The UI can be changed/updated/improved **independently**.
- Goal: a **highly customizable UI** (multiple design languages, themes).

### Folder separation
- Each area of logic/backend gets **its own folder**.
- Easy to navigate, understand, and document.
- Feature-based folder structure under each top-level folder.

### Project structure principles
- Keep the project easy to handle and manage.
- Split the codebase into multiple files (development, maintenance, reuse).
- Document every change in the `DOCS/` folder.
- `DOCS/` is also pushed to GitHub.

---

## 4. Memory & Backup Strategy

### Why a memory folder?
- The sandbox environment sometimes **forgets / deletes** things automatically.
- `MEMORY/` holds: rules, context, session logs, decisions, credentials.
- All of `MEMORY/` + `DOCS/` is pushed to **GitHub** as backup — **except secrets**
  (the GitHub token file is gitignored and stays local only).
- If the sandbox resets: user re-links the GitHub repo → I read `MEMORY/` →
  resume from where we left off **with full context**. (Token must be re-pasted.)

### What lives where
```
anikuta/                       ← our GitHub repo root
├── README.md
├── .gitignore
├── MEMORY/                    ← session memory + rules + credentials
│   ├── CORE-RULES.md          ← YOU ARE HERE (rules, never edit lightly)
│   ├── PROJECT-CONTEXT.md     ← what the project is, goals, links
│   ├── SESSION-START-GUIDE.md ← read this first every new session
│   ├── SESSION-LOGS/          ← one file per session (date-stamped)
│   ├── DECISIONS/             ← key decisions + reasoning (ADR style)
│   └── CREDENTIALS/           ← secrets (token file GITIGNORED; README tracked)
├── DOCS/                      ← all documentation
│   ├── NAVIGATION-GUIDE.md    ← top-level repo map (read first)
│   ├── ARCHITECTURE/
│   └── REFERENCE-NAVIGATION/  ← guide to the aniyomi source in REFERENCE/
├── SETUP/                     ← environment setup + APK signing plan
├── BUILD-APK/                 ← built APK outputs (gitignored binaries)
├── REFERENCE/                 ← pristine aniyomi copy (READ-ONLY, never edit)
└── REFERENCE-STAGING/         ← incoming upstream copies for review
```

---

## 5. The Reference Folder Strategy

We keep **two** reference folders:

- **`REFERENCE/`** — the stable, trusted, **read-only** copy of aniyomi.
  Never edit. Never build from here. It is our "source of truth" for what
  upstream looks like.
- **`REFERENCE-STAGING/`** — where a **fresh** copy of upstream lands when we
  want to check for updates. We diff `REFERENCE-STAGING/` against `REFERENCE/`,
  review what changed, and decide what to adopt into **our working code**
  (the future `app/` folder). We do **not** promote changes into `REFERENCE/`
  by editing — instead we refresh `REFERENCE/` by replacing the whole copy
  after a review is accepted.

### Update flow
1. Copy the latest upstream into `REFERENCE-STAGING/`.
2. Diff `REFERENCE-STAGING/` vs `REFERENCE/`.
3. Review the changes.
4. Decide what to adopt → apply to our working `app/` code.
5. When accepted, replace `REFERENCE/` with the reviewed `REFERENCE-STAGING/`
   copy (so `REFERENCE/` stays the trusted snapshot).

---

## 6. The Live Preview Web Page

- A web page (Next.js, route `/`) shows our progress visually.
- It displays: the mind map of folders, analyzed elements, rules, status.
- Purpose: give the user an **easy visual** of architecture & progress
  (user is not an expert in coding/architecture — I guide them).
- Updated regularly as the project evolves.

---

## 7. Notification Rule

- After **each completed task / response**, send a notification to:
  `ntfy.sh/TASKISDONE`
- Method: HTTP POST to `https://ntfy.sh/TASKISDONE`
- (Changed from `THEANIMEAPPTASKISDONE` to `TASKISDONE` in Session 30. The CI
  workflow `.github/workflows/build-apk.yml` uses `TASKISDONE`.)

---

## 8. Quick Checklist (read every session)

- [ ] Read `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` (the entry point)
- [ ] Read `DOCS/CURRENT-STATE.md` (live status)
- [ ] Read `DOCS/ENGINEERING/WORKING-RULES.md` (binding change rules)
- [ ] Read `MEMORY/CORE-RULES.md` (this file — philosophy + 4 core rules)
- [ ] Check `MEMORY/DECISIONS/` for prior decisions
- [ ] Check the tail of `worklog.md` for recent activity
- [ ] Confirm current goal with user before acting
- [ ] Work in small, documented steps
- [ ] On finish: update session log, push to GitHub, send ntfy

---

_Last updated: Session 1 (repo setup). Update this file only with user agreement._
