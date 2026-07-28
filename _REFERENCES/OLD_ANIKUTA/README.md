# OLD_ANIKUTA/

This directory holds a **frozen, read-only snapshot** of the previous ANIKUTA
attempt — the project at `https://github.com/testplay-byte/anikuta` — kept as a
**secondary reference** alongside [`../ANIYOMI_REFRENCE/`](../ANIYOMI_REFRENCE/).

## Why we keep it

The old ANIKUTA project was an earlier attempt at the same goal: a reimagined
Aniyomi. It reached a working state but had structural and modularity issues that
made it hard for AI agents to edit reliably, and parts of its UI were not
polished. **We are starting fresh** in [`../ANIKUTA_PROJECT/`](../ANIKUTA_PROJECT/)
with proper planning from the start.

However, the old project contains **valuable prior work** we can mine during the
design phase:

- `ANIKUTA_OLD/DOCS/REFERENCE-DOCS/SUBSYSTEMS/` — per-subsystem analysis of
  Aniyomi (reader, player, DI, trackers, download manager, source system, data
  layer, UI/theme, backup/restore). This is research we would otherwise redo.
- `ANIKUTA_OLD/DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` — their own
  assessment of what worked and what didn't (lessons learned).
- `ANIKUTA_OLD/MEMORY/SESSION-LOGS/` — 30+ session logs of prior agent work.
- `ANIKUTA_OLD/DOCS/CURRENT-STATE.md` — a snapshot of where the old project got to.

## What's inside

```
OLD_ANIKUTA/
└── ANIKUTA_OLD/                 ← the old project (source-only, no .git)
    ├── app/ core/ data/ domain/ source-api/   ← 5 Gradle modules
    ├── DOCS/                     ← their documentation (incl. Aniyomi analysis)
    ├── MEMORY/                   ← session logs, rules, decisions
    ├── REFERENCE/                ← ⚠️ the old project's OWN copy of Aniyomi
    ├── REFERENCE-STAGING/
    ├── SETUP/  BUILD-APK/  backup/  live-preview/
    ├── README.md  plan.md  worklog.md
    ├── KNOWN-ISSUES.md  PLAYER_REDO_PLAN.md  STORAGE.md  SUBTITLES_FIX.md
    └── settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew
```

## Rules

- 🔒 **READ-ONLY.** Do not modify, reorganize, or build inside this folder.
  Same status as `../ANIYOMI_REFRENCE/`. See ADR-005 and ADR-007.
- 🚫 **Do not build here.** No `./gradlew` runs inside `ANIKUTA_OLD/`.
- 📋 **Mining for ideas is encouraged** — especially the `DOCS/REFERENCE-DOCS/`
  subsystem analysis during the design phase. Copy insights into our
  `../../docs/` (with attribution) rather than editing the old project.

## Provenance

| Field | Value |
|---|---|
| Source repo | `https://github.com/testplay-byte/anikuta` |
| Branch | `main` |
| Snapshot method | GitHub source tarball (`archive/refs/heads/main.tar.gz`) |
| Includes `.git` history? | **No** — source files only |
| Snapshot date | See the commit that added this folder |
| Old project's own identity | App ID `app.anikuta`, 5 modules, built on Aniyomi foundations |
| License | Inherits repo-level Apache-2.0 |

## Note on the duplicated Aniyomi copy

The old project shipped its **own** Aniyomi snapshot at `ANIKUTA_OLD/REFERENCE/`
(commit `2f5cf77`, 1988 files). This **duplicates** our
`../../ANIYOMI_REFRENCE/ANIYOMI/` snapshot (which is a newer `main` snapshot).

We keep the old project's `REFERENCE/` folder intact for fidelity — it shows
exactly which Aniyomi commit the old project was working against, which matters
when reading their subsystem analysis. When *we* study Aniyomi, prefer our
top-level `../../ANIYOMI_REFRENCE/ANIYOMI/` (newer); cross-reference the old
project's `REFERENCE/` only when their docs cite a specific line/commit.

## How this differs from `../ANIKUTA_PROJECT/`

| | `OLD_ANIKUTA/ANIKUTA_OLD/` | `../ANIKUTA_PROJECT/ANIKUTA/` |
|---|---|---|
| Status | Frozen reference (read-only) | Live project (we write here) |
| Origin | Downloaded from `testplay-byte/anikuta` | Built from scratch by us |
| Use | Study prior art + lessons learned | The actual new app |
