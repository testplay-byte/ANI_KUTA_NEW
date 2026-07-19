# Design 3 — Notebook: Source Template Reference

Documents the source template location, file structure, and which files were consulted for this design analysis.

## Template location

```
/home/z/my-project/upload/notebook-extracted/aniverse-template/
```

This is the unzipped form of the source zip `NOTEBOOK_TEMPLATE.zip`. The folder is named `aniverse-template` (not "notebook") because the template's actual project name is "AniVerse".

## Template identity

| Field | Value | Source |
|---|---|---|
| Zip name | `NOTEBOOK_TEMPLATE.zip` | (provided by task) |
| Unzipped folder name | `aniverse-template/` | filesystem |
| `package.json` `name` | `aniverse` | `package.json` line 2 |
| `package.json` `version` | `0.2.0` | `package.json` line 3 |
| Self-described style | "Neobrutalism Design System v2" | `src/app/globals.css` line 5 |
| Self-described tagline | "Raw. Bold. Unapologetic." | `src/app/globals.css` line 7 |
| Light palette name | "Acid Cream" | `src/app/globals.css` line 21 |
| Dark palette name | "Midnight Raw" | `src/app/globals.css` line 22 |
| Layout metadata title | "AniVerse — Your Anime Notebook" | `src/app/layout.tsx` line 24 |
| Layout metadata description | "A bold, neobrutalist anime browsing experience..." | `src/app/layout.tsx` line 25 |
| Layout metadata keywords | `["anime", "streaming", "subbed", "dubbed", "neobrutalism", "watch anime"]` | `src/app/layout.tsx` line 26 |
| README header | "AniVerse — Neobrutalist Homepage UI Template" | `README.md` line 1 |
| Page component header | "AniVerse — Neobrutalism Anime Homepage" | `src/app/page.tsx` line 4 |

The word "notebook" appears only in:
1. The zip filename (`NOTEBOOK_TEMPLATE.zip`).
2. The `public/images/notebook/` folder name (holding 11 fallback anime PNGs).
3. The metadata title "Your Anime Notebook" (`layout.tsx` line 24).

It does **not** appear in any CSS class name, design token, color name, or component name. There is no notebook-paper / lined-paper aesthetic anywhere in the template. The design language is unambiguously neobrutalism.

## File tree (relevant files only)

```
aniverse-template/
├── README.md                                    # Template overview + customization guide
├── package.json                                 # Dependencies (Next 16, React 19, Framer Motion 12, Radix, shadcn, Lucide, tailwindcss 4)
├── tailwind.config.ts                          # Minimal — colors wired to hsl(var(--token)) shims, borderRadius computed from --radius
├── components.json                              # shadcn config: style="new-york", baseColor="neutral", cssVariables=true, iconLibrary="lucide"
├── postcss.config.mjs                          # @tailwindcss/postcss plugin only
├── tsconfig.json                                # (not consulted)
├── next.config.ts                              # (not consulted)
├── eslint.config.mjs                           # (not consulted)
├── public/
│   ├── logo.svg                                 # Site logo (not consulted in detail)
│   ├── robots.txt
│   └── images/notebook/                         # Fallback images
│       ├── hero-banner.png                     # Used as fallback for hero banner
│       ├── anime-1.png ... anime-10.png        # Used as fallback covers when Jikan image fails
└── src/
    ├── app/
    │   ├── layout.tsx                          # Root layout — Geist fonts, metadata, Toaster
    │   ├── globals.css                          # ⭐ Full neobrutalism design system (CSS variables + .neo-* classes + keyframes)
    │   ├── page.tsx                             # ⭐ Single-file homepage (~2844 lines): all UI components inline
    │   └── api/                                 # Backend API routes (NOT consulted — out of scope for design analysis)
    │       ├── anime/_jikan.ts                  # Jikan v4 client + rate limiter + cache
    │       ├── anime/hero/route.ts              # Hero banner (Anilist GraphQL)
    │       ├── anime/trending/route.ts          # Trending (Jikan v4)
    │       ├── anime/popular/route.ts           # Popular (Jikan v4)
    │       ├── anime/seasonal/route.ts          # Seasonal (Jikan v4)
    │       ├── anime/upcoming/route.ts          # Upcoming releases (Anilist GraphQL)
    │       ├── anime/search/route.ts            # Search (Jikan v4)
    │       ├── anime/[id]/route.ts              # Anime detail (Jikan v4)
    │       └── image-proxy/route.ts             # Image CORS proxy
    ├── components/ui/
    │   ├── tooltip.tsx                          # Radix Tooltip wrapper (shadcn new-york style)
    │   ├── toast.tsx                            # Radix Toast wrapper (shadcn new-york style) — NOT neobrutalist
    │   └── toaster.tsx                          # Toast renderer
    ├── hooks/
    │   ├── use-mobile.ts                        # useIsMobile() — 640px breakpoint
    │   └── use-toast.ts                         # useToast() — global toast state
    └── lib/
        ├── utils.ts                             # cn() utility (clsx + tailwind-merge)
        └── anime-cache.ts                       # In-memory cache with TTL
```

