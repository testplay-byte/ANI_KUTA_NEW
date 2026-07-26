package app.confused.anikuta.core.backup

/**
 * The user-selectable data categories for backup/restore.
 *
 * Each category maps to a [BackupProvider]. The user checks/unchecks these
 * in the BackupSettingsScreen to control what gets included in a manual or
 * auto backup.
 *
 * The [id] string is stable across versions — it's stored in [BackupOptions]
 * and used as the key in the backup container. Never change an existing id
 * (add a new one instead).
 *
 * @param id stable provider id (matches [BackupProvider.id])
 * @param displayName human-readable label shown in the UI
 * @param description short description shown under the label
 * @param defaultSelected whether this category is checked by default
 */
enum class BackupCategory(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultSelected: Boolean,
) {
    LIBRARY(
        id = "library",
        displayName = "Library anime",
        description = "Anime in your library (favorites)",
        defaultSelected = true,
    ),
    ANIME_DETAILS(
        id = "anime_details",
        displayName = "Anime details",
        description = "Descriptions, genres, scores, cover colors",
        defaultSelected = true,
    ),
    EPISODES(
        id = "episodes",
        displayName = "Episodes list",
        description = "Episode names, numbers, URLs, summaries",
        defaultSelected = true,
    ),
    EPISODE_METADATA(
        id = "episode_metadata",
        displayName = "Episode metadata",
        description = "Enriched titles, descriptions, thumbnails, air dates",
        defaultSelected = false,
    ),
    WATCH_PROGRESS(
        id = "watch_progress",
        displayName = "Watch progress",
        description = "Playback positions and watch history",
        defaultSelected = true,
    ),
    SOURCE_LINKS(
        id = "source_links",
        displayName = "Source links",
        description = "Source matches and extension links",
        defaultSelected = true,
    ),
    TRACKER(
        id = "tracker",
        displayName = "Tracking",
        description = "AniList/MAL OAuth tokens + tracker bindings",
        defaultSelected = true,
    ),
    CATEGORIES(
        id = "categories",
        displayName = "Library categories",
        description = "Custom categories and anime–category links",
        defaultSelected = true,
    ),
    PREFERENCES(
        id = "preferences",
        displayName = "Preferences",
        description = "All app preferences (display, episode settings, etc.)",
        defaultSelected = true,
    ),
    COVER_IMAGES(
        id = "cover_images",
        displayName = "Cover images",
        description = "Download cover images and bundle them (self-contained)",
        defaultSelected = false,
    );

    companion object {
        /** Look up a category by its stable id. Returns null if not found. */
        fun fromId(id: String): BackupCategory? = entries.firstOrNull { it.id == id }

        /** The default selection set (all categories where [defaultSelected] is true). */
        val defaultSelection: Set<String> = entries.filter { it.defaultSelected }.map { it.id }.toSet()
    }
}
