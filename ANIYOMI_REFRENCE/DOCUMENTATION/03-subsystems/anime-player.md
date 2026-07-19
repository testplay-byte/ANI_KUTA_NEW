# 03-subsystems / anime-player

> The anime watching engine: a View-based Activity wrapping an MPV-based video
> player, with Compose on-screen controls, hoster/video resolution, torrent
> streaming via Torrserver, subtitle/audio track management, PiP, media
> session, AniSkip integration, and custom Lua buttons.

## 1. Purpose & overview

The player is the screen that lets the user watch a single anime episode. Like
the manga reader, it is invoked from anywhere in the app with an `animeId` and
an `episodeId` (and optionally a serialized `hostList` plus `hostIndex` /
`vidIndex` so a deep-link can pre-select a video quality). It then:

1. Resolves a list of `Hoster` objects for the episode (online / downloaded /
   local).
2. Lazily resolves each hoster into a `List<Video>`, picking the "best"
   (preferred or first valid) one.
3. Hands the chosen `Video`'s URL to **MPV** (`aniyomi-mpv-lib`), which streams
   and decodes it.
4. Surfaces Compose on-screen controls over the MPV surface for play/pause,
   seek, audio/subtitle track selection, quality switching, chapters, custom
   buttons, and more.
5. Reports watching progress back to history, trackers, and the download
   manager (delete-after-watch, download-ahead).

It is the second of only two **legacy View-based Activities** in the codebase
(the other being the manga `ReaderActivity`). The reason it has not been
migrated to Compose is that it wraps `AniyomiMPVView`, a `View` whose
`SurfaceView` is owned by the MPV JNI library (`is.xyz.mpv.BaseMPVView`).
Everything **around** the surface — the on-screen controls, sheets, dialogs,
panels — is already Compose, rendered via `binding.controls.setContent {
TachiyomiTheme { PlayerControls(...) } }`.

**Source root:** `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/`

```
player/
├── PlayerActivity.kt            ← View-based Activity; MPV init, lifecycle, PiP, media session, key handling
├── PlayerViewModel.kt           ← androidx.lifecycle.ViewModel; ~2177 lines of state
├── AniyomiMPVView.kt            ← The MPV-wrapping View (extends BaseMPVView)
├── PlayerObserver.kt            ← MPVLib event/property/log observer → forwards to Activity
├── PlayerEnums.kt               ← PlayerOrientation, VideoAspect, Decoder, Sheets, Panels, Dialogs, …
├── PlayerUtils.kt               ← Uri → MPV filepath resolution (fd://, content, file, data, …)
├── PipActions.kt                ← Builds RemoteActions for Picture-in-Picture
├── ExternalIntents.kt           ← "Play in external player" (MX Player / VLC / Web Video Caster / …)
├── controls/                    ← All-Compose overlay UI
│   ├── PlayerControls.kt        ← Root: composes top/middle/bottom + GestureHandler + sheets/panels/dialogs
│   ├── GestureHandler.kt        ← Compose pointerInput: drag-seek, brightness/volume swipe, double-tap
│   ├── TopLeftPlayerControls.kt, TopRightPlayerControls.kt,
│   ├── BottomLeftPlayerControls.kt, BottomRightPlayerControls.kt,
│   ├── MiddlePlayerControls.kt  ← The five regions of the overlay
│   ├── PlayerSheets.kt, PlayerPanels.kt, PlayerDialogs.kt
│   └── components/              ← SeekBar, sheets (Audio/Subtitle/Quality/Chapters/More/Screenshot/PlaybackSpeed),
│                                  panels (SubtitleSettings, AudioDelay, VideoFilters, SubtitleDelay),
│                                  dialogs (EpisodeList, IntegerPicker), BrightnessOverlay, VerticalSliders,
│                                  DoubleTapSeekTriangles, ThumbnailPreview, AutoPlaySwitch, CurrentChapter
├── loader/
│   ├── EpisodeLoader.kt         ← Resolves List<Hoster> for an episode (online / downloaded / local)
│   └── HosterLoader.kt          ← Resolves a Hoster → List<Video>; picks "best" video
├── settings/                    ← Six Preference catalogs (Player, Decoder, Subtitle, Audio, Gesture, Advanced)
└── utils/
    ├── AniSkipApi.kt            ← AniSkip skip-times lookup (intro/outro/recap)
    ├── ChapterUtils.kt          ← Merge MPV chapters + AniSkip timestamps + Video.timestamps
    └── TrackSelect.kt           ← Preferred audio/subtitle track picker (locale-based)
```

> The player is the **anime-only** counterpart to the manga reader. There is
> no manga player; the manga side is covered in [`manga-reader.md`](manga-reader.md).

## 2. The MPV integration

### The library: `aniyomi-mpv-lib`

Declared in `gradle/aniyomi.versions.toml`:

```toml
aniyomi-mpv-lib = "1.18.n"
aniyomi-mpv = { module = "com.github.aniyomiorg:aniyomi-mpv-lib", version.ref = "aniyomi-mpv-lib" }
```

The library exposes the `is.xyz.mpv` package containing:

- **`MPVLib`** — static JNI entry points: `create(context, cacheDir, configDir,
  logLvl)`, `destroy()`, `command(arrayOf(...))`, `setPropertyString/Int/Boolean/
  Double`, `getPropertyString/Int/Boolean/Double`, `observeProperty(name, fmt)`,
  `addObserver`/`removeObserver`, `addLogObserver`/`removeLogObserver`. Internally
  loads `libmpv.so` (and the bundled ffmpeg + media-codec bridges).
- **`BaseMPVView`** — a `FrameLayout` subclass that owns a `SurfaceView`, hooks
  it up to libmpv's render API, and exposes lifecycle hooks: `initOptions(vo)`,
  `observeProperties()`, `postInitOptions()`, plus `initialize(configDir,
  cacheDir, logLvl)` and `destroy()`.
- **`KeyMapping`** — Android `KeyEvent` keycode → mpv key-string table.
- **`Utils`** — helpers like `prettyTime`, `findRealPath`, and the
  `PROTOCOLS` set (`http`, `https`, `rtmp`, …).

### `AniyomiMPVView.kt` (290 lines)

`class AniyomiMPVView(context, attrs) : BaseMPVView(context, attrs)`. It:

