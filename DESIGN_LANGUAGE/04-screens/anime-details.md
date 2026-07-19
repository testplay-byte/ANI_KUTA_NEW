# 04 — Anime Details Screen

> The page the user lands on after tapping an anime cover anywhere in the
> app (Home / Library / Browse / Search). The canonical example of
> ANIKUTA's hero-header pattern (blurred cover + gradient + dynamic
> theming) and the **entry point to the watch page** via the episode list.
>
> **ADR refs:** ADR-015 (custom M3-inspired design language), ADR-011
> (dual-source metadata resolver — AniList + extension), ADR-012 (watch
> page reachable from episode tap).
>
> **Principles applied:** #1, #4, #6, #11.
>
> **Components used:** §7 (blurred cover header), §6 (episode row),
> §9 (section header).
>
> **Status:** STRUCTURE, BEHAVIOR, and the header's visual recipe are fixed
> (this doc). Token refinements tuned in `03-themes/`.

---

## 1. Position in the navigation flow

```
   Cover tap (Home / Library / Browse / MY / Search)
            │
            ▼
   Anime Details page  ──(tap episode row)──►  Video Resolver sheet
        ▲                                              │
        │                                              │ (pick server/audio/quality)
        │                                              ▼
        │                                         Watch Page (ADR-012)
        │                                              │
        │                                              │ (maximize)
        │                                              ▼
        │                                         Fullscreen Player
        │                                              │
        └────────────── back ◄─────────────────────────┘
```

- The details screen is a **pushed** Voyager screen, not a sheet.
- Deep-link-friendly route (`/anime/{anilistId}`) so notifications and
  search results can open it directly.
- Backing out returns to whichever screen the user came from.

---

## 2. Owner's vision

> "Blurred cover at the top with gradient darkening. The page is quite
> beautiful. AniList metadata fetching is on the same screen."

The owner flagged this screen as the **flagship "beautiful" screen**. The
header (blurred cover + gradient + dynamic color from the cover) is the
single most important visual element. The episode list below is the
**second** flagship element (see [`episode-list.md`](episode-list.md)).

---

## 3. Layout (top → bottom)

```
┌──────────────────────────────────────────────────────────────┐
│  ◀ Back   🔖 Save   ↗ Share   ⋮ More          (over banner)  │ ← edge-to-edge
│                                                                │   status bar overlays
│              [BLURRED COVER IMAGE, ~8dp blur]                  │   the header
│              [theme-color tint, 20% alpha]                     │
│              [vertical gradient overlay:                       │
│               black 20% → transparent → background]            │
│                                                                │
│   ┌──────────┐   Title (titleLarge, bold, max 3 lines)        │
│   │  cover   │   ★ score · status · N eps                     │
│   │ thumbnail│   [Ep 1016 in 2d 5h]   ← NextEpisodePill       │
│   └──────────┘   100×150dp, 12dp rounded                       │
├──────────────────────────────────────────────────────────────┤  ← solid background
│  [Genre] [Genre] [Genre] …            (LazyRow, AssistChip)   │
├──────────────────────────────────────────────────────────────┤
│  Synopsis                                                     │ ← §9 header
│  <description, 3-line collapsed / Show more>                  │
├──────────────────────────────────────────────────────────────┤
│  Episodes  [⟳ Fetching metadata…]   N episodes   [Source]     │ ← §9 header
│  ┌────────────────────────────────────────────────────────┐  │
│  │ EpisodeRow #1 (surfaceContainerLow bg)                  │  │ ← §6 row
│  │ EpisodeRow #2 (surfaceContainerHigh bg)                 │  │
│  │ EpisodeRow #3 (surfaceContainerLow bg)                  │  │
│  │ …                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│  Information                                                  │ ← §9 header
│   Format     TV                                               │
│   Status     RELEASING                                        │
│   Season     WINTER 2025                                      │
│   Episodes   12                                               │
│   Score      78 / 100                                         │
└──────────────────────────────────────────────────────────────┘
```

### 3.1 Header height & safe areas

