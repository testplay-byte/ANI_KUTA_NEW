plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.videoresolver"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    // Source API — for AnimeSource, SEpisode, Video, Hoster (ADR-029)
    implementation(projects.core.sourceApi)
    // ── Phase 8 (Doc 04 violations 3+4): the resolver logic + types ──
    // The logic (ResolverService, VideoTitleParser, the strategy objects) +
    // the types (ResolverServer, ResolverVideo, SubtitleTrack, ResolverResult,
    // VideoResolverState) were moved to :core:video-resolver. This module
    // keeps only the UI (VideoResolverSheet, ResolverServerContent,
    // ResolverStates) and imports the types from core.
    implementation(projects.core.videoResolver)

    // Activity Compose (for BackHandler if needed)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
