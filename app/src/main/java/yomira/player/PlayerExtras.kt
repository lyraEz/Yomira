package yomira.player

import `is`.xyz.mpv.MPV
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PlayerExtrasPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Evens out loud and quiet parts so explosions do not wake the house and whispers stay audible. */
    fun nightMode() = store.getBoolean("yomira_night_mode", false)

    /** Brings quiet speech up to a steady level. */
    fun dialogueBoost() = store.getBoolean("yomira_dialogue_boost", false)

    /** Gentle low-frequency lift for phone speakers and earbuds. */
    fun bassBoost() = store.getBoolean("yomira_bass_boost", false)

    /** Seconds to rewind when playback resumes after a long pause (0 = off). */
    fun resumeRewind() = store.getInt("yomira_resume_rewind", 0)
}

/** Audio extras applied as labelled mpv filters, so they never clash with the app's own filters. */
object PlayerExtras {
    private const val NIGHT = "@ymrnight"
    private const val VOICE = "@ymrvoice"
    private const val BASS = "@ymrbass"

    fun applyAudio(mpv: MPV?, prefs: PlayerExtrasPreferences = PlayerExtrasPreferences()) {
        mpv ?: return
        set(mpv, NIGHT, prefs.nightMode().get(), "lavfi=[dynaudnorm=f=250:g=15:p=0.9:m=8]")
        set(mpv, VOICE, prefs.dialogueBoost().get(), "lavfi=[speechnorm=e=6:r=0.00005:l=1]")
        set(mpv, BASS, prefs.bassBoost().get(), "lavfi=[bass=g=5:f=110:w=0.6]")
    }

    private fun set(mpv: MPV, label: String, enabled: Boolean, filter: String) {
        runCatching { mpv.command("af", "remove", label) }
        if (enabled) runCatching { mpv.command("af", "add", "$label:$filter") }
    }

    /** Pause longer than this before the resume rewind kicks in. */
    const val LONG_PAUSE_MS = 30_000L

    fun rewind(mpv: MPV?, seconds: Int) {
        if (mpv == null || seconds <= 0) return
        runCatching { mpv.command("seek", "-$seconds", "relative") }
    }
}
