package yomira.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import yomira.i18n.YMR

/** "Smoothness" card in the player's video panel. */
@Composable
fun SmoothnessCard() {
    val prefs = remember { SmoothnessPreferences() }
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val match by prefs.matchFrameRate().collectAsState()
    val smooth by prefs.smoothMotion().collectAsState()
    val quality by prefs.imageQuality().collectAsState()
    val buffer by prefs.bigBuffer().collectAsState()
    val glassRate by prefs.videoGlassRate().collectAsState()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Speed, null)
                Text(stringResource(YMR.strings.smooth_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                title = stringResource(YMR.strings.smooth_match),
                summary = stringResource(YMR.strings.smooth_match_summary),
                checked = match,
                onChange = { prefs.matchFrameRate().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_motion),
                summary = stringResource(YMR.strings.smooth_motion_summary),
                checked = smooth,
                onChange = {
                    prefs.smoothMotion().set(it)
                    Smoothness.setSmoothMotion(mpv, it)
                },
            )
            ChoiceRow(
                title = stringResource(YMR.strings.smooth_quality),
                summary = stringResource(
                    when (Smoothness.resolve(context, quality)) {
                        ImageQuality.FAST, ImageQuality.AUTO -> YMR.strings.smooth_quality_fast_summary
                        ImageQuality.BALANCED -> YMR.strings.smooth_quality_balanced_summary
                        ImageQuality.HIGH -> YMR.strings.smooth_quality_high_summary
                    },
                ),
                options = listOf(
                    stringResource(YMR.strings.smooth_auto),
                    stringResource(YMR.strings.smooth_quality_fast),
                    stringResource(YMR.strings.smooth_quality_balanced),
                    stringResource(YMR.strings.smooth_quality_high),
                ),
                selected = quality.ordinal,
                onSelect = {
                    val value = ImageQuality.entries[it]
                    prefs.imageQuality().set(value)
                    Smoothness.setImageQuality(context, mpv, value)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_buffer),
                summary = stringResource(YMR.strings.smooth_buffer_summary),
                checked = buffer,
                onChange = { prefs.bigBuffer().set(it) },
            )
            val rates = listOf(-1, 0, 30, 15)
            ChoiceRow(
                title = stringResource(YMR.strings.smooth_glass_rate),
                summary = stringResource(YMR.strings.smooth_glass_rate_summary),
                options = listOf(
                    stringResource(YMR.strings.smooth_auto),
                    stringResource(YMR.strings.smooth_glass_rate_live),
                    "30 fps",
                    "15 fps",
                ),
                selected = rates.indexOf(glassRate).coerceAtLeast(0),
                onSelect = { prefs.videoGlassRate().set(rates[it]) },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    summary: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    shape = RoundedCornerShape(percent = 50),
                )
            }
        }
    }
}
