package yomira.details

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import yomira.metadata.AniListInfo
import yomira.metadata.ShowMeta
import java.util.Locale
import kotlin.math.roundToInt

/** Settings → Appearance → Experience: the anime details screen. */
class DetailsPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Streaming-app layout (big cover, play button, characters, episode cards). */
    fun streamingLayout() = store.getBoolean("yomira_details_streaming", true)

    /** Voice actors shown for the characters: "ja", "pt", "en"... (see AniListDetails.voiceLanguages). */
    fun voiceLanguage() = store.getString("yomira_details_voice_language", "ja")
}

/** Everything the details screen shows, merged from TMDB, AniList and the extension. */
data class DetailsFacts(
    val genres: List<String>,
    val years: String?,
    val runtime: String?,
    val score: String?,
    val scoreSource: String?,
    val status: String?,
    val format: String?,
    val creators: List<String>,
    val directors: List<String>,
    val studios: List<String>,
    val networks: List<String>,
    val countries: List<String>,
    val origin: String?,
    val season: String?,
    val episodes: Int?,
    val release: String?,
)

object DetailsFormat {

    private val portuguese = Locale.forLanguageTag("pt-BR")

    private val genreNames = mapOf(
        "Action" to "Ação", "Adventure" to "Aventura", "Comedy" to "Comédia", "Drama" to "Drama",
        "Ecchi" to "Ecchi", "Fantasy" to "Fantasia", "Horror" to "Terror", "Mahou Shoujo" to "Garotas mágicas",
        "Mecha" to "Mecha", "Music" to "Música", "Mystery" to "Mistério", "Psychological" to "Psicológico",
        "Romance" to "Romance", "Sci-Fi" to "Ficção científica", "Slice of Life" to "Cotidiano",
        "Sports" to "Esportes", "Supernatural" to "Sobrenatural", "Thriller" to "Suspense",
    )

    fun facts(context: Context, anime: Anime, show: ShowMeta?, ani: AniListInfo?, episodeCount: Int): DetailsFacts {
        val details = show?.details
        val pt = Locale.getDefault().language == "pt"
        val genres = details?.genres?.takeIf { it.isNotEmpty() }
            ?: anime.genre?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: ani?.genres?.map { if (pt) genreNames[it] ?: it else it }.orEmpty()

        val first = ani?.startDate ?: details?.firstAirDate
        val last = ani?.endDate ?: details?.lastAirDate
        val running = ani?.status == "RELEASING" || details?.status == "Returning Series"
        val firstYear = first?.take(4)?.toIntOrNull()
        val lastYear = last?.take(4)?.toIntOrNull()
        val years = when {
            firstYear == null -> null
            running -> "$firstYear–"
            lastYear == null || lastYear == firstYear -> "$firstYear"
            else -> "$firstYear–$lastYear"
        }

        val minutes = details?.runtimeMinutes ?: ani?.duration
        val runtime = minutes?.takeIf { it > 0 }?.let {
            if (it >= 60) "${it / 60}h ${(it % 60).toString().padStart(2, '0')}m" else "${it}m"
        }

        val (score, scoreSource) = when {
            details?.voteAverage != null && (details.voteCount ?: 0) >= 5 ->
                String.format(Locale.ROOT, "%.1f", details.voteAverage) to "TMDB"
            ani?.score != null -> String.format(Locale.ROOT, "%.1f", ani.score / 10.0) to "AniList"
            else -> null to null
        }

        return DetailsFacts(
            genres = genres.distinct().take(MAX_GENRES),
            years = years,
            runtime = runtime,
            score = score,
            scoreSource = scoreSource,
            status = status(context, ani?.status, details?.status, anime.status),
            format = ani?.format?.let { format(context, it) },
            creators = ani?.creators?.takeIf { it.isNotEmpty() }
                ?: details?.creators?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(anime.author?.takeIf { it.isNotBlank() }),
            directors = ani?.directors.orEmpty(),
            studios = ani?.studios?.takeIf { it.isNotEmpty() }
                ?: details?.studios?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(anime.artist?.takeIf { it.isNotBlank() }),
            networks = details?.networks.orEmpty(),
            countries = (details?.countries?.takeIf { it.isNotEmpty() } ?: listOfNotNull(ani?.country))
                .map { Locale("", it).getDisplayCountry(Locale.getDefault()).ifBlank { it } },
            origin = ani?.source?.let { origin(context, it) },
            season = ani?.season?.let { season -> ani.seasonYear?.let { "${seasonName(context, season)} $it" } },
            episodes = ani?.episodes ?: episodeCount.takeIf { it > 0 },
            release = listOfNotNull(first?.let(::date), last?.takeIf { !running && it != first }?.let(::date))
                .joinToString(" – ").ifBlank { null },
        )
    }