## Files consulted for this design doc

| File | Purpose | Lines |
|---|---|---|
| `package.json` | Confirm dependencies (Next 16, React 19, Framer Motion 12.23, Radix slot/toast/tooltip, CVA, clsx, lucide-react, tailwind-merge, tailwindcss 4, tw-animate-css, tailwindcss-animate) | 36 |
| `tailwind.config.ts` | Extract color wiring + radius scale; confirm minimal config (no custom spacing/fonts/shadows) | 64 |
| `components.json` | Confirm shadcn config (new-york style, neutral baseColor, cssVariables=true, lucide icons) | 21 |
| `postcss.config.mjs` | Confirm Tailwind v4 PostCSS setup | 5 |
| `src/app/globals.css` | ⭐ Primary source — design system CSS variables, .neo-* component classes, keyframes, reduced-motion rules | 752 |
| `src/app/layout.tsx` | Confirm fonts (Geist, Geist Mono — note: Caveat declared in @theme inline but never imported) + metadata | 45 |
| `src/app/page.tsx` | ⭐ Primary source — all UI component implementations (AnimeCard, ContentRow, GenreSection, GenreCard, NextReleasingSection, Sidebar, HeroBanner inline, Navbar inline, Footer inline, skeletons, hooks) + Framer Motion variants + interaction patterns | 2844 |
| `src/components/ui/tooltip.tsx` | Confirm tooltip styling (mostly shadcn defaults; one inline override applies .neo-card) | 62 |
| `src/components/ui/toast.tsx` | Confirm toast styling (shadcn defaults — NOT neobrutalist; this is a gap) | 129 |
| `src/components/ui/toaster.tsx` | Confirm toast rendering wiring | 35 |
| `src/lib/utils.ts` | Confirm `cn()` is just `clsx + tailwind-merge` (no custom logic) | 6 |
| `src/hooks/use-mobile.ts` | Confirm `useIsMobile()` returns `true` under 640px, `undefined` during SSR | 23 |
| `src/hooks/use-toast.ts` | Confirm toast state management (standard shadcn implementation) | 194 |
| `README.md` | Cross-check template overview, page layout ASCII, component list, design system summary | 321 |
| `public/images/notebook/` | (verified existence of fallback images — not consulted for design) | — |

## Files NOT consulted (out of scope)

- `tsconfig.json`, `next.config.ts`, `eslint.config.mjs` — TypeScript/Next config, no design info.
- `src/app/api/**` — backend API routes, not design-related. The README confirms they proxy Jikan v4 and Anilist GraphQL with 5-min in-memory cache.
- `src/lib/anime-cache.ts` — caching implementation, not design-related.
- `public/logo.svg` — small logo asset, not consulted in detail.
- `public/robots.txt` — SEO, irrelevant.

