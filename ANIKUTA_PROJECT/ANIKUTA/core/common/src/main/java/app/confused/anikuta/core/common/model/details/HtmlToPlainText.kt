package app.confused.anikuta.core.common.model.details

import android.util.Log

/**
 * Small HTML→plain-text normalizer for anime/episode descriptions.
 *
 * Both AniList and extension descriptions can contain HTML (`<br>`, `<b>`,
 * `<i>`, `<a href>`, `~~`). The `SynopsisSection` renders plain text in a
 * `Text` composable, so descriptions need normalizing first.
 *
 * - `<br>` / `<br/>` → `\n`
 * - `</p>` → `\n\n`
 * - `<b>`/`<i>`/`<strong>`/`<em>` → stripped (text kept)
 * - `<a href="...">text</a>` → `text` (URL dropped)
 * - `~~strike~~` → `strike` (kept as plain text)
 * - `&amp;`/`&lt;`/`&gt;`/`&quot;`/`&#39;` → decoded
 * - leftover tags → stripped
 * - collapses 3+ newlines → 2
 * - trims leading/trailing whitespace
 *
 * Non-obvious: uses a two-pass approach (replace block tags first, then strip
 * all remaining tags) to preserve paragraph breaks that a single naive
 * `replace(Regex("<[^>]*>"), "")` would destroy.
 */
object HtmlToPlainText {
    private const val TAG = "HtmlToPlainText"

    /**
     * @param html raw HTML description (may be null/blank).
     * @return plain text, or null if [html] is null/blank after normalization.
     */
    fun normalize(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return try {
            var s = html
            // Block-level breaks (before stripping tags so we keep paragraph spacing).
            s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
            s = s.replace(Regex("(?i)</p>"), "\n\n")
            s = s.replace(Regex("(?i)</div>"), "\n")
            // Strip all remaining tags.
            s = s.replace(Regex("<[^>]*>"), "")
            // Decode common entities.
            s = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
            // Collapse 3+ newlines → 2, and trim.
            s = s.replace(Regex("\n{3,}"), "\n\n").trim()
            if (s.isBlank()) null else s
        } catch (e: Exception) {
            Log.w(TAG, "HTML normalization failed — returning raw input", e)
            html.trim().ifBlank { null }
        }
    }
}

/**
 * Map an AniList status string to [UnifiedStatus].
 *
 * AniList values: `"FINISHED"`, `"RELEASING"`, `"NOT_YET_RELEASED"`, `"CANCELLED"`,
 * `"HIATUS"`. Anything else → [UnifiedStatus.UNKNOWN].
 */
fun mapAniListStatus(status: String?): UnifiedStatus = when (status?.uppercase()) {
    "FINISHED" -> UnifiedStatus.FINISHED
    "RELEASING" -> UnifiedStatus.RELEASING
    "NOT_YET_RELEASED" -> UnifiedStatus.NOT_YET_RELEASED
    "CANCELLED" -> UnifiedStatus.CANCELLED
    "HIATUS" -> UnifiedStatus.HIATUS
    else -> UnifiedStatus.UNKNOWN
}

/**
 * Map an `SAnime.status` int constant to [UnifiedStatus].
 *
 * Constants (from `SAnime.companion` / `AnimeStatus`):
 * 0=UNKNOWN, 1=ONGOING, 2=COMPLETED, 3=LICENSED, 4=PUBLISHING_FINISHED, 5=CANCELLED, 6=ON_HIATUS.
 *
 * Per owner direction (doc 05 §9 Q4): `LICENSED` (3) → [UnifiedStatus.UNKNOWN].
 */
fun mapSAnimeStatus(status: Int): UnifiedStatus = when (status) {
    1 -> UnifiedStatus.RELEASING            // ONGOING
    2 -> UnifiedStatus.FINISHED             // COMPLETED
    3 -> UnifiedStatus.UNKNOWN              // LICENSED — collapse
    4 -> UnifiedStatus.FINISHED             // PUBLISHING_FINISHED
    5 -> UnifiedStatus.CANCELLED
    6 -> UnifiedStatus.HIATUS               // ON_HIATUS — preserved
    else -> UnifiedStatus.UNKNOWN           // 0 or unknown
}
