package eu.kanade.presentation.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EpisodeHeader(
    enabled: Boolean,
    episodeCount: Int?,
    missingEpisodeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fetchType: FetchType = FetchType.Episodes,
) {
    // episode count in a frosted capsule, with a hint that tapping it opens the filters
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(yomira.ui.GlassCard.color())
                .border(yomira.ui.GlassCard.border(), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (episodeCount == null) {
                    stringResource(MR.strings.episodes)
                } else {
                    val pluralCount = when (fetchType) {
                        FetchType.Seasons -> MR.plurals.anime_num_seasons
                        FetchType.Episodes -> MR.plurals.anime_num_episodes
                    }
                    pluralStringResource(pluralCount, count = episodeCount, episodeCount)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = stringResource(MR.strings.action_filter),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        MissingEpisodesWarning(fetchType, missingEpisodeCount)
    }
}

@Composable
private fun MissingEpisodesWarning(fetchType: FetchType, count: Int) {
    if (count == 0) {
        return
    }

    val pluralRes = when (fetchType) {
        FetchType.Seasons -> MR.plurals.missing_items // fallback since missing_seasons might not exist
        FetchType.Episodes -> MR.plurals.missing_items
    }
    Text(
        text = pluralStringResource(pluralRes, count = count, count),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = SECONDARY_ALPHA),
    )
}
