package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SavedSearch

interface FeedSavedSearchRepository {

    suspend fun getGlobal(categoryId: Long = 1): List<FeedSavedSearch>

    fun getGlobalAsFlow(categoryId: Long = 1): Flow<List<FeedSavedSearch>>

    suspend fun getGlobalFeedSavedSearch(categoryId: Long = 1): List<SavedSearch>

    suspend fun countGlobal(categoryId: Long = 1): Long

    suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>>

    suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch>

    suspend fun countBySourceId(sourceId: Long): Long

    suspend fun delete(feedSavedSearchId: Long)

    suspend fun insert(feedSavedSearch: FeedSavedSearch): Long?

    suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>)

    suspend fun updatePartial(update: FeedSavedSearchUpdate)

    suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>)
}
