plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.my"
}

dependencies {
    // Core modules
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.player)        // WatchProgressStore
    implementation(projects.core.tracker)       // StatsCalculator + TrackerManager

    // Data modules (for repository impl visibility at runtime)
    implementation(projects.data.anime)

    // Coil for image loading
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Koin (for koinInject / koinViewModel)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
