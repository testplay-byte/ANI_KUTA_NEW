plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.updatechecker"
}

dependencies {
    // Core modules — interfaces + source-api + AniList + preferences.
    // NOTE: this module deliberately does NOT depend on :data:extension.
    // Per ARCHITECTURE.md §3, ":core → :data" is forbidden. Source/extension
    // access is abstracted behind [EpisodeFetchGateway] (defined here, implemented
    // in :data:extension, injected via Koin). This keeps :core:update-checker
    // testable and lets a future WorkManager worker consume it without pulling
    // the whole extension stack.
    implementation(projects.core.common)
    implementation(projects.core.sourceApi)
    implementation(projects.core.anilist)
    implementation(projects.core.preferences)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
