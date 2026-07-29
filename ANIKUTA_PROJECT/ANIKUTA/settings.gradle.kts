// ANIKUTA root settings
// Module layout per ARCHITECTURE.md §3

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinx")    { from(files("gradle/kotlinx.versions.toml")) }
        create("androidx")   { from(files("gradle/androidx.versions.toml")) }
        create("compose")    { from(files("gradle/compose.versions.toml")) }
        create("anikutaLibs"){ from(files("gradle/anikuta.versions.toml")) }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ANIKUTA"

// ── :app ──
include(":app")

// ── :core ──
include(":core:common")
include(":core:designsystem")
// :core:network removed (Phase 9 — empty stub, 0 .kt files)
include(":core:database")
include(":core:preferences")
include(":core:provider-api")      // Phase 2: pluggable metadata provider contracts (ADR-041)
include(":core:anilist")
include(":core:tracker")           // Agent 2: Profile + Trackers
include(":core:episode-metadata")
include(":core:source-api")
// :core:source-local removed (Phase 9 — empty stub, 0 .kt files; re-add when local-files-as-source is implemented)
include(":core:player")
include(":core:update-checker")
include(":core:download")
// :core:notification removed (Phase 9 — empty stub, 0 .kt files; re-add when episode-release notifications are implemented per ADR-014)
include(":core:backup")
include(":core:video-resolver")  // Phase 8: video-resolver logic + types (Doc 04 violations 3+4)

// ── :data ──
include(":data:anime")
// :data:manga removed (Phase 9 — empty stub, 0 .kt files; re-add when manga reader is implemented per ADR-009)
include(":data:extension")
// :data:tracker removed (Phase 9 — empty stub, 0 .kt files; tracker impls live in :core:tracker)
include(":data:history")

// ── :feature ──
// :feature:home removed (Phase 9 — empty stub; Home tab = BrowseScreen in :feature:browse)
include(":feature:library")
include(":feature:updates")
include(":feature:history")
include(":feature:browse")
include(":feature:search")
include(":feature:my")
// :feature:more removed (Phase 9 — empty stub; More tab rendered inline in MoreScreens.kt)
include(":feature:anime-details")
// :feature:episode-list removed (Phase 9 — empty stub; episode list lives in :feature:anime-details/EpisodesSection.kt)
include(":feature:episode-settings")
include(":feature:video-resolver")
include(":feature:watch")
// :feature:player removed (Phase 9 — empty stub; fullscreen player handled in :feature:watch)
include(":feature:extensions-settings")
include(":feature:settings")
include(":feature:trackers")
include(":feature:backup")
include(":feature:download")

// :i18n removed (Phase 9 — phantom module declared but no directory existed; re-add when Moko Resources is implemented per ADR-027)
