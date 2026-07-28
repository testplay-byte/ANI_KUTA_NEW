plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "app.anikuta.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        // Enable Kotlin 2.2 context parameters (used by ReleaseServiceImpl)
        freeCompilerArgs += "-Xcontext-parameters"
    }
}

sqldelight {
    databases {
        // Anime database only (manga DB excluded per D2 — anime-only)
        create("AnimeDatabase") {
            packageName.set("app.anikuta.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            srcDirs.from(project.file("./src/main/sqldelight"))
        }
    }
}

dependencies {
    // Internal
    implementation(project(":source-api"))
    implementation(project(":domain"))
    implementation(project(":core"))

    // SQLDelight
    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines)
    api(libs.sqldelight.android.paging)

    // DI
    api(libs.injekt)

    // Paging
    implementation(libs.paging.runtime)

    // Testing
    testImplementation(libs.junit)
}
