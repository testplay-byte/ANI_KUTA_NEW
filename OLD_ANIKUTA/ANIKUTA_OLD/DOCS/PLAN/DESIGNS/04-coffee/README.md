# Design 4 — Coffee (AniVerse Notebook)

Warm, coffee-stained notebook aesthetic — the most full-featured of the four templates, built on Next.js 16 + Tailwind v4 + shadcn/ui (new-york) + framer-motion.

## Source

- Zip filename: `COFFEE_TEMPLATE.zip`.
- Internal identity (from code + metadata): **"AniVerse — Your Anime Notebook"**. The stylesheet header calls itself the *"AniVerse — Notebook Design System"*, light theme is named *"Coffee White"* and dark theme *"Dark Coffee"* (`src/app/globals.css` lines 4–6, 71–72, 119). So the design is a **coffee-themed notebook** language; the "Coffee" zip name and the in-code "Notebook" name describe the same design.
- Source path: `/home/z/my-project/upload/coffee-extracted/aniverse-template/`.
- Original stack: Next.js 16 (App Router, client components), React 19, Tailwind CSS v4 (CSS-based config via `@theme inline`), shadcn/ui (new-york style, neutral base color), framer-motion ^12.23.2, lucide-react ^0.525.0, Prisma 6 + SQLite (scaffold only — schema has `User`/`Post`, unused in the homepage demo).
- ANI-KUTA target: Android Jetpack Compose. This document extracts the web design language and adapts tokens/patterns to Compose/Material 3.
- See sibling `03-notebook/` for a separate notebook-themed design (different source template).

## Design language & philosophy

The design is **a coffee-stained paper notebook**, not flat Material. Quoting the stylesheet header (globals.css lines 4–18):

> "AniVerse — Notebook Design System … defines the complete visual language for the notebook-themed UI … Color palettes (light 'Coffee White' + dark 'Dark Coffee') … Notebook-specific CSS tokens (paper, ruled lines, margins) … Visual effect utilities (paper texture, washi tape, sticky notes …)".

Five pillars extracted from the code:

1. **Paper as the primary surface.** The page background is `#F0E6D8` warm cream (light) / `#1A1412` near-black coffee (dark). A fixed dot-grid overlay (`html::before`, opacity 0.4 light / 0.06 dark) sits behind everything to suggest dotted bullet-journal paper. Cards use `--notebook-paper: #FDF8F2` with an inset 80px warm shadow to feel like a sheet laid on a desk.
2. **Coffee as the brand accent.** `--primary: #B8653F` (terracotta/coffee) in light, `#D4956A` (latte) in dark. A dedicated `--notebook-coffee: #5C3D28` deep-roast brown is used for text on sticky-note badges and for the episode-info strip at the bottom of every anime card. The header bar is an even darker coffee `bg-[#6B5240] dark:bg-[#2D2218]` regardless of theme — the nav stays "espresso" at all times.
3. **Handwriting as identity.** A third font, **Caveat** (Google Fonts handwritten), is loaded alongside Geist Sans/Mono and used for every title, badge, rating number, and section header. Inline `style={{ fontFamily: "var(--font-caveat), cursive" }}` appears ~12 times in `page.tsx`. This single choice transforms the UI from "another anime app" into "anime journal".
4. **Stationery effects as decoration.** CSS pseudo-elements render real notebook artifacts: ruled horizontal lines (`.notebook-lines`), a left margin red line (`.notebook-margin`), washi tape strips (`.washi-tape`), spiral-binding dot column (`.spiral-binding`), dog-ear page corner (`.paper-edge`), sticky-note badges (`.sticky-note`), coffee-ring stains (`.coffee-ring`), and an ink-underline behind headings (`.ink-underline`). These are present but used sparingly — never on every element.
5. **Subtle physical motion.** Cards lift `translateY(-3px) rotate(-0.3deg)` on hover (`.card-paper-hover`) — a slight tilt that reads as "lifting a paper card". Genre cards use the same idea via framer-motion `whileHover: { scale: 1.03, y: -4 }`. The hero "coffee ring + blurred primary glow" decoration gently floats (`@keyframes gentle-float` 5s).

## Aesthetics

- **Vibe:** cozy, warm, analog, journal-like. A cross between a Studio Ghibli still and a Moleskine notebook. Reads as "personal anime journal" rather than "streaming platform".
- **Mood words (from code):** "cozy", "warm", "brewed with care" (footer copy line 2133), "Your cozy corner for anime streaming. Jot down your favorites…" (footer tagline line 2067).
- **Density:** medium. Hero is generous (200–476px tall), content rows are tight horizontal scrollers, footer is airy. Not minimal, not cluttered.
- **Personality:** distinctive. Of the four ANI-KUTA designs, this one leans hardest into a single metaphor (coffee + notebook) and is the most "branded".

