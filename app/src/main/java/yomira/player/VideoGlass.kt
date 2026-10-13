package yomira.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.isActive
import tachiyomi.presentation.core.util.collectAsState
import yomira.glass.GlassQuality
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle

/**
 * Live glass over the video. mpv draws into a SurfaceView, which no Compose layer can sample, so
 * while [active] (controls on screen) a tiny copy of the current frame (about 1/12 of the
 * resolution) is taken with PixelCopy on the GPU, in step with the display (one copy per frame at
 * most, never two in flight), and recorded as the backdrop that the player's glass controls blur
 * and bend. The blur hides the low resolution; the video decoder and its surface are never
 * touched, and nothing runs while the controls are hidden.
 */
@Composable
fun VideoGlassBackdrop(
    surfaceView: SurfaceView,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val style = LocalGlassStyle.current
    if (!style.liveBlur) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, content = content)
        return
    }
    val prefs = remember { SmoothnessPreferences() }
    val rate by prefs.videoGlassRate().collectAsState()
    val frames = remember { FrameBuffers() }
    val version = remember { mutableIntStateOf(0) }
    // On screen the source box draws nothing (the real video shows through); its recording, which
    // the glass samples, is the latest frame copy stretched to the screen.
    val backdrop = rememberLayerBackdrop {
        version.intValue
        val bitmap = frames.current ?: return@rememberLayerBackdrop
        drawImage(
            image = bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }

    DisposableEffect(Unit) {
        onDispose { frames.release() }
    }
    LaunchedEffect(active, rate, style.quality) {
        if (!active) return@LaunchedEffect
        val fps = when {
            rate > 0 -> rate
            rate == 0 -> 0
            // Automatic: every display frame, except on the battery tier.
            style.quality >= GlassQuality.BALANCED -> 0
            else -> 30
        }
        val minGapNanos = if (fps <= 0) 0L else 1_000_000_000L / fps - 2_000_000L
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (!frames.busy && now - last >= minGapNanos) {
                    last = now
                    frames.request(surfaceView) { version.intValue++ }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
    }
}

/**
 * Triple-buffered frame copies: PixelCopy writes into a buffer the renderer is not using, and a
 * finished copy becomes the front buffer on the main thread.
 */
private class FrameBuffers {
    private val thread = HandlerThread("yomira-video-glass").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var buffers: Array<Bitmap>? = null
    private var front = 0

    @Volatile
    var busy = false
        private set

    val current: Bitmap? get() = buffers?.get(front)

    /** Starts a copy unless one is already running; [onCopied] runs on the main thread. */
    fun request(view: SurfaceView, onCopied: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || busy) return
        val holder = view.holder ?: return
        if (!holder.surface.isValid || view.width <= 0 || view.height <= 0) return
        val width = (view.width / 12).coerceIn(32, 192)
        val height = (view.height / 12).coerceIn(18, 192)
        val set = buffers?.takeIf { it[0].width == width && it[0].height == height }
            ?: Array(3) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.also { buffers = it }
        val target = (front + 1) % set.size
        busy = true
        runCatching {
            PixelCopy.request(
                view,
                set[target],
                { result ->
                    main.post {
                        busy = false
                        if (result == PixelCopy.SUCCESS && buffers === set) {
                            front = target
                            onCopied()
                        }
                    }
                },
                handler,
            )
        }.onFailure { busy = false }
    }

    fun release() {
        thread.quitSafely()
    }
}
