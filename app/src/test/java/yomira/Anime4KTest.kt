package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yomira.anime4k.Anime4KMode
import yomira.anime4k.Anime4KPreset
import yomira.anime4k.Anime4KQuality
import yomira.anime4k.EnhanceJob
import yomira.anime4k.EnhancedDownloads
import yomira.anime4k.EtaTracker
import yomira.anime4k.ExportQuality
import yomira.anime4k.OutputCodec
import yomira.anime4k.OutputSize
import yomira.anime4k.SegmentPlan
import java.io.File

class Anime4KTest {

    private val pre = listOf("AutoDownscalePre_x2", "AutoDownscalePre_x4")

    @Test
    fun `presets match the official mpv lists`() {
        // bloc97/Anime4K md/GLSL_Instructions_Mac.md, CTRL+1..6 (Fast and HQ).
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_M", "Upscale_CNN_x2_M") + pre + "Upscale_CNN_x2_S",
            Anime4KPreset(Anime4KMode.A, Anime4KQuality.FAST).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_VL", "Upscale_CNN_x2_VL", "Restore_CNN_M") + pre + "Upscale_CNN_x2_M",
            Anime4KPreset(Anime4KMode.AA, Anime4KQuality.HIGH).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_Soft_VL", "Upscale_CNN_x2_VL") + pre + listOf("Restore_CNN_Soft_M", "Upscale_CNN_x2_M"),
            Anime4KPreset(Anime4KMode.BB, Anime4KQuality.HIGH).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Upscale_Denoise_CNN_x2_M") + pre + listOf("Restore_CNN_S", "Upscale_CNN_x2_S"),
            Anime4KPreset(Anime4KMode.CA, Anime4KQuality.FAST).shaders,
        )
        assertEquals(12, Anime4KPreset.ALL.size)
        assertEquals(Anime4KPreset(Anime4KMode.AA, Anime4KQuality.HIGH), Anime4KPreset.fromId("AA_HQ"))
    }

    @Test
    fun `every shader a preset needs is in the APK, unmodified`() {
        val assets = File("src/main/assets/anime4k")
        for (preset in Anime4KPreset.ALL) {
            for (name in preset.shaders) {
                val file = File(assets, "Anime4K_$name.glsl")
                assertTrue(file.exists(), "missing ${file.name}")
                // The official files run in highp; the player's tuned copies use mediump.
                assertTrue("precision mediump" !in file.readText(), "${file.name} is not the official file")
            }
        }
    }

    @Test
    fun `output size keeps the aspect, stays even and never shrinks`() {
        assertEquals(1280 to 720, OutputSize.ORIGINAL.resolve(1280, 720))
        assertEquals(1920 to 1080, OutputSize.P1080.resolve(1280, 720))
        assertEquals(1920 to 1080, OutputSize.P1080.resolve(1920, 1080))
        assertEquals(2560 to 1440, OutputSize.DOUBLE.resolve(1280, 720))
        assertEquals(3840 to 2160, OutputSize.DOUBLE.resolve(1920, 1080))
        // Capped at 2160p.
        assertEquals(3840 to 2160, OutputSize.DOUBLE.resolve(2560, 1440).let { it })
        assertEquals(1440 to 1080, OutputSize.P1080.resolve(640, 480))
        val (w, h) = OutputSize.P1440.resolve(853, 480)
        assertEquals(0, w % 2)
        assertEquals(1440, h)
    }

    @Test
    fun `bitrate follows size, frame rate and codec`() {
        val avc = ExportQuality.HIGH.bitrate(1920, 1080, 23.976, OutputCodec.AVC)
        val hevc = ExportQuality.HIGH.bitrate(1920, 1080, 23.976, OutputCodec.HEVC)
        assertTrue(avc in 7_500_000..8_500_000, "avc $avc")
        assertTrue(hevc < avc)
        assertTrue(ExportQuality.MAXIMUM.bitrate(1920, 1080, 24.0, OutputCodec.HEVC) > hevc)
        // Unknown frame rate falls back to 24.
        assertEquals(ExportQuality.HIGH.bitrate(1280, 720, 24.0, OutputCodec.AVC), ExportQuality.HIGH.bitrate(1280, 720, 0.0, OutputCodec.AVC))
    }

    @Test
    fun `segments cover the whole video and the concat list keeps exact durations`() {
        val segments = SegmentPlan.of(65_500_000L)
        assertEquals(4, segments.size)
        assertEquals(0L, segments.first().startUs)
        assertEquals(65_500_000L, segments.last().endUs)
        assertEquals(5_500_000L, segments.last().durationUs)
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.endUs, b.startUs) }
        val list = SegmentPlan.concatList(listOf("/x/piece_00000.mp4" to segments[0], "/x/it's.mp4" to segments[3]))
        assertTrue("duration 20.000000" in list)
        assertTrue("duration 5.500000" in list)
        assertTrue("file '/x/it'\\''s.mp4'" in list)
        assertTrue(SegmentPlan.of(0).isEmpty())
    }

    @Test
    fun `time estimate smooths frame times`() {
        val eta = EtaTracker(smoothing = 0.5)
        eta.add(2.0)
        eta.add(4.0)
        assertEquals(3.0, eta.secondsPerFrame)
        assertEquals(300L, eta.remainingSeconds(100))
        eta.add(-1.0)
        assertEquals(3.0, eta.secondsPerFrame)
    }

    @Test
    fun `results are told apart by episode, source, preset and output`() {
        val base = EnhanceJob.cacheKey(1, "/ep/1", "AA_HQ", "original", "hevc", "high")
        assertEquals(base, EnhanceJob.cacheKey(1, "/ep/1", "AA_HQ", "original", "hevc", "high"))
        assertNotEquals(base, EnhanceJob.cacheKey(2, "/ep/1", "AA_HQ", "original", "hevc", "high"))
        assertNotEquals(base, EnhanceJob.cacheKey(1, "/ep/2", "AA_HQ", "original", "hevc", "high"))
        assertNotEquals(base, EnhanceJob.cacheKey(1, "/ep/1", "A_FAST", "original", "hevc", "high"))
        assertNotEquals(base, EnhanceJob.cacheKey(1, "/ep/1", "AA_HQ", "2x", "hevc", "high"))
    }

    @Test
    fun `downloads play the Anime4K file first`() {
        assertTrue(EnhancedDownloads.isEnhanced("Episode 1.anime4k.mkv"))
        assertTrue(!EnhancedDownloads.isEnhanced("Episode 1.mkv"))
        val files = listOf("Episode 1.mkv", "Episode 1.anime4k.mkv", "Episode 1.srt")
        assertEquals("Episode 1.anime4k.mkv", EnhancedDownloads.preferEnhanced(files) { it }.first())
        assertEquals("Episode 1.mkv", EnhancedDownloads.preferEnhanced(listOf("Episode 1.mkv")) { it }.first())
    }
}
