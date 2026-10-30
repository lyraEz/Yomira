package yomira.anime4k

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.core.i18n.stringResource
import yomira.glass.AlertDialog
import yomira.i18n.YMR

/**
 * The choice shown when downloading with Enhanced Downloads on: the original file, or a version made
 * after the download with Anime4K or light AI, with how long that takes on this phone.
 */
@Composable
fun Anime4KDownloadDialog(
    episodeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (preset: Enhancer?) -> Unit,
) {
    val prefs = remember { Anime4KPreferences() }
    val context = LocalContext.current
    var enhanced by remember { mutableStateOf(true) }
    var enhancer by remember { mutableStateOf(Enhancer.fromId(prefs.downloadPreset().get()) ?: Enhancer.DEFAULT) }
    val output = remember { OutputSize.fromKey(prefs.outputSize().get()) }
    var measuring by remember { mutableStateOf(false) }
    var measured by remember { mutableIntStateOf(0) }

    // The first time, measure the phone (a second or two) so every option shows its time.
    LaunchedEffect(Unit) {
        if (!EnhanceSpeed.isMeasured(prefs) && Anime4KNative.available) {
            measuring = true
            runCatching { EnhanceSpeed.measure(context, prefs) }
                .onFailure { logcat(LogPriority.WARN, it) { "Anime4K: could not measure the GPU" } }
            measuring = false
            measured++
        }
    }
    val estimate by produceState<Pair<Long?, Long?>?>(null, enhancer, measured) {
        value = withContext(Dispatchers.IO) {
            val size = output.forEnhancer(enhancer)
            EnhanceSpeed.episodeSeconds(context, prefs, enhancer, 1280, 720, size) to
                EnhanceSpeed.episodeSeconds(context, prefs, enhancer, 1920, 1080, size)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoAwesome, null) },
        title = { Text(stringResource(YMR.strings.a4k_download_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChoiceCard(
                    icon = Icons.Outlined.Download,
                    title = stringResource(YMR.strings.a4k_download_original),
                    summary = stringResource(YMR.strings.a4k_download_original_summary),
                    selected = !enhanced,
                    onClick = { enhanced = false },
                )
                ChoiceCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(YMR.strings.a4k_download_enhanced),
                    summary = stringResource(YMR.strings.a4k_download_enhanced_summary),
                    selected = enhanced,
                    onClick = { enhanced = true },
                )
                if (enhanced) {
                    EnhancerPicker(enhancer = enhancer, onChange = { enhancer = it })
                    Text(
                        text = estimateText(context, measuring, estimate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (episodeCount > 1) {
                        Text(
                            stringResource(YMR.strings.a4k_download_many, episodeCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (enhanced) prefs.downloadPreset().set(enhancer.id)
                    onConfirm(if (enhanced) enhancer else null)
                },
            ) { Text(stringResource(YMR.strings.a4k_download_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(YMR.strings.a4k_cancel)) }
        },
    )
}

/** "About 25 min per 720p episode and 55 min per 1080p episode on this phone". */
private fun estimateText(context: Context, measuring: Boolean, estimate: Pair<Long?, Long?>?): String {
    val (hd, fullHd) = estimate ?: (null to null)
    return when {
        measuring -> context.stringResource(YMR.strings.a4k_estimate_measuring)
        hd == null || fullHd == null -> context.stringResource(YMR.strings.a4k_download_estimate_unknown)
        fullHd == 0L -> context.stringResource(YMR.strings.a4k_estimate_720, Anime4KNotifier.formatDuration(context, hd))
        else -> context.stringResource(
            YMR.strings.a4k_estimate_both,
            Anime4KNotifier.formatDuration(context, hd),
            Anime4KNotifier.formatDuration(context, fullHd),
        )
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
