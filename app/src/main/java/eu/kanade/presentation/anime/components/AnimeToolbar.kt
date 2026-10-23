package eu.kanade.presentation.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.DownloadAction
import yomira.glass.glass
import yomira.glass.glassBarFaded
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.components.UpIcon
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun AnimeToolbar(
    title: String,
    titleAlphaProvider: () -> Float,
    hasFilters: Boolean,
    onBackClicked: () -> Unit,
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: () -> Unit,
    onClickMigrate: (() -> Unit)?,
    onClickEditNotes: () -> Unit,
    onClickSettings: (() -> Unit)?,
    onClickSuggestions: (() -> Unit)?,
    // SY -->
    onClickEditInfo: (() -> Unit)?,
    onClickClearAnime: (() -> Unit)?,
    onClickOpenAnimeFolder: (() -> Unit)?,
    onClickMerge: (() -> Unit)?,
    // SY <--
    // Anime only
    changeAnimeSkipIntro: (() -> Unit)?,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlphaProvider: () -> Float = titleAlphaProvider,
) {
    val statusBarPadding = WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues()
    val topPadding = statusBarPadding.calculateTopPadding()
    var stableTopPadding by remember { mutableStateOf(0.dp) }
    if (topPadding > 0.dp && stableTopPadding == 0.dp) {
        stableTopPadding = topPadding
    }

    // Capture the surface color during composition; alpha is applied during draw only.
    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    // YMR --> liquid glass over the cover: the bar fades in as glass, buttons float in glass orbs
    val backdrop = yomira.glass.LocalGlassBackdrop.current
    val glassStyle = yomira.glass.LocalGlassStyle.current
    val barTint = (glassStyle.tintAlpha + 0.32f).coerceAtMost(0.88f)
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        if (backdrop != null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = backgroundAlphaProvider() }
                    .glassBarFaded(backdrop, glassStyle, barTint),
            )
        }
    Column(
        modifier = Modifier
            .drawBehind {
                // drawBehind reads backgroundAlphaProvider() in the draw phase — not
                // composition — so the Column is NOT recomposed on every animation frame
                // of the transparent-to-solid toolbar transition. Only the background
                // fades; toolbar icons and content remain fully visible at all times.
                if (backdrop == null) drawRect(color = bgColor.copy(alpha = backgroundAlphaProvider()))
            }
            .padding(top = stableTopPadding),
    ) {
        val isActionMode = actionModeCounter > 0
        TopAppBar(
            title = {
                Text(
                    text = if (isActionMode) actionModeCounter.toString() else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (isActionMode) 1f else titleAlphaProvider()
                    },
                )
            },
            navigationIcon = {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(start = 6.dp)
                        .glass(androidx.compose.foundation.shape.CircleShape, small = true),
                ) {
                    IconButton(onClick = onBackClicked) {
                        UpIcon(navigationIcon = Icons.Outlined.Close.takeIf { isActionMode })
                    }
                }
            },
            actions = {
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .padding(end = 8.dp)
                        .glass(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50), small = true),
                ) {
                if (isActionMode) {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = Icons.Outlined.SelectAll,
                                onClick = onSelectAll,
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = Icons.Outlined.FlipToBack,
                                onClick = onInvertSelection,
                            ),
                        ),
                    )
                } else {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    if (onClickDownload != null) {
                        val onDismissRequest = { downloadExpanded = false }
                        DownloadDropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = onDismissRequest,
                            onDownloadClicked = onClickDownload,
                        )
                    }

                    val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
                    AppBarActions(
                        actions = persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (onClickDownload != null) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.manga_download),
                                            icon = Icons.Outlined.Download,
                                            onClick = { downloadExpanded = !downloadExpanded },
                                        ),
                                    )
                                }
                                add(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_filter),
                                        icon = Icons.Outlined.FilterList,
                                        iconTint = filterTint,
                                        onClick = onClickFilter,
                                    ),
                                )
                                if (onClickRefresh != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_webview_refresh),
                                            onClick = onClickRefresh,
                                        ),
                                    )
                                }
                                if (onClickEditCategory != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_edit_categories),
                                            onClick = onClickEditCategory,
                                        ),
                                    )
                                }
                                if (onClickMigrate != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_migrate),
                                            onClick = onClickMigrate,
                                        ),
                                    )
                                }
                                if (onClickShare != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_share),
                                            onClick = onClickShare,
                                        ),
                                    )
                                }
                                add(
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_edit_notes),
                                        onClick = onClickEditNotes,
                                    ),
                                )
                                // SY -->
                                if (onClickMerge != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.merge_settings),
                                            onClick = onClickMerge,
                                        ),
                                    )
                                }
                                if (onClickEditInfo != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_edit_info),
                                            onClick = onClickEditInfo,
                                        ),
                                    )
                                }
                                // YMR --> pick the right TMDB show, season and numbering
                                yomira.metadata.LocalTmdbMatchAction.current?.let { openTmdbMatch ->
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(yomira.i18n.YMR.strings.tmdb_match_title),
                                            onClick = openTmdbMatch,
                                        ),
                                    )
                                }
                                // YMR <--
                                if (onClickOpenAnimeFolder != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(KMR.strings.action_open_folder),
                                            onClick = onClickOpenAnimeFolder,
                                        ),
                                    )
                                }
                                if (onClickClearAnime != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_clear_anime),
                                            onClick = onClickClearAnime,
                                        ),
                                    )
                                }
                                if (onClickSuggestions != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(tachiyomi.i18n.sy.SYMR.strings.az_recommends),
                                            onClick = onClickSuggestions,
                                        ),
                                    )
                                }
                                if (onClickSettings != null) {
                                    add(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.settings),
                                            onClick = onClickSettings,
                                        ),
                                    )
                                }
                                // SY <--
                            }
                            .build(),
                    )
                }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
    }
    // YMR <--
}
