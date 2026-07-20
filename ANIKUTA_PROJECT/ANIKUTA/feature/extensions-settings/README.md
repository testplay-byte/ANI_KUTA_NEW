# app.confused.anikuta.feature.extensionssettings

Extensions management (3-category, ADR-016).

**Module path:** `feature/extensions-settings`
**Type:** Android library with Compose
**Status:** UI scaffold — `ExtensionsSettingsScreen.kt` renders the 3-category
structure (Trusted Sources → Installed → Available) with an Anime/Manga
`TwoWayToggle` on top and per-section empty-state copy. Real data binding
(ViewModel + Repository + extension repo fetching + drag-reorderable trusted
sources) lands in a later phase.
