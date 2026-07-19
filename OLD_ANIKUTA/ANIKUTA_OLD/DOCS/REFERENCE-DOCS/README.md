# REFERENCE-DOCS — aniyomi reference documentation

> This folder holds **all documentation for the `REFERENCE/` folder** (the
> pristine, read-only copy of aniyomi). It tells us where each part of
> aniyomi is, what it does, and how the parts relate.
>
> **Update policy:** these docs are NOT updated regularly. They are updated
> only when aniyomi changes are pulled in (via the `REFERENCE-STAGING/`
> review process) or when we discover something new during development.

---

## Status

**Fully populated (Session 4).** aniyomi `main` @ `2f5cf77` (2025-11-05) is
in `REFERENCE/`. Documented at three levels: orientation, architecture, and
per-subsystem deep dives + cross-cutting analyses (dual-model coupling,
decisions pros/cons).

---

## Files in this folder

### Orientation & structure

| File | What it covers | Read this when… |
|------|----------------|-----------------|
| `README.md` | This file — index + update policy. | You're new to the reference docs. |
| `SOURCE-SNAPSHOT.md` | Exactly which aniyomi version is in `REFERENCE/`, + the refresh procedure. | You need to know the version, or you're updating the reference. |
| `NAVIGATION-GUIDE.md` | The high-level map: aniyomi overview + top-level module map + how to diff upstream updates. | You want the 5-minute orientation. |
| `MODULES.md` | **The 13 Gradle modules** — each one's location, purpose, key contents, dependencies, and used-by. | You need to find or understand a specific module. |
| `APP-STRUCTURE.md` | The `app/` module's internal package tree (952 files) — every sub-package with its purpose. | You're looking for a specific screen, feature, or file inside the app. |
| `ARCHITECTURE.md` | How the modules fit together — layering, the source/extension system, dual anime/manga model, request lifecycles, DI, persistence, UI architecture. | You want to understand the big picture / how data flows. |

### Analysis

| File | What it covers | Read this when… |
|------|----------------|-----------------|
| `ANALYSIS-DUAL-MODEL.md` | Are anime & manga linked? Can they be separated? The coupling analysis + the anime-only extraction procedure. | You're planning an anime-first build / deciding on manga. |
| `DECISIONS-ANALYSIS.md` | Pros/cons of the 4 build decisions (package layout, anime-only, DI, persistence). **No decisions made — analysis only.** | You're weighing architectural options before building. |

### Subsystem deep dives (`SUBSYSTEMS/`)

| File | Subsystem | Anime-critical? |
|------|-----------|----------------|
| `SUBSYSTEMS/SOURCE-SYSTEM.md` | Source/extension plugin system (source-api + loaders + managers). | ✅ |
| `SUBSYSTEMS/PLAYER.md` | Anime video player (MPV). | ✅ |
| `SUBSYSTEMS/DATA-LAYER.md` | SQLDelight databases + repositories (dual DBs). | ✅ |
| `SUBSYSTEMS/TRACKERS.md` | The 11 tracker integrations. | ✅ (subset) |
| `SUBSYSTEMS/DOWNLOAD-MANAGER.md` | Episode/chapter download manager (WorkManager + FFmpeg). | ✅ |
| `SUBSYSTEMS/BACKUP-RESTORE.md` | Backup/restore (gzipped protobuf `.tachibk`). | ✅ |
| `SUBSYSTEMS/DI.md` | Dependency injection (Injekt). | ✅ (cross-cutting) |
| `SUBSYSTEMS/UI-THEME.md` | UI architecture (Voyager + Compose) + theming (Material 3). | ✅ (cross-cutting) |
| `SUBSYSTEMS/READER.md` | Manga reader (paged/webtoon). | ❌ manga-only (for later) |

### Recommended reading order

1. `SOURCE-SNAPSHOT.md` — know what version you're reading.
2. `NAVIGATION-GUIDE.md` — 5-minute orientation.
3. `ARCHITECTURE.md` — the big picture (layers, data flow).
4. `ANALYSIS-DUAL-MODEL.md` — understand the anime/manga coupling (key for our anime-first plan).
5. `MODULES.md` / `APP-STRUCTURE.md` — drill into a specific module/feature.
6. `SUBSYSTEMS/<subsystem>.md` — drill into a specific subsystem.
7. `DECISIONS-ANALYSIS.md` — when weighing build options.

---

## How this folder is used

1. **Finding something in aniyomi:** start at `NAVIGATION-GUIDE.md`, then
   drill into `MODULES.md` or `APP-STRUCTURE.md`.
2. **Understanding how aniyomi works:** read `ARCHITECTURE.md`.
3. **When aniyomi updates are reviewed** (via `REFERENCE-STAGING/`): update
   these docs if the structure changed, and record the decision in
   `MEMORY/DECISIONS/`. Follow the refresh procedure in `SOURCE-SNAPSHOT.md`.
4. **Treat these docs as the index into `REFERENCE/`** — they should let a
   reader find any aniyomi subsystem quickly without grepping the source.

---

## Related

- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map (where `REFERENCE-DOCS/`
  fits in the whole repo).
- `DOCS/ARCHITECTURE/README.md` — our project's architecture principles
  (UI/logic separation, feature folders, reference-folder strategy).
- `MEMORY/DECISIONS/` — decisions about how aniyomi patterns map to our build.
