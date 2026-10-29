package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.network.model.NodeStatus
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Magnet
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.isScrollingUp
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import tachiyomi.source.localanime.isLocal
import yomira.glass.AlertDialog
import yomira.i18n.YMR
import yomira.ui.GlassCard
import yomira.ui.PillButton
import yomira.ui.SectionTitle
import yomira.ui.TagPill
import yomira.ui.groupedRow

private const val PINNED_GROUP = "pinned"
private const val LAST_USED_GROUP = "last_used"
private val SearchBarHeight = 60.dp

/** The sources page grouped for display: the last used one, the pinned ones, then by language. */
@Immutable
private data class SourceSection(val header: SourceUiModel.Header, val items: List<SourceUiModel.Item>)

private fun sectionsOf(items: List<SourceUiModel>): List<SourceSection> {
    val sections = mutableListOf<SourceSection>()
    var header: SourceUiModel.Header? = null
    val current = mutableListOf<SourceUiModel.Item>()
    fun flush() {
        header?.let { if (current.isNotEmpty()) sections += SourceSection(it, current.toList()) }
        current.clear()
    }
    items.forEach { model ->
        when (model) {
            is SourceUiModel.Header -> {
                flush()
                header = model
            }
            is SourceUiModel.Item -> current += model
        }
    }
    flush()
    return sections
}

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onToggleNsfwOnly: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()

    // Back first clears the search text and focus
    BackHandler(enabled = !state.searchQuery.isNullOrEmpty()) {
        onChangeSearchQuery("")
        focusManager.clearFocus()
    }

    val searching = !state.searchQuery.isNullOrBlank()
    val sections = remember(state.items) { sectionsOf(state.items) }

    Box(modifier = Modifier.fillMaxSize()) {
        FastScrollLazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                top = SearchBarHeight,
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    LoadingScreen(modifier = Modifier.fillParentMaxSize())
                }
                state.isEmpty -> item(key = "empty") {
                    EmptyScreen(
                        stringRes = if (state.searchQuery.isNullOrEmpty()) MR.strings.source_empty_screen else MR.strings.no_results_found,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
                else -> sections.forEach { section ->
                    val group = section.header.language
                    when {
                        !searching && group == LAST_USED_GROUP -> item(key = "last-used") {
                            val model = section.items.first()
                            LastUsedCard(
                                item = model,
                                hideLatest = state.hideLatest,
                                onClickItem = onClickItem,
                                onLongClickItem = onLongClickItem,
                            )
                        }
                        !searching && group == PINNED_GROUP -> item(key = "pinned") {
                            Column {
                                SectionTitle(title = section.header.displayName, count = section.items.size)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(section.items, key = { "pin-${it.source.id}" }) { model ->
                                        PinnedTile(model, onClickItem = onClickItem, onLongClickItem = onLongClickItem)
                                    }
                                }
                            }
                        }
                        else -> {
                            item(key = "header-${section.header.language}-${section.header.displayName}", contentType = "header") {
                                SectionTitle(title = section.header.displayName, count = section.items.size)
                            }
                            section.items.forEachIndexed { index, model ->
                                item(key = "source-${model.headerKey}-${model.source.id}", contentType = "source") {
                                    SourceRow(
                                        item = model,
                                        first = index == 0,
                                        last = index == section.items.lastIndex,
                                        hideLatest = state.hideLatest,
                                        onClickItem = onClickItem,
                                        onLongClickItem = onLongClickItem,
                                        onClickPin = onClickPin,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search capsule that steps aside while scrolling down and comes back on the way up
        AnimatedVisibility(
            visible = lazyListState.isScrollingUp() || searching,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            SourcesSearchBar(
                query = state.searchQuery,
                onQueryChange = onChangeSearchQuery,
                nsfwOnly = state.nsfwOnly,
                onToggleNsfwOnly = onToggleNsfwOnly,
            )
        }
    }
}

@Composable
private fun SourcesSearchBar(
    query: String?,
    onQueryChange: (String?) -> Unit,
    nsfwOnly: Boolean,
    onToggleNsfwOnly: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    val submit: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .height(SearchBarHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.onSurface.copy(alpha = 0.07f))
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isNullOrEmpty()) {
                    Text(
                        text = stringResource(YMR.strings.browse_search_sources),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query.orEmpty(),
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .runOnEnterKeyPressed(action = submit)
                        .clearFocusOnSoftKeyboardHide(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onBackground),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    cursorBrush = SolidColor(scheme.primary),
                )
            }
            if (!query.isNullOrEmpty()) {
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = stringResource(MR.strings.action_reset),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(8.dp),
                )
            }
        }
        // only sources for adults
        val red = scheme.error
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (nsfwOnly) red.copy(alpha = 0.18f) else scheme.onSurface.copy(alpha = 0.07f))
                .border(if (nsfwOnly) 1.5.dp else 0.dp, if (nsfwOnly) red else Color.Transparent, RoundedCornerShape(percent = 50))
                .clickable(role = Role.Switch, onClick = onToggleNsfwOnly)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "18+",
                color = if (nsfwOnly) red else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            )
        }
    }
}

