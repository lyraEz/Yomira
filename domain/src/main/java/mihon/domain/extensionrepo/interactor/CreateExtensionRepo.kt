package mihon.domain.extensionrepo.interactor

import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.model.KEIYOUSHI_SIGNATURE
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.model.YUZONO_SIGNATURE
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.extensionrepo.service.RepoUrlNormalizer
import tachiyomi.core.common.util.system.logcat

class CreateExtensionRepo(
    private val repository: ExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {
    suspend fun await(indexUrl: String): Result {
        // YMR -->
        // Try every base the pasted URL could mean (the file's directory, a GitHub page's raw
        // address) and report the most useful failure instead of a generic "invalid URL".
        val candidates = RepoUrlNormalizer.candidates(indexUrl) ?: return Result.InvalidUrl
        var failure: Result = Result.InvalidUrl
        for (baseUrl in candidates) {
            when (val probe = service.probe(baseUrl)) {
                is ExtensionRepoService.ProbeResult.Found -> {
                    val result = insert(probe.repo)
                    return if (result == Result.Success) Result.Added(probe.repo, probe.extensionCount) else result
                }
                is ExtensionRepoService.ProbeResult.HttpError ->
                    failure = Result.Unreachable(probe.url, "HTTP ${probe.code}")
                is ExtensionRepoService.ProbeResult.NetworkError ->
                    failure = Result.Unreachable(probe.url, probe.message)
                is ExtensionRepoService.ProbeResult.InvalidFile ->
                    failure = Result.NotARepository(probe.url, probe.message)
            }
        }
        val trimmed = indexUrl.trim()
        if (RepoUrlNormalizer.looksLikeStoreV2(trimmed) || service.isStoreV2Index(trimmed)) {
            return Result.UnsupportedStoreV2
        }
        return failure
        // YMR <--
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
                isVisible = true,
                author = repo.author,
                discord = repo.discord,
                icon = repo.icon,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.WARN, e) { "SQL Conflict attempting to add new repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    /**
     * Error Handler for insert when there are trying to create new repositories
     *
     * SaveExtensionRepoException doesn't provide constraint info in exceptions.
     * First check if the conflict was on primary key. if so return RepoAlreadyExists
     * Then check if the conflict was on fingerprint. if so Return DuplicateFingerprint
     * If neither are found, there was some other Error, and return Result.Error
     *
     * @param repo Extension Repo holder for passing to DB/Error Dialog
     */
    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = repository.getRepo(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo = repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint)
            ?: repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint.removePrefix("0"))
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result

        // YMR -->
        data class Added(val repo: ExtensionRepo, val extensionCount: Int) : Result
        data class Unreachable(val url: String, val reason: String) : Result
        data class NotARepository(val url: String, val reason: String) : Result

        /** The URL is a store-v2 index with no legacy repo.json / index.min.json next to it. */
        data object UnsupportedStoreV2 : Result
        // YMR <--
    }

    companion object {
        const val OFFICIAL_REPO_SIGNATURE = YUZONO_SIGNATURE
        const val KEIYOUSHI_REPO_SIGNATURE = KEIYOUSHI_SIGNATURE
    }
}
