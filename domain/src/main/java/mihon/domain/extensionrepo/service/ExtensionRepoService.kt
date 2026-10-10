package mihon.domain.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            try {
                with(json) {
                    val response = client.newCall(GET("$repo/repo.json")).awaitSuccess()
                    val bodyString = response.body.source().decompressIfGzipped().use { it.readUtf8() }
                    try {
                        decodeFromString<ExtensionRepoMetaDto>(bodyString).toExtensionRepo(baseUrl = repo)
                    } catch (_: Exception) {
                        decodeFromString<ExtensionRepoDto>(bodyString).toExtensionRepo(baseUrl = repo)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }

    // YMR -->
    sealed interface ProbeResult {
        data class Found(val repo: ExtensionRepo, val extensionCount: Int) : ProbeResult
        data class HttpError(val url: String, val code: Int) : ProbeResult
        data class NetworkError(val url: String, val message: String) : ProbeResult
        data class InvalidFile(val url: String, val message: String) : ProbeResult
    }

    /**
     * Checks that [baseUrl] really is a repository this app can read: `repo.json` must describe it
     * and `index.min.json` must be a list of extensions.
     */
    suspend fun probe(baseUrl: String): ProbeResult = withIOContext {
        val repoJsonUrl = "$baseUrl/repo.json"
        val repo = when (val body = fetchText(repoJsonUrl)) {
            is Fetched.Text -> try {
                with(json) {
                    try {
                        decodeFromString<ExtensionRepoMetaDto>(body.text).toExtensionRepo(baseUrl = baseUrl)
                    } catch (_: Exception) {
                        decodeFromString<ExtensionRepoDto>(body.text).toExtensionRepo(baseUrl = baseUrl)
                    }
                }
            } catch (e: Exception) {
                return@withIOContext ProbeResult.InvalidFile(repoJsonUrl, e.message ?: e.javaClass.simpleName)
            }
            is Fetched.Http -> return@withIOContext ProbeResult.HttpError(repoJsonUrl, body.code)
            is Fetched.Failure -> return@withIOContext ProbeResult.NetworkError(repoJsonUrl, body.message)
        }
        if (repo.signingKeyFingerprint.trimStart('0').isEmpty()) {
            return@withIOContext ProbeResult.InvalidFile(repoJsonUrl, "repo.json has no signingKeyFingerprint")
        }

        val indexUrl = "$baseUrl/index.min.json"
        when (val body = fetchText(indexUrl)) {
            is Fetched.Text -> try {
                val count = json.parseToJsonElement(body.text).jsonArray.size
                ProbeResult.Found(repo, count)
            } catch (e: Exception) {
                ProbeResult.InvalidFile(indexUrl, e.message ?: e.javaClass.simpleName)
            }
            is Fetched.Http -> ProbeResult.HttpError(indexUrl, body.code)
            is Fetched.Failure -> ProbeResult.NetworkError(indexUrl, body.message)
        }
    }

    /** Whether [url] serves a store-v2 index (the newer Aniyomi format this app does not read). */
    suspend fun isStoreV2Index(url: String): Boolean = withIOContext {
        val body = fetchText(url) as? Fetched.Text ?: return@withIOContext false
        runCatching {
            val obj = json.parseToJsonElement(body.text).jsonObject
            "extensionList" in obj || ("storeName" in obj && "extensions" in obj)
        }.getOrDefault(false)
    }

    private sealed interface Fetched {
        data class Text(val text: String) : Fetched
        data class Http(val code: Int) : Fetched
        data class Failure(val message: String) : Fetched
    }

    private fun fetchText(url: String): Fetched = try {
        client.newCall(GET(url)).execute().use { response ->
            if (!response.isSuccessful) {
                Fetched.Http(response.code)
            } else {
                Fetched.Text(response.body.source().decompressIfGzipped().use { it.readUtf8() })
            }
        }
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "Failed to fetch $url" }
        Fetched.Failure(e.message ?: e.javaClass.simpleName)
    }
    // YMR <--

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }

        return if (isGzip) gzip().buffer() else this
    }
}