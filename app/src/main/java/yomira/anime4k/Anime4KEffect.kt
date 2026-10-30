package yomira.anime4k

import android.content.Context
import android.opengl.GLES30
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Told after every processed frame, with the time the GPU took for it. */
fun interface FrameListener {
    fun onFrame(presentationTimeUs: Long, seconds: Double)
}

/**
 * Media3 video effect that runs an enhancer (Anime4K or light AI shaders) on every frame. Media3
 * decodes the source into a texture, this effect draws the result into the next texture, and Media3
 * encodes it.
 */
@OptIn(UnstableApi::class)
class Anime4KEffect(
    private val sources: List<String>,
    private val output: OutputSize,
    private val listener: FrameListener? = null,
    private val throttle: () -> Long = { 0L },
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4KShaderProgram(sources, output, listener, throttle)
}

@OptIn(UnstableApi::class)
internal class Anime4KShaderProgram(
    private val sources: List<String>,
    private val output: OutputSize,
    private val listener: FrameListener?,
    private val throttle: () -> Long,
) : BaseGlShaderProgram(false, 1) {

    private var handle = 0L

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val (outWidth, outHeight) = output.resolve(inputWidth, inputHeight)
        try {
            if (handle == 0L) handle = Anime4KNative.create(sources)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
        Anime4KNative.configure(handle, inputWidth, inputHeight, outWidth, outHeight)?.let {
            throw VideoFrameProcessingException("Anime4K: $it")
        }
        return Size(outWidth, outHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val start = SystemClock.elapsedRealtimeNanos()
        // Each pass goes to the GPU on its own (no single long job for the watchdog to stop), and
        // only the frame as a whole is waited for.
        Anime4KNative.process(handle, inputTexId, -1, PassSync.FLUSH)?.let {
            throw VideoFrameProcessingException("Anime4K: $it")
        }
        GLES30.glFinish()
        val seconds = (SystemClock.elapsedRealtimeNanos() - start) / 1e9
        listener?.onFrame(presentationTimeUs, seconds)
        // Cooling down: rest between frames when the phone is warm.
        val pause = throttle()
        if (pause > 0) Thread.sleep(pause)
    }

    override fun release() {
        super.release()
        if (handle != 0L) {
            Anime4KNative.releaseGl(handle)
            Anime4KNative.destroy(handle)
            handle = 0L
        }
    }
}
