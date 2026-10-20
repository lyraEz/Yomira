/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("KDocUnresolvedReference")

package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.util.fastMaxBy
import yomira.glass.LocalFloatingBarInset
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle
import yomira.glass.LocalGlassTopBar
import yomira.glass.LocalLargeTopBar
import androidx.compose.ui.graphics.graphicsLayer
import yomira.glass.glassBarFaded
import yomira.glass.glassSource
import yomira.glass.rememberGlassBackdrop
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * <a href="https://material.io/design/layout/understanding-layout.html" class="external" target="_blank">Material Design layout</a>.
 *
 * Scaffold implements the basic material design visual layout structure.
 *
 * This component provides API to put together several material components to construct your
 * screen, by ensuring proper layout strategy for them and collecting necessary data so these
 * components will work together correctly.
 *
 * Simple example of a Scaffold with [SmallTopAppBar], [FloatingActionButton]:
 *
 * @sample androidx.compose.material3.samples.SimpleScaffoldWithTopBar
 *
 * To show a [Snackbar], use [SnackbarHostState.showSnackbar].
 *
 * @sample androidx.compose.material3.samples.ScaffoldWithSimpleSnackbar
 *
 * Tachiyomi changes:
 * * Pass scroll behavior to top bar by default
 * * Remove height constraint for expanded app bar
 * * Also take account of fab height when providing inner padding
 * * Fixes for fab and snackbar horizontal placements when [contentWindowInsets] is used
 * * Handle consumed window insets
 * * Add startBar slot for Navigation Rail
 * * Added Haze (Glassmorphism) support
 *
 * @param modifier the [Modifier] to be applied to this scaffold
 * @param topBar top app bar of the screen, typically a [SmallTopAppBar]
 * @param startBar side bar on the start of the screen, typically a [NavigationRail]
 * @param bottomBar bottom bar of the screen, typically a [NavigationBar]
 * @param snackbarHost component to host [Snackbar]s that are pushed to be shown via
 * [SnackbarHostState.showSnackbar], typically a [SnackbarHost]
 * @param floatingActionButton Main action button of the screen, typically a [FloatingActionButton]
 * @param floatingActionButtonPosition position of the FAB on the screen. See [FabPosition].
 * @param containerColor the color used for the background of this scaffold. Use [Color.Transparent]
 * to have no color.
 * @param contentColor the preferred color for content inside this scaffold. Defaults to either the
 * matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param contentWindowInsets window insets to be passed to content slot via PaddingValues params.
 * Scaffold will take the insets into account from the top/bottom only if the topBar/ bottomBar
 * are not present, as the scaffold expect topBar/bottomBar to handle insets instead
 * @param content content of the screen. The lambda receives a [PaddingValues] that should be
 * applied to the content root via [Modifier.padding] and [Modifier.consumeWindowInsets] to
 * properly offset top and bottom bars. If using [Modifier.verticalScroll], apply this modifier to
 * the child of the scroll, and not on the scroll itself.
 */
