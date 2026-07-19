# DESIGNS/ — The 4 ANI-KUTA Designs

> Documentation for each of the 4 starting designs the user can pick. Each
> design is a complete visual language. The user picks ONE; it applies
> throughout the app.

---

## The 4 designs

| # | Name | Vibe | Source | Status |
|---|------|------|--------|--------|
| 1 | **Material 3** | Clean, standard, Material You | Google M3 guidelines | ✅ Documented |
| 2 | **Dark Neon** | Dark, glowing, glass-morphic, lime/sky/coral | `DESIGN.md` (provided) | ✅ Documented |
| 3 | **Neobrutalism** | Bold, raw, thick borders, hard shadows | `NOTEBOOK_TEMPLATE.zip` (aniverse) | ✅ Documented |
| 4 | **Coffee Notebook** | Cozy, coffee-stained paper, handwriting font | `COFFEE_TEMPLATE.zip` (aniverse) | ✅ Documented |

> **Note on naming:** the "Notebook" zip actually contains a **Neobrutalism**
> design system (the code self-describes as "AniVerse Neobrutalism v2"). The
> "Coffee" zip contains the actual notebook/coffee-stained paper design. We've
> named them by their actual design identity.

---

## Folder structure (per design)

Each design folder has the same structure:

```
0X-name/
├── README.md          ← main doc (thorough overview)
├── language/          ← design language + philosophy
├── colors/            ← full color palette
├── elements/          ← UI element specs (buttons, cards, etc.)
├── layout/            ← spacing, layout, borders
├── motion/            ← animation specs
└── references/        ← source reference
```

---

## How the designs work together

All 4 designs share the **same backend** (AniList caching, aniyomi extensions,
player, data layer). Only the **UI layer** changes. This is the UI/logic
separation rule in action: 4 UIs, 1 backend.

```
                 ┌─────────────────────┐
                 │   Backend (shared)   │
                 │  AniList + aniyomi   │
                 │  + SQLDelight + DI   │
                 └──────────┬──────────┘
                            │
       ┌────────┬───────────┼───────────┬────────┐
       ▼        ▼           ▼           ▼        ▼
   Material3  Neon    Neobrutalism   Coffee   (future)
   (M3)      (glass)  (bold/raw)   (cozy)
```

The user picks a design in onboarding (or settings). The app loads that design's
Compose theme + components. The backend doesn't know which design is active.

---

## Design selection

- **Onboarding** — the user picks a design during the setup wizard (step 5).
- **Settings** — the user can change designs anytime in Settings → Design.
- **Theme variants** — each design has light/dark/AMOLED + accent presets.
- **Custom tweaks** — limited custom color overrides on top of the chosen design.

See `DOCS/PLAN/CUSTOMIZATION.md` for the full 3-level customization model.

---

## Adaptation note (web → Android)

The 3 custom designs (Neon, Neobrutalism, Coffee) come from **web templates**
(Next.js + Tailwind + Framer Motion + shadcn). We adapt them to **Android
Jetpack Compose**:

| Web concept | Android/Compose equivalent |
|-------------|---------------------------|
| Tailwind classes | Compose modifiers |
| CSS variables | `MaterialTheme` colorScheme / custom data classes |
| `backdrop-blur` | `Modifier.graphicsLayer { renderEffect = ... }` (API 31+) |
| Framer Motion | Compose `AnimatedVisibility`, `animate*AsState`, `InfiniteTransition` |
| shadcn components | M3 Compose components (themed per design) |
| Lucide icons | Material Symbols Outlined |
| next-themes | DataStore-persisted theme state |

Each design's `README.md` has a detailed adaptation section.

---

## Related

- `DOCS/PLAN/CUSTOMIZATION.md` — the 3-level customization model (design → theme → tweaks).
- `DOCS/PLAN/ONBOARDING.md` — the setup wizard (design selection is step 5).
