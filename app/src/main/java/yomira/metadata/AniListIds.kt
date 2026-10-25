package yomira.metadata

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** AniList entry of a library anime: its id, the MyAnimeList id, the catalogue titles and start date. */
data class AniListMedia(
    val id: Long,
    val idMal: Long?,
    val english: String? = null,
    val romaji: String? = null,
    /** First air date as "yyyy-mm-dd"; null when AniList does not know the month. */
    val startDate: String? = null,
    /** Episodes in this entry, when known. */
    val episodes: Int? = null,
)

/**
 * Finds the AniList and MyAnimeList ids of an anime: from its trackers when it is tracked, otherwise
 * by searching AniList for the title (each AniList entry is one season, like most extensions).
 * Results are remembered per anime; a tracker always wins over an earlier search.
 */
object AniListIds {

    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val FIELDS = "id idMal episodes title{english romaji native} synonyms startDate{year month day}"
    private const val MISS_RETRY = 60 * 60 * 1000L

    /** Unit separator: titles may contain any printable character. */
    private const val SEP = "\u001F"
    private const val SEARCH = "search"

    /** animeId to (where the match came from, the match). */
    private val memory = ConcurrentHashMap<Long, Pair<String, AniListMedia>>()
    private val misses = ConcurrentHashMap<Long, Long>()

    private fun store(): PreferenceStore = Injekt.get()
    private fun saved(animeId: Long) = store().getString("yomira_anilist_ids_$animeId", "")

    suspend fun forAnime(
        animeId: Long,
        title: String,
        tracks: List<Track>,
        allowSearch: Boolean,
    ): AniListMedia? = withContext(Dispatchers.IO) {
        val trackers = Injekt.get<TrackerManager>()
        val track = tracks.firstOrNull { it.trackerId == trackers.aniList.id && it.remoteId > 0 }
            ?: tracks.firstOrNull { it.trackerId == trackers.myAnimeList.id && it.remoteId > 0 }
        val source = track?.let { "track:${it.trackerId}:${it.remoteId}" } ?: SEARCH

        remembered(animeId)?.let { (from, media) ->
            if (track == null || from == source) return@withContext media
        }
        if (track != null) {
            val isAniList = track.trackerId == trackers.aniList.id
            val media = byId(if (isAniList) "id" else "idMal", track.remoteId)
            // Without the network, answer with the bare id and ask again next time.
            return@withContext media?.also { keep(animeId, source, it) }
                ?: if (isAniList) AniListMedia(track.remoteId, null) else AniListMedia(0, track.remoteId)
        }
        if (!allowSearch) return@withContext null
        if (System.currentTimeMillis() - (misses[animeId] ?: 0L) < MISS_RETRY) return@withContext null
        val media = search(title)
        if (media == null) {
            misses[animeId] = System.currentTimeMillis()
            return@withContext null
        }
        keep(animeId, SEARCH, media)
        media
    }

    /** Clears the remembered match (after the user fixes the TMDB match or the title changes). */
    fun forget(animeId: Long) {
        memory.remove(animeId)
        misses.remove(animeId)
        saved(animeId).delete()
    }

    private fun remembered(animeId: Long): Pair<String, AniListMedia>? =
        memory[animeId] ?: decode(saved(animeId).get())?.also { memory[animeId] = it }

    private fun keep(animeId: Long, source: String, media: AniListMedia) {
        memory[animeId] = source to media
        saved(animeId).set(
            listOf(
                source,
                media.id,
                media.idMal ?: 0,
                media.english.orEmpty(),
                media.romaji.orEmpty(),
                media.startDate.orEmpty(),
                media.episodes ?: 0,
            ).joinToString(SEP),
        )
    }

    private fun decode(raw: String): Pair<String, AniListMedia>? {
        val parts = raw.split(SEP)
        if (parts.size < 6) return null
        val id = parts[1].toLongOrNull() ?: return null
        return parts[0] to AniListMedia(
            id = id,
            idMal = parts[2].toLongOrNull()?.takeIf { it > 0 },
            english = parts[3].takeIf { it.isNotBlank() },
            romaji = parts[4].takeIf { it.isNotBlank() },
            startDate = parts[5].takeIf { it.isNotBlank() },
            episodes = parts.getOrNull(6)?.toIntOrNull()?.takeIf { it > 0 },
        )
    }

    private fun byId(field: String, value: Long): AniListMedia? {
        val query = "query(\$v:Int){Media($field:\$v,type:ANIME){$FIELDS}}"
        return post(query, value = value)?.obj("data")?.obj("Media")?.toMedia()
    }

    private fun search(title: String): AniListMedia? {
        val query = TitleMatch.withoutMarkers(title).ifBlank { return null }
        val gql = "query(\$q:String){Page(perPage:6){media(search:\$q,type:ANIME,sort:SEARCH_MATCH){$FIELDS}}}"
        val results = post(gql, search = query)?.obj("data")?.obj("Page")?.array("media").orEmpty()
        if (results.isEmpty()) return null
        val scored = results.mapIndexed { index, media ->
            val titles = listOfNotNull(
                media.obj("title")?.string("english"),
                media.obj("title")?.string("romaji"),
                media.obj("title")?.string("native"),
            ) + media.strings("synonyms")
            val best = titles.maxOfOrNull { TitleMatch.similarity(query, it) } ?: 0.0
            // AniList ranks by relevance; keep a small bonus for its order.
            media to best + (0.06 * (results.size - index) / results.size)
        }
        val (media, score) = scored.maxBy { it.second }
        return media.takeIf { score >= 0.55 }?.toMedia()
    }

