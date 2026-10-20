package yomira.player

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import java.util.Date
import kotlin.math.roundToInt
import tachiyomi.presentation.core.util.collectAsState as collectPref

private fun formatTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun ToolButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SectionTitle(title: String, summary: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A-B loop, frame by frame, rotation, stop at the end, speed per anime. */
@Composable
fun ToolsCard() {
    val activity = LocalContext.current as? PlayerActivity
    val mpv = activity?.viewModel?.mpv
    val prefs = remember { PlayerToolsPreferences() }
    val loopA by PlayerTools.loopA.collectAsState()
    val loopB by PlayerTools.loopB.collectAsState()
    val rotation by PlayerTools.rotation.collectAsState()
    val stopAtEnd by PlayerTools.stopAfterEpisode.collectAsState()
    val speedPerAnime by prefs.speedPerAnime().collectPref()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Handyman, null)
                Text(stringResource(YMR.strings.tools_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val loopSummary = when {
                loopA != null && loopB != null ->
                    stringResource(YMR.strings.tools_loop_active, formatTime(loopA!!), formatTime(loopB!!))
                loopA != null -> stringResource(YMR.strings.tools_loop_from, formatTime(loopA!!))
                else -> stringResource(YMR.strings.tools_loop_summary)
            }
            SectionTitle(stringResource(YMR.strings.tools_loop), loopSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(stringResource(YMR.strings.tools_loop_a), { PlayerTools.markA(mpv) }, Modifier.weight(1f))
                ToolButton(stringResource(YMR.strings.tools_loop_b), { PlayerTools.markB(mpv) }, Modifier.weight(1f))
                ToolButton(
                    stringResource(YMR.strings.tools_loop_clear),
                    { PlayerTools.clearLoop(mpv) },
                    Modifier.weight(1f),
                    enabled = loopA != null || loopB != null,
                )
            }

            SectionTitle(stringResource(YMR.strings.tools_frames), stringResource(YMR.strings.tools_frames_summary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(stringResource(YMR.strings.tools_frame_back), { PlayerTools.frameStep(mpv, false) }, Modifier.weight(1f))
                ToolButton(stringResource(YMR.strings.tools_frame_next), { PlayerTools.frameStep(mpv, true) }, Modifier.weight(1f))
            }

            val angles = listOf(0, 90, 180, 270)
            ChoiceRow(
                title = stringResource(YMR.strings.tools_rotate),
                summary = stringResource(YMR.strings.tools_rotate_summary),
                options = angles.map { "$it°" },
                selected = angles.indexOf(rotation).coerceAtLeast(0),
                onSelect = { PlayerTools.rotate(mpv, angles[it]) },
            )

            SwitchRow(
                title = stringResource(YMR.strings.tools_stop_at_end),
                summary = stringResource(YMR.strings.tools_stop_at_end_summary),
                checked = stopAtEnd,
                onChange = { PlayerTools.setStopAfterEpisode(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.tools_speed_per_anime),
                summary = stringResource(YMR.strings.tools_speed_per_anime_summary),
                checked = speedPerAnime,
                onChange = {
                    prefs.speedPerAnime().set(it)
                    if (it) PlayerTools.saveSpeed(mpv, activity?.viewModel?.currentAnime?.value?.id, prefs)
                },
            )
        }
    }
}

/** Mono, balance, headphone crossfeed and treble. */
@Composable
fun SoundCard() {
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val prefs = remember { PlayerToolsPreferences() }
    val mono by prefs.audioMono().collectPref()
    val spatial by prefs.audioSpatial().collectPref()
    val treble by prefs.audioTreble().collectPref()
    val balance by prefs.audioBalance().collectPref()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Headphones, null)
                Text(stringResource(YMR.strings.sound_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                title = stringResource(YMR.strings.sound_mono),
                summary = stringResource(YMR.strings.sound_mono_summary),
                checked = mono,
                onChange = {
                    prefs.audioMono().set(it)
                    PlayerTools.applySound(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.sound_spatial),
                summary = stringResource(YMR.strings.sound_spatial_summary),
                checked = spatial,
                onChange = {
                    prefs.audioSpatial().set(it)
                    PlayerTools.applySound(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.sound_treble),
                summary = stringResource(YMR.strings.sound_treble_summary),
                checked = treble,
                onChange = {
                    prefs.audioTreble().set(it)
                    PlayerTools.applySound(mpv, prefs)
                },
            )

            var draft by remember(balance) { mutableFloatStateOf(balance.toFloat()) }
            val value = draft.roundToInt()
            val label = when {
                value == 0 -> stringResource(YMR.strings.sound_balance_center)
                value < 0 -> stringResource(YMR.strings.sound_balance_left, -value)
                else -> stringResource(YMR.strings.sound_balance_right, value)
            }
            SectionTitle(stringResource(YMR.strings.sound_balance), label)
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    prefs.audioBalance().set(draft.roundToInt())
                    PlayerTools.applySound(mpv, prefs)
                },
                valueRange = -100f..100f,
                steps = 19,
            )
        }
    }
}

