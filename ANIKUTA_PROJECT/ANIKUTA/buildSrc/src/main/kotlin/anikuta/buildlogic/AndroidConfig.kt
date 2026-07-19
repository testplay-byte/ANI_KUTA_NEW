package anikuta.buildlogic

import org.gradle.api.JavaVersion as GradleJavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget as KotlinJvmTarget

object AndroidConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    const val NDK = "27.1.12297006"
    const val BUILD_TOOLS = "35.0.1"

    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17

    const val APPLICATION_ID = "app.confused.anikuta"
    const val VERSION_CODE = 1
    const val VERSION_NAME = "0.1.0"
}
