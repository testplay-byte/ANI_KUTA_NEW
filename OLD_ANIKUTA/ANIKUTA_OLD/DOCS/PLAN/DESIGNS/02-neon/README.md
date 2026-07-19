# Design 2 — Dark Neon

A dark-first, neon-accented, glass-morphic design language for ANI-KUTA —
futuristic, glowing, data-aware, mobile-first.

---

## Source

- **Original document:** `/home/z/my-project/upload/DESIGN.md`
- **Original title:** *Dark Neon Design System*
- **Original target:** Web — Next.js 16 + Tailwind CSS 4 + shadcn/ui, with
  Framer Motion for animation and Lucide React for icons.
- **Our target:** ANI-KUTA — an Android anime app built in **Kotlin + Jetpack
  Compose** (forked from aniyomi). The original is a web design doc; we adapt
  its visual language to Compose. The design's identity (colors, surfaces,
  motion feel, philosophy) is preserved; only the implementation substrate
  changes.
- See `references/README.md` for a section-by-section summary of the original
  and where it lives.

---

## Design language & philosophy

The Dark Neon design is built on seven principles (quoted from
`DESIGN.md` §1). Each principle below is summarized; the in-depth reasoning,
do's and don'ts, and "why" are in `language/README.md`.

| Principle | Summary |
|-----------|---------|
| **Dark-first** | Every surface starts dark. Light is added sparingly through accent colors and subtle borders. |
| **Neon accents on muted canvas** | Base palette is neutral dark gray. Accent colors (lime, sky, coral) provide all visual energy. |
| **Glass, not flat** | Surfaces use `backdrop-blur` and semi-transparent backgrounds to create depth, not solid opaque fills. |
| **Animated but not distracting** | Motion communicates state changes, draws attention to live data, and creates delight — never slows the user down. |
| **Monospace for data** | All numbers, currencies, rates, and technical values use monospace fonts with tabular-nums for alignment. |
| **Mobile-first responsive** | Design for touch, then enhance for desktop. Keypads become floating popups on larger screens. |
| **Sticky footer, no float** | Footer sticks to the viewport bottom on short pages and is pushed down naturally on long pages. |

**For ANI-KUTA:** the dark-first, neon-on-muted-canvas identity translates
cleanly — anime artwork already pops on dark backgrounds, and the lime/sky
accents work well for "new episode", "watching", "completed", etc. The web
doc's keypad/trading-specific patterns are dropped; the surface hierarchy,
glass overlays, and motion language are kept.

---

## Aesthetics

The overall vibe is **dark, glowing, glass-morphic, futuristic**.

- Background is near-black with a faint dot grid + film grain noise — the
  screen feels like tinted glass, not a flat color.
- Three blurred "ambient glow orbs" (lime, sky, coral) drift slowly behind
  content, giving the app a living, neon-city feel.
- Cards are dark panels with hairline white-alpha borders (8% white), not
  solid blocks. Overlays (modals, bottom sheets) use heavy backdrop blur to
  frost whatever is behind them.
- Accent color is used surgically: a lime button glows; a sky chip highlights
  "calculating"; a coral badge warns. Most of the screen is muted gray so the
  accents read instantly.
- Numbers and data are mono-spaced and tabular — the app feels like a precise
  instrument, not a feed.
- Motion is springy and short (200–300ms). Things settle quickly; nothing
  loops distractingly. Live indicators pulse; everything else is still.

For ANI-KUTA specifically, this means: anime poster art glows against the
dark canvas, score numbers feel like instrument readouts, "new episode" /
"airing soon" chips pulse in lime, and the player UI frosts the video frame
when paused.

---

## Color system

All values quoted from `DESIGN.md` §2 and §19.

### Background colors (surfaces)

| Token | Hex | Usage |
|-------|-----|-------|
| `bg-base` | `#1e1e24` | Outermost background, root container |
| `bg-surface` | `#28282f` | Cards, content panels, stat blocks |
| `bg-sidebar` | `#242430` | Sidebar / navigation panel |
| `bg-elevated` | `#333340` | Hover states, active items, emphasis backgrounds |

