package yomira.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val capsuleFill = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.10f),
    0.5f to Color.Black.copy(alpha = 0.22f),
    1f to Color.Black.copy(alpha = 0.34f),
)
private val capsuleRim = Color.White.copy(alpha = 0.16f)

/**
 * Smoked-glass capsule that holds a group of player controls (the progress bar, the buttons at the
 * bottom right). It is a static gradient with a light rim, so it costs nothing per frame: the live
 * glass stays on the icons and the progress track, where it shows the most.
 */
fun Modifier.playerCapsule(): Modifier = this
    .clip(CircleShape)
    .background(capsuleFill)
    .border(0.5.dp, capsuleRim, CircleShape)
