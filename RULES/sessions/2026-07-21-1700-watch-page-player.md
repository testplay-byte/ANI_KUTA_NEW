# Session: Watch Page + Video Player Implementation

**Date:** 2026-07-21 17:00 UTC
**Agent:** ANIKUTA Watch Page Agent
**Task:** Implement the full watch page + video player (Prompt 2)

## What was done

Implemented the ANIKUTA watch page + video player — the most critical missing feature. The app can now: browse anime → open details → load episodes → resolve video sources → **open the watch page with an embedded MPV player** → play video → switch episodes → go fullscreen → save progress → resume.

### Modules created/modified

**`:core:preferences` (NEW — was empty skeleton)**
- `Preference.kt` — Preference<T> interface (key, get, set, isSet, delete, changes, stateIn)
- `PreferenceStore.kt` — PreferenceStore interface (getString, getInt, getFloat, getBoolean, getStringSet, getObject) + getEnum extension
- `AndroidPreference.kt` — sealed class implementing Preference<T> with typed primitives (String, Int, Long, Float, Boolean, StringSet, Object)
- `AndroidPreferenceStore.kt` — SharedPreferences-backed PreferenceStore impl with reactive keyFlow
- `di/PreferenceModule.kt` — Koin module binding PreferenceStore

**`:core:player` (NEW — was empty skeleton)**
- `AnikutaMPVView.kt` — extends `is.xyz.mpv.BaseMPVView`; 2-param ctor for XML inflation; companion `@JvmStatic lateinit var playerPreferences` for Koin bridging; `initOptions()` (hwdec, profile=fast, 256MB demuxer cache, keep-open, tls-ca-file, subtitle styling); `observeProperties()` (time-pos, duration, volume, track-list, pause, paused-for-cache, seeking, eof, sid, aid); `loadTracks()` returns (subs, audio) with "Off" prepended; sid/aid as strings (NOT ints — MPV returns "node"/"string"); `applySubtitlePreferences()` applies 13 subtitle props via setPropertyInt/String
- `PlayerObserver.kt` — MPV event bridge (EventObserver + LogObserver → Callback interface with onEvent, onEventProperty overloads, onFileEnded)
- `MpvConfigManager.kt` — writes default mpv.conf + input.conf to filesDir/mpv/
- `PlayerUtils.kt` — resolveUrlForMpv (content:// → fd:// via openContentFd + Utils.findRealPath)
- `PlayerEnums.kt` — PlayerMode { MINIMIZED, FULLSCREEN }, PlayerLoadingState, VideoTrack, DefaultPlayerView, PlayerVideoAspect
- `PlayerInitializer.kt` — copyAssets (subfont.ttf to config ROOT), initMpvView (7-step init sequence), loadVideo (500ms delay for offline), destroyMpv (reflection destroy, each step in own try/catch)
- `PlayerStateHolder.kt` — shared state for both watch + fullscreen (35 StateFlows: playerMode, loadingState, isPlaying, position, duration, buffering, bufferAheadTime, controlsVisible, controlsLocked, episodeList, currentEpisodeIndex, isSwitchingEpisode, subtitleTracks, audioTracks, currentSubtitleId, currentAudioId, availableServers, currentServer, availableAudioVersions, currentAudioVersion, currentVideoQuality, currentVideoUrl, currentVideoTitle, showStartOverOverlay)
- `PlayerPreferences.kt` — 50 prefs (pref_ prefix): playback, player view, subtitle styling (13 props), subtitle behavior, watched appearance
- `PlayerEpisodePreferences.kt` — 14 prefs (player_ep_ prefix): episode list display (separate from detail page)
- `WatchProgressStore.kt` — JSON map keyed by "$anilistId:$episodeUrl"; Progress(position, duration, title, updatedAt, coverUrl, animeTitle, episodeNumber, thumbnailUrl); reactive changes Flow
- `PlaybackStateStore.kt` — resume state (videoUrl, server, audio, quality, headers, trackIds, sourceId)
- `subtitles/SubtitleTrackFormatter.kt` — standalone ugly-filename detection (.vtt/.srt/.ass + hash-like → display only language code)
- `controls/MinimizedControls.kt` — portrait overlay (timestamp TL, sub+quality TR, play/pause center with 3-zone double-tap, MinimalSeekbar+fullscreen RIGHT)
- `controls/FullscreenControls.kt` — landscape overlay (lock+title TL, server/sub/audio/quality/more TR, replay10/play-pause/forward10 center, seekbar+speed+rotate+skip+minimize+PiP bottom) — NO statusBarsPadding (player.md §3)
- `controls/MinimalSeekbar.kt` — 28dp target, 5dp 3-layer track, 14dp thumb while dragging, floating time pill
- `controls/EpisodeSwitchingOverlay.kt` — loading overlay during switches
- `di/PlayerModule.kt` — Koin module (PlayerPreferences, PlayerEpisodePreferences, WatchProgressStore, PlaybackStateStore)
- `res/layout/mpv_view.xml` — XML layout for BaseMPVView inflation (FQCN: app.confused.anikuta.core.player.AnikutaMPVView)
- `assets/subfont.ttf` — 6.3MB subtitle font (copied to config ROOT at init)

**`:feature:watch` (NEW — was empty skeleton)**
- `WatchRequest.kt` — data class (videoUrl, videoHeaders, videoTitle, anilistId, animeTitle, coverUrl, coverColor, episodeUrl, episodeNumber, sourceId, source, videoServer, videoAudio, videoQuality, episodeList)
- `WatchScreen.kt` — YouTube-style: sticky 16:9 MPV player + episode description + episode list; floating top bar (hides on scroll); fullscreen mode switch (same MPV instance); MPV event observer wiring; progress saving (periodic 10s + on dispose); resume position (on FILE_LOADED); episode switching (re-resolve via ResolverService → load new video → same surface); auto-hide controls (5s/4s); cover-color dynamic theming (generateDynamicScheme wraps subtree in MaterialTheme override)

**`:feature:player` (build.gradle only — no Kotlin files yet)**
- Fullscreen handled WITHIN WatchScreen (mode switch, not separate screen)

**`:core:designsystem` (modified)**
- `theme/CoverColor.kt` — generateDynamicScheme(coverColor) + extractDominantColor(bitmap) using Palette

**`:core:source-api` (modified)**
- OkHttp changed from implementation to api (Video.headers exposes okhttp3.Headers as public type)

**`:feature:video-resolver` (modified)**
- ResolverVideo widened with videoHeaders field (serialized from Video.headers via names().joinToString)
- VideoTitleParser updated to pass headers through

**`:app` (modified)**
- `MainActivity.kt` — watchTarget state + when branch (above detailAnimeId) + BackHandler + onVideoSelected constructs WatchRequest
- `App.kt` — registered preferenceModule + playerModule in Koin startup
- `build.gradle.kts` — ABI splits enabled (arm64-v8a, ADR-032); added :core:preferences, :core:player, :feature:watch, :feature:player deps

### Key MPV gotchas preserved
1. subfont.ttf at config-dir ROOT (NOT fonts/ — was a 15-build subtitle rendering bug)
2. sid/aid read as strings (getPropertyInt returns "unsupported format")
3. 256MB demuxer cache + keep-open=true
4. sub-add only after FILE_LOADED (sending before causes silent drops)
5. content:// → fd:// resolution via openContentFd + Utils.findRealPath
6. 500ms offline loadfile delay (prevents WinID assertion crash)
7. Single MPV instance (NEVER recreated on mode switches — ADR-025)
8. HTTP headers via setOptionString("http-header-fields", ...) before loadfile
9. sub-ass-force-margins + sub-use-margins set BEFORE initialize
10. sub-fonts-dir + osd-fonts-dir set AFTER initialize (runtime API)

### CI builds
- 12+ push/fix cycles to resolve compilation errors
- Final successful build: Run ID 29851152912
- APK: app-arm64-v8a-debug.apk (56MB)
- Verified: libmpv.so (5.5MB) + FFmpeg libs + subfont.ttf (6.3MB) + cacert.pem (236KB)

## What's NOT done (gaps for next agent)

### High priority
- Bottom-up sheets NOT implemented: SubtitleTracksSheet, SubtitleSettingsSheet, AudioTracksSheet, QualitySheet, SpeedSheet, ServerSheet, MoreSheet — currently TODO callbacks in controls
- SubtitleSettingsPanel NOT ported (8 row primitives + 3 sections + 13 MPV properties) — the "prime example" sheet per design language
- NumericKeypad + ColorPickerDialog NOT ported
- FullscreenGestureHandler NOT ported (tap, double-tap seek, horizontal seek, vertical brightness/volume, pinch zoom)
- Anime metadata (title, coverUrl, coverColor) NOT threaded from AnimeDetailScreen — currently empty/null in WatchRequest
- Full episode list NOT passed — currently only the current episode (listOf(episode))

### Medium priority
- ServerVersionDropdowns NOT ported
- FirstTimePlayerPrompt NOT ported
- PlayerSettingsScreen NOT created
- EpisodeMetadataRepository NOT wired in Koin (no concrete source)
- Subtitle track auto-selection NOT implemented (defaultSubtitleMode pref exists but not consumed)
- Audio track auto-selection NOT implemented
- Watched grayscale+blur treatment NOT implemented (design principle #5 — net-new, OLD project doesn't have it either)
- AniList progress sync NOT implemented
- External subtitle track loading (sub-add) NOT implemented — only internal tracks from MPV

### Low priority
- PlayerEpisodePreferences NOT consumed by the episode list (display prefs exist but episode list is simplified)
- Scroll position preservation in episode list NOT implemented
- "Start over?" overlay shown but no tap handler
- Buffer-wait pattern (force pause + poll demuxer-cache-time) NOT implemented
- Audio focus handling NOT implemented
- PiP auto-entry on nav-away NOT implemented (manual button only)

## Key files for next agent
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt` — the main watch page (753 lines)
- `core/player/src/main/java/app/confused/anikuta/core/player/AnikutaMPVView.kt` — the MPV wrapper
- `core/player/src/main/java/app/confused/anikuta/core/player/PlayerInitializer.kt` — MPV init/destroy
- `core/player/src/main/java/app/confused/anikuta/core/player/PlayerStateHolder.kt` — shared state
- `core/player/src/main/java/app/confused/anikuta/core/player/controls/` — MinimizedControls + FullscreenControls
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/controls/sheets/PlayerSheets.kt` — reference for sheets to port
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/controls/SubtitleSettingsPanel.kt` — reference for subtitle panel
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/controls/NumericKeypad.kt` — reference for keypad
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/player/controls/PlayerGestureHandler.kt` — reference for gestures