### Accent colors (neons)

| Token | Hex | Role | Meaning |
|-------|-----|------|---------|
| `accent-lime` | `#BCFF5F` | Primary accent | Success / positive / primary actions / "go" |
| `accent-sky` | `#5FC9FF` | Secondary accent | Info / calculation / neutral highlight |
| `accent-coral` | `#FF5F7E` | Danger accent | Loss / error / deletion / "stop" |

**Usage rules (from §2.2):**
- Lime text on dark backgrounds reads at ~12:1 contrast.
- Accent buttons use the accent as background with `bg-base` as the text
  color (lime button → dark text).
- Accent backgrounds at low opacity for subtle highlights: `bg-accent-lime/10`,
  `bg-accent-sky/5`.
- Never use indigo or blue as primary.
- Coral is never a primary action color — only destructive/negative.

### Text colors

| Token | Hex | Opacity equiv. | Usage |
|-------|-----|----------------|-------|
| `foreground` | `#ffffff` | 100% | Headlines, primary values, active labels |
| `text-secondary` | `#c8c8d4` | ~78% | Body text, secondary info |
| `text-muted` | `#8888a0` | ~53% | Labels, captions, tertiary info |
| `text-dim` | `#55556a` | ~33% | Disabled, hint, decorative |

**Hierarchy rule (§2.3):** every label uses `text-muted`; every value uses
`text-white` or its accent. Never use `text-dim` for anything the user must
read — only for decorative or timestamp elements.

### Border colors (§2.4)

| Pattern | Value | Usage |
|---------|-------|-------|
| Default | `rgba(255,255,255,0.08)` | Cards, containers, dividers |
| Subtle | `rgba(255,255,255,0.04)` | Row separators in tables |
| Accent | `border-accent-{lime,sky,coral}/20` | Active/focused states, highlighted cards |
| Strong | `rgba(255,255,255,0.12)` | Floating popups, elevated overlays |
| Sidebar | `rgba(255,255,255,0.06)` | Sidebar edges, subtle separators |

### Shadow / glow tokens (§2.5)

| Token | Value | Usage |
|-------|-------|-------|
| `shadow-glow-lime` | `0 0 20px rgba(188,255,95,0.2)` | Lime button hover glow |
| `shadow-glow-sky` | `0 0 20px rgba(95,201,255,0.2)` | Sky button hover glow |
| `shadow-glow-coral` | `0 0 20px rgba(255,95,126,0.2)` | Coral emphasis glow |
| `shadow-glow-step` | `rgba(188,255,95,0.125) 0px 0px 12px` | Subtle glow for icon badges |
| `shadow-2xl` | Tailwind default | Floating popups, modals |

Full per-token notes (contrast, Android `Color` literals, M3 baseline
mapping) live in `colors/README.md`.

---

## Typography

From `DESIGN.md` §3.

### Font families

| Usage | Font (web) | Suggested Compose equivalent |
|-------|-----------|------------------------------|
| Body / UI | Geist Sans | Inter or system SansSerif (`FontFamily.SansSerif`) |
| Numbers / data / code | Geist Mono | JetBrains Mono or system Monospace (`FontFamily.Monospace`) |

For ANI-KUTA: ship **Inter** as the body font (Geist Sans isn't on Android by
default) and **JetBrains Mono** for numbers — both freely licensable and
shipped as `.ttf` resources. Alternatively use system fonts to keep APK small.

### Type scale (§3.2)

