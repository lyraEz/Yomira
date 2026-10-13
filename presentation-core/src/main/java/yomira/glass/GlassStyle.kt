package yomira.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the liquid glass pipeline a device runs.
 *
 * - [OFF]: no live backdrop at all; overlays are translucent tinted surfaces with a light rim.
 * - [BATTERY]: live blur and vibrance (Android 12+), no refraction, no shadows.
 * - [BALANCED]: blur, vibrance and edge refraction (refraction needs Android 13+).
 * - [ULTRA]: everything, including the depth bulge, rainbow edges on small controls and shadows.
 */
enum class GlassQuality { OFF, BATTERY, BALANCED, ULTRA }

/** Quality choice stored in the settings; [AUTO] resolves through [GlassDevice.recommendedQuality]. */
enum class GlassQualityMode { AUTO, OFF, BATTERY, BALANCED, ULTRA }

/**
 * Every knob of the glass look. Values are what the user picked in Settings → Liquid glass;
 * [quality] is already resolved for this device (never above what the hardware supports).
 */
@Immutable
data class GlassStyle(
    val quality: GlassQuality = GlassQuality.BALANCED,
    /** Background blur radius behind glass surfaces. */
    val blurRadius: Dp = 10.dp,
    /** Width of the refracting edge, as a fraction of the surface's smaller side (kept below 0.5). */
    val refractionHeight: Float = 0.25f,
    /** How far the edge bends the background, as a fraction of the surface's smaller side. */
    val refractionAmount: Float = 0.5f,
    /** Adds a lens bulge towards the centre (looks best on sheets and dialogs). */
    val depthEffect: Boolean = false,
    /** Splits colours at the edge like a prism. Expensive: only small controls use it. */
    val chromaticAberration: Boolean = false,
    /** Saturation boost of what is seen through the glass (1 = none). */
    val vibrance: Float = 1.5f,
    /** Brightness offset of what is seen through the glass. */
    val brightness: Float = 0.04f,
    /** Opacity of the tint layer that keeps text readable. */
    val tintAlpha: Float = 0.28f,
    /** Opacity of the light rim along the edges. */
    val highlightAlpha: Float = 0.9f,
    /** Soft drop shadow under floating surfaces. */
    val shadow: Boolean = true,
    /** Scale reached when a glass control is pressed. */
    val pressScale: Float = 1.08f,
    /** Darker glass for dark themes, frosted white for light themes. */
    val dark: Boolean = true,
) {
    /** Live blur needs RenderEffect (Android 12, API 31). */
    val liveBlur: Boolean
        get() = quality != GlassQuality.OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Edge refraction needs RuntimeShader (Android 13, API 33). */
    val refraction: Boolean
        get() = liveBlur &&
            quality >= GlassQuality.BALANCED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val shadows: Boolean
        get() = shadow && quality >= GlassQuality.BALANCED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    companion object {
        val Default = GlassStyle()
    }
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Default }

object GlassDevice {

    /** Highest quality this device should run by default. */
    fun recommendedQuality(context: Context): GlassQuality {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return GlassQuality.OFF
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalGb = memory.totalMem / (1024.0 * 1024 * 1024)
        val only32Bit = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        val base = when {
            am?.isLowRamDevice == true || only32Bit || totalGb < 3.0 -> GlassQuality.BATTERY
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || totalGb < 6.0 -> GlassQuality.BALANCED
            else -> GlassQuality.ULTRA
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (power?.isPowerSaveMode == true && base > GlassQuality.BATTERY) {
            GlassQuality.entries[base.ordinal - 1]
        } else {
            base
        }
    }

    /** Resolves the user's choice, never exceeding what the platform can draw. */
    fun resolve(context: Context, mode: GlassQualityMode): GlassQuality {
        val recommended = recommendedQuality(context)
        val wanted = when (mode) {
            GlassQualityMode.AUTO -> return recommended
            GlassQualityMode.OFF -> GlassQuality.OFF
            GlassQualityMode.BATTERY -> GlassQuality.BATTERY
            GlassQualityMode.BALANCED -> GlassQuality.BALANCED
            GlassQualityMode.ULTRA -> GlassQuality.ULTRA
        }
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) GlassQuality.OFF else wanted
    }
}
