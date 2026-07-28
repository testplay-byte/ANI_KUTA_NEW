# Design 3 — Notebook: Layout, Spacing & Borders

Extracted from `tailwind.config.ts`, `src/app/globals.css`, and the inline layout classes used throughout `src/app/page.tsx`. Documents the spacing scale, breakpoints, border system, radii, and page-level layout architecture.

## Tailwind config (`tailwind.config.ts`)

```ts
const config: Config = {
  darkMode: "class",
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: { /* all colors = hsl(var(--token)) shims */ },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      }
    }
  },
  plugins: [tailwindcssAnimate],
};
```

Notable: the Tailwind config is **minimal**. It only extends `colors` (all wired to `hsl(var(--token))` passthrough shims) and `borderRadius` (computed from `--radius`). No custom spacing scale, no custom font sizes, no custom shadows — the design system lives in `globals.css` as CSS variables and `.neo-*` classes, not in the Tailwind config.

The `@theme inline` block in `globals.css` (Tailwind v4 syntax) adds the radius scale and font-family tokens:

```css
@theme inline {
  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
  --font-hand: var(--font-caveat);
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
  /* ... + color tokens */
}
```

## Spacing scale

The template does **not** define a custom spacing scale — it uses Tailwind's default spacing scale (1 unit = 0.25rem = 4px). All gaps, paddings, and margins in `page.tsx` use Tailwind defaults.

### Most-used values (from `page.tsx`)

| Tailwind | Pixels | Used for |
|---|---|---|
| `gap-1` | 4px | Tight badge internals |
| `gap-1.5` | 6px | Badge icon-text gap, button icon-text gap, hero meta dots |
| `gap-2` | 8px | Tag rows, small flex gaps |
| `gap-2.5` | 10px | Section header icon-title gap, sidebar item icon-text |
| `gap-3` | 12px | Content row card gap (mobile) |
| `gap-3.5` | 14px | Timeline entry thumbnail-info gap (md) |
| `gap-4` | 16px | Content row card gap (desktop), grid columns |
| `gap-5` | 20px | Content row card gap (md) |
| `gap-8` | 32px | Footer grid gap (desktop) |
| `space-y-1` | 4px between | Sidebar nav items |
| `space-y-1.5` | 6px between | Card title block |
| `space-y-2` | 8px between | Footer link lists, timeline skeleton info |
| `space-y-6` | 24px | Main content vertical rhythm (mobile) |
| `space-y-8` | 32px | Main content vertical rhythm (sm) |
| `space-y-14` | 56px | Main content vertical rhythm (md+) |

### Padding values (most-used)

| Tailwind | Pixels | Used for |
|---|---|---|
| `px-1 py-[1px]` | 4 / 1 | Sub/dub/ep chips inside card overlay |
| `px-1.5 py-0.5` | 6 / 2 | Footer link hover chip |
| `px-2 py-1` | 8 / 4 | Popup action buttons (View / Save) |
| `px-2.5` | 10 | Sidebar vertical padding, popup horizontal |
| `px-3 py-1.5` | 12 / 6 | "See All" / "All Genres" buttons |
| `px-3 py-2` | 12 / 8 | Skeleton cover padding fallback |
| `p-2 sm:p-2.5 md:p-3` | 8 / 10 / 12 | Timeline entry card padding |
| `p-2.5 sm:p-3 md:p-6` | 10 / 12 / 24 | Timeline container padding |
| `p-3` | 12 | Sidebar nav padding, sidebar bottom padding |
| `p-3 md:p-4` | 12 / 16 | Genre card padding |
| `p-4` | 16 | Toast padding |
| `p-5 pb-4` | 20 / 16 | Sidebar logo block |
| `p-5 md:p-6` | 20 / 24 | Hero details panel padding (desktop) |
| `px-3 sm:px-4 md:px-8 lg:px-12` | 12/16/32/48 | Main content horizontal padding (responsive) |
| `px-2 sm:px-4 md:px-0` | 8/16/0 | Content row horizontal padding |
| `px-4 md:px-6 lg:px-8` | 16/24/32 | Navbar inner padding |
| `px-3 py-1.5` | 12 / 6 | Tooltip padding (shadcn default) |
| `pt-8 pb-16 md:pb-24` | 32 / 64 / 96 | Main content bottom padding |
| `py-8 md:py-14` | 32 / 56 | Footer vertical padding |

### Margin values (most-used)

