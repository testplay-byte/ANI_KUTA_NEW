plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.tracker"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"5338\"")
        buildConfigField("String", "MAL_CLIENT_ID", "\"686b980ff4240fccce7f6a654cea07ce\"")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.core.anilist)
    implementation(projects.core.player)        // WatchProgressStore for stats + sync

    // OkHttp for HTTP
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // kotlinx-serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // SQLDelight coroutines extensions (for Flow queries)
    implementation(libs.sqldelight.coroutines)
}
