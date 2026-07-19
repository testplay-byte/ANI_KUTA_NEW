# 04 — Details Settings Screen (Hub)

> The hub screen for **how the anime-details page looks**. A **live
> preview** at the very top, a **Customize** section linking to three
> subpages (Episode display, Episode layout, Metadata fetching), and a
> separate **Appearance** section for the global dynamic-theming toggle.
>
> The owner called this screen **"perfect. It is beautiful and exactly
> how I expect it to be."** — the live-preview-at-the-top pattern is
> non-negotiable for the new project.
>
> **ADR ref:** ADR-015 (custom M3-inspired design language), ADR-018
> (feature parity + simple mode hides advanced groups).
>
> **Principle refs:** #7 (live preview in appearance-affecting settings),
> #10 (settings divided into sections + simple mode).
>
> **Component refs:** §8 (live preview panel), §9 (subdued
> settings-variant section header). Also reuses the `SettingsGroupCard`,
> `ClickableSettingsRow`, `SwitchSettingsRow`, and
> `SettingsSubpageScaffold` shared scaffolding.
>
> **Status:** STRUCTURE is fixed — the live preview at the top and the
> Customize / Appearance split below are the owner's flagship pattern.
> The known improvement is that the **Watched-episode appearance**
> setting in the Episode display subpage does NOT currently apply to the
> live preview — see §6.

---

## 1. Owner's brief (distilled)

- **Live preview at the very top** of the screen — a bare episode card
  (same padding / structure as the real detail page). The owner: *"That
  is perfect. It is beautiful and exactly how I expect it to be."*
- Below the preview, a **Customize** section with three subpage links:
  - Episode display (which elements show/hide)
  - Episode layout (positions for title, synopsis, date, thumb, etc.)
  - Metadata fetching (which fields to fetch from external sources)
- A **separate "Appearance" section** for the global dynamic-theming
  toggle (color the detail page based on cover image).
- Each subpage ALSO has a sticky live preview at its top — so the user
  sees the effect of any single category of changes.
- The known improvement: the **"Watch episode appearance"** setting
  (None / Grayscale / Blur / Grayscale+Blur) currently does NOT apply to
  the live preview. **Fix this** in the new project.

Reference: `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md`
section 3 (`DetailsSettingsScreen.kt` and its sibling subpages
`DisplaySettingsScreen.kt`, `LayoutSettingsScreen.kt`,
`MetadataSettingsScreen.kt`, plus the shared `EpisodeRowPreview.kt`).

---

## 2. Position in the app

- Reached from: **More → Settings → Details** (the More tab is the fixed
  rightmost bottom-nav tab per ADR-017).
- This is a **subpage** that uses `SettingsSubpageScaffold` (back button
  + title + content slot).
- It is a **hub**: tapping any of the three Customize rows pushes a
  sibling subpage. Backing out of a sibling subpage returns here.

Navigation graph:

```
   settings/details             (this screen — the hub)
   ├── settings/details/display   (Episode display subpage)
   ├── settings/details/layout    (Episode layout subpage)
   └── settings/details/metadata  (Metadata fetching subpage)
```

---

## 3. Layout (ASCII)

