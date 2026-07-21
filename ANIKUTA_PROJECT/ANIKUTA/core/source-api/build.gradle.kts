plugins {
    id("anikuta.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "app.confused.anikuta.core.sourceapi"
}

// Enable context receivers — the reference's OkHttpExtensions.kt uses
// `context(Json)` for Response.parseAs<T>() / decodeFromJsonResponse().
// Extensions compiled against the reference call these with a context receiver,
// so we MUST match the compiled signature (context receiver → extra parameter).
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    // OkHttp (for Headers in Video, networking in HttpSource)
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    // Jsoup (for ParsedAnimeHttpSource, used by extensions)
    implementation("org.jsoup:jsoup:1.19.1")
    // kotlinx-serialization (for Video serialization + Response.parseAs<T>())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // kotlinx-serialization-json-okio — for decodeFromBufferedSource() used by
    // OkHttpExtensions.parseAs<T>() (extensions call response.parseAs<T>() to
    // parse JSON responses). Without this, NoClassDefFoundError at runtime.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.9.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    // RxJava 1.x (for the deprecated fetch* API that extensions use — ADR-029 extension compat)
    implementation("io.reactivex:rxjava:1.3.8")
    // RxAndroid (for AndroidSchedulers, used by some extensions)
    implementation("io.reactivex:rxandroid:1.2.1")
    // NanoHTTPD (for HttpServer model, used by some anime sources)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Injekt — extensions resolve NetworkHelper (and other singletons) via
    // Injekt.get<T>(). AnimeHttpSource uses `by injectLazy()` to obtain the
    // shared NetworkHelper instance registered by App.kt (ADR-029 extension compat).
    // MUST be `api` so consumers (extensions loaded at runtime) can resolve
    // the same injekt types from the host classpath.
    api("com.github.mihonapp:injekt:91edab2317")
    // Compose stable marker (for @Stable annotation — compileOnly, not needed at runtime)
    compileOnly("com.github.skydoves:compose-stable-marker:1.0.5")
    // AndroidX Preference (for PreferenceScreen typealias)
    implementation("androidx.preference:preference-ktx:1.2.1")
}
