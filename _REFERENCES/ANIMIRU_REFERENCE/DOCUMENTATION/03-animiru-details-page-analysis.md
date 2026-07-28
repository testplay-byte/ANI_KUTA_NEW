# Animiru — Extension-Sourced Anime Details Page Analysis

> Task ID: **EXT-DETAILS-TASK4**
> Scope: Animiru reference codebase at `_REFERENCES/ANIMIRU_REFERENCE/animiru-src/` (Aniyomi fork, `eu.kanade.tachiyomi` package, Voyager-based navigation).
> Purpose: Learn how Animiru maps extension `SAnime` data to its details-page UI, whether it has a translation layer, whether it supports extension switching, and what ANIKUTA should adopt or avoid when building its own unified `AnimeDetailScreen` with a pluggable translation layer.

All paths below are prefixed with `_REFERENCES/ANIMIRU_REFERENCE/animiru-src/` (the local-only clone). Line numbers cite the exact location at the time of analysis. ANIKUTA's package is `app.confused.anikuta`; Animiru's is `eu.kanade.tachiyomi` / `tachiyomi.*` / `mihon.*` / `aniyomi.*` — do not confuse the two.

---

## 0. Module layout (top-level)

`settings.gradle.kts:33-50` declares 14 modules:

| Module | Role |
|---|---|
| `:app` | Activities, Voyager screens, Voyager `ScreenModel`s (ViewModels), DI graph, Compose presentation under `eu.kanade.presentation.*`. |
| `:core:common` | Preferences, network helpers, IO utils, security. |
| `:core:archive` | Archive / backup zip helpers. |
| `:core-metadata` | `AnimeDetails` / `EpisodeDetails` data classes (legacy; only used by `LocalSource`). |
| `:data` | SQLDelight schema + repository implementations (`AnimeRepositoryImpl`, `AnimeMapper`, etc.). |
| `:domain` | Pure-Kotlin domain models + interactors (`Anime`, `Episode`, `Track`, `GetAnimeWithEpisodesAndSeasons`, `NetworkToLocalAnime`, …). |
| `:i18n`, `:i18n-aniyomi`, `:i18n-animiru` | Moko Resources string catalogs. |
| `:macrobenchmark` | Performance benchmarks. |
| `:presentation-core` | Shared Compose primitives (LoadingScreen, Scaffold wrappers). |
| `:presentation-widget` | Home-screen widget. |
| `:source-api` | The extension contract: `SAnime`, `SEpisode`, `AnimeSource`, `AnimeHttpSource`. **Same contract ANIKUTA uses** (binary-compat). |
| `:source-local` | Built-in "local source" implementation. |

`README.md:8` confirms Animiru is a fork of Aniyomi. The package layout mirrors upstream Mihon/Aniyomi exactly, with `// AY --> … <-- AY` (Aniyomi), `// AM --> … <-- AM` (Animiru), and `// AM (CUSTOM_INFORMATION) --> … <-- AM (CUSTOM_INFORMATION)` markers diff-commenting the fork's changes.

---

## 1. Architecture: how Animiru handles extension-sourced anime details

### 1.1 ONE unified details screen — the user-facing anime page

There is exactly **one** anime details screen: `AnimeScreen` (Voyager `Screen`).

- File: `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreen.kt:80-83`
  ```kotlin
  class AnimeScreen(
      private val animeId: Long,
      val fromSource: Boolean = false,
  ) : Screen(), AssistContentScreen
  ```
- Constructed everywhere a details page is opened:
  - Browse source: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt:237` — `navigator.push(AnimeScreen(it.id, true))`.
  - Global search: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt:75` — `navigator.push(AnimeScreen(it.id, true))`.
  - Library / recents / history / deep-link: similar `AnimeScreen(id)` pushes.
  - After migration completes: `app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSearchScreen.kt:81-83` — `navigator.push(AnimeScreen(dialog.target.id))` (NEW id, see §4).

The screen takes only an `animeId: Long`. It does NOT take a "source" or "from-extension-vs-tracker" parameter — the source is read from the persisted `Anime.source` column. There is no per-source variant of the screen.

### 1.2 ⚠ The `extension/details/` folder is NOT an anime details page

