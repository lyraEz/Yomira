package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePolicy
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker(
    private val peekIntoPreview: Boolean = false,
) {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    private val preferences by lazy { Injekt.get<UnsortedPreferences>() }

    suspend fun checkForUpdate(
        context: Context,
        forceCheck: Boolean = false,
        autoUpdate: Boolean = AppUpdatePolicy.DISABLE_AUTO_DOWNLOAD !in preferences.appShouldAutoUpdate().get(),
    ): GetApplicationRelease.Result {
        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreview = isPreviewBuildType || peekIntoPreview,
                    isThirdParty = false,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                    fallbackRepository = YOMIRA_BETA_REPO,
                    betaRepository = YOMIRA_BETA_REPO.takeIf { yomira.update.UpdatePreferences().betaUpdates().get() },
                    forceCheck = forceCheck,
                ),
            )

            if (!peekIntoPreview) {
                when (result) {
                    is GetApplicationRelease.Result.NewUpdate -> {
                        AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                        AppUpdateNotifier(context).promptUpdate(result.release)
                    }

                    else -> {}
                }

                if (autoUpdate && result is GetApplicationRelease.Result.NewUpdate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = result.release.getDownloadLink(),
                            title = result.release.version,
                            scheduled = true,
                        )
                    }
                }
            }

            result
        }
    }

    suspend fun getReleaseNotes(context: Context): GetApplicationRelease.Result {
        return withIOContext {
            getApplicationRelease.awaitReleaseNotes(
                GetApplicationRelease.Arguments(
                    isPreview = isPreviewBuildType || peekIntoPreview,
                    isThirdParty = false,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                    fallbackRepository = YOMIRA_BETA_REPO,
                ),
            )
        }
    }
}

val GITHUB_REPO: String by lazy { getGithubRepo() }

// stable releases live in the Yomira repository; betas (and, while the main repository
// cannot be read, a copy of every stable release) in Yomira-releases.
const val YOMIRA_REPO = "lyraEz/Yomira"
const val YOMIRA_BETA_REPO = "lyraEz/Yomira-releases"

@Suppress("UNUSED_PARAMETER")
fun getGithubRepo(peekIntoPreview: Boolean = false): String = YOMIRA_REPO

val RELEASE_TAG: String by lazy { getReleaseTag() }

fun getReleaseTag(peekIntoPreview: Boolean = false): String =
    if (isPreviewBuildType || peekIntoPreview) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
