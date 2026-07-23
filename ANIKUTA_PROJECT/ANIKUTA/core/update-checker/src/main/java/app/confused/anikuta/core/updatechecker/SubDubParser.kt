package app.confused.anikuta.core.updatechecker

/**
 * Parses sub/dub audio availability from an episode's scanlator + name.
 *
 * This mirrors the heuristic used by the anime-details episode list
 * (`feature/anime-details/.../EpisodesSection.kt::parseAudioAvailability`)
 * so the Updates page shows the same "SUB" / "DUB" / "HSUB" pills the user
 * already sees on the detail screen.
 *
 * Detection is keyword-based on the uppercased concatenation of the scanlator
 * and episode name:
 *  - "HSUB" or "HARDSUB" → hardsub (takes precedence over plain SUB)
 *  - "SUB" (and not HSUB) → soft sub
 *  - "DUB" (and not HSUB) → dubbed audio
 *
 * This is deliberately a single function (not a class) so it can be unit-tested
 * in isolation and reused by both [UpdateChecker] and any future caller.
 */
fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}
