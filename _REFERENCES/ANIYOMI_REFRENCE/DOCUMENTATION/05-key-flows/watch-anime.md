# 05-key-flows / Open & watch an anime episode

> Trace an episode tap through the legacy `PlayerActivity` →
> `PlayerViewModel` → `EpisodeLoader` → hoster/video resolution → MPV
> playback → `PlayerObserver` callbacks → progress writes, and back out
> via history + tracker writes.

The player is the anime-only counterpart to the manga reader. Like the
reader, it is a legacy View-based Activity (it wraps an `AniyomiMPVView`
whose `SurfaceView` is owned by the MPV JNI library). See
[`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) for
the full subsystem deep dive; this doc focuses on the end-to-end flow.

## Overview

```
USER: tap episode row (anywhere)
   │
   ▼
context.startActivity(PlayerActivity.newIntent(context, animeId, episodeId, hostList, hostIdx, vidIdx))
   │
   ▼
PlayerActivity.onCreate()
   ├─ setupPlayerMPV()       ── writes mpv.conf / input.conf, copies fonts/scripts, player.initialize(...)
   ├─ setupPlayerAudio()     ── AudioFocusRequest
   ├─ setupMediaSession()    ── MediaSession for lock-screen controls
   ├─ binding.controls.setContent { TachiyomiTheme { PlayerControls(viewModel, …) } }
   └─ onNewIntent(intent)
        ├─ read animeId / episodeId / hostList / hostIdx / vidIdx
        ├─ saveCurrentEpisodeWatchingProgress()   ← flush prior episode
        └─ viewModel.init(animeId, episodeId, hostList, hostIdx, vidIdx)
              │
              ▼
PlayerViewModel.init(...)
   ├─ getAnime.await(animeId)
   ├─ sourceManager.isInitialized.first { it }
   ├─ checkTrackers(anime)  ── sets hasTrackers (affects incognito gate)
   ├─ initEpisodeList(anime)  ── runBlocking { getEpisodesByAnimeId.await } → sort → filter → map to Episode
   ├─ source = sourceManager.getOrStub(anime.source)
   ├─ write MPV user-data/current-anime/{anime-title,intro-length,category}
   └─ if hostList blank:
        EpisodeLoader.getHosters(episode, anime, source)
          ├─ isDownload(episode, anime)?     → buildVideo() → listOf(video).toHosterList()
          ├─ source is AnimeHttpSource?      → source.getHosterList(episode).sortHosters()
          │                                     (legacy ext-lib <1.6: source.getVideoList(episode).sortVideos())
          └─ source is LocalAnimeSource?     → LocalAnimeSourceFileSystem lookup → Video.toHosterList()
   │
   ▼
PlayerViewModel.loadHosters(source, hosterList, hosterIdx, vidIdx)
   ├─ For each hoster in parallel (async on IO):
   │     EpisodeLoader.loadHosterVideos(source, hoster)  ── HosterState.Ready (or Idle/Error)
   ├─ If a preferred video found: loadVideo(source, video, hosterIdx, vidIdx)
   └─ Else: HosterLoader.selectBestVideo(hosterState) → loadVideo(...)
        │
        ▼
PlayerViewModel.loadVideo(source, video, hosterIdx, videoIdx)
   ├─ HosterLoader.getResolvedVideo(source, video)   ← source.resolveVideo if !initialized
   ├─ _currentVideo.update { resolvedVideo }
   ├─ viewModelScope.launchIO { loadThumbnails(resolvedVideo, source) }   ← tile preview
   └─ activity.setVideo(resolvedVideo)
        │
        ▼
PlayerActivity.setVideo(video)
   ├─ setHttpOptions(video)              ← push video.headers / source.headers as MPV http-header-fields
   ├─ set "start" = resumePosition / episode.last_second_seen / 0
   ├─ build videoOptions from video.mpvArgs
   ├─ if Torrserver enabled & url is magnet/.torrent/content://torrent:
   │     TorrentServerService.start()
   │     torrentLinkHandler(url, title, videoOptions)
   │       ├─ content:// → uploadTorrent → torrentPlayLink
   │       ├─ magnet:?   → addTorrent     → torrentPlayLink
   │       └─ MPVLib.command(["loadfile", torrentPlayLink, "replace", "0", videoOptions])
   └─ else:
         MPVLib.command(["loadfile", parseVideoUrl(video.videoUrl), "replace", "0", videoOptions])
   │
   ▼
MPV loads URL ─── MPV_EVENT_FILE_LOADED ──▶ PlayerActivity.fileLoaded()
   ├─ setMpvOptions()       ← read metadata for Video.MPV_ARGS_TAG overrides
   ├─ setMpvMediaTitle()
   ├─ setupPlayerOrientation()
   ├─ setupChapters()       ← merge Video.timestamps + MPV chapters + AniSkip
   └─ setupTracks()         ← load Video.audioTracks / subtitleTracks as MPV audio-add / sub-add
   │
USER: watches
   │
   ▼
MPV time-pos property changes (1 Hz) ─▶ PlayerObserver.eventProperty("time-pos", Long)
   └─ PlayerActivity.onObserverEvent("time-pos", value)  →  viewModel.onSecondReached(pos, duration)
        ├─ currentEp.last_second_seen = pos; currentEp.total_seconds = duration
        ├─ if pos >= duration * progressPreference (0.85 default) && !incognito || hasTrackers:
        │     updateEpisodeProgressOnComplete(currentEp)
        │       ├─ currentEp.seen = true
        │       ├─ updateTrackEpisodeSeen(currentEp)   →  TrackEpisode.await(...)  (see track-progress.md)
        │       └─ deleteEpisodeIfNeeded(currentEp)
        ├─ saveWatchingProgress(currentEp)
        │     ├─ saveEpisodeProgress  → updateEpisode.await(EpisodeUpdate(seen, lastSecondSeen, totalSeconds, …))
        │     └─ saveEpisodeHistory    → upsertHistory.await(AnimeHistoryUpdate(episodeId, seenAt))
        └─ if pos/duration > 0.35: downloadNextEpisodes()    → see download-chapter.md
```

## Step-by-step

### 1. The episode tap → `PlayerActivity`

```kotlin
context.startActivity(
    PlayerActivity.newIntent(context, animeId, episodeId, hostList, hostIndex, vidIndex)
)
```

`hostList` / `hostIndex` / `vidIndex` are optional; they let a deep link
pre-select a video quality (used by Aniyomi's external intents and the
"open in external player" path). Most call sites pass empty values.

Call sites include: the anime details screen's episode list
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreen.kt`),
the History tab (`AnimeHistoryTab.kt`), the Updates tab
(`AnimeUpdatesTab.kt`), notification actions, and deep links.

