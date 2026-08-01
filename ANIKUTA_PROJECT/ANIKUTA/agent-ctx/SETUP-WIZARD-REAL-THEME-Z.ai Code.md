# SETUP-WIZARD-REAL-THEME — Implementation Complete

**Agent:** Z.ai Code (implementer)
**Task ID:** SETUP-WIZARD-REAL-THEME
**Date:** 2026-08-01
**Scope:** Recreate the Setup Wizard to use the REAL ANIKUTA design system
(`AnikutaTheme` from `:core:designsystem`) instead of its own separate
`SetupWizardTheme` / `WizardPalette` color system.

## Result

All planned items implemented. The `:feature:setup-wizard` module compiles
cleanly (verified with `:feature:setup-wizard:compileDebugKotlin` and
`:app:compileDebugKotlin` — both BUILD SUCCESSFUL with only pre-existing
deprecation warnings). The wizard now looks and feels exactly like the rest
of the ANIKUTA app: same surfaces, same accent colors, same typography,
same shapes. The user sees the theme change LIVE as they pick palettes on
the Theme screen.

### Files modified

| File | Change |
|---|---|
| `feature/setup-wizard/src/main/java/.../components/WizardVisuals.kt` | Added a small local `WizardPalette` data class + a `wizardPaletteFromMaterialTheme()` composable helper. Replaced the import of the deleted `theme.WizardPalette` with the local definition. The visual functions themselves are unchanged — they still take `WizardPalette`, but it's now constructed from `MaterialTheme.colorScheme` (reflecting the user's real theme). |
| `feature/setup-wizard/src/main/java/.../SetupWizardApp.kt` | Full rewrite of the main composable + all 15 screens to use `MaterialTheme.colorScheme.*` instead of `palette.*`. The wizard is now wrapped in `AnikutaTheme(...)` with reactive `ThemePreferences` reads (same pattern as `MainActivity.kt`). The Theme screen writes to `themePrefs.accentPreset.set(...)` / `applyFullPalettePreset(...)` / `themeMode.set(...)` / `amoled.set(...)` IMMEDIATELY on tap — the wrapper recomposes live with a smooth cross-fade. The Poison screen keeps its dark-red aesthetic via a local `MaterialTheme(colorScheme = poisonScheme)` override. |

### Files deleted (no longer needed)

| File | Reason |
|---|---|
| `feature/setup-wizard/src/main/java/.../theme/Color.kt` | All wizard palette colors (Lime/Teal/Purple/Coral/Poison + light surfaces). Replaced by the real ANIKUTA colors in `:core:designsystem` (`BgDark`, `Surface1Dark`, etc.) accessed via `MaterialTheme.colorScheme.*`. |
| `feature/setup-wizard/src/main/java/.../theme/Type.kt` | `SetupWizardTypography` M3 type scale. The wizard now uses the real `AnikutaTypography` from `AnikutaTheme`. |
| `feature/setup-wizard/src/main/java/.../theme/WizardPalette.kt` | `WizardPalette` data class + `AllPalettes` / `PaletteNames` / `PoisonPalette` + `SetupWizardTheme` composable. The wizard now uses `AnikutaTheme`. The small `WizardPalette` data class still exists (for the visuals only), but it's moved into `components/WizardVisuals.kt` and is constructed from `MaterialTheme.colorScheme`. |

The now-empty `theme/` folder is also gone.

## Key architectural changes

### 1. `SetupWizardApp` now wraps in `AnikutaTheme` (reactive)

Before:
```kotlin
SetupWizardTheme(palette = effectivePalette, isDark = isDark) {
    Surface(...) { ... }
}
```

After:
```kotlin
val themePrefs = koinInject<ThemePreferences>()
val themeMode by themePrefs.themeMode.changes().collectAsStateWithLifecycle(...)
val amoled by themePrefs.amoled.changes().collectAsStateWithLifecycle(...)
val accentPreset by themePrefs.accentPreset.changes().collectAsStateWithLifecycle(...)
val customAccentArgb by themePrefs.customAccentColor.changes().collectAsStateWithLifecycle(...)
val customAccent = Color(customAccentArgb.toLong() and 0xFFFFFFFF)
val paletteMode by themePrefs.paletteMode.changes().collectAsStateWithLifecycle(...)
// ... (also customBackgroundColor / customCardColor / customTextColor)

AnikutaTheme(
    themeMode = themeMode,
    amoled = amoled,
    accentPreset = accentPreset,
    customAccentColor = customAccent,
    paletteMode = paletteMode,
    customBackground = ...,
    customCard = ...,
    customText = ...,
) {
    Surface(...) { ... }
}
```

