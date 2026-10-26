package yomira.update

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpdatePreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Also offer beta versions (pre-releases in Yomira-releases) when checking for updates. */
    fun betaUpdates() = store.getBoolean("yomira_beta_updates", false)
}
