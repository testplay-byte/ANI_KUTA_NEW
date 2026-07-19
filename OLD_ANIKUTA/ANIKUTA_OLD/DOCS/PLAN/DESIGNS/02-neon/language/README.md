# Design Language & Philosophy — Dark Neon (ANI-KUTA adaptation)

The seven principles that define Dark Neon, expanded with reasoning, do's and
don'ts, and how each applies to ANI-KUTA. Source: `DESIGN.md` §1 (Philosophy)
and §20 (Anti-Patterns & Rules).

---

## The seven principles

### 1. Dark-first

> Every surface starts dark. Light is added sparingly through accent colors
> and subtle borders.

**Why.** A dark canvas does two jobs: it makes the screen comfortable in low
light, and it makes any accent color read instantly. If the canvas were
light, the lime/sky/coral accents would have to compete with the background
and would lose their "neon" quality. Dark-first turns the whole screen into
a backdrop for the accents.

**Do.**
- Start every screen from `bg-base` `#1e1e24`. Layer surfaces (cards,
  panels) on top in `bg-surface` `#28282f`. Light never comes from the
  background.
- Add light via: accent color (sparingly), 8%-alpha white borders, and glow
  shadows on hover.

**Don't.**
- Never invert to a light theme. This design has no light variant. If the
  user wants light, they pick a different design (e.g. Design 4 — Coffee).
- Never use solid white as a card background — that breaks the dark-first
  invariant.

**For ANI-KUTA.** Anime poster art is typically bright and saturated. Dark
surfaces let posters pop without competing. Score numbers, episode counts,
and progress bars read like instrument readouts against the dark canvas.

### 2. Neon accents on muted canvas

> The base palette is neutral dark gray. Accent colors (lime, sky, coral)
> provide all visual energy.

**Why.** Three accent colors, used surgically, communicate state faster than
ten colors used liberally. Lime = go/success, sky = info/neutral-highlight,
coral = stop/danger. The base stays neutral so the user's eye is drawn
*only* to what changed.

**Do.**
- Use lime for primary actions and positive states (currently-airing,
  completed, "watch now").
- Use sky for informational highlights, calculations, secondary actions,
  and live-data indicators.
- Use coral for destructive actions and negative states only (delete,
  dropped, error).
- Use accent colors at low opacity (`/5`, `/10`, `/20`) for backgrounds
  and borders; full strength for text and fills.

**Don't.**
- Never introduce a fourth accent color (no purple, no yellow, no teal).
- Never use coral for a primary action — it must always signal "stop".
- Never use indigo or blue as a primary color (§20). This is explicitly
  forbidden in the source.

**For ANI-KUTA.** Lime = "new episode available" + "completed" + primary
CTA. Sky = "currently watching" + "airing today" + secondary actions. Coral
= "delete from library" + "dropped status". Muted gray = everything else.

### 3. Glass, not flat

> Surfaces use `backdrop-blur` and semi-transparent backgrounds to create
> depth, not solid opaque fills.

**Why.** Flat dark surfaces look lifeless and indistinguishable. Glass —
semi-transparent backgrounds with a real blur of what's behind them — gives
the UI depth and a sense that elements are floating above the canvas. It
also lets the ambient glow orbs and grid pattern bleed through, reinforcing
the "tinted glass over a neon city" aesthetic.

**Do.**
- Overlays (modals, bottom sheets, popups) use `bg-bg-sidebar/[0.97]` plus
  `backdrop-blur-2xl` (16px blur). The 3% transparency lets the underlying
  content bleed through subtly.
- Content panels use `bg-bg-surface/80` plus `backdrop-blur-xl` for a
  softer glass.
- Always pair glass with a `1px` white-alpha border (8% for panels, 12%
  for overlays) — the border is what sells the "edge of glass" illusion.

**Don't.**
- Never use solid opaque backgrounds for overlays. Even `bg-bg-sidebar` at
  100% opacity is forbidden for an overlay (§20).
- Never use glass without a border — borderless glass reads as a rendering
  bug.

**For ANI-KUTA.** Compose glass: API 31+ uses
`RenderEffect.createBlurEffect(16.dp.toPx(), 16.dp.toPx(), CLAMP)` via
`Modifier.graphicsLayer`. Below 31, fall back to `bg-sidebar` at ~95–97%
alpha — the look still reads as "frosted panel". Player overlay controls
frost the video frame; this is the headline use of glass in ANI-KUTA.

### 4. Animated but not distracting

> Motion is used to communicate state changes, draw attention to live data,
> and create delight — never to slow the user down.

