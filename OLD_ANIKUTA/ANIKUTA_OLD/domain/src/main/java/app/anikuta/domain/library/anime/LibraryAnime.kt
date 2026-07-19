package app.anikuta.domain.library.anime

import app.anikuta.domain.entries.anime.model.Anime

data class LibraryAnime(
    val anime: Anime,
    val category: Long,
    val totalCount: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = anime.id

    val unseenCount
        get() = totalCount - seenCount

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = seenCount > 0
}
