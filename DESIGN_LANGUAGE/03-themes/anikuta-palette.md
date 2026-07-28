# Anikuta Palette — #B1F256 (Lime Green)

> The default ANIKUTA color palette, derived from the owner's chosen primary
> color `#B1F256`. This is the starter palette; the user can select others later
> (ADR-015 — customizable).
>
> **Reference structure:** `PROTOTYPE_REFERENCE/Anime_App/.../theme/Color.kt`
> (same M3 role structure, our colors).

## Primary color

```
#B1F256  (RGB: 177, 242, 86)
```

A bright lime green. High luminance — requires dark text on it for contrast.

## Dark theme (default — per owner preference)

### Surface tonal tiers (5 levels, darkening → lightening)

| Role | Hex | Usage |
|---|---|---|
| `background` | `#14111F` | App background (very dark, slight purple tint like prototype) |
| `surface1` | `#1B1729` | Base surface |
| `surface2` | `#221E33` | Elevated surface |
| `surface3` | `#2A2540` | Cards, bottom nav bg (`surfaceVariant`) |
| `surface4` | `#332D4C` | Higher elevation |
| `surface5` | `#3D3656` | Highest elevation, dialogs |

### Text

| Role | Hex | Usage |
|---|---|---|
| `onBackground` / `onSurface` | `#ECE6F5` | Primary text (near-white, slight purple) |
| `onSurfaceVariant` | `#A89EC0` | Secondary text (muted) |
| (subtle) | `#6E6688` | Tertiary text (subtle) |

### M3 color roles (dark theme)

| Role | Hex | Usage |
|---|---|---|
| `primary` | `#B1F256` | Primary actions, active states |
| `onPrimary` | `#1A2E00` | Text/icons on primary (very dark green for contrast) |
| `primaryContainer` | `#4A6B1A` | Active pill bg (bottom nav, day selector) |
| `onPrimaryContainer` | `#D4F5A0` | Text on primaryContainer (light green) |
| `secondary` | `#CCC2DC` | Secondary accent |
| `secondaryContainer` | `#4A4458` | Secondary containers |
| `tertiary` | `#EFB8C8` | Tertiary accent (rarely used) |
| `tertiaryContainer` | `#633B48` | Tertiary containers |
| `error` | `#F2B8B5` | Error states |
| `errorContainer` | `#8C1D18` | Error containers |
| `outline` | `#938F99` | Outlines, dividers |
| `outlineVariant` | `#49454F` | Subtle outlines |

### Functional colors

| Role | Hex | Usage |
|---|---|---|
| `warn` | `#FFCC80` | Scores, warnings (orange) |
| `success` | `#A5D6A7` | Success states (green) |

## Light theme

### Surface tonal tiers

| Role | Hex | Usage |
|---|---|---|
| `background` | `#FEF7FF` | App background (near-white) |
| `surface1` | `#F3EDF7` | Base surface |
| `surface2` | `#EDE7F4` | Elevated surface |
| `surface3` | `#E7E0EB` | Cards, bottom nav bg |
| `surface4` | `#DDD6E4` | Higher elevation |
| `surface5` | `#D0C9DD` | Highest elevation |

### Text

| Role | Hex | Usage |
|---|---|---|
| `onBackground` / `onSurface` | `#1D1B20` | Primary text |
| `onSurfaceVariant` | `#49454F` | Secondary text |
| (subtle) | `#766C8E` | Tertiary text |

### M3 color roles (light theme)

| Role | Hex | Usage |
|---|---|---|
| `primary` | `#5A8C1A` | Primary (darker green for light theme contrast) |
| `onPrimary` | `#FFFFFF` | Text on primary |
| `primaryContainer` | `#D4F5A0` | Active pill bg (light green) |
| `onPrimaryContainer` | `#1A2E00` | Text on primaryContainer |
| `secondary` | `#625B71` | Secondary accent |
| `outline` | `#79747E` | Outlines |
| `outlineVariant` | `#CAC4D0` | Subtle outlines |

## AMOLED theme

Same as dark theme but:
- `background` = `#000000` (pure black)
- `surface1` = `#0A0A0F` (near-black)
- `surface2`–`surface5` = 3-step near-black ramp (`#0C0C0C` → `#131313` → `#1B1B1B`)

## Derivation notes

- `#B1F256` is HSL `(81°, 86%, 64%)` — a bright, saturated lime green.
- `primaryContainer` (dark) = same hue, lower lightness (~30%), lower saturation (~50%).
- `onPrimaryContainer` (dark) = same hue, high lightness (~75%), to read on the dark container.
- Light theme `primary` = same hue, lower lightness (~35%) for contrast on white.
- Surface tiers use a slight purple tint (matching the prototype) for a modern look — this is adjustable.

## Implementation

```kotlin
// In :core:designsystem
val AnikutaDarkColors = darkColorScheme(
    primary = Color(0xFFB1F256),
    onPrimary = Color(0xFF1A2E00),
    primaryContainer = Color(0xFF4A6B1A),
    onPrimaryContainer = Color(0xFFD4F5A0),
    secondary = Color(0xFFCCC2DC),
    // ... etc
    background = Color(0xFF14111F),
    onBackground = Color(0xFFECE6F5),
    surface = Color(0xFF1B1729),
    onSurface = Color(0xFFECE6F5),
    surfaceVariant = Color(0xFF2A2540),
    onSurfaceVariant = Color(0xFFA89EC0),
    // ... etc
)
```

## Customization

The palette is the DEFAULT. Per ADR-015, the user can select other palettes.
The `AnikutaPalette` data class (in `:core:designsystem`) holds the palette;
swapping it swaps all colors app-wide via `MaterialTheme(colorScheme = ...)`.

Future palettes (TBD): the owner may add more. The structure supports any number.
