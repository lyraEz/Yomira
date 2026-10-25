package yomira.discord

import yomira.metadata.TitleMatch
import java.net.URLEncoder

data class PresenceButtonOption(val kind: String, val label: String = "", val url: String = "")

/** A snapshot of [PresencePreferences]. */
data class PresenceOptions(
    val activityType: String = "watching",
    val statusDisplay: String = "name",
    val titleSource: String = "tmdb",
    val detailsLine: String = "anime",
    val stateLine: String = "episode",
    val tmdbEpisodeNames: Boolean = true,
    val badge: Boolean = true,
    val largeImage: String = "poster",
    val smallImage: String = "none",
    val timeMode: String = "bar",
    val pausedMode: String = "frozen",
    val links: Boolean = true,
    val buttons: List<PresenceButtonOption> = listOf(PresenceButtonOption(PresencePreferences.BUTTON_CRUNCHYROLL)),
)

/** What is playing, as the player knows it. Every text may be missing. */
data class PresenceInput(
    val extensionTitle: String,
    val tmdbTitle: String? = null,
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val extensionEpisodeName: String = "",
    val tmdbEpisodeName: String? = null,
    val episodeOverview: String? = null,
    /** "Episódio 5", used when the episode has no real name. */
    val numberLabel: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val episodeImageUrl: String? = null,
    val sourceName: String? = null,
    val positionSeconds: Long = 0,
    val durationSeconds: Long = 0,
    val paused: Boolean = false,
    val nowSeconds: Long = System.currentTimeMillis() / 1000,
    val aniListUrl: String? = null,
    val malUrl: String? = null,
    val tmdbUrl: String? = null,
    val sourceUrl: String? = null,
)

/** Button texts in the user's language. */
data class PresenceLabels(
    val crunchyroll: String = "Watch on Crunchyroll",
    val aniList: String = "View on AniList",
    val mal: String = "View on MyAnimeList",
    val tmdb: String = "View on TMDB",
    val source: String = "Open the website",
)

/** What is sent to Discord: the text fields in [DiscordNative] order plus type, status and times. */
data class PresencePayload(
    val fields: List<String>,
    val type: Int,
    val statusDisplay: Int,
    val start: Long,
    val end: Long,
    val paused: Boolean = false,
) {
    /** Same card, allowing the clock to drift by a couple of seconds between two reads. */
    fun sameAs(other: PresencePayload?): Boolean {
        other ?: return false
        if (fields != other.fields || type != other.type || statusDisplay != other.statusDisplay) return false
        if (paused != other.paused) return false
        return kotlin.math.abs(start - other.start) <= 2 && kotlin.math.abs(end - other.end) <= 2
    }
}

/**
 * Turns what is playing into a Rich Presence card, within Discord's limits (texts of 2 to 128
 * characters, links up to 256, button labels up to 32). A field that does not fit is dropped,
 * because one bad field makes Discord refuse the whole card.
 */
object PresenceBuilder {

    private const val TEXT_MAX = 128
    private const val URL_MAX = 256
    private const val IMAGE_MAX = 300
    private const val BUTTON_LABEL_MAX = 32
    private const val BUTTON_URL_MAX = 512

    private val numberPrefix = Regex(
        """^\s*(?:[TtSs]\d{1,2}\s*[:._-]?\s*[Ee][Pp]?\s*\d{1,4}""" +
            """|(?:[Ee]pis[oóÓ]dio|EPIS[OÓ]DIO|[Ee]pisode|EPISODE|[Ee][Pp]\.?|[Cc]ap[ií]tulo)\s*#?\s*\d{1,4})""" +
            """\s*[-–—:|]\s*""",
    )

