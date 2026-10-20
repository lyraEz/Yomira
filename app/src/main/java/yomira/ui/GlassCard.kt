package yomira.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Look of the cards that hold list rows (updates, history, sources, extensions): a light frosted
 * panel with a hairline rim, like iOS grouped lists. No live blur per row, so long lists scroll as
 * fast as plain ones; the top bar and the tab bar are the only live glass on those screens.
 */
object GlassCard {

    @Composable
    fun color(): Color {
        val scheme = MaterialTheme.colorScheme
        return if (scheme.background.luminance() < 0.5f) {
            scheme.surfaceContainerHigh.copy(alpha = 0.78f)
        } else {
            scheme.surfaceContainerLowest.copy(alpha = 0.92f)
        }
    }

    @Composable
    fun border(): BorderStroke {
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val rim = if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f)
        return BorderStroke(0.5.dp, rim)
    }
}
