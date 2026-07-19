plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.anikuta.source.api"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Internal
    api(project(":core"))

    // DI (Injekt — Mihon fork from JitPack)
    api(libs.injekt)

    // Compose runtime (for @Stable annotation in AnimeFilterList)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)

    // HTML parsing
    api(libs.jsoup)

    // Testing
    testImplementation(libs.junit)
}
