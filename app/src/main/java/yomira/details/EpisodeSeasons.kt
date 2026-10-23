package yomira.details

import yomira.metadata.EpisodeMetadata
import yomira.metadata.ShowMeta
import kotlin.math.floor

/** What [EpisodeSeasons.group] needs from an episode. */
data class SeasonInput(
    val id: Long,
    val name: String,
    val number: Double,
    /** Position in the source's list (0 is the first item the extension returned). */
    val sourceOrder: Long,
    /** Some extensions put the season in the scanlator field ("Temporada 2"). */
    val scanlator: String? = null,
)

/** One tab of the details screen: a season (number) or the specials (number null). */
data class SeasonGroup(
    /** Null for specials (OVAs, films, extras). */
    val season: Int?,
    /** Episode ids in chronological order. */
    val episodeIds: List<Long>,
    /** Number of each episode inside this season, for labels such as "T2 E3". */
    val numbers: Map<Long, Int>,
)

/**
 * Splits an entry's episodes into seasons. Extensions do this in many ways, so the signals are
 * tried from the strongest:
 *
 * 1. the season in the episode name or scanlator: "T2E3", "S02E03", "Temporada 2", "TEMPORADA II",
 *    "2ª Temporada", "Season 3", "2nd Season"; episodes between two marked ones take the season of
 *    the one before; specials, OVAs and films go to their own group;
 * 2. the numbering starting over (1…12, then 1…13);
 * 3. TMDB's seasons when the entry counts the whole show in one sequence.
 *
 * A single group is returned when nothing says otherwise; its season comes from the entry's TMDB
 * match ("Season 2" entries) or is 1.
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
    private val specialWords = Regex(
        """(?i)(?<![A-Za-z])(?:especial|especiais|special|specials|ova|oad|ona|filme|movie|recap|recapitula[çc][ãa]o|extra|extras)(?![A-Za-z])""",
    )
    private val episodeWord = Regex("""(?i)(?:epis[oó]dio|episode|ep\.?|cap[ií]tulo)\s*#?\s*(\d{1,4})(?![0-9])""")

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

    fun isSpecial(name: String): Boolean = specialWords.containsMatchIn(name) && seasonEpisode.find(name) == null

    fun group(episodes: List<SeasonInput>, show: ShowMeta? = null): List<SeasonGroup> {
        if (episodes.isEmpty()) return emptyList()
        val chronological = chronological(episodes)

        // 1. Seasons named in the episodes.
        val explicit = chronological.map { seasonIn(it.name) ?: seasonIn(it.scanlator) }
        val specials = chronological.map { isSpecial(it.name) }
        if (explicit.any { it != null }) {
            val firstNamed = explicit.firstOrNull { it != null }!!
            var current = (firstNamed - 1).coerceAtLeast(1).takeIf { explicit.first() == null } ?: firstNamed
            val seasons = chronological.indices.map { index ->
                explicit[index]?.let { current = it }
                current
            }
            return build(chronological, seasons, specials, numberBySeason = true)
        }

        // 2. Numbering that starts over.
        val resets = resetSeasons(chronological, specials)
        if (resets != null) return build(chronological, resets, specials, numberBySeason = true)

        // 3. One sequence for the whole show, split by TMDB.
        if (show != null && !show.isMovie && show.seasonHint == null) {
            val located = chronological.map { EpisodeMetadata.locate(show, it.number, it.name) }
            val distinct = located.mapNotNull { it?.first }.distinct()
            if (distinct.size > 1) {
                var current = distinct.first()
                val seasons = located.map { position ->
                    position?.first?.let { current = it }
                    current
                }
                val numbers = chronological.indices.mapNotNull { index ->
                    located[index]?.let { chronological[index].id to it.second }
                }.toMap()
                return build(chronological, seasons, specials, numberBySeason = false, known = numbers)
            }
        }

        // A single season: the entry's own (from its title or TMDB match) or 1.
        val season = show?.seasonHint?.takeIf { it > 0 } ?: 1
        return build(chronological, chronological.map { season }, specials, numberBySeason = true)
    }

    /** Oldest first, whatever order the extension uses. */
    private fun chronological(episodes: List<SeasonInput>): List<SeasonInput> {
        val bySource = episodes.sortedBy { it.sourceOrder }
        val first = bySource.firstOrNull { it.number >= 0 }
        val last = bySource.lastOrNull { it.number >= 0 }
        val descending = first != null && last != null && first !== last && first.number > last.number
        return if (descending) bySource.reversed() else bySource
    }

    /**
     * Seasons from the numbering going back to 1 (or 0) after it had passed 2, ignoring specials.
     * Null when it never does.
     */
    private fun resetSeasons(chronological: List<SeasonInput>, specials: List<Boolean>): List<Int>? {
        var season = 1
        var highest = 0.0
        var resets = 0
        val seasons = chronological.mapIndexed { index, episode ->
            if (!specials[index] && episode.number >= 0) {
                if (episode.number <= 1.0 && highest >= 2.0) {
                    season++
                    resets++
                    highest = 0.0
                }
                highest = maxOf(highest, episode.number)
            }
            season
        }
        return seasons.takeIf { resets > 0 }
    }

    private fun build(
        chronological: List<SeasonInput>,
        seasons: List<Int>,
        specials: List<Boolean>,
        numberBySeason: Boolean,
        known: Map<Long, Int> = emptyMap(),
    ): List<SeasonGroup> {
        val bySeason = linkedMapOf<Int?, MutableList<SeasonInput>>()
        chronological.forEachIndexed { index, episode ->
            val key = if (specials[index]) null else seasons[index]
            bySeason.getOrPut(key) { mutableListOf() }.add(episode)
        }
        // Only specials: they are the entry's episodes (a film, an OVA collection).
        if (bySeason.keys.all { it == null }) {
            val only = bySeason.getValue(null)
            return listOf(SeasonGroup(seasons.firstOrNull() ?: 1, only.map { it.id }, numbersOf(only, numberBySeason, known)))
        }
        return bySeason.entries
            .sortedWith(compareBy<Map.Entry<Int?, MutableList<SeasonInput>>> { it.key == null }.thenBy { it.key ?: 0 })
            .map { (season, list) -> SeasonGroup(season, list.map { it.id }, numbersOf(list, numberBySeason, known)) }
    }

    private fun numbersOf(list: List<SeasonInput>, numberBySeason: Boolean, known: Map<Long, Int>): Map<Long, Int> {
        val wholeNumbers = list.map { it.number }.filter { it >= 1 && it == floor(it) }
        // The extension's numbers work inside the season when they start at 1 and do not repeat.
        val usable = numberBySeason &&
            wholeNumbers.size == list.size &&
            wholeNumbers.distinct().size == wholeNumbers.size &&
            (wholeNumbers.minOrNull() ?: 0.0) <= 1.0
        return list.mapIndexed { index, episode ->
            // "Temporada II - Episódio 3" or "T2E3": the name numbers the episode inside its season.
            val named = episodeIn(episode.name).takeIf { numberBySeason && seasonIn(episode.name) != null }
            val number = known[episode.id]
                ?: named
                ?: episode.number.takeIf { usable }?.toInt()
                ?: (index + 1)
            episode.id to number
        }.toMap()
    }
}
