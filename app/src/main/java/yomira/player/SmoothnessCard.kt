package yomira.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import yomira.i18n.YMR

/** "Smoothness" card in the player's video panel: frame pacing, decoding, seeking, glass cost. */
@Composable
fun SmoothnessCard() {
    val prefs = remember { SmoothnessPreferences() }
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val match by prefs.matchFrameRate().collectAsState()
    val smooth by prefs.smoothMotion().collectAsState()
    val buffer by prefs.bigBuffer().collectAsState()
    val glassRate by prefs.videoGlassRate().collectAsState()
    val decoderDrop by prefs.decoderFrameDrop().collectAsState()
    val fastDecode by prefs.fastDecode().collectAsState()
    val fastSeek by prefs.fastSeek().collectAsState()
    val stats by prefs.frameStats().collectAsState()
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
            SwitchRow(
                title = stringResource(YMR.strings.smooth_decoder_drop),
                summary = stringResource(YMR.strings.smooth_decoder_drop_summary),
                checked = decoderDrop,
                onChange = {
                    prefs.decoderFrameDrop().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_fast_decode),
                summary = stringResource(YMR.strings.smooth_fast_decode_summary),
                checked = fastDecode,
                onChange = {
                    prefs.fastDecode().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_fast_seek),
                summary = stringResource(YMR.strings.smooth_fast_seek_summary),
                checked = fastSeek,
                onChange = {
                    prefs.fastSeek().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_buffer),
                summary = stringResource(YMR.strings.smooth_buffer_summary),
                checked = buffer,
                onChange = { prefs.bigBuffer().set(it) },
            )
            val rates = listOf(-1, 0, 30, 15, VideoGlassOff)
            ChoiceRow(
                title = stringResource(YMR.strings.smooth_glass_rate),
                summary = stringResource(YMR.strings.smooth_glass_rate_summary),
                options = listOf(
                    stringResource(YMR.strings.smooth_auto),
                    "60 fps",
                    "30 fps",
                    "15 fps",
                    stringResource(YMR.strings.smooth_off),
                ),
                selected = rates.indexOf(glassRate).coerceAtLeast(0),
                onSelect = { prefs.videoGlassRate().set(rates[it]) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.smooth_stats),
                summary = stringResource(YMR.strings.smooth_stats_summary),
                checked = stats,
                onChange = { prefs.frameStats().set(it) },
            )
        }
    }
}

/** "Quality" card, separate from smoothness: scaling, halos, gradients, HDR and interlacing. */
@Composable
fun QualityCard() {
    val prefs = remember { SmoothnessPreferences() }
    val context = LocalContext.current
    val mpv = (context as? PlayerActivity)?.viewModel?.mpv
    val quality by prefs.imageQuality().collectAsState()
    val antiRing by prefs.antiRing().collectAsState()
    val dither by prefs.dither().collectAsState()
    val toneMapping by prefs.toneMapping().collectAsState()
    val deinterlace by prefs.deinterlace().collectAsState()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.HighQuality, null)
                Text(stringResource(YMR.strings.quality_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                title = stringResource(YMR.strings.quality_antiring),
                summary = stringResource(YMR.strings.quality_antiring_summary),
                checked = antiRing,
                onChange = {
                    prefs.antiRing().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.quality_dither),
                summary = stringResource(YMR.strings.quality_dither_summary),
                checked = dither,
                onChange = {
                    prefs.dither().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.quality_deinterlace),
                summary = stringResource(YMR.strings.quality_deinterlace_summary),
                checked = deinterlace,
                onChange = {
                    prefs.deinterlace().set(it)
                    Smoothness.applyLive(mpv, prefs)
                },
            )
            ChoiceRow(
                title = stringResource(YMR.strings.quality_tone_mapping),
                summary = stringResource(YMR.strings.quality_tone_mapping_summary),
                options = listOf(stringResource(YMR.strings.smooth_auto), "BT.2390", "Hable", "Mobius"),
                selected = toneMapping.ordinal,
                onSelect = {
                    prefs.toneMapping().set(ToneMapping.entries[it])
                    Smoothness.applyLive(mpv, prefs)
                },
            )
        }
    }
}

/**
 * Live playback statistics in the top corner, shown only when enabled in the smoothness card:
 * dropped/late frames, source vs display rate and cadence, resolution and decoder, A/V sync,
 * buffer and network speed, scaler and glass, battery, temperature and clock. Reads a handful of
 * mpv properties once per second.
 */
@Composable
fun FrameStatsOverlay(mpv: () -> MPV?) {
    val prefs = remember { SmoothnessPreferences() }
    val enabled by prefs.frameStats().collectAsState()
    if (!enabled) return
    val context = LocalContext.current
    var lines by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        while (true) {
            val m = mpv()
            if (m != null) {
                fun int(name: String) = runCatching { m.getPropertyInt(name) }.getOrNull() ?: 0
                fun dbl(name: String) = runCatching { m.getPropertyDouble(name) }.getOrNull()
                fun str(name: String) = runCatching { m.getPropertyString(name) }.getOrNull().orEmpty()
                val plan = PlaybackOptimizer.plan.value
                val sourceFps = dbl("container-fps") ?: 0.0
                val outFps = dbl("estimated-vf-fps") ?: 0.0
                val hz = PlaybackOptimizer.appliedHz.takeIf { it > 0f }?.toDouble() ?: dbl("display-fps") ?: 0.0
                val k = if (sourceFps > 0) hz / sourceFps else 0.0
                val cadence = if (sourceFps > 0 && hz > 0 && kotlin.math.abs(k - Math.round(k)) < 0.012) {
                    "✓ %d×".format(Math.round(k))
                } else {
                    "≈ %.2f×".format(k)
                }
                val w = int("video-params/w")
                val h = int("video-params/h")
                val codec = str("video-format").uppercase()
                val hw = str("hwdec-current").ifBlank { "sw" }
                val avsync = dbl("avsync") ?: 0.0
                val buffer = dbl("demuxer-cache-duration") ?: 0.0
                val speed = int("cache-speed") / 1_000_000.0
                val bitrate = (dbl("video-bitrate") ?: 0.0) / 1_000_000.0
                val battery = context.registerReceiver(
                    null,
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
                )
                val level = battery?.let {
                    val l = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val sc = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    if (l >= 0) l * 100 / sc else -1
                } ?: -1
                val temp = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
                lines = buildList {
                    add("VO %d  DEC %d  LATE %d".format(int("frame-drop-count"), int("decoder-frame-drop-count"), int("vo-delayed-frame-count")))
                    add("SRC %.3f  OUT %.2f  ⟶  %.0f Hz %s".format(sourceFps, outFps, hz, cadence))
                    add("%d×%d %s  •  %s".format(w, h, codec, hw))
                    add("SYNC %s  A/V %+.3f s".format(str("video-sync"), avsync))
                    add("BUF %.0f s  NET %.1f MB/s  %.1f Mb/s".format(buffer, speed, bitrate))
                    add("SCALE %s  •  %s".format(str("scale"), plan?.quality?.name ?: "-"))
                    add("BAT %d%%  %.0f°C  •  %s".format(level, temp, clock.format(java.util.Date())))
                }
            }
            delay(1_000)
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp), contentAlignment = Alignment.TopStart) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            lines.forEachIndexed { i, line ->
                Text(
                    text = line,
                    color = if (i == 0) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun ChoiceRow(
    title: String,
    summary: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    recommended: Int? = null,
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
                    label = {
                        Text(
                            if (index == recommended) "$label  ✦" else label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                    shape = RoundedCornerShape(percent = 50),
                )
            }
        }
    }
}
