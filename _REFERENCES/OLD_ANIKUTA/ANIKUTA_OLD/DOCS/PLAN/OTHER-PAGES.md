# Other Pages — Spec

> The home page and detail page are specified separately. This file covers all
> other pages the app needs. Most are still at the "list + brief spec" stage —
> they'll be detailed as we plan each phase.

---

## Page list

| Page | Purpose | Status |
|------|---------|--------|
| Home | Discovery hub (AniList) | `HOME-PAGE.md` ✅ |
| Detail | Anime detail + episodes | `DETAIL-PAGE.md` ✅ |
| Player | Video playback | Brief below |
| Search | Find anime | Brief below |
| Library | Saved anime | Brief below |
| History | Watch progress | Brief below |
| Settings | Customization + config | Brief below |
| Extensions | Install/manage anime sources | Brief below |
| Browse (genre) | Genre-filtered list | Brief below |

---

## Player

- Full-screen video player (from aniyomi's MPV-based player — see
  `DOCS/REFERENCE-DOCS/SUBSYSTEMS/PLAYER.md`).
- **Plays:** the stream resolved by the selected aniyomi extension.
- **Features (from aniyomi):** seek, play/pause, skip, audio/subtitle track
  selection, gestures, PiP.
- **Our additions:** which design's player overlay is shown.
- **Saves progress** to local DB + syncs to AniList (if logged in).
- TODO: custom skip-intro button? Custom buttons?

## Search

- Global search across AniList (by anime name).
- TODO: also search installed extensions? (aniyomi searches extensions.)
- Results → detail page.
- Filters: genre, year, status, sort by.
- TODO: search history? Suggestions?

## Library

- The user's saved anime (from the detail page "Save" button).
- Grouped by category (user-created categories?).
- Sort: title, date added, last watched, unread episodes.
- Filter: by category, by status.
- TODO: grid or list? User choice?

## History

- Recently watched episodes (per episode: anime name, episode number, watch time, thumbnail).
- "Continue watching" row at the top (resume from where you left off).
- TODO: how far back does history go? Clearable?

## Settings

- **Design** → pick from the 4 designs.
- **Theme** → per-design theme variants + accent colors.
- **Custom theming** → limited custom color overrides.
- **Language** → app language.
- **Font** → font style choices.
- **AniList** → login/logout, sync settings.
- **Sources/Extensions** → link to extensions page.
- **Player** → player defaults (decoder, subtitles, gestures).
- **Data & storage** → cache, downloads, backup.
- **About** → app info, version, licenses.

> See `CUSTOMIZATION.md` for the design/theming system.

## Extensions

- List of installed anime extensions (from aniyomi's extension system).
- Install new extensions (browse the extension catalog).
- Enable/disable extensions.
- TODO: where does the extension catalog come from? (aniyomi's extension repos?)

## Browse (genre)

- Reached from the home page "All genres" or a genre card.
- Shows all anime in a genre (from AniList).
- Grid/carousel of cards.
- Sort + filter.
- TODO: pagination (infinite scroll)?

---

## Navigation

- **Bottom nav** (main tabs): Home, Library, History, Search, More.
- **More** tab → Settings, Extensions, About.
- TODO: 4 or 5 bottom-nav tabs? Does "More" collapse several?

---

## Open questions (all pages)

- [ ] Bottom nav: which tabs? (Home, Library, History, Search, More?)
- [ ] Search: AniList only, or also extensions?
- [ ] Library: categories? Grid/list toggle?
- [ ] Player: custom buttons (skip intro)?
- [ ] Extensions: where's the catalog from?
- [ ] Settings: any pages beyond the list above?
- [ ] Onboarding flow for first launch?

---

_Last updated: Session 8 (initial draft). Detailed per-page as we plan each phase._
