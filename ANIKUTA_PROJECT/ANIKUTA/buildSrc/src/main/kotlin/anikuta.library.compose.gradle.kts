// Convention plugin: adds Jetpack Compose to a library module
// Plugin ID: anikuta.library.compose

plugins {
    id("anikuta.library")
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
    debugImplementation(compose.ui.tooling)
}
