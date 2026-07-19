# Design 3 — Notebook: Color Palette

All values extracted directly from `src/app/globals.css` `:root` (light) and `.dark` (dark) blocks. Values are raw hex (the CSS uses hex literals; the `hsl(var(--token))` shim in `tailwind.config.ts` is a passthrough — `hsl(#RRGGBB)` is invalid CSS but browsers tolerate the hex string).

## Light — "Acid Cream"

Defined in `globals.css` lines 72–125. Activated by default (no class on `<html>`).

### Base surfaces

| Token | Hex | RGB | Role |
|---|---|---|---|
| `--background` | `#D9D5CC` | `217, 213, 204` | App canvas (warm off-white, slightly darker than cards) |
| `--foreground` | `#1A1A1A` | `26, 26, 26` | Text + borders + default shadow color |
| `--card` | `#EDEAE3` | `237, 234, 227` | Card / popover surface (lighter cream) |
| `--card-foreground` | `#1A1A1A` | `26, 26, 26` | Text on card |
| `--popover` | `#EDEAE3` | `237, 234, 227` | Popover background |
| `--popover-foreground` | `#1A1A1A` | `26, 26, 26` | Popover text |
| `--muted` | `#CDC9C0` | `205, 201, 192` | Muted surface (skeleton base, scrollbar track) |
| `--muted-foreground` | `#5A5A5A` | `90, 90, 90` | Secondary / muted text |
| `--secondary` | `#FEF3C7` | `254, 243, 199` | Amber-100 — secondary highlight |
| `--secondary-foreground` | `#1A1A1A` | `26, 26, 26` | Text on secondary |
| `--accent` | `#FECDD3` | `254, 205, 211` | Pink-100 — accent surface (genre tags) |
| `--accent-foreground` | `#1A1A1A` | `26, 26, 26` | Text on accent |
| `--sidebar` | `#EDEAE3` | `237, 234, 227` | Sidebar background (= card) |
| `--sidebar-foreground` | `#1A1A1A` | `26, 26, 26` | Sidebar text |
| `--sidebar-accent` | `#FEF3C7` | `254, 243, 199` | Sidebar hover/active (= secondary) |
| `--sidebar-accent-foreground` | `#1A1A1A` | `26, 26, 26` | Text on sidebar-accent |

### Action colors

| Token | Hex | RGB | Role |
|---|---|---|---|
| `--primary` | `#2563EB` | `37, 99, 235` | Blue-600 — primary action (CTA, active nav, logo accent) |
| `--primary-foreground` | `#FFFFFF` | `255, 255, 255` | Text on primary |
| `--sidebar-primary` | `#2563EB` | `37, 99, 235` | Active sidebar item background |
| `--sidebar-primary-foreground` | `#FFFFFF` | `255, 255, 255` | Active sidebar item text |
| `--destructive` | `#EF4444` | `239, 68, 68` | Red-500 — destructive actions |
| `--destructive-foreground` | (not set) | — | Uses `--primary-foreground` (`#FFFFFF`) via toast variant |
| `--border` | `#1A1A1A` | `26, 26, 26` | Border color (= foreground) |
| `--input` | `#1A1A1A` | `26, 26, 26` | Input border color (= border) |
| `--ring` | `#2563EB` | `37, 99, 235` | Focus ring color (= primary) |
| `--sidebar-border` | `#1A1A1A` | `26, 26, 26` | Sidebar border |
| `--sidebar-ring` | `#2563EB` | `37, 99, 235` | Sidebar focus ring |

### Chart palette (declared, lightly used)

| Token | Hex | RGB | Notes |
|---|---|---|---|
| `--chart-1` | `#2563EB` | `37, 99, 235` | Blue (= primary) |
| `--chart-2` | `#EC4899` | `236, 72, 153` | Pink-500 |
| `--chart-3` | `#22C55E` | `34, 197, 94` | Green-500 |
| `--chart-4` | `#F59E0B` | `245, 158, 11` | Amber-500 |
| `--chart-5` | `#8B5CF6` | `139, 92, 246` | Violet-500 |

### Neobrutalism-specific tokens

