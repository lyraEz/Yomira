package yomira.anime4k

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.presentation.core.i18n.stringResource
import yomira.i18n.YMR
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Settings → Anime4K: Enhanced Downloads and how processing runs. */
object SettingsAnime4KScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = YMR.strings.a4k_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Anime4KPreferences() }
        val navigator = LocalNavigator.currentOrThrow
        val downloads by prefs.enhancedDownloads().collectPref()
        val jobs by EnhanceJobs.jobs.collectAsState()
        val presets = presetEntries()
        val outputs = persistentMapOf(
            OutputSize.ORIGINAL.key to stringResource(YMR.strings.a4k_output_original),
            OutputSize.P1080.key to stringResource(YMR.strings.a4k_output_1080),
            OutputSize.P1440.key to stringResource(YMR.strings.a4k_output_1440),
            OutputSize.DOUBLE.key to stringResource(YMR.strings.a4k_output_double),
        )
        val active = jobs.count { it.state.isActive || it.state == JobState.PAUSED }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(YMR.strings.a4k_title),
                preferenceItems = listOf(
                    Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.a4k_info)),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(YMR.strings.a4k_queue_title),
                        subtitle = stringResource(YMR.strings.a4k_queue_summary, active),
                        onClick = { navigator.push(Anime4KQueueScreen()) },
                    ),
                ).toImmutableList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(YMR.strings.a4k_group_downloads),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.enhancedDownloads(),
                        title = stringResource(YMR.strings.a4k_downloads_enabled),
                        subtitle = stringResource(YMR.strings.a4k_downloads_enabled_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.downloadChoice(),
                        title = stringResource(YMR.strings.a4k_downloads_choice),
                        entries = persistentMapOf(
                            "ask" to stringResource(YMR.strings.a4k_downloads_choice_ask),
                            "always" to stringResource(YMR.strings.a4k_downloads_choice_always),
                        ),
                        enabled = downloads,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.downloadPreset(),
                        title = stringResource(YMR.strings.a4k_preset),
                        entries = presets,
                        enabled = downloads,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.outputSize(),
                        title = stringResource(YMR.strings.a4k_output),
                        subtitle = stringResource(YMR.strings.a4k_output_summary),
                        subtitleProvider = { value, entries -> "${entries[value]} · ${stringResource(YMR.strings.a4k_output_summary)}" },
                        entries = outputs,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.codec(),
                        title = stringResource(YMR.strings.a4k_codec),
                        entries = persistentMapOf(
                            OutputCodec.HEVC.key to stringResource(YMR.strings.a4k_codec_hevc),
                            OutputCodec.AVC.key to stringResource(YMR.strings.a4k_codec_avc),
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.exportQuality(),
                        title = stringResource(YMR.strings.a4k_export_quality),
                        entries = persistentMapOf(
                            ExportQuality.BALANCED.key to stringResource(YMR.strings.a4k_export_balanced),
                            ExportQuality.HIGH.key to stringResource(YMR.strings.a4k_export_high),
                            ExportQuality.MAXIMUM.key to stringResource(YMR.strings.a4k_export_max),
                        ),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.keepOriginal(),
                        title = stringResource(YMR.strings.a4k_keep_original),
                        subtitle = stringResource(YMR.strings.a4k_keep_original_summary),
                        enabled = downloads,
                    ),
                ).toImmutableList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(YMR.strings.a4k_group_processing),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.onlyWhileCharging(),
                        title = stringResource(YMR.strings.a4k_only_charging),
                        subtitle = stringResource(YMR.strings.a4k_only_charging_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.pauseWhenHot(),
                        title = stringResource(YMR.strings.a4k_pause_hot),
                        subtitle = stringResource(YMR.strings.a4k_pause_hot_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.prioritizePlayer(),
                        title = stringResource(YMR.strings.a4k_prioritize_player),
                        subtitle = stringResource(YMR.strings.a4k_prioritize_player_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.storageLimitGb(),
                        title = stringResource(YMR.strings.a4k_storage_limit),
                        entries = gbEntries(listOf(4, 8, 12, 20, 32, 64)),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.cleanTemporary(),
                        title = stringResource(YMR.strings.a4k_clean_temp),
                        subtitle = stringResource(YMR.strings.a4k_clean_temp_summary),
                    ),
                ).toImmutableList(),
            ),
        )
    }

    @Composable
    internal fun presetEntries(): ImmutableMap<String, String> {
        val fast = stringResource(YMR.strings.a4k_quality_fast)
        val high = stringResource(YMR.strings.a4k_quality_high)
        return Anime4KPreset.ALL.associate { preset ->
            preset.id to "${preset.mode.label} · ${if (preset.quality == Anime4KQuality.HIGH) high else fast}"
        }.toImmutableMap()
    }

    @Composable
    private fun gbEntries(values: List<Int>): ImmutableMap<Int, String> =
        values.associateWith { stringResource(YMR.strings.a4k_gb, it) }.toImmutableMap()
}
