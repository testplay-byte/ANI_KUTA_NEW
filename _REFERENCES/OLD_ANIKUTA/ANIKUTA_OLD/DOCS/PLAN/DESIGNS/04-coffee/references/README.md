# Design 4 — Coffee (AniVerse Notebook) — Source Template Reference

## Location

`/home/z/my-project/upload/coffee-extracted/aniverse-template/`

Extracted from `COFFEE_TEMPLATE.zip`.

## Internal identity (note the dual name)

- Zip file: `COFFEE_TEMPLATE.zip` → folder `coffee-extracted/aniverse-template/`.
- `package.json` `name` field: `"nextjs_tailwind_shadcn_ts"` (generic scaffold name).
- In-code product name (metadata title, page header, footer): **"AniVerse — Your Anime Notebook"**.
- Stylesheet banner (globals.css line 5): **"AniVerse — Notebook Design System"**.
- Light theme name (globals.css line 72): **"Coffee White"**.
- Dark theme name (line 119): **"Dark Coffee"**.
- Footer tagline (page.tsx line 2133): `© 2025 AniVerse. Brewed with care.`

So the user-facing name is "AniVerse" / "Notebook"; the design palette name is "Coffee White / Dark Coffee"; the zip is "COFFEE_TEMPLATE". Our internal name for this design is "Coffee (AniVerse Notebook)" to disambiguate from `03-notebook/` (a different source template).

## File tree

```
aniverse-template/
├── package.json                 # deps (Next 16, React 19, Tailwind 4, shadcn, framer-motion, …)
├── components.json              # shadcn config (style=new-york, baseColor=neutral, lucide)
├── next.config.ts               # output=standalone, ignoreBuildErrors=true, reactStrictMode=false
├── tsconfig.json                # @/* -> ./src/*, strict
├── postcss.config.mjs           # @tailwindcss/postcss only
├── eslint.config.mjs
├── prisma/
│   └── schema.prisma            # User + Post models (scaffold only — not imported in src/)
├── db/
│   └── custom.db                # SQLite file
├── public/
│   ├── logo.svg
│   ├── robots.txt
│   └── images/notebook/
│       ├── hero-banner.png      # hero carousel image
│       └── anime-1.png … anime-10.png  # 10 demo anime covers
└── src/
    ├── app/
    │   ├── layout.tsx           # fonts (Geist + Geist Mono + Caveat), metadata, Toaster
    │   ├── page.tsx             # 2148-line homepage (all UI inline)
    │   └── globals.css          # 425-line notebook design system
    ├── components/ui/
    │   ├── input.tsx            # shadcn Input
    │   ├── tooltip.tsx          # shadcn Tooltip
    │   ├── toast.tsx            # shadcn Toast (Radix)
    │   └── toaster.tsx          # Toaster mount (uses useToast)
    ├── hooks/
    │   ├── use-mobile.ts        # useIsMobile (768px breakpoint)
    │   └── use-toast.ts         # useToast singleton (TOAST_LIMIT=1)
    └── lib/
        ├── utils.ts             # cn() helper (clsx + tailwind-merge)
        └── db.ts                # Prisma client singleton (unused in src/)
```

## What's actually used vs declared

### Used in src/

- `next` (App Router), `react`, `react-dom`.
- `tailwindcss` v4 (via `@import "tailwindcss"` in globals.css), `@tailwindcss/postcss`, `tw-animate-css`.
- `framer-motion` (motion, AnimatePresence) — heavy use in page.tsx.
- `lucide-react` — 22 icons imported in page.tsx (Search, Bell, BookmarkPlus, Play, Star, TrendingUp, Clock, Flame, ChevronRight, ChevronLeft, Home, Compass, Heart, Sparkles, Zap, Tv, Film, Info, Sun, Moon, Coffee, BookOpen, ArrowRight, Menu, X).
- `@radix-ui/react-tooltip` (in tooltip.tsx), `@radix-ui/react-toast` (in toast.tsx).
- `class-variance-authority` (cva in toast.tsx), `clsx`, `tailwind-merge` (in utils.ts).

