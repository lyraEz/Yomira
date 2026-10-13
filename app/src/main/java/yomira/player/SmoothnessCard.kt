package yomira.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
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
