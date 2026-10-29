package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.anime.components.CoverSettings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.domain.anime.model.AnimeCover as EntryCoverModel

object CommonAnimeItemDefaults {
    val GridHorizontalSpacer = 6.dp
    val GridVerticalSpacer = 8.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

private val ContinueWatchingButtonSizeSmall = 28.dp
private val ContinueWatchingButtonSizeLarge = 30.dp

private val ContinueWatchingButtonIconSizeSmall = 16.dp
private val ContinueWatchingButtonIconSizeLarge = 18.dp

private val ContinueWatchingButtonGridPadding = 8.dp
private val ContinueWatchingButtonListSpacing = 8.dp

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun AnimeCompactGridItem(
    coverData: EntryCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String? = null,
    onClickContinueWatching: (() -> Unit)? = null,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    usePanorama: Boolean? = null,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        // Resolved once: `getEntry` needs it to pick Book vs Panorama, and the cover needs it
        // to decide whether measuring the loaded image's aspect ratio is worth observing.
        val panorama = usePanorama ?: CoverSettings.panoramaCover
        val (entry, ratio) = AnimeCover.getEntry(coverData.animeId, usePanoramaOverride = panorama)
        val coverBlock: @Composable BoxScope.() -> Unit =
            remember(entry, coverData, ratio, isSelected, coverAlpha, panorama) {
                {
                    entry(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                this.alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha
                                this.compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                        data = coverData,
                        ratio = ratio,
                        shape = RectangleShape, // Optimization: Parent clips
                        // The library grid never reads the vibrant colour (only the entry
                        // screen and the player do), and extracting it forces a GPU->CPU
                        // bitmap readback per cover plus a load-state observer that recomposes
                        // this cell on every load transition. Colours are extracted when an
                        // entry is opened instead.
                        shouldExtractColor = false,
                        measureRatio = panorama,
                    )
                }
            }
        val contentBlock: @Composable BoxScope.() -> Unit = remember(title, onClickContinueWatching, progress) {
            {
                if (progress != null) CoverProgress(progress)
                if (title != null) {
                    CoverTextOverlay(
                        title = title,
                        onClickContinueWatching = onClickContinueWatching,
                    )
                } else if (onClickContinueWatching != null) {
                    ContinueWatchingButton(
                        size = ContinueWatchingButtonSizeLarge,
                        iconSize = ContinueWatchingButtonIconSizeLarge,
                        onClick = onClickContinueWatching,
                        modifier = Modifier
                            .padding(ContinueWatchingButtonGridPadding)
                            .align(Alignment.BottomEnd),
                    )
                }
            }
        }
        AnimeGridCover(
            cover = coverBlock,
            ratio = ratio,
            badgesStart = coverBadgeStart,
            badgesEnd = coverBadgeEnd,
            content = contentBlock,
        )
    }
}

/**
 * Title overlay for [AnimeCompactGridItem]
 */
