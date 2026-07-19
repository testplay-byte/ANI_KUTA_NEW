# Color Palette — Dark Neon (ANI-KUTA adaptation)

Every color token, its hex value, its CSS variable, its Tailwind class, and
its Compose `Color` literal. All values quoted from `DESIGN.md` §2 and §19.

---

## Background colors (surfaces)

> Source: `DESIGN.md` §2.1, §19.

| Token | Hex | CSS variable | Tailwind class | Compose `Color` | Usage |
|-------|-----|--------------|----------------|-----------------|-------|
| `bg-base` | `#1e1e24` | `--color-bg-base` | `bg-bg-base` | `Color(0xFF1E1E24)` | Outermost background, body, root container |
| `bg-surface` | `#28282f` | `--color-bg-surface` | `bg-bg-surface` | `Color(0xFF28282F)` | Cards, content panels, stat blocks |
| `bg-sidebar` | `#242430` | `--color-bg-sidebar` | `bg-bg-sidebar` | `Color(0xFF242430)` | Sidebar, navigation panel |
| `bg-elevated` | `#333340` | `--color-bg-elevated` | `bg-bg-elevated` | `Color(0xFF333340)` | Hover states, active items, emphasis backgrounds |

These four tones are the entire dark canvas — a near-black base, two
slightly-lighter surfaces for panels and nav, and an "elevated" tone for
hover/active. The maximum lightness delta across the four is small (about
12% lightness), which is why borders and accent color do most of the
separation work.

---

## Accent colors (neons)

> Source: `DESIGN.md` §2.2, §19.

| Token | Hex | CSS variable | Tailwind class | Compose `Color` | Role | Semantic meaning |
|-------|-----|--------------|----------------|-----------------|------|-------------------|
| `accent-lime` | `#BCFF5F` | `--color-accent-lime` | `bg-accent-lime` / `text-accent-lime` / `border-accent-lime` | `Color(0xFFBCFF5F)` | Primary accent | Success, profit, positive, primary actions, "go" |
| `accent-sky` | `#5FC9FF` | `--color-accent-sky` | `bg-accent-sky` / `text-accent-sky` / `border-accent-sky` | `Color(0xFF5FC9FF)` | Secondary accent | Information, calculation, estimation, neutral-highlight |
| `accent-coral` | `#FF5F7E` | `--color-accent-coral` | `bg-accent-coral` / `text-accent-coral` / `border-accent-coral` | `Color(0xFFFF5F7E)` | Danger accent | Loss, error, deletion, fee cost, "stop/danger" |

### Usage rules (§2.2)

- Lime text on dark backgrounds reads at ~12:1 contrast — well above WCAG
  AAA for normal text. Safe for body text but reserved for emphasis.
- Accent buttons use the accent as background with `bg-base` as the text
  color. (Lime button → dark `#1e1e24` text.) This keeps contrast high
  without darkening the accent.
- Accent backgrounds at low opacity for subtle highlights:
  `bg-accent-lime/10`, `bg-accent-sky/5`, `bg-accent-coral/5`.
- **Never use indigo or blue as primary.**
- **Coral is never a primary action color** — only destructive/negative.

### ANI-KUTA accent mapping

| Concept | Accent |
|---------|--------|
| New episode available, completed, "watch now" CTA | Lime |
| Currently watching, airing today, secondary CTA | Sky |
| Delete from library, dropped status, error | Coral |
| Everything else | Muted (`text-muted` `#8888a0`) |

---

## Text colors

> Source: `DESIGN.md` §2.3.

| Token | Hex | Opacity equiv. | CSS variable | Tailwind class | Compose `Color` | Usage |
|-------|-----|----------------|--------------|----------------|-----------------|-------|
| `foreground` | `#ffffff` | 100% | `--foreground` | `text-white` | `Color.White` | Headlines, primary values, active labels |
| `text-secondary` | `#c8c8d4` | ~78% | `--color-text-secondary` | `text-text-secondary` | `Color(0xFFC8C8D4)` | Body text, secondary info |
| `text-muted` | `#8888a0` | ~53% | `--color-text-muted` | `text-text-muted` | `Color(0xFF8888A0)` | Labels, captions, tertiary info |
| `text-dim` | `#55556a` | ~33% | `--color-text-dim` | `text-text-dim` | `Color(0xFF55556A)` | Disabled, hint text, decorative |

### Hierarchy rule (§2.3)

- Every **label** uses `text-text-muted` (`#8888a0`).
- Every **value** uses `text-white` (`#ffffff`) or its accent color.
- Never use `text-text-dim` (`#55556a`) for anything the user must read —
  only for decorative or timestamp elements.

### Approximate contrast on `bg-base` `#1e1e24`

