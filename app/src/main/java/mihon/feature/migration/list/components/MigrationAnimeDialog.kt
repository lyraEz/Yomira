package mihon.feature.migration.list.components

import yomira.glass.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MigrationAnimeDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    onMigrate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onMigrate()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(
                text = stringResource(
                    if (copy) {
                        MR.strings.migrationListScreen_copyTitle
                    } else {
                        MR.strings.migrationListScreen_migrateTitle
                    },
                ),
            )
        },
        text = {
            Text(
                text = if (skippedCount > 0) {
                    stringResource(
                        if (copy) {
                            MR.strings.migrationListScreen_copyTextWithSkipped
                        } else {
                            MR.strings.migrationListScreen_migrateTextWithSkipped
                        },
                        totalCount - skippedCount,
                        skippedCount,
                    )
                } else {
                    stringResource(
                        if (copy) {
                            MR.strings.migrationListScreen_copyText
                        } else {
                            MR.strings.migrationListScreen_migrateText
                        },
                        totalCount,
                    )
                },
            )
        },
    )
}
