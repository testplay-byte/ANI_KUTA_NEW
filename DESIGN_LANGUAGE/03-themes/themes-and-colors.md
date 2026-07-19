# 03 — Themes & Colors

> The ANIKUTA theme system: one custom M3-inspired design language, multiple
> user-selectable palettes, three surface modes (Light / Dark / AMOLED).
>
> **ADR refs:** ADR-015 (custom M3-inspired, not stock Material 3 Expressive),
> ADR-018 (customizable defaults + simple mode).
>
> **Status:** STRUCTURE is fixed (this doc). Specific color **values** (hex
> codes, contrast ratios, exact hue choices) are TBD — to be decided in a
> dedicated design session with the owner. Do not hardcode palette values into
> production code yet.

---

## 1. Goals

1. **Consistent throughout the application.** Every screen — Home, Library,
   Watch page, Player, Settings, Extensions, Onboarding — reads from the same
   `AnikutaTheme` composable. There is one design system, not per-screen
   themes. (Per-screen overrides exist for **cover-color dynamic theming** only
   — see §6.)
2. **Owner-personality, not stock.** Stock Material 3 Expressive is rejected
   (ADR-015). The shape system, motion springs, and component density are
   custom. The M3 color **roles** (primary, surface, etc.) are reused so
   components stay interoperable, but the **palettes** plugged into those
   roles are ours.
3. **User-selectable.** The owner wants both a **theme mode** (light/dark/
   AMOLED) AND a **color palette** (the accent family) selectable at runtime,
   persisted, and live-applied without restart.
4. **No theme chaos.** The set of palettes is fixed (curated by the owner). We
   do NOT ship an arbitrary HCT color picker to end users. Dynamic color
   (Wallpaper/Monet) is ONE palette option, not the default.

---

## 2. The two-axis selection model

Theme selection is two independent axes the user combines:

| Axis | Values | Stored in |
|---|---|---|
| **Mode** (surface tone) | `Light` · `Dark` · `AMOLED` · `System` (follows OS) | `themeMode` preference |
| **Palette** (accent family) | `Anikuta` (default) · `Monet/Dynamic` (Android 12+) · curated named palettes (e.g. *TBD*) | `themePalette` preference |

- **Mode = System** is the default. It resolves to Light or Dark from the OS
  configuration. AMOLED is always an explicit user pick (it never auto-selects
  from `System` because AMOLED is an opinion, not a mode).