| Token | Hex | Approx contrast vs `#1e1e24` | WCAG |
|-------|-----|-------------------------------|------|
| `foreground` | `#ffffff` | ~16:1 | AAA |
| `text-secondary` | `#c8c8d4` | ~12:1 | AAA |
| `text-muted` | `#8888a0` | ~5.3:1 | AA (normal), fail (small) |
| `text-dim` | `#55556a` | ~2.0:1 | fail — decorative only |
| `accent-lime` | `#BCFF5F` | ~13:1 | AAA |
| `accent-sky` | `#5FC9FF` | ~9:1 | AAA |
| `accent-coral` | `#FF5F7E` | ~6.5:1 | AA |

Notes:
- `text-muted` is the workhorse label color. It passes AA for normal-size
  text but **fails for small text** (≤ 11px). For 9–10px micro labels,
  either bump up to `text-secondary` or accept that they're below AA.
- `text-dim` is intentionally below AA — the source says it's for
  decorative/timestamp elements only.
- All three accents pass AA on `bg-base`. Lime and sky pass AAA.

---

## Border colors

> Source: `DESIGN.md` §2.4.

| Pattern | Value | Tailwind class | Compose `Color` | Usage |
|---------|-------|----------------|-----------------|-------|
| Default border | `rgba(255,255,255,0.08)` | `border-white/[0.08]` | `Color.White.copy(alpha = 0.08f)` | Cards, containers, dividers |
| Subtle border | `rgba(255,255,255,0.04)` | `border-white/[0.04]` | `Color.White.copy(alpha = 0.04f)` | Row separators within tables |
| Accent border | `rgba(188,255,95,0.2)` etc. | `border-accent-lime/20` | `Lime.copy(alpha = 0.20f)` | Active/focused states, highlighted cards |
| Strong border | `rgba(255,255,255,0.12)` | `border-white/[0.12]` | `Color.White.copy(alpha = 0.12f)` | Floating popups, elevated overlays |
| Sidebar border | `rgba(255,255,255,0.06)` | `border-white/[0.06]` | `Color.White.copy(alpha = 0.06f)` | Sidebar edges, subtle separators |

### Recommended border widths

- `1px` hairline for all standard borders (cards, dividers, table rows).
- `1px` for accent borders (active state, focused input).
- `2px` for the bottom of an active tab (mobile tab bar) — see §16.2.

---

## Shadow / glow tokens

> Source: `DESIGN.md` §2.5, §19.

| Token | Value | Compose approximation | Usage |
|-------|-------|------------------------|-------|
| `shadow-glow-lime` | `0 0 20px rgba(188,255,95,0.2)` | `Modifier.drawBehind { drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha = 0.2f), Transparent), radius = size.minDimension)) }` clipped to a slightly larger shape | Lime button hover glow |
| `shadow-glow-sky` | `0 0 20px rgba(95,201,255,0.2)` | Same pattern with sky | Sky button hover glow |
| `shadow-glow-coral` | `0 0 20px rgba(255,95,126,0.2)` | Same pattern with coral | Coral emphasis glow |
| `shadow-glow-step` | `rgba(188,255,95,0.125) 0px 0px 12px` | Tighter radial-gradient, 12dp radius, lime at 12.5% alpha | Subtle step glow for icon badges |
| `shadow-2xl` | Tailwind default (`0 25px 50px -12px rgba(0,0,0,0.5)`) | `Modifier.shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))` | Floating popups, modals |

### Compose note on glows

Compose's built-in `Modifier.shadow` is elevation-based and ambient/spot
colored — it cannot produce a true "neon glow" (a colored halo around the
element). To get the neon-glow effect:
1. Wrap the element in a `Box`.
2. In `Modifier.drawBehind` on the box, draw a radial gradient in the
   accent color, slightly larger than the element, fading to transparent.
3. Place the element on top.

This is a known Compose limitation and the standard workaround.

---

## Full `@theme inline` block (§19)

From `DESIGN.md` §19, the complete `globals.css` theme tokens. Useful as a
reference when porting to Compose's `NeonColors` data class:

```css
@theme inline {
  /* Backgrounds */
  --color-bg-base: #1e1e24;
  --color-bg-surface: #28282f;
  --color-bg-sidebar: #242430;
  --color-bg-elevated: #333340;

  /* Accent neon colors */
  --color-accent-lime: #BCFF5F;
  --color-accent-sky: #5FC9FF;
  --color-accent-coral: #FF5F7E;

  /* Text hierarchy */
  --color-text-secondary: #c8c8d4;
  --color-text-muted: #8888a0;
  --color-text-dim: #55556a;

  /* Glow shadows */
  --shadow-glow-lime: 0 0 20px rgba(188, 255, 95, 0.2);
  --shadow-glow-sky: 0 0 20px rgba(95, 201, 255, 0.2);
  --shadow-glow-coral: 0 0 20px rgba(255, 95, 126, 0.2);
  --shadow-glow-step: rgba(188, 255, 95, 0.125) 0px 0px 12px;
}
```

