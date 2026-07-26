# PROJECT_STARTUP.md — Starting Instructions for AI Agents

> **Read this file FIRST when you start working on ANIKUTA.**
> It gives you the exact steps to get oriented, understand the project, and begin
> working safely. Follow it in order.

---

## Step 1: Read the mandatory files (in this order)

1. **[`./START_HERE.md`](./START_HERE.md)** — the 60-second brief + current state.
2. **[`../ARCHITECTURE.md`](../ARCHITECTURE.md)** — the single source of truth.
3. **[`../RULES/ai-agent-rules.md`](../RULES/ai-agent-rules.md)** — the 14-section ruleset.
4. **[`../RULES/project-conventions.md`](../RULES/project-conventions.md)** — ANIKUTA-specific rules.
5. **[`../RULES/notifications.md`](../RULES/notifications.md)** — ntfy.sh notification format.
6. **[`../DOCS/04-design-decisions.md`](../DOCS/04-design-decisions.md)** — all ADRs.
7. **[`../DESIGN_LANGUAGE/`](../DESIGN_LANGUAGE/)** — UI/UX spec (principles + components first).
8. **[`../DOCS/episode-settings-architecture.md`](../DOCS/episode-settings-architecture.md)** — episode row design.

## Step 2: Understand the project (the 2-minute brief)

**ANIKUTA** is an anime-first Android app (manga comes later) that combines:
1. An **extension-based** content system (Aniyomi-compatible) — extensions are external
   APKs that implement the `:source-api` contract.
2. **AniList as a co-primary data source** (not just a tracker) — for discovery,
   metadata, and personalization.
3. A **custom design language** (M3-inspired but unique — primary #B1F256 lime green,
   RobotoFamily font).
4. **Full feature set**: browse, search, details, watch (MPV), library, history,
   updates, profile, trackers, backup/restore, downloads, episode settings.

**It is NOT a fork of Aniyomi.** The Aniyomi source is a read-only reference at
`_REFERENCES/ANIYOMI_REFRENCE/`. All new code goes in `ANIKUTA_PROJECT/ANIKUTA/`.

## Step 3: Understand the module structure

The project has **41 Gradle modules**. Read `ANIKUTA_PROJECT/ANIKUTA/settings.gradle.kts`
for the full list. Key modules:

