package yomira.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

/** Shows an extension diagnostic report with actions to copy it and, optionally, to uninstall. */
@Composable
fun ExtensionDiagnosticsDialog(
    title: String,
    summary: String?,
    buildReport: suspend () -> String,
    onUninstall: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var report by remember(title) { mutableStateOf<String?>(null) }
    LaunchedEffect(title) {
        report = runCatching { buildReport() }.getOrElse { it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                summary?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                Text(
                    text = report ?: "…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    report?.let { context.copyToClipboard(context.stringResource(YMR.strings.ext_diagnostics), it) }
                },
            ) {
                Text(text = stringResource(YMR.strings.ext_diagnostics_copy))
            }
        },
        dismissButton = {
            Row {
                if (onUninstall != null) {
                    TextButton(onClick = onUninstall) {
                        Text(text = stringResource(MR.strings.ext_uninstall))
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_close))
                }
            }
        },
    )
}