```
┌─────────────────────────────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← status bar
│  ← Details                                                  │ ← top bar
│                                                             │
│  LIVE PREVIEW            ← label: primary, labelMedium Bold │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  (bare EpisodeRowPreview, NOT wrapped in a group card)  ││
│  │  ┌───────────────────────────────────────────────────┐  ││
│  │  │ [Thumbnail]   EP 5 · The Dragon's Vow              │  ││
│  │  │              Mar 15, 2024 · SUB · DUB              │  ││
│  │  │              Synopsis text in surface container…   │  ││
│  │  └───────────────────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ┌── SettingsGroupCard "Settings" ──────────────────────────┐│
│  │  📊 Episode display    Show/hide numbers, titles,        ││ ← Customize
│  │                        summaries…                        ││
│  │  ─────────────────────────────────────────────────────  ││
│  │  🎛 Episode layout     Positions for title, synopsis,    ││
│  │                        date, thumb…                      ││
│  │  ─────────────────────────────────────────────────────  ││
│  │  ✨ Metadata fetching  Fetch thumbnails, titles,         ││
│  │                        descriptions…                     ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ┌── SettingsGroupCard "Appearance" ────────────────────────┐│
│  │  🎨 Dynamic theming   Color the detail page based on     ││ ← Appearance
│  │                       the anime's cover image            ││
│  │                                          [Switch ●—]     ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Vertical structure (top → bottom)

1. **Top bar** — `SettingsSubpageScaffold`: back button + `Details`
   title. No actions slot (the screen has no top-bar actions).
   Edge-to-edge per principle #1.
2. **LIVE PREVIEW label + bare `EpisodeRowPreview`** — see §4.
3. **`SettingsGroupCard` "Settings"** — three `ClickableSettingsRow`s
   (Episode display / Episode layout / Metadata fetching). See §5.
4. **`SettingsGroupCard` "Appearance"** — a single `SwitchSettingsRow`
   (Dynamic theming). See §5.

---

## 4. The live preview at the top — KEEP ("perfect, beautiful")

This is principle #7 / component §8 in action. The owner's flagship
example.

### 4.1 The "LIVE PREVIEW" label

- Text: `LIVE PREVIEW`.
- Style: `labelMedium`, `FontWeight.Bold`,
  `MaterialTheme.colorScheme.primary` (accent), `letterSpacing = 1.sp`.
- Padding: `horizontal = 16.dp, vertical = 4.dp`. Left-aligned. No accent
  bar to its left — the label IS the anchor.

### 4.2 The preview itself — bare, not wrapped

The preview composable (`EpisodeRowPreview`) is rendered **WITHOUT** a
`SettingsGroupCard` wrapper. This is deliberate: the preview's padding
and card structure must **exactly match** the real anime-details page so
the user sees a true representation, not a "settings-styled" version.

- Padding: `horizontal = 16.dp` (matches detail page content padding).
- Background: the screen's default surface (NOT `surfaceVariant` — the
  §8 default is overridden here to match the real detail page exactly).
- Non-interactive: taps inside the preview are ignored.
- Updates **immediately** on every setting change — same-frame
  recomposition via `StateFlow`.

### 4.3 The preview is reactive — never `.get()`

Every preview-affecting preference is collected reactively:

```kotlin
val showThumbnails by prefs.showThumbnails().stateIn(scope).collectAsState()
val showTitles     by prefs.showTitles().stateIn(scope).collectAsState()
// … etc, for every preview-affecting pref
```

**Hard rule:** never use `prefs.X().get()` to read a preview-affecting
pref — the old project's `downloadButtonPlacement` bug (the toggle only
"applied" after navigating away and back) was caused by exactly this.
Always `stateIn(scope).collectAsState()`.

### 4.4 What the preview shows

A single demo episode card with all the layout / display settings
applied: thumbnail (size + side per Layout), episode number (position +
show/hide), title (position + show/hide), date + audio pills (position +
show/hide), synopsis (position + show/hide), download button (placement
per Layout).

> The preview must ALSO reflect the **Watched-episode appearance**
> setting. Currently it does not — see §6.

---

## 5. The "Customize" + "Appearance" sections

### 5.1 Customize — `SettingsGroupCard` titled "Settings"

Three `ClickableSettingsRow`s, separated by `HorizontalDivider()`. Each
row has a leading icon, a title, a subtitle, and navigates on tap:

| Row | Icon | Subtitle (short) | Navigates to |
|---|---|---|---|
| Episode display | `ViewAgenda` | Show/hide numbers, titles, summaries, thumbnails, dates, audio pills | `DisplaySettingsScreen` |
| Episode layout | `Tune` | Positions for title, synopsis, date, episode number, thumbnail, anime info | `LayoutSettingsScreen` |
| Metadata fetching | `AutoAwesome` | Fetch thumbnails, titles, descriptions from external sources | `MetadataSettingsScreen` |

`ClickableSettingsRow` shares the layout of `SwitchSettingsRow`
(leading icon + title + subtitle) but uses a press-scale animation
(`animateFloatAsState(0.98f)` via `AnikutaSprings.press`) instead of a
trailing control. Tap navigates.

### 5.2 Appearance — separate `SettingsGroupCard` titled "Appearance"

A single `SwitchSettingsRow`: icon `Palette`, title "Dynamic theming",
subtitle "Color the detail page based on the anime's cover image",
trailing `Switch`.

**Why a separate card.** Theming is a global appearance concern, NOT a
per-element customization. Mixing it into the "Settings" card would
blur the line between "configure what's on the page" (Customize) and
"configure how the page is colored" (Appearance).

> Per principle #8, the dynamic-theming toggle uses a `Switch` (a true
> on/off setting with no intermediate state) — NOT a §3 2-way toggle.
> 2-way toggles are reserved for labeled binary choices where both
> option names are meaningful ("List / Grid", "Right / Below").

---

## 6. The known improvement — Watched-episode appearance must apply to the preview

### 6.1 The problem

`DisplaySettingsScreen` lets the user pick a visual treatment for
watched episodes — `none` / `grayscale` / `blur` / `both` — plus
`watchedBlurRadius` and `watchedAlpha` slider sub-settings. The pref is
exposed as `watchedAppearance`. **However, `EpisodeRowPreview` does NOT
accept a `watchedAppearance` parameter** — grep on the old
`EpisodeRowPreview.kt` for `watched` returns zero matches. Changes to
this setting do not reflect in the live preview.

### 6.2 The fix (for the new project)

Add three parameters to `EpisodeRowPreview`:

```kotlin
fun EpisodeRowPreview(
    // … existing params …
    watchedAppearance: String = "none",      // "none"|"grayscale"|"blur"|"both"
    watchedBlurRadius: Float = 2f,            // dp
    watchedAlpha: Float = 1f,                 // 0f..1f
    demoWatched: Boolean = true,              // render the demo card as watched
)
```

…and apply the effect per principle #5 / component §6 (`ColorMatrix`
grayscale + `BlurEffect`).

### 6.3 The principle this reinforces

> Any setting that affects on-screen appearance must reflect in the live
> preview (principle #7). The old project's omission is the cautionary
> tale — every new appearance-affecting setting must be wired into the
> preview at implementation time, not "later."

---

## 7. The sibling subpages (Customize targets)

All three subpages share the same skeleton — sticky live preview at the
top, scrollable settings below:

```
SettingsSubpageScaffold(title, onBack) {
    Column {
        Text("LIVE PREVIEW", …)         // sticky, non-scrolling
        EpisodeRowPreview(...)
        LazyColumn { …toggles… }        // scrollable settings below
    }
}
```

- **Episode display** — `SwitchSettingsRow` per element (numbers, titles,
  summaries, thumbnails, dates, audio pills, download button) + the
  4-way `SelectableOptionCard` for Watched-episode appearance with
  conditional slider sub-settings. See `episode-layout-settings.md` §6.
- **Episode layout** — 2-way / 3-way `StyledSegmentedRow` toggles for
  positions. Full spec in `episode-layout-settings.md`.
- **Metadata fetching** — master toggle + per-field sub-toggles
  (Thumbnails / Titles / Summaries) revealed via
  `AnimatedVisibility(expandVertically)`. A `surfaceContainerLow` info
  card explains: "Metadata is fetched when you open an anime's detail
  page. Only fields missing from the extension are enriched."

> The Metadata subpage's preview **forces all display toggles ON** so
  the preview always shows the full set of elements regardless of the
  user's Display prefs — the user is configuring *fetching*, not
  *display*.

---

## 8. Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| **Live preview at the VERY TOP** | ✅ keep ("perfect, beautiful, exactly how I expect") | Non-negotiable |
| Live preview is a BARE episode card (not wrapped in a settings group) | ✅ keep | Padding/structure matches real detail page |
| `LIVE PREVIEW` label (primary, labelMedium, Bold, letterSpacing 1sp) | ✅ keep | Visual anchor above the preview |
| "Customize" section below the preview (three subpage links) | ✅ keep | One `SettingsGroupCard` titled "Settings" |
| Separate "Appearance" section for global theming | ✅ keep | Theming ≠ element customization |
| Each subpage also has a sticky live preview at its top | ✅ keep | Lets the user see effect of any single category of changes |
| `MetadataSettingsScreen` forces all display toggles ON in its preview | ✅ keep | Preview always shows full element set |
| **Watched-episode appearance doesn't apply to the live preview** | ⚠️ improve | Add `watchedAppearance` param to `EpisodeRowPreview` — see §6 |
| Always use `stateIn(scope).collectAsState()` for preview-affecting prefs | ⚠️ note | Never `.get()` — the `downloadButtonPlacement` bug is the cautionary tale |

---

## 9. Simple mode interaction (ADR-018)

Each `SettingsGroupCard` and each setting row carries a
`simpleModeVisible: Boolean` flag (ADR-018):

- **Live preview:** always visible (simple mode shows it too — the
  owner's flagship pattern).
- **Episode display:** visible; the "Watched-episode appearance" 4-way
  card and slider sub-settings are hidden (advanced).
- **Episode layout:** hidden (advanced — positions are power-user
  territory).
- **Metadata fetching:** visible (basic users benefit from enrichment).
- **Dynamic theming:** visible (a delightful default-on toggle).

When a `SettingsGroupCard` has all children hidden by simple mode, the
entire card is hidden (no empty cards).

---

## 10. What's open for the design session

- Whether the live preview should also reflect the dynamic-theming toggle
  (show the cover-color-derived palette when on) — needs a cover-image
  proxy for the demo card.
- Whether the Metadata subpage's "force all display toggles ON" preview
  behavior should be signaled to the user (a small caption) or kept
  invisible.
- The exact cover-image proxy used for the dynamic-theming preview
  (hardcoded demo cover? a small set the user can pick from?).

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #7, #10.
- [`../02-components/components.md`](../02-components/components.md) — §8
  (live preview panel), §9 (subdued settings-variant section header).
- [`episode-layout-settings.md`](episode-layout-settings.md) — the
  Episode layout subpage spec, including the 2-way / 3-way toggle
  visual rules.
- [`bottom-nav.md`](bottom-nav.md) — the floating nav that surfaces the
  "More" tab leading here.
- `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md` §3 —
  source analysis of the old project's `DetailsSettingsScreen.kt` and
  its sibling subpages.
- `DOCS/04-design-decisions.md` — ADR-015, ADR-018.
