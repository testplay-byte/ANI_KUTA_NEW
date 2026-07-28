// buildSrc build script — makes convention plugins available to the main build
import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
}

group = "anikuta.buildlogic"

// Pin the Kotlin version for buildSrc to avoid auto-provisioning mismatches
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(kotlinx.compose.compiler.gradle)
    implementation(libs.spotless.gradle)
}
