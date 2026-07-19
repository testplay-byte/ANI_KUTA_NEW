# Design 1 — Material 3 (Material You)

> The standard, latest Material 3 (Material You) design. Google's official
> design system. No custom reference file — it's the well-documented baseline.

---

## Source

Google Material 3 (M3) / Material You guidelines:
https://m3.material.io/

This is the "default" Android design language. We use the latest M3 components,
dynamic color (Monet on Android 12+), and the M3 type scale.

## Design language & philosophy

- **Material You** — the system adapts to the user (dynamic color from wallpaper,
  adaptive layouts, large-screen support).
- **Accessible by default** — M3 meets WCAG contrast standards out of the box.
- **Motion that informs** — motion communicates hierarchy and state, not decoration.
- **Predictable** — users familiar with Android immediately understand the UI.

## Aesthetics

Clean, modern, standard Android. Rounded corners, tonal color surfaces, clear
elevation hierarchy. Not flashy — familiar and trustworthy.

## Color system

M3 uses a **dynamic color** system (Android 12+):
- **Dynamic color (Monet)** — extracted from the user's wallpaper. Primary,
  secondary, tertiary color roles auto-generated.
- **Static fallback** — a baseline color scheme (our brand seed color) for
  Android < 12 or when dynamic color is disabled.

Color roles: primary, on-primary, primary-container, on-primary-container,
secondary, tertiary, surface, surface-variant, background, error, etc.

> We'll pick a **brand seed color** for ANI-KUTA (e.g. emerald or a custom
> hue) that generates the static fallback scheme.

## Typography

M3 type scale: Display, Headline, Title, Body, Label — each with large/medium/
small variants. Roboto Flex (default) or a custom font.

## Borders & roundness

M3 shape scale: extra-small (4dp), small (8dp), medium (12dp), large (16dp),
extra-large (28dp). Components use specific shapes (e.g. buttons = full/pill,
cards = medium, sheets = extra-large).

## Key UI elements

All standard M3 Compose components: `Button`, `Card`, `TextField`, `Dialog`,
`NavigationBar`, `TopAppBar`, `Scaffold`, `ModalBottomSheet`, `Chip`, `Slider`,
`Switch`, etc. Available via `androidx.compose.material3`.

## Motion

M3 motion patterns: shared element transitions, container transforms, fade
through, shared axis. Easing curves: emphasized, standard, linear.

## Theming

`MaterialTheme(colorScheme = ..., typography = ..., shapes = ...)` in Compose.
Light + dark via `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Monet)
or static `lightColorScheme` / `darkColorScheme`.

## Adaptation for ANI-KUTA

- **Direct M3 Compose usage** — no adaptation needed; it's native Android.
- Dynamic color (Monet) as the default, with our brand seed as the static fallback.
- This is the "safe" design — the one every Android user recognizes.

## What to reuse

Everything — M3 is the baseline. The other 3 designs (Neon, Neobrutalism, Coffee)
are custom themes layered on top of (or replacing) M3 components.

## Open questions

- [ ] Brand seed color for the static fallback scheme?
- [ ] Custom font, or stick with Roboto Flex?