## Color system

All values quoted from `src/app/globals.css` lines 70–163. Light mode is `:root` ("Coffee White"), dark mode is `.dark` ("Dark Coffee"). Tailwind v4 maps them via `@theme inline` (lines 22–64).

### Light — Coffee White

| Token | Hex | Role |
|---|---|---|
| `--background` | `#F0E6D8` | Page background (warm cream) |
| `--foreground` | `#2E1A0E` | Body text (dark coffee) |
| `--card` | `#FDF8F2` | Card / paper surface |
| `--card-foreground` | `#2E1A0E` | Text on card |
| `--popover` | `#FDF8F2` | Popover surface |
| `--primary` | `#B8653F` | Brand / buttons / links (terracotta) |
| `--primary-foreground` | `#FFFAF5` | Text on primary |
| `--secondary` | `#E8DDD0` | Secondary surface |
| `--secondary-foreground` | `#4A3425` | Text on secondary |
| `--muted` | `#E5DAD0` | Muted surface |
| `--muted-foreground` | `#7A6450` | Muted text (warm gray-brown) |
| `--accent` | `#DFCAB5` | Accent surface |
| `--accent-foreground` | `#4A3425` | Text on accent |
| `--destructive` | `#C44040` | Destructive actions |
| `--border` | `#D5C8B8` | Default borders |
| `--input` | `#D5C8B8` | Input borders |
| `--ring` | `#B8653F` | Focus ring (matches primary) |
| `--chart-1..5` | `#B8653F` `#6B8E5B` `#C99545` `#5A7D96` `#966B94` | Chart palette (coffee / sage / gold / slate-teal / mauve) |
| `--sidebar` | `#FDF8F2` | Sidebar surface |
| `--sidebar-primary` | `#B8653F` | Sidebar brand |

### Dark — Dark Coffee

| Token | Hex | Role |
|---|---|---|
| `--background` | `#1A1412` | Page background (near-black roast) |
| `--foreground` | `#F0E0D0` | Body text (warm cream) |
| `--card` | `#2A2220` | Card surface |
| `--popover` | `#2A2220` | Popover surface |
| `--primary` | `#D4956A` | Brand (latte) |
| `--primary-foreground` | `#1A1412` | Text on primary (inverted) |
| `--secondary` | `#3A3230` | Secondary surface |
| `--muted` | `#3A3230` | Muted surface |
| `--muted-foreground` | `#A89080` | Muted text |
| `--accent` | `#3A3230` | Accent surface |
| `--destructive` | `#E06060` | Destructive |
| `--border` | `#3A3230` | Border |
| `--input` | `#4A4240` | Input border |
| `--ring` | `#D4956A` | Focus ring |
| `--chart-1..5` | `#D4956A` `#7B9E6B` `#D4A55A` `#6B8DA6` `#A87BA6` | Chart palette |

### Notebook-specific tokens (both themes)

| Token | Light | Dark | Use |
|---|---|---|---|
| `--notebook-paper` | `#FDF8F2` | `#2A2220` | Paper-surface bg (`.paper-texture`) |
| `--notebook-ruled` | `#DDD2C2` | `#3A3230` | Ruled lines + scrollbar thumb |
| `--notebook-margin` | `#D4A0A0` | `#5A3A3A` | Vertical margin red line |
| `--notebook-shadow` | `rgba(120,95,65,0.15)` | `rgba(0,0,0,0.25)` | Paper drop shadow |
| `--notebook-shadow-hover` | `rgba(120,95,65,0.25)` | `rgba(0,0,0,0.35)` | Paper hover shadow |
| `--notebook-coffee` | `#5C3D28` | `#A08060` | Sticky-note text + episode strip |
| `--notebook-latte` | `#B8956A` | `#8A7050` | Latte accent (declared, used lightly) |
| `--notebook-sage` | `#6B8E5B` | `#6B8E5B` | Sage green accent (genre/Coming-Up icon) |
| `--notebook-sticky` | `#FFF8CC` | `#3A3220` | Sticky-note badge background |
| `--notebook-tape` | `rgba(210,190,160,0.8)` | `rgba(80,70,60,0.5)` | Washi-tape decoration |

### Hardcoded UI colors (in `page.tsx`)

