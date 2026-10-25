package yomira.anime4k

import android.widget.Toast
import eu.kanade.tachiyomi.data.database.models.toDomainEpisode
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import yomira.i18n.YMR
import java.io.File

/**
 * Smart Anime4K inside the player. The episode keeps playing as it is; when its Anime4K version is
 * ready the player offers to switch at the same position. It never switches by itself: the
 * prepared file could be another version of the episode (dubbed, another quality).
 */
object PlayerAnime4K {

    data class State(
        val episodeId: Long = -1,
        val ready: File? = null,
        val switched: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** A new file started in the player. */
    fun onEpisodeStarted(activity: PlayerActivity) {
        val viewModel = activity.viewModel
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value?.toDomainEpisode() ?: return
        val video = viewModel.currentVideo.value
        val current = _state.value
        if (current.episodeId == episode.id && current.switched) return
        if (current.episodeId != episode.id) _state.value = State(episodeId = episode.id)
        // Downloads have their own Anime4K version (Enhanced Downloads).
        val url = video?.videoUrl.orEmpty()
        if (url.startsWith("content:") || url.startsWith("file:") || url.startsWith("/")) return
        if (!Anime4KPreferences().smartEnabled().get()) return

        scope.launch {
            runCatching {
                val context = activity.applicationContext
                EnhanceJobs.load(context)
                val ready = SmartAnime4K.cached(context, anime, episode)
                if (ready != null) {
                    _state.value = _state.value.copy(ready = ready)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, activity.stringResource(YMR.strings.a4k_smart_ready_toast), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val playlist = viewModel.currentPlaylist.value
                val index = playlist.indexOfFirst { it.id == episode.id }
                val next = playlist.getOrNull(index + 1)?.toDomainEpisode()
                SmartAnime4K.onEpisodeOpened(context, anime, episode, video, next)
            }.onFailure { logcat(LogPriority.WARN, it) { "Anime4K: Smart could not start" } }
        }
    }

    /** Checks again (after a job finished while watching). */
    fun refresh(activity: PlayerActivity) {
        val viewModel = activity.viewModel
        val anime = viewModel.currentAnime.value ?: return
        val episode = viewModel.currentEpisode.value?.toDomainEpisode() ?: return
        scope.launch {
            val ready = SmartAnime4K.cached(activity.applicationContext, anime, episode) ?: return@launch
            if (_state.value.episodeId == episode.id) _state.value = _state.value.copy(ready = ready)
        }
    }

    /** Plays the Anime4K version from the current position. */
    fun switchToEnhanced(activity: PlayerActivity) {
        val file = _state.value.ready?.takeIf { it.exists() } ?: return
        val mpv = activity.viewModel.mpv
        val position = mpv.getPropertyDouble("time-pos") ?: 0.0
        mpv.command("set", "start", position.toString())
        mpv.command("loadfile", file.absolutePath, "replace")
        _state.value = _state.value.copy(switched = true)
    }
}
