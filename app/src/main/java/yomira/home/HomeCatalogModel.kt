package yomira.home

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.toDomainAnime
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.metadata.EpisodeMetadata
import yomira.ui.ContinueItem
import yomira.ui.loadContinueItem
import java.util.concurrent.TimeUnit

@Immutable
data class NewEpisodeItem(val anime: Anime, val episodeNumber: Double, val episodeName: String)

@Immutable
data class SourceRow(val sourceId: Long, val sourceName: String, val latest: Boolean, val items: List<Anime>)

@Immutable
data class HomeCatalogState(
    val hero: List<Anime> = emptyList(),
    val continueItems: List<ContinueItem> = emptyList(),
    val newEpisodes: List<NewEpisodeItem> = emptyList(),
    val recentlyAdded: List<Anime> = emptyList(),
    val sourceRows: List<SourceRow> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Data of the "Início" tab: highlights and rows from the library, the history and the updates,
 * plus popular and latest lists of the pinned (or last used) sources.
 */
@OptIn(FlowPreview::class)
class HomeCatalogScreenModel(
    private val getLibraryAnime: tachiyomi.domain.anime.interactor.GetLibraryAnime = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
) : StateScreenModel<HomeCatalogState>(HomeCatalogState()) {

    private val warmed = mutableSetOf<Long>()

    init {
        screenModelScope.launch(Dispatchers.IO) {
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(NEW_EPISODES_DAYS)
            combine(
                getLibraryAnime.subscribe(),
                getHistory.subscribe(""),
                getUpdates.subscribe(seen = false, after = since),
            ) { library, history, updates -> Triple(library, history, updates) }
                .debounce(REFRESH_DEBOUNCE_MS)
                .collect { (library, history, updates) ->
                    val favorites = library.filter { it.anime.favorite }
                    val hero = heroOf(favorites)
                    val continues = history.distinctBy { it.animeId }
                        .take(MAX_CONTINUE)
                        .mapNotNull { loadContinueItem(it.animeId, it.episodeId) }
                    val fresh = updates.distinctBy { it.animeId }.take(MAX_ROW).mapNotNull { update ->
                        favorites.firstOrNull { it.anime.id == update.animeId }?.anime?.let {
                            NewEpisodeItem(it, update.episodeNumber, update.episodeName)
                        }
                    }
                    mutableState.update {
                        it.copy(
                            hero = hero,
                            continueItems = continues,
                            newEpisodes = fresh,
                            recentlyAdded = favorites.sortedByDescending { item -> item.anime.dateAdded }
                                .take(MAX_ROW).map { item -> item.anime },
                            loaded = true,
                        )
                    }
                    warm(hero)
                }
        }
        loadSources()
    }

    /** Highlights: the anime watched most recently, then the latest added. */
    private fun heroOf(favorites: List<LibraryAnime>): List<Anime> =
        (
            favorites.filter { it.lastSeen > 0 }.sortedByDescending { it.lastSeen } +
                favorites.sortedByDescending { it.anime.dateAdded }
            )
            .map { it.anime }
            .distinctBy { it.id }
            .take(MAX_HERO)

    /** Loads the TMDB art (key art, logo) of the highlights once per session. */
    private fun warm(anime: List<Anime>) {
        anime.filter { warmed.add(it.id) }.forEach { item ->
            screenModelScope.launch(Dispatchers.IO) { EpisodeMetadata.ensure(item.id, item.title, emptyList()) }
        }
    }

    fun refreshSources() = loadSources()

    private fun loadSources() {
        screenModelScope.launch(Dispatchers.IO) {
            val pinned = sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }
            val lastUsed = sourcePreferences.lastUsedSource().get()
            val sources = (pinned + lastUsed).distinct()
                .mapNotNull { sourceManager.get(it) as? CatalogueSource }
                .take(MAX_SOURCES)
            val rows = sources.map { source ->
                async {
                    listOfNotNull(
                        row(source, latest = false),
                        if (source.supportsLatest) row(source, latest = true) else null,
                    )
                }
            }.awaitAll().flatten()
            mutableState.update { it.copy(sourceRows = rows) }
        }
    }

    private suspend fun row(source: CatalogueSource, latest: Boolean): SourceRow? = withContext(Dispatchers.IO) {
        try {
            val page = if (latest) source.getLatestUpdates(1) else source.getPopularAnime(1)
            val items = page.animes.take(MAX_ROW).map { networkToLocalAnime.getLocal(it.toDomainAnime(source.id)) }
            SourceRow(source.id, source.name, latest, items).takeIf { items.isNotEmpty() }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Home: could not load ${source.name}" }
            null
        }
    }

    private companion object {
        const val MAX_HERO = 8
        const val MAX_CONTINUE = 12
        const val MAX_ROW = 20
        const val MAX_SOURCES = 3
        const val NEW_EPISODES_DAYS = 14L
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
