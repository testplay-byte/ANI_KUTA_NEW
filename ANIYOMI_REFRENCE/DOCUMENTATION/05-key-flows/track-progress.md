# 05-key-flows / Sync progress to a tracker

> Trace a chapter read / episode watched → `TrackChapter` / `TrackEpisode`
> → tracker API call (e.g. AniList GraphQL mutation) → local `mangatracks`
> / `animetracks` row updated. Also covers the first-time OAuth login
> flow that has to happen before any of this works.

Aniyomi syncs reading/watching progress to **11** tracker services
(MAL, AniList, Shikimori, Bangumi, Kitsu, Simkl, MangaUpdates, Komga,
Kavita, Suwayomi, Jellyfin). See [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md)
for the full tracker catalogue; this doc focuses on the end-to-end flow
for a single progress push.

## Overview

```
READER: chapter finished
   │
   ▼
ReaderViewModel.updateChapterProgressOnComplete(chapter)
   ├─ chapter.read = true
   ├─ updateTrackChapterRead(chapter)
   │   (skipped if incognitoMode || !trackPreferences.autoUpdateTrack().get())
   │     └─ trackChapter.await(context, manga.id, chapter.chapter_number)
   └─ deleteChapterIfNeeded(chapter)

PLAYER: episode finished (>= 85% watched by default)
   │
   ▼
PlayerViewModel.updateEpisodeProgressOnComplete(episode)
   ├─ episode.seen = true
   ├─ updateTrackEpisodeSeen(episode)
   │   (skipped if incognitoMode || !hasTrackers || !autoUpdateTrack)
   │     └─ trackEpisode.await(context, anime.id, episode.episode_number)
   └─ deleteEpisodeIfNeeded(episode)
   │
   ▼
TrackChapter.await / TrackEpisode.await
   ├─ tracks = getTracks.await(mangaId)   ← List<MangaTrack> from SQLDelight
   ├─ for each track:
   │     ├─ service = trackerManager.get(track.trackerId)
   │     ├─ skip if (!isLoggedIn || chapterNumber <= track.lastChapterRead)
   │     └─ async {
   │           try {
   │             refreshedTrack = service.mangaService.refresh(track)   ← GET remote list entry
   │             updatedTrack = refreshedTrack.copy(lastChapterRead = chapterNumber)
   │             service.mangaService.update(updatedTrack, didReadChapter = true)  ← e.g. AniList GraphQL mutation
   │             insertTrack.await(updatedTrack)            ← UPDATE mangatracks SET …
   │             delayedTrackingStore.removeMangaItem(track.id)
   │           } catch (e) {
   │             delayedTrackingStore.addManga(track.id, chapterNumber)  ← queue for retry
   │             DelayedMangaTrackingUpdateJob.setupTask(context)        ← WorkManager one-shot
   │           }
   │         }
   └─ awaitAll + log exceptions
```

## Step-by-step

### 1. The trigger

For the manga side, `ReaderViewModel.updateChapterProgressOnComplete`
(see [`read-manga.md`](read-manga.md)) calls:

```kotlin
private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
    if (incognitoMode) return
    if (!trackPreferences.autoUpdateTrack().get()) return
    val manga = manga ?: return
    val context = Injekt.get<Application>()
    viewModelScope.launchNonCancellable {
        trackChapter.await(context, manga.id, readerChapter.chapter.chapter_number.toDouble())
    }
}
```

The anime side, `PlayerViewModel.updateTrackEpisodeSeen` (see
[`watch-anime.md`](watch-anime.md)) is structurally identical but has an
extra `hasTrackers` gate (it skips when the anime has no bound trackers):

```kotlin
private fun updateTrackEpisodeSeen(episode: Episode) {
    if (basePreferences.incognitoMode().get() || !hasTrackers) return
    if (!trackPreferences.autoUpdateTrack().get()) return
    val anime = currentAnime.value ?: return
    val context = Injekt.get<Application>()
    viewModelScope.launchNonCancellable {
        trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
    }
}
```

