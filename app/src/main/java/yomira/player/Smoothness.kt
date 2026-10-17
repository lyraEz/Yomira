package yomira.player

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs

/**
 * Playback smoothness options.
 *
 * - Frame rate matching: tells Android the video's frame rate on the video surface (Android 11+),
 *   so phones with 90/120 Hz or variable refresh pick a rate that divides evenly by the video
 *   (24 fps on 120 Hz, for example). That removes the uneven frame pacing ("judder") of 24 fps
 *   anime on 60 Hz screens without any extra GPU work. Only seamless switches are requested, so the
 *   screen never blanks.
 * - Smooth motion: mpv resamples the video to the screen's refresh rate and blends neighbouring
 *   frames (display-resample + oversample interpolation). Smoothest result on 60 Hz screens, at a
 *   small GPU cost; off by default.
 */
class SmoothnessPreferences(private val store: PreferenceStore = Injekt.get()) {
    fun matchFrameRate() = store.getBoolean("yomira_match_frame_rate", true)
    fun smoothMotion() = store.getBoolean("yomira_smooth_motion", false)

    /** Video glass refresh: -1 automatic, 0 every display frame, otherwise copies per second. */
    fun videoGlassRate() = store.getInt("yomira_video_glass_rate", -1)

    fun imageQuality() = store.getEnum("yomira_image_quality", ImageQuality.AUTO)

    /** Larger read-ahead so slow or unstable connections do not stall playback. */
    fun bigBuffer() = store.getBoolean("yomira_big_buffer", false)

    /** Drop late frames at the decoder too, so weak phones keep audio and video in sync. */
    fun decoderFrameDrop() = store.getBoolean("yomira_decoder_framedrop", false)

    /** Cheaper software decoding (skips the loop filter on non-reference frames). */
    fun fastDecode() = store.getBoolean("yomira_fast_decode", false)

    /** Seek to the nearest keyframe (instant) instead of the exact frame. */
    fun fastSeek() = store.getBoolean("yomira_fast_seek", false)

    /** Live counter of dropped and late frames over the video. */
    fun frameStats() = store.getBoolean("yomira_frame_stats", false)

    /** Quality: softens the ringing halos sharp scalers leave around lines. */
    fun antiRing() = store.getBoolean("yomira_anti_ring", true)

    /** Quality: dithering for smooth gradients. */
    fun dither() = store.getBoolean("yomira_dither", true)

    /** Quality: tone mapping for HDR video on SDR screens. */
    fun toneMapping() = store.getEnum("yomira_tone_mapping", ToneMapping.AUTO)

    /** Quality: automatic deinterlacing for interlaced (DVD/TV) sources. */
    fun deinterlace() = store.getBoolean("yomira_deinterlace", false)
}

enum class ToneMapping(val mpvValue: String) { AUTO("auto"), BT2390("bt.2390"), HABLE("hable"), MOBIUS("mobius") }

/** mpv scaler presets, from cheapest to sharpest. */
enum class ImageQuality { AUTO, FAST, BALANCED, HIGH }

object Smoothness {

    /** Image quality the device can sustain without dropping frames. */
    fun resolve(context: Context, quality: ImageQuality): ImageQuality = when (quality) {
        ImageQuality.AUTO -> when (Anime4KAdvisor.tier(context)) {
            Anime4KAdvisor.Tier.LOW -> ImageQuality.FAST
            Anime4KAdvisor.Tier.MID -> ImageQuality.BALANCED
            Anime4KAdvisor.Tier.HIGH, Anime4KAdvisor.Tier.FLAGSHIP -> ImageQuality.HIGH
        }
        else -> quality
    }