    /** "2012-07-08" → "8 de jul. de 2012" in the app language. */
    fun date(iso: String): String {
        val parts = iso.split('-').mapNotNull { it.toIntOrNull() }
        return runCatching {
            when (parts.size) {
                3 -> java.time.LocalDate.of(parts[0], parts[1], parts[2]).format(
                    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM),
                )
                2 -> java.time.YearMonth.of(parts[0], parts[1]).format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()),
                )
                else -> parts.firstOrNull()?.toString() ?: iso
            }
        }.getOrDefault(iso)
    }

    fun score(value: Double): String = String.format(Locale.ROOT, "%.1f", (value * 10).roundToInt() / 10.0)

    private fun status(context: Context, aniList: String?, tmdb: String?, extension: Long): String? {
        val key = when (aniList) {
            "FINISHED" -> YMR.strings.details_status_finished
            "RELEASING" -> YMR.strings.details_status_airing
            "NOT_YET_RELEASED" -> YMR.strings.details_status_upcoming
            "CANCELLED" -> YMR.strings.details_status_cancelled
            "HIATUS" -> YMR.strings.details_status_hiatus
            else -> when (tmdb) {
                "Ended", "Released" -> YMR.strings.details_status_finished
                "Returning Series" -> YMR.strings.details_status_airing
                "Canceled" -> YMR.strings.details_status_cancelled
                "In Production", "Planned", "Post Production", "Rumored" -> YMR.strings.details_status_upcoming
                else -> when (extension.toInt()) {
                    1 -> YMR.strings.details_status_airing
                    2, 4 -> YMR.strings.details_status_finished
                    5 -> YMR.strings.details_status_cancelled
                    6 -> YMR.strings.details_status_hiatus
                    else -> null
                }
            }
        }
        return key?.let { context.stringResource(it) }
    }

    private fun format(context: Context, format: String): String = when (format) {
        "TV" -> context.stringResource(YMR.strings.details_format_tv)
        "TV_SHORT" -> context.stringResource(YMR.strings.details_format_tv_short)
        "MOVIE" -> context.stringResource(YMR.strings.details_format_movie)
        "SPECIAL" -> context.stringResource(YMR.strings.details_format_special)
        "OVA", "ONA" -> format
        "MUSIC" -> context.stringResource(YMR.strings.details_format_music)
        else -> format
    }

    private fun origin(context: Context, source: String): String = when (source) {
        "ORIGINAL" -> context.stringResource(YMR.strings.details_origin_original)
        "MANGA" -> context.stringResource(YMR.strings.details_origin_manga)
        "LIGHT_NOVEL" -> context.stringResource(YMR.strings.details_origin_light_novel)
        "VISUAL_NOVEL" -> context.stringResource(YMR.strings.details_origin_visual_novel)
        "VIDEO_GAME" -> context.stringResource(YMR.strings.details_origin_game)
        "NOVEL", "WEB_NOVEL" -> context.stringResource(YMR.strings.details_origin_novel)
        "WEB_MANGA" -> context.stringResource(YMR.strings.details_origin_web_manga)
        else -> source.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(portuguese) }
    }

    private fun seasonName(context: Context, season: String): String = when (season) {
        "WINTER" -> context.stringResource(YMR.strings.details_season_winter)
        "SPRING" -> context.stringResource(YMR.strings.details_season_spring)
        "SUMMER" -> context.stringResource(YMR.strings.details_season_summer)
        "FALL" -> context.stringResource(YMR.strings.details_season_fall)
        else -> season
    }

    private const val MAX_GENRES = 4
}