@ExperimentalMaterial3Api
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    // YMR --> null picks pinned, or collapse-on-scroll when [largeTopBar] is set
    topBarScrollBehavior: TopAppBarScrollBehavior? = null,
    // YMR <--
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    startBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    hazeEnabled: Boolean = false,
    // YMR -->
    glassTopBar: Boolean = true,
    // false: the content is still recorded, but the top bar paints its own glass through
    // LocalGlassBackdrop (e.g. buttons floating over a cover)
    glassTopBarSurface: Boolean = true,
    // iOS-style large title that shrinks into the bar while the content scrolls up
    largeTopBar: Boolean = false,
    // YMR <--
    content: @Composable (PaddingValues) -> Unit,
) {
    // YMR -->
    val scrollBehavior = topBarScrollBehavior ?: if (largeTopBar) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    } else {
        TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    }
    // YMR <--
    // Tachiyomi: Handle consumed window insets
    val remainingWindowInsets = remember { MutableWindowInsets() }
    val hazeState = remember { HazeState() }
    // YMR --> Top bar as liquid glass over the scrolling content (Android 12+)
    val glassStyle = LocalGlassStyle.current
    val glassBackdrop = if (glassTopBar && !hazeEnabled && glassStyle.liveBlur) {
        rememberGlassBackdrop(containerColor)
    } else {
        null
    }
    val glassBarTint = (glassStyle.tintAlpha + 0.32f).coerceAtMost(0.88f)
    val floatingBarInset = LocalFloatingBarInset.current
    // YMR <--

    androidx.compose.material3.Surface(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .onConsumedWindowInsetsChanged {
                remainingWindowInsets.insets = contentWindowInsets.exclude(
                    it,
                )
            }
            .then(modifier),
        color = containerColor,
        contentColor = contentColor,
    ) {
        ScaffoldLayout(
            fabPosition = floatingActionButtonPosition,
            topBar = {
                Box(
                    modifier = if (hazeEnabled) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                blurRadius = 24.dp,
                                tint = HazeTint(Color.Transparent),
                            ),
                        )
                    // YMR -->
                    } else {
                        Modifier
                    }
                ) {
                    // YMR --> glass that fades out below the bar instead of ending in a line
                    if (glassBackdrop != null && glassTopBarSurface) {
                        // A large title starts clear over the page and turns into glass as it collapses
                        val fade = if (largeTopBar) {
                            Modifier.graphicsLayer {
                                val state = scrollBehavior.state
                                // Clear while the large title sits over an unscrolled page; frosted as soon
                                // as it shrinks or something is under it (or a small bar replaced it).
                                alpha = if (state.heightOffsetLimit == 0f) {
                                    1f
                                } else {
                                    maxOf(state.collapsedFraction, state.overlappedFraction).coerceIn(0f, 1f)
                                }
                            }
                        } else {
                            Modifier
                        }
                        Box(Modifier.matchParentSize().then(fade).glassBarFaded(glassBackdrop, glassStyle, glassBarTint))
                    }
                    CompositionLocalProvider(
                        LocalGlassTopBar provides (glassBackdrop != null && glassTopBarSurface),
                        LocalGlassBackdrop provides glassBackdrop.takeUnless { glassTopBarSurface },
                        LocalLargeTopBar provides largeTopBar,
                    ) {
                        topBar(scrollBehavior)
                    }
                    // YMR <--
                }
            },
            startBar = startBar,
            bottomBar = {
                Box(
                    modifier = if (hazeEnabled) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                blurRadius = 24.dp,
                                tint = HazeTint(Color.Transparent),
                            ),
                        )
                    } else {
                        Modifier
                    }
                ) {
                    bottomBar()
                }
            },
            content = {
                Box(
                    modifier = if (hazeEnabled) {
                        Modifier.haze(hazeState)
                    } else {
                        // YMR -->
                        Modifier.glassSource(glassBackdrop)
                        // YMR <--
                    },
                ) {
                    // YMR --> content must never sample a backdrop that records it
                    CompositionLocalProvider(LocalGlassBackdrop provides null) {
                        content(it)
                    }
                    // YMR <--
                }
            },
            snackbar = snackbarHost,
            contentWindowInsets = remainingWindowInsets,
            fab = floatingActionButton,
            // YMR -->
            extraBottom = floatingBarInset,
            // YMR <--
        )
    }
}

/**
 * Layout for a [Scaffold]'s content.
 *
 * @param fabPosition [FabPosition] for the FAB (if present)
 * @param topBar the content to place at the top of the [Scaffold], typically a [SmallTopAppBar]
 * @param content the main 'body' of the [Scaffold]
 * @param snackbar the [Snackbar] displayed on top of the [content]
 * @param fab the [FloatingActionButton] displayed on top of the [content], below the [snackbar]
 * and above the [bottomBar]
 * @param bottomBar the content to place at the bottom of the [Scaffold], on top of the
 * [content], typically a [NavigationBar].
 */
