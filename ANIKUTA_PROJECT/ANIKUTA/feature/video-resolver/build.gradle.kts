plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.videoresolver"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)

    // Activity Compose (for BackHandler if needed)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
