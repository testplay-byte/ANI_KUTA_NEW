package app.confused.anikuta.core.backup

import app.confused.anikuta.core.backup.model.AnimeBackup
import app.confused.anikuta.core.backup.model.AnimeCategoryBackup
import app.confused.anikuta.core.backup.model.CategoryBackup
import app.confused.anikuta.core.backup.model.EpisodeBackup
import app.confused.anikuta.core.backup.model.EpisodeMetadataItem
import app.confused.anikuta.core.backup.model.PreferenceBackup
import app.confused.anikuta.core.backup.model.SourceLinkBackup
import app.confused.anikuta.core.backup.model.TrackerBackupModel
import app.confused.anikuta.core.backup.model.WatchProgressBackup
import kotlinx.serialization.Serializable

/**
 * A sealed class representing one provider's backup payload.
 *
 * Each [BackupProvider] exports one [BackupEntry] subclass and imports the same
 * type. The [providerId] is a computed property (no backing field, so
 * kotlinx-serialization doesn't try to serialize it — the polymorphic class
 * discriminator handles type identity in the JSON).
 *
 * Adding a new data type:
 *  1. Add a new subclass here (with `@Serializable`).
 *  2. Add the `is` branch to [providerId].
 *  3. Create the data model in `model/`.
 *  4. Create a [BackupProvider] implementation in `provider/`.
 *  5. Register the provider in [BackupModule].
 *  6. Add a [BackupCategory] entry.
 */
@Serializable
sealed class BackupEntry {

    /** Stable identifier matching [BackupProvider.id] and [BackupCategory.id]. Computed (not serialized). */
    val providerId: String
        get() = when (this) {
            is Library -> BackupCategory.LIBRARY.id
            is AnimeDetails -> BackupCategory.ANIME_DETAILS.id
            is Episodes -> BackupCategory.EPISODES.id
            is EpisodeMetadata -> BackupCategory.EPISODE_METADATA.id
            is WatchProgress -> BackupCategory.WATCH_PROGRESS.id
            is SourceLinks -> BackupCategory.SOURCE_LINKS.id
            is Tracker -> BackupCategory.TRACKER.id
            is Categories -> BackupCategory.CATEGORIES.id
            is Preferences -> BackupCategory.PREFERENCES.id
            is CoverImages -> BackupCategory.COVER_IMAGES.id
        }

    /** Library anime (favorites from the `animes` table). */
    @Serializable
    data class Library(
        val animes: List<AnimeBackup> = emptyList(),
    ) : BackupEntry()

    /** Full anime details (description, genres, scores — the non-favorite columns). */
    @Serializable
    data class AnimeDetails(
        val animes: List<AnimeBackup> = emptyList(),
    ) : BackupEntry()

    /** Episodes per anime (keyed by anilistId or "sourceId:url"). */
    @Serializable
    data class Episodes(
        val byAnime: Map<String, List<EpisodeBackup>> = emptyMap(),
    ) : BackupEntry()

    /** Enriched episode metadata per anime. Outer key = animeId, inner key = episodeNumber. */
    @Serializable
    data class EpisodeMetadata(
        val byAnime: Map<String, Map<String, EpisodeMetadataItem>> = emptyMap(),
    ) : BackupEntry()

    /** Watch progress (playback positions). */
    @Serializable
    data class WatchProgress(
        val progress: WatchProgressBackup = WatchProgressBackup(),
    ) : BackupEntry()

    /** AniList↔extension source links + extension↔AniList links. */
    @Serializable
    data class SourceLinks(
        val links: SourceLinkBackup = SourceLinkBackup(),
    ) : BackupEntry()

    /** Tracker tokens + bindings. */
    @Serializable
    data class Tracker(
        val data: TrackerBackupModel = TrackerBackupModel(),
    ) : BackupEntry()

    /** Categories + anime–category junction links. */
    @Serializable
    data class Categories(
        val categories: List<CategoryBackup> = emptyList(),
        val links: List<AnimeCategoryBackup> = emptyList(),
    ) : BackupEntry()

    /** All app preferences. */
    @Serializable
    data class Preferences(
        val prefs: PreferenceBackup = PreferenceBackup(),
    ) : BackupEntry()

    /**
     * Cover image references. The actual image bytes are stored as files in the
     * zip container (`covers/<anilistId>.jpg`), not in the JSON. This entry just
     * records which anilistIds have bundled covers + their original URLs.
     */
    @Serializable
    data class CoverImages(
        val covers: Map<String, String> = emptyMap(),
    ) : BackupEntry()
}