- The header is **edge-to-edge** (principle #1). The status bar
  overlays it — **no `statusBarsPadding()` on the header background**.
  Header content (back/share/more buttons, cover thumbnail, title)
  **does** apply `statusBarsPadding()` — only the background extends
  under the bar.
- Banner height: **~360 dp on phones**. On tablets/foldables (width ≥
  600 dp), cap the header at a max-width container (~600 dp centered)
  so the thumbnail + title don't get lost in a wide banner.
- The bottom of the banner has **no hard edge** — the gradient fades
  from transparent → `colorScheme.background`, so the banner visually
  melts into the scrollable content below (component §7).

---

## 4. The "quite beautiful" header — component §7

The header is a direct instantiation of **component §7 — blurred cover
header**. The recipe (carried verbatim from the old project's owner-loved
parameters):

| Effect | Value | Why |
|---|---|---|
| Cover image | `coverImage.extraLarge ?: best()` | High-res for the blur not to pixelate. |
| Blur radius | `~8.dp` (subtle) | "Just enough to make text readable." Higher destroys cover art; lower reduces text contrast. (Old project's exact value — KEEP.) |
| Cover scale | `1.2f` | Avoids hard blur edges at the banner's borders. |
| Cover-color tint | `coverColor.copy(alpha = 0.2f)` overlay | Adds cohesion with the dynamic theme without overpowering the cover. |
| Vertical gradient | `[Black 20%, Transparent, Background]` | Top fade for button legibility; bottom fade blends banner into the page. |
| Black scrim | `0.3f` over the blur | Text contrast (component §7 default). |

### 4.1 Foreground content (overlaid on the banner)

Aligned to the **bottom-start** of the banner, overlapping the gradient:

- **Cover thumbnail** — `100 × 150 dp` `AsyncImage`, `clip(RoundedCornerShape(12.dp))`. Sharp (NOT blurred — only the background is blurred).
- **Title** — `title.preferred()` (`english ?: romaji ?: native`), `titleLarge`, bold, `maxLines = 3`, `onSurface`.
- **Metadata pill row** (`bodySmall`): `★ {averageScore}` · `{status}` · `{episodes} eps`.
- **NextEpisodePill** — tappable countdown pill. Toggles between static text ("Ep 1016 in 2d 5h") and a live `HH:MM:SS` countdown. Hidden if no `nextAiringEpisode`.

### 4.2 Top-row overlay buttons

A `Row` aligned to the top-start of the banner, **status-bar-padded**:

- Back (←) — 36 dp circular, `secondaryContainer` bg.
- Save / bookmark (🔖) — toggle, filled when in Library.
- Share (↗) — share anime deep-link.
- More (⋮) — overflow: tracker status, mark all unwatched, open in AniList.

Buttons use `IconButton` with a circular `Surface` background so they're
visible against any cover. NOT a flat `TopAppBar` — the old project's
floating overlay buttons are the model (KEEP).

---

## 5. Dynamic theming — cover color → ColorScheme

The entire details screen is wrapped in `MaterialTheme(colorScheme =
coverColorScheme)` so every child inherits the cover-derived palette
(recipe in `03-themes/themes-and-colors.md` §6). Algorithm (ported from
old project's `DynamicTheming.kt`):

1. AniList provides `coverImage.color` as a hex string (e.g. `"#FF5722"`). Convert to HSL.
2. Derive: `accent` = same hue, full sat, cover L. `surfaceLow` = hue, `s×0.25`, `L=0.12` (even-index episode cards). `surfaceHigh` = hue, `s×0.30`, `L=0.18` (odd-index). `surfaceContainer` = hue, `s×0.35`, `L=0.24`. `background` = hue, `s×0.20`, `L=0.08`.
3. Map to a full `MaterialTheme.colorScheme.*` via `toM3ColorScheme()`.

### 5.1 Light-theme support (improvement)

The old project hard-coded `onSurface = White.copy(alpha = 0.92f)`. The
new project picks light/dark surfaces based on the cover color's
luminance — bright cover → light scheme; dark cover → dark scheme. Lets
the screen work in Light / Dark / System mode (principle #8).

### 5.2 Critical Compose perf rule (do not break)

`generateDynamicScheme(coverColor)` AND `toM3ColorScheme()` MUST be
wrapped in a single `remember(coverColor, dynamicThemingEnabled,
defaultScheme) { ... }` block. Calling `toM3ColorScheme()` on every
recomposition creates a new `ColorScheme` object each frame → forces ALL
children to recompose on every scroll frame → massive jank. (Old
project: `DetailScreen.kt:255-259`.)

### 5.3 Toggle

Dynamic theming is **on by default** and user-toggleable in details
settings. When off, the page uses the user's selected app theme.

---

## 6. Metadata — the dual-source resolver (ADR-011)

Per ADR-011, anime metadata is fetched via a `MetadataResolver` that
prefers **AniList** and falls back to **extension** data when AniList is
incomplete. The old project only did this for episode fields; the new
project extends it to anime-level fields too (cover, description, status,
etc.).

### 6.1 Three-stage load (port from old project)

| Stage | Source | What it provides | TTL |
|---|---|---|---|
| 1 | AniList (3-tier cache: Local → Supabase → API) | Title, cover, description, score, genres, season, format, status, nextAiring, streamingEpisodes | 24h (local) / 30min (Supabase) |
| 2 | Extension (`AniyomiSourceBridge.findMatch`) | Source match for episode list (NotAired / NoMatch / SingleMatch / MultipleMatches) | Persistent (`ext_match_$id`) |
| 3 | Episode metadata enrichment (Anikage.cc / AniList streaming / Jikan / Kitsu — parallel) | Episode titles, thumbnails, descriptions, air dates | Per-episode |

### 6.2 Loading UX (the "pill-in-header" pattern)

Standardize on the **pill-in-header** loading pattern for all background
operations on this screen (per analysis §4.2):

| State | UX |
|---|---|
| **Initial page load** (`DetailState.Loading`) | **Skeleton** — placeholder banner with shimmer blur, placeholder thumbnail block, 3-line shimmer title. NEVER a bare `CircularProgressIndicator` (old project's jarring flash — REPLACE). |
| **Searching extensions** (`EpisodeState.Searching`) | Small inline `CircularProgressIndicator(16dp) + "Searching extensions…"` pill beside the Episodes header. |
| **Enriching episode metadata** (`isEnrichingMetadata`) | Small `CircularProgressIndicator(12dp) + "Fetching metadata…"` pill in `surfaceVariant` beside the Episodes header. **Best pattern — use for all background ops.** |
| **Source not yet matched** | Banner-style "Matching source…" pill at the top of the episode list section, dismissible. |

### 6.3 Pull-to-refresh (3-stage)

Custom `NestedScrollConnection` maps pull distance to 3 actions (old
project's novel `ThreeStagePullRefresh`):

| Stage | Pull | Action |
|---|---|---|
| 1 | 100 dp | Refresh episodes only. |
| 2 | 200 dp | Refresh details only (bypass cache). |
| 3 | 300 dp | Refresh everything (clear caches + re-match source). |

- Damping factor `0.5×`. 5-minute guard per anime prevents hammering.
- Floating indicator: `Surface(RoundedCornerShape(16.dp), surfaceVariant 95% alpha)` at `TopCenter`, `padding(top = 56.dp)`.
- Pull-distance state MUST use `rememberSaveable` (improvement over old project).

---

## 7. Sections below the header

### 7.1 Genres (LazyRow)

Horizontal scroll of `AssistChip`s, one per genre. 8 dp gap. Tappable
(drill into Browse with that genre filter — TBD).

### 7.2 Synopsis

- Section header: **Synopsis** (§9 — accent, left-aligned).
- Body: HTML-stripped description (`cleanHtmlTags()`), `bodyMedium`,
  `onSurfaceVariant`. Collapsed by default (3 lines, `Ellipsis`).
  "Show more" / "Show less" toggles.

### 7.3 Episodes (links to [`episode-list.md`](episode-list.md))

- Section header: **Episodes** (§9) + enrichment indicator pill (§6.2) +
  episode count + current source name (`[Source: AniKoto]`, tappable →
  source-picker sheet — TBD).
- Below: the episode list as a column of `EpisodeRow` (component §6).
- **Two layout modes** (toggleable in details settings, per principle #8):
  - **"above"** (power user default) — episodes render as direct
    `LazyColumn` items; whole page scrolls as one long list.
  - **"below"** (casual default) — episodes live in an inner `LazyColumn`
    capped at `heightIn(max = 600.dp)` so the Information section below
    stays reachable.
- Alternating card backgrounds: even-index `surfaceContainerLow`,
  odd-index `surfaceContainerHigh` — subtle zebra-stripe rhythm (KEEP).

### 7.4 Information

- Section header: **Information** (§9).
- Two-column key/value list: Format / Status / Season / Episodes / Score
  / Studio / Source / Origin / External links.
- Position is configurable (above or below the Episodes section) per
  `animeInfoPosition` preference.

---

## 8. Entry point to the watch page (ADR-012)

Tapping an episode row does **not** directly open the player. Flow (KEEP
from old project):

```
   tap EpisodeRow
        │
        ▼
   (1) Episode downloaded on disk? ──YES──► Watch Page (offline, skip resolver)
        │ NO
        ▼
   (2) Show Video Resolver sheet  ◄── see video-resolver.md
        │
        ├─ single video?  ──YES──► auto-play → Watch Page
        └─ multiple?      ──YES──► user picks server/audio/quality → Watch Page
```

- The resolver sheet is a **bottom-up menu** (component §1) — no drag
  handle, partial height. Full spec: [`video-resolver.md`](video-resolver.md).
- After picking (or auto-play), the user lands on the **watch page**, not
  fullscreen. Maximize on the watch page goes fullscreen.

---

## 9. What the owner likes (KEEP — from analysis §1.7)

- **8 dp blurred cover banner** — the centerpiece "quite beautiful" effect.
- **Vertical gradient (black 20% → transparent → background)** — no hard edge.
- **Cover-color tint at 20% alpha** — cohesion without overpowering.
- **Dynamic theming from AniList `coverImage.color`** — fast, reliable.
- **Alternating episode card backgrounds** — subtle zebra rhythm.
- **Two info-position modes** (above/below episodes) — power vs casual.
- **"Fetching metadata…" enrichment indicator** — page feels alive.
- **3-stage pull-to-refresh** — explicit control over refresh scope.
- **Long-press episode sheet** with state-aware options.

---

## 10. What to improve (from analysis §1.8 + this doc)

- **Skeleton loader for initial load** — replace the `CircularProgressIndicator`
  flash with a shimmer-skeleton matching the banner + thumbnail + title block.
- **Light-theme support for dynamic theming** — pick surfaces by cover luminance.
- **`rememberSaveable` for pull-to-refresh distance** — survives navigation.
- **Tablet/foldable variant of the header** — max-width container for wide screens.
- **Localization** — Moko Resources or stock `strings.xml`.
- **`MetadataResolver` extended to anime-level fields** (cover, description,
  status) — not just episode fields. Per ADR-011.
- **Filler badge + downloaded checkmark on episode rows** — see
  [`episode-list.md`](episode-list.md) §11.

---

## 11. Compose gotchas (port from analysis §4.3)

Real bugs in the old code the new project must NOT reintroduce:

1. **Remember `ColorScheme` generation** — wrap in `remember(coverColor,
   ...) { ... }` (§5.2). Per-recomposition calls force all children to
   recompose on every scroll frame.
2. **Create new `SEpisode` objects when enriching metadata** — `LazyColumn`
   skips recomposition for same object references; mutation in place
   doesn't trigger UI updates.
3. **Extract bottom sheets into dedicated composables** that receive
   state as a parameter — inline `?.let { ModalBottomSheet(...) }` doesn't
   reliably recompose in long, complex functions.
4. **Use `stateIn(scope).collectAsState()` for preferences** — `remember
   { .get() }` captures once and never updates.
5. **Re-read watched state on `ON_RESUME`** — the `changes` Flow alone
   isn't enough when returning from the player; use a `LifecycleEventObserver`.

---

## 12. Accessibility

- All banner overlay buttons have content descriptions; cover thumbnail
  has `contentDescription = null` (decorative — title is announced
  separately).
- Episode rows are `Role.Button` surfaces; content description includes
  episode number + title.
- 3-stage pull-to-refresh announces the current stage via semantics live
  region.
- Min tap target 48 dp on all overlay buttons.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #1, #4, #6, #11.
- [`../02-components/components.md`](../02-components/components.md) —
  components §6 (episode row), §7 (blurred cover header), §9 (section
  header).
- [`episode-list.md`](episode-list.md) — the episode list rendered below
  the header.
- [`video-resolver.md`](video-resolver.md) — the resolver sheet opened
  on episode tap.
- [`watch-page.md`](watch-page.md) — where the user lands after picking
  a video.
- [`../03-themes/themes-and-colors.md`](../03-themes/themes-and-colors.md)
  §6 — cover-color theming recipe.
- `DOCS/04-design-decisions.md` — ADR-011 (dual-source metadata
  resolver), ADR-012 (watch page), ADR-015 (custom design language).
- `OLD_ANIKUTA/ANALYSIS/details-episodes-resolution-screens.md` §1 —
  source analysis (read-only structural reference).