| Element | Size | Weight | Font |
|---------|------|--------|------|
| Page title | 20px (`text-xl`) | bold | Sans |
| Section heading | 14–16px | semibold/bold | Sans |
| Card value (large) | 24–30px | bold | Mono |
| Card value (medium) | 14–18px | bold | Mono (accent color) |
| Body text | 12–14px | medium | Sans, secondary color |
| Label | 10–12px | medium/semibold | Sans, muted |
| Micro label | 9–10px | semibold | Sans, muted |
| Uppercase label | 10px | semibold, `uppercase tracking-wider` | Sans, muted |
| Monospace value | 12–14px | bold, mono | Mono, accent |
| Table cell | 12px | mono | Mono, secondary |

### Number formatting rules (§3.3)

- All numerical values use mono.
- Tabular-nums (`font-variant-numeric: tabular-nums`) for aligned columns.
- Positive PnL: `+` prefix with lime; negative PnL: `-` prefix with coral.
- Original specifies currency/percentage rules — these don't apply directly to
  ANI-KUTA, but the principle carries: **all numbers are mono + tabular**, and
  +/- semantics use lime/coral.

### Uppercase label pattern (§3.4)

Labels above inputs, cards, and sections consistently use:
```
text-[10px] font-medium text-text-muted uppercase tracking-wider
```
Or slightly larger: `text-xs font-medium text-text-muted uppercase tracking-wider`.

---

## Borders & roundness

From `DESIGN.md` §6.

### Border radius

| Element | Radius | Class |
|---------|--------|-------|
| Cards, panels | 16px | `rounded-2xl` |
| Buttons, inputs | 12px | `rounded-xl` |
| Small buttons, badges | 8px | `rounded-lg` |
| Direction badges | 8px | `rounded-lg` |
| Icon containers | 8–12px | `rounded-lg` / `rounded-xl` |
| Avatars, orbs | 50% | `rounded-full` |
| Keypad keys | 12px (mobile), 8px (desktop popup) | `rounded-xl` / `rounded-lg` |

For ANI-KUTA: `RoundedCornerShape(16.dp)` for cards, `12.dp` for buttons/
inputs, `8.dp` for chips/badges, `CircleShape` for avatars.

### Dividers

- Horizontal divider between sections: `1px` tall, `bg-white/[0.06]`.
- Row separators in tables: applied as `border-b border-white/[0.04]`.

---

## Surfaces & elevation

From `DESIGN.md` §5.

### Surface hierarchy

| Level | Background | Border | Usage |
|-------|-----------|--------|-------|
| 0 — Base | `bg-bg-base` `#1e1e24` | — | Page background |
| 1 — Surface | `bg-bg-surface` `#28282f` | `white/[0.08]` | Cards, panels, content blocks |
| 2 — Sidebar | `bg-bg-sidebar` `#242430` | `white/[0.06]` | Navigation sidebar |
| 3 — Elevated | `bg-bg-elevated` `#333340` or `white/[0.06]` | `white/[0.08]` | Hover states, buttons, inputs |
| 4 — Overlay | `bg-bg-sidebar/[0.97]` + `backdrop-blur-2xl` | `white/[0.12]` | Floating popups, modals |

### Glassmorphism pattern

Overlays / popups / floating elements use:
```css
background: bg-bg-sidebar/[0.97];
backdrop-filter: blur(16px);   /* backdrop-blur-2xl */
border: 1px solid rgba(255,255,255,0.12);
box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5);  /* shadow-2xl */
border-radius: 1rem;            /* rounded-2xl */
```

Content panels use a softer variant:
```css
background: bg-bg-surface/80;
backdrop-filter: blur(16px);   /* backdrop-blur-xl */
border: 1px solid rgba(255,255,255,0.08);
border-radius: 1rem;
```

For ANI-KUTA: glass effects map to Compose `Modifier.graphicsLayer` with
`RenderEffect.createBlurEffect` (API 31+) on a snapshot of the background, or
more practically, to a semi-transparent `Surface` color with a software blur
fallback. On older Android, approximate glass with `bg-sidebar` at ~95% alpha
plus a 1px white-alpha border — the look reads as "frosted panel" even without
a true blur.

---

## Key UI elements

One-liner each, with the neon-specific styling. Full specs (exact padding,
radius, colors, state transitions) are in `elements/README.md`.

