plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.common"
}

dependencies {
    // Coroutines (for Flow in repository interfaces)
    implementation(kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.bundles.test)
}
