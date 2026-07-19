# Design 4 — Coffee (AniVerse Notebook) — Color System

All values quoted verbatim from `src/app/globals.css` lines 70–163. The template uses **CSS custom properties** (not Tailwind config) because Tailwind v4 reads tokens from `@theme inline` which bridges to the CSS vars. Light is `:root`, dark is `.dark`.

## Tailwind v4 bridge (globals.css lines 22–64)

`@theme inline` maps every Tailwind color token to a CSS var:

```
--color-background: var(--background);
--color-foreground: var(--foreground);
--color-card: var(--card);
--color-card-foreground: var(--card-foreground);
--color-popover: var(--popover);
--color-primary: var(--primary);
--color-primary-foreground: var(--primary-foreground);
--color-secondary: var(--secondary);
--color-secondary-foreground: var(--secondary-foreground);
--color-muted: var(--muted);
--color-muted-foreground: var(--muted-foreground);
--color-accent: var(--accent);
--color-accent-foreground: var(--accent-foreground);
--color-destructive: var(--destructive);
--color-border: var(--border);
--color-input: var(--input);
--color-ring: var(--ring);
--color-chart-1..5: var(--chart-1..5);
--color-sidebar* (8 vars)
--color-notebook-coffee: var(--notebook-coffee);
--color-notebook-sage: var(--notebook-sage);
--color-notebook-paper: var(--notebook-paper);
```

Only 3 of the 8 notebook tokens are bridged into Tailwind (`coffee`, `sage`, `paper`). The others (`ruled`, `margin`, `shadow`, `shadow-hover`, `latte`, `sticky`, `tape`) remain CSS-var-only and are consumed directly via `var(--notebook-XXX)` in custom utilities.

## Light theme — "Coffee White" (`:root`, lines 70–116)

### Core shadcn tokens

| Token | Hex | RGB | Note |
|---|---|---|---|
| `--background` | `#F0E6D8` | `240 230 216` | Warm cream — page bg |
| `--foreground` | `#2E1A0E` | `46 26 14` | Dark-roast brown — body text |
| `--card` | `#FDF8F2` | `253 248 242` | Paper surface |
| `--card-foreground` | `#2E1A0E` | `46 26 14` | Text on card |
| `--popover` | `#FDF8F2` | `253 248 242` | Popover surface |
| `--popover-foreground` | `#2E1A0E` | `46 26 14` | |
| `--primary` | `#B8653F` | `184 101 63` | Terracotta / coffee accent |
| `--primary-foreground` | `#FFFAF5` | `255 250 245` | Text on primary |
| `--secondary` | `#E8DDD0` | `232 221 208` | Secondary surface |
| `--secondary-foreground` | `#4A3425` | `74 52 37` | Text on secondary |
| `--muted` | `#E5DAD0` | `229 218 208` | Muted surface |
| `--muted-foreground` | `#7A6450` | `122 100 80` | Muted text — warm gray-brown |
| `--accent` | `#DFCAB5` | `223 202 181` | Accent surface |
| `--accent-foreground` | `#4A3425` | `74 52 37` | |
| `--destructive` | `#C44040` | `196 64 64` | Destructive actions |
| `--border` | `#D5C8B8` | `213 200 184` | Default border |
| `--input` | `#D5C8B8` | `213 200 184` | Input border (same as border) |
| `--ring` | `#B8653F` | `184 101 63` | Focus ring (matches primary) |

### Sidebar tokens (lines 96–103)

| Token | Hex |
|---|---|
| `--sidebar` | `#FDF8F2` |
| `--sidebar-foreground` | `#2E1A0E` |
| `--sidebar-primary` | `#B8653F` |
| `--sidebar-primary-foreground` | `#FFFAF5` |
| `--sidebar-accent` | `#E8DDD0` |
| `--sidebar-accent-foreground` | `#4A3425` |
| `--sidebar-border` | `#D5C8B8` |
| `--sidebar-ring` | `#B8653F` |

### Chart palette (lines 91–95)

| Token | Hex | Color |
|---|---|---|
| `--chart-1` | `#B8653F` | Terracotta (matches primary) |
| `--chart-2` | `#6B8E5B` | Sage green |
| `--chart-3` | `#C99545` | Warm gold |
| `--chart-4` | `#5A7D96` | Slate teal |
| `--chart-5` | `#966B94` | Mauve |

### Notebook-specific tokens (lines 105–115)

