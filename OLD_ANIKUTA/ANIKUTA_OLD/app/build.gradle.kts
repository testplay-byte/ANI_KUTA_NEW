plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.anikuta"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.anikuta"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MPV native lib — only build arm64-v8a
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Fixed debug signing key — allows installing new builds without uninstalling.
    // The keystore is committed to the repo (safe — it's a debug key, not release).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Phase 6.4: Release build with debugging enabled for performance testing.
        // Has R8 optimization (no minification yet) but allows debugging.
        // This lets us test real performance without the debug-build overhead.
        create("release-debuggable") {
            isMinifyEnabled = false
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Ensure FFmpeg + MPV native libraries are packaged into the APK and not
    // stripped by AGP. aniyomi uses the same keepDebugSymbols list.
    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libavcodec",
                "libavdevice",
                "libavfilter",
                "libavformat",
                "libavutil",
                "libc++_shared",
                "libffmpegkit_abidetect",
                "libffmpegkit",
                "libmpv",
                "libplayer",
                "libpostproc",
                "libswresample",
                "libswscale",
                "libxml2",
            )
        }
    }
}

dependencies {
    // Internal modules
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":source-api"))

    // Serialization (JSON for our backup format + Protobuf for aniyomi compat)
    implementation(libs.serialization.json)
    implementation(libs.serialization.protobuf)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Coil (image loading)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Media session dependency removed — PlayerMediaSession was dead code (D.2)

    implementation(libs.mpv.lib)
    // FFmpeg native libraries required by libmpv.so. libmpv.so is dynamically
    // linked against libavcodec/libavformat/libavutil/etc.; without these the
    // MPVLib static initializer crashes with UnsatisfiedLinkError. aniyomi
    // declares the same dependency (gradle/aniyomi.versions.toml: ffmpeg-kit).
    implementation(libs.ffmpeg.kit)
    implementation(libs.arthenica.smartexceptions)

    // WorkManager (download manager)
    implementation(libs.work.runtime)

    // UniFile (SAF / DocumentFile wrapper for folder selection)
    implementation(libs.unifile)

    // androidx.preference (extension settings UI — PreferenceFragmentCompat)
    implementation(libs.preference.ktx)

    // Reorderable LazyColumn (Phase 7 — drag-and-drop priority lists)
    implementation(libs.reorderable)

    // Testing
    testImplementation(libs.junit)
}
// build trigger
// revert trigger Tue Jul  7 20:36:42 UTC 2026
