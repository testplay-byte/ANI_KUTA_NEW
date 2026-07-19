# Design 3 — Notebook: Design Language & Philosophy

## Identity

| Field | Value |
|---|---|
| Internal design ID | `03-notebook` (from zip name `NOTEBOOK_TEMPLATE.zip`) |
| Template name | **AniVerse** (`package.json` `name: "aniverse"`, version `0.2.0`) |
| Self-described style | "Neobrutalism Design System v2" |
| Tagline | *"Raw. Bold. Unapologetic."* |
| Light palette name | "Acid Cream" |
| Dark palette name | "Midnight Raw" |
| Source path | `/home/z/my-project/upload/notebook-extracted/aniverse-template/` |
| Stack (original) | Next.js 16 + React 19 + TypeScript + Tailwind 4 + Framer Motion 12 + Radix (tooltip/toast) + shadcn/ui `new-york` + Lucide |
| Stack (target) | Android + Jetpack Compose (adaptation, not port) |

> The "Notebook" name comes only from the zip filename. The unzipped folder is `aniverse-template`, the `package.json` calls it `aniverse`, the `globals.css` header comment calls it "AniVerse — Neobrutalism Design System v2", and the `layout.tsx` metadata title is "AniVerse — Your Anime Notebook". The only place "notebook" appears in the codebase is as an image folder (`public/images/notebook/`) holding fallback anime cover art. The design language itself is **neobrutalism** — there is no lined-paper, ruled-paper, or journal aesthetic anywhere in the CSS.

## Verbatim source

From `src/app/globals.css` lines 4–23 (the file header comment):

```css
/**
 * AniVerse — Neobrutalism Design System v2
 *
 * Raw. Bold. Unapologetic.
 *
 * Neobrutalism core principles:
 *   - Thick black/white borders (3px solid)
 *   - Hard-edge box shadows with ZERO blur (e.g., 4px 4px 0 #000)
 *   - Bright, saturated accent colors
 *   - Active/pressed states: shadow shrinks + translate(x,y) + bg color change
 *   - Bold, heavy typography (uppercase, black weight)
 *   - Grid-pattern background for that raw canvas feel
 *   - Physical 3D feel through layered shadows
 *   - Hover: color-shift + shadow-grow + lift
 *   - Click/tap: color-burst + shadow-shrink + press
 *
 * Color Palettes:
 *   Light "Acid Cream"  — warm off-white bg, black borders, colored shadows
 *   Dark  "Midnight Raw" — deep charcoal bg, white borders, colored shadows
 */
```

From `src/app/page.tsx` lines 3–29 (the page header comment):

```ts
/**
 * AniVerse — Neobrutalism Anime Homepage
 * ...
 * Design Language: Neobrutalism
 *   - Thick black/white borders (2.5px solid)
 *   - Hard-edge box shadows with ZERO blur
 *   - Bright, saturated accent colors (blue, pink, green, yellow, orange, purple)
 *   - Active/pressed states: shadow shrinks + translate(x,y)
 *   - Bold, heavy uppercase typography
 *   - Flat backgrounds with subtle grid pattern
 *   - Physical 3D feel through layered shadows
 */
```

(Note: the `page.tsx` comment says "2.5px solid" but the actual CSS uses 3px / 3.5px borders — the comment is slightly out of date. The CSS is the source of truth.)

## Core principles (extracted)

### 1. Hard-edge shadows, zero blur

Every elevated surface carries an offset shadow with **`0` blur radius** — pure `{X}px {Y}px 0px {color}`. This is the single most defining visual trait. Soft drop-shadows, glow, or feathered elevation are forbidden.

Consequence: shadows read as physical objects in their own right (a colored slab behind the surface), not as atmospheric depth. They become part of the design vocabulary — colored shadows (blue / pink / green / yellow / orange / purple / red) act as category accents.

### 2. Thick borders are structural

