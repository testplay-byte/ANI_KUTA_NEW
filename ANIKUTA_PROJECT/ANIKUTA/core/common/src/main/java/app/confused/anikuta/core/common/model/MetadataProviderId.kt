package app.confused.anikuta.core.common.model

/**
 * A metadata provider — an external service that supplies anime metadata
 * (titles, covers, scores, schedules, etc.) and optionally tracking.
 *
 * Distinct from [ExtensionSystem] (which is the *source* / streaming side):
 * a metadata provider is the *identity + metadata* side. An anime can be
 * linked to one or more metadata providers (AniList, MAL, TMDB, …); the
 * provider IDs are stored as external links and used to derive [ContentId].
 *
 * Per ADR-050 + the owner's direction, the [ContentId] derivation priority
 * is **user-configurable** (see `ContentIdPreferences`). The default order
 * is the declaration order of this enum (ANIYOMI … KITSU), but the user
 * can reorder it in Settings → Data & Storage → Content Identity.
 *
 * Adding a new provider: add an enum constant here (one line). The
 * [ContentIdGenerator] + the `content_links` table (when implemented)
 * pick it up automatically.
 *
 * @property key The stable string key used in [ContentId] serialization
 *               (e.g., `"al:154587"` for AniList) + the `provider` column
 *               in the future `content_links` table.
 * @property displayName Human-readable name for UI display.
 */
enum class MetadataProviderId(
    val key: String,
    val displayName: String,
) {
    /** AniList — the default co-primary metadata source (ADR-010). GraphQL API. */
    ANILIST("al", "AniList"),

    /** MyAnimeList — OAuth (PKCE) + REST API. Already implemented for tracking. */
    MAL("mal", "MyAnimeList"),

    /** TMDB (The Movie Database) — future. Has season/episode air dates + movies. */
    TMDB("tmdb", "TMDB"),

    /** Kitsu — future. OAuth + JSON:API. */
    KITSU("kitsu", "Kitsu"),
    ;

    companion object {
        /** Resolve a [key] string back to its [MetadataProviderId], or null if unknown. */
        fun fromKey(key: String): MetadataProviderId? = entries.firstOrNull { it.key == key }
    }
}
