# Aniyomi Integration — How aniyomi works behind the scenes

> ANI-KUTA is NOT aniyomi. We **reuse aniyomi's backend systems** (source/extension
> system, player, download manager, data layer) but build our **own UI** and add
> **AniList as a discovery layer** that aniyomi doesn't have.
>
> This doc explains what we reuse, what we change, and how the two layers connect.

---

## What we reuse from aniyomi (the backend layer)

| aniyomi subsystem | Do we reuse it? | How | See |
|-------------------|-----------------|-----|-----|
| Source/extension system | ✅ Yes | Anime extensions stream episodes for us. | `REFERENCE-DOCS/SUBSYSTEMS/SOURCE-SYSTEM.md` |
| Player (MPV) | ✅ Yes | The video player. Maybe reskinned per our 4 designs. | `REFERENCE-DOCS/SUBSYSTEMS/PLAYER.md` |
| Data layer (SQLDelight) | ✅ Yes | Library, history, downloads, preferences. Anime DB only. | `REFERENCE-DOCS/SUBSYSTEMS/DATA-LAYER.md` |
| Download manager | ✅ Yes | Offline episode downloads. Anime side only. | `REFERENCE-DOCS/SUBSYSTEMS/DOWNLOAD-MANAGER.md` |
| Backup/restore | ✅ Yes | User data backup. | `REFERENCE-DOCS/SUBSYSTEMS/BACKUP-RESTORE.md` |
| Trackers | ⚠️ Partial | AniList is our primary tracker. Other trackers optional. | `REFERENCE-DOCS/SUBSYSTEMS/TRACKERS.md` |
| DI (Injekt) | ✅ Yes | Wiring. | `REFERENCE-DOCS/SUBSYSTEMS/DI.md` |
| Theme/design system | ❌ No | We build our own 4 designs. | `PLAN/CUSTOMIZATION.md` |
| Manga reader | ❌ No | Anime-only. Deferred. | `REFERENCE-DOCS/SUBSYSTEMS/READER.md` |
| Manga source/extension | ❌ No | Anime-only. Deferred. | `REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md` |

---

## What we ADD (that aniyomi doesn't have)

1. **AniList as the discovery layer.**
   - aniyomi uses its extensions for everything (search, browse, discovery).
   - ANI-KUTA uses **AniList for discovery** (home page, trending, genres, schedules)
     and **aniyomi extensions for streaming** (resolving episode video URLs).
   - This is our key differentiator. AniList gives us rich metadata (ratings,
     sub/dub counts, genres, schedules) that extensions don't provide.

2. **The 4-design customization system.**
   - aniyomi has one UI (with theme variants). We have 4 full designs.
   - See `CUSTOMIZATION.md`.

3. **A cleaner UI/logic separation.**
   - aniyomi is mid-migration (Views → Compose). We go Compose-first.
   - Our UI layer is fully swappable; the backend doesn't know which design is active.

---

## The integration boundary

```
┌──────────────────────────────────────────────────────────────┐
│                       ANI-KUTA app                            │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                    UI layer (ours)                       │ │
│  │  - Home, Detail, Player, Library, ...                   │ │
│  │  - 4 designs + theming                                  │ │
│  │  - Compose-first                                        │ │
│  └────────────────────────┬────────────────────────────────┘ │
│                           │ calls                             │
│                           ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Integration layer (ours)                    │ │
│  │  - AniList client (GraphQL)                              │ │
│  │  - AniyomiSourceBridge: AniList anime → extension lookup │ │
│  │  - ProgressSync: player progress → DB + AniList          │ │
│  └────────────────────────┬────────────────────────────────┘ │
│                           │ uses                              │
│                           ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Backend layer (from aniyomi)                │ │
│  │  - AnimeSourceManager + AnimeExtensionManager            │ │
│  │  - PlayerActivity / PlayerViewModel (MPV)                │ │
│  │  - AnimeDownloadManager                                  │ │
│  │  - AnimeDatabase (SQLDelight)                            │ │
│  │  - BackupManager                                         │ │
│  │  - Injekt DI                                             │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### The key bridge: AniyomiSourceBridge

AniList gives us an anime's title + ID. aniyomi extensions are searched by
title. The bridge:

1. Takes an AniList anime (title + metadata).
2. Searches the user's installed anime extensions for that title.
3. Returns matching sources + their internal anime IDs.
4. When the user plays an episode, the bridge asks the matched extension to
   resolve the video stream.

> This bridge is the core integration code we write. Everything else is
> aniyomi's existing backend, called through its existing interfaces.

---

## What we track (for upstream comparison)

Every aniyomi subsystem we reuse is recorded in `DOCS/APP/STRUCTURE/` as we
build. When aniyomi updates (monthly check — see `UPSTREAM-TRACKING.md`), we
diff the relevant subsystem and decide whether to adopt changes.

Tracked subsystems (initial):
- `:source-api` (anime side) + `AnimeSourceManager` + `AnimeExtensionManager`
- `PlayerActivity` + `PlayerViewModel` + MPV integration
- `AnimeDownloadManager` + download job
- `AnimeDatabase` (SQLDelight schema)
- `BackupCreator` + `BackupRestorer` (anime fields)
- Injekt modules (anime bindings only)

---

## Open questions

- [ ] AniList: just data source, or also tracker (login + sync)?
- [ ] Extension lookup: by title (fuzzy), or by AniList ID mapping?
- [ ] Player: reskin the UI per our 4 designs, or wrap aniyomi's player as-is?
- [ ] Trackers beyond AniList: keep the other 4 dual-support trackers, or AniList-only?

---

_Last updated: Session 8 (initial draft)._
