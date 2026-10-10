package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

// YMR -->
/**
 * Regression tests for moving to the next episode inside the player.
 *
 * Reported in AniZen/Aniyomi: opening the next episode directly worked, but pressing the
 * next-episode arrow sometimes closed the player. The causes were (1) the previous episode's
 * server list being reused when the next one failed to load, (2) an empty or failed server list
 * finishing the activity and (3) an uncaught "no available videos" exception reaching a global
 * handler that also finished it. The switch now always resolves to "load servers" or "show an
 * error inside the player".
 */
class EpisodeNavigationTest {

    private val hoster = Hoster(hosterUrl = "https://example.org/e/1", hosterName = "Example")

    @Test
    fun `next goes to the following episode`() {
        EpisodeNavigation.adjacentIndex(size = 5, currentIndex = 1, previous = false) shouldBe 2
    }

    @Test
    fun `previous goes to the preceding episode`() {
        EpisodeNavigation.adjacentIndex(size = 5, currentIndex = 1, previous = true) shouldBe 0
    }

    @Test
    fun `there is no next episode after the last one`() {
        EpisodeNavigation.adjacentIndex(size = 3, currentIndex = 2, previous = false) shouldBe -1
        EpisodeNavigation.hasNext(size = 3, currentIndex = 2) shouldBe false
    }

    @Test
    fun `there is no previous episode before the first one`() {
        EpisodeNavigation.adjacentIndex(size = 3, currentIndex = 0, previous = true) shouldBe -1
        EpisodeNavigation.hasPrevious(size = 3, currentIndex = 0) shouldBe false
    }

    @Test
    fun `an episode missing from the playlist has no neighbours`() {
        // Used to report "has next" and jump to the first episode.
        EpisodeNavigation.adjacentIndex(size = 3, currentIndex = -1, previous = false) shouldBe -1
        EpisodeNavigation.hasNext(size = 3, currentIndex = -1) shouldBe false
        EpisodeNavigation.hasPrevious(size = 3, currentIndex = -1) shouldBe false
    }

    @Test
    fun `filler episodes are skipped when moving forward`() {
        val filler = setOf(2, 3)
        EpisodeNavigation.adjacentIndex(size = 6, currentIndex = 1, previous = false) { it in filler } shouldBe 4
    }

    @Test
    fun `only filler left means there is no next episode`() {
        EpisodeNavigation.adjacentIndex(size = 4, currentIndex = 1, previous = false) { it >= 2 } shouldBe -1
    }

    @Test
    fun `filler is not skipped when moving backward`() {
        EpisodeNavigation.adjacentIndex(size = 4, currentIndex = 3, previous = true) { true } shouldBe 2
    }

    @Test
    fun `servers for the next episode are loaded`() {
        val outcome = EpisodeNavigation.afterEpisodeLoad(hosters = listOf(hoster), error = null)
        outcome shouldBe EpisodeNavigation.Outcome.LoadHosters(listOf(hoster))
    }

    @Test
    fun `failing to list servers shows an error instead of closing the player`() {
        val failure = RuntimeException("HTTP 503")
        val outcome = EpisodeNavigation.afterEpisodeLoad(hosters = null, error = failure)
        outcome.shouldBeInstanceOf<EpisodeNavigation.Outcome.ShowError>()
        outcome.reason shouldBe PlaybackError.Reason.HOSTERS_FAILED
        outcome.cause shouldBe failure
    }

    @Test
    fun `an error wins over a server list left from the previous episode`() {
        val outcome = EpisodeNavigation.afterEpisodeLoad(hosters = listOf(hoster), error = RuntimeException())
        outcome.shouldBeInstanceOf<EpisodeNavigation.Outcome.ShowError>()
    }

    @Test
    fun `an empty server list shows an error instead of closing the player`() {
        val outcome = EpisodeNavigation.afterEpisodeLoad(hosters = emptyList(), error = null)
        outcome.shouldBeInstanceOf<EpisodeNavigation.Outcome.ShowError>()
        outcome.reason shouldBe PlaybackError.Reason.NO_HOSTERS
    }
}
// YMR <--
