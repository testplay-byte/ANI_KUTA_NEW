// :feature:setup-wizard
// The 15-screen animated onboarding flow shown on first launch (gated by
// SetupWizardPreferences.isCompleted) and re-runnable from Settings → General.
//
// Ported from the design prototype at /tmp/setupwizard (package
// com.testplaybyte.setupwizard) into the real ANIKUTA app. Real preference
// integration:
//   - Theme screen → ThemePreferences (themeMode + accentPreset)
//   - Poison/Ad screen → AdsPreferences (adsEnabled + dailyQuota)
//   - Folder screen → real OpenDocumentTree picker (URI recorded in state)
//   - Permissions screen → real intent launches (install / notifications /
//     battery / all-files)
//   - Restore screen → real OpenDocument picker (file URI recorded; the rest
//     of the restore flow uses mock data — real BackupManager integration is
//     a planned follow-up)
//   - Finish screen → SetupWizardPreferences.setCompleted(true) + onComplete
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.setupwizard"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.ads)
    // :core:backup — declared per the task spec. The wizard currently uses mock
    // data for the restore flow (Format/Processing/Summary/Linking/Manual/
    // RestoreSummary/RestoreProcessing/RestoreSuccess); real BackupManager
    // integration is a planned follow-up. Declaring the dep now means a future
    // agent can wire it in without touching build.gradle.kts.
    implementation(projects.core.backup)

    // Koin (for koinInject of ThemePreferences + AdsPreferences +
    // SetupWizardPreferences)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Activity Compose (for rememberLauncherForActivityResult — folder picker,
    // backup file picker, permission requests)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coroutines (for LaunchedEffect delays in processing/restore screens)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
