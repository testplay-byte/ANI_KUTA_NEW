// Convention plugin: Android library module base config
// Plugin ID: anikuta.library

import anikuta.buildlogic.AndroidConfig

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = AndroidConfig.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.JavaVersion
        targetCompatibility = AndroidConfig.JavaVersion
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(AndroidConfig.JvmTarget)
    }
}

// Use JUnit 5 (Jupiter) for unit tests
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
