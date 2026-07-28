# Product Vision — ANI-KUTA

> What ANI-KUTA is, who it's for, and the principles that guide every decision.

---

## One-line summary

ANI-KUTA is a **highly customizable anime app** that uses **AniList** for
discovery/metadata and the **aniyomi extension system** for episode streaming —
built anime-first, with a clean separation between UI and backend so the look
can change without breaking how things work.

---

## The core experience

1. **Discover** anime on the home page (trending, freshly updated, by genre,
   most popular, coming up next) — all data from AniList.
2. **Open an anime** → see full details + episode list — AniList metadata +
   aniyomi extension data combined.
3. **Watch** an episode → the aniyomi extension system resolves the stream →
   the player plays it.
4. **Customize** everything — pick from 4 starting designs, apply a theme,
   tweak accent colors. The UI is yours.

---

## What makes ANI-KUTA different

- **Anime-first, not manga-included.** We start with anime only. Manga can be
  added later (the architecture allows it — see `DOCS/REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`).
- **AniList as the discovery layer.** The home page and detail page pull
  metadata from AniList (ratings, episode counts, genres, schedules). This is
  NOT what aniyomi does — aniyomi uses its extensions for everything. We use
  AniList for discovery + aniyomi extensions for streaming. **This is our key
  differentiator.**
- **Highly customizable UI.** 4 starting designs, each with theme variants +
  accent colors + limited custom theming. The user picks the look; the logic
  stays the same.
- **Two-layer architecture.** Frontend (UI) and backend (aniyomi processing)
  are strictly separate. Change the UI without touching the backend. This
  matches our core rule (UI/logic separation).

---

## The principles (how we decide)

1. **UI/logic separation is non-negotiable.** Every feature must split into
   "what it does" (logic) and "how it looks" (UI). The logic is reusable; the
   UI is swappable.
2. **Anime-first.** No manga code until anime is solid. When we add manga,
  it's a parallel stack (per the dual-model analysis).
3. **Track what we adopt from aniyomi.** Every aniyomi feature we use is
  recorded. Monthly, we compare upstream and decide what to adopt.
4. **Modular architecture.** Features are modules. Changes in one area don't
  break others.
5. **Performance + future-proofing.** No premature optimization, but no
  short-sighted hacks either. Every choice is documented with its trade-off.
6. **Customizability is a feature, not an afterthought.** The 4 designs +
  theming are planned from day one, not bolted on.

---

## The data flow (high level)

```
┌─────────────────────────────────────────────────────────────┐
│                        ANI-KUTA app                          │
│                                                              │
│  ┌──────────────────┐         ┌──────────────────────────┐  │
│  │   UI layer        │         │   Backend layer           │  │
│  │  (4 designs +     │ ◀─────▶ │  (aniyomi processing)     │  │
│  │   theming)        │  data   │                           │  │
│  │                   │         │  ┌──────────────────────┐ │  │
│  │  - Home           │         │  │ AniList client       │ │  │
│  │  - Detail         │         │  │ (discovery/metadata) │ │  │
│  │  - Player         │         │  └──────────────────────┘ │  │
│  │  - Library        │         │  ┌──────────────────────┐ │  │
│  │  - Settings       │         │  │ aniyomi extension    │ │  │
│  │  - ...            │         │  │ system (streaming)   │ │  │
│  └──────────────────┘         │  └──────────────────────┘ │  │
│                               │  ┌──────────────────────┐ │  │
│                               │  │ data layer (DB)      │ │  │
│                               │  └──────────────────────┘ │  │
│                               └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

- **AniList** = discovery + metadata (home page, detail page, schedules).
- **aniyomi extensions** = episode streaming (resolve video URLs).
- **Local DB** = library, history, preferences, customizations.

---

## Open questions (to resolve before/during build)

- [ ] AniList: is it ALSO the tracker (login + sync progress), or just a data source?
- [ ] Sources: does the user install anime extensions (like aniyomi), or are some pre-bundled?
- [ ] The 4 designs: does the user have references/names, or should we propose 4?
- [ ] Search: global search? By name, genre, source?
- [ ] Account: AniList OAuth login? Guest mode?
- [ ] Offline: downloads for offline viewing?
- [ ] History: do we track watch progress per episode?

> These are refined in `OTHER-PAGES.md` and answered in batches during planning.

---

_Last updated: Session 8 (initial draft). Evolves as we discuss._