/** Big card for the source used last: one tap back to its popular or latest list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LastUsedCard(
    item: SourceUiModel.Item,
    hideLatest: Boolean,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val source = item.source
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassCard.color())
            .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.22f), Color.Transparent)))
            .combinedClickable(
                onClick = { onClickItem(source, Listing.Popular) },
                onLongClick = { onLongClickItem(source) },
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(
                source = source,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(YMR.strings.browse_continue),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.displayName,
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.secondaryText.isNotEmpty()) {
                    Text(
                        text = item.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = stringResource(MR.strings.popular),
                onClick = { onClickItem(source, Listing.Popular) },
                filled = true,
            )
            if (source.supportsLatest && !hideLatest) {
                PillButton(
                    text = stringResource(MR.strings.latest),
                    onClick = { onClickItem(source, Listing.Latest) },
                )
            }
        }
    }
}

/** A pinned source as a tile in a horizontal row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedTile(
    item: SourceUiModel.Item,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassCard.color())
            .combinedClickable(
                onClick = { onClickItem(item.source, Listing.Popular) },
                onLongClick = { onLongClickItem(item.source) },
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SourceIcon(
            source = item.source,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Text(
            text = item.displayName,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onBackground,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** A source in a grouped card: icon, name with its language and tags, Latest and the pin. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    item: SourceUiModel.Item,
    first: Boolean,
    last: Boolean,
    hideLatest: Boolean,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val source = item.source
    Row(
        modifier = modifier
            .groupedRow(first, last)
            .combinedClickable(
                onClick = { onClickItem(source, Listing.Popular) },
                onLongClick = { onLongClickItem(source) },
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(
            source = source,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.isTorrent) {
                    Icon(CustomIcons.Magnet, contentDescription = "(Torrent)", modifier = Modifier.size(15.dp))
                }
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isApi) TagPill("API", Color(0xFF43A047))
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.status != null) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (item.status) {
                                    NodeStatus.OPERATIONAL -> Color(0xFF4CAF50)
                                    NodeStatus.DEGRADED -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                            ),
                    )
                }
                if (item.secondaryText.isNotEmpty()) {
                    Text(
                        text = item.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (item.isNsfw) TagPill("18+", scheme.error)
            }
        }
        if (source.supportsLatest && !hideLatest) {
            PillButton(
                text = stringResource(MR.strings.latest),
                onClick = { onClickItem(source, Listing.Latest) },
            )
        }
        val pinned = Pin.Pinned in source.pin
        Icon(
            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = stringResource(if (pinned) MR.strings.action_unpin else MR.strings.action_pin),
            tint = if (pinned) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onClickPin(source) }
                .padding(10.dp),
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onClickAddToFeed: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    if (onClickAddToFeed != null) {
                        Text(
                            text = "Add to Feed",
                            modifier = Modifier
                                .clickable(onClick = onClickAddToFeed)
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}