| Where | Light | Dark | Use |
|---|---|---|---|
| Header bar | `#6B5240` | `#2D2218` | `bg-[#6B5240] dark:bg-[#2D2218]` — espresso nav |
| Mobile menu dropdown | `#3D2E1F` | `#1A1410` | `bg-[#3D2E1F] dark:bg-[#1A1410]` |
| Tooltip override | `#2A1F15` | `#2A2218` | Darker coffee tooltip bg |
| Card episode strip | `notebook-coffee/90` | `#0F0A08/90` | Bottom strip on anime card |
| SUB pill | `emerald-500/25` bg + `emerald-300` text + `emerald-400/30` border | same | Episode-count badge (SUB) |
| DUB pill | `amber-500/25` bg + `amber-300` text + `amber-400/30` border | same | Episode-count badge (DUB) |
| Genre card gradients | `from-amber-600 to-orange-500` etc. | same | 8 genre pill gradients (page.tsx lines 779–787) |
| Timeline line colors | `#047857` today / `#d97706` tomorrow / `#1d4ed8` later | same | Vertical gradient line in Coming-Up-Next |

Full per-token table in `colors/README.md`.

## Typography

Fonts (loaded in `src/app/layout.tsx`):

| Slot | Font | Variable | Weights |
|---|---|---|---|
| Sans body | Geist (Google) | `--font-geist-sans` | default |
| Mono | Geist Mono (Google) | `--font-geist-mono` | default |
| **Handwriting** | **Caveat (Google)** | `--font-caveat` | 400, 500, 600, 700 |

Tailwind v4 bridge (globals.css line 25–27):
```
--font-sans: var(--font-geist-sans);
--font-mono: var(--font-geist-mono);
--font-hand: var(--font-caveat);
```

Usage pattern in `page.tsx` — Caveat is applied via inline style, not Tailwind class, with explicit pixel sizes:
- Hero logo: `fontFamily: var(--font-caveat), fontSize: 28px` (line 1696)
- Hero nav brand "AniVerse": same 28px Caveat (line 1696)
- Section headers (Trending/Freshly Updated/etc.): `fontSize: 26px` Caveat (lines 1043, 1181, 1488, 2076, 2095, 2114)
- Anime card title: `fontSize: 16px` Caveat bold (line 1003)
- Genre card title: `fontSize: 18px` Caveat (line 1122)
- Rating/NEW/SUB/DUB badges: `fontSize: 9–14px` Caveat bold (lines 929, 935, 941, 961)

Body copy uses Geist Sans via Tailwind text utilities (`text-sm`, `text-[11px]`, `text-xs`, `text-base`, etc.).

Mobile weight fix (globals.css lines 180–182): `.font-bold/.font-extrabold/.font-black/.font-semibold { -webkit-text-stroke: 0 }` — disables iOS auto-stroke so weights render correctly on mobile.

Hero title (line 1922): `text-[18px] sm:text-4xl md:text-5xl lg:text-[3.5rem] font-black leading-[1.15] tracking-tight` with `WebkitLineClamp: 1`.

## Borders & roundness

`--radius: 0.625rem` = 10px base (globals.css line 71).

Tailwind v4 radius scale (globals.css lines 57–60):
- `--radius-sm` = `calc(0.625rem - 4px)` = **6px**
- `--radius-md` = `calc(0.625rem - 2px)` = **8px**
- `--radius-lg` = `0.625rem` = **10px** (default)
- `--radius-xl` = `calc(0.625rem + 4px)` = **14px**

Actual rounded utility classes used in `page.tsx`:
- `rounded-md` (8px) — pill badges, input borders, sticky-note badges
- `rounded-lg` (10px) — anime cards, hero content blocks, small buttons
- `rounded-xl` (14px) — genre cards, timeline card, popup, mobile menu dropdown
- `rounded-2xl` — header bar (custom ~16px)
- `rounded-full` — bookmark/play round buttons, dots, coffee rings

Border color: `--border: #D5C8B8` (light) / `#3A3230` (dark) — warm beige / coffee. Default opacity variants used widely: `border-border/60`, `border-border/50`, `border-white/10`, `border-white/15`, `border-white/20` on the espresso header.

Global base rule (globals.css line 171): `* { @apply border-border outline-ring/50; }` — every element defaults to the warm border color.

## Surfaces & elevation

Shadows (all defined as CSS vars / utilities, not Material elevation levels):

