package tachiyomi.domain.history.model

import tachiyomi.domain.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.anime.model.AnimeCover
import uy.kohesive.injekt.injectLazy
import java.util.Date

data class HistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val ogTitle: String,
    val episodeNumber: Double,
    val seen: Boolean,
    val seenAt: Date?,
    val watchDuration: Long,
    val coverData: AnimeCover,
) {
    val title: String = customAnimeManager.get(animeId)?.title ?: ogTitle

    companion object {
        private val customAnimeManager: GetCustomAnimeInfo by injectLazy()
    }
}
