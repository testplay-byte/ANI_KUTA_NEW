# Prompt 2 — Step 5 Implementation: Anime Details with Real Episodes

You are now implementing Step 5 of the ANIKUTA project. This is the implementation prompt.

## What to build

Implement priority-based source matching and real episode lists on the anime details screen.

### 1. SourceMatcher module (`:data:extension` or a new `:core:source-matcher`)

Create a `SourceMatcher` that:
- Takes an anime title (from AniList) and searches trusted sources for it
- Searches sources in priority order (the order they appear in the Trusted Sources list)
- Calls `source.getSearchAnime(page=1, query=title, filters=AnimeFilterList(emptyList()))` on each source
- Returns the first source that has results (the matched source + the SAnime object)
- If no source has the anime, returns null (the UI shows "No episodes loaded" + "No sources have this anime")
- Proper logging: `Log.i("AnikutaSourceMatcher", "Searching $sourceCount sources for '$title'")`
- Proper error handling: if a source throws, log the error and continue to the next source

### 2. Update AnimeDetailScreen (`:feature:anime-details`)

The current screen shows AniList data + a "No episodes loaded" empty state. Update it to:
- After loading AniList data, call `SourceMatcher.match(title)` to find a source
- If a source is found, call `source.getEpisodeList(sAnime)` to fetch real episodes
- Show real episode rows (replacing the "No episodes loaded" empty state)
- Show a "Searching sources..." loading state while matching
- Show a "No sources have this anime" state if no source matches
- Show a source indicator (which source the episodes came from)

### 3. Episode rows

Each episode row should show:
- Episode number (circle badge, primaryContainer)
- Episode name
- Play icon (tap → opens video resolver, but for now just log it)
- Watched state: grayscale + alpha 0.55f (via RenderEffect, API 31+)
- Alternating backgrounds (zebra stripe)

The watched state should be toggleable (tap the row → toggle watched). Use in-memory state for now (will be persisted later).

### 4. Source switching

Add a source switcher to the details screen:
- A dropdown or chip showing the current source name
- Tapping it shows a list of all sources that have this anime (search all sources, not just the first match)
- Selecting a different source re-fetches episodes from that source
- The selected source is persisted per-anime in SharedPreferences (key: `source_pref_<anilistId>`)

### 5. Toast notifications

Show Toast messages for:
- "No sources found for this anime" (when SourceMatcher returns null)
- "Failed to load episodes: <error>" (when episode fetch fails)
- "Switched to <source name>" (when source is switched)

### 6. Dependencies to add

The `:feature:anime-details` module needs:
```kotlin
implementation(projects.data.extension)  // for AnimeExtensionManager
implementation(projects.core.sourceApi)  // for AnimeSource, SEpisode, etc.
```

The `:app` module needs to pass `AnimeExtensionManager` to the detail screen.

### 7. Koin wiring

Add `SourceMatcher` to the Koin module:
```kotlin
single<SourceMatcher> { SourceMatcher(get<AnimeExtensionManager>()) }
```

## Key rules to follow

1. **Package names**: Your code uses `app.confused.anikuta.*`. The source-api uses `eu.kanade.tachiyomi.animesource.*` (for extension compat).
2. **DI**: Use Koin (ADR-023). Inject via constructor parameters.
3. **Logging**: Use `android.util.Log` with tags: `AnikutaSourceMatcher`, `AnikutaDetailUI`. Log meaningful actions + errors. (ADR-033)
4. **Error handling**: Every source call must have try-catch with proper error logging + Toast.
5. **No local builds**: Do NOT run `./gradlew assembleDebug`. CI builds (ADR-003).
6. **File size**: Each file max ~300 lines.
7. **Notifications**: Send ntfy.sh notifications (topic TASKISDONE) when you start and finish.
8. **Design language**: Use RobotoFamily + FontWeight.ExtraBold for bold text. Use #B1F256 theme. Edge-to-edge. CollapsingHeader.
9. **Commit on a branch**: Create a branch `feature/step5-source-matching`, commit there, push.
10. **Documentation**: Add KDoc to every public class/function.

## After implementation

1. Commit all files with descriptive messages
2. Push to `feature/step5-source-matching` branch
3. Send a detailed summary notification:

```bash
curl -s -H "Title: ANIKUTA Agent — Step 5 Complete" -d "🟩🟩🟩🟩🟩🟩🟩🟩

Step 5 complete! Source matching + real episodes implemented.

Files created/modified:
[LIST ALL FILES]

Key decisions:
[LIST decisions]

What works:
- SourceMatcher searches trusted sources by title (priority-based)
- Real episode lists from matched source
- Source switching (sticky per-anime)
- Toast notifications for errors
- Watched state (grayscale + blur)
- Proper logging

CI status: [pass/fail]" https://ntfy.sh/TASKISDONE
```

4. Report back with the full file list and any issues encountered.
