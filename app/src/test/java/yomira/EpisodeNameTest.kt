package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import yomira.metadata.EpisodeDisplays
import yomira.metadata.TitleMatch

class EpisodeNameTest {

    @Test
    fun `long ids and file noise leave the name`() {
        assertEquals("Episódio 5", TitleMatch.cleanEpisodeName("Episódio 5 - 1717171717"))
        assertEquals("Episódio 5", TitleMatch.cleanEpisodeName("1699999999123 Episódio 5"))
        assertEquals("Episode 3", TitleMatch.cleanEpisodeName("Episode 3 [A1B2C3D4] (1080p).mkv"))
        assertEquals("O começo", TitleMatch.cleanEpisodeName("O começo [1080p HEVC]"))
    }

    @Test
    fun `normal names stay as they are`() {
        assertEquals("Episódio 12 - O fim", TitleMatch.cleanEpisodeName("Episódio 12 - O fim"))
        assertEquals("Ano 2024", TitleMatch.cleanEpisodeName("Ano 2024"))
        assertEquals("1.5", TitleMatch.cleanEpisodeName("1.5"))
        // Only noise: the original is kept rather than an empty name.
        assertEquals("12345678", TitleMatch.cleanEpisodeName("12345678"))
    }

    @Test
    fun `episode number falls back to the name when the value is an id`() {
        assertEquals("5", EpisodeDisplays.displayNumber(5.0, "Episódio 5"))
        assertEquals("2.5", EpisodeDisplays.displayNumber(2.5, "Especial"))
        assertEquals("7", EpisodeDisplays.displayNumber(1717171717.0, "Episódio 7"))
        assertEquals("7", EpisodeDisplays.displayNumber(4521.0, "Episódio 7"))
        assertEquals("1050", EpisodeDisplays.displayNumber(1050.0, "One Piece 1050"))
        assertNull(EpisodeDisplays.displayNumber(1717171717.0, "Sem número"))
        assertNull(EpisodeDisplays.displayNumber(-1.0, "Sem número"))
    }
}
