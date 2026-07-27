# Design 4 — Coffee (AniVerse Notebook) — Design Language & Philosophy

## Identity

- The template's code names itself **"AniVerse — Your Anime Notebook"** (see `src/app/layout.tsx` line 29 metadata title, `src/app/page.tsx` line 1 file header, and the footer copyright `© 2025 AniVerse. Brewed with care.` at line 2133).
- The stylesheet header (`src/app/globals.css` lines 4–6) declares:

  > "AniVerse — Notebook Design System — This stylesheet defines the complete visual language for the notebook-themed UI."

- Light theme is internally named **"Coffee White"** (globals.css line 72) and dark theme is **"Dark Coffee"** (line 119). The zip file is `COFFEE_TEMPLATE.zip`. The user's task name "Coffee" and the in-code "Notebook" identity describe the same design — it is a coffee-themed notebook.

## Single governing metaphor

**A coffee-stained paper notebook laid open on a wooden desk.** Every visual decision in the template reinforces this metaphor:

| Real-world object | Code-level realization |
|---|---|
| Aged paper sheet | `--background: #F0E6D8` cream + `html::before` 20px dot-grid at opacity 0.4 |
| Fresh paper page (cards) | `--notebook-paper: #FDF8F2` + `.paper-texture` 80px inset warm shadow |
| Notebook ruled lines | `.notebook-lines` — `repeating-linear-gradient` 32px row pitch |
| Red margin line | `.notebook-margin::before` — 2px vertical line at `left:48px`, `opacity:0.5` |
| Spiral binding (left edge) | `.spiral-binding::before` — `radial-gradient` dots every 40px |
| Dog-ear corner | `.paper-edge::after` — 20px triangular corner at bottom-right |
| Washi tape decoration | `.washi-tape::before` — 80×18px rotated `-2deg` strip in `--notebook-tape` color |
| Sticky note | `.sticky-note` — `#FFF8CC` bg + `2px 2px 6px rgba(0,0,0,0.08)` shadow + inset bottom |
| Coffee ring stain | `.coffee-ring` — 120px circle, 3px `rgba(111,78,55,0.08)` border, decoration only |
| Ink underline | `.ink-underline::after` — 3px primary bar at `-0.5deg` rotation |
| Handwritten label | Caveat font (`--font-caveat`) on every title, badge, count |
| Espresso stain on the cover | Header bar hardcoded `#6B5240` (light) / `#2D2218` (dark) — dark in both modes |
| Tagline | "Brewed with care." (footer line 2133) |
| Marketing copy | "Your cozy corner for anime streaming. Jot down your favorites…" (footer line 2067) |

## Philosophy, in five rules

Extracted from the code's behavior (not from any explicit philosophy doc — the template has none):

1. **Be warm, not sterile.** Every neutral color is shifted toward yellow-red. Whites are `#FDF8F2` paper, not pure white. Blacks are `#1A1412` near-black roast, not pure black. Grays are `#7A6450` warm brown-gray, not cool gray. The cool color spectrum only appears in the chart palette (slate-teal `#5A7D96`, mauve `#966B94`) and in the timeline's "later" group (blue `#1d4ed8`) — both used sparingly.

2. **The page is a desk, not a screen.** The fixed dot-grid overlay sits *behind* everything (z-index -1) and never moves. Cards are sheets laid on top. Hover lifts cards with a slight 0.3° tilt — physically impossible for a flat UI, but it sells the paper metaphor. The episode-info strip on every anime card uses `--notebook-coffee/90` with `backdrop-blur-sm` — like coffee spillage on the bottom of a polaroid.

3. **Handwriting carries identity.** The single most distinctive choice is loading a third font (Caveat) on top of Geist Sans + Geist Mono. Caveat appears ~12 times in `page.tsx` via inline `style={{ fontFamily: "var(--font-caveat), cursive", fontSize: ... }}`. Every section title, every anime card title, every "NEW"/rating/SUB/DUB badge, every footer column header is in Caveat. Body copy stays in Geist Sans — the contrast between handwriting and sans-serif IS the design language.

4. **Stationery is decoration, not infrastructure.** The 8 stationery utility classes (`.notebook-lines`, `.notebook-margin`, `.paper-texture`, `.paper-edge`, `.washi-tape`, `.sticky-note`, `.coffee-ring`, `.spiral-binding`) are present but used sparingly. Only `.paper-texture`, `.sticky-note`, `.notebook-tab`, and `.coffee-ring` actually appear in `page.tsx`. The rest are defined in CSS for future use but the homepage keeps restraint — never piling every effect on every element.

