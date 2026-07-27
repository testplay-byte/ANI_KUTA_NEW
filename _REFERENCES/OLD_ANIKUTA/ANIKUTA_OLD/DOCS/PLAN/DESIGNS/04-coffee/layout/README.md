# Design 4 — Coffee (AniVerse Notebook) — Layout, Spacing, Borders

## Layout grid

The template uses no CSS Grid for the homepage except the footer (`grid grid-cols-3 md:grid-cols-4 gap-4 md:gap-8`). Everything else is **Flexbox + horizontal scroll containers**.

### Max-width container

`max-w-[1280px]` is used 6 times in `page.tsx`:
- Header inner (line 1687): `max-w-[1280px] mx-auto px-4 md:px-6 lg:px-8`
- Hero content (line 1898): `max-w-[1280px] mx-auto px-3 sm:px-4 md:px-8 lg:px-12 h-full flex flex-col justify-end sm:justify-center sm:items-start`
- Main content (line 2010): `flex-1 max-w-[1280px] w-full mx-auto px-2 sm:px-4 md:px-8 lg:px-12 space-y-4 sm:space-y-6 md:space-y-14 pb-16 md:pb-24 mt-3 sm:mt-4 md:mt-0`
- Footer inner (line 2051): `max-w-[1280px] mx-auto px-2 sm:px-4 md:px-8 lg:px-12 py-8 md:py-14`

So **1280px is the design max-width**, with horizontal padding that escalates by breakpoint: `px-2` (8px) → `px-4` (16px) → `px-8` (32px) → `px-12` (48px).

### Vertical rhythm

Main content vertical spacing (`space-y-*`):
- Mobile: `space-y-4` = 16px between sections
- `sm:` (640+): `space-y-6` = 24px
- `md:` (768+): `space-y-14` = 56px

The escalation is intentional — mobile keeps sections tight (less scroll), desktop breathes.

Section header bottom margin:
- `mb-2 md:mb-5` (8px mobile / 20px desktop)

Outer page margin around header:
- `mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4 mb-3 md:mb-6` (8/12/24px horizontal, 12/16px top, 12/24px bottom)

Outer page margin around hero:
- `mx-2 sm:mx-0` (mobile gets 8px inset, desktop full-bleed)

Section header horizontal padding:
- `px-2 sm:px-4 md:px-0` (mobile+tablet inset, desktop flush with main column)

## Tailwind v4 breakpoints

