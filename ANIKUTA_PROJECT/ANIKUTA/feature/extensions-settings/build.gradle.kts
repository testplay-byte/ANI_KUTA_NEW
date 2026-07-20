plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.extensionssettings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.sourceApi)

    // Coil for image loading (squircle extension icons — added now, used once
    // real extension rows land in a later phase).
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Lifecycle (ViewModel + runtime — wired when the screen gets a ViewModel)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
