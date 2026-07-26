plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.download"
}

// ── :core:download — the download engine ──
//
// Pure logic + Android (Context for SAF + notifications). NO Compose UI here —
// the UI lives in :feature:download. This keeps the engine swappable and
// testable (per ARCHITECTURE.md §3 module boundaries + the modular
// DownloadManager interface decision in DOCS/04 ADR-020).
//
// Depends on:
//  - :core:preferences  → PreferenceStore (DownloadPreferences + DownloadStore)
//  - :core:source-api    → SEpisode, Track (subtitle/audio), OkHttp (api exposure)
//  - :core:common        → shared utilities/constants (transitive)
//  - okhttp              → file downloads (via :core:source-api api exposure)
//  - kotlinx-serialization → DownloadStore + metadata.json persistence
//  - kotlinx-coroutines  → queue + progress flows
//  - Koin                → DI (DownloadModule)
//
// Does NOT depend on :feature:* (module boundary — feature isolation, Rule §14).
// Does NOT depend on :feature:video-resolver — video URL resolution is
// orchestrated by :app's DownloadOrchestrator, which passes the already-resolved
// DownloadRequest into this module. This keeps the engine decoupled and lets a
// future OneDmDownloadManager swap in without touching resolution logic.

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)

    // OkHttp — for streaming downloads with progress. Comes transitively via
    // :core:source-api (api exposure), but declare explicitly for clarity.
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")

    // AndroidX DocumentFile — SAF folder tree navigation + file creation for
    // the user-selected download folder (content:// URIs, not java.io.File).
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Serialization — DownloadStore persists the queue as JSON; metadata.json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
