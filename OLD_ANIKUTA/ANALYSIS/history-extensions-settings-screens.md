# Old ANIKUTA — History, Extensions, and Details/Episode-Layout Settings Screens

**Task ID:** D-3
**Author:** design-language sub-agent (general-purpose)
**Scope:** Analyze the four owner-flagged screens in the OLD ANIKUTA project and
extract design-language preferences for the new ANIKUTA project.
**Reference tree (read-only, per ADR-007):**
`/home/z/ani_kuta_workspace/ANI_KUTA_NEW/OLD_ANIKUTA/ANIKUTA_OLD/`

The four screens analyzed:
1. **History** — `app/src/main/java/app/anikuta/ui/history/HistoryScreen.kt`
2. **Extensions settings** — `app/src/main/java/app/anikuta/ui/settings/ExtensionsSettingsScreen.kt`
3. **Details settings** (hub) — `app/src/main/java/app/anikuta/ui/settings/DetailsSettingsScreen.kt`
4. **Episode layout** — `app/src/main/java/app/anikuta/ui/settings/LayoutSettingsScreen.kt`
   (plus its sibling subpages `DisplaySettingsScreen.kt`, `MetadataSettingsScreen.kt`,
   `PlayerEpisodeDisplayScreen.kt`, and the shared `EpisodeRowPreview.kt`).

Cross-cutting support files consulted:
- `ui/components/SectionHeader.kt` — accent-color left-aligned header pattern
- `ui/settings/SettingsSubpageScaffold.kt` — shared back-button + title scaffold
- `ui/settings/SettingsComponents.kt` — `SettingsGroupCard`, `SwitchSettingsRow`, etc.
- `ui/settings/SelectableOptionCard.kt` — `StyledSegmentedRow` + `SelectableOptionCard`
- `ui/settings/EpisodeRowPreview.kt` — the live preview composable
- `ui/settings/ExtensionsViewModel.kt` — 3-list data flow
- `ui/history/HistoryViewModel.kt` — `HistoryGroup`, `continueWatching`, `HistoryState`

Relevant ADRs (from `DOCS/04-design-decisions.md`):
- **ADR-015** — Custom M3-inspired design language
- **ADR-016** — Extension categories: video / image-manga (no series/movies split)
- **ADR-017** — Configurable bottom nav (floating bar)
- **ADR-018** — Feature parity with customizable defaults + simple mode

---

## Screen 1 — History

### File path
```
app/src/main/java/app/anikuta/ui/history/HistoryScreen.kt
app/src/main/java/app/anikuta/ui/history/HistoryViewModel.kt   (data flow)
```

### Layout structure (ASCII)

```
┌─────────────────────────────────────────────────────┐
│  statusBarsPadding                                  │
│  ┌───────────────────────────────────────────────┐  │
│  │  FloatingTopBar (RoundedCornerShape 20dp,     │  │
│  │   surfaceContainerHigh, tonalElev 3, shadow 6)│  │
│  │   ┌──────────────┐         ┌──────────────┐  │  │
│  │   │ "History"    │         │ ⋮ (overflow)  │  │  │
│  │   │ titleLarge   │         │ → Clear all   │  │  │
│  │   └──────────────┘         └──────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ▌ Continue Watching        ← 4dp×20dp primary bar  │
│                                                     │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐  LazyRow      │
│  │ 16:9 │ │ 16:9 │ │ 16:9 │ │ 16:9 │  200dp wide    │
│  │ cover│ │ cover│ │ cover│ │ cover│  aspect 16:9   │
│  │ ▓▓▓▓ │ │ ▓▓▓▓ │ │ ▓▓▓▓ │ │ ▓▓▓▓ │                │
│  │ title│ │ title│ │ title│ │ title│  gradient      │
│  │ 5m ▕ │ │ 12m ▕│ │ 2m ▕ │ │ 1m ▕ │  overlay       │
│  └──────┘ └──────┘ └──────┘ └──────┘                │
│                                                     │
│  ▌ Today                       ← accent bar + label │
│  ┌───────────────────────────────────────────────┐  │
│  │ [72×40 thumb] Title                45%        │  │
│  │                2 min ago                        │  │
│  └───────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────┐  │
│  │ [72×40 thumb] Another title         10%       │  │
│  │                45 min ago                       │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ▌ Yesterday                  ← accent bar + label │
│  ...                                                 │
│  ▌ This Week                                        │
│  ...                                                 │
│  ▌ Earlier                                          │
│  ...                                                 │
└─────────────────────────────────────────────────────┘
```

### Key design details the owner flagged

1. **"Continue watching" at the top** — `LazyRow` of `ContinueWatchingCard`
   composables, rendered as a section right under the floating top bar.
   Implemented as:
   ```kotlin
   if (continueWatching.isNotEmpty()) {
       item(key = "continue_header") { HistorySectionHeader("Continue Watching") }
       item(key = "continue_row")    { ContinueWatchingRow(continueWatching, onResume) }
   }
   ```
   The "continue watching" set is computed in the ViewModel by filtering entries
   whose `progressFraction < 0.9f` (90% completion threshold).

2. **Section headers with accent color on the LEFT** — `HistorySectionHeader`
   renders a 4dp-wide × 20dp-tall `primary`-colored tonal bar on the left,
   followed by an 8dp spacer and the title text:
   ```kotlin
   @Composable
   private fun HistorySectionHeader(title: String) {
       Row(verticalAlignment = Alignment.CenterVertically,
           modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
           Surface(
               modifier = Modifier.width(4.dp).height(20.dp),
               shape = RoundedCornerShape(2.dp),
               color = MaterialTheme.colorScheme.primary,        // ← accent bar
           ) {}
           Spacer(modifier = Modifier.width(8.dp))
           Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
       }
   }
   ```
   This mirrors the app-wide `SectionHeader` composable
   (`ui/components/SectionHeader.kt`) which uses a 3×16dp pill in
   `accentColor` (default `primary`) followed by uppercase `labelMedium` text
   in the same color. The History version is slightly larger (20dp vs 16dp,
   `titleLarge` vs `labelMedium`) and uses the system title case rather than
   uppercase — but the **accent-color-bar-on-the-left pattern** is identical.
   The owner flagged this as **"proper"** — keep it.

