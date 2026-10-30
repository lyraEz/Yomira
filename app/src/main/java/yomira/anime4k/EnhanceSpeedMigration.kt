package yomira.anime4k

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * 1.14.0 estimates every option from one measured speed per kind of shader (EnhanceSpeed), so the
 * per-preset timings saved before are dropped.
 */
class EnhanceSpeedMigration : Migration {
    override val version: Float = 11391f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        migrationContext.get<PreferenceStore>()?.getString(Anime4KPreferences.REMOVED_BENCHMARK_KEY, "")?.delete()
        return true
    }
}