**Why.** Animation in a data-dense app has to earn its place. If it
communicates state (a value changed, a section loaded, a modal opened), it
helps. If it just decorates, it competes with the data and tires the user.
Dark Neon uses short, springy motion (200–300ms) so transitions feel alive
but settle fast.

**Do.**
- Use `AnimatePresence` (web) / `AnimatedVisibility` / `AnimatedContent`
  (Compose) for enter/exit on every section switch, modal, and sheet.
- Use spring motion for modals and sheets (`damping=25, stiffness=300`) so
  they feel physical.
- Use `animate-pulse` for the blinking cursor and live indicators only.
- Use stagger (50ms children) for list entrances so items appear to "pour
  in" rather than slam simultaneously.

**Don't.**
- Never animate layout properties (width/height) on frequently-updated
  values — it jitters. Use transform (scale/translate) and opacity.
- Never loop an animation that doesn't communicate state (no spinning
  logos, no infinite shimmer on static content).
- Never use motion longer than ~300ms for routine transitions — that's
  where it starts to slow the user down.

**For ANI-KUTA.** Stagger the home-page rail entrance. Spring-open the
episode picker bottom sheet. Pulse the "airing now" indicator on a detail
page. Don't animate the episode list itself — it changes too often.

### 5. Monospace for data

> All numbers, currencies, rates, and technical values use monospace fonts
> with tabular-nums for alignment.

**Why.** Numbers in proportional fonts shift width as digits change — a
column of episode counts (`01`, `10`, `11`) jitters and is hard to scan.
Monospace + tabular-nums locks digit width so columns align. It also gives
the app an "instrument" feel that matches the dark-neon aesthetic.

**Do.**
- Use the mono font for: episode numbers, scores, progress percentages,
  durations, dates, timestamps, file sizes, anything that scans as data.
- Use `font-variant-numeric: tabular-nums` (or `fontFeatureSettings = "tnum"`
  in Compose) for aligned columns.
- Color positive numbers lime and negative numbers coral (with `+`/`-`
  prefixes) — carries over from the original PnL rule.

**Don't.**
- Never use the sans font for numbers. This is explicitly forbidden in §20.
- Never mix proportional and tabular figures in the same column.

**For ANI-KUTA.** Episode number "01", score "8.45", progress "12 / 24
(50%)", runtime "23 min", air date "2024-04-12" — all mono. A year inside
prose ("Originally aired in 2024") can stay sans if it reads as prose; a
year in a metadata column is mono.

### 6. Mobile-first responsive

> Design for touch, then enhance for desktop. Keypads become floating popups
> on larger screens.

**Why.** Mobile-first forces the design to solve the hard problem (small
screen, touch input, limited chrome) first. Desktop enhancements (sidebar,
floating popups, multi-column) layer on top of a working mobile layout
rather than being crammed down from a desktop design.

**Do.**
- Default layout = single column, bottom nav, top bar, compact cards.
- At `md` (768px) → 2-column grids.
- At `lg` (1024px) → sidebar appears, mobile header hides.
- Keypads: bottom sheet below `lg`, floating popup at/above `lg`.

**Don't.**
- Never design desktop-first and shrink.
- Never put a sidebar on mobile.

**For ANI-KUTA.** ANI-KUTA is mobile-only in spirit (Android app). The
responsive breakpoints become `WindowSizeClass` thresholds: Compact (phone)
= bottom nav + top bar; Medium (small tablet/foldable) = 2-column; Expanded
(tablet/large foldable) = `PermanentNavigationDrawer` or `NavigationRail`.
There is no desktop.

### 7. Sticky footer, no float

> Footer always sticks to the viewport bottom on short pages and is pushed
> down naturally on long pages.

**Why.** A floating footer (one that sits in the middle of the screen when
content is short) looks like a bug. A `position: sticky` footer creates
layout jumps. The clean solution is a flex column: content area takes
available space (`flex-1` / `Modifier.weight(1f)`), footer is `shrink-0`,
so it sits at the bottom when content is short and scrolls naturally with
content when content is long.

**Do.**
- Root layout: `Column { TopBar(); Content(Modifier.weight(1f)); Footer() }`.
- Footer is always `shrink-0` (no `weight`).
- Apply the same pattern inside scrollable areas: footer stays anchored to
  the bottom of the scroll container.

**Don't.**
- Never use `position: sticky` on footers (§20).
- Never use `Modifier.height(???dp)` to fake a sticky footer.

**For ANI-KUTA.** In Compose: `Scaffold(bottomBar = { Footer() })` for the
page-level footer, or `Column { LazyColumn(Modifier.weight(1f)); Footer() }`
for in-content sticky footers (e.g. a "play next episode" bar that stays
under the episode list).