| Tailwind | Pixels | Used for |
|---|---|---|
| `mt-2.5` | 10 | Card title-block margin from cover |
| `mt-0.5` | 2 | Card subtitle margin from title |
| `mt-3 md:mt-4` | 12 / 16 | Navbar top margin |
| `mt-4 md:mt-6` | 16 / 24 | Hero top margin |
| `mt-6 sm:mt-8 md:mt-12` | 24 / 32 / 48 | Main content top margin |
| `mt-auto` | auto | Footer pushed to bottom (flex) |
| `mb-1` | 4 | Hero title margin |
| `mb-1.5` | 6 | Hero meta margin, popup title margin |
| `mb-2` | 8 | Hero description margin |
| `mb-3` | 12 | Hero tags margin, popup info rows |
| `mb-3 md:mb-5` | 12 / 20 | Section header bottom margin |
| `mb-4` | 16 | Footer brand margin, hero CTA gap |
| `mb-6 md:mb-10` | 24 / 40 | Footer grid bottom margin |
| `my-2 sm:my-3` | 8 / 12 | Timeline group separator margin |

### Page max-width

```
max-w-[1280px] mx-auto   /* navbar inner, hero content, main, footer */
```

Everything caps at 1280px wide and centers in the viewport.

## Breakpoints

Tailwind default breakpoints. The template uses four of them.

| Breakpoint | Width | Tailwind prefix | Behavior in this template |
|---|---|---|---|
| Mobile (default) | <640px | (none) | Single column, hamburger sidebar, compact cards, swipe-only scroll |
| sm | ≥640px | `sm:` | Two-column search bar appears, hover-reveal scroll arrows, larger cards |
| md | ≥768px | `md:` | Larger cards, larger fonts, larger paddings, bell button appears |
| lg | ≥1024px | `lg:` | Fixed sidebar (no drawer), main content padded left by sidebar width |

Mobile detection in JS uses `useIsMobile()` (`src/hooks/use-mobile.ts`) with `MOBILE_BREAKPOINT = 640` — same as Tailwind's `sm`. Returns `true` when `window.innerWidth < 640`. Returns `undefined` during SSR (before mount).

## Border system

### Border width scale

The template uses **inconsistent border widths** by design — different element classes have different widths to feel hand-placed.

| Width | Used by |
|---|---|
| 1px | `border` default (toast, shadcn primitives) |
| 1.5px | `style={{ borderWidth: 1.5 }}` — skeleton internals |
| 2px | `.neo-badge`, `.neo-sidebar-item` (resting), `.neo-skeleton` (dashed), section divider `border-foreground/15`, footer bottom `border-foreground/10` |
| 2.5px | Scroll arrows (`border-[2.5px]`) |
| 3px | `.neo-border` standard, `.neo-card` family, `.neo-btn` family, `.neo-input` family, `.neo-sidebar` right edge, footer top, timeline vertical line, hero badge borders |
| 3.5px | `.neo-card-anime` (special thicker border for poster cards) |

### Border color

| Token | Light | Dark |
|---|---|---|
| `--border` | `#1A1A1A` (near black) | `#555555` (mid gray) |
| `--foreground` (used inline via `style={{ borderColor: "var(--foreground)" }}` on badges) | `#1A1A1A` | `#E8E8E8` (near white) |

Note the asymmetry: in light mode `--border` and `--foreground` are the same color; in dark mode they differ (`#555` vs `#E8E8E8`). The template uses `--border` for `.neo-*` element borders (which gives a softer dark-mode look) but `--foreground` for inline badge borders (which gives a sharper dark-mode look). This creates a subtle hierarchy in dark mode.

### Border radius scale

| Token / class | Value | Tailwind alias | Used by |
|---|---|---|---|
| `--radius-sm` | 4px | `rounded-sm` | (rarely used) |
| `--radius-md` | 6px | `rounded-md` | `.neo-badge`, `.neo-nav-btn`, tooltip, scroll arrows, footer link hover, carousel dots |
| `--radius-lg` | 8px | `rounded-lg` | `.neo-btn`, `.neo-input`, `.neo-input-red`, `.neo-sidebar-item`, navbar search input, notification dot |
| `--radius-xl` | 12px | `rounded-xl` | Hero banner, navbar header |
| `--neo-border-radius` | 10px | (inline only) | `.neo-card` family, `.neo-card-anime`, `.neo-skeleton`, timeline container, anime popup |

