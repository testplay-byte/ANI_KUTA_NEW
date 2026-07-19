package app.anikuta.domain.extension.anime.interactor

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.getAndSet
import app.anikuta.domain.source.service.SourcePreferences

/**
 * Trust management for anime extensions.
 *
 * ANI-KUTA uses a simplified per-package trust model (vs aniyomi's
 * per-version+signature model). An extension is "trusted" (appears in
 * Sources and is used for search/resolve) iff its package name is in
 * [SourcePreferences.trustedSources].
 *
 * The user must explicitly trust each installed extension. Max
 * [MAX_TRUSTED] extensions can be trusted at once (Phase 7 requirement).
 *
 * Source: REFERENCE/app/.../domain/extension/anime/interactor/TrustAnimeExtension.kt
 * (simplified — aniyomi also checks repo fingerprints for auto-trust).
 */
class TrustAnimeExtension(
    private val preferences: SourcePreferences,
) {
    fun isTrusted(pkgName: String): Boolean {
        return pkgName in preferences.trustedSources().get()
    }

    fun trust(pkgName: String) {
        preferences.trustedSources().getAndSet { it + pkgName }
    }

    fun revoke(pkgName: String) {
        preferences.trustedSources().getAndSet { it - pkgName }
    }

    fun revokeAll() {
        preferences.trustedSources().delete()
    }

    fun getTrusted(): Set<String> = preferences.trustedSources().get()

    companion object {
        /** Maximum number of extensions that can be trusted (in Sources) at once. */
        const val MAX_TRUSTED = 2
    }
}
