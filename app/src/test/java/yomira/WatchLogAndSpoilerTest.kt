package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import yomira.player.WatchLog
import yomira.ui.SpoilerShield
import java.time.LocalDate

class WatchLogAndSpoilerTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun log(vararg days: Pair<Long, Long>): String =
        days.joinToString(";") { (ago, seconds) -> "${today.minusDays(ago)}=$seconds" }

    @Test
    fun `stats sum today, the last seven days and the total`() {
        val stats = WatchLog.stats(log(0L to 600L, 1L to 1200L, 6L to 300L, 7L to 5000L), today)
        assertEquals(600L, stats.todaySeconds)
        assertEquals(2100L, stats.weekSeconds)
        assertEquals(7100L, stats.totalSeconds)
        assertEquals(5000L, stats.bestDaySeconds)
        assertEquals(listOf(300L, 0L, 0L, 0L, 0L, 1200L, 600L), stats.lastSevenDays)
    }

    @Test
    fun `streak counts consecutive days of at least five minutes`() {
        assertEquals(3, WatchLog.stats(log(0L to 400L, 1L to 900L, 2L to 301L, 4L to 9000L), today).streakDays)
        // Nothing yet today: a streak that reached yesterday is still alive.
        assertEquals(2, WatchLog.stats(log(1L to 900L, 2L to 900L), today).streakDays)
        // Under five minutes does not count.
        assertEquals(0, WatchLog.stats(log(0L to 120L, 1L to 100L), today).streakDays)
    }

    @Test
    fun `parse ignores broken entries`() {
        val map = WatchLog.parse("2026-10-21=60;nonsense;2026-13-40=5;2026-10-20=abc;2026-10-19=30")
        assertEquals(mapOf(LocalDate.of(2026, 10, 21) to 60L, LocalDate.of(2026, 10, 19) to 30L), map)
    }

    @Test
    fun `spoiler mask keeps only the episode number`() {
        assertEquals("T1E3", SpoilerShield.maskTitle("T1E3 - Aquele que é apto à dominância", "Ep"))
        assertEquals("Episode 12", SpoilerShield.maskTitle("Episode 12: The truth", "Ep"))
        assertEquals("Episódio 7.5", SpoilerShield.maskTitle("Episódio 7.5 A revelação", "Ep"))
        assertEquals("Ep", SpoilerShield.maskTitle("The final battle", "Ep"))
    }
}
