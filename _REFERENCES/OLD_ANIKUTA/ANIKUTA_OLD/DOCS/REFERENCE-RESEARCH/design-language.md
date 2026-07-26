# ANI-KUTA UI Design Language — Material 3 Expressive

> The design target for Library / History / Search.
> Extracted from the Detail page, episodes list, settings screens, and player.
> Source files: `app/src/main/java/app/anikuta/ui/{detail,settings,theme}/` + `player/`

---

## Design system: Material 3 Expressive

Built on `androidx.compose.material3`. Dynamic color (Monet) on API 31+,
emerald seed fallback on older APIs.

**What makes it "Expressive" (not vanilla M3):**
1. **Spring-based press feedback** — cards scale + morph corner radius on press
2. **Deliberate surface containment** — floating pill top bars + hero surfaces
3. **Per-anime dynamic theming** — cover color re-themes the entire screen
4. **Alternating zebra rows** — `surfaceContainerLow` / `surfaceContainerHigh`
5. **Uppercase section headers** with a tonal accent bar

---

## Design tokens

### Colors (M3 roles)
```
primary, onPrimary, primaryContainer, onPrimaryContainer
secondary, onSecondary, secondaryContainer, onSecondaryContainer
tertiary, onTertiary, tertiaryContainer, onTertiaryContainer
surface, onSurface, surfaceVariant, onSurfaceVariant
surfaceContainerLow, surfaceContainerHigh, surfaceContainerHighest
outline, outlineVariant
background, onBackground, error, onError
```
> ⚠️ `Theme.kt` does NOT assign `surfaceContainerLow/High/Container/outlineVariant`
> in the static fallback — these only work because Monet populates them on
> API 31+. The revamp should backfill these in the static scheme.

### Shapes (corner radii)
| Token | Radius | Used for |
|-------|--------|----------|
| `shapes.extraSmall` | 2-4 dp | Small badges, chips |
| `shapes.small` | 8 dp | Episode thumbnails, small cards |
| `shapes.medium` | 12 dp | Cards, list items |
| `shapes.large` | 16 dp | ExpressiveAnimeCard default |
| `RoundedCornerShape(20.dp)` | 20 dp | Floating top bars (pill) |
| `RoundedCornerShape(24.dp)` | 24 dp | Hero surfaces, modals |
| `CircleShape` | — | Icon buttons, FABs |

### Typography (M3 type scale + weight overrides)
| Style | Size | Weight | Used for |
|-------|------|--------|----------|
| `headlineMedium` | 28 sp | Bold | Screen titles |
| `titleLarge` | 22 sp | Bold | Section headers |
| `titleMedium` | 16 sp | SemiBold | Card titles, row titles |
| `bodyLarge` | 16 sp | Normal | Body text |
| `bodyMedium` | 14 sp | Normal | Secondary text |
| `labelLarge` | 14 sp | SemiBold | Buttons |
| `labelMedium` | 12 sp | **Bold + uppercase + letterSpacing 1.sp** | Section eyebrow labels |
| `labelSmall` | 11 sp | Medium | Badges, captions |

### Elevation
| Surface | Tonal | Shadow |
|---------|-------|--------|
| Cards | 1-3 dp | 0 (flat) or 1 dp |
| Floating top bar | 3 dp | 6 dp |
| Hero / modal | 4 dp | 8 dp |
| FAB | 6 dp | 6 dp |

### Springs (`AnikutaSprings` — `ui/theme/Expressive.kt`)
| Name | DampingRatio | Stiffness | Used for |
|------|-------------|-----------|----------|
| `press` | `MediumBouncy` (0.6f) | `Low` (200f) | Card press scale (1→0.96) |
| `effects` | `NoBouncy` (1.0f) | `Medium` (400f) | Corner-radius morph (16→20dp) |
| `spatial` | `LowBouncy` (0.8f) | `Medium` (400f) | Layout transitions |
| `expansion` | `MediumBouncy` (0.6f) | `Low` (200f) | Expand/collapse |
| `color` | — | `Medium` | Color transitions |

---

## Component patterns (the building blocks)

### ExpressiveAnimeCard (THE signature card)
- **Shape:** `RoundedCornerShape(16.dp)` → morphs to `20.dp` on press
- **Press:** scales 1→0.96 (`press` spring) + corner morph (`effects` spring)
- **Surface:** `surfaceContainerLow`, tonal 1dp, no shadow
- **Content:** cover image (2:3 or 16:9), title, metadata row
- **⚠️ Currently PRIVATE in `HomeScreen.kt`** — must extract to
  `ui/components/ExpressiveAnimeCard.kt` for Library/History/Search to share

### EpisodeRow (rich variant — detail page)
- **Layout:** 96-112 dp row, `[thumbnail 16:9 128dp | text column | actions]`
- **Thumbnail:** `RoundedCornerShape(8.dp)`, 16:9 aspect
- **Background:** alternating `surfaceContainerLow` / `surfaceContainerHigh`
- **Content:** episode number (badge), title, duration, progress bar (if started)
- **Actions:** download button, play button (appears on hover/press)
- **Long-press:** context menu (download / mark watched / etc.)

### FloatingTopBar (THE top bar pattern)
- **Shape:** `RoundedCornerShape(20.dp)` pill
- **Surface:** `surfaceContainerHigh`, tonal 3dp, shadow 6dp
- **Layout:** `[title or search field] [trailing actions]`
- **Variant — search:** embeds `BasicTextField` in the title slot (aniyomi
  `SearchToolbar` pattern), leading search icon, trailing clear/filter
- **⚠️ Currently PRIVATE in HomeScreen.kt + LibraryScreen.kt + HistoryScreen.kt**
  (coded separately) — must extract to `ui/components/FloatingTopBar.kt`

