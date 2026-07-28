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

    // AniList API + episode-metadata — for AniListDetailsProvider (doc 05 §5 Step 3)
    implementation(projects.core.anilist)
    implementation(projects.core.episodeMetadata)
    // Source API (AnimeSource/SEpisode) + extension manager (SourceMatcher,
    // SourceLinkStore, ExtensionLinkStore, AnimeExtensionManager) — for the
    // AniList provider's stage-2 source-match + stage-3 episode fetch.
    implementation(projects.core.sourceApi)
    implementation(projects.data.extension)

    // SQLDelight coroutines extensions (for Flow)
    implementation(libs.sqldelight.coroutines)
    implementation(kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
