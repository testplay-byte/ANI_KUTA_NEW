# Module Architecture Draft

> **Status: DRAFT — pending open-decision answers.**
> This is a proposed module layout based on the vision (ADRs 009–022) and the
> `RULES/ai-agent-rules.md` module pattern (`:feature:*`, `:core`, `:data`).
> It will be finalized into `ARCHITECTURE.md` once the owner resolves the open
> decisions (DI framework, persistence, etc.).

## Design principles (from `RULES/ai-agent-rules.md`)

- **Feature modules** (`:feature:*`) — one per feature. Never import from each other.
- **`:core`** — shared code used by 2+ features. Repository interfaces live here.
- **`:data`** — repository implementations, database, network.
- **`:app`** — the application shell: DI wiring, navigation, activities.
- **Single entry point per module** — each module has one public entry point.
- **No God classes** — max 300 lines/file, max 3 responsibilities/class.

## Proposed module tree (anime-first, manga-ready per ADR-009)

```
:app                                    ← application shell
├── :core
│   ├── :core:common                    ← utilities, constants, base types
│   ├── :core:designsystem              ← theme, colors, typography, reusable components
│   ├── :core:network                   ← HTTP client, interceptors, rate limiting
│   ├── :core:database                  ← SQLDelight (or Room) — schema, queries
│   ├── :core:preferences               ← PreferenceStore, typed preferences
│   ├── :core:anilist                   ← AniList GraphQL client + repository (ADR-010)
│   ├── :core:episode-metadata          ← per-episode metadata module (ADR-022, pluggable sources)
│   ├── :core:source-api                ← the source/extension contract (KMP)
│   ├── :core:source-local              ← local-files-as-source
│   ├── :core:player                    ← MPV wrapper, player controls, PiP
│   ├── :core:download                  ← download manager, queue, storage
│   ├── :core:notification              ← notification channels, schedulers (ADR-014)
│   └── :core:backup                    ← backup/restore engine
├── :data
│   ├── :data:anime                     ← anime repository impls, anime DB schema
│   ├── :data:manga                     ← manga repository impls, manga DB schema (hidden, ADR-009)
│   ├── :data:extension                 ← extension loader, installer, repo management
│   ├── :data:tracker                   ← tracker impls (MAL, AniList, Shikimori, etc.)
│   └── :data:history                   ← history repository impls
├── :feature
│   ├── :feature:home                   ← Home screen (AniList trending/seasonal)
│   ├── :feature:library                ← Library screen (anime + manga tabs)
│   ├── :feature:updates                ← Updates feed
│   ├── :feature:history                ← History screen
│   ├── :feature:browse                 ← Browse sources / extensions
│   ├── :feature:my                     ← MY screen (personalized dashboard, ADR-021)
│   ├── :feature:more                   ← More tab (settings, downloads, stats, about)
│   ├── :feature:anime-details          ← Anime details screen
│   ├── :feature:episode-list           ← Episode list component (shared)
│   ├── :feature:video-resolver         ← Video resolver screen/sheet (server/audio/quality)
│   ├── :feature:watch                  ← Watch page (YouTube-style, ADR-012)
│   ├── :feature:player                 ← Fullscreen player
│   ├── :feature:extensions-settings    ← Extensions management screen (ADR-016)
│   ├── :feature:settings               ← Settings screens (with simple mode, ADR-018)
│   ├── :feature:trackers               ← Tracker login/binding screens
│   └── :feature:backup                 ← Backup/restore UI
└── :i18n                               ← Moko Resources strings (if chosen)
```

## Dependency rules (invariant)

```
:app  →  :feature:*  →  :core:*  (interfaces only)
                     →  :data:*   (impls, wired via DI)
:feature:*  ↛  :feature:*  (NEVER — cross-feature goes through :core)
:data:*  →  :core:* (to implement interfaces)
```

- **`:core:source-api`** is KMP (`commonMain` + `androidMain`) — the extension contract.
- **`:core:designsystem`** holds the theme + components from `DESIGN_LANGUAGE/`.
- **`:core:episode-metadata`** is the pluggable module (ADR-022) — TMDB can be added later.
- **`:core:anilist`** is the AniList data layer (ADR-010) — dual-role: data source + tracker.
- **`:feature:video-resolver`** is the extensible resolver module the owner wants.

## What's pending (open decisions)

This draft assumes:
- [ ] DI framework (Hilt / Koin / Metro / Injekt) — affects how modules wire up.
- [ ] Persistence (SQLDelight / Room) — affects `:core:database` structure.
- [ ] Compose-only vs mixed Views — watch page + player need `AndroidView` for MPV.
- [ ] Min/target SDK.
- [ ] Localization (Moko Resources / stock).
- [ ] Backup format.

Once these are answered, this draft becomes `ARCHITECTURE.md`.

## See also

- `DOCS/04-design-decisions.md` — ADRs 009–022 (the vision).
- `DESIGN_LANGUAGE/` — the UI spec each feature follows.
- `PLANNING/01-feature-specs/episode-metadata-module.md` — the episode metadata module spec.
- `RULES/ai-agent-rules.md` §4 (modularity), §8 (future-proofing).
