package app.confused.anikuta.data.extension.repo

import kotlinx.serialization.Serializable

/**
 * A remote extension repository — an HTTPS site serving three artifacts:
 *
 * - `<baseUrl>/index.json`  — the list of available extensions.
 * - `<baseUrl>/icon/<pkg>.png` — an extension's icon.
 * - `<baseUrl>/apk/<apkName>` — the actual APK file.
 *
 * (See `ANIYOMI_REFRENCE/DOCUMENTATION/03-subsystems/extensions-update.md`.)
 *
 * This mirrors the Aniyomi reference's `mihon.domain.extensionrepo.model.ExtensionRepo`
 * but drops the `signingKeyFingerprint` field for Phase 4B — signature trust is
 * handled per-install via [app.confused.anikuta.data.extension.trust.TrustExtension]
 * rather than per-repo (the per-repo fingerprint check is a hardening feature we
 * can add later without breaking the contract).
 *
 * Stored as a row in the `anikuta_extension_repos` SharedPreferences set.
 *
 * @param baseUrl   the repo's base URL (no trailing slash), e.g.
 *                  `https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo`.
 * @param name      human-readable repo name (from the repo's `repo.json` metadata,
 *                  or the baseUrl host if the repo doesn't serve `repo.json`).
 * @param shortName optional short display name.
 * @param website   optional website URL for the repo.
 * @param iconUrl   optional icon URL for the repo itself.
 */
@Serializable
data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String? = null,
    val website: String = "",
    val iconUrl: String = "",
) {
    /** The full URL to the repo's extension index. */
    val indexUrl: String get() = if (baseUrl.endsWith("/")) "${baseUrl}index.json" else "$baseUrl/index.json"

    /** Builds the full APK download URL for an extension listed in this repo. */
    fun apkUrl(apkName: String): String =
        if (baseUrl.endsWith("/")) "${baseUrl}apk/$apkName" else "$baseUrl/apk/$apkName"

    /** Builds the full icon URL for an extension package in this repo. */
    fun iconUrlFor(pkgName: String): String =
        if (baseUrl.endsWith("/")) "${baseUrl}icon/$pkgName.png" else "$baseUrl/icon/$pkgName.png"

    companion object {
        /** The default Aniyomi extension repo (ADR-029). */
        val DEFAULT = ExtensionRepo(
            baseUrl = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo",
            name = "Aniyomi Extensions",
            website = "https://aniyomi.org",
        )
    }
}
