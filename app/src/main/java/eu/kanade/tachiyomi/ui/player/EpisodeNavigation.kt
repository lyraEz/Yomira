package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster

// YMR -->
/**
 * Pure rules for moving between episodes inside the player.
 *
 * Kept free of Android and of the view model so the episode-to-episode transition can be unit
 * tested: an episode that fails to load must leave the player open with an error the user can act
 * on (retry, pick another server or episode, leave), never close it on its own.
 */
object EpisodeNavigation {

    /**
     * Index of the episode before or after [currentIndex] in a playlist of [size] items, or -1 when
     * there is none. [isSkippable] lets filler episodes be jumped over when moving forward.
     */
    fun adjacentIndex(
        size: Int,
        currentIndex: Int,
        previous: Boolean,
        isSkippable: (Int) -> Boolean = { false },
    ): Int {
        if (currentIndex !in 0..<size) return -1
        if (previous) return if (currentIndex > 0) currentIndex - 1 else -1

        var index = currentIndex + 1
        while (index < size && isSkippable(index)) {
            index++
        }
        return if (index < size) index else -1
    }

    fun hasPrevious(size: Int, currentIndex: Int): Boolean =
        adjacentIndex(size, currentIndex, previous = true) != -1

    fun hasNext(size: Int, currentIndex: Int): Boolean =
        adjacentIndex(size, currentIndex, previous = false) != -1

    /** What the player does once the hoster list of the requested episode is known. */
    sealed interface Outcome {
        data class LoadHosters(val hosters: List<Hoster>) : Outcome
        data class ShowError(val reason: PlaybackError.Reason, val cause: Throwable?) : Outcome
    }

    fun afterEpisodeLoad(hosters: List<Hoster>?, error: Throwable?): Outcome = when {
        error != null -> Outcome.ShowError(PlaybackError.Reason.HOSTERS_FAILED, error)
        hosters == null -> Outcome.ShowError(PlaybackError.Reason.HOSTERS_FAILED, null)
        hosters.isEmpty() -> Outcome.ShowError(PlaybackError.Reason.NO_HOSTERS, null)
        else -> Outcome.LoadHosters(hosters)
    }
}

/**
 * A playback problem shown inside the player instead of closing it.
 *
 * @param episodeId the episode the error belongs to, so a retry reloads the same one.
 */
data class PlaybackError(
    val reason: Reason,
    val episodeId: Long?,
    val episodeName: String?,
    val detail: String?,
) {
    enum class Reason {
        /** The source failed while listing servers (network, site change, expired session). */
        HOSTERS_FAILED,

        /** The source answered but offered no server for this episode. */
        NO_HOSTERS,

        /** Every server was tried and none produced a playable video. */
        NO_PLAYABLE_VIDEO,

        /** mpv could not open or keep playing the stream (expired URL, HTTP error, codec). */
        STREAM_FAILED,
    }
}
// YMR <--
