# ANI-KUTA Design System

> **The single source of truth for UI consistency across all screens.**
> Read this before building or modifying any screen.
> Last updated: Session 31 (Library revamp).

---

## 1. Design Language: Material 3 Expressive

Our app uses **Material 3 Expressive** — an evolution of Material 3 with:
- Spring-based motion physics (not tween animations)
- Shape morphing on press (corner radius animates)
- Tonal surface containment (surfaceContainerLow/High for depth)
- Dynamic theming (cover color re-themes the Detail + Player screens)

### What makes it "Expressive" (not vanilla M3)
| Trait | Vanilla M3 | M3 Expressive (ours) |
|-------|-----------|---------------------|
| Press feedback | Ripple only | Scale 1→0.96 + corner morph 16→20dp |
| Card shape | Static | Morphs on press (spring) |
| Section headers | Plain text | Uppercase + tonal accent bar |
| Row colors | Uniform | Alternating surfaceContainerLow/High |
| Top bars | TopAppBar | Floating pill (20dp rounded, tonal 3dp + shadow 6dp) |

---

## 2. Color Tokens

```
// Light (warm cream)
--background: #f2e8da
--card / surfaceContainerLow: #f9f2ea
--surfaceContainerHigh: #e8dccc
--surfaceContainer: (between Low and High)
--outlineVariant: #d7cec1
--primary: #231e18
--onPrimary: #f9f2ea

// Dark (deep warm charcoal)
--background: #1a1612
--card / surfaceContainerLow: #221d18
--surfaceContainerHigh: #332b24
--outlineVariant: #3a3128
--primary: #f2e8da
--onPrimary: #1a1612

// Chart colors (used for badges, pills, accents)
--chart-1 (orange): #f05100
--chart-2 (green):  #0fa05c
--chart-3 (teal):   #3d6a7f
--chart-4 (gold):   #f2a618
--chart-5 (red):    #f0503d
```

---

## 3. Shape System

| Radius | Used for |
|--------|----------|
| 6 dp | Small pills (audio, date, metadata badges) |
| 8 dp | Title backgrounds (surfaceContainer), episode number badges |
| 10 dp | Episode thumbnails |
| 12 dp | Cards (library, episode rows, search results) |
| 16 dp | Default card corner (animates to 20dp on press) |
| 20 dp | Floating top bars, category pills |
| 24 dp | Modal bottom sheets, hero surfaces |

**Rule:** Cards use `Surface(shape = RoundedCornerShape(12.dp))` — NOT `Card`.
The `Card` composable adds default elevation we don't want.

---

## 4. Typography

| Style | Size | Weight | Used for |
|-------|------|--------|----------|
| `headlineSmall` | 24sp | SemiBold | Screen titles |
| `titleLarge` | 22sp | SemiBold | Section headers, sheet titles |
| `titleMedium` | 16sp | Medium | Top bar titles |
| `titleSmall` | 14sp | SemiBold | Card titles, episode titles |
| `bodyLarge` | 16sp | Normal | Body text |
| `bodyMedium` | 14sp | Normal | Secondary text |
| `labelLarge` | 14sp | Medium | Buttons |
| `labelMedium` | 12sp | **Bold + uppercase + 1sp letterSpacing** | Section eyebrows |
| `labelSmall` | 11sp | Medium/SemiBold | Badges, pills, captions |

---

## 5. Springs (`AnikutaSprings` in `ui/theme/Expressive.kt`)

| Spring | Damping | Stiffness | Used for |
|--------|---------|-----------|----------|
| `press` | MediumBouncy (0.6) | High | Card press scale (1→0.96) |
| `effects` | NoBouncy (1.0) | Medium | Corner radius morph (16→20dp) |
| `spatial` | LowBouncy (0.8) | Medium | Layout transitions |
| `expansion` | MediumBouncy (0.6) | Low | Expand/collapse |
| `color` | — | Medium | Color transitions |

**Rule:** NEVER use `tween()` for press feedback. Always use springs.

---

## 6. Component Patterns

### Card (library, search, history)
```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (index % 2 == 0) surfaceContainerLow else surfaceContainerHigh,
    tonalElevation = 1.dp,
    interactionSource = interactionSource,
    onClick = onClick,
) { ... }
```
- Alternating bg colors (surfaceContainerLow/High) — matches EpisodeRow
- 12dp rounded — NOT 16dp (16dp is for the press morph target, not the default)
- tonalElevation 1dp — NOT shadow elevation

### Title in a Surface (episode rows, library cards)
```kotlin
Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
```

### Metadata pills (year, format, episodes, audio)
```kotlin
Surface(
    shape = RoundedCornerShape(6.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
) {
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
    )
}
```

### Audio pills (SUB/DUB) — with dot separators
```kotlin
// 2+ versions → short labels (S•D), 1 version → full label (SUB)
Surface(
    shape = RoundedCornerShape(6.dp),
    color = outlineVariant,
) {
    Row(padding(8, 3), spacedBy(4)) {
        parts.forEachIndexed { idx, audio ->
            if (idx > 0) Dot(size=3dp, color=onSurfaceVariant)
            Text(if (useShort) audio.short else audio.full, labelSmall, SemiBold)
        }
    }
}
```

### Cover badges (on the cover image)
```kotlin
// Black 70% alpha overlay — like episode number badges
Surface(
    shape = RoundedCornerShape(6.dp),
    color = Color.Black.copy(alpha = 0.7f),
) {
    Text(text, color = Color.White, labelSmall, Bold)
}
```

