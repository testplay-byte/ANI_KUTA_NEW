// :core:episode-metadata
// Pure data module: metadata sources, repository, preferences, model.
// NO Compose UI in this module — the MetadataSettingsScreen lives in
// :feature:episode-settings (which depends on this module for the prefs).
plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.episodemetadata"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)

    // OkHttp for HTTP (metadata sources)
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // kotlinx-serialization (metadata source JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Coroutines (version provided by the kotlinx-coroutines-bom from anikuta.library)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Koin for DI
    implementation("io.insert-koin:koin-android:4.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
