package tachiyomi.core.common.storage

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        val appName = context.stringResource(MR.strings.app_name)
        val normalizedAppName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val shared = File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                normalizedAppName,
        )
        // Without broad storage access (Android 11+ without "all files access", or older Android
        // without the storage permission, which TV boxes rarely grant) the shared folder cannot be
        // written. The app's own external folder always can, and is still reachable over adb.
        if (canWriteSharedStorage()) return shared
        return context.getExternalFilesDir(null) ?: shared
    }

    private fun canWriteSharedStorage(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
            Environment.isExternalStorageLegacy() && hasWritePermission()
        else -> hasWritePermission()
    }

    private fun hasWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun path(): String {
        return directory().toUri().toString()
    }
}
