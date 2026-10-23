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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.episode.model.Episode
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import yomira.metadata.AniListDetails
import yomira.metadata.EpisodeDisplays
import yomira.metadata.EpisodeMetadata
import yomira.metadata.LocalTmdbMatchAction
import yomira.metadata.rememberAniListInfo
import yomira.metadata.rememberShowMeta
import java.text.DateFormat
import java.util.Date
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Callbacks of the details screen the streaming layout uses. */
class StreamingDetailsActions(
    /** Plays the given episode, or continues as the classic button does when null. */
    val onPlay: (Episode?) -> Unit,
    val onEpisodeClicked: (Episode) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onCoverClicked: () -> Unit,
    val onFilterClicked: () -> Unit,
    val onTrackingClicked: () -> Unit,
    val onWebViewClicked: (() -> Unit)?,
    val onEditCategoryClicked: (() -> Unit)?,
    val onEditNotesClicked: () -> Unit,
    val onEditIntervalClicked: (() -> Unit)?,
    val onShareClicked: (() -> Unit)?,
    val onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    val onMarkSeen: (List<Episode>, Boolean) -> Unit,
    val onBookmark: (List<Episode>, Boolean) -> Unit,
    val onMarkPreviousSeen: (Episode) -> Unit,
)

/** Everything the streaming layout derives from the screen state, kept across recompositions. */
@Stable
class StreamingDetailsModel internal constructor(
    val facts: DetailsFacts,
    val heroImage: Any?,
    val logoUrl: String?,
    val description: String?,
    val groups: List<SeasonGroup>,
    val seasonLabels: List<String>,
    val labels: Map<Long, String>,
    val playLabel: String,
    /** Episode the play button opens: the next unwatched, or the first. */
    val playEpisode: Episode?,
    val people: List<PersonCard>,
    val peopleTitle: String,
    val factRows: List<Pair<String, String>>,
    private val selectedInitial: Int,
    private val chosen: MutableIntState,
    private val more: MutableState<Boolean>,
    private val actionsTarget: MutableState<Long?>,
) {
    /** Season tab: the one the user picked, otherwise the one with the next episode. */
    var selected: Int
        get() = chosen.intValue.takeIf { it in groups.indices } ?: selectedInitial
        set(value) {
            chosen.intValue = value
        }
    var showMore: Boolean
        get() = more.value
        set(value) {
            more.value = value
        }
    var actionsFor: Long?
        get() = actionsTarget.value
        set(value) {
            actionsTarget.value = value
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
    val groups = remember(allItems, show?.seasonHint, show?.seasons, show?.offset) {
        EpisodeSeasons.group(
            allItems.map {
                SeasonInput(it.episode.id, it.episode.name, it.episode.episodeNumber, it.episode.sourceOrder, it.episode.scanlator)
            },
            show,
        )
    }
    val labels = remember(groups) {
        groups.flatMap { group ->
            group.episodeIds.map { id ->
                val number = group.numbers[id] ?: 0
                id to if (group.season != null) "T${group.season} E$number" else context.stringResource(YMR.strings.details_special_n, number)
            }
        }.toMap()
    }
    val seasonLabels = remember(groups) {
        groups.map { group ->
            group.season?.let { context.stringResource(YMR.strings.details_season_n, it) }
                ?: context.stringResource(YMR.strings.details_specials)
        }
    }
    val byId = remember(allItems) { allItems.associateBy { it.episode.id } }
    val next = remember(groups, byId) {
        groups.asSequence().filter { it.season != null }.flatMap { it.episodeIds.asSequence() }
            .mapNotNull { byId[it]?.episode }
            .firstOrNull { !it.seen }
    }
    val first = remember(groups, byId) { groups.firstOrNull()?.episodeIds?.firstOrNull()?.let { byId[it]?.episode } }
    val watching = remember(allItems) { allItems.any { it.episode.seen || it.episode.lastSecondSeen > 0 } }
    val playLabel = when {
        next != null && watching -> context.stringResource(YMR.strings.details_continue, labels[next.id].orEmpty())
        next != null -> context.stringResource(YMR.strings.details_play, labels[next.id].orEmpty())
        first != null -> context.stringResource(YMR.strings.details_play, labels[first.id].orEmpty())
        else -> context.stringResource(YMR.strings.details_play_plain)
    }
    val selectedInitial = remember(groups, next) {
        next?.let { episode -> groups.indexOfFirst { episode.id in it.episodeIds } }?.takeIf { it >= 0 } ?: 0
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
    val chosen = rememberSaveable(anime.id) { mutableIntStateOf(-1) }
    val more = remember { mutableStateOf(false) }
    val actionsTarget = remember { mutableStateOf<Long?>(null) }

    return remember(facts, show, groups, seasonLabels, labels, playLabel, next, first, people, factRows, description, selectedInitial) {
        StreamingDetailsModel(
            facts = facts,
            heroImage = show?.keyArtUrl ?: show?.backdropUrl ?: anime.backgroundUrl ?: anime.asAnimeCover(),
            logoUrl = show?.logoUrl,
            description = description,
            groups = groups,
            seasonLabels = seasonLabels,
            labels = labels,
            playLabel = playLabel,
            playEpisode = next ?: first,
            people = people,
            peopleTitle = peopleTitle,
            factRows = factRows,
            selectedInitial = selectedInitial,
            chosen = chosen,
            more = more,
            actionsTarget = actionsTarget,
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

/**
 * Seasons, episodes and details of the streaming layout. [listItems] draws the classic episode
 * rows (with selection, swipe and downloads) for the list view.
 */
fun LazyListScope.streamingEpisodeItems(
    model: StreamingDetailsModel,
    state: AnimeScreenModel.State.Success,
    actions: StreamingDetailsActions,
    cardsView: Boolean,
    onToggleView: () -> Unit,
    listItems: LazyListScope.(List<EpisodeList>) -> Unit,
) {
    val group = model.groups.getOrNull(model.selected)
    val visible = state.processedEpisodes.associateBy { it.episode.id }
    val groupItems = group?.episodeIds?.mapNotNull { visible[it] }.orEmpty()
    item(key = "streaming-seasons") {
        val context = LocalContext.current
        SeasonsHeader(
            title = context.stringResource(
                if (model.groups.size > 1) YMR.strings.details_seasons else YMR.strings.details_episodes,
            ) + if (model.groups.size <= 1) " (${groupItems.size})" else "",
            seasonLabels = model.seasonLabels,
            selected = model.selected,
            onSelect = { model.selected = it },
            cards = cardsView,
            onToggleView = onToggleView,
            filterActive = state.filterActive,
            onFilter = actions.onFilterClicked,
        )
    }
    if (cardsView) {
        item(key = "streaming-cards-${model.selected}") {
            val context = LocalContext.current
            val show = rememberShowMeta(state.anime.id)
            val cards = remember(groupItems, show, model.labels) {
                groupItems.map { item -> cardData(context, item, model.labels[item.episode.id].orEmpty(), show, state) }
            }
            val start = remember(cards) { cards.indexOfFirst { !it.seen }.takeIf { it >= 0 } ?: 0 }
            EpisodeCardsRow(
                cards = cards,
                startIndex = start,
                onClick = { id -> visible[id]?.let { actions.onEpisodeClicked(it.episode) } },
                onLongClick = { id -> model.actionsFor = id },
            )
            model.actionsFor?.let { id ->
                val item = visible[id] ?: return@let
                val card = cards.firstOrNull { it.id == id }
                EpisodeActionsSheet(
                    title = listOfNotNull(card?.label, card?.title).joinToString(" · "),
                    seen = item.episode.seen,
                    bookmarked = item.episode.bookmark,
                    downloaded = item.isDownloaded,
                    canDownload = actions.onDownloadEpisode != null,
                    onDismiss = { model.actionsFor = null },
                    onDownload = { actions.onDownloadEpisode?.invoke(listOf(item), EpisodeDownloadAction.START) },
                    onDeleteDownload = { actions.onDownloadEpisode?.invoke(listOf(item), EpisodeDownloadAction.DELETE) },
                    onToggleSeen = { actions.onMarkSeen(listOf(item.episode), !item.episode.seen) },
                    onToggleBookmark = { actions.onBookmark(listOf(item.episode), !item.episode.bookmark) },
                    onMarkPreviousSeen = { actions.onMarkPreviousSeen(item.episode) },
                )
            }
        }
    } else {
        listItems(groupItems)
    }
    item(key = "streaming-facts") {
        FactsSection(model.factRows)
    }
}

private fun cardData(
    context: android.content.Context,
    item: EpisodeList.Item,
    label: String,
    show: yomira.metadata.ShowMeta?,
    state: AnimeScreenModel.State.Success,
): EpisodeCardData {
    val episode = item.episode
    val display = EpisodeDisplays.build(
        context = context,
        show = show,
        episodeNumber = episode.episodeNumber,
        extensionName = episode.name,
        extensionSummary = episode.summary,
        extensionPreview = episode.previewUrl,
        previewSize = "w780",
    )
    val meta = EpisodeMetadata.episode(show, episode.episodeNumber, episode.name)
    val date = meta?.airDate?.let(DetailsFormat::date)
        ?: episode.dateUpload.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    val progress = if (episode.totalSeconds > 0) episode.lastSecondSeen.toFloat() / episode.totalSeconds else 0f
    return EpisodeCardData(
        id = episode.id,
        label = label,
        title = display.name ?: display.title,
        summary = display.summary,
        image = display.previewUrl ?: show?.backdropUrl ?: state.anime.backgroundUrl ?: state.anime.asAnimeCover(),
        rating = meta?.voteAverage?.takeIf { it > 0 }?.let(DetailsFormat::score),
        date = date,
        progress = progress,
        seen = episode.seen,
        downloaded = item.isDownloaded,
        bookmarked = episode.bookmark,
    )
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

/** Remembered list/cards switch of the episodes. */
@Composable
fun rememberEpisodeCardsView(): Pair<Boolean, () -> Unit> {
    val prefs = remember { DetailsPreferences() }
    val view by prefs.episodeView().collectPref()
    val cards = view != "list"
    return cards to { prefs.episodeView().set(if (cards) "list" else "cards") }
}

/** Remembered selection of the streaming layout. */
@Composable
fun rememberStreamingLayout(): Boolean {
    val enabled by remember { DetailsPreferences() }.streamingLayout().collectPref()
    return enabled
}
