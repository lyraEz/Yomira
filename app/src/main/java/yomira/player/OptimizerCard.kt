package yomira.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import kotlin.math.roundToInt
import tachiyomi.presentation.core.util.collectAsState as collectPref

/**
 * "Smart optimizer" card: shows what the optimizer detected for the current video and phone, its
 * recommendation for each setting (marked ✦), and lets every one of them be overridden.
 */
@Composable
fun OptimizerCard() {
    val activity = LocalContext.current as? PlayerActivity
    val prefs = remember { OptimizerPreferences() }
    val smooth = remember { SmoothnessPreferences() }
    val enabled by prefs.enabled().collectPref()
    val adaptive by prefs.adaptive().collectPref()
    val battery by prefs.batteryAware().collectPref()
    val modeSwitch by prefs.allowModeSwitch().collectPref()
    val refreshOverride by prefs.refreshOverride().collectPref()
    val syncOverride by prefs.syncOverride().collectPref()
    val quality by smooth.imageQuality().collectPref()
    val glass by smooth.videoGlassRate().collectPref()
    val plan by PlaybackOptimizer.plan.collectAsState()
    var expanded by remember { mutableStateOf(true) }
    val replan = { activity?.let { PlaybackOptimizer.replan(it) } }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Text(stringResource(YMR.strings.optimizer_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                title = stringResource(YMR.strings.optimizer_enabled),
                summary = stringResource(YMR.strings.optimizer_enabled_summary),
                checked = enabled,
                onChange = { prefs.enabled().set(it) },
            )
            if (!enabled) return@Column
            val p = plan
            if (p == null) {
                Text(
                    stringResource(YMR.strings.optimizer_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PlanSummary(p)

                val modeIndex = p.modes.indexOfFirst { it.id == p.refresh?.id }
                val recommendedMode = p.modes.indexOfFirst { it.id == p.recommendedRefresh?.id }
                ChoiceRow(
                    title = stringResource(YMR.strings.optimizer_refresh),
                    summary = stringResource(YMR.strings.optimizer_refresh_summary),
                    options = listOf(stringResource(YMR.strings.smooth_auto)) + p.modes.map { "${it.hz.roundToInt()} Hz" },
                    selected = if (refreshOverride <= 0f) 0 else modeIndex + 1,
                    recommended = if (recommendedMode >= 0) recommendedMode + 1 else null,
                    onSelect = { i ->
                        prefs.refreshOverride().set(if (i == 0) 0f else p.modes[i - 1].hz)
                        replan()
                    },
                )
                ChoiceRow(
                    title = stringResource(YMR.strings.optimizer_sync),
                    summary = stringResource(YMR.strings.optimizer_sync_summary),
                    options = listOf(
                        stringResource(YMR.strings.smooth_auto),
                        stringResource(YMR.strings.optimizer_sync_audio),
                        stringResource(YMR.strings.optimizer_sync_locked),
                        stringResource(YMR.strings.optimizer_sync_smooth),
                    ),
                    selected = syncOverride.ordinal,
                    recommended = p.recommendedSync.ordinal,
                    onSelect = { i ->
                        prefs.syncOverride().set(SyncMode.entries[i])
                        replan()
                    },
                )
                ChoiceRow(
                    title = stringResource(YMR.strings.smooth_quality),
                    summary = stringResource(YMR.strings.optimizer_quality_summary),
                    options = listOf(
                        stringResource(YMR.strings.smooth_auto),
                        stringResource(YMR.strings.smooth_quality_fast),
                        stringResource(YMR.strings.smooth_quality_balanced),
                        stringResource(YMR.strings.smooth_quality_high),
                    ),
                    selected = quality.ordinal,
                    recommended = p.recommendedQuality.ordinal,
                    onSelect = { i ->
                        smooth.imageQuality().set(ImageQuality.entries[i])
                        replan()
                    },
                )
                val rates = listOf(-1, 0, 30, 15, VideoGlassOff)
                ChoiceRow(
                    title = stringResource(YMR.strings.smooth_glass_rate),
                    summary = stringResource(YMR.strings.optimizer_glass_summary),
                    options = listOf(
                        stringResource(YMR.strings.smooth_auto),
                        "60 fps",
                        "30 fps",
                        "15 fps",
                        stringResource(YMR.strings.smooth_off),
                    ),
                    selected = rates.indexOf(glass).coerceAtLeast(0),
                    recommended = rates.indexOf(p.recommendedGlassRate).takeIf { it > 0 },
                    onSelect = { i ->
                        smooth.videoGlassRate().set(rates[i])
                        replan()
                    },
                )
                if (p.adjustments.isNotEmpty()) {
                    val names = p.adjustments.map {
                        when (it) {
                            "quality" -> stringResource(YMR.strings.optimizer_step_quality)
                            "glass" -> stringResource(YMR.strings.optimizer_step_glass)
                            else -> stringResource(YMR.strings.optimizer_step_interpolation)
                        }
                    }
                    Text(
                        stringResource(YMR.strings.optimizer_adjusted, names.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            SwitchRow(
                title = stringResource(YMR.strings.optimizer_adaptive),
                summary = stringResource(YMR.strings.optimizer_adaptive_summary),
                checked = adaptive,
                onChange = { prefs.adaptive().set(it) },
            )
            SwitchRow(
                title = stringResource(YMR.strings.optimizer_battery),
                summary = stringResource(YMR.strings.optimizer_battery_summary),
                checked = battery,
                onChange = {
                    prefs.batteryAware().set(it)
                    replan()
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.optimizer_mode_switch),
                summary = stringResource(YMR.strings.optimizer_mode_switch_summary),
                checked = modeSwitch,
                onChange = {
                    prefs.allowModeSwitch().set(it)
                    replan()
                },
            )
        }
    }
}

/** One tap back to the light defaults, for when playback stutters or the phone heats up. */
@Composable
fun ResetPlayerButton() {
    val context = LocalContext.current
    val mpv = (context as? PlayerActivity)?.viewModel?.mpv
    val done = stringResource(YMR.strings.player_reset_done)
    androidx.compose.material3.OutlinedButton(
        onClick = {
            PlayerDefaults.reset()
            mpv?.let { m ->
                listOf(
                    "glsl-shaders" to "",
                    "interpolation" to "no",
                    "video-sync" to "audio",
                    "deband" to "no",
                ).forEach { (k, v) -> runCatching { m.setPropertyString(k, v) } }
                Smoothness.setImageQuality(context, m, ImageQuality.AUTO)
                PlayerExtras.applyAudio(m)
            }
            android.widget.Toast.makeText(context, done, android.widget.Toast.LENGTH_LONG).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(YMR.strings.player_reset))
    }
}

@Composable
private fun PlanSummary(p: PlaybackPlan) {
    val tier = stringResource(
        when (p.tier) {
            Anime4KAdvisor.Tier.LOW -> YMR.strings.optimizer_tier_low
            Anime4KAdvisor.Tier.MID -> YMR.strings.optimizer_tier_mid
            Anime4KAdvisor.Tier.HIGH -> YMR.strings.optimizer_tier_high
            Anime4KAdvisor.Tier.FLAGSHIP -> YMR.strings.optimizer_tier_flagship
        },
    )
    val decoder = if (p.video.hwdec.isBlank() || p.video.hwdec == "no") {
        stringResource(YMR.strings.optimizer_decoder_software)
    } else {
        stringResource(YMR.strings.optimizer_decoder_hardware)
    }
    val refresh = p.refresh
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(
                YMR.strings.optimizer_video,
                "%.3f".format(p.video.fps),
                "${p.video.width}×${p.video.height}",
                p.video.codec.uppercase().ifBlank { "?" },
                decoder,
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (refresh != null) {
            val k = (refresh.hz / p.video.fps).roundToInt().coerceAtLeast(1)
            Text(
                if (p.exactCadence) {
                    stringResource(YMR.strings.optimizer_cadence_exact, refresh.hz.roundToInt(), k)
                } else {
                    stringResource(YMR.strings.optimizer_cadence_uneven, refresh.hz.roundToInt())
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (p.exactCadence) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(YMR.strings.optimizer_device, tier),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (p.batterySaver) {
            Text(
                stringResource(YMR.strings.optimizer_saver_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** "Audio & extras" card: night mode, dialogue boost, bass, resume rewind. */
@Composable
fun ExtrasCard() {
    val prefs = remember { PlayerExtrasPreferences() }
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val night by prefs.nightMode().collectPref()
    val voice by prefs.dialogueBoost().collectPref()
    val bass by prefs.bassBoost().collectPref()
    val rewind by prefs.resumeRewind().collectPref()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.GraphicEq, null)
                Text(stringResource(YMR.strings.extras_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                title = stringResource(YMR.strings.extras_night),
                summary = stringResource(YMR.strings.extras_night_summary),
                checked = night,
                onChange = {
                    prefs.nightMode().set(it)
                    PlayerExtras.applyAudio(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.extras_voice),
                summary = stringResource(YMR.strings.extras_voice_summary),
                checked = voice,
                onChange = {
                    prefs.dialogueBoost().set(it)
                    PlayerExtras.applyAudio(mpv, prefs)
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.extras_bass),
                summary = stringResource(YMR.strings.extras_bass_summary),
                checked = bass,
                onChange = {
                    prefs.bassBoost().set(it)
                    PlayerExtras.applyAudio(mpv, prefs)
                },
            )
            val options = listOf(0, 3, 5, 10)
            ChoiceRow(
                title = stringResource(YMR.strings.extras_rewind),
                summary = stringResource(YMR.strings.extras_rewind_summary),
                options = listOf(stringResource(YMR.strings.smooth_off), "3 s", "5 s", "10 s"),
                selected = options.indexOf(rewind).coerceAtLeast(0),
                onSelect = { prefs.resumeRewind().set(options[it]) },
            )
        }
    }
}
