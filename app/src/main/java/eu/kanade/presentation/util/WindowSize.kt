package eu.kanade.presentation.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TabletUiMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
@ReadOnlyComposable
fun isTabletUi(): Boolean {
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val tabletUiMode = Injekt.get<UiPreferences>().tabletUiMode().get()
    return when (tabletUiMode) {
        TabletUiMode.AUTOMATIC -> {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                configuration.screenWidthDp >= 600 || configuration.smallestScreenWidthDp >= 600
            } else {
                configuration.smallestScreenWidthDp >= 700
            }
        }
        TabletUiMode.ALWAYS -> true
        TabletUiMode.LANDSCAPE -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        TabletUiMode.NEVER -> false
    }
}
