# Player Screen Fix Plan

> Created before making ANY changes. Read this first.
> Last updated: Session 30

---

## Current Problems (from user testing)

### 1. Custom loading overlay is redundant and ugly
- **Problem**: I built a custom loading overlay (full-screen or 16:9 box with CircularProgressIndicator)
- **Reality**: MPV already handles loading — `force-window=yes` shows a black surface, then the video appears when `MPV_EVENT_FILE_LOADED` fires. The `MinimizedControls` also has its own buffering spinner overlay on the video area.
- **Fix**: REMOVE the custom loading overlay entirely. MPV + MinimizedControls already handle this. Only keep the ERROR overlay (for when video fails to load).

### 2. Video area is fixed 16:9 — should be adaptive
- **Problem**: I hardcoded `aspectRatio(16f/9f)` for the video area
- **Reality**: Videos have different aspect ratios. The video area should fill the available width and let the video's natural aspect ratio determine the height.
- **Fix**: Use `Modifier.fillMaxWidth()` with no aspect ratio constraint. MPV renders the video at its natural ratio inside the view. The view should wrap content height based on the video.

### 3. Top bar is not a proper floating pill container
- **Problem**: I used individual pill-shaped Surfaces for back/settings buttons + a plain text title — looks disjointed, not like a unified floating bar
- **Reference**: Home page uses a single `Surface(shape = RoundedCornerShape(20.dp), color = surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 6.dp)` containing a Row with title + icons
- **Fix**: Use the same pattern as the home page — a single floating Surface with RoundedCornerShape(20.dp) containing all three elements (back button, title, settings)

### 4. Episode caching — not persistent across app restart
- **Problem**: `episodeCache` is in-memory (companion object) — lost when app is killed
- **Fix**: Serialize episode lists to JSON and store in app's filesDir. On app start, check if cached file exists before fetching from extension.

### 5. Episode list in player — not synced
- **Problem**: Player's `EpisodeListView` shows empty because `viewModel.episodeList` is never populated
- **Fix**: Pass `anilistId` from detail page → player uses it to load the same episode cache

---

## Screen Naming

The user wants a proper name for this screen. Proposals:
- **"Player Screen"** (minimized mode) / **"Fullscreen Player"** (fullscreen mode)
- **"Watch Screen"** — emphasizes watching + browsing episodes
- **"Now Playing"** — familiar from music players

I'll go with **"Player Screen"** for now and ask the user if they prefer a different name.

---

## Implementation Order

1. **Remove custom loading overlay** (keep only error overlay)
2. **Fix video area** — adaptive aspect ratio (not fixed 16:9)
3. **Fix top bar** — single floating pill Surface (match home page pattern)
4. **Episode caching** — persist to disk (JSON in filesDir)
5. **Episode list in player** — pass anilistId, load from cache
6. **Document** — update PLAYER-RULES.md with the new architecture

---

## Architecture: What MPV handles vs what we handle

| Feature | Who handles it | Notes |
|---------|---------------|-------|
| Video rendering | MPV (AndroidView) | force-window=yes shows surface immediately |
| Loading state | MPV + MinimizedControls | MPV shows black surface, MinimizedControls shows spinner |
| Buffering indicator | MinimizedControls/FullscreenControls | Both have buffering spinners |
| Error display | Our code | Only ERROR overlay is needed |
| Play/pause | MPV via property | We just toggle pause property |
| Seek | MPV via time-pos property | We set time-pos |
| Track selection | MPV via sid/aid | We call loadTracks() + set sid/aid |
| Subtitle styling | MPV via sub-* properties | We set from preferences |
| Aspect ratio | MPV | Video renders at natural ratio inside the view |
| Controls overlay | Our composables | MinimizedControls + FullscreenControls |
| Mode switching | Our code (Crossfade) | MPV view stays fixed |

---

## File Structure (current + planned)

```
app/anikuta/player/
├── PlayerActivity.kt           # Host + PlayerScreen composable
├── PlayerViewModel.kt          # State
├── PlayerPreferences.kt        # All prefs
├── AnikutaMPVView.kt           # MPV wrapper
├── PlayerObserver.kt           # MPV events
├── WatchProgressStore.kt       # Resume position
├── PlayerEnums.kt              # Enums
├── MpvConfigManager.kt         # MPV config files
├── PlayerMediaSession.kt       # Media session
├── EpisodeCacheStore.kt        # NEW: persistent episode caching
└── controls/
    ├── MinimizedControls.kt    # Minimized video overlay
    ├── FullscreenControls.kt   # Fullscreen video overlay
    ├── PlayerGestureHandler.kt # Gestures
    ├── ServerVersionDropdowns.kt
    ├── EpisodeListView.kt
    ├── FirstTimePlayerPrompt.kt
    ├── SubtitleSettingsPanel.kt
    └── sheets/
        ├── PlayerSheet.kt
        └── PlayerSheets.kt
```
