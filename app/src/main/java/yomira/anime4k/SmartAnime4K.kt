package yomira.anime4k

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Smart Anime4K: when an episode opens, a prepared Anime4K version is used if there is one;
 * otherwise the episode is prepared in the background (it keeps playing the original meanwhile).
 * The results live in a cache with a size limit; the least recently used go first.
 */
object SmartAnime4K {

    fun key(anime: Anime, episode: Episode, prefs: Anime4KPreferences = Anime4KPreferences()): String {
        val settings = prefs.settings(prefs.smartPreset().get())
        return EnhanceJob.cacheKey(anime.source, episode.url, settings.preset.id, settings.output.key, settings.codec.key, settings.quality.key)
    }

    private fun file(context: Context, key: String) = File(EnhanceJobs.smartDir(context), "$key.mkv")

    /** The prepared file for this episode with the current Smart settings, if any. */
    fun cached(context: Context, anime: Anime, episode: Episode): File? {
        val prefs = Anime4KPreferences()
        if (!prefs.smartEnabled().get()) return null
        return file(context, key(anime, episode, prefs)).takeIf { it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }
    }

    /** The job preparing this episode, if one is queued or running. */
    fun pendingJob(anime: Anime, episode: Episode): EnhanceJob? {
        val key = key(anime, episode)
        return EnhanceJobs.jobs.value.firstOrNull { it.kind == JobKind.SMART && it.resultKey == key && it.episodeId == episode.id }
    }

    /**
     * The player opened [episode]. Queues it (and the next one, if set) unless it is prepared or
     * being prepared already. [video] is what the player is streaming, reused for the download.
     */
    fun onEpisodeOpened(context: Context, anime: Anime, episode: Episode, video: Video?, next: Episode?) {
        val prefs = Anime4KPreferences()
        if (!prefs.smartEnabled().get()) return
        if (prefs.smartCurrent().get() && cached(context, anime, episode) == null) {
            queue(context, anime, episode, video?.takeIf { it.videoUrl.startsWith("http") && !it.usesHttpServer() })
        }
        if (prefs.smartNext().get() && next != null && cached(context, anime, next) == null) {
            queue(context, anime, next, null)
        }
        Anime4KQueue.kick(context)
    }

    private fun queue(context: Context, anime: Anime, episode: Episode, video: Video?) {
        val prefs = Anime4KPreferences()
        val settings = prefs.settings(prefs.smartPreset().get())
        EnhanceJobs.add(
            context,
            EnhanceJob(
                id = EnhanceJobs.newId(),
                kind = JobKind.SMART,
                animeId = anime.id,
                episodeId = episode.id,
                sourceId = anime.source,
                episodeUrl = episode.url,
                animeTitle = anime.title,
                episodeName = episode.name,
                presetId = settings.preset.id,
                output = settings.output.key,
                codec = settings.codec.key,
                quality = settings.quality.key,
                streamUrl = video?.videoUrl,
                streamHeaders = video?.headers?.toMultimap()?.mapValues { it.value.joinToString(", ") }.orEmpty(),
            ),
        )
    }

    /** Finds a stream for a queued episode (the next one, or when the saved address expired). */
    suspend fun resolveStream(anime: Anime, episode: Episode): Pair<String, Map<String, String>>? {
        val source = Injekt.get<SourceManager>().get(anime.source) as? AnimeSource ?: return null
        val hosters = EpisodeLoader.getHosters(episode, anime, source, allowDownloaded = false)
        val video = HosterLoader.getBestVideo(source, hosters) ?: return null
        if (!video.videoUrl.startsWith("http") || video.usesHttpServer()) return null
        return video.videoUrl to video.headers?.toMultimap()?.mapValues { it.value.joinToString(", ") }.orEmpty()
    }

    /** Moves a finished result into the cache and trims it to the size limit. */
    fun store(context: Context, job: EnhanceJob, output: File) {
        val target = file(context, job.resultKey)
        target.delete()
        if (!output.renameTo(target)) {
            output.copyTo(target, overwrite = true)
            output.delete()
        }
        trim(context, keep = target)
    }

    fun trim(context: Context, keep: File? = null) {
        val limit = Anime4KPreferences().smartCacheGb().get().toLong() * 1024 * 1024 * 1024
        val files = EnhanceJobs.smartDir(context).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= limit) break
            if (file == keep) continue
            total -= file.length()
            file.delete()
        }
    }

    fun clear(context: Context) {
        EnhanceJobs.smartDir(context).deleteRecursively()
    }

    fun cacheBytes(context: Context): Long = EnhanceJobs.smartDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
