# Session Handoff — 2026-07-27-2047 — Unified Extension Details Page Implementation

## What I did

Implemented the **Unified Extension Details Page** per the approved plan
(`_REFERENCES/ANIMIRU_REFERENCE/DOCUMENTATION/05-IMPLEMENTATION_PLAN.md`),
on branch `feature/extension-details-page` (based on `feature/voyager-navigation`
@ `5b3f833`).

The app now has ONE details page (`AnimeDetailScreen`) that renders data from
either AniList OR an extension via a pluggable `AnimeDetailsProvider` translation
layer. A three-dot menu switches the data source in-place. The old
`ExtensionDetailScreen` is removed.

## Commits (in order)

1. `chore: add Animiru reference folder structure` — setup (from the planning phase).
2. `docs: add extension details page analysis, data mapping, and implementation plan` — the 6 analysis/plan docs (from the planning phase).
3. `feat(details): unified data model + provider interface in :core:common` — `UnifiedAnime`, `DataSource`, `DetailsRequest`, `DetailsResult`, `AnimeDetailsProvider`, `AnimeDetailsProviderRegistry`, `HtmlToPlainText`, status mappers.
4. `feat(details): AniList + Extension AnimeDetailsProvider implementations` — `AniListDetailsProvider` (`:data:anime`), `ExtensionDetailsProvider` (`:data:extension`), `AniListAnimeMapper` (`:core:anilist`), `SAnimeMapper` (`:data:extension`). Build deps added to `:data:anime` + `:data:extension`.
5. `feat(details): Koin wiring for AnimeDetailsProvider registry` — `DetailsModule` + registered in `App.kt`.
6. `feat(details): unified ViewModel + UI refactor + three-dot source-switcher menu` — `AnimeDetailViewModel` rewritten (provider-based + `switchDataSource`/`switchExtension`), `AnimeDetailScreen`/`DetailBanner`/`DetailContent`/`DetailInfo` consume `UnifiedAnime`, `SourceSwitcherMenu` created, `SourceSwitcherDialog.kt` deleted (dead code).
7. `feat(details): repoint navigation to unified screen + remove ExtensionDetailScreen` — `AnimeDetailDestination` updated, `ExtensionDetailDestination` → `ExtensionAnimeDetailDestination` (same screen, extension mode), `AppController.onGoWithoutLinking`/`onLinked` updated, `ExtensionDetailScreen.kt` + `ExtensionDetailViewModel.kt` deleted.

## What's done

- ✅ `AnimeDetailsProvider` interface + `UnifiedAnime` model in `:core:common`
- ✅ `AniListDetailsProvider` in `:data:anime` (wraps the existing 3-stage load)
- ✅ `ExtensionDetailsProvider` in `:data:extension` (`getAnimeDetails` enrichment + AniList merge + Palette cover-color extraction)
- ✅ Koin multi-binding (`DetailsModule` + `App.kt`)
- ✅ `AnimeDetailViewModel` refactored (provider-based + `currentDataSource` + `switchDataSource`)
- ✅ `AnimeDetailScreen`/`DetailBanner`/`DetailContent`/`DetailInfo` consume `UnifiedAnime` with conditional rendering
- ✅ `SourceSwitcherMenu` (three-dot dropdown — replaces the no-op stub)
- ✅ Navigation repointed (`ExtensionAnimeDetailDestination` replaces `ExtensionDetailDestination`)
- ✅ `ExtensionDetailScreen.kt` + `ExtensionDetailViewModel.kt` + `SourceSwitcherDialog.kt` removed
- ✅ Library visibility fix (unlinked extension anime keyed by `sourceId+url` via `getBySourceAndUrl` — visible in library)
- ✅ Phase 9 adaptive-theme block preserved (`coverColorHex` flows from `UnifiedAnime`)
- ✅ ADR-039 drafted (`06-ADR-unified-details-page.md`)

