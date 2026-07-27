# ANI-KUTA Video Player Redo Plan — Match aniyomi Exactly

> Status: Implemented (re-applied after sandbox clear)
> Root cause of subtitle failure: **confirmed** (see §1)
> Strategy: Rewrite the MPV initialization layer to mirror aniyomi's
> `setupPlayerMPV()` / `AniyomiMPVView.initOptions()` exactly, while keeping
> ANI-KUTA's working UI / ViewModel / episode-switching / controls untouched.

---

## 1. Root-cause analysis (why subtitles don't render)

Both aniyomi and ANI-KUTA use the **same** native library
`com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` (provides `is.xyz.mpv.MPVLib`
and `is.xyz.mpv.BaseMPVView`). aniyomi subtitles render; ANI-KUTA's don't.
The difference is purely in the wrapper code.

### The decompiled `BaseMPVView.initialize()` (from the AAR)

```java
public final void initialize(configDir, cacheDir, logLvl, vo) {
    MPVLib.create(context, logLvl);
    MPVLib.setOptionString("config", "yes");
    MPVLib.setOptionString("config-dir", configDir);
    // gpu-shader-cache-dir, icc-cache-dir -> cacheDir
    this.initOptions(vo);                 // ← OUR initOptions runs HERE (before MPVLib.init())
    MPVLib.init();                         // ← mpv context initialized
    this.postInitOptions();                // ← AFTER init
    MPVLib.setOptionString("force-window", "no");   // ← library FORCES force-window=no
    MPVLib.setOptionString("idle", "once");
    this.getHolder().addCallback(...);
    this.observeProperties();
}
```

Key implications:
- `initOptions()` is the **only** correct window for init-time `setOptionString`
  calls (runs between `MPVLib.create()` and `MPVLib.init()`).
- `force-window` is set to `"no"` by the library itself after init — so any
  `setOptionString("force-window","yes")` in `initOptions()` is **overwritten**.
  (The library toggles it to `"yes"`/`"no"` in `surfaceCreated`/
  `surfaceDestroyed`.) ANI-KUTA's `force-window=yes` is dead code.

### The smoking gun (from build #263 logcat)

```
mpv/osd/libass  V  Loading font file '/data/user/0/app.anikuta/files/mpv/fonts/subfont.ttf'
mpv/osd/libass  I  Error opening memory font 'subfont.ttf'
mpv/osd/libass  I  can't find selected font provider
```

The mpv-lib native layer calls `ass_set_fonts(tracker, "<configDir>/subfont.ttf",
"sans-serif", …)` — it looks for `subfont.ttf` **directly in the config dir**
(`<filesDir>/mpv/subfont.ttf`) as the default fallback font.

**ANI-KUTA copied `subfont.ttf` to `<filesDir>/mpv/fonts/subfont.ttf`** (a
subdirectory), so `<configDir>/subfont.ttf` did **not exist** →
`ass_set_fonts` failed → "Error opening memory font" → the font provider was
never initialised → "can't find selected font provider" → **no subtitle text
can be rendered, ever**.

**aniyomi copies `subfont.ttf` to `<filesDir>/mpv/subfont.ttf`** (the config
dir root, via `copyAssets()` → `mpvDir.createFile(filename)`). That is the
single critical difference.

### Secondary divergences (compounding / hygiene)