---

## The "why" behind each principle — synthesis

The seven principles are not independent. They form a coherent stance:

- **Dark-first + neon-on-muted** is the color theory: dark canvas, surgical
  accents.
- **Glass-not-flat** is the surface theory: depth without weight.
- **Animated-but-not-distracting** is the motion theory: motion as
  communication, not decoration.
- **Monospace-for-data** is the typography theory: data is sacred and must
  align.
- **Mobile-first + sticky-footer** is the layout theory: solve the small
  case cleanly, then enhance.

Together they produce the Dark Neon feel: a dark, glowing, glass-morphic
instrument, not a feed or a magazine. Every visual choice should reinforce
one of these five theories. If a design decision doesn't, it's noise.

---

## Adaptation principles for ANI-KUTA

These are *our* additions, not in the source. They govern how we adapt the
web design to Android/Compose without losing its identity.

A. **Preserve the look, not the substrate.** Tailwind class names, Framer
   Motion APIs, and `shadcn/ui` primitives are implementation details. The
   colors, surface hierarchy, motion timing, and glass recipe are the
   identity. If we get those right in Compose, the design is preserved.

B. **Prefer Compose idioms over literal ports.** Use `Scaffold`,
   `LazyColumn`, `AnimatedContent`, `MaterialTheme` overrides — not hand-rolled
   equivalents that mimic the web DOM. A faithful Compose adaptation reads
   better than a literal port.

C. **Map, don't translate, the icon set.** The original uses Lucide
   React (line-art). Material Symbols Outlined is the Android-idiomatic
   line-art set. Use it. Don't bundle a Lucide port just for pixel parity.

D. **Drop the domain-specific stuff.** The web design is for trading tools.
   Keypads, leverage sliders, PnL coloring, margin/volume toggles — none of
   this belongs in ANI-KUTA. Keep the patterns (bottom sheets, status cards,
   +/- coloring), retheme for anime.

E. **Don't soften the dark.** ANI-KUTA has 4 designs; this is the dark one.
   If a screen feels "too dark", that's intentional. Don't reach for lighter
   surfaces to "fix" it — pick a different design instead.

F. **Anti-patterns are rules, not suggestions.** The §20 DO/DON'T list
   applies in Compose as much as in Tailwind. "Never use raw hex inline"
   becomes "never hardcode `Color(0xFF…)` in composables — go through the
   `NeonColors` theme object". Same intent, different mechanism.

---

## Quick reference: do / don't (condensed from §20)

### DO
- Use mono for all numerical values.
- Use accent colors at low opacity (`/5`, `/10`, `/20`) for backgrounds.
- Use `backdrop-blur` on overlays and floating elements.
- Add `pointer-events-none` (web) / `Modifier.pointerInput` skip (Compose)
  to decorative elements.
- Use the custom scrollbar style on all scrollable regions.
- Use `overflow-hidden` on the root container (web) / respect `WindowInsets`
  (Compose) to prevent layout bleed.
- Apply `noise-bg grid-pattern` (web) / the equivalent `Modifier.drawBehind`
  Canvas (Compose) to the root for texture.
- Use `AnimatePresence` (web) / `AnimatedVisibility` (Compose) for
  enter/exit animations.
- Make footer `shrink-0` with `mt-auto` (web) / non-weighted in a `Column`
  (Compose).
- Use `max-h-[calc(100dvh-...)]` (web) / `Modifier.weight(1f)` inside a
  bounded `Column` (Compose) for scrollable list regions.
- Use `dvh` (web) / `fillMaxSize` inside an edge-to-edge activity (Compose)
  for viewport height.
- Add `transition-all` or `transition-colors` (web) / `animateAsState`
  (Compose) to all interactive elements.
- Use `rounded-2xl` for cards, `rounded-xl` for buttons/inputs.

### DON'T
- Never use indigo or blue as a primary color.
- Never use solid opaque backgrounds for overlays — always blur + transparency.
- Never use `text-white` for labels (use `text-text-muted`).
- Never leave interactive elements without hover/focus transitions.
- Never use `position: sticky` on footers — use flex + `mt-auto`.
- Never use `overflow: auto` without the custom scrollbar style.
- Never use raw hex colors inline — use the design tokens.
- Never animate layout properties (width/height) with Framer Motion on
  frequently-updated values.
- Never use `h-screen` (web) / hardcoded pixel heights (Compose).
- Never create new accent colors outside the lime/sky/coral system.
- Never use emoji in production UI unless explicitly requested.
- Never use the sans font for numbers — always mono.
