package yomira.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.launch
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle
import kotlin.math.roundToInt

/**
 * Liquid glass progress bar in the style of iOS sliders: a capsule track of frosted video with the
 * watched part in white and the buffered part dimmer; the thumb is a small white capsule that, while
 * dragging, springs into a larger clear glass lens which bends the track under it. The track also
 * thickens while held. Sizes and colours animate in the layout/draw phases only, so dragging never
 * recomposes the player. Without live blur the track is a translucent capsule and the thumb stays white.
 */
@Composable
fun GlassSeekBar(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    readAheadValue: Float,
    chapterStarts: List<Float>,
    modifier: Modifier = Modifier,
) {
    val videoBackdrop = LocalGlassBackdrop.current
    val style = LocalGlassStyle.current
    val live = videoBackdrop != null && style.liveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val bend = live && style.refraction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val press = remember { Animatable(0f) }
    // The lens (refraction shader over the video and the track) only runs while dragging.
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentRange by rememberUpdatedState(range)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)

    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val readAhead = ((readAheadValue - range.start) / span).coerceIn(0f, 1f)
    val chapters = remember(chapterStarts, range) {
        chapterStarts.map { ((it - range.start) / span).coerceIn(0f, 1f) }.filter { it > 0.002f && it < 0.998f }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val lensBackdrop = if (videoBackdrop != null) rememberCombinedBackdrop(videoBackdrop, trackBackdrop) else trackBackdrop

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target ->
                    onChange(target.coerceIn(currentRange.start, currentRange.endInclusive))
                    onFinished()
                    true
                }
            }
            .pointerInput(rtl) {
                fun toValue(x: Float): Float {
                    val f = (x / size.width).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
                    val r = currentRange
                    return r.start + f * (r.endInclusive - r.start)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    try {
                    down.consume()
                    dragging = true
                    scope.launch { press.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(toValue(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            onChange(toValue(change.position.x))
                            change.consume()
                        }
                    }
                    onFinished()
                    } finally {
                        dragging = false
                        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 380f)) }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track: recorded so the thumb lens can bend it; never contains the thumb.
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = lerp(6.dp.toPx(), 11.dp.toPx(), press.value).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                }
                .layerBackdrop(trackBackdrop)
                .then(
                    if (live) {
                        Modifier.drawPlainBackdrop(
                            backdrop = videoBackdrop!!,
                            shape = { RoundedCornerShape(percent = 50) },
                            effects = {
                                // The video copy is already soft; no blur pass needed.
                                colorControls(brightness = 0.06f, contrast = 1f, saturation = 1.5f)
                            },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.16f)) },
                        )
                    } else {
                        Modifier
                    },
                )
                .drawWithContent {
                    val radius = CornerRadius(size.height / 2f)
                    if (!live) drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = radius)
                    drawContent()
                    fun bar(from: Float, to: Float, color: Color) {
                        val w = (to - from) * size.width
                        if (w <= 0f) return
                        val left = if (rtl) size.width - to * size.width else from * size.width
                        drawRoundRect(color, Offset(left, 0f), Size(w, size.height), radius)
                    }
                    bar(0f, readAhead, Color.White.copy(alpha = 0.32f))
                    bar(0f, fraction, Color.White)
                    // Chapter marks as thin gaps.
                    chapters.forEach { c ->
                        val x = if (rtl) size.width * (1f - c) else size.width * c
                        drawRect(Color.Black.copy(alpha = 0.45f), Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                    }
                },
        )

        // Thumb: small white capsule at rest, clear glass lens while dragging.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = press.value
                    val w = lerp(16.dp.toPx(), 44.dp.toPx(), p).roundToInt()
                    val h = lerp(16.dp.toPx(), 28.dp.toPx(), p).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    val track = constraints.maxWidth
                    val center = (if (rtl) 1f - fraction else fraction) * track
                    val x = (center - w / 2f).roundToInt().coerceIn(-w / 2, track - w / 2)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x, (constraints.maxHeight - h) / 2)
                    }
                }
                .then(
                    if (live && dragging) {
                        Modifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { RoundedCornerShape(percent = 50) },
                            effects = {
                                colorControls(brightness = 0.12f, contrast = 1f, saturation = 1.4f)
                                if (bend) {
                                    val m = size.minDimension
                                    lens(
                                        refractionHeight = m * 0.32f,
                                        refractionAmount = m * 0.6f,
                                        depthEffect = true,
                                        chromaticAberration = false,
                                    )
                                }
                            },
                            highlight = { Highlight.Plain.copy(width = 1.dp, alpha = 0.6f * press.value + 0.2f) },
                            shadow = null,
                            onDrawSurface = {
                                // Solid white at rest, clear glass while held.
                                drawRect(Color.White.copy(alpha = lerp(1f, 0.14f, press.value)))
                            },
                        )
                    } else {
                        Modifier.drawWithContent {
                            drawRoundRect(Color.White, cornerRadius = CornerRadius(size.height / 2f))
                        }
                    },
                ),
        )
    }
}
