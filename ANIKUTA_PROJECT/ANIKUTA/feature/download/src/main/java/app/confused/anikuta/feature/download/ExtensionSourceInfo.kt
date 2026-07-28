package app.confused.anikuta.feature.download

/**
 * Lightweight DTO for an extension source, passed from the host (MainActivity)
 * into the DownloadSettingsScreen. Avoids a `:feature:download → :data:extension`
 * dependency (the host maps from `AnimeExtension.Installed` → this DTO).
 *
 * @param sourceId The source's stable ID (used as the key for server preferences).
 * @param sourceName The source's display name (e.g. "Gogoanime").
 * @param extensionName The extension's display name (e.g. "Gogoanime (en)").
 */
data class ExtensionSourceInfo(
    val sourceId: Long,
    val sourceName: String,
    val extensionName: String,
)
