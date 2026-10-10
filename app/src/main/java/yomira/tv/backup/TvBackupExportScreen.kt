package yomira.tv.backup

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import yomira.tv.companion.CompanionDialog
import yomira.tv.companion.CompanionServer
import java.io.File

/**
 * Follows a backup being written to the app folder and then offers ways to take it off the TV:
 * download it on a phone over the local network, or pull it with adb.
 */
class TvBackupExportScreen(private val path: String) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val file = remember { File(path) }
        var running by remember { mutableStateOf(true) }
        var size by remember { mutableStateOf(0L) }
        var showCompanion by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // The job runs in WorkManager; poll until it is done and the file has content.
            var idlePolls = 0
            while (true) {
                delay(POLL_MS)
                size = file.length()
                if (withIOContext { BackupCreateJob.isManualJobRunning(context) }) {
                    idlePolls = 0
                    continue
                }
                // Not running: either finished, or still queued in WorkManager.
                if (size > 0 || ++idlePolls >= MAX_IDLE_POLLS) {
                    running = false
                    break
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    running -> {
                        CircularProgressIndicator()
                        Text(stringResource(MR.strings.creating_backup))
                    }
                    size > 0 -> {
                        Text(
                            text = stringResource(
                                YMR.strings.backup_saved,
                                Formatter.formatShortFileSize(context, size),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(file.absolutePath, style = MaterialTheme.typography.bodySmall)
                        ListItem(
                            modifier = Modifier.clickable { showCompanion = true },
                            leadingContent = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                            headlineContent = { Text(stringResource(YMR.strings.backup_send_to_phone)) },
                            supportingContent = { Text(stringResource(YMR.strings.backup_send_to_phone_summary)) },
                        )
                        Text(stringResource(YMR.strings.backup_adb_hint), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "adb pull \"${file.absolutePath}\"",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            text = stringResource(YMR.strings.backup_keep_note, BuildConfig.APPLICATION_ID),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> Text(
                        text = stringResource(MR.strings.creating_backup_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (showCompanion) {
            CompanionDialog(
                mode = CompanionServer.Mode.Download(file, file.name),
                title = stringResource(YMR.strings.backup_send_to_phone),
                description = stringResource(YMR.strings.backup_send_to_phone_description),
                onDismissRequest = { showCompanion = false },
            )
        }
    }

    private companion object {
        const val POLL_MS = 700L
        const val MAX_IDLE_POLLS = 15
    }
}
