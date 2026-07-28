# Phase 3 — Execution Plan: Design System & Theme

> Detailed plan for Phase 3: building `:core:designsystem` with the custom
> M3-inspired theme + all reusable components.
>
> **Key references:**
> - `DESIGN_LANGUAGE/` — the design spec.
> - `PROTOTYPE_REFERENCE/Anime_App/` — the owner's prototype (source for patterns).
> - `DOCS/04-design-decisions.md` — ADRs 015, 017, 025, 026.

## Goal

The `:core:designsystem` module is complete with:
1. The Anikuta theme (AnikutaPalette #B1F256, dark/light/AMOLED).
2. All reusable components from `DESIGN_LANGUAGE/02-components/`.
3. Edge-to-edge setup.
4. The floating bottom nav (from the prototype).

## Modules involved

| Module | Role |
|---|---|
| `:core:designsystem` | Theme, colors, typography, shapes, motion, all components |
| `:app` | Apply the theme, wire edge-to-edge, host the bottom nav |

## Step-by-step

### Step 1: Theme setup (`:core:designsystem`)

- `Color.kt` — the Anikuta palette (#B1F256-based, from `DESIGN_LANGUAGE/03-themes/anikuta-palette.md`).
  - Dark, Light, AMOLED color schemes.
  - Surface tonal tiers (surface1–5).
  - Functional colors (warn, success).
- `Type.kt` — typography (M3 type scale, adjusted).
- `Shape.kt` — shapes (28dp for nav, 12dp for cards, 50% for pills).
- `Motion.kt` — animation specs (tween 300ms, FastOutSlowInEasing).
- `Theme.kt` — `AnikutaTheme` composable:
  - `darkTheme: Boolean` parameter (default: true, per owner preference).
  - Status bar color matches background.
  - `AnikutaPalette` data class for customization (ADR-015).

### Step 2: Core components (`:core:designsystem`)

Port from `DESIGN_LANGUAGE/02-components/` + prototype:

1. **AnikutaBottomNavBar** — the floating pill nav (from prototype's `BottomNavBar.kt`).
   - Active-expands / inactive-shrinks.
   - AnimatedVisibility for label.
   - Material vector icons.
   - Configurable items (3–7, per ADR-017).
2. **CollapsingHeader** — shrink-on-scroll title (from prototype).
3. **BottomUpMenu** — bottom sheet with no drag handle, partial height (principle #2, #3).
4. **ThreeWayToggle** — `StyledSegmentedRow` (3-state, from old ANIKUTA).
5. **TwoWayToggle** — 2-state segmented toggle.
6. **CustomNumericKeypad** — for subtitle settings number entry.
7. **EpisodeRow** — watched = grayscale + blur.
8. **BlurredCoverHeader** — blurred cover + gradient.
9. **LivePreviewPanel** — for settings screens.
10. **SectionHeader** — accent-colored, left-aligned.
11. **SettingsGroupCard** — surface-tinted card for settings sections.
12. **CustomToggle** — pill-shaped toggle (not default Switch).
13. **NavItem** + **NavIcons** — data class + icon object.

### Step 3: Edge-to-edge + status bar

- In `:app` `MainActivity`: `enableEdgeToEdge()` (already done in Phase 1).
- `AnikutaTheme` sets status bar color to match background.
- Content draws edge-to-edge; `CollapsingHeader` uses `statusBarsPadding()`.
- Bottom nav uses `navigationBarsPadding()` (or 16dp bottom padding).

### Step 4: Wire the theme in `:app`

- `MainActivity` wraps content in `AnikutaTheme { ... }`.
- The trivial "ANIKUTA" text gets the Anikuta palette colors.
- Verify: status bar matches background, colors are #B1F256-based.

### Step 5: Verify CI

- Commit, push, wait for CI green.
- Download APK, verify the theme applies (green primary color, dark background).

## NOT in Phase 3 scope

- **Actual screens** (Home, Library, etc.) — Phase 4+.
- **Bottom nav wiring to Voyager** — Phase 4 (when we have screens to navigate to).
- **Settings UI** — Phase 10 (but the components are ready).
- **Dynamic cover-color theming** — Phase 5 (with the details screen).

## ADRs referenced

- ADR-015: Custom M3-inspired design language.
- ADR-017: Configurable bottom nav (3–7 tabs, floating, fixed "More").
- ADR-025: Compose-first, edge-to-edge.
- ADR-026: SDK 26/36.

## What the owner will see after Phase 3

- The app opens with the Anikuta dark theme (#B1F256 green primary).
- The "ANIKUTA" text is styled with the theme.
- The status bar matches the background.
- All components are ready in `:core:designsystem` for Phase 4+ screens.
- No bottom nav yet (no screens to navigate to) — but the component exists.
