plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.player"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.player)
    implementation(projects.core.sourceApi)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
