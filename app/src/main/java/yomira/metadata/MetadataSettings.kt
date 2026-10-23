package yomira.metadata

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import java.io.File

/** Settings → Player → "Metadata and skipping": TMDB and Anime Skip keys and options. */
@Composable
fun metadataPreferencesGroup(): Preference.PreferenceGroup {
    val prefs = remember { MetadataPreferences() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typedKey by prefs.tmdbKey().collectAsState()
    val typedClient by prefs.animeSkipClientId().collectAsState()
    val tmdbOn by prefs.tmdbEnabled().collectAsState()

    val effectiveKey = remember(typedKey) { MetadataKeys.tmdb(prefs) }
    val keySummary = when {
        typedKey.isNotBlank() -> MetadataKeys.masked(typedKey)
        effectiveKey.isNotBlank() -> stringResource(YMR.strings.meta_key_builtin)
        else -> stringResource(YMR.strings.meta_key_missing)
    }
    val effectiveClient = remember(typedClient) { MetadataKeys.animeSkip(prefs) }
    val clientSummary = when {
        typedClient.isNotBlank() -> MetadataKeys.masked(typedClient)
        effectiveClient.isNotBlank() -> stringResource(YMR.strings.meta_key_builtin)
        else -> stringResource(YMR.strings.meta_anime_skip_missing)
    }
    val keyOk = stringResource(YMR.strings.meta_key_ok)
    val keyBad = stringResource(YMR.strings.meta_key_bad)
    val keyOffline = stringResource(YMR.strings.meta_key_offline)
    val cleared = stringResource(YMR.strings.meta_cache_cleared)

    return Preference.PreferenceGroup(
        title = stringResource(YMR.strings.meta_title),
        preferenceItems = listOf(
            Preference.PreferenceItem.InfoPreference(stringResource(YMR.strings.meta_info)),
            Preference.PreferenceItem.EditTextPreference(
                pref = prefs.tmdbKey(),
                title = stringResource(YMR.strings.meta_tmdb_key),
                subtitle = keySummary,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(YMR.strings.meta_tmdb_test),
                subtitle = stringResource(YMR.strings.meta_tmdb_test_summary),
                onClick = {
                    scope.launch {
                        val message = withContext(Dispatchers.IO) {
                            val key = MetadataKeys.tmdb(prefs)
                            if (key.isBlank()) return@withContext keyBad
                            try {
                                if (TmdbClient(key).check()) keyOk else keyOffline
                            } catch (_: TmdbAuthException) {
                                keyBad
                            } catch (_: Exception) {
                                keyOffline
                            }
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.tmdbEnabled(),
                title = stringResource(YMR.strings.meta_tmdb_enabled),
                subtitle = stringResource(YMR.strings.meta_tmdb_enabled_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.episodeNames(),
                title = stringResource(YMR.strings.meta_episode_names),
                subtitle = stringResource(YMR.strings.meta_episode_names_summary),
                enabled = tmdbOn,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.episodeThumbnails(),
                title = stringResource(YMR.strings.meta_thumbnails),
                subtitle = stringResource(YMR.strings.meta_thumbnails_summary),
                enabled = tmdbOn,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.englishFallback(),
                title = stringResource(YMR.strings.meta_english_fallback),
                subtitle = stringResource(YMR.strings.meta_english_fallback_summary),
                enabled = tmdbOn,
            ),
            Preference.PreferenceItem.ListPreference(
                pref = prefs.language(),
                title = stringResource(YMR.strings.meta_language),
                entries = persistentMapOf(
                    "pt-BR" to "Português (Brasil)",
                    "pt-PT" to "Português (Portugal)",
                    "en-US" to "English",
                    "es-ES" to "Español",
                ),
                enabled = tmdbOn,
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = prefs.animeSkipClientId(),
                title = stringResource(YMR.strings.meta_anime_skip_key),
                subtitle = clientSummary,
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = prefs.skipWithoutTracking(),
                title = stringResource(YMR.strings.meta_skip_untracked),
                subtitle = stringResource(YMR.strings.meta_skip_untracked_summary),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(YMR.strings.meta_clear_cache),
                subtitle = stringResource(YMR.strings.meta_clear_cache_summary),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            EpisodeMetadata.forgetAll()
                            runCatching {
                                File(Injekt.get<Application>().filesDir, "yomira/meta").deleteRecursively()
                            }
                        }
                        Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                    }
                },
            ),
        ).toImmutableList(),
    )
}