- **Paper drop shadow** (`--notebook-shadow`): `rgba(120,95,65,0.15)` light / `rgba(0,0,0,0.25)` dark. Applied via `.paper-texture` + Tailwind `shadow-lg` / `shadow-md` / `shadow-sm` utilities.
- **Paper hover shadow** (`--notebook-shadow-hover`): `rgba(120,95,65,0.25)` light / `rgba(0,0,0,0.35)` dark. Used in `.card-paper-hover` lift.
- **Card paper hover** (`.card-paper-hover`, lines 381–389):
  ```
  transform: translateY(-3px) rotate(-0.3deg);
  box-shadow: 0 10px 28px var(--notebook-shadow-hover),
              0 3px 10px var(--notebook-shadow);
  ```
  i.e. two-layer shadow with a 0.3° tilt — the signature "lifted paper" feel.
- **Sticky-note shadow** (`.sticky-note`, lines 277–282): `2px 2px 6px rgba(0,0,0,0.08), inset 0 -2px 4px rgba(0,0,0,0.03)` — soft drop + bottom inset to read as paper sitting on a desk.
- **Inner paper glow** (`.paper-texture`, lines 217–222): `box-shadow: var(--notebook-shadow), inset 0 0 80px rgba(139,115,85,0.06)` — large soft inset for paper texture.
- Tailwind shadow utilities: `shadow-sm`, `shadow-md`, `shadow-lg`, `shadow-2xl` used directly on hero CTA, header, popup, etc.

Backgrounds:
- Page: solid `--background` + fixed dot-grid overlay (`html::before`).
- Cards: `--card` (`#FDF8F2` light / `#2A2220` dark).
- Header bar: hardcoded `#6B5240` / `#2D2218` (always espresso — does NOT follow theme tokens).
- Episode strip on cards: `--notebook-coffee/90` light / `#0F0A08/90` dark with `backdrop-blur-sm`.
- Footer: `bg-notebook-paper/80 backdrop-blur-sm`.
- Hero overlays: layered linear gradients from `--background` (left and bottom).

## Key UI elements

> Note: despite the package.json declaring ~25 `@radix-ui/*` packages, only **4** shadcn components are actually generated in `src/components/ui/`: `input`, `tooltip`, `toast`, `toaster`. Everything else (cards, buttons, hero, timeline, popup, footer) is **hand-written inline in `page.tsx`** (2148 lines). So the "shadcn-complete-set" framing of the task is partially true at the dependency level but the homepage itself is bespoke.

### AnimeCard (page.tsx lines 884–1011)

- Fixed widths: `w-[140px] sm:w-[155px] md:w-[168px]`, `flex-shrink-0`.
- Image: `aspect-[3/4]`, `object-cover`, `rounded-lg`, `border border-border/60`.
- Skeleton: `skeleton-shimmer-light` (shimmer keyframe) reserves the slot until image loads, then `opacity-0`.
- Hover: card body `scale-[1.05] origin-top`; inner image `scale-[1.06]` + `brightness-[0.85]`; bottom gradient `from-black/60` fades in.
- Episode strip (bottom): `bg-notebook-coffee/90` with SUB pill (emerald) + DUB pill (amber) + total EP count, all in Caveat font.
- Badges (top corners): `.sticky-note` "NEW" (top-left, `-left-1`) + Caveat rating with star (top-right, `-right-1`).
- Bookmark button (top-right, hover-reveal): 28px circle, `bg-gray-900/70` idle → `bg-primary` when bookmarked.
- Play button (hover center, AnimatePresence): 44px circle `bg-primary border-2 border-primary-foreground/30`.
- Title below: 16px Caveat bold + 11px muted-foreground genre.

### ContentRow (lines 1013–1099)

- Section header: `.notebook-tab` (3px primary bar left of title) + 26px Caveat bold title + icon chip (`w-7 h-7 rounded-md` + accent color: `bg-primary` / `bg-notebook-sage` / `bg-amber-500`) + "See All" link with `ArrowRight`.
- Scroll container: `flex gap-5 overflow-x-auto no-scrollbar`. Horizontal scroll only; scrollbar hidden.
- Desktop-only left/right nav arrows (40px circles, `bg-gray-800/90 dark:bg-gray-900/90`, `text-white`), opacity 0 → 100 on `group-hover/row`.
- Edge fades: `bg-gradient-to-l from-background` 8/12/16px on right; mirrored on left.

### GenreCard (lines 1102–1129)

- `paper-texture` background, `rounded-xl border border-border/60`, `h-[100px]`.
- Washi-tape accent: `-top-1.5` 48×12px gradient strip rotated -1°.
- Lucide icon (5×5) + 18px Caveat name.
- Framer-motion hover: `scale: 1.03, y: -4`; tap: `scale: 0.97`.

### NextReleasingSection (lines 1257–1623) — Coming-Up-Next timeline