### Declared in package.json but NOT used in src/

- `next-themes` — declared but theme is toggled manually via `classList.toggle("dark")` in page.tsx.
- `@prisma/client` + `prisma` — declared, `db.ts` exists, but `db` is never imported in src/. Schema has only `User` + `Post` (scaffold).
- 19 of the 25 `@radix-ui/*` packages — only `react-tooltip` and `react-toast` have generated component files. The rest (accordion, alert-dialog, aspect-ratio, avatar, checkbox, collapsible, context-menu, dialog, dropdown-menu, hover-card, label, menubar, navigation-menu, popover, progress, radio-group, scroll-area, select, separator, slider, slot, switch, tabs, toggle, toggle-group) are declared but unused.
- `cmdk`, `vaul`, `embla-carousel-react`, `react-resizable-panels`, `react-day-picker`, `recharts`, `react-syntax-highlighter`, `react-markdown`, `@mdxeditor/editor`, `@dnd-kit/*`, `input-otp`, `sonner`, `next-auth`, `next-intl`, `zustand`, `zod`, `@hookform/resolvers`, `react-hook-form`, `@reactuses/core`, `@tanstack/react-query`, `@tanstack/react-table`, `date-fns`, `uuid`, `sharp`, `z-ai-web-dev-sdk`.

These are scaffold dependencies carried over from the template's generator; they inflate `node_modules` but don't affect runtime behavior of the homepage. For ANI-KUTA (Android/Compose) they're irrelevant — we just pick the patterns we want and reimplement in Compose.

## Key file sizes

| File | Lines |
|---|---|
| `src/app/page.tsx` | 2148 |
| `src/app/globals.css` | 425 |
| `src/components/ui/toast.tsx` | 129 |
| `src/hooks/use-toast.ts` | 194 |
| `src/components/ui/tooltip.tsx` | 62 |
| `src/app/layout.tsx` | 50 |
| `src/components/ui/input.tsx` | 22 |
| `src/components/ui/toaster.tsx` | 35 |
| `src/hooks/use-mobile.ts` | 19 |
| `src/lib/utils.ts` | 7 |
| `src/lib/db.ts` | 13 |
| `prisma/schema.prisma` | 33 |
| `components.json` | 21 |
| `package.json` | 95 |

## Key source excerpts (quoted for reference)

### globals.css `@theme inline` bridge (lines 22–64)

Maps Tailwind v4 color tokens to CSS vars. See `colors/README.md` for the full mapping.

### globals.css light theme `:root` (lines 70–116)

See `colors/README.md` — full table of every token.

### globals.css `.dark` (lines 118–163)

See `colors/README.md` — full table of every dark token.

### globals.css notebook utilities (lines 190–425)

See `elements/README.md` section 11 — every utility class spec.

### page.tsx structure (2148 lines)

| Lines | Section |
|---|---|
| 1–19 | File header comment |
| 21–56 | Imports (React, framer-motion, lucide, shadcn Input/Tooltip) |
| 60–89 | `AnimeItem` interface |
| 91–304 | `demoTrending` (10 items) |
| 307–460 | `demoSeasonal` (8 items) |
| 463–625 | `demoPopular` (8 items) |
| 628–766 | `demoUpcoming` (6 items with airing times) |
| 768–775 | `demoHeroAnime` (5 picks from trending) |
| 778–787 | `genres` array (8 genres with icon + gradient) |
| 790–794 | `navLinks` array (3 items) |
| 800–818 | `fadeSlideUp` + `sectionFade` framer-motion variants |
| 822–838 | `useIsMobile` inline hook (640px breakpoint) |
| 845–877 | `useHorizontalScroll` hook |
| 884–1011 | `AnimeCard` component |
| 1013–1099 | `ContentRow` component |
| 1102–1129 | `GenreCard` component |
| 1131–1198 | `GenreSection` component (with responsive visibleCount logic) |
| 1200–1255 | `PopupContent` component |
| 1257–1623 | `NextReleasingSection` component (timeline + hover popup) |
| 1629–2148 | `HomePage` component (state, hero auto-advance, theme toggle, header, hero, main, footer) |

