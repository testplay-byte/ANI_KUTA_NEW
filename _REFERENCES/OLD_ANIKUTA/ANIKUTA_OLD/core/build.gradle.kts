plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.anikuta.core"
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
        // Enable Kotlin 2.2 context parameters (used by aniyomi's OkHttpExtensions)
        freeCompilerArgs += "-Xcontext-parameters"
    }
}

dependencies {
    // Network (from aniyomi core:common)
    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)
    api(libs.rxjava)
    api(libs.logcat)

    // Kotlin
    api(libs.coroutines.core)
    api(libs.coroutines.android)
    api(libs.serialization.json)
    api(libs.serialization.json.okio)

    // Preferences
    api(libs.preference.ktx)

    // HTML parsing
    implementation(libs.jsoup)

    // Testing
    testImplementation(libs.junit)
}