The radius scale is **two-layered**: the shadcn `--radius`-derived scale (4/6/8/12px) for shadcn primitives, and the neobrutalism `--neo-border-radius` (10px) for `.neo-*` classes. They overlap and don't form a strict ladder.

### Inline radius values (page.tsx)

| Value | Used for |
|---|---|
| `rounded-[2px]` | Tiny chips inside card overlay (sub/dub/ep counts), skeleton micro-rects |
| `rounded-[3px]` | Timeline dots, carousel dots, skeleton mini-rects |
| `rounded-[4px]` | Footer link hover chips, popup action buttons |
| `rounded-[5px]` | Genre card icon box |
| `rounded-[6px]` | `.neo-badge`, `.neo-nav-btn`, section header icon box, scroll arrows, sidebar logo box |
| `rounded-[8px]` | `.neo-btn`, `.neo-input`, `.neo-sidebar-item`, hamburger button, bell button, skeleton cover thumbnail |
| `rounded-[10px]` | (via `--neo-border-radius`) `.neo-card` family |
| `rounded-lg` (8px) | Navbar search input, search submit button |
| `rounded-xl` (12px) | Hero banner section, navbar header |

## Background grid pattern

The body always carries a 28px square grid pattern, drawn as two crossed `linear-gradient` lines. This is the "raw canvas" base layer.

```css
body {
  color: var(--foreground);
  background-color: var(--background);
  background-image:
    linear-gradient(var(--neo-grid-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--neo-grid-color) 1px, transparent 1px);
  background-size: var(--neo-grid-size) var(--neo-grid-size);  /* 28px × 28px */
}
```

| Mode | `--neo-grid-color` | `--neo-grid-size` |
|---|---|---|
| Light | `rgba(26, 26, 26, 0.14)` (14% black) | 28px |
| Dark | `rgba(255, 255, 255, 0.08)` (8% white) | 28px |

The grid is always visible behind content. It is **not** "lined notebook paper" — it's a uniform square grid like graph paper.

## Page layout architecture

From `src/app/page.tsx` (lines 2162–2840) and the README's ASCII diagram:

```
┌─────────────────────────────────────────────────────────┐
│ Sidebar (fixed on lg+, drawer on mobile)                │
│ ─ 220px wide (64–280px resizable)                       │
│ ─ Logo + nav items + theme toggle                       │
│ ─ 3px right border + 4px right-side hard shadow         │
├─────────────────────────────────────────────────────────┤
│ Main content wrapper (paddingLeft = sidebarWidth on lg+)│
│ ┌────────────────────────────────────────────────────┐  │
│ │ Navbar (mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4)        │  │
│ │ ─ Hamburger + spacer + search + spacer + bell     │  │
│ │ ─ 3px border + 4px red hard shadow                │  │
│ │ ─ 12px radius (rounded-xl)                        │  │
│ ├────────────────────────────────────────────────────┤  │
│ │ Hero banner (mx-2 sm:mx-3 md:mx-5 mt-4 md:mt-6)   │  │
│ │ ─ Full-width carousel, 200/395/432/476px tall     │  │
│ │ ─ .neo-card-anime styling (3.5px border)          │  │
│ │ ─ Desktop: details panel inside (50% width)       │  │
│ │ ─ Mobile: text directly on banner (no panel)      │  │
│ │ ─ Carousel dots below (mobile) / inside (desktop) │  │
│ ├────────────────────────────────────────────────────┤  │
│ │ Main (max-w-1280 mx-auto, vertical stack)         │  │
│ │ ─ ContentRow "Trending Now"   (blue shadow)       │  │
│ │ ─ ContentRow "Freshly Updated" (green shadow)     │  │
│ │ ─ GenreSection (8 cards, colored shadows)         │  │
│ │ ─ ContentRow "Most Popular"   (orange shadow)     │  │
│ │ ─ NextReleasingSection (timeline)                 │  │
│ │ Section vertical rhythm: space-y-6/8/14 (24/32/56)│  │
│ ├────────────────────────────────────────────────────┤  │
│ │ Footer (border-t-3, bg-card, 0 -4px 0 shadow)     │  │
│ │ ─ Brand + Browse + Community + Legal (4 cols)     │  │
│ │ ─ Bottom bar: copyright + Status/API/v2.4.1       │  │
│ └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Sidebar dimensions

| Property | Value |
|---|---|
| Min width | 64px (icons only, collapsed) |
| Default width | 220px |
| Max width | 280px |
| Collapsed threshold | width < 100px (icons only, no text) |
| Position | `fixed top-0 left-0 h-dvh lg:h-screen z-[80]` |
| Mobile drawer | `translate-x-0` open / `-translate-x-full` closed |
| Desktop (lg+) | `lg:translate-x-0` always visible |
| Drag handle | 4px hit area on right edge, 2px visual line, widens to 1.5px on hover, color shifts from `bg-foreground/15` to `bg-primary/60` |

### Main content wrapper

On lg+, the main content has `paddingLeft: ${sidebarWidth}px` injected via an inline `<style>` tag:

```tsx
<style>{`
  @media (min-width: 1024px) {
    .main-content-wrapper { padding-left: ${sidebarWidth}px !important; }
  }
`}</style>
```

This dynamic style is regenerated whenever `sidebarWidth` changes (drag-resize). Below lg, padding-left is 0 (sidebar is a drawer overlay, not in flow).

### Navbar

```tsx
<motion.div className="mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4 relative z-50">
  <header
    className="bg-card border-[3px] border-foreground rounded-xl overflow-hidden"
    style={{ boxShadow: "4px 4px 0px var(--neo-shadow-red)" }}
  >
    <div className="max-w-[1280px] mx-auto px-4 md:px-6 lg:px-8">
      <div className="flex items-center h-14 md:h-16">
        {/* hamburger | spacer | search | spacer | actions */}
      </div>
    </div>
  </header>
