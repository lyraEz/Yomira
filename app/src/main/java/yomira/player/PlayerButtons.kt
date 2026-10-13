package yomira.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import kotlin.math.roundToInt

/** Size and spacing of the player's floating buttons. */
class PlayerButtonPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Multiplier for icon size and inner padding, 0.8 to 1.4. */
    fun scale() = store.getFloat("yomira_player_button_scale", 1f)

    /** Gap kept around each glass chip, in dp. */
    fun spacing() = store.getInt("yomira_player_button_spacing", 3)
}

/** Look of the player's buttons, provided once around the controls. */
data class PlayerButtonLook(val scale: Float = 1f, val spacing: Dp = 3.dp)

val LocalPlayerButtonLook = compositionLocalOf { PlayerButtonLook() }

@Composable
fun ProvidePlayerButtonLook(content: @Composable () -> Unit) {
    val prefs = remember { PlayerButtonPreferences() }
    val scale by prefs.scale().collectAsState()
    val spacing by prefs.spacing().collectAsState()
    val look = remember(scale, spacing) {
        PlayerButtonLook(scale.coerceIn(0.8f, 1.4f), spacing.coerceIn(0, 12).dp)
    }
    CompositionLocalProvider(LocalPlayerButtonLook provides look, content = content)
}

/** Settings card with a live preview, shown above the button layout editor. */
@Composable
fun PlayerButtonSizeCard(modifier: Modifier = Modifier) {
    val prefs = remember { PlayerButtonPreferences() }
    val scale by prefs.scale().collectAsState()
    val spacing by prefs.spacing().collectAsState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Preview over a dark gradient that stands in for the video.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF6B3B5E), Color(0xFF0E1420))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            ProvidePlayerButtonLook {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControlsButton(Icons.Outlined.Subtitles, onClick = {})
                    ControlsButton(Icons.Filled.SkipPrevious, onClick = {}, iconSize = 32.dp)
                    ControlsButton(Icons.Filled.Pause, onClick = {}, iconSize = 40.dp)
                    ControlsButton(Icons.Filled.SkipNext, onClick = {}, iconSize = 32.dp)
                    ControlsButton(Icons.Outlined.ScreenRotation, onClick = {})
                }
            }
        }
        LabeledSlider(
            title = stringResource(YMR.strings.player_button_size),
            value = "${(scale * 100).roundToInt()}%",
            current = scale,
            range = 0.8f..1.4f,
            steps = 11,
            onChange = { prefs.scale().set((it * 20).roundToInt() / 20f) },
        )
        LabeledSlider(
            title = stringResource(YMR.strings.player_button_spacing),
            value = "$spacing dp",
            current = spacing.toFloat(),
            range = 0f..12f,
            steps = 11,
            onChange = { prefs.spacing().set(it.roundToInt()) },
        )
        TextButton(
            onClick = {
                prefs.scale().delete()
                prefs.spacing().delete()
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(YMR.strings.player_button_reset))
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
