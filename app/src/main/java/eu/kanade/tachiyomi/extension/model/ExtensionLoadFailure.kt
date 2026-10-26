package eu.kanade.tachiyomi.extension.model

import androidx.compose.runtime.Immutable

/**
 * An extension APK that is installed on the device but could not be turned into sources.
 *
 * Previously these were logged and dropped, so an APK visible in the system settings simply did
 * not appear in the app. Keeping them lets the UI say why and offer a copyable diagnostic.
 */
@Immutable
data class ExtensionLoadFailure(
    val pkgName: String,
    val name: String,
    val versionName: String?,
    val versionCode: Long,
    val libVersion: Double?,
    val reason: Reason,
    val detail: String? = null,
    val signatureHash: String? = null,
    val isShared: Boolean = true,
) {
    enum class Reason {
        /** The APK has no versionName, which every extension must declare. */
        MISSING_VERSION,

        /** The extension library version is outside what this app supports. */
        LIB_VERSION_UNSUPPORTED,

        /** The APK is not signed. */
        UNSIGNED,

        /** The extension is adult content and adult sources are turned off in settings. */
        NSFW_FILTERED,

        /** The source classes could not be instantiated (API mismatch, missing class, crash). */
        CLASS_LOAD_FAILED,

        /** The manifest does not declare the source class metadata. */
        MANIFEST_INVALID,
    }

    /** Errors the user can only fix by installing another build of the extension. */
    val isIncompatible: Boolean
        get() = reason == Reason.LIB_VERSION_UNSUPPORTED ||
            reason == Reason.CLASS_LOAD_FAILED ||
            reason == Reason.MANIFEST_INVALID ||
            reason == Reason.MISSING_VERSION ||
            reason == Reason.UNSIGNED
}
