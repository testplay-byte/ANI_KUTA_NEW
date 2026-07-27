# 03-subsystems / `trackers.md` — Progress tracking (MAL, AniList, …)

> Aniyomi can sync reading/watching progress to external tracker services so
> the user's lists on MyAnimeList, AniList, Shikimori, Bangumi, Kitsu, Simkl,
> MangaUpdates, Komga, Kavita, Suwayomi, and Jellyfin stay in sync with what
> they read/watch inside the app. This doc covers the `Tracker` interface
> hierarchy, the per-service implementations, the dual manga/anime track
> models, the OAuth login flow, and the auto-track-on-read mechanism.

## Purpose

Trackers serve three jobs:

1. **Push** progress (status, last chapter/episode read, score, start/finish
   dates) from Aniyomi to the remote service whenever the user reads a
   chapter or watches an episode.
2. **Pull** the remote list back into Aniyomi when the user binds a tracker
   to a library entry (so an entry that's already at chapter 50 on MAL
   doesn't re-start at 0 in Aniyomi).
3. **Search** the remote for the right entry to bind to (some titles differ
   between source and tracker).

The system is **fully dual**: there is a `MangaTracker` interface (chapter
progress) and an `AnimeTracker` interface (episode progress), backed by
parallel domain models (`MangaTrack` / `AnimeTrack`), parallel repo tables
(`mangatracks` / `animetracks` — see [`data.md`](../02-modules/data.md)),
and parallel interactors (`TrackChapter` / `TrackEpisode`,
`AddMangaTracks` / `AddAnimeTracks`, etc.).

## The `Tracker` interface hierarchy

```
                        Tracker  (app/.../data/track/Tracker.kt)
                          │  id, name, client, isLoggedIn, login(), logout()
                          │  val animeService: AnimeTracker
                          │  val mangaService: MangaTracker
                          ▲
                BaseTracker  (BaseTracker.kt)
                  │  TrackPreferences-backed credentials
                  │  isLoggedInFlow from credential-pref changes
                  │  logout() clears credentials
                  ▲
   ┌──────────────┼───────────────┬───────────────┬─────────────┐
   │              │               │               │             │
 MangaTracker  AnimeTracker  Enhanced*Tracker  Deletable*Tracker
 (chapter API)  (episode API) (auto-bind for    (delete remote
                              specific sources)  list entry)
```

A concrete tracker class typically extends `BaseTracker` and implements a
combination of those interfaces depending on its capabilities. The `Tracker`
interface exposes `animeService` and `mangaService` as downcasts (with a
default that just `as`-casts `this`), so callers can always go
`tracker.mangaService.update(...)` / `tracker.animeService.update(...)`.

### `Tracker` (interface)

The base contract — every tracker has:

- `id: Long` — stable tracker id (assigned by `TrackerManager`, see below).
- `name: String`.
- `client: OkHttpClient` — `BaseTracker` returns the shared
  `NetworkHelper.client`; some trackers override to add their own
  interceptor or to bypass DNS-over-HTTPS (Komga/Jellyfin need raw IPs).
- `supportsReadingDates: Boolean` — whether the remote exposes start/finish
  dates (MAL/AniList/Kitsu/Shikimori yes; Bangumi no; Komga/Kavita/Suwayomi/
  Jellyfin/MangaUpdates/Simkl no).
- `supportsPrivateTracking: Boolean` — whether the remote has a "private
  list entry" flag (AniList, Bangumi, Kitsu yes; most others no).
- `getLogo()` / `getLogoColor()` — for the settings screen.
- `getCompletionStatus()` / `getScoreList()` — status code that means
  "completed" and the list of score strings to show in a picker.
- `login(username, password)` / `logout()` / `isLoggedIn` /
  `isLoggedInFlow` / `getUsername()` / `getPassword()` /
  `saveCredentials(...)`.

### `BaseTracker` (abstract)

Provides the credential storage on top of `TrackPreferences`:

- `trackPreferences.trackUsername(this)` /
  `trackPassword(this)` — keyed by tracker id, marked `privateKey` so they
  don't get backed up.
- `isLoggedIn = username.isNotEmpty() && password.isNotEmpty()`.
- `isLoggedInFlow` = combine of the two preference-change flows.
- `logout()` clears credentials (subclasses extend to also clear the OAuth
  token and reset their interceptor).

### `MangaTracker` / `AnimeTracker` (interfaces)

Define the per-side contract. Each has:

- `getStatusListManga()` / `getStatusListAnime()` — the statuses this
  tracker supports (e.g. READING/COMPLETED/ON_HOLD/DROPPED/PLAN_TO_READ).
- `getReadingStatus()` / `getRereadingStatus()` (manga) or
  `getWatchingStatus()` / `getRewatchingStatus()` (anime).
- `get10PointScore(track)` — normalize the tracker's score to a 0–10 double
  (used by the library's `TrackerMean` sort).
- `displayScore(track)` — human-readable score string.
- `update(track, didReadChapter/didWatchEpisode)` — push local progress to
  remote (and let the tracker auto-advance status to READING/WATCHING or
  COMPLETED).
- `bind(track, hasReadChapters/hasSeenEpisodes)` — called when first
  linking: looks up the remote list, copies personal fields, and either
  updates or creates the remote entry.
- `searchManga(query)` / `searchAnime(query)` — return a list of
  `*TrackSearch` results.
- `refresh(track)` — re-fetch the remote state.
- Convenience setters: `setRemoteMangaStatus`, `setRemoteLastChapterRead`,
  `setRemoteScore`, `setRemoteStartDate`, `setRemoteFinishDate`,
  `setRemotePrivate` (and the anime twins) — all of which mutate the track
  and call a private `updateRemote` that does `update(track)` then
  `insertTrack.await(...)` to persist locally.

### `EnhancedMangaTracker` / `EnhancedAnimeTracker`

"Enhanced" trackers never prompt the user to manually search & bind — they
auto-bind against a specific source class. Contract:

- `accept(source)` — `source::class.qualifiedName in getAcceptedSources()`.
- `getAcceptedSources()` — list of fully-qualified source class names
  (e.g. `eu.kanade.tachiyomi.extension.all.komga.Komga`).
- `loginNoop()` — saves dummy credentials so `isLoggedIn` returns true (the
  real auth lives in the source's own preferences).
- `match(manga/anime)` — returns zero or one `*TrackSearch` match (no UI
  prompt).
- `isTrackFrom(track, manga/anime, source)` — identity check.
- `migrateTrack(track, manga/anime, newSource)` — re-bind when migrating
  between sources.

Used by Komga, Kavita, Suwayomi (manga) and Jellyfin (anime). The
`AddMangaTracks.bindEnhancedTrackers(manga, source)` flow (invoked on add-to
-library from the History screen and elsewhere) iterates logged-in
enhanced trackers, calls `accept`, and if true calls `match` → `bind`.

### `DeletableMangaTracker` / `DeletableAnimeTracker`

A single-method interface: `suspend fun delete(track)`. Implemented by MAL,
AniList, Shikimori, Kitsu, MangaUpdates (manga side), so the user can
remove the remote list entry from Aniyomi's track info dialog.

## `TrackerManager`

`TrackerManager(context)` is the single registry of all trackers. It is
constructed in `BaseActivity` (Android `Context` is needed by some trackers
to build their own OkHttpClient) and bound via Injekt. It owns the canonical
instances and their **stable ids**:

| id    | Tracker       | Manga | Anime | Auth      | Notes                                    |
|-------|---------------|:-----:|:-----:|-----------|------------------------------------------|
| 1     | MyAnimeList   |   ✓   |   ✓   | OAuth     | `Deletable*Tracker` both sides           |
| 2     | AniList       |   ✓   |   ✓   | OAuth     | `Deletable*Tracker`; score-type pref     |
| 3     | Kitsu         |   ✓   |   ✓   | user/pass | `Deletable*Tracker`; OAuth token from pw |
| 4     | Shikimori     |   ✓   |   ✓   | OAuth     | `Deletable*Tracker`                      |
| 5     | Bangumi       |   ✓   |   ✓   | OAuth     | —                                        |
| 6     | Komga         |   ✓   |   —   | loginNoop | Enhanced; auth lives in Komga source pref |
| 7     | MangaUpdates  |   ✓   |   —   | user/pass | `DeletableMangaTracker`; manga-only      |
| 8     | Kavita        |   ✓   |   —   | loginNoop | Enhanced                                  |
| 9     | Suwayomi      |   ✓   |   —   | loginNoop | Enhanced                                  |
| 101   | Simkl         |   —   |   ✓   | OAuth     | Anime-only                                |
| 102   | Jellyfin      |   —   |   ✓   | loginNoop | Enhanced anime tracker                    |

Helper methods:

- `trackers: List<Tracker>` — all 11, in the order above.
- `loggedInTrackers()` / `loggedInTrackersFlow()` — filter to logged-in
  (combines every tracker's `isLoggedInFlow`).
- `get(id)` / `getAll(ids)` — id → tracker lookup.

The library ScreenModels use `loggedInTrackersFlow()` to drive the
per-tracker `TriState` filter row, and `getAll(loggedInTrackerIds)` in the
`TrackerMean` sort to fetch each tracker's `get10PointScore`.

## Per-tracker details

Each tracker lives in its own subpackage of `data/track/` and follows a
consistent layout: a main `<Tracker>.kt` class, an `<Tracker>Api.kt` (HTTP
calls), an `<Tracker>Interceptor.kt` (auth header injection), and a `dto/`
subfolder of `@Serializable` data classes. Status code constants are
defined as `const val` on the class companion.

### MyAnimeList (`myanimelist/`) — id 1
- **Sides:** manga + anime. **Auth:** OAuth 2.0 (PKCE-like auth code flow).
- **Sync:** status (READING/WATCHING/COMPLETED/ON_HOLD/DROPPED/PLAN_TO_*
  /REREADING/REWATCHING), `last_chapter_read` / `last_episode_seen`, score
  (0–10 integer), start/finish dates. `supportsReadingDates = true`.
- `searchManga` supports `id:<malId>` and `my:<title>` prefixes (the latter
  searches only the user's own list); otherwise generic search.
- `Deletable*Tracker` both sides.
- OAuth token persisted as JSON (`MALOAuth`) in `trackPreferences.trackToken`.

### AniList (`anilist/`) — id 2
- **Sides:** manga + anime. **Auth:** OAuth (token returned in URL fragment
  — `access_token=…&…`).
- **Sync:** GraphQL API. Supports private tracking. Five score types
  (`POINT_100`, `POINT_10`, `POINT_10_DECIMAL`, `POINT_5`, `POINT_3`) chosen
  by `trackPreferences.anilistScoreType()`; `getScoreList` / `indexToScore`
  / `get10PointScore` adapt.
- `supportsReadingDates = true`. `Deletable*Tracker` both sides.
- On construction, if the persisted token is an int (legacy APIv1) the user
  is logged out to force re-auth under APIv2.

### Shikimori (`shikimori/`) — id 4
- **Sides:** manga + anime. **Auth:** OAuth (code).
- **Sync:** status (READING/COMPLETED/ON_HOLD/DROPPED/PLAN_TO_READ/REREADING
  — shared codes for both sides; anime reuses the same numeric values),
  `last_chapter_read` / `last_episode_seen`, 0–10 integer score.
- `Deletable*Tracker` both sides. Token persisted as `SMOAuth` JSON.

### Bangumi (`bangumi/`) — id 5
- **Sides:** manga + anime. **Auth:** OAuth (code).
- **Sync:** status, progress, score (0–10). Supports private tracking.
- `supportsReadingDates = false`. Not `Deletable*Tracker`. Token persisted
  as `BGMOAuth`.

### Kitsu (`kitsu/`) — id 3
- **Sides:** manga + anime. **Auth:** username/password → server returns
  OAuth `access_token` (no browser flow).
- **Sync:** status (READING/WATCHING/COMPLETED/ON_HOLD/DROPPED/PLAN_TO_*),
  progress, score, start/finish dates. Supports private tracking.
- `supportsReadingDates = true`. `Deletable*Tracker` both sides. Token
  persisted as `KitsuOAuth`; `getUserId()` returns the password slot (the
  user id is needed for several API calls).

### Simkl (`simkl/`) — id 101
- **Sides:** anime only. **Auth:** OAuth (code).
- **Sync:** status (WATCHING/COMPLETED/ON_HOLD/NOT_INTERESTING/PLAN_TO_WATCH),
  `last_episode_seen`, score (0–10). No `supportsReadingDates`.
- Token persisted as `SimklOAuth`. Not `Deletable*Tracker`.

### MangaUpdates (`mangaupdates/`) — id 7
- **Sides:** manga only. **Auth:** username/password → session token.
- **Sync:** list (READING_LIST/WISH_LIST/COMPLETE_LIST/UNFINISHED_LIST/
  ON_HOLD_LIST — note "list" instead of "status"), progress (chapter
  number), 0–10 one-decimal score.
- `DeletableMangaTracker`. Token persisted as a session-token string in the
  password slot. Has its own `MangaUpdatesInterceptor` for auth headers.

### Komga (`komga/`) — id 6
- **Sides:** manga only. **Auth:** `loginNoop()` — real auth (API key +
  server URL) lives in the Komga source's preferences
  (`ConfigurableSource`).
- **Enhanced tracker:** only accepts source class
  `eu.kanade.tachiyomi.extension.all.komga.Komga`. No manual search/bind —
  `match(manga)` returns the entry directly.
- **Sync:** status (UNREAD/READING/COMPLETED), `last_chapter_read`. No
  score, no dates. `searchManga(query)` throws `Not yet implemented`
  (enhanced trackers don't need it).
- Uses its own `OkHttpClient` with `Dns.SYSTEM` (Komga servers may be raw
  IPs on the LAN).

### Kavita (`kavita/`) — id 8
- **Sides:** manga only. **Auth:** `loginNoop()` — Kavita source holds the
  real API key.
- **Enhanced tracker.** Same shape as Komga (UNREAD/READING/COMPLETED).
- Holds an in-memory `authentications: OAuth?` map (multi-server) and
  `loadOAuth()` reads it back from per-source preferences via
  `MangaSourceManager`.

### Suwayomi (`suwayomi/`) — id 9
- **Sides:** manga only. **Auth:** `loginNoop()`.
- **Enhanced tracker.** Talks to a local Suwayomi server's REST API
  (`SuwayomiApi`). Same status shape as Komga/Kavita.

### Jellyfin (`jellyfin/`) — id 102
- **Sides:** anime only. **Auth:** `loginNoop()` — Jellyfin source holds
  the real API key.
- **Enhanced anime tracker** (only one of its kind). Accepts
  `eu.kanade.tachiyomi.animeextension.all.jellyfin.Jellyfin`.
- **Sync:** status (UNSEEN/WATCHING/COMPLETED), `last_episode_seen`. No
  score. `searchAnime` throws "Not used".
- Own `OkHttpClient` with `Dns.SYSTEM` and `JellyfinInterceptor`.

## The dual manga/anime track models

The domain layer has parallel track models, repositories, and interactors:

| Manga (`tachiyomi.domain.track.manga.*`) | Anime (`tachiyomi.domain.track.anime.*`) |
|------------------------------------------|------------------------------------------|
| `model.MangaTrack`                       | `model.AnimeTrack`                       |
| `repository.MangaTrackRepository`        | `repository.AnimeTrackRepository`        |
| `interactor.GetMangaTracks`              | `interactor.GetAnimeTracks`              |
| `interactor.GetTracksPerManga`           | `interactor.GetTracksPerAnime`           |
| `interactor.InsertMangaTrack`            | `interactor.InsertAnimeTrack`            |
| `interactor.DeleteMangaTrack`            | `interactor.DeleteAnimeTrack`            |

Both `MangaTrack` and `AnimeTrack` are `data class`es with the same shape:
`id, {manga,anime}Id, trackerId, remoteId, libraryId?, title,
last{ChapterRead,EpisodeSeen}, total{Chapters,Episodes}, status, score,
remoteUrl, startDate, finishDate, private`.

There is also a legacy `data.database.models.{manga.MangaTrack,
anime.AnimeTrack}` interface with snake_case fields (`manga_id`,
`last_chapter_read`, …) used internally by the tracker implementations and
the `*TrackSearch` model classes. Conversion happens via
`toDomainTrack(idRequired)` / `toDbTrack()` extension functions in
`app/.../domain/track/{manga,anime}/model/`.

`TrackPreferences` (`app/.../domain/track/service/TrackPreferences.kt`)
holds the per-tracker credential slots (`trackUsername`, `trackPassword`,
`trackToken`, `trackAuthExpired`), the AniList score-type pref, and these
behavior toggles:

- `autoUpdateTrack()` — master switch: should the reader/player auto-push
  progress when a chapter/episode is finished?
- `trackOnAddingToLibrary()` — auto-bind enhanced trackers when adding to
  library.
- `autoUpdateTrackOnMarkRead()` — `AutoTrackState` enum (ALWAYS/ASK/NEVER):
  push when the user manually marks a chapter/episode as read.
- `showNextEpisodeAiringTime()` — UI toggle for the anime track info dialog.

## OAuth login flow

Five trackers use OAuth 2.0 via the browser: MAL, AniList, Shikimori,
Bangumi, Simkl. (Kitsu and MangaUpdates take username/password directly in
the app; Komga/Kavita/Suwayomi/Jellyfin `loginNoop()`.)

```
   Settings screen                Browser                 TrackLoginActivity
   ────────────────             ─────────                ───────────────────
   User taps "Login"  ─────────►OAuth authorize URL
                                with redirect URI:
                                aniyomi://<host>
                                                        (manifest-intent-filter
                                                         catches the URI)
                                User authorizes ────────►Browser redirects to
                                                          aniyomi://<host>?code=…
                                                                              │
                                                                              ▼
                                                       handleResult(data: Uri)
                                                       ── host match ──►
                                                          tracker.login(code)
                                                                              │
                                                                              ▼
                                                       tracker exchanges code
                                                       for OAuth token, fetches
                                                       username, saveCredentials
                                                                              │
                                                                              ▼
                                                       returnToSettings()
                                                       (finish + relaunch
                                                        MainActivity)
```

The redirect URIs use distinct hosts so the same `TrackLoginActivity` can
dispatch to the right tracker:

| Tracker    | Redirect host         | Code in fragment/query |
|------------|-----------------------|------------------------|
| AniList    | `anilist-auth`        | fragment: `access_token=…` |
| MAL        | `myanimelist-auth`    | query: `code=…`        |
| Shikimori  | `shikimori-auth`      | query: `code=…`        |
| Bangumi    | `bangumi-auth`        | query: `code=…`        |
| Simkl      | `simkl-auth`          | query: `code=…`        |

`TrackLoginActivity` (`app/.../ui/setting/track/TrackLoginActivity.kt`)
extends `BaseOAuthLoginActivity`, which just shows a `LoadingScreen`
Compose view while `handleResult(intent.data)` runs in `onCreate`. On
failure (no code) it logs out the tracker and returns to settings.

The settings UI that launches the browser lives in
`app/.../ui/setting/track/` (alongside the login activity). Each tracker
has its own `*SettingsScreen`/`*LoginDialog` Composable that opens the
browser via `Intent.ACTION_VIEW` on the OAuth URL.

## Auto-track on chapter/episode read

When the reader or player finishes a chapter/episode, the corresponding
ViewModel calls the auto-track interactor, which pushes the new progress to
every logged-in tracker bound to that entry.

```
   ReaderViewModel                  PlayerViewModel
   ──────────────                   ───────────────
   onChapterComplete(chapter)       onEpisodeComplete(episode)
        │                                │
        ▼                                ▼
   updateTrackChapterRead()         updateTrackEpisodeSeen()
   (skipped if incognitoMode or     (skipped if incognitoMode or
    !autoUpdateTrack)                 !autoUpdateTrack or
        │                              !hasTrackers)
        ▼                                ▼
   TrackChapter.await(ctx,          TrackEpisode.await(ctx,
     mangaId, chapterNumber)          animeId, episodeNumber)
        │                                │
        ▼                                ▼
   for each MangaTrack on mangaId:  for each AnimeTrack on animeId:
     - service.mangaService.refresh  - service.animeService.refresh
     - copy(lastChapterRead=…)       - copy(lastEpisodeSeen=…)
     - service.mangaService.update   - service.animeService.update
       (didReadChapter = true)         (didWatchEpisode = true)
     - insertTrack.await             - insertTrack.await
     - delayedStore.remove           - delayedStore.remove
   on failure:                       on failure (or offline):
     - delayedStore.add(trackId,       - delayedStore.add(trackId,
         lastChapterRead)                  lastEpisodeSeen)
     - Delayed*TrackingUpdateJob        - Delayed*TrackingUpdateJob
       .setupTask(ctx)                    .setupTask(ctx)
```

### Delayed tracking

If a tracker push fails (network error, 5xx, etc.) or the device is
offline, the progress is queued in a SharedPreferences-backed store
(`DelayedMangaTrackingStore` / `DelayedAnimeTrackingStore`, file
`tracking_queue`) and a one-shot WorkManager job
(`DelayedMangaTrackingUpdateJob` / `DelayedAnimeTrackingUpdateJob`) is
scheduled with `NetworkType.CONNECTED` constraint and exponential backoff.
The job replays every queued item via `TrackChapter`/`TrackEpisode`, clears
successful ones, and `Result.retry()`s until the queue is empty (giving up
after 3 attempts per item).

Note: anime tracks also check `context.isOnline()` and queue if offline,
**even if the tracker is otherwise ready** — manga doesn't do this pre-check
(it relies on the try/catch). The `hasTrackers` flag on `PlayerViewModel`
additionally gates the per-episode `saveEpisodeProgress` write so progress
still updates locally when offline + tracking.

### Track on manual mark-as-read

The `autoUpdateTrackOnMarkRead()` preference (`AutoTrackState` enum)
controls whether `TrackChapter`/`TrackEpisode` is also called when the user
manually flips a chapter/episode's read/seen flag from the chapter list or
updates screen. `ALWAYS` (default) → always track; `ASK` → prompt;
`NEVER` → never.

## Track search / bind UI

The per-entry tracking UI is reachable from `MangaScreen` / `AnimeScreen`
(the entry detail screen). It is **not** a Voyager screen — it's a Voyager
`Screen` pushed onto the navigator (`MangaTrackInfoDialogHomeScreen` /
`AnimeTrackInfoDialogHomeScreen`) so it has its own `*TrackInfoDialog`
Composable and a `StateScreenModel` for state.

Files: `app/.../ui/entries/{manga,anime}/track/`:

| File                              | Role                                                   |
|-----------------------------------|--------------------------------------------------------|
| `MangaTrackItem.kt`               | `data class MangaTrackItem(track: MangaTrack?, tracker: Tracker)` |
| `MangaTrackInfoDialog.kt`         | Compose dialog: per-tracker rows + search sheet        |
| `anime/AnimeTrackItem.kt`         | Anime twin                                             |
| `anime/AnimeTrackInfoDialog.kt`   | Anime twin                                             |

The dialog shows one row per `Tracker` (so the user can see at a glance
which trackers are bound and which are available). For an unbound tracker,
tapping opens a search sheet that calls `tracker.mangaService.searchManga
(query)` / `tracker.animeService.searchAnime(query)` and lists
`*TrackSearch` results. Tapping a result calls `AddMangaTracks.bind(...)` /
`AddAnimeTracks.bind(...)` which:

1. Calls `tracker.bind(item, hasReadChapters)` — looks up the remote list,
   copies personal fields, sets status (READING if local has read chapters,
   else PLAN_TO_READ).
2. `insertTrack.await(track)` — persists locally.
3. If local has read chapters past the remote's `lastChapterRead`, calls
   `tracker.setRemoteLastChapterRead(track, latestLocalReadChapterNumber)`
   to push local forward.
4. If local has a read history but the track has no start date, sets the
   start date from the earliest history entry.
5. `syncChapterProgressWithTrack.await(mangaId, track, tracker)` — for
   enhanced trackers, walks the local chapter list and marks read all
   chapters whose number is ≤ the remote's `lastChapterRead` (so binding a
   tracker that says "chapter 50" locally marks chapters 1–50 as read).

For an already-bound tracker, the row shows status / progress / score
pickers that call the `setRemote*` helpers (each of which does
`update(track)` + `insertTrack.await`). A "Delete" button (only on
`Deletable*Tracker` implementations) calls `tracker.delete(track)` and
removes the local row.

### `bindEnhancedTrackers`

When `TrackPreferences.trackOnAddingToLibrary()` is true and the user adds
an entry to the library, `AddMangaTracks.bindEnhancedTrackers(manga,
source)` (and the anime twin) runs:

```
trackerManager.loggedInTrackers()
    .filterIsInstance<EnhancedMangaTracker>()
    .filter { it.accept(source) }
    .forEach { service ->
        service.match(manga)?.let { track ->
            track.manga_id = manga.id
            service.mangaService.bind(track)         // no UI prompt
            insertTrack.await(track.toDomainTrack()!!)
            syncChapterProgressWithTrack.await(manga.id, track, service.mangaService)
        }
    }
```

This is what makes Komga/Kavita/Suwayomi/Jellyfin "just work" — the user
logs in once (via the source settings, not Aniyomi's tracker settings) and
every library entry from that source is auto-tracked.

## Refreshing tracks

`RefreshMangaTracks` / `RefreshAnimeTracks` (in
`app/.../domain/track/{manga,anime}/interactor/`) re-fetch every track for
an entry from the remote and persist the updated state. Invoked from the
track info dialog's refresh action and from the entry's "refresh metadata"
flow.

## Key files

### Tracker contracts (`app/src/main/java/eu/kanade/tachiyomi/data/track/`)

| File                          | Role                                                        |
|-------------------------------|-------------------------------------------------------------|
| `Tracker.kt`                  | Base interface                                              |
| `BaseTracker.kt`              | Credential-storage abstract class                            |
| `MangaTracker.kt`             | Manga-side contract + `setRemote*` helpers                  |
| `AnimeTracker.kt`             | Anime-side contract + `setRemote*` helpers                  |
| `EnhancedMangaTracker.kt`     | Auto-bind contract (manga)                                  |
| `EnhancedAnimeTracker.kt`     | Auto-bind contract (anime)                                  |
| `DeletableMangaTracker.kt`    | `delete(track)` contract (manga)                            |
| `DeletableAnimeTracker.kt`    | `delete(track)` contract (anime)                            |
| `TrackerManager.kt`           | Registry of all 11 trackers + `loggedInTrackersFlow`        |
| `model/MangaTrackSearch.kt`   | Search-result model (manga)                                 |
| `model/AnimeTrackSearch.kt`   | Search-result model (anime)                                 |

### Per-tracker packages (`app/src/main/java/eu/kanade/tachiyomi/data/track/<svc>/`)

Each package has a main `<Svc>.kt`, `<Svc>Api.kt`, `<Svc>Interceptor.kt`,
`<Svc>Utils.kt` (sometimes), and a `dto/` subfolder of `@Serializable`
classes.

| Package          | Notes                                                      |
|------------------|------------------------------------------------------------|
| `myanimelist/`   | OAuth; manga+anime; deletable both sides                   |
| `anilist/`       | OAuth (fragment); GraphQL; manga+anime; deletable; scores  |
| `shikimori/`     | OAuth; manga+anime; deletable                              |
| `bangumi/`       | OAuth; manga+anime; private tracking                       |
| `kitsu/`         | user/pass → OAuth; manga+anime; deletable; dates           |
| `simkl/`         | OAuth; anime-only                                          |
| `mangaupdates/`  | user/pass; manga-only; deletable                           |
| `komga/`         | Enhanced; manga-only; loginNoop                            |
| `kavita/`        | Enhanced; manga-only; loginNoop                            |
| `suwayomi/`      | Enhanced; manga-only; loginNoop                            |
| `jellyfin/`      | Enhanced anime tracker; loginNoop                          |

### Domain (`domain/src/main/java/tachiyomi/domain/track/`)

| File                                              | Role                              |
|---------------------------------------------------|-----------------------------------|
| `manga/model/MangaTrack.kt`                       | Manga track data class            |
| `anime/model/AnimeTrack.kt`                       | Anime track data class            |
| `manga/repository/MangaTrackRepository.kt`        | Repo interface                    |
| `anime/repository/AnimeTrackRepository.kt`        | Repo interface                    |
| `manga/interactor/{GetMangaTracks,GetTracksPerManga,InsertMangaTrack,DeleteMangaTrack}.kt` | Manga interactors |
| `anime/interactor/{GetAnimeTracks,GetTracksPerAnime,InsertAnimeTrack,DeleteAnimeTrack}.kt` | Anime interactors |

### App-level track interactors (`app/src/main/java/eu/kanade/domain/track/`)

| File                                              | Role                              |
|---------------------------------------------------|-----------------------------------|
| `manga/interactor/AddMangaTracks.kt`              | `bind` + `bindEnhancedTrackers`   |
| `anime/interactor/AddAnimeTracks.kt`              | Anime twin                        |
| `manga/interactor/TrackChapter.kt`                | Auto-track on chapter read        |
| `anime/interactor/TrackEpisode.kt`                | Auto-track on episode seen        |
| `manga/interactor/SyncChapterProgressWithTrack.kt`| Pull remote→local on bind         |
| `anime/interactor/SyncEpisodeProgressWithTrack.kt`| Anime twin                        |
| `manga/interactor/RefreshMangaTracks.kt`          | Re-fetch all tracks for an entry  |
| `anime/interactor/RefreshAnimeTracks.kt`          | Anime twin                        |
| `manga/store/DelayedMangaTrackingStore.kt`        | SharedPrefs queue for failed pushes |
| `anime/store/DelayedAnimeTrackingStore.kt`        | Anime twin                        |
| `manga/service/DelayedMangaTrackingUpdateJob.kt`  | WorkManager replay job            |
| `anime/service/DelayedAnimeTrackingUpdateJob.kt`  | Anime twin                        |
| `service/TrackPreferences.kt`                     | Credentials + behavior prefs      |
| `model/AutoTrackState.kt`                         | `ALWAYS/ASK/NEVER` enum           |

### UI (`app/src/main/java/eu/kanade/tachiyomi/`)

| File                                              | Role                              |
|---------------------------------------------------|-----------------------------------|
| `ui/setting/track/BaseOAuthLoginActivity.kt`      | OAuth redirect receiver base      |
| `ui/setting/track/TrackLoginActivity.kt`          | Dispatches redirect to tracker    |
| `ui/setting/track/<svc>/…`                        | Per-tracker settings Composables  |
| `ui/entries/manga/track/MangaTrackInfoDialog.kt`  | Bind + edit UI (manga)            |
| `ui/entries/manga/track/MangaTrackItem.kt`        | `(track, tracker)` pair           |
| `ui/entries/anime/track/AnimeTrackInfoDialog.kt`  | Anime twin                        |
| `ui/entries/anime/track/AnimeTrackItem.kt`        | Anime twin                        |

## See also

- [`library-management.md`](library-management.md) — the library's per-tracker `TriState` filter and the `TrackerMean` sort.
- [`../02-modules/app.md`](../02-modules/app.md) — `TrackLoginActivity` is registered there as an `intent-filter` for `aniyomi://` URIs.
- [`../02-modules/data.md`](../02-modules/data.md) — the `mangatracks` / `animetracks` tables.
- [`../02-modules/domain.md`](../02-modules/domain.md) — the Interactor pattern shared by all track use cases.
- [`../05-key-flows/track-progress.md`](../05-key-flows/track-progress.md) — end-to-end "log in to MAL and sync a chapter" user flow.
- [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md) — every `TrackPreferences` key.
