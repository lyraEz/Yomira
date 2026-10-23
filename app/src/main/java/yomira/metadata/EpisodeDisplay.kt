package yomira.metadata

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
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

    /** Highest episode number taken as is; above it the number in the name is preferred. */
    private const val LARGE_NUMBER = 1000.0
    private const val MAX_NUMBER = 20000.0

    /**
     * Number to show for an episode. Some extensions give ids or timestamps as the episode number;
     * then the number written in the name ("Episódio 5") is used, or none at all.
     */
    fun displayNumber(value: Double, name: String): String? {
        val fromName = yomira.details.EpisodeSeasons.episodeIn(name)
        val sane = value >= 0 && value <= MAX_NUMBER && !value.isNaN()
        return when {
            sane && (value <= LARGE_NUMBER || fromName == null || fromName.toDouble() == value) -> number(value)
            fromName != null -> fromName.toString()
            else -> null
        }
    }

    fun build(
        context: Context,
        show: ShowMeta?,
        episodeNumber: Double,
        extensionName: String,
        extensionSummary: String?,
        extensionPreview: String?,
        previewSize: String = "w300",
        prefs: MetadataPreferences = MetadataPreferences(),
    ): EpisodeDisplay = try {
        compose(context, show, episodeNumber, extensionName, extensionSummary, extensionPreview, previewSize, prefs)
    } catch (e: Throwable) {
        // Lists, the player and the start screen must never fail because of TMDB data:
        // fall back to what the extension gave.
        logcat(LogPriority.ERROR, e) { "Episode display failed" }
        EpisodeDisplay(
            title = extensionName,
            name = extensionName.takeIf { it.isNotBlank() },
            summary = extensionSummary?.takeIf { it.isNotBlank() },
            previewUrl = extensionPreview?.takeIf { it.isNotBlank() },
            label = "",
            runtimeMinutes = null,
        )
    }

    private fun compose(
        context: Context,
        show: ShowMeta?,
        episodeNumber: Double,
        extensionName: String,
        extensionSummary: String?,
        extensionPreview: String?,
        previewSize: String,
        prefs: MetadataPreferences,
    ): EpisodeDisplay {
        val enabled = prefs.tmdbEnabled().get()
        val meta = if (enabled) EpisodeMetadata.episode(show, episodeNumber, extensionName) else null
        val cleanName = TitleMatch.cleanEpisodeName(extensionName)
        val numberText = displayNumber(episodeNumber, cleanName)
        val numberLabel = numberText?.let { context.stringResource(YMR.strings.episode_number_label, it) }
            ?: context.stringResource(YMR.strings.episode_plain)

        val tmdbName = meta?.name?.takeUnless { TitleMatch.isGenericName(it) }
        val ownName = cleanName.takeUnless { TitleMatch.isGenericName(it) || it.isBlank() }
        val name = if (prefs.episodeNames().get()) tmdbName ?: ownName else ownName ?: tmdbName

        val title = when {
            prefs.episodeNames().get() && tmdbName != null && numberText != null ->
                context.stringResource(YMR.strings.episode_title_format, numberText, tmdbName)
            prefs.episodeNames().get() && tmdbName != null -> tmdbName
            else -> cleanName.ifBlank { numberLabel }
        }
        val summary = meta?.overview?.takeIf { it.isNotBlank() } ?: extensionSummary?.takeIf { it.isNotBlank() }
        val preview = extensionPreview?.takeIf { it.isNotBlank() }
            ?: meta?.stillUrl(previewSize)?.takeIf { prefs.episodeThumbnails().get() }
        val label = (if (enabled) EpisodeMetadata.seasonEpisodeLabel(show, episodeNumber, extensionName) else null)
            ?.takeIf { numberText != null }
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
