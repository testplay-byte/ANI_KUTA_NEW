plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.videoresolver"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    // Source API — for AnimeSource, SEpisode, Video, Hoster (ADR-029)
    implementation(projects.core.sourceApi)

    // Activity Compose (for BackHandler if needed)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
