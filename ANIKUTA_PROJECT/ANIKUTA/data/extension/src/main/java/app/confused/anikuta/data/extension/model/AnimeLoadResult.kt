package app.confused.anikuta.data.extension.model

/**
 * The result of attempting to load a single extension APK.
 *
 * - [Success]                 — the extension loaded; its sources are live.
 * - [Untrusted]               — the APK is installed but its signature isn't trusted.
 * - [Error]                   — loading failed (bad version, unsigned, class error, etc.).
 * - [UnrecognizedExtension]   — the package doesn't look like an extension at all
 *   (missing the `tachiyomi.animeextension` feature flag).
 *
 * Ported from the Aniyomi reference (`AnimeLoadResult.kt`). The reference only
 * has three variants; we add [UnrecognizedExtension] to make the "this isn't an
 * extension" case explicit (per the implementation prompt's requirement).
 */
sealed interface AnimeLoadResult {
    data class Success(val extension: AnimeExtension.Installed) : AnimeLoadResult
    data class Untrusted(val extension: AnimeExtension.Untrusted) : AnimeLoadResult
    data object Error : AnimeLoadResult
    data object UnrecognizedExtension : AnimeLoadResult
}
