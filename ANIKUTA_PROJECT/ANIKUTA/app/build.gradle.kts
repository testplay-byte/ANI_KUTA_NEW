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

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
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
}
