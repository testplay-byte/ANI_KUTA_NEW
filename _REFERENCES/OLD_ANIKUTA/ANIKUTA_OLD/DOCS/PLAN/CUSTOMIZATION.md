# Customization System

> The design + theming system that makes ANI-KUTA "highly customizable."
> Planned from day one — not bolted on.

---

## The 3-level customization model

```
Level 1: Design       — pick 1 of 4 starting designs (the overall look)
Level 2: Theme        — per-design, pick a theme variant (colors) + accent
Level 3: Custom tweak — limited custom color overrides for specific places
```

---

## Level 1 — The 4 starting designs

Each design is a complete visual language: layout, card style, spacing,
typography feel, icon set, motion. The user picks ONE; it applies throughout.

> TODO: the user hasn't specified the 4 designs. Proposals below are starting
> points for discussion. Names are placeholders.

| Design | Codename | Vibe | Card style | Inspiration |
|--------|----------|------|------------|-------------|
| 1 | **Classic** | Clean, standard, Material 3 | Standard poster cards, rounded | aniyomi / Mihon default |
| 2 | **Compact** | Dense, info-rich, minimal | Smaller cards, more per row, less padding | Tachiyomi legacy / info-dense |
| 3 | **Cinematic** | Big, bold, image-forward | Large backdrop cards, bold typography | Streaming services (Netflix-ish) |
| 4 | **Minimal** | Lots of whitespace, typography-led | Outlined cards, monochrome accents | iOS / minimal design |

**Each design defines:**
- Card layout (poster size, info shown, arrangement).
- Section spacing + density.
- Typography scale + weight.
- Icon style (filled / outlined / duotone).
- Motion (subtle / playful / none).
- Bottom-nav style.

**Each design does NOT change:**
- The features (all 4 designs have the same features).
- The backend (same logic, same data).
- The data model (same source of truth).

> This is the UI/logic separation in action: 4 UIs, 1 backend.

---

## Level 2 — Theming (per design)

Each design ships with:
- **Theme variants:** Light, Dark, AMOLED (true black). Maybe more per design.
- **Accent color presets:** a palette of 6-10 accent colors per design (e.g.
  emerald, rose, amber, violet, cyan, orange).
- The user picks a theme + an accent.

> TODO: do accent presets differ per design, or is there a global accent palette?

---

## Level 3 — Custom tweaks (limited)

For users who want more control, a **limited** custom theming panel:
- Override the accent color with a custom hex picker.
- Override the background shade (within a safe range).
- Override card corner radius (within a range).
- TODO: what else is overridable? (Keep it limited — not a full theme editor.)

**Not customizable (to avoid breakage):**
- Layout structure (can't move sections around at the pixel level).
- Font family (only the choices each design offers).
- Icon set (tied to the design).

> The goal: enough customizability to feel personal, not so much that the user
> can break the design or get overwhelmed.

---

## Other customizations (beyond design/theme)

- **Language:** full app language selection (from i18n resources).
- **Font style:** a few font choices per design (e.g. default, rounded, mono).
- **Section visibility:** show/hide home page sections.
- **Card density:** within each design, a density slider (fewer/more cards).

---

## How it's built (architecture)

```
┌─────────────────────────────────────────────┐
│              UI layer (per design)            │
│  design-1/  design-2/  design-3/  design-4/  │
│  each has: components/ + theme/ + tokens/    │
└──────────────────┬──────────────────────────┘
                   │ reads
                   ▼
┌─────────────────────────────────────────────┐
│           Theme system (shared)              │
│  - active design (1-4)                       │
│  - active theme (light/dark/amoled)          │
│  - active accent (preset or custom hex)      │
│  - custom overrides                          │
└──────────────────┬──────────────────────────┘
                   │ applies
                   ▼
┌─────────────────────────────────────────────┐
│         Logic layer (shared, backend)        │
│  same regardless of design                   │
└─────────────────────────────────────────────┘
```

- Switching designs = swapping the UI component tree.
- The logic layer doesn't know which design is active.
- This is the UI/logic separation rule, made concrete.

---

## Open questions

- [ ] The 4 designs: are my proposals right, or do you have references/names?
- [ ] Accent palette: per-design or global?
- [ ] Custom theming: how limited? Which overrides?
- [ ] Font choices: which fonts?
- [ ] Can the user save a "preset" (design + theme + accent + tweaks) to share?

---

_Last updated: Session 8 (initial draft)._
