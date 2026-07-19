# Design 3 — Notebook

Bold, raw **Neobrutalism** design system for an anime browsing app — thick black borders, hard-edge zero-blur shadows, saturated color accents, uppercase heavy type, and a faint grid-pattern canvas. Adapted from the AniVerse Next.js template (zip: `NOTEBOOK_TEMPLATE.zip`).

> Note on naming: the source zip is called "Notebook", the unzipped folder is `aniverse-template/`, and the actual code (`globals.css` header, README, metadata) describes itself as **"AniVerse — Neobrutalism Design System v2"** with the tagline *"Raw. Bold. Unapologetic."* There is **no lined-paper / notebook aesthetic** in the code — the word "notebook" only appears as an image folder name (`public/images/notebook/`). The design language is unambiguously **Neobrutalism**. We keep the "Notebook" name as the internal design ID (matching the zip) but document the real visual identity.

## Source

- Original: `NOTEBOOK_TEMPLATE.zip` → unzipped at `/home/z/my-project/upload/notebook-extracted/aniverse-template/`.
- Template identity: **AniVerse** (`package.json` name = `aniverse`, version `0.2.0`).
- Stack (web): Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS 4 + Framer Motion 12 + Radix UI primitives (tooltip, toast) + shadcn/ui (style: `new-york`, baseColor: `neutral`) + Lucide icons + tailwindcss-animate + tw-animate-css.
- Target stack (us): Android + Jetpack Compose. We are **adapting** this design language to Compose — we do not port the React code.

## Design language & philosophy

Extracted from the CSS header comment in `src/app/globals.css` (verbatim):

> AniVerse — Neobrutalism Design System v2
> Raw. Bold. Unapologetic.
>
> Neobrutalism core principles:
> - Thick black/white borders (3px solid)
> - Hard-edge box shadows with ZERO blur (e.g., `4px 4px 0 #000`)
> - Bright, saturated accent colors
> - Active/pressed states: shadow shrinks + translate(x,y) + bg color change
> - Bold, heavy typography (uppercase, black weight)
> - Grid-pattern background for that raw canvas feel
> - Physical 3D feel through layered shadows
> - Hover: color-shift + shadow-grow + lift
> - Click/tap: color-burst + shadow-shrink + press

The two named palettes are:
- Light mode: **"Acid Cream"** — warm off-white background, black borders, colored shadows.
- Dark mode: **"Midnight Raw"** — deep charcoal background, gray borders, colored accents pop.

This is not a clean, quiet, lined-paper design — it is loud, tactile, and physical. Surfaces feel like cut-out paper or sticker art that lifts on hover and presses down on tap. Each card carries a colored hard shadow (blue / pink / green / yellow / orange / purple / red) that doubles as a category accent.

## Aesthetics

- **Mood:** raw, bold, unapologetic, tactile, playful-but-aggressive.
- **Depth model:** physical — every surface has a real offset shadow you can "press into". No soft elevation, no glassmorphism, no gradients-as-decoration.
- **Background:** solid color + a 28px square grid pattern overlaid (`linear-gradient` cross-hatch), so the canvas always reads as graph paper.
- **Shape language:** mostly rounded rectangles (radius 6–10px), but with thick 3px outlines that make them read as drawn outlines rather than soft cards. Timeline dots are 3px-radius squares, not circles — small but deliberate.
- **Type:** uppercase + black/extrabold weight + tight tracking + `textShadow` doubling (drop-shadow on the logo, accent-shadow on section icons).
- **Color use:** saturated. The base surfaces are warm-neutral; the color comes from the hard shadows and a small number of badges / tags.

## Color system

Extracted from `src/app/globals.css` `:root` (light) and `.dark` (dark). All values are raw hex (the CSS uses hex directly, not oklch). Tailwind config wires these as `hsl(var(--token))` shims — but the values stored in the CSS variables are hex (e.g. `#2563EB`), so `hsl()` wrapping is effectively a no-op passthrough. We treat them as hex.

### Light — "Acid Cream"

| Token | Hex | Role |
|---|---|---|
| `--background` | `#D9D5CC` | Warm off-white canvas (slightly darker than the cards) |
| `--foreground` | `#1A1A1A` | Near-black text + borders |
| `--card` | `#EDEAE3` | Slightly lighter cream for cards / popovers |
| `--card-foreground` | `#1A1A1A` | Text on card |
| `--popover` | `#EDEAE3` | Popover background |
| `--popover-foreground` | `#1A1A1A` | Popover text |
| `--primary` | `#2563EB` | Blue-600 — primary action color |
| `--primary-foreground` | `#FFFFFF` | Text on primary |
| `--secondary` | `#FEF3C7` | Amber-100 — secondary highlight (sidebar accent) |
| `--secondary-foreground` | `#1A1A1A` | Text on secondary |
| `--muted` | `#CDC9C0` | Muted surface (skeleton base) |
| `--muted-foreground` | `#5A5A5A` | Muted body text |
| `--accent` | `#FECDD3` | Pink-100 — accent surface (genre tags) |
| `--accent-foreground` | `#1A1A1A` | Text on accent |
| `--destructive` | `#EF4444` | Red-500 — destructive actions |
| `--border` | `#1A1A1A` | Borders = foreground (neobrutalism) |
| `--input` | `#1A1A1A` | Input border color |
| `--ring` | `#2563EB` | Focus ring color |
| `--chart-1` | `#2563EB` | Blue |
| `--chart-2` | `#EC4899` | Pink |
| `--chart-3` | `#22C55E` | Green |
| `--chart-4` | `#F59E0B` | Amber |
| `--chart-5` | `#8B5CF6` | Purple |

