package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import yomira.details.EpisodeSeasons

class EpisodeSeasonsTest {

    @Test
    fun `season written in many ways`() {
        assertEquals(2, EpisodeSeasons.seasonIn("T2E5"))
        assertEquals(1, EpisodeSeasons.seasonIn("S01E02 - Title"))
        assertEquals(2, EpisodeSeasons.seasonIn("Temporada 2 - Episódio 4"))
        assertEquals(2, EpisodeSeasons.seasonIn("TEMPORADA II EPISÓDIO 4"))
        assertEquals(3, EpisodeSeasons.seasonIn("Kimetsu - Temporada III"))
        assertEquals(2, EpisodeSeasons.seasonIn("2ª Temporada - Ep 7"))
        assertEquals(2, EpisodeSeasons.seasonIn("2nd Season Episode 7"))
        assertEquals(4, EpisodeSeasons.seasonIn("Season 4"))
        assertNull(EpisodeSeasons.seasonIn("Episódio 12"))
        assertNull(EpisodeSeasons.seasonIn("Sonho de Inverno"))
        assertEquals(5, EpisodeSeasons.episodeIn("T2E5"))
        assertEquals(12, EpisodeSeasons.episodeIn("Episódio 12 - O fim"))
    }

    @Test
    fun `season and episode together`() {
        assertEquals(2 to 4, EpisodeSeasons.seasonEpisodeIn("TEMPORADA II EPISÓDIO 4"))
        assertEquals(3 to 7, EpisodeSeasons.seasonEpisodeIn("Episódio 7", scanlator = "Temporada 3"))
        assertNull(EpisodeSeasons.seasonEpisodeIn("Episódio 7"))
        assertNull(EpisodeSeasons.seasonEpisodeIn("Temporada 2"))
    }
}