- **Primary button (lime)** — solid lime `#BCFF5F` background, dark `#1e1e24`
  text, 12px radius, h-48dp, lime glow shadow on hover, lighter `#d4ff99` on
  hover, `active:scale-[0.98]` press.
- **Secondary button (sky)** — same shape as primary but `#5FC9FF` background
  + sky glow; no custom hover color (opacity fade instead).
- **Toggle button** — active: `bg-accent-lime/10`, `border-accent-lime/20`,
  `text-accent-lime`. Inactive: `bg-bg-base`, `border-white/[0.08]`,
  `text-text-muted`, hover → `text-secondary`.
- **Icon button (subtle)** — `text-text-dim`, `p-1.5`, `rounded-lg`, hover →
  `text-accent-sky` + `bg-accent-sky/10`. Destructive variant hovers to coral.
- **Ghost / text button** — `text-xs`, `text-text-muted`, `px-3 py-1.5`,
  `rounded-lg`, hover → `text-white` + `bg-white/[0.04]`.
- **Standard card** — `bg-bg-surface`, `border-white/[0.08]`, `rounded-2xl`,
  `p-5`. Header row: 7×7dp icon badge (`bg-accent-sky/5`, `border-accent-sky/10`,
  `rounded-lg`) + 10px uppercase muted label.
- **Stat card (small)** — same surface as standard card, `p-4`, large mono
  value in white + small mono sub-value in accent.
- **Status card** — colored variant: positive = `bg-accent-lime/5` +
  `border-accent-lime/10` + lime icon; warning = sky variant; danger = coral
  variant. All `rounded-xl`, `p-4`.
- **Icon badge** — `w-7 h-7 rounded-lg bg-accent-{c}/5 border-accent-{c}/10`,
  icon at 14px in accent color. Larger variant: `w-9 h-9 rounded-xl
  bg-accent-{c}/10 border-accent-{c}/20` + `shadow-glow-step`.
- **Keypad input (tap-to-enter)** — `h-11`, `bg-bg-base`, `border-white/[0.08]`
  → active `border-accent-lime/40 ring-2 ring-accent-lime/15`, mono value,
  blinking 2px sky cursor when active.
- **Numeric keypad** — mobile = bottom sheet (`bg-bg-sidebar/[0.97]` + heavy
  blur + `rounded-t-2xl`), desktop = floating popup (`bg-bg-sidebar/[0.97]` +
  blur + `border-white/[0.12]` + `rounded-2xl`). 3×4 grid, h-14 keys (h-11
  desktop), lime confirm button.
- **Modal** — backdrop `bg-black/40` + `backdrop-blur-sm`; modal
  `bg-bg-sidebar/[0.98]` + `backdrop-blur-2xl` + `border-white/[0.1]` +
  `rounded-2xl` + `shadow-2xl`. Mobile: full-bleed `inset-4`; desktop:
  centered `max-w-lg`.
- **Data table** — `bg-bg-surface`, `border-white/[0.08]`, `rounded-2xl`,
  `overflow-hidden`. Header: `bg-bg-sidebar/50`, 12px uppercase muted, 8%
  bottom border. Body rows: `border-b border-white/[0.04]`, hover
  `bg-white/[0.02]`.
- **Direction badge** — `inline-flex`, `text-[11px]`, `px-2 py-0.5`,
  `rounded-lg`. Long = `bg-accent-lime/10 text-accent-lime`; Short =
  `bg-accent-coral/10 text-accent-coral`.
- **Navigation item (active)** — `w-full`, `px-3 py-2.5`, `rounded-xl`,
  `bg-accent-lime/7 border-accent-lime/14.5 text-accent-lime`.
- **Navigation item (inactive)** — `text-text-muted`, transparent border,
  hover `text-text-secondary` + `bg-white/[0.04]`.
- **Live indicator** — 8×8dp sky dot with `animate-ping` halo +
  `text-[9px] uppercase tracking-widest` "LIVE" label.
