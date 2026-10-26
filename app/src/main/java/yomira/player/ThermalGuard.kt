package yomira.player

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR

/**
 * Keeps the phone from overheating during playback. Android reports the device's thermal state
 * (Android 10+) and, on Android 11+, a forecast of how close it is to throttling. When the phone
 * gets warm the player switches to its lightest settings for the rest of the session: no shader
 * chain (Anime4K), no frame blending, plain bilinear scaling, no GPU debanding, decoder frame
 * dropping, and no live glass on the controls. A hot phone throttles its CPU and GPU, which is what
 * makes every app stutter until it cools down.
 */
object ThermalGuard {

    private val _hot = MutableStateFlow(false)
    val hot: StateFlow<Boolean> = _hot.asStateFlow()

    /** Last thermal status (PowerManager.THERMAL_STATUS_*), -1 when unknown. */
    @Volatile
    var status: Int = -1
        private set

    /** 0..1+ forecast of thermal headroom used (1 = throttling), -1 when unknown. */
    @Volatile
    var headroom: Float = -1f
        private set

    /** Runs while the player is visible. */
    suspend fun watch(context: Context, mpv: () -> MPV?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        _hot.value = false
        while (true) {
            status = pm.currentThermalStatus
            headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN).takeIf { !it.isNaN() } ?: -1f
            } else {
                -1f
            }
            val warm = status >= PowerManager.THERMAL_STATUS_MODERATE || headroom >= 0.9f
            if (warm && !_hot.value) {
                _hot.value = true
                lighten(mpv())
                runCatching {
                    Toast.makeText(context, context.stringResource(YMR.strings.thermal_light_mode), Toast.LENGTH_LONG).show()
                }
            } else if (_hot.value) {
                // Re-apply in case a new episode restored heavier options.
                lighten(mpv())
            }
            delay(10_000)
        }
    }

    private fun lighten(mpv: MPV?) {
        mpv ?: return
        listOf(
            // The color shader is a few operations per pixel; only the heavy ones go.
            "glsl-shaders" to ColorGrading.withColor(""),
            "interpolation" to "no",
            "video-sync" to "audio",
            "scale" to "bilinear",
            "cscale" to "bilinear",
            "dscale" to "bilinear",
            "correct-downscaling" to "no",
            "sigmoid-upscaling" to "no",
            "deband" to "no",
            "framedrop" to "decoder+vo",
        ).forEach { (key, value) -> runCatching { mpv.setPropertyString(key, value) } }
    }

    fun label(): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "ok"
        PowerManager.THERMAL_STATUS_LIGHT -> "warm"
        PowerManager.THERMAL_STATUS_MODERATE -> "HOT"
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> "HOT!"
        else -> "?"
    }
}

/** Puts every player tweak back to the light defaults (Yomira options and heavy decoder ones). */
object PlayerDefaults {

    fun reset(store: PreferenceStore = Injekt.get(), decoder: DecoderPreferences = Injekt.get()) {
        val keys = listOf(
            "yomira_match_frame_rate", "yomira_smooth_motion", "yomira_video_glass_rate", "yomira_image_quality",
            "yomira_big_buffer", "yomira_decoder_framedrop", "yomira_fast_decode", "yomira_fast_seek",
            "yomira_frame_stats", "yomira_anti_ring", "yomira_dither", "yomira_tone_mapping", "yomira_deinterlace",
            "yomira_optimizer", "yomira_optimizer_adaptive", "yomira_optimizer_battery",
            "yomira_optimizer_mode_switch", "yomira_optimizer_refresh", "yomira_optimizer_sync",
            "yomira_night_mode", "yomira_dialogue_boost", "yomira_bass_boost", "yomira_resume_rewind",
            "yomira_audio_mono", "yomira_audio_balance", "yomira_audio_spatial", "yomira_audio_treble",
            "yomira_blue_light", "yomira_speed_per_anime",
        )
        keys.forEach { key -> runCatching { store.getString(key).delete() } }
        runCatching { decoder.smoothMotion().delete() }
        runCatching { decoder.interpolationMode().delete() }
        runCatching { decoder.enableAnime4K().delete() }
        runCatching { decoder.anime4kMode().delete() }
        runCatching { decoder.anime4kQuality().delete() }
        runCatching { decoder.videoDebanding().delete() }
        runCatching { decoder.gpuNext().delete() }
        runCatching { decoder.highQualityScaling().delete() }
        runCatching { decoder.adaptiveShaderScaling().delete() }
    }

    /** One-time reset when updating to the version that fixed the overheating defaults. */
    fun migrate(store: PreferenceStore = Injekt.get()): Boolean {
        val done = store.getBoolean("yomira_player_defaults_v150", false)
        if (done.get()) return false
        reset(store)
        done.set(true)
        return true
    }
}
