# Plan: Statistics & Watch Tracking (Future Session)

> **Status: PLANNING — not for current implementation.**
> This is a thorough plan for a future dedicated session.
> Created: Session 23.

## Goal

Track the user's own watching habits (NOT AniList tracking) and display them
in a beautiful Statistics section on the More page. The user explicitly said:
"We are not going to implement the tracking functionalities of the statistics
and stuff like that for the current time being. We will implement it later."

## What we track (all local, no external sync)

### 1. Watch time
- **Total watch time** (seconds → format as Xh Ym)
- **Per-day watch time** (for daily heatmap)
- **Per-week watch time** (for weekly heatmap)
- **Per-month watch time** (for monthly heatmap)
- **When the user watches most** (hour-of-day distribution, day-of-week distribution)

### 2. Episodes
- **Total episodes watched** (count of episodes where watch progress > 80%)
- **Episodes started but not finished** (progress 10–80%)
- **Episodes dropped** (progress < 10%)

### 3. Scores
- **Mean score** (average of user's anime ratings — from AniList or local)
- **Score distribution** (how many 10s, 9s, 8s, etc.)
- **Genre preference** (which genres the user rates highest)

### 4. Collection
- **Total anime in library** (from LibraryStore)
- **Total episodes available** (sum across all library anime)
- **Completion rate** (completed / total in library)

### 5. Genre tracking
- **Most-watched genres** (by episode count)
- **Highest-rated genres** (by mean score)
- **Genre distribution** (pie chart or bar chart)

## Data model (SQLDelight)

New tables in the `dataanime/` sqldelight source set:

### `watch_sessions.sq`
```sql
CREATE TABLE watch_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    anilist_id INTEGER NOT NULL,
    episode_url TEXT NOT NULL,
    episode_number REAL NOT NULL,
    start_time INTEGER NOT NULL,    -- epoch millis
    end_time INTEGER NOT NULL,      -- epoch millis
    duration_seconds INTEGER NOT NULL,
    completed INTEGER NOT NULL DEFAULT 0,  -- 1 if watched > 80%
    FOREIGN KEY (anilist_id) REFERENCES animes(id)
);
```

### `episode_views.sq`
```sql
CREATE TABLE episode_views (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    anilist_id INTEGER NOT NULL,
    episode_url TEXT NOT NULL,
    view_time INTEGER NOT NULL,     -- epoch millis
    duration_seconds INTEGER NOT NULL,
    FOREIGN KEY (anilist_id) REFERENCES animes(id)
);
```

## Tracking points (where to record)

1. **PlayerActivity.onPause()** — record a watch session (start→end time, duration).
2. **PlayerActivity.onDestroy()** — if duration > 30s, record as a watch session.
3. **WatchProgressStore.save()** — when progress > 80%, mark episode as "watched" in `episode_views`.
4. **LibraryStore.save()** — when an anime is saved, we can track collection growth.

## UI design (Statistics screen)

### Layout
- **Header card**: Total watch time (large number) + total episodes watched
- **Heatmap section**: GitHub-style contribution heatmap (daily watch activity)
  - Toggle: Daily / Weekly / Monthly
  - Color intensity = watch time that day
- **Charts section**:
  - Hour-of-day bar chart (when the user watches most)
  - Day-of-week bar chart
  - Genre distribution (top 5 genres by watch time)
  - Score distribution (bar chart: 1–10)
- **Stats grid**:
  - Mean score
  - Total anime in collection
  - Completion rate
  - Most-watched genre

### Implementation
- Use a Compose charting library (or custom Canvas-drawn charts for M3 Expressive)
- Custom heatmap using a `LazyRow` of `Canvas`-drawn day cells
- All data comes from SQLDelight queries (aggregated by day/week/month)

## Settings (user control)

In Settings → General:
- **Track watch time** (toggle, default ON)
- **Track episode views** (toggle, default ON)
- **Reset statistics** (button with confirmation dialog)
- **Export statistics** (future — export as JSON/CSV)

## Architecture

- `domain/statistics/` — interactor interfaces + models
  - `GetWatchTimeStats` — aggregates total/per-day/per-week
  - `GetEpisodeStats` — aggregates episode counts
  - `GetScoreStats` — aggregates score distribution
  - `GetGenreStats` — aggregates genre preferences
- `data/statistics/` — SQLDelight repository implementations
- `app/ui/statistics/` — Compose UI (StatisticsScreen + StatisticsViewModel)

## Dependencies

- Charting: custom Canvas drawing (no external library needed for bar charts + heatmap)
- The `animes` table already has `genre` (StringList) — we can use it for genre tracking
- AniList scores come from the AniList API (already cached in CacheManager)

## Open questions for the user (when we implement)

1. Should the heatmap show the last 30 days, 90 days, or a full year?
2. Should we track watch time per-episode or per-session?
3. Should the genre tracking use AniList genres or extension-provided genres?
4. Should we add a "streak" counter (days watched in a row)?
5. Should we add a "most-watched anime" leaderboard?

## Estimated effort

- Data model + tracking points: ~2 hours
- Statistics repository + interactors: ~2 hours
- UI (heatmap + charts + stats grid): ~4 hours
- Settings + polish: ~1 hour
- **Total: ~9 hours (1–2 sessions)**
