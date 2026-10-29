package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR

/** Watched share of an anime while it is in progress, for the line along its cover. */
internal fun LibraryItem.watchProgress(): Float? {
    val total = libraryAnime.totalEpisodes
    val seen = libraryAnime.seenCount
    return if (total > 0 && seen in 1 until total) seen.toFloat() / total else null
}

/** The line under a library card: "12 episodes", "5 of 12 watched" (accent) or "All watched". */
internal class CardStatus(val text: String, val accent: Boolean)

@Composable
internal fun LibraryItem.cardStatus(): CardStatus? {
    val total = libraryAnime.totalEpisodes.toInt()
    val seen = libraryAnime.seenCount.toInt()
    return when {
        total <= 0 -> null
        seen <= 0 -> CardStatus(pluralStringResource(YMR.plurals.library_card_episodes, total, total), accent = false)
        seen >= total -> CardStatus(stringResource(YMR.strings.library_card_done), accent = false)
        else -> CardStatus(stringResource(YMR.strings.library_card_progress, seen, total), accent = true)
    }
}

/** Category name between the results while searching the library. */
@Composable
internal fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
    )
}
