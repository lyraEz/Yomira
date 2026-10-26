package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getNumberOfItemsForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.zIndex(1f),
    ) {
        // capsule tabs instead of the Material tab row
        yomira.glass.CapsuleTabs(
            tabs = categories.map { yomira.glass.CapsuleTab(it.visualName, getNumberOfItemsForCategory(it)) },
            selectedIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex.coerceAtLeast(0)),
            onSelect = onTabItemClick,
        )
    }
}
