package yomira.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.source.interactor.GetRemoteAnime
import tachiyomi.presentation.core.i18n.stringResource
import yomira.glass.LocalFloatingBarInset
import yomira.i18n.YMR
import yomira.metadata.EpisodeDisplays
import yomira.metadata.ShowMeta
import yomira.metadata.rememberEpisodeDisplay
import yomira.metadata.rememberShowMeta
import yomira.ui.ContinueItem
import yomira.ui.openContinueEpisode

private val Shade = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 12f)

/** "Início": a streaming-style catalog of the library and of the pinned sources. */
data object HomeCatalogTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 9u,
            title = stringResource(YMR.strings.home_tab),
            icon = rememberVectorPainter(Icons.Outlined.Home),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val model = rememberScreenModel { HomeCatalogScreenModel() }
        val state by model.state.collectAsState()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            LocalFloatingBarInset.current + 16.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = bottom),
        ) {
            if (state.hero.isNotEmpty()) {
                item(key = "hero") {
                    HeroCarousel(state.hero) { navigator.push(AnimeScreen(it.id)) }
                }
            } else if (state.loaded) {
                item(key = "empty") { EmptyHome() }
            }
            if (state.continueItems.isNotEmpty()) {
                item(key = "continue") {
                    Section(stringResource(YMR.strings.continue_watching)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.continueItems, key = { it.episodeId }) { item ->
                                ContinueCard(
                                    item = item,
                                    onClick = { scope.launch { openContinueEpisode(context, item) } },
                                    onLongClick = { navigator.push(AnimeScreen(item.animeId)) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.newEpisodes.isNotEmpty()) {
                item(key = "new") {
                    val episodeBadge = stringResource(YMR.strings.home_episode_badge)
                    Section(stringResource(YMR.strings.home_new_episodes)) {
                        PosterRow(
                            items = state.newEpisodes.map { it.anime },
                            badge = { anime ->
                                state.newEpisodes.firstOrNull { it.anime.id == anime.id }?.let {
                                    EpisodeDisplays.displayNumber(it.episodeNumber, it.episodeName)
                                        ?.let { number -> episodeBadge.format(number) }
                                }
                            },
                            onClick = { navigator.push(AnimeScreen(it.id)) },
                        )
                    }
                }
            }
            if (state.recentlyAdded.isNotEmpty()) {
                item(key = "library") {
                    Section(stringResource(YMR.strings.home_library)) {
                        PosterRow(items = state.recentlyAdded, onClick = { navigator.push(AnimeScreen(it.id)) })
                    }
                }
            }
            state.sourceRows.forEach { row ->
                item(key = "source-${row.sourceId}-${row.latest}") {
                    Section(
                        title = stringResource(
                            if (row.latest) YMR.strings.home_source_latest else YMR.strings.home_source_popular,
                            row.sourceName,
                        ),
                        onMore = {
                            navigator.push(
                                BrowseSourceScreen(
                                    row.sourceId,
                                    if (row.latest) GetRemoteAnime.QUERY_LATEST else GetRemoteAnime.QUERY_POPULAR,
                                ),
                            )
                        },
                    ) {
                        PosterRow(items = row.items, onClick = { navigator.push(AnimeScreen(it.id)) })
                    }
                }
            }
        }
    }
}

/** Big swipeable highlights with the title logo, genres and a details button. */
@Composable
private fun HeroCarousel(items: List<Anime>, onOpen: (Anime) -> Unit) {
    val pager = rememberPagerState { items.size }
    // Turns by itself, but not while the user is dragging it.
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(items.size, dragged) {
        if (dragged || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(HERO_INTERVAL_MS)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val height = (LocalConfiguration.current.screenHeightDp * 0.66f).coerceIn(440f, 700f).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Color.Black),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            HeroPage(items[page], onOpen)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val selected = index == pager.currentPage
                val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                val color by animateColorAsState(
                    if (selected) Color.White else Color.White.copy(alpha = 0.45f),
                    label = "dotColor",
                )
                Box(
                    Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun HeroPage(anime: Anime, onOpen: (Anime) -> Unit) {
    val show = rememberShowMeta(anime.id)
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = show?.keyArtUrl ?: show?.backdropUrl ?: anime.backgroundUrl ?: anime.asAnimeCover(),
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onOpen(anime) },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        0.14f to Color.Transparent,
                        0.5f to Color.Transparent,
                        0.8f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.95f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroTitle(anime, show)
            val line = heroLine(anime, show)
            if (line.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    line,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = Shade),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(YMR.strings.home_see_details),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onOpen(anime) }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun HeroTitle(anime: Anime, show: ShowMeta?) {
    var failed by remember(show?.logoUrl) { mutableStateOf(false) }
    val logo = show?.logoUrl
    if (logo != null && !failed) {
        AsyncImage(
            model = logo,
            contentDescription = anime.title,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 110.dp),
        )
    } else {
        Text(
            anime.title,
            style = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, shadow = Shade),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Série • Animação • 2013–" from TMDB when known, otherwise the extension's genres. */
private fun heroLine(anime: Anime, show: ShowMeta?): String {
    val details = show?.details
    val genres = details?.genres?.takeIf { it.isNotEmpty() } ?: anime.genre.orEmpty()
    val first = details?.firstAirDate?.take(4)
    val last = details?.lastAirDate?.take(4)
    val years = when {
        first == null -> null
        details.status == "Returning Series" -> "$first–"
        last != null && last != first -> "$first–$last"
        else -> first
    }
    return (genres.take(2) + listOfNotNull(years)).joinToString("  •  ")
}

@Composable
private fun Section(title: String, onMore: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onMore != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        content()
    }
}

/** Landscape card: episode still, minutes left, "T1 E2", anime and episode names, progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val show = rememberShowMeta(item.animeId)
    val display = rememberEpisodeDisplay(
        animeId = item.animeId,
        episodeNumber = item.episodeNumber,
        extensionName = item.episodeName,
        extensionSummary = item.summary,
        extensionPreview = item.preview,
        previewSize = "w780",
    )
    Box(
        modifier = Modifier
            .width(270.dp)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1C1C22))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = display.previewUrl ?: show?.backdropUrl ?: item.background ?: item.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))),
        )
        if (item.remainingMs > 0) {
            val minutes = ((item.remainingMs + 59_999) / 60_000).toInt()
            Text(
                stringResource(YMR.strings.home_minutes_left, minutes),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else if (item.isNext) {
            Text(
                stringResource(YMR.strings.continue_next),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            Text(display.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                item.animeTitle,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, shadow = Shade),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            display.name?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.progress > 0f) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(72.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress.coerceIn(0.04f, 1f))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterRow(
    items: List<Anime>,
    onClick: (Anime) -> Unit,
    badge: (Anime) -> String? = { null },
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { anime ->
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onClick(anime) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = anime.asAnimeCover(),
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    badge(anime)?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Home, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(YMR.strings.home_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HERO_INTERVAL_MS = 7_000L
