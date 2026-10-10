package yomira.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import tachiyomi.presentation.core.util.TvDevice
import tachiyomi.presentation.core.util.collectAsState

/**
 * Wraps app content for TV use: larger text for viewing distance, a margin that keeps content
 * out of the overscan area, and a strong focus indication on every ripple-based component
 * (buttons, list rows, icon buttons, navigation items). Does nothing on touch devices.
 *
 * @param applySafeArea false for screens that must stay full-bleed (the video surface); such
 * screens apply [TvSafeArea] to their own controls instead.
 */
@Composable
fun TvContent(applySafeArea: Boolean = true, content: @Composable () -> Unit) {
    if (!TvDevice.isTv) {
        content()
        return
    }
    val textScale by TvSettings.textScalePercent().collectAsState()
    val density = LocalDensity.current
    val scaledDensity = remember(density, textScale) {
        Density(density.density, density.fontScale * textScale / 100f)
    }
    val focusRipple = remember {
        RippleConfiguration(
            rippleAlpha = RippleAlpha(
                draggedAlpha = 0.16f,
                focusedAlpha = 0.36f,
                hoveredAlpha = 0.16f,
                pressedAlpha = 0.24f,
            ),
        )
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalRippleConfiguration provides focusRipple,
    ) {
        if (applySafeArea) {
            TvSafeArea(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

/** Pads [content] by the configured overscan margin (5% by default) on TV. */
@Composable
fun TvSafeArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!TvDevice.isTv) {
        Box(modifier) { content() }
        return
    }
    val percent by TvSettings.safeAreaPercent().collectAsState()
    BoxWithConstraints(modifier) {
        val horizontal = maxWidth * percent / 100f
        val vertical = maxHeight * percent / 100f
        Box(Modifier.padding(horizontal = horizontal, vertical = vertical)) {
            content()
        }
    }
}