The trigger can also be a manual mark-as-read from the chapter list /
updates screen — in that case, the `autoUpdateTrackOnMarkRead()`
preference (`AutoTrackState` enum: ALWAYS/ASK/NEVER) controls whether
`TrackChapter`/`TrackEpisode` is called.

### 2. `TrackChapter.await` / `TrackEpisode.await`

`TrackChapter` (`../ANIYOMI/app/src/main/java/eu/kanade/domain/track/manga/interactor/TrackChapter.kt`)
is the orchestrator. The anime twin `TrackEpisode` is structurally
identical. The body:

```kotlin
suspend fun await(context, mangaId, chapterNumber, setupJobOnFailure = true) =
    withNonCancellableContext {
        val tracks = getTracks.await(mangaId)
        if (tracks.isEmpty()) return@withNonCancellableContext

        tracks.mapNotNull { track ->
            val service = trackerManager.get(track.trackerId)
            if (service == null || !service.isLoggedIn ||
                chapterNumber <= track.lastChapterRead) {
                return@mapNotNull null        // skip unbound / logged-out / already-read
            }

            async {
                runCatching {
                    try {
                        val refreshedTrack = service.mangaService
                            .refresh(track.toDbTrack())           // GET remote list entry
                            .toDomainTrack(idRequired = true)!!
                            .copy(lastChapterRead = chapterNumber)
                        service.mangaService.update(updatedTrack.toDbTrack(), true)  // PUSH
                        insertTrack.await(updatedTrack)           // UPDATE mangatracks
                        delayedTrackingStore.removeMangaItem(track.id)
                    } catch (e: Exception) {
                        delayedTrackingStore.addManga(track.id, chapterNumber)
                        if (setupJobOnFailure) {
                            DelayedMangaTrackingUpdateJob.setupTask(context)
                        }
                        throw e
                    }
                }
            }
        }
            .awaitAll()
            .mapNotNull { it.exceptionOrNull() }
            .forEach { logcat(LogPriority.INFO, it) }
    }
```

Key behaviours:

- **`withNonCancellableContext`** — the whole push runs even if the
  reader/player is destroyed before it finishes.
- **Per-track `async`** — every bound tracker is pushed in parallel.
- **Skip if not logged in** — a track row may exist for a tracker the
  user has since logged out of; that row is silently skipped.
- **Skip if `chapterNumber <= track.lastChapterRead`** — never push
  backwards (a re-read of an earlier chapter doesn't regress the
  tracker).
- **Refresh-then-update** — the local track row may be stale, so
  `refresh(track)` GETs the current remote state first. The
  `lastChapterRead` is then set to the just-read chapter number, and
  `update(track, didReadChapter = true)` pushes the merged state back.
  The `didReadChapter = true` flag lets the tracker auto-advance status
  (e.g. from `Plan to read` to `Reading`, or to `Completed` when the
  chapter number equals `totalChapters`).
- **`insertTrack.await`** — persists the updated local track row
  (`UPDATE mangatracks SET last_chapter_read = ?, status = ?, … WHERE
  _id = ?`).
- **Delayed queue on failure** — any exception (network error, 5xx,
  logged-out mid-flight) queues the item in `DelayedMangaTrackingStore`
  and arms `DelayedMangaTrackingUpdateJob` (WorkManager one-shot,
  `NetworkType.CONNECTED`, exponential backoff). The job replays the
  queue when connectivity returns, retrying each item up to 3 times.

