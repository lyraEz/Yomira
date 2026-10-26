package yomira.anime4k

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings → Anime4K: Enhanced Downloads, Smart Anime4K and how processing runs. */
class Anime4KPreferences(private val store: PreferenceStore = Injekt.get()) {

    // Enhanced Downloads

    /** Offer "Anime4K Enhanced" when downloading. Off: downloads work as before. */
    fun enhancedDownloads() = store.getBoolean("yomira_a4k_downloads", false)

    /** "ask" shows the choice on every download, "always" enhances every download. */
    fun downloadChoice() = store.getString("yomira_a4k_download_choice", "ask")

    fun downloadPreset() = store.getString("yomira_a4k_download_preset", "AA_HQ")

    fun outputSize() = store.getString("yomira_a4k_output", OutputSize.ORIGINAL.key)

    fun codec() = store.getString("yomira_a4k_codec", OutputCodec.HEVC.key)

    fun exportQuality() = store.getString("yomira_a4k_quality", ExportQuality.HIGH.key)

    /** Keep the original next to the Anime4K version (both are played from the download). */
    fun keepOriginal() = store.getBoolean("yomira_a4k_keep_original", true)

    // How processing runs

    fun onlyWhileCharging() = store.getBoolean("yomira_a4k_only_charging", true)

    fun pauseWhenHot() = store.getBoolean("yomira_a4k_pause_hot", true)

    /** Pause processing while the player is open, so playback never stutters. */
    fun prioritizePlayer() = store.getBoolean("yomira_a4k_prioritize_player", true)

    /** Space Yomira may use for pieces and the Smart cache, in GB. */
    fun storageLimitGb() = store.getInt("yomira_a4k_storage_gb", 12)

    /** Delete the pieces as soon as a video is finished. */
    fun cleanTemporary() = store.getBoolean("yomira_a4k_clean_temp", true)

    // Smart Anime4K

    fun smartEnabled() = store.getBoolean("yomira_a4k_smart", false)

    fun smartPreset() = store.getString("yomira_a4k_smart_preset", "A_FAST")

    /** Prepare the episode that was just opened. */
    fun smartCurrent() = store.getBoolean("yomira_a4k_smart_current", true)

    /** Also prepare the next episode. */
    fun smartNext() = store.getBoolean("yomira_a4k_smart_next", false)

    /** Space for prepared episodes, in GB; the oldest go first. */
    fun smartCacheGb() = store.getInt("yomira_a4k_smart_cache_gb", 6)

    /** Download episodes for Smart only on Wi-Fi. */
    fun smartWifiOnly() = store.getBoolean("yomira_a4k_smart_wifi", true)

    // Last benchmark on this phone, for time estimates

    fun benchmarkSecondsPerFrame() = store.getString("yomira_a4k_bench_spf", "")

    fun settings(presetId: String = downloadPreset().get()): ExportSettings = ExportSettings(
        preset = Anime4KPreset.fromId(presetId) ?: Anime4KPreset.DEFAULT,
        output = OutputSize.fromKey(outputSize().get()),
        codec = OutputCodec.fromKey(codec().get()),
        quality = ExportQuality.fromKey(exportQuality().get()),
    )

    /** Seconds per frame measured for a preset and size key, if a benchmark ran. */
    fun measuredSecondsPerFrame(key: String): Double? = benchmarkSecondsPerFrame().get()
        .split(';')
        .mapNotNull { entry -> entry.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1].toDoubleOrNull() } }
        .firstOrNull { it.first == key }?.second

    fun rememberBenchmark(key: String, secondsPerFrame: Double) {
        val others = benchmarkSecondsPerFrame().get().split(';').filter { it.isNotBlank() && !it.startsWith("$key=") }
        benchmarkSecondsPerFrame().set((others + "$key=$secondsPerFrame").takeLast(24).joinToString(";"))
    }

    companion object {
        fun benchmarkKey(preset: Anime4KPreset, height: Int, output: OutputSize) = "${preset.id}@${height}p>${output.key}"
    }
}
