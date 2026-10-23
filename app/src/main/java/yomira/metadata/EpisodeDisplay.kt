package yomira.metadata

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import tachiyomi.core.common.i18n.stringResource
import yomira.i18n.YMR
import kotlin.math.floor

/** How an episode is shown once TMDB data is merged with what the extension gave. */
data class EpisodeDisplay(
    /** Title for lists: "Episódio 2 – Como um garoto". */
    val title: String,
    /** Just the episode's own name, when it has a real one. */
    val name: String?,
    val summary: String?,
    val previewUrl: String?,
    /** "T1 E2" when the season is known, otherwise "Episódio 2". */
    val label: String,
    val runtimeMinutes: Int?,
)

object EpisodeDisplays {

    fun number(value: Double): String =
        if (value == floor(value)) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')

    fun build(
        context: Context,
        show: ShowMeta?,
        episodeNumber: Double,
        extensionName: String,
        extensionSummary: String?,
        extensionPreview: String?,
        previewSize: String = "w300",
        prefs: MetadataPreferences = MetadataPreferences(),
    ): EpisodeDisplay {
        val enabled = prefs.tmdbEnabled().get()
        val meta = if (enabled) EpisodeMetadata.episode(show, episodeNumber, extensionName) else null
        val numberText = number(episodeNumber)
        val numberLabel = context.stringResource(YMR.strings.episode_number_label, numberText)

        val tmdbName = meta?.name?.takeUnless { TitleMatch.isGenericName(it) }
        val ownName = extensionName.takeUnless { TitleMatch.isGenericName(it) || it.isBlank() }
        val name = if (prefs.episodeNames().get()) tmdbName ?: ownName else ownName ?: tmdbName

        val title = when {
            prefs.episodeNames().get() && tmdbName != null ->
                context.stringResource(YMR.strings.episode_title_format, numberText, tmdbName)
            else -> extensionName.ifBlank { numberLabel }
        }
        val summary = meta?.overview?.takeIf { it.isNotBlank() } ?: extensionSummary?.takeIf { it.isNotBlank() }
        val preview = extensionPreview?.takeIf { it.isNotBlank() }
            ?: meta?.stillUrl(previewSize)?.takeIf { prefs.episodeThumbnails().get() }
        val label = (if (enabled) EpisodeMetadata.seasonEpisodeLabel(show, episodeNumber, extensionName) else null)
            ?: TitleMatch.seasonEpisodeIn(extensionName)?.let { "T${it.first} E${it.second}" }
            ?: numberLabel
        return EpisodeDisplay(title, name, summary, preview, label, meta?.runtime)
    }
}

/** [EpisodeDisplays.build] remembered for the current TMDB data. */
@Composable
fun rememberEpisodeDisplay(
    animeId: Long,
    episodeNumber: Double,
    extensionName: String,
    extensionSummary: String?,
    extensionPreview: String?,
    previewSize: String = "w300",
): EpisodeDisplay {
    val context = LocalContext.current
    val show = rememberShowMeta(animeId)
    return remember(show, episodeNumber, extensionName, extensionSummary, extensionPreview, previewSize) {
        EpisodeDisplays.build(context, show, episodeNumber, extensionName, extensionSummary, extensionPreview, previewSize)
    }
}