- **Empty state** — 64×64dp lime badge (`bg-accent-lime/5
  border-accent-lime/10 rounded-2xl`) with 32px lime-30%-opacity icon,
  followed by semibold white heading + muted description + lime CTA.

### ANI-KUTA icon assignment adaptation

The original maps concepts → Lucide icons. For ANI-KUTA we map to
**Material Symbols** (the Android-idiomatic equivalent) preserving the accent
color rules:

| Concept | Material Symbol | Accent |
|---------|----------------|--------|
| New episode / airing soon | `notifications_active` | Lime |
| Watching / in progress | `play_circle` | Sky |
| Completed | `check_circle` | Lime |
| Dropped / paused | `pause_circle` | Coral |
| Plan to watch | `bookmark` | Muted |
| Rating / score | `star` | Lime |
| Schedule / calendar | `calendar_today` | Sky |
| Search | `search` | Muted |
| Download | `download` | Sky |
| Settings | `settings` | Muted |

---

## Motion & animations

From `DESIGN.md` §13. Web uses **Framer Motion**; ANI-KUTA uses Compose
animation APIs (see `motion/README.md` for the full mapping).

### What animates

- **Page/section transitions** — fade 200ms in/out, wrapped in
  `AnimatePresence mode="wait"`.
- **Stagger entrance** — children fade in 8px upward, 50ms stagger, 250ms
  each.
- **Modal enter/exit** — `opacity 0→1`, `scale 0.95→1`, `y 20→0`, spring
  `damping=25, stiffness=300`.
- **Bottom sheet (mobile keypad)** — `y: 100% → 0`, spring
  `damping=28, stiffness=300`.
- **Floating popup (desktop keypad)** — `opacity + scale 0.9→1 + y 8→0`,
  spring `damping=25, stiffness=300`.
- **Progress bar** — width 0 → %, 1000ms ease-out.
- **Keypress / button press** — `whileTap scale 0.92` (mobile 0.9), or pure
  CSS `active:scale-[0.98]` on buttons.
- **Live indicator** — `animate-ping` halo + `animate-pulse` text "LIVE".
- **Blinking cursor** — 2×20dp sky bar, `animate-pulse`.
- **Animated top border (live preview)** — 1dp gradient
  `transparent → accent-sky/20 → transparent`, `animate-pulse`.

### Standard transition classes (§13.12)

| Property | Duration | Class |
|----------|----------|-------|
| Color change | 200ms | `transition-colors` |
| All properties | 200ms | `transition-all duration-200` |
| Hover effects | 300ms | `transition-all duration-300` |
| Value changes | 200ms | `transition-all duration-200` |

### Anti-patterns (from §20)

- Never animate layout properties (width/height) with Framer Motion on
  frequently-updated values — use transform/opacity only.
- Never leave interactive elements without hover/focus transitions.
- Never use `h-screen` (use `h-dvh`); in Compose, use `Modifier.fillMaxHeight`
  inside a `BoxWithConstraints` / the activity's `WindowInsets` API, never a
  hard pixel height.

---

## Background effects

From `DESIGN.md` §14.

### Noise texture (§14.1)

A subtle SVG `feTurbulence` film-grain at **3% opacity** applied to the root
container via a `::before` pseudo-element. In Compose, draw the same SVG as a
`VectorPath` or a small `Image` resource repeated/tiled at 3% alpha behind
all content.

### Grid dot pattern (§14.2)

A radial-gradient dot grid: 1px dot at `rgba(255,255,255,0.03)`, tiled every
`24px × 24px`. In Compose, draw via `DrawScope` in a `Canvas` or use a tiled
`ImageBitmap`.

### Ambient glow orbs (§14.3)

Three floating blurred circles behind content:

| Orb | Color | Size | Blur | Position | Animation |
|-----|-------|------|------|----------|-----------|
| Lime | `rgba(188,255,95,0.05)` | 256px | 100px | Top-left | `orb-float 8s ease-in-out infinite` |
| Sky | `rgba(95,201,255,0.05)` | 256px | 100px | Bottom-right | `orb-float 10s ease-in-out infinite reverse` |
| Coral | `rgba(255,95,126,0.03)` | 384px | 120px | Center | static |

All orbs: `pointer-events: none; z-index: 0`.

Keyframe:
```css
@keyframes orb-float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(10px, -10px); }
}
```

In Compose: three `Modifier.drawBehind` circles with `Brush.radialGradient`
+ `RenderEffect.createBlurEffect`, animated with `infiniteRepeatable`
`tween(8000, easing = EaseInOut)`.

### In-card glow accents (§14.4)

Small blurred circles absolutely positioned inside cards for visual interest:
- Default: `w-32 h-32 bg-accent-lime/5 rounded-full blur-[60px]`.
- Small: `w-10 h-10 bg-accent-lime/10 rounded-full blur-[20px]`.
- Centered: `w-32 h-32 bg-accent-sky/5 rounded-full blur-[60px]`.

All `pointer-events-none`.

### Custom scrollbar (§15)

Web: 6px thumb, transparent track, `rgba(255,255,255,0.1)` thumb, hover
`rgba(255,255,255,0.2)`, fully rounded. In Compose: style via
`LazyListState` + a custom `verticalScroll` indicator drawn with
`Modifier.drawWithContent`, or accept the platform scrollbar but tint it.

---

## Adaptation for ANI-KUTA (Android/Compose)

The web design's identity maps cleanly to Jetpack Compose. Below is the
mapping for each web concept. The intent is to preserve the *look* and *feel*
exactly; only the implementation substrate changes.