The `hasTrackers` asymmetry (anime gates `updateTrackEpisodeSeen` on
`hasTrackers`; manga doesn't gate `updateTrackChapterRead`) is
documented in [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md)
and [`../03-subsystems/history.md`](../03-subsystems/history.md).

### 3. Tracker API call — the AniList example

Each tracker's `MangaTracker.update(track, didReadChapter)` and
`AnimeTracker.update(track, didWatchEpisode)` is implemented by the
tracker class. For AniList
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/Anilist.kt`),
`update` builds a GraphQL mutation and POSTs it to
`https://graphql.anilist.co`:

```graphql
mutation($mediaId: Int, $progress: Int, $status: MediaListStatus) {
    SaveMediaListEntry(mediaId: $mediaId, progress: $progress, status: $status) {
        id
        …
    }
}
```

The `AnilistInterceptor`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/AnilistInterceptor.kt`)
injects the OAuth `access_token` (stored in `TrackPreferences.trackToken(this)`)
as the `Authorization: Bearer …` header on every request. If the token
has expired (the API returns 401), the interceptor silently uses the
saved `refresh_token` to get a new `access_token` and retries the
request.

Other trackers' `update` implementations:

| Tracker | API style | Endpoint |
|---|---|---|
| MAL | REST (JSON) | `PUT /manga/{id}/my_list_status` (with `num_chapters_read`) — `MyAnimeListApi` |
| Shikimori | REST (JSON) | `PATCH /v2/user_rates/{id}` — `ShikimoriApi` |
| Bangumi | REST (JSON) | `POST /v0/users/-/collections/{subject_id}` — `BangumiApi` |
| Kitsu | JSON:API | `PATCH /library-entries/{id}` — `KitsuApi` |
| Simkl | REST (JSON) | `POST /sync/history` — `SimklApi` (anime-only) |
| MangaUpdates | REST (JSON) | `PUT /v1/series/{id}/track` — `MangaUpdatesApi` (manga-only) |
| Komga / Kavita / Suwayomi | REST (JSON) | server's own `readList`/`progress` endpoint (manga-only, enhanced) |
| Jellyfin | REST (JSON) | `POST /Users/{userId}/Items/{id}/UserData` — `JellyfinApi` (anime-only, enhanced) |

The "enhanced" trackers (Komga, Kavita, Suwayomi, Jellyfin) never
prompt the user to search & bind — they auto-bind against a specific
source class via `bindEnhancedTrackers` (see step 5). Their `update`
calls are still per-row just like the OAuth trackers.

### 4. First-time setup — OAuth login

Before any of this works, the user has to log in to each tracker. Five
trackers (MAL, AniList, Shikimori, Bangumi, Simkl) use OAuth 2.0 via
the browser; two (Kitsu, MangaUpdates) take username/password directly
in-app; the four enhanced trackers use `loginNoop()` (the user logs in
via the source's own settings screen, not Aniyomi's).

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

The redirect URIs use distinct hosts so the same `TrackLoginActivity`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/setting/track/TrackLoginActivity.kt`)
can dispatch to the right tracker:

| Tracker | Redirect host | Code in fragment/query |
|---|---|---|
| AniList | `anilist-auth` | fragment: `access_token=…` |
| MAL | `myanimelist-auth` | query: `code=…` |
| Shikimori | `shikimori-auth` | query: `code=…` |
| Bangumi | `bangumi-auth` | query: `code=…` |
| Simkl | `simkl-auth` | query: `code=…` |

`TrackLoginActivity` extends `BaseOAuthLoginActivity`, which just shows
a `LoadingScreen` Compose view while `handleResult(intent.data)` runs
in `onCreate`. On failure (no code) it logs out the tracker and returns
to settings.

`tracker.login(code)` exchanges the code for an OAuth token, fetches
the username (so the settings UI can show "Logged in as @user"), and
calls `saveCredentials(username, token)` — which writes the
`trackUsername` and `trackPassword` (a misnomer; it's the OAuth token)
preferences for that tracker, marked `privateKey` so they don't get
backed up.

### 5. Track search / bind

Once logged in, the user has to **bind** a tracker to a library entry.
From the manga / anime detail screen, the user opens the track dialog
(`MangaTrackInfoDialogHomeScreen` / `AnimeTrackInfoDialogHomeScreen`,
pushed as a Voyager `Screen`). The dialog shows one row per `Tracker`;
for an unbound tracker, tapping opens a search sheet that calls
`tracker.mangaService.searchManga(query)` / `tracker.animeService.searchAnime(query)`
and lists `*TrackSearch` results. Tapping a result calls
`AddMangaTracks.bind(...)` / `AddAnimeTracks.bind(...)`:

1. `tracker.bind(item, hasReadChapters)` — looks up the remote list,
   copies personal fields (status, score, start/finish dates), sets
   status (`READING` if local has read chapters, else `PLAN_TO_READ`).
2. `insertTrack.await(track)` — persists locally
   (`INSERT INTO mangatracks (manga_id, tracker_id, remote_id, …)`).
3. If local has read chapters past the remote's `lastChapterRead`,
   `tracker.setRemoteLastChapterRead(track, latestLocalReadChapterNumber)`
   pushes local forward.
4. If local has a read history but the track has no start date, sets
   the start date from the earliest history entry.
5. `syncChapterProgressWithTrack.await(mangaId, track, tracker)` — for
   enhanced trackers, walks the local chapter list and marks read all
   chapters whose number is ≤ the remote's `lastChapterRead` (so
   binding a tracker that says "chapter 50" locally marks chapters
   1–50 as read).