- **Palette = Anikuta** (the owner's chosen default palette) is the default.
  Monet is opt-in. Curated palettes ship in-app and grow over time per owner
  decision.
- The two axes compose freely: any palette can be rendered in any mode. Each
  palette provides three role-sets: `lightRoles`, `darkRoles`, `amoledRoles`.
  Switching mode does NOT change palette; switching palette does NOT change
  mode.

### 2.1 AMOLED vs Dark

- **Dark** uses M3 dark surfaces with a slight elevation tonal lift
  (e.g. `surface = #191C1A`, `surfaceContainer` lighter). Cards remain
  distinguishable from the background by tone.
- **AMOLED** forces `surface = #000000` and `background = #000000`. Elevated
  surfaces use a subtle dark-grey (NOT a tonal lift toward primary) —
  maximizes OLED battery savings and gives a high-contrast black look.
  Accents keep their dark-mode luminance to avoid blown-out highlights.

---

## 3. Palette contract (what every palette must provide)

Every palette is a `data class AnikutaPalette` providing three complete M3
`ColorScheme`-shaped role-sets:

```
AnikutaPalette(
    id: String,                  // stable id for persistence ("anikuta", "monet", …)
    displayName: String,         // shown in the picker UI
    lightRoles: ColorRoles,      // primary, onPrimary, primaryContainer, …
    darkRoles: ColorRoles,       // …
    amoledRoles: ColorRoles,     // dark roles with pure-black surfaces
    isDynamic: Boolean = false,  // Monet is dynamic (computed at runtime)
)
```

The role set is the **full M3 role set** the old ANIKUTA project already uses
(`Color.kt` in `OLD_ANIKUTA/…/ui/theme/`): `primary`, `onPrimary`,
`primaryContainer`, `onPrimaryContainer`, `secondary`, `secondary*`,
`tertiary`, `tertiary*`, `error`, `error*`, `background`, `onBackground`,
`surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline` —
plus the M3 `surfaceContainer*` tonal variants used by the floating bottom
nav and sheets.

> **Note:** Specific hex values are decided in a dedicated design session.
> The contract above is what the code depends on; palettes are pluggable
> values, not new code.

---

## 4. How a palette applies at runtime

```
@Composable
fun AnikutaTheme(mode: ThemeMode, palette: AnikutaPalette, content: ...) {
    val resolvedMode = when (mode) {
        ThemeMode.System -> if (isSystemInDarkTheme()) Dark else Light
        else -> mode
    }
    val colorScheme = when (resolvedMode) {
        Light  -> palette.lightRoles.toColorScheme()
        Dark   -> palette.darkRoles.toColorScheme()
        AMOLED -> palette.amoledRoles.toColorScheme()
    }
    MaterialTheme(colorScheme, typography = AnikutaTypography,
                  shapes = AnikutaShapes, content = content)
}
```

- Typography and Shapes are **shared across all palettes and all modes** —
  part of the design language, not part of any palette.
- Motion springs (`AnikutaSprings` in old `Expressive.kt`) are also global —
  see `01-principles/motion.md` (TBD).
- The palette is observed as a Compose `State`; switching it in Settings
  recomposes the entire tree — no activity restart.

---

## 5. Where the user selects theme & palette

In **Settings → Appearance** (split into a dedicated subpage; mirrors the old
project's settings-subpage pattern):

```
Settings → Appearance
├── Mode:        [ Light | Dark | AMOLED | System ]   ← single-choice row
├── Palette:     [ Anikuta | Monet | <named> | … ]    ← horizontal scroll of swatches
├── Cover-color theming: [ On | Off ]                 ← §6
└── (later) Owner-defined defaults override            ← ADR-018
```

- The Palette row shows each palette as a small swatch (primary +
  primaryContainer + a sample surface tone). Tapping a swatch applies it
  immediately — the user sees the live effect, including on the Settings
  screen itself.
- **Simple Mode** (ADR-018) hides AMOLED and non-default palettes — a
  first-time user sees only Light/Dark/System and the default `Anikuta`
  palette.
- Onboarding includes a single **Theme** step (mode + palette) — same as the
  old project's `OnboardingScreen` ThemeStep, but extended to expose the
  palette axis.

---

## 6. Cover-color dynamic theming (per-screen override)

Inherited concept from the old ANIKUTA detail + player screens
(`generateDynamicScheme(Color(coverColor)).toM3ColorScheme()` in
`PlayerScreen.kt`). When enabled globally (Appearance toggle, default ON):

- The **anime details page** and the **watch page** derive a secondary color
  scheme from the current anime's cover art (HCT/Monet extraction).
- The override **wraps only that screen's subtree** in a `MaterialTheme`
  override — it does not change the user's selected palette. Backing out
  restores the user's palette.
- AMOLED mode is respected: cover-derived schemes still get pure-black
  surfaces in AMOLED.
- This is the ONLY per-screen theme override in the app. Every other screen
  uses the user's palette verbatim.

> See `04-screens/watch-page.md` for how cover-color theming is applied
> there.

---

## 7. Dynamic color (Monet) — one palette among many

- On Android 12+ (API 31+), the `Monet` palette reads the system wallpaper
  colors via `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
- Monet is **one entry in the Palette picker**, not the default. The owner's
  default is the curated `Anikuta` palette.
- On Android < 12, the Monet entry is hidden (or shown disabled with a
  "requires Android 12+" note — TBD in the design session).
- AMOLED + Monet composes by overriding the Monet dark scheme's surface
  roles with pure black.

---

## 8. Reference to the old ANIKUTA project

The old project at `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/
theme/` ships three files worth studying for STRUCTURE (not for values):

- `Theme.kt` — `AnikutaTheme` composable with light/dark schemes + Monet
  fallback. **No AMOLED, no palette selection.** Reuse the structure, expand
  per this doc.
- `Color.kt` — static emerald seed (e.g. `Primary = 0xFF006B3C` …). One
  palette only. Replace with the contract in §3; the emerald seed is one
  candidate for the curated list — owner decides.
- `Expressive.kt` — `AnikutaTypography`, `AnikutaShapes`, `AnikutaSprings`.
  Reuse as the **global** (palette-independent) typography/shapes/motion;
  relocate to `03-themes/typography.md`, `03-themes/shapes.md`,
  `01-principles/motion.md`.

**What's new in ANIKUTA vs old:**

1. AMOLED mode (old project has only Light/Dark).
2. Palette selection axis (old project has only the static emerald palette +
   Monet).
3. The two-axis model (mode × palette) with cross-product rendering.
4. Owner-curated palette set (old project ships exactly one).

---

## 9. Persistence & backup

- `themeMode` and `themePalette` are stored in the standard preferences store
  (same mechanism as the old project's `UiPreferences`).
- Both values are included in the app backup payload so a restore preserves
  the user's theme.
- The palette is stored by **stable id**, not by index — adding/removing
  palettes in a future release doesn't corrupt old preferences.

---

## 10. Open for the design session (DO NOT implement yet)

- Exact hex values + the final curated palette list (names + count).
- Whether the `Anikuta` default palette keeps the emerald seed or moves to a
  different hue family.
- Typography refinements (the old `AnikutaTypography` is near-verbatim M3
  Expressive — owner may want adjustments).
- Shape system refinements (corner radii per shape token).
- Contrast-ratio targets for AMOLED (WCAG AA minimum; AAA aspirational).
- Whether `Monet` is shown disabled or hidden on Android < 12.

Until these are decided, the implementation ships a **single default palette
(`Anikuta`)** with Light/Dark/AMOLED modes, and the Palette picker is a
placeholder. The contract in §3 is final; only the values are pending.

---

## See also

- `DESIGN_LANGUAGE/README.md` — folder map + primary reference policy.
- `DESIGN_LANGUAGE/04-screens/bottom-nav.md` — bottom nav uses
  `surfaceContainer` from the active palette.
- `DESIGN_LANGUAGE/04-screens/watch-page.md` — cover-color override consumer.
- `DOCS/04-design-decisions.md` ADR-015 (custom M3-inspired), ADR-018
  (customizable defaults + simple mode).
- `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/theme/` — the
  prior implementation (read-only reference).
