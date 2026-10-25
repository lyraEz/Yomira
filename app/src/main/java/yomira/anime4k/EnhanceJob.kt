package yomira.anime4k

import kotlinx.serialization.Serializable

/** What happens to a job's result: it goes into the download, or into the Smart cache. */
@Serializable
enum class JobKind { DOWNLOAD, SMART }

@Serializable
enum class JobState {
    /** Waiting for its turn. */
    QUEUED,

    /** Getting the episode (Smart). */
    DOWNLOADING,

    /** Pieces being processed. */
    PROCESSING,

    /** Stopped by the phone (heat, battery, charger, player open); continues by itself. */
    WAITING,

    /** Stopped by the user. */
    PAUSED,

    /** Joining the pieces with the audio and subtitles. */
    JOINING,
    DONE,
    FAILED,
    ;

    val isActive: Boolean get() = this == QUEUED || this == DOWNLOADING || this == PROCESSING || this == WAITING || this == JOINING
}

/**
 * One video to process. Saved as JSON after every change, so a job survives the app being closed;
 * the pieces in [workDir][EnhanceJobs.workDir] make it resume where it stopped.
 */
@Serializable
data class EnhanceJob(
    val id: String,
    val kind: JobKind,
    val animeId: Long,
    val episodeId: Long,
    val sourceId: Long,
    /** Episode URL in its source: with the source id and preset it identifies a result. */
    val episodeUrl: String,
    val animeTitle: String,
    val episodeName: String,
    val presetId: String,
    val output: String,
    val codec: String,
    val quality: String,
    /** Original video (DOWNLOAD: the downloaded file; SMART: filled once downloaded). */
    val sourceUri: String? = null,
    /** Episode folder of the download that receives the result. */
    val targetDir: String? = null,
    /** Name of the downloaded video without extension. */
    val videoBaseName: String? = null,
    /** SMART: stream to download, with its headers. */
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val state: JobState = JobState.QUEUED,
    val doneFrames: Long = 0,
    val totalFrames: Long = 0,
    val doneSegments: List<Int> = emptyList(),
    val totalSegments: Int = 0,
    val secondsPerFrame: Double? = null,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (totalFrames > 0) (doneFrames.toFloat() / totalFrames).coerceIn(0f, 1f) else 0f

    val remainingSeconds: Long? get() = secondsPerFrame?.let { ((totalFrames - doneFrames).coerceAtLeast(0) * it).toLong() }

    val preset: Anime4KPreset get() = Anime4KPreset.fromId(presetId) ?: Anime4KPreset.DEFAULT

    val settings: ExportSettings get() = ExportSettings(
        preset = preset,
        output = OutputSize.fromKey(output),
        codec = OutputCodec.fromKey(codec),
        quality = ExportQuality.fromKey(quality),
    )

    /** Same episode, source, preset, output and engine: the same result. */
    val resultKey: String get() = cacheKey(sourceId, episodeUrl, presetId, output, codec, quality)

    companion object {
        fun cacheKey(sourceId: Long, episodeUrl: String, presetId: String, output: String, codec: String, quality: String): String {
            val raw = listOf(sourceId, episodeUrl, presetId, output, codec, quality, Anime4KPreset.ENGINE_VERSION).joinToString("|")
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
