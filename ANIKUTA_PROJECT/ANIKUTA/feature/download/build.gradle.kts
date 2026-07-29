plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.download"
}

// ── :feature:download — the Downloads UI ──
//
// Compose screen + ViewModel for the Downloads page (queue + downloaded
// library). Depends on :core:download (the engine) + :core:designsystem
// (CollapsingHeader, MoreListRow, RobotoFamily) + :core:preferences (for the
// preferences sheet's reactive reads). Does NOT depend on :feature:video-resolver
// — enqueue orchestration lives in :app's DownloadOrchestrator. This screen
// only observes DownloadManager flows + issues pause/cancel/delete commands.

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.download)
    implementation(projects.core.sourceApi)
    // ── Phase 8 (Doc 04 violation 4): :feature:video-resolver dep replaced ──
    // :feature:download imports only the resolver types (ResolverServer +
    // ResolverVideo) for the DownloadVideoPickerSheet — no UI from
    // :feature:video-resolver. It now depends on :core:video-resolver where
    // those types live. The feature→feature dep is gone.
    implementation(projects.core.videoResolver)

    // Coil for cover thumbnails
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // activity-compose (BackHandler)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
