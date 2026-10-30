package yomira.anime4k

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings → Anime4K and light AI: Enhanced Downloads and how processing runs. */
class Anime4KPreferences(private val store: PreferenceStore = Injekt.get()) {

    // Enhanced Downloads

    /** Offer an enhanced version when downloading. Off: downloads work as before. */
    fun enhancedDownloads() = store.getBoolean("yomira_a4k_downloads", false)

    /** "ask" shows the choice on every download, "always" enhances every download. */
    fun downloadChoice() = store.getString("yomira_a4k_download_choice", "ask")

    fun downloadPreset() = store.getString("yomira_a4k_download_preset", Enhancer.DEFAULT.id)

    fun outputSize() = store.getString("yomira_a4k_output", OutputSize.ORIGINAL.key)

    fun codec() = store.getString("yomira_a4k_codec", OutputCodec.HEVC.key)

    fun exportQuality() = store.getString("yomira_a4k_quality", ExportQuality.HIGH.key)

    /** Keep the original next to the enhanced version (both are played from the download). */
    fun keepOriginal() = store.getBoolean("yomira_a4k_keep_original", true)

    // How processing runs

    fun onlyWhileCharging() = store.getBoolean("yomira_a4k_only_charging", true)

    fun pauseWhenHot() = store.getBoolean("yomira_a4k_pause_hot", true)

    /** Pause processing while the player is open, so playback never stutters. */
    fun prioritizePlayer() = store.getBoolean("yomira_a4k_prioritize_player", true)

    /** Space Yomira may use for the pieces of videos being processed, in GB. */
    fun storageLimitGb() = store.getInt("yomira_a4k_storage_gb", 12)

    /** Delete the pieces as soon as a video is finished. */
    fun cleanTemporary() = store.getBoolean("yomira_a4k_clean_temp", true)

    // Measured speed of this phone's GPU in GFLOP per second, for time estimates (EnhanceSpeed)

    /** Fragment shaders in half precision: Anime4K. */
    fun fragmentGflops() = store.getFloat("yomira_a4k_gflops_fragment", 0f)

    /** Compute shaders: light AI. */
    fun computeGflops() = store.getFloat("yomira_a4k_gflops_compute", 0f)

    fun settings(presetId: String = downloadPreset().get()): ExportSettings = ExportSettings(
        preset = Enhancer.fromId(presetId) ?: Enhancer.DEFAULT,
        output = OutputSize.fromKey(outputSize().get()),
        codec = OutputCodec.fromKey(codec().get()),
        quality = ExportQuality.fromKey(exportQuality().get()),
    )

    companion object {
        /** Settings of Smart Anime4K, removed in 1.13.0; the migration deletes them. */
        val REMOVED_SMART_KEYS = listOf(
            "yomira_a4k_smart",
            "yomira_a4k_smart_preset",
            "yomira_a4k_smart_current",
            "yomira_a4k_smart_next",
            "yomira_a4k_smart_cache_gb",
            "yomira_a4k_smart_wifi",
        )

        /** Per-preset timings kept before 1.14.0, replaced by one speed per kind of shader. */
        const val REMOVED_BENCHMARK_KEY = "yomira_a4k_bench_spf"
    }
}
