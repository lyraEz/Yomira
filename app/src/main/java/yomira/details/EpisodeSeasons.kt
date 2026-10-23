package yomira.details

/**
 * Reads seasons and episode numbers written in episode names. Extensions write them in many
 * ways: "T2E3", "S02E03", "Temporada 2", "TEMPORADA II", "2ª Temporada", "Season 3", "2nd Season".
 */
object EpisodeSeasons {

    private val roman = mapOf(
        "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5,
        "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
    )

    private val seasonEpisode = Regex("""(?i)(?<![A-Za-z0-9])[TS]\s?(\d{1,2})\s*[:._-]?\s*E[Pp]?\s*(\d{1,4})(?![0-9])""")
    private val seasonWord = Regex(
        """(?i)(?<![A-Za-z])(?:temporada|season|saison|staffel|temp\.)\s*[:#-]?\s*(\d{1,2}|[IVX]{1,4})(?![A-Za-z0-9])""",
    )
    private val ordinalSeason = Regex(
        """(?i)(?<![A-Za-z0-9])(\d{1,2})\s*(?:ª|º|a|st|nd|rd|th|°)?\s*(?:temporada|season)(?![A-Za-z])""",
    )
    private val episodeWord = Regex("""(?i)(?:epis[oóÓ]dio|episode|ep\.?|cap[iíÍ]tulo)\s*#?\s*(\d{1,4})(?![0-9])""")

    /** Season named in a text, or null. */
    fun seasonIn(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        seasonEpisode.find(text)?.let { return it.groupValues[1].toIntOrNull()?.takeIf { s -> s in 0..99 } }
        seasonWord.find(text)?.let { match ->
            val value = match.groupValues[1]
            return value.toIntOrNull() ?: roman[value.uppercase()]
        }
        ordinalSeason.find(text)?.let { return it.groupValues[1].toIntOrNull()?.takeIf { s -> s in 1..99 } }
        return null
    }

    /** Episode number written in a name ("T2E5", "Episódio 5", "EP 05"), or null. */
    fun episodeIn(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        seasonEpisode.find(name)?.let { return it.groupValues[2].toIntOrNull() }
        return episodeWord.find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Season and episode written in a name, as in "Temporada II - Episódio 4". The season may also
     * come from the scanlator field, where some extensions put it.
     */
    fun seasonEpisodeIn(name: String?, scanlator: String? = null): Pair<Int, Int>? {
        val episode = episodeIn(name) ?: return null
        val season = seasonIn(name) ?: seasonIn(scanlator) ?: return null
        return season.takeIf { it >= 1 }?.let { it to episode }
    }
}
