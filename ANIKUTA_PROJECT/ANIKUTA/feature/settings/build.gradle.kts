plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.settings"
}

dependencies {
    // Core modules
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.player)

    // Koin
    implementation(libs.koin.androidx.compose)

    // Lifecycle
    implementation(androidx.lifecycle.runtimektx)
    implementation(androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
