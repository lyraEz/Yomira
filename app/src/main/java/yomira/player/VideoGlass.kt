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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import tachiyomi.presentation.core.util.collectAsState
import yomira.glass.GlassQuality
import yomira.glass.LocalGlassBackdrop
import yomira.glass.LocalGlassStyle

/**
 * Live glass over the video. mpv draws into a SurfaceView, which no Compose layer can sample, so
 * while [active] (controls on screen) a tiny copy of the current frame (about 1/16 of the
 * resolution) is taken with PixelCopy and recorded as the backdrop that the player's glass controls
 * sample. The copy is already soft, so the controls need no blur pass of their own.
 *
 * Cost control, so the video never drops frames because of the glass: copies run at a fixed rate
 * (30 per second by default, never two in flight), stop while the video is paused (one fresh copy
 * is taken when it pauses) and while the controls are hidden. "Off" disables the live copy entirely.
 */
@Composable
fun VideoGlassBackdrop(
    surfaceView: SurfaceView,
    active: Boolean,
    paused: Boolean,
    content: @Composable () -> Unit,
) {
    val style = LocalGlassStyle.current
    val prefs = remember { SmoothnessPreferences() }
    val userRate by prefs.videoGlassRate().collectAsState()
    val plan by PlaybackOptimizer.plan.collectAsState()
    // "Auto" follows the optimizer's plan for this phone and video.
    val rate = if (userRate == -1) plan?.glassRate ?: -1 else userRate
    val hot by ThermalGuard.hot.collectAsState()
    if (!style.liveBlur || rate == VideoGlassOff || hot) {
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
    LaunchedEffect(active, paused, rate, style.quality) {
        if (!active) return@LaunchedEffect
        if (paused) {
            // The picture is still: one copy is enough.
            frames.request(surfaceView) { version.intValue++ }
            return@LaunchedEffect
        }
        val fps = when {
            rate > 0 -> rate
            rate == 0 -> 60
            style.quality >= GlassQuality.BALANCED -> 30
            else -> 20
        }
        val gap = (1000L / fps).coerceAtLeast(12L)
        while (isActive) {
            frames.request(surfaceView) { version.intValue++ }
            delay(gap)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
    }
}

/** [SmoothnessPreferences.videoGlassRate] value that turns the live glass off. */
const val VideoGlassOff = -2

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
        val width = (view.width / 16).coerceIn(24, 128)
        val height = (view.height / 16).coerceIn(14, 128)
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
