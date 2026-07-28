plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.anikuta.domain"
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
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        // Enable Kotlin 2.2 context parameters (used by ExtensionRepoService)
        freeCompilerArgs += "-Xcontext-parameters"
    }
}

dependencies {
    // Internal
    implementation(project(":source-api"))
    implementation(project(":core"))

    // Kotlin
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.serialization.json.okio)

    // Paging (for AnimeSourceRepository)
    implementation(libs.paging.runtime)

    // DI
    api(libs.injekt)

    // Compose (for @Immutable/@Stable annotations in domain models)
    compileOnly(platform(libs.compose.bom))
    compileOnly(libs.compose.ui)

    // Testing
    testImplementation(libs.junit)
}
