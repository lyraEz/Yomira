package yomira.player

import android.app.Application
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.details.EpisodeSeasons
import yomira.i18n.YMR
import yomira.metadata.AniListIds
import yomira.metadata.AniListMedia
import yomira.metadata.EpisodeMetadata
import yomira.metadata.EpisodeNumbers
import yomira.metadata.MetadataKeys
import yomira.metadata.MetadataPreferences
import yomira.metadata.TitleMatch
import yomira.metadata.array
import yomira.metadata.double
import yomira.metadata.obj
import yomira.metadata.string
import kotlin.math.abs
import kotlin.math.floor

/**
 * Opening, ending, recap and preview times for an episode, turned into skippable chapters.
 *
 * Sources, in order: AniSkip (needs the MyAnimeList id) and Anime Skip (anime-skip.com, needs the
 * AniList id and an API client id). The ids come from the anime's trackers or, when it is not
 * tracked, from an AniList search by title; later seasons are reached through AniList's sequels.
 */
object SkipTimes {

    private const val ANISKIP = "https://api.aniskip.com/v2/skip-times"
    private const val ANIME_SKIP = "https://api.anime-skip.com/graphql"

    /** Longest believable segment; anything longer is a bad submission. */
    private const val MAX_SEGMENT = 6 * 60.0

    /** Videos this close in length are the same cut. */
    private const val LENGTH_SLACK = 2.0

    suspend fun fetch(
        animeId: Long,
        animeTitle: String,
        episodeNumber: Double,
        episodeName: String,
        durationSeconds: Int?,
        tracks: List<Track>,
    ): List<TimeStamp>? = withContext(Dispatchers.IO) {
        val prefs = MetadataPreferences()
        val media = AniListIds.forAnime(
            animeId = animeId,
            title = animeTitle,
            tracks = tracks,
            allowSearch = prefs.skipWithoutTracking().get(),
        ) ?: return@withContext null
        val targets = targets(animeId, media, episodeNumber, episodeName)
        if (targets.isEmpty()) return@withContext null
        val duration = durationSeconds ?: 0

        targets.firstNotNullOfOrNull { target ->
            target.malId?.let { aniSkip(it, target.episode, duration) }?.takeIf { it.isNotEmpty() }
        }?.let { return@withContext it }

        val clientId = MetadataKeys.animeSkip(prefs)
        if (clientId.isBlank()) return@withContext null
        val season = TitleMatch.forShowSearch(animeTitle).second
        targets.firstNotNullOfOrNull { target ->
            target.aniListId.takeIf { it > 0 }
                ?.let { animeSkip(clientId, it, target.episode, duration, season) }
                ?.takeIf { it.isNotEmpty() }
        }
    }

    /** An AniList entry (one season) and the episode's number inside it. */
    private data class Target(val aniListId: Long, val malId: Long?, val episode: Int)

    /**
     * Which season entry an episode belongs to. AniList and MyAnimeList keep one entry per season,
     * while many extensions put every season in one entry: "T2E25", 2.025 or a running "25" must
     * use the second season's times, not the first's.
     */
    private fun targets(animeId: Long, media: AniListMedia, episodeNumber: Double, episodeName: String): List<Target> {
        val declared = EpisodeNumbers.packed(episodeNumber)
            ?: TitleMatch.seasonEpisodeIn(episodeName)
            ?: EpisodeSeasons.seasonEpisodeIn(episodeName)
        val whole = episodeNumber.takeIf { it == floor(it) && it >= 1 }?.toInt()
        val base = AniListIds.entry(media.id)
        if (base == null) {
            // Only the MyAnimeList id is known (a tracker, offline): no way to reach other seasons.
            if (declared != null && declared.first > 1) return emptyList()
            val number = declared?.second ?: whole ?: return emptyList()
            return listOf(Target(media.id, media.idMal, number))
        }

        val found = mutableListOf<Pair<AniListIds.Entry, Int>>()
        if (declared != null && declared.first > 1) {
            val root = AniListIds.root(base)
            // TMDB knows how many episodes each season has: count from the very first one.
            val show = EpisodeMetadata.show(animeId)?.takeUnless { it.isMovie }
            val located = show?.let { EpisodeMetadata.locate(it, episodeNumber, episodeName) }
            if (show != null && located != null && show.seasons.any { it.number >= 1 }) {
                val before = show.seasons.filter { it.number in 1 until located.first }.sumOf { it.episodeCount }
                AniListIds.walk(root, before + located.second)?.let(found::add)
            }
            if (found.isEmpty()) {
                val entry = AniListIds.hop(root, declared.first - 1)
                val count = entry?.episodes
                when {
                    entry == null -> Unit
                    count == null || declared.second <= count -> found += entry to declared.second
                    // The number goes on from the first season ("T2E25").
                    else -> AniListIds.walk(root, declared.second)?.let(found::add)
                }
            }
        } else {
            val number = declared?.second ?: whole ?: return emptyList()
            found += AniListIds.walk(base, number) ?: (base to number)
        }
        return found.distinct().map { (entry, episode) -> Target(entry.id, entry.idMal, episode) }
    }

