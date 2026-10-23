package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlaybackError
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

// YMR -->
/**
 * Shown instead of closing the player when an episode or stream fails.
 *
 * Every action is a plain button, so the user can always retry, switch servers or leave.
 */
@Composable
fun PlaybackErrorOverlay(
    error: PlaybackError,
    hasServers: Boolean,
    hasNextEpisode: Boolean,
    onRetry: () -> Unit,
    onChooseServer: () -> Unit,
    onChooseEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(error) {
        runCatching { retryFocus.requestFocus() }
    }

    val title = stringResource(
        when (error.reason) {
            PlaybackError.Reason.HOSTERS_FAILED -> YMR.strings.player_error_hosters_failed
            PlaybackError.Reason.NO_HOSTERS -> YMR.strings.player_error_no_hosters
            PlaybackError.Reason.NO_PLAYABLE_VIDEO -> YMR.strings.player_error_no_video
            PlaybackError.Reason.STREAM_FAILED -> YMR.strings.player_error_stream
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            error.episodeName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
            error.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                )
            }
            Text(
                text = stringResource(YMR.strings.player_error_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .focusRequester(retryFocus),
                ) {
                    ActionLabel(Icons.Rounded.Refresh, stringResource(YMR.strings.player_error_retry))
                }
                if (hasServers) {
                    ErrorAction(Icons.Rounded.Dns, stringResource(YMR.strings.player_error_servers), onChooseServer)
                }
                ErrorAction(
                    Icons.AutoMirrored.Rounded.List,
                    stringResource(YMR.strings.player_error_episodes),
                    onChooseEpisode,
                )
                if (hasNextEpisode) {
                    ErrorAction(Icons.Rounded.SkipNext, stringResource(YMR.strings.player_error_next), onNextEpisode)
                }
                ErrorAction(Icons.AutoMirrored.Rounded.ExitToApp, stringResource(YMR.strings.player_error_exit), onExit)
            }
        }
    }
}

@Composable
private fun ErrorAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier,
    ) {
        ActionLabel(icon, label)
    }
}

@Composable
private fun ActionLabel(icon: ImageVector, label: String) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
    Text(text = label, color = Color.Unspecified)
}
// YMR <--