`PlayerActivity` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`)
is **not** a Voyager screen — it is a `BaseActivity` launched via
`startActivity`. It has not been migrated to Compose because it wraps
`AniyomiMPVView` (extends `is.xyz.mpv.BaseMPVView`), a `FrameLayout` whose
`SurfaceView` is owned by the MPV JNI library. Everything **around** the
surface — on-screen controls, sheets, panels, dialogs — is Compose,
rendered via `binding.controls.setContent { TachiyomiTheme { PlayerControls(...) } }`.

### 2. `PlayerActivity.onCreate` → `setupPlayerMPV` → `onNewIntent`

`onCreate`:

1. `enableEdgeToEdge()`; set up the uncaught-exception handler.
2. **`setupPlayerMPV()`** — writes `mpv.conf` and `input.conf` to the
   per-app MPV config dir, copies `subfont.ttf` and `cacert.pem` from
   assets, copies any user-installed scripts/fonts/shaders/script-opts
   from `StorageManager`'s directories, and finally calls
   `player.initialize(configDir, cacheDir, logLvl)`. This is the call
   that loads `libmpv.so` and creates the MPV core.
3. `setupPlayerAudio()` — registers an `AudioFocusRequestCompat` with
   duck-on-transient-loss and pause-on-loss, with a `restoreAudioFocus`
   lambda that resumes when focus returns.
4. `setupMediaSession()` — a `MediaSession` for lock-screen / Bluetooth
   controls.
5. `setupPlayerOrientation()`.
6. Observes `viewModel.eventFlow`.
7. Sets `binding.controls` Compose content.
8. `onNewIntent(intent)` — reads `animeId` / `episodeId` / `hostList` /
   `hostIndex` / `vidIndex`, calls `saveCurrentEpisodeWatchingProgress()`
   to flush the prior episode's progress, then `viewModel.init(...)`
   followed by `viewModel.loadHosters(...)`.

### 3. `PlayerViewModel.init` → episode list + hoster list

`PlayerViewModel.init(animeId, initialEpisodeId, hostList, hostIndex, vidIndex)`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`)
is a `suspend fun`:

1. `getAnime.await(animeId)` — single-row SQLDelight query.
2. `sourceManager.isInitialized.first { it }` — wait for the
   `AnimeExtensionManager` initial scan.
3. `checkTrackers(anime)` — `runBlocking { getTracks.await(anime.id) }`
   and sets `hasTrackers`. This flag gates the incognito-mode behavior
   (see step 6).
