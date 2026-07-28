plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.data.extension"
}

dependencies {
    // Core modules — interfaces + the Aniyomi-compatible source-api contract (ADR-029)
    implementation(projects.core.common)
    implementation(projects.core.sourceApi)
    // Preferences — for ExtensionLinkStore (caches extension→AniList links)
    implementation(projects.core.preferences)
    // update-checker — implements EpisodeFetchGateway here (the :core→:data boundary
    // is inverted via the interface defined in :core:update-checker; see EpisodeFetchGatewayImpl).
    implementation(projects.core.updateChecker)
    // AniList API + episode-metadata — for ExtensionDetailsProvider (doc 05 §5 Step 3):
    // the extension provider merges AniList metadata for linked anime + fetches
    // episode metadata when an anilistId/malId is available.
    implementation(projects.core.anilist)
    implementation(projects.core.episodeMetadata)
    // Design system — for PaletteExtraction.extractFromBitmap (Phase 9 cover-color
    // extraction for extension-sourced covers). :core:designsystem has no Coil dep,
    // so the provider loads the bitmap via OkHttp (already a dep) + passes it here.
    implementation(projects.core.designsystem)

    // AndroidX core — NotificationCompat + ContextCompat (foreground service, broadcast receivers)
    implementation("androidx.core:core-ktx:1.15.0")

    // OkHttp — for downloading the repo index + extension APKs
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // Serialization — for parsing the repo index JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    // RxJava 1.x — source-api compat (the deprecated fetch* API extensions still call)
    implementation("io.reactivex:rxjava:1.3.8")
}
