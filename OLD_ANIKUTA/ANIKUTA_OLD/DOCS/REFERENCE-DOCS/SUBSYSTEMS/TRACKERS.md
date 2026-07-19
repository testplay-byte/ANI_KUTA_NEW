# Trackers

Two-way sync of the user's anime/manga library (status, progress, score, dates) with third-party tracking services and self-hosted media servers.

## Where it lives

- Reference root: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/track/`
- Per-tracker packages (11): `anilist/`, `bangumi/`, `jellyfin/`, `kavita/`, `kitsu/`, `komga/`, `mangaupdates/`, `myanimelist/`, `shikimori/`, `simkl/`, `suwayomi/`. Each typically contains: `<Name>.kt` (the `Tracker` impl), `<Name>Api.kt` (HTTP), `<Name>Interceptor.kt` (auth header + token refresh), `<Name>Utils.kt`, and a `dto/` sub-package of `@Serializable` response types.
- Base contracts (in `data/track/` root): `Tracker.kt`, `BaseTracker.kt`, `AnimeTracker.kt`, `MangaTracker.kt`, `EnhancedAnimeTracker.kt`, `EnhancedMangaTracker.kt`, `DeletableAnimeTracker.kt`, `DeletableMangaTracker.kt`, `TrackerManager.kt`.
- Search-result models: `data/track/model/AnimeTrackSearch.kt`, `MangaTrackSearch.kt`.
- DB-row interfaces: `data/database/models/anime/AnimeTrack.kt`, `data/database/models/manga/MangaTrack.kt` (+ `*Impl.kt`).
- Domain models (immutable data classes): `domain/src/main/java/tachiyomi/domain/track/{anime,manga}/model/{AnimeTrack,MangaTrack}.kt`.
- Domain mappers: `app/src/main/java/eu/kanade/domain/track/{anime,manga}/model/*.kt` (`toDbTrack()` / `toDomainTrack()`).
- Interactors (sync lifecycle): `app/src/main/java/eu/kanade/domain/track/{anime,manga}/interactor/` — `AddAnimeTracks` / `AddMangaTracks`, `TrackEpisode` / `TrackChapter`, `RefreshAnimeTracks` / `RefreshMangaTracks`, `SyncEpisodeProgressWithTrack` / `SyncChapterProgressWithTrack`.
- Offline queue: `app/src/main/java/eu/kanade/domain/track/{anime,manga}/store/DelayedAnimeTrackingStore.kt` (+ manga) and `service/DelayedAnimeTrackingUpdateJob.kt` (+ manga, WorkManager).
- Repository impls: `data/src/main/java/tachiyomi/data/track/{anime,manga}/{AnimeTrack,MangaTrack}RepositoryImpl.kt` + `*Mapper.kt`. SQLDelight `anime_sync` / `manga_sync` queries.
- Preferences: `app/src/main/java/eu/kanade/domain/track/service/TrackPreferences.kt`.
- Login UI: `app/src/main/java/eu/kanade/tachiyomi/ui/setting/track/` — `BaseOAuthLoginActivity.kt`, `TrackLoginActivity.kt`. Settings screen: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt`.
- Per-entry track UI: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/{anime,manga}/track/`.

## What it does

For each library entry the user can bind one or more tracker rows. Aniyomi then keeps the local row and each remote row in sync: when the user watches an episode / reads a chapter, changes status, sets a score, or sets start/finish dates, the corresponding tracker API is called and the local DB row is updated with the remote response. The reverse direction (pulling remote progress into local) is supported for "enhanced" trackers only (Jellyfin/Komga/Kavita/Suwayomi — the self-hosted media servers).

## Key types & files

### `Tracker` interface (`Tracker.kt`)
The base contract every tracker implements. Identity + credentials + UI metadata:
- `id: Long`, `name: String`, `client: OkHttpClient` (shared network client from `NetworkHelper`).
- `supportsReadingDates: Boolean`, `supportsPrivateTracking: Boolean`.
- `getLogo(): Int`, `getLogoColor(): Int` (drawable res + brand color).
- `getCompletionStatus(): Long`, `getScoreList(): ImmutableList<String>`.
- `login(username, password)`, `logout()`, `isLoggedIn`, `isLoggedInFlow: Flow<Boolean>`, `getUsername()/getPassword()/saveCredentials(...)`.
- Convenience upcasts: `animeService: AnimeTracker` and `mangaService: MangaTracker` (default to `this as ...`, will ClassCast if the tracker doesn't implement that side).

### `BaseTracker` abstract class (`BaseTracker.kt`)
Provides shared credential storage via `TrackPreferences` and the shared `OkHttpClient` from `NetworkHelper`. `isLoggedIn` = both username and password non-empty. `logout()` clears credentials. `isLoggedInFlow` combines the username + password preference flows. Injected via `uy.kohesive.injekt.injectLazy()`.

### `AnimeTracker` / `MangaTracker` interfaces
SPLIT contract — one per media type, NOT a single unified `TrackService`. Each defines the same shape with media-specific names:
- Status enum helpers: `getStatusListAnime()` / `getStatusListManga()`, `getWatchingStatus()` / `getReadingStatus()`, `getRewatchingStatus()` / `getRereadingStatus()`, `getCompletionStatus()`, `getStatusForAnime(status)` / `getStatusForManga(status)` (returns a `StringResource`).
- Score helpers: `getScoreList()`, `indexToScore(index)`, `get10PointScore(track)`, `displayScore(track)`.
- Core operations: `update(track, didWatchEpisode/didReadChapter)`, `bind(track, hasSeenEpisodes/hasReadChapters)`, `searchAnime(query)` / `searchManga(query)`, `refresh(track)`.
- High-level setters (default-implemented in the interface): `setRemoteAnimeStatus` / `setRemoteMangaStatus`, `setRemoteLastEpisodeSeen` / `setRemoteLastChapterRead`, `setRemoteScore`, `setRemoteStartDate`, `setRemoteFinishDate`, `setRemotePrivate`. Each mutates the track then calls private `updateRemote(track)` which calls `update(track)` and persists via `InsertAnimeTrack` / `InsertMangaTrack`.
- `register(item, animeId/mangaId)` — default-impl that delegates to `AddAnimeTracks.bind` / `AddMangaTracks.bind`.

### `EnhancedAnimeTracker` / `EnhancedMangaTracker`
Markers for trackers that never prompt the user to manually search/bind. They are bound to specific source classes (`getAcceptedSources(): List<String>` of fully-qualified source class names) and auto-match via `match(anime/manga): AnimeTrackSearch?`. Provide `accept(source)`, `loginNoop()`, `isTrackFrom(track, anime/manga, source)`, `migrateTrack(track, anime/manga, newSource)`. Used by the four self-hosted media-server trackers (Jellyfin, Komga, Kavita, Suwayomi).

### `DeletableAnimeTracker` / `DeletableMangaTracker`
Single-method interface: `suspend fun delete(track)`. Implemented by trackers whose remote API supports removing a list entry (MAL, Anilist, Kitsu, Shikimori, MangaUpdates). Bangumi, Simkl, and the four enhanced trackers do NOT implement it.

### `TrackerManager` (`TrackerManager.kt`)
Eagerly constructs and registers all 11 trackers with stable integer IDs (constants: `ANILIST=2`, `KITSU=3`, `KAVITA=8`, `SIMKL=101`, `JELLYFIN=102`; the others are inlined: MAL=1, Shikimori=4, Bangumi=5, Komga=6, MangaUpdates=7, Suwayomi=9). Holds them in `trackers: List<Tracker>` and exposes `loggedInTrackers()`, `loggedInTrackersFlow()`, `get(id)`, `getAll(ids)`. Injected as a singleton via Injekt.

### DB-row interfaces (`data/database/models/{anime,manga}/`)
`AnimeTrack` / `MangaTrack` — mutable `Serializable` interfaces with snake_case fields (`anime_id`, `tracker_id`, `remote_id`, `library_id`, `last_episode_seen` / `last_chapter_read`, `total_episodes` / `total_chapters`, `score`, `status`, `started_watching_date` / `started_reading_date`, `finished_watching_date` / `finished_reading_date`, `tracking_url`, `private`). Has `copyPersonalFrom(other)` to copy progress/score/status/dates from a remote row. `*Impl.kt` are the concrete implementations. Companion `create(serviceId)` factory.

### Domain models (`domain/.../track/{anime,manga}/model/`)
Immutable `data class AnimeTrack` / `MangaTrack` with camelCase fields used by the domain/data layers. Mappers in `app/.../domain/track/{anime,manga}/model/*.kt` convert between DB-row and domain forms.

### Search-result models (`data/track/model/`)
`AnimeTrackSearch` / `MangaTrackSearch` — extend the DB-row interfaces (`AnimeTrack` / `MangaTrack`) with extra display-only fields (`authors`, `artists`, `cover_url`, `summary`, `publishing_status`, `publishing_type`, `start_date`). Returned by `searchAnime` / `searchManga`.

### `TrackPreferences`
Stores per-tracker credentials and tokens in private SharedPreferences keyed by `tracker.id`: `trackUsername`, `trackPassword`, `trackToken`, `trackAuthExpired`. Also holds global flags: `autoUpdateTrack()`, `trackOnAddingToLibrary()`, `autoUpdateTrackOnMarkRead()` (an `AutoTrackState` enum: ALWAYS / ASK / NEVER), `showNextEpisodeAiringTime()`, plus `anilistScoreType()` (one of `POINT_10`, `POINT_100`, `POINT_10_DECIMAL`, `POINT_5`, `POINT_3`).

### OAuth base / login activity
There is no shared `OAuth` base class. Each OAuth tracker defines its own DTO (`MALOAuth`, `ALOAuth`, `KitsuOAuth`, `SMOAuth`, `BGMOAuth`, `SimklOAuth`) and its own `Interceptor` that handles bearer-token injection + refresh. The shared piece is `BaseOAuthLoginActivity` — an Activity that receives the OAuth deep-link callback (`intent.data`) and dispatches to `handleResult(data)`. The single concrete subclass `TrackLoginActivity` switches on `data.host` (`anilist-auth`, `bangumi-auth`, `myanimelist-auth`, `shikimori-auth`, `simkl-auth`) and calls the tracker's `suspend fun login(code)` / `login(token)`.

## The 11 trackers

| # | Service | Class | ID | Anime | Manga | Auth method | Deletable | Enhanced | Notes |
|---|---------|-------|----|------|-------|-------------|-----------|----------|-------|
| 1 | MyAnimeList | `MyAnimeList` | 1 | yes | yes | OAuth2 PKCE (browser) — `MyAnimeListApi.authUrl()` → `TrackLoginActivity` host `myanimelist-auth` → `login(code)` → `api.getAccessToken(authCode)` (exchanges code+PKCE verifier) | yes (both) | no | Supports reading dates. Token refresh handled in `MyAnimeListInterceptor.refreshToken()`. Has `trackAuthExpired` flag for unrecoverable 401. Search supports `id:` and `my:` prefixes. |
| 2 | AniList | `Anilist` | 2 | yes | yes | OAuth2 implicit grant (browser) — `AnilistApi.authUrl()` returns `response_type=token`; `TrackLoginActivity` host `anilist-auth` extracts `access_token=...` from URL fragment → `login(token)` → `api.createOAuth(token)` (long-lived, ~1yr) | yes (both) | no | Supports reading dates + private tracking. Score type is configurable (POINT_10/100/10_DECIMAL/5/3). Uses GraphQL (`https://graphql.anilist.co/`). Rate-limited at 85 req/min. API v1→v2 migration forces logout on bad score pref. |
| 3 | Kitsu | `Kitsu` | 3 | yes | yes | OAuth2 password grant (in-app form, NOT browser) — `login(username, password)` → `api.login(...)` posts `grant_type=password` + client_id/secret → `KitsuOAuth` | yes (both) | no | Supports reading dates + private tracking. No rewatching/rereading status (`getRewatchingStatus()` returns -1). Stores userId as "password" credential. Token refresh in `KitsuInterceptor`. |
| 4 | Shikimori | `Shikimori` | 4 | yes | yes | OAuth2 auth-code (browser) — `ShikimoriApi.authUrl()` → `TrackLoginActivity` host `shikimori-auth` → `login(code)` → `api.accessToken(code)` | yes (both) | no | Anime and manga share the same status constants (`READING`/`WATCHING` both = 1L). Uses Shikimori's REST API + user nickname (or stringified ID) in queries. |
| 5 | Bangumi | `Bangumi` | 5 | yes | yes | OAuth2 auth-code (browser) — `BangumiApi.authUrl()` → `TrackLoginActivity` host `bangumi-auth` → `login(code)` → `api.accessToken(code)` | no | no | Supports private tracking. No rewatching/rereading status (-1). Uses `bgm.tv` OAuth endpoint. |
| 6 | Komga | `Komga` | 6 | no | yes | None — `loginNoop()` saves dummy `"user"/"pass"` credentials just to flip `isLoggedIn`. Real auth = basic auth / API key configured per Komga source instance | n/a | yes (`EnhancedMangaTracker`) | Self-hosted manga server. Bound to source `eu.kanade.tachiyomi.extension.all.komga.Komga`. Uses `Dns.SYSTEM` (no DoH — IP-based addressing). No scores (empty list). |
| 7 | MangaUpdates | `MangaUpdates` | 7 | no | yes | Session token via username/password (in-app form) — `login(username, password)` → `api.authenticate(...)` returns `MULoginResponse(uid, sessionToken)` → saved as credentials | yes | no | Manga-only (Baka-Updates). Uses list-based statuses (READING_LIST, WISH_LIST, COMPLETE_LIST, UNFINISHED_LIST, ON_HOLD_LIST). Score is 0.0–10.0 with one decimal. |
| 8 | Kavita | `Kavita` | 8 | no | yes | None — `loginNoop()` dummy creds. Real auth = JWT per Kavita server, fetched on demand by `KavitaInterceptor` via `Kavita.loadOAuth()` which reads `APIURL`/`APIKEY` from each of 3 Kavita source instances' prefs and calls `api.getNewToken(...)` | n/a | yes (`EnhancedMangaTracker`) | Self-hosted manga server. Bound to source `eu.kanade.tachiyomi.extension.all.kavita.Kavita`. `OAuth` holder in `KavitaModels.kt` keeps up to 3 server JWTs. |
| 9 | Suwayomi | `Suwayomi` | 9 | no | yes | None — `loginNoop()` dummy creds. Real auth handled by the source | n/a | yes (`EnhancedMangaTracker`) | Self-hosted manga server (Tachidesk). Bound to source `eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk`. Uses `SuwayomiApi` (WebSocket/REST). Logo color marked TODO. |
| 10 | Simkl | `Simkl` | 101 | yes | no | OAuth2 auth-code (browser) — `SimklApi.authUrl()` → `TrackLoginActivity` host `simkl-auth` → `login(code)` → `api.accessToken(code)` | no | no | Anime-only (includes TV + movie search via `searchAnime(query, "anime"|"tv"|"movie")`). Statuses: WATCHING, COMPLETED, ON_HOLD, NOT_INTERESTING, PLAN_TO_WATCH. No rewatching (returns 0). |
| 11 | Jellyfin | `Jellyfin` | 102 | yes | no | None — `loginNoop()` dummy creds. Real auth = per-source API key, fetched by `JellyfinInterceptor` from the Jellyfin source instance's prefs (`user_id` / `api_key`), keyed by userId | n/a | yes (`EnhancedAnimeTracker`) | Self-hosted anime/media server. Bound to source `eu.kanade.tachiyomi.animeextension.all.jellyfin.Jellyfin`. Custom OkHttpClient with `Dns.SYSTEM`. No scores. `getRewatchingStatus()` returns -1. |

## How it works

### Registration
`TrackerManager` is constructed once (Injekt singleton, see `di/AppModule.kt`). It eagerly instantiates all 11 tracker classes with their hardcoded IDs. `loggedInTrackers()` filters by `isLoggedIn` (which is just "both credentials non-empty" — note this means the four `loginNoop()` enhanced trackers count as "logged in" the moment the user toggles them on in settings, even though they have no real credentials). `loggedInTrackersFlow()` exposes a reactive combined flow consumed by UI to render the list of available services in track dialogs and settings.

### Login / OAuth flow

Two distinct paths depending on the tracker:

**Browser-based OAuth (MAL, Anilist, Shikimori, Bangumi, Simkl):**
1. User opens `SettingsTrackingScreen` and taps a tracker.
2. `openInBrowser(...)` opens `<Service>Api.authUrl()` in the system browser. The URL encodes `client_id`, `redirect_uri`, and (for MAL) a PKCE `code_challenge`.
3. After the user authorizes on the service's site, the browser redirects to the app's deep link (an Android app link / custom scheme handled by `TrackLoginActivity`).
4. `TrackLoginActivity.handleResult(data)` switches on `data.host` (`myanimelist-auth`, `anilist-auth`, `shikimori-auth`, `bangumi-auth`, `simkl-auth`):
   - Anilist: extracts `access_token=...` from the URL fragment (implicit grant).
   - Others: extracts the `code` query parameter (auth-code grant).
5. Calls the tracker's `suspend fun login(code)` / `login(token)`, which:
   - Exchanges the code/token for an `*OAuth` object via the API.
   - Calls `interceptor.setAuth(oauth)` / `interceptor.newAuth(oauth)` so subsequent requests carry the bearer token.
   - Fetches the current user (`api.getCurrentUser()`) and saves credentials via `saveCredentials(username, accessToken)`.
   - On any failure: `logout()`.
6. `returnToSettings()` finishes the activity and returns to `MainActivity`.

**In-app form (Kitsu, MangaUpdates):**
- `SettingsTrackingScreen` shows a username/password dialog. On submit, `tracker.login(username, password)` is called directly:
  - Kitsu: posts `grant_type=password` + `client_id`/`client_secret` to Kitsu's token endpoint → `KitsuOAuth`. Saves username + userId as credentials.
  - MangaUpdates: `api.authenticate(username, password)` returns a `MULoginResponse(uid, sessionToken)`. Saves `uid` as username, `sessionToken` as password. `MangaUpdatesInterceptor` injects the session token header.

**No-op login (Komga, Kavita, Suwayomi, Jellyfin):**
- `loginNoop()` just calls `saveCredentials("user", "pass")` to flip `isLoggedIn`. The real per-server credentials live in the corresponding source extension's preferences and are read lazily by the tracker's interceptor (e.g. `JellyfinInterceptor.getApiKey(userId)` reads `api_key` from the matching Jellyfin source's prefs; `Kavita.loadOAuth()` iterates 3 Kavita source instances and fetches a JWT per server).

### Token refresh
Trackers with expiring OAuth tokens handle refresh inside their OkHttp `Interceptor`:
- `MyAnimeListInterceptor`: if `oauth.isExpired()`, sends `MyAnimeListApi.refreshTokenRequest(oauth)` (POST to `/v1/oauth2/token` with `grant_type=refresh_token`). On 401, sets `trackAuthExpired=true` and throws `MALTokenExpired` so the UI can prompt re-login.
- `KitsuInterceptor`: same pattern with `refresh_token` grant.
- Anilist tokens are long-lived (~1 year, `System.currentTimeMillis() + 31536000000`) so no refresh logic exists.
- Shikimori/Bangumi/Simkl: TODO — refresh behaviour not verified in this pass; `*OAuth` DTOs exist and tokens are persisted via `saveToken`/`restoreToken`.

### Sync lifecycle

**When does a track update fire?**

1. **On episode watched (anime):** `PlayerViewModel.updateTrackEpisodeSeen(episode)` is called from `updateEpisodeProgressOnComplete(currentEp)` (player completion callback). It returns early if incognito mode, no trackers bound, or `TrackPreferences.autoUpdateTrack()` is false. Otherwise it launches `TrackEpisode.await(context, animeId, episodeNumber)`. Same flow on chapter read for manga via `ReaderViewModel` → `TrackChapter.await(...)`.
2. **On manual status/score/date change:** the per-entry track dialog (`AnimeTrackInfoDialog` / `MangaTrackInfoDialog`) calls the `AnimeTracker.setRemote*` helpers, which mutate the track and call `updateRemote(track)` → `update(track)` → `insertTrack.await(...)`.
3. **On manual bind (search):** user opens the track dialog, taps "Add tracking", searches (`tracker.searchAnime(query)`), picks a result → `AnimeTracker.register(item, animeId)` → `AddAnimeTracks.bind(tracker, item, animeId)`.
4. **On adding to library:** if `TrackPreferences.trackOnAddingToLibrary()` is true, enhanced trackers auto-bind via `AddAnimeTracks.bindEnhancedTrackers(anime, source)` which iterates `trackerManager.loggedInTrackers().filterIsInstance<EnhancedAnimeTracker>().filter { it.accept(source) }` and calls `service.match(anime)` to silently bind.
5. **On manual refresh:** "Refresh" action in the track dialog or pull-to-refresh on the entry screen calls `RefreshAnimeTracks.await(animeId)` which fans out to all bound, logged-in trackers in parallel (`supervisorScope` + `async`), calls `service.animeService.refresh(track)`, persists the updated row, and runs `SyncEpisodeProgressWithTrack` to propagate remote progress back to local episodes.

**What `TrackEpisode.await()` does (the core "I just watched an episode" flow):**

1. Fetch all tracks for the anime via `GetAnimeTracks`.
2. For each track: skip if tracker not found, not logged in, or `episodeNumber <= track.lastEpisodeSeen` (no backwards sync).
3. If online: `service.animeService.refresh(track)` to get latest remote state → copy with `lastEpisodeSeen = episodeNumber` → `service.animeService.update(updatedTrack, didWatchEpisode=true)` → `insertTrack.await(updatedTrack)` → `delayedTrackingStore.removeAnimeItem(track.id)`.
4. If offline: `delayedTrackingStore.addAnime(track.id, episodeNumber)` (SharedPreferences-backed queue keyed by trackId) → `DelayedAnimeTrackingUpdateJob.setupTask(context)` enqueues a one-time WorkManager job with `NetworkType.CONNECTED` constraint + exponential 5-min backoff. The job retries up to 3 times; on each run it drains the queue by calling `TrackEpisode.await(...)` again with `setupJobOnFailure=false`.

**Inside `update(track, didWatchEpisode)`:** each tracker applies its own status-transition logic. Common pattern (MAL, Anilist, Kitsu, Shikimori, Bangumi, Simkl): if not COMPLETED and `didWatchEpisode` is true:
- If `last_episode_seen == total_episodes` and `total_episodes > 0`: set status = COMPLETED, set `finished_watching_date = now`.
- Else if not already rewatching: set status = WATCHING/READING; if this is the first episode, also set `started_watching_date = now`.
Then `api.updateItem(track)` / `api.updateLibAnime(track)` is called and the response (or the input track) is returned.

**`bind(track, hasSeenEpisodes)` flow:** looks up the item in the user's remote list (`api.findListItem(track)` / `api.findLibAnime(...)`). If found, copies personal fields (progress, score, status, dates) from the remote row, sets the library_id / remote_id, and calls `update(track)`. If not found, creates a new entry with status = `hasSeenEpisodes ? WATCHING : PLAN_TO_WATCH` (or equivalent), score 0, via `api.addLibAnime(track)`.

**`SyncEpisodeProgressWithTrack` (reverse sync, enhanced trackers only):** returns early if the tracker is not an `EnhancedAnimeTracker`. Marks all local episodes with `episodeNumber <= remoteTrack.lastEpisodeSeen` as seen, takes the max of remote and local "last seen", updates the tracker and the local episodes in one transaction. This is how watching on a Jellyfin/Komga/Kavita/Suwayomi server propagates back into Aniyomi's local read state.

### How track data is stored

- **Local DB:** SQLDelight. Tables `anime_sync` (anime) and `manga_sync` (manga). Schema mirrors the `AnimeTrack` / `MangaTrack` interfaces. Queries live in `.sq` files (not read in this pass — TODO). Access via `AnimeDatabaseHandler` / `MangaDatabaseHandler`.
- **Repository:** `AnimeTrackRepositoryImpl` / `MangaTrackRepositoryImpl` (in `:data` module) implement `AnimeTrackRepository` / `MangaTrackRepository` (in `:domain`). Methods: `getTrackByAnimeId`, `getTracksByAnimeId`, `getAnimeTracksAsFlow`, `getTracksByAnimeIdAsFlow`, `delete(animeId, trackerId)`, `insertAnime(track)`, `insertAllAnime(tracks)`.
- **Domain interactors:** `InsertAnimeTrack`, `GetAnimeTracks`, `GetTracksPerAnime`, `DeleteAnimeTrack` (and manga equivalents). Wired in `DomainModule.kt`.
- **Credentials/tokens:** `TrackPreferences` → private SharedPreferences (`Preference.privateKey(...)`). Keyed by `tracker.id`. Separate keys for username, password, token, auth-expired flag.
- **Offline queue:** `DelayedAnimeTrackingStore` — separate SharedPreferences file `"tracking_queue"`, keyed by trackId → lastEpisodeSeen (Float). Drained by `DelayedAnimeTrackingUpdateJob` (WorkManager).
- **OAuth DTOs:** serialized to JSON and stored in the same `trackToken` preference (e.g. `MyAnimeList.loadOAuth()` / `saveOAuth(oauth)`).

## Dependencies

Depends on:
- **DATA-LAYER** (`:data` module): `AnimeTrackRepositoryImpl` / `MangaTrackRepositoryImpl`, `AnimeTrackMapper` / `MangaTrackMapper`, SQLDelight `anime_sync` / `manga_sync` tables + handlers. Track rows persist here.
- **DOMAIN** (`:domain` module): `AnimeTrack` / `MangaTrack` data classes, repository interfaces, `InsertAnimeTrack` / `GetAnimeTracks` / `DeleteAnimeTrack` / `GetTracksPerAnime` interactors.
- **Network** (`NetworkHelper` from `app/.../network/`): shared `OkHttpClient` (with rate limiting, etc.) used as the base for each tracker's `client`. Each tracker builds a derived client with its own `Interceptor`.
- **DI (Injekt):** `TrackerManager` singleton, `TrackPreferences` lazy, `NetworkHelper` lazy, per-tracker singletons. Wired in `di/AppModule.kt` and `DomainModule.kt`.
- **Preferences** (`tachiyomi.core.common.preference.PreferenceStore`): backs `TrackPreferences`.
- **WorkManager:** `DelayedAnimeTrackingUpdateJob` / `DelayedMangaTrackingUpdateJob` for offline retry.
- **SOURCE-SYSTEM:** the four enhanced trackers (Jellyfin, Komga, Kavita, Suwayomi) depend on their corresponding source extensions being installed and configured — they read API URLs / API keys / JWTs from the source's `sourcePreferences()`. `JellyfinInterceptor` and `Kavita.loadOAuth()` actively look up sources by computed source ID (MD5 hash of the source key) via `AnimeSourceManager` / `MangaSourceManager`.
- **i18n:** `tachiyomi.i18n.MR` (manga-shared strings) and `tachiyomi.i18n.aniyomi.AYMR` (anime-specific strings) for status labels.
- **Backup/restore:** `BackupFileValidator` uses `TrackerManager.get(id)` to check whether trackers referenced in a backup file are logged in.

Depended on by:
- **UI-THEME / Settings UI:** `SettingsTrackingScreen` renders the list of trackers from `TrackerManager.trackers` and the login forms / browser-open buttons.
- **Entry screens:** `AnimeScreenModel` / `MangaScreenModel` expose track state for the info header; `AnimeTrackInfoDialog` / `MangaTrackInfoDialog` render bound tracks and the add-track search UI.
- **Player / Reader:** `PlayerViewModel` calls `TrackEpisode.await(...)` on episode completion; `ReaderViewModel` calls `TrackChapter.await(...)` on chapter read.
- **Library screens:** `AnimeLibraryScreenModel` / `MangaLibraryScreenModel` honor `trackOnAddingToLibrary()` to auto-bind enhanced trackers.
- **Migration:** `MigrateAnimeDialog` / `MigrateMangaDialog` use `EnhancedAnimeTracker.migrateTrack(...)` to rebind tracks when migrating an entry between sources.
- **Backup:** `AnimeRestorer` / manga restorer insert tracks from backups via `InsertAnimeTrack`.
- **Stats:** `AnimeStatsScreenModel` / `MangaStatsScreenModel` count tracked entries.

## Anime vs manga

This is the most important section for an anime-first fork. The tracker contract is **split**, not unified:

- `Tracker` is the base identity/credential contract. A tracker then implements **zero, one, or both** of `AnimeTracker` and `MangaTracker`.
- The `Tracker.animeService` and `Tracker.mangaService` extension properties do `this as AnimeTracker` / `this as MangaTracker` — they will `ClassCastException` if the tracker doesn't implement that side. Callers must check with `is` first (or use `filterIsInstance<AnimeTracker>()` as `AddAnimeTracks.bindEnhancedTrackers` does).

Per-service media support:

| Service | Anime | Manga | Interfaces implemented |
|---------|-------|-------|------------------------|
| MyAnimeList | yes | yes | `MangaTracker`, `AnimeTracker`, `DeletableMangaTracker`, `DeletableAnimeTracker` |
| AniList | yes | yes | `MangaTracker`, `AnimeTracker`, `DeletableMangaTracker`, `DeletableAnimeTracker` |
| Kitsu | yes | yes | `AnimeTracker`, `MangaTracker`, `DeletableMangaTracker`, `DeletableAnimeTracker` |
| Shikimori | yes | yes | `MangaTracker`, `AnimeTracker`, `DeletableMangaTracker`, `DeletableAnimeTracker` |
| Bangumi | yes | yes | `MangaTracker`, `AnimeTracker` (not deletable) |
| Komga | no | yes | `EnhancedMangaTracker`, `MangaTracker` |
| MangaUpdates | no | yes | `MangaTracker`, `DeletableMangaTracker` |
| Kavita | no | yes | `EnhancedMangaTracker`, `MangaTracker` |
| Suwayomi | no | yes | `EnhancedMangaTracker`, `MangaTracker` |
| Simkl | yes | no | `AnimeTracker` |
| Jellyfin | yes | no | `EnhancedAnimeTracker`, `AnimeTracker` |

Tally: **5 support both**, **2 are anime-only** (Simkl, Jellyfin), **4 are manga-only** (Komga, MangaUpdates, Kavita, Suwayomi).

Status-code constants are per-tracker and per-media. Some trackers (MAL, Anilist, Kitsu) use distinct code ranges for anime vs manga (e.g. MAL `WATCHING=11`, `READING=1`); others (Shikimori, Bangumi) reuse the same constants for both. `getStatusForAnime(status)` and `getStatusForManga(status)` return different `StringResource`s for the same code where this matters.

DB / domain models are also fully split: separate `anime_sync` vs `manga_sync` SQLDelight tables, separate `AnimeTrack` vs `MangaTrack` interfaces (snake_case `last_episode_seen` / `last_chapter_read`, etc.), separate domain data classes, separate repositories, separate interactors (`TrackEpisode` vs `TrackChapter`, `AddAnimeTracks` vs `AddMangaTracks`, `RefreshAnimeTracks` vs `RefreshMangaTracks`, `SyncEpisodeProgressWithTrack` vs `SyncChapterProgressWithTrack`), separate offline stores and WorkManager jobs.

**Can we ship anime-only trackers cleanly?** Yes. The split contract means a tracker that only implements `AnimeTracker` (Simkl, Jellyfin) never has any manga-side code exercised. To go anime-only:
- Keep: MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Simkl, Jellyfin (7 trackers — all anime-supporting).
- Drop: Komga, MangaUpdates, Kavita, Suwayomi (4 manga-only trackers).
- For the 5 dual-support trackers (MAL, AniList, Kitsu, Shikimori, Bangumi), they each have a `searchManga`, `bind(MangaTrack)`, `update(MangaTrack)`, `refresh(MangaTrack)` etc. — these can be left in place (dead code) or surgically removed. The `MangaTracker` interface implementation can be dropped from the class declaration without breaking anime-side compilation, but the `dto/MALManga.kt`, `dto/ALManga.kt`, `dto/Kitsu*` manga DTOs, and the `*Api.kt` manga methods would need cleanup.
- The `Tracker.mangaService` getter would need to either be removed or made nullable, since not all trackers would implement `MangaTracker`.
- All manga-side interactors, repositories, DB tables, and UI screens would be removed in a fuller anime-only fork (out of scope for this doc — see DATA-LAYER and UI-THEME docs).

## Relationships

- **DATA-LAYER:** Trackers persist all bound rows in `anime_sync` / `manga_sync` SQLDelight tables via `AnimeTrackRepositoryImpl` / `MangaTrackRepositoryImpl`. The `:data` module owns the schema and mappers; the `:app` module owns the tracker classes and interactors. Mappers in `app/.../domain/track/{anime,manga}/model/*.kt` bridge the DB-row interfaces and the domain data classes.
- **UI-THEME:** `SettingsTrackingScreen` is the user-facing login/logout UI. `AnimeTrackInfoDialog` / `MangaTrackInfoDialog` (and their `presentation/track/` Compose counterparts) are the per-entry binding/status/score UI. All render tracker brand colors via `getLogoColor()` and logos via `getLogo()`.
- **SOURCE-SYSTEM:** Four of the 11 trackers ARE media servers that also exist as source extensions — Jellyfin (anime source `eu.kanade.tachiyomi.animeextension.all.jellyfin.Jellyfin`), Komga (manga source `eu.kanade.tachiyomi.extension.all.komga.Komga`), Kavita (manga source `eu.kanade.tachiyomi.extension.all.kavita.Kavita`), Suwayomi (manga source `eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk`). These trackers are `EnhancedAnimeTracker` / `EnhancedMangaTracker` bound to those source classes via `getAcceptedSources()`. Their auth credentials live in the source extension's `sourcePreferences()`, not in `TrackPreferences`. `JellyfinInterceptor` and `Kavita.loadOAuth()` reach into the source manager to fetch them. This is the most notable overlap between subsystems.
- **PLAYER / READER:** `PlayerViewModel` → `TrackEpisode`; `ReaderViewModel` → `TrackChapter`. These are the runtime sync triggers.
- **BACKUP/RESTORE:** `BackupFileValidator` reports trackers in a backup that aren't logged in. `AnimeRestorer` / manga restorer re-insert tracks via `InsertAnimeTrack` / `InsertMangaTrack`.
- **MIGRATION:** `MigrateAnimeDialog` / `MigrateMangaDialog` use `EnhancedTracker.migrateTrack(...)` to rebind when the underlying source changes.
- **DOMAIN module:** owns the `AnimeTrack` / `MangaTrack` data classes, repository interfaces, and the basic `Insert/Get/Delete/GetTracksPerAnime` interactors. The richer sync interactors (`AddAnimeTracks`, `TrackEpisode`, `RefreshAnimeTracks`, `SyncEpisodeProgressWithTrack`) live in the `:app` module under `eu/kanade/domain/track/...` because they depend on `TrackerManager` (which is in `:app`).

## Notes for our build (anime-first)

For an anime-first ANI-KUTA fork, prioritize keeping:

1. **MyAnimeList** — largest anime user base, full OAuth, deletable. Must-have.
2. **AniList** — second-largest anime community, GraphQL, configurable score types, supports private tracking. Must-have.
3. **Simkl** — anime-only, no manga baggage to strip. Easy keep.
4. **Kitsu** — anime + manga but both sides are well-factored; password-grant login is simpler than browser OAuth. Keep if user demand exists.
5. **Shikimori** — anime + manga; Russian-community popular. Optional keep.
6. **Bangumi** — anime + manga; Chinese-community popular. Optional keep.
7. **Jellyfin** — anime-only enhanced tracker; pairs with the Jellyfin anime source extension. Keep if we keep the Jellyfin source.

Drop entirely (manga-only, no anime side):
- **Komga**, **Kavita**, **Suwayomi** — enhanced manga-server trackers with zero anime code. Trivially removable.
- **MangaUpdates** — manga-only list service. Removable.

Concrete cleanup work if we go anime-only:
- Remove the 4 manga-only tracker packages (`komga/`, `kavita/`, `suwayomi/`, `mangaupdates/`) and their entries in `TrackerManager`.
- Remove `EnhancedMangaTracker.kt`, `DeletableMangaTracker.kt`, `MangaTracker.kt` interfaces.
- Remove `MangaTrack` DB interface, `MangaTrackImpl`, `MangaTrackSearch`, the domain `MangaTrack` data class, `MangaTrackRepository`, `MangaTrackRepositoryImpl`, `MangaTrackMapper`, the `manga_sync` SQLDelight table + queries.
- Remove `AddMangaTracks`, `TrackChapter`, `RefreshMangaTracks`, `SyncChapterProgressWithTrack`, `DelayedMangaTrackingStore`, `DelayedMangaTrackingUpdateJob`.
- Remove `MangaTrackInfoDialog` and the manga-side track UI composables; remove the `mangaService` getter from `Tracker` (or make it nullable).
- For the 5 dual-support trackers (MAL, AniList, Kitsu, Shikimori, Bangumi), strip `MangaTracker` / `DeletableMangaTracker` from their class declarations and delete the manga DTOs + `*Api.searchManga/addLibManga/updateLibManga/findLibManga/deleteLibManga` methods + `bind(MangaTrack)` / `update(MangaTrack)` / `refresh(MangaTrack)` overloads.
- `TrackerManager` shrinks from 11 to 7 trackers (or fewer if Bangumi/Shikimori/Kitsu are dropped).

The split contract makes this clean — there is no shared `TrackService` to surgically bisect; each tracker class can be reduced to its `AnimeTracker` implementation alone.

## TODOs / open questions

- **Shikimori/Bangumi/Simkl token refresh:** not fully verified in this pass. The `*OAuth` DTOs are persisted via `saveToken`/`restoreToken` but I did not confirm whether each `Interceptor` actively refreshes expired tokens (the way MAL and Kitsu interceptors do). TODO: read `ShikimoriInterceptor.kt`, `BangumiInterceptor.kt`, `SimklInterceptor.kt` end-to-end.
- **Anilist token expiry:** `createOAuth` sets expiry to `now + 365 days`. Confirmed no refresh logic in `AnilistInterceptor` — after ~1 year the user must re-login. Verify this is the actual behaviour.
- **SQLDelight schema:** the `.sq` files for `anime_sync` / `manga_sync` were not read in this pass. TODO: locate and document the table schema + indexes (likely under `app/src/main/java/eu/kanade/tachiyomi/data/database/sqldelight/` or similar).
- **`MALApi.searchAnime` double-appends `q`:** lines 104–105 of `MyAnimeListApi.kt` call `.appendQueryParameter("q", query.take(64))` and then `.appendQueryParameter("q", query)` — appears to be an upstream bug; the second call adds a second `q=` parameter overriding the truncation. Not relevant to docs but worth flagging.
- **WorkManager job uniqueness:** `DelayedAnimeTrackingUpdateJob` and `DelayedMangaTrackingUpdateJob` both use tag-based `enqueueUniqueWork` — verify they don't collide if both fire simultaneously.
- **`Tracker.animeService` / `mangaService` casts:** these are unsafe casts with no `is` check. Callers that don't pre-filter will crash. Confirm all call sites use `filterIsInstance` or `is` checks (the ones I read do, but a full audit was not done).
- **Komga client override:** `Komga.client` is overridden to use `Dns.SYSTEM` but does NOT add the Komga interceptor to the client (Komga uses basic auth via URL or per-request headers from `KomgaApi`). Verify this is intentional.
