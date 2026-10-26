package yomira.anime4k

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Output size of the processed video. */
enum class OutputSize(val key: String) {
    /** Same size as the source: Anime4K restores lines and artifacts without upscaling. */
    ORIGINAL("original"),

    /** Upscaled to 1080p when the source is smaller. */
    P1080("1080"),

    /** Upscaled to 1440p when the source is smaller. */
    P1440("1440"),

    /** Twice the source size (the upscaling networks at full strength). */
    DOUBLE("2x"),
    ;

    /** Width and height for a source, kept even (encoders need it) and never smaller than the source. */
    fun resolve(width: Int, height: Int, maxHeight: Int = MAX_HEIGHT): Pair<Int, Int> {
        val targetHeight = when (this) {
            ORIGINAL -> height
            P1080 -> max(height, 1080)
            P1440 -> max(height, 1440)
            DOUBLE -> height * 2
        }.let { min(it, max(height, maxHeight)) }
        val scale = targetHeight.toDouble() / height
        return even((width * scale).roundToInt()) to even(targetHeight)
    }

    companion object {
        const val MAX_HEIGHT = 2160

        fun fromKey(key: String?): OutputSize = entries.firstOrNull { it.key == key } ?: ORIGINAL

        private fun even(value: Int): Int = max(2, value - value % 2)
    }
}

/** Codec of the processed video. */
enum class OutputCodec(val key: String, val mimeType: String, val efficiency: Double) {
    HEVC("hevc", "video/hevc", 0.62),
    AVC("avc", "video/avc", 1.0),
    ;

    companion object {
        fun fromKey(key: String?): OutputCodec = entries.firstOrNull { it.key == key } ?: HEVC
    }
}

/**
 * Export quality as bits per pixel per frame for H.264; HEVC needs about 60% of it. Anime4K sharpens
 * lines and keeps fine detail, which low bitrates would blur away again.
 */
enum class ExportQuality(val key: String, val bitsPerPixel: Double) {
    BALANCED("balanced", 0.10),
    HIGH("high", 0.16),
    MAXIMUM("max", 0.26),
    ;

    fun bitrate(width: Int, height: Int, frameRate: Double, codec: OutputCodec): Int {
        val fps = if (frameRate in 1.0..240.0) frameRate else 24.0
        val bps = width.toDouble() * height * fps * bitsPerPixel * codec.efficiency
        return bps.roundToLong().coerceIn(1_000_000L, 80_000_000L).toInt()
    }

    companion object {
        fun fromKey(key: String?): ExportQuality = entries.firstOrNull { it.key == key } ?: HIGH
    }
}

/** One piece of the video processed (and resumed) on its own. */
data class Segment(val index: Int, val startUs: Long, val endUs: Long) {
    val durationUs: Long get() = endUs - startUs
}

object SegmentPlan {

    /** Long enough that starting the encoder and compiling shaders costs little; short enough to resume. */
    const val DEFAULT_SEGMENT_US = 20_000_000L

    fun of(durationUs: Long, segmentUs: Long = DEFAULT_SEGMENT_US): List<Segment> {
        if (durationUs <= 0) return emptyList()
        val count = ((durationUs + segmentUs - 1) / segmentUs).toInt()
        return List(count) { index ->
            val start = index * segmentUs
            Segment(index, start, min(durationUs, start + segmentUs))
        }
    }

    /** ffmpeg concat list with exact durations, so audio stays in sync across the pieces. */
    fun concatList(files: List<Pair<String, Segment>>): String = buildString {
        appendLine("ffconcat version 1.0")
        for ((path, segment) in files) {
            appendLine("file '${path.replace("'", "'\\''")}'")
            appendLine("duration ${"%.6f".format(java.util.Locale.ROOT, segment.durationUs / 1_000_000.0)}")
        }
    }
}

/** Seconds per frame seen so far, smoothed, and what that means for the rest of the work. */
class EtaTracker(private val smoothing: Double = 0.15) {

    var secondsPerFrame: Double? = null
        private set

    fun add(seconds: Double) {
        if (seconds <= 0 || seconds.isNaN()) return
        val current = secondsPerFrame
        secondsPerFrame = if (current == null) seconds else current + smoothing * (seconds - current)
    }

    fun remainingSeconds(framesLeft: Long): Long? = secondsPerFrame?.let { (it * framesLeft).roundToLong() }

    fun seed(value: Double?) {
        if (value != null && value > 0) secondsPerFrame = value
    }
}

/** Rough storage needs: the pieces plus the joined file, with room to spare. */
object StorageEstimate {
    fun bytesFor(durationUs: Long, bitrate: Int, audioBytes: Long): Long {
        val video = bitrate.toDouble() / 8 * (durationUs / 1_000_000.0)
        return ((video * 2.0) + audioBytes * 1.2 + 200L * 1024 * 1024).roundToLong()
    }
}
