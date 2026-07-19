// buildSrc settings — used to build the convention plugins
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs")      { from(files("../gradle/libs.versions.toml")) }
        create("kotlinx")   { from(files("../gradle/kotlinx.versions.toml")) }
        create("androidx")  { from(files("../gradle/androidx.versions.toml")) }
        create("compose")   { from(files("../gradle/compose.versions.toml")) }
    }
}
