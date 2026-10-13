package yomira.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The content layer that glass surfaces of the current area refract. Provided by the screen or
 * shell that records it with [glassSource]; null where nothing is recorded (glass then falls back
 * to a translucent surface).
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** True inside a [tachiyomi.presentation.core.components.material.Scaffold] top bar drawn as glass. */
val LocalGlassTopBar = compositionLocalOf { false }

/**
 * Extra bottom space reserved by a floating bar drawn over the content (the home tab bar). Scaffolds
 * inside the home tabs add it to their content padding so lists scroll underneath the glass.
 */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

/**
 * Records the content drawn in its subtree so sibling glass surfaces can blur and refract it. The
 * opaque [base] avoids see-through holes where the content draws nothing.
 *
 * A glass surface must never be inside the subtree it samples (the GPU would read the layer it is
 * drawing into); keep glass as a sibling of the [glassSource] box.
 */
@Composable
fun rememberGlassBackdrop(base: Color = MaterialTheme.colorScheme.background): LayerBackdrop {
    val baseColor by rememberUpdatedState(base)
    return rememberLayerBackdrop {
        drawRect(baseColor)
        drawContent()
    }
}

fun Modifier.glassSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) layerBackdrop(backdrop) else this

/**
 * Press state of a glass control: a bouncy 0→1 progress and the finger position, read only in the
 * draw and layer phases so pressing never recomposes the control.
 */
@Stable
class GlassInteraction internal constructor(private val scope: CoroutineScope) {
    private val animation = Animatable(0f, 0.001f)
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

    val progress: Float
        get() = animation.value

    var position by mutableStateOf(Offset.Zero)
        private set

    internal fun press(at: Offset) {
        position = at
        scope.launch { animation.animateTo(1f, pressSpec) }
    }

    internal fun move(to: Offset) {
        position = to
    }

    internal fun release() {
        scope.launch { animation.animateTo(0f, pressSpec) }
    }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/** Observes touches without consuming them, so clickable children keep working. */
fun Modifier.glassPress(interaction: GlassInteraction): Modifier = pointerInput(interaction) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        interaction.press(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            interaction.move(change.position)
        }
        interaction.release()
    }
}

