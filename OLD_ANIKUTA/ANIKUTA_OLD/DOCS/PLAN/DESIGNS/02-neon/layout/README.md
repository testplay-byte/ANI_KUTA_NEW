# Layout, Spacing & Borders — Dark Neon (ANI-KUTA adaptation)

Spacing scale, border radius values, container widths, responsive
breakpoints, and the sticky-footer rule. Quoted from `DESIGN.md` §4, §6, §17.

Conventions: 1 Tailwind unit = 4px. `p-4` = 16px, `gap-3` = 12px, etc.

---

## 1. Root layout structure

> Source: §4.1.

The web app's root layout is a centered, bordered "app shell" — a single
1400px-wide panel with rounded corners, sitting on a textured backdrop. The
shell itself contains a sidebar + main content area, with the main area
holding a mobile header (when sidebar is hidden), the scrollable main, and
a sticky footer.

```
┌──────────────────────────────────────────────────────┐
│ h-dvh w-dvw overflow-hidden flex items-center        │   ← textured backdrop
│ justify-center bg-bg-base noise-bg grid-pattern      │     (noise + dot grid)
│   ┌────────────────────────────────────────────────┐ │
│   │ h-full w-full lg:max-w-[1400px]                │ │   ← app shell
│   │ lg:border lg:border-white/[0.08]               │ │     (bordered + rounded on lg+)
│   │ lg:rounded-2xl overflow-hidden flex flex-col    │ │
│   │   ┌──────────┬─────────────────────────────┐   │ │
│   │   │ Sidebar  │  Main Content Area           │   │ │
│   │   │ 220px    │  flex-1 flex flex-col         │   │ │
│   │   │          │    ┌───────────────────────┐  │   │ │
│   │   │          │    │ MobileHeader (lg:hidden)│  │   │ │
│   │   │          │    ├───────────────────────┤  │   │ │
│   │   │          │    │ main (flex-1, scroll)  │  │   │ │
│   │   │          │    │   p-4 sm:p-6           │  │   │ │
│   │   │          │    ├───────────────────────┤  │   │ │
│   │   │          │    │ footer (shrink-0)      │  │   │ │
│   │   │          │    └───────────────────────┘  │   │ │
│   │   └──────────┴─────────────────────────────┘   │ │
│   └────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### Adaptation for ANI-KUTA

ANI-KUTA is a mobile app, so the outer "textured backdrop + centered 1400px
shell" doesn't apply directly. Instead:

- **Phone (Compact width):** full-screen app. Top bar (h-14 / 56dp) +
  scrollable main + bottom navigation. No sidebar.
- **Tablet / large foldable (Expanded width):** the shell *does* apply —
  constrain content to a centered panel (e.g. `max-w-[840.dp]` or full
  width with side padding) with the sidebar (or `NavigationRail`) on the
  left and the main content on the right. Use `WindowSizeClass` to switch.
- **Textured backdrop:** the `noise-bg grid-pattern` is applied to the
  entire app background regardless of form factor — it's the canvas on
  which everything sits.

In Compose: `Modifier.fillMaxSize().background(NeonColors.bgBase)` on the
root, with a `Canvas` overlay drawing the noise + dot grid (see
`motion/README.md` and `elements/README.md` for the Canvas recipes).

---

## 2. Spacing scale

> Source: §4.2.

The Dark Neon design uses Tailwind's default spacing scale (4px multiples).
Below are the contexts in which each value is used.

| Context | Padding | Gap | Compose |
|---------|---------|-----|---------|
| Page content | `p-4 sm:p-6` (16px → 24px) | — | `Modifier.padding(16.dp)` / `24.dp` based on width |
| Card body | `p-4` / `p-5` / `p-6` (16/20/24px) | — | `16.dp` / `20.dp` / `24.dp` |
| Card grid | — | `gap-3` / `gap-4` (12/16px) | `Arrangement.spacedBy(12.dp)` / `16.dp` |
| Form groups | — | `space-y-4` / `space-y-5` (16/20px vertical) | `Arrangement.spacedBy(16.dp)` / `20.dp` |
| Inner card sections | — | `space-y-2.5` / `space-y-3` (10/12px) | `Arrangement.spacedBy(10.dp)` / `12.dp` |
| Icon + text | — | `gap-1.5` / `gap-2` / `gap-3` (6/8/12px) | `Arrangement.spacedBy(6.dp)` / `8.dp` / `12.dp` |
| Button groups | — | `gap-3` (12px) | `Arrangement.spacedBy(12.dp)` |
| Table cell padding | `px-4 py-3` (16/12px) | `gap-3` (12px) | `PaddingValues(16.dp, 12.dp)` |
| Footer | `px-4 sm:px-6 py-3` (16/24px horiz, 12px vert) | — | `PaddingValues(16.dp, 12.dp)` / `24.dp` horiz |

### Full spacing scale (Tailwind reference)

| Tailwind | px | dp |
|----------|----|----|
| `0` | 0 | 0.dp |
| `0.5` | 2 | 2.dp |
| `1` | 4 | 4.dp |
| `1.5` | 6 | 6.dp |
| `2` | 8 | 8.dp |
| `2.5` | 10 | 10.dp |
| `3` | 12 | 12.dp |
| `3.5` | 14 | 14.dp |
| `4` | 16 | 16.dp |
| `5` | 20 | 20.dp |
| `6` | 24 | 24.dp |
| `8` | 32 | 32.dp |
| `10` | 40 | 40.dp |
| `12` | 48 | 48.dp |
| `16` | 64 | 64.dp |

Use these consistently. The design relies on tight, small spacings (most
gaps are 6–16dp). Don't reach for 24dp+ unless you're spacing major
sections.

---

## 3. Max content width

> Source: §4.3.

| Element | Width | Compose |
|---------|-------|---------|
| Desktop app shell | `lg:max-w-[1400px]` centered `mx-auto` | N/A for phone; on tablet/large foldable, `Modifier.widthIn(max = 1400.dp).align(CenterHorizontally)` |
| Form panels | `max-w-2xl mx-auto` (672px) | `Modifier.widthIn(max = 672.dp).align(CenterHorizontally)` |
| Sidebar | `w-[220px] shrink-0` | `Modifier.width(220.dp)` inside a `Row` |

For ANI-KUTA: form panels (settings screens, login) cap at `672.dp` and
center horizontally on Expanded widths. On phone, they fill the screen with
standard page padding (`p-4`).

---

## 4. Border radius

> Source: §6.1.

| Element | Radius | Tailwind | Compose |
|---------|--------|----------|---------|
| Cards, panels | 16px | `rounded-2xl` | `RoundedCornerShape(16.dp)` |
| Buttons, inputs | 12px | `rounded-xl` | `RoundedCornerShape(12.dp)` |
| Small buttons, badges | 8px | `rounded-lg` | `RoundedCornerShape(8.dp)` |
| Direction badges | 8px | `rounded-lg` | `RoundedCornerShape(8.dp)` |
| Icon containers | 8–12px | `rounded-lg` / `rounded-xl` | `8.dp` / `12.dp` |
| Avatars, orbs | 50% | `rounded-full` | `CircleShape` |
| Keypad keys (mobile) | 12px | `rounded-xl` | `RoundedCornerShape(12.dp)` |
| Keypad keys (desktop popup) | 8px | `rounded-lg` | `RoundedCornerShape(8.dp)` |
| Bottom sheet top corners | 16px | `rounded-t-2xl` | `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)` |

`--radius` in `:root` is `0.625rem` (10px) — this is shadcn/ui's base token,
but the actual class usage above takes precedence.

### Rule of thumb

- **16dp** for surfaces that contain a lot (cards, panels, modals, sheets).
- **12dp** for interactive controls (buttons, inputs, keypad keys).
- **8dp** for small chips, badges, icon-only buttons.
- **Full** for avatars, indicator dots, orbs.

---

## 5. Borders & dividers

> Source: §6.2.

### Border colors (see `colors/README.md` for full token table)

| Pattern | Value | Usage |
|---------|-------|-------|
| Default | `rgba(255,255,255,0.08)` | Cards, containers, dividers |
| Subtle | `rgba(255,255,255,0.04)` | Row separators within tables |
| Accent | `border-accent-{lime,sky,coral}/20` | Active/focused states, highlighted cards |
| Strong | `rgba(255,255,255,0.12)` | Floating popups, elevated overlays |
| Sidebar | `rgba(255,255,255,0.06)` | Sidebar edges, subtle separators |

### Border widths

- `1px` hairline for almost all borders (cards, dividers, table rows,
  icon badges).
- `1px` for accent borders on active/focused states.
- `2px` for the bottom of an active mobile tab (`border-b-2 border-accent-lime`).

### Divider patterns

Horizontal divider between sections (§6.2):
```html
<div className="w-full h-px bg-white/[0.06]" />
```
Compose: `Box(Modifier.fillMaxWidth().height(1.dp).background(NeonColors.borderSidebar))`.

Row separator in tables (§6.2):
```html
<!-- Applied as border on the row -->
className="border-b border-white/[0.04]"
```
Compose: `Modifier.drawBehind { drawLine(...) }` or simply use
`Divider(color = NeonColors.borderSubtle, thickness = 1.dp)` between rows.

---

## 6. Surface & elevation hierarchy

> Source: §5.1.

Five levels. Each level is a specific background + border combination.

| Level | Background | Border | Usage |
|-------|-----------|--------|-------|
| 0 — Base | `bg-bg-base` `#1e1e24` | — | Page background |
| 1 — Surface | `bg-bg-surface` `#28282f` | `border-white/[0.08]` | Cards, panels, content blocks |
| 2 — Sidebar | `bg-bg-sidebar` `#242430` | `border-white/[0.06]` | Navigation sidebar |
| 3 — Elevated | `bg-bg-elevated` `#333340` or `bg-white/[0.06]` | `border-white/[0.08]` | Hover states, buttons, inputs |
| 4 — Overlay | `bg-bg-sidebar/[0.97]` + `backdrop-blur-2xl` | `border-white/[0.12]` | Floating popups, modals |

