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

    // Koin
    implementation(libs.koin.androidx.compose)

    // Lifecycle
    implementation(androidx.lifecycle.runtimektx)
    implementation(androidx.lifecycle.viewmodel.compose)
}