### SettingsGroupCard
- **Shape:** `RoundedCornerShape(16.dp)`
- **Surface:** `surfaceContainerLow`
- **Contains:** `ClickableSettingsRow` / `SwitchSettingsRow` / `LeadingIcon`
- **Section header:** uppercase `labelMedium Bold primary letterSpacing=1.sp`
  + 3×16dp tonal accent bar on the left

### SelectableOptionCard
- Card-style selector for 2-4 options with descriptions
- Selected state: `primary` border + `primary` text (NOT `primaryContainer`)
- Used in settings for mode selection

### StyledSegmentedRow
- Pill-style segmented row for short-label 2-3 options
- Alternative to M3 `SegmentedButton` (which we DON'T use)

### ModalBottomSheet
- `RoundedCornerShape(24.dp)` top corners
- Drag handle
- Used for: quality/server/audio/subtitle/speed selection sheets

### SkeletonBox / SkeletonAnimeCard
- Loading state — shimmer placeholder matching the content shape
- **History currently uses bare `CircularProgressIndicator`** — should switch
  to skeletons to match

---

## Layout patterns

### Detail page
- **Collapsing header:** cover banner with blur + parallax
- **Sticky info:** title, score, metadata below the banner
- **Episode list:** grouped by season, `EpisodeRow` with alternating bg
- **Dynamic theming:** `generateDynamicScheme(coverColor)` re-themes the page

### Settings subpages
- `SettingsSubpageScaffold` — `FloatingTopBar` with back button + title
- Scrollable column of `SettingsGroupCard`s
- Each group has an uppercase header + accent bar

### Player overlay
- Auto-hide controls (3s timeout)
- Gesture areas (left = brightness, right = volume, horizontal = seek)
- Bottom: scrubber + play/pause + skip buttons
- Top: back + title + overflow
- Sheets: quality, audio, subtitle, speed (from bottom)

---

## Interaction patterns

### Press feedback (THE expressive signature)
```kotlin
// ExpressiveAnimeCard pattern
val interaction = remember { MutableInteractionSource() }
val pressed by interaction.collectIsPressedAsState()
val scale by animateFloatAsState(if (pressed) 0.96f else 1f, pressSpring)
val corner by animateDpAsState(if (pressed) 20.dp else 16.dp, effectsSpring)
Surface(
    shape = RoundedCornerShape(corner),
    scale = scale,
    interactionSource = interaction,
    ...
)
```

### Section header recipe
```kotlin
Row(verticalAlignment = CenterVertically) {
    Box(Modifier.size(3.dp, 16.dp).background(primary, CircleShape))
    Text(text, style = labelMedium, fontWeight = Bold,
         letterSpacing = 1.sp, color = primary)
}
```

### Pull-to-refresh
- `ThreeStagePullRefresh` — custom 3-stage pull (refresh / fetch / complete)
- Currently cosmetic on Library (just re-reads store) — needs real refresh logic

---

## Reusable components inventory

| Component | Location | Used in | Reusable for L/H/S? |
|-----------|----------|---------|---------------------|
| `ExpressiveAnimeCard` | `HomeScreen.kt` (private) | Home | **Extract → Library, Search, History cards** |
| `FloatingTopBar` | `HomeScreen.kt` (private) | Home, Library, History | **Extract → all 3 pages** |
| `EpisodeRow` | `DetailScreen.kt` | Detail | **History list rows** |
| `SettingsGroupCard` | `SettingsComponents.kt` | Settings | Settings pages |
| `SelectableOptionCard` | `SelectableOptionCard.kt` | Settings | Settings pages |
| `StyledSegmentedRow` | `SelectableOptionCard.kt` | Settings, Player | Settings, Library display toggle |
| `SettingsSubpageScaffold` | `SettingsSubpageScaffold.kt` | Settings | Settings pages |
| `SkeletonBox` / `SkeletonAnimeCard` | `components/SkeletonBox.kt` | Home, Detail | **Library, Search, History loading** |
| `ThreeStagePullRefresh` | `detail/ThreeStagePullRefresh.kt` | Detail | Library pull-refresh |
| `DynamicTheming` | `detail/DynamicTheming.kt` | Detail, Player | Library (per-anime theming) |

---

## Cheat sheet: what to DO and AVOID

### DO
- Use `AnikutaSprings.press` + corner morph on all cards
- Use `FloatingTopBar` (20dp pill, tonal 3dp, shadow 6dp) for top bars
- Use uppercase `labelMedium Bold primary letterSpacing=1.sp` + accent bar for section headers
- Use alternating `surfaceContainerLow`/`surfaceContainerHigh` for list rows
- Use `SkeletonAnimeCard` / `SkeletonBox` for loading states
- Use `RoundedCornerShape(16.dp)` for cards (morphing to 20dp on press)
- Use `RoundedCornerShape(8.dp)` for episode thumbnails
- Use 2:3 aspect for anime covers, 16:9 for episode thumbnails

### AVOID
- ❌ M3 `TopAppBar` (use `FloatingTopBar` instead)
- ❌ M3 `SegmentedButton` (use `StyledSegmentedRow`)
- ❌ Flat `Surface(onClick)` with default ripple (use spring press feedback)
- ❌ `tween` animations for press (use springs)
- ❌ `MaterialTheme.shapes.*` defaults (use explicit `RoundedCornerShape(N.dp)`)
- ❌ `primaryContainer` for selected state (use `primary` border + `primary` text)
- ❌ Bare `CircularProgressIndicator` for loading (use skeletons)
- ❌ Hardcoded colors (use M3 color roles)