The hierarchy is enforced strictly: don't use Level 4 background for a
card, don't use Level 1 background for a modal, etc. The visual language
depends on consistency.

---

## 7. Glassmorphism recipe

> Source: §5.2.

### Overlays, popups, floating elements

```css
background: bg-bg-sidebar/[0.97];                  /* sidebar at 97% alpha */
backdrop-filter: blur(16px);                       /* backdrop-blur-2xl */
border: 1px solid rgba(255, 255, 255, 0.12);       /* strong border */
box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);  /* shadow-2xl */
border-radius: 1rem;                                /* rounded-2xl = 16px */
```

### Content panels (softer glass)

```css
background: bg-bg-surface/80;                      /* surface at 80% alpha */
backdrop-filter: blur(16px);                       /* backdrop-blur-xl */
border: 1px solid rgba(255, 255, 255, 0.08);       /* default border */
border-radius: 1rem;                                /* rounded-2xl = 16px */
```

### Compose note

API 31+: use `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP) }` on a snapshot of the background.

Below 31: fall back to `bg-sidebar` at 95–97% alpha (no blur). The look
still reads as "frosted panel" because of the semi-transparency + border.

---

## 8. Responsive breakpoints

> Source: §17.

The web design uses Tailwind's default breakpoints (mobile-first):