| Token | Value | Role |
|---|---|---|
| `--neo-shadow` | `#1A1A1A` | Default black shadow (= foreground) |
| `--neo-shadow-blue` | `#2563EB` | Blue accent shadow |
| `--neo-shadow-pink` | `#EC4899` | Pink accent shadow |
| `--neo-shadow-green` | `#22C55E` | Green accent shadow |
| `--neo-shadow-yellow` | `#F59E0B` | Yellow accent shadow |
| `--neo-shadow-orange` | `#F97316` | Orange accent shadow |
| `--neo-shadow-purple` | `#8B5CF6` | Purple accent shadow |
| `--neo-shadow-red` | `#EF4444` | Red accent shadow (= destructive) |
| `--neo-border` | `3px solid #1A1A1A` | Standard 3px solid border |
| `--neo-border-radius` | `10px` | Default card radius |
| `--neo-grid-color` | `rgba(26, 26, 26, 0.14)` | Background grid line color (14% black) |
| `--neo-grid-size` | `28px` | Background grid square size |
| `--neo-hover-bg` | `#DBEAFE` | Blue-100 — hover background wash |
| `--neo-active-bg` | `#BFDBFE` | Blue-200 — active/pressed background wash |

### Hover/active tint overrides per color variant (light mode)

Hardcoded in the `.neo-card-{color}:hover` / `:active` rules (not tokenized):

| Variant | Hover bg | Active bg |
|---|---|---|
| blue | `#DBEAFE` (blue-100) | `#BFDBFE` (blue-200) |
| pink | `#FCE7F3` (pink-100) | `#FBCFE8` (pink-200) |
| green | `#DCFCE7` (green-100) | `#BBF7D0` (green-200) |
| yellow | `#FEF9C3` (yellow-100) | `#FEF08A` (yellow-200) |
| purple | `#EDE9FE` (violet-100) | `#DDD6FE` (violet-200) |
| orange | `#FFEDD5` (orange-100) | `#FED7AA` (orange-200) |

### Inline Tailwind badge colors (light mode)

Used directly on `<span className="neo-badge bg-{color}-400 ...">`:

| Badge use | Background | Text | Dark-mode equivalent |
|---|---|---|---|
| NEW | `bg-green-400` `#4ADE80` | black | `bg-green-600` `#16A34A`, text `text-green-50` |
| Rating (star) | `bg-yellow-400` `#FACC15` | black | `bg-yellow-600` `#CA8A04`, text `text-yellow-50` |
| SUB | `bg-green-400` | black | `bg-green-600`, text `text-green-50` |
| DUB | `bg-pink-400` `#F472B6` | black | `bg-pink-600` `#DB2777`, text `text-pink-50` |
| Today | `bg-green-400` | black | `bg-green-600`, text `text-green-50` |
| Tomorrow | `bg-amber-400` `#FBBF24` | black | `bg-amber-600` `#D97706`, text `text-amber-50` |
| Later | `bg-blue-400` `#60A5FA` | white | `bg-blue-600` `#2563EB` |
| Trending / EP count | `bg-primary` `#2563EB` | white | (same) |
| Genre Action | `bg-blue-400` | black | — |
| Genre Romance | `bg-pink-400` | black | — |
| Genre Fantasy | `bg-violet-400` `#A78BFA` | white | — |
| Genre Sci-Fi | `bg-cyan-400` `#22D3EE` | black | — |
| Genre Comedy | `bg-yellow-400` | black | — |
| Genre Horror | `bg-zinc-600` `#52525B` | white | — |
| Genre Mystery | `bg-emerald-400` `#34D399` | black | — |
| Genre Adventure | `bg-orange-400` `#FB923C` | black | — |

### Inline shadow overrides for section icons (light)

Each section header has an 8×8 icon box whose 2×2 hard shadow is color-coded:

| Section | Icon box bg | Shadow color |
|---|---|---|
| Trending Now | `bg-primary` (blue) | `var(--neo-shadow-blue)` |
| Freshly Updated | `bg-primary` (blue) | `var(--neo-shadow-green)` |
| By Genre | `bg-primary` (blue) | `var(--neo-shadow-orange)` |
| Most Popular | `bg-primary` (blue) | `var(--neo-shadow-orange)` |
| Coming Up Next | `bg-primary` (blue) | `var(--neo-shadow-green)` |

(All section icons use `bg-primary` for the box itself — only the shadow color varies per section.)

### Inline shadow overrides for content rows (light)

Each `ContentRow` has a `shadowColor` prop applied to its cards' box-shadows:

