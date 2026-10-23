package yomira.metadata

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.network.NetworkHelper
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** AniList data of one library entry: studio, staff, score and characters with their voices. */
@Serializable
data class AniListInfo(
    val id: Long,
    val genres: List<String> = emptyList(),
    /** 0..100. */
    val score: Int? = null,
    /** TV, TV_SHORT, MOVIE, OVA, ONA, SPECIAL, MUSIC. */
    val format: String? = null,
    /** FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS. */
    val status: String? = null,
    /** ORIGINAL, MANGA, LIGHT_NOVEL, VISUAL_NOVEL, VIDEO_GAME, NOVEL, WEB_NOVEL... */
    val source: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val country: String? = null,
    val studios: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val characters: List<AniCharacter> = emptyList(),
    val fetchedAt: Long = 0,
)

/** A character with their voice actors per language code (ja, pt, en...; see [AniListDetails.voiceLanguages]). */
@Serializable
data class AniCharacter(
    val name: String,
    val image: String? = null,
    /** MAIN, SUPPORTING or BACKGROUND. */
    val role: String? = null,
    val voices: Map<String, List<AniVoice>> = emptyMap(),
)

@Serializable
data class AniVoice(val name: String, val image: String? = null)

/**
 * Details from AniList for the details screen. Uses the entry's AniList id (from its trackers or a
 * title search, see [AniListIds]) and keeps the answer on the device for a week.
 */
object AniListDetails {

    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val TTL = 7 * 24 * 60 * 60 * 1000L
    private const val MISS_RETRY = 6 * 60 * 60 * 1000L

    /** Voice languages offered in the settings: code to AniList's language name. */
    val voiceLanguages = linkedMapOf(
        "ja" to "Japanese",
        "pt" to "Portuguese",
        "en" to "English",
        "es" to "Spanish",
        "it" to "Italian",
        "fr" to "French",
        "de" to "German",
        "ko" to "Korean",
    )

    /** "Portuguese (BR)" → "pt"; languages outside the list keep their lowercase name. */
    fun languageCode(languageV2: String?): String? {
        val name = languageV2?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return voiceLanguages.entries.firstOrNull { name.startsWith(it.value, ignoreCase = true) }?.key
            ?: name.lowercase(Locale.ROOT)
    }

    private val _infos = MutableStateFlow<Map<Long, AniListInfo>>(emptyMap())
    val infos: StateFlow<Map<Long, AniListInfo>> = _infos.asStateFlow()

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val misses = ConcurrentHashMap<Long, Long>()

    fun info(animeId: Long): AniListInfo? = _infos.value[animeId]

    /** Loads the entry's AniList data when it is missing or older than a week. */
    suspend fun ensure(animeId: Long, title: String) {
        withContext(Dispatchers.IO) {
            locks.getOrPut(animeId) { Mutex() }.withLock {
                val cached = info(animeId) ?: readDisk(animeId)?.also { publish(animeId, it) }
                val now = System.currentTimeMillis()
                if (cached != null && now - cached.fetchedAt < TTL) return@withLock
                if (cached == null && now - (misses[animeId] ?: 0L) < MISS_RETRY) return@withLock
                try {
                    val tracks = runCatching { Injekt.get<GetTracks>().await(animeId) }.getOrDefault(emptyList())
                    val media = AniListIds.forAnime(animeId, title, tracks, allowSearch = true)
                    val id = media?.id?.takeIf { it > 0 }
                    val fresh = id?.let { fetch(it) }
                    if (fresh == null) {
                        misses[animeId] = now
                    } else {
                        publish(animeId, fresh)
                        writeDisk(animeId, fresh)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "AniList: could not load details for anime $animeId" }
                }
            }
        }
    }

    /** The user changed the match: load again. */
    fun forget(animeId: Long) {
        _infos.update { it - animeId }
        misses.remove(animeId)
        runCatching { file(animeId).delete() }
    }

    private fun publish(animeId: Long, info: AniListInfo) {
        _infos.update { it + (animeId to info) }
    }

