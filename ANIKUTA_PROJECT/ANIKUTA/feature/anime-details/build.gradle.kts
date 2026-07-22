plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.animedetails"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.anilist)
    // Source API — for AnimeSource, SEpisode, AnimeFilterList (ADR-029)
    implementation(projects.core.sourceApi)
    // Extension manager — for AnimeExtensionManager + SourceMatcher (Step 5)
    implementation(projects.data.extension)

    // Koin (for koinInject of AnimeRepository + CategoryRepository)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Activity Compose (for BackHandler)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Coil for images
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle (ViewModel + viewModelScope)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
