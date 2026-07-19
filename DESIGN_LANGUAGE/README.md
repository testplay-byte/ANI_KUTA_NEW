# DESIGN_LANGUAGE/

> The ANIKUTA design language spec — a custom M3-inspired visual + interaction
> language for the app. This is **not** stock Material 3 Expressive; it has the
> owner's specific preferences baked in.
>
> **Source of truth for all UI.** Per `RULES/ai-agent-rules.md` §6, every screen
> and component must follow this spec. No improvising UI.

## How this folder is organized

| Folder | Contents |
|---|---|
| [`01-principles/`](01-principles/) | Core design principles (layout, spacing, motion, the "no drag bar" rule, edge-to-edge, etc.). |
| [`02-components/`](02-components/) | Reusable components (bottom-up menus, toggles, custom keyboard, floating bottom nav, etc.). |
| [`03-themes/`](03-themes/) | Color palettes, typography, theme selection system. |
| [`04-screens/`](04-screens/) | Per-screen design specs (anime details, watch page, player, episode list, extensions settings, etc.). |

## Primary design reference

The **old ANIKUTA project** (`OLD_ANIKUTA/ANIKUTA_OLD/`) is the primary design
reference. Only the screens the owner explicitly flagged are referenced — see
`OLD_ANIKUTA/ANALYSIS/` for the analysis of those screens. **Do not** take
design cues from screens the owner did not flag.

## What this design language is NOT

- Not stock Material 3 Expressive (the owner finds it insufficient).
- Not a copy of Aniyomi's UI (the owner finds it ugly, especially the bottom nav).
- Not a free-for-all. Every layout decision must trace back to a doc here.

## Status

Being populated. The analysis of the old ANIKUTA screens (`OLD_ANIKUTA/ANALYSIS/`)
feeds into the per-screen specs here.
