package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AppBar
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import yomira.ui.HeaderActions
import yomira.ui.HeaderButton
import yomira.ui.TabHeader
import yomira.ui.TabHeaderSearch

@Composable
fun LibraryToolbar(
    hasActiveFilters: Boolean,
    selectedCount: Int,
    title: LibraryToolbarTitle,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomEntry: () -> Unit,
    onClickSyncNow: () -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    subtitle: String? = null,
) = when {
    selectedCount > 0 -> LibrarySelectionToolbar(
        selectedCount = selectedCount,
        onClickUnselectAll = onClickUnselectAll,
        onClickSelectAll = onClickSelectAll,
        onClickInvertSelection = onClickInvertSelection,
        scrollBehavior = scrollBehavior,
    )
    else -> LibraryRegularToolbar(
        title = title,
        subtitle = subtitle,
        hasFilters = hasActiveFilters,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onClickFilter = onClickFilter,
        onClickRefresh = onClickRefresh,
        onClickGlobalUpdate = onClickGlobalUpdate,
        onClickOpenRandomEntry = onClickOpenRandomEntry,
        onClickSyncNow = onClickSyncNow,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LibraryRegularToolbar(
    title: LibraryToolbarTitle,
    subtitle: String?,
    hasFilters: Boolean,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomEntry: () -> Unit,
    onClickSyncNow: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    TabHeader(
        title = title.text,
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        search = TabHeaderSearch(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = stringResource(YMR.strings.library_search_hint),
        ),
        actions = {
            HeaderButton(
                icon = Icons.Outlined.Search,
                contentDescription = stringResource(MR.strings.action_search),
                onClick = { onSearchQueryChange("") },
            )
            HeaderButton(
                icon = Icons.Outlined.FilterList,
                contentDescription = stringResource(MR.strings.action_filter),
                onClick = onClickFilter,
                active = hasFilters,
            )
            HeaderActions(
                persistentListOf(
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_library),
                        onClick = onClickGlobalUpdate,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_category),
                        onClick = onClickRefresh,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(YMR.strings.library_random),
                        onClick = onClickOpenRandomEntry,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(SYMR.strings.sync_library),
                        onClick = onClickSyncNow,
                    ),
                ),
            )
        },
    )
}

/** While picking anime: how many are picked, and select all / invert / done. */
@Composable
private fun LibrarySelectionToolbar(
    selectedCount: Int,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    if (scrollBehavior != null) {
        SideEffect {
            scrollBehavior.state.heightOffsetLimit = 0f
            scrollBehavior.state.heightOffset = 0f
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(60.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(MR.strings.action_cancel),
            onClick = onClickUnselectAll,
        )
        Text(
            text = stringResource(YMR.strings.library_selected, selectedCount),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HeaderButton(
            icon = Icons.Outlined.SelectAll,
            contentDescription = stringResource(MR.strings.action_select_all),
            onClick = onClickSelectAll,
        )
        HeaderButton(
            icon = Icons.Outlined.FlipToBack,
            contentDescription = stringResource(MR.strings.action_select_inverse),
            onClick = onClickInvertSelection,
        )
    }
}

@Immutable
data class LibraryToolbarTitle(
    val text: String,
    val numberOfEntries: Int? = null,
)