## What's NOT done / follow-ups

- **No CI build verification yet.** The implementation is committed but `./gradlew :app:assembleDebug` was NOT run locally (per ADR-003 — CI-only builds). The next agent should trigger CI via `workflow_dispatch` on `feature/extension-details-page` and fix any compile errors. Likely areas to check:
  - Import cleanup (some unused imports may remain).
  - The `AnimeDetailViewModel.observeLibraryState` for unlinked anime uses `animeState.collect` which never completes — verify it doesn't block other coroutines.
  - `EpisodesSection` signature unchanged — verify it still compiles with the `EpisodeState` type (unchanged).
  - The `ExtensionLinkingSheet` "go without linking" path now pushes `ExtensionAnimeDetailDestination` — verify the linking sheet's `onGoWithoutLinking` callback signature matches.
- **Recommendations/Relations sections** — deferred per owner Q5 (out of scope).
- **`UnifiedStatus.HIATUS`** — added per owner Q4, but the DB `AnimeStatus` int mapping in `saveAnimeToLibrary` maps it to `ON_HIATUS` (6). Verify the `animes.sq` status column accepts this.
- **Palette extraction** — uses `PaletteExtraction.extractFromBitmap` (Phase 9 implemented) with an OkHttp-loaded bitmap. When Phase 9's `extractCoverColor(url)` is fully implemented, the provider could switch to it (cleaner). Currently the OkHttp + BitmapFactory approach works and needs no Coil dep.
- **Session handoff discipline** — this is the first handoff note in a while; the project had a 4+ day handoff deficit (per ANALYSIS-E). Future agents should continue writing these.

## Key files

- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/` — `UnifiedAnime`, `DataSource`, `DetailsRequest`, `AnimeDetailsProvider`, `AnimeDetailsProviderRegistry`, `HtmlToPlainText`
- `core/anilist/src/main/java/app/confused/anikuta/core/anilist/details/AniListAnimeMapper.kt` — `AniListAnime.toUnifiedAnime()`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/details/AniListDetailsProvider.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/details/` — `ExtensionDetailsProvider.kt`, `SAnimeMapper.kt`
- `app/src/main/java/app/confused/anikuta/di/DetailsModule.kt` — Koin wiring
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/` — `AnimeDetailViewModel.kt` (rewritten), `AnimeDetailScreen.kt` (rewritten), `DetailBanner.kt`/`DetailContent.kt`/`DetailInfo.kt` (UnifiedAnime), `SourceSwitcherMenu.kt` (new)
- `app/src/main/java/app/confused/anikuta/navigation/Destinations.kt` — `AnimeDetailDestination` + `ExtensionAnimeDetailDestination`
- `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` — `pushExtensionDetail` + `onGoWithoutLinking` + `onLinked`
- `_REFERENCES/ANIMIRU_REFERENCE/DOCUMENTATION/06-ADR-unified-details-page.md` — ADR-039

## Blockers / next steps

1. **Trigger CI** on `feature/extension-details-page` and fix compile errors.
2. **Regression test** on a linked AniList anime (verify no regression vs. pre-refactor).
3. **Test** an unlinked extension anime (verify it renders on the unified page + appears in the library after saving).
4. **Test** the three-dot menu: switch AniList ↔ Extension in-place; switch extension via ManualSearchSheet.
5. **Coordinate with Phase 9 agent** — confirm `PaletteExtraction` API compatibility (the provider uses `extractFromBitmap`, not the stub `extractCoverColor`).

## Pointers

- Plan: `_REFERENCES/ANIMIRU_REFERENCE/DOCUMENTATION/05-IMPLEMENTATION_PLAN.md`
- ADR: `_REFERENCES/ANIMIRU_REFERENCE/DOCUMENTATION/06-ADR-unified-details-page.md`
- Worklog: `/home/z/my-project/worklog.md` (see `EXT-DETAILS-IMPL` entry)
