package yomira.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import yomira.glass.GlassQuality
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle
import kotlin.coroutines.resume

/**
 * Live glass over the video. mpv draws into a SurfaceView, which no Compose layer can sample, so
 * while [active] (controls on screen) a tiny copy of the current frame (about 1/10 of the
 * resolution, at most 10 times a second) is taken with PixelCopy on the GPU and recorded as the
 * backdrop that the player's glass controls blur and bend. The blur hides the low resolution;
 * the video decoder and its surface are never touched, and nothing runs while the controls are
 * hidden.
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
    LaunchedEffect(active, style.quality) {
        if (!active) return@LaunchedEffect
        val interval = if (style.quality >= GlassQuality.BALANCED) 100L else 200L
        while (isActive) {
            if (frames.copy(surfaceView)) version.intValue++
            delay(interval)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
    }
}

/** Double-buffered frame copies so the renderer never reads a bitmap PixelCopy is writing. */
private class FrameBuffers {
    private val thread = HandlerThread("yomira-video-glass").apply { start() }
    private val handler = Handler(thread.looper)
    private var buffers: Array<Bitmap>? = null
    private var front = 0

    val current: Bitmap? get() = buffers?.get(front)

    suspend fun copy(view: SurfaceView): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val holder = view.holder ?: return false
        if (!holder.surface.isValid || view.width <= 0 || view.height <= 0) return false
        val width = (view.width / 10).coerceIn(32, 256)
        val height = (view.height / 10).coerceIn(18, 256)
        val pair = buffers?.takeIf { it[0].width == width && it[0].height == height }
            ?: Array(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.also { buffers = it }
        val back = 1 - front
        val ok = suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(view, pair[back], { result -> cont.resume(result == PixelCopy.SUCCESS) }, handler)
            }.onFailure { cont.resume(false) }
        }
        if (ok) front = back
        return ok
    }

    fun release() {
        thread.quitSafely()
    }
}
