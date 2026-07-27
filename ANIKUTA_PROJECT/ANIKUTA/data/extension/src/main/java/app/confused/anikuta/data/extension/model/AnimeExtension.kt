package app.confused.anikuta.data.extension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.animesource.AnimeSource

/**
 * Represents an anime extension — an external APK that implements the
 * `:core:source-api` contract (ADR-029: Aniyomi-compatible).
 *
 * An extension can be in one of three states:
 * - [Installed]  — loaded into memory, its sources are available to the app.
 * - [Available]  — listed in a remote extension repo, not yet installed.
 * - [Untrusted]  — installed on disk but its signing certificate hasn't been
 *   approved by the user yet. The loader refuses to instantiate its sources
 *   until [AnimeExtensionManager.trust] is called.
 *
 * Ported from the Aniyomi reference (`AnimeExtension.kt`) and adapted to the
 * ANIKUTA package (`app.confused.anikuta.data.extension`). The shape mirrors
 * the reference so future Aniyomi extension tooling stays compatible.
 *
 * @see AnimeLoadResult
 */
sealed class AnimeExtension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean
    abstract val isTorrent: Boolean

    /**
     * An extension whose APK is installed and whose source classes have been
     * successfully loaded into memory.
     *
     * @param sources      the live [AnimeSource] instances loaded from the APK.
     * @param icon         the extension's launcher icon (lazy-loaded).
     * @param hasUpdate    `true` when a newer version exists in a remote repo.
     * @param isObsolete   `true` when the extension is no longer listed in any
     *                     known repo (delisted / repo removed).
     * @param isShared     `true` when installed via the system package manager
     *                     (shared with other apps); `false` for private installs.
     * @param repoUrl      the base URL of the repo this extension came from (if known).
     */
    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        override val isTorrent: Boolean,
        val pkgFactory: String?,
        val sources: List<AnimeSource>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val repoUrl: String? = null,
    ) : AnimeExtension()

    /**
     * An extension that exists in a remote repo index but is not yet installed.
     *
     * The [sources] field holds source *metadata* (not live [AnimeSource] instances)
     * because the APK hasn't been loaded. This metadata powers the stub-source
     * registry so the UI can show source names before install.
     *
     * @param apkName  the APK filename inside the repo (`<repo>/apk/<apkName>`).
     * @param iconUrl  full URL to the extension's icon (`<repo>/icon/<pkg>.png`).
     * @param repoUrl  the base URL of the repo this listing came from.
     */
    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        override val isTorrent: Boolean,
        val sources: List<AnimeSourceMetadata>,
        val apkName: String,
        val iconUrl: String,
        val repoUrl: String,
    ) : AnimeExtension() {

        /**
         * Lightweight metadata about a source inside an available extension.
         * Mirrors the reference's `AnimeExtension.Available.AnimeSource`.
         */
        data class AnimeSourceMetadata(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        )
    }

    /**
     * An extension whose APK is installed but whose signature has not been
     * trusted by the user. Its sources are NOT loaded until [AnimeExtensionManager.trust]
     * writes the signature to the trusted-extensions preference and re-runs the loader.
     */
    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val isTorrent: Boolean = false,
    ) : AnimeExtension()
}