### `:root` semantic tokens (shadcn/ui mapping)

```css
:root {
  --radius: 0.625rem;
  --background: #1e1e24;
  --foreground: #ffffff;
  --card: #28282f;
  --card-foreground: #ffffff;
  --primary: #BCFF5F;
  --primary-foreground: #1e1e24;
  --secondary: #333340;
  --secondary-foreground: #c8c8d4;
  --muted: #333340;
  --muted-foreground: #8888a0;
  --accent: #5FC9FF;
  --accent-foreground: #1e1e24;
  --destructive: #FF5F7E;
  --border: rgba(255, 255, 255, 0.08);
  --input: rgba(255, 255, 255, 0.08);
  --ring: rgba(188, 255, 95, 0.3);
  --chart-1: #BCFF5F;
  --chart-2: #5FC9FF;
  --chart-3: #FF5F7E;
  --chart-4: #c8c8d4;
  --chart-5: #55556a;
  --sidebar: #242430;
  --sidebar-foreground: #c8c8d4;
  --sidebar-primary: #BCFF5F;
  --sidebar-primary-foreground: #1e1e24;
  --sidebar-accent: rgba(188, 255, 95, 0.07);
  --sidebar-accent-foreground: #BCFF5F;
  --sidebar-border: rgba(255, 255, 255, 0.06);
  --sidebar-ring: rgba(188, 255, 95, 0.3);
}
```

This block is shadcn/ui-specific and doesn't map cleanly to Compose Material 3
tokens — we ship our own `NeonColors` data class instead. The values above
are still the source of truth for *what* colors to use; only the wrapping
mechanism changes.

---

## Suggested Compose `NeonColors` data class

Sketch of the Compose-side theme object ANI-KUTA will use. Not final code,
but a 1:1 token mapping from the web design.

```kotlin
@Immutable
data class NeonColors(
    // Backgrounds
    val bgBase: Color = Color(0xFF1E1E24),
    val bgSurface: Color = Color(0xFF28282F),
    val bgSidebar: Color = Color(0xFF242430),
    val bgElevated: Color = Color(0xFF333340),

    // Accents
    val accentLime: Color = Color(0xFFBCFF5F),
    val accentSky: Color = Color(0xFF5FC9FF),
    val accentCoral: Color = Color(0xFFFF5F7E),

    // Text
    val foreground: Color = Color.White,
    val textSecondary: Color = Color(0xFFC8C8D4),
    val textMuted: Color = Color(0xFF8888A0),
    val textDim: Color = Color(0xFF55556A),

    // Borders (white-alpha scale)
    val borderSubtle: Color = Color.White.copy(alpha = 0.04f),
    val borderDefault: Color = Color.White.copy(alpha = 0.08f),
    val borderStrong: Color = Color.White.copy(alpha = 0.12f),
    val borderSidebar: Color = Color.White.copy(alpha = 0.06f),

    // Per-accent borders
    val borderLime: Color = Color(0xFFBCFF5F).copy(alpha = 0.20f),
    val borderSky: Color = Color(0xFF5FC9FF).copy(alpha = 0.20f),
    val borderCoral: Color = Color(0xFFFF5F7E).copy(alpha = 0.20f),

    // Ring (focus)
    val ringLime: Color = Color(0xFFBCFF5F).copy(alpha = 0.30f),
)

@Immutable
data class NeonGlow(
    val lime: Color = Color(0xFFBCFF5F).copy(alpha = 0.20f),
    val sky: Color = Color(0xFF5FC9FF).copy(alpha = 0.20f),
    val coral: Color = Color(0xFFFF5F7E).copy(alpha = 0.20f),
    val step: Color = Color(0xFFBCFF5F).copy(alpha = 0.125f),
    val radius: Dp = 20.dp,
    val stepRadius: Dp = 12.dp,
)
```

These are accessed in composables via a `CompositionLocal` — never hardcoded
inline.

---

## Quick reference cheat sheet (§ end of DESIGN.md)

```
Backgrounds: #1e1e24 (base)  #28282f (surface)  #242430 (sidebar)  #333340 (elevated)
Accents:     #BCFF5F (lime)  #5FC9FF (sky)      #FF5F7E (coral)
Text:        #ffffff (primary) #c8c8d4 (secondary) #8888a0 (muted) #55556a (dim)
Borders:     white/[0.04] (subtle)  white/[0.08] (default)  white/[0.12] (strong)
Glow:        shadow-glow-lime/sky/coral (20px, accent/0.2)  shadow-glow-step (12px, lime/0.125)
```