| Web concept (Tailwind/Framer) | Compose equivalent |
|-------------------------------|--------------------|
| `bg-bg-base` `#1e1e24` etc. | `Color(0xFF1E1E24)` etc., defined as a `NeonColors` data class implementing the Compose `Colors`/`ColorScheme` shape — but we ship our own `NeonTheme` since M3's tokens don't line up 1:1. |
| `border-white/[0.08]` | `BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))` or `Modifier.border(1.dp, White08, RoundedCornerShape(16.dp))`. |
| `rounded-2xl` (16dp) | `RoundedCornerShape(16.dp)`. `rounded-xl` → `12.dp`, `rounded-lg` → `8.dp`. |
| `backdrop-blur-2xl` (16px) | API 31+: `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(16.dp.toPx(), 16.dp.toPx(), Shader.TileMode.CLAMP) }`. Below 31: approximate with a near-opaque dark surface (95–97% alpha) — the look still reads as "frosted". |
| `bg-bg-sidebar/[0.97]` | `Color(0xFF242430).copy(alpha = 0.97f)` as a `Surface` color. |
| `shadow-glow-lime` etc. | `Modifier.shadow(elevation = 0.dp, shape = …, ambientColor = Lime, spotColor = Lime)` is not enough — Compose shadows are elevation-based. Implement glow as a `Modifier.drawBehind` radial gradient behind the element, or a custom `Brush.radialGradient` overlay clipped to a slightly larger shape. |
| `shadow-2xl` (Tailwind) | `Modifier.shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Black50, spotColor = Black50)` or use `Modifier.shadow` with high elevation. |
| Framer Motion `motion.div` + `AnimatePresence` | `AnimatedContent` / `Crossfade` / `AnimatedVisibility` in Compose, with `tween(200)` or `spring(dampingRatio, stiffness)` specs. |
| Framer `staggerChildren` | Compose: index each item's `AnimatedVisibility` with `launchIn` delays of `index * 50L ms`, or a `LaunchedEffect` that flips a list of booleans with delays. |
| `transition-all duration-200` | `animateColorAsState(animationSpec = tween(200))`, `animateDpAsState`, etc. |
| `active:scale-[0.98]` (CSS) | `Modifier.scale(scale)` driven by `interactionSource.collectIsPressedAsState()`, animate with `spring`. |
| `animate-pulse` (CSS) | `rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing)))`. |
| `animate-ping` (CSS) | `InfiniteTransition` animating scale 1→2 + alpha 1→0, repeat. |
| `custom-scrollbar` | Custom drawn via `Modifier.drawWithContent` reading `LazyListState.firstVisibleItemIndex` / scroll offset; or use the `Scrollbar` helper from Accompanist/Accompanist-successor libraries. |
| `noise-bg` (SVG turbulence) | Tiled `ImageBitmap` resource, or a `DrawScope` shader; simplest is a pre-rendered PNG tile drawn at 3% alpha. |
| `grid-pattern` (radial dot grid) | `Canvas` in `Modifier.drawBehind` drawing 1px circles every `24.dp`. |
| Ambient glow orbs | Three `Modifier.drawBehind` `Brush.radialGradient` circles + `infiniteRepeatable` translate animations. |
| `lg:max-w-[1400px]` (desktop app container) | N/A for an Android app — full-screen. Keep the concept for tablet/foldable: at `WindowSizeClass.Expanded` width, constrain content to a max width and center. |
| `lg:hidden` / `max-lg:hidden` (sidebar swap) | Use `WindowSizeClass` (Material 3 window-size classes): Compact = bottom nav + top bar; Medium/Expanded = rail or drawer. |
| Sidebar `w-[220px]` | `ModalNavigationDrawer` or `PermanentNavigationDrawer` body width `220.dp`. |
| `h-dvh` (dynamic viewport height) | `Modifier.fillMaxSize()` inside an edge-to-edge activity that respects `WindowInsets`. Never hardcode pixel heights. |
| Geist Sans / Geist Mono | Inter (sans) + JetBrains Mono (mono) shipped as resources, or system `FontFamily.SansSerif` / `FontFamily.Monospace`. |
| Lucide React icons | Material Symbols (`androidx.compose.material:material-icons-extended`) or a Lucide-compatible Android icon set if we want pixel-parity. The line-art style is preserved by Material Symbols Outlined. |
| Tailwind `text-[10px]` etc. | `TextStyle(fontSize = 10.sp)`. Use `.sp` not `.dp` for text. |
| `tabular-nums` | `TextStyle(fontFeatureSettings = "tnum")` — Compose supports OpenType features on Android Q+. |
| Sticky footer (`mt-auto` + flex) | `Scaffold(bottomBar = …)` or `Column { …; Spacer(Modifier.weight(1f)); Footer() }`. |

### Things we explicitly drop from the web design

- **Trading-specific components** — keypads, leverage sliders, margin/volume
  toggles, PnL formatting. None apply to ANI-KUTA. We keep the *surface and
  motion language* (bottom sheets, floating popups, status cards) but apply
  them to anime-domain content (episode picker, source picker, quality
  picker, etc.).
- **Desktop/table-first responsive layout** — ANI-KUTA is mobile-first; the
  "sidebar at lg:1024px" rule becomes "permanent drawer at
  `WindowSizeClass.Expanded`".
- **`shadcn/ui` component primitives** — replaced by Compose Material 3
  primitives (`Card`, `Button`, `ModalBottomSheet`, `Dialog`, `Surface`)
  re-themed to Neon tokens.

### Things we keep verbatim

- The full color palette (backgrounds, accents, text, borders, glows).
- The surface hierarchy (5 levels: base → surface → sidebar → elevated →
  overlay).
- Glass-morphism on overlays.
- The type scale, mono-for-numbers rule, uppercase-label pattern.
- Border radius scale (16/12/8/full).
- The motion language (200–300ms transitions, spring modals, ping halos for
  live indicators, stagger entrances).
- Ambient glow orbs + grid + noise background.
- The anti-patterns list (§20) — applied as Compose-side rules.

---

## What to reuse

Directly reused from `DESIGN.md` without modification:

