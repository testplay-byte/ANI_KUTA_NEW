# Session handoff — Aniyomi reference documentation (Phase 0c)

**Agent:** Z.ai Code (session 3)
**Task ID:** phase-0c-aniyomi-documentation
**Session goal:** Read and understand the entire Aniyomi reference codebase and
produce comprehensive, multi-folder documentation under
`ANIYOMI_REFRENCE/DOCUMENTATION/` so anyone (human or AI agent) can understand
every part of the reference project from the docs alone.

## What I did

- Pulled latest repo state.
- Sent an orange "processing" notification (per ADR-008).
- **Reconnaissance:** read the Gradle config, version catalogs, package trees for
  app/data/domain/source-api, and the reader/player/source/extension/data
  packages — to ground the docs in the actual codebase.
- Created the `DOCUMENTATION/` folder with 8 subfolders.
- Wrote a shared `_SUBAGENT_BRIEF.md` (deleted after use) to give subagents
  consistent reconnaissance context.
- **Wrote the foundational 11 docs myself:**
  - `README.md` (master index, 149 lines)
  - `00-overview/` (5 docs: project overview, tech stack, module map, build
    system, project conventions)
  - `01-architecture/` (6 docs: architecture overview, DI/Injekt, state/async,
    navigation/Voyager, preferences system, error handling)
- **Dispatched 12 parallel subagents in 3 batches** for the heavier docs:
  - Batch A (4 agents): per-module deep dives (`02-modules/`, 14 docs)
  - Batch B (4 agents): subsystem docs (`03-subsystems/`, 16 docs)
  - Batch C (4 agents): data models (`04/`, 4 docs), key flows (`05/`, 9 docs),
    UI (`06/`, 5 docs), reference index (`07/`, 4 docs)
- **Verified link integrity:** 1,081 relative cross-links checked, 0 broken.
- Updated `ANIYOMI_REFRENCE/README.md` to point at the DOCUMENTATION folder.
- Wrote this handoff note.

## What is DONE

- **64 documentation files, ~21,400 lines** under `ANIYOMI_REFRENCE/DOCUMENTATION/`.
- Every Gradle module documented (13 modules + index).
- Every major subsystem documented (15 subsystems + index): reader, player,
  source system, downloads, trackers (11 of them), backup, history, updates,
  search, extensions-update, torrent-streaming, notifications, storage-cache,
  updater, library.
- All domain models + full DB schema (manga + anime, dual) + preference catalog.
- 8 end-to-end user flows traced with actual file paths.
- UI catalog (all screens), theme system, components, Compose migration status.
- Lookup layer: file-index (200+ entries), glossary (57 terms), 15×13
  cross-reference matrix.
- All 1,081 cross-links resolve; no broken links.

## Notable findings (highlights from the analysis)

- The **dual manga/anime pattern** is the single most important architectural
  fact: nearly every concept exists twice (Chapter↔Episode, `sqldelight/`↔
  `sqldelightanime/`, `source`↔`animesource`, etc.).
- Two **independent SQLDelight databases**: `tachiyomi.db` (manga, v32) and
  `tachiyomi.animedb` (anime, v135, bootstrapped when manga was at v112).
- **11 trackers**, not 7: MAL, AniList, Shikimori, Bangumi, MangaUpdates, Kitsu,
  Simkl + Komga, Kavita, Suwayomi (enhanced manga) + Jellyfin (enhanced anime).
- **`.tachibk` backups are gzipped kotlinx-serialization protobuf** (not JSON),
  with anime fields bumped to proto# 500+ so Mihon can still parse the manga half.
- **4 extension installer backends**: Legacy, Private, PackageInstaller, Shizuku.
- Extensions load via `ChildFirstPathClassLoader` (child-first class resolution),
  NOT `DexClassLoader` — lets extensions ship their own Jsoup/OkHttp.
- The **reader and player are the only two legacy View-based Activities**
  (reader uses `subsampling-scale-image-view`; player wraps `AniyomiMPVView`).
  Everything else is Compose + Voyager.
- **19 themes** ship in `:presentation-core` (16 XML + 2 Kotlin + 1 dynamic Monet).
- The player supports **custom Lua-scripted buttons** (bidirectional via MPV
  `user-data/aniyomi/<action>` properties, 12 supported actions).
- Aniyomi **defaults to the Anime library** (not Manga) — an Aniyomi-specific
  deviation from Tachiyomi/Mihon's manga-first default.

## What is NOT done

- Nothing on the documentation side — the task is complete.
- The actual ANIKUTA app still has no code (Phase 1, after design phase 0b).

## What the NEXT agent should do

- The owner said the next step (after this documentation) is "the analysis of the
  original reference project" — but they also said "for now you are not going to
  do anything like that and I will tell you how to do that." So **wait for the
  owner's direction**.
- When directed, the documentation just produced is the perfect base for design
  decisions: every subsystem's architecture, data model, and flow is documented.
  The open decisions in `docs/04-design-decisions.md` can now be resolved by
  referencing these docs.

## Pointers (files to read first)

- `/ANIYOMI_REFRENCE/DOCUMENTATION/README.md` — the master index.
- `/ANIYOMI_REFRENCE/DOCUMENTATION/00-overview/01-project-overview.md`
- `/ANIYOMI_REFRENCE/DOCUMENTATION/00-overview/05-project-conventions.md` — the dual manga/anime pattern.
- `/ANIYOMI_REFRENCE/DOCUMENTATION/07-reference/cross-reference-matrix.md` — subsystem × module map.
- `/home/z/my-project/worklog.md` — the 12 subagent work records (A-1..A-4, B-1..B-4, C-1..C-4) with detailed findings.

## Dev environment notes

- Working directory: `/home/z/ani_kuta_workspace/ANI_KUTA_NEW`.
- 12 subagents ran in 3 parallel batches; their detailed findings are in
  `/home/z/my-project/worklog.md`.
- The `_SUBAGENT_BRIEF.md` scratch file was deleted after use.
- No code under `ANIKUTA_PROJECT/` was touched (still Phase 1).
- No builds performed (CI-only policy, ADR-003).