| Token | Hex / RGBA | Use |
|---|---|---|
| `--notebook-paper` | `#FDF8F2` | `.paper-texture` bg |
| `--notebook-ruled` | `#DDD2C2` | `.notebook-lines` + `.custom-scrollbar` thumb |
| `--notebook-margin` | `#D4A0A0` | `.notebook-margin::before` vertical line |
| `--notebook-shadow` | `rgba(120, 95, 65, 0.15)` | Paper drop shadow |
| `--notebook-shadow-hover` | `rgba(120, 95, 65, 0.25)` | Paper hover shadow |
| `--notebook-coffee` | `#5C3D28` | Deep roast — sticky-note text + card episode strip |
| `--notebook-latte` | `#B8956A` | Latte accent (declared, used lightly) |
| `--notebook-sage` | `#6B8E5B` | Sage accent (matches chart-2) |
| `--notebook-sticky` | `#FFF8CC` | Sticky-note badge bg |
| `--notebook-tape` | `rgba(210, 190, 160, 0.8)` | `.washi-tape::before` strip |

## Dark theme — "Dark Coffee" (`.dark`, lines 118–163)

### Core shadcn tokens

| Token | Hex | RGB | Note |
|---|---|---|---|
| `--background` | `#1A1412` | `26 20 18` | Near-black roast |
| `--foreground` | `#F0E0D0` | `240 224 208` | Warm cream |
| `--card` | `#2A2220` | `42 34 32` | Card surface |
| `--card-foreground` | `#F0E0D0` | `240 224 208` | |
| `--popover` | `#2A2220` | `42 34 32` | |
| `--popover-foreground` | `#F0E0D0` | `240 224 208` | |
| `--primary` | `#D4956A` | `212 149 106` | Latte (lighter than light primary, for contrast on dark bg) |
| `--primary-foreground` | `#1A1412` | `26 20 18` | Inverted — dark text on light latte |
| `--secondary` | `#3A3230` | `58 50 48` | |
| `--secondary-foreground` | `#D4B8A0` | `212 184 160` | |
| `--muted` | `#3A3230` | `58 50 48` | Same as secondary |
| `--muted-foreground` | `#A89080` | `168 144 128` | |
| `--accent` | `#3A3230` | `58 50 48` | Same as secondary/muted |
| `--accent-foreground` | `#D4B8A0` | `212 184 160` | |
| `--destructive` | `#E06060` | `224 96 96` | Lighter red for dark bg |
| `--border` | `#3A3230` | `58 50 48` | |
| `--input` | `#4A4240` | `74 66 64` | Slightly lighter than border |
| `--ring` | `#D4956A` | `212 149 106` | Matches primary |

### Sidebar tokens

| Token | Hex |
|---|---|
| `--sidebar` | `#2A2220` |
| `--sidebar-foreground` | `#F0E0D0` |
| `--sidebar-primary` | `#D4956A` |
| `--sidebar-primary-foreground` | `#1A1412` |
| `--sidebar-accent` | `#3A3230` |
| `--sidebar-accent-foreground` | `#D4B8A0` |
| `--sidebar-border` | `#3A3230` |
| `--sidebar-ring` | `#D4956A` |

### Chart palette (dark)

| Token | Hex |
|---|---|
| `--chart-1` | `#D4956A` (matches primary) |
| `--chart-2` | `#7B9E6B` |
| `--chart-3` | `#D4A55A` |
| `--chart-4` | `#6B8DA6` |
| `--chart-5` | `#A87BA6` |

### Notebook-specific tokens (dark)

| Token | Hex / RGBA | Use |
|---|---|---|
| `--notebook-paper` | `#2A2220` | Same as card |
| `--notebook-ruled` | `#3A3230` | |
| `--notebook-margin` | `#5A3A3A` | Darker red margin |
| `--notebook-shadow` | `rgba(0, 0, 0, 0.25)` | |
| `--notebook-shadow-hover` | `rgba(0, 0, 0, 0.35)` | |
| `--notebook-coffee` | `#A08060` | Lighter roast (for contrast on dark sticky) |
| `--notebook-latte` | `#8A7050` | |
| `--notebook-sage` | `#6B8E5B` | Same as light (sage is theme-agnostic) |
| `--notebook-sticky` | `#3A3220` | Dark sticky-note bg |
| `--notebook-tape` | `rgba(80, 70, 60, 0.5)` | Darker, more transparent tape |

## Hardcoded colors in `page.tsx` (NOT tokens)

These appear as inline Tailwind arbitrary-value classes and bypass the CSS var system entirely:

| Use | Light | Dark | Source line |
|---|---|---|---|
| Header bar bg | `#6B5240` | `#2D2218` | 1686 |
| Mobile menu dropdown bg | `#3D2E1F` | `#1A1410` | 1773 |
| Tooltip override bg | `#2A1F15` | `#2A2218` | 1825, 1838 |
| Card episode strip bg (dark) | `notebook-coffee/90` | `#0F0A08/90` | 924 |
| Hero decorative blurred glow | `bg-primary/10 blur-2xl` | same | 1895 |
| SUB pill | `bg-emerald-500/25` + `text-emerald-300` + `border-emerald-400/30` | same | 928–931 |
| DUB pill | `bg-amber-500/25` + `text-amber-300` + `border-amber-400/30` | same | 933–936 |
| Timeline "today" badge | `bg-emerald-800/15 text-emerald-700 dark:text-emerald-400 border-emerald-700/25` | | 1451 |
| Timeline "tomorrow" badge | `bg-amber-600/15 text-amber-700 dark:text-amber-400 border-amber-600/25` | | 1458 |
| Timeline "later" badge | `bg-blue-800/15 text-blue-700 dark:text-blue-400 border-blue-700/25` | | 1465 |
| Timeline dot "today" | `bg-emerald-700 ring-2 ring-emerald-700/20` | | 1452 |
| Timeline dot "tomorrow" | `bg-amber-600 ring-2 ring-amber-600/20` | | 1459 |
| Timeline dot "later" | `bg-blue-700 ring-2 ring-blue-700/20` | | 1466 |
| Timeline line color "today" | `#047857` | | 1453 |
| Timeline line color "tomorrow" | `#d97706` | | 1460 |
| Timeline line color "later" | `#1d4ed8` | | 1467 |
| Card hover arrow bg | `bg-gray-800/90 dark:bg-gray-900/90` | | 1060, 1081 |
| Bookmark button idle bg | `bg-gray-900/70 text-white/80` | | 977 |
| Hero image hover brightness | `group-hover:brightness-[0.85]` | | 911 |
| Hero gradient overlay bottom | `from-black/60 via-transparent to-transparent` | | 919 |
| Hero rating text | `text-amber-600 dark:text-amber-400` | | 1240, 1930 |

### Genre card gradients (8 genres, page.tsx lines 779–787)

| Genre | Tailwind gradient |
|---|---|
| Action | `from-amber-600 to-orange-500` |
| Romance | `from-rose-400 to-pink-500` |
| Fantasy | `from-violet-400 to-purple-500` |
| Sci-Fi | `from-cyan-500 to-blue-500` |
| Comedy | `from-yellow-500 to-amber-500` |
| Horror | `from-gray-500 to-gray-700` |
| Mystery | `from-emerald-500 to-teal-500` |
| Adventure | `from-orange-500 to-red-500` |

These break from the coffee palette — they're saturated Tailwind preset gradients used only as 40%-opacity washi-tape strips on genre cards. The body of each card stays paper-textured.

## Skeleton shimmer (globals.css lines 357–372)

```css
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.skeleton-shimmer-light {
  background: linear-gradient(90deg, #E5DAD0 25%, #D8CCBC 50%, #E5DAD0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
.dark .skeleton-shimmer-light {
  background: linear-gradient(90deg, #3A3230 25%, #4A4240 50%, #3A3230 75%);
}
```

Uses `--muted` (`#E5DAD0` light / `#3A3230` dark) plus a slightly darker midpoint.

## Global dot-grid overlay (globals.css lines 225–238)

```css
html::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  opacity: 0.4;
  background-image: radial-gradient(circle at 1px 1px, rgba(139, 115, 85, 0.08) 1px, transparent 0);
  background-size: 20px 20px;
}
.dark html::before {
  opacity: 0.06;
  background-image: radial-gradient(circle at 1px 1px, rgba(255, 220, 180, 0.06) 1px, transparent 0);
}
```

The dot color `rgba(139, 115, 85, …)` is a warm brown (close to `--notebook-coffee` `#5C3D28` lightened). Dark-mode dots are `rgba(255, 220, 180, …)` — a warm cream.

## Coffee ring stain (globals.css lines 285–292)

```css
.coffee-ring {
  position: absolute;
  width: 120px;
  height: 120px;
  border: 3px solid rgba(111, 78, 55, 0.08);
  border-radius: 50%;
  pointer-events: none;
}
```

Hardcoded `rgba(111, 78, 55, 0.08)` — close to `--notebook-coffee` `#5C3D28` at 8% alpha.

## Color philosophy summary

