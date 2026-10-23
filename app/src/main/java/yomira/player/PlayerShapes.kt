package yomira.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Rounder shapes for everything inside the player: chips turn into pills and menus, text fields,
 * cards, dialogs and sheets get soft corners. Buttons and icon buttons are already pills or circles.
 */
private val PlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Shape of the press highlight of a list row in the player sheets. */
val PlayerRowShape = RoundedCornerShape(16.dp)

@Composable
fun ProvidePlayerShapes(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = PlayerShapes,
        content = content,
    )
}

/** Insets a full-width row a little and rounds it, so its press highlight is a pill. */
fun Modifier.playerRow(): Modifier = this
    .padding(horizontal = 6.dp)
    .clip(PlayerRowShape)
