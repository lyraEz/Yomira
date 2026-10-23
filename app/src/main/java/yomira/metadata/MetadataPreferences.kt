package yomira.metadata

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings → Player / Appearance: episode metadata from TMDB and skip times. */
class MetadataPreferences(private val store: PreferenceStore = Injekt.get()) {

    /** TMDB API key (v3) or API read access token (v4). Empty: tracker key or built-in key. */
    fun tmdbKey() = store.getString("yomira_tmdb_key", "")

    /** Use TMDB for episode names, descriptions, thumbnails and the title logo. */
    fun tmdbEnabled() = store.getBoolean("yomira_tmdb_enabled", true)

    /** Replace generic names such as "Episódio 3" with the real episode titles. */
    fun episodeNames() = store.getBoolean("yomira_tmdb_episode_names", true)

    /** Episode stills when the extension does not provide a thumbnail. */
    fun episodeThumbnails() = store.getBoolean("yomira_tmdb_thumbnails", true)

    /** Fill titles and descriptions that have no Portuguese translation with the English ones. */
    fun englishFallback() = store.getBoolean("yomira_tmdb_english_fallback", true)

    /** Language of names and descriptions (TMDB code). */
    fun language() = store.getString("yomira_tmdb_language", "pt-BR")

    /** Anime Skip (anime-skip.com) API client id; empty disables that source. */
    fun animeSkipClientId() = store.getString("yomira_anime_skip_client_id", "")

    /** Look skip times up by title when the anime is not tracked on MyAnimeList or AniList. */
    fun skipWithoutTracking() = store.getBoolean("yomira_skip_without_tracking", true)

    /** Manual TMDB match for one anime: "tv|movie:id:season:offset" (season -1 = automatic). */
    fun tmdbOverride(animeId: Long) = store.getString("yomira_tmdb_override_$animeId", "")
}

/** Which keys are actually used: the one typed in the app, then the tracker's, then the built-in one. */
object MetadataKeys {

    fun tmdb(prefs: MetadataPreferences = MetadataPreferences()): String {
        prefs.tmdbKey().get().trim().takeIf { it.isNotEmpty() }?.let { return it }
        val trackerKey = runCatching {
            Injekt.get<TrackPreferences>().trackApiKey(Injekt.get<TrackerManager>().tmdb).get().trim()
        }.getOrDefault("")
        if (trackerKey.isNotEmpty()) return trackerKey
        return BuildConfig.YOMIRA_TMDB_KEY
    }

    fun animeSkip(prefs: MetadataPreferences = MetadataPreferences()): String =
        prefs.animeSkipClientId().get().trim().ifEmpty { BuildConfig.YOMIRA_ANIME_SKIP_CLIENT_ID }

    /** "••••1a2b" for showing a stored key without revealing it. */
    fun masked(key: String): String = if (key.length <= 4) "••••" else "••••" + key.takeLast(4)
}

/** A manual TMDB match chosen in the anime screen. */
data class TmdbOverride(val isMovie: Boolean, val tmdbId: Long, val season: Int, val offset: Int) {

    fun encode(): String = "${if (isMovie) "movie" else "tv"}:$tmdbId:$season:$offset"

    companion object {
        fun decode(raw: String): TmdbOverride? {
            val parts = raw.split(':')
            if (parts.size != 4) return null
            val id = parts[1].toLongOrNull() ?: return null
            return TmdbOverride(
                isMovie = parts[0] == "movie",
                tmdbId = id,
                season = parts[2].toIntOrNull() ?: -1,
                offset = parts[3].toIntOrNull() ?: 0,
            )
        }
    }
}
