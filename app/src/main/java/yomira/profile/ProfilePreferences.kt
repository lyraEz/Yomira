package yomira.profile

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Name and photo shown at the top of the statistics screen. Stays on the phone. */
class ProfilePreferences(private val store: PreferenceStore = Injekt.get()) {
    fun profilePhotoUri() = store.getString("profile_photo_uri", "")
    fun displayName() = store.getString("display_name", "Anime Explorer")
}
