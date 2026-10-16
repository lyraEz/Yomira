package yomira.player

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.colorControls
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle

/**
 * Player icon whose glyph itself is liquid glass: the blurred, brightened video shows through the
 * icon's shape (no bubble around it), with a soft shadow underneath for contrast over bright
 * scenes. Without live blur (Android 11 and older, or glass off) it is a white icon with the same
 * shadow. Pressing springs the icon up slightly.
 */
@Composable
fun GlassIcon(
    painter: Painter,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: InteractionSource? = null,
    color: Color = Color.White,
) {
    val backdrop = LocalGlassBackdrop.current
    val style = LocalGlassStyle.current
    val pressed = interactionSource?.collectIsPressedAsState()?.value ?: false
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "glassIconPress",
    )
    val alpha = if (enabled) 1f else 0.4f
    val live = backdrop != null && style.liveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center,
    ) {
        // Soft shadow of the glyph (two offset copies, no blur pass) keeps it readable over
        // bright frames at almost no cost.
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val d = 0.8.dp.toPx()
                    translate(top = d) {
                        with(painter) { draw(this@drawWithContent.size, alpha = 0.32f, colorFilter = ShadowFilter) }
                    }
                    translate(top = d * 2.2f) {
                        with(painter) { draw(this@drawWithContent.size, alpha = 0.16f, colorFilter = ShadowFilter) }
                    }
                },
        )
        if (live && color == Color.White) {
            // The glyph is a window onto the (already soft) video copy: brightened and more
            // saturated, a light frost, and a specular sheen on the upper part.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        maskWith(painter)
                    }
                    .drawPlainBackdrop(
                        backdrop = backdrop!!,
                        shape = { RectangleShape },
                        effects = {
                            colorControls(brightness = 0.22f, contrast = 1.05f, saturation = 1.8f)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 0.34f))
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.5f),
                                    0.55f to Color.White.copy(alpha = 0.08f),
                                    1f to Color.Transparent,
                                ),
                            )
                        },
                    ),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        with(painter) { draw(this@drawWithContent.size, colorFilter = ColorFilter.tint(color)) }
                    },
            )
        }
    }
}

@Composable
fun GlassIcon(
    icon: ImageVector,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: InteractionSource? = null,
    color: Color = Color.White,
) {
    GlassIcon(rememberVectorPainter(icon), size, modifier, enabled, interactionSource, color)
}

private val ShadowFilter = ColorFilter.tint(Color.Black)

/** Keeps what was drawn so far only where [painter] has pixels. */
private fun DrawScope.maskWith(painter: Painter) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { blendMode = BlendMode.DstIn }
        canvas.saveLayer(size.toRect(), paint)
        with(painter) { draw(this@maskWith.size) }
        canvas.restore()
    }
}
