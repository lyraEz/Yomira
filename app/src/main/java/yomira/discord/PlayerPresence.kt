package yomira.discord

import android.content.Context
import androidx.lifecycle.Lifecycle
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.toSAnime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.details.EpisodeSeasons
import yomira.i18n.YMR
import yomira.metadata.AniListIds
import yomira.metadata.EpisodeDisplays
import yomira.metadata.EpisodeMetadata
import yomira.metadata.EpisodeNumbers
import yomira.metadata.MetadataPreferences
import kotlin.math.floor

/**
 * Keeps the Discord card in step with the player: called on start, resume, pause, unpause, episode
 * changes and seeks, never on every tick. Leaving the player removes the card.
 */
object PlayerPresence {

    private const val EXIT_GRACE_MS = 1_200L
    private const val SEEK_SETTLE_MS = 700L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var exitJob: Job? = null

    /** Why nothing is shown for what is playing, for the player card. */
    enum class Block { None, Disabled, Incognito, HiddenAnime, Nsfw, Source, Category, OutsideLibrary }

    @Synchronized
    fun onPlayerEvent(activity: PlayerActivity, exiting: Boolean) {
        if (exiting) {
            onLeaving(activity)
        } else {
            exitJob?.cancel()
            refresh(activity)
        }
    }

    /** A seek (or the resume position) settled: the progress bar jumps to the new place. */
    fun onSeek(activity: PlayerActivity) = refresh(activity, SEEK_SETTLE_MS)

    @Synchronized
    fun onDestroy(activity: PlayerActivity) {
        job?.cancel()
        exitJob?.cancel()
        DiscordPresence.detach(activity)
    }

    /** Sends the card for what is playing now (also after a setting changes in the player). */
    @Synchronized
    fun refresh(activity: PlayerActivity, delayMs: Long = 0) {
        job?.cancel()
        job = scope.launch {
            if (delayMs > 0) delay(delayMs)
            try {
                update(activity)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Discord: could not update the card" }
            }
        }
    }

    /** Leaving for a moment keeps the card; staying away (not in picture-in-picture) removes it. */
    @Synchronized
    private fun onLeaving(activity: PlayerActivity) {
        exitJob?.cancel()
        exitJob = scope.launch {
            delay(EXIT_GRACE_MS)
            if (activity.isInPictureInPictureMode) return@launch
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            job?.cancel()
            DiscordPresence.clear()
        }
    }

    private suspend fun update(activity: PlayerActivity) {
        val prefs = PresencePreferences()
        if (!prefs.enabled().get()) {
            DiscordPresence.detach(activity)
            return
        }
        val viewModel = activity.viewModel
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value ?: return
        val source = viewModel.currentSource.value
        if (block(prefs, viewModel.incognitoMode, anime, source) != Block.None) {
            DiscordPresence.clear(PresenceState.Hidden)
            return
        }
        val appId = prefs.applicationId() ?: return
        DiscordPresence.attach(activity, appId)

        val options = prefs.options()
        // Read from mpv itself: the flows may not have caught up with the event that called us.
        val mpv = viewModel.mpv
        val input = gather(
            activity = activity,
            anime = anime,
            episode = episode,
            source = source,
            options = options,
            position = runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()?.toLong()
                ?: viewModel.pos.value?.toLong() ?: 0L,
            duration = runCatching { mpv.getPropertyDouble("duration") }.getOrNull()?.toLong()
                ?: viewModel.duration.value?.toLong() ?: 0L,
            paused = runCatching { mpv.getPropertyBoolean("pause") }.getOrNull() ?: (viewModel.paused.value == true),
        )
        val payload = PresenceBuilder.build(input, options, labels(activity))
        if (payload == null) DiscordPresence.clear(PresenceState.Paused) else DiscordPresence.publish(payload)
    }

    suspend fun block(prefs: PresencePreferences, incognito: Boolean, anime: Anime, source: AnimeSource?): Block {
        if (!prefs.enabled().get()) return Block.Disabled
        if (incognito) return Block.Incognito
        if (anime.id.toString() in prefs.hiddenAnime().get()) return Block.HiddenAnime
        if (prefs.hideNsfw().get() && isNsfw(source)) return Block.Nsfw
        if (prefs.sourceMode().get() == "selected" && anime.source.toString() !in prefs.sources().get()) return Block.Source
        if (!anime.favorite) return if (prefs.outsideLibrary().get()) Block.None else Block.OutsideLibrary
        val allowed = prefs.categories().get()
        if (allowed.isEmpty()) return Block.None
        val categories = runCatching { Injekt.get<GetCategories>().await(anime.id).map { it.id } }.getOrDefault(emptyList())
            .ifEmpty { listOf(Category.UNCATEGORIZED_ID) }
        return if (categories.any { it.toString() in allowed }) Block.None else Block.Category
    }

