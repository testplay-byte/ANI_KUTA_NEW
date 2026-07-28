plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.data.anime"
}

dependencies {
    // Core modules (interfaces + database)
    implementation(projects.core.common)
    implementation(projects.core.database)

    // SQLDelight coroutines extensions (for Flow)
    implementation(libs.sqldelight.coroutines)
    implementation(kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