5. **Motion mimics paper physics.** Card hover is `translateY(-3px) rotate(-0.3deg)` — a slight tilt as if lifting a card off a stack. Genre cards lift `y: -4` plus `scale: 1.03`. The hero's coffee-ring + blurred primary-glow decoration gently floats (`gentle-float` 5s ease-in-out infinite, ±6px Y + ±1° rotation). Skeleton loading shimmers like ink spreading. The "ink-spread" keyframe (`scale:0 opacity:0.6` → `scale:1 opacity:0`) is defined but not yet used in the homepage — reserved for future loading states.

## Aesthetic positioning vs. the other 3 ANI-KUTA designs

| Design | Primary metaphor | Type system | Decorative effects | Density |
|---|---|---|---|---|
| 01 Material 3 | Material surfaces, elevation tiers | Roboto / system | Ripple, elevation, motion specs | Medium-high |
| 02 Modern Neon | Cyberpunk grid, glowing edges | (per its own doc) | Glow, gradients, dark-first | High |
| 03 Notebook | (per its own doc — likely similar but distinct source) | (per its own doc) | (per its own doc) | — |
| **04 Coffee (AniVerse Notebook)** | **Coffee-stained paper notebook** | **Geist Sans + Geist Mono + Caveat handwriting** | **Dot grid, ruled lines, washi tape, sticky notes, coffee rings, ink underlines** | **Medium** |

Coffee is the **most metaphor-driven** of the four. Where Material 3 leans on system conventions and Neon leans on contrast/glow, Coffee commits fully to one extended metaphor and lets it drive every color, font, shadow, and motion choice.

## Where the design succeeds

- **Distinctive identity.** A user scrolling past would not mistake this for any other anime app. The Caveat font + warm coffee palette + sticky-note badges are immediately recognizable.
- **Strong information hierarchy.** Section headers in 26px Caveat bold + notebook-tab bar + icon chip create clear visual rhythm. The "Coming Up Next" timeline is genuinely useful — color-coded by day with a single continuous gradient line.
- **Restraint on dark mode.** Dark Coffee uses warm `#1A1412` not pure black, and `#F0E0D0` cream text not pure white — easier on the eyes than true black and stays on-brand.
- **Mobile-aware.** Custom `useIsMobile` hook disables whileInView animations inside horizontal scrollers (where IntersectionObserver fails). Touch swipe on hero. Mobile gets its own dot row below the hero. Mobile menu dropdown is well-positioned.
- **Accessibility hooks present.** `prefers-reduced-motion` zeroes animations. `aria-invalid` ring states on inputs. Focus-visible rings via `--ring`.

## Where the design risks

- **Caveat at 9–11px may be illegible** on low-DPI phones (SUB/DUB/EP badges). The handwriting strokes become mushy. Consider min 12px or fall back to Geist Bold for tiny badges.
- **Hardcoded header bg** `#6B5240` / `#2D2218` bypasses the theme tokens — if we ever want to lighten the header in light mode we'd have to change source code, not a token.
- **Dot-grid overlay on `html::before`** is a full-screen fixed element. On a long-scrolling Android `LazyColumn` this is fine, but on a low-end device with many overlays it adds overdraw.
- **next-themes is declared but unused** — the manual `classList.toggle` doesn't persist theme choice. ANI-KUTA must add DataStore persistence.
- **21 unused Radix dependencies** inflate `node_modules`. Not a runtime cost for ANI-KUTA (we're Compose-side) but worth noting the template is scaffold-heavy.
- **The "espresso header in light mode"** is a strong choice. It works here because the rest of the page is so warm, but in a more standard anime-browsing context it might feel heavy.

## ANI-KUTA adaptation philosophy

When porting to Compose:

- **Keep the metaphor intact.** Don't flatten the notebook effects into Material-standard surfaces. The dot-grid background, the paper-textured cards, the sticky-note badges, the Caveat titles — these ARE the brand. Drop them and you have just another anime app.
- **Treat the 6 notebook-specific tokens** (`paper/ruled/margin/coffee/latte/sage/sticky/tape`) as a first-class `CoffeeNotebookColors` data class alongside the standard `ColorScheme`. Don't try to fit them into `primary/secondary/tertiary` slots — they're decoration colors, not action colors.
- **Caveat is non-negotiable.** Bundle the font. If licensing or APK-size is a concern, drop it last and only after trying everything else.
- **The 0.3° hover tilt** is a tiny detail that does a lot of work. Implement it via Compose `graphicsLayer { rotationZ = -0.3f; translationY = -3.dp.toPx() }` on `pointerInput` press.
- **Spring-back ease `[0.34, 1.56, 0.64, 1]`** for popovers → Compose `spring(dampingRatio = 0.6f, stiffness = 400f)` (mild overshoot).