- Card: `rounded-xl border border-border bg-card shadow-lg`, padding `p-3 sm:p-4`.
- Vertical line: 3px wide, `linear-gradient(to bottom, stops)` joining today (emerald `#047857`) → tomorrow (amber `#d97706`) → later (blue `#1d4ed8`), centered on the dot column at `left-[12px]`.
- Group header: pill badge (e.g. "Today" with `bg-emerald-800/15 text-emerald-700 border-emerald-700/25`) + horizontal divider `h-px bg-border/40`.
- Entry card: `flex-1 … rounded-lg border border-border/50 bg-background/60` with `hover:bg-accent/40 hover:border-primary/30 hover:shadow-md hover:grayscale-0`. Later/tomorrow entries start `grayscale-[50%]` / `grayscale` and de-saturate on hover.
- Dot: `w-3.5 h-3.5 rounded-full` colored per group + `ring-2 ring-{color}/20`.
- Cover thumb: 40×56 (mobile) / 50×70 (desktop) `rounded-md`.
- EP badge: `text-[10px] font-bold text-primary bg-primary/10 border-primary/15`.
- Hover popup (300/340/380px wide): banner image + Episode pill + airing time + 3-line description + genre chips + footer meta (rating, aired/total, type, year). Spring-back ease `[0.34,1.56,0.64,1]`, scale 0.95→1, y ±8.

### Header (lines 1679–1847)

- Wrapper: `mx-2 sm:mx-3 md:mx-6 mt-3 md:mt-4 mb-3 md:mb-6`.
- Bar: `bg-[#6B5240] dark:bg-[#2D2218] rounded-2xl shadow-lg border border-white/10`, `h-14 md:h-16`, `max-w-[1280px]`.
- Logo: `w-8 h-8 rounded-lg bg-primary` with `BookOpen` icon + "Ani**Verse**" in 28px Caveat white.
- Desktop tab nav: `bg-white/10 rounded-xl p-1` containing pill buttons. Active state animates a `motion.div layoutId="nav-pill"` with spring stiffness 400 / damping 30 (the shared-layout animation slides the highlight between tabs).
- Search input: shadcn `<Input>` with hardcoded `bg-white/10 border-white/15 text-white placeholder:text-white/40`, search icon at `left-2.5`.
- Mobile menu: hamburger toggles a `fixed top-[68px] right-3 w-44` dropdown `bg-[#3D2E1F] dark:bg-[#1A1410]` with 3 nav items + theme toggle.
- Theme toggle: AnimatePresence icon swap (Sun/Moon) with rotate ±90° on enter/exit, 0.2s.
- Notifications: bell icon with `bg-primary` 8px dot top-right.

### Hero carousel (lines 1849–2007)

- Container heights: `h-[200px] sm:h-[395px] md:h-[432px] lg:h-[476px]`. `rounded-2xl` on mobile, full-bleed `sm:rounded-none` on desktop.
- Background image: `absolute h-full w-auto left-1/2 sm:left-[66.67%] -translate-x-1/2` — centered on mobile, shifted right on desktop so text has room on the left.
- Overlays: two stacked linear gradients (left-to-right + bottom-to-top) into `--background`.
- Decorations (desktop only, `hidden lg:block`): 2 coffee-ring stains + `bg-primary/10 blur-2xl` floating glow with `.animate-gentle-float`.
- Touch swipe: horizontal swipe advances hero (`onTouchStart`/`onTouchEnd` with 40px threshold + axis check).
- Text content: `w-[45%] md:w-[40%]` (desktop) left-aligned at bottom-mobile / center-desktop.
  - Tags row (desktop): sage "TV Series" + primary "#N Trending".
  - Title: `font-black` 18px mobile → `text-[3.5rem]` lg, single-line clamp.
  - Meta row: amber star + rating + dot + year + dot + genre.
  - Description: 2-line clamp, `max-w-md`.
  - CTA: primary "Watch Now" (rounded-xl, scale 1.03/0.97 hover/tap) + paper "Details" + paper round bookmark.
- Carousel dots: 1.5px tall pills, active `w-8 bg-primary`, inactive `w-2 bg-muted-foreground/30`. Separate mobile (below hero) + desktop (bottom-center overlay) dot rows.
- Auto-advance: 6-second `setInterval`.

### Footer (lines 2044–2144)

- `bg-notebook-paper/80 backdrop-blur-sm border-t border-border`.
- 4-column grid: Brand (logo + tagline "Your cozy corner for anime streaming…") + Browse + Community + Legal. All sub-headings in 16px Caveat bold.
- Bottom bar: "© 2025 AniVerse. Brewed with care." + Status/API/version links.

### shadcn components actually present (`src/components/ui/`)

