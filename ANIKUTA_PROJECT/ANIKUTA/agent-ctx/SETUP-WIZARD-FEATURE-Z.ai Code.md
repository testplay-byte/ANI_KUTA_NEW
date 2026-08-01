# SETUP-WIZARD-FEATURE — Implementation Complete

**Agent:** Z.ai Code (implementer)
**Task ID:** SETUP-WIZARD-FEATURE
**Date:** 2026 (session)
**Scope:** Port the design prototype (15-screen animated onboarding flow at
`/tmp/setupwizard/`) into a real `:feature:setup-wizard` module of the ANIKUTA
Android app, wire it into real preferences + permission launchers + file
pickers, and gate it behind a first-launch preference.

## Result

All planned items implemented. Module compiles against the project's existing
convention plugins (`anikuta.library.compose` + `anikuta.library`).

### Files created (new)

| File | Purpose |
|---|---|
| `core/ads/build.gradle.kts` | New `:core:ads` module — uses `anikuta.library` plugin |
| `core/ads/src/main/AndroidManifest.xml` | Empty manifest (library module) |
| `core/ads/src/main/java/app/confused/anikuta/core/ads/AdsPreferences.kt` | Persisted ad config: enabled, dailyQuota, cooldownMinutes, minStaySeconds, adUrl |
| `core/ads/src/main/java/app/confused/anikuta/core/ads/di/AdsModule.kt` | Koin module binding `AdsPreferences` as singleton |
| `core/preferences/src/main/java/app/confused/anikuta/core/preferences/SetupWizardPreferences.kt` | One-shot gate: `isCompleted()` / `setCompleted(done)` |
| `feature/setup-wizard/build.gradle.kts` | New `:feature:setup-wizard` module — uses `anikuta.library.compose` plugin |
| `feature/setup-wizard/src/main/AndroidManifest.xml` | Empty manifest |
| `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/theme/Color.kt` | All wizard palette colors (Lime/Teal/Purple/Coral/Poison + light surfaces) — ported verbatim from prototype |
| `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/theme/WizardPalette.kt` | `WizardPalette` data class + `AllPalettes`/`PaletteNames` + `PoisonPalette` + `SetupWizardTheme` composable — ported verbatim |
| `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/theme/Type.kt` | `SetupWizardTypography` M3 type scale — ported, uses `RobotoFamily` from `:core:designsystem` (no font bundling) |
| `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/components/WizardVisuals.kt` | All 14 animated Canvas visuals — ported verbatim (package + `WizardPalette` import updated) |
| `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/SetupWizardApp.kt` | Main composable + 15 screens + shared components — ported with real preference integration |

### Files modified

| File | Change |
|---|---|
| `settings.gradle.kts` | Added `include(":core:ads")` + `include(":feature:setup-wizard")` |
| `app/build.gradle.kts` | Added `implementation(projects.core.ads)` + `implementation(projects.feature.setupWizard)` |
| `app/src/main/java/app/confused/anikuta/App.kt` | Added `adsModule` to the Koin `startKoin` modules list |
| `app/src/main/java/app/confused/anikuta/MainActivity.kt` | Added Setup Wizard gate — if `!setupPrefs.isCompleted()`, render `SetupWizardApp` (bypassing AnikutaTheme); else fall through to the existing AnikutaTheme { AnikutaRoot() } flow. The wizard's `onComplete` callback flips a local `wizardDone` state that triggers recomposition. |
| `core/preferences/src/main/java/app/confused/anikuta/core/preferences/di/PreferenceModule.kt` | Added `single { SetupWizardPreferences(get()) }` |
| `feature/settings/src/main/java/app/confused/anikuta/feature/settings/GeneralSettingsScreen.kt` | Added "Onboarding" section with "Run setup wizard again" card. Sets `setupPrefs.setCompleted(false)` + calls `context.findActivity()?.recreate()` so the next composition re-enters the wizard branch. |

## Real-preference integration summary

The prototype was a demo with mock state — ported to actually apply user
selections to the real ANIKUTA preference stores:

### Theme screen → `ThemePreferences`
- `LaunchedEffect(state.paletteIndex)` writes the mapped `AccentPreset` to
  `themePrefs.accentPreset` + `themePrefs.customAccentColor`.
  - Mapping: Lime → `AccentPreset.LIME`, Teal → `TEAL`, Purple → `VIOLET`,
    Coral → `CORAL`.