### Dark — "Midnight Raw"

| Token | Hex | Role |
|---|---|---|
| `--background` | `#2A2A32` | Deep cool charcoal |
| `--foreground` | `#E8E8E8` | Off-white text + borders |
| `--card` | `#363640` | Slightly lighter charcoal card |
| `--card-foreground` | `#E8E8E8` | Text on card |
| `--popover` | `#363640` | Popover background |
| `--popover-foreground` | `#E8E8E8` | Popover text |
| `--primary` | `#3B82F6` | Blue-500 — slightly brighter than light mode |
| `--primary-foreground` | `#FFFFFF` | Text on primary |
| `--secondary` | `#40404C` | Muted bluish-gray |
| `--secondary-foreground` | `#E8E8E8` | Text on secondary |
| `--muted` | `#3E3E48` | Skeleton base |
| `--muted-foreground` | `#B0B0B8` | Muted body text |
| `--accent` | `#5C3348` | Muted plum — accent surface |
| `--accent-foreground` | `#E8E8E8` | Text on accent |
| `--destructive` | `#FF2D2D` | Brighter red for dark mode |
| `--border` | `#555555` | Muted gray border (less harsh than pure white) |
| `--input` | `#555555` | Input border |
| `--ring` | `#3B82F6` | Focus ring |
| `--chart-1..5` | `#3B82F6` / `#F472B6` / `#4ADE80` / `#FBBF24` / `#A78BFA` | Lightened accent variants |

### Neobrutalism-specific tokens (shadow colors)

These are the heart of the system — every card/button has a hard shadow drawn in one of these accent colors. Defined in `:root` and overridden in `.dark`.

| Token | Light hex | Dark hex | Used for |
|---|---|---|---|
| `--neo-shadow` | `#1A1A1A` | `#1A1A1E` | Default black/charcoal shadow |
| `--neo-shadow-blue` | `#2563EB` | `#3B82F6` | Trending, hero, primary |
| `--neo-shadow-pink` | `#EC4899` | `#F472B6` | Romance genre, DUB badge |
| `--neo-shadow-green` | `#22C55E` | `#4ADE80` | Freshly Updated, SUB badge, "Today" |
| `--neo-shadow-yellow` | `#F59E0B` | `#FBBF24` | Rating badge, Comedy |
| `--neo-shadow-orange` | `#F97316` | `#FB923C` | Most Popular, Adventure |
| `--neo-shadow-purple` | `#8B5CF6` | `#A78BFA` | Fantasy genre |
| `--neo-shadow-red` | `#EF4444` | `#FF3333` | Navbar search/button accents |

### Neobrutalism interaction tokens

| Token | Light | Dark | Meaning |
|---|---|---|---|
| `--neo-hover-bg` | `#DBEAFE` (blue-100) | `#3E4258` (cool slate) | Background wash on hover |
| `--neo-active-bg` | `#BFDBFE` (blue-200) | `#4A5068` | Background wash on press |
| `--neo-border` | `3px solid #1A1A1A` | `3px solid #555555` | Standard element border |
| `--neo-border-radius` | `10px` | `10px` | Default card radius |
| `--neo-grid-color` | `rgba(26,26,26,0.14)` | `rgba(255,255,255,0.08)` | Background grid line color |
| `--neo-grid-size` | `28px` | `28px` | Background grid square size |

### Badges — direct Tailwind colors (not tokens)

Badges use raw Tailwind colors inline:
- `NEW` → `bg-green-400 text-black` (dark: `bg-green-600 text-green-50`)
- Rating → `bg-yellow-400 text-black` (dark: `bg-yellow-600 text-yellow-50`)
- `SUB` → `bg-green-400 text-black`
- `DUB` → `bg-pink-400 text-black`
- "Today" → `bg-green-400 text-black`
- "Tomorrow" → `bg-amber-400 text-black`
- "Later" → `bg-blue-400 text-white`

## Typography

From `src/app/layout.tsx`:

- **Sans** (body, UI): `Geist` (Google Fonts) → exposed as `--font-geist-sans`, mapped to `--font-sans`.
- **Mono** (rare use): `Geist_Mono` → `--font-geist-mono`.
- A `--font-hand` variable is declared in `@theme inline` (mapped to `--font-caveat`, i.e. the Caveat handwriting font) but **no Caveat import is present in `layout.tsx`** — the variable is declared but unused. Treat as a TODO / available slot for an accent handwritten font (could be useful for ANI-KUTA "journal" accents).

