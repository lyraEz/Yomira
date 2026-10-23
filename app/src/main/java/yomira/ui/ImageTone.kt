package yomira.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A dark tone taken from a picture (its muted color), used to fade big artwork into the page.
 * Pictures are read at 64 px, so this is cheap; results stay in memory for the session.
 */
object ImageTone {

    private const val MAX_ENTRIES = 96
    private const val SAMPLE_SIZE = 64

    private val cache = object : LinkedHashMap<String, Int>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > MAX_ENTRIES
    }

    fun cached(key: String): Color? = synchronized(cache) { cache[key] }?.let(::Color)

    suspend fun load(context: Context, model: Any): Color? = withContext(Dispatchers.IO) {
        val key = model.toString()
        cached(key)?.let { return@withContext it }
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val bitmap = (result.image as? BitmapImage)?.bitmap
            ?: result.image.asDrawable(context.resources).toBitmap()
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val swatch = palette.darkMutedSwatch
            ?: palette.mutedSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: return@withContext null
        val tone = deepen(swatch.rgb)
        synchronized(cache) { cache[key] = tone }
        Color(tone)
    }

    /** Same hue, calm and dark enough for white text on top. */
    private fun deepen(rgb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hsl[1] = hsl[1].coerceAtMost(0.5f)
        hsl[2] = hsl[2].coerceIn(0.1f, 0.19f)
        return ColorUtils.HSLToColor(hsl)
    }
}

/** Tone of [model], null until it is known. */
@Composable
fun rememberImageTone(model: Any?): Color? {
    val context = LocalContext.current
    val key = model?.toString()
    var tone by remember(key) { mutableStateOf(key?.let(ImageTone::cached)) }
    LaunchedEffect(key) {
        if (model == null || tone != null) return@LaunchedEffect
        tone = runCatching { ImageTone.load(context, model) }.getOrNull()
    }
    return tone
}

/** Tone used while a picture's own tone is still unknown. */
val DefaultImageTone = Color(0xFF15151C)
