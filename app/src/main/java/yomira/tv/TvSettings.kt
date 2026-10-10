package yomira.tv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * TV detection and the settings that only matter when the app is driven by a remote.
 */
object TvSettings {

    enum class Mode { AUTO, TV, PHONE }

    private val store: PreferenceStore by lazy { Injekt.get() }

    /** Lets the user force the TV or the touch interface when detection guesses wrong. */
    fun mode() = store.getEnum("yomira_tv_mode", Mode.AUTO)

    /** Percentage of the screen kept clear on each edge for TVs that overscan. */
    fun safeAreaPercent() = store.getInt("yomira_tv_safe_area_percent", DEFAULT_SAFE_AREA_PERCENT)

    /** Text scale applied on TV, in percent of the system font scale. */
    fun textScalePercent() = store.getInt("yomira_tv_text_scale_percent", DEFAULT_TEXT_SCALE_PERCENT)

    /** Turns the MENU key into a long press of OK, for actions that need a long press. */
    fun menuKeyLongPress() = store.getBoolean("yomira_tv_menu_long_press", true)

    fun isTv(context: Context): Boolean = when (mode().get()) {
        Mode.TV -> true
        Mode.PHONE -> false
        Mode.AUTO -> detect(context)
    }

    /**
     * Fire TV reports UI_MODE_TYPE_TELEVISION and the amazon.hardware.fire_tv feature; Android TV
     * reports leanback. Boxes that report neither are recognised by having no touchscreen and no
     * system document picker.
     */
    fun detect(context: Context): Boolean {
        val pm = context.packageManager
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (pm.hasSystemFeature(FIRE_TV_FEATURE)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        val noTouch = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val noPicker = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .resolveActivity(pm) == null
        return noTouch && noPicker
    }

    /** Whether the system document picker exists; many TV boxes ship without one. */
    fun hasDocumentPicker(context: Context): Boolean =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .resolveActivity(context.packageManager) != null

    const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
    private const val DEFAULT_SAFE_AREA_PERCENT = 5
    private const val DEFAULT_TEXT_SCALE_PERCENT = 115
}
