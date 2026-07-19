# 04 — Video Resolver (Server / Audio / Quality Picker)

> The bottom-up sheet that appears **after the user taps an episode** and
> **before the watch page opens**. Resolves the video list (servers, audio
> versions, qualities) from the matched extension, then either auto-plays
> (single video) or shows this sheet for the user to pick. The owner wants
> this as a **complete module** — extensible for new extensions.
>
> **Principles applied:** #2 (no drag handle), #3 (partial height), #11
> (custom M3 — no blue colors, accent on right).
>
> **Components used:** §1 (bottom-up menu — this IS the spec for §1 in
> resolver context).
>
> **Status:** STRUCTURE, BEHAVIOR, and the 3-tier hierarchy fixed (this
> doc). `AudioVersion` extensibility (§7) and the resolver module
> extraction (§8) are the main improvements over the old project.

---

## 1. Position in the navigation flow

```
   tap EpisodeRow (on details screen or watch page)
        │
        ▼
   (1) Episode downloaded on disk? ──YES──► Watch Page (offline, skip resolver)
        │ NO
        ▼
   (2) Video Resolver sheet  ◄── THIS DOC
        │  States: Resolving / Cached / Show / Error (§4)
        │
        ├─ single video?  ──YES──► auto-play → Watch Page
        └─ multiple?      ──YES──► user picks server/audio/quality → Watch Page
```

- The resolver does NOT directly open the player. It always sits between
  episode tap and watch page (unless the episode is downloaded or there
  is exactly one video — both are auto-play shortcuts).
