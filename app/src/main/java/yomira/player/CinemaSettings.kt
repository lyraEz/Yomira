package yomira.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import yomira.i18n.YMR
import kotlin.math.roundToInt

/** Settings → Player → Layout: options of the premium controls, with a live preview. */
@Composable
fun CinemaSettingsCard(modifier: Modifier = Modifier) {
    val prefs = remember { CinemaPreferences() }
    val enabled by prefs.enabled().collectAsState()
    val spread by prefs.centerSpread().collectAsState()
    val seek by prefs.centerSeek().collectAsState()
    val source by prefs.showSource().collectAsState()
    val remaining by prefs.remainingTime().collectAsState()
    val bar by prefs.barHeight().collectAsState()
    val pauseScreen by prefs.pauseScreen().collectAsState()
    val pauseDelay by prefs.pauseDelay().collectAsState()
    val logo by prefs.showLogo().collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CinemaPreview(enabled, spread, seek, source, remaining, bar)

        SwitchRow(
            title = stringResource(YMR.strings.cinema_enabled),
            summary = stringResource(YMR.strings.cinema_enabled_summary),
            checked = enabled,
            onChange = { prefs.enabled().set(it) },
        )
        if (enabled) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(YMR.strings.cinema_spread),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(spread * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(YMR.strings.cinema_spread_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = spread,
                    onValueChange = { prefs.centerSpread().set((it * 100).roundToInt() / 100f) },
                    valueRange = 0.10f..0.35f,
                    steps = 24,
                )
            }
            ChoiceRow(
                title = stringResource(YMR.strings.cinema_center_buttons),
                summary = stringResource(YMR.strings.cinema_center_buttons_summary),
                options = listOf(
                    stringResource(YMR.strings.cinema_center_seek),
                    stringResource(YMR.strings.cinema_center_episodes),
                ),
                selected = if (seek) 0 else 1,
                onSelect = { prefs.centerSeek().set(it == 0) },
            )
            val heights = listOf(3, 4, 6)
            ChoiceRow(
                title = stringResource(YMR.strings.cinema_bar),
                summary = stringResource(YMR.strings.cinema_bar_summary),
                options = listOf(
                    stringResource(YMR.strings.cinema_bar_thin),
                    stringResource(YMR.strings.cinema_bar_medium),
                    stringResource(YMR.strings.cinema_bar_thick),
                ),
                selected = heights.indexOf(bar).coerceAtLeast(0),
                onSelect = { prefs.barHeight().set(heights[it]) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.cinema_source),
                summary = stringResource(YMR.strings.cinema_source_summary),
                checked = source,
                onChange = { prefs.showSource().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.cinema_remaining),
                summary = stringResource(YMR.strings.cinema_remaining_summary),
                checked = remaining,
                onChange = { prefs.remainingTime().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.cinema_pause_screen),
                summary = stringResource(YMR.strings.cinema_pause_screen_summary),
                checked = pauseScreen,
                onChange = { prefs.pauseScreen().set(it) },
            )
            if (pauseScreen) {
                val delays = listOf(3, 5, 10, 15)
                ChoiceRow(
                    title = stringResource(YMR.strings.cinema_pause_delay),
                    summary = stringResource(YMR.strings.cinema_pause_delay_summary),
                    options = delays.map { "$it s" },
                    selected = delays.indexOf(pauseDelay).coerceAtLeast(0),
                    onSelect = { prefs.pauseDelay().set(delays[it]) },
                )
                SwitchRow(
                    title = stringResource(YMR.strings.cinema_logo),
                    summary = stringResource(YMR.strings.cinema_logo_summary),
                    checked = logo,
                    onChange = { prefs.showLogo().set(it) },
                )
            }
            TextButton(
                onClick = {
                    listOf(
                        prefs.centerSpread(),
                        prefs.pauseDelay(),
                        prefs.barHeight(),
                    ).forEach { it.delete() }
                    listOf(
                        prefs.centerSeek(),
                        prefs.showSource(),
                        prefs.remainingTime(),
                        prefs.pauseScreen(),
                        prefs.showLogo(),
                    ).forEach { it.delete() }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(YMR.strings.player_button_reset))
            }
        }
    }
}

/** Small mock player that follows the options as they change. */
@Composable
private fun CinemaPreview(
    cinema: Boolean,
    spread: Float,
    seek: Boolean,
    source: Boolean,
    remaining: Boolean,
    bar: Int,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF6B3B5E), Color(0xFF0E1420)))),
    ) {
        val white = Color.White
        Box(
            Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        if (!cinema) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SkipPrevious, null, tint = white, modifier = Modifier.size(26.dp))
                Icon(Icons.Filled.Pause, null, tint = white, modifier = Modifier.size(40.dp))
                Icon(Icons.Filled.SkipNext, null, tint = white, modifier = Modifier.size(26.dp))
            }
            return@BoxWithConstraints
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.ClosedCaption, null, tint = white, modifier = Modifier.size(16.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = white, modifier = Modifier.size(16.dp))
        }
        val offset = maxWidth * spread
        Box(Modifier.align(Alignment.Center).offset(y = (-26).dp)) {
            Icon(
                if (seek) Icons.Filled.Replay10 else Icons.Filled.SkipPrevious,
                null,
                tint = white,
                modifier = Modifier.offset(x = -offset).size(26.dp).align(Alignment.Center),
            )
            Icon(Icons.Filled.Pause, null, tint = white, modifier = Modifier.size(38.dp).align(Alignment.Center))
            Icon(
                if (seek) Icons.Filled.Forward10 else Icons.Filled.SkipNext,
                null,
                tint = white,
                modifier = Modifier.offset(x = offset).size(26.dp).align(Alignment.Center),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("Cyberpunk: Edgerunners", color = white, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("T1 E2 • Como um garoto", color = white.copy(alpha = 0.9f), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            if (source) Text("Via MegaEmbed", color = white.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 12.sp)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .height(bar.dp),
            ) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(white.copy(alpha = 0.24f), Offset.Zero, size, radius)
                drawRoundRect(white.copy(alpha = 0.42f), Offset.Zero, Size(size.width * 0.36f, size.height), radius)
                drawRoundRect(white, Offset.Zero, Size(size.width * 0.14f, size.height), radius)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.ClosedCaption, null, tint = white, modifier = Modifier.size(14.dp))
                    Icon(Icons.Outlined.Speed, null, tint = white, modifier = Modifier.size(14.dp))
                }
                Text(
                    if (remaining) "-21:59" else "03:33 / 25:34",
                    color = white,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
