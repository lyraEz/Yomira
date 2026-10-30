package yomira.anime4k

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

val EnhanceQuality.labelRes: StringResource
    get() = when (this) {
        EnhanceQuality.LITE -> YMR.strings.a4k_quality_lite
        EnhanceQuality.FAST -> YMR.strings.a4k_quality_fast
        EnhanceQuality.HIGH -> YMR.strings.a4k_quality_high
    }

val LightAiLook.labelRes: StringResource
    get() = when (this) {
        LightAiLook.SHARP -> YMR.strings.a4k_look_sharp
        LightAiLook.SOFT -> YMR.strings.a4k_look_soft
    }

/** "Anime4K A+A · High", "Light AI Sharp · Lite". */
fun Enhancer.displayName(context: Context): String = when (this) {
    is Anime4KPreset -> "Anime4K ${mode.label} · ${context.stringResource(quality.labelRes)}"
    is LightAiPreset -> "${context.stringResource(YMR.strings.a4k_engine_light_ai)} " +
        "${context.stringResource(look.labelRes)} · ${context.stringResource(quality.labelRes)}"
}

@Composable
fun Enhancer.displayName(): String = displayName(LocalContext.current)

fun Enhancer.withQuality(quality: EnhanceQuality): Enhancer = when (this) {
    is Anime4KPreset -> copy(quality = quality)
    is LightAiPreset -> copy(quality = quality)
}

/** What the chosen mode or look is for. */
@Composable
internal fun enhancerHint(enhancer: Enhancer): String = when (enhancer) {
    is Anime4KPreset -> modeHint(enhancer.mode)
    is LightAiPreset -> stringResource(
        when (enhancer.look) {
            LightAiLook.SHARP -> YMR.strings.a4k_light_ai_sharp_hint
            LightAiLook.SOFT -> YMR.strings.a4k_light_ai_soft_hint
        },
    )
}

@Composable
internal fun modeHint(mode: Anime4KMode): String = stringResource(
    when (mode) {
        Anime4KMode.A -> YMR.strings.a4k_mode_a_hint
        Anime4KMode.B -> YMR.strings.a4k_mode_b_hint
        Anime4KMode.C -> YMR.strings.a4k_mode_c_hint
        Anime4KMode.AA -> YMR.strings.a4k_mode_aa_hint
        Anime4KMode.BB -> YMR.strings.a4k_mode_bb_hint
        Anime4KMode.CA -> YMR.strings.a4k_mode_ca_hint
    },
)

/** Engine (Anime4K or light AI), then its mode or look, then Lite / Fast / High. */
@Composable
internal fun EnhancerPicker(enhancer: Enhancer, onChange: (Enhancer) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(YMR.strings.a4k_engine))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(YMR.strings.a4k_engine_anime4k), selected = enhancer is Anime4KPreset) {
                if (enhancer !is Anime4KPreset) onChange(Anime4KPreset(Anime4KMode.A, enhancer.quality))
            }
            Chip(stringResource(YMR.strings.a4k_engine_light_ai), selected = enhancer is LightAiPreset) {
                if (enhancer !is LightAiPreset) onChange(LightAiPreset(LightAiLook.SHARP, enhancer.quality))
            }
        }
        when (enhancer) {
            is Anime4KPreset -> {
                Label(stringResource(YMR.strings.a4k_mode))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Anime4KMode.entries.forEach { mode ->
                        Chip(mode.label, selected = enhancer.mode == mode) { onChange(enhancer.copy(mode = mode)) }
                    }
                }
            }
            is LightAiPreset -> {
                Label(stringResource(YMR.strings.a4k_look))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LightAiLook.entries.forEach { look ->
                        Chip(stringResource(look.labelRes), selected = enhancer.look == look) { onChange(enhancer.copy(look = look)) }
                    }
                }
            }
        }
        Label(stringResource(YMR.strings.a4k_quality))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EnhanceQuality.entries.forEach { quality ->
                Chip(stringResource(quality.labelRes), selected = enhancer.quality == quality) {
                    onChange(enhancer.withQuality(quality))
                }
            }
        }
        Text(
            text = enhancerHint(enhancer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(percent = 50),
    )
}