3. **Time-grouped sections** — `HistoryViewModel.groupByTime` buckets entries
   into ordered groups: `Today`, `Yesterday`, `This Week`, `Earlier`. Each
   non-empty bucket is rendered as a `HistorySectionHeader` followed by a list
   of `HistoryEntryRow`s. The grouping is calendar-day based (not 24-hour
   deltas) so "Today" always means the current calendar day.

4. **Cover text overlay is "not proper"** — `ContinueWatchingCard` overlays
   the title + remaining-minutes + progress bar directly on the cover with a
   black vertical gradient (transparent → 75% black). The owner flagged this
   overlay text as "not proper." Code that the owner is unhappy with:
   ```kotlin
   // Bottom gradient for text readability
   Box(modifier = Modifier.fillMaxSize().background(
       Brush.verticalGradient(
           colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
       ),
   ))
   // Info column — title, "X min left", progress bar
   Column(modifier = Modifier.align(Alignment.BottomStart)
       .fillMaxWidth().padding(12.dp)) {
       Text(entry.title, style = MaterialTheme.typography.labelMedium,
           fontWeight = FontWeight.Bold, color = Color.White,
           maxLines = 2, overflow = TextOverflow.Ellipsis)
       Spacer(modifier = Modifier.height(4.dp))
       Text("$remainingMin min left",
           style = MaterialTheme.typography.labelSmall,
           color = Color.White.copy(alpha = 0.85f))
       Spacer(modifier = Modifier.height(6.dp))
       LinearProgressIndicator(
           progress = { entry.progressFraction.coerceIn(0f, 1f) },
           modifier = Modifier.fillMaxWidth(),
           color = Color.White,
           trackColor = Color.White.copy(alpha = 0.3f),
       )
   }
   ```
   Issues: title uses `labelMedium` (too small), the gradient is harsh
   (75% black at the bottom), and the progress bar competes with the title for
   the same overlay space.

5. **Card placement "could be made better"** — `ContinueWatchingCard` is a
   `200dp × (200 * 9/16)dp` portrait card in a `LazyRow` with 10dp spacing.
   The owner feels the size/placement of the continue-watching cards in
   relation to the rest of the screen is suboptimal. Possible improvements:
   make the carousel a hero-style banner (full-width, 16:9), or move the
   continue-watching section into a peekable top carousel with snap behavior.

### Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| "Continue watching" section at the top | ✅ keep | Carousel position is right |
| Section headers with accent color on left (Today / Yesterday / This Week / Earlier) | ✅ keep | The accent bar + bold title pattern is "proper" |
| Text overlay on covers (title, min-left, progress bar) | ⚠️ improve | "not proper" — rework typography, contrast, and spacing |
| Card placement in continue-watching row | ⚠️ improve | "could be made better" — consider hero-style or snappable layout |
| Per-row 16:9 thumbnail + title + relative time + percent | ✅ keep (implicit) | Functional, low-glance list row |
| Long-press to remove single entry; overflow → clear all | ✅ keep | Standard destructive-action pattern |
| Floating top bar (RoundedCornerShape 20, tonalElev 3, shadow 6) | ✅ keep | Matches HomeScreen pattern |

### Key composable: `HistoryEntryRow`

The list row for a chronological entry demonstrates the "small thumbnail +
meta column + trailing readout" pattern used across many ANIKUTA list rows.
Shape: `Surface(RoundedCornerShape(14.dp), surfaceContainerLow, tonalElev 1)`
with a `Row` containing:

1. 72×40dp 16:9 thumbnail (with hue-derived placeholder fallback when no image URL).
2. 12dp spacer.
3. Meta `Column(weight 1f)`: title (`bodyMedium` Medium, 2 lines) + relative
   time (`bodySmall` `onSurfaceVariant`).
4. 8dp spacer.
5. Trailing percentage readout (`labelSmall` Bold `primary`).

The row is `combinedClickable` with `onLongClick` triggering a haptic
`LongPress` and firing `onLongPress()` (used to surface the single-entry
delete dialog). See `HistoryScreen.kt` lines 427–516 for the full source.

### Data flow (`HistoryViewModel`)

- Reactive: collects `WatchProgressStore.changes` (a `Flow<Map<String, Progress>>`)
  — history updates in real time as the user watches episodes; no manual refresh
  needed.
- `continueWatching` = entries with `progressFraction < 0.9f`.
- `groups` = ordered buckets produced by `groupByTime(now)` using
  calendar-day deltas: `Today` / `Yesterday` / `This Week` (2..7 days) /
  `Earlier`.
- States: `Loading`, `Empty`, `Error(message)`, `Success(continueWatching, groups)`.

---

## Screen 2 — Extensions settings

### File path
```
app/src/main/java/app/anikuta/ui/settings/ExtensionsSettingsScreen.kt   (835 lines)
app/src/main/java/app/anikuta/ui/settings/ExtensionsViewModel.kt        (data flow)
```

### Layout structure (ASCII)

