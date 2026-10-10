package yomira.tv.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.min

/**
 * A WebView with an on-screen pointer driven by the D-pad.
 *
 * Site checks such as Cloudflare Turnstile ask for a click on a box inside an iframe, which D-pad
 * focus navigation cannot reach. With the pointer the arrows move a cursor, OK taps where it is,
 * and pushing against the top or bottom edge scrolls the page. Back keeps its usual meaning.
 */
@SuppressLint("ViewConstructor")
class TvCursorWebView(context: Context) : WebView(context) {

    var cursorEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var cursorX = -1f
    private var cursorY = -1f
    private var pressDownTime = 0L

    private val density = resources.displayMetrics.density
    private val radius = CURSOR_RADIUS_DP * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 200, 87) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 26, 18, 61)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cursorX < 0 || cursorX > w || cursorY > h) {
            cursorX = w / 2f
            cursorY = h / 2f
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!cursorEnabled) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(event, -1f, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(event, 1f, 0f)
            KeyEvent.KEYCODE_DPAD_UP -> move(event, 0f, -1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(event, 0f, 1f)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> tap(event)
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun move(event: KeyEvent, dx: Float, dy: Float): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val step = BASE_STEP_DP * density * min(1f + event.repeatCount * ACCELERATION, MAX_SPEED)
        val newX = cursorX + dx * step
        val newY = cursorY + dy * step
        val edge = EDGE_DP * density

        // Pushing past the top or bottom edge scrolls the page instead of moving the cursor.
        when {
            dy < 0 && newY < edge && canScrollVertically(-1) -> scrollBy(0, -step.toInt() * 2)
            dy > 0 && newY > height - edge && canScrollVertically(1) -> scrollBy(0, step.toInt() * 2)
            dx < 0 && newX < edge && canScrollHorizontally(-1) -> scrollBy(-step.toInt() * 2, 0)
            dx > 0 && newX > width - edge && canScrollHorizontally(1) -> scrollBy(step.toInt() * 2, 0)
        }
        cursorX = newX.coerceIn(0f, width.toFloat())
        cursorY = newY.coerceIn(0f, height.toFloat())
        invalidate()
        return true
    }

    private fun tap(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                pressDownTime = SystemClock.uptimeMillis()
                sendTouch(MotionEvent.ACTION_DOWN, pressDownTime)
            }
            KeyEvent.ACTION_UP -> sendTouch(MotionEvent.ACTION_UP, pressDownTime)
        }
        return true
    }

    private fun sendTouch(action: Int, downTime: Long) {
        val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, cursorX, cursorY, 0)
        motion.source = InputDevice.SOURCE_TOUCHSCREEN
        dispatchTouchEvent(motion)
        motion.recycle()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (!cursorEnabled || cursorX < 0) return
        // The canvas is translated by the scroll offset; draw in view coordinates.
        val x = cursorX + scrollX
        val y = cursorY + scrollY
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, stroke)
    }

    private companion object {
        const val CURSOR_RADIUS_DP = 10f
        const val BASE_STEP_DP = 14f
        const val ACCELERATION = 0.35f
        const val MAX_SPEED = 5f
        const val EDGE_DP = 32f
    }
}
