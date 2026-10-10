package eu.kanade.tachiyomi.extension.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.tachiyomi.util.lang.Hash
import tachiyomi.core.common.i18n.stringResource
import yomira.i18n.YMR
import java.io.File

// YMR -->
/**
 * Reads a downloaded extension APK before it is handed to the system installer, so problems the
 * installer would only report as "App not installed" get a precise explanation instead.
 */
object ExtensionApkInspector {

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"

    data class ApkInfo(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val libVersion: Double?,
        val isExtension: Boolean,
        val signatures: List<String>,
    )

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private val FLAGS = PackageManager.GET_META_DATA or
        PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun inspect(context: Context, apk: File): ApkInfo {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, FLAGS)
            ?: throw ExtensionInstallException(context.stringResource(YMR.strings.ext_error_not_apk))
        return info.toApkInfo()
    }

    /** Null when Android should accept the APK, otherwise a message for the user. */
    fun checkInstallable(context: Context, apk: ApkInfo): String? {
        if (!apk.isExtension) {
            return context.stringResource(YMR.strings.ext_error_not_extension, apk.packageName)
        }
        val lib = apk.libVersion
        if (lib == null || lib < ExtensionLoader.LIB_VERSION_MIN || lib >= ExtensionLoader.LIB_VERSION_MAX + 1.0) {
            return context.stringResource(
                YMR.strings.ext_error_lib_version,
                lib?.toString() ?: "?",
                ExtensionLoader.LIB_VERSION_MIN,
                ExtensionLoader.LIB_VERSION_MAX,
            )
        }
        val installed = installedPackage(context, apk.packageName) ?: return null
        val installedVersion = PackageInfoCompat.getLongVersionCode(installed)
        if (apk.versionCode < installedVersion) {
            return context.stringResource(
                YMR.strings.ext_error_downgrade,
                installed.versionName ?: installedVersion.toString(),
                apk.versionName ?: apk.versionCode.toString(),
            )
        }
        val installedSignatures = installed.signatureHashes()
        if (installedSignatures.isNotEmpty() && apk.signatures.isNotEmpty() &&
            installedSignatures.none { it in apk.signatures }
        ) {
            return context.stringResource(YMR.strings.ext_error_signature_mismatch)
        }
        return null
    }

    /** Explains a PackageInstaller failure status in the user's language. */
    fun describeInstallerStatus(context: Context, status: Int, message: String?): String {
        val summary = context.stringResource(
            when (status) {
                PackageInstaller.STATUS_FAILURE_CONFLICT -> YMR.strings.ext_error_installer_conflict
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> YMR.strings.ext_error_installer_incompatible
                PackageInstaller.STATUS_FAILURE_STORAGE -> YMR.strings.ext_error_installer_storage
                PackageInstaller.STATUS_FAILURE_INVALID -> YMR.strings.ext_error_not_apk
                PackageInstaller.STATUS_FAILURE_BLOCKED -> YMR.strings.ext_error_installer_blocked
                else -> YMR.strings.ext_error_installer_generic
            },
        )
        return if (message.isNullOrBlank()) summary else "$summary ($message)"
    }

    private fun installedPackage(context: Context, pkgName: String): PackageInfo? = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(pkgName, FLAGS)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun PackageInfo.toApkInfo(): ApkInfo {
        val meta = applicationInfo?.metaData
        val lib = (
            meta?.get("aniyomix.extensionLib") ?: meta?.get("tachiyomix.extensionLib")
            )?.let {
            when (it) {
                is Int -> it.takeIf { v -> v != 0 }?.toDouble()
                is Float -> it.toDouble()
                is Double -> it
                is String -> it.toDoubleOrNull()
                else -> null
            }
        } ?: versionName?.substringBeforeLast('.')?.toDoubleOrNull()
        return ApkInfo(
            packageName = packageName,
            versionName = versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            libVersion = lib,
            isExtension = reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE },
            signatures = signatureHashes(),
        )
    }

    private fun PackageInfo.signatureHashes(): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            this.signatures
        }
        return signatures.orEmpty().map { Hash.sha256(it.toByteArray()) }
    }
}
// YMR <--
