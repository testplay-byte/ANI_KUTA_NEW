# Phase 6 — The 4 Designs + Theming System

> **Status: PLANNING (not started).** This document defines the goal, scope,
> tasks, open questions, and dependencies. Implementation begins after the user
> answers the open questions and gives the go-ahead.
>
> **Prerequisite:** Phase 5 complete ✅ (extension chain works, real episodes
> play, Library/History/Search/Settings all functional).

---

## 1. Goal

**The customization system lands.** By the end of Phase 6, the user can:
1. Pick from **4 complete designs** (Material 3, Dark Neon, Neobrutalism, Coffee Notebook) — each applies throughout the entire app.
2. Choose a **theme variant** (Light / Dark / AMOLED) per design.
3. Pick an **accent color** from presets (or follow system/Monet on M3).
4. **Show/hide home page sections** (trending, popular, fresh, genres, schedule).
5. See the design they pick in a **live preview** before applying.

The app goes from "functional but one-design" to "fully customizable with 4 distinct visual identities."

---

## 2. Scope — what's IN Phase 6

### 2.1 Design system architecture (the foundation)

This is the most important piece. Currently the app has ONE hardcoded theme (`AnikutaTheme` in `Theme.kt` + `Expressive.kt`). Phase 6 builds a **design-system abstraction** so all 4 designs plug in:

```
DesignProvider (CompositionLocal)
  ├── Material3Design   (current — refactored)
  ├── NeonDesign        (new — dark glass, neon accents)
  ├── NeobrutalismDesign (new — thick borders, hard shadows)
  └── CoffeeDesign      (new — paper texture, handwriting font)
```

Each design provides:
- **Color scheme** (light + dark + AMOLED variants)
- **Typography** (font family, weights, sizes)
- **Shapes** (corner radii, border styles)
- **Motion specs** (spring configs, durations)
- **Component overrides** (card style, button style, nav style)

**Tasks:**
- **6.1** `DesignSpec` interface + `DesignProvider` CompositionLocal — the abstraction layer.
- **6.2** Refactor existing `AnikutaTheme` → `Material3Design` (no visual change, just moves it behind the interface).
- **6.3** `DesignStore` (PreferenceStore-backed) — remembers the user's pick + theme variant + accent.

### 2.2 Design 2 — Dark Neon

From `DOCS/PLAN/DESIGNS/02-neon/`: dark glass-morphic, lime/sky/coral neon accents, backdrop-blur, hairline borders, neon glow shadows.

**Tasks:**
- **6.4** `NeonDesign` — color scheme (dark-only, AMOLED variant), typography (same as M3 but with neon weight), shapes (rounded with hairline borders), motion (Framer-Motion-style springs).
- **6.5** Neon component overrides: `NeoCard` (glass + blur + glow), `NeoButton` (neon outline), `NeoNavBar` (glass pill).

### 2.3 Design 3 — Neobrutalism

From `DOCS/PLAN/DESIGNS/03-notebook/`: bold, raw, 3px borders, hard offset shadows (4px rest / 8px hover / 1px press), uppercase black/extrabold typography, 28px background grid pattern, spring-overshoot easing.

