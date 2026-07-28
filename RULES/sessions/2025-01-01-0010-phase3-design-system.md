# Session handoff — Phase 3: Design system & theme

**Agent:** Z.ai Code (session 10)
**Task ID:** phase-3-design-system
**Session goal:** Build `:core:designsystem` with the Anikuta theme (#B1F256) and
core reusable components.

## What I did

### Step 1: Theme files (in `:core:designsystem/theme/`)
- `Color.kt` — the full #B1F256-based palette: dark/light/AMOLED color schemes,
  surface tonal tiers (surface1–5), text tiers, M3 color roles, functional colors.
  (From `DESIGN_LANGUAGE/03-themes/anikuta-palette.md`.)
- `Type.kt` — M3 type scale with tightened letter spacing (modern feel).
- `Shape.kt` — corner radii (28dp nav pill, 16dp cards, 12dp standard, 8dp chips, 6dp buttons).
- `Motion.kt` — animation specs (300ms standard, 200ms short, 100ms instant, FastOutSlowInEasing).
- `Theme.kt` — `AnikutaTheme` composable: dark default, AMOLED option, status bar matches background.

### Step 2: Core components (in `:core:designsystem/component/`)
- `NavIcons.kt` — Material vector icons (Home, Library, History, Schedule, Search, Settings, More) + `NavItem` data class.
- `BottomNavBar.kt` — the floating pill nav (the flagship component). Ported from the prototype: floating overlay (not Scaffold), 28dp pill, 42/58dp heights, active-expands/inactive-shrinks, AnimatedVisibility label, color animations.
- `CollapsingHeader.kt` — shrink-on-scroll title (32sp → 22sp), pinned, actions slot.
- `AnikutaBottomSheet.kt` — modal bottom sheet with NO drag handle (principle #2), partial height (principle #3).
- `CustomToggle.kt` — pill-shaped toggle (not default Switch).
- `SegmentedToggles.kt` — `TwoWayToggle` + `ThreeWayToggle` (segmented selectors with primary/onPrimary selected styling).
- `SectionHeader.kt` — accent-colored, left-aligned section header (principle #6).
- `SettingsGroupCard.kt` — surface-tinted card for settings groups (with `SettingRow` helper).

### Step 3: Wired theme in `:app`
- `MainActivity` now wraps content in `AnikutaTheme(darkTheme = true)`.
- The "ANIKUTA" text uses `MaterialTheme.colorScheme.primary` (#B1F256 green).
- Status bar matches the background color.
- Edge-to-edge enabled (`enableEdgeToEdge()`).

## What is DONE (pending CI)
- Theme system complete (#B1F256 palette, dark/light/AMOLED).
- 8 core components implemented (bottom nav, collapsing header, bottom sheet,
  custom toggle, 2-way/3-way toggles, section header, settings group card).
- Theme wired in `:app`.

## What is NOT done
- The remaining 5 components from the plan (CustomNumericKeypad, EpisodeRow,
  BlurredCoverHeader, LivePreviewPanel) — deferred to when their screens are built
  (Phase 5+). They need screen-specific context.
- Actual screens (Home, Library, etc.) — Phase 4+.
- Bottom nav wiring to Voyager — Phase 4 (when we have screens to navigate to).

## What the owner will see after Phase 3
- The app opens with the Anikuta dark theme.
- Background: dark (#14111F).
- "ANIKUTA" text: #B1F256 lime green.
- Status bar: matches the background.
- All components are ready in `:core:designsystem` for Phase 4+ screens.

## What the NEXT agent should do
1. Check CI passes.
2. Download the APK and verify the theme applies.
3. When the owner gives the go, proceed to Phase 4 (first source & browse).

## Pointers
- `core/designsystem/src/main/java/.../theme/` — theme files.
- `core/designsystem/src/main/java/.../component/` — components.
- `app/src/main/java/.../MainActivity.kt` — theme wired here.
- `PLANNING/03-phase-3-execution-plan.md` — the plan.
