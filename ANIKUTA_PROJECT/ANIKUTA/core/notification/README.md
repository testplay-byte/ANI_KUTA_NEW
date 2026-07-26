# app.confused.anikuta.core.notification

Notification channels, schedulers (ADR-014).

**Module path:** `core/notification`
**Type:** Android library
**Status:** ⚠️ Empty stub — NOT YET IMPLEMENTED

## Why this is a stub

Episode-release notifications (ADR-014) are a **planned but not-yet-implemented
feature**. ADR-014 specifies a dual-mode notification system:
- **AniList mode:** fire-and-forget at the scheduled release time.
- **Extension mode:** poll at the scheduled time, retry with backoff (10 min,
  20 min) until the episode appears.

The `:core:update-checker` module already implements the new-episode *detection*
logic (manual checking). The notification *scheduling* (WorkManager) + the
notification channels + the per-series/global preference UI are the missing
pieces that would live in this module.

This stub has no source files and is **not depended on by `:app`**. It's on the
Phase 9 roadmap.

## Note

Download notifications are a separate concern — they're already implemented in
`:core:download/DownloadNotificationManager.kt`. That code is not part of this
module's scope.