    private fun JsonObject.toMedia(): AniListMedia? {
        val id = long("id") ?: return null
        val start = obj("startDate")
        val year = start?.int("year")
        val month = start?.int("month")
        return AniListMedia(
            id = id,
            idMal = long("idMal"),
            english = obj("title")?.string("english"),
            romaji = obj("title")?.string("romaji"),
            startDate = if (year != null && month != null) {
                String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, start?.int("day") ?: 1)
            } else {
                null
            },
            episodes = int("episodes")?.takeIf { it > 0 },
        )
    }

    /** An AniList entry and its neighbours in the chain of TV seasons. */
    data class Entry(
        val id: Long,
        val idMal: Long?,
        /** Episodes in the entry; while it airs, the ones aired so far. Null when unknown. */
        val episodes: Int?,
        val sequel: Long?,
        val prequel: Long?,
    )

    private const val ENTRY_TTL = 24 * 60 * 60 * 1000L
    private const val MAX_HOPS = 12
    private val seasonFormats = setOf("TV", "TV_SHORT", "ONA")
    private val entries = ConcurrentHashMap<Long, Entry>()

    private fun savedEntry(id: Long) = store().getString("yomira_anilist_entry_$id", "")

    /** The entry with this AniList id, from memory, the device or AniList (blocking). */
    fun entry(id: Long): Entry? {
        if (id <= 0) return null
        entries[id]?.let { return it }
        val raw = savedEntry(id).get().split(SEP)
        val savedAt = raw.getOrNull(5)?.toLongOrNull() ?: 0L
        val finished = raw.getOrNull(6) == "1"
        val saved = if (raw.size >= 7) {
            Entry(
                id = id,
                idMal = raw[0].toLongOrNull()?.takeIf { it > 0 },
                episodes = raw[1].toIntOrNull()?.takeIf { it > 0 },
                sequel = raw[2].toLongOrNull()?.takeIf { it > 0 },
                prequel = raw[3].toLongOrNull()?.takeIf { it > 0 },
            )
        } else {
            null
        }
        if (saved != null && (finished || System.currentTimeMillis() - savedAt < ENTRY_TTL)) {
            entries[id] = saved
            return saved
        }
        val query = "query(\$v:Int){Media(id:\$v,type:ANIME){id idMal episodes status nextAiringEpisode{episode} " +
            "relations{edges{relationType node{id type format}}}}}"
        val media = post(query, value = id)?.obj("data")?.obj("Media") ?: return saved
        fun related(type: String): Long? = media.obj("relations")?.array("edges").orEmpty()
            .firstOrNull { edge ->
                val node = edge.obj("node")
                edge.string("relationType") == type &&
                    node?.string("type") == "ANIME" &&
                    node.string("format") in seasonFormats
            }
            ?.obj("node")?.long("id")
        val aired = media.obj("nextAiringEpisode")?.int("episode")?.minus(1)?.takeIf { it > 0 }
        val entry = Entry(
            id = id,
            idMal = media.long("idMal")?.takeIf { it > 0 },
            episodes = media.int("episodes")?.takeIf { it > 0 } ?: aired,
            sequel = related("SEQUEL"),
            prequel = related("PREQUEL"),
        )
        val done = media.string("status") == "FINISHED"
        entries[id] = entry
        savedEntry(id).set(
            listOf(
                entry.idMal ?: 0,
                entry.episodes ?: 0,
                entry.sequel ?: 0,
                entry.prequel ?: 0,
                "",
                System.currentTimeMillis(),
                if (done) "1" else "0",
            ).joinToString(SEP),
        )
        return entry
    }

    /** The first season of the chain this entry belongs to. */
    fun root(start: Entry): Entry {
        var current = start
        repeat(MAX_HOPS) {
            current = current.prequel?.let(::entry) ?: return current
        }
        return current
    }

    /** The entry [hops] seasons after [start], or null when the chain is shorter. */
    fun hop(start: Entry, hops: Int): Entry? {
        var current = start
        repeat(hops.coerceIn(0, MAX_HOPS)) {
            current = current.sequel?.let(::entry) ?: return null
        }
        return current
    }

    /**
     * The entry and episode of the [number]th episode counted from the start of [start], going on
     * into its sequels ("episode 25" of a 24-episode first season is the second season's first).
     */
    fun walk(start: Entry, number: Int): Pair<Entry, Int>? {
        if (number < 1) return null
        var current = start
        var remaining = number
        repeat(MAX_HOPS) {
            val count = current.episodes ?: return current to remaining
            if (remaining <= count) return current to remaining
            val next = current.sequel?.let(::entry) ?: return null
            remaining -= count
            current = next
        }
        return null
    }

    internal fun post(query: String, value: Long? = null, search: String? = null): JsonObject? {
        val body = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    value?.let { put("v", it) }
                    search?.let { put("q", it) }
                },
            )
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).header("Accept", "application/json").build()
        return runCatching {
            Injekt.get<NetworkHelper>().client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                TmdbClient.json.parseToJsonElement(response.body.string()) as? JsonObject
            }
        }.getOrNull()
    }
}