## What the template is

A **single-page anime browsing homepage**. One page (`/`) shows:

1. **Sidebar** (fixed on lg+, drawer on mobile) — 5 nav links + theme toggle, drag-resizable.
2. **Navbar** — search bar (animated morph on mobile), hamburger, bell, theme toggle.
3. **Hero banner** — full-width carousel (5 items), auto-advances every 6s, swipeable on mobile.
4. **Content rows** (3) — "Trending Now", "Freshly Updated", "Most Popular" — horizontal scroll with hover-reveal arrows on desktop.
5. **Genre section** — 8 genre cards (Action, Romance, Fantasy, Sci-Fi, Comedy, Horror, Mystery, Adventure) with color-coded shadows.
6. **Coming Up Next** — vertical timeline of upcoming releases grouped Today/Tomorrow/Later, with grayscale-by-recency and hover popups.
7. **Footer** — 4-column grid (Brand + Browse + Community + Legal) + bottom bar with version.

The entire UI is driven by a single `AnimeItem` TypeScript interface (defined in `page.tsx` lines 71–97, also documented in `README.md` lines 57–84). Data is fetched from 5 API routes in parallel on mount, with skeleton placeholders shown until data loads.

The template is intended as a **drop-in homepage** for any anime app — replace the API routes with your own data source (any backend that returns `AnimeItem`-shaped JSON), keep the UI as-is. The README explicitly notes it works for "ANY content type, not just anime" — movies, games, books — as long as data is mapped to `AnimeItem`.

## What the template is NOT

- **Not a multi-page app.** Only `/` exists. No detail page, no settings, no profile, no player. (Just API routes returning JSON.)
- **Not a component library.** UI components are defined inline in `page.tsx` (2844 lines, single file). The `components/ui/` folder contains only 3 shadcn primitives (tooltip, toast, toaster) — everything else lives in `page.tsx`.
- **Not a complete design system.** It's a one-off implementation with `.neo-*` CSS classes. No design tokens JSON, no Figma export, no Storybook. The CSS variables in `globals.css` are the de facto token system.
- **Not a "notebook" design.** Despite the zip name, there is no notebook-paper / lined-paper / journal aesthetic. The design language is **neobrutalism** (per the CSS header comment and README).

## How we are using it

We are **adapting the design language** (neobrutalism, "Acid Cream" / "Midnight Raw" palettes, .neo-* component patterns, motion vocabulary) for **ANI-KUTA**, an Android anime app built in Jetpack Compose. We are NOT porting the React/Next.js code.

Specifically we are extracting:
- The full color token system (`globals.css` `:root` and `.dark` blocks) → Compose `NeoColors` data class.
- The border / radius / spacing system → Compose modifiers and shape tokens.
- The `.neo-*` component patterns (cards, buttons, badges, inputs, sidebar, skeleton, section headers, timeline) → Compose composables.
- The motion vocabulary (spring overshoot curve, lift-and-press interactions, hover-pause popups, staggered entrances) → Compose `animateFloatAsState` + `AnimatedVisibility`.
- The 28px grid background → Compose `Modifier.drawBehind`.
- The mobile-suppression pattern (skip entrance animations on compact width) → Compose `WindowSizeClass` check.

