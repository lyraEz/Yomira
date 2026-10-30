package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yomira.anime4k.Anime4KMode
import yomira.anime4k.Anime4KPreset
import yomira.anime4k.EnhanceJob
import yomira.anime4k.EnhanceQuality
import yomira.anime4k.EnhancedDownloads
import yomira.anime4k.Enhancer
import yomira.anime4k.EtaTracker
import yomira.anime4k.ExportQuality
import yomira.anime4k.LightAiLook
import yomira.anime4k.LightAiPreset
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
            Anime4KPreset(Anime4KMode.A, EnhanceQuality.FAST).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_VL", "Upscale_CNN_x2_VL", "Restore_CNN_M") + pre + "Upscale_CNN_x2_M",
            Anime4KPreset(Anime4KMode.AA, EnhanceQuality.HIGH).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_Soft_VL", "Upscale_CNN_x2_VL") + pre + listOf("Restore_CNN_Soft_M", "Upscale_CNN_x2_M"),
            Anime4KPreset(Anime4KMode.BB, EnhanceQuality.HIGH).shaders,
        )
        assertEquals(
            listOf("Clamp_Highlights", "Upscale_Denoise_CNN_x2_M") + pre + listOf("Restore_CNN_S", "Upscale_CNN_x2_S"),
            Anime4KPreset(Anime4KMode.CA, EnhanceQuality.FAST).shaders,
        )
        // Lite: the small networks only.
        assertEquals(
            listOf("Clamp_Highlights", "Restore_CNN_S", "Upscale_CNN_x2_S") + pre + "Upscale_CNN_x2_S",
            Anime4KPreset(Anime4KMode.A, EnhanceQuality.LITE).shaders,
        )
        assertEquals(18, Anime4KPreset.ALL.size)
        // Ids saved by older versions still resolve.
        assertEquals(Anime4KPreset(Anime4KMode.AA, EnhanceQuality.HIGH), Enhancer.fromId("AA_HQ"))
        assertEquals(Anime4KPreset(Anime4KMode.A, EnhanceQuality.FAST), Enhancer.fromId("A_FAST"))
    }

    @Test
    fun `light AI presets use the CuNNy files and only upscale`() {
        assertEquals(listOf("lightai/CuNNy-faster-DS.glsl"), LightAiPreset(LightAiLook.SHARP, EnhanceQuality.LITE).assets)
        assertEquals(listOf("lightai/CuNNy-2x12-SOFT.glsl"), LightAiPreset(LightAiLook.SOFT, EnhanceQuality.HIGH).assets)
        assertEquals(LightAiPreset(LightAiLook.SOFT, EnhanceQuality.FAST), Enhancer.fromId("AI_SOFT_FAST"))
        assertEquals(Enhancer.ALL.size, Enhancer.ALL.map { it.id }.toSet().size)
        assertTrue(LightAiPreset.ALL.all { it.upscaleOnly })
        assertTrue(Anime4KPreset(Anime4KMode.C, EnhanceQuality.FAST).upscaleOnly)
        assertTrue(!Anime4KPreset(Anime4KMode.A, EnhanceQuality.FAST).upscaleOnly)
        // "Original" means 1080p for an upscaler, and stays the source size for Anime4K A.
        val sharp = LightAiPreset(LightAiLook.SHARP, EnhanceQuality.LITE)
        assertEquals(OutputSize.P1080, OutputSize.ORIGINAL.forEnhancer(sharp))
        assertEquals(OutputSize.P1440, OutputSize.P1440.forEnhancer(sharp))
        assertEquals(OutputSize.ORIGINAL, OutputSize.ORIGINAL.forEnhancer(Enhancer.DEFAULT))
    }

    @Test
    fun `every light AI shader is in the APK with its license`() {
        val assets = File("src/main/assets")
        for (preset in LightAiPreset.ALL) {
            val file = File(assets, preset.assets.single())
            assertTrue(file.exists(), "missing ${file.name}")
            val text = file.readText()
            assertTrue("//!COMPUTE" in text && "//!HOOK LUMA" in text, "${file.name} is not a CuNNy shader")
            assertTrue("GNU Lesser General Public" in text, "${file.name} lost its license header")
        }
        assertTrue(File(assets, "lightai/LICENSE-CuNNy.txt").exists())
        assertTrue(File(assets, "lightai/LICENSE-GPL-3.0.txt").exists())
    }

    @Test
    fun `every shader a preset needs is in the APK, unmodified`() {
        val assets = File("src/main/assets/anime4k")
        for (preset in Anime4KPreset.ALL) {
            assertEquals(preset.shaders.map { "anime4k/Anime4K_$it.glsl" }, preset.assets)
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
