package eu.kanade.tachiyomi.ui.player

import android.view.KeyEvent

// YMR -->
/**
 * Decides who handles a key in the player: the Compose UI, the player logic or mpv.
 *
 * Hardware keys (keyboards, headsets, game controllers, media buttons). Before this, every key
 * went to mpv first, so arrow keys could not move through menus, Enter did nothing useful and
 * MEDIA_STOP sent mpv's `quit`. The rules:
 *
 * | State                             | Arrows / Enter             | Back            |
 * |-----------------------------------|----------------------------|-----------------|
 * | text field focused                | UI                         | UI              |
 * | sheet, panel, dialog or error     | UI (focus navigation)      | UI (closes it)  |
 * | controls visible                  | UI (focus navigation)      | hide controls   |
 * | controls hidden                   | ←/→ seek, ↑/↓/OK show them | UI (leaves)     |
 *
 * Media keys work in every state except text input: play/pause, rewind/fast-forward seek,
 * next/previous change episode and stop leaves the player. Keys this router does not know go to
 * mpv, so keyboard shortcuts from input.conf keep working.
 */
object PlayerKeyRouter {

    data class State(
        val textInputActive: Boolean,
        val overlayOpen: Boolean,
        val controlsShown: Boolean,
    )

    sealed interface Decision {
        /** Let the Compose hierarchy (focus, clicks, BackHandler) handle the key. */
        data object Ui : Decision

        /** Hand the key to mpv's own bindings. */
        data object Mpv : Decision

        /** Consumed by the player; [action] runs on key down. */
        data class Handle(val action: Action, val repeatable: Boolean = false) : Decision
    }

    enum class Action {
        SHOW_CONTROLS,
        HIDE_CONTROLS,
        TOGGLE_PLAY_PAUSE,
        PLAY,
        PAUSE,
        SEEK_BACKWARD,
        SEEK_FORWARD,
        NEXT_EPISODE,
        PREVIOUS_EPISODE,
        OPEN_MENU,
        EXIT,
    }

    fun route(keyCode: Int, state: State): Decision {
        if (state.textInputActive) return Decision.Ui

        mediaAction(keyCode)?.let { return it }

        if (state.overlayOpen) return Decision.Ui

        return if (state.controlsShown) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> Decision.Handle(Action.HIDE_CONTROLS)
                KeyEvent.KEYCODE_MENU -> Decision.Handle(Action.OPEN_MENU)
                in NAVIGATION_KEYS -> Decision.Ui
                else -> Decision.Mpv
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Decision.Handle(Action.SEEK_BACKWARD, repeatable = true)
                KeyEvent.KEYCODE_DPAD_RIGHT -> Decision.Handle(Action.SEEK_FORWARD, repeatable = true)
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> Decision.Handle(Action.SHOW_CONTROLS)
                KeyEvent.KEYCODE_MENU -> Decision.Handle(Action.OPEN_MENU)
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> Decision.Ui
                else -> Decision.Mpv
            }
        }
    }

    private fun mediaAction(keyCode: Int): Decision? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_SPACE,
        -> Decision.Handle(Action.TOGGLE_PLAY_PAUSE)
        KeyEvent.KEYCODE_MEDIA_PLAY -> Decision.Handle(Action.PLAY)
        KeyEvent.KEYCODE_MEDIA_PAUSE -> Decision.Handle(Action.PAUSE)
        KeyEvent.KEYCODE_MEDIA_REWIND -> Decision.Handle(Action.SEEK_BACKWARD, repeatable = true)
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Decision.Handle(Action.SEEK_FORWARD, repeatable = true)
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> Decision.Handle(Action.NEXT_EPISODE)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> Decision.Handle(Action.PREVIOUS_EPISODE)
        KeyEvent.KEYCODE_MEDIA_STOP -> Decision.Handle(Action.EXIT)
        else -> null
    }

    private val NAVIGATION_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_TAB,
    )
}
// YMR <--
