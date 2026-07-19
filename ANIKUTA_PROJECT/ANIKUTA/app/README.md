# ANIKUTA app module

The main application module. Contains:
- `App.kt` — the `Application` class (Koin DI setup, logging initialization).
- `MainActivity.kt` — the single Activity (edge-to-edge, Compose host).
- `AndroidManifest.xml` — app declaration.
- Resources (strings, themes).

**Application ID:** `app.confused.anikuta` (ADR-031)
**Display name:** ANIKUTA
**Build namespace:** `anikuta.*` convention plugins (ADR-031)

**Dependencies:** `:core:common`, `:core:designsystem`, Koin, Voyager, kotlinx-coroutines, logcat.

**Status:** Phase 1 skeleton — trivial "ANIKUTA" text on screen. Navigation + features come in later phases.