1. **Hue family:** warm yellow-red (YR) dominates. Primary `#B8653F` sits at hue 17° (orange-brown). Cream `#F0E6D8` is hue 36° (warm yellow). The only cool colors are reserved for: chart-4 slate teal, chart-5 mauve, the timeline "later" group (blue), and the genre-card washi-tape gradients (which are mostly off-palette decoration).
2. **Saturation:** medium-low. Primary is 49% saturation; muted-foreground 14%; accent 30%. Nothing is neon.
3. **Lightness:** light theme stays in the top half of the L* range (page L≈90, card L≈97, primary L≈49); dark theme compresses into the bottom quarter (page L≈8, card L≈14, primary L≈62 for visibility).
4. **Contrast:** `--foreground` on `--background` light = `#2E1A0E` on `#F0E6D8` → ratio ~11.5:1 (AAA). Dark `#F0E0D0` on `#1A1412` → ~13.8:1 (AAA). Muted `#7A6450` on `#F0E6D8` → ~3.8:1 (passes AA for large text, borderline for small). `--notebook-coffee` `#5C3D28` on `--notebook-sticky` `#FFF8CC` → ~8.4:1 (AAA).
5. **Theme-aware primary inversion:** light primary `#B8653F` (dark text on light bg) becomes dark primary `#D4956A` (dark text on light latte) — i.e. the primary stays "mid-tone orange-brown" but its role flips from "dark accent on cream" to "light accent on dark roast". This is the correct dark-mode handling.

## Compose adaptation

Define a `CoffeeColors` object holding two `ColorScheme` instances + a `CoffeeNotebookColors` data class:

```kotlin
data class CoffeeNotebookColors(
    val paper: Color, val ruled: Color, val margin: Color,
    val shadow: Color, val shadowHover: Color,
    val coffee: Color, val latte: Color, val sage: Color,
    val sticky: Color, val tape: Color,
    val espressoBar: Color, // hardcoded header bg
)

val CoffeeWhiteNotebook = CoffeeNotebookColors(
    paper = Color(0xFFFDF8F2), ruled = Color(0xFFDDD2C2),
    margin = Color(0xFFD4A0A0),
    shadow = Color(0x26785F41), shadowHover = Color(0x40785F41),
    coffee = Color(0xFF5C3D28), latte = Color(0xFFB8956A),
    sage = Color(0xFF6B8E5B),
    sticky = Color(0xFFFFF8CC), tape = Color(0xCCD2BEA0),
    espressoBar = Color(0xFF6B5240),
)

val DarkCoffeeNotebook = CoffeeNotebookColors(
    paper = Color(0xFF2A2220), ruled = Color(0xFF3A3230),
    margin = Color(0xFF5A3A3A),
    shadow = Color(0x40000000), shadowHover = Color(0x59000000),
    coffee = Color(0xFFA08060), latte = Color(0xFF8A7050),
    sage = Color(0xFF6B8E5B),
    sticky = Color(0xFF3A3220), tape = Color(0x8050463C),
    espressoBar = Color(0xFF2D2218),
)

val CoffeeWhiteColorScheme = lightColorScheme(
    background = Color(0xFFF0E6D8), onBackground = Color(0xFF2E1A0E),
    surface = Color(0xFFFDF8F2), onSurface = Color(0xFF2E1A0E),
    primary = Color(0xFFB8653F), onPrimary = Color(0xFFFFFAF5),
    secondary = Color(0xFFE8DDD0), onSecondary = Color(0xFF4A3425),
    tertiary = Color(0xFFDFCAB5), onTertiary = Color(0xFF4A3425),
    error = Color(0xFFC44040), onError = Color(0xFFFFFAF5),
    outline = Color(0xFFD5C8B8), outlineVariant = Color(0xFFD5C8B8),
    /* muted → surfaceVariant; mutedForeground → onSurfaceVariant */
    surfaceVariant = Color(0xFFE5DAD0), onSurfaceVariant = Color(0xFF7A6450),
)

val DarkCoffeeColorScheme = darkColorScheme(
    background = Color(0xFF1A1412), onBackground = Color(0xFFF0E0D0),
    surface = Color(0xFF2A2220), onSurface = Color(0xFFF0E0D0),
    primary = Color(0xFFD4956A), onPrimary = Color(0xFF1A1412),
    secondary = Color(0xFF3A3230), onSecondary = Color(0xFFD4B8A0),
    tertiary = Color(0xFF3A3230), onTertiary = Color(0xFFD4B8A0),
    error = Color(0xFFE06060), onError = Color(0xFF1A1412),
    outline = Color(0xFF3A3230), outlineVariant = Color(0xFF4A4240),
    surfaceVariant = Color(0xFF3A3230), onSurfaceVariant = Color(0xFFA89080),
)
```

The chart palette maps to a separate `CoffeeChartColors` list since Compose chart libraries typically want a list, not separate `ColorScheme` slots.
