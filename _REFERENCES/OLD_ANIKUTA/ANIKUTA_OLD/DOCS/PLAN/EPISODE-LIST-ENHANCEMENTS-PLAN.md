# Plan: Episode List Enhancements (Future Session)

> **Status: PLANNING — not for current implementation.**
> This is a thorough plan for a future session.
> Created: Session 23.

## Goal

Enhance the episode list on the anime detail page to support:
1. **Episode titles** (not just "Episode 1")
2. **Episode summaries** (short description)
3. **Episode thumbnails** (preview image)
4. **Auto-fetch** episode info from other sources if the extension doesn't provide it
5. **User settings** to toggle each feature on/off
6. **Soft loading** — show episodes first, then enhance with info in the background

## Current state

- The episode list shows: episode number badge + episode name (from the extension)
- The `SEpisode` model has: `url`, `name`, `episode_number`, `date`, `scanlator`
- No thumbnail, no summary, no enhanced title
- The extension's `getEpisodeList()` returns `List<SEpisode>` — some extensions
  put rich info in `name` (e.g. "Episode 5 - The Dragon's Labyrinth"), but we
  don't parse or display it specially

## What aniyomi does (from research)

- aniyomi's `SEpisode` has the same fields as ours
- aniyomi doesn't have a separate "episode thumbnail" field in `SEpisode`
- aniyomi's episode list UI shows: episode number, name, date, and a
  "seen" indicator (progress bar)
- aniyomi doesn't auto-fetch episode info from other sources — it only uses
  what the extension provides
- **Conclusion**: this feature is mostly NOVEL — we build it from scratch

## Data model changes

### New: `EpisodeMetadata` (domain model)
```kotlin
data class EpisodeMetadata(
    val episodeUrl: String,        // key — matches SEpisode.url
    val anilistId: Int,            // which anime this belongs to
    val title: String?,            // "The Dragon's Labyrinth" (without "Episode 5 - ")
    val summary: String?,          // short description
    val thumbnailUrl: String?,     // preview image
    val airDate: Long?,            // epoch millis
    val source: String,            // "extension" or "anilist" or "tmdb" or "manual"
    val fetchedAt: Long,           // when this was fetched (for cache TTL)
)
```

### SQLDelight table: `episode_metadata.sq`
```sql
CREATE TABLE episode_metadata (
    episode_url TEXT NOT NULL,
    anilist_id INTEGER NOT NULL,
    title TEXT,
    summary TEXT,
    thumbnail_url TEXT,
    air_date INTEGER,
    source TEXT NOT NULL DEFAULT 'extension',
    fetched_at INTEGER NOT NULL,
    PRIMARY KEY (episode_url, anilist_id),
    FOREIGN KEY (anilist_id) REFERENCES animes(id)
);
```

## Title parsing

The extension often puts rich info in `SEpisode.name`:
- `"Episode 5 - The Dragon's Labyrinth"` → title = "The Dragon's Labyrinth"
- `"EP 5 - The Dragon's Labyrinth"` → title = "The Dragon's Labyrinth"
- `"The Dragon's Labyrinth"` → title = "The Dragon's Labyrinth" (no prefix)
- `"Episode 5"` → title = null (no title, just episode number)

**Parser**: `EpisodeTitleParser.parse(name, episodeNumber)` → `String?`
- Strip `"Episode X - "`, `"EP X - "`, `"Ep X - "` prefixes
- If the remaining text is just the episode number → return null
- Otherwise return the remaining text

## Auto-fetch from other sources

If the extension doesn't provide titles/summaries/thumbnails, we can fetch from:

### 1. AniList (primary)
- AniList has episode metadata for some anime (titles, air dates, descriptions)
- Query: `Media(id: $anilistId) { episodes { title episode airingAt description } }`
- This gives us per-episode titles + descriptions + air dates
- **Limitation**: AniList episode data is incomplete for many anime

### 2. TheTVDB / TMDB (secondary, future)
- Some anime have rich episode metadata on TVDB/TMDB
- Requires API keys + mapping AniList→TVDB/TMDB IDs
- **Defer to later** — AniList is sufficient for v1

### 3. Extension-provided (fallback)
- Some extensions put the title in `SEpisode.name`
- The parser above handles this

## Soft loading strategy

1. **Phase 1 (instant)**: Show the episode list from the extension (or cache).
   Display: episode number + whatever name the extension gives.
2. **Phase 2 (background)**: Check if we have `EpisodeMetadata` for each
   episode in the DB. If yes, show the title/summary/thumbnail immediately.
3. **Phase 3 (background, if enabled)**: If metadata is missing AND the user
   has "auto-fetch" enabled, query AniList for episode metadata. Update the
   list smoothly as data arrives.
4. **Cache**: Fetched metadata is cached in `episode_metadata` table with a
   24-hour TTL. On next visit, Phase 2 shows cached data instantly.

## User settings (Settings → Player or Settings → General)

New settings:
- **Show episode titles** (toggle, default ON)
- **Show episode summaries** (toggle, default ON)
- **Show episode thumbnails** (toggle, default ON)
- **Auto-fetch episode info** (toggle, default ON)
  - When ON: fetches from AniList if the extension doesn't provide info
  - When OFF: only shows what the extension provides

## UI changes (DetailScreen episode list)

Current episode row:
```
[EP 5]  Episode 5 - The Dragon's Labyrinth
```

Enhanced episode row (when metadata is available):
```
[thumbnail]  EP 5  The Dragon's Labyrinth
                    Summary text here, up to 2 lines...
                    📅 Aired 2024-03-15
```

Enhanced episode row (when metadata is NOT available):
```
[EP 5]  Episode 5 - The Dragon's Labyrinth
        (same as current — no thumbnail, no summary)
```

### Layout
- Thumbnail: 120×68dp (16:9) on the left, rounded corners
- Episode number: badge or chip
- Title: bold, 1 line (the parsed title, or fallback to SEpisode.name)
- Summary: bodySmall, max 2 lines, ellipsis
- Air date: labelSmall, optional

## Architecture

- `domain/episode/metadata/` — `EpisodeMetadata` model + repository interface
- `data/episode/metadata/` — SQLDelight repository impl
- `domain/episode/interactor/` — `GetEpisodeMetadata`, `FetchEpisodeMetadataFromAniList`
- `data/anilist/` — extend `AniListRepository` with `getEpisodeMetadata(anilistId)`
- `app/ui/detail/` — update `DetailViewModel` + `DetailScreen` episode list
- `app/.../player/PlayerPreferences.kt` — add the 4 new toggle prefs

## Open questions for the user (when we implement)

1. Should the thumbnail be tappable to open a preview?
2. Should we show a "loading" shimmer where the thumbnail will appear?
3. Should the auto-fetch use AniList only, or also TVDB/TMDB?
4. Should we cache thumbnails locally (Coil does this automatically)?
5. Should the user be able to manually edit episode titles?

## Estimated effort

- Data model + SQLDelight table: ~1 hour
- EpisodeTitleParser: ~30 min
- AniList episode metadata query: ~1 hour
- DetailViewModel changes (soft loading): ~2 hours
- DetailScreen episode list UI: ~2 hours
- Settings + preferences: ~30 min
- **Total: ~7 hours (1 session)**
