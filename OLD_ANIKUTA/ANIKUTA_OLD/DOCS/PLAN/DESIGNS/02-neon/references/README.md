# References — Dark Neon (ANI-KUTA adaptation)

Pointer to the source design doc, a section-by-section summary, and notes on
how each section maps to ANI-KUTA.

---

## Original source document

**File:** `/home/z/my-project/upload/DESIGN.md`

- **Title:** *Dark Neon Design System*
- **Size:** ~43KB, 1301 lines, 20 sections
- **Original target:** Web — Next.js 16 + Tailwind CSS 4 + shadcn/ui, with
  Framer Motion for animation and Lucide React for icons.
- **Domain:** Written for a trading/finance dashboard (mentions of PnL,
  leverage, fees, keypads for currency entry). Domain-specific patterns are
  adapted out for ANI-KUTA; the design *language* (colors, surfaces, motion,
  typography) is preserved.
- **Status:** Read-only reference. We do not modify the original.

This folder (`02-neon/`) is our adaptation of the above for ANI-KUTA. The
original is the authoritative source for all color values, spacing, motion
parameters, and component recipes; our docs here quote from it and adapt
where the web substrate doesn't apply to Android/Compose.

> The original `DESIGN.md` is **not** copied into this folder — it's a
> 43KB web-focused document and we want to keep this folder focused on
> the ANI-KUTA adaptation. Read the original at the path above when you
> need to verify a value.

---

## Section-by-section summary of the original

Each section of `DESIGN.md` is summarized below with: what it contains,
whether we adopt it directly (D), adapt it (A), or drop it (X) for ANI-KUTA,
and where to find our version.

### §1 — Philosophy

Seven principles: dark-first, neon accents on muted canvas, glass not flat,
animated but not distracting, monospace for data, mobile-first responsive,
sticky footer.

- **Adoption:** D (direct). All seven principles are core to the design's
  identity and apply unchanged to ANI-KUTA.
- **Our version:** `language/README.md`.

### §2 — Color System

Four background colors (`bg-base`, `bg-surface`, `bg-sidebar`, `bg-elevated`),
three accent colors (`accent-lime`, `accent-sky`, `accent-coral`), four text
colors (`foreground`, `text-secondary`, `text-muted`, `text-dim`), five
border patterns, five glow shadow tokens. Includes CSS variables, Tailwind
classes, and usage rules.

- **Adoption:** D (direct). All hex values quoted verbatim.
- **Our version:** `colors/README.md`.

### §3 — Typography

Geist Sans (body) + Geist Mono (numbers). Type scale from 9px micro labels
to 30px large card values. Number formatting rules (currency decimals,
percentage precision, +/- PnL coloring). Uppercase label pattern
(`text-[10px] font-medium text-text-muted uppercase tracking-wider`).

