package yomira.metadata

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

/** What Yomira knows about an anime from TMDB, cached on the device. */
@Serializable
data class ShowMeta(
    val animeId: Long,
    val tmdbId: Long,
    val isMovie: Boolean = false,
    val name: String = "",
    val overview: String = "",
    val logoPath: String? = null,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    /** Poster without text (TMDB "no language" poster), for the big cover with the logo on top. */
    val keyArtPath: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    /** TMDB season this library entry is (from its title or the manual match); null = absolute numbering. */
    val seasonHint: Int? = null,
    /** Added to the extension's episode numbers (manual match). */
    val offset: Int = 0,
    val episodes: Map<String, EpisodeMeta> = emptyMap(),
    val fetchedSeasons: Map<Int, Long> = emptyMap(),
    val fetchedAt: Long = 0,
    val language: String = "",
    val overrideKey: String = "",
    /** Show-level details for the details screen; null on data saved by older versions. */
    val details: ShowDetails? = null,
    val detailsVersion: Int = 0,
    /** Seasons the extension names ("T2E13", 2.013) with its first and last episode numbers. */
    val extensionSeasons: Map<Int, ExtensionSeason> = emptyMap(),
) {
    val logoUrl: String? get() = TmdbClient.image(logoPath, "w500")
    val backdropUrl: String? get() = TmdbClient.image(backdropPath, "w1280")
    val posterUrl: String? get() = TmdbClient.image(posterPath, "w500")
    val keyArtUrl: String? get() = TmdbClient.image(keyArtPath, "w780")
}

/** TMDB show (or movie) details: genres in the chosen language, dates, people and score. */
@Serializable
data class ShowDetails(
    val genres: List<String> = emptyList(),
    val firstAirDate: String? = null,
    val lastAirDate: String? = null,
    val runtimeMinutes: Int? = null,
    /** TMDB status: "Returning Series", "Ended", "Canceled", "In Production", "Released"... */
    val status: String? = null,
    val studios: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    /** ISO 3166 codes. */
    val countries: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val seasonOverviews: Map<Int, String> = emptyMap(),
    val cast: List<CastMember> = emptyList(),
)

/** An actor and the character they play (TMDB credits). */
@Serializable
data class CastMember(val name: String, val character: String? = null, val profilePath: String? = null) {
    val profileUrl: String? get() = TmdbClient.image(profilePath, "w185")
}

@Serializable
data class SeasonInfo(val number: Int, val episodeCount: Int, val airDate: String? = null)

/** A season as the extension numbers it: "T2E13" to "T2E29" is first 13, last 29. */
@Serializable
data class ExtensionSeason(val first: Int, val last: Int)

@Serializable
data class EpisodeMeta(
    val season: Int,
    val number: Int,
    val name: String = "",
    val overview: String = "",
    val still: String? = null,
    val runtime: Int? = null,
    val airDate: String? = null,
    val voteAverage: Double? = null,
) {
    fun stillUrl(size: String = "w300"): String? = TmdbClient.image(still, size)
}

/**
 * Episode names, descriptions, stills and title logos from TMDB, in the user's language. The data
 * from the extension is never changed in the database (downloads are named after it); screens
 * show these values on top of it.
 */
object EpisodeMetadata {

    private const val HOUR = 60 * 60 * 1000L
    private const val SHOW_TTL = 7 * 24 * HOUR
    private const val SEASON_TTL = 3 * 24 * HOUR
    private const val MISSING_RETRY = 12 * HOUR
    private const val NO_MATCH_RETRY = 6 * HOUR
    private const val MAX_SEASONS_PER_PASS = 30
    private const val MAX_CAST = 24
    private const val MIN_LIST_FOR_SEASONS = 3

    /** Raise when ShowMeta gains fields: older saved data is fetched again. */
    private const val DETAILS_VERSION = 1

    /** A season that starts this many days after the entry still counts as its season. */
    private const val DATE_SLACK_DAYS = 20