| Breakpoint | Width (web) | Usage |
|-----------|-------------|-------|
| Default | 0px | Mobile layout (tab bar, compact cards) |
| `sm` | 640px | Slightly larger padding, multi-column stats |
| `md` | 768px | 2-column grids, desktop table headers |
| `lg` | 1024px | Sidebar appears, mobile header hides |
| `xl` | 1280px | Wider content areas |

### Key responsive rules (§17)

- Sidebar: `max-lg:hidden` (hidden below 1024px).
- Mobile header: `lg:hidden` (hidden above 1024px).
- Keypad: bottom sheet below 1024px, floating popup above.
- Table: compact mobile row / full desktop row.
- Page padding: `p-4 sm:p-6`.
- Footer padding: `px-4 sm:px-6`.

### ANI-KUTA adaptation

Compose uses Material 3 `WindowSizeClass` instead of pixel breakpoints:

| WindowSizeClass | Width | Web equiv | ANI-KUTA layout |
|----------------|-------|-----------|-----------------|
| Compact | < 600dp | < `sm` | Phone: bottom nav + top bar, single column, `p-4` |
| Medium | 600–839dp | `sm` – `md` | Small tablet / foldable: 2-column grids, `p-6` |
| Expanded | 840–1199dp | `md` – `lg` | Tablet: 2–3 columns, optional `NavigationRail` |
| Large | 1200–1599dp | `lg` – `xl` | Large tablet: `PermanentNavigationDrawer` (220dp sidebar) |
| Extra Large | ≥ 1600dp | ≥ `xl` | Desktop-class: full sidebar + wide content |

