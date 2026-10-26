package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(2, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        // Stable releases (from the main repository, or the fallback while it cannot be read) and,
        // when asked, the betas published in the beta repository.
        val stable = releasesWithFallback(arguments.repository, arguments.fallbackRepository)
            .filter { !it.preRelease }
        val betas = arguments.betaRepository
            ?.let { repo -> runCatching { service.releaseNotes(repo) }.getOrDefault(emptyList()) }
            .orEmpty()
            .filter { it.preRelease }
        val releases = (stable + betas)
            .filter {
                isNewVersion(
                    arguments.isPreview,
                    arguments.commitCount,
                    arguments.versionName,
                    it.version,
                )
            }
            .sortedByDescending { AppVersion.parse(it.version) }

        val latest = releases.getLatest() ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            isPreview = arguments.isPreview,
            commitCount = arguments.commitCount,
            versionName = arguments.versionName,
            versionTag = latest.version,
        )
        return when {
            isNewVersion && arguments.isThirdParty -> Result.ThirdPartyInstallation
            isNewVersion -> Result.NewUpdate(latest)
            else -> Result.NoNewUpdate
        }
    }

    suspend fun awaitReleaseNotes(arguments: Arguments): Result {
        val releases = releasesWithFallback(arguments.repository, arguments.fallbackRepository)
            .filter { !it.preRelease }
        val checksumRegex = """---(\R|.)*Checksums(\R|.)*""".toRegex()

        val release = releases.firstOrNull()
            ?.copy(
                info = releases.joinToString("\r---\r") {
                    "## ${it.version}\r\r" +
                        it.info.replace(checksumRegex, "")
                },
            )
        if (release == null) return Result.NoNewUpdate
        return Result.NewUpdate(release)
    }

    /**
     * [isPreview] is if current version is Preview (beta) build
     *
     * [versionTag] is the version of new release
     *
     * Release (stable) version will compare with current's [versionName] ("v0.1.2")
     *
     * Preview (beta) version will compare with current's [commitCount] ("r1234")
     */
    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        return if (isPreview) {
            // Preview builds: based on releases in the app repository
            // tagged as something like "r1234"
            versionTag.replace("[^\\d]".toRegex(), "").toInt() > commitCount
        } else {
            // Release builds: tags like "v1.12.0" or "v1.13.0-beta.2"
            AppVersion.isNewer(versionTag, versionName)
        }
    }

    /** Releases of [repository], or of [fallback] when the first cannot be read or has none. */
    private suspend fun releasesWithFallback(repository: String, fallback: String?): List<Release> {
        val primary = try {
            service.releaseNotes(repository)
        } catch (e: Exception) {
            if (fallback == null) throw e
            null
        }
        if (!primary.isNullOrEmpty() || fallback == null) return primary.orEmpty()
        return service.releaseNotes(fallback)
    }

    data class Arguments(
        /** If current version is Preview (beta) build */
        val isPreview: Boolean,
        /** If current version is from third party */
        val isThirdParty: Boolean,
        /** Commit count of current version */
        val commitCount: Int,
        /** Current version name, could be version tag (v0.1.2) or commit count (r1234) */
        val versionName: String,
        /** Repository name */
        val repository: String,
        /** Read instead of [repository] when that one cannot be read or has no releases */
        val fallbackRepository: String? = null,
        /** Also offer the pre-releases (betas) published here */
        val betaRepository: String? = null,
        /** Force check for new update */
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
        data object ThirdPartyInstallation : Result
    }
}

internal fun List<Release>.getLatest(): Release? {
    val checksumRegex = """---(\R|.)*Checksums(\R|.)*""".toRegex()

    return firstOrNull()
        ?.copy(
            info = joinToString("\r---\r") {
                "## ${it.version}\r\r" +
                    it.info.replace(checksumRegex, "")
            },
        )
}
