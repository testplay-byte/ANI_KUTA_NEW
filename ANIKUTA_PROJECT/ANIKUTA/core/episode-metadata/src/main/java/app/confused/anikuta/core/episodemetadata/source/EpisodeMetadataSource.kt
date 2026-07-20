package app.confused.anikuta.core.episodemetadata.source

import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest

/**
 * A pluggable source for episode metadata.
 *
 * Per ADR-022: extensions declare what features they support. This interface
 * is the contract for metadata sources. New sources (TMDB, etc.) can be added
 * by implementing this interface and registering in the
 * [EpisodeMetadataSourceRegistry].
 *
 * Known sources (from the old ANIKUTA project):
 * - AniList streaming episodes
 * - Jikan (MyAnimeList)
 * - Kitsu
 * - Anikage.cc
 *
 * Future sources:
 * - TMDB (The Movie Database) — per owner's plan.
 */
interface EpisodeMetadataSource {
    /** Unique source identifier (e.g. "anilist", "jikan", "kitsu", "tmdb"). */
    val id: String

    /** Human-readable name. */
    val name: String

    /** Whether this source supports the given anime (e.g. by ID type). */
    fun supports(request: EpisodeMetadataRequest): Boolean

    /** Fetch episode metadata for the given request. */
    suspend fun fetch(request: EpisodeMetadataRequest): EpisodeMetadata?

    /** The fields this source can provide (for merge priority). */
    val providedFields: Set<EpisodeMetadataField>
}

/** Fields that a metadata source can provide. Used for merge priority. */
enum class EpisodeMetadataField {
    TITLE,
    DESCRIPTION,
    THUMBNAIL,
    AIR_DATE,
    FILLER,
}
