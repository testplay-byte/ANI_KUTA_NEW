// :feature:episode-settings
// Hosts the 4 episode-settings screens (Hub, Display, Layout, Metadata) + shared
// components (SettingsSubpageScaffold, SwitchSettingsRow, SegmentedRow, EpisodeRowPreview).
// Uses the Compose-enabled library convention plugin.
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.episodesettings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.episodeMetadata)
    // ── Phase 8 (Doc 04 violation 2): :feature:anime-details dep REMOVED ──
    // EpisodeDisplayPreferences + EpisodeDisplayPrefs were moved to
    // :core:preferences (where all other preferences live). This module
    // imports them from there — no feature→feature dep needed.

    // Koin (for koinInject of EpisodeDisplayPreferences + EpisodeMetadataPreferences)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Activity Compose (for BackHandler + system bars padding)
    implementation("androidx.activity:activity-compose:1.10.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Coroutines (for Preference.changes collection; version via BOM from anikuta.library)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