| Section | Card shadow color |
|---|---|
| Trending Now | `var(--neo-shadow-blue)` |
| Freshly Updated | `var(--neo-shadow-green)` |
| Most Popular | `var(--neo-shadow-orange)` |

## Dark — "Midnight Raw"

Defined in `globals.css` lines 127–176. Activated by `.dark` class on `<html>`.

### Base surfaces

| Token | Hex | RGB | Role |
|---|---|---|---|
| `--background` | `#2A2A32` | `42, 42, 50` | Cool charcoal canvas |
| `--foreground` | `#E8E8E8` | `232, 232, 232` | Off-white text + borders |
| `--card` | `#363640` | `54, 54, 64` | Card surface (slightly lighter charcoal) |
| `--card-foreground` | `#E8E8E8` | `232, 232, 232` | Text on card |
| `--popover` | `#363640` | `54, 54, 64` | Popover background |
| `--popover-foreground` | `#E8E8E8` | `232, 232, 232` | Popover text |
| `--muted` | `#3E3E48` | `62, 62, 72` | Skeleton base |
| `--muted-foreground` | `#B0B0B8` | `176, 176, 184` | Muted body text |
| `--secondary` | `#40404C` | `64, 64, 76` | Muted bluish-gray |
| `--secondary-foreground` | `#E8E8E8` | `232, 232, 232` | Text on secondary |
| `--accent` | `#5C3348` | `92, 51, 72` | Muted plum |
| `--accent-foreground` | `#E8E8E8` | `232, 232, 232` | Text on accent |
| `--sidebar` | `#32323C` | `50, 50, 60` | Sidebar bg (slightly darker than card) |
| `--sidebar-foreground` | `#E8E8E8` | `232, 232, 232` | Sidebar text |
| `--sidebar-accent` | `#40404C` | `64, 64, 76` | Sidebar hover/active (= secondary) |
| `--sidebar-accent-foreground` | `#E8E8E8` | `232, 232, 232` | Text on sidebar-accent |

### Action colors

| Token | Hex | RGB | Notes |
|---|---|---|---|
| `--primary` | `#3B82F6` | `59, 130, 246` | Blue-500 — brighter than light's blue-600 for visibility |
| `--primary-foreground` | `#FFFFFF` | `255, 255, 255` | Text on primary |
| `--sidebar-primary` | `#3B82F6` | `59, 130, 246` | Active sidebar item bg |
| `--sidebar-primary-foreground` | `#FFFFFF` | `255, 255, 255` | Active sidebar item text |
| `--destructive` | `#FF2D2D` | `255, 45, 45` | Brighter red than light mode |
| `--border` | `#555555` | `85, 85, 85` | Mid-gray border (less harsh than pure white) |
| `--input` | `#555555` | `85, 85, 85` | Input border |
| `--ring` | `#3B82F6` | `59, 130, 246` | Focus ring |
| `--sidebar-border` | `#555555` | `85, 85, 85` | Sidebar border |
| `--sidebar-ring` | `#3B82F6` | `59, 130, 246` | Sidebar focus ring |

### Chart palette (dark)

| Token | Hex | RGB | Lightened variant |
|---|---|---|---|
| `--chart-1` | `#3B82F6` | `59, 130, 246` | Blue-500 |
| `--chart-2` | `#F472B6` | `244, 114, 182` | Pink-400 |
| `--chart-3` | `#4ADE80` | `74, 222, 128` | Green-400 |
| `--chart-4` | `#FBBF24` | `251, 191, 36` | Amber-400 |
| `--chart-5` | `#A78BFA` | `167, 139, 250` | Violet-400 |

### Neobrutalism-specific tokens (dark)

| Token | Value | Difference from light |
|---|---|---|
| `--neo-shadow` | `#1A1A1E` | Slightly bluer black for charcoal cohesion |
| `--neo-shadow-blue` | `#3B82F6` | Brighter (blue-500 vs blue-600) |
| `--neo-shadow-pink` | `#F472B6` | Pink-400 (brighter) |
| `--neo-shadow-green` | `#4ADE80` | Green-400 (brighter) |
| `--neo-shadow-yellow` | `#FBBF24` | Amber-400 (brighter) |
| `--neo-shadow-orange` | `#FB923C` | Orange-400 (brighter) |
| `--neo-shadow-purple` | `#A78BFA` | Violet-400 (brighter) |
| `--neo-shadow-red` | `#FF3333` | Brighter red |
| `--neo-border` | `3px solid #555555` | Gray border (vs near-black in light) |
| `--neo-border-radius` | `10px` | Same |
| `--neo-grid-color` | `rgba(255, 255, 255, 0.08)` | White grid (vs dark grid in light) |
| `--neo-grid-size` | `28px` | Same |
| `--neo-hover-bg` | `#3E4258` | Cool slate (vs blue-100) |
| `--neo-active-bg` | `#4A5068` | Deeper slate (vs blue-200) |