4. `initEpisodeList(anime)` — `runBlocking {
   getEpisodesByAnimeId.await(anime.id) }`, then
   `sortedWith(getEpisodeSort(anime, sortDescending = false))`, then
   filtered by `downloadedOnly`, then mapped to legacy `Episode` objects
   (`toDbEpisode()`).
5. `source = sourceManager.getOrStub(anime.source)` — live `AnimeSource`
   or `StubAnimeSource`.
6. Writes MPV `user-data/current-anime/{anime-title,intro-length,category}`
   properties (the `intro-length` is read by the AniSkip Lua script).
7. Resolves the initial hoster list:
   - If `hostList` is non-blank (deep link), parse it via `toHosterList()`.
   - Else call `EpisodeLoader.getHosters(episode, anime, source)`.

### 4. `EpisodeLoader.getHosters` — dispatch on download / source type

`EpisodeLoader.getHosters(episode, anime, source)` (in
`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt`)
dispatches on download state and source type:

```
                       ┌────────────────────────────────────────┐
                       │ EpisodeLoader.getHosters(episode,...)  │
                       └──────────────────┬─────────────────────┘
                                          │
        isDownload(episode, anime)? ──────┤
            (AnimeDownloadManager         │
             .isEpisodeDownloaded)        │
                ┌─────────────────────────┴──────────────────┐
                ▼                                               ▼
   getHostersOnDownloaded                       source is AnimeHttpSource?
   downloadManager.buildVideo(...)                  │              │
       → listOf(video).toHosterList()               ▼              ▼
                                          getHostersOnHttp     source is LocalAnimeSource?
                                          │                        │
                                          ▼                        ▼
   source.getHosterList(episode)         getHostersOnLocal
   .sortHosters()                         (LocalAnimeSourceFileSystem lookup → Video)
   OR (legacy ext-lib <1.6:)
   source.getVideoList(episode)
       .sortVideos().toHosterList()
```

The `checkHasHosters(source)` reflection (looking for `getHosterList` /
`hosterListRequest` / `hosterListParse` methods on the source class
hierarchy) is a backwards-compat shim for extensions built against
ext-lib <1.6 (before the hosters API existed); such sources fall back to
the flat `getVideoList(episode)` and wrap the result as a single
pseudo-hoster (`NO_HOSTER_LIST`). See
[`../02-modules/source-api.md`](../02-modules/source-api.md) for the
hoster/Video contract.

### 5. `loadHosters` → `loadVideo` → `setVideo`

`PlayerViewModel.loadHosters(source, hosterList, hosterIdx, vidIdx)`:

- For each hoster in parallel on `Dispatchers.IO`:
  `EpisodeLoader.loadHosterVideos(source, hoster)` returns a
  `HosterState.Ready` (or `Idle` if `lazy` and not forced, or `Error`).
- If a "preferred" video was found in any hoster (and the hoster index
  matches the requested one): `loadVideo(source, video, hosterIdx, vidIdx)`.
- Else: `HosterLoader.selectBestVideo(hosterState)` returns the
  `(hosterIdx, vidIdx)` pair of the first video marked `preferred` (or
  the first non-empty-URL video), and `loadVideo(source, bestVideo,
  hosterIdx, vidIdx)` is called.

`loadVideo`:

1. `HosterLoader.getResolvedVideo(source, video)` — for `AnimeHttpSource`s
   and not-yet-initialized videos, calls `source.resolveVideo(video)`
   (the source may need another HTTP call to resolve a placeholder URL).
2. `_currentVideo.update { resolvedVideo }`.
3. `viewModelScope.launchIO { loadThumbnails(resolvedVideo, source) }` —
   prefetches the first 2 tile maps for the seek-bar thumbnail preview.
4. `activity.setVideo(resolvedVideo)`.

`PlayerActivity.setVideo(video, position = null)`:

1. `setHttpOptions(video)` — pushes `video.headers` (or `source.headers`)
   as MPV `http-header-fields`.
2. Sets MPV `"start"` property: if `isLoadingEpisode`, use
   `resumePosition` or `episode.last_second_seen` or `0`; else use
   `player.timePos` (continue from current pos).
3. Builds `videoOptions` from `video.mpvArgs` (key=value pairs joined
   with `,`).