    private fun isNsfw(source: AnimeSource?): Boolean {
        val id = source?.id ?: return false
        return runCatching {
            Injekt.get<ExtensionManager>().installedExtensionsFlow.value
                .firstOrNull { extension -> extension.sources.any { it.id == id } }
                ?.isNsfw == true
        }.getOrDefault(false)
    }

    private suspend fun gather(
        activity: PlayerActivity,
        anime: Anime,
        episode: Episode,
        source: AnimeSource?,
        options: PresenceOptions,
        position: Long,
        duration: Long,
        paused: Boolean,
    ): PresenceInput {
        val number = episode.episode_number.toDouble()
        val name = episode.name
        val metaPrefs = MetadataPreferences()
        EpisodeMetadata.ensure(anime.id, anime.title, listOf(number to name))
        val show = EpisodeMetadata.show(anime.id)?.takeIf { metaPrefs.tmdbEnabled().get() }
        val meta = EpisodeMetadata.episode(show, number, name)
        val located = show?.takeUnless { it.isMovie }?.let { EpisodeMetadata.locate(it, number, name) }
            ?: EpisodeNumbers.packed(number)
            ?: EpisodeSeasons.seasonEpisodeIn(name, episode.scanlator)
            ?: number.takeIf { it >= 1 && it == floor(it) }?.let { 1 to it.toInt() }
            ?: if (show?.isMovie == true) 1 to 1 else null

        val wantsAniList = options.titleSource == "english" ||
            options.titleSource == "romaji" ||
            options.links ||
            options.buttons.any {
                it.kind == PresencePreferences.BUTTON_ANILIST ||
                    it.kind == PresencePreferences.BUTTON_MAL ||
                    it.kind == PresencePreferences.BUTTON_CRUNCHYROLL
            }
        val media = if (wantsAniList) {
            val tracks = runCatching { Injekt.get<GetTracks>().await(anime.id) }.getOrDefault(emptyList())
            AniListIds.forAnime(anime.id, anime.title, tracks, allowSearch = metaPrefs.skipWithoutTracking().get())
        } else {
            null
        }

        val shown = EpisodeDisplays.displayNumber(number, name) ?: EpisodeDisplays.number(number)
        val numberLabel = when {
            located != null && located.first > 1 ->
                activity.stringResource(YMR.strings.rpc_season_episode, located.first, located.second)
            located != null -> activity.stringResource(YMR.strings.rpc_episode_number, located.second.toString())
            else -> activity.stringResource(YMR.strings.rpc_episode_number, shown)
        }

        return PresenceInput(
            extensionTitle = anime.title,
            tmdbTitle = show?.name?.takeIf { it.isNotBlank() },
            englishTitle = media?.english,
            romajiTitle = media?.romaji,
            extensionEpisodeName = name,
            tmdbEpisodeName = meta?.name?.takeIf { metaPrefs.episodeNames().get() },
            episodeOverview = meta?.overview?.takeIf { it.isNotBlank() } ?: episode.summary,
            numberLabel = numberLabel,
            season = located?.first,
            episode = located?.second,
            posterUrl = show?.posterUrl ?: anime.thumbnailUrl,
            episodeImageUrl = meta?.stillUrl("w780") ?: episode.preview_url,
            sourceName = source?.name,
            positionSeconds = position,
            durationSeconds = duration,
            paused = paused,
            aniListUrl = media?.id?.takeIf { it > 0 }?.let { "https://anilist.co/anime/$it" },
            malUrl = media?.idMal?.let { "https://myanimelist.net/anime/$it" },
            tmdbUrl = show?.let { "https://www.themoviedb.org/${if (it.isMovie) "movie" else "tv"}/${it.tmdbId}" },
            sourceUrl = (source as? AnimeHttpSource)?.let { runCatching { it.getAnimeUrl(anime.toSAnime()) }.getOrNull() },
        )
    }

    internal fun labels(context: Context) = PresenceLabels(
        crunchyroll = context.stringResource(YMR.strings.rpc_button_crunchyroll),
        aniList = context.stringResource(YMR.strings.rpc_button_anilist),
        mal = context.stringResource(YMR.strings.rpc_button_mal),
        tmdb = context.stringResource(YMR.strings.rpc_button_tmdb),
        source = context.stringResource(YMR.strings.rpc_button_source),
    )
}