- `input.tsx` — single `<input>` wrapper with `cva`-free className concat. Uses `h-9 rounded-md border-input bg-transparent dark:bg-input/30 focus-visible:ring-ring/50`.
- `tooltip.tsx` — Radix Tooltip wrapper. Content uses `bg-primary text-primary-foreground rounded-md px-3 py-1.5 text-xs` + arrow.
- `toast.tsx` + `toaster.tsx` + `use-toast.ts` — classic shadcn toast system (cva variants: default + destructive). `ToastViewport` pinned bottom-right on desktop, top on mobile.

Full element specs in `elements/README.md`.

## Motion & animations

Implemented with **framer-motion ^12.23.2** + CSS keyframes. See `motion/README.md` for the full catalog.

Key patterns:

- **Stagger fade-up** (`fadeSlideUp`, lines 801–808): `opacity:0 y:16 → opacity:1 y:0`, duration 0.3, ease `[0,0,0.2,1]`, delay `i * 0.06`.
- **Section fade** (`sectionFade`, 811–818): same idea, duration 0.4, no stagger.
- **whileInView gating**: every section uses `viewport={{ once: true, margin: "-50px" }}` or `-60px`. On mobile, anime cards bypass whileInView (`initial="visible"`) to avoid IntersectionObserver failing inside horizontal scroll containers.
- **Hero cross-fade** (AnimatePresence `mode="wait"`): bg image opacity 0→1 over 0.8s easeInOut.
- **Hero text slide**: y 20→0 enter, y -10 exit, 0.45s.
- **Play button pop**: scale 0.8→1, 0.15s.
- **Card hover lift**: CSS `.card-paper-hover` — `translateY(-3px) rotate(-0.3deg)` + 2-layer shadow, 200ms ease-out.
- **Genre card hover**: framer `whileHover: { scale: 1.03, y: -4 }`, `whileTap: { scale: 0.97 }`.
- **Nav active pill**: `motion.div layoutId="nav-pill"` with spring `{ stiffness: 400, damping: 30 }` — slides between tabs.
- **Theme icon swap**: AnimatePresence `mode="wait"`, Sun/Moon rotate ±90° + opacity, 0.2s.
- **Mobile menu dropdown**: opacity + y(-8) + scale(0.95) → 0, 0.15s ease `[0,0,0.2,1]`.
- **Hover popup** (Coming-Up-Next): scale 0.95→1 + y ±8 (placement-aware), 0.2s ease `[0.34,1.56,0.64,1]` (spring-back).
- **CSS keyframes**: `shimmer` (skeleton, 1.5s linear infinite), `ink-spread` (loading blot), `gentle-float` (5s ease-in-out infinite, decorative).
- **Reduced motion**: `@media (prefers-reduced-motion: reduce)` zeroes all animation durations (globals.css 419–425).

## Theming

### What's wired

- `next-themes ^0.4.6` is in `package.json` deps but **NOT used in the actual code**. The `<html lang="en" suppressHydrationWarning>` in `layout.tsx` has no `<ThemeProvider>` wrapper — just the `Toaster` is mounted.
- Theme switching is done **manually** in `page.tsx`:
  ```ts
  const [isDark, setIsDark] = useState(false);
  const toggleTheme = useCallback(() => {
    setIsDark((prev) => {
      const next = !prev;
      document.documentElement.classList.toggle("dark", next);
      return next;
    });
  }, []);
  ```
- Dark mode is class-based: `@custom-variant dark (&:is(.dark *));` (globals.css line 20). Toggling `.dark` on `<html>` flips every `dark:` utility and every `:root` → `.dark` CSS var swap.
- No persistence (no `localStorage` save). Default is light (`isDark = false`).
- The footer + tagline also reference the metaphor: "Brewed with care."

### Theme-aware surfaces (summary)

- Light → warm cream `#F0E6D8` page, paper `#FDF8F2` cards, terracotta `#B8653F` primary.
- Dark → near-black roast `#1A1412` page, `#2A2220` cards, latte `#D4956A` primary.
- The espresso header bar `#6B5240 / #2D2218` is dark in both modes — stays readable in light mode.

### Compose adaptation

- Replace `next-themes` / manual class toggle with Compose `MaterialTheme(colorScheme = …)` + a `isSystemInDarkTheme()`-aware switcher persisted via DataStore.
- Map the CSS vars 1:1 to a `CoffeeColors` object holding light/dark `Color` values; pick by theme.
- Class-based `.dark` flipping → simply swap `MaterialTheme` at the root composable.

## Adaptation for ANI-KUTA (Android/Compose)

### Token mapping

