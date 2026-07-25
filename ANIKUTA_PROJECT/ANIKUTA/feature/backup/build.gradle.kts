// :feature:backup
//
// The Backup & Restore UI for ANIKUTA.
//
// Provides BackupSettingsScreen — a full-page settings screen with four sections:
//   1. Backup    — create a manual backup (select categories + format)
//   2. Restore   — restore from a file (format auto-detected, summary + confirm)
//   3. Auto-backup — enable/configure periodic background backups (WorkManager)
//   4. Storage   — select the SAF folder, view storage usage
//
// Depends on :core:backup for the engine (BackupManager, BackupStorage, etc.).
// Follows the ANIKUTA design language: #B1F256 primary, RobotoFamily font,
// surfaceVariant cards (alpha 0.4f), ModalBottomSheet dragHandle=null.
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.backup"
}

dependencies {
    // Core modules
    implementation(projects.core.backup)
    implementation(projects.core.anilist)     // AniListApi for Aniyomi translation
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.common)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // activity-compose (BackHandler)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Koin (for koinInject / koinViewModel)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
