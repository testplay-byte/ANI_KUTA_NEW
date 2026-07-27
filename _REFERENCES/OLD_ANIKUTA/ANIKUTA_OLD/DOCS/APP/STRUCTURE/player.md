# Player System (Phase 4)

> **Selective copy-paste from aniyomi (Decision D1).** This doc records exactly
> what was copied, what was dropped, and why — so each adoption is traceable.

## TL;DR

The aniyomi player is **not a self-contained module**. A bulk copy of its 64
player files (~12 000 lines) produced **2 277 compile errors** because every
file transitively depends on aniyomi's i18n (`AY`/`AYMR` moko strings),
`tachiyomi.presentation.core` UI kit, the custom `tachiyomi.core.preference`
system, the data layer, and the source/episode loader.

ANI-KUTA instead ships a **clean minimal player** (7 files, ~600 lines) that
depends only on the MPV lib + our own `PreferenceStore`. It is verifiable
today via a public-domain sample stream; real episode URLs arrive in Phase 5
when the source system is wired.

## Files

```
app/src/main/java/app/anikuta/player/
├── PlayerPreferences.kt      8 prefs via our PreferenceStore
├── PlayerEnums.kt            PlayerVideoAspect, PlayerLoadingState
├── AnikutaMPVView.kt         thin BaseMPVView wrapper (initOptions/observeProperties/postInitOptions)
├── PlayerObserver.kt         MPVLib.EventObserver + LogObserver -> Callback
├── PlayerViewModel.kt        state surface (position/duration/paused/buffering/error/controlsVisible)
├── PlayerActivity.kt         Compose host (AndroidView), MPV lifecycle, landscape, edge-to-edge
└── controls/
    └── PlayerControls.kt     single M3 Expressive overlay (top bar + center + bottom slider)
```

## What was copied from aniyomi

| aniyomi source | ANI-KUTA target | What we kept | What we dropped |
|---|---|---|---|
| `AniyomiMPVView.kt` (290 lines) | `AnikutaMPVView.kt` (~115) | `BaseMPVView` subclass, `initOptions` (hwdec/cache/profile/speed), `observeProperties`, property accessors (`duration`/`timePos`/`paused`/`volume`) | decoder panels (deband/yuv420p/filters), subtitle options (~20 setOptionString calls), audio options, `TrackDelegate`, `onKey` key mapping, `VideoFilters`, `toColorHexString` |
| `PlayerObserver.kt` | `PlayerObserver.kt` | `EventObserver` + `LogObserver` impls, all `eventProperty` overloads, `efEvent`, `logMessage` | hard `PlayerActivity` reference → replaced with a `Callback` interface (decoupled + testable) |
| `PlayerViewModel.kt` (2059 lines) | `PlayerViewModel.kt` (~90) | playback state surface (position/duration/paused/buffering/error/controlsVisible) | episode pre-fetching, hoster resolution, AniSkip, track selection, chapter sync, progress saving, `Event` flow, backup hooks |
| `PlayerActivity.kt` (1317 lines) | `PlayerActivity.kt` (~200) | MPV lifecycle (`initialize`→`addLogObserver`→`addObserver`→`loadfile`→release), landscape, edge-to-edge, keep-screen-on, system-bar control | PiP, media session, audio focus, orientation-lock pref, key mapping, screenshot, chapter sync, AniSkip, backup hooks, view-binding XML |
| `PlayerEnums.kt` (171 lines) | `PlayerEnums.kt` (~20) | `PlayerVideoAspect`, `PlayerLoadingState` | `StepMode`, `SeekDirection`, `AudioMode`, `SubtitleState`, `Pip` actions |
| `PlayerPreferences.kt` (~40 fields) | `PlayerPreferences.kt` (8 fields) | speed, hwdec, gpu-next, volume boost, audio lang, seek step, brightness, auto-hide | subtitle typography/colors/border/shadow/position, decoder banding/yuv/hwdec presets, gesture prefs, advanced (mpv.conf/input.conf/scripts/fonts), PiP, orientation, internal seek text |
| split controls (~20 files) | `PlayerControls.kt` (~210) | top bar (back+title), center (play/pause+seek±10s+buffering), bottom (slider+time) | TopLeft/TopRight/BottomLeft/BottomRight splits, sheets (quality/audio/subtitle/chapters/screenshot/more), panels (subtitle settings×4, video filters, audio/sub delay), dialogs (episode list, integer picker), `DoubleTapSeekTriangles`, `GestureHandler`, `BrightnessOverlay`, `AutoPlaySwitch`, `CurrentChapter`, `VerticalSliders` |

**Result:** 64 files → 7 files. ~12 000 lines → ~600 lines. 2 277 errors → 0.