    /** Start dates this close mean the entry is that whole season. */
    private const val SAME_START_DAYS = 45

    private val movieWords = Regex("""(?i)\b(?:movie|filme|gekijouban|the movie|film)\b""")

    private val _shows = MutableStateFlow<Map<Long, ShowMeta>>(emptyMap())
    val shows: StateFlow<Map<Long, ShowMeta>> = _shows.asStateFlow()

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val noMatch = ConcurrentHashMap<Long, Long>()

    fun show(animeId: Long): ShowMeta? = _shows.value[animeId]

    /**
     * TMDB (season, episode) of an episode of this library entry.
     *
     * A known season (from the title, the air dates or the manual match) wins over a "T1E2" in the
     * episode name, because many extensions call every entry "T1". Without one, the name decides,
     * and then the running number is counted across the seasons.
     */
    fun locate(show: ShowMeta, episodeNumber: Double, episodeName: String): Pair<Int, Int>? {
        if (show.isMovie) return 1 to 1
        EpisodeNumbers.packed(episodeNumber)?.let { packed ->
            val (season, episode) = packedLocation(show, packed)
            return reconcile(show, season, episode, extensionSeason = packed.first)
        }
        val named = TitleMatch.seasonEpisodeIn(episodeName)
        val whole = episodeNumber.takeIf { it == floor(it) }?.toInt()
        show.seasonHint?.let { hint ->
            val number = whole ?: named?.second ?: return null
            val count = show.seasons.firstOrNull { it.number == hint }?.episodeCount ?: Int.MAX_VALUE
            val shifted = number + show.offset
            if (shifted in 1..count) return hint to shifted
            // The extension already counts from the start of this season.
            if (show.offset != 0 && number in 1..count) return hint to number
            // The extension keeps counting from the earlier seasons.
            val continued = absolute(show, number)
            return if (continued != null && continued.first >= hint) continued else hint to shifted
        }
        named?.let { (season, episode) ->
            return if (episode >= 1) reconcile(show, season, episode, extensionSeason = season) else null
        }
        return absolute(show, (whole ?: return null) + show.offset)
    }

    /**
     * Fits a season and episode the extension wrote into TMDB's seasons. Some extensions call a
     * continuation a new season: "T2E25" for TMDB's S2E1 (numbering that goes on from season 1),
     * or "T2E13" for TMDB's S1E13 when TMDB keeps both parts in one season. Without this those
     * episodes had no name or image.
     */
    private fun reconcile(show: ShowMeta, season: Int, episode: Int, extensionSeason: Int): Pair<Int, Int> {
        val regular = show.seasons.filter { it.number >= 1 }.sortedBy { it.number }
        if (regular.isEmpty() || season < 1) return season to episode
        val info = regular.firstOrNull { it.number == season }
        if (info != null && episode in 1..info.episodeCount) return season to episode
        val own = show.extensionSeasons[extensionSeason]
        val continues = own != null && own.first > 1
        // The number goes on from the earlier seasons: S2 "E25" is S2E1 after a 24-episode S1.
        if (info != null && (own == null || continues)) {
            val before = regular.filter { it.number < season }.sumOf { it.episodeCount }
            val inSeason = episode - before
            if (inSeason in 1..info.episodeCount) return season to inSeason
        }
        if (continues) absolute(show, episode)?.let { return it }
        // A season TMDB does not have, numbered from 1: count the extension's earlier seasons.
        if (info == null && own != null) {
            val earlier = (1 until extensionSeason).map { show.extensionSeasons[it] ?: return season to episode }
            val before = earlier.sumOf { it.last - it.first + 1 }
            absolute(show, before + episode)?.let { return it }
        }
        return season to episode
    }

