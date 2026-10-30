package yomira.anime4k

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES30
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BenchmarkResult(
    val preset: Enhancer,
    val width: Int,
    val height: Int,
    val outWidth: Int,
    val outHeight: Int,
    val secondsPerFrame: Double,
    val gflopsPerFrame: Double,
    val passes: Int,
    val setupSeconds: Double,
) {
    /** Hours for a 24-minute episode at 23.976 frames per second, at this speed. */
    val hoursPerEpisode: Double get() = secondsPerFrame * EPISODE_FRAMES / 3600.0

    /** How many times slower than real time (24 fps); below 1 would be real time. */
    val slowerThanRealTime: Double get() = secondsPerFrame * 23.976

    companion object {
        const val EPISODE_FRAMES = 24 * 60 * 23.976
    }
}

/**
 * Measures an enhancer on this phone's GPU with an offscreen frame of the given size. The shaders do
 * the same work whatever the picture, so a generated test pattern gives real timings.
 */
object Anime4KBenchmark {

    suspend fun run(
        context: Context,
        preset: Enhancer,
        width: Int,
        height: Int,
        output: OutputSize,
        frames: Int = 3,
    ): BenchmarkResult = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val sources = preset.load(context)
        val (outWidth, outHeight) = output.resolve(width, height)
        val egl = OffscreenGl()
        try {
            egl.makeCurrent()
            val setupStart = SystemClock.elapsedRealtimeNanos()
            val handle = Anime4KNative.create(sources)
            try {
                Anime4KNative.configure(handle, width, height, outWidth, outHeight)?.let { error(it) }
                val input = texture(width, height, pattern(width, height))
                val target = texture(outWidth, outHeight, null)
                val fbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, target, 0)
                // The first frame also pays for shader compilation.
                Anime4KNative.process(handle, input, fbo, PassSync.FLUSH)?.let { error(it) }
                GLES30.glFinish()
                val setup = (SystemClock.elapsedRealtimeNanos() - setupStart) / 1e9
                var best = Double.MAX_VALUE
                repeat(frames.coerceAtLeast(1)) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    Anime4KNative.process(handle, input, fbo, PassSync.FLUSH)?.let { error(it) }
                    GLES30.glFinish()
                    best = minOf(best, (SystemClock.elapsedRealtimeNanos() - start) / 1e9)
                }
                GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                GLES30.glDeleteTextures(2, intArrayOf(input, target), 0)
                BenchmarkResult(
                    preset = preset,
                    width = width,
                    height = height,
                    outWidth = outWidth,
                    outHeight = outHeight,
                    secondsPerFrame = best,
                    gflopsPerFrame = Anime4KNative.flopsPerFrame(handle) / 1e9,
                    passes = Anime4KNative.passCount(handle),
                    setupSeconds = setup,
                )
            } finally {
                Anime4KNative.releaseGl(handle)
                Anime4KNative.destroy(handle)
            }
        } finally {
            egl.release()
        }
    }

    /** Lines, gradients and flat areas, like drawn animation. */
    private fun pattern(width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until height) {
            for (x in 0 until width) {
                val line = (x + y) % 37 < 2 || (x * 3 + y) % 91 < 2
                val r = if (line) 20 else 120 + (x * 100 / width)
                val g = if (line) 20 else 90 + (y * 120 / height)
                val b = if (line) 30 else 160
                buffer.put(r.toByte()).put(g.toByte()).put(b.toByte()).put(255.toByte())
            }
        }
        buffer.position(0)
        return buffer
    }

    private fun texture(width: Int, height: Int, pixels: ByteBuffer?): Int {
        val id = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        return id
    }
}

/** An OpenGL ES 3 context with a tiny offscreen surface, current on the calling thread. */
internal class OffscreenGl {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private val context: android.opengl.EGLContext
    private val surface: android.opengl.EGLSurface

    init {
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL not available" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "no OpenGL ES 3 configuration"
        }
        config = configs[0]!!
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "no OpenGL ES 3 context" }
        surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
    }

    fun makeCurrent() {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
    }
}
