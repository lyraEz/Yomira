package yomira.anime4k

import android.content.Context

/** How much work an enhancer does: Lite is the quickest, High the slowest and strongest. */
enum class EnhanceQuality(val key: String) {
    LITE("LITE"),
    FAST("FAST"),
    HIGH("HQ"),
}

/**
 * One way to enhance a video: an Anime4K preset or a light AI upscaler. [id] identifies it in jobs
 * and settings; every enhancer is a list of mpv user shaders the engine runs unchanged.
 */
sealed interface Enhancer {
    val id: String
    val quality: EnhanceQuality

    /** Shader files in the APK, in pipeline order. */
    val assets: List<String>

    /**
     * The light AI networks only double the picture: they do nothing unless the output is at least
     * 1.3 times the source. Anime4K also restores lines at the source size.
     */
    val upscaleOnly: Boolean

    fun load(context: Context): List<String> = assets.map { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    companion object {
        /** Raise when the engine or the shaders change: older cached results are not reused. */
        const val ENGINE_VERSION = 2

        val DEFAULT: Enhancer = Anime4KPreset(Anime4KMode.A, EnhanceQuality.FAST)

        val ALL: List<Enhancer> by lazy { Anime4KPreset.ALL + LightAiPreset.ALL }

        fun fromId(id: String?): Enhancer? = ALL.firstOrNull { it.id == id }
    }
}

/** The six official Anime4K v4 modes. */
enum class Anime4KMode(val key: String, val label: String) {
    A("A", "A"),
    B("B", "B"),
    C("C", "C"),
    AA("AA", "A+A"),
    BB("BB", "B+B"),
    CA("CA", "C+A"),
}

/**
 * One official Anime4K preset: the same shader lists as bloc97/Anime4K's mpv input.conf (CTRL+1 to
 * CTRL+6). "High" (HQ in the official files) uses the VL and M networks, "Fast" M and S, and "Lite"
 * the S networks only (about half the work of Fast). Mirrored in scripts/yomira/anime4k/presets.py,
 * which checks them against libplacebo.
 */
data class Anime4KPreset(val mode: Anime4KMode, override val quality: EnhanceQuality) : Enhancer {

    override val id: String get() = "${mode.key}_${quality.key}"

    /** Mode C only has upscaling networks, so like light AI it needs a bigger output. */
    override val upscaleOnly: Boolean get() = mode == Anime4KMode.C

    /** Shader names without the "Anime4K_" prefix and ".glsl" suffix, in pipeline order. */
    val shaders: List<String> get() {
        val (big, small) = when (quality) {
            EnhanceQuality.LITE -> "S" to "S"
            EnhanceQuality.FAST -> "M" to "S"
            EnhanceQuality.HIGH -> "VL" to "M"
        }
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

    /** The unmodified official files in assets/anime4k. */
    override val assets: List<String> get() = shaders.map { "anime4k/Anime4K_$it.glsl" }

    companion object {
        val ALL: List<Anime4KPreset> = EnhanceQuality.entries.flatMap { quality ->
            Anime4KMode.entries.map { Anime4KPreset(it, quality) }
        }
    }
}

/** Look of the light AI upscaler: CuNNy's DS (denoise and sharpen) or SOFT (anti-aliased) models. */
enum class LightAiLook(val key: String, val suffix: String) {
    SHARP("SHARP", "DS"),
    SOFT("SOFT", "SOFT"),
}

/**
 * Light AI: CuNNy, small convolutional networks trained on drawn art that double the luma (the
 * brightness), in assets/lightai (LGPL-3.0, unmodified). Lighter than Anime4K Fast at every size:
 * Lite is the "faster" model, Fast the "fast" one and High the 2x12 one.
 */
data class LightAiPreset(val look: LightAiLook, override val quality: EnhanceQuality) : Enhancer {

    override val id: String get() = "AI_${look.key}_${quality.key}"

    override val upscaleOnly: Boolean get() = true

    val file: String get() {
        val size = when (quality) {
            EnhanceQuality.LITE -> "faster"
            EnhanceQuality.FAST -> "fast"
            EnhanceQuality.HIGH -> "2x12"
        }
        return "CuNNy-$size-${look.suffix}.glsl"
    }

    override val assets: List<String> get() = listOf("lightai/$file")

    companion object {
        val ALL: List<LightAiPreset> = EnhanceQuality.entries.flatMap { quality ->
            LightAiLook.entries.map { LightAiPreset(it, quality) }
        }
    }
}