- Injects all six player-preference catalogs + `NetworkPreferences` via Injekt.
- Exposes typed Kotlin properties over MPV state:
  - `duration: Int?`, `timePos: Int?` (get & set), `paused: Boolean?`,
    `hwdecActive: String`, `videoH: Int?`, `getVideoOutAspect(): Double?`.
  - `sid`, `secondarySid`, `aid: Int` via a `TrackDelegate` (delegated
    property) that translates `Int` ↔ mpv's `"no"`/number-string convention.
- Implements `initOptions(vo)`:
  - `setVo("gpu-next" or "gpu")` per `decoderPreferences.gpuNext()`.
  - `hwdec = "auto"` if `tryHWDecoding()`, else `"no"`.
  - Debanding (None / CPU `gradfun` filter / GPU `deband=yes`).
  - Optional `vf=format=yuv420p` for older decoders.
  - `keep-open=true`, `input-default-bindings=true`, `ytdl=no`,
    `tls-verify=yes` with the bundled `cacert.pem`.
  - Demuxer cache capped to 64 MB (32 MB on older Android).
  - Screenshot directory = `Pictures/`.
  - All 5 `VideoFilters` (`brightness`/`saturation`/`contrast`/`gamma`/`hue`)
    pushed from preferences.
  - `speed`, `vd-lavc-film-grain=cpu` (mpv workaround #14651).
  - `setupSubtitlesOptions()` + `setupAudioOptions()` — see §8 and §9.
- Implements `observeProperties()` — registers ~30 properties with MPV's
  observer system. The most interesting are the `user-data/aniyomi/*`
  properties (see §10) and `user-data/current-anime/intro-length` (read by the
  AniSkip Lua script).
- Implements `postInitOptions()` — toggles the optional stats overlay page.
- Implements `onKey(event: KeyEvent): Boolean` — maps Android keys to mpv
  `keydown`/`keyup` commands with modifier prefixes.

### `PlayerObserver.kt` (61 lines)

```kotlin
class PlayerObserver(val activity: PlayerActivity) :
    MPVLib.EventObserver, MPVLib.LogObserver { ... }
```

It is registered via `MPVLib.addObserver(playerObserver)` and
`MPVLib.addLogObserver(playerObserver)`. Every MPV property change or event is
forwarded to the **Activity on the UI thread** via `activity.runOnUiThread { ... }`,
which then dispatches to the ViewModel:

| Observer callback | Activity handler | What it does |
|---|---|---|
| `eventProperty(name)` | `onObserverEvent(property)` | `chapter-list` → load chapters; `track-list` → load audio/sub tracks. |
| `eventProperty(name, Long)` | `onObserverEvent(property, value)` | `time-pos` → update position + chapter; `demuxer-cache-time` → read-ahead bar; `volume`/`volume-max`; `duration`; `user-data/current-anime/intro-length` → AniSkip. |
| `eventProperty(name, Boolean)` | `onObserverEvent(property, value)` | `pause` → keep-screen-on + PiP refresh; `paused-for-cache`/`seeking` → loading indicator; `eof-reached` → endFile (autoplay next). |
| `eventProperty(name, String)` | `onObserverEvent(property, value)` | `aid`/`sid`/`secondary-sid` → track selection; `hwdec`/`hwdec-current` → decoder display; `user-data/aniyomi/*` → Lua invocation (§10). |
| `eventProperty(name, Double)` | `onObserverEvent(property, value)` | `speed`; `video-params/aspect` → recompute PiP aspect ratio. |
| `event(eventId)` | `event(eventId)` | `MPV_EVENT_FILE_LOADED` → `fileLoaded()`; `MPV_EVENT_SEEK` → loading; `MPV_EVENT_PLAYBACK_RESTART` → clear `isExiting`. |
| `efEvent(err)` | toast | File-ended error reporting (with HTTP error context captured in `logMessage`). |
| `logMessage(prefix, level, text)` | `logcat` | Routes MPV logs to logcat with proper priority; captures "HTTP error" strings for `efEvent`. |

## 3. PlayerActivity lifecycle & PlayerViewModel state

### `PlayerActivity.kt` (1384 lines)

`class PlayerActivity : BaseActivity()`. Highlights of its lifecycle:

| Method | What it does |
|---|---|
| `onCreate` | `enableEdgeToEdge()`; `setupPlayerMPV()` (writes `mpv.conf`/`input.conf`, copies `subfont.ttf` + `cacert.pem` + user scripts/fonts, calls `player.initialize(...)`); `setupPlayerAudio()` (audio focus); `setupMediaSession()`; `setupPlayerOrientation()`; sets a process-wide `UncaughtExceptionHandler`; observes `viewModel.eventFlow`; sets `binding.controls` Compose content; calls `onNewIntent(intent)`. |
| `onNewIntent` | Reads `animeId`/`episodeId`/`hostList`/`hostIndex`/`vidIndex`; saves current progress; calls `viewModel.init(...)`; then `viewModel.loadHosters(...)`. |
| `onStart` | Pip params; immersive-sticky; layout-in-cutout; restore saved brightness. |
| `onResume` | Re-sync MPV vs. system volume; clear `isExiting`. |
| `onPause` | Save progress; if finishing → stop MPV + delete pending episodes; else → `viewModel.pause()`. |
| `onStop` | Persist brightness if `rememberPlayerBrightness`; if in PiP and screen on → delete pending. |
| `onDestroy` | `player.isExiting = true`; abandon audio focus; release media session; unregister noisy receiver; remove MPV observers; `player.destroy()`; `viewModel.stopHttpServer()`. |
| `onUserLeaveHint` | If PiP enabled + playing + `pipOnExit` → `enterPictureInPictureMode()`. |
| `onPictureInPictureModeChanged` | On enter: hide controls, register `pipReceiver` for `PIP_INTENTS_FILTER` broadcasts (play/pause/next/prev/skip). On exit: unregister. |
| `onKeyDown`/`onKeyUp` | Volume keys → volume; DPAD-left/right + media rewind/ff → left/right double-tap; space → pause/unpause; media stop → finish; else → `player.onKey(event)`. |
| `onConfigurationChanged` | If not PiP → re-apply video aspect; else hide controls. |
| `onSaveInstanceState` | If not a config change → `viewModel.onSaveInstanceStateNonConfigurationChange()` (persists progress). |

The Activity owns several non-ViewModel pieces of state: the `MediaSession`,
the `AudioFocusRequestCompat`, the `noisyReceiver` (pauses on headphone
unplug), the `pipReceiver`, the `pipRect` (source-rect hint for PiP), and the
broadcast-receiver registrations. **Audio focus** is handled inline: a
duck-on-transient-loss and a pause-on-loss with a `restoreAudioFocus` lambda
that resumes when focus returns.

### `PlayerViewModel.kt` (2177 lines)

`class PlayerViewModel @JvmOverloads constructor(activity, savedState, ...) :
ViewModel()`. Constructed via a `PlayerViewModelProviderFactory` so it can take
the Activity reference (needed because the ViewModel issues MPV commands and
reads window/audio-manager state). The Activity is passed as the **first**
constructor parameter — unusual but consistent with mpvKt's design.

The ViewModel exposes **dozens of `MutableStateFlow`s**, the most important
being:

| Flow | Purpose |
|---|---|
| `currentAnime`, `currentEpisode`, `currentSource`, `currentVideo` | The active (anime, episode, source, video) tuple. |
| `currentPlaylist: List<Episode>` | The episode list filtered + sorted; used for next/prev. |
| `hasPreviousEpisode` / `hasNextEpisode` | For the skip buttons. |
| `hosterList: List<Hoster>` / `hosterState: List<HosterState>` / `hosterExpandedList` / `selectedHosterVideoIndex` | The hoster resolution UI state. |
| `isLoadingEpisode` / `isLoadingHosters` / `isLoadingTracks` / `isLoading` | Loading indicators. |
| `pos: Float` / `duration: Float` / `seekPosition: Float` / `isSeeking` / `readAhead: Float` | Playback position + seek bar state. |
| `paused: Boolean` / `pausedState: Boolean?` | Playback state; `pausedState` carries the "should we resume paused?" flag across episode loads. |
| `subtitleTracks` / `selectedSubtitles: Pair<Int, Int>` / `audioTracks` / `selectedAudio` | Track lists and selections (primary + secondary subtitle). |
| `chapters: List<IndexedSegment>` / `currentChapter` / `skipIntroText` | Chapter list (merged with AniSkip). |
| `controlsShown` / `seekBarShown` / `areControlsLocked` / `sheetShown: Sheets` / `panelShown: Panels` / `dialogShown: Dialogs` | UI visibility state. |
| `currentBrightness` / `currentVolume` / `currentMPVVolume` / `volumeBoostCap` / `isBrightnessSliderShown` / `isVolumeSliderShown` | Gesture-controlled sliders. |
| `gestureSeekAmount` / `doubleTapSeekAmount` / `isSeekingForwards` / `seekText` | Double-tap-seek visuals. |
| `customButtons: CustomButtonFetchState` / `primaryButton` / `primaryButtonTitle` | The Lua-driven custom button feature (§10). |
| `thumbnailImage: ImageBitmap?` / `thumbnailInfo` | The seek-bar thumbnail preview (tile-based). |
| `remainingTime` | Sleep-timer countdown. |
| `playbackSpeed` / `currentDecoder` | Playback speed and active hardware decoder. |

`SavedStateHandle` is used for `episode_id`, `episode_position`, and
`quality_index: Pair<Int, Int>` so the player can restore after a process kill.

## 4. The `loader/` package — hoster & video resolution

### `EpisodeLoader.kt` (206 lines)

The anime equivalent of the reader's `ChapterLoader`. Its companion object
exposes:

- **`getHosters(episode, anime, source): List<Hoster>`** — dispatches on the
  source type and download state:

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
   source.getVideoList(episode)               →  toHosterList()
       .sortVideos().toHosterList()
  ```

  The `checkHasHosters(source)` reflection (looking for `getHosterList` /
  `hosterListRequest` / `hosterListParse` methods on the source class
  hierarchy) is a backwards-compat shim for extensions built against ext-lib
  <1.6 (before the hosters API existed); such sources fall back to the flat
  `getVideoList(episode)` and wrap the result as a single pseudo-hoster
  (`NO_HOSTER_LIST`). See [`../02-modules/source-api.md`](../02-modules/source-api.md)
  for the hoster/Video contract.

- **`loadHosterVideos(source, hoster, force): HosterState`** — resolves a
  single `Hoster` into its `List<Video>`. If `hoster.lazy` and `!force`,
  returns `HosterState.Idle` (the user has to tap to expand). Otherwise calls
  `getVideos(source, hoster)`, which calls `source.getVideoList(hoster)` for
  online sources, parses any `"null"` placeholder URLs via
  `source.getVideoUrl(video)`, and returns `HosterState.Ready`.

### `HosterLoader.kt` (170 lines)

The "best video" picker, used by both the in-app player (via
`PlayerViewModel.loadHosters`) and the external-player path (`ExternalIntents`).

- **`selectBestVideo(hosterState): Pair<Int, Int>`** — purely synchronous.
  Returns the indices of the (hoster, video) pair to use. Algorithm:
  1. Among `HosterState.Ready` hosters, find the first that has a video marked
     `preferred` (and either `READY` or `QUEUE`).
  2. Else, find the first hoster with any video that has a non-empty
     `videoUrl` and state `READY`/`QUEUE`.
  3. Else `(-1, -1)` (no playable video).

- **`getBestVideo(source, hosterList): Video?`** — async, used when we don't
  have time to lazy-load every hoster (e.g. external player). Spawns one
  `async` per hoster in parallel on `Dispatchers.IO`, calls
  `EpisodeLoader.loadHosterVideos`, and short-circuits via a custom
  `EarlyReturnException` the moment a preferred video resolves successfully.

- **`getResolvedVideo(source, video): Video?`** — for `AnimeHttpSource`s and
  not-yet-initialized videos, calls `source.resolveVideo(video)` (the source
  may need to do another HTTP call to resolve a placeholder URL); returns the
  video with `initialized = true`.

### `HosterState` sealed class

Defined in `controls/components/sheets/QualitySheet.kt` (next to the sheet
that displays it):

```kotlin
sealed class HosterState { open val name: String
    data class Idle(name)        // lazy hoster, not yet loaded
    data class Loading(name)     // resolve in flight
    data class Error(name)       // resolve failed
    data class Ready(name, videoList: List<Video>, videoState: List<Video.State>)
}
```

`videoState` is a parallel list (one `Video.State` per video) so the UI can
show per-video loading/error/ready status.

## 5. The `controls/` package — the on-screen overlay

The overlay is **entirely Jetpack Compose**, rendered into `binding.controls`
(a `ComposeView`) by `PlayerControls(viewModel, onBackPress, modifier)`. It is
structured into five regions plus a gesture handler plus a stack of
sheets/panels/dialogs.

### `PlayerControls.kt` (682 lines)

The root composable. Sets up a `ConstraintLayout` with five child regions:

```
 ┌─────────────────────────────────────────────┐
 │  TopLeftPlayerControls   TopRightPlayerControls │   ← title, back, settings, decoder, …
 │                                                │
 │                                                │
 │             MiddlePlayerControls               │   ← center play/pause, loading, skip-intro
 │             (GestureHandler overlay)           │
 │                                                │
 │  BottomLeftPlayerControls BottomRightPlayerControls │ ← play/pause, prev/next, sheets, lock
 │           SeekbarWithTimers                    │   ← the segmented seeker
 └─────────────────────────────────────────────┘
```

Plus optional overlays: `BrightnessOverlay`, `BrightnessSlider`,
`VolumeSlider`, `ThumbnailPreview` (above the seek bar while dragging),
`TextPlayerUpdate` (transient text like "Skipped OP"), `DoubleTapSeekTriangles`
(left/right edge animations on double-tap), and the custom primary Lua button
(§10). Finally it composes `PlayerSheets`, `PlayerPanels`, `PlayerDialogs`
based on the active `sheetShown` / `panelShown` / `dialogShown`.

### `GestureHandler.kt` (364 lines)

A composable `Modifier.pointerInput` that captures the entire screen's touch
events when controls are visible. Implements:

- **Tap** — single tap = toggle controls; double-tap left/center/right = the
  `leftDoubleTapGesture` / `centerDoubleTapGesture` / `rightDoubleTapGesture`
  preference (Seek / PlayPause / Switch / Custom).
- **Horizontal drag** — if `gestureHorizontalSeek()` is on, drags show the
  seek bar and `seekPosition` (with thumbnail preview); releasing seeks MPV.
- **Vertical drag** — left half = brightness, right half = volume (or
  swapped if `swapVolumeBrightness`); only if `gestureVolumeBrightness()` is on.

### Sheets (`controls/components/sheets/`)

| Sheet | Purpose |
|---|---|
| `QualitySheet.kt` | Lists hosters and their videos; lets the user tap to switch. Shows per-hoster loading/error state and per-video state. |
| `AudioTracksSheet.kt` | Audio track list (from MPV's `track-list`), plus an "add external" launcher. |
| `SubtitleTracksSheet.kt` | Subtitle track list (primary + secondary), plus "add external". |
| `ChaptersSheet.kt` | List of merged chapters (MPV-native + Video.timestamps + AniSkip). Tap to seek. |
| `PlaybackSpeedSheet.kt` | Speed presets (configurable via `speedPresets()` preference) + custom slider. |
| `ScreenshotSheet.kt` | Take screenshot (with/without subs), save, share, or set as cover/background/thumbnail. |
| `MoreSheet.kt` | Catch-all: open subtitle/audio delay panels, video filter panel, sleep timer, decoder switch, etc. |

### Panels (`controls/components/panels/`)

| Panel | Purpose |
|---|---|
| `SubtitleSettingsPanel.kt` | Tabbed card with Typography, Colors, Miscellaneous (delay, font, override ASS). |
| `SubtitleDelayPanel.kt` | Slider for `sub-delay` and `secondary-sub-delay`. |
| `AudioDelayPanel.kt` | Slider for `audio-delay`. |
| `VideoFiltersPanel.kt` | Sliders for brightness/saturation/contrast/gamma/hue (mapped to MPV `vf` properties). |

### Dialogs (`controls/components/dialogs/`)

| Dialog | Purpose |
|---|---|
| `EpisodeListDialog.kt` | Pick any episode from `currentPlaylist` to switch to. |
| `IntegerPickerDialog.kt` | Generic integer picker, driven by Lua `launch_int_picker` invocations. |
| `PlayerDialog.kt` | Container. |

### Other components

- **`SeekBar.kt`** — uses `dev.vivvvek.seeker.Seeker` (the `seeker` library)
  with `Segment`s to render chapter boundaries on the seek bar. Supports
  `onPreview` callback that drives the `ThumbnailPreview` popup.
- **`ThumbnailPreview.kt`** — fetches tile-based thumbnails from the source
  (`source.getVideoThumbnails(video)` returns a `ThumbnailInfo` with
  `tileInfo` + `imageTileUrls`). The ViewModel preloads the first 2 tile maps
  and LRU-caches 3 tiles.
- **`VerticalSliders.kt`** — Compose sliders for volume/brightness that
  appear briefly during gestures.
- **`BrightnessOverlay.kt`** — full-screen black overlay for sub-zero
  brightness.
- **`DoubleTapSeekTriangles.kt`** — animated triangles on left/right edge
  showing accumulated seek amount.
- **`AutoPlaySwitch.kt`** — toggle in `MiddlePlayerControls` for autoplay-next.
- **`CurrentChapter.kt`** — small text showing the current chapter name.

## 6. The `settings/` package — preference catalogs

Six small `PreferenceStore`-backed classes. Each is registered in Injekt and
consumed by `AniyomiMPVView.initOptions()` (so they're applied at MPV init) and
by the Compose settings panels (so they're live-editable).

| File | Notable preferences |
|---|---|
| `PlayerPreferences.kt` | `preserveWatchingPosition`, `progressPreference` (0.85 — episode marked seen at 85% by default), `defaultPlayerOrientationType`, `playerFullscreen`, `hideControls`, `rememberPlayerBrightness`/`playerBrightnessValue`, `rememberPlayerVolume`/`playerVolumeValue`, `enablePip`/`pipOnExit`/`pipEpisodeToasts`/`pipReplaceWithPrevious`, `alwaysUseExternalPlayer`/`externalPlayerPreference`, `playerSpeed`/`speedPresets`, `invertDuration`, `aspectState` (Crop/Fit/Stretch), `autoplayEnabled`, `enableSkipIntro`/`autoSkipIntro`/`enableNetflixStyleIntroSkip`/`waitingTimeIntroSkip`/`aniSkipEnabled`/`disableAniSkipOnChapters`, `showFailedHosters`/`showEmptyHosters`, `panelOpacity`, `playerTimeToDisappear`, `reduceMotion`. |
| `DecoderPreferences.kt` | `tryHWDecoding`, `gpuNext`, `videoDebanding` (None/CPU/GPU), `useYUV420P`, plus the 5 `VideoFilters` (`brightnessFilter`/`saturationFilter`/`contrastFilter`/`gammaFilter`/`hueFilter`). |
| `SubtitlePreferences.kt` | `preferredSubLanguages`, `subtitleWhitelist`/`subtitleBlacklist`, `screenshotSubtitles`, `subtitleFont` (default "Sans Serif"), `subtitleFontSize`, `subtitleFontScale`, `subtitleBorderSize`, `boldSubtitles`/`italicSubtitles`, `textColorSubtitles`/`borderColorSubtitles`/`backgroundColorSubtitles`, `borderStyleSubtitles` (OutlineAndShadow/Box...), `subtitleJustification` (Auto/Left/Center/Right), `subtitlePos`, `overrideSubsASS`, `subtitlesDelay`/`subtitlesSpeed`/`subtitlesSecondaryDelay`, `shadowOffsetSubtitles`. |
| `AudioPreferences.kt` | `preferredAudioLanguages`, `enablePitchCorrection`, `audioChannels` (Auto/AutoSafe/Mono/Stereo/ReverseStereo — each maps to an MPV property/value pair), `volumeBoostCap` (default 30), `audioDelay`. |
| `GesturePreferences.kt` | `gestureVolumeBrightness`, `swapVolumeBrightness`, `gestureHorizontalSeek`, `showSeekBar`, `defaultIntroLength` (85s), `skipLengthPreference` (10s), `playerSmoothSeek`, `leftDoubleTapGesture`/`centerDoubleTapGesture`/`rightDoubleTapGesture` (each: None/Seek/PlayPause/Switch/Custom), `mediaPreviousGesture`/`mediaPlayPauseGesture`/`mediaNextGesture`. |
| `AdvancedPlayerPreferences.kt` | `mpvUserFiles` (load user scripts/shaders/script-opts from disk), `mpvConf` (raw `mpv.conf` text), `mpvInput` (raw `input.conf` text), `playerStatisticsPage` (0=off, 1..N = stats page). |

`PlayerEnums.kt` (171 lines) collects the related enums:
`SetAsArt`, `ArtType` (Cover/Background/Thumbnail), `PlayerOrientation` (8
values), `VideoAspect` (Crop/Fit/Stretch), `SingleActionGesture`,
`CustomKeyCodes`, `Decoder` (AutoCopy/Auto/SW/HW/HWPlus → mpv `hwdec` values),
`Debanding`, `Sheets`, `Panels`, `Dialogs`, `PlayerUpdates`,
`VideoFilters` (brightness/saturation/contrast/gamma/hue → MPV property).

## 7. Video pipeline — from `Video` to MPV

```
PlayerViewModel.loadHosters(source, hosterList, hosterIndex, videoIndex)
   │
   ├─ For each hoster in parallel (async on Dispatchers.IO):
   │     EpisodeLoader.loadHosterVideos(source, hoster) ──▶ HosterState.Ready
   │
   ├─ If a "preferred" video was found in any hoster ─▶ loadVideo(source, video, ...)
   │     (uses an AtomicBoolean so only the first preferred video wins)
   │
   └─ Else ──▶ HosterLoader.selectBestVideo(hosterState)
              ──▶ loadVideo(source, bestVideo, ...)

PlayerViewModel.loadVideo(source, video, hosterIdx, videoIdx): Boolean
   │
   ├─ HosterLoader.getResolvedVideo(source, video)   ← source.resolveVideo if !initialized
   ├─ _currentVideo.update { resolvedVideo }
   ├─ viewModelScope.launchIO { loadThumbnails(resolvedVideo, source) }   ← tile preview
   └─ activity.setVideo(resolvedVideo)

PlayerActivity.setVideo(video, position = null)
   │
   ├─ setHttpOptions(video)        ← pushes video.headers (or source.headers) as MPV http-header-fields
   ├─ if isLoadingEpisode: set "start" = resumePosition / episode.last_second_seen / 0
   │  else:               set "start" = player.timePos (continue from current pos)
   ├─ build videoOptions from video.mpvArgs (key=value pairs joined with ",")
   │
   ├─ if torrentPreferences.torrServerEnable().get() && (
   │       url starts with torrentServerApi.hostUrl || "magnet" || ends with ".torrent"
   │   ):
   │     TorrentServerService.start()
   │     torrentLinkHandler(url, title, videoOptions)
   │       ├─ if "content://" → upload stream to Torrserver → torrentPlayLink
   │       ├─ if "magnet"     → torrentServerApi.addTorrent(url, ...) → playLink
   │       └─ MPVLib.command(["loadfile", playLink, "replace", "0", videoOptions])
   │
   └─ else:
         MPVLib.command(["loadfile", parseVideoUrl(video.videoUrl), "replace", "0", videoOptions])

MPV loads the URL ───▶ MPV_EVENT_FILE_LOADED ──▶ PlayerActivity.fileLoaded()
   │
   ├─ setMpvOptions()       ← read MPV "metadata" property for Video.MPV_ARGS_TAG overrides
   ├─ setMpvMediaTitle()    ← writes "user-data/current-anime/episode-title" + "force-media-title"
   ├─ setupPlayerOrientation()
   ├─ setupChapters()       ← merge Video.timestamps + MPV chapter-list
   ├─ setupTracks()         ← load Video.audioTracks / Video.subtitleTracks as MPV "audio-add"/"sub-add"
   └─ aniSkip check         ← aniSkipResponse(duration) → merge into chapters; set skip button
```

`PlayerActivity.parseVideoUrl(url)` delegates to `Uri.resolveUri(context)` (in
`PlayerUtils.kt`), which translates Android `content://` URIs to `fd://`
via `openContentFd` (detaches the `ParcelFileDescriptor`, lets MPV own it),
passes `file://` paths through, and supports `data://` and MPV-supported
protocols (`http`, `https`, `rtmp`, …) verbatim.

### NanoHTTPD local server (extension-side)

For sources that need to proxy/rewrite URLs (e.g. to inject headers or unpack
an encrypted stream), `AnimeHttpSource` (in `:source-api`) exposes a `server:
HttpServer?` property — a `NanoHTTPD(0)` started on a random port by the
extension. The extension's `getVideoList`/`getVideoUrl` may return URLs pointing
at this local server; MPV then plays from `http://127.0.0.1:<port>/...`. The
**ViewModel** closes the server in `stopHttpServer()` on Activity destroy.

See [`../02-modules/source-api.md`](../02-modules/source-api.md) for the
`HttpServer` contract and the ext-lib 17 server lifecycle.

## 8. Torrent streaming via Torrserver

When the chosen `Video.videoUrl` is a magnet link, a `.torrent` URL, or a
`content://` URI pointing at a `.torrent` file, and
`torrentPreferences.torrServerEnable().get()` is on, the player hands off to
Torrserver rather than passing the URL directly to MPV.

`PlayerActivity.torrentLinkHandler(videoUrl, title, videoOptions)`:

1. If `content://` → open the input stream and call
   `torrentServerApi.uploadTorrent(stream, title, false)`.
2. If `magnet:?xt=...&index=N` → parse `index` (which file in the torrent to play).
3. Else → `torrentServerApi.addTorrent(videoUrl, title, "", "", false)`.
4. Get a playable URL via
   `torrentServerUtils.getTorrentPlayLink(torrent, index)` — this is a
   `http://127.0.0.1:<torrserver-port>/...` URL that MPV can stream.
5. `MPVLib.command(["loadfile", torrentPlayLink, "replace", "0", videoOptions])`.

`TorrentServerService.start()` (in `data/torrent/service/`) keeps the
Torrserver process alive while the player is open. See
[`torrent-streaming.md`](torrent-streaming.md) for the Torrserver lifecycle
and configuration.

## 9. Subtitles, audio, and chapters

### Subtitle pipeline

- **Embedded** subtitles (MKV/MP4 SRT/ASS streams) are exposed automatically
  by MPV's demuxer; `PlayerObserver.eventProperty("track-list")` triggers
  `loadTracks()` which enumerates `track-list/count` and populates
  `subtitleTracks` / `audioTracks`.
- **External** subtitles (and external audio) are loaded from
  `Video.subtitleTracks` / `Video.audioTracks` (set by the source) via
  `MPVLib.command(["sub-add", url, "auto", lang])` / `["audio-add", ...]` in
  `PlayerActivity.setupTracks()`. The user can also add arbitrary external
  subtitle/audio files at runtime via the sheets (which call
  `viewModel.addSubtitle(uri)` / `addAudio(uri)` — these resolve content URIs
  via `openContentFd` and feed them to MPV).
- **ASS override** — `subtitlePreferences.overrideSubsASS()` sets
  `sub-ass-override=force` and `sub-ass-justify=yes`, so the user's subtitle
  styling (font, color, border, position) overrides the ASS file's.
- **Font embedding** — `PlayerActivity.copyAssets(mpvDir)` copies `subfont.ttf`
  from the app's assets into the MPV config dir. `copyFontsDirectory(mpvDir)`
  copies any user-installed `.ttf`/`.otf` files from
  `storageManager.getFontsDirectory()` and points MPV at them via
  `sub-fonts-dir` and `osd-fonts-dir`.
- **Font picker** — the subtitle typography panel uses
  `io.github.yubyf:truetypeparser-light` (`TTFFile`) to enumerate the names of
  installed TTF/OTF fonts so the user can pick one by name. The chosen name is
  pushed to MPV as `sub-font`.

### Audio

- `setupAudioOptions()` in `AniyomiMPVView` sets `alang`, `audio-delay`,
  `audio-pitch-correction`, `volume-max` (= user cap + 100, so volume can be
  boosted above 100% up to `volumeBoostCap`).
- `AudioChannels` enum maps to either `audio-channels` (auto/auto-safe/mono/
  stereo) or, for `ReverseStereo`, an `af=pan=[stereo|c0=c1|c1=c0]` filter.
- Audio focus is handled in the Activity (§3). On `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`
  the volume is halved via `MPVLib.command(["multiply", "volume", "0.5"])` and
  doubled back on focus regain.

### Chapters & AniSkip

`PlayerActivity.fileLoaded()` calls `setupChapters()` which merges up to three
sources via `ChapterUtils.mergeChapters(...)`:

1. **MPV-native chapters** (from the file's own chapter metadata, e.g. MKV
   chapters) — read via `MPVLib.getPropertyInt("chapter-list/count")` and
   `getPropertyString("chapter-list/$i/title")`.
2. **`Video.timestamps`** — a `List<TimeStamp>` provided by the source
   alongside the video URL (each `TimeStamp` has `start`, `end`, `name`, and a
   `ChapterType` of Opening/Ending/Recap/MixedOp/Other).
3. **AniSkip timestamps** — fetched by `PlayerViewModel.aniSkipResponse(duration)`
   which calls `AniSkipApi.getResult(malId, episodeNumber, episodeLength)`
   (`https://api.aniskip.com/v2/skip-times/...`). The MAL id is resolved from
   the anime's tracker rows; if the user is logged in via AniList, the AniSkip
   API's GraphQL `Media{idMal}` query converts the AniList id to a MAL id.

The merged chapter list drives: the segments on the seek bar
(`SeekBar`'s `Segment`s), the `ChaptersSheet`, and the skip-intro/skip-outro
button. The Netflix-style countdown (a toast that says "Skip Intro in Ns" and
auto-skips when it hits 0) is driven by `setChapter(position)` which is called
on every `time-pos` update.

`ChapterUtils.kt` also provides `ChapterType.getStringRes()` for localized
chapter names (Opening/Ending/Recap/MixedOp).

### `TrackSelect.kt`

Picks the preferred audio/subtitle track after `loadTracks()`. Algorithm:

1. Parse `preferredSubLanguages` (or `preferredAudioLanguages`) as a
   comma-separated list of locale codes.
2. For subtitles, filter the list through `subtitleWhitelist` (must match) and
   `subtitleBlacklist` (must not match) by name.
3. Find the first preferred locale that any track matches; otherwise fall back
   to the system default locale.
4. Return the first matching `VideoTrack`, or `null` (then the caller picks
   the first track).

## 10. Custom Lua buttons

The player supports user-defined buttons backed by MPV's Lua scripting system.
The flow:

1. The user writes Lua scripts via **Settings → Player → Custom buttons**;
   each `CustomButton` (a domain model in `tachiyomi.domain.custombuttons`) has
   `name`, `luaContent` (the body of the button function), `longPressContent`,
   and `isFavorite` (the "primary" button shown on the main overlay).
2. `PlayerViewModel.init` fetches all buttons via `GetCustomButtons.getAll()`
   and calls `activity.setupCustomButtons(buttons)`.
3. `PlayerActivity.setupCustomButtons(buttons)` builds a Lua module file
   `scripts/custombuttons.lua` that:
   - `require`s the bundled `aniyomi.lua` bridge (copied from assets).
   - For each button, defines a `button<id>()` function and a `button<id>long()`
     function, and registers them as MPV script messages via
     `mp.register_script_message('call_button_<id>', button<id>)` and
     `mp.register_script_message('call_button_<id>_long', button<id>long)`.
   - Calls `MPVLib.command(["load-script", filePath])` to load it.
4. The Compose overlay shows the primary button (if any) in
   `MiddlePlayerControls`. Tapping calls
   `CustomButton.execute()` → `MPVLib.command(["script-message", "call_button_<id>"])`.
5. The Lua script can call back into Kotlin by setting
   `user-data/aniyomi/<action>` properties — these are observed by
   `AniyomiMPVView` (registered in `observedProps`) and dispatched by
   `PlayerViewModel.handleLuaInvocation(property, value)`. Supported actions:

| Lua property | Effect |
|---|---|
| `user-data/aniyomi/show_text` | Show transient text via `PlayerUpdates.ShowText`. |
| `user-data/aniyomi/toggle_ui` | `show` / `hide` / `toggle` the on-screen controls. |
| `user-data/aniyomi/show_panel` | Open a settings panel (subtitle_settings/subtitle_delay/audio_delay/video_filters). |
| `user-data/aniyomi/set_button_title` / `reset_button_title` | Change the primary button's label. |
| `user-data/aniyomi/switch_episode` | `n` (next) / `p` (previous) episode. |
| `user-data/aniyomi/pause` | `pause` / `unpause` / `pauseunpause`. |
| `user-data/aniyomi/seek_to` / `seek_by` | Seek to an absolute or relative position. |
| `user-data/aniyomi/seek_to_with_text` / `seek_by_with_text` | Same, with a custom toast text. |
| `user-data/aniyomi/launch_int_picker` | Open the `IntegerPickerDialog` (format: `title|nameFormat|start|stop|step|property`). |
| `user-data/aniyomi/toggle_button` | `show` / `hide` / `toggle` the primary button. |
| `user-data/aniyomi/software_keyboard` | `show` / `hide` / `toggle` the soft keyboard. |

## 11. PiP, media session, and external intents

### Picture-in-Picture — `PipActions.kt`

`createPipActions(context, isPaused, replaceWithPrevious, playlistCount, playlistPosition)`
builds an `ArrayList<RemoteAction>` of 3 actions:

- Slot 1: **Skip previous episode** (if `replaceWithPrevious`) **or**
  **Forward 10s** (otherwise, `PIP_SKIP`).
- Slot 2: **Play** (if paused) **or** **Pause** (if playing).
- Slot 3: **Skip next episode**.

Each `RemoteAction` wraps a `PendingIntent.getBroadcast` for the
`PIP_INTENTS_FILTER` (`"pip_control"`) action, carrying a
`PIP_INTENT_ACTION` (`"media_control"`) int extra (`PIP_PAUSE`, `PIP_PLAY`,
`PIP_PREVIOUS`, `PIP_NEXT`, `PIP_SKIP`).

`PlayerActivity.onPictureInPictureModeChanged` registers a `BroadcastReceiver`
for that filter on PiP entry (and unregisters on exit) which dispatches the
intents to `viewModel.pause()` / `unpause()` / `changeEpisode(...)` /
`seekBy(10)`.

`createPipParams()` builds `PictureInPictureParams.Builder()`:

- On Android 13+: sets title = `anime.title`, subtitle = `episode.name`.
- On Android 12+: `setAutoEnterEnabled` and `setSeamlessResizeEnabled` based
  on playing state + `pipOnExit`.
- Sets the 3 actions.
- Sets `setSourceRectHint(pipRect)` (the bounds of the video surface, captured
  via `Modifier.onGloballyPositioned` in `PlayerControls`).
- Computes the aspect ratio from `player.videoH` × `player.getVideoOutAspect()`
  and calls `setAspectRatio` (clamped to 0.42–2.38 to avoid weird PiP windows).

### Media session — `PlayerActivity.setupMediaSession()`

Creates a `MediaSession("PlayerActivity")` with callbacks wired to
`gesturePreferences.mediaPreviousGesture` / `mediaPlayPauseGesture` /
`mediaNextGesture` (each a `SingleActionGesture`: None/Seek/PlayPause/Switch/
Custom). When `Custom`, it sends a synthetic keypress to MPV via
`CustomKeyCodes.MediaPrevious`/`MediaPlay`/`MediaNext` (which the user's
`input.conf` can bind). Sets the playback state actions (PLAY, PAUSE, STOP,
SKIP_TO_PREVIOUS, SKIP_TO_NEXT) and registers a `noisyReceiver` for
`ACTION_AUDIO_BECOMING_NOISY` (pauses when headphones unplug).

### External intents — `ExternalIntents.kt` (595 lines)

Used when `playerPreferences.alwaysUseExternalPlayer()` is on, or when the user
picks "Play externally" from an episode menu. `getExternalIntent(context,
animeId, episodeId, chosenVideo)`:

1. `initAnime(animeId, episodeId)` — loads `anime`, `source`, `episode`.
2. `EpisodeLoader.getHosters(episode, anime, source)`.
3. Pick the video: `chosenVideo` if non-null, else
   `HosterLoader.getBestVideo(source, hosters)`.
4. `getVideoUrl(source, context, video)` — `HosterLoader.getResolvedVideo(source, video)`,
   then either:
   - If the file is on-device and the URI isn't `content://` → wrap with
     `FileProvider.getUriForFile(...)` (so MX Player/VLC can read it).
   - Else → pass the URL through.
5. If `externalPlayerPreference()` is empty → return a generic
   `ACTION_VIEW` intent with `setDataAndTypeAndNormalize(url, mime)`, extras
   (anime/episode title, position), and video headers.
6. Else → `getIntentForPackage(pkgName, ...)`:
   - `WEB_VIDEO_CASTER` (`com.instantbits.cast.webvideo`) gets a special
     intent with `subtitle` extra (first matching the device locale, else the
     first subtitle track), HTTP headers bundle, and `secure_uri=true`.
   - Other packages (MX Player, VLC, …) get a `setPackage(pkgName)` intent
     with position extras and (for some packages) header/subtitle extras.

The class also handles position tracking: it computes the start position from
`episode.last_second_seen` (and `preserveWatchingPosition`), and on return
from the external player the caller is expected to update progress (the
external-player code path uses `DelayedAnimeTrackingUpdateJob` for tracker
updates and writes progress via `UpdateEpisode`).

## 12. Key files table

| File | Lines | Role |
|---|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` | ~1384 | Activity: MPV init, lifecycle, PiP, media session, audio focus, key dispatch, `setVideo`, `fileLoaded`, `torrentLinkHandler`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt` | ~2177 | ViewModel with ~30 StateFlows; hoster/video resolution, progress, history, tracker, download-ahead, screenshots, custom buttons, AniSkip. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/AniyomiMPVView.kt` | ~290 | `BaseMPVView` subclass; typed property accessors; `initOptions` (decoder/subtitle/audio config); `observedProps` registration; `onKey`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerObserver.kt` | ~61 | `MPVLib.EventObserver` + `LogObserver`; forwards to Activity on UI thread. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerEnums.kt` | ~171 | PlayerOrientation, VideoAspect, Decoder, Debanding, Sheets, Panels, Dialogs, PlayerUpdates, VideoFilters, SingleActionGesture, CustomKeyCodes, SetAsArt, ArtType. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt` | ~55 | `Uri.resolveUri(context)` (fd:// for content://, file/data/http/... passthrough), `Uri.getFileName`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PipActions.kt` | ~113 | `createPipActions` + `createPipAction`; PiP intent constants. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt` | ~595 | "Play in external player" — MX Player/VLC/Web Video Caster/generic; FileProvider wrapping; position extras; subtitle selection. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt` | ~206 | `getHosters` dispatch (downloaded/online/local); `loadHosterVideos` (resolve hoster → videos). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt` | ~170 | `selectBestVideo` (sync) + `getBestVideo` (async, parallel) + `getResolvedVideo`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt` | ~682 | Root Compose overlay; composes 5 regions + GestureHandler + sheets/panels/dialogs. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/GestureHandler.kt` | ~364 | Tap, double-tap, horizontal-drag-seek, vertical-drag-brightness/volume. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt` | ~199 | Hosts all sheets (Audio/Subtitle/Quality/Chapters/More/Screenshot/PlaybackSpeed). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerPanels.kt` | — | Hosts the four panels (SubtitleSettings, SubtitleDelay, AudioDelay, VideoFilters). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerDialogs.kt` | — | Hosts the dialogs (EpisodeList, IntegerPicker). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopLeftPlayerControls.kt` | — | Title, back, webview, share, settings, decoder. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/TopRightPlayerControls.kt` | — | PiP, orientation, aspect, screenshot, more-sheet trigger. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomLeftPlayerControls.kt` | — | Prev episode, rewind. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/BottomRightPlayerControls.kt` | — | Play/pause, next, forward, skip-intro. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/MiddlePlayerControls.kt` | — | Center play/pause, loading spinner, skip button, custom primary Lua button, autoplay switch. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt` | — | `Seeker`-based segmented seek bar + timer labels. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/ThumbnailPreview.kt` | — | Tile-based seek preview popup. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt` | — | Hoster/video picker; defines `HosterState` sealed class. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/AudioTracksSheet.kt` | — | Audio track list + add-external launcher. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/SubtitleTracksSheet.kt` | — | Subtitle track list (primary + secondary) + add-external. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ChaptersSheet.kt` | — | Merged chapter list; tap to seek. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/PlaybackSpeedSheet.kt` | — | Speed presets + slider. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/ScreenshotSheet.kt` | — | Screenshot capture/save/share/set-as-art. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/MoreSheet.kt` | — | Catch-all: panels trigger, sleep timer, decoder switch, etc. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsPanel.kt` | — | Tabbed subtitle styling (Typography/Colors/Misc). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt` | — | Subtitle delay sliders. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/AudioDelayPanel.kt` | — | Audio delay slider. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/VideoFiltersPanel.kt` | — | Brightness/saturation/contrast/gamma/hue sliders. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/EpisodeListDialog.kt` | — | Episode picker. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/IntegerPickerDialog.kt` | — | Generic integer picker (Lua-driven). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt` | ~86 | Top-level player preferences. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt` | ~22 | HW decode, gpu-next, debanding, yuv420p, video filters. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt` | ~64 | Font, size, colors, borders, delay, override ASS. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt` | ~27 | Preferred langs, pitch correction, channels, volume boost, delay. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt` | ~36 | Volume/brightness/seek gestures, double-tap actions, media-key actions. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt` | ~15 | mpv.conf / input.conf text, user files toggle, stats page. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/AniSkipApi.kt` | ~117 | AniSkip skip-times API + AniList→MAL id conversion. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt` | ~105 | Merge MPV chapters + Video.timestamps + AniSkip stamps; `ChapterType.getStringRes`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt` | ~65 | Locale-based preferred audio/subtitle track picker. |

## See also

- [`../05-key-flows/watch-anime.md`](../05-key-flows/watch-anime.md) — the
  end-to-end user journey from "tap an episode" to "marked seen".
- [`manga-reader.md`](manga-reader.md) — the manga counterpart.
- [`torrent-streaming.md`](torrent-streaming.md) — Torrserver lifecycle and
  configuration.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the `Hoster`
  and `Video` contracts; the `AnimeHttpSource.server: HttpServer?` NanoHTTPD
  hook.
- [`../02-modules/app.md`](../02-modules/app.md) — where the player sits in
  the `:app` module.
- [`history.md`](history.md) — `UpsertAnimeHistory` fed by the watching
  progress timer.
- [`trackers.md`](trackers.md) — `TrackEpisode` and the tracker ecosystem.
- [`download-manager.md`](download-manager.md) — download-ahead and
  delete-after-watch hooks.
- [`../01-architecture/04-navigation.md`](../01-architecture/04-navigation.md)
  — why the player is a legacy Activity and not a Voyager screen.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — the `PreferenceStore` backing the six player preference catalogs.
- [`../06-ui/compose-migration.md`](../06-ui/compose-migration.md) — the
  player's hybrid View+Compose status.