Type patterns used in `page.tsx`:

| Role | Class | Approx size / weight |
|---|---|---|
| Logo / hero title | `font-black uppercase tracking-tight` | 22–40px / 900 |
| Section title | `font-extrabold uppercase tracking-wide` | 18–20px / 800 |
| Card title | `font-black uppercase tracking-tight` | 13px / 900 |
| Body | `text-sm font-medium` / `text-xs font-bold uppercase` | 12–14px |
| Meta text | `text-[11px] font-bold uppercase tracking-wide text-muted-foreground` | 11px / 700 |
| Badge | `font-extrabold uppercase tracking-[0.04em]` | 10px / 800 |
| Tiny sub-labels (sub/dub/ep counts) | `text-[9px] font-extrabold tracking-wide` | 9px / 800 |
| Sub-label inside badge | `text-[6px] font-bold uppercase tracking-wider` | 6px / 700 |

Letter-spacing is tight on big headings (`-tight`, `-0.02em` on buttons) and slightly open on small uppercase labels (`0.04em` on badges, `tracking-wider` on micro-labels). Anti-aliasing is forced via `-webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale; text-rendering: optimizeLegibility;` in `@layer base`. Heavy weights (`font-bold`/`extrabold`/`black`/`semibold`) get `-webkit-text-stroke: 0` to avoid synthetic-stroke artifacts.

Text-shadow effects:
- Logo: `textShadow: "2px 2px 0px var(--neo-shadow-blue)"` — a hard offset shadow in accent color, not a blur.
- Hero mobile title: adaptive — `0 1px 4px rgba(0,0,0,0.7), 0 0 8px rgba(0,0,0,0.4)` for dark banners; reversed for light (driven by `useImageBrightness` hook sampling banner pixels).

## Borders & roundness

Defined as CSS variables, surfaced to Tailwind via `borderRadius` in `tailwind.config.ts`:

| Token | Value | Tailwind alias |
|---|---|---|
| `--radius` | `0.5rem` (8px) | base |
| `--radius-sm` | `calc(var(--radius) - 4px)` = 4px | `rounded-sm` |
| `--radius-md` | `calc(var(--radius) - 2px)` = 6px | `rounded-md` |
| `--radius-lg` | `var(--radius)` = 8px | `rounded-lg` |
| `--radius-xl` | `calc(var(--radius) + 4px)` = 12px | `rounded-xl` |
| `--neo-border-radius` | `10px` | (used by `.neo-card` family) |

Effective radii in the neobrutalism classes:
- `.neo-card`, `.neo-card-*`, `.neo-card-anime` → `10px` (`var(--neo-border-radius)`)
- `.neo-btn`, `.neo-btn-*` → `8px`
- `.neo-badge` → `6px`
- `.neo-input`, `.neo-input-red` → `8px`
- `.neo-sidebar-item` → `8px`
- `.neo-nav-btn` → `6px`
- Scroll arrows → `6px`
- Genre card icon box → `5px`
- Timeline dots → `3px` (square)
- Hero carousel active dot → `3px`

Border widths (all `solid`, color = `--border`):
- Default `.neo-*` elements: **3px** (`--neo-border`)
- Anime card override (`.neo-card-anime`): **3.5px**
- Sidebar border-right: **3px**
- Footer top border: **3px**
- Timeline vertical line: **3px** `bg-foreground/20`
- Badges: **2px**
- Skeleton placeholder: **2px dashed**
- Card hover scroll arrows: **2.5px**
- Divider lines inside sidebar/footer: **2px** (`border-foreground/15`)

## Surfaces & elevation

The neobrutalism elevation model is **hard-offset shadows, zero blur**. There are no soft drop-shadows anywhere. Standard shadow recipe: `{N}px {N}px 0px {color}`.

| Element | Resting shadow | Hover shadow | Active/pressed shadow |
|---|---|---|---|
| `.neo-card` | `4px 4px 0 var(--neo-shadow)` | `8px 8px 0 var(--neo-shadow-blue)` | `1px 1px 0 var(--neo-shadow-blue)` |
| `.neo-card-anime` | `5px 5px 0 var(--neo-shadow)` | `9px 9px 0 var(--neo-shadow-blue)` | `2px 2px 0 var(--neo-shadow-blue)` |
| `.neo-card-{color}` | `4px 4px 0 var(--neo-shadow-{color})` | `8px 8px 0 var(--neo-shadow-{color})` | `1px 1px 0 var(--neo-shadow-{color})` |
| `.neo-btn` | `3px 3px 0 var(--neo-shadow)` | `5px 5px 0 var(--neo-shadow)` | `1px 1px 0 var(--neo-shadow)` |
| `.neo-btn-{color}` | `3px 3px 0 var(--neo-shadow-{color})` | `5px 5px 0 var(--neo-shadow-{color})` | `1px 1px 0 var(--neo-shadow-{color})` |
| Hero desktop details panel | `8px 8px 0 var(--neo-shadow-blue)` | (same) | (same) |
| Hero banner section | (uses `.neo-card-anime` baseline) | (same) | (same) |
| Navbar header | `4px 4px 0 var(--neo-shadow-red)` | (static) | (static) |
| Timeline container | `4px 4px 0 var(--neo-shadow)` | (static) | (static) |
| Sidebar (`.neo-sidebar`) | `4px 0 0 var(--neo-shadow)` (right-side hard shadow) | (static) | (static) |
| Footer | `0 -4px 0 var(--neo-shadow)` (top-side hard shadow) | (static) | (static) |
| Logo box, section header icon | `2px 2px 0 var(--neo-shadow-{color})` | (static) | (static) |