    private val skipNames = Regex("""(?i)^\s*(?:op|opening|intro|abertura|ed|ending|outro|credits|encerramento|recap|resumo|preview|pr[eé]via)\b""")

    /** A chapter the file names after an opening, ending, recap or preview. */
    fun isSkipName(title: String): Boolean = skipNames.containsMatchIn(title)

    /** A mixed opening or ending has story scenes over it: it is offered, never skipped alone. */
    fun isMixed(type: ChapterType, title: String): Boolean =
        type == ChapterType.MixedOp || title == label(ChapterType.Ending, mixed = true)

    private fun label(type: ChapterType, preview: Boolean = false, mixed: Boolean = false): String {
        val context = Injekt.get<Application>()
        return when {
            preview -> context.stringResource(YMR.strings.segment_preview)
            type == ChapterType.MixedOp -> context.stringResource(YMR.strings.segment_opening_mixed)
            type == ChapterType.Opening -> context.stringResource(YMR.strings.segment_opening)
            type == ChapterType.Ending && mixed -> context.stringResource(YMR.strings.segment_ending_mixed)
            type == ChapterType.Ending -> context.stringResource(YMR.strings.segment_ending)
            type == ChapterType.Recap -> context.stringResource(YMR.strings.segment_recap)
            else -> ""
        }
    }

    /**
     * AniSkip times, first among the ones sent for a video of this length, then among all of them
     * (extensions often serve another cut than the one most people timed).
     */
    private fun aniSkip(malId: Long, episode: Int, duration: Int): List<TimeStamp> {
        val results = (if (duration > 0) aniSkipResults(malId, episode, duration) else null)
            ?: aniSkipResults(malId, episode, 0)
            ?: return emptyList()
        return pickAniSkip(results, duration.toDouble()).mapNotNull { result ->
            val (type, mixed) = when (result.kind) {
                "op" -> ChapterType.Opening to false
                "mixed-op" -> ChapterType.MixedOp to true
                "ed" -> ChapterType.Ending to false
                "mixed-ed" -> ChapterType.Ending to true
                "recap" -> ChapterType.Recap to false
                else -> return@mapNotNull null
            }
            TimeStamp(result.start, result.end, label(type, mixed = mixed), type)
        }.sortedBy { it.start }
    }

    private fun aniSkipResults(malId: Long, episode: Int, length: Int): List<AniSkipResult>? {
        val url = "$ANISKIP/$malId/$episode?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed" +
            "&types[]=recap&episodeLength=$length"
        val root = request(Request.Builder().url(url).build()) ?: return null
        if (root["found"]?.toString() != "true") return null
        return root.array("results").mapNotNull { result ->
            val interval = result.obj("interval") ?: return@mapNotNull null
            val start = interval.double("startTime") ?: return@mapNotNull null
            val end = interval.double("endTime") ?: return@mapNotNull null
            val kind = result.string("skipType") ?: return@mapNotNull null
            AniSkipResult(kind, start, end, result.double("episodeLength") ?: 0.0)
        }.takeIf { it.isNotEmpty() }
    }