4. **Torrent branch**: if `torrentPreferences.torrServerEnable().get()`
   and the URL is a magnet, `.torrent`, or `content://` torrent file →
   `TorrentServerService.start()` then `torrentLinkHandler(url, title,
   videoOptions)`:
   - `content://` → `torrentServerApi.uploadTorrent(stream, title, false)`.
   - `magnet:?xt=...&index=N` → parse `index`.
   - Else → `torrentServerApi.addTorrent(url, title, "", "", false)`.
   - Get a playable URL via
     `torrentServerUtils.getTorrentPlayLink(torrent, index)`, then
     `MPVLib.command(["loadfile", torrentPlayLink, "replace", "0", videoOptions])`.
5. **Direct branch**: `MPVLib.command(["loadfile",
   parseVideoUrl(video.videoUrl), "replace", "0", videoOptions])`.
   `parseVideoUrl` delegates to `Uri.resolveUri(context)` which
   translates `content://` URIs to `fd://` (via `openContentFd`),
   passes `file://` through, and supports `data://` and MPV-supported
   protocols verbatim.

### 6. MPV loads → `fileLoaded`

When MPV finishes loading the URL, it emits `MPV_EVENT_FILE_LOADED`.
`PlayerObserver.event(MPV_EVENT_FILE_LOADED)` forwards to
`PlayerActivity.fileLoaded()`, which:

1. `setMpvOptions()` — reads MPV's `metadata` property for
   `Video.MPV_ARGS_TAG` overrides.
2. `setMpvMediaTitle()` — writes
   `user-data/current-anime/episode-title` and `force-media-title`.
3. `setupPlayerOrientation()`.
4. `setupChapters()` — merges three sources via
   `ChapterUtils.mergeChapters(...)`:
   - MPV-native chapters (MKV chapter metadata).
   - `Video.timestamps` (provided by the source).
   - AniSkip timestamps — `PlayerViewModel.aniSkipResponse(duration)`
     calls `AniSkipApi.getResult(malId, episodeNumber, episodeLength)`.
     The MAL id is resolved from the anime's tracker rows; if the user
     is logged in via AniList, the AniSkip API's GraphQL `Media{idMal}`
     query converts the AniList id to a MAL id.
5. `setupTracks()` — loads `Video.audioTracks` / `Video.subtitleTracks`
   via `MPVLib.command(["audio-add", url, "auto", lang])` /
   `["sub-add", url, "auto", lang])`.

The merged chapter list drives the seek-bar segments, the `ChaptersSheet`,
and the skip-intro button.

### 7. `onSecondReached` — the heart of the watch loop

MPV emits `time-pos` once per second. `PlayerObserver.eventProperty(name,
Long)` forwards to `PlayerActivity.onObserverEvent("time-pos", value)`,
which calls `viewModel.onSecondReached(pos, duration)`:

```kotlin
private fun onSecondReached(position: Int, duration: Int) {
    if (isLoadingEpisode.value) return
    val currentEp = currentEpisode.value ?: return
    if (episodeId == -1L) return
    if (duration == 0) return

    val seconds = position * 1000L
    val totalSeconds = duration * 1000L
    currentEp.last_second_seen = seconds
    currentEp.total_seconds = totalSeconds
    episodePosition = seconds

    val progress = playerPreferences.progressPreference().get()    // 0.85 by default
    val shouldTrack = !incognitoMode || hasTrackers
    if (seconds >= totalSeconds * progress && shouldTrack) {
        viewModelScope.launchNonCancellable {
            updateEpisodeProgressOnComplete(currentEp)
        }
    }

    saveWatchingProgress(currentEp)

    val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
    if (inDownloadRange) {
        downloadNextEpisodes()
    }
}
```

`updateEpisodeProgressOnComplete(currentEp)`:

1. `currentEp.seen = true`.
2. `updateTrackEpisodeSeen(currentEp)` — calls `TrackEpisode.await(ctx,
   anime.id, episode.episode_number.toDouble())` (see
   [`track-progress.md`](track-progress.md)). Note: this method is gated
   on `!incognitoMode && hasTrackers && autoUpdateTrack`.
3. `deleteEpisodeIfNeeded(currentEp)` — if the `removeAfterReadSlots`
   preference is set, enqueues the episode N-back for deletion.
4. Optionally marks duplicate-numbered episodes seen (if the
   `markDuplicateSeenEpisodeAsSeen` pref is on).

`saveWatchingProgress(episode)` calls:

- `saveEpisodeProgress(episode)` — `updateEpisode.await(EpisodeUpdate(id,
  seen, bookmark, fillermark, lastSecondSeen, totalSeconds))`. **Gated
  on `!incognitoMode || hasTrackers`** — this is the anime/anime-vs-manga
  asymmetry noted in [`../03-subsystems/history.md`](../03-subsystems/history.md).
