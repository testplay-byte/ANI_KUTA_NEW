plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.providerapi"
}

dependencies {
    // The provider-api contracts reference UnifiedAnime + LocalId/ContentId, which
    // live in :core:common. This is a one-directional leaf dependency (no cycle).
    implementation(project(":core:common"))

    // Coroutines for suspend functions + Flow
    implementation(kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
