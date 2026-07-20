plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.sourceapi"
}

dependencies {
    // OkHttp (for Headers in Video, networking in HttpSource)
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // Jsoup (for ParsedAnimeHttpSource, used by extensions)
    implementation("org.jsoup:jsoup:1.19.1")
    // kotlinx-serialization (for Video serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    // RxJava 1.x (for the deprecated fetch* API that extensions use — ADR-029 extension compat)
    implementation("io.reactivex:rxjava:1.3.8")
    // RxAndroid (for AndroidSchedulers, used by some extensions)
    implementation("io.reactivex:rxandroid:1.2.1")
    // Compose stable marker (for @Stable annotation — compileOnly, not needed at runtime)
    compileOnly("com.github.skydoves:compose-stable-marker:1.0.5")
    // AndroidX Preference (for PreferenceScreen typealias)
    implementation("androidx.preference:preference-ktx:1.2.1")
}