| Web token | Compose mapping |
|---|---|
| `--background` / `--foreground` | `ColorScheme.background` / `onBackground` |
| `--card` / `--card-foreground` | `ColorScheme.surface` / `onSurface` (cards use `Surface`) |
| `--primary` / `--primary-foreground` | `ColorScheme.primary` / `onPrimary` |
| `--secondary` / `--secondary-foreground` | `ColorScheme.secondary` / `onSecondary` |
| `--muted` / `--muted-foreground` | custom `muted`/`onMuted` (extend `ColorScheme`) |
| `--accent` / `--accent-foreground` | `ColorScheme.tertiary` / `onTertiary` (or custom) |
| `--destructive` | `ColorScheme.error` |
| `--border` | `ColorScheme.outline` |
| `--input` | `ColorScheme.outlineVariant` |
| `--ring` | `ColorScheme.primary` (focus ring → Compose `Modifier.focus` indication) |
| `--radius` (10px) + scale | `Shapes(small=6.dp, medium=8.dp, large=10.dp, extraLarge=14.dp)` |
| `--notebook-paper/coffee/sage/sticky/ruled/margin/tape` | custom `CoffeeNotebookColors` data class — kept as named `Color` fields, NOT in `ColorScheme` |
| `--font-caveat` | bundled `Caveat-Bold.ttf` in `res/font/`, exposed as `Typography` extension |
| `--font-geist-sans/mono` | bundled Geist family OR default `sans-serif` / `monospace` fallback |

### Element mapping

| Web pattern | Compose equivalent |
|---|---|
| `<Input>` shadcn | `OutlinedTextField` with coffee-toned `colors()` + Caveat variant for hand-labeled fields |
| `<Tooltip>` Radix | `TooltipBox` (Material 3 `RichTooltip`) |
| `<Toast>` Radix | `Snackbar` with custom CoffeeSnackbar colors |
| `.sticky-note` badge | `Surface(color = stickyYellow, shape = RoundedCornerShape(2.dp), shadowElevation = 2.dp)` + `Text(fontFamily = caveatBold)` |
| `.paper-texture` background | `Modifier.drawBehind { drawRect(paper); drawCircledots(dotGrid) }` — draw the dot-grid + inset shadow as a DrawScope |
| `.card-paper-hover` | `Modifier.pointerInput { detectTapGestures(onPress = …) }` + animate scale/rotation with `graphicsLayer` |
| `.washi-tape` | small `Canvas` composable above the card drawing a rotated gradient strip |
| `.coffee-ring` | `Canvas` drawing 2 concentric `drawCircle` outlines at low alpha |
| `.ink-underline` | `Box` behind `Text` with `Modifier.background(primary.copy(alpha=0.3f))` rotated -0.5° |
| `.notebook-lines` | `Modifier.drawBehind { repeat(rowCount) { drawLine(…) } }` |
| Framer `layoutId` nav pill | `LookaheadScope` + `SharedTransitionLayout` (Compose 1.7+) or a manual `animateDpAsState` for the pill x-offset |
| Framer `whileInView` stagger | `LazyRow` + `LaunchedEffect` triggering per-item `AnimatedVisibility` with staggered delay |
| Framer `mode="wait"` cross-fade | `Crossfade(targetState = heroIndex, animationSpec = tween(800))` |
| Skeleton shimmer | `Modifier.drawBehind` with `rememberInfiniteTransition` translating a linear gradient |

### Layout adaptation

- The `max-w-[1280px]` centered column maps to a `Box(Modifier.fillMaxSize().padding(horizontal = 24.dp))` on tablet/desktop; phone uses full width.
- Tailwind breakpoints (`sm 640 / md 768 / lg 1024`) → Compose `WindowSizeClass` (Compact < 600 / Medium 600–840 / Expanded > 840). Use `currentWindowAdaptiveInfo()` to switch hero heights / nav style / grid columns.
- Horizontal `no-scrollbar` rows → `LazyRow` with `LazyRowScrollbar.NONE` (or hidden via `LocalScrollConnection`).

### Fonts on Android

- Caveat is a Google Font — download `.ttf` from Google Fonts and bundle as `res/font/caveat_bold.ttf`. Then `val caveat = FontFamily(Font(R.font.caveat_bold))` and use in a `Typography` override for `headlineLarge`/`titleLarge`/`labelSmall` (whatever maps to "title text" in the screen).
- Geist is also a Google Font (since 2024). Bundle `geist_regular.ttf` / `geist_medium.ttf` / `geist_bold.ttf` similarly. Fallback to `FontFamily.SansSerif` if not bundled.

### Theme persistence

