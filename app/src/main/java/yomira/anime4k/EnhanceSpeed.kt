package yomira.anime4k

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Time estimates: the work an enhancer does per frame (worked out by the engine from the passes
 * that run at that size) divided by the speed measured on this phone's GPU.
 */
object EnhanceSpeed {

    /** A whole episode runs slower than a few test frames: heat, and the decoder and encoder around it. */
    private const val SUSTAINED = 0.75

    /** Decoding, copying and encoding a frame, on top of the shaders. */
    private const val FRAME_OVERHEAD_SECONDS = 0.004

    private val flopsCache = ConcurrentHashMap<String, Long>()

    /** GPU work per frame at this source size and output, or null when the engine cannot run it. */
    fun flopsPerFrame(context: Context, enhancer: Enhancer, width: Int, height: Int, output: OutputSize): Long? {
        if (!Anime4KNative.available) return null
        val (outWidth, outHeight) = output.resolve(width, height)
        val key = "${enhancer.id}@${width}x$height>${outWidth}x$outHeight"
        flopsCache[key]?.let { return it }
        val handle = runCatching { Anime4KNative.create(enhancer.load(context)) }.getOrNull() ?: return null
        return try {
            Anime4KNative.estimateFlops(handle, width, height, outWidth, outHeight)?.also { flopsCache[key] = it }
        } finally {
            Anime4KNative.destroy(handle)
        }
    }

    /** GFLOP per second measured on this phone for this kind of shader, or null before a measurement. */
    fun gflops(prefs: Anime4KPreferences, enhancer: Enhancer): Double? =
        speedPref(prefs, enhancer).get().toDouble().takeIf { it > 0 }

    fun isMeasured(prefs: Anime4KPreferences): Boolean =
        prefs.fragmentGflops().get() > 0 && prefs.computeGflops().get() > 0

    /** Keeps the speed a benchmark found, for the estimates of every option of the same kind. */
    fun remember(prefs: Anime4KPreferences, result: BenchmarkResult) {
        if (result.gflopsPerFrame <= 0 || result.secondsPerFrame <= 0) return
        speedPref(prefs, result.preset).set((result.gflopsPerFrame / result.secondsPerFrame).toFloat())
    }

    /** Measures both kinds of shaders with the Lite options: a second or two of GPU work. */
    suspend fun measure(context: Context, prefs: Anime4KPreferences) {
        val anime4k = Anime4KPreset(Anime4KMode.A, EnhanceQuality.LITE)
        remember(prefs, Anime4KBenchmark.run(context, anime4k, 1280, 720, OutputSize.ORIGINAL))
        val lightAi = LightAiPreset(LightAiLook.SHARP, EnhanceQuality.LITE)
        remember(prefs, Anime4KBenchmark.run(context, lightAi, 960, 540, OutputSize.P1080))
    }

    /** Expected seconds per frame, or null when the phone was not measured yet. */
    fun secondsPerFrame(
        context: Context,
        prefs: Anime4KPreferences,
        enhancer: Enhancer,
        width: Int,
        height: Int,
        output: OutputSize,
    ): Double? {
        val speed = gflops(prefs, enhancer) ?: return null
        val flops = flopsPerFrame(context, enhancer, width, height, output) ?: return null
        return flops / 1e9 / (speed * SUSTAINED) + FRAME_OVERHEAD_SECONDS
    }

    /**
     * Seconds for a 24-minute episode: null when the phone was not measured yet, 0 when the option
     * would not change a video of this size (an upscaler on a video already at the output size).
     */
    fun episodeSeconds(
        context: Context,
        prefs: Anime4KPreferences,
        enhancer: Enhancer,
        width: Int,
        height: Int,
        output: OutputSize,
    ): Long? {
        if (flopsPerFrame(context, enhancer, width, height, output) == 0L) return 0
        return secondsPerFrame(context, prefs, enhancer, width, height, output)
            ?.let { (it * BenchmarkResult.EPISODE_FRAMES).toLong() }
    }

    private fun speedPref(prefs: Anime4KPreferences, enhancer: Enhancer) =
        if (enhancer is LightAiPreset) prefs.computeGflops() else prefs.fragmentGflops()
}
