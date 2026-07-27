# Reference Navigation Guide — aniyomi source

> **Status: Populated.** aniyomi `main` @ `2f5cf77` (2025-11-05) is in
> `REFERENCE/`. This guide is the 5-minute orientation. For depth, follow the
> links to the dedicated docs.

This guide is the **entry point** into `REFERENCE/` — the pristine, read-only
copy of the aniyomi upstream source. Use it to get oriented, then drill into
the detailed docs.

**Current snapshot:** aniyomi `main` @ `2f5cf77` (2025-11-05).
See `SOURCE-SNAPSHOT.md` for full version details.

---

## 1. Overview of aniyomi

aniyomi is an anime/manga streaming and reading app for Android. It is the
anime fork of **Mihon/Tachiyomi** — the long-running open-source manga reader
ecosystem. aniyomi extends the Tachiyomi/Mihon architecture with anime
playback (a video player, episode sources, and anime trackers) alongside the
original manga reading features.

- Upstream repo: https://github.com/aniyomiorg/aniyomi
- Ecosystem: Tachiyomi fork lineage (Mihon is the active manga-only successor;
  aniyomi is the anime + manga fork).
- License: Apache License 2.0 (see `REFERENCE/LICENSE`).
- Language: Kotlin. Build system: Gradle (Kotlin DSL).
- Snapshot: 1,988 files, ~24 MB, commit `2f5cf77` (2025-11-05).

What we want from aniyomi (initial focus, to be refined):
- TODO — confirm with user which subsystems we intend to reuse vs. rewrite.

---

## 2. Top-level module map (quick view)

aniyomi is a **13-module Gradle project** with clean-architecture layering.

| Module | One-line purpose |
|--------|------------------|
| `app/` | The Android application (952 kt files — the bulk). |
| `core/common/` | Shared utilities (preferences, storage, i18n bridge). |
| `core/archive/` | Archive format handling (CBZ/CBR/EPUB/ZIP/RAR). |
| `core-metadata/` | Metadata parsing for entries. |
| `data/` | SQLDelight database + repository implementations. |
| `domain/` | Domain models + use cases + repo interfaces. |
| `source-api/` | **The source/extension contract** (plugin boundary). |
| `source-local/` | Local-filesystem source implementation. |
| `i18n/` | Shared (Mihon) translations. |
| `i18n-aniyomi/` | aniyomi-specific translations. |
| `presentation-core/` | Shared Compose UI components + design system. |
| `presentation-widget/` | Home-screen widgets. |
| `macrobenchmark/` | Performance benchmarks (not shipped). |

**For the full module reference** (each module's location, key contents,
dependencies, and used-by), see **`MODULES.md`**.

**For the `app/` module's internal package tree** (all 20 screen packages,
the 11 trackers, the source/extension system, DI, etc.), see
**`APP-STRUCTURE.md`**.

---

## 3. How it all fits together (quick view)

```
app  →  data + domain + presentation + source-* + core-* + i18n
              ↓
          domain (contracts)  ←  data (implements)
              ↓
          source-api (plugin boundary for 3rd-party extensions)
              ↓
          core/common + i18n (foundation)
```

**For the full architecture** (layering diagram, the source/extension system,
dual anime/manga model, request lifecycles, DI, persistence, UI architecture),
see **`ARCHITECTURE.md`**.

---

## 4. How to find things — quick lookup

| I want to find… | Look in… |
|------------------|----------|
| A module's purpose / dependencies | `MODULES.md` |
| A screen or feature inside `app/` | `APP-STRUCTURE.md` |
| How data flows / the big picture | `ARCHITECTURE.md` |
| The exact aniyomi version | `SOURCE-SNAPSHOT.md` |
| The video player | `APP-STRUCTURE.md` → `ui/player/` |
| The manga reader | `APP-STRUCTURE.md` → `ui/reader/` |
| A tracker integration | `APP-STRUCTURE.md` → `data/track/<service>/` |
| The source/extension contract | `MODULES.md` → `:source-api` |
| The database schema | `MODULES.md` → `:data` (`sqldelight*/`) |
| A domain model / use case | `MODULES.md` → `:domain` |

---

## 5. How to diff upstream updates

Use this mini-procedure whenever we want to bring in a new aniyomi release.

1. **Take a fresh copy** of the new aniyomi version and place it in
   `REFERENCE-STAGING/` (do NOT touch `REFERENCE/` yet).
2. **Diff** `REFERENCE-STAGING/` against `REFERENCE/`:
   - `diff -qr REFERENCE/ REFERENCE-STAGING/` for a quick file-level list,
     or `git diff --no-index REFERENCE/ REFERENCE-STAGING/` for full content.
3. **Review** the changes by subsystem using `MODULES.md` and
   `APP-STRUCTURE.md` to locate what each change affects. Focus on:
   - security-relevant changes,
   - data-layer / persistence changes (migration implications — remember the
     dual anime/manga databases),
   - `:source-api` changes (extension compatibility),
   - anything that touches code we have already modified in our working `app/`.
4. **Decide what to adopt.** For each non-trivial adoption, record a short
   decision entry in `MEMORY/DECISIONS/` (ADR style: context → decision →
   consequence).
5. **Promote into our working code** (`app/`), NOT into `REFERENCE/`. Apply
   and adapt the changes, keeping our UI/logic separation and feature-folder
   rules.
6. **Refresh `REFERENCE/`** by replacing the whole copy with the contents of
   `REFERENCE-STAGING/`. (`REFERENCE/` is only ever refreshed wholesale; it
   is never hand-edited.)
7. **Clear `REFERENCE-STAGING/`** so it is empty until the next review cycle.
8. **Update `SOURCE-SNAPSHOT.md`** with the new commit hash + date, and
   update `MODULES.md` / `APP-STRUCTURE.md` / `ARCHITECTURE.md` if the
   upstream structure changed.

---

## 6. Open questions

- TODO — do we mirror aniyomi's package layout (`eu.kanade.*`), or restructure
  to fit our feature-folder rule + our `app.anikuta` package? (Future decision
  — affects how much we can copy vs. rewrite.)
- TODO — license obligations (Apache 2.0): will be handled at publish time
  per user (attribution + NOTICE + license text). Not a concern during dev.
- TODO — keep both anime + manga models, or go anime-only? (Future decision.)