**Tasks:**
- **6.6** `NeobrutalismDesign` — 7 accent colors (blue/pink/green/yellow/orange/purple/red), Acid Cream (light) + Midnight Raw (dark) palettes, 3px borders, hard shadows.
- **6.7** Neobrutalism component overrides: `NeoCard` (3.5px border, offset shadow, lift-on-press), `NeoButton`, `NeoBadge`, custom `Modifier.neoShadow` (Compose's built-in shadow is wrong for neobrutalism).
- **6.8** Background grid pattern via `Modifier.drawBehind`.

### 2.4 Design 4 — Coffee Notebook

From `DOCS/PLAN/DESIGNS/04-coffee/`: cozy, coffee-stained paper, Caveat handwriting font, terracotta/cream palette, sticky-note badges, washi tape, coffee-ring decor.

**Tasks:**
- **6.9** `CoffeeDesign` — terracotta/cream palette, Caveat font (bundled), paper texture background, sticky-note badges.
- **6.10** Coffee component overrides: `CoffeeCard` (paper texture, sticky-note badge), `CoffeeButton` (washi tape accent).

### 2.5 Theme variants + accents

**Tasks:**
- **6.11** Theme variant system: Light / Dark / AMOLED per design. AMOLED = pure black backgrounds (OLED battery save).
- **6.12** Accent picker: each design has 5-7 accent presets. M3 also supports "follow system/Monet" (Android 12+).
- **6.13** `ThemeStore` — persists theme variant + accent per design.

### 2.6 Home page customization

**Tasks:**
- **6.14** Section show/hide: `HomeCustomizationStore` (PreferenceStore) — user can toggle each of the 6 home sections (trending, popular, fresh, genres, schedule, hero). HomeScreen reads this + shows/hides accordingly.
- **6.15** Section reordering (stretch goal — may defer): drag-to-reorder home sections.

### 2.7 Settings UI

**Tasks:**
- **6.16** Design picker screen (replaces the "Coming soon" badge in MoreScreen): grid of 4 design cards, each showing a mini-preview. Tap → live preview → "Apply" button.
- **6.17** Theme picker: Light / Dark / AMOLED segmented control.
- **6.18** Accent picker: row of color swatches per design.
- **6.19** Home customization screen: list of 6 sections with toggles + (optionally) drag handles.

### 2.8 Live preview

**Tasks:**
- **6.20** Design preview composable: shows a sample card + button + nav bar in the selected design, so the user sees what they're picking before applying.

---

## 3. Scope — what's NOT in Phase 6

- **Player UI redesign** (server/quality selection, better controls, gestures) — deferred to a later player-polish phase.
- **Per-screen design overrides** — the user picks ONE design for the whole app (no mixing). Per-screen overrides come later if requested.
- **Custom fonts upload** — only the bundled fonts (Caveat for Coffee, system for others). User can't upload their own.
- **Custom accent color picker** (hex input) — only presets. Hex picker comes later if requested.
- **Animated theme transitions** (crossfade when switching designs) — nice-to-have, may defer.
- **Widget / lock screen customization** — not in scope.

---

## 4. Dependencies + risks

### 4.1 The big risk: Compose theme abstraction complexity

Compose's `MaterialTheme` is global — there's no built-in way to swap color schemes / typography / shapes at runtime without recomposing the whole tree. The standard approach is `CompositionLocal` + a `DesignProvider` that wraps `MaterialTheme`. This works but requires every component to read from the provider, not hardcoded `MaterialTheme.colorScheme.*`.

**Mitigation:** Phase 6.1 (DesignSpec interface) is the foundation — if it's wrong, everything breaks. We build it first, test it with Material3 (refactored), then plug in the other 3 designs one at a time.

### 4.2 Font bundling (Coffee design)

The Coffee design needs the Caveat handwriting font. We need to bundle it as a resource (`res/font/`) and load it via `FontFamily(Font(R.font.caveat))`. This adds ~100KB to the APK.

### 4.3 Glass-morphic blur (Neon design)

Compose's `Modifier.blur` works on Android 12+ but is slow on older devices. For the Neon design's glass cards, we may need `RenderEffect` (API 31+) with a fallback to semi-transparent overlays on older devices.

### 4.4 Neobrutalism shadows

Compose's built-in `Modifier.shadow` uses soft elevation shadows — wrong for neobrutalism (which needs hard offset shadows). We need a custom `Modifier.neoShadow` using `drawBehind` + `translation` offset.

---

## 5. Task breakdown (proposed order)

| # | Task | Depends on | Est. complexity |
|---|------|------------|-----------------|
| 6.1 | DesignSpec interface + DesignProvider CompositionLocal | — | High |
| 6.2 | Refactor AnikutaTheme → Material3Design | 6.1 | Medium |
| 6.3 | DesignStore (persist pick + theme + accent) | 6.1 | Low |
| 6.4 | NeonDesign (colors, typography, shapes, motion) | 6.1 | Medium |
| 6.5 | Neon component overrides (NeoCard, NeoButton, NeoNavBar) | 6.4 | Medium |
| 6.6 | NeobrutalismDesign (7 accents, Acid Cream + Midnight Raw) | 6.1 | Medium |
| 6.7 | Neobrutalism component overrides + Modifier.neoShadow | 6.6 | High |
| 6.8 | Neobrutalism background grid pattern | 6.6 | Low |
| 6.9 | CoffeeDesign (terracotta/cream, Caveat font, paper texture) | 6.1 | Medium |
| 6.10 | Coffee component overrides | 6.9 | Medium |
| 6.11 | Theme variant system (Light/Dark/AMOLED) | 6.2 | Medium |
| 6.12 | Accent picker (5-7 presets per design + Monet for M3) | 6.11 | Low |
| 6.13 | ThemeStore (persist theme + accent per design) | 6.3 | Low |
| 6.14 | Home section show/hide (HomeCustomizationStore) | — | Low |
| 6.15 | Section reordering (stretch — may defer) | 6.14 | High |
| 6.16 | Design picker screen (grid + preview + apply) | 6.1–6.10 | Medium |
| 6.17 | Theme picker (Light/Dark/AMOLED segmented) | 6.11 | Low |
| 6.18 | Accent picker (swatches) | 6.12 | Low |
| 6.19 | Home customization screen (toggles) | 6.14 | Low |
| 6.20 | Live preview composable | 6.1–6.10 | Medium |
| 6.21 | Phase 6 verification | all | — |

**Suggested order:** 6.1 → 6.2 → 6.3 (foundation), then 6.11 → 6.12 → 6.13 (theme system), then 6.4–6.10 (the 3 new designs, one at a time), then 6.14 → 6.19 (home customization), then 6.16–6.20 (settings UI + preview), then 6.21 (verify).

---

## 6. Open questions for the user

**Q1 — Build order:** Should we build all 4 designs in Phase 6, or build the foundation (6.1–6.3) + Material 3 + ONE new design (Neon), then ship the other 2 designs in Phase 7? My proposal: **all 4 in Phase 6** — the design docs are thorough, and doing them together keeps the abstraction consistent.

**Q2 — Default design:** Which design should be the default for new users (first boot)? My proposal: **Material 3** (safest, most familiar, supports Monet).

**Q3 — AMOLED:** Should every design have an AMOLED variant (pure black), or only the dark-capable ones (M3, Neon, Neobrutalism)? Coffee is inherently light (paper). My proposal: **AMOLED for M3 + Neon + Neobrutalism; Coffee has Light + Dark only** (Dark = dark coffee stain, not pure black).

**Q4 — Accent presets:** How many accent colors per design? My proposal: **5-7 per design** (matching the design's palette — e.g., Neon has lime/sky/coral/purple/pink; Neobrutalism has the 7 neo colors; Coffee has terracotta/olive/navy/plum; M3 follows Monet or 6 presets).

**Q5 — Home section reordering:** Include drag-to-reorder in Phase 6 (task 6.15, high complexity), or defer? My proposal: **defer** — show/hide is enough for Phase 6; reordering is polish.

**Q6 — Live preview:** When the user is in the design picker, should the preview be (a) a static sample card, or (b) a live mini-app (actual home cards rendered in the selected design)? My proposal: **(a) static sample card** — simpler, faster, and enough to show the design's vibe.

**Q7 — Onboarding integration:** Should the onboarding Design step (step 6) use the new full design picker, or keep the simple 4-button pick? My proposal: **simple pick in onboarding (4 buttons), full picker in Settings** — onboarding should be fast.

**Q8 — Animated transitions:** When the user switches designs, should the whole app crossfade to the new design, or just snap? My proposal: **snap** (crossfade is nice but risky — Compose recomposition can glitch).

---

## 7. How we'll manage Phase 6

- **Same incremental copy-paste (D1):** each design references its `DOCS/PLAN/DESIGNS/0X-*/` docs. No bulk copy from aniyomi (aniyomi only has one design).
- **UI/logic separation:** all design specs are pure data (no composables). Component overrides are thin wrappers. The app's screens don't change — they read from `DesignProvider`.
- **One design at a time:** build M3 (refactored) → verify no visual regression → build Neon → verify → build Neobrutalism → verify → build Coffee → verify. Each is a separate commit + build.
- **GitHub:** each task = one commit. Push after each. Build must be green before moving on.
- **Documentation:** each design gets a `DOCS/APP/STRUCTURE/designs/0X-*.md` adoption record (what was implemented, what was adapted, what was deferred).
- **ntfy:** sent at the end of each task + when builds go green/red.

---

## 8. Verification (Phase 6 done = these all pass)

- [ ] Onboarding Design step still works (simple 4-button pick).
- [ ] Settings → Design picker shows all 4 designs with previews.
- [ ] Tap each design → app instantly switches to that design (no restart).
- [ ] Material 3: looks identical to before (no regression).
- [ ] Neon: dark glass cards, neon accents, hairline borders.
- [ ] Neobrutalism: 3px borders, hard shadows, uppercase type, grid background.
- [ ] Coffee: paper texture, Caveat font, terracotta/cream, sticky-note badges.
- [ ] Theme picker: Light/Dark/AMOLED works per design (Coffee = Light/Dark only).
- [ ] Accent picker: 5-7 swatches per design, tap → accent changes.
- [ ] Home customization: toggle each of 6 sections → home page updates.
- [ ] Restart app → design + theme + accent + home sections all persist.
- [ ] No crashes when switching designs rapidly.

---

_Last updated: Session 20 (Phase 6 planning). Implementation starts after user
answers the open questions._