/** What shows on screen: clock, battery, end time and the warm filter. */
@Composable
fun DisplayCard() {
    val prefs = remember { PlayerToolsPreferences() }
    val clock by prefs.infoClock().collectPref()
    val battery by prefs.infoBattery().collectPref()
    val endTime by prefs.infoEndTime().collectPref()
    val warm by prefs.blueLight().collectPref()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Visibility, null)
                Text(stringResource(YMR.strings.display_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                title = stringResource(YMR.strings.display_clock),
                summary = stringResource(YMR.strings.display_clock_summary),
                checked = clock,
                onChange = { prefs.infoClock().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.display_battery),
                summary = stringResource(YMR.strings.display_battery_summary),
                checked = battery,
                onChange = { prefs.infoBattery().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.display_end_time),
                summary = stringResource(YMR.strings.display_end_time_summary),
                checked = endTime,
                onChange = { prefs.infoEndTime().set(it) },
            )
            val levels = listOf(0, 35, 65, 100)
            ChoiceRow(
                title = stringResource(YMR.strings.display_warm),
                summary = stringResource(YMR.strings.display_warm_summary),
                options = listOf(
                    stringResource(YMR.strings.smooth_off),
                    stringResource(YMR.strings.display_warm_low),
                    stringResource(YMR.strings.display_warm_medium),
                    stringResource(YMR.strings.display_warm_high),
                ),
                selected = levels.indexOfFirst { it >= warm }.coerceAtLeast(0),
                onSelect = { prefs.blueLight().set(levels[it]) },
            )
        }
    }
}

/** Warm tint drawn over the video (below the controls); nothing at all when off. */
@Composable
fun WarmFilterOverlay() {
    val prefs = remember { PlayerToolsPreferences() }
    val level by prefs.blueLight().collectPref()
    if (level <= 0) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFF8A2B).copy(alpha = 0.22f * level / 100f)),
    )
}

private data class InfoSnapshot(val clock: String, val battery: Int, val charging: Boolean, val endsAt: String?)

private fun readBattery(context: Context): Pair<Int, Boolean> {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return level to charging
}

/**
 * Small line under the episode title: clock, battery and when the episode ends at the current
 * speed. Refreshed every 15 seconds while visible, so it never recomposes with the position.
 */
@Composable
fun PlayerInfoLine(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { PlayerToolsPreferences() }
    val showClock by prefs.infoClock().collectPref()
    val showBattery by prefs.infoBattery().collectPref()
    val showEnd by prefs.infoEndTime().collectPref()
    if (!showClock && !showBattery && !showEnd) return

    var info by remember { mutableStateOf<InfoSnapshot?>(null) }
    LaunchedEffect(Unit) {
        val timeFormat = DateFormat.getTimeFormat(context)
        while (true) {
            val now = System.currentTimeMillis()
            val (level, charging) = readBattery(context)
            val pos = viewModel.pos.value
            val duration = viewModel.duration.value
            val speed = (viewModel.playbackSpeed.value ?: 1f).coerceAtLeast(0.1f)
            val endsAt = if (pos != null && duration != null && duration > 0 && duration >= pos) {
                timeFormat.format(Date(now + ((duration - pos) / speed * 1000).toLong()))
            } else {
                null
            }
            info = InfoSnapshot(timeFormat.format(Date(now)), level, charging, endsAt)
            delay(15_000)
        }
    }
    val snapshot = info ?: return
    val color = Color.White.copy(alpha = 0.72f)
    val style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
    Row(
        modifier = modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showClock) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Outlined.Schedule, null, tint = color, modifier = Modifier.size(13.dp))
                Text(snapshot.clock, color = color, style = style)
            }
        }
        if (showBattery && snapshot.battery >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    if (snapshot.charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
                    null,
                    tint = if (!snapshot.charging && snapshot.battery <= 15) Color(0xFFFF6B6B) else color,
                    modifier = Modifier.size(13.dp),
                )
                Text("${snapshot.battery}%", color = color, style = style)
            }
        }
        if (showEnd && snapshot.endsAt != null) {
            Text(stringResource(YMR.strings.display_ends_at, snapshot.endsAt), color = color, style = style)
        }
    }
}