    /** The seasons the extension names in these episodes, with their first and last numbers. */
    internal fun extensionSeasons(episodes: List<Pair<Double, String>>): Map<Int, ExtensionSeason> =
        episodes
            .mapNotNull { (number, name) -> EpisodeNumbers.packed(number) ?: TitleMatch.seasonEpisodeIn(name) }
            .filter { (season, episode) -> season >= 1 && episode >= 1 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, numbers) -> ExtensionSeason(numbers.min(), numbers.max()) }

    private fun mergeSeasons(
        old: Map<Int, ExtensionSeason>,
        new: Map<Int, ExtensionSeason>,
    ): Map<Int, ExtensionSeason> = (old.keys + new.keys).associateWith { season ->
        val a = old[season]
        val b = new[season]
        when {
            a == null -> b!!
            b == null -> a
            else -> ExtensionSeason(minOf(a.first, b.first), maxOf(a.last, b.last))
        }
    }

    /**
     * Season packed in the number (2.004). It counts from this entry's first season: an entry that
     * is TMDB's season 4 calls it 1. When it already equals the known season it is kept.
     */
    private fun packedLocation(show: ShowMeta, packed: Pair<Int, Int>): Pair<Int, Int> {
        val (season, episode) = packed
        val hint = show.seasonHint ?: return packed
        if (season == hint) return packed
        val known = show.seasons.map { it.number }.toSet()
        val shifted = hint + season - 1
        return when {
            known.isEmpty() || shifted in known -> shifted to episode
            else -> packed
        }
    }

    /** Running number counted across the regular seasons. */
    private fun absolute(show: ShowMeta, number: Int): Pair<Int, Int>? {
        if (number < 1) return null
        val regular = show.seasons.filter { it.number >= 1 }.sortedBy { it.number }
        if (regular.isEmpty()) return 1 to number
        var remaining = number
        for (season in regular) {
            if (remaining <= season.episodeCount) return season.number to remaining
            remaining -= season.episodeCount
        }
        return null
    }

    fun episode(show: ShowMeta?, episodeNumber: Double, episodeName: String): EpisodeMeta? {
        show ?: return null
        val (season, number) = locate(show, episodeNumber, episodeName) ?: return null
        return show.episodes["$season:$number"]
    }

    /** "T1 E2" for the player; null when the season is unknown. */
    fun seasonEpisodeLabel(show: ShowMeta?, episodeNumber: Double, episodeName: String): String? {
        val located = show?.takeUnless { it.isMovie }?.let { locate(it, episodeNumber, episodeName) }
            ?: TitleMatch.seasonEpisodeIn(episodeName)
            ?: return null
        return "T${located.first} E${located.second}"
    }

    /** Drops everything in memory (the settings also delete the files). */
    fun forgetAll() {
        _shows.value = emptyMap()
        noMatch.clear()
    }

    /** Loads an anime's saved data from the device without going online (for long lists). */
    suspend fun peek(animeId: Long) {
        if (show(animeId) != null) return
        withContext(Dispatchers.IO) { readDisk(animeId) }?.let(::publish)
    }

    /** The user picked another TMDB match: allow a new search right away (the data reloads itself). */
    fun matchChanged(animeId: Long) {
        noMatch.remove(animeId)
    }

    /** Drops what is known about an anime. */
    fun forget(animeId: Long) {
        _shows.update { it - animeId }
        noMatch.remove(animeId)
        runCatching { file(animeId).delete() }
    }

    /**
     * Makes sure the show and the seasons of [episodes] (number, name from the extension) are
     * loaded. Cheap when everything is cached; safe to call from every screen that shows them.
     */
    suspend fun ensure(animeId: Long, title: String, episodes: List<Pair<Double, String>>) {
        withContext(Dispatchers.IO) {
            val prefs = MetadataPreferences()
            if (!prefs.tmdbEnabled().get()) return@withContext
            val key = MetadataKeys.tmdb(prefs)
            if (key.isBlank()) return@withContext
            locks.getOrPut(animeId) { Mutex() }.withLock {
                try {
                    load(animeId, title, episodes, prefs, TmdbClient(key))
                } catch (e: TmdbAuthException) {
                    logcat(LogPriority.WARN) { "TMDB: the API key was rejected" }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "TMDB: could not load metadata for anime $animeId" }
                }
            }
        }
    }

    private suspend fun load(
        animeId: Long,
        title: String,
        episodes: List<Pair<Double, String>>,
        prefs: MetadataPreferences,
        client: TmdbClient,
    ) {
        val now = System.currentTimeMillis()
        val language = prefs.language().get()
        val overrideRaw = prefs.tmdbOverride(animeId).get()
        val cached = show(animeId) ?: readDisk(animeId)?.also(::publish)

        var show: ShowMeta? = cached
        val stale = cached == null ||
            cached.language != language ||
            cached.overrideKey != overrideRaw ||
            cached.detailsVersion < DETAILS_VERSION ||
            now - cached.fetchedAt > SHOW_TTL
        if (stale) {
            val recentlyMissed = now - (noMatch[animeId] ?: 0L) < NO_MATCH_RETRY
            if (cached == null && recentlyMissed) return
            val fresh = fetchShow(client, animeId, title, overrideRaw, language, prefs)
            if (fresh == null) {
                noMatch[animeId] = now
            } else {
                show = if (cached != null &&
                    cached.tmdbId == fresh.tmdbId &&
                    cached.isMovie == fresh.isMovie &&
                    cached.language == language &&
                    cached.detailsVersion == DETAILS_VERSION
                ) {
                    fresh.copy(
                        episodes = cached.episodes,
                        fetchedSeasons = cached.fetchedSeasons,
                        extensionSeasons = cached.extensionSeasons,
                    )
                } else {
                    fresh
                }
                publish(show)
                writeDisk(show)
            }
        }
        var current = show ?: return
        if (current.isMovie) return

        // Only whole lists say where a season starts (one episode from the player could mislead).
        val named = if (episodes.size >= MIN_LIST_FOR_SEASONS) {
            mergeSeasons(current.extensionSeasons, extensionSeasons(episodes))
        } else {
            current.extensionSeasons
        }
        if (named != current.extensionSeasons) {
            current = current.copy(extensionSeasons = named)
            publish(current)
            writeDisk(current)
        }

        val wanted = episodes.mapNotNull { (number, name) -> locate(current, number, name) }
        val seasons = wanted.map { it.first }.distinct().filter { season ->
            val fetched = current.fetchedSeasons[season] ?: 0L
            val missing = wanted.any { it.first == season && current.episodes["${it.first}:${it.second}"] == null }
            now - fetched > SEASON_TTL || (missing && now - fetched > MISSING_RETRY)
        }.take(MAX_SEASONS_PER_PASS)
        if (seasons.isEmpty()) return

        var updated = current
        for (season in seasons) {
            val list = fetchSeason(client, current.tmdbId, season, language, prefs) ?: continue
            updated = updated.copy(
                episodes = updated.episodes + list.associateBy { "${it.season}:${it.number}" },
                fetchedSeasons = updated.fetchedSeasons + (season to now),
            )
        }
        publish(updated)
        writeDisk(updated)
    }

    private suspend fun fetchShow(
        client: TmdbClient,
        animeId: Long,
        title: String,
        overrideRaw: String,
        language: String,
        prefs: MetadataPreferences,
    ): ShowMeta? {
        val override = TmdbOverride.decode(overrideRaw)
        val (query, titleSeason) = TitleMatch.forShowSearch(title)
        val tracks = runCatching { Injekt.get<GetTracks>().await(animeId) }.getOrDefault(emptyList())
        val aniList = suspend { AniListIds.forAnime(animeId, title, tracks, allowSearch = true) }

        val (tmdbId, isMovie) = if (override != null) {
            override.tmdbId to override.isMovie
        } else {
            TitleMatch.searchVariants(query).firstNotNullOfOrNull { findMatch(client, it, title, language) }
                ?: aniList()?.let { media ->
                    listOfNotNull(media.english, media.romaji)
                        .flatMap { TitleMatch.searchVariants(TitleMatch.forShowSearch(it).first) }
                        .distinct()
                        .firstNotNullOfOrNull { findMatch(client, it, it, language) }
                }
                ?: return null
        }

        if (isMovie) {
            val movie = client.movie(tmdbId, language) ?: return null
            var overview = movie.string("overview").orEmpty()
            if (overview.isBlank() && prefs.englishFallback().get() && !language.startsWith("en")) {
                overview = client.movie(tmdbId, "en-US")?.string("overview").orEmpty()
            }
            val name = movie.string("title").orEmpty()
            return ShowMeta(
                animeId = animeId,
                tmdbId = tmdbId,
                isMovie = true,
                name = name,
                overview = overview,
                logoPath = pickLogo(movie),
                backdropPath = movie.string("backdrop_path"),
                posterPath = movie.string("poster_path"),
                keyArtPath = pickKeyArt(movie),
                episodes = mapOf(
                    "1:1" to EpisodeMeta(
                        season = 1,
                        number = 1,
                        name = name,
                        overview = overview,
                        still = movie.string("backdrop_path"),
                        runtime = movie.int("runtime"),
                        airDate = movie.string("release_date"),
                        voteAverage = movie.double("vote_average"),
                    ),
                ),
                fetchedAt = System.currentTimeMillis(),
                language = language,
                overrideKey = overrideRaw,
                details = ShowDetails(
                    genres = movie.array("genres").mapNotNull { it.string("name") },
                    firstAirDate = movie.string("release_date"),
                    runtimeMinutes = movie.int("runtime"),
                    status = movie.string("status"),
                    studios = movie.array("production_companies").mapNotNull { it.string("name") },
                    countries = movie.array("production_countries").mapNotNull { it.string("iso_3166_1") },
                    voteAverage = movie.double("vote_average"),
                    voteCount = movie.int("vote_count"),
                    cast = castOf(movie.obj("credits")),
                ),
                detailsVersion = DETAILS_VERSION,
            )
        }

        val tv = client.tv(tmdbId, language) ?: return null
        val seasons = tv.array("seasons").mapNotNull { season ->
            val number = season.int("season_number") ?: return@mapNotNull null
            val count = season.int("episode_count") ?: 0
            SeasonInfo(number, count, season.string("air_date")).takeIf { count > 0 }
        }
        var offset = override?.offset ?: 0
        val hint = override?.season?.takeIf { it >= 0 }
            ?: titleSeason?.takeIf { season -> seasons.any { it.number == season } }
            ?: if (seasons.count { it.number >= 1 } > 1) {
                aniList()?.startDate?.let { start -> seasonFromDate(client, tmdbId, seasons, start, language) }
                    ?.let { (season, before) ->
                        if (offset == 0) offset = before
                        season
                    }
            } else {
                null
            }
        return ShowMeta(
            animeId = animeId,
            tmdbId = tmdbId,
            isMovie = false,
            name = tv.string("name").orEmpty(),
            overview = tv.string("overview").orEmpty(),
            logoPath = pickLogo(tv),
            backdropPath = tv.string("backdrop_path"),
            posterPath = tv.string("poster_path"),
            keyArtPath = pickKeyArt(tv),
            seasons = seasons,
            seasonHint = hint,
            offset = offset,
            fetchedAt = System.currentTimeMillis(),
            language = language,
            overrideKey = overrideRaw,
            details = ShowDetails(
                genres = tv.array("genres").mapNotNull { it.string("name") },
                firstAirDate = tv.string("first_air_date"),
                lastAirDate = tv.string("last_air_date"),
                runtimeMinutes = tv.ints("episode_run_time").firstOrNull { it > 0 }
                    ?: tv.obj("last_episode_to_air")?.int("runtime"),
                status = tv.string("status"),
                studios = tv.array("production_companies").mapNotNull { it.string("name") },
                networks = tv.array("networks").mapNotNull { it.string("name") },
                countries = tv.strings("origin_country"),
                creators = tv.array("created_by").mapNotNull { it.string("name") },
                voteAverage = tv.double("vote_average"),
                voteCount = tv.int("vote_count"),
                seasonOverviews = tv.array("seasons").mapNotNull { season ->
                    val number = season.int("season_number") ?: return@mapNotNull null
                    season.string("overview")?.let { number to it }
                }.toMap(),
                cast = castOf(tv.obj("aggregate_credits")),
            ),
            detailsVersion = DETAILS_VERSION,
        )
    }

    /** Main cast from TMDB credits: plain ("cast[].character") or aggregated ("cast[].roles[]"). */
    private fun castOf(credits: JsonObject?): List<CastMember> =
        credits?.array("cast").orEmpty().take(MAX_CAST).mapNotNull { person ->
            val name = person.string("name") ?: return@mapNotNull null
            val character = person.string("character")
                ?: person.array("roles").firstNotNullOfOrNull { it.string("character") }
            CastMember(name, character, person.string("profile_path"))
        }

    /**
     * TMDB keeps an anime's seasons under one show while extensions list each season apart. When the
     * title names no season, the entry's AniList start date picks the TMDB season that aired then
     * (season, episodes before it). For a second cour inside one TMDB season, the episodes that
     * aired before it become the offset. Null means counting from the first season.
     */
    private fun seasonFromDate(
        client: TmdbClient,
        tmdbId: Long,
        seasons: List<SeasonInfo>,
        start: String,
        language: String,
    ): Pair<Int, Int>? {
        val startDay = epochDay(start) ?: return null
        val regular = seasons.filter { it.number >= 1 }.sortedBy { it.number }
        val first = regular.firstOrNull()?.number ?: return null
        val (season, airedOn) = regular
            .mapNotNull { season -> epochDay(season.airDate)?.let { season to it } }
            .lastOrNull { it.second <= startDay + DATE_SLACK_DAYS }
            ?: return null
        if (abs(startDay - airedOn) <= SAME_START_DAYS) {
            return if (season.number == first) null else season.number to 0
        }
        val episodes = client.season(tmdbId, season.number, language)?.array("episodes").orEmpty()
        val before = episodes.count { episode ->
            epochDay(episode.string("air_date"))?.let { it < startDay - 3 } == true
        }
        return when {
            before in 1 until season.episodeCount -> season.number to before
            season.number == first -> null
            else -> season.number to 0
        }
    }

    private fun epochDay(date: String?): Long? =
        date?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() }

    /** Best TMDB match for a title: (id, isMovie). Searches in English and in the user's language. */
    private fun findMatch(client: TmdbClient, query: String, fullTitle: String, language: String): Pair<Long, Boolean>? {
        if (query.isBlank()) return null
        val tv = best(query, client.searchTv(query, "en-US"), client.searchTv(query, language), movie = false)
        val looksLikeMovie = movieWords.containsMatchIn(fullTitle)
        val movie = if (tv == null || looksLikeMovie) {
            best(query, client.searchMovie(query, "en-US"), client.searchMovie(query, language), movie = true)
        } else {
            null
        }
        return when {
            movie != null && (tv == null || movie.second >= tv.second - 0.05) -> movie.first to true
            tv != null -> tv.first to false
            else -> null
        }
    }

    private fun best(query: String, english: List<JsonObject>, local: List<JsonObject>, movie: Boolean): Pair<Long, Double>? {
        val nameKey = if (movie) "title" else "name"
        val originalKey = if (movie) "original_title" else "original_name"
        val localById = local.associateBy { it.long("id") }
        val ranked = english.ifEmpty { local }
        if (ranked.isEmpty()) return null
        return ranked.mapIndexedNotNull { index, item ->
            val id = item.long("id") ?: return@mapIndexedNotNull null
            val names = listOfNotNull(
                item.string(nameKey),
                item.string(originalKey),
                localById[id]?.string(nameKey),
            )
            var score = names.maxOfOrNull { TitleMatch.similarity(query, it) } ?: 0.0
            if (16L in item.longs("genre_ids")) score += 0.12
            if (item.string("original_language") == "ja") score += 0.08
            score += 0.06 * (ranked.size - index) / ranked.size
            id to score
        }.maxByOrNull { it.second }?.takeIf { it.second >= 0.62 }
    }

    /** Best-rated poster without text, if TMDB has one. */
    private fun pickKeyArt(details: JsonObject): String? =
        details.obj("images")?.array("posters").orEmpty()
            .filter { it.string("iso_639_1") == null }
            .maxByOrNull { it.double("vote_average") ?: 0.0 }
            ?.string("file_path")

    private fun pickLogo(details: JsonObject): String? {
        val logos = details.obj("images")?.array("logos").orEmpty()
            .filter { it.string("file_path")?.endsWith(".png", ignoreCase = true) == true }
        if (logos.isEmpty()) return null
        val byLanguage = listOf("pt", "en", null)
        for (language in byLanguage) {
            logos.filter { it.string("iso_639_1") == language }
                .maxByOrNull { it.double("vote_average") ?: 0.0 }
                ?.let { return it.string("file_path") }
        }
        return logos.maxByOrNull { it.double("vote_average") ?: 0.0 }?.string("file_path")
    }

    private fun fetchSeason(
        client: TmdbClient,
        tmdbId: Long,
        season: Int,
        language: String,
        prefs: MetadataPreferences,
    ): List<EpisodeMeta>? {
        val local = client.season(tmdbId, season, language)?.array("episodes")?.mapNotNull { it.toEpisode(season) }
            ?: return null
        val needsFallback = prefs.englishFallback().get() &&
            !language.startsWith("en") &&
            local.any { TitleMatch.isGenericName(it.name) || it.overview.isBlank() }
        if (!needsFallback) return local
        val english = client.season(tmdbId, season, "en-US")?.array("episodes")
            ?.mapNotNull { it.toEpisode(season) }
            ?.associateBy { it.number }
            .orEmpty()
        return local.map { episode ->
            val fallback = english[episode.number] ?: return@map episode
            episode.copy(
                name = if (TitleMatch.isGenericName(episode.name) && !TitleMatch.isGenericName(fallback.name)) {
                    fallback.name
                } else {
                    episode.name
                },
                overview = episode.overview.ifBlank { fallback.overview },
            )
        }
    }

    private fun JsonObject.toEpisode(season: Int): EpisodeMeta? {
        val number = int("episode_number") ?: return null
        return EpisodeMeta(
            season = int("season_number") ?: season,
            number = number,
            name = string("name").orEmpty(),
            overview = string("overview").orEmpty(),
            still = string("still_path"),
            runtime = int("runtime"),
            airDate = string("air_date"),
            voteAverage = double("vote_average")?.takeIf { (int("vote_count") ?: 0) > 0 },
        )
    }

    private fun publish(show: ShowMeta) {
        _shows.update { it + (show.animeId to show) }
    }

    private fun file(animeId: Long): File =
        File(Injekt.get<Application>().filesDir, "yomira/meta/$animeId.json")

    private fun readDisk(animeId: Long): ShowMeta? = runCatching {
        val file = file(animeId)
        if (!file.exists()) return null
        TmdbClient.json.decodeFromString(ShowMeta.serializer(), file.readText())
    }.getOrNull()

    private fun writeDisk(show: ShowMeta) {
        runCatching {
            val file = file(show.animeId)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(TmdbClient.json.encodeToString(ShowMeta.serializer(), show))
            tmp.renameTo(file)
        }
    }
}

/** The cached TMDB data of an anime, updated when it arrives. */
@Composable
fun rememberShowMeta(animeId: Long?): ShowMeta? {
    val shows by EpisodeMetadata.shows.collectAsState()
    return animeId?.let { shows[it] }
}
