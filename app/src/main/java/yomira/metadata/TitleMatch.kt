package yomira.metadata

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/** Cleans anime titles from extensions and compares them with catalogue titles. */
object TitleMatch {

    private val bracketed = Regex("""\[[^]]*]|\{[^}]*}""")
    private val audioMarkers = Regex(
        """(?i)\(\s*(?:dub(?:lado)?|leg(?:endado)?|sub(?:s|bed|titled)?|pt-?br|uncensored|sem censura|tv)\s*\)""" +
            """|\b(?:dublado|legendado|dub|pt-?br)\b""",
    )
    private val seasonWords = listOf(
        Regex("""(?i)\b(?:season|temporada|saison)\s*(\d{1,2})\b"""),
        Regex("""(?i)\b(\d{1,2})\s*(?:st|nd|rd|th|ª|º|a)?\s*(?:season|temporada)\b"""),
        Regex("""(?i)(?:^|\s)S(\d{1,2})$"""),
    )
    private val partWords = Regex("""(?i)\b(?:part|parte|cour)\s*\d\b""")
    private val romanSeason = Regex("""\s(II|III|IV|V)$""")
    private val romanValues = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5)

    /** Title without dub/sub markers and brackets, for catalogues that list each season apart. */
    fun withoutMarkers(title: String): String =
        title.replace(bracketed, " ").replace(audioMarkers, " ").collapse()

    /** Title for catalogues that group seasons (TMDB) and the season it names, if any. */
    fun forShowSearch(title: String): Pair<String, Int?> {
        var text = withoutMarkers(title)
        var season: Int? = null
        for (pattern in seasonWords) {
            val match = pattern.find(text) ?: continue
            season = match.groupValues[1].toIntOrNull()
            text = text.removeRange(match.range).toString()
            break
        }
        if (season == null) {
            romanSeason.find(text)?.let { match ->
                season = romanValues[match.groupValues[1]]
                text = text.removeRange(match.range).toString()
            }
        }
        text = text.replace(partWords, " ").trimEnd(':', '-', '–', ' ').collapse()
        return text to season
    }

    /** Lowercase, no accents, letters and digits only. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    /** 0..1, 1 = same title. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val distance = levenshtein(x, y)
        val ratio = 1.0 - distance.toDouble() / max(x.length, y.length)
        // A title that is the start of the other ("Frieren" vs "Frieren: Beyond Journey's End").
        val prefix = if (x.startsWith(y) || y.startsWith(x)) 0.75 else 0.0
        // Whole words inside the other ("Kimetsu no Yaiba" in "Demon Slayer: Kimetsu no Yaiba").
        val shorter = if (x.length <= y.length) x else y
        val longer = if (x.length <= y.length) y else x
        val inside = if (shorter.length >= 6 && " $longer ".contains(" $shorter ")) 0.72 else 0.0
        return maxOf(ratio, prefix, inside)
    }

    private val trailingNumber = Regex("""\s+\d{1,2}$""")
    private val subtitleSplit = Regex("""\s*(?::|\s-\s|\s–\s)\s*""")

    /**
     * Searches to try for a show, best first: the title, then without a trailing season number
     * ("Boku no Hero Academia 6") and without the subtitle ("Kimetsu no Yaiba: Yuukaku-hen").
     */
    fun searchVariants(query: String): List<String> {
        val variants = mutableListOf(query)
        trailingNumber.find(query)?.let { variants += query.removeRange(it.range).toString() }
        val head = query.split(subtitleSplit, limit = 2).first()
        if (head != query && head.length >= 4) variants += head
        return variants.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun String.collapse(): String = replace(Regex("""\s+"""), " ").trim()

    /** "T1E2", "S02E05", "T1 E2" in an episode name. */
    private val seasonEpisode = Regex("""(?i)\b[TS](\d{1,2})\s*[:._-]?\s*E[Pp]?\s*(\d{1,4})\b""")

    fun seasonEpisodeIn(name: String): Pair<Int, Int>? {
        val match = seasonEpisode.find(name) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null
        return season to episode
    }

    /** Words, numbers and markers that say nothing about the episode itself. */
    private val filler = Regex(
        """(?i)\b[TS]\d{1,2}\s*[:._-]?\s*E[Pp]?\s*\d{1,4}\b""" +
            """|\b(?:epis[oó]dios?|episodes?|eps?|cap[ií]tulos?|chapters?|temporadas?|seasons?|partes?|parts?""" +
            """|dublado|legendado|dub|leg|subs?|hd|fhd|sd|uncensored|sem censura)\b""" +
            """|[\d\s\p{P}\p{S}]+""",
    )

    /** Names that carry no information ("Episódio 3", "Episode 3", "T1E2 episódios 2"). */
    fun isGenericName(name: String?): Boolean = name.isNullOrBlank() || filler.replace(name, "").isBlank()
}