Borders are 3px solid `--border` (= `--foreground` in light mode = `#1A1A1A` near-black; `#555555` mid-gray in dark mode). Borders are not decorative outlines — they are the visual edge of the surface, as important as the fill. Anime cards use 3.5px (thicker still). Badges use 2px (thinner, but still clearly bordered). Skeletons use 2px **dashed** (raw / unfinished look).

### 3. Physical interaction model

Every interactive surface responds to hover/tap with a **physical lift-and-press motion**:

- Resting: shadow at `N px N px 0` (e.g. 4×4 for cards, 3×3 for buttons).
- Hover: shadow grows to `~2N px 2N px 0`, surface translates `(-2, -2)` (lifts up-left).
- Pressed: shadow shrinks to `~N/4 px N/4 px 0`, surface translates `(+3, +3)` (presses down-right into where the shadow was).

Background also shifts: hover tints the surface with `--neo-hover-bg` (light blue wash); press tints with `--neo-active-bg` (deeper blue wash). The template literally calls this "color-burst" — the surface changes color in sync with the physical motion.

### 4. Uppercase heavy typography

All headings, button labels, badges, meta text, and most labels are `text-transform: uppercase`. Weights range from `font-bold` (700) to `font-black` (900). Body text is the rare exception with mixed case.

Tracking is **tight on large text** (`-tight` = `-0.025em` on titles; `-0.02em` on buttons) and **slightly open on small uppercase labels** (`0.04em` on badges, `tracking-wider` = `0.1em` on 6px micro-labels). This contrast — tight big, open small — keeps both readable.

### 5. Saturated accent colors over neutral base

The base surfaces are warm-neutral (cream `#D9D5CC` / `#EDEAE3` in light; cool charcoal `#2A2A32` / `#363640` in dark). The color comes from the **hard shadows** (7 accent colors) and a small number of inline-tinted badges (raw Tailwind `bg-green-400`, `bg-yellow-400`, `bg-pink-400`, etc.). There are no gradients on UI elements — only on image overlays (hero bottom gradient for text readability) and the skeleton shimmer sweep.

### 6. Raw canvas: 28px grid background

The body always carries a 28px square grid pattern, drawn as two crossed `linear-gradient` lines (one horizontal, one vertical) tiled at `--neo-grid-size`. Line color is `rgba(26,26,26,0.14)` in light mode (subtle dark grid) or `rgba(255,255,255,0.08)` in dark (subtle light grid). This gives the canvas the "graph paper / raw sketchbook" feel without ever being literal ruled notebook lines.

### 7. Square corners (mostly), small radii

Radii are small and intentionally varied: cards 10px, buttons/inputs 8px, badges 6px, nav buttons 6px, icon boxes 5px, timeline dots 3px (squares with tiny corner rounding), carousel dots 3px. Nothing is fully pill-shaped, nothing is sharp-cornered. The variation is intentional — different element classes have different radii to feel hand-placed rather than systematized.

### 8. Spring overshoot as default motion curve

Almost every animated transition uses the same easing: `cubic-bezier(0.34, 1.56, 0.64, 1)` — a bezier that overshoots past 1.0 on the Y axis, giving a springy bounce. Used for: card entrances, section entrances, sidebar drawer, hero text slide-in, popups, sidebar item hover. Buttons and small press transitions use plain `ease` at 0.1s for snappiness. Popups use Framer Motion's spring physics (`stiffness 400, damping 18, mass 0.9`).

### 9. Mobile suppresses entrance animations

The template detects `<640px` via `useIsMobile()` and **disables `whileInView` entrance animations** on mobile — sections mount in their final state instead of animating in. Comment in code: animations on mobile scroll fire unreliably and feel janky. Hover-reveal scroll arrows are also hidden on mobile (users swipe). This is a deliberate, defensible mobile UX choice — we should mirror it in Compose.

## Mood keywords

Raw. Bold. Unapologetic. Punchy. Tactile. Physical. Sticker-art. Cut-out. Sketchbook. Loud. Playful-aggressive. Hand-placed. Anti-minimalist.

