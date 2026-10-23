package yomira.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One destination of the floating tab bar. */
class GlassTabItem(
    val key: Any,
    val label: String,
    val icon: @Composable (selected: Boolean) -> Painter,
    val badge: String? = null,
)

/** Height of the floating bar itself (without the gap under it). */
val FloatingTabBarHeight: Dp = 64.dp

/** Space the floating bar takes from the bottom of the content (bar + gap above the system bar). */
val FloatingTabBarInset: Dp = FloatingTabBarHeight + 14.dp

/** Height and tab width of the bar while it is collapsed (icons only). */
private val CompactTabBarHeight: Dp = 48.dp
private val CompactTabWidth: Dp = 44.dp
private val CapsuleInset: Dp = 5.dp
private val SideGap: Dp = 10.dp

/**
 * Floating liquid glass tab bar in the style of iOS: a capsule with a selection lens that slides
 * between tabs with a spring (and follows the finger when dragged along the bar), plus an optional
 * round glass button on the side (search).
 *
 * When [collapsed] (while the content scrolls down) the capsule shrinks to the icons and the
 * labels fade out; it grows back when the flag clears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingGlassTabBar(
    tabs: List<GlassTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onTabLongPress: (Int) -> Unit = {},
    backdrop: com.kyant.backdrop.Backdrop? = LocalGlassBackdrop.current,
    sideButton: (@Composable (size: Dp) -> Unit)? = null,
) {
    val progress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "tabBarCollapse",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val height = lerp(FloatingTabBarHeight, CompactTabBarHeight, progress)
        val side = if (sideButton != null) height + SideGap else 0.dp
        val fullWidth = maxWidth - side
        val compactWidth = (CompactTabWidth * tabs.size + CapsuleInset * 2).coerceAtMost(fullWidth)
        Row(
            horizontalArrangement = Arrangement.spacedBy(SideGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCapsule(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onTabSelected = onTabSelected,
                onTabLongPress = onTabLongPress,
                backdrop = backdrop,
                labelFraction = { 1f - progress },
                modifier = Modifier
                    .width(lerp(fullWidth, compactWidth, progress))
                    .height(height),
            )
            sideButton?.invoke(height)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassCapsule(
    tabs: List<GlassTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabLongPress: (Int) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop?,
    labelFraction: () -> Float,
    modifier: Modifier,
) {
    if (tabs.isEmpty()) return
    val style = LocalGlassStyle.current
    val interaction = rememberGlassInteraction()
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onTabSelected)
    val slideSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 420f)

    LaunchedEffect(selectedIndex) {
        if (!dragging) position.animateTo(selectedIndex.toFloat(), slideSpec)
    }

    val accent = MaterialTheme.colorScheme.primary
    val onGlass = MaterialTheme.colorScheme.onSurface
    val lensColor = if (style.dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)
    val lensRim = if (style.dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.7f)

    BoxWithConstraints(
        modifier = modifier
            .glass(RoundedCornerShape(percent = 50), backdrop = backdrop, interaction = interaction),
    ) {
        val inset = CapsuleInset
        val tabWidth = (maxWidth - inset * 2) / tabs.size
        val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
        val last = (tabs.size - 1).toFloat()

        // Selection lens: slides with a spring and stretches while moving.
        Box(
            Modifier
                .padding(inset)
                .width(tabWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = position.value * tabWidthPx
                    val travel = abs(position.value - position.targetValue).coerceAtMost(1f)
                    val pressed = interaction.progress
                    scaleX = 1f + travel * 0.18f + pressed * 0.06f
                    scaleY = 1f - travel * 0.06f + pressed * 0.06f
                }
                .background(lensColor, RoundedCornerShape(percent = 50))
                .border(0.5.dp, lensRim, RoundedCornerShape(percent = 50)),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(inset)
                .pointerInput(tabs.size, tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val target = position.value.roundToInt().coerceIn(0, tabs.size - 1)
                            scope.launch { position.animateTo(target.toFloat(), slideSpec) }
                            if (target != currentSelected) currentOnSelected(target)
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { position.animateTo(currentSelected.toFloat(), slideSpec) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            position.snapTo((position.value + dragAmount / tabWidthPx).coerceIn(0f, last))
                        }
                    }
                },
        ) {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val tickOnTap = LocalGlassStyle.current.haptics
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onLongClick = {
                                if (tickOnTap) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabLongPress(index)
                            },
                            onClick = {
                                if (tickOnTap && !selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onTabSelected(index)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(
                            painter = tab.icon(selected),
                            contentDescription = null,
                            tint = if (selected) accent else onGlass.copy(alpha = 0.78f),
                            modifier = Modifier.size(24.dp),
                        )
                        if (tab.badge != null) {
                            Text(
                                text = tab.badge,
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(start = 14.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    // Long labels ("Biblioteca") shrink a little before they get cut.
                    BasicText(
                        text = tab.label,
                        style = TextStyle(
                            color = if (selected) accent else onGlass.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                        modifier = Modifier
                            .collapsible(labelFraction)
                            .graphicsLayer {
                                // Fades out faster than it shrinks, so it never shows cut off.
                                alpha = ((labelFraction() - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            },
                    )
                }
            }
        }
    }
}

/** Lays the label out with a share of its height, so the icon re-centers as it goes away. */
private fun Modifier.collapsible(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/** Round glass button the size of the tab bar, used beside it (search). */
@Composable
fun GlassOrbButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = FloatingTabBarHeight,
    backdrop: com.kyant.backdrop.Backdrop? = LocalGlassBackdrop.current,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = rememberGlassInteraction()
    Box(
        modifier = modifier
            .size(size)
            .glass(CircleShape, backdrop = backdrop, interaction = interaction, small = true)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painter, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}