This is the SAME pattern as `MainActivity.kt`. The wizard now uses the real
app theme with the user's chosen accent / mode / palette.

### 2. Theme screen writes to `ThemePreferences` IMMEDIATELY (live preview)

The Theme screen now shows the real `AccentPreset` enum:
- **10 accent-only presets** (LIME, CORAL, ROSE, AMBER, RED, TEAL, BLUE,
  CYAN, VIOLET, EMERALD) in a horizontal carousel.
- **5 full-palette presets** (MIDNIGHT, SUNSET, FOREST, CHARCOAL, COFFEE)
  in a separate row, each with a mini palette swatch (bg + card + accent).
- **CUSTOM** option at the end of the accent row.
- **Theme mode** toggle (Dark / Light / System).
- **AMOLED** toggle (only visible in dark mode).

Tap handlers:
```kotlin
// Accent-only preset
if (paletteMode == PaletteMode.FULL) {
    themePrefs.paletteMode.set(PaletteMode.SIMPLIFIED)
}
themePrefs.accentPreset.set(preset)

// Full-palette preset
themePrefs.applyFullPalettePreset(preset)

// Theme mode
themePrefs.themeMode.set(mode)

// AMOLED
themePrefs.amoled.set(it)

// Custom
themePrefs.selectCustom()
```

Because `SetupWizardApp`'s `AnikutaTheme` wrapper reads these same prefs
reactively, the wizard LIVE-UPDATES as the user taps — the entire wizard
recomposes with the new colors via `animateColorAsState` (~400ms cross-fade).

### 3. All screens use `MaterialTheme.colorScheme.*` instead of `palette.*`

