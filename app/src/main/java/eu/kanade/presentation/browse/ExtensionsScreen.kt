package eu.kanade.presentation.browse

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import eu.kanade.tachiyomi.extension.model.ExtensionLoadFailure
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.core.common.i18n.stringResource
import yomira.i18n.YMR
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.ContainerStyle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.components.DotSeparatorNoSpaceText
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.screen.browse.ExtensionReposScreen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Magnet
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ExtensionScreen(
    state: ExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onOpenWebView: (Extension.Available) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
    // YMR -->
    onBuildDiagnostics: suspend (Extension?, String, String?) -> String = { _, _, _ -> "" },
    onUninstallPackage: (String) -> Unit = {},
    // YMR <--
) {
    val navigator = LocalNavigator.currentOrThrow
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val containerStyles by uiPreferences.containerStyles().collectAsState()
    val useContainer = remember(containerStyles) { ContainerStyle.BROWSE in containerStyles }

    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = !state.isLoading,
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.isEmpty -> {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.empty_screen
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.label_extension_repos,
                            icon = Icons.Outlined.Settings,
                            onClick = { navigator.push(ExtensionReposScreen()) },
                        ),
                    ),
                )
            }
            else -> {
                ExtensionContent(
                    state = state,
                    contentPadding = contentPadding,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onOpenWebView = onOpenWebView,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                    useContainer = useContainer,
                    // YMR -->
                    onBuildDiagnostics = onBuildDiagnostics,
                    onUninstallPackage = onUninstallPackage,
                    // YMR <--
                )
            }
        }
    }
}

