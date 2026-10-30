package mihon.core.migration.migrations

import mihon.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupLibraryUpdateMigration(),
        SetupSyncDataMigration(),
        SetupAppUpdateMigration(),
        yomira.anime4k.RemoveSmartAnime4KMigration(),
        yomira.anime4k.EnhanceSpeedMigration(),
    )
