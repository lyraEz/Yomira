package eu.kanade.tachiyomi.ui.player

import android.view.KeyEvent
import eu.kanade.tachiyomi.ui.player.PlayerKeyRouter.Action
import eu.kanade.tachiyomi.ui.player.PlayerKeyRouter.Decision
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// YMR -->
class PlayerKeyRouterTest {

    private val hidden = PlayerKeyRouter.State(textInputActive = false, overlayOpen = false, controlsShown = false)
    private val shown = hidden.copy(controlsShown = true)
    private val menu = hidden.copy(overlayOpen = true)
    private val typing = shown.copy(textInputActive = true)

    @Test
    fun `with controls hidden left and right seek and repeat while held`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_DPAD_LEFT, hidden) shouldBe
            Decision.Handle(Action.SEEK_BACKWARD, repeatable = true)
        PlayerKeyRouter.route(KeyEvent.KEYCODE_DPAD_RIGHT, hidden) shouldBe
            Decision.Handle(Action.SEEK_FORWARD, repeatable = true)
    }

    @Test
    fun `with controls hidden OK, enter and up or down show the controls`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
        ).forEach { key ->
            PlayerKeyRouter.route(key, hidden) shouldBe Decision.Handle(Action.SHOW_CONTROLS)
        }
    }

    @Test
    fun `enter never reaches mpv, where it would skip to the next playlist entry`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_ENTER, hidden) shouldBe Decision.Handle(Action.SHOW_CONTROLS)
        PlayerKeyRouter.route(KeyEvent.KEYCODE_ENTER, shown) shouldBe Decision.Ui
    }

    @Test
    fun `with controls hidden back leaves through the activity`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_BACK, hidden) shouldBe Decision.Ui
    }

    @Test
    fun `with controls visible the D-pad moves focus and back only hides them`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
        ).forEach { key ->
            PlayerKeyRouter.route(key, shown) shouldBe Decision.Ui
        }
        PlayerKeyRouter.route(KeyEvent.KEYCODE_BACK, shown) shouldBe Decision.Handle(Action.HIDE_CONTROLS)
    }

    @Test
    fun `an open sheet, panel, dialog or error owns the D-pad and back`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
        ).forEach { key ->
            PlayerKeyRouter.route(key, menu) shouldBe Decision.Ui
        }
    }

    @Test
    fun `media keys work whether or not controls or menus are visible`() {
        listOf(hidden, shown, menu).forEach { state ->
            PlayerKeyRouter.route(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, state) shouldBe Decision.Handle(Action.TOGGLE_PLAY_PAUSE)
            PlayerKeyRouter.route(KeyEvent.KEYCODE_MEDIA_NEXT, state) shouldBe Decision.Handle(Action.NEXT_EPISODE)
            PlayerKeyRouter.route(KeyEvent.KEYCODE_MEDIA_PREVIOUS, state) shouldBe Decision.Handle(Action.PREVIOUS_EPISODE)
            PlayerKeyRouter.route(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, state) shouldBe
                Decision.Handle(Action.SEEK_FORWARD, repeatable = true)
        }
    }

    @Test
    fun `stop leaves the player instead of sending quit to mpv`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_MEDIA_STOP, hidden) shouldBe Decision.Handle(Action.EXIT)
    }

    @Test
    fun `menu key opens the player menu as an alternative to long press`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_MENU, hidden) shouldBe Decision.Handle(Action.OPEN_MENU)
        PlayerKeyRouter.route(KeyEvent.KEYCODE_MENU, shown) shouldBe Decision.Handle(Action.OPEN_MENU)
    }

    @Test
    fun `a focused text field receives every key`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_1,
        ).forEach { key ->
            PlayerKeyRouter.route(key, typing) shouldBe Decision.Ui
        }
    }

    @Test
    fun `other keys keep their mpv input conf bindings`() {
        PlayerKeyRouter.route(KeyEvent.KEYCODE_S, hidden) shouldBe Decision.Mpv
        PlayerKeyRouter.route(KeyEvent.KEYCODE_S, shown) shouldBe Decision.Mpv
    }
}
// YMR <--
