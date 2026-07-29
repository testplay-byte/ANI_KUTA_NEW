// :core:backup
//
// The backup/restore engine for ANIKUTA (ADR-028, ADR-035).
//
// Format: gzipped JSON in a zip container (.anikuta extension).
//   - backup.json.gz  — all serializable data (library, episodes, categories,
//                       watch progress, links, tracker, preferences, metadata)
//   - covers/*.jpg    — optional cover images (bundled for self-contained backups)
//
// Aniyomi compatibility: restore-only. Decodes Aniyomi protobuf .tachibk backups
// using kotlinx-serialization-protobuf + minimal model classes (see format/aniyomi/).
//
// This module is the aggregation point for all backup providers. It depends on
// the data-owning modules to read/write their stores. The TrackerBackupProvider
// interface (defined in :core:tracker) is implemented there and adapted here.
plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.backup"
}

dependencies {
    // ── Core data modules (read/write backup data) ──
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.preferences)
    implementation(projects.core.player)            // WatchProgressStore
    implementation(projects.core.episodeMetadata)   // EpisodeMetadataCache
    implementation(projects.core.tracker)           // TrackerBackupProvider iface + TrackRepository
    implementation(projects.core.anilist)           // AniListApi for Aniyomi translation

    // ── Phase 8 (Doc 04 violation 1): :data:extension dep REMOVED ──
    // SourceLinkBackupProvider used to import SourceLinkStore +
    // ExtensionLinkStore directly from :data:extension (a core→data inversion).
    // It now injects the SourceLinkBackupAccess interface declared in this
    // module; the impl (SourceLinkBackupAccessImpl) lives in :data:extension
    // and is Koin-bound in app/.../di/ExtensionModule.kt. The :app module
    // pulls in :data:extension + this module + wires them together at DI time.

    // ── Serialization ──
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")

    // ── Coroutines ──
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // ── OkHttp (cover image downloads) ──
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")

    // ── Koin DI ──
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // ── WorkManager (auto-backup periodic worker) ──
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ── DocumentFile (SAF folder/file management) ──
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ── Testing ──
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
