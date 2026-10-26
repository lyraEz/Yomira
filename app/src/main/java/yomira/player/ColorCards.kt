package yomira.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.presentation.player.components.SliderItem
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import `is`.xyz.mpv.MPV
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import kotlin.math.roundToInt
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Waits for a slider to settle before mpv rebuilds its shaders. */
private const val SLIDER_SETTLE_MS = 150L

/** White balance, exposure and tones: fixes what the picture got wrong. */
@Composable
fun ColorCorrectionCard() {
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val prefs = remember { ColorPreferences() }
    val on by prefs.correctionOn().collectPref()
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Exposure, null)
                Text(stringResource(YMR.strings.color_correction_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(
                    title = stringResource(YMR.strings.color_apply),
                    summary = stringResource(YMR.strings.color_correction_summary),
                    checked = on,
                    onChange = {
                        prefs.correctionOn().set(it)
                        ColorGrading.apply(mpv)
                    },
                )
                TextButton(
                    onClick = {
                        prefs.resetCorrection()
                        ColorGrading.apply(mpv)
                    },
                ) {
                    Text(stringResource(MR.strings.action_reset))
                }
            }
            ColorSlider(YMR.strings.color_temperature, prefs.temperature(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_tint, prefs.tint(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_exposure, prefs.exposure(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_shadows, prefs.shadows(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_highlights, prefs.highlights(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_vibrance, prefs.vibrance(), -100, 100, mpv)
        }
    }
}

/** Looks, split toning, contrast curve and fade: gives the picture a style. */
@Composable
fun ColorGradingCard() {
    val mpv = (LocalContext.current as? PlayerActivity)?.viewModel?.mpv
    val prefs = remember { ColorPreferences() }
    val on by prefs.gradingOn().collectPref()
    val shadowHue by prefs.shadowHue().collectPref()
    val shadowAmount by prefs.shadowAmount().collectPref()
    val highlightHue by prefs.highlightHue().collectPref()
    val highlightAmount by prefs.highlightAmount().collectPref()
    val balance by prefs.balance().collectPref()
    val curve by prefs.curve().collectPref()
    val saturation by prefs.saturation().collectPref()
    val fade by prefs.fade().collectPref()
    val look = ColorLook.of(
        ColorGrade(shadowHue, shadowAmount, highlightHue, highlightAmount, balance, curve, saturation, fade),
    )
    var expanded by remember { mutableStateOf(false) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Palette, null)
                Text(stringResource(YMR.strings.color_grading_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    title = stringResource(YMR.strings.color_apply),
                    summary = stringResource(YMR.strings.color_grading_summary),
                    checked = on,
                    onChange = {
                        prefs.gradingOn().set(it)
                        ColorGrading.apply(mpv)
                    },
                )
                ChoiceRow(
                    title = stringResource(YMR.strings.color_look),
                    summary = "",
                    options = ColorLook.entries.map { stringResource(it.label()) },
                    selected = look?.ordinal ?: -1,
                    onSelect = {
                        prefs.setGrade(ColorLook.entries[it].grade)
                        ColorGrading.apply(mpv)
                    },
                )
                TextButton(
                    onClick = {
                        prefs.resetGrade()
                        ColorGrading.apply(mpv)
                    },
                ) {
                    Text(stringResource(MR.strings.action_reset))
                }
            }
            ColorSlider(YMR.strings.color_shadow_hue, prefs.shadowHue(), 0, 359, mpv, hue = true)
            ColorSlider(YMR.strings.color_shadow_amount, prefs.shadowAmount(), 0, 100, mpv)
            ColorSlider(YMR.strings.color_highlight_hue, prefs.highlightHue(), 0, 359, mpv, hue = true)
            ColorSlider(YMR.strings.color_highlight_amount, prefs.highlightAmount(), 0, 100, mpv)
            ColorSlider(YMR.strings.color_balance, prefs.balance(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_curve, prefs.curve(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_saturation, prefs.saturation(), -100, 100, mpv)
            ColorSlider(YMR.strings.color_fade, prefs.fade(), 0, 100, mpv)
            Text(
                text = stringResource(YMR.strings.color_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: dev.icerock.moko.resources.StringResource,
    pref: Preference<Int>,
    min: Int,
    max: Int,
    mpv: MPV?,
    hue: Boolean = false,
) {
    val value by pref.collectPref()
    SliderItem(
        label = stringResource(label),
        value = value.toFloat(),
        valueText = when {
            hue -> "$value°"
            min < 0 && value > 0 -> "+$value"
            else -> value.toString()
        },
        onChange = {
            val next = it.roundToInt()
            if (next != value) {
                pref.set(next)
                ColorGrading.apply(mpv, SLIDER_SETTLE_MS)
            }
        },
        max = max.toFloat(),
        min = min.toFloat(),
        modifier = Modifier.fillMaxWidth(),
        icon = {
            if (hue) {
                val rgb = ColorShader.hueToRgb(value.toDouble())
                Box(
                    Modifier
                        .size(18.dp)
                        .background(Color(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat()), CircleShape),
                )
            }
        },
    )
}

private fun ColorLook.label() = when (this) {
    ColorLook.NONE -> YMR.strings.color_look_none
    ColorLook.CINEMA -> YMR.strings.color_look_cinema
    ColorLook.WARM -> YMR.strings.color_look_warm
    ColorLook.COOL -> YMR.strings.color_look_cool
    ColorLook.VIVID -> YMR.strings.color_look_vivid
    ColorLook.VINTAGE -> YMR.strings.color_look_vintage
    ColorLook.PASTEL -> YMR.strings.color_look_pastel
    ColorLook.DRAMATIC -> YMR.strings.color_look_dramatic
    ColorLook.NOIR -> YMR.strings.color_look_noir
}