1. **All color tokens** — backgrounds, accents, text tiers, borders, glow
   shadows. Hex values quoted exactly.
2. **Surface hierarchy** (5 levels) and the glass-morphism recipe.
3. **Type scale** — sizes, weights, mono-for-numbers rule, uppercase-label
   pattern.
4. **Border radius scale** — 16/12/8/full and what each is used for.
5. **Motion language** — durations, easings, spring params, the
   stagger/page-transition/modal/sheet/ping patterns.
6. **Background effects** — noise, grid dots, three ambient orbs, in-card
   glow accents.
7. **Glow shadow tokens** — used for hover/active emphasis on accent
   buttons, icon badges, live indicators.
8. **Anti-patterns** — the entire DO/DON'T list (§20), adapted to Compose
   idioms (e.g. "never hardcode pixel heights" instead of "use dvh").
9. **Status card pattern** — positive/warning/danger colored variants
   (`accent-{c}/5` background + `accent-{c}/10` border + accent icon).
   Maps cleanly to ANI-KUTA's "watching/completed/dropped" status chips.

Adapted (kept in spirit, restyled for the anime domain):

- Trading keypad → ANI-KUTA episode/source picker bottom sheet.
- Leverage slider → playback speed / skip-intro-length slider.
- Direction badge (long/short) → "dubbed/subbed" or "airing/completed"
  badge, using the same shape and accent mapping.
- PnL +/- coloring → "new episode available" (lime) vs "no new episodes"
  (muted) vs "dropped" (coral).

Dropped:

- Trading-specific component layouts, currency/percent formatting rules,
  the shadcn-ui reference, the `next/font` import, the Tailwind config block.

---

## Open questions

These need user clarification before we lock the Compose implementation:

1. **Fonts** — OK to ship Inter + JetBrains Mono as bundled `.ttf`
   resources (~1.5MB extra APK), or strictly use system fonts to keep APK
   small? The web design's identity depends on Geist's slightly-geometric
   character; system Roboto is a reasonable but distinct fallback.
2. **Glass blur on Android < 31** — `RenderEffect` requires API 31+.
   Below 31, do we (a) ship the near-opaque dark fallback described above
   and accept reduced glass fidelity on older devices, or (b) raise
   `minSdk` to 31? (aniyomi's `minSdk` is currently 26 — raising to 31
   would cut some users.)
3. **Bottom nav vs drawer at expanded width** — the web design uses a
   220dp sidebar at `lg`. On ANI-KUTA tablets/foldables, do we want a
   `PermanentNavigationDrawer` (sidebar-style) or a `NavigationRail`
   (narrower, Material 3 standard)?
4. **Anime-domain icon set** — Material Symbols Outlined (Android-idiomatic,
   line-art-friendly) vs a Lucide-equivalent port for pixel parity with the
   original web design?
5. **Coral's role in ANI-KUTA** — the original reserves coral for "loss /
   error / destructive". For ANI-KUTA, what's destructive? Delete-from-
   library is the obvious case. Should "dropped" status also use coral, or
   stay neutral to avoid the "danger" connotation?
6. **Live indicator usage** — the `animate-ping` LIVE pill is great for
   "currently airing" anime. Is it OK to use it on home-page cards (could
   get visually noisy if 20+ cards all pulse), or only on the detail page
   for a single "airing now" indicator?
7. **Monospace scope** — apply mono to *every* number (episode count, score,
   year, duration, progress %), or only to "data" numbers (score, progress,
   episode number) and let prose-y numbers (year, duration) use sans? The
   original says "all numerical values" but a year like "2024" in a
   synopsis feels less data-y.
8. **Theme picker integration** — ANI-KUTA ships 4 designs. The Neon design
   exposes 3 accent colors (lime/sky/coral). Should the user be able to
   reassign which accent is "primary" (e.g. pick sky as the main accent
   instead of lime), or is lime-always-primary part of this design's
   identity?

These will be raised in the next planning session and answers folded back
into this folder.
