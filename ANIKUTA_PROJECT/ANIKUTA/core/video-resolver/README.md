# app.confused.anikuta.core.videoresolver

Video resolver logic + types (server/audio/quality hierarchy + the resolver service).

**Module path:** `core/video-resolver`
**Type:** Android library (no Compose)
**Status:** Phase 8 (Doc 04 violations 3+4 fix)

## Why this module exists

The video-resolver logic + types previously lived in `:feature:video-resolver`.
`feature:watch` and `feature:download` imported them from there, creating two
feature→feature dependencies (Doc 04 violations 3+4).

Phase 8 moves the logic-only types + service here (a core module). The three
feature modules (`:feature:video-resolver`, `:feature:watch`, `:feature:download`)
now depend on this core module instead of one another.

## What's here

- `ResolverService` — calls `source.getHosterList` / `getVideoList` + groups
  results into the 3-tier hierarchy (Server → Audio → Quality).
- `ResolverResult` — Success / NoSources / Error sealed type.
- `VideoResolverState` — the UI state machine that drives `VideoResolverSheet`
  (which stays in `:feature:video-resolver`).
- `ResolverServer`, `ResolverAudioVersion`, `ResolverVideo`, `SubtitleTrack` —
  the resolver hierarchy data types.
- `VideoTitleParser` — title parsing + grouping logic.
- `VideoResolverStrategy` + `StructuredResolverStrategy` + `RawResolverStrategy`
  + `ResolverStrategyPicker` — the strategy pattern.

## What stayed in `:feature:video-resolver`

- `VideoResolverSheet.kt` — the bottom-sheet UI.
- `ResolverServerContent.kt` — the server accordion UI.
- `ResolverStates.kt` — the `ResolvingContent` / `NoSourcesContent` /
  `ErrorContent` composables (UI for the resolver state machine).

These are pure Compose UI — they belong in a feature module, not core.