    fun build(input: PresenceInput, options: PresenceOptions, labels: PresenceLabels = PresenceLabels()): PresencePayload? {
        if (input.paused && options.pausedMode == "hide") return null
        val fields = MutableList(DiscordNative.FIELD_COUNT) { "" }

        val title = title(input, options)
        val episodeName = episodeName(input, options)
        fields[DiscordNative.DETAILS] = text(
            when (options.detailsLine) {
                "episode" -> episodeName
                "none" -> ""
                else -> title
            },
        )
        val frozen = input.paused && options.pausedMode == "frozen"
        fields[DiscordNative.STATE] = text(
            when {
                frozen -> frozenClock(input.positionSeconds, input.durationSeconds)
                options.stateLine == "overview" -> input.episodeOverview?.takeIf { it.isNotBlank() } ?: episodeName
                options.stateLine == "number" -> input.numberLabel
                options.stateLine == "source" -> input.sourceName.orEmpty()
                options.stateLine == "none" -> ""
                else -> episodeName
            },
        )

        val poster = image(input.posterUrl)
        val still = image(input.episodeImageUrl)
        val large = when (options.largeImage) {
            "episode" -> still.ifEmpty { poster }
            "none" -> ""
            else -> poster.ifEmpty { still }
        }
        fields[DiscordNative.LARGE_IMAGE] = large
        fields[DiscordNative.LARGE_TEXT] = text(badge(input, options).ifEmpty { title })
        val small = when (options.smallImage) {
            "poster" -> poster
            "episode" -> still
            else -> ""
        }.takeIf { it != large }.orEmpty()
        fields[DiscordNative.SMALL_IMAGE] = small
        if (small.isNotEmpty()) fields[DiscordNative.SMALL_TEXT] = text(episodeName.ifBlank { title })

        if (options.links) {
            val page = link(input.aniListUrl, URL_MAX).ifEmpty { link(input.tmdbUrl, URL_MAX) }
            if (fields[DiscordNative.DETAILS].isNotEmpty()) fields[DiscordNative.DETAILS_URL] = page
            if (large.isNotEmpty()) fields[DiscordNative.LARGE_URL] = page
        }

        val buttons = options.buttons
            .mapNotNull { button(it, input, title, labels) }
            .distinctBy { it.second }
            .take(2)
        buttons.getOrNull(0)?.let { (label, url) ->
            fields[DiscordNative.BUTTON1_LABEL] = label
            fields[DiscordNative.BUTTON1_URL] = url
        }
        buttons.getOrNull(1)?.let { (label, url) ->
            fields[DiscordNative.BUTTON2_LABEL] = label
            fields[DiscordNative.BUTTON2_URL] = url
        }

        val position = input.positionSeconds.coerceAtLeast(0)
        val duration = input.durationSeconds.coerceAtLeast(0)
        val start = if (input.paused || options.timeMode == "none") 0L else input.nowSeconds - position
        val end = if (start > 0 && options.timeMode == "bar" && duration > position) start + duration else 0L

        val type = when (options.activityType) {
            "playing" -> 0
            "listening" -> 2
            else -> 3
        }
        val display = when (options.statusDisplay) {
            "state" -> if (fields[DiscordNative.STATE].isNotEmpty()) 1 else 0
            "details" -> if (fields[DiscordNative.DETAILS].isNotEmpty()) 2 else 0
            else -> 0
        }
        return PresencePayload(fields, type, display, start, end, input.paused)
    }

    fun title(input: PresenceInput, options: PresenceOptions): String {
        val cleaned = TitleMatch.withoutMarkers(input.extensionTitle).ifBlank { input.extensionTitle.trim() }
        val picked = when (options.titleSource) {
            "english" -> input.englishTitle ?: input.tmdbTitle
            "romaji" -> input.romajiTitle
            "extension" -> null
            else -> input.tmdbTitle ?: input.englishTitle
        }
        return picked?.trim()?.takeIf { it.isNotEmpty() } ?: cleaned
    }

    fun episodeName(input: PresenceInput, options: PresenceOptions): String {
        input.tmdbEpisodeName?.trim()
            ?.takeIf { options.tmdbEpisodeNames && it.isNotEmpty() && !TitleMatch.isGenericName(it) }
            ?.let { return it }
        val cleaned = TitleMatch.cleanEpisodeName(input.extensionEpisodeName).replace(numberPrefix, "").trim()
        return if (TitleMatch.isGenericName(cleaned)) input.numberLabel else cleaned
    }

    /** "Season N, Episode M": Discord turns it into the S#E# badge. */
    private fun badge(input: PresenceInput, options: PresenceOptions): String {
        if (!options.badge) return ""
        val season = input.season ?: return ""
        val episode = input.episode ?: return ""
        if (season < 1 || episode < 1) return ""
        return "Season $season, Episode $episode"
    }

    private fun button(
        option: PresenceButtonOption,
        input: PresenceInput,
        title: String,
        labels: PresenceLabels,
    ): Pair<String, String>? {
        val (label, url) = when (option.kind) {
            PresencePreferences.BUTTON_CRUNCHYROLL ->
                labels.crunchyroll to
                    "https://www.crunchyroll.com/search?q=" + encode(input.englishTitle ?: title)
            PresencePreferences.BUTTON_ANILIST -> labels.aniList to input.aniListUrl
            PresencePreferences.BUTTON_MAL -> labels.mal to input.malUrl
            PresencePreferences.BUTTON_TMDB -> labels.tmdb to input.tmdbUrl
            PresencePreferences.BUTTON_SOURCE -> labels.source to input.sourceUrl
            PresencePreferences.BUTTON_CUSTOM ->
                option.label to option.url
                    .replace("{title}", encode(title))
                    .replace("{episode}", input.episode?.toString().orEmpty())
            else -> return null
        }
        val cleanLabel = label.trim().take(BUTTON_LABEL_MAX).trim()
        val cleanUrl = link(url, BUTTON_URL_MAX)
        if (cleanLabel.isEmpty() || cleanUrl.isEmpty()) return null
        return cleanLabel to cleanUrl
    }

    private fun frozenClock(position: Long, duration: Long): String =
        if (duration > 0) "⏸ ${clock(position)} / ${clock(duration)}" else "⏸ ${clock(position)}"

    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** One line of 2 to 128 characters, or empty. */
    fun text(value: String?): String {
        val clean = value.orEmpty().replace(Regex("""\s+"""), " ").trim()
        if (clean.length < 2) return ""
        if (clean.length <= TEXT_MAX) return clean
        var cut = TEXT_MAX - 1
        if (Character.isHighSurrogate(clean[cut - 1])) cut--
        return clean.substring(0, cut).trimEnd() + "…"
    }

    private fun image(url: String?): String = link(url, IMAGE_MAX)

    private fun link(url: String?, max: Int): String {
        val clean = url?.trim().orEmpty()
        val web = clean.startsWith("https://") || clean.startsWith("http://")
        return if (web && clean.length <= max && clean.none { it.isWhitespace() }) clean else ""
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