- The resolver is a **bottom-up sheet** (component §1) — no drag handle,
  partial height. Does NOT cover the whole screen (principle #3).
- On the watch page, switching episodes from the list reuses this flow.

---

## 2. Owner's vision

> "After tapping an episode, the app RESOLVES the video — doesn't
> directly open the player. Shows server names, audio versions (sub/dub),
> resolutions (quality). 3-tier hierarchy: Server → Audio → Quality.
> This is a COMPLETE MODULE — easily extensible for new extensions."

The owner flagged this as a "complete module" — the resolver is a
first-class feature, not an afterthought. Extensibility for new
extensions (new audio versions, new title formats, new hierarchies) is
a hard requirement (§7).

---

## 3. The resolution flow (port from old project)

`playEpisode(episode)` is the entry point. Short-circuits in order:

1. **Downloaded on disk?** → play offline file, skip resolver.
2. **`matchedSource` null?** → recover from cache (up to 20×500 ms
   retries while extensions load async).
3. **In-memory video cache hit (10-min TTL)?** → show picker INSTANTLY
   with "Refreshing…" badge + background re-resolve.
4. **Cache miss** → state = `Resolving(episode)` → `resolveVideos`:
   - Try `source.getHosterList(episode)` (preferred).
   - Fallback on ANY exception → `source.getVideoList(episode)`.
   - Filter `videoUrl.isNotBlank()`.
5. `groupVideosByServer(allVideos)` (§6).
6. Result empty → `Error` state. Single video → auto-play. 2+ videos →
   cache (in-memory 10-min) + `Show` state → sheet renders.

**Disk cache note (port):** the disk video cache is **written but not
read** for the picker. Reason: cached `videoUrls` contain `localhost:PORT`
proxy URLs that are stale after app restart (the proxy dies with the
process). Showing stale URLs would let the user tap a dead URL. Only the
in-memory cache (10-min TTL, safe because the proxy is alive) is used
for picker display. New project should either (a) read it with a
proxy-URL refresh step, or (b) stop writing it — TBD (§9).

---

## 4. The states of `VideoPickerState`

```kotlin
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()
    data class Cached(val episode: SEpisode, val serverSections: List<ServerSection>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverSections: List<ServerSection>) : VideoPickerState()
    data class Error(val episode: SEpisode, val message: String) : VideoPickerState()  // NEW
}
```

| State | When | UI |
|---|---|---|
| `Hidden` | No episode tapped | Nothing rendered |
| `Resolving` | First-time resolve (cache miss) | Bottom sheet opens with centered `CircularProgressIndicator` + "Resolving video…" + "Fetching servers from the extension". Sheet chrome visible, dismissible. |
| `Cached` | Cache hit + background re-resolve in progress | Sheet renders INSTANTLY with cached data + "Refreshing…" badge (§5.1) |
| `Show` | Fresh resolve OR background re-resolve finished | Sheet renders with current data, no refreshing badge |
| `Error` (NEW) | `resolveVideos` throws or returns empty | Inline error with Retry + "Open in browser" fallback (§8) |

### 4.1 Smooth-update logic (KEEP — critical UX)

When the background re-resolve finishes, compare to cached data using
`compareServerSections(a, b)` — compares by `videoTitle + resolution`
(stable), NOT `videoUrl` (contains `localhost:PORT` which changes each
resolution). Unchanged → silent `Cached` → `Show` transition (removing
the badge, no re-animation). Changed → update smoothly without
close/reopen. **Hard rule** (analysis §3.3).

---

## 5. Layout — the bottom sheet

```
╔══════════════════════════════════════════════════════════════════╗
║  Select quality                            [⟳ Refreshing…]       ║ ← titleMedium bold
║──────────────────────────────────────────────────────────────────║   + optional badge
║  ┌────────────────────────────────────────────────────────────┐ ║   NO drag handle
║  │ ▼ VidPlay-1                            [HSUB] [DUB] [SUB]   │ ║   (principle #2)
║  │      [Hardsub]  3 qualities                              │ ║
║  │      ▶                                          [1080p]    │ ║ ← VideoRow (play icon
║  │      ────────────────────────────────────────────────────  │ ║   left, weighted spacer,
║  │      ▶                                          [720p]     │ ║   quality chip RIGHT)
║  │      ────────────────────────────────────────────────────  │ ║
║  │      ▶                                          [360p]     │ ║
║  │                                                            │ ║
║  │ ▶ HD-1                                [SUB]                 │ ║ ← collapsed server
║  │ ▶ VidPlay-2                           [SUB]                 │ ║   (accordion — only one
║  └────────────────────────────────────────────────────────────┘ ║   expanded at a time)
╚══════════════════════════════════════════════════════════════════╝
   ↑ partial height (principle #3) — underlying screen visible above (dimmed scrim)
```

### 5.1 Header row

- `Text("Select quality", titleMedium, bold)` weighted left.
- Optional `Row { CircularProgressIndicator(14dp, 2dp) + Text("Refreshing…", labelSmall) }`
  on the right — shown only in `Cached` state while `isRefreshing = true`.
- NO drag handle (principle #2). Sheet still dismissible by swipe-down
  on scrim / content.

### 5.2 Body

- `LazyColumn` with `heightIn(max = 420.dp)` + `animateContentSize`:
  shrinks when all servers collapsed, grows up to 420 dp when one
  expands. Smooth height animation.
- **Accordion behavior**: only one server expanded at a time
  (`toggleServer(key)` collapses all others when expanding).

### 5.3 Sheet chrome (component §1)

- `ModalBottomSheet` with `dragHandle = null` (principle #2).
- `skipPartiallyExpanded = false` — sheet sits at natural height (NOT
  full-screen — caused an auto-close glitch in old project).
- Top corners `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`.
- Container: `surfaceContainerLow`. Scrim: `Color.Black @ 0.6f`,
  dismiss-on-tap. Max height ~70% viewport (principle #3).

---

## 6. The 3-tier hierarchy: Server → Audio → Quality

### 6.1 Data model + grouping algorithm

```kotlin
data class ServerSection(val serverName: String, val audioSections: List<AudioSubSection>)
data class AudioSubSection(val audio: AudioVersion, val videos: List<Video>)  // sorted desc by quality
```

`groupVideosByServer(videos)`:

1. Each `Video` is parsed by `VideoTitleParser.parse(video)` into
   `ParsedVideo(video, server, audio, quality)`.
2. Group by `server` → group by `audio` → sort by `quality` descending.
3. Audio display order: `SUB, DUB, HSUB, ANY` (default — user-configurable
   per §7.2).

### 6.2 `VideoTitleParser` — extracting server/audio/quality

Most extensions (AniKoto and `anikototheme/multisrc` users) format video
titles as `"{server} - {audio} - {quality}"`, e.g. `"VidPlay-1 - SUB - 360p"`.
The parser extracts the real server/audio/quality from the title so the
resolver can re-group by audio version and sort by quality — regardless
of how the extension structured the list.

| Field | Extraction |
|---|---|
| Quality | Prefer `Video.resolution` (structured field). Fall back to regex `\b(\d{3,4})p\b` on the title. |
| Audio | Scan title for `\b(SUB\|DUB\|HSUB\|HARDSUB\|SUBBED\|DUBBED)\b`. Map to `AudioVersion.fromToken()`. |
| Server | Token before the first `" - "` separator. If no separator, full title. |

**Graceful degradation (HARD RULE):** if the title doesn't match the
expected format, fall back to server = full title, audio = `ANY`,
quality = `Video.resolution` or `null`. Extensions that don't follow the
convention still work — they just show up as one big "server" with one
"Any" audio section. Never break extensions that don't follow the
convention.

---

## 7. The three row composables

### 7.1 `ServerHeader` — top-level collapsible section

```
┌─────────────────────────────────────────────────────────────────┐
│ ▼ VidPlay-1                                  [HSUB] [DUB] [SUB] │
└─────────────────────────────────────────────────────────────────┘
```

- `Surface(RoundedCornerShape(10.dp), surfaceVariant)`, clickable.
- Left: collapse/expand chevron (`KeyboardArrowDown` expanded /
  `KeyboardArrowRight` collapsed), 20 dp, `onSurfaceVariant`.
- Middle: server name, `titleSmall`, bold, `onSurface`, `weight(1f)`.
- Right: audio tag chips — order: HSUB leftmost, DUB middle, SUB
  rightmost (reversed from default `SUB, DUB, HSUB, ANY` order — SUB on
  the right is the owner's specific preference, KEEP).
  - `Surface(RoundedCornerShape(4.dp), outlineVariant)`, `labelSmall`
    semibold `onSurfaceVariant`.

### 7.2 `AudioSubHeader`

```
      [Hardsub]  3 qualities
```

- `Row(padding(horizontal = 40.dp, vertical = 6.dp))`.
- Audio pill: `Surface(RoundedCornerShape(6.dp),
  secondaryContainer.copy(alpha = 0.7f))`, `labelMedium` semibold
  `onSecondaryContainer`.
- Video count: `bodySmall`, `onSurfaceVariant`. "N qualities" (plural-aware).

**No blue colors** (owner explicit preference — KEEP). Use
`surfaceVariant` / `secondaryContainer` / `outlineVariant` throughout.
Never `primary` for backgrounds (only for the selected quality chip's
text + OK-equivalent actions).

### 7.3 `VideoRow` — the quality row

```
      ▶                                          [1080p]
```

- `Row(padding(horizontal = 56.dp, vertical = 10.dp), clickable)`.
- Left: `PlayArrow` icon, 16 dp, `onSurfaceVariant.copy(alpha = 0.5f)`.
- Middle: weighted spacer (pushes chip right).
- Right: quality chip — `Surface(RoundedCornerShape(50),
  secondaryContainer)` (pill), `labelMedium` semibold
  `onSecondaryContainer`, e.g. "1080p" or "Unknown".
- **Only ONE quality representation** as text (the chip). No duplicate
  plain-text label. (Owner preference — KEEP.)
- `HorizontalDivider(color = outlineVariant.copy(alpha = 0.3f))` between
  video rows.
- Selected video (when re-opening the resolver from the watch page)
  gets a `primary`-colored border on its chip + a `Check` icon before
  the play icon.

---

## 8. The "Resolving" overlay (cache-miss state) + "Error" state (NEW)

When state is `Resolving`, the bottom sheet opens with chrome visible
but the body shows a centered loading card:

```
║                  ┌──────────────────────────┐                    ║
║                  │       ⟳ (spinner)        │                    ║
║                  │   Resolving video…       │                    ║
║                  │ Fetching servers from    │                    ║
║                  │      the extension       │                    ║
║                  └──────────────────────────┘                    ║
```

Centered `Surface(RoundedCornerShape(16.dp), surfaceContainerHigh)`,
`padding(24.dp)`. `CircularProgressIndicator` + `bodyMedium` "Resolving
video…" + `bodySmall` `onSurfaceVariant` "Fetching servers from the
extension".

**Improvement over old project**: the old `Resolving` overlay was a
**full-screen black-60% scrim** that blocked the detail page entirely.
The new project renders it **inside the bottom sheet** — underlying
screen visible above the sheet, just dimmed by the sheet's scrim. Less
jarring, more consistent with the sheet chrome.

**`Error` state (NEW):** when `resolveVideos` throws or returns empty,
the bottom sheet shows an inline error state (instead of hiding + showing
a Toast — old project behavior, REPLACE):

```
║                  ┌──────────────────────────┐                    ║
║                  │       ⚠ (warning)        │                    ║
║                  │  No playable video found │                    ║
║                  │  for this episode.       │                    ║
║                  │  [ Retry ]  [ Open web ] │                    ║
║                  └──────────────────────────┘                    ║
```

Two buttons: **Retry** (re-runs `resolveVideos`, clears the in-memory
cache for this episode first) and **Open in browser** (opens the source
website in the system browser — fallback when all servers fail).

---

## 9. Extensibility — the "complete module" requirement

The owner wants this resolver extensible for new extensions. The old
project's resolver is **already mostly extensible** (analysis §3.9);
the new project closes the remaining gaps:

### 9.1 Already extensible (KEEP)

- **Data model is generic.** `ServerSection` / `AudioSubSection` / `Video`
  are not tied to any specific extension format.
- **Parser has graceful fallback** (§6.2).
- **`Video.resolution` is the structured quality field.** Extensions that
  populate it get proper sorting; extensions that don't are regex-parsed
  from the title.

### 9.2 Needs to be made extensible (NEW — improvements)

| Gap (old project) | New-project fix |
|---|---|
| `AudioVersion` is a closed enum (`SUB, DUB, HSUB, ANY`). | Make it an **open sealed class** or **string-keyed data class** with a registry. Extensions declare their own (Raw, Commentary, multi-language dubs, etc.). |
| `AudioVersion.fromToken()` tokenizer is regex-based — adding tokens requires editing `AUDIO_REGEX`. | Let extensions declare their own token mappings via the registry. |
| Hierarchy fixed at 3 levels (Server → Audio → Quality). | Make it **configurable** — extensions can declare a 4th level (e.g., "mirror") via capability mechanism (ADR-022). |
| Display order (`audioOrder = [SUB, DUB, HSUB, ANY]`) hardcoded. | Make it **user-configurable** (owner might prefer DUB first). |
| No default server/audio/quality preference. | Remember last selection per anime (or globally) and pre-select. |
| No "best quality" auto-pick option. | Add a preference: "Always play highest quality on preferred server, skip picker." |

### 9.3 Module extraction (NEW — architectural improvement)

Extract the resolver into its own Gradle module
(`:feature:video-resolver`) with a `VideoResolver` interface that
extensions can implement to provide custom parsing/grouping. Default
implementation uses the `VideoTitleParser` regex approach. Extensions
that want richer metadata (e.g., "this video is HDR", "this audio is
5.1 surround") declare it via a capability mechanism (per ADR-022).

```kotlin
interface VideoResolver {
    fun resolve(episode: SEpisode, source: AnimeSource): List<ServerSection>
    fun parseVideo(video: Video): ParsedVideo
    val audioOrder: List<AudioVersion>  // user-overridable
    val supportedHierarchyDepth: Int     // 3 default; extensions can declare 4+
}
```

---

## 10. Unification with the player-side dropdowns (NEW — improvement)

The old project's `ServerVersionDropdowns.kt` (player-side UI for
switching server/audio mid-playback) is a **completely different design**
(two side-by-side dropdowns) than the resolver bottom sheet. The new
project **unifies these**: same data, same component, different surface.

- On the details screen / watch page (pre-playback): bottom-up sheet
  (this doc).
- In the player (mid-playback): compact dropdown variant of the same
  component, surfaced from the player's "more" menu. Same data model,
  same `VideoResolver` interface, same theming (no blue colors).

Compact dropdown variant is TBD in [`player.md`](player.md).

---

## 11. What the owner likes (KEEP — from analysis §3.10)

- **Resolver appears BETWEEN episode tap and player open** — not direct
  play. Gives the user choice over server/audio/quality.
- **3-tier hierarchy: Server → Audio → Quality** — matches how users
  think about streaming sources.
- **Collapsible server sections (accordion)** — only one expanded at a
  time.
- **Audio tag chips on server headers** — at-a-glance summary.
- **Quality chip on the RIGHT** with play icon on the left — clean,
  scannable. No duplicate plain-text label.
- **SUB on the right** of audio tag chips (reversed order) — owner's
  specific preference.
- **No blue colors** — `surfaceVariant` / `secondaryContainer` /
  `outlineVariant` throughout.
- **"Resolving video…" overlay** with clear explanation.
- **Cache-hit instant render + background re-resolve** — instant render
  + "Refreshing…" badge + silent update if unchanged.
- **`compareServerSections` uses `videoTitle + resolution`** (stable)
  instead of `videoUrl` (changing `localhost:PORT`).
- **Single-video auto-play** — if only 1 video, skip picker.
- **Offline-first** — if downloaded, play local file directly.

---

## 12. What to improve (from analysis §3.11 + this doc)

- **`AudioVersion` extensibility** — open class / registry (§9.2).
- **Default server/audio/quality preferences** — remember last per anime.
- **"Best quality" auto-pick preference** — skip picker for power users.
- **Disk video cache** — either read (with proxy-URL refresh) or stop
  writing it (§3).
- **Inline error state** — Retry button inside the sheet (§8).
- **"Open in browser" fallback** — when all servers fail (§8).
- **`Resolving` overlay inside the sheet** — not a full-screen scrim (§8).
- **Unify resolver + player-side dropdowns** — same component, different
  surface (§10).
- **Module extraction** — `:feature:video-resolver` with `VideoResolver`
  interface (§9.3).
- **Localization** — Moko Resources or stock `strings.xml`.

---

## 13. Accessibility

- Each `ServerHeader`, `AudioSubHeader`, and `VideoRow` is a clickable
  surface with `Role.Button` semantics. Content description: server
  name + audio version + quality (for `VideoRow`).
- Accordion expand/collapse announced via semantics state.
- "Resolving" overlay announces "Loading" via a semantics live region.
- "Error" state's Retry and "Open in browser" buttons have explicit
  content descriptions.
- Min tap target 48 dp on all rows.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #2, #3, #11.
- [`../02-components/components.md`](../02-components/components.md) —
  component §1 (this doc is the spec for §1 in the resolver context).
- [`anime-details.md`](anime-details.md) §8 — the entry-point flow.
- [`episode-list.md`](episode-list.md) — the row that triggers this sheet.
- [`watch-page.md`](watch-page.md) — where the user lands after picking.
- [`player.md`](player.md) — fullscreen player; the player-side server/
  audio dropdown should reuse this resolver's data model (§10).
- `DOCS/04-design-decisions.md` — ADR-011, ADR-012, ADR-015, ADR-022.
- `OLD_ANIKUTA/ANALYSIS/details-episodes-resolution-screens.md` §3 —
  source analysis (read-only structural reference).