@Composable
private fun ExtensionContent(
    state: ExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onOpenWebView: (Extension.Available) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    useContainer: Boolean,
    // YMR -->
    onBuildDiagnostics: suspend (Extension?, String, String?) -> String,
    onUninstallPackage: (String) -> Unit,
    // YMR <--
) {
    val context = LocalContext.current
    var trustState by remember { mutableStateOf<Extension.Untrusted?>(null) }
    // YMR -->
    var diagnosticsRequest by remember { mutableStateOf<DiagnosticsRequest?>(null) }
    // YMR <--
    val installGranted = rememberRequestPackageInstallsPermissionState(initialValue = true)

    val handleItemClick: (Extension) -> Unit = remember(onInstallExtension, onOpenExtension, state.items) {
        click@{ ext ->
            // YMR --> a failed install opens its explanation instead of retrying blindly
            val failedItem = state.items.values.flatten()
                .find { it.extension.pkgName == ext.pkgName && it.installStep == InstallStep.Error }
            if (failedItem != null) {
                diagnosticsRequest = DiagnosticsRequest(
                    extension = ext,
                    pkgName = ext.pkgName.substringBefore("-"),
                    title = ext.name,
                    summary = failedItem.installError,
                    installError = failedItem.installError,
                )
                return@click
            }
            // YMR <--
            when (ext) {
                is Extension.Available -> onInstallExtension(ext)
                is Extension.Installed -> onOpenExtension(ext)
                is Extension.Untrusted -> {
                    trustState = ext
                }
            }
        }
    }
    val handleItemSecondaryAction: (Extension) -> Unit = remember(onOpenWebView, onOpenExtension) {
        { ext ->
            when (ext) {
                is Extension.Available -> onOpenWebView(ext)
                is Extension.Installed -> onOpenExtension(ext)
                else -> {}
            }
        }
    }
    val handleItemAction: (Extension) -> Unit = remember(onInstallExtension, onUpdateExtension, onOpenExtension) {
        { ext ->
            when (ext) {
                is Extension.Available -> onInstallExtension(ext)
                is Extension.Installed -> {
                    if (ext.hasUpdate) {
                        onUpdateExtension(ext)
                    } else {
                        onOpenExtension(ext)
                    }
                }
                is Extension.Untrusted -> {
                    trustState = ext
                }
            }
        }
    }

    FastScrollLazyColumn(
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp
        ),
    ) {
        if (!installGranted && state.installer?.requiresSystemPermission == true) {
            item(key = "extension-permissions-warning") {
                WarningBanner(
                    textRes = MR.strings.ext_permission_install_apps_warning,
                    modifier = Modifier.clickable {
                        context.launchRequestPackageInstallsPermission()
                    },
                )
            }
        }

        state.items.forEach { (header, items) ->
        item(
            contentType = "header",
            key = "extensionHeader-${header.hashCode()}",
        ) {
            when (header) {
                is ExtensionUiModel.Header.Resource -> {
                    val action: @Composable RowScope.() -> Unit =
                        if (header.textRes == MR.strings.ext_updates_pending) {
                            {
                                Button(onClick = { onClickUpdateAll() }) {
                                    Text(
                                        text = stringResource(MR.strings.ext_update_all),
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                            }
                        } else {
                            {}
                        }
                    ExtensionHeader(
                        textRes = header.textRes,
                        modifier = Modifier,
                        action = action,
                    )
                }
                is ExtensionUiModel.Header.Text -> {
                    ExtensionHeader(
                        text = header.text,
                        modifier = Modifier,
                    )
                }
            }
        }

        itemsIndexed(
            items = items,
            key = { _, item -> "extension-${item.extension.pkgName}-${item.repoName}" },
            contentType = { _, _ -> "extension_item" },
        ) { index, item ->
            if (useContainer) {
                val isFirst = index == 0
                val isLast = index == items.lastIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(
                        topStart = if (isFirst) 16.dp else 0.dp,
                        topEnd = if (isFirst) 16.dp else 0.dp,
                        bottomStart = if (isLast) 16.dp else 0.dp,
                        bottomEnd = if (isLast) 16.dp else 0.dp,
                    ),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    ExtensionItem(
                        modifier = Modifier,
                        item = item,
                        onClickItem = handleItemClick,
                        onLongClickItem = onLongClickItem,
                        onClickItemSecondaryAction = handleItemSecondaryAction,
                        onClickItemCancel = onClickItemCancel,
                        onClickItemAction = handleItemAction,
                    )
                }
            } else {
                ExtensionItem(
                    modifier = Modifier,
                    item = item,
                    onClickItem = handleItemClick,
                    onLongClickItem = onLongClickItem,
                    onClickItemSecondaryAction = handleItemSecondaryAction,
                    onClickItemCancel = onClickItemCancel,
                    onClickItemAction = handleItemAction,
                )
            }
        }
    }
        // YMR --> APKs that are installed but did not load, with the reason
        if (state.failures.isNotEmpty()) {
            item(contentType = "header", key = "extensionHeader-failures") {
                ExtensionHeader(text = stringResource(YMR.strings.ext_problems))
            }
            itemsIndexed(
                items = state.failures,
                key = { _, failure -> "extension-failure-${failure.pkgName}" },
                contentType = { _, _ -> "extension_failure" },
            ) { _, failure ->
                val summary = failure.describe()
                ExtensionFailureItem(
                    failure = failure,
                    onClick = {
                        diagnosticsRequest = DiagnosticsRequest(
                            extension = null,
                            pkgName = failure.pkgName,
                            title = failure.name,
                            summary = summary,
                            installError = null,
                            canUninstall = failure.isShared,
                        )
                    },
                )
            }
        }
        // YMR <--
    }
    // YMR -->
    diagnosticsRequest?.let { request ->
        ExtensionDiagnosticsDialog(
            request = request,
            buildDiagnostics = { onBuildDiagnostics(request.extension, request.pkgName, request.installError) },
            onUninstall = if (request.canUninstall) {
                {
                    onUninstallPackage(request.pkgName)
                    diagnosticsRequest = null
                }
            } else {
                null
            },
            onDismissRequest = { diagnosticsRequest = null },
        )
    }
    // YMR <--
    if (trustState != null) {
        ExtensionTrustDialog(
            onClickConfirm = {
                val untrusted = trustState ?: return@ExtensionTrustDialog
                onTrustExtension(untrusted)
                trustState = null
            },
            onClickDismiss = {
                val untrusted = trustState ?: return@ExtensionTrustDialog
                onUninstallExtension(untrusted)
                trustState = null
            },
            onDismissRequest = {
                trustState = null
            },
        )
    }
}

