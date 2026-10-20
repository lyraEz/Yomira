package yomira.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Screen-to-screen motion in the style of iOS: the new screen slides in over the old one on a
 * critically damped spring while the old one drifts a quarter of the way left, shrinks a little and
 * dims. Going back plays it in reverse, with the leaving screen kept on top.
 */
object YomiraTransitions {

    private const val BEHIND_SCALE = 0.94f
    private const val BEHIND_ALPHA = 0.55f

    private val slideSpec: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 420f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    private val fadeSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 420f,
    )

    /** True when going from [initial] to [target] moves back down the stack. */
    fun isBack(navigator: Navigator, initial: Screen, target: Screen): Boolean {
        val items = navigator.items
        val from = items.indexOf(initial)
        val to = items.indexOf(target)
        return if (from >= 0 && to >= 0) to < from else navigator.lastEvent == StackEvent.Pop
    }

    fun push(navigator: Navigator, initial: Screen, target: Screen): ContentTransform {
        val back = isBack(navigator, initial, target)
        return if (!back) {
            val enter = slideInHorizontally(slideSpec) { it }
            val exit = slideOutHorizontally(slideSpec) { -it / 4 } +
                scaleOut(fadeSpec, targetScale = BEHIND_SCALE) +
                fadeOut(fadeSpec, targetAlpha = BEHIND_ALPHA)
            ContentTransform(enter, exit, targetContentZIndex = 1f, sizeTransform = SizeTransform(clip = false))
        } else {
            val enter = slideInHorizontally(slideSpec) { -it / 4 } +
                scaleIn(fadeSpec, initialScale = BEHIND_SCALE) +
                fadeIn(fadeSpec, initialAlpha = BEHIND_ALPHA)
            val exit = slideOutHorizontally(slideSpec) { it }
            ContentTransform(enter, exit, targetContentZIndex = -1f, sizeTransform = SizeTransform(clip = false))
        }
    }
}

/**
 * [AnimatedContent] over the navigator's stack that also follows the system back gesture: while
 * the finger moves, the pop animation is scrubbed with it; letting go finishes it, cancelling
 * springs the screen back. On Android 13 and older the gesture arrives as a single back press.
 */
@Composable
fun PredictiveScreenTransition(
    navigator: Navigator,
    transition: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
) {
    val current = navigator.lastItem
    val state = remember { SeekableTransitionState(current) }
    val seekTransition = rememberTransition(state, label = "screens")

    LaunchedEffect(current) {
        if (state.currentState != current) state.animateTo(current)
    }

    PredictiveBackHandler(enabled = navigator.canPop) { events ->
        val previous = navigator.items.getOrNull(navigator.items.lastIndex - 1)
        try {
            events.collect { event -> if (previous != null) state.seekTo(event.progress, previous) }
            navigator.pop()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring back to the screen it started from, as far as it was dragged.
            coroutineScope {
                val totalMs = (seekTransition.totalDurationNanos / 1_000_000).coerceAtLeast(1)
                val ms = (state.fraction * totalMs).toInt().coerceAtLeast(1)
                animate(state.fraction, 0f, animationSpec = tween(ms)) { value, _ ->
                    launch { if (value > 0f) state.seekTo(value) }
                }
            }
            // Only after every pending seek above has run.
            state.snapTo(navigator.lastItem)
        }
    }

    seekTransition.AnimatedContent(
        transitionSpec = transition,
        modifier = modifier,
        contentKey = { it.key },
    ) { screen ->
        key(screen.key) {
            navigator.saveableState("transition", screen) {
                content(screen)
            }
        }
    }
}
