package yomira.discord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.player.components.ExpandableCard
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import yomira.player.ChoiceRow
import yomira.player.SwitchRow
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** The Discord card of the player panel: the main options, applied to the card right away. */
@Composable
fun DiscordPresenceCard() {
    val activity = LocalContext.current as? PlayerActivity ?: return
    val prefs = remember { PresencePreferences() }
    val enabled by prefs.enabled().collectPref()
    val hidden by prefs.hiddenAnime().collectPref()
    val sourceMode by prefs.sourceMode().collectPref()
    val sources by prefs.sources().collectPref()
    val details by prefs.detailsLine().collectPref()
    val state by prefs.stateLine().collectPref()
    val display by prefs.statusDisplay().collectPref()
    val large by prefs.largeImage().collectPref()
    val time by prefs.timeMode().collectPref()
    val paused by prefs.pausedMode().collectPref()
    val badge by prefs.badge().collectPref()
    val tmdbNames by prefs.tmdbEpisodeNames().collectPref()
    val button1 by prefs.button(1).collectPref()
    val status by DiscordPresence.status.collectAsState()
    val anime by activity.viewModel.currentAnime.collectAsState()
    val source by activity.viewModel.currentSource.collectAsState()
    val installed = remember { DiscordPresence.isDiscordInstalled(activity) }
    var expanded by remember { mutableStateOf(false) }

    var block by remember { mutableStateOf(PlayerPresence.Block.None) }
    LaunchedEffect(enabled, hidden, sourceMode, sources, anime, source) {
        val current = anime ?: return@LaunchedEffect
        block = PlayerPresence.block(prefs, activity.viewModel.incognitoMode, current, source)
    }
    val apply = { PlayerPresence.refresh(activity) }

    ExpandableCard(
        isExpanded = expanded,
        onExpand = { expanded = it },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CustomIcons.Discord, null, Modifier.size(24.dp))
                Text(stringResource(YMR.strings.rpc_title))
            }
        },
        colors = panelCardsColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val statusText = when {
                !installed -> stringResource(YMR.strings.rpc_status_no_discord)
                !enabled -> stringResource(YMR.strings.rpc_status_off)
                block != PlayerPresence.Block.None -> blockLabel(block)
                else -> statusLabel(status)
            }
            SwitchRow(
                title = stringResource(YMR.strings.rpc_enabled),
                summary = statusText,
                checked = enabled,
                onChange = {
                    prefs.enabled().set(it)
                    apply()
                },
            )
            if (!enabled) return@Column

            val animeId = anime?.id?.toString()
            if (animeId != null) {
                SwitchRow(
                    title = stringResource(YMR.strings.rpc_player_this_anime),
                    summary = anime?.title.orEmpty(),
                    checked = animeId !in hidden,
                    onChange = { show ->
                        prefs.hiddenAnime().set(if (show) hidden - animeId else hidden + animeId)
                        apply()
                    },
                )
            }
            val sourceId = source?.id?.toString()
            if (sourceId != null) {
                SwitchRow(
                    title = stringResource(YMR.strings.rpc_player_this_source),
                    summary = source?.name.orEmpty(),
                    checked = sourceMode != "selected" || sourceId in sources,
                    onChange = { show ->
                        if (sourceMode != "selected") {
                            // Every other extension stays allowed.
                            val all = Injekt.get<SourceManager>().getCatalogueSources().map { it.id.toString() }.toSet()
                            prefs.sources().set(if (show) all else all - sourceId)
                            prefs.sourceMode().set("selected")
                        } else {
                            prefs.sources().set(if (show) sources + sourceId else sources - sourceId)
                        }
                        apply()
                    },
                )
            }

            val detailsKeys = listOf("anime", "episode", "none")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_details),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_line_anime),
                    stringResource(YMR.strings.rpc_line_episode),
                    stringResource(YMR.strings.rpc_line_none),
                ),
                selected = detailsKeys.indexOf(details).coerceAtLeast(0),
                onSelect = {
                    prefs.detailsLine().set(detailsKeys[it])
                    apply()
                },
            )
            val stateKeys = listOf("episode", "overview", "number", "source", "none")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_state),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_line_episode),
                    stringResource(YMR.strings.rpc_line_overview),
                    stringResource(YMR.strings.rpc_line_number),
                    stringResource(YMR.strings.rpc_line_source),
                    stringResource(YMR.strings.rpc_line_none),
                ),
                selected = stateKeys.indexOf(state).coerceAtLeast(0),
                onSelect = {
                    prefs.stateLine().set(stateKeys[it])
                    apply()
                },
            )
            val displayKeys = listOf("name", "details", "state")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_status_display),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_display_name),
                    stringResource(YMR.strings.rpc_display_details),
                    stringResource(YMR.strings.rpc_display_state),
                ),
                selected = displayKeys.indexOf(display).coerceAtLeast(0),
                onSelect = {
                    prefs.statusDisplay().set(displayKeys[it])
                    apply()
                },
            )
            val imageKeys = listOf("poster", "episode", "none")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_large),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_image_poster),
                    stringResource(YMR.strings.rpc_image_episode),
                    stringResource(YMR.strings.rpc_image_none),
                ),
                selected = imageKeys.indexOf(large).coerceAtLeast(0),
                onSelect = {
                    prefs.largeImage().set(imageKeys[it])
                    apply()
                },
            )
            val timeKeys = listOf("bar", "elapsed", "none")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_time),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_time_bar),
                    stringResource(YMR.strings.rpc_time_elapsed),
                    stringResource(YMR.strings.rpc_time_none),
                ),
                selected = timeKeys.indexOf(time).coerceAtLeast(0),
                onSelect = {
                    prefs.timeMode().set(timeKeys[it])
                    apply()
                },
            )
            val pausedKeys = listOf("frozen", "keep", "hide")
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_paused),
                summary = "",
                options = listOf(
                    stringResource(YMR.strings.rpc_paused_frozen),
                    stringResource(YMR.strings.rpc_paused_keep),
                    stringResource(YMR.strings.rpc_paused_hide),
                ),
                selected = pausedKeys.indexOf(paused).coerceAtLeast(0),
                onSelect = {
                    prefs.pausedMode().set(pausedKeys[it])
                    apply()
                },
            )
            val buttons = buttonEntries()
            val buttonKeys = buttons.keys.filter { it != PresencePreferences.BUTTON_CUSTOM || button1 == it }
            ChoiceRow(
                title = stringResource(YMR.strings.rpc_button_n, 1),
                summary = stringResource(YMR.strings.rpc_buttons_info),
                options = buttonKeys.map { buttons[it].orEmpty() },
                selected = buttonKeys.indexOf(button1).coerceAtLeast(0),
                onSelect = {
                    prefs.button(1).set(buttonKeys[it])
                    apply()
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.rpc_badge),
                summary = stringResource(YMR.strings.rpc_badge_summary),
                checked = badge,
                onChange = {
                    prefs.badge().set(it)
                    apply()
                },
            )
            SwitchRow(
                title = stringResource(YMR.strings.rpc_episode_tmdb),
                summary = stringResource(YMR.strings.rpc_episode_tmdb_summary),
                checked = tmdbNames,
                onChange = {
                    prefs.tmdbEpisodeNames().set(it)
                    apply()
                },
            )
            Text(
                stringResource(YMR.strings.rpc_player_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun blockLabel(block: PlayerPresence.Block): String = when (block) {
    PlayerPresence.Block.Incognito -> stringResource(YMR.strings.rpc_block_incognito)
    PlayerPresence.Block.HiddenAnime -> stringResource(YMR.strings.rpc_block_hidden)
    PlayerPresence.Block.Nsfw -> stringResource(YMR.strings.rpc_block_nsfw)
    PlayerPresence.Block.Source -> stringResource(YMR.strings.rpc_block_source)
    PlayerPresence.Block.Category -> stringResource(YMR.strings.rpc_block_category)
    PlayerPresence.Block.OutsideLibrary -> stringResource(YMR.strings.rpc_block_outside)
    PlayerPresence.Block.Disabled -> stringResource(YMR.strings.rpc_status_off)
    PlayerPresence.Block.None -> ""
}

/** One line in the player's More sheet: Discord on or off, and a way to the full card. */
@Composable
fun DiscordQuickRow(onOpenOptions: () -> Unit) {
    val activity = LocalContext.current as? PlayerActivity ?: return
    val prefs = remember { PresencePreferences() }
    val enabled by prefs.enabled().collectPref()
    val status by DiscordPresence.status.collectAsState()
    val installed = remember { DiscordPresence.isDiscordInstalled(activity) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(CustomIcons.Discord, null, Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(YMR.strings.rpc_enabled), style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    !installed -> stringResource(YMR.strings.rpc_status_no_discord)
                    !enabled -> stringResource(YMR.strings.rpc_status_off)
                    else -> statusLabel(status)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onOpenOptions) { Text(stringResource(YMR.strings.rpc_options)) }
        Switch(
            checked = enabled,
            onCheckedChange = {
                prefs.enabled().set(it)
                PlayerPresence.refresh(activity)
            },
        )
    }
}
