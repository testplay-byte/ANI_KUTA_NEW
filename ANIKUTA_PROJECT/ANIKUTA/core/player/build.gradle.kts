plugins {
    id("anikuta.library.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.player"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.sourceApi)

    // MPV — the video player (ADR-025). MUST be `api` because AnikutaMPVView
    // extends is.xyz.mpv.BaseMPVView (a public supertype) — consumers need to
    // see BaseMPVView + MPVLib to call view methods.
    api(anikutaLibs.aniyomi.mpv)
    // FFmpeg — libmpv.so is dynamically linked against it
    implementation(anikutaLibs.ffmpeg.kit)
    implementation(anikutaLibs.arthenica.smartexceptions)
    // Seeker — Compose seekbar library
    implementation(anikutaLibs.seeker)
    // NanoHTTPD — localhost proxy for proxied video URLs
    implementation(anikutaLibs.nanohttpd)
    // Media session — for background media controls
    implementation(anikutaLibs.mediasession)
    // TrueType parser — for subtitle font parsing
    implementation(anikutaLibs.truetypeparser)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Coroutines + serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
}