**Interaction transform model** (consistent across all `.neo-*` interactive elements):

- Hover: `transform: translate(-2px, -2px)` (the element lifts up-left, shadow grows to fill the gap → reads as the surface rising off the page).
- Active: `transform: translate(3px, 3px)` (the element presses down-right into where the shadow was → reads as physical depression).
- Buttons use the same pattern with smaller deltas: `-1px/-1px` hover, `+2px/+2px` active.
- Transitions: `0.1s ease` for buttons (snappy), `0.15s ease` for cards (slightly softer), `0.3s cubic-bezier(0.34, 1.56, 0.64, 1)` for sidebar drawer (springy overshoot).

**Background canvas:** `body` has a fixed `background-color: var(--background)` plus a grid pattern:

```css
background-image:
  linear-gradient(var(--neo-grid-color) 1px, transparent 1px),
  linear-gradient(90deg, var(--neo-grid-color) 1px, transparent 1px);
background-size: var(--neo-grid-size) var(--neo-grid-size);  /* 28px squares */
```

This grid is always visible behind everything — gives the canvas the "raw graph-paper" feel without ever drawing actual notebook lines.

## Key UI elements

All elements defined in `src/app/globals.css` as `.neo-*` classes and consumed inline by components in `src/app/page.tsx`.

### `.neo-card` — base surface

- White/cream surface (`var(--card)`) + 3px black border + 10px radius + 4px hard shadow.
- 7 color variants: `.neo-card`, `.neo-card-blue`, `-pink`, `-green`, `-yellow`, `-purple`, `-orange`. Each swaps the shadow color and the hover/active background tint (e.g. blue variant hovers to `#DBEAFE`, presses to `#BFDBFE`).
- Used for: genre cards, popups, hero details panel.

### `.neo-card-anime` — anime cover card

- Special variant with **3.5px border** (thicker than other cards) and **5px resting shadow**.
- Hover lifts to 9px shadow + `translate(-2px, -2px)`; active presses to 2px shadow + `translate(3px, 3px)`.
- Internal layout (from `AnimeCard` component in `page.tsx`):
  - `aspect-[5/7]` cover image (object-cover).
  - Top-left: `NEW` badge (`.neo-badge bg-green-400`).
  - Top-right: rating badge (`.neo-badge bg-yellow-400` + Star icon).
  - Bottom overlay: sub/dub/ep count chips with `bg-black/70` gradient + tiny 9px text on tinted pills (emerald-500/25 for sub, rose-500/25 for dub, white/10 for ep count).
  - Below image: title (`font-black uppercase text-[13px] tracking-tight`) + genre subtitle (`text-[11px] font-bold text-muted-foreground`).
- Widths by breakpoint: 140px mobile / 155px sm / 168px md.

### `.neo-btn` — bold button

- 3px border, 8px radius, `3px 3px 0` resting shadow, `font-weight: 800; text-transform: uppercase; letter-spacing: -0.02em`.
- Variants: `.neo-btn` (black shadow), `.neo-btn-blue`, `.neo-btn-red`.
- Red variant: hover swaps background to `--destructive` red and text to white — used for navbar search submit.

### `.neo-badge` — small tag

- 2px border, 6px radius, 10px uppercase 800-weight text, `padding: 2px 8px`, `letter-spacing: 0.04em`.
- Background is set inline per use (`bg-primary`, `bg-green-400`, `bg-yellow-400`, `bg-pink-400`, `bg-amber-400`, `bg-blue-400`...).
- Often paired with a Lucide icon at `w-2.5 h-2.5` or `w-3 h-3`.

### `.neo-section-header` — section title

- A 4px wide vertical accent bar (`::before` pseudo-element, `var(--primary)` background) with a 2px blue hard shadow on the right edge (`box-shadow: 2px 0 0 var(--neo-shadow-blue)`).
- Pairs with an 8×8px icon box (`bg-primary text-primary-foreground border-2 border-foreground rounded-[6px]`) that itself carries a `2px 2px 0 var(--neo-shadow-{color})` accent shadow.
- Title text: `font-extrabold uppercase tracking-wide text-lg md:text-xl`.

