plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.data.extension"
}

dependencies {
    // Core modules — interfaces + the Aniyomi-compatible source-api contract (ADR-029)
    implementation(projects.core.common)
    implementation(projects.core.sourceApi)
    // Preferences — for ExtensionLinkStore (caches extension→AniList links)
    implementation(projects.core.preferences)

    // AndroidX core — NotificationCompat + ContextCompat (foreground service, broadcast receivers)
    implementation("androidx.core:core-ktx:1.15.0")

    // OkHttp — for downloading the repo index + extension APKs
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // Serialization — for parsing the repo index JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    // RxJava 1.x — source-api compat (the deprecated fetch* API extensions still call)
    implementation("io.reactivex:rxjava:1.3.8")
}
