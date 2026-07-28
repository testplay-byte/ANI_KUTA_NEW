# Anime Detail Page — Spec

> Detailed spec of the anime detail page. Reached when the user taps any anime
> card (from home, search, library, etc.).

---

## Data sources

- **AniList** — anime metadata: name, description, cover art, banner, rating,
  genres, total episodes, sub/dub counts, status (airing/finished), studio,
  season, tags.
- **aniyomi extension system** — episode-level data: which episodes are
  available to stream, from which sources, thumbnails, descriptions.

> This page is where AniList metadata + aniyomi extension data merge. AniList
> tells us "this anime has 24 episodes"; the extension system tells us "here's
> where you can stream episodes 1-24."

---

## Layout (top to bottom)

### 1. Header / hero
- **Banner image** (wide backdrop) at the top.
- **Cover art** (poster) overlapping the banner.
- **Anime name** (large).
- **Quick info row:** rating · status (airing/finished) · total episodes · season + year.
- **Genres** (pills/chips).
- **Action buttons:**
  - **Save** (add to library — heart/bookmark icon)
  - **Share** (share the anime link)
  - TODO: "Watch" button (starts first unwatched episode)?

### 2. Description
- Anime synopsis/description (from AniList).
- Expandable if long (show more / show less).

### 3. Episode list
- **Each episode row:**
  - **Thumbnail** (episode screenshot, if available from the extension)
  - **Episode number** + **title**
  - **Description** (if available)
  - **Watch status** indicator (watched / unwatched / partially watched)
- Sort: ascending/descending (toggle).
- TODO: group by season? If the anime has multiple seasons, how do we show them?

### 4. Sources (TODO: where in the layout?)
- Which extensions can stream this anime.
- User can pick a source (or auto-pick the best).
- TODO: a "Sources" section, or a dropdown in the header, or only in the player?

### 5. Trackers (TODO)
- AniList sync status (if logged in).
- TODO: show "tracked on AniList" badge?

### 6. Related / recommendations (TODO)
- Sequels, prequels, side stories, recommendations.
- From AniList's relations data.

---

## Interactions

- **Tap an episode** → opens the player at that episode.
- **Tap "Save"** → adds to library (heart fills).
- **Tap "Share"** → system share sheet.
- **Tap a genre** → opens genre-filtered browse.
- **Long-press an episode** → quick actions (mark watched, download, etc.).
- **Pull to refresh** → re-fetch metadata + episode list.

---

## Behind the scenes (aniyomi integration)

1. User opens detail page (passed the AniList anime ID).
2. UI asks backend for AniList metadata (by ID).
3. UI asks backend for available sources (which extensions have this anime).
   - Backend searches the user's installed anime extensions for the anime title.
   - Returns matching sources + their internal IDs.
4. UI asks backend for the episode list (from the selected/best source).
5. UI displays: AniList metadata + extension episode list merged.
6. User taps an episode → player opens → extension resolves the stream.

> See `ANIYOMI-INTEGRATION.md` for the full backend flow.

---

## Open questions

- [ ] Multiple seasons: separate detail pages, or one page with season tabs?
- [ ] Source selection: in the detail page, or only in the player?
- [ ] Related/recommendations: include or defer?
- [ ] Tracker integration: show on detail page, or only in settings?
- [ ] Episode thumbnails: always available from extensions, or fallback?

---

_Last updated: Session 8 (initial draft)._
