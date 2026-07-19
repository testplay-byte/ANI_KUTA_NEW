# 04 — Extensions Settings Screen

> The user's management surface for anime/manga extensions: install, trust,
> untrust, delete, reorder trusted sources, search, filter, and refresh.
> Three stacked categories — **Sources (trusted) → Installed → Available**
> — with an **anime/manga toggle at the very top**.
>
> **ADR ref:** ADR-015 (custom M3-inspired design language), ADR-016
> (extension categories: Video / Image-Manga — no series/movies split),
> ADR-018 (feature parity + simple mode).
>
> **Principle refs:** #1 (edge-to-edge top bar), #6 (accent-color
> left-aligned section headers via §9 in the subdued settings variant),
> #8 (multi-way toggles via §3 — the anime/manga toggle is a 2-way
> toggle), #10 (settings divided into sections; simple mode hides
> advanced).
>
> **Component refs:** §3 (2-way toggle for anime/manga), §9 (subdued
> section-header variant for the three category cards), and the
> `SettingsGroupCard` + `SettingsSubpageScaffold` shared scaffolding.
>
> **Status:** STRUCTURE is fixed — the 3-category separation is the
> owner's flagship preference for this screen ("quite good"). The
> anime/manga toggle at the top is a **new requirement** (ADR-016) — the
> old project is anime-only and does not implement it.

---

## 1. Owner's brief (distilled)

- A **2-way anime/manga toggle at the very top** to switch which set of
  extensions is shown (ADR-016: Video / Image-Manga).
