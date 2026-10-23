package yomira.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.glass.glass
import yomira.i18n.YMR
import yomira.metadata.EpisodeMetadata
import yomira.metadata.rememberEpisodeDisplay
import yomira.metadata.rememberShowMeta
import tachiyomi.presentation.core.util.collectAsState as collectPref

private val CardHeight = 112.dp

/** Card height, taller with a larger system font so its four lines always fit. */
@Composable
private fun cardHeight(): Dp = CardHeight * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)

/** Room the card takes above the tab bar; lists add it to their bottom padding. */
@Composable
fun continueCardInset(): Dp = cardHeight() + 10.dp

class ContinueWatchingPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** "Continue watching" card above the tab bar on the start screen. */
    fun showOnHome() = store.getBoolean("yomira_continue_card", true)

    /** Episode whose card was closed; the card returns with the next episode watched. */
    fun dismissedEpisode() = store.getLong("yomira_continue_dismissed", -1L)
}

@Immutable
data class ContinueItem(
    val animeId: Long,
    val episodeId: Long,
    val animeTitle: String,
    val cover: AnimeCover,
    val background: String?,
    val episodeNumber: Double,
    val episodeName: String,
    val summary: String?,
    val preview: String?,
    /** Milliseconds left in the episode, 0 when unknown. */
    val remainingMs: Long,
    /** 0..1 watched. */
    val progress: Float,
    /** True when the last episode was finished and this is the one after it. */
    val isNext: Boolean,
)

/** Opens the player on the card's episode, following "always use external player". */
suspend fun openContinueEpisode(context: Context, item: ContinueItem) {
    val extPlayer = Injekt.get<PlayerPreferences>().alwaysUseExternalPlayer().get()
    MainActivity.startPlayerActivity(context, item.animeId, item.episodeId, extPlayer)
}

/** The episode to continue: the last one watched if unfinished, otherwise the next one. */
@Composable
fun rememberContinueWatching(): ContinueItem? {
    val prefs = remember { ContinueWatchingPreferences() }
    val dismissed by prefs.dismissedEpisode().collectPref()
    val item by produceState<ContinueItem?>(initialValue = null) {
        val getHistory = Injekt.get<GetHistory>()
        val getNextEpisodes = Injekt.get<GetNextEpisodes>()
        val getAnime = Injekt.get<GetAnime>()
        getHistory.subscribe("")
            .map { list -> list.firstOrNull() }
            .distinctUntilChanged { old, new -> old?.episodeId == new?.episodeId && old?.seenAt == new?.seenAt }
            .collectLatest { history ->
                value = history?.let {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val anime = getAnime.await(it.animeId) ?: return@runCatching null
                            val episode = getNextEpisodes.await(it.animeId, it.episodeId, onlyUnseen = false)
                                .firstOrNull() ?: return@runCatching null
                            val isNext = episode.id != it.episodeId
                            val total = episode.totalSeconds
                            val watched = episode.lastSecondSeen.coerceIn(0L, total.coerceAtLeast(0L))
                            ContinueItem(
                                animeId = anime.id,
                                episodeId = episode.id,
                                animeTitle = anime.title,
                                cover = anime.asAnimeCover(),
                                background = anime.backgroundUrl,
                                episodeNumber = episode.episodeNumber,
                                episodeName = episode.name,
                                summary = episode.summary,
                                preview = episode.previewUrl,
                                remainingMs = if (!isNext && total > 0) total - watched else 0L,
                                progress = if (!isNext && total > 0) watched.toFloat() / total else 0f,
                                isNext = isNext,
                            )
                        }.getOrNull()
                    }
                }
            }
    }
    return item?.takeIf { it.episodeId != dismissed }
}

/**
 * Liquid glass card for the start screen: the anime's poster, the episode still fading into the
 * glass, season and episode, minutes left and a play button.
 */
@Composable
fun ContinueWatchingCard(
    item: ContinueItem,
    backdrop: Backdrop?,
    onPlay: () -> Unit,
    onOpenAnime: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.animeId, item.episodeId) {
        EpisodeMetadata.ensure(item.animeId, item.animeTitle, listOf(item.episodeNumber to item.episodeName))
    }
    val show = rememberShowMeta(item.animeId)
    val display = rememberEpisodeDisplay(
        animeId = item.animeId,
        episodeNumber = item.episodeNumber,
        extensionName = item.episodeName,
        extensionSummary = item.summary,
        extensionPreview = item.preview,
        previewSize = "w780",
    )
    val image: Any? = display.previewUrl ?: show?.backdropUrl ?: item.background ?: item.cover
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight())
            .glass(shape, backdrop = backdrop)
            .clip(shape)
            .combinedClickable(onClick = onPlay, onLongClick = onOpenAnime),
    ) {
        // The episode still on the right, melting into the glass; the text never sits on it.
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = 0.22f))
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Transparent, 0.6f to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 26.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(YMR.strings.continue_watching),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 78.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(if (item.isNext) YMR.strings.continue_next else YMR.strings.continue_watching),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    item.animeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(display.label, display.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (!item.isNext && item.remainingMs > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(item.progress.coerceIn(0.02f, 1f))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        val minutes = ((item.remainingMs + 59_999) / 60_000).toInt()
                        Text(
                            stringResource(YMR.strings.continue_minutes_left, minutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {
                    display.runtimeMinutes?.takeIf { it > 0 }?.let { runtime ->
                        Text(
                            stringResource(YMR.strings.continue_runtime, runtime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
