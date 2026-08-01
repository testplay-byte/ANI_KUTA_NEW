# POST-INSTALL-POPUP-DOTS-PROGRESS — Implementation Record

**Agent:** Z.ai Code (main coordinator + implementer)
**Task ID:** POST-INSTALL-POPUP-DOTS-PROGRESS
**Project root:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**Date:** 2025

## Summary

Implemented four features requested by the user:

1. **Post-install success popup** with APK-deletion animation
2. **Red notification dots** on More → Settings row + Settings → About & Updates
   row + AboutScreen → Check for updates icon (when update is available or
   download is in progress)
3. **Download progress bar** in AboutScreen (visible while a download is in
   flight — no need to expand the update sheet)
4. **Custom-color popup** in the Setup Wizard ThemeScreen (tapping CUSTOM shows
   an "OK" popup instead of changing the theme)
5. **FolderVisual redesign** — replaced the bottle-shaped folder drawing with
   a modern file-manager folder icon (rounded-rectangle body + tab at
   top-left + accent stripe across the top).

The commit message also references "ad settings simplified + ad URL change +
icon" — those were already in the working tree before this session started
(pre-existing modifications to `AdsPreferences.kt`, `AdSettingsSection.kt`,
`ic_launcher_foreground.xml`, `ic_launcher_background.xml`). They are
included in the commit unchanged.

## Files modified (8)

### 1. `core/app-update/src/main/java/app/confused/anikuta/core/appupdate/AppUpdatePreferences.kt`
- Added new preference `pendingPostInstallPref` (String, default "")
  backed by `KEY_PENDING_POST_INSTALL = "pref_app_update_pending_post_install"`.
- Added three public methods:
  - `setPendingPostInstall(version: String)` — records the version the user
    is about to install (called before launching the system installer).
  - `getPendingPostInstall(): String` — returns the recorded version, or
    empty string.
  - `clearPendingPostInstall()` — clears the marker (called after the
    popup is shown so it doesn't re-show on the next launch).

### 2. `core/app-update/src/main/java/app/confused/anikuta/core/appupdate/AppUpdateManager.kt`
- Modified `installDownloadedApk(apkPath: String? = null)`:
  - Before launching the installer, looks up the version name from the
    downloaded-APK record (by `filePath`) — falls back to the latest
    update's `versionName` if no record exists.
  - Calls `preferences.setPendingPostInstall(versionName)` so the next
    startup knows to show the post-install popup.

### 3. `app/src/main/java/app/confused/anikuta/navigation/AppController.kt`
- Added `var showPostInstallSuccess by mutableStateOf(false)` (private set)
  + `fun showPostInstallPopup()` + `fun dismissPostInstallPopup()`.
- Placed right after `showUpdateDialog` per spec.

### 4. `app/src/main/java/app/confused/anikuta/navigation/AnikutaRoot.kt`
- Inside `LaunchedEffect(Unit)` (startup update check): if
  `updatePrefs.getPendingPostInstall()` is non-empty, clears it + calls
  `appController.showPostInstallPopup()` + skips the rest of the update
  check (no need to check for updates right after installing one).
- Added overlay #7: `if (appController.showPostInstallSuccess) {
  PostInstallSuccessSheet(appController) }`.

### 5. `app/src/main/java/app/confused/anikuta/navigation/PostInstallSuccessSheet.kt` (NEW)
- A `ModalBottomSheet` with `dragHandle = null`.
- Title: "Update Installed Successfully" (bold, primary-colored, centered).
- A `LaunchedEffect` runs the animation:
  - 0–1500ms: `CircularProgressIndicator` + "Cleaning up downloaded APK…"
  - At ~1500ms: calls `appController.updateManager.cleanupOldDownloads()`
    to delete the just-installed APK file.
  - 1500–2000ms: `CheckCircle` icon + "APK deleted" (with `scaleIn`
    animation).
  - At ~2000ms: calls `appController.dismissPostInstallPopup()`.
- Uses `AnimatedVisibility` to cross-fade between the two phases.

### 6. `app/src/main/java/app/confused/anikuta/navigation/MoreScreens.kt`
- Added `showDot: Boolean = false` parameter to `MoreRow`.
- When `showDot` is true, overlays an 8dp red (`Color(0xFFFF5252)`) circle
  at the `TopEnd` corner of the icon (using a `Box` wrapper around the
  icon + dot).
- In `MoreScreen`, observes `AppUpdateManager.latestUpdate` +
  `downloadProgress` (via `koinInject`) and passes
  `showDot = (latestUpdate != null || download in progress)` to the
  Settings row.
- Same change applied to `SettingsScreen`'s "About & Updates" row.

### 7. `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AboutScreen.kt`
- Added imports: `background`, `Box`, `size`, `CircleShape`,
  `LinearProgressIndicator`, `Color`.
- Observes `updateManager.downloadProgress` + `latestUpdate`.
- "Check for updates" icon now wrapped in a `Box` with an optional red
  dot (same `Color(0xFFFF5252)` 8dp circle).
- Added a conditional `LinearProgressIndicator` item below the manual
  check button — visible only while `downloadProgress != null &&
  !isComplete && error == null`. Shows "Downloading update… X%" row +
  the progress bar.

### 8. `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/SetupWizardApp.kt`
- In `ThemeScreen`, added `var showCustomPopup by remember { mutableStateOf(false) }`.
- Changed the CUSTOM card's `clickable` from `themePrefs.selectCustom()`
  to `showCustomPopup = true` (does NOT change the theme).
- Added an `AlertDialog` at the end of `ThemeScreen`:
  - Title: "Custom Theme"
  - Body: "You can configure a custom accent color in Settings → Appearance → General after completing the setup wizard."
  - Buttons: "OK" (dismiss).

### 9. `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/components/WizardVisuals.kt`
- Completely replaced the `FolderVisual` composable's Canvas drawing.
- New folder shape:
  - Body: large rounded rectangle (130×96 u), filled with
    `palette.surface4` (vertical gradient to `surface5`).
  - Tab: smaller rounded rectangle (48×18 u) at top-left, overlapping
    the body (drawn first so the body covers the seam).
  - Accent stripe: a horizontal gradient (`palette.primary` → 75% alpha)
    across the top of the body — uses `drawRoundRect` with the body's
    corner radius so it sits flush inside the rounded top.
  - Inner content lines: 3 thin horizontal lines (suggest files inside).
- Kept the existing animations: floating, card drop, scan beam.
- Kept the `selected = true` check badge at the top-right corner.
- Anime cards now fade out as they enter the folder body (last 25% of
  the drop) so it looks like they "land" inside rather than covering it.

## Verification

- Could not build locally (no JDK 17 in sandbox; CI runs on Ubuntu w/ JDK 17).
- Carefully reviewed all edits for syntax + import correctness.
- All new imports use already-existing dependencies (`core:app-update` is
  already a dep of `:app` and `:feature:settings`; `material3` is already
  a dep of `:feature:setup-wizard`).

## Commit

Commit message (truncated in the file for brevity — see the actual commit
for the full message):

```
feat: post-install popup + red dots + progress bar + ad URL change + icon + folder redesign

1. Post-install success popup:
   - After installing an update + reopening the app, a bottom-up sheet appears
   - Shows 'Update Installed Successfully' + cleaning up animation
   - Deletes the APK file + shows 'APK deleted' confirmation
   - Auto-dismisses after ~2 seconds
...
```
