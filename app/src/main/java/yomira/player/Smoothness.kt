package yomira.player

import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.PreferenceStore
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
}

object Smoothness {

    /** Options read once when mpv starts. */
    fun applyMpvOptions(mpv: MPV?, prefs: SmoothnessPreferences = SmoothnessPreferences()) {
        mpv ?: return
        if (prefs.smoothMotion().get()) {
            mpv.setOptionString("video-sync", "display-resample")
            mpv.setOptionString("interpolation", "yes")
            mpv.setOptionString("tscale", "oversample")
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
