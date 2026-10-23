package yomira.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import yomira.metadata.AniListDetails
import yomira.metadata.EpisodeDisplays
import yomira.metadata.LocalTmdbMatchAction
import yomira.metadata.rememberAniListInfo
import yomira.metadata.rememberShowMeta
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Callbacks of the details screen the streaming layout uses. */
class StreamingDetailsActions(
    /** Plays the given episode, or continues as the classic button does when null. */
    val onPlay: (Episode?) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onCoverClicked: () -> Unit,
    val onTrackingClicked: () -> Unit,
    val onWebViewClicked: (() -> Unit)?,
    val onEditCategoryClicked: (() -> Unit)?,
    val onEditNotesClicked: () -> Unit,
    val onEditIntervalClicked: (() -> Unit)?,
    val onShareClicked: (() -> Unit)?,
)

/** Everything the streaming layout derives from the screen state, kept across recompositions. */
@Stable
class StreamingDetailsModel internal constructor(
    val facts: DetailsFacts,
    val heroImage: Any?,
    val logoUrl: String?,
    val description: String?,
    val playLabel: String,
    /** Episode the play button opens: the next unwatched, or the first. */
    val playEpisode: Episode?,
    val people: List<PersonCard>,
    val peopleTitle: String,
    val factRows: List<Pair<String, String>>,
    private val more: MutableState<Boolean>,
) {
    var showMore: Boolean
        get() = more.value
        set(value) {
            more.value = value
        }
}

@Composable
fun rememberStreamingDetails(state: AnimeScreenModel.State.Success): StreamingDetailsModel {
    val context = LocalContext.current
    val anime = state.anime
    val show = rememberShowMeta(anime.id)
    val ani = rememberAniListInfo(anime.id)
    val prefs = remember { DetailsPreferences() }
    val voiceLanguage by prefs.voiceLanguage().collectPref()

    LaunchedEffect(anime.id, anime.title) { AniListDetails.ensure(anime.id, anime.title) }

    val allItems = state.episodes
    // Same episode the classic "Continue" button opens.
    val next = remember(allItems, anime, state.selectedSeason, state.episodeToSeason) {
        allItems.getNextUnseen(
            anime = anime,
            seasonName = state.selectedSeason.takeIf { anime.seasonGroupingMode == LibraryPreferences.SeasonGrouping.Tabs },
            episodeToSeason = state.episodeToSeason,
        )
    }
    val watching = remember(allItems) { allItems.any { it.episode.seen || it.episode.lastSecondSeen > 0 } }
    val nextLabel = remember(next, show) {
        next?.let { EpisodeDisplays.build(context, show, it.episodeNumber, it.name, null, null).label }
    }
    val playLabel = when {
        nextLabel.isNullOrBlank() -> context.stringResource(YMR.strings.details_play_plain)
        watching && next?.seen == false -> context.stringResource(YMR.strings.details_continue, nextLabel)
        else -> context.stringResource(YMR.strings.details_play, nextLabel)
    }

    val facts = remember(anime, show, ani, allItems.size) {
        DetailsFormat.facts(context, anime, show, ani, allItems.size)
    }
    val people = remember(ani, show, anime.cast, voiceLanguage) {
        val characters = ani?.characters.orEmpty()
        when {
            characters.isNotEmpty() -> characters.map { character ->
                val chosen = character.voices[voiceLanguage]?.firstOrNull()
                val (code, voice) = if (chosen != null) {
                    voiceLanguage to chosen
                } else {
                    "ja" to character.voices["ja"]?.firstOrNull()
                }
                PersonCard(
                    name = character.name,
                    image = character.image,
                    otherName = voice?.name,
                    otherImage = voice?.image,
                    otherLabel = if (voice != null) {
                        context.stringResource(YMR.strings.details_voice, languageName(code))
                    } else {
                        context.stringResource(YMR.strings.details_no_voice, languageName(voiceLanguage))
                    },
                )
            }
            !show?.details?.cast.isNullOrEmpty() -> show!!.details!!.cast.map { member ->
                PersonCard(
                    name = member.character?.takeIf { it.isNotBlank() } ?: member.name,
                    image = member.profileUrl,
                    otherName = member.name.takeIf { member.character != null },
                    otherImage = member.profileUrl,
                    otherLabel = context.stringResource(YMR.strings.details_actor),
                )
            }
            else -> anime.cast.orEmpty().map { credit ->
                PersonCard(
                    name = credit.character?.takeIf { it.isNotBlank() } ?: credit.name,
                    image = credit.image_url,
                    otherName = credit.name.takeIf { credit.character != null },
                    otherImage = credit.image_url,
                    otherLabel = credit.role,
                )
            }
        }
    }
    val peopleTitle = context.stringResource(
        if (!ani?.characters.isNullOrEmpty()) YMR.strings.details_characters else YMR.strings.details_cast,
    )
    val description = anime.description?.takeIf { it.isNotBlank() }
        ?: show?.seasonHint?.let { show.details?.seasonOverviews?.get(it) }?.takeIf { it.isNotBlank() }
        ?: show?.overview?.takeIf { it.isNotBlank() }
    val factRows = remember(facts, state.source) {
        listOf(
            YMR.strings.details_fact_status to facts.status,
            YMR.strings.details_fact_release to (facts.release ?: facts.years),
            YMR.strings.details_fact_aired to facts.season,
            YMR.strings.details_fact_episodes to facts.episodes?.toString(),
            YMR.strings.details_fact_runtime to facts.runtime,
            YMR.strings.details_fact_format to facts.format,
            YMR.strings.details_fact_studio to facts.studios.joinToString(", "),
            YMR.strings.details_fact_creator to facts.creators.joinToString(", "),
            YMR.strings.details_fact_director to facts.directors.joinToString(", "),
            YMR.strings.details_fact_origin to facts.origin,
            YMR.strings.details_fact_network to facts.networks.joinToString(", "),
            YMR.strings.details_fact_country to facts.countries.joinToString(", "),
            YMR.strings.details_fact_score to facts.score?.let { "$it · ${facts.scoreSource}" },
            YMR.strings.details_fact_source to state.source.name,
        ).mapNotNull { (label, value) -> value?.takeIf { it.isNotBlank() }?.let { context.stringResource(label) to it } }
    }
    val more = remember { mutableStateOf(false) }

    return remember(facts, show, playLabel, next, people, factRows, description) {
        StreamingDetailsModel(
            facts = facts,
            heroImage = show?.keyArtUrl ?: show?.backdropUrl ?: anime.backgroundUrl ?: anime.asAnimeCover(),
            logoUrl = show?.logoUrl,
            description = description,
            playLabel = playLabel,
            playEpisode = next,
            people = people,
            peopleTitle = peopleTitle,
            factRows = factRows,
            more = more,
        )
    }
}