@Composable
internal fun BoxScope.CoverTextOverlay(
    title: String,
    onClickContinueWatching: (() -> Unit)?,
) {
    val gradient = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color(0xCC000000),
        )
    }
    Spacer(
        modifier = Modifier
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .drawBehind {
                drawRect(brush = gradient)
            },
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        GridItemTitle(
            modifier = Modifier
                .weight(1f)
                .padding(all = 4.dp),
            title = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                ),
            ),
            minLines = 1,
            maxLines = 2,
        )
        if (onClickContinueWatching != null) {
            ContinueWatchingButton(
                size = ContinueWatchingButtonSizeSmall,
                iconSize = ContinueWatchingButtonIconSizeSmall,
                onClick = onClickContinueWatching,
                modifier = Modifier.padding(
                    bottom = ContinueWatchingButtonGridPadding,
                    end = ContinueWatchingButtonGridPadding,
                ),
            )
        }
    }
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun AnimeComfortableGridItem(
    coverData: EntryCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String = "",
    titleMaxLines: Int = 2,
    coverAlpha: Float = 1f,
    onClickContinueWatching: (() -> Unit)? = null,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    usePanorama: Boolean? = null,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String? = null,
    subtitleAccent: Boolean = false,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        val panorama = usePanorama ?: CoverSettings.panoramaCover
        val (entry, ratio) = AnimeCover.getEntry(coverData.animeId, usePanoramaOverride = panorama)
        val coverBlock: @Composable BoxScope.() -> Unit =
            remember(entry, coverData, ratio, isSelected, coverAlpha, panorama) {
                {
                    entry(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                this.alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha
                                this.compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                        data = coverData,
                        ratio = ratio,
                        shape = RectangleShape, // Optimization: Parent clips
                        // See AnimeCompactGridItem: no colour extraction for library items.
                        shouldExtractColor = false,
                        measureRatio = panorama,
                    )
                }
            }
        val contentBlock: @Composable BoxScope.() -> Unit = remember(onClickContinueWatching, progress) {
            {
                if (progress != null) CoverProgress(progress)
                if (onClickContinueWatching != null) {
                    ContinueWatchingButton(
                        size = ContinueWatchingButtonSizeLarge,
                        iconSize = ContinueWatchingButtonIconSizeLarge,
                        onClick = onClickContinueWatching,
                        modifier = Modifier
                            .padding(ContinueWatchingButtonGridPadding)
                            .align(Alignment.BottomEnd),
                    )
                }
            }
        }
        Column {
            AnimeGridCover(
                cover = coverBlock,
                ratio = ratio,
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
                content = contentBlock,
            )
            GridItemTitle(
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
                title = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                minLines = if (subtitle != null) 1 else 2,
                maxLines = titleMaxLines,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp, bottom = 2.dp),
                    color = if (subtitleAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (subtitleAccent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun AnimeGridCover(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    ratio: Float = AnimeCover.Book.ratio,
    cover: @Composable BoxScope.() -> Unit = {},
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    // soft shadow under the cover (a hardware shadow, drawn once per layer)
    val shadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .graphicsLayer {
                this.shape = shape
                this.clip = true
                shadowElevation = 3.dp.toPx()
                ambientShadowColor = shadowColor
                spotShadowColor = shadowColor
            },
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }
    }
}

@Composable
internal fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
internal fun GridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = if (isSelected) 0.95f else 1f
    // a little rounder than the cover it surrounds (4 dp padding)
    val shape = RoundedCornerShape(20.dp)
    val borderColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .then(
                if (scale < 1f) {
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.shape = shape
                        this.clip = true
                    }
                } else {
                    Modifier.clip(shape)
                },
            )
            .drawBehind {
                if (isSelected) {
                    drawRoundRect(
                        color = borderColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    )
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .drawBehind {
                        drawCircle(color = surfaceColor)
                    },
            )
        }
    }
}

/**
 * Layout of list item.
 */
@Composable
fun AnimeListItem(
    isSelected: Boolean = false,
    title: String,
    coverData: EntryCoverModel,
    coverAlpha: Float = 1f,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    badge: @Composable (RowScope.() -> Unit),
    onClickContinueWatching: (() -> Unit)? = null,
    entries: Int = 0,
    containerHeight: Int = 0,
    usePanorama: Boolean? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val density = LocalDensity.current
    // Resolve the effective value before sizing the row: keying the height on the *nullable*
    // parameter left callers that pass nothing (the season list) at the book height while their
    // covers still rendered at the panorama aspect.
    val panorama = usePanorama ?: CoverSettings.panoramaCover
    val height = remember(panorama) {
        if (panorama) 96.dp else 76.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .selectedBackground(isSelected)
            .height(height)
            .clickable(
                onClick = {
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (entry, ratio) = AnimeCover.getEntry(
            coverData.animeId,
            usePanoramaOverride = panorama,
        )
        entry(
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = coverAlpha
                    this.compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            data = coverData,
            ratio = ratio,
            measureRatio = panorama,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .weight(1f),
        ) {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        BadgeGroup(content = badge)
        if (onClickContinueWatching != null) {
            ContinueWatchingButton(
                size = ContinueWatchingButtonSizeSmall,
                iconSize = ContinueWatchingButtonIconSizeSmall,
                onClick = onClickContinueWatching,
                modifier = Modifier.padding(start = ContinueWatchingButtonListSpacing),
            )
        }
    }
}

@Composable
private fun ContinueWatchingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // white disc with a dark play mark, like the play buttons of Home and the details page
    val shadow = Color.Black.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                shadowElevation = 4.dp.toPx()
                shape = CircleShape
                clip = true
                ambientShadowColor = shadow
                spotShadowColor = shadow
            }
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(MR.strings.action_resume),
            tint = Color(0xFF111114),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** How much of the anime was watched, as a thin accent line along the bottom of the cover. */
@Composable
internal fun BoxScope.CoverProgress(progress: Float) {
    val accent = MaterialTheme.colorScheme.primary
    Spacer(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.45f))
                drawRect(accent, size = size.copy(width = size.width * progress.coerceIn(0f, 1f)))
            },
    )
}
