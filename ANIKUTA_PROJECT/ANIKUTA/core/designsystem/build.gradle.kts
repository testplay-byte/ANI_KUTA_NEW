plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.core.designsystem"
}

dependencies {
    // Core modules
    implementation(projects.core.common)

    // Coroutines
    implementation(kotlinx.coroutines.core)
}
