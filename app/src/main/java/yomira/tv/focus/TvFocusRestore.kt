package yomira.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.util.TvDevice

/**
 * For the main list or grid of a screen on TV: when the screen appears (including when coming back
 * to it), focus goes into the list and lands on the item that had it before, instead of starting
 * again from the navigation rail or the top bar. No-op on touch devices.
 */
@Composable
fun Modifier.tvFocusRestore(): Modifier {
    if (!TvDevice.isTv) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        // Let the list lay out its first items before focus is moved into it.
        repeat(RETRIES) {
            delay(RETRY_DELAY_MS)
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return this
        .focusRequester(requester)
        .focusRestorer()
}

private const val RETRIES = 4
private const val RETRY_DELAY_MS = 120L