- **THREE separate categories**, in this exact order:
  1. **Trusted / saved sources** at the VERY TOP (max 2; drag-reorderable;
     the user's "primary + secondary" sources).
  2. **Installed extensions** in the MIDDLE (installed locally but not
     trusted; trust / delete actions).
  3. **All available extensions** at the VERY BOTTOM (fetched from repos;
     install action).
- The 3-category separation is a **key design-language preference** — keep
  it verbatim.
- The compact circular install button (no blue badge) — keep.
- The squircle extension icons — keep.
- Search slides down BELOW the top-bar actions — keep.
- Pull-to-refresh — keep.
- The max-2-trusted limit + revoke-to-add popup — keep.

Reference: `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md`
section 2 (`ExtensionsSettingsScreen.kt` + `ExtensionsViewModel.kt`).

---

## 2. Position in the app

- Reached from: **More → Extensions** (the More tab is the fixed
  rightmost bottom-nav tab per ADR-017), or directly from the Browse
  screen's overflow.
- This is a **subpage**, not a top-level tab. It uses the shared
  `SettingsSubpageScaffold` (back button + title + actions slot).
- Backing out returns to wherever the user came from (More or Browse).

---

## 3. Layout (ASCII)

```
┌─────────────────────────────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← status bar
│  ← Extensions                          [🔍] [⚙ Tune] [🌐]    │ ← top bar
│                                                             │
│  ┌─ Anime / Manga toggle (§3 2-way) ─────────────────────┐  │
│  │         ▓▓▓ Anime ▓▓▓         │      Manga             │  │
│  └────────────────────────────────┴────────────────────────┘  │ ← ADR-016
│                                                             │
│  ┌─ animated search field (visible only when search on) ─┐  │
│  │ 🔍  Search extensions…                            ✕    │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                             │
│  PullToRefreshBox                                           │
│  ┌── SettingsGroupCard #1 ────────────────────────────────┐  │
│  │  ▌ SOURCES · 1/2                                       │  │ ← §9 subdued
│  │     (max 2 trusted, drag-and-drop reorder)             │  │
│  │  ⋮  [icon] Crunchyroll      v1.4 · 1 source            │  │
│  │             [Untrust ✓]                                │  │
│  │  ─────────────────────────────────────────────────    │  │
│  │  ⋮  [icon] HiAnime          v2.1 · 1 source            │  │
│  │             [Untrust ✓]                                │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌── SettingsGroupCard #2 ────────────────────────────────┐  │
│  │  ▌ INSTALLED · 3                                       │  │ ← §9 subdued
│  │  [icon] AnimePahe        v1.2 · EN                     │  │
│  │         [Trust ✓]  [Delete 🗑]                          │  │
│  │  ─────────────────────────────────────────────────    │  │
│  │  [icon] Gogoanime        v1.5 · EN                     │  │
│  │         [Trust ✓]  [Delete 🗑]                          │  │
│  │  ─────────────────────────────────────────────────    │  │
│  │  [icon] Zoro             v1.0 · EN                     │  │
│  │         [Trust ✓]  [Delete 🗑]                          │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌── SettingsGroupCard #3 ────────────────────────────────┐  │
│  │  ▌ AVAILABLE · 47                                      │  │ ← §9 subdued
│  │  [icon] Aniwatch         v1.1 · EN                     │  │
│  │         [Circular ⬇ install button]                    │  │
│  │  ─────────────────────────────────────────────────    │  │
│  │  [icon] 9anime           v1.3 · EN                     │  │
│  │         [Circular ⬇ install button]                    │  │
│  │  …                                                     │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Vertical structure (top → bottom)

1. **Top bar** — `SettingsSubpageScaffold`: back button + `Extensions`
   title + actions slot (🔍 search, ⚙ Tune filter, 🌐 manage repos).
   Edge-to-edge per principle #1.
2. **Anime/Manga toggle** — a §3 2-way toggle, full-width, sticky below
   the top bar (does NOT scroll away). See §4.
3. **Animated search field** — only visible when search is active;
   slides down via `AnimatedVisibility`. See §5.
4. **Pull-to-refresh wrapper** — wraps the three `SettingsGroupCard`s
   below.
5. **Three `SettingsGroupCard`s** — Sources / Installed / Available, in
   that order, separated by 12dp vertical spacing. See §6.

---

## 4. The anime/manga toggle (NEW — ADR-016)

The old project is anime-only, so this toggle is **not yet implemented**
in the old codebase. It is a **forward-looking requirement for the new
project**. Per ADR-016, extensions are categorized as **Video** (anime)
or **Image/Manga**, and the user switches which set the screen shows via
this toggle.

- **Component:** §3 (2-way toggle). NOT a `Switch`, NOT a `FilterChip`
  pair — a labeled 2-way segmented toggle so both states are visible at
  once.
- **Options:** `Anime` / `Manga`. (Labels are the user-facing terms; the
  underlying ADR-016 categories are Video / Image-Manga — the toggle's
  two segments map 1:1.)
- **Position:** directly below the top bar, full-width, with 16dp
  horizontal padding. **Sticky** — does NOT scroll away with the list.
- **Default:** `Anime` (the app is anime-first per ADR-009).
- **Behavior:** switching the toggle swaps the contents of all three
  category cards (Sources / Installed / Available) to the selected
  category's extensions. Trusted sources are tracked separately per
  category — the user can have up to 2 trusted anime sources AND up to
  2 trusted manga sources.
- **Persistence:** the selected category is persisted so reopening the
  screen restores the user's last view.

```
┌─────────────────────────────────────────────┐
│  ▓▓▓▓ Anime ▓▓▓▓ │      Manga                │  ← active = Anime
└─────────────────────────────────────────────┘
        ↑                          ↑
   filled accent             transparent + onSurfaceVariant
```

---

## 5. Top-bar actions + search field

### 5.1 Actions slot

Three `IconButton`s in the top bar's actions slot:

| Icon | Action |
|---|---|
| `Search` (or `Close` when search active) | Toggle the search field |
| `Tune` | Open the filter bottom sheet (sort + language) |
| `Public` (globe) | Navigate to Manage Repositories |

### 5.2 Search field — slides down BELOW the actions

The search field is NOT in the top bar itself — it appears below the
top bar with an `AnimatedVisibility` slide-down. This is deliberate:
keeping search out of the top bar prevents horizontal overflow when
three icons + a title + a back button are all on one row.

```
   Top bar:   ← Extensions        [🔍] [⚙] [🌐]
                ↓ AnimatedVisibility(slide down)
   Search:    🔍  Search extensions…                    ✕
```

- Filter applies to all three category cards simultaneously.
- A search with zero matches shows a "No extensions match '<query>'"
  empty state in each card.
- Closing the search restores the unfiltered view.

### 5.3 Filter bottom sheet

Triggered from the `Tune` icon. Uses §1 (bottom-up menu, no drag handle,
partial height). Two sections inside:

1. **Sort by** — 2-way segmented pills: `Name A–Z` / `Name Z–A`.
2. **Languages** — multi-select chips in a 3-wide flow row. Selected
   chips use `secondaryContainer`; unselected use `surfaceVariant` at
   50% alpha.

---

## 6. The 3-category separation (the owner's flagship preference)

The owner explicitly called the 3-section separation **"quite good"** and
a **key design-language reference**. This is the single most important
pattern on the screen — preserve it verbatim.

### 6.1 Card 1 — Sources (TRUSTED, max 2, reorderable)

- **Header:** `SOURCES · N/2` (§9 subdued settings variant —
  `onSurfaceVariant` text + accent bar).
- **Contents:** a `sh.calvin.reorderable` drag-and-drop `LazyColumn`
  (heightIn max = `count × 72dp`, `userScrollEnabled = false`) of
  `SourceExtensionRow`s.
- **Empty state copy:** "No trusted sources. Install an extension, then
  tap Trust to add it here."
- **Max-2 limit.** Trusting a 3rd source triggers
  `MaxTrustedSourcesDialog` (`AlertDialog`) showing the two currently
  trusted sources with logos and letting the user revoke one to make
  room. The ViewModel auto-trusts the pending extension after a 500ms
  delay so the revoke processes first.
- **Row (`SourceExtensionRow`):** drag handle (⋮⋮, only when count > 1)
  + squircle icon + name + `v1.4 · 1 source` meta + trailing `Untrust`
  (`VerifiedUser`, `primary` tint).

### 6.2 Card 2 — Installed (MIDDLE, untrusted but local)

- **Header:** `INSTALLED · N`.
- **Contents:** `forEachIndexed` loop of `UntrustedExtensionRow`s
  separated by `HorizontalDivider(outlineVariant)`.
- **Empty state copy:** "No extensions installed. Browse Available
  below to install one."
- **Row (`UntrustedExtensionRow`):** squircle icon + name + `v1.2 · EN`
  meta + trailing `Trust` (`VerifiedUser`, `onSurfaceVariant`) +
  `Delete` (`Delete`, `error` tint).

### 6.3 Card 3 — Available (VERY BOTTOM, from repos)

- **Header:** `AVAILABLE · N`.
- **Contents:** list of `AvailableExtensionRow`s — the longest list,
  usually dozens. Loading state = spinner in card body; on error =
  retry button.
- **Empty state copy:** "No extensions available. Add a repository to
  see extensions."
- **Row (`AvailableExtensionRow`):** squircle icon + name + `v1.1 · EN`
  meta + trailing one of:
  - `Check` icon if already installed (disabled, `onSurfaceVariant`).
  - `CircularProgressIndicator` if currently downloading.
  - **Compact circular ⬇ install button** otherwise — `Surface` with
    `RoundedCornerShape(50)` + `Download` icon padded 8dp. Intentionally
    compact ("no blue badge") per a documented design decision.

---

## 7. Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| **3-category separation: Sources / Installed / Available** | ✅ keep ("quite good") | The single most important pattern to preserve |
| Trusted sources at the VERY TOP | ✅ keep | Above installed, above available |
| Installed in the MIDDLE | ✅ keep | Between trusted and available |
| Available at the VERY BOTTOM | ✅ keep | Longest list, lowest priority |
| **Anime/manga toggle at the top** | ⚠️ add (per ADR-016) | New requirement — old project is anime-only |
| Drag-and-drop reorder of trusted sources | ✅ keep | `sh.calvin.reorderable` |
| Max-2-trusted limit + revoke-to-add popup | ✅ keep | Enforces primary/secondary mental model |
| Compact circular install button (no blue badge) | ✅ keep | Cleaner than full-width text buttons |
| Squircle extension icons (`RoundedCornerShape(percent = 28)`) | ✅ keep | |
| Search bar slides down BELOW the top-bar actions | ✅ keep | Prevents horizontal overflow |
| Filter bottom sheet with segmented pills (sort + language chips) | ✅ keep | §1 bottom-up menu, no drag handle |
| Pull-to-refresh | ✅ keep | Auto-refreshes via BroadcastReceiver on package install/uninstall too |
| Per-section empty-state copy | ✅ keep | "No trusted sources. Install an extension, then tap Trust…" |
| `HorizontalDivider(outlineVariant)` between rows | ✅ keep | Subtle separator |
| Per-category max-2-trusted limit (independent for anime vs manga) | ⚠️ new implication | Trusted sources tracked separately per category — verify with owner |

---

## 8. Data flow (ViewModel)

Three independent `StateFlow`s — one per category. Switching the
anime/manga toggle (§4) repopulates all three:

```kotlin
private val _category   = MutableStateFlow(ExtensionCategory.ANIME)   // ADR-016
private val _sources    = MutableStateFlow<List<Installed>>(emptyList())   // trusted
private val _installed  = MutableStateFlow<List<Untrusted>>(emptyList())
private val _available  = MutableStateFlow<List<Available>>(emptyList())
```

- `_sources` filtered by `category` AND `isTrusted = true`; ordered by
  `sourcePriorityOrder` (the drag-reorder persistence key).
- `_installed` filtered by `category` AND `isTrusted = false`; sorted per
  filter sheet selection.
- `_available` from repos, filtered by `category`; sorted per filter
  sheet selection; language filter applied.
- Auto-refresh on `BroadcastReceiver` package install/uninstall events
  (extensions are APKs).

---

## 9. Simple mode interaction (ADR-018)

This screen is mostly power-user territory — the casual user installs
one or two extensions and never returns. Simple mode interaction:

- In simple mode, the **filter bottom sheet** is hidden (the `Tune`
  action icon is removed from the top bar). Sort defaults to `Name A–Z`;
  no language filter.
- The **manage repos** (`🌐`) action is hidden in simple mode — the
  default repos are sufficient for casual users.
- The 3-category structure, the anime/manga toggle, search, and
  pull-to-refresh all remain visible in simple mode.

---

## 10. What's open for the design session

- Whether the anime/manga toggle should be a 2-way §3 (current plan) or
  a 3-way §2 (`Anime` / `Manga` / `Both`).
- Whether trusted sources should also be reorderable via long-press drag
  (current) OR an explicit "Edit order" mode toggle.
- The max-2-trusted limit's UI when the user has 0 trusted sources —
  prominent "Add your first trusted source" CTA vs. plain empty copy.
- Tablet layout — could the 3 cards become a 2-column grid (Sources
  full-width on top, Installed + Available side-by-side below)? TBD.

---

## See also

- [`../01-principles/core-principles.md`](../01-principles/core-principles.md)
  — principles #1, #6, #8, #10.
- [`../02-components/components.md`](../02-components/components.md) — §1
  (bottom-up menu for the filter sheet), §3 (2-way toggle for
  anime/manga), §9 (subdued settings-variant section header).
- [`bottom-nav.md`](bottom-nav.md) — the floating nav that brings the
  user to the "More" tab, which surfaces this screen.
- `OLD_ANIKUTA/ANALYSIS/history-extensions-settings-screens.md` §2 —
  source analysis of the old project's `ExtensionsSettingsScreen.kt` +
  `ExtensionsViewModel.kt`.
- `DOCS/04-design-decisions.md` — ADR-015, ADR-016, ADR-018.
