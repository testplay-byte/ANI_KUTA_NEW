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
include(":core:network")
include(":core:database")
include(":core:preferences")
include(":core:anilist")
include(":core:episode-metadata")
include(":core:source-api")
include(":core:source-local")
include(":core:player")
include(":core:download")
include(":core:notification")
include(":core:backup")

// ── :data ──
include(":data:anime")
include(":data:manga")
include(":data:extension")
include(":data:tracker")
include(":data:history")

// ── :feature ──
include(":feature:home")
include(":feature:library")
include(":feature:updates")
include(":feature:history")
include(":feature:browse")
include(":feature:my")
include(":feature:more")
include(":feature:anime-details")
include(":feature:episode-list")
include(":feature:video-resolver")
include(":feature:watch")
include(":feature:player")
include(":feature:extensions-settings")
include(":feature:settings")
include(":feature:trackers")
include(":feature:backup")

// ── :i18n ──
include(":i18n")
