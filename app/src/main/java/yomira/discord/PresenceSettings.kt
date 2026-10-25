package yomira.discord

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Settings → Connections: Discord Rich Presence. */
@Composable
fun discordPresenceGroups(): List<Preference> {
    val prefs = remember { PresencePreferences() }
    val context = LocalContext.current
    val enabled by prefs.enabled().collectPref()
    val app by prefs.app().collectPref()
    val customId by prefs.customAppId().collectPref()
    val sourceMode by prefs.sourceMode().collectPref()
    val hidden by prefs.hiddenAnime().collectPref()
    val buttons = (1..2).map { prefs.button(it).collectPref().value }
    val installed = remember { DiscordPresence.isDiscordInstalled(context) }
    val status by DiscordPresence.status.collectAsState()

    val sources = remember {
        Injekt.get<SourceManager>().getCatalogueSources()
            .sortedWith(compareBy({ it.name.lowercase() }, { it.lang }))
            .associate { it.id.toString() to "${it.name} (${it.lang.uppercase()})" }
            .toImmutableMap()
    }
    val categories by remember { Injekt.get<GetCategories>().subscribe() }.collectAsState(emptyList())
    val categoryEntries = categories.associate { it.id.toString() to it.visualName }.toImmutableMap()

    val statusText = when {
        !installed -> stringResource(YMR.strings.rpc_status_no_discord)
        !enabled -> stringResource(YMR.strings.rpc_status_off)
        else -> statusLabel(status)
    }
    val customInvalid = app == PresencePreferences.APP_CUSTOM && prefs.applicationId() == null
    val cleared = stringResource(YMR.strings.rpc_hidden_cleared)
    val testEpisode = stringResource(YMR.strings.rpc_test_episode)
    val testSent = stringResource(YMR.strings.rpc_test_sent)

    val lineEntries = persistentMapOf(
        "anime" to stringResource(YMR.strings.rpc_line_anime),
        "episode" to stringResource(YMR.strings.rpc_line_episode),
        "none" to stringResource(YMR.strings.rpc_line_none),
    )
    val stateEntries = persistentMapOf(
        "episode" to stringResource(YMR.strings.rpc_line_episode),
        "overview" to stringResource(YMR.strings.rpc_line_overview),
        "number" to stringResource(YMR.strings.rpc_line_number),
        "source" to stringResource(YMR.strings.rpc_line_source),
        "none" to stringResource(YMR.strings.rpc_line_none),
    )
    val imageEntries = persistentMapOf(
        "poster" to stringResource(YMR.strings.rpc_image_poster),
        "episode" to stringResource(YMR.strings.rpc_image_episode),
        "none" to stringResource(YMR.strings.rpc_image_none),
    )
    val buttonEntries = buttonEntries()

    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_title),
            preferenceItems = listOf(
                Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.rpc_info)),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.enabled(),
                    title = stringResource(YMR.strings.rpc_enabled),
                    subtitle = stringResource(YMR.strings.rpc_enabled_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(YMR.strings.rpc_status),
                    subtitle = statusText,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(YMR.strings.rpc_test),
                    subtitle = stringResource(YMR.strings.rpc_test_summary),
                    enabled = enabled && installed && !customInvalid,
                    onClick = {
                        val activity = context.findActivity() ?: return@TextPreference
                        val appId = prefs.applicationId() ?: return@TextPreference
                        val sample = PresenceInput(
                            extensionTitle = "Yomira",
                            extensionEpisodeName = testEpisode,
                            numberLabel = testEpisode,
                            season = 1,
                            episode = 1,
                            durationSeconds = 24 * 60,
                        )
                        PresenceBuilder.build(sample, prefs.options(), PlayerPresence.labels(context))?.let {
                            DiscordPresence.test(activity, appId, it)
                            Toast.makeText(context, testSent, Toast.LENGTH_LONG).show()
                        }
                    },
                ),
            ).toImmutableList(),
        ),
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_group_look),
            enabled = enabled,
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.app(),
                    title = stringResource(YMR.strings.rpc_app),
                    entries = persistentMapOf(
                        PresencePreferences.APP_CRUNCHYROLL to stringResource(YMR.strings.rpc_app_crunchyroll),
                        PresencePreferences.APP_CUSTOM to stringResource(YMR.strings.rpc_app_custom),
                    ),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = prefs.customAppId(),
                    title = stringResource(YMR.strings.rpc_app_id),
                    subtitle = when {
                        customInvalid -> stringResource(YMR.strings.rpc_app_id_invalid)
                        customId.isNotBlank() -> customId
                        else -> stringResource(YMR.strings.rpc_app_id_summary)
                    },
                    enabled = app == PresencePreferences.APP_CUSTOM,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.activityType(),
                    title = stringResource(YMR.strings.rpc_type),
                    entries = persistentMapOf(
                        "watching" to stringResource(YMR.strings.rpc_type_watching),
                        "playing" to stringResource(YMR.strings.rpc_type_playing),
                        "listening" to stringResource(YMR.strings.rpc_type_listening),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.statusDisplay(),
                    title = stringResource(YMR.strings.rpc_status_display),
                    entries = persistentMapOf(
                        "name" to stringResource(YMR.strings.rpc_display_name),
                        "details" to stringResource(YMR.strings.rpc_display_details),
                        "state" to stringResource(YMR.strings.rpc_display_state),
                    ),
                ),
            ).toImmutableList(),
        ),
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_group_text),
            enabled = enabled,
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.titleSource(),
                    title = stringResource(YMR.strings.rpc_title_source),
                    entries = persistentMapOf(
                        "tmdb" to stringResource(YMR.strings.rpc_title_tmdb),
                        "english" to stringResource(YMR.strings.rpc_title_english),
                        "romaji" to stringResource(YMR.strings.rpc_title_romaji),
                        "extension" to stringResource(YMR.strings.rpc_title_extension),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.detailsLine(),
                    title = stringResource(YMR.strings.rpc_details),
                    entries = lineEntries,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.stateLine(),
                    title = stringResource(YMR.strings.rpc_state),
                    entries = stateEntries,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.tmdbEpisodeNames(),
                    title = stringResource(YMR.strings.rpc_episode_tmdb),
                    subtitle = stringResource(YMR.strings.rpc_episode_tmdb_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.badge(),
                    title = stringResource(YMR.strings.rpc_badge),
                    subtitle = stringResource(YMR.strings.rpc_badge_summary),
                ),
            ).toImmutableList(),
        ),
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_group_media),
            enabled = enabled,
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.largeImage(),
                    title = stringResource(YMR.strings.rpc_large),
                    entries = imageEntries,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.smallImage(),
                    title = stringResource(YMR.strings.rpc_small),
                    entries = persistentMapOf(
                        "none" to stringResource(YMR.strings.rpc_image_none),
                        "poster" to stringResource(YMR.strings.rpc_image_poster),
                        "episode" to stringResource(YMR.strings.rpc_image_episode),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.timeMode(),
                    title = stringResource(YMR.strings.rpc_time),
                    entries = persistentMapOf(
                        "bar" to stringResource(YMR.strings.rpc_time_bar),
                        "elapsed" to stringResource(YMR.strings.rpc_time_elapsed),
                        "none" to stringResource(YMR.strings.rpc_time_none),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.pausedMode(),
                    title = stringResource(YMR.strings.rpc_paused),
                    entries = persistentMapOf(
                        "frozen" to stringResource(YMR.strings.rpc_paused_frozen),
                        "keep" to stringResource(YMR.strings.rpc_paused_keep),
                        "hide" to stringResource(YMR.strings.rpc_paused_hide),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.links(),
                    title = stringResource(YMR.strings.rpc_links),
                    subtitle = stringResource(YMR.strings.rpc_links_summary),
                ),
            ).toImmutableList(),
        ),
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_group_buttons),
            enabled = enabled,
            preferenceItems = buildList {
                add(Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.rpc_buttons_info)))
                for (index in 1..2) {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            pref = prefs.button(index),
                            title = stringResource(YMR.strings.rpc_button_n, index),
                            entries = buttonEntries,
                        ),
                    )
                    if (buttons[index - 1] == PresencePreferences.BUTTON_CUSTOM) {
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = prefs.buttonLabel(index),
                                title = stringResource(YMR.strings.rpc_button_label, index),
                                subtitle = "%s",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = prefs.buttonUrl(index),
                                title = stringResource(YMR.strings.rpc_button_url, index),
                                subtitle = stringResource(YMR.strings.rpc_button_url_summary),
                            ),
                        )
                    }
                }
            }.toImmutableList(),
        ),
        Preference.PreferenceGroup(
            title = stringResource(YMR.strings.rpc_group_filters),
            enabled = enabled,
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = prefs.sourceMode(),
                    title = stringResource(YMR.strings.rpc_sources_mode),
                    entries = persistentMapOf(
                        "all" to stringResource(YMR.strings.rpc_sources_all),
                        "selected" to stringResource(YMR.strings.rpc_sources_selected),
                    ),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = prefs.sources(),
                    title = stringResource(YMR.strings.rpc_sources),
                    entries = sources,
                    enabled = sourceMode == "selected",
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = prefs.categories(),
                    title = stringResource(YMR.strings.rpc_categories),
                    entries = categoryEntries,
                    subtitleProvider = { value, entries -> categorySummary(value, entries) },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.outsideLibrary(),
                    title = stringResource(YMR.strings.rpc_outside_library),
                    subtitle = stringResource(YMR.strings.rpc_outside_library_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.hideNsfw(),
                    title = stringResource(YMR.strings.rpc_hide_nsfw),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(YMR.strings.rpc_hidden_anime),
                    subtitle = stringResource(YMR.strings.rpc_hidden_anime_summary, hidden.size),
                    enabled = hidden.isNotEmpty(),
                    onClick = {
                        prefs.hiddenAnime().set(emptySet())
                        Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                    },
                ),
            ).toImmutableList(),
        ),
    )
}

