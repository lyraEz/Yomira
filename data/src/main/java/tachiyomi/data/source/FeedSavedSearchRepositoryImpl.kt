package tachiyomi.data.source

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class FeedSavedSearchRepositoryImpl(
    private val handler: DatabaseHandler,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(categoryId: Long): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectGlobalByCategory(categoryId, FeedSavedSearchMapper::map) }
    }

    override fun getGlobalAsFlow(categoryId: Long): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectGlobalByCategory(categoryId, FeedSavedSearchMapper::map) }
    }

    override suspend fun getGlobalFeedSavedSearch(categoryId: Long): List<SavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectGlobalFeedSavedSearchByCategory(categoryId, SavedSearchMapper::map) }
    }

    override suspend fun countGlobal(categoryId: Long): Long {
        return handler.awaitOne { feed_saved_searchQueries.countGlobalByCategory(categoryId) }
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return handler.awaitList { feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map) }
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return handler.subscribeToList { feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map) }
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return handler.awaitList {
            feed_saved_searchQueries.selectSourceFeedSavedSearch(sourceId, SavedSearchMapper::map)
        }
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return handler.awaitOne { feed_saved_searchQueries.countSourceFeedSavedSearch(sourceId) }
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        handler.await { feed_saved_searchQueries.deleteById(feedSavedSearchId) }
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        return handler.awaitOneExecutable(true) {
            feed_saved_searchQueries.insert(
                sourceId = feedSavedSearch.source,
                savedSearch = feedSavedSearch.savedSearch,
                global = feedSavedSearch.global,
                searchType = feedSavedSearch.type.toLong(),
                category = feedSavedSearch.category,
                lastTopUrl = feedSavedSearch.lastTopUrl,
            )
            feed_saved_searchQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        return handler.await(true) {
            feedSavedSearch.forEach {
                feed_saved_searchQueries.insert(
                    sourceId = it.source,
                    savedSearch = it.savedSearch,
                    global = it.global,
                    searchType = it.type.toLong(),
                    category = it.category,
                    lastTopUrl = it.lastTopUrl,
                )
            }
        }
    }

    override suspend fun updatePartial(update: FeedSavedSearchUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: FeedSavedSearchUpdate) {
        feed_saved_searchQueries.update(
            source = update.source,
            delete_saved_search = update.deleteSavedSearch,
            saved_search = update.savedSearch,
            global = update.global,
            feed_order = update.feedOrder,
            search_type = update.searchType,
            category = update.category,
            last_top_url = update.lastTopUrl,
            id = update.id,
        )
    }
}