Mapping (wizard's old palette → M3 colorScheme):
- `palette.primary` → `MaterialTheme.colorScheme.primary`
- `palette.onPrimary` → `MaterialTheme.colorScheme.onPrimary`
- `palette.primaryContainer` → `MaterialTheme.colorScheme.primaryContainer`
- `palette.onPrimaryContainer` → `MaterialTheme.colorScheme.onPrimaryContainer`
- `palette.background` → `MaterialTheme.colorScheme.background`
- `palette.surface1` → `MaterialTheme.colorScheme.surface`
- `palette.surface2` → `MaterialTheme.colorScheme.surfaceVariant`
- `palette.surface3` → `MaterialTheme.colorScheme.surfaceVariant` (closest M3 role)
- `palette.surface4` → `MaterialTheme.colorScheme.outlineVariant`
- `palette.surface5` → `MaterialTheme.colorScheme.outline`

All `palette: WizardPalette` parameters were REMOVED from screen composable
signatures (`WelcomeScreen`, `ThemeScreen`, `FolderScreen`, `PermissionsScreen`,
`RestoreScreen`, `FormatScreen`, `ProcessingScreen`, `SummaryScreen`,
`LinkingScreen`, `ManualScreen`, `RestoreSummaryScreen`,
`RestoreProcessingScreen`, `RestoreSuccessScreen`, `PoisonScreen`,
`FinishScreen`) and from shared components (`ScreenLayout`, `PageHeading`,
`ActionRow`, `WizardButton`).

### 4. Visuals still take `WizardPalette` (built from MaterialTheme)

The animated Canvas visuals in `WizardVisuals.kt` need a handful of color
"tones" (primary, primaryContainer, surface1..5, background) that don't all
have exact M3 equivalents. Rather than rewrite all 14 visuals, I:

1. Defined a small `WizardPalette` data class locally in
   `components/WizardVisuals.kt` (NOT in a separate `theme/` package).
2. Added a `wizardPaletteFromMaterialTheme()` composable helper that builds
   a `WizardPalette` from the current `MaterialTheme.colorScheme`, deriving
   the intermediate tones (surface3, surface4, surface5) as blends between
   the M3 surface roles.

Screens call this helper when they need to pass a palette to a visual:
```kotlin
val palette = wizardPaletteFromMaterialTheme()
WelcomeVisual(palette, modifier = ...)
```

The visuals themselves are unchanged — they still take `WizardPalette` and
use `palette.primary`, `palette.surface3`, etc. But those values now come
from `MaterialTheme.colorScheme`, so they reflect the user's chosen theme.

### 5. Poison screen forces a red `MaterialTheme` override

The "Choose Your Poison" ad-consent screen keeps its dark-red aesthetic
regardless of the user's chosen theme. Per the task spec, the wizard wraps
JUST that screen's content in a `MaterialTheme(colorScheme = ...)` override:

```kotlin
WizardStep.POISON -> {
    val baseScheme = MaterialTheme.colorScheme
    val poisonScheme = baseScheme.copy(
        primary = Color(0xFFFF5252),
        onPrimary = Color(0xFF1A0000),
        primaryContainer = Color(0xFF5C1A1A),
        onPrimaryContainer = Color(0xFFFFE5E5),
        background = Color(0xFF1A0808),
        surface = Color(0xFF240D0D),
        surfaceVariant = Color(0xFF2E1414),
        onBackground = Color(0xFFFFEAEA),
        onSurface = Color(0xFFFFEAEA),
    )
    MaterialTheme(colorScheme = poisonScheme) {
        PoisonScreen(...)
    }
}
```

The rest of the wizard continues to use the user's real theme.

### 6. `WizardState` simplified

Removed `paletteIndex` and `themeMode` fields — these now live in
`ThemePreferences` and are read reactively at the top of `SetupWizardApp`.
`WizardState` only carries non-theme wizard data (step, folder, permissions,
linked anime, ad settings).

The wizard's local `ThemeMode` enum (with `label`) is also removed — the
wizard now uses `app.confused.anikuta.core.preferences.ThemeMode` directly,
with a small `themeModeLabel(mode)` helper for the UI text.

### 7. `applyPreferences` simplified

Theme preferences are now applied LIVE on the Theme screen (the user picks
→ pref is written → AnikutaTheme recomposes). So the `applyPreferences`
lambda at the Finish screen no longer needs to apply theme prefs — it only
persists:
- Ad preferences (`AdsPreferences.setAdsEnabled`, `setDailyQuota`,
  `setAdName`, `setAdTiming`)
- Folder URI (`DownloadPreferences.downloadFolderUri().set(...)`)
- Mark wizard completed (`SetupWizardPreferences.setCompleted(true)`)

## Real-backend integration preserved (unchanged)

All the real-backend integration from the previous SETUP-WIZARD-FEATURE
task is preserved verbatim:

- **Folder screen**: `OpenDocumentTree()` SAF picker, takes persistable
  URI permission, parses the tree URI to extract a display name.
- **Permissions screen**: real system intents (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`,
  `POST_NOTIFICATIONS` runtime contract, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
  `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`). Re-checks on `ON_RESUME`
  via `LifecycleEventObserver`.
- **Restore screen**: `OpenDocument()` file picker, takes persistable read
  permission.
- **Poison screen**: 3-step ad-consent flow (name / frequency / timing) —
  writes to `AdsPreferences` on Confirm.
- **Finish screen**: `applyPreferences()` persists ad settings + folder URI
  + marks completed, then calls `onComplete()` (which flips `wizardDone`
  in `MainActivity`).

## Build verification

Verified locally with `./gradlew`:

```
:feature:setup-wizard:compileDebugKotlin  →  BUILD SUCCESSFUL
:app:compileDebugKotlin                   →  BUILD SUCCESSFUL
```

Only pre-existing deprecation warnings remain (e.g.
`Icons.Filled.ArrowBack` → `Icons.AutoMirrored.Filled.ArrowBack`).
No new errors or warnings introduced.

## Notes for future agents

- The wizard's `WizardPalette` data class is now an INTERNAL detail of the
  visuals package — it's only there because the visuals need surface
  variants (surface3/4/5) that don't have exact M3 equivalents. Future
  visual additions should keep using `wizardPaletteFromMaterialTheme()`.
- The `MainActivity.kt` comment about the wizard "bringing its own theme"
  is now slightly outdated — the wizard uses `AnikutaTheme` directly. The
  code path is unchanged, but if you're touching `MainActivity`, consider
  updating the comment.
- The `:feature:setup-wizard` module's `build.gradle.kts` already had
  `implementation(projects.core.designsystem)` as a dependency, so no
  build file changes were needed.
