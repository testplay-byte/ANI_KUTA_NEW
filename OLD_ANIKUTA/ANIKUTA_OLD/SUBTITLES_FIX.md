# ANI-KUTA Subtitle Rendering — Complete Issue History & Solution

> **Status: SOLVED** (2026-07-12, commit `67e165c` on `player-experiment`)
> This document records the full journey of debugging why subtitles wouldn't
> render in the ANI-KUTA video player, the root cause, and the fix.

---

## TL;DR

**Subtitles didn't render because the bundled `subfont.ttf` asset was a GitHub
HTML 404 error page, not a real TrueType font.** libass found the file at the
correct path but couldn't parse it as a font, so the font provider never
initialized and no subtitle text could be drawn. Everything else in the
pipeline (TLS, .vtt download, webvtt parsing, track selection, `sid`) worked
correctly.

The fix: replaced the fake HTML file with the real DejaVu Sans TTF (6.4 MB).

---

## The full debugging journey

### Build #263 (the starting point) — "Subtitles don't render"

The app plays video fine but subtitles never appear on screen. ~6 builds of
targeted patches over multiple sessions failed to fix it. The symptom: video
plays, the subtitle track shows up in the sheet, but no text is drawn on the
video.

### Phase 1: The font-path fix (redo, commit `626973b`)

**Hypothesis:** `subfont.ttf` was copied to `mpv/fonts/` (a subdirectory) but
the mpv-lib native `BaseMPVView.initialize()` calls
`ass_set_fonts(<configDir>/subfont.ttf)` looking for it at the **config-dir
root**.

**Fix applied:** Rewrote `PlayerActivity.copyAssets()` to copy both
`cacert.pem` and `subfont.ttf` to the `mpv/` root (not `fonts/`). Mirrored
aniyomi's `setupPlayerMPV()` / `initOptions()` exactly. Removed dead options
(`force-window`, `vid`, `cache-secs`, `slang`, `sub-fonts-dir` via wrong API).

**Result:** Logcat showed `SUBTITLE_FONTCHECK: subfont.ttf at config root = true`.
The font-path was now correct. But subtitles **still didn't render.** This fix
was necessary but not sufficient.

### Phase 2: The feedback-loop fix (commit `5c01567`)

**New symptom (from verbose log):** `sid=1` was being set correctly, then
immediately turned off (`sid=no`), then re-selected, in a ~2.5s loop. The user
tapped "Off" in the subtitle sheet, my `autoSelectSubtitleTrack` (mode=on)
re-enabled it, the user tapped Off again...

**Fix applied:** `autoSelectSubtitleTrack` now respects `userDisabledSubtitles`
in ALL modes. The mode pref (off/on/auto) only controls **initial** auto-
selection on new episode. After that, the user's explicit choice wins for the
current session.

### Phase 3: The verbose logging (commit `7ed1d3c`)

**Problem:** All prior logs were at `warn` level, which **hides all libass
messages**. We were flying blind — the actual libass error was invisible.

**Fix applied:** Added `verboseLogging()` preference (wired to `logLvl="v"` +
`msg-level=all=v`). Added `dumpSubtitleState()` diagnostic that logs
track-list, sid, sub-visibility, sub-text. Added `SUBTITLE_FONTCHECK`.

**Result:** The next log finally showed the full libass chain — and the real
error.

### Phase 4: THE root cause (commit `67e165c`) — THE FIX

**The verbose log revealed:**
```
mpv/osd/libass  V  Setting up fonts...
mpv/osd/libass  I  can't find selected font provider
mpv/sub/ass     I  fontselect: ... -> /data/.../mpv/subfont.ttf, 0, (none)
mpv/sub/ass     I  Error opening font: '/data/.../mpv/subfont.ttf', 0
```

The font **was at the correct path** (Phase 1 fix worked), `fontselect` found
it, but it **could not be opened**.

**Investigation:** Ran `file app/src/main/assets/subfont.ttf`:
```
HTML document, Unicode text, UTF-8 text, very long lines (31686)
```

The 311,901-byte "subfont.ttf" was **a GitHub HTML 404 error page**, not a
font. The first bytes were `\n\n\n\n<!DOCTYPE html>` instead of a valid TTF
signature (`\x00\x01\x00\x00`). Someone had downloaded it from a non-raw
GitHub URL and saved the HTML redirect page as the font file.

**Fix:** Downloaded the real DejaVu Sans TTF (6,365,592 bytes, valid
`\x00\x01\x00\x00` signature) from the aniyomi-mpv-lib repo. Replaced the
fake HTML file. Verified: `TrueType Font data, 20 tables`.

**Result:** Subtitles finally rendered. ✅

---

## Why this took so long to find

1. **The corrupt font looked like a real file.** It had the right name
   (`subfont.ttf`), a plausible size (312 KB), and was in the right directory.
   Nothing in the build process flagged it.

2. **The error was invisible at `warn` log level.** libass messages are
   `VERBOSE`/`INFO` level. With `msg-level=all=warn`, the "Error opening font"
   and "can't find selected font provider" messages were silently dropped.
   Every log for 6+ builds showed only the Kotlin-side `SUBTITLE_DIAG` lines,
   which all looked correct (sid=1, track selected, etc.).

3. **Multiple red herrings.** The font-path issue (Phase 1), the feedback
   loop (Phase 2), and the TLS-verify question (disproved) each looked like
   plausible root causes and consumed debugging effort.

4. **The pipeline worked end-to-end except for the font.** The .vtt downloaded,
   parsed, the track was added, `sid` was set, `sub-visibility` was true —
   everything was correct except libass couldn't render text without a working
   font.

---

## The complete subtitle pipeline (what "working" looks like)

With the real font in place, the pipeline is:

