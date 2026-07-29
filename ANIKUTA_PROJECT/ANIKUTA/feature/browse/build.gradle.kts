plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.browse"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.anilist)
    // Phase 7: BrowseScreen resolves the active HomeFeedProvider through the
    // MetadataProviderRegistry instead of calling AniListApi directly. Adding
    // MAL/TMDB later = one module + one Koin line (ADR-041).
    implementation(projects.core.providerApi)
    // Scroll-blur overlay toggle: BrowseScreen reads ThemePreferences.headerBlurEffect
    // to enable/disable the frosted-glass overlay under the collapsing header.
    implementation(projects.core.preferences)

    // Koin — BrowseScreen uses GlobalContext.get().get<ThemePreferences>()
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Coil for image loading
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
