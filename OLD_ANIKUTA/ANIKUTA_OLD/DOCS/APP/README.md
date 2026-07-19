# APP/ — Our App's Build Documentation

> This folder documents **our app** (ANI-KUTA) — what we built, where things
> are, how to make changes, and why we chose what we chose.
>
> Distinct from `DOCS/REFERENCE-DOCS/` (which documents aniyomi, the upstream
> reference) and `DOCS/PLAN/` (which documents the plan, pre-build). This
> folder is filled in AS WE BUILD.

---

## Folder structure

```
APP/
├── README.md          ← this file (index)
├── DECISIONS.md       ← the 4 build decisions + all architectural decisions, with reasoning
├── STRUCTURE/         ← where things are in our app (per-module layout, filled as we build)
├── HOW-TO/            ← common change procedures (how to add a screen, add a source, etc.)
└── RATIONALE/         ← deeper reasoning for major choices (why X over Y)
```

## Files

| File | What it covers | Status |
|------|----------------|--------|
| `README.md` | This file — index. | ✅ |
| `DECISIONS.md` | The 4 build decisions (D1-D4) + all architectural decisions, with reasoning. | ✅ (initial) |
| `STRUCTURE/` | Per-module layout of our app. One file per area. | Empty — filled in Phase 1+. |
| `HOW-TO/` | Step-by-step guides for common changes. | Empty — filled as patterns emerge. |
| `RATIONALE/` | Deep dives on why we chose X over Y. | Empty — filled for major choices. |

## Update policy

- **DECISIONS.md** is updated whenever a decision is made (in `MEMORY/DECISIONS/` too).
- **STRUCTURE/** is updated whenever we add or restructure a module.
- **HOW-TO/** is added whenever we do something non-obvious more than once.
- **RATIONALE/** is added for any decision that deserves deeper reasoning than a one-liner.

## Related

- `DOCS/PLAN/` — the pre-build plan (what we intend to build).
- `DOCS/REFERENCE-DOCS/` — aniyomi reference docs (what we're building on).
- `MEMORY/DECISIONS/` — the decision log (ADR-style, one-liners + links to rationale here).
