package yomira.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import yomira.ui.DefaultImageTone
import yomira.ui.rememberImageTone
import java.util.Locale

private val Amber = Color(0xFFF5C518)
private val HeroShadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 12f)

/**
 * Top of the details screen: the key art across the screen, the title logo (or the name), the
 * genres and the play, library and more buttons. The art fades into a tone taken from it and
 * then into the page, with no hard edge.
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
    val tone by animateColorAsState(
        rememberImageTone(image) ?: DefaultImageTone,
        animationSpec = tween(TONE_FADE_MS),
        label = "heroTone",
    )
    val page = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .drawBehind { drawRect(tone) },
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
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.16f to Color.Transparent,
                                0.4f to Color.Transparent,
                                0.66f to tone.copy(alpha = 0.72f),
                                0.84f to tone.copy(alpha = 0.95f),
                                1f to tone,
                            ),
                        )
                    },
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
        // The tone melts into the page below the buttons.
        Box(
            Modifier
                .fillMaxWidth()
                .height(HeroTail)
                .drawBehind { drawRect(Brush.verticalGradient(listOf(tone, page))) },
        )
    }
}

/** Height of the fade from the artwork's tone into the page. */
val HeroTail = 56.dp
private const val TONE_FADE_MS = 600

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
            .padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 16.dp),
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

/** What an episode card shows. */
@Immutable
data class EpisodeCardData(
    /** "T1 E2", or "Episódio 2" when the season is unknown. */
    val label: String,
    val title: String,
    val summary: String?,
    /** Episode still; null draws a compact card without a picture. */
    val image: Any?,
    val rating: String?,
    /** Date, scanlator, time watched: shown after the rating, separated by dots. */
    val details: List<String>,
    /** 0..1, 0 when not started. */
    val progress: Float,
    val seen: Boolean,
    val bookmarked: Boolean,
    /** Spoiler protection: blur the still. */
    val blurImage: Boolean = false,
)

/**
 * An episode of the details list: the still across the card with the season and episode, name,
 * synopsis, rating and date over it. [trailing] holds the download button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeCard(
    data: EpisodeCardData,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val hasImage = data.image != null
    val shape = RoundedCornerShape(22.dp)
    val primary = MaterialTheme.colorScheme.primary
    val textColor = if (hasImage) Color.White else MaterialTheme.colorScheme.onSurface
    val softColor = if (hasImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (hasImage) Modifier.aspectRatio(16f / 9f) else Modifier)
            .clip(shape)
            .background(
                if (hasImage) {
                    Brush.verticalGradient(listOf(Color(0xFF1C1C22), Color(0xFF1C1C22)))
                } else {
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer),
                    )
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (hasImage) {
            AsyncImage(
                model = data.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (data.blurImage) Modifier.blur(18.dp) else Modifier),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = if (data.seen) 0.5f else 0.28f),
                            0.32f to Color.Black.copy(alpha = if (data.seen) 0.5f else 0.12f),
                            0.55f to Color.Black.copy(alpha = if (data.seen) 0.6f else 0.35f),
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .then(if (hasImage) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasImage) Color.Black.copy(alpha = 0.55f) else primary.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(6.dp))
                if (data.seen) CardIcon(Icons.Filled.CheckCircle, hasImage)
                if (data.bookmarked) CardIcon(Icons.Filled.Bookmark, hasImage)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (hasImage) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    trailing()
                }
            }
            if (hasImage) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(8.dp))
            Text(
                data.title,
                color = textColor.copy(alpha = if (data.seen) 0.75f else 1f),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp),
            )
            if (!data.summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    data.summary,
                    color = softColor,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            if (data.rating != null || data.details.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                    if (data.rating != null) {
                        Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(data.rating, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (data.details.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        data.details.joinToString("  •  "),
                        color = softColor,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (data.progress > 0f && !data.seen) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(textColor.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(data.progress.coerceIn(0.02f, 1f))
                        .background(primary),
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(primary.copy(alpha = 0.22f))
                    .border(3.dp, primary, shape),
            )
        }
    }
}

@Composable
private fun CardIcon(icon: ImageVector, onImage: Boolean) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (onImage) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (onImage) Color.White else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
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