- `saveEpisodeHistory(episode)` — `upsertHistory.await(AnimeHistoryUpdate(
  episodeId, seenAt))`. Gated on `!incognitoMode` only. The anime
  history table's `upsert` simply replaces `last_seen` (no duration
  accumulation — see [`../03-subsystems/history.md`](../03-subsystems/history.md)).

`downloadNextEpisodes()` only fires if `downloadAheadAmount > 0` AND the
current and next episodes are both already downloaded (so we don't
download-ahead over a streaming watch). It enqueues the next
`downloadAheadAmount` unseen episodes via `downloadManager.downloadEpisodes(anime,
episodesToDownload)` — see [`download-chapter.md`](download-chapter.md).

### 8. Pause / finish → flush progress

`PlayerActivity.onPause` calls `saveCurrentEpisodeWatchingProgress()` (if
finishing → stop MPV + delete pending episodes; else → `viewModel.pause()`).
`PlayerActivity.onSaveInstanceState` (if not a config change) calls
`viewModel.onSaveInstanceStateNonConfigurationChange()` which persists the
current progress to the DB.

## Sequence diagram

```
USER: tap episode
   │
   ▼
PlayerActivity.onCreate()
   ├─ setupPlayerMPV()  ── write mpv.conf/input.conf, copy fonts/scripts, player.initialize()
   ├─ setupMediaSession(), setupPlayerAudio()
   └─ onNewIntent(intent)
        ├─ saveCurrentEpisodeWatchingProgress()  ── flush prior episode
        └─ viewModel.init(animeId, episodeId, hostList, hostIdx, vidIdx)
             ├─ getAnime.await(animeId)  ── SQLDelight
             ├─ checkTrackers(anime)  ── sets hasTrackers
             ├─ initEpisodeList(anime)  ── getEpisodesByAnimeId → sort → map to Episode
             ├─ source = sourceManager.getOrStub(anime.source)
             └─ EpisodeLoader.getHosters(episode, anime, source)
                  └─ AnimeHttpSource: source.getHosterList(episode).sortHosters()
                       (or legacy: source.getVideoList(episode))
        └─ viewModel.loadHosters(source, hosterList, hostIdx, vidIdx)
             ├─ per-hoster async: EpisodeLoader.loadHosterVideos(source, hoster)  ── HosterState.Ready
             ├─ if preferred video found: loadVideo(source, video, hostIdx, vidIdx)
             │   └─ HosterLoader.getResolvedVideo(source, video)  ── source.resolveVideo if needed
             │       └─ activity.setVideo(resolvedVideo)
             │            ├─ setHttpOptions(video)
             │            ├─ if torrent: TorrentServerService.start() + torrentLinkHandler → MPVLib loadfile
             │            └─ else:        MPVLib.command(["loadfile", parseVideoUrl(url), …])
USER: watches
   │
   ▼
MPV time-pos (1 Hz) → PlayerObserver.eventProperty("time-pos", Long)
   └─ viewModel.onSecondReached(pos, duration)
        ├─ currentEp.last_second_seen = pos; total_seconds = duration
        ├─ if pos >= duration * 0.85 && (!incognito || hasTrackers):
        │     updateEpisodeProgressOnComplete(currentEp)
        │       ├─ currentEp.seen = true
        │       ├─ TrackEpisode.await(ctx, anime.id, episodeNumber)   → see track-progress.md
        │       └─ deleteEpisodeIfNeeded(currentEp)                   → see download-chapter.md
        ├─ saveWatchingProgress(currentEp)
        │     ├─ saveEpisodeProgress  → updateEpisode.await(EpisodeUpdate(…))
        │     └─ saveEpisodeHistory    → upsertHistory.await(AnimeHistoryUpdate(episodeId, seenAt))
        └─ if pos/duration > 0.35: downloadNextEpisodes()             → see download-chapter.md
USER: back / home
   │
   ▼
PlayerActivity.onPause() → saveCurrentEpisodeWatchingProgress()
   └─ saveEpisodeProgress + saveEpisodeHistory (final flush)
```

## See also

- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — the full player subsystem deep dive (MPV integration, controls, sheets, subtitles, audio, chapters, custom Lua buttons).
- [`read-manga.md`](read-manga.md) — the manga counterpart.
- [`../03-subsystems/history.md`](../03-subsystems/history.md) — the history write path and the incognito-mode asymmetry.
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md) — the Torrserver hand-off in step 5.
- [`track-progress.md`](track-progress.md) — what `TrackEpisode` does after an episode is marked seen.
- [`download-chapter.md`](download-chapter.md) — the auto-download-ahead path.
