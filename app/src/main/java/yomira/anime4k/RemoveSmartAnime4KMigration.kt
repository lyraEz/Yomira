package yomira.anime4k

import android.app.Application
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Smart Anime4K was removed in 1.13.0. Frees the space its cache of prepared episodes used and
 * forgets its jobs and settings, so nothing of it keeps running or taking storage.
 */
class RemoveSmartAnime4KMigration : Migration {
    override val version: Float = 11300f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        runCatching { EnhanceJobs.removeSmartLeftovers(context) }
        migrationContext.get<PreferenceStore>()?.let { store ->
            Anime4KPreferences.REMOVED_SMART_KEYS.forEach { store.getString(it, "").delete() }
        }
        return true
    }
}
