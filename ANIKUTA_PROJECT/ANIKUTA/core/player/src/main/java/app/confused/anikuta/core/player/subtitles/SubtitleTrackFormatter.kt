package app.confused.anikuta.core.player.subtitles

/**
 * Standalone formatter for MPV track-list display names.
 *
 * Extracted from the inline formatting block in
 * `AnikutaMPVView.loadTracks()`. That block is kept verbatim in the view to
 * preserve the documented "L6 fix" (skip tracks with no valid id; never emit
 * a bogus "Track -1"); this standalone object lets non-view callers (tests,
 * future TrackSheet refactor, a Compose-side preview) reuse the same logic
 * without depending on `BaseMPVView`.
 *
 * The rules mirror what the view does today:
 *  - A title that looks like an ugly filename (`.vtt` / `.srt` / `.ass` /
 *    `.ssa` suffix, or a >20-char hash with no spaces) is discarded so the
 *    language (or fallback "Track N") is shown instead.
 *  - When both a real title and a language are available, the result is
 *    `"<title> (<lang>)"`.
 *  - When only one of the two is available, that one is shown.
 *  - When neither is available, fall back to `"Track <id>"`.
 *
 * Note: this object does NOT inject the "Off" sentinel — callers add that
 * themselves (it's a UI-only concept that doesn't belong in a name formatter).
 */
object SubtitleTrackFormatter {

    fun formatTrackName(id: Int, title: String, lang: String): String {
        val isUglyFilename = title.isNotBlank() && (
            title.endsWith(".vtt", ignoreCase = true) ||
            title.endsWith(".srt", ignoreCase = true) ||
            title.endsWith(".ass", ignoreCase = true) ||
            title.endsWith(".ssa", ignoreCase = true) ||
            (title.length > 20 && title.none { it == ' ' })
        )
        val displayTitle = if (isUglyFilename) "" else title
        return when {
            displayTitle.isNotBlank() && lang.isNotBlank() -> "$displayTitle ($lang)"
            displayTitle.isNotBlank() -> displayTitle
            lang.isNotBlank() -> lang
            else -> "Track $id"
        }
    }
}