### Hover/active tint overrides per color variant (dark mode)

Hardcoded in `.dark .neo-card-{color}:hover` / `:active` rules:

| Variant | Hover bg | Active bg |
|---|---|---|
| blue | `#2A3A5C` | `#344A6E` |
| pink | `#4C2A3E` | `#5A3448` |
| green | `#2A4232` | `#32503E` |
| yellow | `#44422A` | `#504E34` |
| purple | `#382E4E` | `#42385A` |
| orange | `#4C3224` | `#5A3C2E` |
| red (`neo-input-red` focus) | — | `#4A2222` |
| red (`neo-input-red` focus bg) | — | `#4A2222` |

These are tonally-down versions of each accent — the same hue but at charcoal-appropriate lightness. Each pair follows the pattern: hover is the "highlight" tone, active is a slightly stronger version.

## Adaptive text on hero banner (mobile only)

The `useImageBrightness(src)` hook (`page.tsx` lines 1928–1999) samples the center-bottom 40% of the hero banner image at 80×40 px resolution, computes average luminance `(0.299r + 0.587g + 0.114b)`, and returns `true` if luminance > 140 (light background). The hero text color and shadow then adapt:

| Banner luminance | Title style | Meta style |
|---|---|---|
| Light (>140) | `color: #111`, `textShadow: 0 1px 4px rgba(255,255,255,0.85), 0 0 8px rgba(255,255,255,0.5)` | `color: #333`, `textShadow: 0 1px 3px rgba(255,255,255,0.7)` |
| Dark (≤140) | `color: #fff`, `textShadow: 0 1px 4px rgba(0,0,0,0.7), 0 0 8px rgba(0,0,0,0.4)` | `color: #ddd`, `textShadow: 0 1px 3px rgba(0,0,0,0.6)` |

Note: this adaptive-text system applies **only to mobile** — the desktop hero uses a `neo-card` details panel with `var(--card)` background, so text contrast is deterministic. Mobile hero has no panel wrapper; text sits directly on the banner image.

External banner images are routed through `/api/image-proxy?url=...` to bypass canvas CORS restrictions.

## Sidebar overlay color

```css
.neo-sidebar-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}
```

Used only when the sidebar is open in mobile drawer mode. The only `backdrop-filter: blur()` in the entire design system — every other surface is opaque.

## Hex cheat-sheet (one-page reference)

### Light

```
Background:  #D9D5CC   Card:       #EDEAE3   Popover:    #EDEAE3
Foreground:  #1A1A1A   Muted:      #CDC9C0   MutedText:  #5A5A5A
Primary:     #2563EB   Secondary:  #FEF3C7   Accent:     #FECDD3
Destructive: #EF4444   Border:     #1A1A1A   Ring:       #2563EB

Shadows:     #1A1A1A (black)   #2563EB (blue)   #EC4899 (pink)
             #22C55E (green)   #F59E0B (yellow) #F97316 (orange)
             #8B5CF6 (purple)  #EF4444 (red)

Hover:       #DBEAFE (blue-100)   Active: #BFDBFE (blue-200)
Grid:        rgba(26,26,26,0.14) @ 28px
```

### Dark

```
Background:  #2A2A32   Card:       #363640   Popover:    #363640
Foreground:  #E8E8E8   Muted:      #3E3E48   MutedText:  #B0B0B8
Primary:     #3B82F6   Secondary:  #40404C   Accent:     #5C3348
Destructive: #FF2D2D   Border:     #555555   Ring:       #3B82F6

Shadows:     #1A1A1E (black)   #3B82F6 (blue)   #F472B6 (pink)
             #4ADE80 (green)   #FBBF24 (yellow) #FB923C (orange)
             #A78BFA (purple)  #FF3333 (red)

Hover:       #3E4258 (slate)    Active: #4A5068 (deeper slate)
Grid:        rgba(255,255,255,0.08) @ 28px
```