- **Adoption:** A. Geist Sans/Mono → Inter + JetBrains Mono on Android
  (Geist isn't readily available as an Android font bundle). Number
  formatting rules adapted: PnL/currency rules don't apply to ANI-KUTA,
  but "all numbers are mono + tabular" + "+/- uses lime/coral" do.
- **Our version:** main `README.md` §Typography, `elements/README.md`
  (icon assignments), `colors/README.md` (accent mapping).

### §4 — Spacing & Layout

Root layout structure (textured backdrop → bordered app shell → sidebar +
main → mobile header + scrollable main + sticky footer). Spacing scale
(4px multiples). Max content widths (1400px desktop, 672px forms, 220px
sidebar).

- **Adoption:** A. The web's "centered 1400px shell" doesn't apply to a
  mobile app, but the spacing scale, max widths for forms, and the
  sticky-footer pattern all carry over.
- **Our version:** `layout/README.md`.

### §5 — Surface & Elevation

Five-level surface hierarchy (base → surface → sidebar → elevated →
overlay). Glassmorphism recipes for overlays (`bg-sidebar/[0.97]` +
`backdrop-blur-2xl`) and content panels (`bg-surface/80` +
`backdrop-blur-xl`).

- **Adoption:** D + A. The hierarchy is direct. The `backdrop-blur` recipe
  maps to Compose `RenderEffect.createBlurEffect` (API 31+) with a
  near-opaque fallback below 31.
- **Our version:** `layout/README.md` §6, §7; main `README.md` §Surfaces.

### §6 — Borders & Dividers

Border radius scale (16/12/8/full). Divider patterns (1px
`bg-white/[0.06]` between sections; 1px `border-b border-white/[0.04]`
between table rows).

- **Adoption:** D (direct). Radius and divider values quoted verbatim.
- **Our version:** `layout/README.md` §4, §5; main `README.md` §Borders.

### §7 — Buttons & Controls

Primary lime button, secondary sky button, toggle buttons (active/inactive),
icon buttons (subtle/destructive), ghost/text button, loading spinner. Each
with full className including background, text color, border, radius,
padding, hover state, press state, disabled state.

- **Adoption:** A. Button recipes apply directly to ANI-KUTA; the colors
  and shapes carry over to Compose `Button`/`OutlinedButton`/
  `TextButton`/`IconButton` re-themed to Neon tokens.
- **Our version:** `elements/README.md` §1–§6, §19.

### §8 — Cards & Containers

Standard card, stat card, highlighted/status card (positive/warning/danger
variants), icon badge (small and large), decorative in-card glow accents.

- **Adoption:** A + D. Card patterns apply directly. The "status card"
  pattern maps cleanly to ANI-KUTA's watching/completed/dropped status.
- **Our version:** `elements/README.md` §7–§11.

### §9 — Icons

Lucide React (web) line-art icon set. Icon size conventions (14/16/20/28/
32px contexts). Color rules (inherit context color; never raw white in
labels). Common icon assignments (Profit=ArrowUpRight lime, Loss=ArrowDownRight
coral, etc.).

- **Adoption:** A. Lucide → Material Symbols Outlined (Android-idiomatic
  line-art set). Icon assignments adapted for anime domain (New episode=
  notifications_active lime, etc.).
- **Our version:** `elements/README.md` §12.

### §10 — Form Inputs & Keypads

Keypad input (tap-to-enter), mobile bottom-sheet numeric keypad, desktop
floating-popup numeric keypad, leverage slider, input mode toggle.

- **Adoption:** A. Trading-specific uses (numeric keypad for currency,
  leverage slider, margin/volume toggle) become ANI-KUTA episode picker,
  source picker, playback-speed slider, dub/sub toggle. The visual recipe
  is preserved; the content swaps.
- **Our version:** `elements/README.md` §13.

### §11 — Modals & Overlays

Full edit modal (backdrop + glass surface + spring enter/exit + responsive
mobile/desktop layout). Simple confirmation overlay (lighter backdrop).

- **Adoption:** A. Modal recipe applies directly. Maps to Compose
  `Dialog`/`AlertDialog` re-themed, or `ModalBottomSheet` for sheet-style.
- **Our version:** `elements/README.md` §14.

### §12 — Tables & Lists

Desktop data table (grid-based header + scrollable body + hover states).
Responsive table (desktop full row / mobile condensed row). Direction badge
(long/short, lime/coral).

- **Adoption:** A. Table recipe applies to ANI-KUTA's episode lists,
  library lists, schedule lists. Direction badge becomes dub/sub badge
  or airing status badge.
- **Our version:** `elements/README.md` §15.

### §13 — Animations & Transitions

Framer Motion library. Page transitions (fade 200ms), stagger container
(50ms children, 250ms each), modal spring (damping 25, stiffness 300),
mobile keypad sheet (y 100%→0 spring damping 28), desktop keypad popup
(scale 0.9→1 spring), progress bar (width 0→%, 1000ms easeOut), keypress
micro-interaction (whileTap scale 0.9/0.92), button press (active:scale
0.98), live indicator (animate-ping halo), blinking cursor (animate-pulse
2×20dp sky), animated top border (gradient + animate-pulse), standard
transition durations (200/200/300/200ms).

- **Adoption:** A. All animations map to Compose animation APIs:
  `AnimatedVisibility`/`AnimatedContent`/`Crossfade` for transitions,
  `InfiniteTransition` for pulses, `animateXAsState` for state-driven
  changes, `ModalBottomSheet` for sheets. Spring params map (Framer
  `damping=25, stiffness=300` ≈ Compose `dampingRatio=0.8f,
  stiffness=Spring.StiffnessMedium`).
- **Our version:** `motion/README.md`.

### §14 — Background Effects

Noise texture (SVG `feTurbulence` at 3% opacity), grid dot pattern (1px
dots at 24px intervals, 3% opacity), three ambient glow orbs (lime/sky/
coral, blurred, slowly animated), in-card glow accents.

- **Adoption:** A. Noise + grid map to Compose `Canvas`/`drawBehind`. Orbs
  map to `Modifier.drawBehind` + `Brush.radialGradient` + `Modifier.blur`
  (API 31+) or pre-rendered blurred bitmaps. In-card glows same.
- **Our version:** main `README.md` §Background effects; `elements/README.md`
  §11.

### §15 — Scrollbars

Custom dark scrollbar: 6px width, transparent track, `rgba(255,255,255,0.1)`
thumb (0.2 on hover), fully rounded.

- **Adoption:** A. Compose doesn't have a default scrollbar — draw via
  `Modifier.drawWithContent` reading `LazyListState`, or accept platform
  scrollbar tinted toward white/10%.
- **Our version:** `elements/README.md` §23.

### §16 — Navigation Patterns

Desktop sidebar (220px wide, brand row + nav items + optional quick stats;
active item = `bg-accent-lime/7 border-accent-lime/14.5 text-accent-lime`,
inactive = `text-text-muted` + transparent border + hover `bg-white/[0.04]`).
Mobile header (h-14, brand badge + menu button) + tab bar (active = lime +
bottom-2 border, inactive = muted).

- **Adoption:** A. Desktop sidebar → `PermanentNavigationDrawer` or
  `NavigationRail` at `WindowSizeClass.Expanded`. Mobile header + tab bar →
  `TopAppBar` + `NavigationBar` (bottom) re-themed.
- **Our version:** `elements/README.md` §16; `layout/README.md` §8.

### §17 — Responsive Breakpoints

Tailwind defaults: sm 640, md 768, lg 1024, xl 1280. Key rules: sidebar
hidden below lg, mobile header hidden above lg, keypad as bottom sheet
below lg / floating popup above, table compact/full switch at md.

- **Adoption:** A. Tailwind breakpoints → Material 3 `WindowSizeClass`
  (Compact < 600dp, Medium 600–839dp, Expanded 840–1199dp, Large
  1200–1599dp, XL ≥ 1600dp). Layout switches happen at class boundaries,
  not pixel breakpoints.
- **Our version:** `layout/README.md` §8.

### §18 — Component Reference

Component architecture tree (Page → Sidebar/MobileHeader → MainContent →
AnimatePresence → Section → Cards/Keypad/Modal/etc.). Empty state pattern.
Key/value row pattern.

- **Adoption:** A. The architecture tree is web-specific (Sidebar vs
  MobileHeader at lg breakpoint) but the principle — small focused
  composables composed into screens — carries to Compose. Empty state
  pattern and key/value row apply directly.
- **Our version:** `elements/README.md` §17 (empty state), §18 (key/value
  row).

### §19 — Tailwind Theme Configuration

Complete `@theme inline` CSS-variable block (backgrounds, accents, text,
glow shadows) and `:root` shadcn/ui semantic tokens (background, foreground,
card, primary, secondary, muted, accent, destructive, border, input, ring,
chart-1..5, sidebar-*).

- **Adoption:** A. The values are direct; the wrapping mechanism changes
  from CSS variables to a Compose `NeonColors` data class accessed via
  `CompositionLocal`.
- **Our version:** `colors/README.md` (full block quoted verbatim +
  suggested `NeonColors` data class sketch).

### §20 — Anti-Patterns & Rules

DO list (mono for numbers, accent colors at low opacity, backdrop-blur on
overlays, pointer-events-none on decorative elements, custom-scrollbar,
overflow-hidden root, noise-bg grid-pattern root, AnimatePresence for
enter/exit, footer shrink-0 + mt-auto, max-h-[calc(100dvh-...)] scroll
regions, dvh units, transition-all/transition-colors on interactives,
rounded-2xl cards / rounded-xl buttons). DON'T list (no indigo/blue
primary, no solid opaque overlays, no text-white labels, no interactive
elements without transitions, no position:sticky footers, no overflow:auto
without custom-scrollbar, no raw hex inline, no animating layout properties
on frequent updates, no h-screen, no new accent colors, no emoji, no sans
for numbers).

