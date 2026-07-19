# Phase 4 — Detailed Plan (Video Player — MPV from aniyomi)

> Phase 4 builds the video player — where the user watches anime episodes.
> We reuse aniyomi's MPV-based player (the core of the app).

---

## Goals

1. **Player opens and plays video** — tap an episode → player → video plays.
2. **MPV integration** — copy aniyomi's MPV library + PlayerActivity + PlayerViewModel.
3. **Extension stream resolution** — the extension resolves the video URL from the source.
4. **Playback controls** — seek, play/pause, skip, audio/subtitle tracks, gestures, PiP.
5. **Progress saving** — save watch progress to the local DB.
6. **M3 Expressive player UI** — consistent with the rest of the app.

---

## Sub-steps

### Step 4.1 — Copy MPV native library + PlayerActivity + PlayerViewModel
- Copy aniyomi's `ui/player/` package (PlayerActivity, PlayerViewModel, controls, loader, settings, utils).
- Add the MPV native library dependency (`com.github.aniyomiorg:aniyomi-mpv-lib`).
- Rename packages to `app.anikuta.player.*`.
- Wire in DI (AppModule).
- **This is the biggest single step** — the player is ~3,000+ lines of code.

### Step 4.2 — Episode loader (stream resolution)
- When the user taps an episode, the loader:
  1. Gets the anime's source (from the extension system).
  2. Calls `source.fetchEpisodeList()` → gets episode URLs.
  3. Calls `source.fetchVideoList()` → resolves video stream URLs.
  4. Passes the video URL to MPV via `MPVLib.command("loadfile", url)`.
- **Requires a working extension** — this is where the extension system needs to be functional.
- If no extension is loaded → show "No streaming source available".

### Step 4.3 — Player controls UI (M3 Expressive)
- **Overlay controls** — seek bar, play/pause, skip ±10s, audio/subtitle track selector.
- **Gesture controls** — swipe left/right to seek, swipe up/down for brightness/volume.
- **PiP (Picture-in-Picture)** — auto-enters PiP when the user leaves the player.
- **Spring-based animations** — controls fade in/out with spring, seek bar snaps with spring.
- **M3 Expressive styling** — `surfaceContainerHigh` on the overlay, rounded controls.

### Step 4.4 — Player settings
- Copy aniyomi's player settings (decoder, subtitles, audio, gestures).
- Add a settings screen accessible from the player.
- Persist settings via preferences.

### Step 4.5 — Progress saving
- Save watch progress (episode ID, position in seconds, total duration) to the local DB.
- "Continue watching" row on the home page (Phase 5 — History).
- Resume from where the user left off when reopening an episode.

### Step 4.6 — Navigation: detail → player
- Add navigation route: `player/{anilistId}/{episodeNumber}`.
- Episode tap on the detail page → player.
- Back button → returns to the detail page.
- Player is fullscreen (no bottom nav).

### Step 4.7 — Phase 4 verification
- Tap an episode → player opens → video plays.
- Seek, pause, skip, track selection all work.
- Progress saves.
- PiP works.
- Fix bugs → Phase 4 complete → Phase 5 (library + history + search).

---

## What Phase 4 does NOT include
- **No downloads** (Phase 7) — streaming only.
- **No the other 3 designs** (Phase 6) — Material 3 only.
- **No AniList tracker sync** (Phase 8) — progress saves locally only.
- **No custom skip-intro** — future enhancement.

---

## Key concern: extension system

The player **requires a working extension** to resolve video streams. Currently our extension system is **stubbed** (Step 1.7 created stubs that compile but don't actually load extensions).

Before Phase 4 can fully work, we need to:
1. Make `AnimeExtensionLoader` actually load extension APKs (DexClassLoader).
2. Make `AnimeExtensionManager` discover + install extensions.
3. The AniKoto 180 extension (from the recommended repo) needs to be installable.

This is a **prerequisite** that could be done as Step 4.0 (or as a separate mini-phase before Phase 4).

---

## Open questions
1. **Extension system first?** Should we make the extension system functional BEFORE the player (Step 4.0), or build the player with mock data first?
2. **MPV version** — aniyomi uses `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n`. Use the same?
3. **Player UI complexity** — copy aniyomi's full player UI (complex, ~2,000 lines of controls) or build a simpler custom one?
4. **PiP priority** — is PiP important for Phase 4, or can it come later?
5. **Gestures** — swipe-to-seek, brightness/volume gestures — Phase 4 or later?