We are NOT porting:
- React-specific reliability hacks (`useHorizontalScroll` with ResizeObserver/MutationObserver/multi-timer checks — Compose `LazyRow` handles this natively).
- `createPortal`-based popups (Compose `Popup` composable replaces this).
- `useImageBrightness` canvas sampling (we may port this to Coil + Bitmap, but it's lower priority).
- The Next.js API routes (we have our own data layer).
- The toast component (it's default shadcn, not neobrutalist — we'll redesign it).

## Verification notes

A few inconsistencies in the source template that we should be aware of:

1. **`page.tsx` line 14** says "Thick black/white borders (2.5px solid)" but the actual CSS uses 3px / 3.5px borders. The comment is out of date.
2. **`README.md` line 229** says `--neo-border: 2.5px solid var(--foreground)` but the actual CSS uses `3px solid #1A1A1A`. Same issue.
3. **`README.md` lines 232–238** lists the shadow colors with the comment `--neo-shadow-yellow: #eab308` but the actual CSS uses `#F59E0B`. The README hex values are slightly off.
4. **`@theme inline` block in `globals.css`** declares `--font-hand: var(--font-caveat)` (Caveat handwriting font) but `layout.tsx` never imports Caveat via `next/font/google`. The variable is declared but unused — likely a leftover from a planned "handwritten accent" feature that was dropped.
5. **`tailwind.config.ts`** wraps every color as `hsl(var(--token))`, but the values stored in `:root` are hex literals (e.g. `--primary: #2563EB`). `hsl(#2563EB)` is technically invalid CSS, but browsers tolerate the hex string as a passthrough. The shim is a no-op.
6. **`globals.css` line 2** imports `tw-animate-css` but the `tailwind.config.ts` plugins array only includes `tailwindcss-animate` (not `tw-animate-css`). Both libraries are installed in `package.json`. The actual animation utility classes used (e.g. `animate-in fade-in-0 zoom-in-95` in `tooltip.tsx`) come from `tw-animate-css`. The wiring is split across two libraries.
7. **`use-toast.ts`** has `TOAST_REMOVE_DELAY = 1000000` (11.5 days) — a known shadcn quirk to keep toasts in the DOM after dismissal for screen reader announcement. Not a bug, but worth noting.
8. **Toast component** uses default shadcn styling (soft shadow, 1px border, no neobrutalism treatment) — the only place in the entire design where the neobrutalism aesthetic is broken. TODO for ANI-KUTA: redesign toasts in the neobrutalism style.

## How to reproduce this analysis

If the template is moved or re-extracted, the same analysis can be reproduced by reading these files in this order:

1. `package.json` — confirm dependencies.
2. `tailwind.config.ts` — confirm Tailwind config (minimal).
3. `components.json` — confirm shadcn config.
4. `src/app/globals.css` — extract all design tokens, .neo-* classes, keyframes.
5. `src/app/layout.tsx` — extract fonts and metadata.
6. `src/app/page.tsx` — read sections in order:
   - Lines 1–58: header comment + imports (confirms design language, dependencies).
   - Lines 66–183: data types, `mapJikanAnime`, `genres[]` array (confirms color-to-genre mapping), `sidebarLinks[]`.
   - Lines 184–207: Framer Motion variants (`neoSlideUp`, `neoSectionPop`).
   - Lines 209–344: `CardHoverContext`, `useCardHover`, `useHorizontalScroll`.
   - Lines 346–624: skeleton components (mirror real components).
   - Lines 626–1011: `AnimeCard` (the most important component — patterns for cover, badges, hover popup).
   - Lines 1013–1198: `ContentRow` (horizontal scroll + arrows + edge fades).
   - Lines 1200–1316: `GenreSection` + `GenreCard`.
   - Lines 1318–1429: `PopupContent` (shared popup content).
   - Lines 1431–1769: `NextReleasingSection` (timeline + popup logic).
   - Lines 1771–1913: `Sidebar` (drawer + drag-resize).
   - Lines 1915–1999: `useImageBrightness` hook.
   - Lines 2001–2844: `HomePage` (main component — state, data fetching, layout, navbar, hero, sections, footer).
7. `src/components/ui/tooltip.tsx` and `toast.tsx` — confirm shadcn primitive styling.
8. `src/hooks/use-mobile.ts` — confirm breakpoint.
9. `README.md` — cross-check the design system summary.

Total lines of code consulted: ~4,500 (mostly `page.tsx` + `globals.css`).
