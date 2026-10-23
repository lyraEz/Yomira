package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import yomira.details.EpisodeSeasons
import yomira.details.SeasonInput
import yomira.metadata.SeasonInfo
import yomira.metadata.ShowMeta

class EpisodeSeasonsTest {

    private var nextId = 0L

    /** Episodes as an extension lists them, newest first unless [ascending]. */
    private fun episodes(vararg names: Pair<String, Double>, ascending: Boolean = true): List<SeasonInput> {
        val list = names.map { (name, number) -> SeasonInput(nextId++, name, number, 0) }
        val ordered = if (ascending) list else list.reversed()
        return ordered.mapIndexed { index, input -> input.copy(sourceOrder = index.toLong()) }
    }

    private fun shape(groups: List<yomira.details.SeasonGroup>) = groups.map { it.season to it.episodeIds.size }

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
    fun `explicit seasons in names, newest first`() {
        val list = episodes(
            "Temporada I - Episódio 1" to 1.0,
            "Temporada I - Episódio 2" to 2.0,
            "Temporada II - Episódio 1" to 3.0,
            "Temporada II - Episódio 2" to 4.0,
            "Temporada II - Episódio 3" to 5.0,
            ascending = false,
        )
        val groups = EpisodeSeasons.group(list)
        assertEquals(listOf(1 to 2, 2 to 3), shape(groups))
        // Numbers restart inside each season.
        assertEquals(listOf(1, 2, 3), groups[1].episodeIds.map { groups[1].numbers[it] })
    }

    @Test
    fun `unmarked episodes before the first marked season`() {
        val list = episodes(
            "Episódio 1" to 1.0,
            "Episódio 2" to 2.0,
            "T2E1" to 3.0,
            "T2E2" to 4.0,
        )
        assertEquals(listOf(1 to 2, 2 to 2), shape(EpisodeSeasons.group(list)))
    }

    @Test
    fun `numbering that starts over`() {
        val list = episodes(
            "Episódio 1" to 1.0,
            "Episódio 2" to 2.0,
            "Episódio 3" to 3.0,
            "Episódio 1" to 1.0,
            "Episódio 2" to 2.0,
        )
        assertEquals(listOf(1 to 3, 2 to 2), shape(EpisodeSeasons.group(list)))
    }

    @Test
    fun `specials go last`() {
        val list = episodes(
            "Episódio 1" to 1.0,
            "Episódio 2" to 2.0,
            "OVA - O Começo" to 3.0,
            "Filme" to 4.0,
        )
        assertEquals(listOf(1 to 2, null to 2), shape(EpisodeSeasons.group(list)))
    }

    @Test
    fun `one sequence split by TMDB`() {
        val show = ShowMeta(animeId = 1, tmdbId = 2, seasons = listOf(SeasonInfo(1, 3), SeasonInfo(2, 2)))
        val list = episodes(*(1..5).map { "Episódio $it" to it.toDouble() }.toTypedArray())
        val groups = EpisodeSeasons.group(list, show)
        assertEquals(listOf(1 to 3, 2 to 2), shape(groups))
        assertEquals(listOf(1, 2), groups[1].episodeIds.map { groups[1].numbers[it] })
    }

    @Test
    fun `single season from the TMDB match and continuing numbers`() {
        val show = ShowMeta(animeId = 1, tmdbId = 2, seasonHint = 2, seasons = listOf(SeasonInfo(1, 12), SeasonInfo(2, 12)))
        val list = episodes(*(13..15).map { "Episódio $it" to it.toDouble() }.toTypedArray())
        val groups = EpisodeSeasons.group(list, show)
        assertEquals(listOf(2 to 3), shape(groups))
        assertEquals(listOf(1, 2, 3), groups[0].episodeIds.map { groups[0].numbers[it] })
    }

    @Test
    fun `a film alone keeps one group`() {
        val groups = EpisodeSeasons.group(episodes("Filme" to 1.0))
        assertEquals(listOf(1 to 1), shape(groups))
    }
}
