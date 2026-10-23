package yomira.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import java.util.Locale

private val Amber = Color(0xFFF5C518)
private val HeroShadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 12f)

/**
 * Top of the details screen: the key art across the screen, the title logo (or the name), the
 * genres and the play, library and more buttons.
 */
@Composable
fun DetailsHero(
    image: Any?,
    logoUrl: String?,
    title: String,
    genres: List<String>,
    playLabel: String,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onImageClick: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val height = (screenHeight * 0.64f).coerceIn(420f, 680f).dp
    val shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color.Black),
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onImageClick),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.16f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.74f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.96f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var logoFailed by remember(logoUrl) { mutableStateOf(false) }
            if (logoUrl != null && !logoFailed) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier
                        .fillMaxWidth(0.74f)
                        .heightIn(max = 112.dp),
                )
            } else {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = HeroShadow,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = genres.joinToString("  •  "),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = HeroShadow),
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onPlay),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        playLabel,
                        color = Color.Black,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                HeroCircle(
                    icon = if (favorite) Icons.Filled.Check else Icons.Filled.Add,
                    description = stringResource(if (favorite) YMR.strings.details_in_library else YMR.strings.details_add_library),
                    onClick = onToggleFavorite,
                )
                Spacer(Modifier.width(10.dp))
                HeroCircle(
                    icon = Icons.Filled.MoreHoriz,
                    description = stringResource(YMR.strings.details_more),
                    onClick = onMore,
                )
            }
        }
    }
}

