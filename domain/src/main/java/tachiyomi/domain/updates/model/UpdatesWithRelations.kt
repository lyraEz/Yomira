package tachiyomi.domain.updates.model

import tachiyomi.domain.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.anime.model.AnimeCover
import uy.kohesive.injekt.injectLazy

data class UpdatesWithRelations(
    val animeId: Long,
    val ogAnimeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val episodeNumber: Double,
    val scanlator: String?,
    val seen: Boolean,
    val bookmark: Boolean,
    val fillermark: Boolean,
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: AnimeCover,
) {
    val animeTitle: String = getCustomAnimeInfo.get(animeId)?.title ?: ogAnimeTitle

    companion object {
        private val getCustomAnimeInfo: GetCustomAnimeInfo by injectLazy()
    }
}
