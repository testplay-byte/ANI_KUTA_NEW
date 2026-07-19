# Design 4 — Coffee (AniVerse Notebook) — UI Element Specs

> The template's `src/components/ui/` ships only **4 shadcn components** (`input`, `tooltip`, `toast`, `toaster`) even though `package.json` declares ~25 `@radix-ui/*` packages. Every other UI element in the homepage is **hand-written inline in `src/app/page.tsx`** (2148 lines, all custom components). This document catalogs both: the actual shadcn components AND the bespoke elements used in the homepage.

## 1. shadcn Input (`src/components/ui/input.tsx`)

Full source (22 lines):

```tsx
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30 border-input flex h-9 w-full min-w-0 rounded-md border bg-transparent px-3 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
        "focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]",
        "aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive",
        className
      )}
      {...props}
    />
  )
}
```

| Spec | Value |
|---|---|
| Height | `h-9` (36px) |
| Padding | `px-3 py-1` (12px / 4px) |
| Radius | `rounded-md` (8px) |
| Border | `border border-input` (`#D5C8B8` light / `#4A4240` dark) |
| Text size | `text-base md:text-sm` (16px mobile, 14px desktop) |
| Background | `bg-transparent` (uses parent's bg); `dark:bg-input/30` in dark mode |
| Shadow | `shadow-xs` (subtle) |
| Focus | `focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]` |
| Invalid | `aria-invalid:border-destructive aria-invalid:ring-destructive/20` |
| File input | inline-flex, 28px tall, transparent |

**Used in `page.tsx`** (header search, line 1733) with heavy className override for the espresso header:
```
bg-white/10 border-white/15 text-white placeholder:text-white/40
rounded-lg pl-8 pr-3 h-[38px]
focus:border-primary/60 focus:ring-primary/20 hover:bg-white/15
max-w-[120px] sm:max-w-[240px] md:max-w-[320px]
```
i.e. transparent white tint on espresso bar, search icon at `left-2.5`.

## 2. shadcn Tooltip (`src/components/ui/tooltip.tsx`)

Standard Radix Tooltip wrapper. Defaults:

| Spec | Value |
|---|---|
| Delay | `delayDuration={0}` (Provider default in component, but `page.tsx` overrides to 300ms at line 1677) |
| Content bg | `bg-primary` (terracotta in light, latte in dark) |
| Content text | `text-primary-foreground` |
| Content padding | `px-3 py-1.5` |
| Content radius | `rounded-md` (8px) |
| Content text | `text-xs text-balance` |
| Side offset | `0` (default) |
| Arrow | `bg-primary fill-primary size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px]` |
| Animation | `animate-in fade-in-0 zoom-in-95` enter; reverse on exit; `slide-in-from-{side}-2` direction-aware |

**Used in `page.tsx`** for the desktop theme-toggle (line 1806) and notifications (line 1831) buttons. TooltipContent is overridden with `bg-[#2A1F15] dark:bg-[#2A2218] text-white border-white/10` — a darker espresso tooltip that overrides the default primary bg.

## 3. shadcn Toast (`src/components/ui/toast.tsx`, `toaster.tsx`, `hooks/use-toast.ts`)

Classic shadcn toast system. Key specs:

| Spec | Value |
|---|---|
| Viewport position | mobile: `top-0 w-full p-4 flex-col-reverse`; desktop (`sm:`): `bottom-0 right-0 top-auto flex-col md:max-w-[420px]` |
| Toast root | `rounded-md border p-4 pr-6 shadow-lg` |
| Animation enter | `data-[state=open]:animate-in slide-in-from-top-full sm:slide-in-from-bottom-full` |
| Animation exit | `data-[state=closed]:animate-out fade-out-80 slide-out-to-right-full` |
| Swipe | `data-[swipe=move]:translate-x-[var(--radix-toast-swipe-move-x)]` |
| Variants | `default: border bg-background text-foreground`; `destructive: border-destructive bg-destructive text-destructive-foreground` |
| Close button | `absolute right-1 top-1 rounded-md p-1 text-foreground/50 opacity-0 group-hover:opacity-100` with X icon (16px) |
| Action button | `h-8 px-3 text-sm rounded-md border bg-transparent hover:bg-secondary` |
| Title | `text-sm font-semibold` |
| Description | `text-sm opacity-90` |
| Limit | `TOAST_LIMIT = 1` (only 1 toast at a time) |
| Remove delay | `TOAST_REMOVE_DELAY = 1000000` ms (~17 min — effectively never auto-remove; relies on user dismissal) |

**`useToast` hook** is a module-level singleton: `memoryState` + `listeners` array + `dispatch` function. Toasts get auto-incrementing numeric IDs. The hook exposes `{ toasts, toast, dismiss }`.

## 4. Bespoke AnimeCard (page.tsx lines 884–1011)

The signature element of the design. Specs:

| Spec | Value |
|---|---|
| Container width | `w-[140px] sm:w-[155px] md:w-[168px]`, `flex-shrink-0` |
| Card body | `rounded-lg overflow-hidden border border-border/60 cursor-pointer` |
| Image aspect | `aspect-[3/4]` (3:4 portrait) |
| Image | `absolute inset-0 w-full h-full object-cover` |
| Hover lift | `pt-3 hover:scale-[1.05] origin-top` (CSS transform on outer wrapper) |
| Image hover | `group-hover:scale-[1.06] group-hover:brightness-[0.85]` |
| Gradient overlay | `bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100` |
| Episode strip | bottom, `bg-notebook-coffee/90 dark:bg-[#0F0A08]/90 backdrop-blur-sm border-t border-white/5 px-1.5 py-[4px] rounded-b-lg` |
| SUB pill | `bg-emerald-500/25 px-1 py-[0.5px] rounded-sm border border-emerald-400/30` with Caveat 14px bold emerald-300 number + Caveat 8px emerald-400/80 "SUB" |
| DUB pill | same shape but `bg-amber-500/25 border-amber-400/30` + amber-300/amber-400 text |
| Total EP | `text-white/60` Caveat 10px |
| NEW badge | `top-2 -left-1`, `.sticky-note px-1.5 py-0.5 text-[9px] font-bold text-notebook-coffee rounded-sm` |
| Rating badge | `top-2 -right-1`, same sticky-note style + Star icon 10px (`fill-notebook-coffee text-notebook-coffee`) + Caveat 9px bold number |
| Bookmark btn | `top-2 right-10 z-20`, 28px circle `bg-gray-900/70 text-white/80 hover:bg-primary hover:text-primary-foreground border border-white/20`, hover-reveal |
| Play button | center, AnimatePresence, 44px circle `bg-primary text-primary-foreground border-2 border-primary-foreground/30 shadow-lg`, Play icon 18px (`fill-primary-foreground`) |
| Title below | `mt-2.5 px-0.5`, Caveat 16px bold `truncate leading-tight` |
| Genre below | `text-[11px] text-muted-foreground mt-0.5` |
| Skeleton | `skeleton-shimmer-light` shimmer; fades to `bg-muted/30 opacity-0` when image loaded |
| Image loading | `loading="lazy"`; `onLoad` flips `imageLoaded` state |

## 5. Bespoke ContentRow (page.tsx lines 1013–1099)

| Spec | Value |
|---|---|
| Section header | `flex items-center justify-between mb-2 md:mb-5 px-2 sm:px-4 md:px-0` |
| Title block | `.notebook-tab` (3px primary bar at `left:0` via `::before`) + `flex items-center gap-2.5` |
| Icon chip | `w-7 h-7 rounded-md {accentColor} text-white flex items-center justify-center`, icon 14px |
| Title text | `text-xl font-bold tracking-tight` + Caveat 26px override |
| "See All" button | `flex items-center gap-1 text-sm text-muted-foreground hover:text-primary font-medium` + ArrowRight 14px |
| Scroll container | `flex gap-5 overflow-x-auto overflow-y-clip no-scrollbar px-2 sm:px-4 md:px-2 py-2 md:py-6` |
| Left arrow (desktop) | `absolute left-0 top-0 bottom-0 z-20`, 40px circle `bg-gray-800/90 dark:bg-gray-900/90 border border-white/15 shadow-lg text-white hover:bg-gray-700`, ChevronLeft 20px; `opacity-0 group-hover/row:opacity-100`; `hidden md:flex` |
| Right arrow | mirrored |
| Right edge fade | `absolute right-0 top-0 bottom-0 w-8 sm:w-12 md:w-16 bg-gradient-to-l from-background via-background/50 to-transparent pointer-events-none z-10` |
| Left edge fade | mirrored |
| Stagger animation | `motion.section variants={sectionFade} viewport={{ once: true, margin: "-60px" }}` |

## 6. Bespoke GenreCard (page.tsx lines 1102–1129)

| Spec | Value |
|---|---|
| Container | `flex-1 min-w-0 max-w-[200px]` |
| Card body | `rounded-xl border border-border/60 paper-texture p-4 flex flex-col justify-between h-[100px]` |
| Hover border | `hover:border-primary/60 hover:shadow-lg hover:shadow-primary/10` |
| Washi tape | `-top-1.5 left-1/2 -translate-x-1/2 w-12 h-3 bg-gradient-to-r ${genre.color} opacity-40 rounded-sm rotate-[-1deg]` |
| Icon | 20px, `text-primary` |
| Title | Caveat 18px semibold |
| Framer hover | `whileHover: { scale: 1.03, y: -4 }`, `whileTap: { scale: 0.97 }`, duration 0.2 ease `[0,0,0.2,1]` |

## 7. Bespoke NextReleasingSection timeline (page.tsx lines 1257–1623)

The most complex element. Specs:

### Container

- Outer: `rounded-xl border border-border bg-card shadow-lg overflow-visible mx-2 sm:mx-4 md:mx-0`
- Padding: `p-3 sm:p-4`
- Section header: same `notebook-tab` pattern as ContentRow, icon chip uses `bg-notebook-sage`.

### Vertical timeline line (lines 1504–1528)

- Position: `absolute left-[12px] -translate-x-1/2 top-4 bottom-4 rounded-full z-0`
- Width: `3px`
- Background: `linear-gradient(to bottom, ${stops})` where stops join group colors with hard transitions at proportional offsets (e.g. if today has 2/6 items, stop is `#047857 0%, #047857 33.3%, #d97706 33.3%, #d97706 66.6%, #1d4ed8 66.6%, #1d4ed8 100%`)
- Opacity: `0.5`

### Group header

- `flex items-center gap-3 mb-2 pl-6 ml-[3px]`
- Badge: `text-[11px] font-bold px-2.5 py-0.5 rounded-md border` + group badge class
- Divider: `flex-1 h-px bg-border/40`

### Entry card (per anime)

- Layout: `flex gap-3 sm:gap-4`
- Dot column: `flex-shrink-0 w-6 flex items-center justify-center relative` with 14×14px `rounded-full` dot + `ring-2 ring-{color}/20`
- Card body: `flex-1 flex items-center gap-3 sm:gap-4 p-2.5 sm:p-3 rounded-lg border border-border/50 bg-background/60 cursor-pointer`
- Hover: `hover:bg-accent/40 hover:border-primary/30 hover:shadow-md hover:grayscale-0 active:shadow-none active:translate-x-[1px] active:translate-y-[1px]`
- Tomorrow/later entries: `grayscale-[50%]` / `grayscale` until hover
- Cover thumb: `w-[40px] h-[56px] sm:w-[50px] sm:h-[70px] rounded-md overflow-hidden border border-border/50 shadow-sm`, `object-cover` image
- Title: Caveat 16px bold `truncate`
- EP badge: `text-[10px] font-bold text-primary bg-primary/10 px-1.5 py-0.5 rounded border border-primary/15`
- Time label: `text-[10px] sm:text-[11px] font-semibold text-foreground/80 bg-muted/80 px-2 py-0.5 rounded border border-border/50` + Clock icon 10px

### Hover popup (lines 1602–1619)

- Size: `w-[300px] sm:w-[340px] md:w-[380px]`
- Surface: `rounded-xl border border-border/60 bg-card shadow-2xl overflow-hidden`
- Position: `fixed z-[100]` with calculated `top`/`left` (placement-aware — above or below the entry)
- Animation: scale 0.95→1, y ±8 (placement-aware), duration 0.2, ease `[0.34,1.56,0.64,1]` (back-out spring)
- Dismissal: 200ms hide timeout on mouse leave; click-outside; scroll; resize
- Stillness timer: 300ms hover delay before showing (avoids flicker when sweeping across entries)
- Touch: popup trigger disabled on touch devices (`"ontouchstart" in window`); tap toggles popup instead

### Popup content (lines 1200–1255)

- Optional banner: `h-[70px] sm:h-[100px]` image with `bg-gradient-to-t from-[hsl(var(--card))]` overlay
- Or 1px gradient bar `from-primary via-primary/60 to-primary`
- Title: `text-base font-bold line-clamp-2`
- Episode pill: `text-[11px] font-bold text-primary bg-primary/10 px-2 py-0.5 rounded-md border border-primary/15` + Play icon 10px + "Episode N"
- Time: `text-[11px] font-semibold text-muted-foreground` + Clock 10px
- Description: `text-xs text-muted-foreground line-clamp-3`
- Genre chips: `text-[10px] font-semibold px-2 py-0.5 rounded-full bg-accent text-accent-foreground border border-border/50`
- Footer meta: `flex items-center gap-3 pt-2.5 border-t border-border/60 text-[11px] text-muted-foreground` — amber star + rating + aired/total + type + year

## 8. Bespoke Header (page.tsx lines 1679–1847)

| Spec | Value |
|---|---|
| Wrapper | `mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4 mb-3 md:mb-6 relative z-50` |
| Bar | `bg-[#6B5240] dark:bg-[#2D2218] rounded-2xl shadow-lg border border-white/10` |
| Inner | `max-w-[1280px] mx-auto px-4 md:px-6 lg:px-8` |
| Height | `h-14 md:h-16` (56px / 64px) |
| Layout | `flex items-center justify-between` |
| Logo box | `w-8 h-8 rounded-lg bg-primary text-primary-foreground flex items-center justify-center shadow-sm` + BookOpen icon 16px |
| Brand text | `text-2xl font-bold tracking-tight text-white` + Caveat 28px override; "Ani" white + "Verse" primary |
| Desktop nav | `hidden md:flex items-center gap-0.5 bg-white/10 rounded-xl p-1` |
| Nav button | `relative flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-sm font-medium transition-colors duration-200 whitespace-nowrap` |
| Nav active | `text-white` (vs `text-white/50 hover:text-white/80` for inactive) |
| Nav active pill | `motion.div layoutId="nav-pill"` `absolute inset-0 bg-white/15 rounded-lg border border-white/10` with spring `{ stiffness: 400, damping: 30 }` |
| Search | shadcn `<Input>` with override (see section 1) |
| Hamburger | `md:hidden w-8 h-8 rounded-lg text-white/80 hover:bg-white/10` with Menu/X icon 16px |
| Mobile dropdown | `fixed top-[68px] right-3 w-44 rounded-xl bg-[#3D2E1F] dark:bg-[#1A1410] border border-white/15 shadow-2xl overflow-hidden z-[200]` |
| Dropdown item | `w-full flex items-center gap-2.5 px-4 py-2.5 text-sm font-medium` + 16px icon; active = `text-primary bg-primary/10`, inactive = `text-white/70 hover:text-white hover:bg-white/5` |
| Dropdown divider | `border-t border-white/10 my-1` |
| Theme toggle (desktop) | `hidden md:flex w-8 h-8 rounded-lg items-center justify-center text-white/70 hover:text-white hover:bg-white/10` |
| Theme icon | AnimatePresence swap Sun↔Moon, rotate ±90° + opacity, duration 0.2 |
| Notifications | `hidden md:flex relative w-8 h-8 rounded-lg` + Bell 16px + `top-1 right-1 w-2 h-2 bg-primary rounded-full` dot |
| Backdrop | `fixed inset-0 z-[199]` for closing mobile menu on outside tap |
| Animation | `motion.div initial={{ y: -20, opacity: 0 }} animate={{ y: 0, opacity: 1 }}` duration 0.4 ease `[0,0,0.2,1]` |

## 9. Bespoke Hero carousel (page.tsx lines 1849–2007)

| Spec | Value |
|---|---|
| Container | `relative w-full h-[200px] sm:h-[395px] md:h-[432px] lg:h-[476px] rounded-2xl sm:rounded-none overflow-hidden` |
| Outer wrap | `mx-2 sm:mx-0 relative z-10` |
| BG image | `absolute h-full w-auto left-1/2 sm:left-[66.67%] -translate-x-1/2` — centered mobile, right-shifted desktop |
| BG cross-fade | AnimatePresence `mode="wait"`, opacity 0→1 over 0.8s easeInOut |
| Left gradient | `bg-gradient-to-r from-background via-background/80 sm:via-background/90 to-background/30 sm:to-background/20 z-[1]` |
| Bottom gradient | `bg-gradient-to-t from-background/70 via-transparent to-background/20 sm:to-background/30 z-[1]` |
| Coffee rings | 2× `.coffee-ring hidden lg:block` at `top:15%/20%, right:15%/12%, opacity:0.06/0.04` |
| Floating glow | `top-24 right-[20%] w-20 h-20 rounded-full bg-primary/10 blur-2xl pointer-events-none animate-gentle-float z-[1]` |
| Content wrap | `relative z-10 max-w-[1280px] mx-auto px-3 sm:px-4 md:px-8 lg:px-12 h-full flex flex-col justify-end sm:justify-center sm:items-start pointer-events-none pb-3 sm:pb-0` |
| Content width | `w-full sm:w-[45%] md:w-[40%]` |
| Text animation | `motion.div key=hero-text-${heroIndex}` initial y:20 → 0, exit y:-10, duration 0.45 ease `[0,0,0.2,1]` |
| Tags row (desktop) | `hidden sm:flex flex-wrap items-center gap-2 mb-3` |
| Sage tag | `text-[11px] font-bold px-2 py-0.5 rounded-md bg-notebook-sage/20 text-notebook-sage border border-notebook-sage/30` + Zap icon 12px + type label |
| Primary tag | `bg-primary/15 text-primary border-primary/20` + TrendingUp icon + "#N Trending" |
| Title | `text-[18px] sm:text-4xl md:text-5xl lg:text-[3.5rem] font-black leading-[1.15] tracking-tight` with `WebkitLineClamp: 1` |
| Meta row | `flex items-center gap-2 text-[10px] sm:text-sm text-muted-foreground` |
| Rating | `text-amber-600 dark:text-amber-400 font-bold` + Star 10–14px `fill-amber-500` |
| Dot separator | `w-1 h-1 rounded-full bg-muted-foreground/40` |
| Description | `hidden sm:block text-sm md:text-[15px] text-muted-foreground leading-relaxed max-w-md` with 2-line clamp |
| Watch Now btn | `motion.button whileHover scale:1.03 whileTap scale:0.97` + `flex items-center gap-1.5 sm:gap-2 px-3 sm:px-6 py-1.5 sm:py-3 rounded-xl bg-primary text-primary-foreground font-semibold text-[11px] sm:text-sm shadow-md hover:shadow-lg` + Play icon |
| Details btn (desktop) | `hidden sm:flex items-center gap-2 px-5 py-3 rounded-xl bg-notebook-paper border border-border text-foreground font-medium text-sm hover:bg-accent shadow-sm` + Info icon |
| Bookmark btn (desktop) | `hidden sm:flex w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-notebook-paper border border-border items-center justify-center text-muted-foreground hover:text-primary hover:border-primary/30 shadow-sm` + BookmarkPlus icon |
| Desktop dots | `absolute bottom-5 left-1/2 -translate-x-1/2 z-10 flex items-center gap-2 pointer-events-auto hidden sm:flex` |
| Active dot | `w-8 bg-primary h-1.5 rounded-full transition-all duration-300` |
| Inactive dot | `w-2 bg-muted-foreground/30 hover:bg-muted-foreground/50 h-1.5 rounded-full` |
| Mobile dots | `flex sm:hidden items-center justify-center gap-2 py-2.5` (same dot styling) |
| Auto-advance | `setInterval` 6000ms cycling `heroIndex` through `min(5, heroItems.length)` items |
| Touch swipe | `onTouchStart` records `{startX, startY}`; `onTouchEnd` computes dx/dy; if `|dx| >= 40 && |dx| > |dy|` advances (dx<0 → next, dx>0 → prev) |

## 10. Bespoke Footer (page.tsx lines 2044–2144)

| Spec | Value |
|---|---|
| Outer | `motion.footer` opacity 0→1 on view, duration 0.4 |
| Surface | `border-t border-border bg-notebook-paper/80 backdrop-blur-sm mt-auto` |
| Inner | `max-w-[1280px] mx-auto px-2 sm:px-4 md:px-8 lg:px-12 py-8 md:py-14` |
| Grid | `grid grid-cols-3 md:grid-cols-4 gap-4 md:gap-8 mb-6 md:mb-10` |
| Brand col | `hidden md:block col-span-1` — logo + 26px Caveat brand + `text-sm text-muted-foreground leading-relaxed max-w-xs` tagline |
| Sub-heading | `text-xs md:text-sm font-bold mb-2 md:mb-3 text-foreground` + Caveat 16px override |
| Links | `text-xs text-muted-foreground hover:text-primary transition-colors duration-150` |
| Bottom bar | `flex flex-col sm:flex-row items-center justify-between gap-3 pt-8 border-t border-border` |
| Copyright | `text-xs text-muted-foreground` — `© 2025 AniVerse. Brewed with care.` |
| Status links | `text-xs text-muted-foreground hover:text-primary` — Status / API / v2.4.1 |

## 11. Stationery effect utility classes (globals.css lines 190–425)

These are CSS classes (not components) applied via Tailwind className:

| Class | Effect | Source |
|---|---|---|
| `.notebook-lines` | Ruled horizontal lines, 32px row pitch, `--notebook-ruled` color | 191–202 |
| `.notebook-margin` | Adds `::before` 2px red vertical line at `left:48px` | 205–214 |
| `.paper-texture` | `--notebook-paper` bg + `inset 0 0 80px rgba(139,115,85,0.06)` shadow | 217–222 |
| `.paper-edge` | `::after` 20px dog-ear bottom-right corner | 241–257 |
| `.washi-tape` | `::before` 80×18px rotated -2° tape strip in `--notebook-tape` color | 260–274 |
| `.sticky-note` | `--notebook-sticky` bg + `2px 2px 6px rgba(0,0,0,0.08)` + inset bottom shadow | 277–282 |
| `.coffee-ring` | 120×120px circle, 3px `rgba(111,78,55,0.08)` border, decoration only | 285–292 |
| `.spiral-binding` | `::before` 24px-wide column of 4px dots every 40px on left edge | 295–312 |
| `.ink-underline` | `::after` 3px primary bar at -0.5° rotation, opacity 0.3 | 315–330 |
| `.custom-scrollbar` | 6px scrollbar with `--notebook-ruled` thumb | 333–346 |
| `.no-scrollbar` | Hide scrollbar (webkit + firefox) | 349–355 |
| `.skeleton-shimmer-light` | 1.5s shimmer keyframe gradient (see colors doc) | 363–372 |
| `.card-paper-hover` | Hover: `translateY(-3px) rotate(-0.3deg)` + 2-layer shadow, 200ms | 381–389 |
| `.notebook-tab` | `padding-left:16px` + `::before` 3px primary bar | 392–406 |
| `.animate-gentle-float` | `gentle-float` keyframe 5s ease-in-out infinite (±6px Y, ±1° rot) | 414–416 |

## 12. Hooks

### `useIsMobile` (page.tsx lines 829–838, custom)

Returns true when `window.innerWidth < 640` (sm breakpoint). Used in `AnimeCard` to bypass `whileInView` animations on mobile (where horizontal scroll breaks IntersectionObserver).

### `useIsMobile` (src/hooks/use-mobile.ts, separate)

A second copy (768px breakpoint) — used by shadcn components. The two coexist; the inline one is mobile-first for the homepage.

### `useHorizontalScroll` (page.tsx lines 845–877)

Returns `{ scrollRef, canScrollLeft, canScrollRight, scroll }`. Attaches passive scroll + resize listeners to detect edges. `scroll("left"|"right")` scrolls by `1.5 × clientWidth` with `behavior: "smooth"`.

## Compose element mapping (summary)

| Web element | Compose element |
|---|---|
| shadcn `<Input>` | `OutlinedTextField` with coffee colors |
| shadcn `<Tooltip>` | `RichTooltipBox` (Material 3) |
| shadcn `<Toast>` | `Snackbar` with custom CoffeeSnackbarColors |
| AnimeCard | Custom `AnimeCardComposable` — `Card` + `AsyncImage` + `Badge` (sticky) + `IconButton` (bookmark/play) |
| ContentRow | `LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp))` with hidden scrollbar |
| GenreCard | `Surface(shape = RoundedCornerShape(14.dp))` + `Canvas` for washi tape |
| NextReleasingSection | `Column` + `Canvas` drawing the gradient line + `LazyColumn` of entries |
| Header | `TopAppBar`-like custom `Row` in `Surface(color = espressoBar)` |
| Hero carousel | `BoxWithConstraints` + `Crossfade(targetState = heroIndex)` for bg, `AnimatedContent` for text |
| Footer | `Column` with `Row` grid columns |
| `.sticky-note` | `Surface(color = sticky, shape = RoundedCornerShape(2.dp), shadowElevation = 2.dp)` |
| `.paper-texture` | `Modifier.drawBehind { drawRect(paper); drawCircleGrid(dotGrid) }` |
| `.card-paper-hover` | `Modifier.pointerInput { detectTapGestures }` + `graphicsLayer { translationY = -3.dp; rotationZ = -0.3f }` on hover state |
| `.washi-tape` | Small `Canvas` drawing rotated gradient rect |
| `.coffee-ring` | `Canvas` with 2× `drawCircle(style = Stroke(width = 3.dp))` |
| `.ink-underline` | `Box(Modifier.matchParentSize().background(primary.copy(alpha = 0.3f)).rotate(-0.5f))` behind `Text` |
| `.notebook-tab` | `Row` with 3.dp-wide `Box(background = primary)` then content |
| `.skeleton-shimmer-light` | `Modifier.drawBehind` + `rememberInfiniteTransition` translating a linear gradient |
| `.animate-gentle-float` | `rememberInfiniteTransition` + `graphicsLayer { translationY = floatAnim.value.dp; rotationZ = rotAnim.value }` |
