package yomira.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import yomira.i18n.YMR

/**
 * Opens links like Compose's default handler, but shows a message instead of crashing when no
 * app can open them (for example when no browser is installed).
 */
class SafeUriHandler(private val context: Context) : UriHandler {

    override fun openUri(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "No app can open $uri" }
            Toast.makeText(context, context.stringResource(YMR.strings.no_app_for_link, uri), Toast.LENGTH_LONG).show()
        }
    }
}