/** Top of the streaming layout: cover with play button, overview and characters. */
fun LazyListScope.streamingTopItems(
    model: StreamingDetailsModel,
    state: AnimeScreenModel.State.Success,
    actions: StreamingDetailsActions,
) {
    item(key = "streaming-hero") {
        DetailsHero(
            image = model.heroImage,
            logoUrl = model.logoUrl,
            title = state.anime.title,
            genres = model.facts.genres,
            playLabel = model.playLabel,
            favorite = state.anime.favorite,
            onPlay = { actions.onPlay(model.playEpisode) },
            onToggleFavorite = actions.onToggleFavorite,
            onMore = { model.showMore = true },
            onImageClick = actions.onCoverClicked,
        )
        if (model.showMore) MoreSheet(state, actions) { model.showMore = false }
    }
    item(key = "streaming-overview") {
        DetailsOverview(model.facts, model.description, state.anime.note)
    }
    item(key = "streaming-people") {
        PeopleSection(model.peopleTitle, model.people)
    }
}

/** Details table after the episode list. */
fun LazyListScope.streamingFactsItem(model: StreamingDetailsModel) {
    item(key = "streaming-facts") {
        FactsSection(model.factRows)
    }
}

/** "⋯" of the hero: tracking, browser, categories, notes, update interval, share, TMDB data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(state: AnimeScreenModel.State.Success, actions: StreamingDetailsActions, onDismiss: () -> Unit) {
    val tmdbMatch = LocalTmdbMatchAction.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetAction(
                Icons.Outlined.Sync,
                stringResource(YMR.strings.details_tracking),
                trailing = state.trackingCount.takeIf { it > 0 }?.toString(),
            ) {
                onDismiss()
                actions.onTrackingClicked()
            }
            actions.onWebViewClicked?.let { open ->
                SheetAction(Icons.Outlined.Public, stringResource(YMR.strings.details_webview)) {
                    onDismiss()
                    open()
                }
            }
            actions.onEditCategoryClicked?.let { edit ->
                SheetAction(Icons.AutoMirrored.Outlined.Label, stringResource(YMR.strings.details_categories)) {
                    onDismiss()
                    edit()
                }
            }
            SheetAction(Icons.Outlined.Edit, stringResource(YMR.strings.details_notes)) {
                onDismiss()
                actions.onEditNotesClicked()
            }
            actions.onEditIntervalClicked?.let { edit ->
                SheetAction(Icons.Outlined.HourglassEmpty, stringResource(YMR.strings.details_interval)) {
                    onDismiss()
                    edit()
                }
            }
            actions.onShareClicked?.let { share ->
                SheetAction(Icons.Outlined.Share, stringResource(YMR.strings.details_share)) {
                    onDismiss()
                    share()
                }
            }
            tmdbMatch?.let { open ->
                SheetAction(Icons.Outlined.Movie, stringResource(YMR.strings.tmdb_match_title)) {
                    onDismiss()
                    open()
                }
            }
        }
    }
}

/** Remembered selection of the streaming layout. */
@Composable
fun rememberStreamingLayout(): Boolean {
    val enabled by remember { DetailsPreferences() }.streamingLayout().collectPref()
    return enabled
}

/** What the episode card adds to a row of the classic list. */
@androidx.compose.runtime.Immutable
data class EpisodeCardExtras(
    /** "T1 E2" (TMDB, the name or the packed number), or "Episódio 2". */
    val label: String,
    /** The episode's own name, without the number the label already shows. */
    val name: String?,
    val rating: String?,
    val airDate: String?,
    val image: Any?,
    val progress: Float,
)

@Composable
fun rememberEpisodeCardExtras(
    animeId: Long,
    episode: Episode,
    display: yomira.metadata.EpisodeDisplay,
    showImage: Boolean,
): EpisodeCardExtras {
    val show = rememberShowMeta(animeId)
    return remember(show, episode, display, showImage) {
        val meta = yomira.metadata.EpisodeMetadata.episode(show, episode.episodeNumber, episode.name)
        EpisodeCardExtras(
            label = display.label,
            name = display.name,
            rating = meta?.voteAverage?.takeIf { it > 0 }?.let(DetailsFormat::score),
            airDate = meta?.airDate?.let(DetailsFormat::date),
            image = display.previewUrl.takeIf { showImage && !it.isNullOrBlank() },
            progress = if (!episode.seen && episode.totalSeconds > 0) {
                episode.lastSecondSeen.toFloat() / episode.totalSeconds
            } else {
                0f
            },
        )
    }
}