## Anti-keywords (what this design is NOT)

- Glassmorphism, blur backgrounds (the only `backdrop-filter: blur(4px)` is on the mobile sidebar overlay — nowhere else).
- Soft elevation shadows (Material-style `elevation = 6dp` etc. — forbidden).
- Lined / ruled notebook paper (despite the zip name).
- Minimalism / whitespace-heavy / quiet.
- Pastel washes (colors are saturated Tailwind 400/500/600 levels, not 100/200).
- Material 3 dynamic color (the palette is hand-picked, fixed hex).
- Neon glow (the `02-neon` design covers that aesthetic — this one is hard-edged, not glowing).

## Comparison to other ANI-KUTA design candidates

| Trait | 01 Material 3 | 02 Neon | **03 Notebook (Neobrutalism)** | 04 Coffee |
|---|---|---|---|---|
| Elevation | Soft tonal shadows | Glow / blur | **Hard zero-blur offset shadows** | TBD |
| Borders | Hairline / tonal | Glow lines | **3px solid black/gray** | TBD |
| Color source | Dynamic system palette | Saturated neon RGB | **Fixed saturated accents on warm-neutral base** | TBD |
| Background | Surface tint | Dark + glow | **Solid + 28px grid** | TBD |
| Type | Roboto, system | Display neon | **Geist sans, uppercase black** | TBD |
| Motion | Material spring | Pulsing glow | **Spring overshoot, lift-and-press** | TBD |
| Mood | Calm, adaptive | Cyber, electric | **Raw, tactile, loud** | TBD |

## Why this might (or might not) fit ANI-KUTA

**Fits well:**

- Anime browsing is fundamentally about **poster art** — covers are colorful, busy, maximalist. A loud neobrutalist frame around them doesn't compete; it complements. The 3px black border + colored hard shadow makes each poster read as a "sticker" placed on the canvas.
- Genre browsing maps naturally to the 7-color shadow system (blue=Action, pink=Romance, purple=Fantasy, etc.) — already implemented in the template's `genres[]` array.
- Schedule / airing timeline is already designed (`NextReleasingSection`) with color-coded dots per recency (Today/Tomorrow/Later) and grayscale-by-future. Direct port to ANI-KUTA's airing schedule.
- Bold uppercase type reads well at small sizes on phone screens — good for dense anime titles.
- The "physical press" interaction model is satisfying on touch devices — every tap gives literal tactile feedback via the shadow-press.

**Risks:**

- Heavy 3px borders everywhere can feel visually noisy when many cards are on screen at once (e.g. a search results grid). May need to thin borders for dense grids.
- The 28px background grid is unusual and can feel busy. May want to soften or remove for ANI-KUTA's main browse screens, keep only for "journal" / personal-list screens where the "notebook" identity is wanted.
- 7 accent shadow colors is a lot — risk of rainbow chaos if not disciplined. The template uses each color for a specific semantic role (blue=trending, green=freshly-updated, orange=popular, etc.); we should formalize this mapping.
- The "Acid Cream" light-mode background (`#D9D5CC`) is warm-tinted, which can clash with cool anime cover art. May need a cooler-neutral alternative.
- Uppercase everywhere can hurt readability for long-form text (synopses, descriptions). The template uses mixed case only for descriptions and footer body text — we should keep that pattern.

## Design tokens ownership

Single source of truth for the design tokens is `src/app/globals.css` (lines 72–176 for the `:root` and `.dark` blocks). The Tailwind config (`tailwind.config.ts`) wires these tokens into Tailwind utilities (`bg-card`, `text-foreground`, `border-border`, etc.) but defines no additional color values — every value lives in CSS variables. The shadcn `components.json` confirms `baseColor: "neutral"` and `cssVariables: true`, but the actual values diverge from shadcn's `neutral` defaults — the design has been overridden.

For Compose, we will mirror the CSS-variable layer as a `NeoColors` data class + `LocalNeoColors` CompositionLocal. See `colors/README.md` for the full table.
