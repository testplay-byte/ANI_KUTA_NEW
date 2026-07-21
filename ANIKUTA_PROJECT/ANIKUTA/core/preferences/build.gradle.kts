plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.preferences"
}

dependencies {
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