```
┌─────────────────────────────────────────────────────┐
│  ← Extensions               [🔍] [⚙ Tune] [🌐]      │ ← SettingsSubpageScaffold
│  statusBarsPadding                                  │   (back + title + actions)
│                                                     │
│  [ Animated search bar — only when search is on ]   │
│  ┌───────────────────────────────────────────────┐  │
│  │ 🔍  Search extensions…                    ✕   │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  PullToRefreshBox                                   │
│  ┌───────────────────────────────────────────────┐  │
│  │  ▌ SOURCES · 1/2                              │  │ ← SettingsGroupCard #1
│  │     (max 2 trusted, drag-and-drop reorder)    │  │
│  │  ⋮  [icon] Crunchyroll     v1.4 · 1 source    │  │
│  │             [Untrust icon]                    │  │
│  │  ─────────────────────────────────────────    │  │
│  │  ⋮  [icon] HiAnime         v2.1 · 1 source    │  │
│  │             [Untrust icon]                    │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  ▌ INSTALLED · 3                              │  │ ← SettingsGroupCard #2
│  │  [icon] AnimePahe       v1.2 · EN             │  │
│  │         [Trust icon] [Delete icon]            │  │
│  │  ─────────────────────────────────────────    │  │
│  │  [icon] Gogoanime       v1.5 · EN             │  │
│  │         [Trust icon] [Delete icon]            │  │
│  │  ─────────────────────────────────────────    │  │
│  │  [icon] Zoro            v1.0 · EN             │  │
│  │         [Trust icon] [Delete icon]            │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  ▌ AVAILABLE · 47                             │  │ ← SettingsGroupCard #3
│  │  [icon] Aniwatch        v1.1 · EN             │  │
│  │         [Circular ⬇ install button]           │  │
│  │  ─────────────────────────────────────────    │  │
│  │  [icon] 9anime         v1.3 · EN              │  │
│  │         [Circular ⬇ install button]           │  │
│  │  ...                                           │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### The 3-category separation (owner's key preference)

The owner explicitly flagged the 3-section separation as **"quite good"** and a
**key design-language reference**. Implemented as three back-to-back
`SettingsGroupCard` items inside one `LazyColumn` (`contentPadding = 16.dp`,
`verticalArrangement = Arrangement.spacedBy(12.dp)`):

```kotlin
// 1. Sources section — TRUSTED, max 2, drag-reorderable
item(key = "sources_section") {
    SettingsGroupCard(title = "Sources · ${sortedSources.size}/2") {
        if (sortedSources.isEmpty()) {
            EmptySectionBody("No trusted sources. Install an extension, then tap Trust to add it here.")
        } else {
            // sh.calvin.reorderable drag-and-drop list (heightIn max = count * 72dp)
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                onReorderPriority(from.index, to.index)
            }
            LazyColumn(state = lazyListState, userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(max = (sortedSources.size * 72).dp)) {
                items(sortedSources, key = { it.pkgName }) { ext ->
                    ReorderableItem(reorderableState, key = ext.pkgName) {
                        SourceExtensionRow(ext = ext, onClick = { onOpenDetails(ext.pkgName) },
                            onUntrust = { onRevoke(ext) },
                            showDragHandle = sortedSources.size > 1,
                            dragModifier = if (sortedSources.size > 1) Modifier.draggableHandle() else Modifier)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// 2. Installed section — UNTRUSTED but installed locally (forEachIndexed + HorizontalDivider)
item(key = "installed_section") {
    SettingsGroupCard(title = "Installed · ${installed.size}") { /* UntrustedExtensionRow per ext */ }
}

// 3. Available section — from repos, ready to install (LoadingBody / EmptySectionBody / list)
item(key = "available_section") {
    SettingsGroupCard(title = "Available · ${available.size}") { /* AvailableExtensionRow per ext */ }
}
```

The ViewModel exposes three `StateFlow`s — one per section:
```kotlin
private val _sources   = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())   // trusted
private val _installed = MutableStateFlow<List<AnimeExtension.Untrusted>>(emptyList())   // untrusted installed
private val _available = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())    // from repos
```

### The anime/manga toggle at the top (owner-flagged, NOT yet in code)

The owner's preference (per task description and **ADR-016**) is to have an
**anime/manga toggle at the very top** of this screen to switch which
extensions are shown. **Grep confirms this toggle is not yet implemented in
the old project** — the old project is anime-only, so there was no need for
the toggle. This is a **forward-looking requirement for the new project**:
add a 2-way `SegmentedButton` (or `FilterChip` pair) above the
`PullToRefreshBox` to switch between "Video" and "Image/Manga" categories
per ADR-016. Suggested insertion point: between the
`AnimatedVisibility(search)` block and the `PullToRefreshBox`, inside the
outer `Column`.

### Top-bar actions (search / filter / repos)

The `SettingsSubpageScaffold` accepts an `actions` slot, populated with three
`IconButton`s:

```kotlin
SettingsSubpageScaffold(
    title = "Extensions",
    onBack = onBack,
    actions = {
        IconButton(onClick = { viewModel.setSearchActive(!isSearchActive) }) {
            Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (isSearchActive) "Close search" else "Search")
        }
        IconButton(onClick = { showFilterSheet = true }) {
            Icon(Icons.Default.Tune, contentDescription = "Filter")
        }
        IconButton(onClick = onManageRepos) {
            Icon(Icons.Outlined.Public, contentDescription = "Manage repositories")
        }
    },
) { ... }
```

The search field appears BELOW the action icons (not in the top bar itself)
with an `AnimatedVisibility` slide-down — this is a deliberate pattern so the
top bar never overflows horizontally.

### Filter bottom sheet (sort + language)

Triggered from the `Tune` icon. Uses `ModalBottomSheet` + `SegmentedOption`
pills. Two sections: "Sort by" (Name A-Z / Name Z-A) and "Languages"
(multi-select chips in 3-wide rows).

```kotlin
@Composable
private fun SegmentedOption(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = modifier,
    ) { ... }
}
```

### The "Sources · N/2" limit + max-2 popup

A hard cap of 2 trusted sources enforces a "primary + secondary" mental model.
Exceeding the limit triggers `MaxTrustedSourcesDialog` (an `AlertDialog`) which
shows the currently-trusted sources with their logos (via `ExtensionIconSlot`)
and lets the user revoke one to make room; the ViewModel then auto-trusts the
pending extension (`revokeAndAutoTrust(pkgToRevoke)` — 500ms delay so the
revoke processes before the trust call). See `ExtensionsSettingsScreen.kt`
lines 502–553 for the full source.

### Extension row types

Three distinct row layouts (one per section), all sharing a common
`ExtensionIconSlot` (a squircle-shape Surface — `RoundedCornerShape(percent = 28)`
— with the extension's `Drawable` icon or `iconUrl`, falling back to the
`Icons.Default.Extension` glyph). Rows differ in their trailing action:

| Row | Trailing action | Notes |
|---|---|---|
| `SourceExtensionRow` | Drag handle (left) + Untrust (VerifiedUser icon, primary tint) | Drag handle only shown when >1 source |
| `UntrustedExtensionRow` | Trust (VerifiedUser icon, onSurfaceVariant) + Delete (Delete icon, error tint) | |
| `AvailableExtensionRow` | Check (if installed) / Spinner (if downloading) / Circular ⬇ button (otherwise) | Compact install button, not a full text button |

The circular install button — a `Surface` with `RoundedCornerShape(50)` and a
`Download` icon padded by 8dp — is intentionally compact ("no blue badge")
per a documented design decision in the file header.

### Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| 3-category separation: Sources / Installed / Available | ✅ keep ("quite good") | The single most important pattern to preserve |
| Trusted sources at the VERY TOP | ✅ keep | Above installed, above available |
| Installed in the MIDDLE | ✅ keep | Between trusted and available |
| Available at the VERY BOTTOM | ✅ keep | Longest list, lowest priority |
| Drag-and-drop reorder of trusted sources | ✅ keep | `sh.calvin.reorderable` |
| Max-2-trusted limit + revoke-to-add popup | ✅ keep | Enforces primary/secondary mental model |
| Compact circular install button (no blue badge) | ✅ keep | Cleaner than full-width text buttons |
| Squircle extension icons | ✅ keep | `RoundedCornerShape(percent = 28)` |
| Search bar slides down BELOW the top-bar actions | ✅ keep | Prevents horizontal overflow |
| Filter bottom sheet with SegmentedOption pills | ✅ keep | Sort + language chips |
| Pull-to-refresh | ✅ keep | Auto-refreshes via BroadcastReceiver on package install/uninstall too |
| **Anime/manga toggle at the top** | ⚠️ add (per ADR-016) | Not yet implemented — old project is anime-only |
| Per-section empty-state copy | ✅ keep | "No trusted sources. Install an extension, then tap Trust…" |
| HorizontalDivider color: `outlineVariant` between rows | ✅ keep | Subtle separator |

---

## Screen 3 — Details settings (hub)

### File path
```
app/src/main/java/app/anikuta/ui/settings/DetailsSettingsScreen.kt   (162 lines)
```

### Layout structure (ASCII)

```
┌─────────────────────────────────────────────────────┐
│  ← Details                                          │ ← SettingsSubpageScaffold
│  statusBarsPadding                                  │
│                                                     │
│  LIVE PREVIEW       ← primary-colored, labelMedium  │
│  ┌───────────────────────────────────────────────┐  │
│  │  EpisodeRowPreview (bare episode card,        │  │
│  │  NOT wrapped in SettingsGroupCard — same      │  │
│  │  padding as the real detail page)             │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │ [Thumbnail]  EP 5 · The Dragon's…       │  │  │
│  │  │             Mar 15, 2024 · S • D        │  │  │
│  │  │             Synopsis text in surface    │  │  │
│  │  │             container…                  │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  CUSTOMIZE          ← primary-colored, labelMedium  │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Settings"
│  │  📊 Episode display   Show/hide numbers,      │  │
│  │                       titles, summaries…      │  │
│  │  ─────────────────────────────────────────    │  │
│  │  🎛 Episode layout    Positions for title,    │  │
│  │                       synopsis, date, thumb…  │  │
│  │  ─────────────────────────────────────────    │  │
│  │  ✨ Metadata fetching Fetch thumbnails,       │  │
│  │                       titles, descriptions…   │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Appearance"
│  │  🎨 Dynamic theming    Color the detail page  │  │
│  │                        based on cover image   │  │
│  │                                  [Switch]     │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### Key design details the owner flagged

1. **Live preview at the VERY TOP** — `EpisodeRowPreview` is rendered as the
   first item, with `LIVE PREVIEW` label above it (primary-colored,
   `labelMedium`, Bold, `letterSpacing = 1.sp`). The preview is a bare
   episode card (NOT wrapped in `SettingsGroupCard`) so its padding and card
   structure exactly match the real detail page. **The owner called this
   "perfect. It is beautiful and exactly how I expect it to be."**

   ```kotlin
   item {
       Column {
           Text(
               "LIVE PREVIEW",
               style = MaterialTheme.typography.labelMedium,
               fontWeight = FontWeight.Bold,
               color = MaterialTheme.colorScheme.primary,
               letterSpacing = 1.sp,
               modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
           )
           Column(modifier = Modifier.padding(horizontal = 16.dp)) {
               EpisodeRowPreview(
                   showThumbnails = showThumbnails,
                   showSummaries = showSummaries,
                   showTitles = showTitles,
                   showDates = showDates,
                   showEpisodeNumber = showEpisodeNumber,
                   showAudioPills = showAudioPills,
                   synopsisPosition = synopsisPos,
                   datePosition = datePos,
                   thumbnailSize = thumbSize,
                   titlePosition = titlePos,
                   episodeNumberPosition = epNumPos,
                   thumbnailPosition = thumbPos,
                   downloadButtonPlacement = dlPlacement,
               )
           }
       }
   }
   ```

2. **"Customize" section below the preview** — A `SettingsGroupCard` titled
   "Settings" containing three `ClickableSettingsRow` entries, each with an
   icon, title, subtitle, and tap-to-navigate behavior:
   - Episode display (icon: `ViewAgenda`) → `DisplaySettingsScreen`
   - Episode layout (icon: `Tune`) → `LayoutSettingsScreen`
   - Metadata fetching (icon: `AutoAwesome`) → `MetadataSettingsScreen`

   ```kotlin
   SettingsGroupCard(title = "Settings") {
       ClickableSettingsRow(
           icon = Icons.Default.ViewAgenda,
           title = "Episode display",
           subtitle = "Show or hide episode numbers, titles, summaries, thumbnails, dates, and audio pills",
           onClick = onOpenDisplay,
       )
       HorizontalDivider()
       ClickableSettingsRow(
           icon = Icons.Default.Tune,
           title = "Episode layout",
           subtitle = "Positions for title, synopsis, date, episode number, thumbnail, and anime info",
           onClick = onOpenLayout,
       )
       HorizontalDivider()
       ClickableSettingsRow(
           icon = Icons.Default.AutoAwesome,
           title = "Metadata fetching",
           subtitle = "Fetch episode thumbnails, titles, and descriptions from external sources",
           onClick = onOpenMetadata,
       )
   }
   ```

3. **Separate "Appearance" section** — Dynamic theming toggle lives in its
   OWN `SettingsGroupCard` (titled "Appearance"), separate from the
   "Customize" group. This separation is intentional: theming is a global
   appearance concern, not a per-element customization.

   ```kotlin
   item {
       val dynamicTheming by prefs.dynamicDetailTheming().stateIn(scope).collectAsState()
       Column(modifier = Modifier.padding(horizontal = 16.dp)) {
           SettingsGroupCard(title = "Appearance") {
               SwitchSettingsRow(
                   icon = Icons.Default.Palette,
                   title = "Dynamic theming",
                   subtitle = "Color the detail page based on the anime's cover image",
                   checked = dynamicTheming,
                   onCheckedChange = { prefs.dynamicDetailTheming().set(it) },
               )
           }
       }
   }
   ```

### The "Customize" subpages (siblings — share the live-preview pattern)

Per the navigation graph (`AnikutaNavGraph.kt`):
- `settings/details/display` → `DisplaySettingsScreen`
- `settings/details/layout`  → `LayoutSettingsScreen`
- `settings/details/metadata`→ `MetadataSettingsScreen`

All three subpages share the same skeleton:
```
SettingsSubpageScaffold(title, onBack) {
    Column {
        Text("LIVE PREVIEW", ...)         // sticky, non-scrolling
        EpisodeRowPreview(...)
        LazyColumn { ...toggles... }      // scrollable settings below
    }
}
```

### Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| Live preview at the VERY TOP | ✅ keep ("perfect, beautiful, exactly how I expect") | Non-negotiable for new ANIKUTA |
| Live preview is a BARE episode card (not wrapped in a settings group) | ✅ keep | Padding/card structure matches real detail page |
| "LIVE PREVIEW" label (primary, labelMedium, Bold, letterSpacing 1sp) | ✅ keep | Visual anchor above the preview |
| "Customize" section below the preview | ✅ keep | Three subpage links in one card |
| Separate "Appearance" section for global theming | ✅ keep | Theming ≠ element customization |
| Each subpage also has a sticky live preview at its top | ✅ keep | Lets the user see effect of any single category of changes |
| `MetadataSettingsScreen` forces all display toggles ON in its preview | ✅ keep | Preview always shows full set of elements regardless of Display prefs |
| **"Watched episode appearance" doesn't apply to the live preview** | ⚠️ improve | See Screen 4 below |

---

## Screen 4 — Episode layout settings (and its siblings)

This is the screen the owner described as "beautiful toggles: 3-way toggles
and 2-way toggles." The implementation is split across:

### File paths
```
app/src/main/java/app/anikuta/ui/settings/LayoutSettingsScreen.kt          (310 lines, "Details → Episode layout")
app/src/main/java/app/anikuta/ui/settings/DisplaySettingsScreen.kt         (203 lines, "Details → Episode display")
app/src/main/java/app/anikuta/ui/settings/MetadataSettingsScreen.kt        (170 lines, "Details → Metadata fetching")
app/src/main/java/app/anikuta/ui/settings/PlayerEpisodeDisplayScreen.kt    (298 lines, "Player → Episode list" — duplicate of Display+Layout for the in-player episode list)
app/src/main/java/app/anikuta/ui/settings/EpisodeRowPreview.kt             (488 lines, shared live preview)
app/src/main/java/app/anikuta/ui/settings/SelectableOptionCard.kt          (153 lines, StyledSegmentedRow + SelectableOptionCard)
```

### Layout structure (ASCII) — `LayoutSettingsScreen`

```
┌─────────────────────────────────────────────────────┐
│  ← Episode layout                                   │ ← SettingsSubpageScaffold
│  statusBarsPadding                                  │
│                                                     │
│  LIVE PREVIEW       ← sticky, non-scrolling         │
│  ┌───────────────────────────────────────────────┐  │
│  │  EpisodeRowPreview(...)                       │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ─── scrollable settings below ─────────────────    │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Text content"
│  │  Title                                         │  │
│  │  Where the episode title appears              │  │
│  │  ┌─────────────┬──────────────┐               │  │
│  │  │    Right    │    Below     │  ← 2-way seg  │  │
│  │  └─────────────┴──────────────┘               │  │
│  │  ─────────────────────────────────────────    │  │
│  │  Synopsis                                      │  │
│  │  Where the episode description appears        │  │
│  │  ┌─────────────┬──────────────┐               │  │
│  │  │    Right    │    Below     │               │  │
│  │  └─────────────┴──────────────┘               │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Badges & pills"
│  │  Date & audio pills                           │  │
│  │  ┌─────────┬─────────┬─────────┐              │  │
│  │  │  Above  │  Below  │  Full   │  ← 3-way seg │  │
│  │  └─────────┴─────────┴─────────┘              │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Episode number"
│  │  Position                                      │  │
│  │  ┌─────────────┬──────────────┐               │  │
│  │  │   Overlay   │    Badge     │  ← 2-way seg  │  │
│  │  └─────────────┴──────────────┘               │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Thumbnail"
│  │  Side                                          │  │
│  │  ┌─────────────┬──────────────┐               │  │
│  │  │    Left     │    Right     │  ← 2-way seg  │  │
│  │  └─────────────┴──────────────┘               │  │
│  │  ─────────────────────────────────────────    │  │
│  │  Size                                          │  │
│  │  ┌─────────┬─────────┬─────────┐              │  │
│  │  │  Small  │ Medium  │  Large  │  ← 3-way seg │  │
│  │  └─────────┴─────────┴─────────┘              │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Page layout"
│  │  Anime info                                    │  │
│  │  Above = full-page scroll. Below = episodes…  │  │
│  │  ┌──────────────┬───────────────┐             │  │
│  │  │ Above eps    │ Below eps     │  ← 2-way    │  │
│  │  └──────────────┴───────────────┘             │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │ ← SettingsGroupCard "Download button"
│  │  Placement                                     │  │
│  │  ┌──────────────┬───────────────┐             │  │
│  │  │ Episode row  │ Synopsis      │  ← 2-way    │  │
│  │  └──────────────┴───────────────┘             │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### The beautiful toggles: 2-way and 3-way `StyledSegmentedRow`

The toggle style the owner praised is implemented in
`SelectableOptionCard.kt`:

```kotlin
@Composable
internal fun StyledSegmentedRow(
    options: List<Pair<String, Boolean>>,   // (label, isSelected)
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, (label, selected) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                    onClick = { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
```

Visual rules:
- Container: `surfaceVariant` at 50% alpha, `RoundedCornerShape(12.dp)`, 4dp inner padding.
- Selected pill: `primary` background + `onPrimary` text, Bold.
- Unselected pill: transparent + `onSurfaceVariant` text, Medium.
- Each pill is `weight(1f)` so they share width evenly.
- 4dp gap between pills (matches container inner padding).

**2-way usage** (Title position: Right / Below):
```kotlin
StyledSegmentedRow(
    options = listOf("Right" to (titlePos == "right"), "Below" to (titlePos == "below")),
    onSelect = { prefs.titlePosition().set(if (it == 0) "right" else "below") },
)
```

**3-way usage** (Date & audio pills position: Above / Below / Full):
```kotlin
StyledSegmentedRow(
    options = listOf(
        "Above" to (datePos == "right_above_synopsis"),
        "Below" to (datePos == "right_below_synopsis"),
        "Full"  to (datePos == "below"),
    ),
    onSelect = { idx ->
        prefs.datePosition().set(when (idx) {
            0 -> "right_above_synopsis"
            1 -> "right_below_synopsis"
            else -> "below"
        })
    },
)
```

### The 4-way `SelectableOptionCard` (alternative toggle for ≥4 options)

When there are 4 options (e.g. "Watched episode appearance" in
`DisplaySettingsScreen`), the design uses `SelectableOptionCard` instead —
each option is a tappable bordered card with a checkmark when selected:

```kotlin
SelectableOptionCard(
    title = "Visual treatment",
    subtitle = "How episodes you've already watched appear in the list",
    options = listOf(
        Triple("none",     "None",            "Watched episodes look the same as unwatched"),
        Triple("grayscale","Grayscale",       "Black & white — desaturate the entire card"),
        Triple("blur",     "Blur",            "Slightly blur the entire card"),
        Triple("both",     "Grayscale + Blur","Maximum visual distinction"),
    ),
    selectedValue = watchedAppearance,
    onSelect = { prefs.watchedEpisodeAppearance().set(it) },
)
```

Visual rules for `SelectableOptionCard`:
- Selected card: 2dp `primary` border + `primary`-tinted text + checkmark icon.
- Unselected card: 1dp `outlineVariant` border + `onSurface` text.
- Background stays `surface` (neutral) — comment in the source explicitly
  notes that `primaryContainer` was tried and rejected as "too dark/blue."

### Metadata fetching configuration (3rd subpage)

`MetadataSettingsScreen` implements the "metadata fetching configuration"
the owner wants documented. Layout:
1. Sticky live preview (display toggles forced ON so all elements show).
2. Master toggle: "Fetch episode metadata" (`enableInAppMetadataFetch`).
3. When master toggle is ON, an `AnimatedVisibility(expandVertically)`
   reveals 3 per-field `SwitchSettingsRow`s:
   - Thumbnails (`fetchMetadataThumbnails`)
   - Titles (`fetchMetadataTitles`)
   - Summaries (`fetchMetadataSummaries`)
4. A `surfaceContainerLow` info card explains: "Metadata is fetched when you
   open an anime's detail page. Only fields missing from the extension are
   enriched."

```kotlin
item {
    AnimatedVisibility(
        visible = enableMetadataFetch,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        SettingsGroupCard(title = "What to fetch") {
            SwitchSettingsRow(
                icon = Icons.Default.Image,
                title = "Thumbnails",
                subtitle = "Fetch episode preview images",
                checked = fetchThumbnails,
                onCheckedChange = { prefs.fetchMetadataThumbnails().set(it) },
            )
            HorizontalDivider()
            SwitchSettingsRow(
                icon = Icons.Default.Title,
                title = "Titles",
                subtitle = "Fetch episode titles",
                checked = fetchTitles,
                onCheckedChange = { prefs.fetchMetadataTitles().set(it) },
            )
            HorizontalDivider()
            SwitchSettingsRow(
                icon = Icons.Default.Subtitles,
                title = "Summaries",
                subtitle = "Fetch episode descriptions",
                checked = fetchSummaries,
                onCheckedChange = { prefs.fetchMetadataSummaries().set(it) },
            )
        }
    }
}
```

### The known improvement: watched-episode appearance doesn't apply to live preview

`DisplaySettingsScreen` lets the user pick a visual treatment for watched
episodes (None / Grayscale / Blur / Grayscale+Blur) and exposes the
preference as `watchedAppearance` (plus `watchedBlurRadius` and
`watchedAlpha` for the slider-controlled sub-settings). However,
`EpisodeRowPreview` **does not accept a `watchedAppearance` parameter** —
grep on `EpisodeRowPreview.kt` for `watched` returns zero matches. This
means changes to "Watched episode appearance" do not reflect in the live
preview. The owner explicitly flagged this as an improvement area. Fix in
the new project: add a `watchedAppearance: String = "none"` (plus
`watchedBlurRadius: Float = 2f`, `watchedAlpha: Float = 1f`) parameter to
`EpisodeRowPreview` and apply the grayscale/blur effect to the demo card.

### The known improvement: `downloadButtonPlacement` toggle was buggy

A code comment in `LayoutSettingsScreen.kt` records a fixed bug — the toggle
previously used `.get()` which captured the value once and never recomposed
("the toggle only 'applied' after navigating away and back"). The fix: use
`prefs.downloadButtonPlacement().stateIn(scope).collectAsState()`.

Lesson: **always** use `.stateIn(scope).collectAsState()` for live-preview
prefs — never `.get()`. The new project should make this idiomatic.

### Owner likes (keep) vs improvements

| Aspect | Owner verdict | Notes |
|---|---|---|
| 2-way `StyledSegmentedRow` toggles | ✅ keep ("beautiful") | Right/Below, Left/Right, Overlay/Badge, Above/Below, Episode-row/Synopsis |
| 3-way `StyledSegmentedRow` toggles | ✅ keep ("beautiful") | Above/Below/Full for date+pills; Small/Medium/Large for thumbnail size |
| 4-way `SelectableOptionCard` for ≥4 options | ✅ keep | Used for watched-episode appearance |
| Container: `surfaceVariant` 50% alpha, `RoundedCornerShape(12.dp)`, 4dp padding | ✅ keep | Soft neutral container |
| Selected pill: `primary` + `onPrimary`, Bold; Unselected: transparent + `onSurfaceVariant`, Medium | ✅ keep | Strong-but-not-overwhelming contrast |
| Sticky live preview at top of every subpage | ✅ keep | "Perfect, beautiful" per owner |
| `LabeledSection` (title + description + control below) | ✅ keep | Each toggle has a clear title + helpful subtitle |
| `SettingsGroupCard` grouping (3-5 controls per card) | ✅ keep | "Text content", "Badges & pills", "Episode number", "Thumbnail", "Page layout", "Download button" |
| Metadata fetching: master toggle + per-field sub-toggles + `AnimatedVisibility` | ✅ keep | Clean reveal pattern |
| Slider sub-settings appear conditionally (only when relevant) | ✅ keep | Blur slider only when blur is on; alpha slider only when grayscale is on |
| `SwitchSettingsRow` for on/off element toggles (icons: Numbers, Title, Subtitles, Image, CalendarMonth, RecordVoiceOver, Download) | ✅ keep | All element toggles share the same row style |
| **Watched-episode appearance doesn't update live preview** | ⚠️ improve | Add `watchedAppearance` param to `EpisodeRowPreview` |
| **`.get()` bug on `downloadButtonPlacement` (now fixed)** | ⚠️ note | Always use `stateIn(scope).collectAsState()` for live-preview prefs |
| Per-anime info position setting (`animeInfoPosition`: above/below episodes) | ✅ keep | LayoutSettingsScreen section 5 |

---

## Cross-cutting design language summary

The four screens share a tight, repeatable design vocabulary. The new ANIKUTA
project should adopt the following building blocks verbatim (per ADR-015).

| # | Building block | Key spec |
|---|---|---|
| 1 | `SettingsSubpageScaffold` | `Column(statusBarsPadding)` → `Row` (back `IconButton` + `titleLarge` Bold title + `actions` slot) + content slot. |
| 2 | `SettingsGroupCard` | `Surface(RoundedCornerShape(16.dp), surfaceContainerLow, tonalElev 1.dp)`. Header: 3×16dp `primary` accent bar + uppercase `labelMedium` Bold `primary` text (`letterSpacing 1.sp`). Body: free-form content slot, rows separated by `HorizontalDivider(outlineVariant)`. |
| 3 | `LabeledSection` | Inside a `SettingsGroupCard`: `[titleMedium SemiBold]` title + `[bodySmall onSurfaceVariant]` description + 10dp spacer + control. Padding `horizontal=16, vertical=12`. |
| 4 | `StyledSegmentedRow` (2-way / 3-way toggle) | Container: `surfaceVariant` 50% alpha, `RoundedCornerShape(12.dp)`, 4dp inner padding. Each pill `weight(1f)`, `RoundedCornerShape(8.dp)`. Selected: `primary` bg + `onPrimary` Bold. Unselected: transparent + `onSurfaceVariant` Medium. |
| 5 | `SelectableOptionCard` (≥4-option toggle) | Each option is its own `Surface(RoundedCornerShape(12.dp), surface)`. Selected: 2dp `primary` border + `primary` Bold text + Check icon. Unselected: 1dp `outlineVariant` border + `onSurface` Medium text. 8dp vertical gap. |
| 6 | `SwitchSettingsRow` | 40dp squircle `LeadingIcon` (`RoundedCornerShape(12.dp)`, `secondaryContainer` bg, 20dp icon, `onSecondaryContainer` tint) + title (`titleMedium` Medium) + subtitle (`bodySmall` `onSurfaceVariant`) + trailing `Switch`. Row is `clickable` to toggle. |
| 7 | `SliderSettingsRow` | Same leading-icon + title + subtitle layout as `SwitchSettingsRow`. Trailing value label (`labelLarge` Bold `primary`, via `valueFormatter`). `Slider(fillMaxWidth)` below. |
| 8 | `ClickableSettingsRow` | Same layout as `SwitchSettingsRow` but with press-scale animation (`animateFloatAsState(0.98f)` via `AnikutaSprings.press`). Tap navigates to subpage. |
| 9 | Live preview pattern | "LIVE PREVIEW" label (`labelMedium`, Bold, `primary`, `letterSpacing 1.sp`, `padding horizontal=16, vertical=4`). Preview composable rendered WITHOUT a `SettingsGroupCard` wrapper — bare, `padding(horizontal=16.dp)` to match real detail page. Sticky non-scrolling at top of every Customize subpage. All preview-affecting prefs via `stateIn(scope).collectAsState()` (reactive — never `.get()`). |
| 10 | `SectionHeader` (non-settings screens) | `ui/components/SectionHeader.kt`: 3×16dp `accentColor` `CircleShape` bar + uppercase `labelMedium` Bold `accentColor` text (`letterSpacing 1.sp`). History uses a slightly larger variant (4×20dp bar, `titleLarge`). The accent-color-bar-on-the-left pattern is the **app-wide section-header signature**. |
| 11 | Color/typography conventions | Surface hierarchy: `surface` < `surfaceContainerLow` < `surfaceContainer` < `surfaceContainerHigh`. Selected/active accent: `primary` (NOT `primaryContainer` — explicitly rejected as too dark/blue). `secondaryContainer` for leading icons, install buttons, squircle ext icons. `outlineVariant` for dividers. Typography: `titleLarge` Bold = screen titles; `titleMedium` Medium = row titles; `bodyMedium` = list/dialog text; `bodySmall` `onSurfaceVariant` = subtitles/meta; `labelMedium` Bold `primary` `letterSpacing 1.sp` = section/group labels; `labelSmall` = pills/badges/overlays. |

---

## Mapping to ADRs

| ADR | How this analysis informs it |
|---|---|
| **ADR-015** (Custom M3-inspired design language) | All four screens are direct design references. The `SettingsGroupCard` + `LabeledSection` + `StyledSegmentedRow`/`SelectableOptionCard` system, plus the `SectionHeader` accent-bar pattern, should be the foundation of the new ANIKUTA settings UI. |
| **ADR-016** (Extension categories: video / image-manga) | The 3-category separation (Sources / Installed / Available) is the structural template. The anime/manga toggle at the top of the extensions screen is a **new requirement** not yet implemented in the old project — add a `SegmentedButton` (Video / Image-Manga) above the `PullToRefreshBox`. The 3-category pattern then repeats per category. |
| **ADR-017** (Configurable bottom nav, floating bar) | Not directly analyzed here, but the `FloatingTopBar` pattern in `HistoryScreen` (statusBarsPadding + `surfaceContainerHigh` + `RoundedCornerShape(20.dp)` + `tonalElevation = 3.dp` + `shadowElevation = 6.dp`) is the same family as the floating bottom nav — preserve the visual language. |
| **ADR-018** (Feature parity + simple mode + custom defaults) | The `SettingsGroupCard`-per-category pattern scales naturally to "simple mode": in simple mode, hide advanced `SettingsGroupCard`s (e.g., "Badges & pills", "Episode number" position) and keep only essentials. Each `SettingsGroupCard` already maps cleanly to a simple-mode-visible flag. |

---

## Top priorities for the new ANIKUTA

### Must-preserve (in priority order)

1. **Live preview at the top of every Customize subpage** — owner: "perfect,
   beautiful, exactly how I expect it to be."
2. **3-category separation on Extensions screen** (Sources / Installed /
   Available; trusted at top, available at bottom).
3. **Accent-color bar on the left of every section header** (3×16dp pill +
   uppercase `labelMedium` Bold text, `letterSpacing 1.sp`). App-wide signature.
4. **`StyledSegmentedRow` for 2-way and 3-way toggles** — owner: "beautiful."
5. **`SettingsGroupCard`** as the universal grouping container.
6. **`LabeledSection`** (title + description + control) inside each card.
7. **Sticky preview + scrollable settings below** layout.
8. **`SwitchSettingsRow` with squircle `LeadingIcon`** for on/off toggles.
9. **`SelectableOptionCard`** for ≥4-option choices.
10. **`SliderSettingsRow`** with trailing value label for continuous values.
11. **Floating top bar** (`surfaceContainerHigh` + `RoundedCornerShape(20.dp)`
    + tonal/shadow elevation) on top-level screens.
12. **Max-2-trusted-sources limit** with revoke-to-add popup (primary +
    secondary mental model).

### Must-improve (in priority order)

1. **Add anime/manga toggle at top of Extensions screen** (per ADR-016). Not
   yet implemented — old project is anime-only.
2. **Make `EpisodeRowPreview` accept `watchedAppearance`** (plus
   `watchedBlurRadius`, `watchedAlpha`) so the watched-episode setting reflects
   in the live preview. Currently it does not.
3. **Rework continue-watching card overlay text** — owner: "not proper."
   Consider: `titleSmall` (not `labelMedium`), softer gradient (50% black),
   more breathing room, or move meta into a footer strip below the cover.
4. **Rework continue-watching card placement** — owner: "could be made better."
   Consider: hero-style full-width banner with snap, or 2-row staggered grid.
5. **Always use `stateIn(scope).collectAsState()` for live-preview prefs** —
   never `.get()` (the `downloadButtonPlacement` bug is the cautionary tale).
6. **Add a "simple mode" visibility flag to every `SettingsGroupCard`** so the
   owner can hide advanced groups (per ADR-018).
7. **Unify the two private `LabeledSection` definitions** (`LayoutSettingsScreen`
   and `PlayerEpisodeDisplayScreen` each declare their own). Move one shared
   `LabeledSection` into `SettingsComponents.kt`.
8. **Unify the duplicate Display/Layout settings** — `DisplaySettingsScreen` +
   `LayoutSettingsScreen` (detail page) vs `PlayerEpisodeDisplayScreen` (player
   episode list, duplicates both). Decide whether the player needs its own pref
   set or can share the detail-page prefs with an override.

---

## File index (exact paths in the old project)

```
# History
app/src/main/java/app/anikuta/ui/history/HistoryScreen.kt
app/src/main/java/app/anikuta/ui/history/HistoryViewModel.kt

# Extensions settings
app/src/main/java/app/anikuta/ui/settings/ExtensionsSettingsScreen.kt
app/src/main/java/app/anikuta/ui/settings/ExtensionsViewModel.kt

# Details settings (hub) + its 3 Customize subpages
app/src/main/java/app/anikuta/ui/settings/DetailsSettingsScreen.kt
app/src/main/java/app/anikuta/ui/settings/DisplaySettingsScreen.kt        # Episode display subpage
app/src/main/java/app/anikuta/ui/settings/LayoutSettingsScreen.kt         # Episode layout subpage
app/src/main/java/app/anikuta/ui/settings/MetadataSettingsScreen.kt       # Metadata fetching subpage
app/src/main/java/app/anikuta/ui/settings/PlayerEpisodeDisplayScreen.kt   # Player → Episode list (duplicate)

# Shared support (used by all of the above)
app/src/main/java/app/anikuta/ui/settings/SettingsSubpageScaffold.kt
app/src/main/java/app/anikuta/ui/settings/SettingsComponents.kt           # SettingsGroupCard, SwitchSettingsRow, SliderSettingsRow, ClickableSettingsRow, LeadingIcon
app/src/main/java/app/anikuta/ui/settings/SelectableOptionCard.kt         # StyledSegmentedRow, SelectableOptionCard
app/src/main/java/app/anikuta/ui/settings/EpisodeRowPreview.kt            # the live preview composable
app/src/main/java/app/anikuta/ui/components/SectionHeader.kt              # app-wide accent-bar section header

# Preferences backing these screens
app/src/main/java/app/anikuta/player/PlayerPreferences.kt                 # showEpisodeTitles, synopsisPosition, downloadButtonPlacement, watchedEpisodeAppearance, fetchMetadata*, etc.
app/src/main/java/app/anikuta/player/PlayerEpisodePreferences.kt          # separate pref set for the in-player episode list
app/src/main/java/app/anikuta/domain/source/service/SourcePreferences.kt  # sourcePriorityOrder (drag-reorder persistence)

# Navigation
app/src/main/java/app/anikuta/navigation/AnikutaNavGraph.kt               # routes: settings/details, settings/details/display, settings/details/layout, settings/details/metadata, settings/extensions, settings/player/episodes
```
