package app.confused.anikuta.core.common.model

/**
 * Full source provenance for an anime entry — the "where did this come from?"
 * metadata stored alongside the [Anime] per ADR-050.
 *
 * This is the Tier 1 metadata that accompanies the [LocalId] (Tier 1 identity).
 * It captures everything needed to re-resolve the source on a fresh device
 * (backup/restore) or after an extension is uninstalled + reinstalled:
 * - which [system] the source uses (Aniyomi, CloudStream, …),
 * - which repo the extension came from ([repoUrl], [repoName]),
 * - which extension produced it ([extensionPkgName], [extensionName], versions, lang),
 * - the source's display name ([sourceName] — distinct from extension name; one
 *   extension can have multiple sources via `AnimeSourceFactory`),
 * - bookkeeping ([discoveredAt], [lastResolvedAt], [linkConfidence]).
 *
 * Per the owner's direction (rev 2 of the identity model): "all these four things
 * which I mentioned are important things which need to be handled" — system, repo,
 * extension, source-content-id. This class stores the first three; the
 * source-content-id is part of the [LocalId] itself.
 *
 * Stored on the `animes` table as flat columns (see `animes.sq`). Nullable as a
 * whole (nullable during the Phase 1 transition; backfilled by
 * `AnimeRepositoryImpl.backfillIdentityColumns()`).
 */
data class SourceProvenance(
    /** The extension system. See [ExtensionSystem]. */
    val system: ExtensionSystem,

    /** The extension repo base URL (nullable if installed from an APK, not a repo). */
    val repoUrl: String?,

    /** Human-readable repo name (from `ExtensionRepo.name`). */
    val repoName: String?,

    /** The extension's package name (e.g., `eu.kanade.tachiyomi.animeextension.en.gogoanime`). */
    val extensionPkgName: String?,

    /** Human-readable extension name (e.g., "GogoAnime"). */
    val extensionName: String?,

    /** The extension's version name (e.g., "1.4.3"). */
    val extensionVersionName: String?,

    /** The extension's version code (e.g., 143). */
    val extensionVersionCode: Long?,

    /** The extension's language (e.g., "en"). */
    val extensionLang: String?,

    /** The extension's NSFW flag. */
    val isNsfw: Boolean,

    /**
     * The source's display name — distinct from [extensionName].
     * One extension can have multiple sources via `AnimeSourceFactory`.
     */
    val sourceName: String?,

    /** Epoch ms when this [LocalId] was first created (for debugging + staleness checks). */
    val discoveredAt: Long,

    /** Epoch ms when the [ContentId] was last re-resolved (for debugging). */
    val lastResolvedAt: Long,

    /**
     * How the provider link was established:
     * - 0 = none (no provider link — unlinked),
     * - 1 = auto-matched by `SourceMatcher` (may be wrong; UI can flag it),
     * - 2 = user-confirmed (high confidence).
     */
    val linkConfidence: Int,
)
