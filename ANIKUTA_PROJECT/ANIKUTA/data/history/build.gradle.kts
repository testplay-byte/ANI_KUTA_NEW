plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.data.history"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)

    implementation(libs.sqldelight.coroutines)
    implementation(kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
