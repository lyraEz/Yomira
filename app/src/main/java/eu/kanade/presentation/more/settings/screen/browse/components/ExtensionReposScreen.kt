@file:JvmName("ExtensionReposScreenKt")

package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.browse.RepoScreenState
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.util.plus

@Composable
fun ExtensionReposScreen(
    state: RepoScreenState.Success,
    onClickCreate: () -> Unit,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onOpenDiscord: (ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit,
    onClickRefresh: () -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(MR.strings.label_extension_repos),
                scrollBehavior = scrollBehavior,
                actions = {
                    // a visible, focusable add action; the FAB is awkward to reach with a remote
                    IconButton(onClick = onClickCreate) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(MR.strings.action_add_repo),
                        )
                    }
                    IconButton(onClick = onClickRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(resource = MR.strings.action_webview_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isAdding) {
            LinearProgressIndicator(
                modifier = Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxWidth(),
            )
        }
        if (state.isEmpty) {
            EmptyScreen(
                MR.strings.information_empty_repos,
                modifier = Modifier.padding(paddingValues),
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_add_repo,
                        icon = Icons.Outlined.Add,
                        onClick = onClickCreate,
                    ),
                ),
            )
            return@Scaffold
        }

        ExtensionReposContent(
            repos = state.repos,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onOpenWebsite = onOpenWebsite,
            onOpenDiscord = onOpenDiscord,
            onClickDelete = onClickDelete,
            onToggleVisibility = onToggleVisibility,
        )
    }
}