/**
 * Turns the element into a liquid glass surface: the content behind it ([backdrop]) is
 * saturated, blurred and bent at the edges, under a thin tint and a light rim.
 *
 * Below Android 12, or with glass turned off, it draws a translucent tinted surface with the same
 * shape instead (the library cannot blur there and would show a sharp copy of the background).
 *
 * @param tint accent colour mixed into the glass (e.g. the primary colour for an active button).
 * @param small small round controls get a stronger rim and may use prism edges on Ultra.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    style: GlassStyle = LocalGlassStyle.current,
    interaction: GlassInteraction? = null,
    tint: Color = Color.Unspecified,
    surfaceAlpha: Float = style.tintAlpha,
    small: Boolean = false,
): Modifier {
    val fallbackColor = if (tint.isSpecified) {
        tint.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
    }
    val glass = remember(backdrop, style, shape, interaction, tint, surfaceAlpha, small, fallbackColor) {
        if (backdrop == null || !style.liveBlur) {
            fallbackGlass(shape, style, interaction, fallbackColor)
        } else {
            liveGlass(backdrop, shape, style, interaction, tint, surfaceAlpha, small)
        }
    }
    val press = if (interaction != null) Modifier.glassPress(interaction) else Modifier
    return this.then(glass).then(press)
}

private fun fallbackGlass(
    shape: Shape,
    style: GlassStyle,
    interaction: GlassInteraction?,
    color: Color,
): Modifier {
    val rim = if (style.dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val scale = if (interaction != null) {
        Modifier.graphicsLayer {
            val s = lerp(1f, style.pressScale, interaction.progress)
            scaleX = s
            scaleY = s
        }
    } else {
        Modifier
    }
    val shadow = if (style.shadow && style.quality != GlassQuality.OFF) {
        Modifier.shadow(12.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.2f))
    } else {
        Modifier
    }
    return scale
        .then(shadow)
        .clip(shape)
        .background(color)
        .border(0.5.dp, rim, shape)
}

private fun liveGlass(
    backdrop: Backdrop,
    shape: Shape,
    style: GlassStyle,
    interaction: GlassInteraction?,
    tint: Color,
    surfaceAlpha: Float,
    small: Boolean,
): Modifier {
    val base = if (style.dark) Color.Black else Color.White
    val canBend = shape is CornerBasedShape
    val highlight: (() -> Highlight?) = if (style.highlightAlpha <= 0f) {
        { null }
    } else if (small) {
        { Highlight.Plain.copy(width = 1.dp, alpha = style.highlightAlpha) }
    } else {
        { Highlight.Default.copy(alpha = style.highlightAlpha) }
    }
    val shadow: (() -> Shadow?)? = if (style.shadows) {
        { Shadow(radius = if (small) 12.dp else 20.dp, alpha = 0.9f) }
    } else {
        null
    }
    return Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val press = interaction?.progress ?: 0f
            colorControls(brightness = style.brightness, contrast = 1f, saturation = style.vibrance)
            blur((style.blurRadius + 2.dp * press).toPx())
            if (style.refraction && canBend) {
                val minSide = size.minDimension
                lens(
                    refractionHeight = minSide * style.refractionHeight.coerceIn(0f, 0.45f) + 2.dp.toPx() * press,
                    refractionAmount = minSide * style.refractionAmount.coerceIn(0f, 1f),
                    depthEffect = style.depthEffect && style.quality == GlassQuality.ULTRA,
                    chromaticAberration = style.chromaticAberration &&
                        small &&
                        style.quality == GlassQuality.ULTRA,
                )
            }
        },
        highlight = highlight,
        shadow = shadow,
        layerBlock = if (interaction != null) {
            {
                val s = lerp(1f, style.pressScale, interaction.progress)
                scaleX = s
                scaleY = s
            }
        } else {
            null
        },
        onDrawSurface = {
            drawRect(base.copy(alpha = surfaceAlpha))
            if (tint.isSpecified) {
                drawRect(tint, blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = 0.55f))
            }
            val press = interaction?.progress ?: 0f
            if (press > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f * press), Color.Transparent),
                        center = interaction?.position ?: center,
                        radius = size.minDimension * 1.2f,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        },
    )
}

/**
 * Background of a full-width bar (top bar) over scrolling content: blurred and saturated, no lens,
 * with a tint strong enough to keep the title readable.
 */
fun Modifier.glassBar(backdrop: Backdrop, style: GlassStyle, tintAlpha: Float, edgeColor: Color): Modifier {
    val base = if (style.dark) Color.Black else Color.White
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            colorControls(brightness = style.brightness, contrast = 1f, saturation = style.vibrance)
            blur((style.blurRadius * 2f).coerceAtMost(48.dp).toPx())
        },
        onDrawSurface = {
            drawRect(base.copy(alpha = tintAlpha))
            drawRect(edgeColor, topLeft = Offset(0f, size.height - 1f), size = size.copy(height = 1f))
        },
    )
}

/**
 * [glassBar] whose blur and tint keep going [fade] below the bar and fade out there, so the glass
 * melts into the content instead of ending in a hard line (iOS scroll-edge effect). Put it on a
 * child that matches the bar's size: the extra height is drawn below without changing the layout.
 */
fun Modifier.glassBarFaded(
    backdrop: Backdrop,
    style: GlassStyle,
    tintAlpha: Float,
    fade: Dp = 24.dp,
): Modifier {
    val base = if (style.dark) Color.Black else Color.White
    return this
        .layout { measurable, constraints ->
            val extra = fade.roundToPx()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val placeable = measurable.measure(Constraints.fixed(width, height + extra))
            layout(width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val solid = ((size.height - fade.toPx()) / size.height).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    solid to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        .drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                colorControls(brightness = style.brightness, contrast = 1f, saturation = style.vibrance)
                blur((style.blurRadius * 1.5f).coerceAtMost(32.dp).toPx())
            },
            onDrawSurface = {
                drawRect(base.copy(alpha = tintAlpha))
            },
        )
}

/** Blur radius scaled for wide surfaces such as sheets. */
fun GlassStyle.sheetBlur(): Dp = (blurRadius * 1.6f).coerceAtMost(40.dp)