    private fun scalerOptions(quality: ImageQuality): List<Pair<String, String>> = when (quality) {
        ImageQuality.FAST, ImageQuality.AUTO -> listOf(
            "scale" to "bilinear",
            "cscale" to "bilinear",
            "dscale" to "bilinear",
            "correct-downscaling" to "no",
            "sigmoid-upscaling" to "no",
        )
        ImageQuality.BALANCED -> listOf(
            "scale" to "spline36",
            "cscale" to "spline36",
            "dscale" to "mitchell",
            "correct-downscaling" to "yes",
            "sigmoid-upscaling" to "no",
        )
        ImageQuality.HIGH -> listOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "spline36",
            "dscale" to "mitchell",
            "correct-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
        )
    }

    /** Applies an image quality preset while playing. */
    fun setImageQuality(context: Context, mpv: MPV?, quality: ImageQuality) {
        mpv ?: return
        scalerOptions(resolve(context, quality)).forEach { (key, value) ->
            runCatching { mpv.setPropertyString(key, value) }
        }
    }

    /** Options read once when mpv starts. */
    fun applyMpvOptions(context: Context, mpv: MPV?, prefs: SmoothnessPreferences = SmoothnessPreferences()) {
        mpv ?: return
        scalerOptions(resolve(context, prefs.imageQuality().get())).forEach { (key, value) ->
            runCatching { mpv.setOptionString(key, value) }
        }
        if (prefs.bigBuffer().get()) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val lowRam = am?.isLowRamDevice == true
            val megs = if (lowRam) 48 else 128
            runCatching {
                mpv.setOptionString("cache", "yes")
                mpv.setOptionString("cache-secs", "120")
                mpv.setOptionString("demuxer-readahead-secs", "60")
                mpv.setOptionString("demuxer-max-bytes", "${megs * 1024 * 1024}")
            }
        }
        playbackOptions(prefs).forEach { (key, value) -> runCatching { mpv.setOptionString(key, value) } }
        qualityOptions(prefs).forEach { (key, value) -> runCatching { mpv.setOptionString(key, value) } }
        if (prefs.smoothMotion().get()) {
            mpv.setOptionString("video-sync", "display-resample")
            mpv.setOptionString("interpolation", "yes")
            mpv.setOptionString("tscale", "oversample")
        }
    }

    private fun playbackOptions(prefs: SmoothnessPreferences): List<Pair<String, String>> = buildList {
        add("framedrop" to if (prefs.decoderFrameDrop().get()) "decoder+vo" else "vo")
        add("hr-seek" to if (prefs.fastSeek().get()) "no" else "default")
        if (prefs.fastDecode().get()) {
            add("vd-lavc-fast" to "yes")
            add("vd-lavc-skiploopfilter" to "nonref")
        } else {
            add("vd-lavc-fast" to "no")
            add("vd-lavc-skiploopfilter" to "default")
        }
    }

    private fun qualityOptions(prefs: SmoothnessPreferences): List<Pair<String, String>> = listOf(
        "scale-antiring" to if (prefs.antiRing().get()) "0.6" else "0",
        "dither-depth" to if (prefs.dither().get()) "auto" else "no",
        "tone-mapping" to prefs.toneMapping().get().mpvValue,
        "deinterlace" to if (prefs.deinterlace().get()) "auto" else "no",
    )

    /** Re-applies the playback and quality options while playing (decoder ones apply to the next video). */
    fun applyLive(mpv: MPV?, prefs: SmoothnessPreferences = SmoothnessPreferences()) {
        mpv ?: return
        (playbackOptions(prefs) + qualityOptions(prefs)).forEach { (key, value) ->
            runCatching { mpv.setPropertyString(key, value) }
        }
    }

    /** Applies or removes smooth motion while playing. */
    fun setSmoothMotion(mpv: MPV?, enabled: Boolean) {
        mpv ?: return
        runCatching {
            mpv.setPropertyString("video-sync", if (enabled) "display-resample" else "audio")
            mpv.setPropertyString("interpolation", if (enabled) "yes" else "no")
            if (enabled) mpv.setPropertyString("tscale", "oversample")
        }
    }

    /**
     * Keeps the surface's frame rate in step with the current video while the player is visible.
     * Polls cheaply (one property read every 2 s) and only touches the surface when the rate changes.
     */
    suspend fun frameRateLoop(surfaceView: SurfaceView, mpv: () -> MPV?, prefs: SmoothnessPreferences = SmoothnessPreferences()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        var applied = 0f
        while (true) {
            delay(2_000)
            // The playback optimizer handles the refresh rate itself when it is on.
            if (OptimizerPreferences().enabled().get()) continue
            val enabled = prefs.matchFrameRate().get()
            val fps = if (enabled) {
                runCatching { mpv()?.getPropertyDouble("container-fps") }.getOrNull()?.toFloat() ?: 0f
            } else {
                0f
            }
            if (abs(fps - applied) < 0.01f) continue
            val surface = surfaceView.holder?.surface ?: continue
            if (!surface.isValid) continue
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    surface.setFrameRate(
                        fps,
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
                    )
                } else {
                    surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                }
            }.isSuccess
            if (ok) applied = fps
        }
    }
}
