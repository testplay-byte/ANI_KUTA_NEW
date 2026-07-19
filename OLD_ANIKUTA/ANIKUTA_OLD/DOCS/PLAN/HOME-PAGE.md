# Home Page — Spec

> Detailed spec of the ANI-KUTA home page. The home page is the first screen
> users see — it's the discovery hub.

---

## Data source

**AniList GraphQL API** — all home page data comes from AniList (NOT from
aniyomi extensions). AniList provides: trending, popular, newly updated,
genres, schedules, ratings, episode counts, titles, descriptions, cover art.

> Why AniList? It's a rich, free, community-maintained anime database with a
> well-documented GraphQL API. It gives us ratings, sub/dub episode counts,
> genres, and schedules — exactly what the home page needs.

---

## Layout (top to bottom)

### 1. Hero section (top)
- Full-width featured anime (auto-rotating or staff-picked).
- Shows: large cover art / backdrop, title, short description, "Watch" button.
- TODO: is the hero anime picked by us, or is it "trending #1"?

### 2. Trending Now (carousel)
- Horizontal carousel of anime cards.
- **Each card shows:**
  - Cover art (poster)
  - **Rating** (e.g. 8.5/10 or 85%)
  - **Total episodes** (e.g. 24)
  - **Sub episodes** count (e.g. 24 sub)
  - **Dub episodes** count (e.g. 12 dub)
  - **Anime name**
  - **Genres** (e.g. Action, Fantasy)
- Swipe/scroll horizontally.

### 3. Freshly Updated (carousel)
- Anime with recently-released new episodes.
- Same card format as Trending.
- TODO: "freshly updated" = new episodes released, or newly added to AniList?

### 4. Browse by Genre (carousel)
- Shows the **top 5 genres** as cards (e.g. Action, Romance, Comedy, Fantasy, Drama).
- Each genre card → tapping it opens a genre-filtered browse page.
- **"All genres" button** → opens the full genre list.

### 5. Most Popular (carousel or grid)
- Popular anime (all-time or currently popular).
- Same card format.
- TODO: carousel or grid? User can probably choose.

### 6. Coming Up Next (schedule)
- Shows anime with upcoming/newly-released episodes.
- **Three sub-sections:**
  - **Today** — anime releasing/released today
  - **Tomorrow** — anime releasing tomorrow
  - **Next week** — anime releasing within the next 7 days
- Each item: anime name, episode number, release time.

---

## Customization (per-user)

The user can:
- **Show/hide any section** (e.g. hide "Most Popular" if they don't care).
- **Reorder sections** (TODO: drag-to-reorder, or just show/hide?).
- **Choose card style** (TODO: part of the 4 designs, or independent?).

> See `CUSTOMIZATION.md` for the full customization system.

---

## Interactions

- **Tap a card** → opens the anime detail page (`DETAIL-PAGE.md`).
- **Tap a genre** → opens genre-filtered browse.
- **Tap "All genres"** → opens full genre grid.
- **Pull to refresh** → re-fetch from AniList.
- **Long-press a card** → quick actions (save to library, share, hide).

---

## Open questions

- [ ] Hero: auto-rotate? How many? Picked how?
- [ ] "Freshly updated" definition?
- [ ] Section reorder: drag or just show/hide?
- [ ] Card style: fixed per-design or user-selectable?
- [ ] Pagination/infinite scroll on carousels, or fixed count?
- [ ] Loading states: skeletons? Shimmer?
- [ ] Error states: AniList down → cached data + retry?

---

_Last updated: Session 8 (initial draft)._
