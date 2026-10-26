package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            ExtensionDetailsScreenModel(
                pkgName = pkgName,
                context = context,
            )
        }
        val state by screenModel.state.collectAsState()
        var showDiagnostics by remember { mutableStateOf(false) }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        ExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            onClickEnableAll = { screenModel.toggleSources(true) },
            onClickDisableAll = { screenModel.toggleSources(false) },
            onClickClearCookies = screenModel::clearCookies,
            onClickUninstall = screenModel::uninstallExtension,
            onClickSource = screenModel::toggleSource,
            onClickDiagnostics = { showDiagnostics = true },
        )

        if (showDiagnostics) {
            val extension = state.extension
            yomira.extensions.ExtensionDiagnosticsDialog(
                title = extension?.name ?: pkgName,
                summary = null,
                buildReport = {
                    eu.kanade.tachiyomi.extension.util.ExtensionDiagnostics.build(context, extension, pkgName, null)
                },
                onUninstall = null,
                onDismissRequest = { showDiagnostics = false },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
