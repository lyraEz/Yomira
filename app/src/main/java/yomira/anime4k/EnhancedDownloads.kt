package yomira.anime4k

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * Enhanced Downloads: an episode downloaded as "Anime4K Enhanced" is processed after the download
 * finishes, and the result goes into the same episode folder as "<name>.anime4k.mkv".
 */
object EnhancedDownloads {

    const val ENHANCED_SUFFIX = ".anime4k"

    private fun store(): PreferenceStore = Injekt.get()

    /** Episode ids waiting for their download to finish, with the preset chosen for them. */
    private fun requests() = store().getStringSet("yomira_a4k_requests", emptySet())

    /** Remembers the choice made when the download was started. */
    fun request(episodeIds: Collection<Long>, preset: Anime4KPreset) {
        val ids = episodeIds.map { it.toString() }.toSet()
        val others = requests().get().filterNot { it.substringBefore('=') in ids }
        requests().set(others.toSet() + ids.map { "$it=${preset.id}" })
    }

    fun forget(episodeId: Long) {
        requests().set(requests().get().filterNot { it.substringBefore('=') == episodeId.toString() }.toSet())
    }

    private fun requested(episodeId: Long): Anime4KPreset? = requests().get()
        .firstOrNull { it.substringBefore('=') == episodeId.toString() }
        ?.let { Anime4KPreset.fromId(it.substringAfter('=')) }

    /** Called by the downloader when an episode is in its folder. Queues the processing if asked. */
    fun onDownloaded(context: Context, anime: Anime, episode: Episode, episodeDir: UniFile?) {
        val prefs = Anime4KPreferences()
        val preset = requested(episode.id)
            ?: Anime4KPreset.fromId(prefs.downloadPreset().get())?.takeIf {
                prefs.enhancedDownloads().get() && prefs.downloadChoice().get() == "always"
            }
            ?: return
        forget(episode.id)
        val dir = episodeDir ?: return
        val video = originalVideo(dir) ?: return
        val settings = prefs.settings(preset.id)
        val job = EnhanceJob(
            id = EnhanceJobs.newId(),
            kind = JobKind.DOWNLOAD,
            animeId = anime.id,
            episodeId = episode.id,
            sourceId = anime.source,
            episodeUrl = episode.url,
            animeTitle = anime.title,
            episodeName = episode.name,
            presetId = preset.id,
            output = settings.output.key,
            codec = settings.codec.key,
            quality = settings.quality.key,
            sourceUri = video.uri.toString(),
            targetDir = dir.uri.toString(),
            videoBaseName = video.name.orEmpty().substringBeforeLast('.'),
        )
        EnhanceJobs.add(context, job)
        Anime4KQueue.kick(context)
    }

    /** The downloaded video that is not an Anime4K result. */
    fun originalVideo(dir: UniFile): UniFile? = dir.listFiles().orEmpty()
        .filter { isVideo(it.name) && !isEnhanced(it.name) }
        .maxByOrNull { it.length() }

    fun isVideo(name: String?): Boolean {
        val lower = name.orEmpty().lowercase()
        return lower.endsWith(".mkv") || lower.endsWith(".mp4")
    }

    fun isEnhanced(name: String?): Boolean = name.orEmpty().lowercase().contains("$ENHANCED_SUFFIX.")

    /** Puts the result next to the original, then removes the original if the settings say so. */
    fun deliver(context: Context, job: EnhanceJob, output: File) {
        val dir = job.targetDir?.let { UniFile.fromUri(context, Uri.parse(it)) }
            ?.takeIf { it.exists() } ?: throw IOException("the download folder is gone")
        val base = job.videoBaseName ?: "episode"
        val name = "$base$ENHANCED_SUFFIX.mkv"
        dir.findFile(name)?.delete()
        val target = dir.createFile(name) ?: throw IOException("could not create $name")
        try {
            output.inputStream().use { from -> target.openOutputStream().use { to -> from.copyTo(to, 1 shl 20) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        if (target.length() != output.length()) {
            target.delete()
            throw IOException("copy of the result is incomplete")
        }
        output.delete()
        if (!Anime4KPreferences().keepOriginal().get()) {
            val original = job.sourceUri?.let { uri -> dir.listFiles().orEmpty().firstOrNull { it.uri.toString() == uri } }
            if (original != null && !isEnhanced(original.name)) {
                original.delete()
                logcat(LogPriority.INFO) { "Anime4K: replaced the original of ${job.episodeName}" }
            }
        }
    }

    /** Order for picking the file to play: the Anime4K version first. */
    fun <T> preferEnhanced(files: List<T>, name: (T) -> String?): List<T> =
        files.sortedBy { if (isEnhanced(name(it))) 0 else 1 }
}
