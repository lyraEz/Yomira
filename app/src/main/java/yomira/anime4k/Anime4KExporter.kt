package yomira.anime4k

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToLong

/** What the exporter needs to know about the source video. */
data class SourceInfo(
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val hasAudio: Boolean,
    val audioBytesEstimate: Long,
) {
    val frames: Long get() = (durationUs / 1_000_000.0 * frameRate).roundToLong().coerceAtLeast(1)
}

data class ExportSettings(
    val preset: Enhancer,
    val output: OutputSize,
    val codec: OutputCodec,
    val quality: ExportQuality,
) {
    /** The output size actually used: an upscale-only enhancer turns "Original" into 1080p. */
    val outputSize: OutputSize get() = output.forEnhancer(preset)
}

/** Why processing stopped between two pieces. */
class ExportPaused(val reason: String) : Exception(reason)

class ExportFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Turns a video file into an enhanced version (Anime4K or light AI), a piece at a time:
 *  1. each 20-second piece is decoded, run through the preset on the GPU and encoded by Media3,
 *  2. finished pieces stay on disk, so stopping loses at most the piece in progress,
 *  3. ffmpeg joins the pieces and copies the original audio, subtitles and fonts without re-encoding,
 *  4. the result is checked (streams and length) before it replaces anything.
 */
@OptIn(UnstableApi::class)
class Anime4KExporter(private val context: Context) {

    interface Listener {
        /** Frames done out of the total, the smoothed seconds per frame, and the piece in progress. */
        fun onProgress(doneFrames: Long, totalFrames: Long, secondsPerFrame: Double?, segment: Int, segments: Int)

        fun onSegmentDone(index: Int, secondsPerFrame: Double?)

        fun onJoining()
    }

