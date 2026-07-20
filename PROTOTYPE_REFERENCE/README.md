# PROTOTYPE_REFERENCE/

This directory holds a **read-only snapshot** of the owner's prototype Android
project — the project whose UI/UX the owner likes and wants ANIKUTA to follow.

## What's inside

```
PROTOTYPE_REFERENCE/
└── Anime_App/        ← the prototype project (25 Kotlin files)
```

## Why we keep it

The prototype has several screens and components the owner explicitly flagged as
design references:
- **Bottom navigation bar** — floating pill design, active item expands to show
  label, inactive items are icon-only. The owner says this is "the perfect way."
- **Schedule screen** — minimal, weekly airing schedule with day selector.
- **Search screen** — the owner's favorite: "best page," modern, fully functional.
- **Settings screen** — collapsing header, sectioned groups, custom toggles.
- **CollapsingHeader** — reusable title that shrinks on scroll.

## Rules

- 🔒 **READ-ONLY.** Same status as `ANIYOMI_REFRENCE/` and `OLD_ANIKUTA/`. Do not
  modify, reorganize, or build inside this folder.
- 📋 **Mine for patterns.** When implementing a screen, read the corresponding
  file here, then re-implement in `ANIKUTA_PROJECT/ANIKUTA/` following our
  architecture (Koin, SQLDelight, module boundaries).
- 📖 See [`ANALYSIS.md`](ANALYSIS.md) for the detailed analysis of each screen
  and component.

## Provenance

| Field | Value |
|---|---|
| Source repo | `https://github.com/testplay-byte/ANDROID-PROTOTYPE` |
| Subfolder | `Android_app/Anime_App` |
| Branch | `main` |
| Snapshot method | GitHub source tarball (source-only, no `.git`) |
| Package | `com.testplaybyte.animeapp` |
| File count | 25 Kotlin files |
