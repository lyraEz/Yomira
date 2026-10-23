package yomira.home

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavConfig
import eu.kanade.domain.ui.model.NavConfigValidator
import eu.kanade.domain.ui.model.NavItem
import eu.kanade.domain.ui.model.StartScreen
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Once per install: puts the "Início" tab first and makes it the start screen for people who had
 * already arranged their tabs (new installs get it from the defaults).
 */
object HomeTabSetup {

    fun ensure(ui: UiPreferences = Injekt.get(), store: PreferenceStore = Injekt.get()) {
        val done = store.getBoolean("yomira_home_tab_added", false)
        if (done.get()) return
        runCatching {
            val visible = ui.bottomNavTabs()
            val hidden = ui.bottomNavHiddenTabs()
            if (visible.isSet() && NavItem.HOME.id !in visible.get()) {
                val config = NavConfigValidator.validate(
                    NavConfig(
                        visibleTabs = (listOf(NavItem.HOME.id) + visible.get()).toImmutableList(),
                        hiddenTabs = hidden.get().filter { it != NavItem.HOME.id }.toImmutableList(),
                    ),
                )
                visible.set(config.visibleTabs)
                hidden.set(config.hiddenTabs)
            }
            if (ui.startScreen().isSet()) ui.startScreen().set(StartScreen.HOME)
        }
        done.set(true)
    }
}