- Replace manual `classList.toggle` with a `ThemeRepository` backed by DataStore (Boolean `is_dark_theme` key). Read on app start in the root composable, expose via `StateFlow<Boolean>`, switch `MaterialTheme(colorScheme = if (isDark) darkCoffeeColors() else coffeeWhiteColors())`.

## What to reuse

**Definitely reuse (signature identity):**
- Caveat handwriting font for all titles, badges, section headers, episode counts.
- The full coffee color palette (light + dark + notebook-specific).
- The 5 notebook CSS effects ported to Compose DrawScopes: dot-grid bg, ruled lines, washi tape, sticky-note badges, coffee-ring decoration on hero.
- `.card-paper-hover` lift-with-0.3°-tilt on every card-like composable.
- The "espresso header bar that's dark in both themes" pattern.
- 10px base radius + the sm/md/lg/xl scale.
- Timeline gradient line in Coming-Up-Next (today/tomorrow/later color-coding).
- SUB-emerald / DUB-amber pill convention on every anime card.

**Probably reuse:**
- AnimatePresence `mode="wait"` hero cross-fade → `Crossfade`.
- `layoutId` nav pill → Compose `SharedTransitionLayout`.
- Hover popup with placement-aware y-offset and spring-back ease.
- 6-second hero auto-advance.
- Mobile hamburger dropdown + desktop pill tabs — same responsive split as the template.

**Adapt, don't copy:**
- Hardcoded `#6B5240` header bg should become a token (`coffee.espressoBar`) so dark/light can subtly differ if desired.
- The manual `classList.toggle` → DataStore-persisted MaterialTheme swap.
- `next-themes`, `react-resizable-panels`, `cmdk`, `recharts`, `react-day-picker`, `@dnd-kit`, `embla-carousel-react`, `vaul`, `sonner`, `@mdxeditor/editor`, `next-auth`, `next-intl`, `react-syntax-highlighter` deps — all web-only. Their Compose analogues (where ANI-KUTA needs them) should be chosen separately.

**Probably skip:**
- Prisma + SQLite (the `User`/`Post` schema is scaffold-only, never imported in `src/`). ANI-KUTA uses SQLDelight for the local cache; do not port this.
- The 21 unused Radix dependencies (`@radix-ui/react-accordion` … `react-tooltip` are installed but no component file exists for most of them). Only `input/tooltip/toast/toaster` exist; the rest are dependency dead-weight from the scaffold.

## Open questions

1. **Caveat font licensing on Android** — Google Fonts OFL-licensed; safe to bundle. Confirm weight set covers 400/500/600/700 (the template loads all 4 weights).
2. **Geist font availability** — Geist was released as open-source by Vercel in 2024; confirm the variable font files are downloadable for Android bundling, otherwise fall back to Inter (also Google, similar humanist sans).
3. **Dot-grid background perf** — `html::before` is a fixed-position radial-gradient. On Android, drawing the same pattern in `Modifier.drawBehind` over a scrolling list may cause overdraw. Consider a tiled `BitmapDrawable` background or only draw the grid on the screen root, not per-card.
4. **Sticky-note yellow contrast** — `--notebook-sticky: #FFF8CC` (light) with `--notebook-coffee: #5C3D28` text passes WCAG AA, but in dark mode sticky becomes `#3A3220` with `#A08060` text — verify contrast for accessibility.
5. **Header espresso in light mode** — keeping the header dark in light mode is a strong design choice but increases visual weight at the top. Decide if ANI-KUTA keeps this or softens it.
6. **Caveat legibility at 9–11px** — the template uses Caveat at 9–11px for SUB/DUB/EP badges. Handwriting fonts at small sizes can be hard to read on low-DPI phones; consider min 12px or fall back to Geist Bold for tiny badges.
7. **next-themes removal** — confirmed unused; if any future code expects a `useTheme()` hook it will need a different mechanism. For ANI-KUTA this is moot (we use Compose theming).
8. **Timeline gradient line on touch devices** — the template disables the hover popup on touch (`isTouchDevice` check). On Android, every device is touch — so the Coming-Up-Next popup must be triggered by tap (which the template already supports via `handleCardClick`) instead of hover. Verify the tap UX feels native.
9. **Reduced motion parity** — `@media (prefers-reduced-motion: reduce)` is honored. Compose equivalent: check `LocalAccessibilityManager` / `Settings.Global.ANIMATOR_DURATION_SCALE` and skip non-essential animations.
10. **Brand naming** — the in-code identity is "AniVerse" (footer line 2133 "© 2025 AniVerse. Brewed with care."). Our app is "ANI-KUTA". Decide if we keep the "Brewed with care" tagline, replace it, or drop entirely.
