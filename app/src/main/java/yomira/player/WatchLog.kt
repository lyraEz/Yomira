package yomira.player

import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class WatchLogPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** "yyyy-MM-dd=seconds;..." for the last [WatchLog.KEEP_DAYS] days. */
    fun log() = store.getString("yomira_watch_log", "")

    /** Daily goal in minutes, 0 = no goal. */
    fun dailyGoal() = store.getInt("yomira_watch_goal", 0)
}

/** Summary shown in the More tab. */
data class WatchStats(
    val todaySeconds: Long,
    val weekSeconds: Long,
    val totalSeconds: Long,
    val streakDays: Int,
    val bestDaySeconds: Long,
    /** Seconds per day for the last 7 days, oldest first (the last one is today). */
    val lastSevenDays: List<Long>,
)

/**
 * Counts how long videos actually play (paused time does not count), per day, on the device.
 * Written every minute and when the player closes; nothing leaves the phone.
 */
object WatchLog {

    const val KEEP_DAYS = 400
    private const val TICK_MS = 10_000L
    private const val FLUSH_EVERY_TICKS = 6

    /** Runs while the player is visible; [isPlaying] is polled every few seconds. */
    suspend fun track(isPlaying: () -> Boolean, prefs: WatchLogPreferences = WatchLogPreferences()) {
        var pending = 0L
        var ticks = 0
        try {
            while (true) {
                delay(TICK_MS)
                if (isPlaying()) pending += TICK_MS / 1000
                if (++ticks >= FLUSH_EVERY_TICKS && pending > 0) {
                    add(prefs, pending)
                    pending = 0
                    ticks = 0
                }
            }
        } finally {
            if (pending > 0) add(prefs, pending)
        }
    }

    @Synchronized
    private fun add(prefs: WatchLogPreferences, seconds: Long) {
        val today = LocalDate.now()
        val map = parse(prefs.log().get())
        map[today] = (map[today] ?: 0L) + seconds
        val oldest = today.minusDays(KEEP_DAYS.toLong())
        prefs.log().set(
            map.filterKeys { it.isAfter(oldest) }
                .toSortedMap()
                .entries
                .joinToString(";") { "${it.key}=${it.value}" },
        )
    }

    fun parse(raw: String): MutableMap<LocalDate, Long> {
        val map = HashMap<LocalDate, Long>()
        raw.split(';').forEach { entry ->
            val parts = entry.split('=')
            if (parts.size != 2) return@forEach
            val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
            val seconds = parts[1].toLongOrNull() ?: return@forEach
            map[day] = seconds
        }
        return map
    }

    fun stats(raw: String, today: LocalDate = LocalDate.now()): WatchStats {
        val map = parse(raw)
        val week = (0L..6L).sumOf { map[today.minusDays(it)] ?: 0L }
        // A streak survives until the end of today: watching yesterday still counts.
        var day = if ((map[today] ?: 0L) >= MIN_DAY_SECONDS) today else today.minusDays(1)
        var streak = 0
        while ((map[day] ?: 0L) >= MIN_DAY_SECONDS) {
            streak++
            day = day.minusDays(1)
        }
        return WatchStats(
            todaySeconds = map[today] ?: 0L,
            weekSeconds = week,
            totalSeconds = map.values.sum(),
            streakDays = streak,
            bestDaySeconds = map.values.maxOrNull() ?: 0L,
            lastSevenDays = (6L downTo 0L).map { map[today.minusDays(it)] ?: 0L },
        )
    }

    /** A day counts towards the streak after five minutes of watching. */
    private const val MIN_DAY_SECONDS = 300L
}