## MPV lib API (verified)

The `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` artifact exposes (package
`is.xyz.mpv`):

- `BaseMPVView(Context, AttributeSet)` — abstract surface view. Three abstract
  hooks: `initOptions(vo: String)`, `observeProperties()`, `postInitOptions()`.
  Constructor requires a **non-null** `AttributeSet` — ANI-KUTA creates an
  empty one via `Xml.asAttributeSet(Xml.newPullParser())` for the Compose
  `AndroidView` factory.
- `MPVLib` — static façade: `create`, `command(String[])`, `setOptionString`,
  `getProperty{Int,Boolean,Double,String}`, `setProperty{Int,Boolean,Double,String}`,
  `observeProperty(name, format)`, `addObserver`, `removeObserver`,
  `addLogObserver`, `removeLogObserver`.
- `MPVLib.EventObserver` / `MPVLib.LogObserver` — callback interfaces.
- `MPVLib.mpvFormat.MPV_FORMAT_{NONE,STRING,FLAG,INT64,DOUBLE,…}` — observe formats.
- `MPVLib.mpvEventId.MPV_EVENT_{FILE_LOADED,SEEK,PLAYBACK_RESTART,IDLE,…}` — events.
- `MPVLib.mpvLogLevel.MPV_LOG_LEVEL_{FATAL,ERROR,WARN,INFO,…}` — log levels.

## Gotchas encountered (and fixed)

1. **`AttributeSet?` vs `AttributeSet`** — `BaseMPVView`'s constructor is
   non-nullable. Passing `null` from Compose's `AndroidView` factory fails to
   compile. Initial fix: create an empty `AttributeSet` via
   `Xml.asAttributeSet(...)`. **Later** that turned out to crash at runtime
   (`ClassCastException: XmlPullAttributes cannot be cast to XmlBlock$Parser`)
   because Android's `obtainStyledAttributes` needs a real `XmlBlock$Parser`,
   which only comes from inflating an actual XML layout. Final fix: inflate
   `R.layout.mpv_view` via `LayoutInflater` in the `AndroidView` factory.
2. **`app.anikuta.player.PlayerPreferences` in `AppModule`** — the
   fully-qualified name collided with the module's `app: Application`
   parameter (`app.anikuta` parsed as property access on `app`). Fix: add an
   import and use the short name.
