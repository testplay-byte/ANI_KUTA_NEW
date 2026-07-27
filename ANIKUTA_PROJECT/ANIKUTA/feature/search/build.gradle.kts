plugins {
    id("anikuta.library.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.feature.search"
}

dependencies {
    // Core modules — AniList data, design system, preferences, source-api contract
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.anilist)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)

    // Data layer — extension manager + source matcher + the new link store
    implementation(projects.data.extension)

    // Coil 3 — cover image loading
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // kotlinx-serialization — for RecentSearchesStore JSON encoding
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Lifecycle + ViewModel (Compose integration)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