1. **FILE_LOADED** fires → `loadExternalTracks()` runs
2. `sub-add <url> auto <lang>` command sent to MPV
3. MPV downloads the .vtt over HTTPS (TLS verified via `cacert.pem`)
4. MPV parses it: `Found 'webvtt' at score=100` → `Detected file format: webvtt`
5. Track added to track-list: `● Subs --sid=1 (webvtt) [external]`
6. `autoSelectSubtitleTrack` selects it: `sid=1`
7. libass initializes: `Setting up fonts...` → loads `subfont.ttf` →
   `font provider` ready
8. When a cue is active at the current timestamp: `sub-text` is non-empty →
   libass renders the text on the GPU surface

The `SUB_DUMP` diagnostic confirms all of this:
```
SUB_DUMP: track-count=3 sid=1 sub-visibility=true sub-start=5 sub-delay=0 sub-text='[dialogue]'
SUB_DUMP:   [2] type=sub id=1 lang='English' selected=true codec='webvtt' external=true
```

---

## Key files & what they do

| File | Role |
|---|---|
| `app/src/main/assets/subfont.ttf` | The DejaVu Sans TTF font libass uses to render subtitle text. **MUST be a real TTF** (was the root-cause bug). |
| `app/src/main/assets/cacert.pem` | Mozilla CA bundle for TLS verification of HTTPS .vtt downloads. |
| `PlayerActivity.copyAssets()` | Copies both assets to the `mpv/` config-dir **root** (not `fonts/`). |
| `PlayerActivity.initMpvView()` | Centralized MPV init: config files, asset copy, `sub-ass-force-margins`, `initialize`, observers, runtime `sub-fonts-dir`/`osd-fonts-dir`. |
| `PlayerActivity.autoSelectSubtitleTrack()` | Reads `defaultSubtitleMode` pref (off/on/auto); respects `userDisabledSubtitles`. |
| `PlayerActivity.dumpSubtitleState()` | Diagnostic: logs track-list, sid, sub-visibility, sub-text. |
| `AnikutaMPVView.initOptions()` | MPV init-time options, mirrors aniyomi. `msg-level` wired to `verboseLogging()` pref. |
| `AnikutaMPVView.loadTracks()` | Builds user-friendly track names (filters out ugly .vtt filenames). |
| `PlayerPreferences.defaultSubtitleMode()` | off/on/auto — controls initial auto-selection. |
| `PlayerPreferences.verboseLogging()` | Toggle for `all=v` MPV logging (default false now that subs work). |
| `SubtitleTracksSheet` | Button/chip-style subtitle selection UI (Off + language FilterChips). |
| `SubtitleStatusPill` | On-screen indicator — only shows DOWNLOADING/NONE/ERROR (not ON/OFF). |

---

## Configuration

### Default subtitle mode (Settings → Player → Subtitles)
- **Off** — never auto-select subtitles on new episode
- **On** — always auto-select the best track (default)
- **Auto** — only select if a track matches `preferredSubtitleLanguage`

### Preferred subtitle language
Comma-separated language codes (default `en,eng`). Used as a tiebreaker in
"On" mode and as the match criterion in "Auto" mode.

### Verbose MPV logging
Toggle (default **off**). When on, sets `logLvl="v"` + `msg-level=all=v` so
logcat shows the full mpv subtitle chain (libass, .vtt, cues, render). Useful
for future debugging; has a performance cost.

---

## Lessons learned

1. **Verify binary assets are valid.** A `file` check on every bundled asset
   would have caught the fake font immediately. (The build never validates
   asset contents.)

2. **Enable verbose logging early.** Debugging subtitle rendering without
   `all=v` log level is nearly impossible — the critical libass messages are
   all VERBOSE/INFO. The `verboseLogging()` toggle now makes this easy.

3. **The mpv-lib `subfont.ttf` must be at the config-dir root**, not in a
   `fonts/` subdirectory. The native `ass_set_fonts()` call looks for it at
   `<configDir>/subfont.ttf`.

4. **`sub-fonts-dir` is a runtime option** (`setPropertyString`), not an
   init option (`setOptionString`). Setting it at init time is silently
   ignored.

5. **`force-window` is managed by the library** (`BaseMPVView.initialize`
   forces `no` after init and toggles it in surface callbacks). Setting it
   in `initOptions()` is dead code.

6. **User explicit choice must win over auto-selection.** The feedback loop
   (mode=on re-enabling subs after the user tapped Off) was a real UX bug
   that looked like the subtitle bug.

---

## Build history (subtitle-related)

| Commit | Branch | Change | Result |
|---|---|---|---|
| `4c67939` | main | Build #263 (broken starting point) | Subtitles don't render |
| `626973b` | main | Font-path fix (redo) — subfont.ttf to config root | Font path correct, still no render |
| `9022763` | player-experiment | Subtitle defaults (off/on/auto) + minimized default | No render (feedback loop) |
| `5c01567` | player-experiment | Fix feedback loop + status indicator + ntfy.sh | No render (blind to libass) |
| `7ed1d3c` | player-experiment | Verbose logging + SUB_DUMP diagnostic | Exposed the real error |
| `67e165c` | player-experiment | **THE FIX: real DejaVu Sans TTF** | **✅ Subtitles render** |

---

## How to re-verify subtitles work

1. `adb install -r app-debug.apk && adb shell pm clear app.anikuta`
2. Play an episode with subtitles
3. `adb logcat -d | grep -iE "SUB_DUMP|libass|Error opening font"`
4. **Pass:** `SUB_DUMP: ... sub-text='[dialogue]'` (non-empty) and NO
   "Error opening font" / "can't find selected font provider"
5. **Fail:** if "Error opening font" reappears, the `subfont.ttf` asset is
   corrupt again — re-download the real TTF.