3. **`mapOf` + forward-referenced property + for-loop destructuring** — caused
   a Kotlin resolver cascade ("ambiguous component1/iterator", "unresolved
   observedProps", phantom "missing brace"/"unclosed comment"). The
   `MPVLib.mpvFormat.MPV_FORMAT_*` symbols are correct (verified by inspecting
   the AAR); the failure was the resolver combination. Fix: call
   `MPVLib.observeProperty(name, format)` directly per property — no map, no
   for-loop, no destructuring.
4. **`collectAsStateWithLifecycle`** — requires `lifecycle-runtime-compose`,
   which isn't in our version catalog. The rest of the app uses
   `collectAsState()`; the player matches.
5. **Player crashes on 2nd open (SIGABRT, native assertion).** Root cause:
   `MPVLib.create`/`BaseMPVView.initialize()` can only run ONCE per process —
   a second call triggers a native assertion that the Java crash handler
   can't catch. Fix: call `BaseMPVView.destroy()` (via reflection) in
   `onDestroy()`, then `initialize()` fresh on the next open. This matches
   aniyomi's `onCreate→initialize`, `onDestroy→destroy` lifecycle. The
   `mpvInitialized` flag is kept as a belt-and-suspenders guard. (Commit
   `459499a`.)
6. **TLS certificate rejected → `loading failed`.** Streaming servers
   commonly use self-signed or untrusted certificates. aniyomi ships a
   `cacert.pem` in assets and sets `tls-verify=yes` + `tls-ca-file`; we don't
   have that asset, so MPV rejected the connection with "The certificate is
   not correctly signed by the trusted CA". Fix: `tls-verify=no` in
   `initOptions()`. Acceptable for a sideloaded app (not on Google Play).
   (Commit `2414d12`.)
7. **HTTP 403 Forbidden.** Streaming servers require `Referer` + `User-Agent`
   headers. The extension sets these in its `videoListRequest`, but MPV loads
   the bare URL and knows nothing about them. Fix: extract
   `Video.videoHeaders` in `DetailViewModel.playSpecificVideo`, pass it
   through `PlayerActivity.newIntent(..., videoHeaders = ...)`, and in
   `PlayerScreen` call `MPVLib.setOptionString("http-header-fields", headers)`
   before `loadfile`. MPV parses newline-separated `Header: Value` fields.
   Fallback to a default `User-Agent` when the extension provides no headers.
   (Commits `dbcd967`, `1d5f7d2`.)
8. **Infinite loading spinner on failure.** `PlayerObserver.efEvent`
   (END_FILE with error) wasn't surfaced to the ViewModel, so a failed load
   left the spinner spinning forever. Fix: added
   `onFileEnded(errorMessage: String?)` to `PlayerObserver.Callback`;
   `PlayerActivity` routes it to `viewModel.onError(message)` →
   `loadingState = ERROR`. The overlay now shows the error + "Go Back" button
   within 1–2 s of failure. (Commit `dbcd967`.)
9. **Extension video resolution crash (`IllegalArgumentException: Invalid URL
   host`).** Extensions only override `videoListRequest`, not
   `hosterListRequest`. The base `AnimeHttpSource.hosterListRequest`
   constructs `GET(baseUrl + episode.url)` where `episode.url` is an
   already-encoded path — concatenating it onto `baseUrl` is malformed. Fix:
   in `DetailViewModel.playEpisode`, catch **ALL** `Throwable` from
   `getHosterList` (not just `IllegalStateException`) and fall back to
   `getVideoList`. (Commit `b25c8fa`.)

## Launch contract

`PlayerActivity` is launched via `PlayerActivity.newIntent(context, ...)` with
these extras:

- `EXTRA_VIDEO_URL` (required) — direct stream URL from the resolved `Video`.
- `EXTRA_TITLE` (optional) — episode/anime title shown in the overlay top bar.
- `EXTRA_VIDEO_HEADERS` (optional) — newline-separated `Header: Value` pairs
  from `Video.videoHeaders`. Passed to MPV via `http-header-fields` before
  `loadfile`. Required for servers that 403 without `Referer`/`User-Agent`.
- `EXTRA_ANILIST_ID` + `EXTRA_EPISODE_URL` + `EXTRA_EPISODE_NUMBER`
  (optional) — when all three are present, watch progress is saved/resumed
  under `(anilistId, episodeUrl)` and the episode is synced to AniList on
  finish.

The detail screen resolves the `Video` (via the extension's
`getHosterList`/`getVideoList`) and launches the player with the resolved URL
+ headers. The "Play sample" button (public-domain Big Buck Bunny stream) was
removed once the source pipeline went live.

## Deferred to later phases

These aniyomi player features are intentionally absent and tracked as future
work (not bugs):

- **Episode/source loading** — `EpisodeLoader`, `HosterLoader`, hoster
  resolution, quality selection. ✅ **Done in Phase 5** (`DetailViewModel`
  resolves videos via `getHosterList`/`getVideoList`; server/quality
  selection via `ModalBottomSheet`).
- **Progress saving** — upsert `AnimeHistory` on pause/exit; resume prompt.
  ✅ **Done in Phase 5** (`WatchProgressStore` + `seekToSavedPosition` +
  "start over?" overlay).
- **Subtitle settings panels** — typography, colors, border, shadow, position,
  delay, secondary delay. (Phase 6 — settings.)
- **Audio/subtitle track selection** — sheets listing `track-list` entries.
  (Phase 6.)
- **Gestures** — brightness/volume/seek swipe, double-tap seek, pinch zoom.
  (Phase 6.)
- **PiP** — `PictureInPictureParams`, PiP actions. (Phase 7.)
- **Media session + audio focus** — lock-screen controls, focus handling.
  (Phase 7.)
- **AniSkip** — skip intro/outro via skip times API. (Phase 7.)
- **Screenshots + chapters** — `screenshot-directory`, chapter list. (Phase 7.)
- **mpv.conf / input.conf / scripts / fonts** — user customization files.
  (Phase 7.)

## Verification

- GitHub Actions build **#`28812131592`** (commit `f96e8a3`): ✅ success.
  Initial player compiles + registers + is reachable from the detail screen.
- Builds `b25c8fa` → `2414d12` → `dbcd967` → `1d5f7d2`: ✅ all green on
  GitHub Actions (arm64-v8a). These fixed the end-to-end pipeline (extension
  video resolution, TLS, 403/headers, error overlay, destroy lifecycle).
- **On-device user verification (build `1d5f7d2`, Session 20):**
  - ✅ Episode plays from server/quality selection.
  - ✅ Loading screen → video starts playing correctly.
  - ✅ Seek forward works (buffers, seeks, resumes).
  - ✅ Resume works (replay same episode → resumes from saved position).
  - ⚠️ Minor: first episode tap on a cold launch can race the source manager
    and show "no source available"; reopening the app clears it. Tracked as a
    polish item.