@Composable
private fun ExtensionItem(
    item: ExtensionUiModel.Item,
    onClickItem: (Extension) -> Unit,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onClickItemAction: (Extension) -> Unit,
    onClickItemSecondaryAction: (Extension) -> Unit,
    modifier: Modifier = Modifier,
) {
    eu.kanade.tachiyomi.util.system.PerformanceBenchmarkHelper.countRecomposition("ExtensionItem")
    val (extension, installStep) = item
    val onItemClick = remember(onClickItem, extension.pkgName) { { onClickItem(extension) } }
    val onItemLongClick = remember(onLongClickItem, extension.pkgName) { { onLongClickItem(extension) } }

    val iconContent: @Composable RowScope.() -> Unit = remember(extension.pkgName, installStep) {
        {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                    )
                }

                val padding = if (idle) 0.dp else 8.dp
                ExtensionIcon(
                    extension = extension,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(padding),
                )
            }
        }
    }

    val actionContent: @Composable RowScope.() -> Unit = remember(extension, installStep, onClickItemCancel, onClickItemAction, onClickItemSecondaryAction) {
        {
            ExtensionItemActions(
                extension = extension,
                installStep = installStep,
                onClickItemCancel = onClickItemCancel,
                onClickItemAction = onClickItemAction,
                onClickItemSecondaryAction = onClickItemSecondaryAction,
            )
        }
    }

    val contentBlock: @Composable RowScope.() -> Unit = remember(extension, installStep, item.repoName) {
        {
            ExtensionItemContent(
                extension = extension,
                installStep = installStep,
                repoName = item.repoName,
                modifier = Modifier.weight(1f),
            )
        }
    }

    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onItemClick,
        onLongClickItem = onItemLongClick,
        icon = iconContent,
        action = actionContent,
        content = contentBlock,
    )
}

