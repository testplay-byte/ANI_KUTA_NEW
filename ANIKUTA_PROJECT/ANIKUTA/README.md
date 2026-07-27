# ANIKUTA

> The actual app codebase for the ANIKUTA project.
> A reimagined, restructured version of [Aniyomi](https://github.com/aniyomiorg/aniyomi).

---

## ✅ Status: Feature-complete (Phase 8+)

This is the live ANIKUTA app — **41 Gradle modules** across `:core:*`,
`:feature:*`, `:data:*`, and `:app`. It builds debug APKs via CI and has all
major features working: browse, search, anime details (incl. extension-only
details), watch (MPV), library, history, updates (schedule + calendar), profile
(stats + charts), trackers (AniList + MAL), backup/restore, downloads/offline
playback, and episode settings.

See `../../DOCS/05-roadmap.md` for the full status and what's planned next
(Phase 9: Voyager navigation migration, notifications, general settings, theme
toggle, etc.).

---

## What ANIKUTA is

- An **anime-first** Android app (manga deferred per ADR-009).
- Built on the **concepts** of Aniyomi (library, sources, trackers, player,
  reader), but with a **complete redesign and restructure**.
- Some Aniyomi features are **hidden or deferred** initially (ADR-018, ADR-034);
  all core functionality will eventually be ported/adapted.
- **Modular Gradle architecture** — see `../../ARCHITECTURE.md` §3 for the full
  module tree and `settings.gradle.kts` for the canonical list.

---

## Tech stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (AndroidView for MPV only) |
| Navigation | Hand-rolled state machine in `app/.../MainActivity.kt` (Voyager migration planned) |
| DI | Koin (host) + Injekt (extension compat) |
| Persistence | SQLDelight (with status-tracking columns) |
| Player | MPV (aniyomi-mpv-lib), single instance |
| Extensions | Aniyomi-compatible source-api |

See `../../ARCHITECTURE.md` §2 and `../../DOCS/04-design-decisions.md` (ADRs
001–036) for the full rationale.

---

## Module layout (41 modules)

```
ANIKUTA/
├── app/                        ← application shell (DI, nav, activities, DownloadOrchestrator)
├── core/                       ← 15 core modules (common, designsystem, database, anilist, player, etc.)
├── data/                       ← 5 data modules (anime, extension, history, tracker[stub], manga[stub])
├── feature/                    ← 19 feature modules (browse, search, watch, library, etc.)
├── buildSrc/                   ← convention plugins (anikuta.library, anikuta.library.compose, ...)
├── gradle/                     ← version catalogs (libs.versions.toml + kotlinx/androidx/compose/anikutaLibs)
├── settings.gradle.kts         ← the canonical 41-module list
└── gradle.properties
```

Each module has its own `README.md`. See `../../ARCHITECTURE.md` §3 for
dependency rules and the full tree.

---

## Where the ideas come from

The original Aniyomi source is available read-only at
`../../_REFERENCES/ANIYOMI_REFRENCE/ANIYOMI/` (off-limits for normal work — see
`../../_REFERENCES/README.md`). Use it to study how a feature was implemented,
then re-implement it here in the ANIKUTA style. **Never edit the reference.**

A map of Aniyomi's modules and what they do is at
`../../DOCS/03-reference-module-map.md` — start there when porting.

---

## Build & CI

- **APKs are built via GitHub Actions ONLY** (ADR-003). No local APK builds.
- CI workflow: `../../.github/workflows/ci.yml`
- Build command: `./gradlew :app:assembleDebug` (arm64-v8a only — ADR-032)
- To trigger a build on a feature branch: push + `workflow_dispatch`.

---

## Making changes

1. Read `../../ARCHITECTURE.md` (single source of truth) + the relevant module READMEs.
2. Read `../../RULES/ai-agent-rules.md` (the 14-section ruleset).
3. Work on a feature branch (`feature/...` or `fix/...`). Never commit to `main`.
4. Follow module boundaries: feature modules never import from each other.
5. Update module READMEs + `ARCHITECTURE.md` if structural. Add an ADR if a decision.
6. Leave a session handoff note in `../../RULES/sessions/`.