### `.neo-input` / `.neo-input-red` — text input

- Card-colored background + 3px border + 8px radius.
- Default shadow is transparent (zero blur, zero offset — invisible).
- On focus: shadow grows to `3px 3px 0 var(--neo-shadow-{color})` + background shifts to blue tint (`#DBEAFE` light / `#2A3A5C` dark).
- Red variant uses `var(--neo-shadow-red)` and a `#FEE2E2` light / `#4A2222` dark focus tint — used for the navbar search input.

### `.neo-sidebar` — navigation drawer

- `var(--sidebar)` background + 3px right border + `4px 0 0 var(--neo-shadow)` right-side hard shadow.
- Slide transition: `transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)` (springy overshoot).
- Mobile overlay: `rgba(0,0,0,0.5)` + `backdrop-filter: blur(4px)`.
- Items: `.neo-sidebar-item` — 8px radius, 800-weight uppercase 13px text, transparent 2px resting border. Hover fills border + adds `2px 2px 0 var(--neo-shadow)` shadow + `translate(-1px, -1px)`. Active state fills background with `--primary` + `2px 2px 0 var(--neo-shadow)` shadow.
- Resizable drag handle on the right edge (4px hit area, 2px visual line that widens to 1.5px and turns blue on hover). Min width 64px (collapsed, icons-only), max 280px, default 220px.

### `.neo-skeleton` — loading placeholder

- Dashed border (2px dashed `var(--border)`) instead of solid — "raw, unfinished look" per the CSS comment.
- Pulse animation `neo-pulse` (1.2s ease-in-out infinite) on opacity.
- Diagonal shimmer overlay (`linear-gradient 105deg` at 250% background-size, animated `neo-shimmer` 2s) — a soft white sweep across the surface. Dark mode uses dimmer white overlays (0.06–0.10 alpha vs 0.12–0.18 in light).

### `.neo-scrollbar` — thick scrollbar

- 8px width/height.
- Track: `var(--muted)` + 2px border + 4px radius.
- Thumb: `var(--primary)` + 2px border + 4px radius.

### Hover popup (anime card / timeline entry)

- Portaled to `document.body`, `position: fixed`, `z-[9999]`.
- Uses `.neo-card-anime` styling with a 6px colored hard shadow.
- Spring entrance: `stiffness: 400, damping: 18, mass: 0.9` (Framer Motion), scale from `0.85 × 0.3` (asymmetric — pops open more in Y than X) → `1 × 1`.
- Transform origin: `bottom center` if shown above the card, `top center` if shown below.
- Position algorithm (`updatePopupPos`): smart-clamps to viewport, flips above↔below based on space, left/right-aligns the popup to whichever card corner is closer to the screen center.

### Timeline (Next Releasing section)

- Container: card with `3px border-foreground`, `4px 4px 0 var(--neo-shadow)`, `var(--neo-border-radius)` radius.
- Vertical line: 3px wide, `bg-foreground/20`.
- Dots: 12px mobile / 14px md, **3px-radius squares** (not circles), 2px border, color-coded by group (green = Today, amber = Tomorrow, blue = Later), each with `1px 1px 0 var(--neo-shadow)` micro-shadow.
- Group separators: 2px dashed `border-foreground/15`.
- Entry card: `.neo-card` with `flex items-center gap-3`, thumbnail `48×68` mobile / `42×60` md, title `text-[13px] font-black uppercase`, badges below.
- Grayscale treatment: today = `grayscale-0`, tomorrow = `grayscale-[50%]`, later = `grayscale` (full). Hover on any entry clears grayscale.

## Motion & animations

Two motion layers: (1) CSS keyframes in `globals.css` for ambient effects, (2) Framer Motion variants in `page.tsx` for component entry/interaction.

### CSS keyframes (`globals.css`)

| Name | Duration | Curve | Purpose |
|---|---|---|---|
| `neo-pulse` | 1.2s, ease-in-out, infinite | opacity 1 → 0.5 → 1 | Skeleton base pulsing |
| `neo-shimmer` | 2s, ease-in-out, infinite | background-position 200% → -200% | Skeleton diagonal light sweep |
| `neo-pop-in` | 0.35s, `cubic-bezier(0.34, 1.56, 0.64, 1)` | scale 0.8 + rotate(-2deg) → 1.05 + rotate(0.5deg) → 1 | Pop-in bounce (utility class `.neo-pop-in`) |
| `neo-shake` | (defined but not invoked) | rotate -1.5deg → 1.5deg | Shake-on-hover hook (unused) |
| `neo-slide-in-right` | (defined but not invoked) | translateX(20px) + opacity 0 → 0 + 1 | Slide-in hook (unused) |

### Framer Motion variants (`page.tsx`)

