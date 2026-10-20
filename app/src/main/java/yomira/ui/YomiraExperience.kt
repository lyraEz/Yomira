package yomira.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR

/** App-wide comfort options that live outside the player. */
class ExperiencePreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Blur thumbnails and hide titles of episodes not watched yet. */
    fun hideSpoilers() = store.getBoolean("yomira_hide_spoilers", false)

    /** Long-press the app icon to jump back into the last episode. */
    fun continueShortcut() = store.getBoolean("yomira_continue_shortcut", true)
}

/**
 * Keeps a "Continue: <anime>" shortcut on the launcher icon pointing at the episode that was
 * opened last, so it reopens the player right there.
 */
object ContinueShortcut {

    private const val ID = "yomira_continue"

    fun publish(context: Context, animeId: Long, episodeId: Long, animeTitle: String) {
        if (!ExperiencePreferences().continueShortcut().get()) return
        runCatching {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val longLabel = context.stringResource(YMR.strings.shortcut_continue, animeTitle)
            val shortcut = ShortcutInfoCompat.Builder(context, ID)
                .setShortLabel(animeTitle.take(24).ifBlank { longLabel.take(24) })
                .setLongLabel(longLabel.take(45))
                .setIcon(IconCompat.createWithResource(context, R.drawable.sc_continue_48dp))
                .setIntent(intent)
                .setRank(0)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    fun remove(context: Context) {
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID)) }
    }
}

/** True while drawing an unwatched episode with spoiler protection on: its thumbnail is blurred. */
val LocalSpoilerBlur = compositionLocalOf { false }

object SpoilerShield {
    private val numbered = Regex("""^(.*?\d+(?:[.,]\d+)?)""")

    /** "Episode 12 - The truth" -> "Episode 12"; titles without a number become a neutral label. */
    fun maskTitle(title: String, fallback: String): String {
        val beforeSeparator = title.split(" - ", " – ", ": ").first().trim()
        if (beforeSeparator.length < title.trim().length && beforeSeparator.any(Char::isDigit)) return beforeSeparator
        return numbered.find(title.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    }
}

@Composable
fun rememberHideSpoilers(): Boolean {
    val prefs = remember { ExperiencePreferences() }
    val hide by prefs.hideSpoilers().collectAsState()
    return hide
}