</motion.div>
```

- 3px border + 12px radius + 4px **red** hard shadow (the navbar is the only place that uses red as the resting shadow color — subtle visual cue that the search lives here).
- Height: 56px mobile / 64px md+.
- Inner content uses a 5-zone flex: `[hamburger] [spacer] [search] [spacer] [actions]`. The spacers are animated `motion.div`s that collapse when the mobile search is focused (giving more room to the search bar).

### Hero banner

```tsx
<section
  className="relative w-full h-[200px] sm:h-[395px] md:h-[432px] lg:h-[476px] rounded-xl overflow-hidden neo-card-anime"
  onTouchStart={...} onTouchEnd={...}
>
```

- Full width, 4 responsive heights.
- 12px radius, 3.5px border (from `.neo-card-anime`).
- Touch-swipe gesture: horizontal swipe ≥40px (and not more vertical than horizontal) cycles hero index.
- Auto-advances every 6s (`setInterval`).
- Desktop (sm+): 50% width details panel inside the hero, `rotate(-1deg)` tilted, `8px 8px 0 var(--neo-shadow-blue)` shadow.
- Mobile (<sm): no panel — text directly on banner, adaptive color via `useImageBrightness`.
- Carousel dots: below the hero on mobile, inside on desktop (`absolute bottom-5 left-1/2 -translate-x-1/2`).

### Main content sections

```tsx
<main className="flex-1 max-w-[1280px] w-full mx-auto px-2 sm:px-4 md:px-8 lg:px-12 space-y-6 sm:space-y-8 md:space-y-14 pb-16 md:pb-24 mt-6 sm:mt-8 md:mt-12 relative z-10">
```

- Max-width 1280px, centered.
- Horizontal padding: 8 / 16 / 32 / 48 by breakpoint.
- Vertical rhythm between sections: 24 / 32 / 56 by breakpoint.
- Bottom padding: 64 / 96 by breakpoint (room for any fixed mobile chrome).

### ContentRow layout

```tsx
<motion.section className="relative">
  {/* Section header */}
  <div className="flex items-center justify-between mb-3 md:mb-5 px-2 sm:px-4 md:px-0">
    <div className="neo-section-header flex items-center gap-2.5">...</div>
    <button className="neo-btn ...">See All</button>
  </div>
  {/* Scroll container */}
  <div className="relative group/row">
    {/* Left arrow (overlay) */}
    <div className="absolute left-0 sm:left-1 top-0 bottom-0 z-20 flex items-center">...</div>
    {/* Card track */}
    <div
      ref={scrollRef}
      className="flex gap-4 md:gap-5 overflow-x-auto overflow-y-clip no-scrollbar px-2 sm:px-4 md:px-2 py-2 md:py-6"
    >
      {items.map(...) }
      <div className="flex-shrink-0 w-2 sm:w-4" />  {/* end spacer */}
    </div>
    {/* Right arrow (overlay) */}
    <div className="absolute right-0 sm:right-1 top-0 bottom-0 z-20 flex items-center">...</div>
    {/* Edge fade gradients */}
    <div className="absolute right-0 top-0 bottom-0 w-10 sm:w-14 md:w-20 bg-gradient-to-l from-background via-background/80 to-transparent pointer-events-none z-10" />
    <div className="absolute left-0 top-0 bottom-0 w-10 sm:w-14 md:w-20 bg-gradient-to-r from-background via-background/80 to-transparent pointer-events-none z-10" />
  </div>
