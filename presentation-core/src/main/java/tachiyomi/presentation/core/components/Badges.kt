package tachiyomi.presentation.core.components

import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    // capsule pills over covers
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            // frosted dark pill with a light rim, readable on any cover
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.28f)) }
            .border(0.5.dp, Color.White.copy(alpha = 0.22f), shape)
            .height(20.dp),
    ) {
        content()
    }
}

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    // translucent dark glass by default; callers that pass a colour keep it
    color: Color = Color.Black.copy(alpha = 0.42f),
    textColor: Color = Color.White,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
) {
    Text(
        text = text,
        modifier = modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .drawBehind {
                drawRect(color = color)
            }
            .padding(horizontal = 7.dp, vertical = 1.dp),
        color = textColor,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun Badge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    iconColor: Color = MaterialTheme.colorScheme.onSecondary,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .drawBehind {
                drawRect(color = color)
            }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = iconColor,
        )
    }
}

@Composable
fun Badge(
    painter: Painter,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    tint: Color = Color.Unspecified,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .drawBehind {
                drawRect(color = color)
            },
    ) {
        Icon(
            painter = painter,
            tint = tint,
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Composable
fun Badge(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    tint: Color? = null,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .drawBehind {
                drawRect(color = color)
            },
    ) {
        Image(
            bitmap = imageBitmap,
            colorFilter = tint?.let { ColorFilter.tint(it) },
            contentDescription = null,
            modifier = modifier,
        )
    }
}
