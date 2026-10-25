package yomira.anime4k

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

/** Smart Anime4K in the player's More sheet: progress of this episode, and the switch when ready. */
@Composable
fun Anime4KSmartRow() {
    val activity = LocalContext.current as? PlayerActivity ?: return
    val prefs = remember { Anime4KPreferences() }
    if (!prefs.smartEnabled().get()) return
    val state by PlayerAnime4K.state.collectAsState()
    val jobs by EnhanceJobs.jobs.collectAsState()
    val job = jobs.firstOrNull { it.kind == JobKind.SMART && it.episodeId == state.episodeId }

    LaunchedEffect(job?.state) {
        if (job?.state == JobState.DONE) PlayerAnime4K.refresh(activity)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(YMR.strings.a4k_smart_title), style = MaterialTheme.typography.titleSmall)
            val text = when {
                state.switched -> stringResource(YMR.strings.a4k_smart_playing)
                state.ready != null -> stringResource(YMR.strings.a4k_smart_ready)
                job != null -> Anime4KNotifier.describe(activity, job)
                else -> stringResource(YMR.strings.a4k_smart_idle)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (job != null && state.ready == null && job.state == JobState.PROCESSING) {
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.ready != null && !state.switched) {
            FilledTonalButton(onClick = { PlayerAnime4K.switchToEnhanced(activity) }) {
                Text(stringResource(YMR.strings.a4k_smart_switch))
            }
        }
    }
}
