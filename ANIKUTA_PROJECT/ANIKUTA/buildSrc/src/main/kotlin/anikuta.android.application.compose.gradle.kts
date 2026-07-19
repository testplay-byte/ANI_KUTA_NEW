// Convention plugin: adds Jetpack Compose to an application module
// Plugin ID: anikuta.android.application.compose

plugins {
    id("anikuta.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(compose.bom))
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.runtime)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)
    debugImplementation(compose.ui.tooling)

    implementation(androidx.activity)
}