```ts
// Staggered slide-up with slight rotation — used by cards & genre cards
const neoSlideUp = {
  hidden:  { opacity: 0, y: 24, rotate: -1 },
  visible: (i) => ({
    opacity: 1, y: 0, rotate: 0,
    transition: { duration: 0.25, ease: [0.34, 1.56, 0.64, 1], delay: i * 0.05 },
  }),
};

// Section-level pop — used by ContentRow / GenreSection / NextReleasingSection
const neoSectionPop = {
  hidden:  { opacity: 0, scale: 0.95 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.3, ease: [0.34, 1.56, 0.64, 1] } },
};
```

Signature easing curve throughout: **`cubic-bezier(0.34, 1.56, 0.64, 1)`** — a spring-like overshoot. Used by sidebar drawer, card entrances, section entrances, hero text, popups, sidebar items.

### Per-component motion patterns

| Component | Pattern |
|---|---|
| Navbar header | Slide-down on mount: `y: -20 → 0`, 0.3s spring |
| Hero banner background | Cross-fade between slides: `opacity 0 → 1`, 0.6s easeInOut, `AnimatePresence mode="wait"` |
| Hero text panel | Slide-up: `y: 20 → 0`, 0.35s spring, keyed per slide |
| Hero (loaded ↔ skeleton) | `AnimatePresence mode="wait"` swap with `y: 12 → 0, scale: 0.98 → 1` |
| Content sections | `initial y: 20 → animate y: 0`, staggered by 0.08s between sections (0, 0.08, 0.16, 0.24, 0.32) |
| Anime card entry | `neoSlideUp` + `whileInView` (viewport margin `-50px`, `once: true`), 50ms stagger |
| Anime card hover | `whileHover: { translateX: -2, translateY: -2 }`, `whileTap: { translateX: 3, translateY: 3 }`, 0.1s |
| Genre card hover | Same hover/tap deltas as anime card |
| Timeline entry | `initial opacity: 0, x: -12 → opacity: 1, x: 0`, 0.25s spring, 40ms stagger |
| Timeline entry hover | `whileHover translateX: -1, translateY: -1`; `whileTap translateX: 2, translateY: 2` |
| Card hover popup | Spring: `stiffness 400, damping 18, mass 0.9`; asymmetric scale-in (0.85 × 0.3 → 1 × 1); `transformOrigin` bottom-center or top-center |
| Timeline popup | `scale 0.95 + y: ±8 → 1 + y: 0`, 0.2s spring |
| Sidebar drawer | `transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)` (CSS transition) |
| Mobile sidebar overlay | `opacity 0 → 1`, 0.2s |
| Hamburger ↔ close button | Animate `width: 36 → 0`, `opacity: 1 → 0` when search is focused |
| Mobile search submit morph | `AnimatePresence mode="wait"` — bell icon (36px square) morphs into Search button (88px wide) via scale/opacity/width transition with 0.15s delay |
| Carousel dots | CSS `transition-all duration-200` — active dot stretches from 3×3 to 8×3 |
| Hero CTA buttons | `whileHover translateX/Y: -1`, `whileTap translateX/Y: +2` |

### Mobile animation suppression

The `useIsMobile()` hook (`src/hooks/use-mobile.ts`) returns `true` under 640px. Components check this and **skip entrance animations on mobile** (`initial="visible"` instead of `initial="hidden"`). Reasoning: scroll-triggered animations on mobile fire unreliably and feel janky; static-on-mount is smoother. We should mirror this in Compose (skip `AnimatedVisibility` on small screens for list/section entrances).

### Reduced motion

Global CSS rule:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

All keyframe animations and CSS transitions collapse to near-instant. Compose equivalent: gate animations on `LocalAccessibilityManager.isReduceMotionEnabled` (or Compose `LocalAccessibilityManager`).

## Adaptation for ANI-KUTA (Android/Compose)

We are not porting React. We extract the design language and rebuild the primitives in Jetpack Compose.

### Color → Compose

Translate every CSS variable to a `Color` constant in a `NeoColors` object. Provide `NeoColors.light()` and `NeoColors.dark()` (mirroring the two named palettes). Surface them via a CompositionLocal `LocalNeoColors`.

| CSS | Compose |
|---|---|
| `--background` `#D9D5CC` | `Color(0xFFD9D5CC)` |
| `--foreground` `#1A1A1A` | `Color(0xFF1A1A1A)` |
| `--card` `#EDEAE3` | `Color(0xFFEDEAE3)` |
| `--primary` `#2563EB` | `Color(0xFF2563EB)` |
| `--secondary` `#FEF3C7` | `Color(0xFFFEF3C7)` |
| `--muted` `#CDC9C0` | `Color(0xFFCDC9C0)` |
| `--accent` `#FECDD3` | `Color(0xFFFECDD3)` |
| `--destructive` `#EF4444` | `Color(0xFFEF4444)` |
| `--neo-shadow-blue` `#2563EB` | `Color(0xFF2563EB)` |
| `--neo-shadow-pink` `#EC4899` | `Color(0xFFEC4899)` |
| `--neo-shadow-green` `#22C55E` | `Color(0xFF22C55E)` |
| `--neo-shadow-yellow` `#F59E0B` | `Color(0xFFF59E0B)` |
| `--neo-shadow-orange` `#F97316` | `Color(0xFFF97316)` |
| `--neo-shadow-purple` `#8B5CF6` | `Color(0xFF8B5CF6)` |
| `--neo-shadow-red` `#EF4444` | `Color(0xFFEF4444)` |
| `--neo-hover-bg` `#DBEAFE` | `Color(0xFFDBEAFE)` |
| `--neo-active-bg` `#BFDBFE` | `Color(0xFFBFDBFE)` |