@Composable
private fun categorySummary(value: Set<String>, entries: ImmutableMap<String, String>): String {
    val names = value.mapNotNull { entries[it] }
    return if (names.isEmpty()) stringResource(YMR.strings.rpc_categories_all) else names.joinToString()
}

@Composable
internal fun buttonEntries(): ImmutableMap<String, String> = persistentMapOf(
    PresencePreferences.BUTTON_NONE to stringResource(YMR.strings.rpc_button_none),
    PresencePreferences.BUTTON_CRUNCHYROLL to stringResource(YMR.strings.rpc_button_crunchyroll),
    PresencePreferences.BUTTON_ANILIST to stringResource(YMR.strings.rpc_button_anilist),
    PresencePreferences.BUTTON_MAL to stringResource(YMR.strings.rpc_button_mal),
    PresencePreferences.BUTTON_TMDB to stringResource(YMR.strings.rpc_button_tmdb),
    PresencePreferences.BUTTON_SOURCE to stringResource(YMR.strings.rpc_button_source),
    PresencePreferences.BUTTON_CUSTOM to stringResource(YMR.strings.rpc_button_custom),
)

@Composable
internal fun statusLabel(status: PresenceStatus): String = when (status.state) {
    PresenceState.Off -> stringResource(YMR.strings.rpc_status_ready)
    PresenceState.NoDiscord -> stringResource(YMR.strings.rpc_status_no_discord)
    PresenceState.Unavailable -> stringResource(YMR.strings.rpc_status_unavailable)
    PresenceState.Connecting -> stringResource(YMR.strings.rpc_status_connecting)
    PresenceState.Active -> stringResource(YMR.strings.rpc_status_active)
    PresenceState.Paused -> stringResource(YMR.strings.rpc_status_paused)
    PresenceState.Hidden -> stringResource(YMR.strings.rpc_status_hidden)
    PresenceState.Error -> stringResource(YMR.strings.rpc_status_error, status.message.ifBlank { "?" })
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