</motion.section>
```

Card-track layout details:
- Flex row, gap 16/20, `overflow-x-auto` with hidden scrollbar (`.no-scrollbar`).
- `overflow-y-clip` so cards lifting on hover don't expand the row vertically.
- Padding: `py-2 md:py-6` (top/bottom room for hover lift + shadow growth).
- End spacer: 8/16px so the last card can fully scroll into view.
- Edge fades: 40/56/80px gradient overlays on each side, opacity-toggled by scroll position (left fade visible when `canScrollLeft`, right fade visible when `canScrollRight`).

### GenreSection layout

```tsx
<div ref={containerRef} className="flex gap-3 md:gap-4 px-3 sm:px-5 md:px-2 pt-2 pb-2 overflow-visible">
  {genres.slice(0, visibleCount).map(...)}
</div>
```

- Flex row, gap 12/16.
- Cards: `flex-1 min-w-0 max-w-[200px]` — equal-width columns capped at 200px.
- Each card: `h-[90px] md:h-[100px]`, internal `p-3 md:p-4`.
- `visibleCount` is computed dynamically from container width, gap, and a min card width (80px mobile / 110px desktop) — fits as many cards as possible without overflow.

### NextReleasingSection timeline layout

```tsx
<div
  className="bg-card border-[3px] border-foreground rounded-[var(--neo-border-radius)] p-2.5 sm:p-3 md:p-6 overflow-visible relative"
  style={{ boxShadow: "4px 4px 0px var(--neo-shadow)" }}
>
  <div className="relative">
    {/* Vertical line */}
    <div className="absolute left-[13px] sm:left-[15px] md:left-[23px] top-2 bottom-2 w-[3px] bg-foreground/20" />
    <div className="space-y-0">
      {renderGroup("Today", ...)}
      {<div className="border-t-2 border-dashed border-foreground/15 my-2 sm:my-3" />}
      {renderGroup("Tomorrow", ...)}
      {<div className="border-t-2 border-dashed border-foreground/15 my-2 sm:my-3" />}
      {renderGroup("Later", ...)}
    </div>
  </div>
</div>
```

Each entry:

```tsx
<div className="relative pl-8 sm:pl-9 md:pl-14">  {/* room for dot + line */}
  {/* Dot — absolute */}
  <div className="absolute left-[7px] sm:left-[9px] md:left-[17px] top-3 sm:top-4 md:top-5 w-[12px] h-[12px] md:w-[14px] md:h-[14px] rounded-[3px] border-2 z-10 ${dotColor}" style={{ boxShadow: "1px 1px 0px var(--neo-shadow)" }} />
  {/* Entry card */}
  <div className="py-1.5 sm:py-2">
    <motion.div className="neo-card flex items-center gap-2.5 sm:gap-3 md:gap-3.5 p-2 sm:p-2.5 md:p-3 ...">
      <img className="w-[48px] h-[68px] sm:w-[36px] sm:h-[52px] md:w-[42px] md:h-[60px] rounded-[6px] object-cover flex-shrink-0 border-2 border-border/40" />
      <div className="flex-1 min-w-0">...</div>
    </motion.div>
  </div>
</div>
```

- Vertical line is at `left-[13/15/23]` (a 3px-wide column centered on the 12/14px dots).
- Each entry is left-padded by `pl-8/9/14` to clear the dot+line.
- Dots: 12px mobile / 14px md, 3px-radius squares, 2px border, color-coded (green=Today, amber=Tomorrow, blue=Later), 1×1 black hard shadow.
- Group separators: 2px dashed `border-foreground/15`.
- Card thumbnails shrink on `sm+`: 48×68 → 36×52 (sm) → 42×60 (md). Mobile shows larger thumbnails because there's less horizontal room for text.

### Footer layout

```tsx
<motion.footer
  className="border-t-[3px] border-foreground bg-card mt-auto"
  style={{ boxShadow: "0 -4px 0px var(--neo-shadow)" }}
