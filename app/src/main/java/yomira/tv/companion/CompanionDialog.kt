package yomira.tv.companion

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import java.io.File

/**
 * Shows a QR code that opens [CompanionServer] on a phone, for as long as the dialog is visible.
 *
 * [onText] / [onUpload] are called on the main thread when the phone sends something; the dialog
 * stays open so a second attempt is possible, and the caller decides when to close it.
 */
@Composable
fun CompanionDialog(
    mode: CompanionServer.Mode,
    title: String,
    description: String,
    onDismissRequest: () -> Unit,
    onText: (String) -> Unit = {},
    onUpload: (File) -> Unit = {},
) {
    val context = LocalContext.current
    val page = CompanionServer.PageText(
        title = title,
        description = description,
        inputLabel = stringResource(
            if (mode == CompanionServer.Mode.Upload) YMR.strings.companion_file_label else YMR.strings.companion_text_label,
        ),
        submit = stringResource(YMR.strings.companion_send),
        sent = stringResource(YMR.strings.companion_sent),
        download = stringResource(YMR.strings.companion_download),
    )
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var received by remember { mutableStateOf(false) }

    DisposableEffect(mode) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val server = CompanionServer(
            mode = mode,
            page = page,
            uploadDir = File(context.cacheDir, "companion"),
            onText = { text ->
                mainHandler.post {
                    received = true
                    onText(text)
                }
            },
            onUpload = { file ->
                mainHandler.post {
                    received = true
                    onUpload(file)
                }
            },
        )
        val host = LanAddress.find()
        if (host == null) {
            error = context.getString(android.R.string.unknownName)
        } else {
            try {
                server.start(SOCKET_TIMEOUT_MS, true)
                url = server.url(host)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Could not start companion server" }
                error = e.message
            }
        }
        onDispose {
            runCatching { server.stop() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = description, textAlign = TextAlign.Center)
                val currentUrl = url
                when {
                    error != null -> Text(
                        text = stringResource(YMR.strings.companion_no_network),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    currentUrl != null -> {
                        val qr = remember(currentUrl) { QrCode.bitmap(currentUrl, QR_SIZE_PX).asImageBitmap() }
                        Image(bitmap = qr, contentDescription = currentUrl, modifier = Modifier.size(220.dp))
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(
                                if (received) YMR.strings.companion_received else YMR.strings.companion_waiting,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
    )
}

private const val SOCKET_TIMEOUT_MS = 30_000
private const val QR_SIZE_PX = 512