- **Adoption:** A. All rules apply; the substrate-specific ones ("use
  custom-scrollbar", "use dvh", "use AnimatePresence") become Compose
  idioms ("draw a custom scrollbar", "use fillMaxSize inside an
  edge-to-edge activity", "use AnimatedVisibility").
- **Our version:** `language/README.md` §Quick reference: do / don't;
  `motion/README.md` §15; `layout/README.md` §11–§12.

### Quick Reference Cheat Sheet (end of §20)

A condensed summary of all tokens (colors, accents, text, borders, radius,
font, icons, animation, layout, responsive) in 12 lines.

- **Adoption:** D. Quoted verbatim in our `colors/README.md` and the main
  `README.md`.

---

## Other ANI-KUTA design references

This is one of four ANI-KUTA starting designs. The others live in sibling
folders under `/home/z/my-project/anikuta/DOCS/PLAN/DESIGNS/`:

- `01-material3/` — Material 3 baseline (Android-idiomatic).
- `02-neon/` — **this one** (Dark Neon, adapted from a web design).
- `03-notebook/` — TBD.
- `04-coffee/` — TBD.

The user picks one design as their default; all four share the same backend
(AniList + aniyomi extensions) and the same logic layer. Only the UI
substrate differs.

---

## How to use this folder

- **Start with** `README.md` (this folder's root) for the design overview.
- **Read** `language/README.md` for the philosophy and the "why" behind
  each principle.
- **Reference** `colors/README.md` for exact hex/Compose values.
- **Reference** `elements/README.md` when implementing a specific component.
- **Reference** `layout/README.md` for spacing, breakpoints, and the
  sticky-footer rule.
- **Reference** `motion/README.md` for animation parameters and Compose
  mapping.
- **Read** `references/README.md` (this file) when you need to trace any
  value back to the original `DESIGN.md`.

When in doubt about a value, the original `DESIGN.md` at
`/home/z/my-project/upload/DESIGN.md` is the source of truth. This folder
quotes from it; it doesn't replace it.
