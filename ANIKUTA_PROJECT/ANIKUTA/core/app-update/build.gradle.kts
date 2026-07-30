plugins {
    id("anikuta.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.appupdate"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