(Equivalent dark variants — see `colors/README.md` for the full table.)

### Borders & shadows → Compose

Compose's built-in `Modifier.shadow()` uses soft elevation shadows — **wrong for neobrutalism**. Build a custom modifier:

```kotlin
fun Modifier.neoShadow(
    color: Color,
    offsetX: Dp = 4.dp,
    offsetY: Dp = 4.dp,
) = this.drawBehind {
    drawRect(
        color = color,
        topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
        size = size,
    )
}
```

Or use a `graphicsLayer` with a translated duplicate — `drawBehind` is simpler. Pair with a `border(width = 3.dp, color = NeoColors.border)` modifier.

### Radius → Compose

| Tailwind | Compose `RoundedCornerShape` |
|---|---|
| 4px (`rounded-sm`) | `RoundedCornerShape(4.dp)` |
| 6px (`rounded-md`, badges, nav buttons) | `RoundedCornerShape(6.dp)` |
| 8px (buttons, inputs, sidebar items) | `RoundedCornerShape(8.dp)` |
| 10px (`.neo-card` family — `--neo-border-radius`) | `RoundedCornerShape(10.dp)` |
| 12px (`rounded-xl`, hero banner) | `RoundedCornerShape(12.dp)` |

### Typography → Compose

Map Geist to a Compose font family. If we can't bundle Geist, fall back to a similar grotesk (Inter, Manrope). Build a `NeoTypography`:

| Role | Compose TextStyle |
|---|---|
| Logo / hero title | `FontWeight.Black, 22–40.sp, letterSpacing = (-0.5).sp, TextTransform.Upper` |
| Section title | `FontWeight.ExtraBold, 18–20.sp, letterSpacing = 0.5.sp, Upper` |
| Card title | `FontWeight.Black, 13.sp, letterSpacing = (-0.25).sp, Upper` |
| Badge | `FontWeight.ExtraBold, 10.sp, letterSpacing = 0.4.sp, Upper` |
| Body | `FontWeight.Medium, 12–14.sp` |
| Meta | `FontWeight.Bold, 11.sp, letterSpacing = 0.4.sp, Upper` |

Compose's `TextStyle` doesn't have native uppercase — wrap with `String.uppercase()` or build a `TextTransform` shim.

### Motion → Compose

Use `androidx.compose.animation.core.spring`:

```kotlin
val NeoSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,   // ~0.56 overshoot
    stiffness = Spring.StiffnessMediumLow,
)
```

`cubic-bezier(0.34, 1.56, 0.64, 1)` is an overshoot bezier → mirror with `DampingRatioMediumBouncy`. For the popup spring (`stiffness 400, damping 18, mass 0.9`) → use `spring(dampingRatio = 0.55f, stiffness = 1500f)` and tune by feel.

For entrance staggers, use `AnimatedVisibility` with a per-item `launchAnim` delay (`index * 50L`).

### Background grid → Compose

```kotlin
fun Modifier.neoGridPattern(
    color: Color,
    size: Dp = 28.dp,
) = this.drawBehind {
    val px = size.toPx()
    drawRect(color = color, topLeft = Offset(px, 0f), size = Size(1f, size.height))
    // ...repeat for vertical & horizontal lines, or use Path + drawIntoCanvas
}
```

Apply on the root Scaffold background.

### Interactions → Compose

- Hover/press lift: use `Modifier.pointerInput` + `animateFloatAsState` driven by `interactionSource.collectIsPressedAsState()`. Translate the composable by `(-2.dp, -2.dp)` on hover/press-armed and `(+3.dp, +3.dp)` on pressed, while animating the shadow offset inversely (4 → 8 → 1).
- Mobile has no hover — collapse hover state into press-feedback (long-press arms hover, tap = press). The template already does this implicitly by skipping entrance animations on mobile and using `whileTap` for press feedback.

## What to reuse

Strongly reusable for ANI-KUTA:

1. **The full color token system** — both light ("Acid Cream") and dark ("Midnight Raw") palettes, including the 7 neobrutalism accent-shadow colors. Direct hex translation.
2. **The hard-shadow elevation model** — `4px/4px/0` resting, `8px/8px/0` hover, `1px/1px/0` pressed. Maps cleanly to a custom `Modifier.neoShadow`.
3. **Border widths and radii** — 3px borders everywhere, 10px card radius, 8px button/input radius, 6px badge radius.
4. **The 28px background grid** — gives ANI-KUTA a recognizable canvas without being literal lined paper.
5. **Typography hierarchy** — uppercase black/extrabold with tight tracking for titles, slightly open tracking for tiny uppercase labels. We may swap Geist for Inter/Manrope.
6. **Motion vocabulary** — spring overshoot (`cubic-bezier(0.34, 1.56, 0.64, 1)`), 50ms card stagger, asymmetric popup scale, `whileHover` lift + `whileTap` press.
7. **Section header pattern** — 4px accent bar + 8×8 icon box + uppercase extrabold title. Cheap and recognizable.
8. **Badge system** — 2px border, 6px radius, 10px uppercase, color-coded per category (green=SUB/Today, pink=DUB, yellow=rating, amber=Tomorrow, blue=Later).
9. **Skeleton loading** — dashed-border + pulse + diagonal shimmer. Distinctive neobrutalism touch.
10. **Genre card model** — colored shadow per genre (`neo-card-blue`, `-pink`, `-purple`, `-yellow`, `-orange`, `-green`). Perfect for ANI-KUTA genre browsing.
11. **Timeline model** — vertical 3px line + square (3px-radius) color-coded dots + grayscale-by-recency. Great for an airing schedule.

Reuse with caution:

1. **Hover popups** — desktop-only pattern. On Android, replace with long-press → bottom sheet or expanded card.
2. **Horizontal scroll arrows** — desktop/tablet only. Mobile uses swipe. Skip the arrow buttons entirely on Compose.
3. **Sidebar drawer** — adapt to Compose `ModalNavigationDrawer` (or `PermanentNavigationDrawer` on tablets). Keep the 3px right border + `4px 0 0` hard shadow.
4. **Image brightness sampling** (`useImageBrightness`) — sample center-bottom pixels of hero banner to flip text color. Port as a Coil + Bitmap.getColor call, cache by URL.

Probably skip:

1. **`useHorizontalScroll` hook internals** (ResizeObserver / MutationObserver / multiple delayed timers) — web-specific reliability bandaid. Compose `LazyRow` handles this natively.
2. **`createPortal`-based popups** — replace with Compose `Popup` composable.
3. **`tw-animate-css` and `tailwindcss-animate`** — Tailwind-specific animation utilities; not needed in Compose.

## Open questions

1. **Naming inconsistency.** The zip is called "Notebook" but the code is unambiguously a "Neobrutalism" design named "AniVerse". Should we rename our internal design ID from "Notebook" to "Neobrutalism" (or "AniVerse")? Currently keeping "Notebook" to match the source zip + folder convention used for the other designs (01-material3, 02-neon, 04-coffee).
2. **The unused `--font-hand` / Caveat slot.** `@theme inline` declares a `--font-hand` mapped to `--font-caveat` (Caveat handwriting font), but `layout.tsx` never imports Caveat. Was a "journal/notebook" handwriting accent planned and dropped? If yes, we could add it to ANI-KUTA as a sparingly-used accent font for journal entries / notes — would tie back to the "Notebook" name. TODO: confirm intent.
3. **Three unused keyframes.** `neo-shake` and `neo-slide-in-right` are defined in `globals.css` but never invoked by any class. Were these planned features? Worth porting to Compose as available motion primitives if we find a use.
4. **Dark mode "Midnight Raw" border color** is `#555555` (mid-gray), not pure white. This softens the look vs the light mode's pure `#1A1A1A`. Confirm whether we want this asymmetry in ANI-KUTA dark mode or prefer a stronger contrast.
5. **Anime card aspect ratio.** The template uses `aspect-[5/7]` (portrait poster). ANI-KUTA may want a different ratio depending on source artwork (MAL uses 5/7 too, so probably fine).
6. **`textShadow` on logo.** Uses `2px 2px 0 var(--neo-shadow-blue)` (hard offset, no blur). Compose `Text` doesn't natively support text-shadow — we'd need to draw it manually behind the text via `drawBehind` or use a `Modifier.graphicsLayer` shadow on the Text composable. Verify approach.
7. **Inconsistent border-radius tokens.** `--neo-border-radius` is `10px` but Tailwind's `--radius-lg` is `8px`. The hero uses `rounded-xl` (12px) but the navbar header also uses `rounded-xl` (12px). Some inline elements use `rounded-[6px]` (6px) and `rounded-[5px]` (5px) explicitly. The radius system is **not strict** — there's a 10px neobrutalism radius layered on top of a 4/6/8/12 shadcn scale. We'll need to pick one canonical scale for Compose.
8. **`hsl(var(--x))` shim.** Tailwind config wraps every color as `hsl(var(--token))`, but the values stored in `:root` are hex (e.g. `#2563EB`), not hsl triples. `hsl(#2563EB)` is invalid CSS but browsers tolerate it as a color-string passthrough. We should NOT replicate this shim in Compose — just use the hex directly.
