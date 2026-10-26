package tachiyomi.domain.library.model

import tachiyomi.domain.anime.model.Anime

data class LibraryAnime(
    val anime: Anime,
    val category: Long,
    val totalEpisodes: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
    val folderId: Long? = null,
) {
    val id: Long = anime.id

    val unseenCount
        get() = totalEpisodes - seenCount

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasFillermarks
        get() = fillermarkCount > 0

    val hasStarted = seenCount > 0
}
