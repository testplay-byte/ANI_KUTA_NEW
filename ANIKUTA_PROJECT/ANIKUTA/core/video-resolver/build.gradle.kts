// :core:video-resolver
//
// Phase 8 — module-architecture fix (Doc 04 violations 3+4).
//
// The video-resolver logic types + service were previously in
// :feature:video-resolver. Both :feature:watch and :feature:download imported
// them from there, creating two feature→feature dependencies. They now live in
// this core module — :feature:video-resolver (UI: VideoResolverSheet,
// ResolverServerContent, ResolverStates) + :feature:watch + :feature:download
// all depend on :core:video-resolver for the logic + types.
//
// The types in this module are pure Kotlin (no Compose UI) — the @Serializable
// Video model from :core:source-api uses kotlinx-serialization, which is
// already on the classpath via the source-api dep. No Compose plugin needed.
plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.videoresolver"
}

dependencies {
    // Source API — for AnimeSource, SEpisode, Video, Hoster (ADR-029). The
    // resolver calls source.getHosterList/getVideoList + reads Video fields.
    implementation(projects.core.sourceApi)

    // Coroutines (withContext/withTimeoutOrNull in ResolverService)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
