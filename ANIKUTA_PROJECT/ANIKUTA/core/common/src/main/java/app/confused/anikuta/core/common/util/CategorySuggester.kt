package app.confused.anikuta.core.common.util

/**
 * Suggests category names based on 5 status keywords.
 *
 * Per user spec (§3.2):
 * - Suggestions appear only after the user has typed 3+ characters.
 * - A suggestion matches if ANY 3-letter substring of the typed text matches
 *   ANY 3-letter prefix of ANY of the 5 keywords (case-insensitive).
 * - The suggestion's case matches the user's typing:
 *   - All lowercase → suggestion is all lowercase (e.g. "watching")
 *   - First letter capital, rest lowercase → suggestion is capitalized (e.g. "Watching")
 *   - All caps → suggestion is all caps (e.g. "WATCHING")
 *   - Mixed case → suggestion matches the user's exact casing pattern
 *
 * This is a pure Kotlin object with no Compose/Android dependencies, so it
 * can be unit-tested independently.
 *
 * The 5 keywords are category-name SUGGESTIONS only — they are NOT status
 * filters (per user decision Q6). Tapping a suggestion auto-completes the
 * text field with the suggested name.
 */
object CategorySuggester {

    private val KEYWORDS = listOf("watching", "completed", "paused", "dropped", "planning")

    /**
     * @param typed the current text in the category-name field.
     * @return the suggested full keyword (with matching casing), or null if
     *         no suggestion applies (typed too short, or no 3-char substring
     *         matches any keyword prefix).
     */
    fun suggest(typed: String): String? {
        if (typed.length < 3) return null
        val lowerTyped = typed.lowercase()

        for (keyword in KEYWORDS) {
            // Check if any 3-char substring of typed matches any 3-char prefix of keyword.
            for (i in 0..lowerTyped.length - 3) {
                val substring = lowerTyped.substring(i, i + 3)
                if (keyword.startsWith(substring)) {
                    // Match found — apply the user's casing to the keyword.
                    return applyCasing(keyword, typed)
                }
            }
        }
        return null
    }

    /**
     * Apply the casing pattern of [typed] to [keyword].
     *
     * - All lowercase → keyword.lowercase()
     * - All uppercase → keyword.uppercase()
     * - First letter capital, rest lowercase → keyword.replaceFirstChar { it.uppercase() }
     * - Mixed case → apply per-character casing from [typed] where possible.
     */
    private fun applyCasing(keyword: String, typed: String): String {
        return when {
            typed.all { it.isLowerCase() } -> keyword.lowercase()
            typed.all { it.isUpperCase() } -> keyword.uppercase()
            typed[0].isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                keyword.replaceFirstChar { it.uppercase() }
            else -> {
                // Mixed case: apply per-character casing from typed to keyword,
                // character by character. If typed is longer than keyword, the
                // extra characters are ignored. If shorter, the rest of keyword
                // stays lowercase.
                val result = StringBuilder(keyword.length)
                keyword.forEachIndexed { index, c ->
                    if (index < typed.length) {
                        val source = typed[index]
                        result.append(
                            when {
                                source.isUpperCase() -> c.uppercaseChar()
                                source.isLowerCase() -> c.lowercaseChar()
                                else -> c
                            }
                        )
                    } else {
                        result.append(c.lowercaseChar())
                    }
                }
                result.toString()
            }
        }
    }
}
