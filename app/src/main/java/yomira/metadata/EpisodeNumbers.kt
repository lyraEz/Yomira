package yomira.metadata

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object EpisodeNumbers {

    private const val MAX_SEASON = 60

    /**
     * Season and episode packed into one number by some extensions: 2.004 is season 2, episode 4.
     * The value arrives as a float, so 1.001 reads 1.0010000467300415. Halves and other round
     * fractions (12.5, 1.25) stay ordinary episode numbers.
     */
    fun packed(value: Double): Pair<Int, Int>? {
        if (value.isNaN() || value < 1.0 || value >= MAX_SEASON + 1) return null
        val season = floor(value).toInt()
        val thousandths = (value - season) * 1000
        val episode = thousandths.roundToInt()
        if (abs(thousandths - episode) > 0.01) return null
        if (episode < 1 || episode % 50 == 0) return null
        return season to episode
    }

    /** "T2 E4" for a packed number. */
    fun packedLabel(value: Double): String? = packed(value)?.let { (season, episode) -> "T$season E$episode" }
}