### Core modules (`:core:*`)
| Module | Purpose |
|---|---|
| `:core:common` | Domain models (Anime, Episode, Category), repository interfaces |
| `:core:designsystem` | Compose UI components, theme (#B1F256), RobotoFamily, CollapsingHeader |
| `:core:preferences` | PreferenceStore (SharedPreferences-backed, reactive via `Preference.changes()`) |
| `:core:database` | SQLDelight schemas (animes.sq, episodes.sq, animehistory.sq, animetrack.sq, etc.) |
| `:core:anilist` | AniList GraphQL API + LocalAniListCache (persistent, 24h TTL) + AniListRateLimiter |
| `:core:player` | MPV player, WatchProgressStore, PlayerEpisodePreferences, EpisodeSwitchingOverlay |
| `:core:episode-metadata` | Episode metadata sources (Jikan/MAL, Anikage.cc, AniList) + EpisodeMetadataCache (persistent) |
| `:core:source-api` | SEpisode, SAnime, Video, Hoster, AnimeSource interfaces |
| `:core:tracker` | AniList + MAL OAuth tracking, TrackSyncManager, StatsCalculator |
| `:core:update-checker` | Reusable new-episode checker (manual checking, future: WorkManager) |
| `:core:backup` | BackupManager, BackupProvider, ANIKUTA + Aniyomi format, auto-backup |
| `:core:download` | DownloadManager interface, DefaultDownloadManager, queue, preferences |
| `:core:network` | NetworkHelper, OkHttpClient |
| `:core:notification` | (Stub — not yet implemented) |

### Feature modules (`:feature:*`)
| Module | Purpose |
|---|---|
| `:feature:browse` | Homepage (trending grid, daily cache, pull-to-refresh) |
| `:feature:search` | Search (AniList + extensions, linking flow, filters, recent searches) |
| `:feature:anime-details` | Details page + extension-only details page + EpisodesSection |
| `:feature:watch` | Watch/player page (MPV, episode list, metadata, controls) |
| `:feature:library` | Library (grid/list, categories, badges, settings sheet) |
| `:feature:episode-settings` | Full-page episode display/layout/metadata settings |
| `:feature:history` | Watch history (day-grouped list) |
| `:feature:updates` | Updates + Schedule (list + calendar) |
| `:feature:my` | Profile (stats, charts, behind-status, reset) |
| `:feature:trackers` | Trackers settings (login/logout) |
| `:feature:backup` | Backup settings screen + Aniyomi restore flow |
| `:feature:download` | Downloads screen (queue + downloaded files) |
| `:feature:video-resolver` | Smart video resolver (structured + raw strategies) |
| `:feature:extensions-settings` | Extension manager + repo settings |

### Data modules (`:data:*`)
| Module | Purpose |
|---|---|
| `:data:anime` | AnimeRepositoryImpl, EpisodeRepositoryImpl, CategoryRepositoryImpl |
| `:data:extension` | AnimeExtensionManager, SourceMatcher, SourceLinkStore, ExtensionLinkStore |
| `:data:history` | (Stub) |
| `:data:tracker` | (Stub) |
| `:data:manga` | (Stub — manga comes later) |

## Step 4: Understand the build system

Read `ANIKUTA_PROJECT/ANIKUTA/buildSrc/src/main/kotlin/` for convention plugins:
- `anikuta.library` — base Android library (no Compose)
- `anikuta.library.compose` — adds Compose (compiler + bom + material3 + icons-extended)
- `anikuta.android.application` — the app module

CI: `.github/workflows/ci.yml` — APKs built EXCLUSIVELY via GitHub Actions.
- Push to `main` → auto-trigger CI.
- Feature branches → manual `workflow_dispatch` trigger.
- No local builds (ADR-003).

## Step 5: Understand the navigation

`AnikutaRoot.kt` uses **Voyager** navigation (ADR-037): a single root `Navigator`
with `Screen` classes in `navigation/Destinations.kt`. The `AppController` (Koin
singleton in `navigation/AppController.kt`) holds shared state + business logic.

**Key files:**
- `navigation/AnikutaRoot.kt` — the root composable: `Navigator` + bottom nav + overlay sheets.
- `navigation/Destinations.kt` — all Voyager `Screen` classes (tabs + pushed screens).
- `navigation/AppController.kt` — central state holder + business logic (resolve, download, OAuth, linking).
- `navigation/MoreScreens.kt` — `MoreScreen` + `SettingsScreen` composables (extracted from the old MainActivity).
- `navigation/NavModule.kt` — Koin module for `AppController` + shared `AniListApi`.

**Tab navigation:** The 4 bottom-nav tabs (Home, Library, Search, More) are the root
screens. Switching tabs calls `navigator.replace(newTab)`. Pushed screens (detail,
watch, settings, etc.) push on top. The bottom nav is hidden when the stack depth > 1.

**Overlays:** The resolver sheet, linking sheet, and download picker sheet are modal
overlays rendered at the root level (in `AppOverlays`), driven by `AppController`
state. They are NOT navigated screens.

**New screens:** Create a `Screen` class (data class or object) in `Destinations.kt`
with a `@Composable Content()` that calls the feature composable. Push it with
`navigator.push(YourDestination)`. Back is handled automatically by Voyager.

## Step 6: Understand Koin DI

Modules in `app/src/main/java/.../di/`:
- `databaseModule`, `repositoryModule`, `playerModule`, `preferenceModule`
- `extensionModule`, `searchModule`, `episodeMetadataModule`
- `trackerModule`, `updateCheckerModule`, `backupModule`, `downloadAppModule`
- `historyModule`, `updatesModule`, `myModule`, `trackersModule`
- `backupFeatureModule`, `aniyomiRestoreModule`

Use `koinInject<>()` in composables, `get<>()` in module constructors params.

## Step 7: Understand the design language

- **Primary color**: `MaterialTheme.colorScheme.primary` = #B1F256 (lime green)
- **Font**: `RobotoFamily` for ALL text
- **Card backgrounds**: `surfaceVariant.copy(alpha = 0.4f)` (same as More screen buttons)
- **Pills/badges**: `outlineVariant` surface, `RoundedCornerShape(6.dp)`, `fontSize = 9.sp`, `lineHeight = 11.sp`
- **Section headers**: RobotoFamily ExtraBold 11sp, uppercase, `onSurfaceVariant`, letterSpacing 0.06.sp
- **Switches**: Material3 `Switch` (NOT custom toggle pills)
- **Bottom sheets**: `dragHandle = null`
- **No indigo or blue colors.**
- Use `CollapsingHeader` for page titles.

Reference screens for UI design:
- `feature/updates/.../UpdatesScreen.kt` — tab strip, pull-to-refresh, cards
- `feature/my/.../ProfileScreen.kt` — settings sections, charts, toggle rows
- `feature/library/.../LibraryScreen.kt` — grid/list, categories, settings sheet

## Step 8: Understand key data flows

### Episode list persistence
- Episodes are saved to SQLDelight DB after fetching from the source (`EpisodeRepository.upsert`)
- On re-open: loaded from DB instantly (no network, works offline)
- Source links persisted via `SourceLinkStore` (sourceId + SAnime URL + title)
- Episode metadata persisted via `EpisodeMetadataCache` (PreferenceStore, 24h TTL)

### Video resolution
- `ResolverService` calls `getHosterList()` → checks `hoster.videoList` first (non-lazy)
- Falls back to `getVideoList(hoster)` for lazy hosters, then `getVideoList(episode)` for old API
- `VideoTitleParser` parses titles into Server → Audio → Quality hierarchy
- `ResolverStrategyPicker` auto-selects structured vs raw strategy
- Host names propagated from `Hoster.hosterName` to the parser

### Extension-only details
- For anime not on AniList — uses `SAnime` data directly
- Same UI as normal details page (DetailBanner, SynopsisSection, EpisodesSection)
- "A" button opens the AniList linking sheet as an overlay
- Saves to library with `anilistId = null`

## Step 9: Clone and start working

```bash
git clone -b main "https://<PAT>@github.com/testplay-byte/ANI_KUTA_NEW.git" anikuta
cd anikuta
git config user.email "anikuta-bot@users.noreply.github.com"
git config user.name "anikuta-bot"
git checkout -b feature/your-feature
```

To build: push to your feature branch and trigger CI:
```bash
curl -X POST -H "Authorization: token <PAT>" -H "Accept: application/vnd.github+json" \
"https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/workflows/ci.yml/dispatches" \
-d '{"ref":"feature/your-feature"}'
```

To send a notification: `curl -d "message" https://ntfy.sh/TASKISDONE`
