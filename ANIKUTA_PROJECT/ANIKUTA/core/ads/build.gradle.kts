plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.ads"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
