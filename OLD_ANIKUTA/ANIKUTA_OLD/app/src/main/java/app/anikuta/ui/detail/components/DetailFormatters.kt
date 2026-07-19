package app.anikuta.ui.detail.components

/**
 * Formatting helpers for the detail screen.
 *
 * Extracted from [DetailScreen.kt] to improve modularity and keep
 * the main screen file focused on layout/composition.
 */

/**
 * Formats a date_upload (epoch millis) as a readable date string.
 *
 * @param epochMillis Epoch milliseconds (e.g., from `SEpisode.date_upload`).
 * @return A localized date string like "Jan 15, 2025", or empty string if
 *         the input is invalid.
 */
internal fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Formats a time-until-airing value (in seconds) into a human-readable string.
 *
 * Examples:
 * - 90000 → "1d 1h"
 * - 3600  → "1h 0m"
 * - 300   → "5m"
 * - 0     → "soon"
 */
internal fun formatTimeRemaining(secondsUntilAiring: Int): String {
    if (secondsUntilAiring <= 0) return "soon"
    val days = secondsUntilAiring / 86400
    val hours = (secondsUntilAiring % 86400) / 3600
    val minutes = (secondsUntilAiring % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "soon"
    }
}

/**
 * Strips HTML formatting tags from AniList descriptions.
 *
 * AniList returns descriptions with `<br>`, `<i>`, `</i>`, `<b>`, `</b>`, etc.
 * This function:
 * 1. Converts `<br>` to newlines
 * 2. Strips all other HTML tags
 * 3. Decodes common HTML entities (`&amp;`, `&lt;`, etc.)
 */
internal fun cleanHtmlTags(text: String): String {
    return text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
