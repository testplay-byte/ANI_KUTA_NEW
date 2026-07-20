# Prompt 2 — Step 6 Implementation: Video Resolver as a Dedicated Module

You are now implementing Step 6 of the ANIKUTA project. This is the implementation prompt.

## What to build

Transform the basic `:feature:video-resolver` skeleton into a proper, dedicated module that handles video resolution from extensions.

### 1. VideoTitleParser (`:feature:video-resolver`)

Port and improve the old project's `VideoTitleParser.kt`:
- Parses video titles to extract: server name, audio version (SUB/DUB/HSUB), quality/resolution
- Handles various extension title formats:
  - `"ServerName - SUB - 1080p"` → server=ServerName, audio=SUB, quality=1080p
  - `"ServerName - DUB - 720p"` → server=ServerName, audio=DUB, quality=720p
  - `"1080p"` → quality=1080p (no server/audio info)
  - `"ServerName"` → server=ServerName (no audio/quality info)
- Provides a `ParsedVideo` data class with: serverName, audioVersion, quality, videoUrl, originalTitle
- Sorts videos by: server name (alphabetical) → audio version (SUB first, then DUB, then HSUB) → quality (highest first)

### 2. ResolverService (`:feature:video-resolver`)

Create a `ResolverService` that:
- Takes an `AnimeSource` and an `SEpisode`
- Calls `source.getVideoList(episode)` (or `source.getHosterList(episode)` + `source.getVideoList(hoster)` for ext-lib-16 sources)
- Handles both the old `getVideoList` API and the new `getHosterList` API
- Returns a `ResolverResult`:
  - `Success(List<ResolverServer>)` — servers with parsed audio/quality
  - `NoSources` — no videos available
  - `Error(message)` — fetch failed
- Proper error handling with try-catch + logging
- Timeout handling (10s per source call)

### 3. ResolverServer model

```kotlin
data class ResolverServer(
    val name: String,           // e.g. "Miruro", "Google Drive"
    val audioVersions: List<ResolverAudioVersion>,
)

data class ResolverAudioVersion(
    val label: String,          // "SUB", "DUB", "HSUB", "Unknown"
    val videos: List<ResolverVideo>,
)

data class ResolverVideo(
    val quality: String,        // "1080p", "720p", "480p", "Unknown"
    val url: String,
    val videoTitle: String,     // original title for fallback
)
```

### 4. Update VideoResolverSheet

Rewrite the bottom sheet UI:
- **NO drag handle** (design language principle #2)
- **Partial height** (principle #3)
- Shows the 3-tier hierarchy: Server → Audio → Quality
- Each server is an expandable section
- Within each server: audio versions (SUB/DUB chips)
- Within each audio version: quality buttons (1080p, 720p, etc.)
- Loading state: spinner + "Resolving video sources..."
- No sources state: "No video sources available" + "Install an anime extension..."
- Error state: error message + retry button
- Close button (X, top-right)
- Title: "Episode N" (where N is the episode number)

### 5. Update VideoResolverState

Update the state machine:
```kotlin
sealed interface VideoResolverState {
    data object Hidden : VideoResolverState
    data class Resolving(val episodeNumber: Int) : VideoResolverState
    data class Show(val episodeNumber: Int, val servers: List<ResolverServer>) : VideoResolverState
    data class NoSources(val episodeNumber: Int) : VideoResolverState
    data class Error(val episodeNumber: Int, val message: String) : VideoResolverState
}
```

### 6. Wire into the app

In `MainActivity.kt`:
- When an episode is tapped, call `ResolverService.resolve(source, episode)`
- Show the `VideoResolverSheet` with the resolving state
- When resolution completes, update the state to Show/NoSources/Error
- When a video is selected, dismiss the sheet (Phase 6 watch page will handle playback)

### 7. Dependencies

The `:feature:video-resolver` module needs:
```kotlin
implementation(projects.core.common)
implementation(projects.core.designsystem)
implementation(projects.core.sourceApi)  // for AnimeSource, SEpisode, Video, Hoster
```

## Key rules to follow

1. **Package**: `app.confused.anikuta.feature.videoresolver`
2. **DI**: Use Koin (ADR-023). Inject via constructor.
3. **Logging**: Use `android.util.Log` with tag `AnikutaResolver`.
4. **Error handling**: Every source call must have try-catch + logging + user-facing error state.
5. **No local builds**: CI only (ADR-003).
6. **File size**: Each file max ~300 lines.
7. **Notifications**: Send ntfy.sh notifications (topic TASKISDONE).
8. **Design language**: No drag handle, partial height, RobotoFamily ExtraBold, #B1F256 theme.
9. **Commit on a branch**: `feature/step6-video-resolver`.
10. **Documentation**: Add KDoc to every public class/function.

## After implementation

1. Commit, push to `feature/step6-video-resolver`
2. Send summary notification (topic TASKISDONE)
3. Report back with full file list + decisions + issues.