The task brief mentioned `animiru-src/app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details` — that folder is the **installed-extension management screen**, not a per-anime details page.

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt:16-18`:
  ```kotlin
  data class ExtensionDetailsScreen(
      private val pkgName: String,   // ← the extension APK package name, NOT an anime id
  ) : Screen()
  ```
- `ExtensionDetailsScreenModel.kt:35-44` loads an `Extension.Installed` by `pkgName` and exposes its bundled `sources` list.
- The screen lets the user: toggle individual sources on/off, clear cookies, uninstall the extension, toggle incognito mode (`ExtensionDetailsScreenModel.kt:100-139`).

This folder is **only relevant to ANIKUTA as a reference for an extension-management UI** (if ANIKUTA ever needs one). It is NOT a reference for the per-anime details page and should be ignored for the purposes of this analysis. Everything below concerns `AnimeScreen`.

### 1.3 ScreenModel (the ViewModel)

- File: `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:126-180` — class `AnimeScreenModel` extends Voyager `StateScreenModel<State>`. Constructed with just `(context, lifecycle, animeId, isFromSource)`; everything else is `Injekt.get()` (Injekt is the DI container, equivalent to Koin).
- State shape: `AnimeScreenModel.kt:1865-2033`:
  ```kotlin
  sealed interface State {
      data object Loading : State
      data class Success(
          val anime: Anime,
          val source: AnimeSource,
          val isFromSource: Boolean,
          val episodes: List<EpisodeList.Item>,
          val seasons: List<AnimeSeasonItem>,            // AY
          val availableScanlators: Set<String>,
          val excludedScanlators: Set<String>,
          val trackingCount: Int = 0,
          val hasLoggedInTrackers: Boolean = false,
          val isSyncingTrackers: Boolean = false,          // AM
          val isRefreshingData: Boolean = false,
          val dialog: Dialog? = null,
          val hasPromptedToAddBefore: Boolean = false,
          val hideMissingEpisodes: Boolean = false,
          val trackItems: List<TrackItem> = emptyList(),   // AY
          val nextAiringEpisode: Pair<Int, Long> = ...,
      ) : State
  }
  ```

### 1.4 Data flow: extension `SAnime` → DB `Anime` → screen

```
┌───────────────────────────────────────────────────────────────────────────┐
│ EXTENSION (classloader boundary)                                          │
│  • SAnime  (source-api: SAnime.kt)                                        │
│  • SEpisode (source-api: SEpisode.kt)                                     │
└───────────────────────────────────────────────────────────────────────────┘
                │  source.getSearchAnime() / source.getAnimeDetails()
                │  source.getEpisodeList()
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ FIRST-SEEN MAPPING  (SAnime → Anime)                                      │
│  mihon/domain/anime/model/SAnime.kt:6-29  fun SAnime.toDomainAnime(sid)   │
│  → NetworkToLocalAnime.kt:14-16  →  AnimeRepositoryImpl.insertNetworkAnime │
│  → SQLDelight  animes.sq  insertNetworkAnime                               │
└───────────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ LOCAL DB (SQLDelight)  — animes table                                     │
│  data/src/main/sqldelight/tachiyomi/data/animes.sq:7-41                   │
│  Columns: source, url, title, author, artist, description, genre,         │
│           status, thumbnail_url, background_url, update_strategy,          │
│           fetch_type, season_number, initialized,                          │
│           favorite, last_update, viewer, episode_flags, cover_last_modified│
│           date_added, notes, …                                             │
│  Domain object:  domain/src/main/java/tachiyomi/domain/anime/model/Anime.kt│
│    Has "og*" original fields + customAnimeInfo overlay (see §2)            │
└───────────────────────────────────────────────────────────────────────────┘
                │  AnimeRepository.getAnimeByIdAsFlow(animeId)
                │  EpisodeRepository.getEpisodeByAnimeIdAsFlow(animeId)
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ DOMAIN INTERACTOR (one-shot + Flow)                                       │
│  GetAnimeWithEpisodesAndSeasons.kt:16-29  Triple<Anime, List<Episode>, …> │
└───────────────────────────────────────────────────────────────────────────┘
                │  combine(animeFlow, downloadCache.changes, downloadManager.queue)
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ SCREENMODEL  AnimeScreenModel.kt:237-256 (subscribe) + :282-354 (init)    │
│  • Also calls fetchAnimeFromSource() if !initialized  → refresh from ext. │
│  • Also calls fetchEpisodesAndSeasonsFromSource() if empty                │
│  • Also calls syncTrackers() for EnhancedTrackers                         │
└───────────────────────────────────────────────────────────────────────────┘
                │  state.value as State.Success
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ COMPOSE UI  (presentation/anime/AnimeScreen.kt)                           │
│  LazyVerticalGrid items: AnimeInfoBox → AnimeActionRow → Description →    │
│                          EpisodeHeader → [Seasons | Episodes]             │
└───────────────────────────────────────────────────────────────────────────┘
```

Init / refresh logic (key locations):
- `AnimeScreenModel.kt:237-256` — subscribe to `getAnimeAndEpisodesAndSeasons.subscribe(animeId, applyScanlatorFilter = true)` combined with `downloadCache.changes` and `downloadManager.queueState`, pushed into `State.Success`.
- `AnimeScreenModel.kt:282-354` — one-shot init: reads the persisted anime + episodes, sets default flags if not favorite, and dispatches 3 parallel tasks: `syncTrackers()`, `fetchAnimeFromSource()` (only if `!anime.initialized`), `fetchEpisodesAndSeasonsFromSource()` (only if episodes/seasons list is empty for the expected `fetchType`).
- `AnimeScreenModel.kt:417-433` — `fetchAnimeFromSource()`:
  ```kotlin
  val networkAnime = state.source.getAnimeDetails(state.anime.toSAnime())
  updateAnime.awaitUpdateFromSource(state.anime, networkAnime, manualFetch)
  ```
  i.e. round-trips `Anime → SAnime` via `toSAnime()`, calls the extension, then writes the result back via `UpdateAnime.awaitUpdateFromSource` (see §2.3).

**Net**: Animiru reads its details-page data from a single local `Anime` row. That row was *originally* populated from `SAnime` at first-seen time and is *refreshed* from `SAnime` on demand. There is no separate "extension view" or "tracker view" of the page — the page is the local DB row, full stop.

---

## 2. Data translation: SAnime → Anime mapping

### 2.1 Animiru does NOT have a rich "translation layer"

Honest assessment: Animiru has **two small focused mapper functions** and a refresh interactor, not a translation layer in the ANIKUTA sense. The `Anime` domain object IS the unified format; `SAnime` is the only input shape on the extension side; `Track` (AniList/MAL/etc.) is a separate row, not a merged-in alternative source. There is no pluggable interface for "this anime's details come from AniList today and from an extension tomorrow."

The closest thing Animiru has to a "translation layer" is the **`og*` field + `CustomAnimeInfo` overlay** on the `Anime` model itself (§2.4) — and that's a user-edit-override mechanism, not a multi-source adapter.

### 2.2 The first-seen mapper: `SAnime.toDomainAnime(sourceId)`

File: `domain/src/main/java/mihon/domain/anime/model/SAnime.kt:6-29`.

Used by `SearchScreenModel.kt:170` (and the analogous `BrowseSourceScreenModel`) when a search-result `SAnime` first needs to be persisted so the user can tap into `AnimeScreen(animeId)`.

| `SAnime` field (extension contract) | `Anime` field (domain) | Coalescing / transform |
|---|---|---|
| `url` (`SAnime.kt:9`) | `url` (`Anime.kt:30`) | Direct copy. |
| `title` (`SAnime.kt:11`) | `ogTitle` (`Anime.kt:32`) | Direct copy. **Note**: writes to `ogTitle`, NOT to a hypothetical `title` field — see §2.4. |
| `artist` (`SAnime.kt:13`) | `ogArtist` (`Anime.kt:33`) | Direct copy (nullable). |
| `author` (`SAnime.kt:15`) | `ogAuthor` (`Anime.kt:34`) | Direct copy (nullable). |
| `description` (`SAnime.kt:17`) | `ogDescription` (`Anime.kt:35`) | Direct copy (nullable). |
| `genre` (`SAnime.kt:19`) — comma-separated string | `ogGenre: List<String>?` (`Anime.kt:36`) | `SAnime.getGenres()` (`SAnime.kt:37-40`) splits on `", "`, trims, drops blanks, dedupes. |
| `status` (`SAnime.kt:21`) — `Int` (0–6) | `ogStatus: Long` (`Anime.kt:37`) | `.toLong()`. |
| `thumbnail_url` (`SAnime.kt:23`) | `thumbnailUrl` (`Anime.kt:39`) | Direct copy (nullable). |
| `background_url` (`SAnime.kt:26`, AY) | `backgroundUrl` (`Anime.kt:41`) | Direct copy (nullable). |
| `update_strategy` (`SAnime.kt:29`) | `updateStrategy` (`Anime.kt:43`) | Direct copy. |
| `fetch_type` (`SAnime.kt:31`, AY) | `fetchType` (`Anime.kt:50`) | Direct copy. |
| `season_number` (`SAnime.kt:33`, AY) | `seasonNumber` (`Anime.kt:52`) | Direct copy. |
| `initialized` (`SAnime.kt:35`) | `initialized` (`Anime.kt:44`) | Direct copy. |
| — (no equivalent) | `source = sourceId` | Set from the calling source's id, NOT from SAnime. |
| — | everything else (`favorite`, `lastUpdate`, `nextUpdate`, `viewerFlags`, `episodeFlags`, `coverLastModified`, `dateAdded`, `notes`, `seasonFlags`, …) | Defaults from `Anime.create()` (`Anime.kt:361-403`). |

### 2.3 The refresh mapper: `Anime.copyFrom(other: SAnime)` + `UpdateAnime.awaitUpdateFromSource`

File: `app/src/main/java/eu/kanade/domain/anime/model/Anime.kt:70-106`.

Called from:
- `app/src/main/java/eu/kanade/tachiyomi/data/library/MetadataUpdateJob.kt:136` (background library update).
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/AnimeRestorer.kt:121, 125` (backup restore — note: this overload takes `Anime`, not `SAnime`).
- The live details-page refresh path goes through `UpdateAnime.awaitUpdateFromSource` instead (doesn't call `copyFrom` directly):

`UpdateAnime.kt:33-123` — `awaitUpdateFromSource(localAnime, remoteAnime: SAnime, manualFetch, …)`:
1. Computes `title` to write: only if `remoteTitle.isNotEmpty() && (!localAnime.favorite || libraryPreferences.updateAnimeTitles.get())` (`UpdateAnime.kt:51-56`). i.e. **does NOT overwrite the title for favorited anime unless the user opted in** — preserves the user's chosen title.
2. Computes `coverLastModified` / `backgroundLastModified` refresh logic — avoids clobbering custom covers/backgrounds (`UpdateAnime.kt:58-90`).
3. Builds an `AnimeUpdate` partial DTO with: `title` (maybe null), `author`, `artist`, `description`, `genre = remoteAnime.getGenres()`, `thumbnailUrl`, `backgroundUrl`, `status = remoteAnime.status.toLong()`, `updateStrategy`, `initialized = true` (`UpdateAnime.kt:98-117`).
4. Calls `animeRepository.update(animeUpdate)` → `AnimeRepositoryImpl.partialUpdate` → `animesQueries.update` (`AnimeRepositoryImpl.kt:204-247`).

### 2.4 The `og*` + `CustomAnimeInfo` overlay — the closest thing to a "translation layer"

`domain/src/main/java/tachiyomi/domain/anime/model/Anime.kt:31-82`:

```kotlin
val ogTitle: String,            // "original" — extension-sourced
val ogArtist: String?,
val ogAuthor: String?,
val ogDescription: String?,
val ogGenre: List<String>?,
val ogStatus: Long,
// ...
private val customAnimeInfo = if (favorite) getCustomAnimeInfo.get(id) else null   // line 59-63

val title: String        get() = customAnimeInfo?.title ?: ogTitle                 // line 65-66
val author: String?      get() = customAnimeInfo?.author ?: ogAuthor               // line 68-69
val artist: String?      get() = customAnimeInfo?.artist ?: ogArtist               // line 71-72
val description: String? get() = customAnimeInfo?.description ?: ogDescription     // line 74-75
val genre: List<String>? get() = customAnimeInfo?.genre ?: ogGenre                 // line 77-78
val status: Long         get() = customAnimeInfo?.status ?: ogStatus               // line 80-81
```

`CustomAnimeInfo` (`domain/src/main/java/tachiyomi/domain/anime/model/CustomAnimeInfo.kt`) is a user-editable override blob persisted in a separate SQLDelight table. The "Edit Info" overflow menu (`AnimeToolbar.kt:163-172`, `EditAnimeDialog.kt`, `AnimeScreenModel.kt:435-498`) writes to it.

**What this is**: a 2-layer overlay (original + user override) for ONE source of truth (the extension). The details-screen Composables read `anime.title`, `anime.author`, etc. — they don't know or care whether the value is the og-original or the user override.

**What this is NOT**: a multi-source adapter. There is no `customAnimeInfo.fromAniList`, no "switch source" toggle. If the user wants AniList metadata, they have to add a `Track` binding (see §2.5) and view it in a separate dialog.

### 2.5 Tracker metadata (AniList/MAL/Kitsu/Shikimori/Bangumi/Simkl/Jellyfin) is kept SEPARATE

`domain/src/main/java/tachiyomi/domain/track/model/Track.kt:5-20`:

```kotlin
data class Track(
    val id: Long,
    val animeId: Long,        // ← FK to Anime
    val trackerId: Long,      // ← which tracker (AniList, MAL, …)
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,        // ← tracker-side title (NOT merged into Anime.title)
    val lastEpisodeSeen: Double,
    val totalEpisodes: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
    val private: Boolean,
)
```

- Trackers are listed in `app/src/main/java/eu/kanade/tachiyomi/data/track/` (`myanimelist/`, `anilist/`, `kitsu/`, `shikimori/`, `bangumi/`, `simkl/`, `jellyfin/`).
- Each tracker implements `Tracker` / `BaseTracker` (`app/src/main/java/eu/kanade/tachiyomi/data/track/Tracker.kt`, `BaseTracker.kt`).
- `EnhancedTracker` (`app/src/main/java/eu/kanade/tachiyomi/data/track/EnhancedTracker.kt`) is the marker for trackers that auto-bind to a specific extension source (e.g., a Shikimori tracker that activates when the anime comes from a particular source). It still writes a `Track` row — it does NOT merge into `Anime`.
- The details screen surfaces trackers via a separate bottom-sheet dialog `TrackInfoDialogHomeScreen` (`AnimeScreen.kt:348-361`), reached from the "Track" action button in `AnimeActionRow`.
- Tracker `score`/`status`/`lastEpisodeSeen` are NOT shown on the main details page; they live in the track sheet.

**Honest takeaway**: Animiru's main details page is extension-data-only. AniList/MAL are trackers (a secondary concept), shown in a modal sheet, not as an alternative source for the page itself. This is the OPPOSITE of ANIKUTA's vision where AniList can be a primary source for the page.

---

## 3. UI implementation: what the details page shows

### 3.1 Top-level layout

`app/src/main/java/eu/kanade/presentation/anime/AnimeScreen.kt:104-178` — `AnimeScreen` Composable. Branches on `isTabletUi()` to `AnimeScreenSmallImpl` (single-column LazyVerticalGrid) or `AnimeScreenLargeImpl` (two-pane). Both render the same items, just laid out differently.

The small-grid item sequence (`AnimeScreen.kt:522-690`):

| # | Item key (`AnimeScreenItem.*`) | Composable | What it shows |
|---|---|---|---|
| 1 | `INFO_BOX` (`:523-542`) | `AnimeInfoBox` (`AnimeInfoHeader.kt:114-176`) | Blurred backdrop (cover or `background_url`, blurred 4dp, alpha 0.2, gradient fade to `background`); cover thumbnail (clickable → full-image dialog); title (or `unknown_title` placeholder); author (or `unknown_author`); artist (hidden if blank or == author); status icon + label; source name (with warning icon if `StubSource`). |
| 2 | `ACTION_ROW` (`:544-569`) | `AnimeActionRow` (`AnimeInfoHeader.kt:178-274`) | 4 action buttons: Add-to-Library / In-Library (heart), Next-update-interval (hourglass, hidden if `onEditIntervalClicked==null`), Track (sync icon, hidden if `onTrackingClicked==null`, shows progress spinner if `isSyncingTrackers`), WebView (globe, hidden if non-http source). Long-press on Add-to-Library → edit categories. |
| 3 | `DESCRIPTION_WITH_TAG` (`:571-590`) | `ExpandableAnimeDescription` (`AnimeInfoHeader.kt:276-…`) | Description (markdown-rendered via `descriptionAnnotator`, `description_placeholder` if blank — **NOT hidden**); user notes (inline); genre tags as `FlowRow` of `TagsChip` (each chip → tap = global search by tag, long-press = copy to clipboard). Tags section is hidden if `tags.isNullOrEmpty()` (`AnimeInfoHeader.kt:305`). |
| 4 | `EPISODE_HEADER` (`:592-621`) | `ItemHeader` | Filter button (with active-filter tint) + episode-or-season count + missing-episode count. Switches label per `anime.fetchType`. |
| 5a | `AIRING_TIME` (`:639-666`, only if `state.airingTime > 0L && showNextEpisodeAirTime && status != COMPLETED`) | `NextEpisodeAiringListItem` | Live countdown to next episode airing (per-second tick via `LaunchedEffect`). |
| 5b | episodes (`sharedEpisodeItems`, `AnimeScreen.kt:1156-…`) OR seasons (`sharedSeasons`, `AnimeScreen.kt:1132-1155`) | `AnimeEpisodeListItem` / `AnimeSeasonListItem` | The episode/season list with download state, seen/bookmark/fillermark indicators, scanlator, file size (optional, AM), swipe actions. Switches between the two based on `anime.fetchType`. |

Plus a `FloatingActionButton` "Continue Watching" (`AnimeScreen.kt:135`, `onContinueWatching`) and a top `AnimeToolbar`.

### 3.2 Top app bar (`AnimeToolbar`)

File: `app/src/main/java/eu/kanade/presentation/anime/components/AnimeToolbar.kt:31-196`.

Top-row icon actions (always visible, when applicable):
- **Download** dropdown (icon: `Icons.Outlined.Download`) — only if `onClickDownload != null` (i.e., source isn't local/stub AND `fetchType == Episodes`).
- **Filter** (icon: `Icons.Outlined.FilterList`, tinted when `hasFilters`) — opens `EpisodeSettingsDialog` / `SeasonSettingsDialog`.

Overflow menu items (in order, when applicable):
| Label (string resource) | Condition | Action |
|---|---|---|
| `action_change_intro_length` (AY) | `onSkipIntroClicked != null` (favorite + Episodes fetchType) | `SetIntervalDialog`-like flow for skip-intro length. |
| `action_webview_refresh` | always | `screenModel::fetchAllFromSource` — full refresh from source. |
| `action_edit_categories` | `onClickEditCategory != null` (favorite) | `ChangeCategoryDialog`. |
| `action_migrate` | `onClickMigrate != null` (favorite) | `navigator.push(MigrationConfigScreen(successState.anime.id))` — **see §4**. |
| `action_share` | `onClickShare != null` (http source) | Share intent with `source.getAnimeUrl(...)`. |
| `action_edit_info` (AM) | `onClickEditInfo != null` (always) | `EditAnimeDialog` — writes to `CustomAnimeInfo`. |
| `action_notes` | always | `navigator.push(AnimeNotesScreen(anime))`. |
| `settings` (AY) | `onClickSettings != null` (`ConfigurableAnimeSource`) | `navigator.push(SourcePreferencesScreen(source.id))` — the source's own preference UI. |

### 3.3 Bottom action menu (selection mode only)

`app/src/main/java/eu/kanade/presentation/anime/components/AnimeBottomActionMenu.kt:78-…`. Appears only when episodes are multi-selected. Items: bookmark / un-bookmark, fillermark / un-fillermark (AY), mark-previous-as-seen, download, delete, play-externally, play-internally.

### 3.4 "Hide-if-empty" / placeholder patterns

Confirmed patterns (Tachiyomi convention, Animiru follows it):

| Field | Empty handling | Citation |
|---|---|---|
| `title` | Shows `MR.strings.unknown_title` ("Unknown title") — **placeholder, not hidden**. | `AnimeInfoHeader.kt:466` |
| `author` | Shows `MR.strings.unknown_author` ("Unknown author") — **placeholder, not hidden**. | `AnimeInfoHeader.kt:495-496` |
| `artist` | **Hidden** if `null`/blank OR if equal to `author`. | `AnimeInfoHeader.kt:514` |
| `description` | Shows `MR.strings.description_placeholder` — **placeholder, not hidden**. | `AnimeInfoHeader.kt:291-292` |
| `genre` (tags) | **Hidden** if `null`/empty. | `AnimeInfoHeader.kt:305` |
| `notes` | Rendered inline in the description section only if non-blank (`AnimeScreen.kt:582` passes `state.anime.notes`). | — |
| `nextUpdate` | "Not applicable" if null, "Expected soon" if 0 days, else `<N> days`. | `AnimeInfoHeader.kt:221-230` |
| trackingCount | Shows `manga_tracking_tab` ("Tracking") if 0, else `<N> trackers` plural. | `AnimeInfoHeader.kt:252-257` |
| status | Switch over `SAnime.ONGOING/COMPLETED/LICENSED/PUBLISHING_FINISHED/CANCELLED/ON_HIATUS/UNKNOWN`; default → `MR.strings.unknown`. | `AnimeInfoHeader.kt:545-569` |
| WebView button | **Hidden** if source is not an `AnimeHttpSource`. | `AnimeScreen.kt:162-168` (`.takeIf { isHttpSource }`) |
| Share button | **Hidden** if source is not an `AnimeHttpSource`. | `AnimeScreen.kt:196` (`.takeIf { isHttpSource }`) |
| Migrate menu item | **Hidden** if anime is not in library. | `AnimeScreen.kt:206-208` (`.takeIf { successState.anime.favorite }`) |
| Edit categories | **Hidden** if anime is not in library. | `AnimeScreen.kt:202` |
| Edit fetch interval | **Hidden** if anime is not in library. | `AnimeScreen.kt:203-205` |
| Source settings | **Hidden** if source is not `ConfigurableAnimeSource`. | `AnimeScreen.kt:210-212` |
| Skip intro | **Hidden** if not favorite or `fetchType != Episodes`. | `AnimeScreen.kt:213-217` |
| Airing countdown | **Hidden** if `airingTime <= 0`, `showNextEpisodeAirTime` off, or status is `COMPLETED`. | `AnimeScreen.kt:638, 652-655` |
| Episode list | **Empty list** if `fetchType == Seasons` (only seasons shown). Vice-versa. | `AnimeScreenModel.kt:287-300` |

---

## 4. Extension switching: separate "Migrate" flow, NOT in-place

### 4.1 The honest answer

**Animiru does NOT support in-place source switching on the details page.** Switching the source of an existing anime requires a separate, multi-screen "Migrate" wizard that creates a NEW `Anime` DB row with a NEW `animeId` and a NEW `source`/`url`. The user is then navigated to `AnimeScreen(target.id)`, leaving the old row behind (or deleting it, if "Migrate" rather than "Copy" was chosen).

This is directly relevant to ANIKUTA's planned 3-dot-menu "View from Extension / View from AniList" toggle: **Animiru has no precedent for that UX**. The closest analogue is the migrate flow below.

### 4.2 The migrate UX (code path)

Entry point: `AnimeScreen.kt:206-208`:
```kotlin
onMigrateClicked = {
    navigator.push(MigrationConfigScreen(successState.anime.id))
}.takeIf { successState.anime.favorite },
```

Flow:
1. **`MigrationConfigScreen(animeId)`** — `app/src/main/java/mihon/feature/migration/config/MigrationConfigScreen.kt:73-444`.
   - Lists all `AnimeHttpSource`s, partitioned into "selected" / "available". User picks which sources to search across, reorders them. Continue FAB → calls `continueMigration(openSheet = true, extraSearchQuery = null)` (`:86-98`).
   - For a single-anime migration (the common case from `AnimeScreen`), `animeIds.singleOrNull() != null`, so it skips the sheet and goes straight to `MigrateSearchScreen(animeId)` (`:92-96`).
2. **`MigrateSearchScreen(animeId)`** — `app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSearchScreen.kt:20-103`.
   - `MigrateSearchScreenModel` searches the configured sources for the current anime's title (`SmartSourceSearchEngine`).
   - User taps a result → if `fetchType == Seasons`, opens `SelectAnimeDialog` first (`:55-58`); else calls `onSelectAnime(it)` (`:60`).
   - `onSelectAnime` sets `SearchScreenModel.Dialog.Migrate(current, target)` (`:36`).
3. **`MigrateAnimeDialog`** — `app/src/main/java/mihon/feature/migration/dialog/MigrateAnimeDialog.kt:57-188`.
   - AlertDialog titled `migration_dialog_what_to_include`.
   - Checkboxes for each applicable `MigrationFlag`: `EPISODE`, `CATEGORY`, `CUSTOM_COVER`, `CUSTOM_BACKGROUND` (AY), `NOTES`, `REMOVE_DOWNLOAD` (`:201-215`).
   - Three buttons: "Show anime" (navigate to target's `AnimeScreen` without migrating), "Show seasons" (AY, for season-mode targets), and either "Copy" or "Migrate" depending on `canMigrate` (`current.fetchType == target.fetchType`).
   - "Copy" → `screenModel.migrateAnime(replace = false)` — keeps the source anime in library, adds the target as a second favorite.
   - "Migrate" → `screenModel.migrateAnime(replace = true)` — removes the source anime from library (sets `favorite=false, dateAdded=0`), adds target as favorite with the source's `episodeFlags/viewerFlags/dateAdded/notes` (`MigrateAnimeUseCase.kt:162-177`).
4. **`MigrateAnimeUseCase`** — `app/src/main/java/mihon/domain/migration/usecases/MigrateAnimeUseCase.kt:30-184`.
   - Calls `targetSource.getEpisodeList(target.toSAnime())` (or `getSeasonList` for season-mode) and syncs episodes via `syncEpisodesWithSource` (`:71-80`).
   - If `MigrationFlag.EPISODE` selected and `target.fetchType == Episodes`: matches episodes by `episodeNumber`, transfers `seen`/`bookmark`/`dateFetch` (`:84-116`).
   - If `MigrationFlag.CATEGORY`: copies category memberships (`:119-122`).
   - Trackers: re-binds `Track` rows to `target.id` (and runs `EnhancedTracker.migrateTrack` for enhanced trackers) (`:125-138`).
   - If `MigrationFlag.REMOVE_DOWNLOAD`: deletes the source anime's downloads (`:142-147`).
   - If `MigrationFlag.CUSTOM_COVER` / `CUSTOM_BACKGROUND`: copies the custom cover/background file to the target's cache slot (`:150-160`).
   - Updates `current` (unfavorite if `replace`) and `target` (favorite + inherit flags/notes) via two `AnimeUpdate`s (`:162-177`).
5. **Post-migration navigation** — `MigrateSearchScreen.kt:77-85`:
   ```kotlin
   onComplete = {
       if (navigator.lastItem is AnimeScreen) {
           val lastItem = navigator.lastItem
           navigator.popUntil { navigator.items.contains(lastItem) }
           navigator.push(AnimeScreen(dialog.target.id))   // ← NEW animeId
       } else {
           navigator.replace(AnimeScreen(dialog.target.id))
       }
   }
   ```

### 4.3 Where migration is reachable from

- Overflow menu on the details page (favorite only) — `AnimeScreen.kt:206-208`.
- Library multi-select "Migrate" action — `AnimeBottomActionMenu.kt:380, 408, 412` (via `LibraryBottomActionMenu`).
- Adding an anime to library when a duplicate-by-title already exists in another source — `Dialog.DuplicateAnime` (`AnimeScreenModel.kt:1788, 1838-1841`), reached via `toggleFavorite()` → `getDuplicateLibraryAnime` (`AnimeScreenModel.kt:541-548`).

### 4.4 Why it's NOT in-place

The `Anime` row has `source: Long` and `url: String` as primary identity (`Anime.kt:18, 30`, `animes.sq:9-10`). The `source` column is the extension source-id. To "switch source" you would have to either:
- Mutate `source` and `url` on the existing row (which would orphan the existing `episodes` rows — episodes have their own URLs that are source-specific), or
- Create a new row (what Animiru does).

Animiru chose the latter for data-integrity reasons: episode URLs are source-specific, history is keyed by episode-id, and the `Track` bindings point at the `remoteId` of the *original* source's anime (not the new source's). Migration is the only safe way to switch.

---

## 5. Lessons for ANIKUTA

### 5.1 What to ADOPT

1. **The `og*` + user-override overlay pattern** (`Anime.kt:31-82`). The `ogTitle`/`ogAuthor`/…/`ogStatus` "original" fields + a separate `CustomAnimeInfo` overlay, with computed `title`/`author`/… getters that prefer the override, is a clean model. It lets the user edit info without losing the source's original, and the UI doesn't need to know which is which. **ANIKUTA should generalize this**: instead of `og* (extension) + custom (user)`, use `originalSource (extension OR AniList) + override (user)`. The 2-layer overlay generalizes naturally to a pluggable source.

2. **The small focused SAnime→Anime mapper** (`mihon/domain/anime/model/SAnime.kt:6-29`, ~25 lines). Don't over-engineer. ANIKUTA's translation layer should produce similarly small, testable mappers: `SAnime.toUnifiedAnime()`, `AniListMedia.toUnifiedAnime()`. Animiru proves that a single function with a row-by-row field table is enough.

3. **Hide-if-empty + placeholder-when-required rendering** (§3.4). The pattern of "title → placeholder, description → placeholder, artist → hidden, tags → hidden, action button → hidden if N/A" is the right baseline for a unified page where data may be sparse (extension) or rich (AniList). ANIKUTA's unified screen should adopt the same per-field policy. Particularly: do NOT hide title/description/author — show placeholders — but DO hide tags/artist/secondary-action-buttons when empty.

4. **`LazyVerticalGrid` with `GridItemSpan(maxLineSpan)` for header-style items** (`AnimeScreen.kt:527, 548, 575, 596, 642`). This is how Animiru gets a single details page that works on phone (1 column) and tablet (2 columns) with the same Composables. ANIKUTA's screen should do the same.

5. **`AssistContentScreen` with `onProvideAssistUrl`** (`AnimeScreen.kt:85-87, 118-128`). Lets Android's "Now Playing" / screen-search assistant pick up the anime's URL. Cheap to implement, useful UX.

6. **Coalesce-on-refresh semantics in `UpdateAnime.awaitUpdateFromSource`** (`UpdateAnime.kt:51-56`): don't overwrite the user's title for favorited anime unless they opted in. ANIKUTA should keep this — once a user picks an AniList title or an extension title, refreshing shouldn't yank it away.

### 5.2 What to AVOID

1. **The separate "Migrate" wizard for source switching** (§4). Animiru's flow is 3 screens + a dialog + a use-case that creates a new DB row, leaves the old one behind (or deletes it), and re-binds tracks/downloads by best-effort matching. Episode history continuity is lost (episodes are matched by `episodeNumber` only, not by URL). This is acceptable for "I'm switching from a dead extension to a live one" but it is NOT the right UX for ANIKUTA's "View from Extension / View from AniList" toggle, which should be a 1-tap view-mode switch on the SAME anime row. **Animiru's flow is the cautionary tale, not the template.**

2. **Baking `source: Long` into the `Anime` row as the source of truth** (`Anime.kt:18`, `animes.sq:9`). Because the source-id is part of the row's identity, switching sources means creating a new row. ANIKUTA's design (an anime can have multiple source bindings + an AniList binding, with the unified page reading from whichever binding is currently "active") is fundamentally more flexible. ANIKUTA's `:data:anime` schema should keep `source_id`/`url` on a *separate* `anime_source_links` table, NOT on the `animes` row itself.

3. **Conflating "tracker" with "alternative details source"** (§2.5). In Animiru, AniList/MAL are trackers — secondary metadata shown in a modal sheet, with their own `lastEpisodeSeen`/`status`/`score` fields. The main details page reads only extension data. ANIKUTA's vision is the opposite: AniList CAN be the primary source for the page. ANIKUTA should not inherit Animiru's tracker-as-secondary concept unchanged — AniList should be a first-class `AnimeSource`-equivalent in the translation layer, not a `Track`.

4. **The `ExtensionDetailsScreen` folder** (§1.2). It's only there because Animiru's extension system supports multiple sources per installed APK and needs a management UI. ANIKUTA's `ExtensionDetailScreen.kt` (the one slated for removal) is a different thing entirely — it's a per-anime details page that bypasses the unified screen. Don't use Animiru's `ExtensionDetailsScreen` as a reference for what to keep OR what to remove; it's not analogous.

### 5.3 What ANIKUTA is doing DIFFERENTLY

1. **Pluggable translation layer** (extension-OR-AniList → unified → same screen). Animiru does NOT have this. Animiru's `Anime` IS the unified format, populated exclusively from `SAnime` (with a user-edit overlay). AniList/MAL/Jikan sit in a separate `Track` table and surface in a separate modal sheet. ANIKUTA's pluggable layer is novel relative to Animiru — there's no validating precedent here, only the negative precedent that Animiru's lack of one is why its "Migrate" flow is so heavy.

2. **In-place view-mode toggle** ("View from Extension / View from AniList" in the 3-dot menu). Animiru has no equivalent; its closest feature is the separate "Migrate" wizard (§4). ANIKUTA's toggle assumes the unified page can re-render from a different binding without creating a new DB row — which requires the schema change in §5.2.2 above.

3. **Removal of `ExtensionDetailScreen.kt`**. Animiru doesn't have a per-anime extension-only details screen to remove — its `AnimeScreen` already handles extension-sourced anime (because the `Anime` row IS extension-sourced). ANIKUTA is starting from a different baseline where extension-only anime have their own screen; consolidating into one is the ANIKUTA-specific cleanup.

### 5.4 Does Animiru's approach validate ANIKUTA's unified-page vision?

**Partially.** Animiru validates:
- That ONE details page (keyed by `animeId`) is sufficient for both favorited and not-yet-favorited anime, for both first-seen (search result) and fully-initialized states.
- That extension-sourced data is rich enough to populate a full details page without needing AniList/MAL.
- That the `og*` + override overlay model is a clean way to let users customize without losing the original.

Animiru does NOT validate:
- The pluggable translation layer (Animiru has none).
- In-place source switching (Animiru has the opposite — a heavy migration wizard).
- Treating AniList as a primary details source (Animiru treats it as a secondary tracker).

So: Animiru's architecture is a useful reference for the **shape of the unified page** (sections, hide-if-empty, og+override) but a **negative reference for source-switching UX**. ANIKUTA's pluggable-translation-layer design remains the right call; Animiru's migration flow is what ANIKUTA is explicitly trying to avoid.

---

## Appendix A: Key file:line citations (quick reference)

| Concept | File:line |
|---|---|
| `AnimeScreen` Voyager Screen | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreen.kt:80-83` |
| `AnimeScreen.Content()` | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreen.kt:89-466` |
| `AnimeScreenModel` class | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:126-180` |
| Init: subscribe + fetch-from-source | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:237-355` |
| `fetchAnimeFromSource()` | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:417-433` |
| `State.Success` data class | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:1865-2033` |
| `SAnime` interface (extension contract) | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt:7-84` |
| `SEpisode` interface | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt:7-49` |
| **First-seen mapper** `SAnime.toDomainAnime(sourceId)` | `domain/src/main/java/mihon/domain/anime/model/SAnime.kt:6-29` |
| **Refresh mapper** `Anime.copyFrom(other: SAnime)` | `app/src/main/java/eu/kanade/domain/anime/model/Anime.kt:70-106` |
| **Reverse mapper** `Anime.toSAnime()` | `app/src/main/java/eu/kanade/domain/anime/model/Anime.kt:53-68` |
| **Refresh interactor** `UpdateAnime.awaitUpdateFromSource` | `app/src/main/java/eu/kanade/domain/anime/interactor/UpdateAnime.kt:33-123` |
| `NetworkToLocalAnime` interactor | `domain/src/main/java/tachiyomi/domain/anime/interactor/NetworkToLocalAnime.kt:6-17` |
| `AnimeRepositoryImpl.insertNetworkAnime` | `data/src/main/java/tachiyomi/data/anime/AnimeRepositoryImpl.kt:127-172` |
| `AnimeRepositoryImpl.partialUpdate` | `data/src/main/java/tachiyomi/data/anime/AnimeRepositoryImpl.kt:204-247` |
| `AnimeMapper.mapAnime` (SQLDelight row → domain) | `data/src/main/java/tachiyomi/data/anime/AnimeMapper.kt:11-88` |
| `animes` SQLDelight table | `data/src/main/sqldelight/tachiyomi/data/animes.sq:7-41` |
| `Anime` domain model + `og*` + `customAnimeInfo` overlay | `domain/src/main/java/tachiyomi/domain/anime/model/Anime.kt:15-82` |
| `GetAnimeWithEpisodesAndSeasons` interactor | `domain/src/main/java/tachiyomi/domain/anime/interactor/GetAnimeWithEpisodesAndSeasons.kt:11-44` |
| `Track` domain model | `domain/src/main/java/tachiyomi/domain/track/model/Track.kt:5-20` |
| `AnimeScreen` Compose presentation | `app/src/main/java/eu/kanade/presentation/anime/AnimeScreen.kt:104-1366` |
| Small-UI grid item sequence | `app/src/main/java/eu/kanade/presentation/anime/AnimeScreen.kt:522-690` |
| `AnimeInfoBox` (cover/title/author/artist/status/source) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeInfoHeader.kt:114-176` |
| `AnimeActionRow` (Library/Interval/Track/WebView) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeInfoHeader.kt:178-274` |
| `ExpandableAnimeDescription` (description + tags + notes) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeInfoHeader.kt:276-…` |
| `AnimeContentInfo` (title/author/artist/status row) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeInfoHeader.kt:453-597` |
| `AnimeToolbar` (overflow menu) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeToolbar.kt:31-196` |
| `AnimeBottomActionMenu` (selection mode) | `app/src/main/java/eu/kanade/presentation/anime/components/AnimeBottomActionMenu.kt:78-…` |
| **Migrate entry point** (from details page) | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreen.kt:206-208` |
| `MigrationConfigScreen` (pick sources) | `app/src/main/java/mihon/feature/migration/config/MigrationConfigScreen.kt:73-444` |
| `MigrateSearchScreen` (search + pick match) | `app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSearchScreen.kt:20-103` |
| `MigrateAnimeDialog` (choose flags + Copy/Migrate) | `app/src/main/java/mihon/feature/migration/dialog/MigrateAnimeDialog.kt:57-188` |
| `MigrateAnimeUseCase` (performs the data transfer) | `app/src/main/java/mihon/domain/migration/usecases/MigrateAnimeUseCase.kt:30-184` |
| Post-migrate navigation (NEW animeId) | `app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/search/MigrateSearchScreen.kt:77-85` |
| Duplicate-anime migrate flow | `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt:1788, 1838-1841` |
| `ExtensionDetailsScreen` (NOT a per-anime page — ext mgmt) | `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt:16-53` |

---

*End of document. This is the only artifact produced by EXT-DETAILS-TASK4. No source files (Animiru or ANIKUTA) were modified.*
