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
    implementation(projects.core.providerApi)
    implementation(projects.core.player)
    // ── Agent 1: History + Updates ──
    implementation(projects.core.updateChecker)
    // source-api — for ExtensionAppHolder.init() in App.kt (ADR-029)
    implementation(projects.core.sourceApi)
    // ── Phase 8 (Doc 04 violations 3+4): video-resolver logic + types ──
    // :app's DownloadOrchestrator + AppController + AnikutaRoot consume the
    // resolver service + types. They now import from :core:video-resolver
    // (the UI sheet stays in :feature:video-resolver, which the app still
    // depends on below for the sheet composable).
    implementation(projects.core.videoResolver)

    // Data modules (for Koin wiring)
    implementation(projects.data.anime)
    implementation(projects.data.extension)
    implementation(projects.data.history)

    // Feature modules
    implementation(projects.feature.browse)
    implementation(projects.feature.search)
    implementation(projects.feature.animeDetails)
    implementation(projects.feature.library)
    implementation(projects.feature.extensionsSettings)
    implementation(projects.feature.videoResolver)
    implementation(projects.feature.watch)
    // Episode settings screens (Hub / Display / Layout / Metadata) — full pages
    implementation(projects.feature.episodeSettings)
    // Appearance / UI customization (theme mode, accent colors, custom palette)
    implementation(projects.feature.settings)
    // ── Agent 1: History + Updates ──
    implementation(projects.feature.history)
    implementation(projects.feature.updates)
    // ── Agent 2: Profile + Trackers ──
    implementation(projects.feature.my)
    implementation(projects.feature.trackers)
    implementation(projects.core.tracker)
    // ── Agent 2: Downloads & Offline Playback ──
    implementation(projects.core.download)
    implementation(projects.feature.download)

    // Core modules for episode metadata
    implementation(projects.core.episodeMetadata)

    // ── Agent 1: Backup & Restore ──
    implementation(projects.core.backup)
    implementation(projects.feature.backup)

    // ── Advertising system ──
    implementation(projects.core.ads)
    // ── App self-update system ──
    implementation(projects.core.appUpdate)

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
    // lifecycle-runtime-compose — for collectAsStateWithLifecycle (download-state observation)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Logging (ADR-033)
    implementation(libs.logcat)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
