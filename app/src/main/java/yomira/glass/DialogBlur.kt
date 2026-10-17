package yomira.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/**
 * Blurs the screen behind the dialog window this is called from (Android 12+, when the system
 * allows cross-window blur and the glass quality is not off), so sheets float over frosted
 * content instead of a flat dim.
 */
@Composable
fun DialogWindowBlur() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val style = LocalGlassStyle.current
    if (!style.liveBlur) return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val radius = with(LocalDensity.current) { style.sheetBlur().coerceAtMost(28.dp).toPx() }.roundToInt()
    DisposableEffect(window, radius) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply {
            blurBehindRadius = radius
            dimAmount = 0.28f
        }
        onDispose { }
    }
}
