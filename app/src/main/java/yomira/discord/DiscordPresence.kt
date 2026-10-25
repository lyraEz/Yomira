package yomira.discord

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Where the Rich Presence stands, for the settings and the player card. */
enum class PresenceState { Off, NoDiscord, Unavailable, Connecting, Active, Paused, Hidden, Error }

data class PresenceStatus(val state: PresenceState = PresenceState.Off, val message: String = "")

/**
 * Sends Rich Presence cards to the Discord app on the phone. The player calls [attach], then
 * [publish] on discrete events (start, pause, seek, episode change) and [detach] when it closes.
 *
 * Discord accepts about five updates every 20 seconds, so cards are sent at most every few seconds
 * (the newest one wins) and repeated cards are skipped.
 */
object DiscordPresence {

    const val DISCORD_PACKAGE = "com.discord"

    private const val MIN_INTERVAL_MS = 4_000L
    private const val STATUS_DELAY_MS = 1_500L
    private const val STOP_DELAY_MS = 800L

    /** One thread: the order of attach, publish, clear and detach is kept. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var activity: WeakReference<Activity>? = null
    private var appId = 0L

    /** A connection is still open after [detach] and must be closed before the next one. */
    private var connectionStale = false
    private var last: PresencePayload? = null
    private var lastSentAt = 0L
    private var pendingJob: Job? = null
    private var stopJob: Job? = null
    private var statusJob: Job? = null

    private val _status = MutableStateFlow(PresenceStatus())
    val status: StateFlow<PresenceStatus> = _status.asStateFlow()

    fun isDiscordInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(DISCORD_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Connects through [owner] (the player). A new activity or app id starts a fresh connection. */
    fun attach(owner: Activity, applicationId: Long) {
        scope.launch {
            stopJob?.cancel()
            if (activity?.get() === owner && appId == applicationId && !connectionStale) return@launch
            if (!isDiscordInstalled(owner)) {
                _status.value = PresenceStatus(PresenceState.NoDiscord)
                return@launch
            }
            // The old connection was bound through another activity (or another app id).
            if (activity != null || connectionStale) DiscordNative.stop()
            connectionStale = false
            last = null
            if (!DiscordNative.prepare(owner)) {
                activity = null
                _status.value = PresenceStatus(PresenceState.Unavailable)
                return@launch
            }
            DiscordNative.start(applicationId)
            activity = WeakReference(owner)
            appId = applicationId
            _status.value = PresenceStatus(PresenceState.Connecting)
        }
    }

    fun publish(payload: PresencePayload) {
        scope.launch {
            if (activity?.get() == null) return@launch
            pendingJob?.cancel()
            // The card on Discord is already this one.
            if (payload.sameAs(last)) return@launch
            val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSentAt)
            if (wait <= 0) {
                send(payload)
            } else {
                pendingJob = scope.launch {
                    delay(wait)
                    send(payload)
                }
            }
        }
    }

    private fun send(payload: PresencePayload) {
        if (activity?.get() == null) return
        DiscordNative.update(payload.fields.toTypedArray(), payload.type, payload.statusDisplay, payload.start, payload.end)
        last = payload
        lastSentAt = System.currentTimeMillis()
        watchStatus(if (payload.paused) PresenceState.Paused else PresenceState.Active)
    }

    /** Removes the card but keeps the connection (leaving the player for a moment, a hidden anime). */
    fun clear(reason: PresenceState = PresenceState.Off) {
        scope.launch {
            pendingJob?.cancel()
            if (last != null) {
                DiscordNative.clear()
                last = null
                lastSentAt = System.currentTimeMillis()
            }
            if (activity != null) _status.value = PresenceStatus(reason)
        }
    }

    /** Removes the card and closes the connection opened through [owner]. */
    fun detach(owner: Activity) {
        scope.launch {
            if (activity?.get() !== owner) return@launch
            pendingJob?.cancel()
            statusJob?.cancel()
            if (last != null) DiscordNative.clear()
            last = null
            // Nothing else is sent from here on; the connection closes a moment later.
            activity = null
            appId = 0L
            connectionStale = true
            _status.value = PresenceStatus(PresenceState.Off)
            stopJob?.cancel()
            stopJob = scope.launch {
                // Lets the clear reach Discord before the connection closes.
                delay(STOP_DELAY_MS)
                DiscordNative.stop()
                connectionStale = false
            }
        }
    }

    /** Shows [payload] through [owner] for a while (the settings' test), then removes it. */
    fun test(owner: Activity, applicationId: Long, payload: PresencePayload, seconds: Long = 30) {
        attach(owner, applicationId)
        publish(payload)
        scope.launch {
            delay(seconds * 1000)
            detach(owner)
        }
    }

    private fun watchStatus(expected: PresenceState) {
        statusJob?.cancel()
        statusJob = scope.launch {
            delay(STATUS_DELAY_MS)
            val raw = DiscordNative.status()
            val phase = raw.substringBefore('|')
            val message = raw.substringAfter('|', "")
            _status.value = when (phase) {
                "ACTIVE" -> PresenceStatus(expected)
                "ERROR" -> PresenceStatus(PresenceState.Error, message)
                "UNAVAILABLE" -> PresenceStatus(PresenceState.Unavailable)
                else -> PresenceStatus(PresenceState.Connecting)
            }
        }
    }
}
