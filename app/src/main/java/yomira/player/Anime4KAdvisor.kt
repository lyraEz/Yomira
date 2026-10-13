package yomira.player

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.ui.player.utils.Anime4KManager
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.delay

/**
 * Picks the Anime4K preset a phone can run without dropping frames, from what can be read without
 * root: RAM, 64-bit support, Android version and the SoC model (Android 12+). The in-player check
 * ([measureDroppedFrames]) confirms it on the real video.
 */
object Anime4KAdvisor {

    enum class Tier { LOW, MID, HIGH, FLAGSHIP }

    data class Recommendation(val tier: Tier, val mode: Anime4KManager.Mode, val quality: Anime4KManager.Quality)

    private val flagshipSocs = listOf(
        "SM8", // Snapdragon 8 series
        "TENSOR", "GS", "ZUMA", // Google Tensor
        "MT699", "MT698", "DIMENSITY 9", // Dimensity 9000 family
        "S5E99", "EXYNOS 2", // Exynos 2x00
    )

    fun tier(context: Context): Tier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalGb = memory.totalMem / (1024.0 * 1024 * 1024)
        val only32Bit = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        if (am?.isLowRamDevice == true || only32Bit || totalGb < 3.0) return Tier.LOW
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".uppercase()
        } else {
            Build.HARDWARE.uppercase()
        }
        return when {
            totalGb >= 7.5 && flagshipSocs.any { it in soc } -> Tier.FLAGSHIP
            totalGb >= 5.5 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Tier.HIGH
            else -> Tier.MID
        }
    }

    fun recommend(context: Context): Recommendation {
        val tier = tier(context)
        return when (tier) {
            // Not enough headroom: even the lightest network costs more than it gives.
            Tier.LOW -> Recommendation(tier, Anime4KManager.Mode.OFF, Anime4KManager.Quality.FAST)
            Tier.MID -> Recommendation(tier, Anime4KManager.Mode.C, Anime4KManager.Quality.FAST)
            Tier.HIGH -> Recommendation(tier, Anime4KManager.Mode.A, Anime4KManager.Quality.FAST)
            Tier.FLAGSHIP -> Recommendation(tier, Anime4KManager.Mode.A, Anime4KManager.Quality.BALANCED)
        }
    }

    /** One step lighter, used when the check finds dropped frames. Null when already the lightest. */
    fun lighter(mode: Anime4KManager.Mode, quality: Anime4KManager.Quality): Pair<Anime4KManager.Mode, Anime4KManager.Quality>? {
        if (quality != Anime4KManager.Quality.FAST) {
            return mode to Anime4KManager.Quality.entries[quality.ordinal - 1]
        }
        return when (mode) {
            Anime4KManager.Mode.A_PLUS -> Anime4KManager.Mode.A to quality
            Anime4KManager.Mode.B_PLUS -> Anime4KManager.Mode.B to quality
            Anime4KManager.Mode.C_PLUS -> Anime4KManager.Mode.C to quality
            Anime4KManager.Mode.A, Anime4KManager.Mode.B -> Anime4KManager.Mode.C to quality
            Anime4KManager.Mode.C -> Anime4KManager.Mode.OFF to quality
            Anime4KManager.Mode.OFF -> null
        }
    }

    data class FrameCheck(val dropped: Int, val delayed: Int, val seconds: Int) {
        val smooth: Boolean get() = dropped + delayed <= 1
    }

    /**
     * Counts frames mpv dropped (decoder and output) and frames shown late over [seconds] of
     * playback. Must be called while the video is playing.
     */
    suspend fun measureDroppedFrames(mpv: MPV, seconds: Int = 8): FrameCheck {
        fun read(name: String) = runCatching { mpv.getPropertyInt(name) ?: 0 }.getOrDefault(0)
        val dropStart = read("frame-drop-count") + read("decoder-frame-drop-count")
        val delayStart = read("vo-delayed-frame-count")
        delay(seconds * 1000L)
        val dropEnd = read("frame-drop-count") + read("decoder-frame-drop-count")
        val delayEnd = read("vo-delayed-frame-count")
        return FrameCheck(
            dropped = (dropEnd - dropStart).coerceAtLeast(0),
            delayed = (delayEnd - delayStart).coerceAtLeast(0),
            seconds = seconds,
        )
    }
}