    private fun fetch(id: Long): AniListInfo? {
        val query = "query(\$id:Int){Media(id:\$id,type:ANIME){id genres averageScore format status source " +
            "episodes duration season seasonYear countryOfOrigin " +
            "startDate{year month day} endDate{year month day} " +
            "studios(isMain:true){nodes{name}} " +
            "staff(sort:[RELEVANCE,ID],perPage:25){edges{role node{name{full}}}} " +
            "characters(sort:[ROLE,RELEVANCE,ID],perPage:25){edges{role node{name{full} image{large}} " +
            "voiceActors(sort:[RELEVANCE,ID]){name{full} image{large} languageV2}}}}}"
        val media = post(query, id)?.obj("data")?.obj("Media") ?: return null
        val staff = media.obj("staff")?.array("edges").orEmpty().mapNotNull { edge ->
            val name = edge.obj("node")?.obj("name")?.string("full") ?: return@mapNotNull null
            (edge.string("role") ?: "") to name
        }
        return AniListInfo(
            id = id,
            genres = media.strings("genres"),
            score = media.int("averageScore"),
            format = media.string("format"),
            status = media.string("status"),
            source = media.string("source"),
            episodes = media.int("episodes"),
            duration = media.int("duration"),
            season = media.string("season"),
            seasonYear = media.int("seasonYear"),
            startDate = media.obj("startDate")?.date(),
            endDate = media.obj("endDate")?.date(),
            country = media.string("countryOfOrigin"),
            studios = media.obj("studios")?.array("nodes").orEmpty().mapNotNull { it.string("name") }.distinct(),
            creators = staff.filter { (role, _) -> role.startsWith("Original Creator") || role.startsWith("Original Story") }
                .map { it.second }.distinct(),
            directors = staff.filter { (role, _) -> role == "Director" || role.startsWith("Director (") }
                .map { it.second }.distinct(),
            characters = media.obj("characters")?.array("edges").orEmpty().mapNotNull { edge ->
                val node = edge.obj("node") ?: return@mapNotNull null
                val name = node.obj("name")?.string("full") ?: return@mapNotNull null
                AniCharacter(
                    name = name,
                    image = node.obj("image")?.string("large")?.takeUnless { it.endsWith("/default.jpg") },
                    role = edge.string("role"),
                    voices = edge.array("voiceActors").mapNotNull { actor ->
                        val actorName = actor.obj("name")?.string("full") ?: return@mapNotNull null
                        val code = languageCode(actor.string("languageV2")) ?: return@mapNotNull null
                        code to AniVoice(
                            actorName,
                            actor.obj("image")?.string("large")?.takeUnless { it.endsWith("/default.jpg") },
                        )
                    }.groupBy({ it.first }, { it.second }),
                )
            },
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun JsonObject.date(): String? {
        val year = int("year") ?: return null
        val month = int("month") ?: return year.toString()
        val day = int("day") ?: return String.format(Locale.ROOT, "%04d-%02d", year, month)
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
    }

    private fun post(query: String, id: Long): JsonObject? {
        val body = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { put("id", id) })
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).header("Accept", "application/json").build()
        return runCatching {
            Injekt.get<NetworkHelper>().client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                TmdbClient.json.parseToJsonElement(response.body.string()) as? JsonObject
            }
        }.getOrNull()
    }

    private fun file(animeId: Long): File =
        File(Injekt.get<Application>().filesDir, "yomira/anilist/$animeId.json")

    private fun readDisk(animeId: Long): AniListInfo? = runCatching {
        val file = file(animeId)
        if (!file.exists()) return null
        TmdbClient.json.decodeFromString(AniListInfo.serializer(), file.readText())
    }.getOrNull()

    private fun writeDisk(animeId: Long, info: AniListInfo) {
        runCatching {
            val file = file(animeId)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(TmdbClient.json.encodeToString(AniListInfo.serializer(), info))
            tmp.renameTo(file)
        }
    }
}

/** The cached AniList data of an anime, updated when it arrives. */
@Composable
fun rememberAniListInfo(animeId: Long?): AniListInfo? {
    val infos by AniListDetails.infos.collectAsState()
    return animeId?.let { infos[it] }
}