Tailwind v4 default breakpoints (template doesn't override):
- `sm` = 640px
- `md` = 768px
- `lg` = 1024px
- `xl` = 1280px
- `2xl` = 1536px

The template uses `sm` / `md` / `lg` heavily. `xl` and `2xl` are rarely used — the 1280px max-width container handles large screens.

`useIsMobile` inline hook (page.tsx line 832) uses `640` (sm) as its threshold. The separate `src/hooks/use-mobile.ts` uses `768` (md). Both coexist — the inline is for the homepage, the file is for shadcn.

## Responsive behavior table

| Element | Mobile (`<sm`) | Tablet (`sm`–`md`) | Desktop (`md`+) | Wide (`lg`+) |
|---|---|---|---|---|
| Header outer margin | `mx-2 mt-3 mb-3` | `mx-3 mt-4 mb-3` | `mx-6 mt-4 mb-6` | same |
| Header height | `h-14` (56px) | same | `h-16` (64px) | same |
| Nav visible | hamburger | hamburger | desktop tabs | desktop tabs |
| Hero height | `h-[200px]` | `h-[395px]` | `h-[432px]` | `h-[476px]` |
| Hero corner radius | `rounded-2xl` | `rounded-none` | `rounded-none` | `rounded-none` |
| Hero description | hidden | visible | visible | visible |
| Hero "Details" btn | hidden | visible | visible | visible |
| Hero dots | below hero (mobile row) | bottom-center overlay | bottom-center overlay | bottom-center overlay |
| Coffee ring decorations | hidden | hidden | hidden | visible (`lg:block`) |
| Floating glow | visible | visible | visible | visible |
| Main padding | `px-2` | `px-4` | `px-8` | `px-12` |
| Section vertical gap | `space-y-4` (16px) | `space-y-6` (24px) | `space-y-14` (56px) | same |
| AnimeCard width | `w-[140px]` | `w-[155px]` | `w-[168px]` | same |
| ContentRow scroll arrows | hidden | hidden | visible on hover | visible on hover |
| Edge fade width | `w-8` (32px) | `w-12` (48px) | `w-16` (64px) | same |
| Footer grid | `grid-cols-3` (no brand col) | same | `grid-cols-4` (with brand col) | same |

## Spacing scale (Tailwind defaults, but key values used)

| Tailwind | px | Where used |
|---|---|---|
| `0.5` | 2px | tiny gaps (badges) |
| `1` | 4px | icon-text gaps, small padding |
| `1.5` | 6px | small gaps |
| `2` | 8px | default gap, default padding |
| `2.5` | 10px | small padding |
| `3` | 12px | medium padding |
| `3.5` | 14px | button padding |
| `4` | 16px | section header bottom, default gap-4 |
| `5` | 20px | `gap-5` between cards in row |
| `6` | 24px | section spacing sm, header inner padding |
| `8` | 32px | section spacing md, padding |
| `10` | 40px | not commonly used |
| `12` | 48px | `px-12` wide padding |
| `14` | 56px | section spacing md (main space-y-14) |
| `16` | 64px | `pb-16` mobile bottom padding |

## Border radius scale

Defined in globals.css lines 57–60:

```css
--radius: 0.625rem;             /* 10px — base */
--radius-sm: calc(var(--radius) - 4px);   /* 6px */
--radius-md: calc(var(--radius) - 2px);   /* 8px */
--radius-lg: var(--radius);              /* 10px */
--radius-xl: calc(var(--radius) + 4px);  /* 14px */
```

These are mapped to Tailwind's `rounded-sm/md/lg/xl` via `@theme inline` (Tailwind v4 auto-generates the scale from the `--radius-*` tokens).

### Actual rounded utility classes used in page.tsx

| Class | px | Where |
|---|---|---|
| `rounded-sm` | 2px | (not used in homepage — defined in sticky-note badge inline as `rounded-sm` Tailwind default = 2px in v4) |
| `rounded-md` | 8px | Input, tooltip content, sticky-note badges, EP badge, timeline group badge, dots, search input override, hamburger button, theme toggle, mobile menu items, footer links |
| `rounded-lg` | 10px | AnimeCard body, hero content blocks, hero CTA buttons, header logo box, timeline entry card, nav tab buttons |
| `rounded-xl` | 14px | GenreCard, NextReleasingSection card, hover popup, mobile menu dropdown, desktop nav tab container, hero CTA "Watch Now" |
| `rounded-2xl` | ~16px | Header bar, hero container (mobile only) |
| `rounded-full` | 9999px | Bookmark/play round buttons, dots, coffee rings, floating glow, theme-toggle dots in mobile menu |
| `rounded-b-lg` | 10px bottom | AnimeCard episode strip |
| `rounded-t` (none) | — | (not used) |

### Border widths

Tailwind default `border` = 1px throughout. No 2px or 3px borders used (except inside CSS pseudo-elements which use 2-3px hardcoded values).

| Use | Color | Width | Opacity |
|---|---|---|---|
| Default element border | `border-border` | 1px | 100% |
| Card border | `border-border/60` | 1px | 60% |
| Light card border | `border-border/50` | 1px | 50% |
| Espresso header border | `border-white/10` | 1px | 10% |
| Mobile dropdown border | `border-white/15` | 1px | 15% |
| Bookmark button border | `border-white/20` | 1px | 20% |
| Play button border | `border-2 border-primary-foreground/30` | 2px | 30% |
| SUB/DUB pill border | `border-emerald-400/30` / `border-amber-400/30` | 1px | 30% |
| Section divider | `h-px bg-border/40` | 1px | 40% |
| Footer bottom divider | `border-t border-border` | 1px | 100% |
| Global default (`* { @apply border-border }`) | `border-border` | 1px | 100% |

## Global border default

globals.css line 171: `* { @apply border-border outline-ring/50; }`

Every element defaults to the warm coffee border color and primary-color focus ring outline at 50% opacity. This is the shadcn default base style.

## Shadows

See `README.md` "Surfaces & elevation" section. Summary:

| Utility | Used for |
|---|---|
| `shadow-xs` | Input |
| `shadow-sm` | Logo box, bookmark/play buttons, footer sub-headers, cover thumb, "Details" btn |
| `shadow-md` | Hero "Watch Now" btn, timeline entry hover |
| `shadow-lg` | Header bar, NextReleasingSection card, hero CTA hover, sticky-note badges |
| `shadow-2xl` | Hover popup, mobile menu dropdown |
| `.card-paper-hover:hover` | Card lift (translateY -3px rotate -0.3deg + 2-layer shadow) |
| `.paper-texture` | Paper surface (drop + 80px inset) |
| `.sticky-note` | Sticky-note badge (2px 2px 6px drop + inset bottom) |

## Pseudo-element decoration positions

| Effect | Position | Size |
|---|---|---|
| `.notebook-margin::before` | `left: 48px`, top-to-bottom | 2px wide vertical |
| `.paper-edge::after` | `bottom:0 right:0` | 20×20px triangle |
| `.washi-tape::before` | `top: -6px`, centered horizontally | 80×18px rotated -2° |
| `.spiral-binding::before` | `left:0 top:0 bottom:0` | 24px wide column of 4px dots every 40px |
| `.ink-underline::after` | `bottom: -2px left: -4px right: -4px` | 3px tall primary bar |
| `.notebook-tab::before` | `left:0 top:4px bottom:4px` | 3px wide primary bar |
| `html::before` (dot grid) | `inset: 0` fixed, `z-index: -1` | full viewport, 20×20px tile |

## Compose layout adaptation

- `max-w-[1280px] mx-auto` → `Box(Modifier.fillMaxWidth().widthIn(max = 1280.dp).padding(horizontal = …))` inside a `Surface` or use `Layout` with measurement.
- Tailwind responsive padding `px-2 sm:px-4 md:px-8 lg:px-12` → read `WindowSizeClass` from `currentWindowAdaptiveInfo()`:
  - Compact (<600): `horizontal = 8.dp`
  - Medium (600–840): `horizontal = 32.dp`
  - Expanded (840+): `horizontal = 48.dp`
- Tailwind `space-y-*` between sections → `Column(verticalArrangement = Arrangement.spacedBy(spacing))` where `spacing` switches by WindowSizeClass.
- Border 1px at opacity → `Modifier.border(width = 1.dp, color = borderColor.copy(alpha = 0.6f))`.
- `rounded-md` (8px) → `RoundedCornerShape(8.dp)`; build a `CoffeeShapes` `Shapes` object:
  ```kotlin
  val CoffeeShapes = Shapes(
      small = RoundedCornerShape(6.dp),
      medium = RoundedCornerShape(8.dp),
      large = RoundedCornerShape(10.dp),
      extraLarge = RoundedCornerShape(14.dp),
  )
  ```
- Sticky-note badge = `Surface(shape = RoundedCornerShape(2.dp), shadowElevation = 2.dp, color = sticky)`.
- Custom 16px radius for header = `RoundedCornerShape(16.dp)`.
- `rounded-full` = `CircleShape`.
- Pseudo-element decorations → `Modifier.drawWithContent` + `drawBehind` for the dot grid, washi tape, coffee ring, ink underline, spiral binding. Each is a small DrawScope block drawing geometric shapes at the same coordinates as the CSS.
