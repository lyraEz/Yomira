package mihon.domain.extensionrepo.service

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns whatever URL the user pasted into the repository base URLs worth probing.
 *
 * The app reads the legacy layout: `<base>/repo.json` for the name and signing key and
 * `<base>/index.min.json` for the extensions. Users often paste one of those files, a GitHub page,
 * or the `store-v2.json` address that newer Aniyomi builds use. A store-v2 index is a different
 * format the app does not read, but repositories that publish it usually keep the legacy files
 * next to it, so its directory is tried as a base too.
 */
object RepoUrlNormalizer {

    private val KNOWN_INDEX_FILES = listOf("index.min.json", "index.json", "repo.json")

    /** Returns null when the text is not an http(s) URL at all. */
    fun candidates(input: String): List<String>? {
        var url = input.trim().removeSuffix("/")
        if (url.isEmpty()) return null
        if (!url.contains("://")) url = "https://$url"

        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "https" && httpUrl.scheme != "http") return null

        val result = mutableListOf<String>()
        val withoutQuery = url.substringBefore('?').substringBefore('#').removeSuffix("/")
        val lastSegment = withoutQuery.substringAfterLast('/')

        when {
            KNOWN_INDEX_FILES.any { lastSegment.equals(it, ignoreCase = true) } -> {
                result += withoutQuery.substringBeforeLast('/')
            }
            lastSegment.endsWith(".json", ignoreCase = true) -> {
                // e.g. store-v2.json or store-v2-1000-7.json: try the directory that holds it.
                result += withoutQuery.substringBeforeLast('/')
            }
            else -> {
                result += withoutQuery
                githubBlobToRaw(withoutQuery)?.let { result += it }
            }
        }
        return result.distinct()
    }

    /** True when the pasted URL points at a store-v2 style index rather than a legacy one. */
    fun looksLikeStoreV2(input: String): Boolean {
        val last = input.trim().substringBefore('?').removeSuffix("/").substringAfterLast('/')
        return last.startsWith("store", ignoreCase = true) && last.endsWith(".json", ignoreCase = true)
    }

    /**
     * https://github.com/<owner>/<repo>/tree/<branch>[/path] -> raw.githubusercontent.com base.
     */
    private fun githubBlobToRaw(url: String): String? {
        val match = Regex("^https?://github\\.com/([^/]+)/([^/]+)/(?:tree|blob)/([^/]+)(/.*)?$").find(url)
            ?: return null
        val (owner, repo, branch, path) = match.destructured
        return "https://raw.githubusercontent.com/$owner/$repo/$branch$path".removeSuffix("/")
    }
}
