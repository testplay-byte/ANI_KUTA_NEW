package eu.kanade.tachiyomi.util

import kotlinx.serialization.json.Json

/**
 * App provided default [Json] instance.
 *
 * The original Aniyomi version uses `Injekt.get<Json>()`. We replaced Injekt
 * with a direct lazy initialization since we use Koin (ADR-023).
 *
 * @since extensions-lib 16
 */
val defaultJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
