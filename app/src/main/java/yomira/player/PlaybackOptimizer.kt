package yomira.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.Surface
import android.view.SurfaceView
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

/** How video frames are paced against the screen. */
enum class SyncMode { AUTO, AUDIO, LOCKED, LOCKED_SMOOTH }

class OptimizerPreferences(private val store: PreferenceStore = Injekt.get()) {
    fun enabled() = store.getBoolean("yomira_optimizer", true)

    /** Re-checks every video and steps down a level if frames start dropping. */
    fun adaptive() = store.getBoolean("yomira_optimizer_adaptive", true)

    /** Lighter plan on battery saver or low battery. */
    fun batteryAware() = store.getBoolean("yomira_optimizer_battery", true)

    /** Allows refresh switches that briefly blank the screen when no seamless one exists (Android 12+). */
    fun allowModeSwitch() = store.getBoolean("yomira_optimizer_mode_switch", false)

    /** 0 = follow the recommendation, otherwise the refresh rate the user picked. */
    fun refreshOverride() = store.getFloat("yomira_optimizer_refresh", 0f)

    fun syncOverride() = store.getEnum("yomira_optimizer_sync", SyncMode.AUTO)
}

/** What is known about the current video. */
data class VideoInfo(
    val fps: Double,
    val width: Int,
    val height: Int,
    val codec: String,
    val hwdec: String,
)

data class DisplayMode(val id: Int, val hz: Float)

/** The optimizer's decision for one video; the panel explains it from these fields. */
data class PlaybackPlan(
    val video: VideoInfo,
    val modes: List<DisplayMode>,
    val refresh: DisplayMode?,
    val recommendedRefresh: DisplayMode?,
    val exactCadence: Boolean,
    val sync: SyncMode,
    val recommendedSync: SyncMode,
    val quality: ImageQuality,
    val recommendedQuality: ImageQuality,
    val glassRate: Int,
    val recommendedGlassRate: Int,
    val tier: Anime4KAdvisor.Tier,
    val batterySaver: Boolean,
    val decoderFrameDrop: Boolean,
    val fastDecode: Boolean,
    val adjustments: List<String> = emptyList(),
    val stepDown: Int = 0,
)

/**
 * On-device playback optimizer. It is a rules engine fed by measurements, not a cloud model: for
 * every new video it reads the source frame rate, resolution, codec and decoder from mpv, the
 * refresh rates the panel supports, the phone's tier (SoC, RAM, 64-bit), battery saver and battery
 * level, and picks:
 *
 * - the refresh rate that is an exact multiple of the source (24 fps → 24/48/72/120 Hz, 30 → 60/90/
 *   120, 60 → 60/120...). Every video frame then stays on screen for the same number of refreshes,
 *   so motion has perfect cadence with no interpolation and no judder;
 * - frame pacing locked to the display (mpv display-resample with the exact rate), or interpolation
 *   when no exact multiple exists;
 * - scaler quality from the pixel load (width × height × fps) against the tier;
 * - the cost of the live glass and decoder fallbacks.
 *
 * Every recommendation can be overridden. While playing it watches the drop counters and, if frames
 * start dropping, steps down one level at a time (quality → glass → interpolation) for that video.
 */
object PlaybackOptimizer {

    private val _plan = MutableStateFlow<PlaybackPlan?>(null)
    val plan: StateFlow<PlaybackPlan?> = _plan.asStateFlow()

    /** Refresh rate actually requested from the window, for the statistics overlay. */
    @Volatile
    var appliedHz: Float = 0f
        private set

    fun displayModes(activity: Activity): List<DisplayMode> {
        val display = activity.currentDisplay() ?: return emptyList()
        val current = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .map { DisplayMode(it.modeId, it.refreshRate) }
            .distinctBy { (it.hz * 100).roundToInt() }
            .sortedBy { it.hz }
    }

    @Suppress("DEPRECATION")
    private fun Activity.currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay

    private fun multiple(hz: Float, fps: Double): Double = hz / fps

    private fun isExact(hz: Float, fps: Double): Boolean {
        val ratio = multiple(hz, fps)
        return ratio >= 0.99 && abs(ratio - ratio.roundToInt()) < 0.012
    }

