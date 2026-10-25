package yomira.anime4k

import android.content.Context

/** The six official Anime4K v4 modes. */
enum class Anime4KMode(val key: String, val label: String) {
    A("A", "A"),
    B("B", "B"),
    C("C", "C"),
    AA("AA", "A+A"),
    BB("BB", "B+B"),
    CA("CA", "C+A"),
}

/** "Fast" uses the M and S networks, "High" (HQ in the official files) the VL and M ones. */
enum class Anime4KQuality(val key: String, val big: String, val small: String) {
    FAST("FAST", "M", "S"),
    HIGH("HQ", "VL", "M"),
}

/**
 * One official preset: the same shader lists as bloc97/Anime4K's mpv input.conf (CTRL+1 to CTRL+6,
 * Fast and HQ). Mirrored in scripts/yomira/anime4k/presets.py, which checks them against libplacebo.
 */
data class Anime4KPreset(val mode: Anime4KMode, val quality: Anime4KQuality) {

    val id: String get() = "${mode.key}_${quality.key}"

    val label: String get() = "${mode.label} ${if (quality == Anime4KQuality.HIGH) "High" else "Fast"}"

    /** Shader names without the "Anime4K_" prefix and ".glsl" suffix, in pipeline order. */
    val shaders: List<String> get() {
        val big = quality.big
        val small = quality.small
        val pre = listOf("AutoDownscalePre_x2", "AutoDownscalePre_x4")
        return when (mode) {
            Anime4KMode.A ->
                listOf("Clamp_Highlights", "Restore_CNN_$big", "Upscale_CNN_x2_$big") + pre + "Upscale_CNN_x2_$small"
            Anime4KMode.B ->
                listOf("Clamp_Highlights", "Restore_CNN_Soft_$big", "Upscale_CNN_x2_$big") + pre + "Upscale_CNN_x2_$small"
            Anime4KMode.C ->
                listOf("Clamp_Highlights", "Upscale_Denoise_CNN_x2_$big") + pre + "Upscale_CNN_x2_$small"
            Anime4KMode.AA ->
                listOf("Clamp_Highlights", "Restore_CNN_$big", "Upscale_CNN_x2_$big", "Restore_CNN_$small") + pre +
                    "Upscale_CNN_x2_$small"
            Anime4KMode.BB ->
                listOf("Clamp_Highlights", "Restore_CNN_Soft_$big", "Upscale_CNN_x2_$big") + pre +
                    listOf("Restore_CNN_Soft_$small", "Upscale_CNN_x2_$small")
            Anime4KMode.CA ->
                listOf("Clamp_Highlights", "Upscale_Denoise_CNN_x2_$big") + pre +
                    listOf("Restore_CNN_$small", "Upscale_CNN_x2_$small")
        }
    }

    /** Reads the shader sources from the APK (assets/anime4k, the unmodified official files). */
    fun load(context: Context): List<String> = shaders.map { name ->
        context.assets.open("anime4k/Anime4K_$name.glsl").bufferedReader().use { it.readText() }
    }

    companion object {
        /** Raise when the engine or the shaders change: older cached results are not reused. */
        const val ENGINE_VERSION = 1

        val DEFAULT = Anime4KPreset(Anime4KMode.A, Anime4KQuality.FAST)

        val ALL: List<Anime4KPreset> = Anime4KQuality.entries.flatMap { quality ->
            Anime4KMode.entries.map { Anime4KPreset(it, quality) }
        }

        fun fromId(id: String?): Anime4KPreset? = ALL.firstOrNull { it.id == id }
    }
}