@Composable
private fun ScaffoldLayout(
    fabPosition: FabPosition,
    topBar: @Composable () -> Unit,
    startBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit,
    // YMR -->
    extraBottom: Dp = 0.dp,
    // YMR <--
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        /**
         * Tachiyomi: Remove height constraint for expanded app bar
         */
        val topBarConstraints = looseConstraints.copy(maxHeight = Constraints.Infinity)

        layout(layoutWidth, layoutHeight) {
            val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
            val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
            // YMR --> a floating bar drawn over the content counts as part of the bottom inset
            val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout) + extraBottom.roundToPx()
            // YMR <--

            // Tachiyomi: Add startBar slot for Navigation Rail
            val startBarPlaceables = subcompose(ScaffoldLayoutContent.StartBar, startBar).fastMap {
                it.measure(looseConstraints)
            }
            val startBarWidth = startBarPlaceables.fastMaxBy { it.width }?.width ?: 0

            // Tachiyomi: layoutWidth after horizontal insets
            val insetLayoutWidth = layoutWidth - leftInset - rightInset - startBarWidth

            val topBarPlaceables = subcompose(ScaffoldLayoutContent.TopBar, topBar).fastMap {
                it.measure(topBarConstraints)
            }

            val topBarHeight = topBarPlaceables.fastMaxBy { it.height }?.height ?: 0

            val snackbarPlaceables = subcompose(ScaffoldLayoutContent.Snackbar, snackbar).fastMap {
                it.measure(looseConstraints)
            }

            val snackbarHeight = snackbarPlaceables.fastMaxBy { it.height }?.height ?: 0
            val snackbarWidth = snackbarPlaceables.fastMaxBy { it.width }?.width ?: 0

            // Tachiyomi: Calculate insets for snackbar placement offset
            val snackbarLeft = if (snackbarPlaceables.isNotEmpty()) {
                (insetLayoutWidth - snackbarWidth) / 2 + leftInset
            } else {
                0
            }

            val fabPlaceables =
                subcompose(ScaffoldLayoutContent.Fab, fab).fastMapNotNull { measurable ->
                    measurable
                        // KMK -->
                        .measure(looseConstraints.offset(-leftInset - rightInset, -bottomInset))
                        .takeIf { it.height != 0 && it.width != 0 }
                    // KMK <--
                }

            val fabWidth = fabPlaceables.fastMaxBy { it.width }?.width ?: 0
            val fabHeight = fabPlaceables.fastMaxBy { it.height }?.height ?: 0

            val fabPlacement = if (fabPlaceables.isNotEmpty() && fabWidth != 0 && fabHeight != 0) {
                // FAB distance from the left of the layout, taking into account LTR / RTL
                // Tachiyomi: Calculate insets for fab placement offset
                val fabLeftOffset = when (fabPosition) {
                    // KMK -->
                    FabPosition.Start -> {
                        if (layoutDirection == LayoutDirection.Ltr) {
                            FabSpacing.roundToPx() + leftInset
                        } else {
                            layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                        }
                    }
                    FabPosition.EndOverlay,
                    // KMK <--
                    FabPosition.End,
                    -> {
                        if (layoutDirection == LayoutDirection.Ltr) {
                            layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                        } else {
                            FabSpacing.roundToPx() + leftInset
                        }
                    }
                    else -> {
                        leftInset + ((insetLayoutWidth - fabWidth) / 2)
                    }
                }

                FabPlacement(
                    left = fabLeftOffset,
                    width = fabWidth,
                    height = fabHeight,
                )
            } else {
                null
            }

            val bottomBarPlaceables = subcompose(ScaffoldLayoutContent.BottomBar) {
                bottomBar()
            }.fastMap { it.measure(looseConstraints) }

            val bottomBarHeight = bottomBarPlaceables
                .fastMaxBy { it.height }
                ?.height
                ?.takeIf { it != 0 }
            val fabOffsetFromBottom = fabPlacement?.let {
                kotlin.math.max(bottomBarHeight ?: 0, bottomInset) + it.height + FabSpacing.roundToPx()
            }

            val snackbarOffsetFromBottom = if (snackbarHeight != 0) {
                snackbarHeight + (fabOffsetFromBottom ?: kotlin.math.max(bottomBarHeight ?: 0, bottomInset))
            } else {
                0
            }

            val bodyContentPlaceables = subcompose(ScaffoldLayoutContent.MainContent) {
                val insets = contentWindowInsets.asPaddingValues(this@SubcomposeLayout)
                val fabOffsetDp = fabOffsetFromBottom?.let { it.toDp() } ?: 0.dp
                val bottomBarHeightPx = bottomBarHeight ?: 0
                // YMR -->
                val extraBottomDp = extraBottom
                // YMR <--
                val innerPadding = PaddingValues(
                    top = if (topBarPlaceables.isEmpty()) {
                        insets.calculateTopPadding()
                    } else {
                        topBarHeight.toDp()
                    },
                    // Tachiyomi: Also take account of fab height when providing inner padding
                    bottom = if (bottomBarPlaceables.isEmpty() || bottomBarHeightPx == 0) {
                        max(insets.calculateBottomPadding() + extraBottomDp, fabOffsetDp)
                    } else {
                        max(bottomBarHeightPx.toDp(), fabOffsetDp)
                    },
                    start = max(
                        insets.calculateStartPadding((this@SubcomposeLayout).layoutDirection),
                        startBarWidth.toDp(),
                    ),
                    end = insets.calculateEndPadding((this@SubcomposeLayout).layoutDirection),
                )
                content(innerPadding)
            }.fastMap { it.measure(looseConstraints) }

            // Placing to control drawing order to match default elevation of each placeable

            bodyContentPlaceables.fastForEach {
                it.place(0, 0)
            }
            startBarPlaceables.fastForEach {
                it.placeRelative(0, 0)
            }
            topBarPlaceables.fastForEach {
                it.place(0, 0)
            }
            snackbarPlaceables.fastForEach {
                it.place(
                    snackbarLeft,
                    layoutHeight - snackbarOffsetFromBottom,
                )
            }
            // The bottom bar is always at the bottom of the layout
            bottomBarPlaceables.fastForEach {
                it.place(0, layoutHeight - (bottomBarHeight ?: 0))
            }
            // Explicitly not using placeRelative here as `leftOffset` already accounts for RTL
            fabPlaceables.fastForEach {
                it.place(fabPlacement?.left ?: 0, layoutHeight - (fabOffsetFromBottom ?: 0))
            }
        }
    }
}

/**
 * Placement information for a [FloatingActionButton] inside a [Scaffold].
 *
 * @property left the FAB's offset from the left edge of the bottom bar, already adjusted for RTL
 * support
 * @property width the width of the FAB
 * @property height the height of the FAB
 */
@Immutable
internal class FabPlacement(
    val left: Int,
    val width: Int,
    val height: Int,
)

// FAB spacing above the bottom bar / bottom of the Scaffold
private val FabSpacing = 16.dp

private enum class ScaffoldLayoutContent { TopBar, MainContent, Snackbar, Fab, BottomBar, StartBar }