    private fun lowBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) return true
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return !plugged && level >= 0 && level * 100 / scale.coerceAtLeast(1) <= 15
    }

    fun recommend(
        context: Context,
        video: VideoInfo,
        modes: List<DisplayMode>,
        smooth: SmoothnessPreferences = SmoothnessPreferences(),
        prefs: OptimizerPreferences = OptimizerPreferences(),
        stepDown: Int = 0,
        adjustments: List<String> = emptyList(),
    ): PlaybackPlan {
        val tier = Anime4KAdvisor.tier(context)
        val saver = prefs.batteryAware().get() && lowBattery(context)
        val strong = tier >= Anime4KAdvisor.Tier.HIGH && !saver
        val fps = video.fps

        // Refresh rate: exact multiple of the source.
        val exact = modes.filter { isExact(it.hz, fps) }
        val recommendedRefresh = when {
            modes.isEmpty() || fps <= 0.0 -> null
            exact.isNotEmpty() && strong -> exact.filter { it.hz <= 121f }.maxByOrNull { it.hz } ?: exact.maxBy { it.hz }
            exact.isNotEmpty() -> exact.firstOrNull { it.hz >= 47f } ?: exact.last()
            else -> modes.maxBy { it.hz }
        }
        val override = prefs.refreshOverride().get()
        val refresh = if (override > 0f) modes.minByOrNull { abs(it.hz - override) } ?: recommendedRefresh else recommendedRefresh
        val exactCadence = refresh != null && fps > 0 && isExact(refresh.hz, fps)

        // Pacing.
        val recommendedSync = when {
            exactCadence -> SyncMode.LOCKED
            tier >= Anime4KAdvisor.Tier.MID && !saver -> SyncMode.LOCKED_SMOOTH
            else -> SyncMode.AUDIO
        }
        val sync = prefs.syncOverride().get().takeUnless { it == SyncMode.AUTO } ?: recommendedSync
            .let { if (stepDown >= 3 && it == SyncMode.LOCKED_SMOOTH) SyncMode.LOCKED else it }

        // Image quality from the pixel load.
        val load = video.width.toLong() * video.height * fps.coerceAtLeast(1.0)
        val fhd30 = 1920L * 1080 * 30
        val software = video.hwdec.isBlank() || video.hwdec == "no"
        var recommendedQuality = when (tier) {
            Anime4KAdvisor.Tier.LOW -> ImageQuality.FAST
            Anime4KAdvisor.Tier.MID -> if (load > fhd30 || software) ImageQuality.FAST else ImageQuality.BALANCED
            Anime4KAdvisor.Tier.HIGH -> if (load > fhd30 * 2) ImageQuality.BALANCED else ImageQuality.HIGH
            Anime4KAdvisor.Tier.FLAGSHIP -> if (load > fhd30 * 4) ImageQuality.BALANCED else ImageQuality.HIGH
        }
        if (saver && recommendedQuality > ImageQuality.FAST) {
            recommendedQuality = ImageQuality.entries[recommendedQuality.ordinal - 1]
        }
        if (stepDown >= 1 && recommendedQuality > ImageQuality.FAST) {
            recommendedQuality = ImageQuality.entries[recommendedQuality.ordinal - 1]
        }
        val userQuality = smooth.imageQuality().get()
        val quality = if (userQuality == ImageQuality.AUTO) recommendedQuality else userQuality

        // Live glass cost.
        val userGlass = smooth.videoGlassRate().get()
        val recommendedGlass = when {
            stepDown >= 2 || tier == Anime4KAdvisor.Tier.LOW || saver -> VideoGlassOff
            tier == Anime4KAdvisor.Tier.MID -> 15
            else -> 30
        }
        val glassRate = if (userGlass != -1) userGlass else recommendedGlass

        return PlaybackPlan(
            video = video,
            modes = modes,
            refresh = refresh,
            recommendedRefresh = recommendedRefresh,
            exactCadence = exactCadence,
            sync = sync,
            recommendedSync = recommendedSync,
            quality = quality,
            recommendedQuality = recommendedQuality,
            glassRate = glassRate,
            recommendedGlassRate = recommendedGlass,
            tier = tier,
            batterySaver = saver,
            decoderFrameDrop = smooth.decoderFrameDrop().get() || tier == Anime4KAdvisor.Tier.LOW,
            fastDecode = smooth.fastDecode().get() || (software && tier <= Anime4KAdvisor.Tier.MID),
            adjustments = adjustments,
            stepDown = stepDown,
        )
    }

    private fun readVideo(mpv: MPV): VideoInfo? {
        val fps = runCatching { mpv.getPropertyDouble("container-fps") }.getOrNull()
            ?: runCatching { mpv.getPropertyDouble("estimated-vf-fps") }.getOrNull()
            ?: return null
        if (fps <= 1.0) return null
        val w = runCatching { mpv.getPropertyInt("video-params/w") }.getOrNull() ?: 0
        val h = runCatching { mpv.getPropertyInt("video-params/h") }.getOrNull() ?: 0
        if (w <= 0 || h <= 0) return null
        val codec = runCatching { mpv.getPropertyString("video-format") }.getOrNull().orEmpty()
        val hwdec = runCatching { mpv.getPropertyString("hwdec-current") }.getOrNull().orEmpty()
        return VideoInfo(fps, w, h, codec, hwdec)
    }

    private fun apply(activity: Activity, surfaceView: SurfaceView, mpv: MPV, plan: PlaybackPlan, prefs: OptimizerPreferences) {
        val fps = plan.video.fps
        val refresh = plan.refresh
        // 1. Screen: pin the display mode and tell the surface the source rate.
        if (refresh != null) {
            runCatching {
                val params = activity.window.attributes
                params.preferredDisplayModeId = refresh.id
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) params.preferredRefreshRate = refresh.hz
                activity.window.attributes = params
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val surface = surfaceView.holder?.surface
            if (surface != null && surface.isValid) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        surface.setFrameRate(
                            fps.toFloat(),
                            Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                            if (prefs.allowModeSwitch().get()) {
                                Surface.CHANGE_FRAME_RATE_ALWAYS
                            } else {
                                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                            },
                        )
                    } else {
                        surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                    }
                }
            }
        }
        // 2. Pacing.
        runCatching {
            when (plan.sync) {
                SyncMode.AUDIO, SyncMode.AUTO -> {
                    mpv.setPropertyString("video-sync", "audio")
                    mpv.setPropertyString("interpolation", "no")
                }
                SyncMode.LOCKED -> {
                    mpv.setPropertyString("video-sync", "display-resample")
                    mpv.setPropertyString("interpolation", "no")
                }
                SyncMode.LOCKED_SMOOTH -> {
                    mpv.setPropertyString("video-sync", "display-resample")
                    mpv.setPropertyString("interpolation", "yes")
                    mpv.setPropertyString("tscale", "oversample")
                }
            }
        }
        // 3. Scaling and decoder fallbacks.
        Smoothness.setImageQuality(activity, mpv, plan.quality)
        runCatching {
            mpv.setPropertyString("framedrop", if (plan.decoderFrameDrop) "decoder+vo" else "vo")
            if (plan.fastDecode) {
                mpv.setPropertyString("vd-lavc-fast", "yes")
                mpv.setPropertyString("vd-lavc-skiploopfilter", "nonref")
            }
        }
    }

    private fun dropCount(mpv: MPV): Int {
        fun int(name: String) = runCatching { mpv.getPropertyInt(name) }.getOrNull() ?: 0
        return int("frame-drop-count") + int("decoder-frame-drop-count")
    }

    /** Re-plans now with the current preferences (after the user edits a recommendation). */
    fun replan(activity: Activity) {
        val current = _plan.value ?: return
        _plan.value = recommend(
            activity,
            current.video,
            displayModes(activity),
            stepDown = current.stepDown,
            adjustments = current.adjustments,
        )
        pendingApply = true
    }

    @Volatile
    private var pendingApply = false

    /**
     * Runs while the player is visible: plans each new video, applies the plan, and adapts it if
     * frames drop. Falls back to plain frame-rate hints when the optimizer is off.
     */
    suspend fun run(activity: Activity, surfaceView: SurfaceView, mpv: () -> MPV?) {
        val prefs = OptimizerPreferences()
        val smooth = SmoothnessPreferences()
        var lastKey = ""
        var lastDrops = -1
        var overrideHz = -1f
        var firstCheckAt = 0L
        pendingApply = true
        while (true) {
            delay(1_000)
            val m = mpv() ?: continue
            if (!prefs.enabled().get()) {
                _plan.value = null
                appliedHz = 0f
                lastKey = ""
                continue
            }
            val video = readVideo(m) ?: continue
            val key = "%.3f:%d:%d:%s".format(video.fps, video.width, video.height, video.codec)
            if (key != lastKey) {
                // New video (or a stream switch): fresh plan, no step-down carried over.
                lastKey = key
                PlayerExtras.applyAudio(m)
                _plan.value = recommend(activity, video, displayModes(activity), smooth, prefs)
                pendingApply = true
                lastDrops = -1
                firstCheckAt = System.currentTimeMillis() + 6_000
            }
            val plan = _plan.value ?: continue
            if (pendingApply) {
                pendingApply = false
                apply(activity, surfaceView, m, plan, prefs)
            }
            // Tell mpv the exact refresh only once the screen has really switched to it; if the
            // phone refused the switch, mpv keeps measuring the real one.
            val actual = activity.currentDisplay()?.refreshRate ?: 0f
            appliedHz = actual
            val desired = plan.refresh?.hz ?: 0f
            val wanted = if (desired > 0f && abs(actual - desired) < 0.6f) desired else 0f
            if (wanted != overrideHz) {
                overrideHz = wanted
                val value = "%.3f".format(java.util.Locale.ROOT, wanted)
                runCatching { m.setPropertyString("display-fps-override", value) }
                runCatching { m.setPropertyString("override-display-fps", value) }
            }

            // Adaptive step-down: only while playing, after the first seconds of a video.
            if (!prefs.adaptive().get()) continue
            val paused = runCatching { m.getPropertyBoolean("pause") }.getOrNull() ?: true
            val seeking = runCatching { m.getPropertyBoolean("seeking") }.getOrNull() ?: false
            val now = System.currentTimeMillis()
            if (paused || seeking || now < firstCheckAt) {
                lastDrops = dropCount(m)
                continue
            }
            val drops = dropCount(m)
            if (lastDrops >= 0 && drops - lastDrops >= 3 && plan.stepDown < 3) {
                val step = plan.stepDown + 1
                val note = when (step) {
                    1 -> "quality"
                    2 -> "glass"
                    else -> "interpolation"
                }
                _plan.value = recommend(
                    activity,
                    video,
                    displayModes(activity),
                    smooth,
                    prefs,
                    stepDown = step,
                    adjustments = plan.adjustments + note,
                )
                pendingApply = true
                firstCheckAt = now + 5_000
            }
            lastDrops = drops
        }
    }
}
