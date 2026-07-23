package app.confused.anikuta.core.updatechecker

import app.confused.anikuta.core.common.model.Anime

/**
 * The result of checking one library anime for new episodes.
 *
 * Produced by [UpdateChecker.checkForUpdates] / [UpdateChecker.checkAnime].
 * The Updates page consumes a list of these reactively via
 * [UpdateChecker.getLastResults].
 *
 * @property anime The library anime that was checked.
 * @property newEpisodeCount How many episodes are new since the last check.
 *   Zero is possible only for [UpdateChecker.checkAnime] (single-anime check);
 *   [UpdateChecker.checkForUpdates] only returns results with `newEpisodeCount > 0`.
 * @property newEpisodes The new episodes (episode number, title, url, upload date).
 *   Sorted ascending by episode number.
 * @property checkedAt Epoch-millis when this check ran.
 * @property hasSub True if any new episode's name/scanlator indicates a subbed
 *   release (parsed by [parseAudioAvailability]).
 * @property hasDub True if any new episode indicates a dubbed release.
 * @property sourceName The extension source name the episodes came from, or
 *   null if no source matched (in which case `newEpisodes` is empty and the
 *   result exists only to surface an AniList airing cross-reference).
 * @property isNew True if this result was found in the MOST RECENT check run.
 *   False for results carried over from previous checks. The Updates page uses
 *   this to highlight freshly-found updates (accent treatment) while showing
 *   older ones normally. Set by [UpdateChecker] when merging results.
 */
data class UpdateResult(
    val anime: Anime,
    val newEpisodeCount: Int,
    val newEpisodes: List<EpisodeInfo>,
    val checkedAt: Long,
    val hasSub: Boolean,
    val hasDub: Boolean,
    val sourceName: String?,
    val isNew: Boolean = false,
)

/**
 * Live progress of an in-flight update check. Emitted by
 * [UpdateChecker.getCheckProgress] as the check iterates library anime.
 *
 * The Updates page renders a "Currently checking" card from [Checking] so the
 * user sees exactly which anime is being searched (poster + title + index/total)
 * with smooth transitions between anime — instead of an opaque spinner that
 * only resolves when the whole check finishes.
 *
 * - [Idle] — no check in flight (the default).
 * - [Checking] — a check is running; [currentAnime] is being fetched right now,
 *   [currentIndex]/[totalCount] is its position in the library, and [foundSoFar]
 *   is the running list of results (grows incrementally as each anime resolves).
 * - [Completed] — the check finished; [foundCount] anime had new episodes.
 */
sealed interface UpdateCheckProgress {
    data object Idle : UpdateCheckProgress
    data class Checking(
        val currentAnime: Anime,
        val currentIndex: Int,
        val totalCount: Int,
        val foundSoFar: Int,
    ) : UpdateCheckProgress
    data class Completed(
        val foundCount: Int,
        val totalChecked: Int,
    ) : UpdateCheckProgress
}

/**
 * A single episode discovered during an update check.
 *
 * A trimmed, serializable view of `SEpisode` (the Aniyomi source-api type).
 * We don't pass `SEpisode` around because it's a mutable `Serializable`
 * interface tied to the extension classloader — keeping our own data class
 * lets the Updates UI live in a module that doesn't depend on `:core:source-api`
 * at runtime if we later want to split it.
 */
data class EpisodeInfo(
    val episodeNumber: Float,
    val title: String,
    val url: String,
    val dateUpload: Long,
    val scanlator: String?,
)

/**
 * Sub/dub audio availability parsed from an episode's scanlator + name.
 *
 * Matches the heuristic used by the anime-details episode list
 * (`EpisodesSection.kt::parseAudioAvailability`):
 *  - "HSUB"/"HARDSUB" → hasHsub (hardcoded subtitles)
 *  - "SUB" (and not HSUB) → hasSub
 *  - "DUB" (and not HSUB) → hasDub
 */
data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
) {
    /** True if any audio variant is available. */
    val hasAny: Boolean get() = hasSub || hasDub || hasHsub
}
