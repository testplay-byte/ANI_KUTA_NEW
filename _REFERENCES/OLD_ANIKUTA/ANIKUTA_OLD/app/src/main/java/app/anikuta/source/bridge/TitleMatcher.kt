package app.anikuta.source.bridge

import kotlin.math.max
import kotlin.math.min

/**
 * Title fuzzy matcher for AniList ↔ extension source matching (Phase 5 task 5.2).
 *
 * Normalizes titles (lowercase, strip punctuation, strip common suffixes like
 * "Season N" / "(TV)" / "(Sub)" / "(Dub)") then computes a similarity ratio
 * using Levenshtein distance. A ratio ≥ [THRESHOLD] (0.80 = 80%) counts as a
 * match.
 *
 * This is deliberately simple and dependency-free. If matching quality needs
 * to improve later, swap Levenshtein for Jaro-Winkler (better for short strings
 * with common prefixes) without changing the public API.
 */
object TitleMatcher {

    /** Minimum similarity ratio (0.0–1.0) for a match. 0.80 = 80%. */
    const val THRESHOLD = 0.80

    /**
     * Compute the similarity ratio between two raw title strings.
     * Returns 1.0 for identical (after normalization), 0.0 for completely different.
     */
    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() && nb.isEmpty()) return 1.0
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        // If one is a prefix/substring of the other (e.g., "Frieren" vs
        // "Frieren: Beyond Journey's End"), count it as a strong match.
        if (na in nb || nb in na) return 0.95
        val maxLen = max(na.length, nb.length)
        val dist = levenshtein(na, nb)
        return 1.0 - dist.toDouble() / maxLen
    }

    /** True if [a] and [b] are ≥ [THRESHOLD] similar. */
    fun matches(a: String, b: String): Boolean = similarity(a, b) >= THRESHOLD

    /**
     * Normalize a title for comparison: lowercase, strip punctuation, strip
     * common anime-title suffixes, collapse whitespace.
     */
    fun normalize(title: String): String {
        var s = title.lowercase().trim()
        // Strip "season N" / "sN" suffixes
        s = s.replace(Regex("\\s*season\\s*\\d+\\s*"), " ")
        s = s.replace(Regex("\\s+\\d+(st|nd|rd|th)\\s+season\\s*"), " ")
        // Strip parenthetical qualifiers: (TV), (Sub), (Dub), (2024), etc.
        s = s.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
        s = s.replace(Regex("\\s*\\[[^]]*]\\s*"), " ")
        // Strip non-alphanumeric (keep spaces)
        s = s.replace(Regex("[^a-z0-9 ]"), " ")
        // Collapse whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    /**
     * Classic Levenshtein edit distance. O(m*n) DP.
     * Public so tests can verify the implementation directly.
     */
    fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        // Two rows to save memory.
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }
}