>
  <div className="max-w-[1280px] mx-auto px-2 sm:px-4 md:px-8 lg:px-12 py-8 md:py-14">
    <div className="grid grid-cols-3 md:grid-cols-4 gap-4 md:gap-8 mb-6 md:mb-10">
      {/* Brand (hidden on mobile — col-span-1, hidden md:block) */}
      {/* Browse */}
      {/* Community */}
      {/* Legal */}
    </div>
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-8 border-t-2 border-foreground/10">
      <p>© 2025 AniVerse. Bold by design.</p>
      <div className="flex items-center gap-4">Status | API | v2.4.1</div>
    </div>
  </div>
</motion.footer>
```

- Top edge: 3px solid foreground border.
- Above-border shadow: `0 -4px 0 var(--neo-shadow)` — 4px black slab rising upward (mirrors the sidebar's right-side shadow).
- 3-col mobile / 4-col desktop grid.
- Brand block is `hidden md:block` — mobile users see only 3 link columns.
- Bottom separator: `pt-8 border-t-2 border-foreground/10`.

## Compose layout mapping

| Tailwind | Compose |
|---|---|
| `mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4` | `Modifier.padding(start=8.dp, end=8.dp, top=12.dp)` + `.width(8.dp at sm / 24.dp at md)` via `windowSizeClass` |
| `max-w-[1280px] mx-auto` | `Modifier.widthIn(max = 1280.dp).align(Alignment.CenterHorizontally)` inside a `Box(maxWidth = Infinity)` |
| `flex items-center justify-between` | `Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)` |
| `flex gap-4 md:gap-5 overflow-x-auto no-scrollbar` | `LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 8.dp))` |
| `grid grid-cols-3 md:grid-cols-4 gap-4 md:gap-8` | `LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), ...)`, or `Row` of weighted `Column`s with `Arrangement.spacedBy` |
| `space-y-6 sm:space-y-8 md:space-y-14` | `Column(verticalArrangement = Arrangement.spacedBy(24.dp))` (override per breakpoint) |
| `px-2 sm:px-4 md:px-8 lg:px-12` | `PaddingValues(horizontal = 8.dp)` (override per `WindowSizeClass`) |
| `h-[200px] sm:h-[395px] md:h-[432px] lg:h-[476px]` | `Modifier.height(200.dp)` + `with(LocalDensity) { ... }` or `BoxWithConstraints` to switch on `maxHeight` |
| `absolute left-0 top-0 bottom-0 w-10 ... z-10` | `Modifier.align(Alignment.Start)` inside a `Box`, `.fillMaxHeight().width(40.dp).zIndex(10f)` |
| `pl-8 sm:pl-9 md:pl-14` | `Modifier.padding(start = 32.dp)` (override per `WindowSizeClass`) |
| `border-t-[3px] border-foreground` | `Modifier.drawBehind { drawRect(color = foreground, topLeft = Offset(0, 0), size = Size(size.width, 3.dp.toPx())) }` or `Modifier.border(width = 3.dp, color = foreground, shape = RectangleShape)` on top edge only |

For responsive values, the recommended pattern is to read `LocalWindowSizeClass.current` (or `LocalConfiguration.current.screenWidthDp`) at the top of each composable and pick the right Dp value — Compose doesn't have Tailwind's automatic breakpoint prefixes.

## Open layout questions

1. **Inconsistent border widths.** The template uses 1px / 1.5px / 2px / 2.5px / 3px / 3.5px borders across different elements. For Compose we should pick **a strict 3-tier scale** (e.g. 2 / 3 / 3.5dp) and map every existing usage to one of those tiers — otherwise the design will drift.
2. **Inconsistent radius scale.** Same issue — 4 / 5 / 6 / 8 / 10 / 12px. Pick a strict scale (e.g. 4 / 6 / 8 / 10 / 12dp) and consolidate.
3. **Inline `style={{ boxShadow: ... }}` overrides.** Many elements set their shadow color inline rather than via a token-based class. In Compose, all shadow colors should flow through `LocalNeoColors` to allow theme switching.
4. **Mobile-only adaptive text on hero.** The `useImageBrightness` hook + adaptive text color is mobile-only in the template. For ANI-KUTA on Android (always mobile-class), this becomes the only mode — verify we want to keep the desktop "always-on-card" treatment for tablet/landscape.
5. **`h-dvh` vs `h-screen`.** Sidebar uses `h-dvh lg:h-screen` — dynamic viewport height on mobile (handles mobile browser chrome), screen height on desktop. Compose: just use `Modifier.fillMaxHeight()` inside a `Scaffold` — handles both cases natively.