@Composable
private fun HeroCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** Years, runtime and score, then who made it and the synopsis. */
@Composable
fun DetailsOverview(
    facts: DetailsFacts,
    description: String?,
    note: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val meta = listOfNotNull(facts.years, facts.runtime)
        if (meta.isNotEmpty() || facts.score != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                meta.forEach {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                if (facts.score != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            facts.scoreSource.orEmpty(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Amber)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Text(facts.score, color = Amber, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        CreditLine(stringResource(YMR.strings.details_fact_creator), facts.creators)
        CreditLine(stringResource(YMR.strings.details_fact_studio), facts.studios)
        if (!description.isNullOrBlank()) {
            var expanded by remember(description) { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .animateContentSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
            ) {
                Text(
                    description.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(if (expanded) YMR.strings.details_less else YMR.strings.details_more_text),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!note.isNullOrBlank()) {
            Text(
                note.trim(),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditLine(label: String, names: List<String>) {
    if (names.isEmpty()) return
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            names.take(3).joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A character (or an actor when there are no characters) in the cast row. */
@Immutable
data class PersonCard(
    val name: String,
    val image: String?,
    /** Voice actor or actor. */
    val otherName: String? = null,
    val otherImage: String? = null,
    /** "Dublagem · Japonês" or "Ator". */
    val otherLabel: String? = null,
)

@Composable
fun PeopleSection(title: String, people: List<PersonCard>) {
    if (people.isEmpty()) return
    var shown by remember { mutableStateOf<PersonCard?>(null) }
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = 20.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(people, key = { it.name + it.otherName }) { person ->
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { shown = person },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(person.image, person.name, 80.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    shown?.let { person -> PersonDialog(person) { shown = null } }
}

@Composable
private fun Avatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Character and voice actor (or actor) side by side, with their names. */
@Composable
private fun PersonDialog(person: PersonCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PortraitWithName(person.image, person.name, stringResource(YMR.strings.details_character))
                if (person.otherName != null) {
                    PortraitWithName(person.otherImage, person.otherName, person.otherLabel)
                }
            }
            if (person.otherName == null && person.otherLabel != null) {
                Text(
                    person.otherLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(YMR.strings.details_close))
            }
        }
    }
}

@Composable
private fun PortraitWithName(url: String?, name: String, label: String?) {
    Column(Modifier.width(128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(176.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** "Temporadas" with the view switch and the filter button, then the season pills. */
@Composable
fun SeasonsHeader(
    title: String,
    seasonLabels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    cards: Boolean,
    onToggleView: () -> Unit,
    filterActive: Boolean,
    onFilter: () -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(title, Modifier.weight(1f))
            IconButton(onClick = onToggleView) {
                Icon(
                    if (cards) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.ViewCarousel,
                    contentDescription = stringResource(if (cards) YMR.strings.details_view_list else YMR.strings.details_view_cards),
                )
            }
            IconButton(onClick = onFilter) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = stringResource(YMR.strings.details_filter),
                    tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (seasonLabels.size > 1) {
            val listState = rememberLazyListState()
            LaunchedEffect(selected) { listState.animateScrollToItem(selected.coerceAtLeast(0)) }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(seasonLabels.size) { index ->
                    val isSelected = index == selected
                    Text(
                        seasonLabels[index],
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** What an episode card shows. */
@Immutable
data class EpisodeCardData(
    val id: Long,
    val label: String,
    val title: String,
    val summary: String?,
    val image: Any?,
    val rating: String?,
    val date: String?,
    /** 0..1, 0 when not started. */
    val progress: Float,
    val seen: Boolean,
    val downloaded: Boolean,
    val bookmarked: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeCard(
    data: EpisodeCardData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(300.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1C1C22))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = data.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (data.seen) 0.45f else 0.1f),
                        0.38f to Color.Black.copy(alpha = if (data.seen) 0.55f else 0.2f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (data.bookmarked) CardIcon(Icons.Outlined.Bookmark)
            if (data.downloaded) CardIcon(Icons.Filled.DownloadDone)
            if (data.seen) CardIcon(Icons.Filled.CheckCircle)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        ) {
            Text(
                data.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                data.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!data.summary.isNullOrBlank()) {
                Text(
                    data.summary,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (data.rating != null || data.date != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.rating != null) {
                        Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(data.rating, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    if (data.date != null) {
                        Text(
                            data.date,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        if (data.progress > 0f && !data.seen) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(data.progress.coerceIn(0.02f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun CardIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Cards of one season in a row, scrolled to [startIndex]. */
@Composable
fun EpisodeCardsRow(
    cards: List<EpisodeCardData>,
    startIndex: Int,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0)))
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            EpisodeCard(card, onClick = { onClick(card.id) }, onLongClick = { onLongClick(card.id) })
        }
    }
}

/** Actions of one episode, opened by a long press on its card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeActionsSheet(
    title: String,
    seen: Boolean,
    bookmarked: Boolean,
    downloaded: Boolean,
    canDownload: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onToggleSeen: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMarkPreviousSeen: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (canDownload) {
                if (downloaded) {
                    SheetAction(Icons.Outlined.Delete, stringResource(YMR.strings.details_ep_delete)) {
                        onDeleteDownload()
                        onDismiss()
                    }
                } else {
                    SheetAction(Icons.Outlined.Download, stringResource(YMR.strings.details_ep_download)) {
                        onDownload()
                        onDismiss()
                    }
                }
            }
            SheetAction(
                if (seen) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                stringResource(if (seen) YMR.strings.details_ep_unseen else YMR.strings.details_ep_seen),
            ) {
                onToggleSeen()
                onDismiss()
            }
            SheetAction(
                if (bookmarked) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark,
                stringResource(if (bookmarked) YMR.strings.details_ep_unbookmark else YMR.strings.details_ep_bookmark),
            ) {
                onToggleBookmark()
                onDismiss()
            }
            SheetAction(Icons.Outlined.DoneAll, stringResource(YMR.strings.details_ep_previous)) {
                onMarkPreviousSeen()
                onDismiss()
            }
        }
    }
}

/** One row of a bottom sheet. */
@Composable
fun SheetAction(icon: ImageVector, label: String, trailing: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    )
}

/** "Detalhes": status, dates, studio, people, origin and country as label and value rows. */
@Composable
fun FactsSection(rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        SectionTitle(stringResource(YMR.strings.details_facts))
        Spacer(Modifier.height(8.dp))
        rows.forEachIndexed { index, (label, value) ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val half = maxWidth * 0.62f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = half),
                    )
                }
            }
            if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/** Language name in the app's language: "ja" → "Japonês". */
fun languageName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
