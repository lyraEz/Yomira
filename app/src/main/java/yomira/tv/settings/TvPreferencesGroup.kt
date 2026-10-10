package yomira.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.TvDevice
import yomira.i18n.YMR
import yomira.tv.TvSettings

/** "TV" group in Settings → Appearance. */
@Composable
fun tvPreferencesGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val detected = TvSettings.detect(context)
    return Preference.PreferenceGroup(
        title = stringResource(YMR.strings.tv_settings),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.ListPreference(
                pref = TvSettings.mode(),
                title = stringResource(YMR.strings.tv_mode),
                entries = persistentMapOf(
                    TvSettings.Mode.AUTO to stringResource(
                        YMR.strings.tv_mode_auto,
                        stringResource(if (detected) YMR.strings.tv_mode_tv else YMR.strings.tv_mode_phone),
                    ),
                    TvSettings.Mode.TV to stringResource(YMR.strings.tv_mode_tv),
                    TvSettings.Mode.PHONE to stringResource(YMR.strings.tv_mode_phone),
                ),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = TvSettings.textScalePercent(),
                title = stringResource(YMR.strings.tv_text_scale),
                enabled = TvDevice.isTv,
                entries = persistentMapOf(
                    100 to "100%",
                    115 to "115%",
                    130 to "130%",
                    150 to "150%",
                ),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = TvSettings.safeAreaPercent(),
                title = stringResource(YMR.strings.tv_safe_area),
                subtitle = stringResource(YMR.strings.tv_safe_area_summary),
                enabled = TvDevice.isTv,
                entries = persistentMapOf(
                    0 to stringResource(YMR.strings.tv_safe_area_none),
                    3 to "3%",
                    5 to "5%",
                    8 to "8%",
                ),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = TvSettings.menuKeyLongPress(),
                title = stringResource(YMR.strings.tv_menu_long_press),
                subtitle = stringResource(YMR.strings.tv_menu_long_press_summary),
                enabled = TvDevice.isTv,
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.tv_restart_note)),
        ),
    )
}