For an already-bound tracker, the row shows status / progress / score
pickers that call the `setRemote*` helpers (each does `update(track)` +
`insertTrack.await`). A "Delete" button (only on `Deletable*Tracker`
implementations) calls `tracker.delete(track)` and removes the local
row.

### 6. `bindEnhancedTrackers` — auto-bind on add-to-library

When `TrackPreferences.trackOnAddingToLibrary()` is true and the user
adds an entry to the library (see [`add-to-library.md`](add-to-library.md)),
`AddMangaTracks.bindEnhancedTrackers(manga, source)` (and the anime
twin) runs:

```kotlin
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

This is what makes Komga/Kavita/Suwayomi/Jellyfin "just work" — the
user logs in once (via the source settings, not Aniyomi's tracker
settings) and every library entry from that source is auto-tracked.

## Sequence diagram

```
READER: chapter complete → ReaderViewModel.updateChapterProgressOnComplete
   └─ updateTrackChapterRead(chapter)
        (skip if incognitoMode || !autoUpdateTrack)
        └─ trackChapter.await(ctx, manga.id, chapterNumber)
             ├─ tracks = getTracks.await(mangaId)  ── SQLDelight: SELECT * FROM mangatracks WHERE manga_id=?
             └─ for each track (parallel async):
                  ├─ service = trackerManager.get(track.trackerId)
                  ├─ skip if !isLoggedIn || chapterNumber <= track.lastChapterRead
                  └─ try:
                       ├─ refreshedTrack = service.mangaService.refresh(track)   ← GET remote list entry
                       │     e.g. AniList: GraphQL query SaveMediaListEntry
                       │     (AnilistInterceptor injects Bearer token; refreshes on 401)
                       ├─ updatedTrack = refreshedTrack.copy(lastChapterRead = chapterNumber)
                       ├─ service.mangaService.update(updatedTrack, didReadChapter = true)  ← POST/PUT
                       │     e.g. AniList: GraphQL mutation SaveMediaListEntry(progress: N, status: READING)
                       ├─ insertTrack.await(updatedTrack)            ── UPDATE mangatracks SET last_chapter_read=?, status=?, …
                       └─ delayedTrackingStore.removeMangaItem(track.id)
                     catch:
                       ├─ delayedTrackingStore.addManga(track.id, chapterNumber)
                       └─ DelayedMangaTrackingUpdateJob.setupTask(ctx)   ── WorkManager one-shot
                                                                                          │
                                                                                          ▼ (later, when online)
                                                          DelayedMangaTrackingUpdateJob.doWork()
                                                            └─ replay queue via TrackChapter.await(..., setupJobOnFailure = false)
```

## See also

- [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) — the full tracker subsystem deep dive (interface hierarchy, all 11 trackers, OAuth flows, delayed-tracking queue, enhanced-tracker auto-bind).
- [`read-manga.md`](read-manga.md) and [`watch-anime.md`](watch-anime.md) — where the `TrackChapter` / `TrackEpisode` call originates.
- [`add-to-library.md`](add-to-library.md) — the `bindEnhancedTrackers` hook on add-to-library.
- [`../03-subsystems/history.md`](../03-subsystems/history.md) — the incognito-mode asymmetry between manga and anime.
- [`backup-flow.md`](backup-flow.md) — track bindings are backed up (without OAuth tokens).
