package yomira.tv.backup

import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import yomira.tv.TvSettings
import yomira.tv.companion.CompanionDialog
import yomira.tv.companion.CompanionServer

/**
 * Restore without depending on the system file picker: lists the backups the app can read and
 * lets a phone upload one over the local network.
 */
class TvRestoreBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        var backups by remember { mutableStateOf<List<BackupFiles.Found>?>(null) }
        var showCompanion by remember { mutableStateOf(false) }
        val hasPicker = remember { TvSettings.hasDocumentPicker(context) }

        LaunchedEffect(Unit) {
            backups = withIOContext { BackupFiles.list(context) }
        }

        fun restore(uri: Uri) {
            if (BackupRestoreJob.isRunning(context)) {
                context.toast(MR.strings.restore_in_progress)
            } else {
                navigator.push(RestoreBackupScreen(uri.toString()))
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(::restore)
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_restore_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { showCompanion = true },
                        leadingContent = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                        headlineContent = { Text(stringResource(YMR.strings.backup_receive_from_phone)) },
                        supportingContent = { Text(stringResource(YMR.strings.backup_receive_from_phone_summary)) },
                    )
                }
                if (hasPicker) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable {
                                try {
                                    picker.launch("*/*")
                                } catch (_: ActivityNotFoundException) {
                                    context.toast(MR.strings.file_picker_error)
                                }
                            },
                            leadingContent = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            headlineContent = { Text(stringResource(YMR.strings.backup_system_picker)) },
                        )
                    }
                }
                item { HorizontalDivider() }
                item {
                    Text(
                        text = stringResource(YMR.strings.backup_found_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                val list = backups
                when {
                    list == null -> item {
                        Text(stringResource(MR.strings.loading), modifier = Modifier.padding(16.dp))
                    }
                    list.isEmpty() -> item {
                        Text(
                            text = stringResource(
                                YMR.strings.backup_none_found,
                                BackupFiles.appBackupDir(context).absolutePath,
                            ),
                            modifier = Modifier.padding(PaddingValues(horizontal = 16.dp)),
                        )
                    }
                    else -> items(list, key = { it.uri.toString() }) { backup ->
                        ListItem(
                            modifier = Modifier.clickable { restore(backup.uri) },
                            leadingContent = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                            headlineContent = { Text(backup.name) },
                            supportingContent = {
                                val where = stringResource(
                                    when (backup.location) {
                                        BackupFiles.Location.APP_FOLDER -> YMR.strings.backup_location_app
                                        BackupFiles.Location.STORAGE_FOLDER -> YMR.strings.backup_location_storage
                                        BackupFiles.Location.DOWNLOADS -> YMR.strings.backup_location_downloads
                                        BackupFiles.Location.RECEIVED -> YMR.strings.backup_location_received
                                    },
                                )
                                val size = Formatter.formatShortFileSize(context, backup.sizeBytes)
                                val date = DateUtils.getRelativeTimeSpanString(backup.modified)
                                Text("$where · $size · $date")
                            },
                        )
                    }
                }
            }
        }

        if (showCompanion) {
            CompanionDialog(
                mode = CompanionServer.Mode.Upload,
                title = stringResource(YMR.strings.backup_receive_from_phone),
                description = stringResource(YMR.strings.backup_receive_from_phone_description),
                onDismissRequest = { showCompanion = false },
                onUpload = { file ->
                    showCompanion = false
                    restore(Uri.fromFile(file))
                },
            )
        }
    }
}