## Compose mapping (recommended)

```kotlin
data class NeoColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val popover: Color,
    val popoverForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val sidebar: Color,
    val sidebarForeground: Color,
    // Neobrutalism accents
    val neoShadow: Color,
    val neoShadowBlue: Color,
    val neoShadowPink: Color,
    val neoShadowGreen: Color,
    val neoShadowYellow: Color,
    val neoShadowOrange: Color,
    val neoShadowPurple: Color,
    val neoShadowRed: Color,
    val neoHoverBg: Color,
    val neoActiveBg: Color,
    val neoGridColor: Color,
) {
    companion object {
        fun light() = NeoColors(
            background = Color(0xFFD9D5CC),
            foreground = Color(0xFF1A1A1A),
            card = Color(0xFFEDEAE3),
            cardForeground = Color(0xFF1A1A1A),
            popover = Color(0xFFEDEAE3),
            popoverForeground = Color(0xFF1A1A1A),
            muted = Color(0xFFCDC9C0),
            mutedForeground = Color(0xFF5A5A5A),
            primary = Color(0xFF2563EB),
            primaryForeground = Color(0xFFFFFFFF),
            secondary = Color(0xFFFEF3C7),
            secondaryForeground = Color(0xFF1A1A1A),
            accent = Color(0xFFFECDD3),
            accentForeground = Color(0xFF1A1A1A),
            destructive = Color(0xFFEF4444),
            border = Color(0xFF1A1A1A),
            input = Color(0xFF1A1A1A),
            ring = Color(0xFF2563EB),
            sidebar = Color(0xFFEDEAE3),
            sidebarForeground = Color(0xFF1A1A1A),
            neoShadow = Color(0xFF1A1A1A),
            neoShadowBlue = Color(0xFF2563EB),
            neoShadowPink = Color(0xFFEC4899),
            neoShadowGreen = Color(0xFF22C55E),
            neoShadowYellow = Color(0xFFF59E0B),
            neoShadowOrange = Color(0xFFF97316),
            neoShadowPurple = Color(0xFF8B5CF6),
            neoShadowRed = Color(0xFFEF4444),
            neoHoverBg = Color(0xFFDBEAFE),
            neoActiveBg = Color(0xFFBFDBFE),
            neoGridColor = Color(26, 26, 26, alpha = 0.14f),
        )

        fun dark() = NeoColors(
            background = Color(0xFF2A2A32),
            foreground = Color(0xFFE8E8E8),
            card = Color(0xFF363640),
            cardForeground = Color(0xFFE8E8E8),
            popover = Color(0xFF363640),
            popoverForeground = Color(0xFFE8E8E8),
            muted = Color(0xFF3E3E48),
            mutedForeground = Color(0xFFB0B0B8),
            primary = Color(0xFF3B82F6),
            primaryForeground = Color(0xFFFFFFFF),
            secondary = Color(0xFF40404C),
            secondaryForeground = Color(0xFFE8E8E8),
            accent = Color(0xFF5C3348),
            accentForeground = Color(0xFFE8E8E8),
            destructive = Color(0xFFFF2D2D),
            border = Color(0xFF555555),
            input = Color(0xFF555555),
            ring = Color(0xFF3B82F6),
            sidebar = Color(0xFF32323C),
            sidebarForeground = Color(0xFFE8E8E8),
            neoShadow = Color(0xFF1A1A1E),
            neoShadowBlue = Color(0xFF3B82F6),
            neoShadowPink = Color(0xFFF472B6),
            neoShadowGreen = Color(0xFF4ADE80),
            neoShadowYellow = Color(0xFFFBBF24),
            neoShadowOrange = Color(0xFFFB923C),
            neoShadowPurple = Color(0xFFA78BFA),
            neoShadowRed = Color(0xFFFF3333),
            neoHoverBg = Color(0xFF3E4258),
            neoActiveBg = Color(0xFF4A5068),
            neoGridColor = Color(255, 255, 255, alpha = 0.08f),
        )
    }
}

val LocalNeoColors = staticCompositionLocalOf<NeoColors> {
    error("NeoColors not provided")
}
```

The `LocalNeoColors` should be provided at the root composable, picking `light()` or `dark()` based on the system theme (or ANI-KUTA's user theme preference).
