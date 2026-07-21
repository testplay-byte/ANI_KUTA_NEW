plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.watch"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)
    implementation(projects.core.player)
    implementation(projects.core.episodeMetadata)
    implementation(projects.data.anime)
    implementation(projects.data.history)
    implementation(projects.feature.videoResolver)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