| Option / behaviour | aniyomi | ANI-KUTA (before redo) | Impact |
|---|---|---|---|
| `force-window` | NOT set (library manages) | `setOptionString("force-window","yes")` in initOptions | Dead — overwritten by library. Remove. |
| `vid` | NOT set | `setOptionString("vid","1")` | Unnecessary; mpv auto-selects. Remove. |
| `cache` / `cache-secs` | NOT set | `cache=yes`, `cache-secs=120` | Not subtitle-related; aniyomi relies on demuxer cache only. Keep demuxer cache (256 MB) for buffering, drop `cache`/`cache-secs`. |
| `slang` | NOT set (post-load TrackSelect instead) | `slang=en,eng` | Bypasses ANI-KUTA's own auto-select. Remove. |
| `sub-fonts-dir` | `setPropertyString` (runtime), after init; points at `mpv/fonts/` for USER fonts | `setOptionString` (init) pointing at `mpv/fonts/` | Wrong API. `sub-fonts-dir` is runtime-only; init-time setOptionString is silently ignored/rejected. Must use setPropertyString after init. |
| `osd-fonts-dir` | `setPropertyString` (runtime) | NOT set | Add (matches aniyomi). |
| `font-dir` | NOT set | `setOptionString("font-dir",…)` | Not an mpv option aniyomi uses. Remove. |
| `sub-ass-force-margins` | `setOptionString("yes")` before initialize | NOT set | Add. |
| `sub-use-margins` | `setOptionString("yes")` before initialize | NOT set | Add. |
| `subfont.ttf` location | `mpv/subfont.ttf` (root) | `mpv/fonts/subfont.ttf` | **THE BUG.** Move to root. |
| `mpv.conf` / `input.conf` | Written (from pref, default empty) before init | NOT written at init | Write a clean (minimal) file to prevent stale-config interference. |
| log level | `"warn"` (or `"info"` verbose) | `"v"` (build #263 diagnostic) | Revert to `"warn"`. |
| `msg-level` | `all=warn` (or `all=v` verbose) | `all=warn,demuxer=v,sub=v,…` | Revert to `all=warn`. |

---

## 2. Files changed

| File | Change |
|---|---|
| `app/.../player/AnikutaMPVView.kt` | Rewrote `initOptions()` to match aniyomi's option set. Removed force-window/vid/cache/cache-secs/slang/sub-fonts-dir/font-dir. Kept working subtitle-prefs + track API + live-update method. |
| `app/.../player/PlayerActivity.kt` | Rewrote `copyAssets()` to copy both `cacert.pem` AND `subfont.ttf` to `mpvDir` ROOT. Added centralized `initMpvView()` companion helper (config files + margins + initialize + observers + sub-fonts-dir/osd-fonts-dir). |
| `app/.../player/PlayerScreen.kt` | Both `AndroidView` factories (minimized + fullscreen) call the new `PlayerActivity.initMpvView()` helper instead of inlining init. |
| `app/.../player/MpvConfigManager.kt` | Cleaned `DEFAULT_MPV_CONF`: removed `force-window=yes` (library manages it) and `cache-secs` (handled at runtime). |

**Untouched (working, out of scope):** `PlayerViewModel.kt`, `PlayerObserver.kt`,
`PlayerScreen.kt` UI/controls, `controls/*`, `sheets/*`, `SubtitleSettingsPanel.kt`,
episode-switching, watch progress, dynamic theming, PiP, etc.

---

## 3. Exact implementation spec

### 3.1 `AnikutaMPVView.initOptions()` — match aniyomi

Removed (with reason):
- `force-window=yes` → library forces `no` after init; managed by surface callbacks.
- `vid=1` → mpv auto-selects video track.
- `cache=yes` / `cache-secs=120` → aniyomi doesn't set; demuxer cache handles buffering.
- `slang=en,eng` → ANI-KUTA has its own post-load auto-select; let it work.
- `sub-fonts-dir` / `font-dir` (setOptionString at init) → wrong API; moved to runtime setPropertyString in `initMpvView()`.

Kept:
- `applySubtitlePreferencesInit()` (subtitle style via setOptionString — matches aniyomi).
- `applySubtitlePreferences()` (live runtime updates via setPropertyString — used by SubtitleSettingsPanel).
- Track API (`sid`/`aid`/`loadTracks`) — already matches aniyomi's TrackDelegate pattern.
- demuxer-max-bytes=256MB (intentional buffering divergence, documented).

### 3.2 `PlayerActivity.copyAssets()` — subfont.ttf to ROOT

Both `cacert.pem` and `subfont.ttf` are copied to `mpvDir` (config-dir root).
This mirrors aniyomi's `copyAssets()` exactly. This is THE subtitle fix.

### 3.3 `PlayerActivity.initMpvView()` — centralized init (mirrors aniyomi setupPlayerMPV)

Sequence: write clean mpv.conf/input.conf → copyAssets to root →
setOptionString sub-ass-force-margins + sub-use-margins →
view.initialize(configDir, cacheDir, logLvl) → addLogObserver + addObserver →
http-header-fields → runtime setPropertyString sub-fonts-dir + osd-fonts-dir
→ SUBTITLE_FONTCHECK diagnostic log.

### 3.4 `PlayerScreen.kt` — both factories call the helper

### 3.5 `MpvConfigManager.DEFAULT_MPV_CONF` — clean (no force-window, no cache-secs)

---

## 4. Verification

- `./gradlew :app:compileDebugKotlin` → must compile.
- Runtime: `SUBTITLE_FONTCHECK: subfont.ttf at config root = true (311901 bytes)`,
  NO "Error opening memory font" / "can't find selected font provider",
  subtitles visible on screen.

---

## 5. What is NOT changing

- All UI/Composables, controls, sheets, gestures, PiP, episode list, dynamic
  theming, watch progress, AniList sync, episode switching, buffer-wait system.
- `PlayerViewModel`, `PlayerObserver` (already match aniyomi's pattern).
- Track API (`sid`/`aid`/`loadTracks`) — already correct.
- `sub-add` with `"auto"` flag — already matches aniyomi.
- The `cacert.pem` + `subfont.ttf` assets themselves (already bundled).