    internal data class AniSkipResult(val kind: String, val start: Double, val end: Double, val episodeLength: Double)

    /**
     * One result per kind: the one sent for a video of the closest length. When this video is
     * longer or shorter, endings are moved with the end of the video (an extra logo at the start or
     * a cut preview changes where they fall); openings and recaps stay where they are.
     */
    internal fun pickAniSkip(results: List<AniSkipResult>, duration: Double): List<AniSkipResult> =
        results
            .filter { it.end > it.start && it.end - it.start <= MAX_SEGMENT }
            .groupBy { it.kind }
            .mapNotNull { (_, sameKind) ->
                val best = sameKind.minBy { if (duration > 0 && it.episodeLength > 0) abs(it.episodeLength - duration) else 0.0 }
                val shift = if (duration > 0 && best.episodeLength > 0) duration - best.episodeLength else 0.0
                val moved = if (abs(shift) > LENGTH_SLACK && best.kind.endsWith("ed")) {
                    best.copy(start = best.start + shift, end = best.end + shift)
                } else {
                    best
                }
                moved.takeIf { it.start >= 0 && (duration <= 0 || it.start < duration) }
                    ?.let { if (duration > 0 && it.end > duration) it.copy(end = duration) else it }
            }

    private fun animeSkip(clientId: String, anilistId: Long, episode: Int, duration: Int, season: Int?): List<TimeStamp> {
        val query = "query(\$id: String!){ findShowsByExternalId(service: ANILIST, serviceId: \$id) " +
            "{ episodes { number absoluteNumber season baseDuration timestamps { at type { name } } } } }"
        val body = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { put("id", anilistId.toString()) })
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ANIME_SKIP)
            .post(body)
            .header("X-Client-ID", clientId)
            .build()
        val root = request(request) ?: return emptyList()
        val episodes = root.obj("data")?.array("findShowsByExternalId").orEmpty()
            .flatMap { it.array("episodes") }
        val byNumber = episodes.filter { it.string("number")?.toDoubleOrNull()?.toInt() == episode }
        val candidates = byNumber.ifEmpty {
            episodes.filter { it.string("absoluteNumber")?.toDoubleOrNull()?.toInt() == episode }
        }
        val picked = candidates
            .sortedWith(
                compareBy<JsonObject> { if (season != null && it.string("season") == season.toString()) 0 else 1 }
                    .thenBy { candidate ->
                        val base = candidate.double("baseDuration") ?: 0.0
                        if (duration > 0 && base > 0) abs(base - duration) else 0.0
                    },
            )
            .firstOrNull() ?: return emptyList()

        val end = (picked.double("baseDuration") ?: 0.0).takeIf { it > 0 } ?: duration.toDouble()
        val stamps = picked.array("timestamps")
            .mapNotNull { stamp -> stamp.double("at")?.let { it to stamp.obj("type")?.string("name").orEmpty() } }
            .sortedBy { it.first }
        return stamps.mapIndexedNotNull { index, (at, name) ->
            val until = stamps.getOrNull(index + 1)?.first ?: end
            val (type, preview) = when (name.lowercase()) {
                "intro", "new intro" -> ChapterType.Opening to false
                "mixed intro" -> ChapterType.MixedOp to false
                "credits", "new credits", "mixed credits" -> ChapterType.Ending to false
                "recap" -> ChapterType.Recap to false
                "preview" -> ChapterType.Ending to true
                else -> return@mapIndexedNotNull null
            }
            TimeStamp(at, until, label(type, preview), type)
                .takeIf { until > at + 1 && until - at <= MAX_SEGMENT }
        }
    }

    private fun request(request: Request): JsonObject? = runCatching {
        Injekt.get<NetworkHelper>().client.newCall(request).execute().use { response ->
            // AniSkip answers 404 with a JSON body when it has no times; read it anyway.
            yomira.metadata.TmdbClient.json.parseToJsonElement(response.body.string()) as? JsonObject
        }
    }.onFailure { logcat(LogPriority.WARN, it) { "Skip times request failed" } }.getOrNull()
}
