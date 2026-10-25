package yomira.discord

import android.app.Activity
import com.discord.socialsdk.DiscordSocialSdkInit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * JNI bridge to src/main/cpp/yomira_discord.cpp. The native side keeps one worker thread for every
 * Discord SDK call, so these functions only queue work and return.
 */
internal object DiscordNative {

    /** Order of the text fields of [update]; must match the native enum. */
    const val DETAILS = 0
    const val DETAILS_URL = 1
    const val STATE = 2
    const val STATE_URL = 3
    const val LARGE_IMAGE = 4
    const val LARGE_TEXT = 5
    const val LARGE_URL = 6
    const val SMALL_IMAGE = 7
    const val SMALL_TEXT = 8
    const val SMALL_URL = 9
    const val BUTTON1_LABEL = 10
    const val BUTTON1_URL = 11
    const val BUTTON2_LABEL = 12
    const val BUTTON2_URL = 13
    const val FIELD_COUNT = 14

    @Volatile private var loaded: Boolean? = null

    /**
     * Gives the SDK the player activity (it binds to the Discord app through it) and loads the
     * bridge. False when the libraries are missing for this ABI.
     */
    @Synchronized
    fun prepare(activity: Activity): Boolean {
        if (loaded == false) return false
        return runCatching {
            // Loads the SDK library first, so its JNI_OnLoad runs before the bridge links to it.
            DiscordSocialSdkInit.setEngineActivity(activity)
            if (loaded == null) System.loadLibrary("yomira_discord")
            loaded = true
            true
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "Discord: the SDK could not be loaded" }
            loaded = false
            false
        }
    }

    val isLoaded: Boolean get() = loaded == true

    fun start(applicationId: Long): Boolean = isLoaded && runCatching { nativeStart(applicationId) }.getOrDefault(false)

    fun update(fields: Array<String>, type: Int, statusDisplay: Int, start: Long, end: Long) {
        if (!isLoaded) return
        runCatching { nativeUpdate(fields, longArrayOf(type.toLong(), statusDisplay.toLong(), start, end)) }
    }

    fun clear() {
        if (isLoaded) runCatching { nativeClear() }
    }

    /** Blocks until the worker ends: never call it on the main thread. */
    fun stop() {
        if (isLoaded) runCatching { nativeStop() }
    }

    /** "PHASE|message", e.g. "ACTIVE|" or "ERROR|reason". */
    fun status(): String = if (isLoaded) runCatching { nativeStatus() }.getOrDefault("IDLE|") else "UNAVAILABLE|"

    private external fun nativeStart(applicationId: Long): Boolean
    private external fun nativeUpdate(fields: Array<String>, numbers: LongArray): Boolean
    private external fun nativeClear()
    private external fun nativeStop()
    private external fun nativeStatus(): String
}