### Floating top bar
```kotlin
Surface(
    shape = RoundedCornerShape(20.dp),
    color = surfaceContainerHigh,
    tonalElevation = 3.dp,
    shadowElevation = 6.dp,
) { ... }
```

### Category pills
```kotlin
Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (isSelected) primary else surfaceContainerHigh,
) {
    Text(name, padding(16, 8), labelLarge,
         fontWeight = if (isSelected) Bold else Medium,
         color = if (isSelected) onPrimary else onSurfaceVariant)
}
```

### Gradient overlay (scroll fade)
```kotlin
// At the top of a scrollable list — fades content under the status bar
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
        .align(Alignment.TopCenter)
        .background(
            Brush.verticalGradient(
                0f to background.copy(alpha = 0.95f),
                0.5f to background.copy(alpha = 0.7f),
                1f to background.copy(alpha = 0f),
            ),
        ),
)
```

---

## 7. Layout Recipes

### Library page
```
┌─────────────────────────────────┐
│ FloatingTopBar (pill, 20dp)     │  ← sticky, hides on sheet open
├─────────────────────────────────┤
│ CategoryPills (horizontal scroll)│  ← hides on sheet open
├─────────────────────────────────┤
│ "X saved" count                 │  ← hides on sheet open
├─────────────────────────────────┤
│ Grid/List of cards              │  ← scrolls
│  ┌──────┐ ┌──────┐              │
│  │ card │ │ card │              │  ← alternating bg
│  └──────┘ └──────┘              │
│  ┌──────┐ ┌──────┐              │
│  │ card │ │ card │              │
│  └──────┘ └──────┘              │
└─────────────────────────────────┘
[gradient overlay at top when scrolled]
```

### Settings bottom sheet
```
┌─────────────────────────────────┐
│ [Filter] [Sort] [Display]       │  ← TabRow
├─────────────────────────────────┤
│ Tab content (scrollable)        │  ← max 400dp height
│                                 │
└─────────────────────────────────┘
```

### Episode row (detail page — the reference)
```
┌──────────────────────────────────────────────┐
│ Surface(12dp, alternating bg)                 │
│ ┌──────────┐  ┌─────────────────────────────┐│
│ │ Thumbnail │  │ Title (surfaceContainer,8dp)││
│ │ (10dp)    │  │ ┌────────────────────────┐ ││
│ │ [EP 1]    │  │ │ Episode Title          │ ││
│ │           │  │ └────────────────────────┘ ││
│ │           │  │ [2025] [SUB] [DUB]         ││
│ │           │  │ Synopsis...                ││
│ └──────────┘  └─────────────────────────────┘│
└──────────────────────────────────────────────┘
```

---

## 8. What to AVOID

- ❌ `tween()` for press feedback — use springs
- ❌ `Card` composable — use `Surface(shape = RoundedCornerShape(12.dp))`
- ❌ `primaryContainer` for selected state — use `primary` border + `primary` text
- ❌ `MaterialTheme.shapes.*` defaults — use explicit `RoundedCornerShape(N.dp)`
- ❌ Bare `CircularProgressIndicator` for loading — use skeletons
- ❌ Flat rows without alternating colors — always alternate
- ❌ Plain text titles — put them in a `surfaceContainer` Surface (8dp)
- ❌ `primaryContainer`/`secondaryContainer`/`tertiaryContainer` for SUB/DUB badges — use `outlineVariant` (matches AudioPills)
- ❌ White/surface badges on cover images — use `Color.Black.copy(alpha = 0.7f)` overlay

---

## 9. Screen-Specific Rules

### Library
- Grid cards: alternating bg, 12dp Surface, title in surfaceContainer, metadata as pills
- List cards: alternating bg, 56×80dp thumbnail, same pill pattern
- Sort: click to select (ascending), click again to invert (descending) — show ArrowUp/Down
- Settings sheet: Filter/Sort/Display tabs, max 400dp height, no categories in sheet
- Categories: pills on the page (below top bar), not in the sheet
- Gradient: 80dp verticalGradient at top when scrolled

### History
- Continue Watching: 16:9 cards with episode thumbnails (not anime covers)
- List rows: 72×40dp thumbnails, alternating bg
- Tap Continue Watching → direct player launch (saved server)
- Tap thumbnail → detail page
- Tap body → direct player launch

### Search
- Source toggle (AniList/Extensions) centered above the search bar
- Search bar: "Search your anime" placeholder, Enter to search
- Filter sheet: mode-aware (AniList = genre/year/season/format/status/sort/adult; Extension = genre/format/sort/status)
- Pagination: infinite scroll (25 per page)
- Source results: clickable → SourceDetailScreen

### Detail
- Episode rows: the REFERENCE pattern — all other screens should match this
- Alternating bg, 12dp Surface, title in surfaceContainer (8dp), pills in outlineVariant (6dp)
- Thumbnail: 10dp rounded, episode number overlay (black 70% alpha)

### Player
- Gradient overlay at the top of the episode list (35dp, background → transparent)
- Controls: auto-hide, spring animations
- Dynamic theming from cover color

---

## 10. How to Use This Document

1. **Before building any screen** — read sections 2-6 (tokens, shapes, typography, springs, components)
2. **Before building cards** — read section 7 (layout recipes) + the EpisodeRow pattern
3. **Before choosing colors** — read section 8 (what to avoid)
4. **After building** — verify against section 9 (screen-specific rules)

If any screen doesn't match this document, it's a bug.
