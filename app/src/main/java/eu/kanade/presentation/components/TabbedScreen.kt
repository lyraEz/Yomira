package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectAsStatePref
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TabbedScreen(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    scrollable: Boolean = false,
    searchQuery: String? = null,
    onChangeSearchQuery: (String?) -> Unit = {},
    largeTitle: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val hazeEnabled by uiPreferences.hazeEnabled().collectAsStatePref()

    Scaffold(
        largeTopBar = largeTitle,
        topBar = { scrollBehavior ->
            val tab = tabs.getOrNull(state.currentPage) ?: tabs.getOrNull(0)
            val currentTitleRes = titleRes ?: tab?.titleRes
            if (currentTitleRes != null && largeTitle) {
                // main tab (Browse): big title, the page's summary and round buttons
                val searchEnabled = tab?.searchEnabled ?: false
                yomira.ui.TabHeader(
                    title = stringResource(currentTitleRes),
                    subtitle = tab?.subtitle,
                    scrollBehavior = scrollBehavior,
                    search = yomira.ui.TabHeaderSearch(
                        query = if (searchEnabled) searchQuery else null,
                        onQueryChange = onChangeSearchQuery,
                    ),
                    actions = {
                        if (searchEnabled) {
                            yomira.ui.HeaderButton(
                                icon = Icons.Outlined.Search,
                                contentDescription = stringResource(tachiyomi.i18n.MR.strings.action_search),
                                onClick = { onChangeSearchQuery("") },
                            )
                        }
                        yomira.ui.HeaderActions(tab?.actions ?: persistentListOf())
                    },
                )
            } else if (currentTitleRes != null) {
                val searchEnabled = tab?.searchEnabled ?: false

                SearchToolbar(
                    titleContent = {
                        AppBarTitle(
                            stringResource(currentTitleRes),
                            modifier = modifier,
                            null,
                            tab?.numberTitle ?: 0,
                        )
                    },
                    searchEnabled = searchEnabled,
                    searchQuery = if (searchEnabled) searchQuery else null,
                    onChangeSearchQuery = onChangeSearchQuery,
                    actions = { AppBarActions(tab?.actions ?: persistentListOf()) },
                    navigateUp = tab?.navigateUp,
                    scrollBehavior = scrollBehavior.takeIf { largeTitle },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        hazeEnabled = hazeEnabled,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // the bottom inset goes to the pages, so lists scroll under the floating bar
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
        ) {
            // capsule tabs instead of the Material tab row
            yomira.glass.CapsuleTabs(
                tabs = tabs.map { yomira.glass.CapsuleTab(stringResource(it.titleRes), it.badgeNumber) },
                selectedIndex = state.currentPage.coerceIn(0, maxOf(0, tabs.lastIndex)),
                onSelect = { index -> scope.launch { state.animateScrollToPage(index) } },
            )

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1,
            ) { page ->
                tabs[page].content(
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    snackbarHostState,
                )
            }
        }
    }
}

data class TabContent(
    val titleRes: StringResource,
    val badgeNumber: Int? = null,
    val searchEnabled: Boolean = false,
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    val content: @Composable (contentPadding: PaddingValues, snackbarHostState: SnackbarHostState) -> Unit,
    val numberTitle: Int = 0,
    val cancelAction: () -> Unit = {},
    val navigateUp: (() -> Unit)? = null,
    /** Short line under the big title while this page is shown ("12 sources"). */
    val subtitle: String? = null,
)

