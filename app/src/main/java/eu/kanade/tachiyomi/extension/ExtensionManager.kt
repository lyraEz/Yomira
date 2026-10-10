package eu.kanade.tachiyomi.extension

import android.content.Context
import android.graphics.drawable.Drawable
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.ExtensionLoadFailure
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * The manager of anime extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available anime extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every anime extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 *
 * @param context The application context.
 * @param preferences The application preferences.
 */
class ExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustExtension = Injekt.get(),
    private val extensionRepoRepository: mihon.domain.extensionrepo.repository.ExtensionRepoRepository = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * API where all the available anime extensions can be found.
     */
    private val api = ExtensionApi()

    /**
     * The installer which installs, updates and uninstalls the anime extensions.
     */
    private val installer: ExtensionInstaller = Injekt.get()

    private val iconMap = mutableMapOf<String, Drawable>()

    private val repoHostAuthorRegex = """^https://(?:raw\.githubusercontent\.com|codeberg\.org|gitlab\.com)/([^/]+)/.*""".toRegex()

    private val installedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Installed>())
    val installedExtensionsFlow = combine(
        installedExtensionsMapFlow,
        extensionRepoRepository.subscribeAll(),
    ) { installedMap, repos ->
        installedMap.values.map { extension ->
            val matchingRepo = repos.find {
                it.signingKeyFingerprint.equals(extension.signatureHash, ignoreCase = true) ||
                it.signingKeyFingerprint.padStart(64, '0').equals(extension.signatureHash.padStart(64, '0'), ignoreCase = true)
            }
            val author = matchingRepo?.author
                ?: matchingRepo?.let { repo ->
                    repoHostAuthorRegex.find(repo.baseUrl)?.let { match ->
                        "@${match.groupValues[1]}"
                    } ?: repo.shortName ?: repo.name
                } ?: extension.author

            extension.copy(author = author)
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    private val availableExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Available>())
    val availableExtensionsFlow = availableExtensionsMapFlow.mapExtensions(scope)

    private val untrustedExtensionsMapFlow = MutableStateFlow(emptyMap<String, Extension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionsMapFlow.mapExtensions(scope)

    // YMR -->
    private val failedExtensionsMapFlow = MutableStateFlow(emptyMap<String, ExtensionLoadFailure>())

    /** Installed extension APKs that could not be loaded, with the reason. */
    val failedExtensionsFlow: StateFlow<List<ExtensionLoadFailure>> = failedExtensionsMapFlow
        .map { it.values.sortedBy { failure -> failure.name.lowercase() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Last install error per item, keyed like the extension list items. */
    val installErrors: StateFlow<Map<String, String>> get() = installer.installErrors

    fun reportInstallError(downloadId: Long, detail: String) {
        installer.reportInstallError(downloadId, detail)
    }

    fun failureFor(pkgName: String): ExtensionLoadFailure? = failedExtensionsMapFlow.value[pkgName]

    /** Scans the installed packages again, e.g. after changing a setting that filters extensions. */
    suspend fun reloadExtensions() {
        initAnimeExtensions()
        updatePendingUpdatesCount()
    }
    // YMR <--

    init {
        scope.launch {
            initAnimeExtensions()
        }
        ExtensionInstallReceiver(InstallationListener()).register(context)
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = installedExtensionsMapFlow.value.values
            .find { ext ->
                ext.sources.any { it.id == sourceId }
            }
            ?.pkgName
            ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo!!
                .loadIcon(context.packageManager)
        }
    }

    fun getExtensionNameForSource(sourceId: Long): String? {
        return installedExtensionsMapFlow.value.values
            .find { ext -> ext.sources.any { it.id == sourceId } }
            ?.name
    }

    fun isTorrentSource(sourceId: Long): Boolean {
        return installedExtensionsMapFlow.value.values
            .any { ext -> ext.isTorrent && ext.sources.any { it.id == sourceId } }
    }

    private var availableAnimeExtensionsSourcesData: Map<Long, StubSource> = emptyMap()

    private fun setupAvailableAnimeExtensionsSourcesDataMap(
        animeextensions: List<Extension.Available>,
    ) {
        if (animeextensions.isEmpty()) return
        availableAnimeExtensionsSourcesData = animeextensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableAnimeExtensionsSourcesData[id]

    /**
     * Loads and registers the installed animeextensions.
     */
    private suspend fun initAnimeExtensions() {
        val animeextensions = ExtensionLoader.loadExtensions(context)

        installedExtensionsMapFlow.value = animeextensions
            .filterIsInstance<LoadResult.Success>()
            .associate { it.extension.pkgName to it.extension }

        untrustedExtensionsMapFlow.value = animeextensions
            .filterIsInstance<LoadResult.Untrusted>()
            .associate { it.extension.pkgName to it.extension }

        // YMR -->
        failedExtensionsMapFlow.value = animeextensions
            .filterIsInstance<LoadResult.Error>()
            .mapNotNull { it.failure }
            .associateBy { it.pkgName }
        // YMR <--

        _isInitialized.value = true
    }

    /**
     * Finds the available anime extensions in the [api] and updates [availableExtensionsMapFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<Extension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        availableExtensionsMapFlow.value = extensions.associateBy { "${it.pkgName}-${it.author}" }
        updatedInstalledAnimeExtensionsStatuses(extensions)
        setupAvailableAnimeExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(animeextensions: List<Extension.Available>) {
        if (subLanguagesEnabledOnFirstRun || animeextensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the animeextension level.
        val availableLanguages = animeextensions
            .flatMap(Extension.Available::sources)
            .distinctBy(Extension.Available.AnimeSource::lang)
            .map(Extension.Available.AnimeSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Sets the update field of the installed animeextensions with the given [availableExtensions].
     *
     * @param availableExtensions The list of animeextensions given by the [api].
     */
    private fun updatedInstalledAnimeExtensionsStatuses(
        availableExtensions: List<Extension.Available>,
    ) {
        if (availableExtensions.isEmpty()) {
            preferences.animeExtensionUpdatesCount().set(0)
            return
        }

        val installedExtensionsMap = installedExtensionsMapFlow.value.toMutableMap()
        var changed = false

        for ((pkgName, extension) in installedExtensionsMap) {
            val availableExt = availableExtensions.find { it.pkgName == pkgName && it.author == extension.author }
                ?: availableExtensions.find { it.pkgName == pkgName }

            if (availableExt == null && !extension.isObsolete) {
                installedExtensionsMap[pkgName] = extension.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = extension.updateExists(availableExt)
                
                val newExt = extension.copy(
                    hasUpdate = hasUpdate,
                    repoUrl = availableExt.repoUrl,
                )
                
                if (newExt != extension) {
                    installedExtensionsMap[pkgName] = newExt
                    changed = true
                }
            }
        }
        if (changed) {
            installedExtensionsMapFlow.value = installedExtensionsMap
        }
        updatePendingUpdatesCount()
    }

    /**
     * Returns a flow of the installation process for the given anime extension.
         It will complete
     * once the anime extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The anime extension to be installed.
     */
    fun installExtension(extension: Extension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    /**
     * Returns a flow of the installation process for the given anime extension. It will complete
     * once the anime extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The anime extension to be updated.
     */
    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val availableExt = availableExtensionsMapFlow.value["${extension.pkgName}-${extension.author}"]
            ?: availableExtensionsMapFlow.value.values.find { it.pkgName == extension.pkgName }
            ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        installer.cancelInstall(extension.pkgName)
    }

    /**
     * Sets to "installing" status of an anime extension installation.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
        // YMR -->
        // The installer reporting success only means the APK is on the device. Read it back from
        // PackageManager and load it, so the list shows whether it is trusted and has sources even
        // when the package broadcast is late or never arrives (seen on some TV firmwares).
        if (step == InstallStep.Installed) {
            installer.packageForDownload(downloadId)?.let { pkgName ->
                scope.launch { refreshInstalledPackage(pkgName) }
            }
        }
        // YMR <--
    }

    // YMR -->
    private suspend fun refreshInstalledPackage(pkgName: String) {
        repeat(REFRESH_ATTEMPTS) { attempt ->
            when (val result = ExtensionLoader.loadExtensionFromPkgName(context, pkgName)) {
                is LoadResult.Success -> {
                    failedExtensionsMapFlow.value -= pkgName
                    untrustedExtensionsMapFlow.value -= pkgName
                    installedExtensionsMapFlow.value += result.extension.withUpdateCheck()
                    updatePendingUpdatesCount()
                    return
                }
                is LoadResult.Untrusted -> {
                    failedExtensionsMapFlow.value -= pkgName
                    installedExtensionsMapFlow.value -= pkgName
                    untrustedExtensionsMapFlow.value += result.extension
                    updatePendingUpdatesCount()
                    return
                }
                is LoadResult.Error -> {
                    val failure = result.failure
                    if (failure != null) {
                        installedExtensionsMapFlow.value -= pkgName
                        untrustedExtensionsMapFlow.value -= pkgName
                        failedExtensionsMapFlow.value += (pkgName to failure)
                        return
                    }
                    // Not visible to PackageManager yet; give the system a moment.
                    delay(REFRESH_DELAY_MS * (attempt + 1))
                }
            }
        }
        logcat(LogPriority.WARN) { "Installed package $pkgName could not be read back from PackageManager" }
    }
    // YMR <--

    /**
     * Uninstalls the anime extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: Extension) {
        installer.uninstallApk(extension.pkgName)
    }

    // YMR -->
    /** Uninstalls by package name, for APKs that never loaded into an [Extension]. */
    fun uninstallPackage(pkgName: String) {
        installer.uninstallApk(pkgName)
    }
    // YMR <--

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param extension the extension to trust
     */
    suspend fun trust(extension: Extension.Untrusted) {
        untrustedExtensionsMapFlow.value[extension.pkgName] ?: return

        trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

        untrustedExtensionsMapFlow.value -= extension.pkgName

        // YMR --> a trusted extension can still fail to load; keep it visible with the reason
        when (val result = ExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName)) {
            is LoadResult.Success -> registerNewExtension(result.extension)
            is LoadResult.Error -> result.failure?.let { failedExtensionsMapFlow.value += (it.pkgName to it) }
            is LoadResult.Untrusted -> untrustedExtensionsMapFlow.value += result.extension
        }
        // YMR <--
    }

    /**
     * Registers the given anime extension in this and the source managers.
     *
     * @param extension The anime extension to be registered.
     */
    private fun registerNewExtension(extension: Extension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    /**
     * Registers the given updated anime extension in this and the source managers previously removing
     * the outdated ones.
     *
     * @param extension The anime extension to be registered.
     */
    private fun registerUpdatedExtension(extension: Extension.Installed) {
        installedExtensionsMapFlow.value += extension
    }

    /**
     * Unregisters the animeextension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterAnimeExtension(pkgName: String) {
        installedExtensionsMapFlow.value -= pkgName
        untrustedExtensionsMapFlow.value -= pkgName
        // YMR -->
        failedExtensionsMapFlow.value -= pkgName
        // YMR <--
    }

    /**
     * Listener which receives events of the anime extensions being installed, updated or removed.
     */
    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: Extension.Installed) {
            // YMR -->
            failedExtensionsMapFlow.value -= extension.pkgName
            // YMR <--
            registerNewExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
            installer.dismissInstallNotification(extension.pkgName)
        }

        override fun onExtensionUpdated(extension: Extension.Installed) {
            // YMR -->
            failedExtensionsMapFlow.value -= extension.pkgName
            // YMR <--
            registerUpdatedExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
            installer.dismissInstallNotification(extension.pkgName)
        }

        override fun onExtensionUntrusted(extension: Extension.Untrusted) {
            // YMR -->
            failedExtensionsMapFlow.value -= extension.pkgName
            // YMR <--
            installedExtensionsMapFlow.value -= extension.pkgName
            untrustedExtensionsMapFlow.value += extension
            updatePendingUpdatesCount()
            installer.dismissInstallNotification(extension.pkgName)
        }

        // YMR -->
        override fun onExtensionFailed(failure: ExtensionLoadFailure) {
            installedExtensionsMapFlow.value -= failure.pkgName
            untrustedExtensionsMapFlow.value -= failure.pkgName
            failedExtensionsMapFlow.value += (failure.pkgName to failure)
            updatePendingUpdatesCount()
            installer.dismissInstallNotification(failure.pkgName)
        }
        // YMR <--

        override fun onPackageUninstalled(pkgName: String) {
            ExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterAnimeExtension(pkgName)
            updatePendingUpdatesCount()
            installer.dismissInstallNotification(pkgName)
        }
    }

    /**
     * Extension method to set the update field of an installed anime extension.
     */
    private fun Extension.Installed.withUpdateCheck(): Extension.Installed {
        return if (updateExists()) {
            copy(hasUpdate = true)
        } else {
            this
        }
    }

    private fun Extension.Installed.updateExists(
        availableExtension: Extension.Available? = null,
    ): Boolean {
        val availableExt = availableExtension
            ?: availableExtensionsMapFlow.value[pkgName]
            ?: return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = installedExtensionsMapFlow.value.values.count { it.hasUpdate }
        preferences.animeExtensionUpdatesCount().set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }

    private operator fun <T : Extension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    // YMR -->
    private companion object {
        const val REFRESH_ATTEMPTS = 5
        const val REFRESH_DELAY_MS = 400L
    }
    // YMR <--

    private fun <T : Extension> StateFlow<Map<String, T>>.mapExtensions(
        scope: CoroutineScope,
    ): StateFlow<List<T>> {
        return map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, value.values.toList())
    }
}
