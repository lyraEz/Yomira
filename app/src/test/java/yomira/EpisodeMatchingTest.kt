package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yomira.metadata.EpisodeMetadata
import yomira.metadata.SeasonInfo
import yomira.metadata.ShowMeta
import yomira.metadata.TitleMatch

class EpisodeMatchingTest {

    private fun show(hint: Int? = null, offset: Int = 0, vararg counts: Int) = ShowMeta(
        animeId = 1,
        tmdbId = 2,
        seasons = listOf(SeasonInfo(0, 3)) + counts.mapIndexed { index, count -> SeasonInfo(index + 1, count) },
        seasonHint = hint,
        offset = offset,
    )

    @Test
    fun `generic names are recognised`() {
        listOf("Episódio 3", "Episode 12", "T1E2 episódios 2", "Ep. 5", "EP 07 [HD]", "", null).forEach {
            assertTrue(TitleMatch.isGenericName(it), "$it")
        }
        listOf("Episódio 12 - O Retorno", "Como um garoto", "第1話", "Let's Go").forEach {
            assertFalse(TitleMatch.isGenericName(it), it)
        }
    }

    @Test
    fun `show search drops markers and reads the season`() {
        assertEquals("Shingeki no Kyojin" to 3, TitleMatch.forShowSearch("Shingeki no Kyojin Season 3 Part 2 (Dublado)"))
        assertEquals("Jujutsu Kaisen" to 2, TitleMatch.forShowSearch("Jujutsu Kaisen 2nd Season"))
        assertEquals("Spy x Family" to 2, TitleMatch.forShowSearch("Spy x Family Temporada 2"))
        assertEquals("Overlord" to 2, TitleMatch.forShowSearch("Overlord II"))
        assertEquals("Frieren" to null, TitleMatch.forShowSearch("Frieren [Legendado]"))
    }

    @Test
    fun `season and episode in names`() {
        assertEquals(1 to 2, TitleMatch.seasonEpisodeIn("T1E2 episódios 2"))
        assertEquals(2 to 5, TitleMatch.seasonEpisodeIn("S02E05 - Title"))
        assertEquals(1 to 12, TitleMatch.seasonEpisodeIn("T1 E12"))
        assertNull(TitleMatch.seasonEpisodeIn("Episódio 3"))
    }

    @Test
    fun `similar titles and search variants`() {
        assertTrue(TitleMatch.similarity("Kimetsu no Yaiba", "Demon Slayer: Kimetsu no Yaiba") >= 0.72)
        assertTrue(TitleMatch.similarity("Frieren", "Frieren: Beyond Journey's End") >= 0.75)
        assertEquals(1.0, TitleMatch.similarity("Ataque dos Titãs", "ataque dos titas"))
        assertTrue(TitleMatch.similarity("Naruto", "Bleach") < 0.5)
        assertEquals(
            listOf("Boku no Hero Academia 6", "Boku no Hero Academia"),
            TitleMatch.searchVariants("Boku no Hero Academia 6"),
        )
        assertEquals(
            listOf("Kimetsu no Yaiba: Yuukaku-hen", "Kimetsu no Yaiba"),
            TitleMatch.searchVariants("Kimetsu no Yaiba: Yuukaku-hen"),
        )
    }

    @Test
    fun `without a season the running number crosses seasons`() {
        val show = show(null, 0, 12, 12, 10)
        assertEquals(2 to 2, EpisodeMetadata.locate(show, 14.0, "Episódio 14"))
        assertEquals(1 to 1, EpisodeMetadata.locate(show, 1.0, "Episódio 1"))
        assertNull(EpisodeMetadata.locate(show, 40.0, "Episódio 40"))
        assertNull(EpisodeMetadata.locate(show, 12.5, "Episódio 12.5"))
        // "T2E5" in the name decides when the season is unknown.
        assertEquals(2 to 5, EpisodeMetadata.locate(show, 1.0, "T2E5"))
    }

    @Test
    fun `a known season wins over the name`() {
        val show = show(2, 0, 12, 12, 10)
        assertEquals(2 to 3, EpisodeMetadata.locate(show, 3.0, "T1E3"))
        // The extension keeps counting from the first season.
        assertEquals(2 to 2, EpisodeMetadata.locate(show, 14.0, "Episódio 14"))
        assertEquals("T2 E3", EpisodeMetadata.seasonEpisodeLabel(show, 3.0, "Episódio 3"))
    }

    @Test
    fun `second cour inside one season uses the offset`() {
        val show = show(2, 7, 26, 18)
        assertEquals(2 to 8, EpisodeMetadata.locate(show, 1.0, "Episódio 1"))
        assertEquals(2 to 18, EpisodeMetadata.locate(show, 11.0, "Episódio 11"))
        // Numbered from the start of the season already.
        assertEquals(2 to 12, EpisodeMetadata.locate(show, 12.0, "Episódio 12"))
    }

    @Test
    fun `airing season with few episodes listed keeps its season`() {
        val show = show(3, 0, 12, 12, 5)
        assertEquals(3 to 8, EpisodeMetadata.locate(show, 8.0, "Episódio 8"))
    }

    @Test
    fun `movies and manual offsets`() {
        assertEquals(1 to 1, EpisodeMetadata.locate(ShowMeta(1, 2, isMovie = true), 1.0, "Filme"))
        assertEquals(1 to 1, EpisodeMetadata.locate(show(null, -12, 12, 12), 13.0, "Episódio 13"))
    }
}
