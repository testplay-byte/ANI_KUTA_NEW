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

    // Palette — for cover-color dynamic theming (watch-page.md §7)
    implementation("androidx.palette:palette-ktx:1.0.0")
}
