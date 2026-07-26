# Reference Research — ANI-KUTA revamp

> Persistent research notes so we don't redo the work. Backed up to GitHub.
> Branch: `library-history-search-revamp`
> Last updated: Session 31

## What's here

| File | What it covers |
|------|----------------|
| `aniyomi-solutions.md` | How aniyomi (our upstream reference) solves each problem we're tackling: watched/unwatched tracking, categories, history UI, resume, last-watched sort, search, sub/dub counts. With exact file paths in `REFERENCE/`. |
| `design-language.md` | ANI-KUTA's existing Material 3 Expressive design language — design tokens, component patterns, layout recipes. Extracted from the Detail page, episodes list, settings screens, and player. The design target for Library/History/Search. |

## How to use these docs

- **Before implementing any feature**, read the relevant section in
  `aniyomi-solutions.md` to see how aniyomi does it (file paths + line numbers
  are included so you can read the actual code in `REFERENCE/`).
- **Before building any UI**, read `design-language.md` to match the existing
  M3 Expressive look (tokens, springs, component patterns).
- **Update these docs** if you discover something new during implementation.

## Where the raw research lives

The full, unabridged research (with every code snippet and line number) was
appended to the sandbox worklog at `/home/z/my-project/worklog.md` under these
Task IDs:
- `REFERENCE-RESEARCH-1` — aniyomi reference research (8 topics)
- `UI-DESIGN-ANALYSIS-1` — ANI-KUTA UI design language analysis

The condensed, actionable version is in the two files in this folder.