@Composable
private fun ExtensionItemContent(
    extension: Extension,
    installStep: InstallStep,
    repoName: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = MaterialTheme.padding.medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (extension.isTorrent) {
                Icon(
                    imageVector = CustomIcons.Magnet,
                    contentDescription = "(Torrent)",
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = extension.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Won't look good but it's not like we can ellipsize overflowing content
        FlowRow(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                if (extension is Extension.Installed && extension.lang.isNotEmpty()) {
                    Text(
                        text = LocaleHelper.getSourceDisplayName(
                            extension.lang,
                            LocalContext.current,
                        ),
                    )
                }

                if (extension.versionName.isNotEmpty()) {
                    Text(
                        text = extension.versionName,
                    )
                }

                Text(
                    text = repoName ?: "@?",
                    color = MaterialTheme.colorScheme.primary,
                )

                val warning = when {
                    extension is Extension.Untrusted -> MR.strings.ext_untrusted
                    extension is Extension.Installed && extension.isObsolete -> MR.strings.ext_obsolete
                    else -> null
                }
                if (warning != null) {
                    Text(
                        text = stringResource(warning).uppercase(),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (extension.contentWarning == eu.kanade.tachiyomi.extension.model.ContentWarning.MIXED) {
                    Text(
                        text = "MIXED",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                } else if (extension !is Extension.Installed && extension.isNsfw) {
                    Text(
                        text = "18+",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                if (!installStep.isCompleted()) {
                    DotSeparatorNoSpaceText()
                    Text(
                        text = when (installStep) {
                            InstallStep.Pending -> stringResource(MR.strings.ext_pending)
                            InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
                            // YMR -->
                            InstallStep.AwaitingConfirmation -> stringResource(YMR.strings.ext_awaiting_confirmation)
                            // YMR <--
                            InstallStep.Installing -> stringResource(MR.strings.ext_installing)
                            else -> error("Must not show non-install process text")
                        },
                    )
                }
                // YMR -->
                if (installStep == InstallStep.Error) {
                    DotSeparatorNoSpaceText()
                    Text(
                        text = stringResource(YMR.strings.ext_install_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (installStep.isCompleted() && extension is Extension.Installed) {
                    DotSeparatorNoSpaceText()
                    Text(
                        text = if (extension.sources.isEmpty()) {
                            stringResource(YMR.strings.ext_loaded_no_sources)
                        } else {
                            stringResource(YMR.strings.ext_loaded_sources, extension.sources.size)
                        },
                    )
                }
                // YMR <--
            }
        }
    }
}

@Composable
private fun ExtensionItemActions(
    extension: Extension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
    onClickItemCancel: (Extension) -> Unit = {},
    onClickItemAction: (Extension) -> Unit = {},
    onClickItemSecondaryAction: (Extension) -> Unit = {},
) {
    val isIdle = installStep.isCompleted()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        when {
            !isIdle -> {
                IconButton(onClick = { onClickItemCancel(extension) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(MR.strings.action_cancel),
                    )
                }
            }
            installStep == InstallStep.Error -> {
                IconButton(onClick = { onClickItemAction(extension) }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(MR.strings.action_retry),
                    )
                }
            }
            else -> {
                when (extension) {
                    is Extension.Installed -> {
                        IconButton(onClick = { onClickItemSecondaryAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(MR.strings.action_settings),
                            )
                        }

                        if (extension.hasUpdate) {
                            IconButton(onClick = { onClickItemAction(extension) }) {
                                Icon(
                                    imageVector = Icons.Outlined.GetApp,
                                    contentDescription = stringResource(MR.strings.ext_update),
                                )
                            }
                        }
                    }
                    is Extension.Untrusted -> {
                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = stringResource(MR.strings.ext_trust),
                            )
                        }
                    }
                    is Extension.Available -> {
                        if (extension.sources.isNotEmpty()) {
                            IconButton(
                                onClick = { onClickItemSecondaryAction(extension) },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Public,
                                    contentDescription = stringResource(MR.strings.action_open_in_web_view),
                                )
                            }
                        }

                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_install),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionHeader(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    ExtensionHeader(
        text = stringResource(textRes),
        modifier = modifier,
        action = action,
    )
}

@Composable
private fun ExtensionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.header,
        )
        action()
    }
}

@Composable
private fun ExtensionTrustDialog(
    onClickConfirm: () -> Unit,
    onClickDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.untrusted_extension))
        },
        text = {
            Text(text = stringResource(MR.strings.untrusted_extension_message))
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(MR.strings.ext_trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onClickDismiss) {
                Text(text = stringResource(MR.strings.ext_uninstall))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

private const val TORRENT_ICON = "torrentIcon"

// YMR -->
private data class DiagnosticsRequest(
    val extension: Extension?,
    val pkgName: String,
    val title: String,
    val summary: String?,
    val installError: String?,
    val canUninstall: Boolean = false,
)

@Composable
private fun ExtensionLoadFailure.describe(): String = when (reason) {
    ExtensionLoadFailure.Reason.MISSING_VERSION -> stringResource(YMR.strings.ext_failure_missing_version)
    ExtensionLoadFailure.Reason.LIB_VERSION_UNSUPPORTED ->
        stringResource(YMR.strings.ext_failure_lib_version, libVersion?.toString() ?: "?")
    ExtensionLoadFailure.Reason.UNSIGNED -> stringResource(YMR.strings.ext_failure_unsigned)
    ExtensionLoadFailure.Reason.NSFW_FILTERED ->
        stringResource(YMR.strings.ext_failure_nsfw) + "\n" + stringResource(YMR.strings.ext_nsfw_settings_hint)
    ExtensionLoadFailure.Reason.CLASS_LOAD_FAILED -> stringResource(YMR.strings.ext_failure_class_load)
    ExtensionLoadFailure.Reason.MANIFEST_INVALID -> stringResource(YMR.strings.ext_failure_manifest)
}

@Composable
private fun ExtensionFailureItem(
    failure: ExtensionLoadFailure,
    onClick: () -> Unit,
) {
    BaseBrowseItem(
        onClickItem = onClick,
        onLongClickItem = onClick,
        icon = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        action = {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(YMR.strings.ext_diagnostics),
                )
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.padding.medium),
            ) {
                Text(
                    text = failure.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = listOfNotNull(failure.versionName, failure.describe().lineSequence().first())
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun ExtensionDiagnosticsDialog(
    request: DiagnosticsRequest,
    buildDiagnostics: suspend () -> String,
    onUninstall: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var report by remember(request) { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        report = runCatching { buildDiagnostics() }.getOrElse { it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = request.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                request.summary?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = report ?: "…",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    report?.let {
                        context.copyToClipboard(context.stringResource(YMR.strings.ext_diagnostics), it)
                    }
                },
            ) {
                Text(text = stringResource(YMR.strings.ext_diagnostics_copy))
            }
        },
        dismissButton = {
            Row {
                if (onUninstall != null) {
                    TextButton(onClick = onUninstall) {
                        Text(text = stringResource(MR.strings.ext_uninstall))
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_close))
                }
            }
        },
    )
}
// YMR <--
