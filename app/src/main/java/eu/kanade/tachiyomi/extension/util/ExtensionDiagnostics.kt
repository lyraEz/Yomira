package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.ExtensionLoadFailure
import eu.kanade.tachiyomi.source.online.HttpSource
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

// YMR -->
/**
 * Builds a plain-text report about one extension: what the device, PackageManager, the loader and
 * the repositories say about it. Meant to be copied and pasted into a bug report.
 */
object ExtensionDiagnostics {

    suspend fun build(context: Context, extension: Extension?, pkgName: String, installError: String?): String =
        withIOContext {
            val manager = Injekt.get<ExtensionManager>()
            val repos = runCatching { Injekt.get<ExtensionRepoRepository>().getAll() }.getOrDefault(emptyList())
            val failure: ExtensionLoadFailure? = manager.failureFor(pkgName)
            val installed = manager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
            val untrusted = manager.untrustedExtensionsFlow.value.find { it.pkgName == pkgName }

            buildString {
                appendLine("== Yomira extension diagnostics ==")
                appendLine("app: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                appendLine("abis: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("installer: ${Injekt.get<BasePreferences>().extensionInstaller().get()}")
                appendLine("adult sources enabled: ${Injekt.get<SourcePreferences>().showNsfwSource().get()}")
                appendLine("supported extension lib: ${ExtensionLoader.LIB_VERSION_MIN}..${ExtensionLoader.LIB_VERSION_MAX}")
                appendLine()

                appendLine("== package $pkgName ==")
                val realPkg = installed?.pkgName ?: untrusted?.pkgName ?: failure?.pkgName ?: pkgName
                val shared = try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(realPkg, PackageManager.GET_META_DATA)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                if (shared != null) {
                    appendLine(
                        "installed on device: yes, ${shared.versionName} (${PackageInfoCompat.getLongVersionCode(shared)})",
                    )
                } else {
                    appendLine("installed on device: no")
                }
                val private = File(ExtensionLoader.getPrivateExtensionDir(context), "$realPkg.ext")
                appendLine("private copy: ${if (private.isFile) "yes" else "no"}")

                val state = when {
                    installed != null -> "loaded, ${installed.sources.size} source(s)"
                    untrusted != null -> "untrusted (signature not trusted yet)"
                    failure != null -> "failed: ${failure.reason}"
                    extension is Extension.Available -> "available, not installed"
                    else -> "unknown"
                }
                appendLine("state: $state")

                val signature = installed?.signatureHash ?: untrusted?.signatureHash ?: failure?.signatureHash
                if (signature != null) {
                    appendLine("signature sha256: $signature")
                    val repo = repos.find { it.signingKeyFingerprint.equals(signature, ignoreCase = true) }
                    appendLine("matching repository: ${repo?.let { "${it.name} (${it.baseUrl})" } ?: "none"}")
                }
                (installed ?: untrusted ?: extension)?.let {
                    appendLine("version: ${it.versionName} (${it.versionCode}), lib ${it.libVersion}")
                    it.repoUrl?.let { url -> appendLine("repository url: $url") }
                }
                failure?.let {
                    appendLine("load failure: ${it.reason}")
                    it.detail?.let { detail -> appendLine("detail: $detail") }
                }
                installError?.let { appendLine("last install error: $it") }

                installed?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
                    appendLine()
                    appendLine("== sources ==")
                    sources.forEach { source ->
                        val base = (source as? HttpSource)?.baseUrl.orEmpty()
                        appendLine("${source.id} ${source.name} [${source.lang}] $base".trimEnd())
                    }
                }
                if (extension is Extension.Available) {
                    appendLine()
                    appendLine("== repository entry ==")
                    appendLine("apk: ${extension.apkName}")
                    extension.sources.forEach { appendLine("${it.id} ${it.name} [${it.lang}] ${it.baseUrl}") }
                }

                appendLine()
                appendLine("== repositories ==")
                repos.forEach { appendLine("${it.name}: ${it.baseUrl} key=${it.signingKeyFingerprint}") }
            }
        }
}
// YMR <--
