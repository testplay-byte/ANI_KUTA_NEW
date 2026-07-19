# Subsystem Docs — aniyomi

> Deep dives into each aniyomi subsystem: where it lives, what it does, how it
> works internally, and how it handles the dual anime/manga model.
>
> Read `MODULES.md` + `APP-STRUCTURE.md` + `ARCHITECTURE.md` first for the
> big picture; come here when you need depth on a specific subsystem.

---

## Files

| File | Subsystem | Anime-critical? |
|------|-----------|-----------------|
| `SOURCE-SYSTEM.md` | The source/extension plugin system (source-api + loaders + managers). | ✅ yes |
| `PLAYER.md` | The anime video player (MPV). | ✅ yes (anime-only) |
| `DATA-LAYER.md` | SQLDelight databases + repository implementations. | ✅ yes |
| `TRACKERS.md` | The 11 tracker integrations (MAL, AniList, etc.). | ✅ yes (anime subset) |
| `DOWNLOAD-MANAGER.md` | Episode/chapter download manager (WorkManager + FFmpeg). | ✅ yes |
| `BACKUP-RESTORE.md` | Backup/restore (gzipped protobuf `.tachibk`). | ✅ yes |
| `DI.md` | Dependency injection (Injekt). | ✅ yes (cross-cutting) |
| `UI-THEME.md` | UI architecture (Voyager + Compose) + theming (Material 3). | ✅ yes (cross-cutting) |
| `READER.md` | The manga reader (paged/webtoon). | ❌ manga-only (for later) |

## Each doc follows the same structure

1. Where it lives
2. What it does
3. Key files & classes
4. How it works (internals + data flow)
5. Dependencies
6. **Anime vs manga** (how it handles the dual model — coupling notes)
7. Relationships (cross-refs)
8. Notes for our build (anime-first)
9. TODOs / open questions

## Related analysis docs (one level up)

- `../ANALYSIS-DUAL-MODEL.md` — synthesizes the "anime vs manga" sections
  across all subsystems into one coupling analysis + the anime-only extraction
  procedure.
- `../DECISIONS-ANALYSIS.md` — pros/cons for the 4 build decisions, informed
  by these subsystem docs.
