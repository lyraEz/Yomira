package yomira.player

import `is`.xyz.mpv.MPV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class PlayerToolsPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Clock, battery and "ends at" under the episode title while the controls show. */
    fun infoClock() = store.getBoolean("yomira_info_clock", true)
    fun infoBattery() = store.getBoolean("yomira_info_battery", true)
    fun infoEndTime() = store.getBoolean("yomira_info_end_time", true)

    /** Warm tint over the video, 0 (off) to 100. */
    fun blueLight() = store.getInt("yomira_blue_light", 0)

    /** Same sound in both ears (one earbud, hearing in one ear). */
    fun audioMono() = store.getBoolean("yomira_audio_mono", false)

    /** Left/right balance, -100 (all left) to 100 (all right). */
    fun audioBalance() = store.getInt("yomira_audio_balance", 0)

    /** Headphone crossfeed: less fatiguing stereo, sound feels in front instead of inside the head. */
    fun audioSpatial() = store.getBoolean("yomira_audio_spatial", false)

    /** Gentle high-frequency lift for muffled audio. */
    fun audioTreble() = store.getBoolean("yomira_audio_treble", false)

    /** Restore the last playback speed used for each anime. */
    fun speedPerAnime() = store.getBoolean("yomira_speed_per_anime", false)

    /** "animeId=speed;animeId=speed" */
    fun speedMap() = store.getString("yomira_speed_map", "")
}

/**
 * Player tools that act directly on mpv: A-B loop, frame stepping, rotation, per-anime speed,
 * stopping at the end of the episode and the extra audio filters. Session state lives here so the
 * panel and the overlays show the same thing.
 */
object PlayerTools {

    private val _loopA = MutableStateFlow<Double?>(null)
    val loopA: StateFlow<Double?> = _loopA.asStateFlow()

    private val _loopB = MutableStateFlow<Double?>(null)
    val loopB: StateFlow<Double?> = _loopB.asStateFlow()

    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _stopAfterEpisode = MutableStateFlow(false)
    val stopAfterEpisode: StateFlow<Boolean> = _stopAfterEpisode.asStateFlow()

    /** Called for every file mpv loads (new episode, new stream, retry). */
    fun onFileLoaded(mpv: MPV?, animeId: Long?) {
        mpv ?: return
        PlayerExtras.applyAudio(mpv)
        applySound(mpv)
        clearLoop(mpv)
        restoreSpeed(mpv, animeId)
        ColorGrading.apply(mpv)
    }

    // A-B loop -------------------------------------------------------------------------------

    fun markA(mpv: MPV?) {
        val t = position(mpv) ?: return
        runCatching { mpv!!.setPropertyDouble("ab-loop-a", t) }
        _loopA.value = t
        val b = _loopB.value
        if (b != null && b <= t) {
            runCatching { mpv!!.setPropertyString("ab-loop-b", "no") }
            _loopB.value = null
        }
    }

    fun markB(mpv: MPV?) {
        val t = position(mpv) ?: return
        val a = _loopA.value ?: 0.0
        if (t <= a) return
        if (_loopA.value == null) {
            runCatching { mpv!!.setPropertyDouble("ab-loop-a", 0.0) }
            _loopA.value = 0.0
        }
        runCatching { mpv!!.setPropertyDouble("ab-loop-b", t) }
        _loopB.value = t
    }

    fun clearLoop(mpv: MPV?) {
        mpv?.let {
            runCatching { it.setPropertyString("ab-loop-a", "no") }
            runCatching { it.setPropertyString("ab-loop-b", "no") }
        }
        _loopA.value = null
        _loopB.value = null
    }

    // Frames and rotation -------------------------------------------------------------------

    fun frameStep(mpv: MPV?, forward: Boolean) {
        runCatching { mpv?.command(if (forward) "frame-step" else "frame-back-step") }
    }

    fun rotate(mpv: MPV?, degrees: Int) {
        val value = ((degrees % 360) + 360) % 360
        runCatching { mpv?.setPropertyInt("video-rotate", value) }
        _rotation.value = value
    }

    // End of episode -------------------------------------------------------------------------

    fun setStopAfterEpisode(enabled: Boolean) {
        _stopAfterEpisode.value = enabled
    }

    /** True once if the user asked to stop at the end of this episode (autoplay is skipped). */
    fun consumeStopAfterEpisode(): Boolean {
        if (!_stopAfterEpisode.value) return false
        _stopAfterEpisode.value = false
        return true
    }

    // Speed per anime ------------------------------------------------------------------------

    private fun readSpeeds(prefs: PlayerToolsPreferences): MutableMap<Long, Double> =
        prefs.speedMap().get().split(';')
            .mapNotNull { entry ->
                val (id, speed) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val key = id.toLongOrNull() ?: return@mapNotNull null
                val value = speed.toDoubleOrNull() ?: return@mapNotNull null
                key to value
            }
            .toMap(LinkedHashMap())

    private fun restoreSpeed(mpv: MPV, animeId: Long?, prefs: PlayerToolsPreferences = PlayerToolsPreferences()) {
        if (animeId == null || !prefs.speedPerAnime().get()) return
        val speed = readSpeeds(prefs)[animeId] ?: return
        runCatching { mpv.setPropertyDouble("speed", speed) }
    }

    /** Remembers the current speed for [animeId] (keeps the 200 most recent anime). */
    fun saveSpeed(mpv: MPV?, animeId: Long?, prefs: PlayerToolsPreferences = PlayerToolsPreferences()) {
        if (mpv == null || animeId == null || !prefs.speedPerAnime().get()) return
        val speed = runCatching { mpv.getPropertyDouble("speed") }.getOrNull() ?: return
        val map = readSpeeds(prefs)
        if (map[animeId]?.let { kotlin.math.abs(it - speed) < 0.001 } == true) return
        map.remove(animeId)
        map[animeId] = speed
        val trimmed = map.entries.toList().takeLast(200)
        prefs.speedMap().set(trimmed.joinToString(";") { "${it.key}=${"%.2f".format(Locale.US, it.value)}" })
    }

    // Sound ----------------------------------------------------------------------------------

    private const val MONO = "@ymrmono"
    private const val BALANCE = "@ymrbalance"
    private const val SPATIAL = "@ymrspatial"
    private const val TREBLE = "@ymrtreble"

    fun applySound(mpv: MPV?, prefs: PlayerToolsPreferences = PlayerToolsPreferences()) {
        mpv ?: return
        val balance = prefs.audioBalance().get().coerceIn(-100, 100)
        set(mpv, MONO, prefs.audioMono().get(), "lavfi=[aformat=channel_layouts=mono]")
        set(
            mpv,
            BALANCE,
            balance != 0,
            "lavfi=[stereotools=balance_out=${"%.2f".format(Locale.US, balance / 100f)}]",
        )
        set(mpv, SPATIAL, prefs.audioSpatial().get(), "lavfi=[crossfeed=strength=0.3:range=0.6]")
        set(mpv, TREBLE, prefs.audioTreble().get(), "lavfi=[treble=g=4:f=3500:w=0.6]")
    }

    private fun set(mpv: MPV, label: String, enabled: Boolean, filter: String) {
        runCatching { mpv.command("af", "remove", label) }
        if (enabled) runCatching { mpv.command("af", "add", "$label:$filter") }
    }

    private fun position(mpv: MPV?): Double? =
        mpv?.let { runCatching { it.getPropertyDouble("time-pos") }.getOrNull() }
}