    fun probe(file: File): SourceInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video: MediaFormat? = null
            var audioBitrate = 0L
            var hasAudio = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && video == null) video = format
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioBitrate += if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    } else {
                        192_000L
                    }
                }
            }
            video ?: throw ExportFailed("no video track")
            val duration = if (video.containsKey(MediaFormat.KEY_DURATION)) video.getLong(MediaFormat.KEY_DURATION) else 0L
            val rate = if (video.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                runCatching { video.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
                    .getOrElse { video.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }
            } else {
                23.976
            }
            val probed = if (duration > 0) duration else ffprobeDurationUs(file)
            if (probed <= 0) throw ExportFailed("unknown duration")
            return SourceInfo(
                durationUs = probed,
                width = video.getInteger(MediaFormat.KEY_WIDTH),
                height = video.getInteger(MediaFormat.KEY_HEIGHT),
                frameRate = rate,
                hasAudio = hasAudio,
                audioBytesEstimate = audioBitrate / 8 * (probed / 1_000_000),
            )
        } finally {
            extractor.release()
        }
    }

    /**
     * Processes [source] into [workDir]/enhanced.mkv. [done] are pieces finished in an earlier run.
     * [pauseCheck] is asked between pieces and every few seconds; a reason stops the run cleanly.
     */
    suspend fun export(
        source: File,
        info: SourceInfo,
        workDir: File,
        settings: ExportSettings,
        done: Set<Int>,
        listener: Listener,
        eta: EtaTracker,
        pauseCheck: () -> String?,
        throttle: () -> Long,
    ): File {
        workDir.mkdirs()
        val segments = SegmentPlan.of(info.durationUs)
        val shaderSources = settings.preset.load(context)
        val (outWidth, outHeight) = settings.outputSize.resolve(info.width, info.height)
        val bitrate = settings.quality.bitrate(outWidth, outHeight, info.frameRate, settings.codec)
        val framesPerUs = info.frameRate / 1_000_000.0
        val finished = done.filter { segmentFile(workDir, it).length() > 0 }.toMutableSet()

        for (segment in segments) {
            if (segment.index in finished) continue
            pauseCheck()?.let { throw ExportPaused(it) }
            val doneFrames = finished.sumOf { (segments[it].durationUs * framesPerUs).roundToLong() }
            val target = segmentFile(workDir, segment.index)
            target.delete()
            var segmentFrames = 0L
            val effect = Anime4KEffect(
                sources = shaderSources,
                output = settings.outputSize,
                listener = FrameListener { _, seconds ->
                    eta.add(seconds)
                    segmentFrames++
                    listener.onProgress(doneFrames + segmentFrames, info.frames, eta.secondsPerFrame, segment.index, segments.size)
                },
                throttle = throttle,
            )
            try {
                runSegment(source, segment, target, effect, settings.codec, bitrate, pauseCheck)
            } catch (e: Throwable) {
                target.delete()
                throw e
            }
            if (target.length() <= 0) throw ExportFailed("empty piece ${segment.index}")
            finished += segment.index
            listener.onSegmentDone(segment.index, eta.secondsPerFrame)
        }

        listener.onJoining()
        val output = File(workDir, ENHANCED_NAME)
        join(source, workDir, segments, output)
        validate(output, info)
        return output
    }

    private suspend fun runSegment(
        source: File,
        segment: Segment,
        target: File,
        effect: Anime4KEffect,
        codec: OutputCodec,
        bitrate: Int,
        pauseCheck: () -> String?,
    ) = coroutineScope {
        val main = Handler(Looper.getMainLooper())
        var transformer: Transformer? = null
        // Stops the piece early when the phone overheats or the user pauses.
        val watcher = launch {
            while (isActive) {
                delay(5_000)
                if (pauseCheck() != null) {
                    main.post { transformer?.cancel() }
                    break
                }
            }
        }
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val built = Transformer.Builder(context)
                        .setVideoMimeType(codec.mimeType)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                                .setEnableFallback(true)
                                .build(),
                        )
                        // Keep the source's own gamma: Anime4K was trained on what mpv shows, not linear light.
                        .setVideoFrameProcessorFactory(
                            DefaultVideoFrameProcessor.Factory.Builder()
                                .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
                                .build(),
                        )
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (!continuation.isActive) return
                                val reason = pauseCheck()
                                continuation.resumeWithException(
                                    if (reason != null) ExportPaused(reason) else ExportFailed(exportException.errorCodeName, exportException),
                                )
                            }
                        })
                        .build()
                    transformer = built
                    val item = MediaItem.Builder()
                        .setUri(Uri.fromFile(source))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionUs(segment.startUs)
                                .setEndPositionUs(segment.endUs)
                                .build(),
                        )
                        .build()
                    val edited = EditedMediaItem.Builder(item)
                        .setRemoveAudio(true)
                        .setEffects(Effects(emptyList(), listOf(effect)))
                        .build()
                    built.start(edited, target.absolutePath)
                    continuation.invokeOnCancellation { main.post { built.cancel() } }
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    /** Joins the pieces and copies audio, subtitles, fonts and chapters from the original. */
    private suspend fun join(source: File, workDir: File, segments: List<Segment>, output: File) = withContext(Dispatchers.IO) {
        val list = File(workDir, "pieces.ffconcat")
        list.writeText(SegmentPlan.concatList(segments.map { segmentFile(workDir, it.index).absolutePath to it }))
        output.delete()
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-hide_banner",
                "-f", "concat", "-safe", "0", "-i", list.absolutePath,
                "-i", source.absolutePath,
                "-map", "0:v:0", "-map", "1:a?", "-map", "1:s?", "-map", "1:t?",
                "-map_metadata", "1", "-map_chapters", "1",
                "-c", "copy",
                output.absolutePath,
            ),
        )
        if (!ReturnCode.isSuccess(session.returnCode) || output.length() <= 0) {
            output.delete()
            throw ExportFailed("join failed: " + session.allLogsAsString.takeLast(400))
        }
    }

    /** The joined file must have a video stream, the original audio and about the same length. */
    private fun validate(output: File, info: SourceInfo) {
        val session = FFprobeKit.getMediaInformation(output.absolutePath)
        val media = session.mediaInformation ?: throw ExportFailed("unreadable result")
        val streams = media.streams.orEmpty()
        if (streams.none { it.type == "video" }) throw ExportFailed("result has no video")
        if (info.hasAudio && streams.none { it.type == "audio" }) throw ExportFailed("result lost the audio")
        val durationUs = (media.duration?.toDoubleOrNull() ?: 0.0) * 1_000_000
        if (abs(durationUs - info.durationUs) > 2_000_000) {
            throw ExportFailed("result is ${durationUs / 1e6}s long, the original ${info.durationUs / 1e6}s")
        }
    }

    private fun ffprobeDurationUs(file: File): Long =
        (FFprobeKit.getMediaInformation(file.absolutePath).mediaInformation?.duration?.toDoubleOrNull() ?: 0.0)
            .times(1_000_000).roundToLong()

    companion object {
        const val ENHANCED_NAME = "enhanced.mkv"

        fun segmentFile(workDir: File, index: Int) = File(workDir, "piece_%05d.mp4".format(index))
    }
}