### layout.tsx (50 lines, full file)

```tsx
import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Caveat } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
const caveat = Caveat({
  variable: "--font-caveat",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "AniVerse — Your Anime Notebook",
  description: "A cozy notebook-styled anime streaming experience. Browse, watch, and journal your favorite anime in a warm, coffee-inspired setting.",
  keywords: ["anime", "streaming", "subbed", "dubbed", "notebook", "watch anime"],
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${geistSans.variable} ${geistMono.variable} ${caveat.variable} antialiased`}>
        {children}
        <Toaster />
      </body>
    </html>
  );
}
```

Notable:
- `<html lang="en" suppressHydrationWarning>` — no `className="dark"` default; theme is toggled by client-side JS in page.tsx.
- No `ThemeProvider` from `next-themes` is mounted despite `next-themes` being in deps.
- All 3 fonts loaded via `next/font/google` and exposed as CSS variables on `<body>`.
- Toaster is mounted at the body level.

### components.json (21 lines, full file)

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": true,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/app/globals.css",
    "baseColor": "neutral",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

- shadcn **style: new-york** (smaller, sharper than default).
- **baseColor: neutral** (overridden entirely by the coffee palette in globals.css).
- **RSC: true** (React Server Components enabled).
- `tailwind.config: ""` — confirms Tailwind v4 CSS-based config (no `tailwind.config.ts`).
- Icon library: lucide.

### next.config.ts (13 lines, full file)

```ts
const nextConfig: NextConfig = {
  output: "standalone",
  typescript: { ignoreBuildErrors: true },
  reactStrictMode: false,
};
```

- `output: "standalone"` — for self-contained deployment.
- `ignoreBuildErrors: true` — TypeScript errors don't block build (scaffold-friendly; not best practice).
- `reactStrictMode: false` — disabled (likely to avoid double-render in dev for animation debugging).

## Demo data

10 demo anime images at `public/images/notebook/anime-1.png` … `anime-10.png` plus a `hero-banner.png`. These are placeholder covers reused across `demoTrending` (10 items), `demoSeasonal` (8 items, reuses covers from trending), `demoPopular` (8 items, reuses covers), `demoUpcoming` (6 items, reuses covers). The anime titles are original ("Golden Fields Chronicle", "Midnight Bloom", "Steel Horizon", "Whispering Pines", "Crimson Academy", "Ocean's Lullaby", "Neon District", "Sakura Letters", "Dragon's Heir", "Shadow Protocol", plus 18 more for seasonal/popular/upcoming).

## How to verify this design's identity

If you want to confirm the dual "Coffee" + "Notebook" naming:

1. `cat src/app/globals.css | head -20` — header comment names it "AniVerse — Notebook Design System".
2. `grep -n "Coffee White" src/app/globals.css` → line 72 (light theme comment).
3. `grep -n "Dark Coffee" src/app/globals.css` → line 119 (dark theme comment).
4. `grep -n "AniVerse" src/app/page.tsx` → lines 4, 5, 1698 (brand), 2063 (footer brand), 2133 (copyright).
5. `grep -n "Brewed with care" src/app/page.tsx` → line 2133.

## Sibling design folders

| Folder | Source template | Notes |
|---|---|---|
| `01-material3/` | (per its own doc) | Material 3 baseline |
| `02-neon/` | (per its own doc) | Modern Neon |
| `03-notebook/` | (different source) | Another notebook-themed design — verify if this is the same template under a different name or a different source entirely before assuming duplication |
| `04-coffee/` (this doc) | `COFFEE_TEMPLATE.zip` → `aniverse-template/` | Coffee-themed notebook, Next.js + shadcn new-york + framer-motion |

If `03-notebook/` documents the same source template, the two docs should be merged. If it documents a different source (e.g. a vanilla HTML/CSS notebook template), the two are legitimately separate designs that share a "notebook" aesthetic but differ in execution.
