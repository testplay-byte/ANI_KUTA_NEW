plugins {
    id("anikuta.android.application.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // ABI splits: arm64-v8a only (ADR-032)
    // Disabled for Phase 1 (no native libs yet); enable when MPV is added
    splits {
        abi {
            isEnable = false
        }
    }

    // Stable debug signing — so CI builds can update over previous versions
    // without uninstalling (the owner's request). This is a debug-only keystore
    // committed to the repo; NOT for release.
    signingConfigs {
        getByName("debug") {
            storeFile = file("anikuta-debug.keystore")
            storePassword = "android"
            keyAlias = "anikuta-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // Core modules
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.anilist)

    // Data modules (for Koin wiring)
    implementation(projects.data.anime)
    implementation(projects.data.history)

    // Feature modules
    implementation(projects.feature.browse)
    implementation(projects.feature.anime.details)
    implementation(projects.feature.extensions.settings)

    // Koin (ADR-023)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)

    // Voyager navigation
    implementation(libs.bundles.voyager)

    // Coroutines
    implementation(kotlinx.coroutines.android)

    // Lifecycle
    implementation(androidx.lifecycle.runtimektx)
    implementation(androidx.lifecycle.viewmodel.compose)

    // Logging (ADR-033)
    implementation(libs.logcat)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
