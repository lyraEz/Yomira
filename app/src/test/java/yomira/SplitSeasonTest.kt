package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import yomira.metadata.EpisodeMetadata
import yomira.metadata.ExtensionSeason
import yomira.metadata.SeasonInfo
import yomira.metadata.ShowMeta
import yomira.player.SkipTimes

class SplitSeasonTest {

    private fun show(vararg counts: Int, hint: Int? = null, named: Map<Int, ExtensionSeason> = emptyMap()) = ShowMeta(
        animeId = 1,
        tmdbId = 2,
        seasons = counts.mapIndexed { index, count -> SeasonInfo(index + 1, count) },
        seasonHint = hint,
        extensionSeasons = named,
    )

    @Test
    fun `second season numbered on from the first`() {
        // Kusuriya no Hitorigoto: 2.025 is TMDB's S2E1.
        assertEquals(2 to 1, EpisodeMetadata.locate(show(24, 24, hint = 1), 2.025, "Espelhos"))
        assertEquals(2 to 4, EpisodeMetadata.locate(show(24, 24), 28.0, "T2E28 - Espelhos"))
    }

    @Test
    fun `extension season that TMDB keeps inside one season`() {
        // Tensei Kizoku: TMDB has one 29-episode season, the extension calls 13 onwards "T2".
        val named = mapOf(1 to ExtensionSeason(1, 12), 2 to ExtensionSeason(13, 16))
        assertEquals(1 to 13, EpisodeMetadata.locate(show(29, named = named), 13.0, "T2E13 - Promessa"))
        assertEquals(1 to 16, EpisodeMetadata.locate(show(29, named = named), 2.016, "Forte Vakmakro"))
        // Without knowing where the extension's season starts, nothing is guessed.
        assertEquals(2 to 13, EpisodeMetadata.locate(show(29), 13.0, "T2E13 - Promessa"))
    }

    @Test
    fun `extension season restarting at 1 inside a TMDB season`() {
        val named = mapOf(1 to ExtensionSeason(1, 12), 2 to ExtensionSeason(1, 12))
        assertEquals(1 to 13, EpisodeMetadata.locate(show(24, named = named), 1.0, "T2E1"))
        // TMDB does not list that many episodes yet: no wrong image.
        assertEquals(2 to 1, EpisodeMetadata.locate(show(12, named = named), 1.0, "T2E1"))
    }

    @Test
    fun `regular seasons stay as they are`() {
        assertEquals(2 to 1, EpisodeMetadata.locate(show(12, 12), 1.0, "T2E1"))
        assertEquals(1 to 5, EpisodeMetadata.locate(show(12, 12, hint = 1), 5.0, "Episódio 5"))
    }

    @Test
    fun `extension seasons are read from the episode list`() {
        val seasons = EpisodeMetadata.extensionSeasons(
            listOf(1.0 to "T1E1", 12.0 to "T1E12", 13.0 to "T2E13 - Promessa", 2.016 to "Forte"),
        )
        assertEquals(mapOf(1 to ExtensionSeason(1, 12), 2 to ExtensionSeason(13, 16)), seasons)
    }

    @Test
    fun `AniSkip keeps one result per kind and moves endings with the video length`() {
        val results = listOf(
            SkipTimes.AniSkipResult("op", 90.0, 180.0, 1420.0),
            SkipTimes.AniSkipResult("op", 10.0, 100.0, 1500.0),
            SkipTimes.AniSkipResult("ed", 1300.0, 1390.0, 1420.0),
            SkipTimes.AniSkipResult("op", 0.0, 900.0, 1430.0),
        )
        // This video is 10 seconds longer: the ending moves, the opening stays.
        val picked = SkipTimes.pickAniSkip(results, 1430.0).associateBy { it.kind }
        assertEquals(90.0, picked.getValue("op").start)
        assertEquals(1310.0, picked.getValue("ed").start)
        assertEquals(1400.0, picked.getValue("ed").end)
        // Same length: nothing moves.
        assertEquals(1300.0, SkipTimes.pickAniSkip(results, 1420.0).first { it.kind == "ed" }.start)
    }
}
