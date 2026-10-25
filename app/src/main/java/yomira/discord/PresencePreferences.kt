package yomira.discord

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings → Connections → Discord, and the Discord card in the player. */
class PresencePreferences(private val store: PreferenceStore = Injekt.get()) {

    /** Publish what is playing on Discord. Off until the user turns it on. */
    fun enabled() = store.getBoolean("yomira_rpc_enabled", false)

    /** Which Discord application the card belongs to: [APP_CRUNCHYROLL] or [APP_CUSTOM]. */
    fun app() = store.getString("yomira_rpc_app", APP_CRUNCHYROLL)

    /** Application ID typed by the user (Discord Developer Portal) for [APP_CUSTOM]. */
    fun customAppId() = store.getString("yomira_rpc_app_id", "")

    /** Name in the card header; empty = "Crunchyroll" or "Yomira", after the card style. */
    fun appName() = store.getString("yomira_rpc_app_name", "")

    /** The header name actually used. */
    fun effectiveAppName(): String = appName().get().trim().ifEmpty {
        if (app().get() == APP_CUSTOM) "Yomira" else "Crunchyroll"
    }

    /** "watching", "playing" or "listening". */
    fun activityType() = store.getString("yomira_rpc_type", "watching")

    /** What the member list shows after "Watching": "name" (the app), "details" or "state". */
    fun statusDisplay() = store.getString("yomira_rpc_status_display", "name")

    /** Anime title: "tmdb", "english", "romaji" or "extension" (without "Dublado" and similar). */
    fun titleSource() = store.getString("yomira_rpc_title", "tmdb")

    /** First line: "anime", "episode" or "none". */
    fun detailsLine() = store.getString("yomira_rpc_details", "anime")

    /** Second line: "episode", "overview", "number", "source" or "none". */
    fun stateLine() = store.getString("yomira_rpc_state", "episode")

    /** Episode names from TMDB instead of the extension's ("Episódio 3"). */
    fun tmdbEpisodeNames() = store.getBoolean("yomira_rpc_episode_tmdb", true)

    /** "Season N, Episode M" in the large image text, which Discord shows as the S#E# badge. */
    fun badge() = store.getBoolean("yomira_rpc_badge", true)

    /** Large image: "poster", "episode" or "none". */
    fun largeImage() = store.getString("yomira_rpc_large", "poster")

    /** Small image over the large one: "none", "poster" or "episode". */
    fun smallImage() = store.getString("yomira_rpc_small", "none")

    /** Time while playing: "bar" (progress bar), "elapsed" or "none". */
    fun timeMode() = store.getString("yomira_rpc_time", "bar")

    /** When paused: "frozen" (⏸ position / length), "keep" (same card without time) or "hide". */
    fun pausedMode() = store.getString("yomira_rpc_paused", "frozen")

    /** Title and cover open the anime's page (AniList, otherwise TMDB) when clicked. */
    fun links() = store.getBoolean("yomira_rpc_links", true)

    fun button(index: Int) = store.getString("yomira_rpc_button$index", if (index == 1) BUTTON_CRUNCHYROLL else BUTTON_NONE)
    fun buttonLabel(index: Int) = store.getString("yomira_rpc_button${index}_label", "")
    fun buttonUrl(index: Int) = store.getString("yomira_rpc_button${index}_url", "")

    /** "all" extensions or only the "selected" ones in [sources]. */
    fun sourceMode() = store.getString("yomira_rpc_sources_mode", "all")
    fun sources() = store.getStringSet("yomira_rpc_sources", emptySet())

    /** Library categories that may be shown; empty = all of them. */
    fun categories() = store.getStringSet("yomira_rpc_categories", emptySet())

    /** Anime that are not in the library (watched from Browse). */
    fun outsideLibrary() = store.getBoolean("yomira_rpc_outside_library", true)

    /** Never show anime from extensions marked 18+. */
    fun hideNsfw() = store.getBoolean("yomira_rpc_hide_nsfw", true)

    /** Anime hidden from Discord one by one (from the player). */
    fun hiddenAnime() = store.getStringSet("yomira_rpc_hidden_anime", emptySet())

    /** The Discord application ID to use, or null when the typed one is not valid. */
    fun applicationId(): Long? = when (app().get()) {
        APP_CUSTOM -> customAppId().get().trim().toLongOrNull()?.takeIf { it > 0 }
        else -> CRUNCHYROLL_APP_ID
    }

    fun options(): PresenceOptions = PresenceOptions(
        appName = effectiveAppName(),
        activityType = activityType().get(),
        statusDisplay = statusDisplay().get(),
        titleSource = titleSource().get(),
        detailsLine = detailsLine().get(),
        stateLine = stateLine().get(),
        tmdbEpisodeNames = tmdbEpisodeNames().get(),
        badge = badge().get(),
        largeImage = largeImage().get(),
        smallImage = smallImage().get(),
        timeMode = timeMode().get(),
        pausedMode = pausedMode().get(),
        links = links().get(),
        buttons = (1..2).map { PresenceButtonOption(button(it).get(), buttonLabel(it).get(), buttonUrl(it).get()) },
    )

    companion object {
        /** Crunchyroll's official integration: the card with the tall cover and the S#E# badge. */
        const val CRUNCHYROLL_APP_ID = 981509069309354054L

        const val APP_CRUNCHYROLL = "crunchyroll"
        const val APP_CUSTOM = "custom"

        const val BUTTON_NONE = "none"
        const val BUTTON_CRUNCHYROLL = "crunchyroll"
        const val BUTTON_ANILIST = "anilist"
        const val BUTTON_MAL = "mal"
        const val BUTTON_TMDB = "tmdb"
        const val BUTTON_SOURCE = "source"
        const val BUTTON_CUSTOM = "custom"
    }
}
