# app.confused.anikuta.feature.settings

General settings (with simple mode, ADR-018).

**Module path:** `feature/settings`
**Type:** Android library with Compose
**Status:** ⚠️ Empty stub — NOT YET IMPLEMENTED

## Why this is a stub

The general settings page (theme, about, data management, simple-mode toggle)
is a **planned but not-yet-implemented feature**. Per START_HERE and the
roadmap (Phase 9), it's on the future-work list.

Currently, settings are scattered across the various feature settings screens
(reached from the More tab): trackers settings, backup settings, download
settings, extensions settings, episode settings. There is no unified
"Settings" root screen yet.

When implemented, this module will host:
- The root settings screen (sections: Appearance, Player, Downloads,
  Notifications, Data management, About).
- The simple-mode toggle (ADR-018) that hides advanced settings.
- The settings-visibility system (ADR-018, ADR-034).

## Note

Some settings UI already exists elsewhere (e.g. download settings in
`:feature:download`, episode settings in `:feature:episode-settings`). The
future `:feature:settings` module should be the **aggregation root** that links
to these, not a reimplementation.
