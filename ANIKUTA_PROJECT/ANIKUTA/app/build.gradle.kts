plugins {
    id("anikuta.android.application.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // ABI splits: arm64-v8a only (ADR-032)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
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
    implementation(projects.core.preferences)
    implementation(projects.core.player)
    // source-api — for ExtensionAppHolder.init() in App.kt (ADR-029)
    implementation(projects.core.sourceApi)

    // Data modules (for Koin wiring)
    implementation(projects.data.anime)
    implementation(projects.data.extension)
    implementation(projects.data.history)

    // Feature modules
    implementation(projects.feature.browse)
    implementation(projects.feature.search)
    implementation(projects.feature.animeDetails)
    implementation(projects.feature.extensionsSettings)
    implementation(projects.feature.videoResolver)
    implementation(projects.feature.watch)
    implementation(projects.feature.player)

    // Core modules for episode metadata
    implementation(projects.core.episodeMetadata)

    // OkHttp + serialization (used by ExtensionModule for extension API HTTP client)
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Injekt (required by Keiyoushi-family extensions — ADR-029 extension compat)
    // Extensions expect uy.kohesive.injekt to be on the host classpath.
    implementation("com.github.mihonapp:injekt:91edab2317")

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
