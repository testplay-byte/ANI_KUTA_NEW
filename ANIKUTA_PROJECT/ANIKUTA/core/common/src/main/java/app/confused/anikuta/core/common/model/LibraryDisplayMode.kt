package app.confused.anikuta.core.common.model

/**
 * Display mode for the library page.
 *
 * Per user decision (Q3): display mode is GLOBAL — all categories use the
 * same mode. Stored in [LibraryPreferences].
 *
 * - [COMPACT_GRID] — cover with title overlaid at the bottom (gradient).
 * - [COMFORTABLE_GRID] — cover with title below it (2 lines).
 * - [LIST] — horizontal row: small cover + title + metadata.
 */
enum class LibraryDisplayMode {
    COMPACT_GRID,
    COMFORTABLE_GRID,
    COVER_ONLY,
    LIST,
}
