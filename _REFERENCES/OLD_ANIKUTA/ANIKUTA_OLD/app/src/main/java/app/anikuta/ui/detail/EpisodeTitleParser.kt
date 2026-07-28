package app.anikuta.ui.detail

/**
 * Phase 7.5 — Parses episode names to extract clean titles.
 *
 * Extensions often put rich info in `SEpisode.name`:
 *   "Episode 5 - The Dragon's Labyrinth" → title = "The Dragon's Labyrinth"
 *   "EP 5 - The Dragon's Labyrinth"      → title = "The Dragon's Labyrinth"
 *   "Ep 5 - The Dragon's Labyrinth"      → title = "The Dragon's Labyrinth"
 *   "The Dragon's Labyrinth"              → title = "The Dragon's Labyrinth" (no prefix)
 *   "Episode 5"                           → title = null (no title, just episode number)
 *
 * Source: REFERENCE/app/.../presentation/util/ItemNumberFormatter.kt
 * (aniyomi doesn't parse names — it uses display modes. We parse to extract
 * the title portion when the extension includes it.)
 */
object EpisodeTitleParser {

    private val PREFIX_REGEX = Regex(
        """^(?:Episode|Ep\.?|EP)\s*\d+(?:\.\d+)?\s*[-:–—]\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extract a clean title from the episode name.
     *
     * @param name The raw SEpisode.name (e.g. "Episode 5 - The Dragon's Labyrinth")
     * @param episodeNumber The episode number (for fallback)
     * @return The cleaned title, or null if the name is just "Episode N" with no title.
     */
    fun parseTitle(name: String, episodeNumber: Float): String? {
        if (name.isBlank()) return null

        // Try stripping the "Episode X - " prefix
        val stripped = PREFIX_REGEX.replace(name, "").trim()

        // If the stripped result is empty or just a number, there's no title
        if (stripped.isEmpty() || stripped.matches(Regex("""\d+(?:\.\d+)?"""))) {
            return null
        }

        return stripped
    }

    /**
     * Get the display title for an episode.
     *
     * If the name has a parseable title, returns it.
     * Otherwise returns "Episode N" as fallback.
     */
    fun getDisplayTitle(name: String, episodeNumber: Float): String {
        return parseTitle(name, episodeNumber) ?: "Episode ${formatEpisodeNumber(episodeNumber)}"
    }

    /**
     * Format an episode number: 5.0 → "5", 5.5 → "5.5"
     */
    fun formatEpisodeNumber(episodeNumber: Float): String {
        return if (episodeNumber % 1f == 0f) {
            episodeNumber.toInt().toString()
        } else {
            episodeNumber.toString()
        }
    }
}
