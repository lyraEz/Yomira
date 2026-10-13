package yomira.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings → Appearance → Liquid glass. */
class GlassPreferences(private val store: PreferenceStore = Injekt.get()) {

    fun mode() = store.getEnum("yomira_glass_mode", GlassQualityMode.AUTO)

    fun blurRadius() = store.getFloat("yomira_glass_blur", DEFAULT.blurRadius.value)

    fun refractionHeight() = store.getFloat("yomira_glass_refraction_height", DEFAULT.refractionHeight)

    fun refractionAmount() = store.getFloat("yomira_glass_refraction_amount", DEFAULT.refractionAmount)

    fun depthEffect() = store.getBoolean("yomira_glass_depth", DEFAULT.depthEffect)

    fun chromaticAberration() = store.getBoolean("yomira_glass_chromatic", true)

    fun vibrance() = store.getFloat("yomira_glass_vibrance", DEFAULT.vibrance)

    fun brightness() = store.getFloat("yomira_glass_brightness", DEFAULT.brightness)

    fun tintAlpha() = store.getFloat("yomira_glass_tint", DEFAULT.tintAlpha)

    fun highlight() = store.getFloat("yomira_glass_highlight", DEFAULT.highlightAlpha)

    fun shadow() = store.getBoolean("yomira_glass_shadow", DEFAULT.shadow)

    fun pressScale() = store.getFloat("yomira_glass_press_scale", DEFAULT.pressScale)

    /** Puts every knob back to its default (the quality mode is kept). */
    fun resetLook() {
        listOf(
            blurRadius(), refractionHeight(), refractionAmount(), vibrance(), brightness(), tintAlpha(),
            highlight(), pressScale(),
        ).forEach { it.delete() }
        depthEffect().delete()
        chromaticAberration().delete()
        shadow().delete()
    }

    companion object {
        val DEFAULT = GlassStyle()
    }
}

/** Reads the glass settings and provides the resolved [GlassStyle] to everything below. */
@Composable
fun ProvideGlassStyle(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { GlassPreferences() }
    val mode by prefs.mode().collectAsState()
    val blur by prefs.blurRadius().collectAsState()
    val height by prefs.refractionHeight().collectAsState()
    val amount by prefs.refractionAmount().collectAsState()
    val depth by prefs.depthEffect().collectAsState()
    val chromatic by prefs.chromaticAberration().collectAsState()
    val vibrance by prefs.vibrance().collectAsState()
    val brightness by prefs.brightness().collectAsState()
    val tint by prefs.tintAlpha().collectAsState()
    val highlight by prefs.highlight().collectAsState()
    val shadow by prefs.shadow().collectAsState()
    val press by prefs.pressScale().collectAsState()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val quality = remember(mode) { GlassDevice.resolve(context, mode) }
    val style = remember(quality, blur, height, amount, depth, chromatic, vibrance, brightness, tint, highlight, shadow, press, dark) {
        GlassStyle(
            quality = quality,
            blurRadius = blur.dp,
            refractionHeight = height,
            refractionAmount = amount,
            depthEffect = depth,
            chromaticAberration = chromatic,
            vibrance = vibrance,
            brightness = brightness,
            tintAlpha = tint,
            highlightAlpha = highlight,
            shadow = shadow,
            pressScale = press,
            dark = dark,
        )
    }
    CompositionLocalProvider(LocalGlassStyle provides style, content = content)
}