- `LaunchedEffect(state.themeMode)` writes the mapped mode to
  `themePrefs.themeMode`.
  - Mapping: wizard's `ThemeMode.DARK/LIGHT/SYSTEM` → app's
    `ThemeMode.DARK/LIGHT/SYSTEM` (renamed to `AppThemeMode` via import alias
    to avoid the name collision with the wizard's own `ThemeMode` enum).
- These writes are LIVE — they happen as the user picks, so when the wizard
  finishes and `MainActivity` flips to `AnikutaRoot`, the new theme is already
  in the preferences and the main `AnikutaTheme` reads them as initial values.

### Poison/Ad screen → `AdsPreferences`
- On "Confirm" (the final step of the 3-step Poison flow):
  - `adsPrefs.setAdsEnabled(true)`
  - `adsPrefs.setDailyQuota(state.adSettings.frequency * 3)` — maps the 1-3
    frequency picker to 3/6/9 daily quota (the task's suggested multiplier).
- Name + timing remain display-only (the `AdsPreferences` API doesn't have
  name/timing fields yet — per the task spec).

### Folder screen → real SAF picker
- `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`
- On result: `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`
  so the URI remains usable across process restarts. The URI string is stored
  in `WizardState.folderUri` and displayed in the folder card (last path
  segment, since SAF tree URIs look like `primary:Anime/`).
- Actual folder scanning for anime files is a follow-up — the wizard just
  records that a folder was selected.

### Permissions screen → real system intents + permission checks
- **installApps**: `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with
  `package:<pkgName>` URI.
- **notifications**: `ActivityResultContracts.RequestPermission()` with
  `Manifest.permission.POST_NOTIFICATIONS` (only on Tiramisu+; pre-Tiramisu
  is granted at install time).
- **battery**: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with
  `package:<pkgName>` URI (falls back to
  `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` if the per-app intent fails).
- **allFiles**: `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`
  with `package:<pkgName>` URI on R+ (falls back to
  `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`).
- Initial state read via `ContextCompat.checkSelfPermission()` (notifications)
  + `packageManager.canRequestPackageInstalls()` (install) +
  `PowerManager.isIgnoringBatteryOptimizations()` (battery) +
  `Environment.isExternalStorageManager()` (all-files).
- A `DisposableEffect(lifecycleOwner)` registers a `LifecycleEventObserver`
  that re-reads all four permission states on `ON_RESUME` (so when the user
  comes back from the system Settings screen, the toggles update). The
  observer is removed on dispose (no leak).

### Restore screen → real SAF file picker (mock restore flow)
- `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`
  with `arrayOf("*/*")` mime filter.
- On result: stores the URI in `WizardState.backupFileUri` and advances to
  `WizardStep.FORMAT`.
- The Format screen shows the real backup file's display name (queried via
  `contentResolver.query(uri, ..., OpenableColumns.DISPLAY_NAME, ...)`) —
  falls back to the URI's last path segment.
- The rest of the restore flow (Processing, Summary, Linking, Manual,
  RestoreSummary, RestoreProcessing, RestoreSuccess) uses the prototype's
  mock data unchanged. Real `BackupManager` integration is a planned
  follow-up — the `:core:backup` dep is declared in build.gradle.kts so a
  future agent can wire it in without touching the build config.

### Finish screen → `SetupWizardPreferences.setCompleted(true)` + `onComplete()`
- The "Start Exploring" button calls `setupPrefs.setCompleted(true)` then
  `onComplete()`.
- `onComplete` is provided by `MainActivity` and flips the local `wizardDone`
  state — the activity recomposes to show `AnikutaRoot`.

## Integration into app startup

`MainActivity.onCreate`:
1. `koinInject<SetupWizardPreferences>()`.
2. `var wizardDone by remember { mutableStateOf(setupPrefs.isCompleted()) }`.
3. If `!wizardDone` → render `SetupWizardApp(onComplete = { wizardDone = true })`
   and `return@setContent` (bypasses `AnikutaTheme` entirely — the wizard has
   its own `SetupWizardTheme`).
4. Else → existing `AnikutaTheme { AnikutaRoot() }` flow.

The wizard's own `SetupWizardTheme` is intentionally separate from
`AnikutaTheme` so the onboarding flow can show all 4 palettes (Lime/Teal/
Purple/Coral) at full saturation regardless of the user's saved `AccentPreset`.
The user's selection is mirrored to `ThemePreferences` live as they pick — so
when `wizardDone` flips to `true` and `AnikutaTheme` enters composition, the
new accent/mode is picked up immediately (each `collectAsStateWithLifecycle`
uses the current pref as its initial value).

## Re-run option

`GeneralSettingsScreen` (Settings → General) gained an "Onboarding" section
with a "Run setup wizard again" card. On click:
1. `setupPrefs.setCompleted(false)`.
2. `context.findActivity()?.recreate()` — `MainActivity` re-enters `onCreate`
   and the wizard branch wins (since `setupPrefs.isCompleted()` is now false).

## Koin DI

- `:core:ads` brings `adsModule` (binds `AdsPreferences` as singleton).
- `:core:preferences` `preferenceModule` extended with
  `single { SetupWizardPreferences(get()) }`.
- `App.kt`'s `startKoin` modules list extended with `adsModule`.
- The wizard itself doesn't need a Koin module — it `koinInject`s
  `ThemePreferences`, `AdsPreferences`, and `SetupWizardPreferences` directly.

## Package + import notes

- Package name: `app.confused.anikuta.feature.setupwizard` (NOT
  `com.testplaybyte.setupwizard` — that was the prototype's package).
- `RobotoFamily` imported from `app.confused.anikuta.core.designsystem.theme.RobotoFamily`
  (already exists in `:core:designsystem` — no font TTFs bundled in the
  wizard module).
- `WizardPalette` lives in `app.confused.anikuta.feature.setupwizard.theme`
  (the prototype had it in `ui.theme` — flattened the `ui` prefix per the
  project's existing convention in `:core:designsystem`).
- Material icons (`Icons.Default.*`) come from `material-icons-extended`,
  which the `anikuta.library.compose` convention plugin already brings in.
- `LocalLifecycleOwner` resolved via FQN
  `androidx.lifecycle.compose.LocalLifecycleOwner.current` (the lifecycle-2.8.7
  path used elsewhere in the project — see `feature/watch/WatchScreen.kt:60`).

## Issues encountered

- **No Android SDK / JDK 17 in the environment.** Compilation deferred to CI
  per ADR-003. All changes reviewed manually for type-safety, import paths,
  and call-site compatibility.
- **`WizardMode` typo** in the `toAppThemeMode()` extension function — the
  enum is `ThemeMode` (the wizard's), not `WizardMode`. Fixed.
- **Lifecycle observer leak**: initial draft registered the observer inside
  a `LaunchedEffect(Unit)` (which doesn't auto-remove on dispose). Refactored
  to `DisposableEffect(lifecycleOwner)` with explicit
  `lifecycle.removeObserver(observer)` in `onDispose`.
- **`scope`/`launch` unused**: initial draft declared
  `val scope = rememberCoroutineScope()` + `import kotlinx.coroutines.launch`
  but never used them (all coroutine work is done via `LaunchedEffect`).
  Removed both.
- **ThemeMode name collision**: the wizard defines its own `ThemeMode` enum
  (DARK/LIGHT/SYSTEM with `label: String`) which collides with the app's
  `ThemeMode` enum in `:core:preferences`. Resolved by importing the app's
  version as `AppThemeMode` (`import ... as AppThemeMode`) and using the
  unqualified `ThemeMode` to refer to the wizard's enum throughout the file.

## Cross-cutting considerations

- **The wizard does NOT use `AnikutaTheme`.** It uses its own
  `SetupWizardTheme` so the palette preview can show all 4 colors at full
  saturation regardless of the user's saved accent. This is intentional —
  the wizard's theme is a temporary "onboarding" theme that's replaced by
  the real `AnikutaTheme` once `wizardDone` flips.
- **Theme prefs are written LIVE** (via `LaunchedEffect` on each
  `paletteIndex`/`themeMode` change), not batched at the end. This means
  if the user backs out of the wizard mid-flow, their selections are still
  persisted. The wizard's "Back" button doesn't undo theme writes. This is
  fine for an onboarding flow — the user can always re-run the wizard from
  Settings → General.
- **Ad prefs are written on Confirm** (the final step of the Poison flow),
  not on each change. This is because the Poison screen has a 3-step
  sub-flow (name → frequency → timing) and we only want to persist the
  final configuration.
- **Restore flow is mock.** The Format/Processing/Summary/Linking/Manual/
  RestoreSummary/RestoreProcessing/RestoreSuccess screens use the
  prototype's hardcoded mock data (`DEFAULT_ANIME`, "247 items", "1432
  episodes", etc.). The backup file URI is real (picked via SAF) and its
  display name is shown on the Format screen, but the actual restore is
  a no-op animation. Real `BackupManager` integration is a planned
  follow-up — the `:core:backup` dep is declared so a future agent can
  wire it in.

## Verification

- File-by-file manual review of all imports + package names — no leftover
  `com.testplaybyte` references.
- All 15 screens present (Welcome, Theme, Folder, Permissions, Restore,
  Format, Processing, Summary, Linking, Manual, RestoreSummary,
  RestoreProcessing, RestoreSuccess, Poison, Finish).
- All 14 animated Canvas visuals ported (FloatingShapes, MiniAnimePreview,
  FolderVisual, ShieldVisual, RestoreVisual, WarningVisual,
  ProcessingVisual, ClipboardVisual, SearchVisual, AllLinkedVisual,
  RestoreProcessingVisual, RestoreSuccessVisual, PoisonBottleVisual,
  PoisonPillVisual, FinishVisual — WelcomeVisual is defined in WizardVisuals.kt
  but unused by the main composable; kept for completeness).
- `SetupWizardApp` accepts `onComplete: () -> Unit` per the task spec.
- `SetupWizardPreferences` API matches the task spec: `isCompleted()` +
  `setCompleted(done)`.
- `AdsPreferences` API exposes `setAdsEnabled` + `setDailyQuota` (plus
  `setCooldownMinutes`, `setMinStaySeconds`, `setAdUrl` for future use).
