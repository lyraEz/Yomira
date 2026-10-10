package yomira.tv.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Where backups can be written and found without the system file picker.
 *
 * Fire TV and many boxes ship without a working document picker, so backups are also kept in the
 * app's own external folder (`Android/data/<package>/files/backups`), which needs no permission and
 * is reachable with `adb pull`, and can be sent to or received from a phone on the same network.
 */
object BackupFiles {

    data class Found(
        val name: String,
        val uri: Uri,
        val sizeBytes: Long,
        val modified: Long,
        val location: Location,
    )

    enum class Location { APP_FOLDER, STORAGE_FOLDER, DOWNLOADS, RECEIVED }

    fun appBackupDir(context: Context): File =
        (context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")).also { it.mkdirs() }

    fun receivedDir(context: Context): File = File(context.cacheDir, "companion")

    fun newBackupFile(context: Context, fileName: String): File =
        File(appBackupDir(context), fileName).also { if (!it.exists()) it.createNewFile() }

    fun list(context: Context): List<Found> {
        val found = mutableListOf<Found>()
        fun addDir(dir: File?, location: Location) {
            dir?.listFiles()
                ?.filter { it.isFile && it.isBackup() && it.length() > 0 }
                ?.forEach { found += Found(it.name, Uri.fromFile(it), it.length(), it.lastModified(), location) }
        }
        addDir(appBackupDir(context), Location.APP_FOLDER)
        addDir(receivedDir(context), Location.RECEIVED)
        runCatching {
            @Suppress("DEPRECATION")
            addDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Location.DOWNLOADS)
        }
        runCatching {
            Injekt.get<StorageManager>().getAutomaticBackupsDirectory()
                ?.listFiles()
                ?.filter { it.isFile && it.name?.isBackupName() == true }
                ?.forEach { file ->
                    found += Found(
                        name = file.name.orEmpty(),
                        uri = file.uri,
                        sizeBytes = file.length(),
                        modified = file.lastModified(),
                        location = Location.STORAGE_FOLDER,
                    )
                }
        }
        return found
            .distinctBy { it.uri.toString() }
            .sortedByDescending { it.modified }
    }

    private fun File.isBackup() = name.isBackupName()

    private fun String.isBackupName() = endsWith(".tachibk") || endsWith(".proto.gz")
}