Switch via `currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass`.

---

## 9. The sticky-footer rule

> Source: §1 principle 7, §20 anti-pattern.

### Rule

> Footer always sticks to the viewport bottom on short pages and is pushed
> down naturally on long pages.

### Implementation (web)

```html
<div class="h-dvh flex flex-col">
  <main class="flex-1 overflow-y-auto">...</main>
  <footer class="shrink-0 px-4 sm:px-6 py-3">...</footer>
</div>
```

The `flex-1` main fills available space; the `shrink-0` footer sits at the
bottom when content is short, and is pushed down by content when content is
long.

### Implementation (Compose)

```kotlin
Scaffold(
    bottomBar = { Footer() },   // Material 3 Scaffold handles the sticky behavior
) { padding ->
    LazyColumn(modifier = Modifier.padding(padding)) {
        // content
    }
}
```

Or, inside a scrollable region (in-content sticky footer, e.g. "play next
episode" bar under an episode list):

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        // episodes
    }
    PlayNextBar()   // sticky at bottom of the column
}
```

### Anti-patterns (§20)

- Never use `position: sticky` on footers. Use flex + `mt-auto` (web) or
  `weight(1f)` on the scrollable content (Compose).
- Never use `h-screen` (web) / hardcoded pixel heights (Compose). Use
  `h-dvh` (web) / `fillMaxSize()` inside an edge-to-edge activity that
  respects `WindowInsets` (Compose).

---

## 10. List scroll regions

> Source: §20 anti-patterns, §12.1.

### Rule

Use `max-h-[calc(100dvh-320px)]` for scrollable table/list bodies so the
table doesn't grow unbounded and push the footer off-screen.

### Compose equivalent

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // header (fixed)
    Header()
    // scrollable body (bounded)
    LazyColumn(modifier = Modifier.weight(1f)) {
        // rows
    }
    // footer (sticky)
    Footer()
}
```

The `weight(1f)` on the body ensures it takes whatever vertical space is
left between header and footer, and scrolls internally.

### Always pair with `custom-scrollbar`

The original mandates `custom-scrollbar` on every scrollable region (§20).
Compose equivalent: draw the scrollbar via `Modifier.drawWithContent`
reading `LazyListState`, or accept the platform scrollbar tinted toward
`Color.White.copy(alpha = 0.1f)`.

---

## 11. Viewport height

> Source: §20.

- Web: always `h-dvh` (dynamic viewport height). Never `h-screen` (which
  doesn't account for mobile browser chrome and causes layout jumps).
- Compose: always `Modifier.fillMaxSize()` inside an edge-to-edge activity
  that respects `WindowInsets`. Never hardcode pixel heights.

This matters most on Android: the status bar, navigation bar, and gesture
insets all change available height. Use `Modifier.windowInsetsPadding`
or `Scaffold`'s inset handling to keep content out from under system bars.

---

## 12. Root container rules (§20)

- Use `overflow-hidden` on the root container (web) / respect `WindowInsets`
  (Compose) to prevent page scroll bleeding outside the app shell.
- Apply `noise-bg grid-pattern` (web) / `Modifier.drawBehind` Canvas with
  noise + dot grid (Compose) to the root for texture.
- Make footer `shrink-0` with `mt-auto` (web) / non-weighted at the bottom
  of a `Column` (Compose).
- Use `max-h-[calc(100dvh-...)]` (web) / `Modifier.weight(1f)` (Compose)
  for scrollable list regions.
