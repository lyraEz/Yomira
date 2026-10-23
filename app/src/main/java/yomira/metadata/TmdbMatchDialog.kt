package yomira.metadata

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

/** Opens the "TMDB data" dialog from the anime screen's menu; null hides the menu entry. */
val LocalTmdbMatchAction = staticCompositionLocalOf<(() -> Unit)?> { null }

@Immutable
private data class TmdbResult(
    val id: Long,
    val isMovie: Boolean,
    val name: String,
    val year: String?,
    val posterPath: String?,
)

@Immutable
private data class SeasonChoice(val number: Int, val episodeCount: Int)

/**
 * Lets the user pick the right TMDB show, season and numbering for an anime when the automatic
 * match is wrong. Saves a [TmdbOverride]; [onChanged] reloads the data.
 */
@Composable
fun TmdbMatchDialog(
    animeId: Long,
    animeTitle: String,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val prefs = remember { MetadataPreferences() }
    val key = remember { MetadataKeys.tmdb(prefs) }
    val language = remember { prefs.language().get() }
    val current = rememberShowMeta(animeId)
    val saved = remember { TmdbOverride.decode(prefs.tmdbOverride(animeId).get()) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(TitleMatch.forShowSearch(animeTitle).first) }
    var results by remember { mutableStateOf<List<TmdbResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(
            current?.let { TmdbResult(it.tmdbId, it.isMovie, it.name, null, it.posterPath) },
        )
    }
    var seasons by remember { mutableStateOf<List<SeasonChoice>>(emptyList()) }
    var season by remember { mutableIntStateOf(saved?.season ?: -1) }
    var offset by remember { mutableIntStateOf(saved?.offset ?: 0) }

    fun search() {
        if (key.isBlank() || query.isBlank()) return
        loading = true
        failed = false
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val client = TmdbClient(key)
                    val tv = client.searchTv(query, language).take(8).mapNotNull { it.toResult(movie = false) }
                    val movies = client.searchMovie(query, language).take(4).mapNotNull { it.toResult(movie = true) }
                    tv + movies
                }.getOrNull()
            }
            loading = false
            failed = found == null
            results = found.orEmpty()
        }
    }

    LaunchedEffect(Unit) { if (current == null) search() }
    LaunchedEffect(selected?.id, selected?.isMovie) {
        val choice = selected
        seasons = emptyList()
        if (choice == null || choice.isMovie || key.isBlank()) return@LaunchedEffect
        seasons = withContext(Dispatchers.IO) {
            runCatching {
                TmdbClient(key).tv(choice.id, language)?.array("seasons").orEmpty().mapNotNull { item ->
                    val number = item.int("season_number") ?: return@mapNotNull null
                    val count = item.int("episode_count") ?: 0
                    SeasonChoice(number, count).takeIf { count > 0 }
                }
            }.getOrDefault(emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(YMR.strings.tmdb_match_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (key.isBlank()) {
                    Text(stringResource(YMR.strings.tmdb_match_missing_key))
                    return@Column
                }
                Text(
                    text = current?.name?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(YMR.strings.tmdb_match_current, it) }
                        ?: stringResource(YMR.strings.tmdb_match_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(YMR.strings.tmdb_match_search)) },
                    trailingIcon = {
                        IconButton(onClick = ::search) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(MR.strings.action_search))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) {
                    Text(stringResource(YMR.strings.meta_key_offline), color = MaterialTheme.colorScheme.error)
                }
                val list = results
                if (list != null && list.isEmpty() && !failed && !loading) {
                    Text(stringResource(YMR.strings.tmdb_match_no_results))
                }
                val shown = list ?: listOfNotNull(selected)
                shown.forEach { result ->
                    ResultRow(
                        result = result,
                        selected = selected?.id == result.id && selected?.isMovie == result.isMovie,
                        onClick = {
                            if (selected?.id != result.id || selected?.isMovie != result.isMovie) {
                                season = -1
                                offset = 0
                            }
                            selected = result
                        },
                    )
                }
                val choice = selected
                if (choice != null && !choice.isMovie) {
                    Text(
                        stringResource(YMR.strings.tmdb_match_season),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = season < 0,
                            onClick = { season = -1 },
                            label = { Text(stringResource(YMR.strings.tmdb_match_season_auto)) },
                        )
                        seasons.forEach { item ->
                            FilterChip(
                                selected = season == item.number,
                                onClick = { season = item.number },
                                label = {
                                    Text(
                                        if (item.number == 0) {
                                            stringResource(YMR.strings.tmdb_match_specials)
                                        } else {
                                            stringResource(YMR.strings.tmdb_match_season_item, item.number, item.episodeCount)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        stringResource(YMR.strings.tmdb_match_offset),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { offset = (offset - 1).coerceAtLeast(-999) }) {
                            Icon(Icons.Outlined.Remove, contentDescription = null)
                        }
                        Text(
                            if (offset > 0) "+$offset" else offset.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(56.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { offset = (offset + 1).coerceAtMost(999) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    }
                    Text(
                        stringResource(YMR.strings.tmdb_match_offset_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank() && selected != null,
                onClick = {
                    val choice = selected ?: return@TextButton
                    prefs.tmdbOverride(animeId).set(
                        TmdbOverride(choice.isMovie, choice.id, if (choice.isMovie) -1 else season, offset).encode(),
                    )
                    EpisodeMetadata.matchChanged(animeId)
                    onChanged()
                    onDismiss()
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            Row {
                if (saved != null) {
                    TextButton(
                        onClick = {
                            prefs.tmdbOverride(animeId).delete()
                            EpisodeMetadata.matchChanged(animeId)
                            onChanged()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(YMR.strings.tmdb_match_automatic))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ResultRow(result: TmdbResult, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = TmdbClient.image(result.posterPath, "w92"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(40.dp)
                    .height(60.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    stringResource(if (result.isMovie) YMR.strings.tmdb_match_movie else YMR.strings.tmdb_match_series),
                    result.year,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun kotlinx.serialization.json.JsonObject.toResult(movie: Boolean): TmdbResult? {
    val id = long("id") ?: return null
    val name = string(if (movie) "title" else "name") ?: string(if (movie) "original_title" else "original_name")
        ?: return null
    val date = string(if (movie) "release_date" else "first_air_date")
    return TmdbResult(id, movie, name, date?.take(4), string("poster_path"))
}
