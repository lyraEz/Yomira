package yomira.anime4k

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.i18n.stringResource
import yomira.glass.AlertDialog
import yomira.i18n.YMR

/**
 * The choice shown when downloading with Enhanced Downloads on: the original file, or an Anime4K
 * version made after the download with the chosen preset.
 */
@Composable
fun Anime4KDownloadDialog(
    episodeCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (preset: Anime4KPreset?) -> Unit,
) {
    val prefs = remember { Anime4KPreferences() }
    val context = LocalContext.current
    var enhanced by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf(Anime4KPreset.fromId(prefs.downloadPreset().get()) ?: Anime4KPreset.DEFAULT) }
    val output = remember { OutputSize.fromKey(prefs.outputSize().get()) }
    val measured = prefs.measuredSecondsPerFrame(Anime4KPreferences.benchmarkKey(preset, 1080, output))

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
                    Text(stringResource(YMR.strings.a4k_mode), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Anime4KMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preset.mode == mode,
                                onClick = { preset = preset.copy(mode = mode) },
                                label = { Text(mode.label) },
                                shape = RoundedCornerShape(percent = 50),
                            )
                        }
                    }
                    Text(stringResource(YMR.strings.a4k_quality), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Anime4KQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = preset.quality == quality,
                                onClick = { preset = preset.copy(quality = quality) },
                                label = {
                                    Text(stringResource(if (quality == Anime4KQuality.HIGH) YMR.strings.a4k_quality_high else YMR.strings.a4k_quality_fast))
                                },
                                shape = RoundedCornerShape(percent = 50),
                            )
                        }
                    }
                    Text(
                        text = modeHint(preset.mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (measured != null) {
                            stringResource(
                                YMR.strings.a4k_download_estimate,
                                Anime4KNotifier.formatDuration(context, (measured * BenchmarkResult.EPISODE_FRAMES).toLong()),
                            )
                        } else {
                            stringResource(YMR.strings.a4k_download_estimate_unknown)
                        },
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
                    if (enhanced) prefs.downloadPreset().set(preset.id)
                    onConfirm(if (enhanced) preset else null)
                },
            ) { Text(stringResource(YMR.strings.a4k_download_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(YMR.strings.a4k_cancel)) }
        },
    )
}

@Composable
internal fun modeHint(mode: Anime4KMode): String = stringResource(
    when (mode) {
        Anime4KMode.A -> YMR.strings.a4k_mode_a_hint
        Anime4KMode.B -> YMR.strings.a4k_mode_b_hint
        Anime4KMode.C -> YMR.strings.a4k_mode_c_hint
        Anime4KMode.AA -> YMR.strings.a4k_mode_aa_hint
        Anime4KMode.BB -> YMR.strings.a4k_mode_bb_hint
        Anime4KMode.CA -> YMR.strings.a4k_mode_ca_hint
    },
)

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
