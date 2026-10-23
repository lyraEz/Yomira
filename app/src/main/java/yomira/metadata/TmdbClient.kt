package yomira.metadata

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Raised when TMDB refuses the key (401), so the settings can say so. */
class TmdbAuthException : Exception("TMDB rejected the API key")

/**
 * Minimal TMDB v3 client. Accepts the v3 API key (sent as `api_key`) or the v4 read access token
 * (a JWT starting with "eyJ", sent as a bearer token). Blocking: call it from an IO dispatcher.
 */
class TmdbClient(
    private val key: String,
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) {

    private val bearer = key.startsWith("eyJ")

    private fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        val url = "$API$path".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
            if (!bearer) addQueryParameter("api_key", key)
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (bearer) header("Authorization", "Bearer $key") }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw TmdbAuthException()
            if (!response.isSuccessful) return null
            return runCatching { json.parseToJsonElement(response.body.string()) as? JsonObject }.getOrNull()
        }
    }

    /** True when the key works. */
    fun check(): Boolean = get("/configuration") != null

    fun searchTv(query: String, language: String): List<JsonObject> =
        get("/search/tv", mapOf("query" to query, "language" to language, "include_adult" to "false"))
            ?.array("results").orEmpty()

    fun searchMovie(query: String, language: String): List<JsonObject> =
        get("/search/movie", mapOf("query" to query, "language" to language, "include_adult" to "false"))
            ?.array("results").orEmpty()

    /** Show details with its logos (Portuguese, English or textless). */
    fun tv(id: Long, language: String): JsonObject? =
        get(
            "/tv/$id",
            mapOf(
                "language" to language,
                "append_to_response" to "images,aggregate_credits",
                "include_image_language" to "pt,en,null",
            ),
        )

    fun movie(id: Long, language: String): JsonObject? =
        get(
            "/movie/$id",
            mapOf(
                "language" to language,
                "append_to_response" to "images,credits",
                "include_image_language" to "pt,en,null",
            ),
        )

    fun season(id: Long, season: Int, language: String): JsonObject? =
        get("/tv/$id/season/$season", mapOf("language" to language))

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val IMAGES = "https://image.tmdb.org/t/p/"

        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /** Full image URL; sizes: w300/w780/w1280 for stills and backdrops, w500 for logos. */
        fun image(path: String?, size: String): String? = path?.takeIf { it.isNotBlank() }?.let { "$IMAGES$size$it" }
    }
}

internal fun JsonObject.element(name: String): JsonElement? = this[name]
internal fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
internal fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.int(name: String): Int? = long(name)?.toInt()
internal fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonObject.array(name: String): List<JsonObject> =
    (this[name] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.longs(name: String): List<Long> =
    (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
internal fun JsonObject.ints(name: String): List<Int> =
    (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull?.toInt() }.orEmpty()
internal fun JsonObject.strings(name: String): List<String> =
    (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
