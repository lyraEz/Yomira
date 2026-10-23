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
import yomira.i18n.YMR
import yomira.metadata.AniListIds
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
 * tracked, from an AniList search by title.
 */
object SkipTimes {

    private const val ANISKIP = "https://api.aniskip.com/v2/skip-times"
    private const val ANIME_SKIP = "https://api.anime-skip.com/graphql"

    /** Longest believable segment; anything longer is a bad submission. */
    private const val MAX_SEGMENT = 6 * 60.0

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
        // A later season packed in the number (2.004) belongs to another AniList entry; its
        // times would be the first season's, so none are better.
        val packed = EpisodeNumbers.packed(episodeNumber)
        if (packed != null && packed.first > 1) return@withContext null
        // "T2E5" in the name wins over the running number for anime split into seasons.
        val number = TitleMatch.seasonEpisodeIn(episodeName)?.second
            ?: packed?.second
            ?: episodeNumber.takeIf { it == floor(it) && it >= 1 }?.toInt()
            ?: return@withContext null
        val numbers = listOfNotNull(number, inSeason(animeId, number, episodeNumber, episodeName, media.episodes))
        val duration = durationSeconds ?: 0

        val malId = media.idMal?.takeIf { it > 0 }
        if (malId != null) {
            numbers.firstNotNullOfOrNull { aniSkip(malId, it, duration).takeIf { times -> times.isNotEmpty() } }
                ?.let { return@withContext it }
        }

        val clientId = MetadataKeys.animeSkip(prefs)
        if (clientId.isBlank() || media.id <= 0) return@withContext null
        val season = TitleMatch.forShowSearch(animeTitle).second
        numbers.firstNotNullOfOrNull {
            animeSkip(clientId, media.id, it, duration, season).takeIf { times -> times.isNotEmpty() }
        }
    }

    /**
     * When the extension keeps counting from the earlier seasons (the second season starting at
     * episode 13) and the number is past this AniList entry's last episode, the episode's number
     * inside its TMDB season.
     */
    private fun inSeason(animeId: Long, number: Int, episodeNumber: Double, episodeName: String, entryEpisodes: Int?): Int? {
        if (entryEpisodes == null || number <= entryEpisodes) return null
        val show = EpisodeMetadata.show(animeId) ?: return null
        val located = EpisodeMetadata.locate(show, episodeNumber, episodeName)?.second ?: return null
        return located.takeIf { it in 1..entryEpisodes && it != number }
    }

    private fun label(type: ChapterType, preview: Boolean = false): String {
        val context = Injekt.get<Application>()
        return when {
            preview -> context.stringResource(YMR.strings.segment_preview)
            type == ChapterType.Opening || type == ChapterType.MixedOp -> context.stringResource(YMR.strings.segment_opening)
            type == ChapterType.Ending -> context.stringResource(YMR.strings.segment_ending)
            type == ChapterType.Recap -> context.stringResource(YMR.strings.segment_recap)
            else -> ""
        }
    }

    private fun aniSkip(malId: Long, episode: Int, duration: Int): List<TimeStamp> {
        val url = "$ANISKIP/$malId/$episode?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed" +
            "&types[]=recap&episodeLength=$duration"
        val root = request(Request.Builder().url(url).build()) ?: return emptyList()
        if (root["found"]?.toString() != "true") return emptyList()
        return root.array("results").mapNotNull { result ->
            val interval = result.obj("interval") ?: return@mapNotNull null
            val start = interval.double("startTime") ?: return@mapNotNull null
            val end = interval.double("endTime") ?: return@mapNotNull null
            val type = when (result.string("skipType")) {
                "op" -> ChapterType.Opening
                "mixed-op" -> ChapterType.MixedOp
                "ed", "mixed-ed" -> ChapterType.Ending
                "recap" -> ChapterType.Recap
                else -> return@mapNotNull null
            }
            TimeStamp(start, end, label(type), type).takeIf { end > start && end - start <= MAX_SEGMENT }
        }.sortedBy { it.start }
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
