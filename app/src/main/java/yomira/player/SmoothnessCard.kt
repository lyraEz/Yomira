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
 * Small live counter of dropped/late frames in the top corner, shown only when enabled in the
 * smoothness card. Reads three mpv properties once per second.
 */
@Composable
fun FrameStatsOverlay(mpv: () -> MPV?) {
    val prefs = remember { SmoothnessPreferences() }
    val enabled by prefs.frameStats().collectAsState()
    if (!enabled) return
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val m = mpv()
            if (m != null) {
                fun long(name: String) = runCatching { m.getPropertyInt(name) }.getOrNull() ?: 0
                val dropped = long("frame-drop-count")
                val decoder = long("decoder-frame-drop-count")
                val late = long("vo-delayed-frame-count")
                val fps = runCatching { m.getPropertyDouble("estimated-display-fps") }.getOrNull() ?: 0.0
                text = "VO %d  DEC %d  LATE %d  %.0f fps".format(dropped, decoder, late, fps)
            }
            delay(1_000)
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp), contentAlignment = Alignment.TopStart) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
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
