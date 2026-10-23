package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import yomira.metadata.EpisodeDisplays
import yomira.metadata.EpisodeNumbers
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

    @Test
    fun `season and episode packed in one number`() {
        // Floats as they arrive from the extension.
        assertEquals(1 to 1, EpisodeNumbers.packed(1.001f.toDouble()))
        assertEquals(1 to 2, EpisodeNumbers.packed(1.0019999742507935))
        assertEquals(2 to 4, EpisodeNumbers.packed(2.003999948501587))
        assertEquals(3 to 16, EpisodeNumbers.packed(3.0160000324249268))
        assertEquals(1 to 10, EpisodeNumbers.packed(1.01))
        // Ordinary numbers stay as they are.
        assertNull(EpisodeNumbers.packed(5.0))
        assertNull(EpisodeNumbers.packed(12.5))
        assertNull(EpisodeNumbers.packed(1.25))
        assertNull(EpisodeNumbers.packed(0.001))
        assertNull(EpisodeNumbers.packed(1717171717.0))

        assertEquals("1", EpisodeDisplays.displayNumber(1.0010000467300415, "O Mundo das Espadas"))
        assertEquals("T2 E4", EpisodeDisplays.shortLabel(2.003999948501587, "", "Ep %s"))
        assertEquals("Ep 5", EpisodeDisplays.shortLabel(5.0, "", "Ep %s"))
        assertEquals("Episódio 1 - O Mundo", TitleMatch.cleanEpisodeName("Episódio 1.0010000467300415 - O Mundo"))
        assertEquals("Episódio 12.5", TitleMatch.cleanEpisodeName("Episódio 12.500000001"))
    }
}
