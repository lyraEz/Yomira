package yomira.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.CastManager
import eu.kanade.tachiyomi.ui.player.PlayerButton
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.controls.LocalPlayerButtonsClickEvent
import eu.kanade.tachiyomi.ui.player.controls.RenderPlayerButton
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import yomira.metadata.EpisodeDisplays
import yomira.metadata.EpisodeMetadata
import yomira.metadata.rememberShowMeta
import kotlin.math.abs
import tachiyomi.presentation.core.util.collectAsState as collectPref

/** Settings → Player → Layout: the premium ("Cinema") controls. */
class CinemaPreferences(private val store: PreferenceStore = Injekt.get()) {
    /** Streaming-app layout: title and progress at the bottom left, big centre controls. */
    fun enabled() = store.getBoolean("yomira_cinema_layout", true)

    /** Distance from the play button to each side button, as a fraction of the screen width. */
    fun centerSpread() = store.getFloat("yomira_cinema_center_spread", 0.22f)

    /** Side buttons seek a few seconds (true) or change episode (false). */
    fun centerSeek() = store.getBoolean("yomira_cinema_center_seek", true)

    /** "Via <server>" under the episode title. */
    fun showSource() = store.getBoolean("yomira_cinema_show_source", true)

    /** Time as "-21:59" instead of "03:33 / 25:34". */
    fun remainingTime() = store.getBoolean("yomira_cinema_remaining", false)

    /** "You are watching" screen while paused. */
    fun pauseScreen() = store.getBoolean("yomira_cinema_pause_screen", true)

    /** Seconds paused without touching the screen before that screen shows. */
    fun pauseDelay() = store.getInt("yomira_cinema_pause_delay", 5)

    /** Title logo from TMDB on the pause screen. */
    fun showLogo() = store.getBoolean("yomira_cinema_logo", true)

    /** Thickness of the progress bar in dp. */
    fun barHeight() = store.getInt("yomira_cinema_bar_height", 4)
}

/** Everything the premium controls show about the current episode. */
@Immutable
data class PlayerEpisodeInfo(
    val animeTitle: String = "",
    val label: String = "",
    val name: String? = null,
    val overview: String? = null,
    val logoUrl: String? = null,
    val imageUrl: String? = null,
    val source: String? = null,
)

@Composable
fun rememberPlayerEpisodeInfo(viewModel: PlayerViewModel): PlayerEpisodeInfo {
    val context = LocalContext.current
    val anime by viewModel.currentAnime.collectAsState()
    val episode by viewModel.currentEpisode.collectAsState()
    val hosters by viewModel.hosterList.collectAsState()
    val selected by viewModel.selectedHosterVideoIndex.collectAsState()
    val show = rememberShowMeta(anime?.id)

    LaunchedEffect(anime?.id, episode?.id) {
        val currentAnime = anime ?: return@LaunchedEffect
        val currentEpisode = episode ?: return@LaunchedEffect
        EpisodeMetadata.ensure(
            currentAnime.id,
            currentAnime.title,
            listOf(currentEpisode.episode_number.toDouble() to currentEpisode.name),
        )
    }

    return remember(anime, episode, show, hosters, selected) {
        val currentAnime = anime
        val currentEpisode = episode
        if (currentAnime == null || currentEpisode == null) return@remember PlayerEpisodeInfo()
        val display = EpisodeDisplays.build(
            context = context,
            show = show,
            episodeNumber = currentEpisode.episode_number.toDouble(),
            extensionName = currentEpisode.name,
            extensionSummary = currentEpisode.summary,
            extensionPreview = currentEpisode.preview_url,
            previewSize = "w780",
        )
        PlayerEpisodeInfo(
            animeTitle = currentAnime.title,
            label = display.label,
            name = display.name,
            overview = display.summary,
            logoUrl = show?.logoUrl,
            imageUrl = display.previewUrl ?: show?.backdropUrl,
            source = hosters.getOrNull(selected.first)?.hosterName?.takeIf { it.isNotBlank() },
        )
    }
}

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 10f)

/** Top right: the configured buttons, then back. */
@Composable
fun CinemaTopRow(
    buttons: List<PlayerButton>,
    viewModel: PlayerViewModel,
    castManager: CastManager,
    onBackPress: () -> Unit,
    onCastClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // Scrolls when the buttons do not fit (portrait); back stays visible at the end.
        modifier = modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        buttons.forEach { button ->
            RenderPlayerButton(
                button = button,
                viewModel = viewModel,
                castManager = castManager,
                onBackPress = onBackPress,
                onCastClick = onCastClick,
            )
        }
        ControlsButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBackPress, iconSize = 24.dp)
    }
}

/**
 * Play/pause in the middle and the side buttons placed by screen width (not packed next to it),
 * so they sit where the thumbs are on a phone held sideways.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun CinemaCenterControls(
    viewModel: PlayerViewModel,
    controlsShown: Boolean,
    areControlsLocked: Boolean,
    isLoading: Boolean,
    isLoadingEpisode: Boolean,
    showLoadingCircle: Boolean,
    isStopped: Boolean,
    paused: Boolean,
    gestureSeekAmount: Pair<Int, Int>?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val prefs = remember { CinemaPreferences() }
    val gestures = remember { Injekt.get<GesturePreferences>() }
    val spread by prefs.centerSpread().collectPref()
    val seekMode by prefs.centerSeek().collectPref()
    val seekSeconds by gestures.skipLengthPreference().collectPref()
    val scale = LocalPlayerButtonLook.current.scale
    val big = 88.dp * scale
    val side = 58.dp * scale
    val showButtons = controlsShown && !areControlsLocked
    val clickEvent = LocalPlayerButtonsClickEvent.current

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val offset = maxWidth * spread.coerceIn(0.08f, 0.40f)
        when {
            (isLoading || isLoadingEpisode) && showLoadingCircle -> {
                CircularProgressIndicator(Modifier.size(big * 0.7f), color = Color.White, strokeWidth = 3.dp)
            }
            isStopped -> Spacer(Modifier.size(big))
            gestureSeekAmount != null -> {
                Text(
                    stringResource(
                        MR.strings.player_gesture_seek_indicator,
                        if (gestureSeekAmount.second >= 0) '+' else '-',
                        Utils.prettyTime(abs(gestureSeekAmount.second)),
                        Utils.prettyTime(gestureSeekAmount.first + gestureSeekAmount.second),
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = TextShadow),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                )
            }
            else -> AnimatedVisibility(showButtons, enter = fadeIn(), exit = fadeOut()) {
                val icon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(big)
                        .clickable(interaction, indication = null) {
                            clickEvent()
                            viewModel.pauseUnpause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    GlassIcon(
                        painter = rememberAnimatedVectorPainter(icon, !paused),
                        size = big * 0.78f,
                        interactionSource = interaction,
                    )
                }
            }
        }

        if (gestureSeekAmount == null) {
            AnimatedVisibility(showButtons, Modifier.offset(x = -offset), enter = fadeIn(), exit = fadeOut()) {
                if (seekMode) {
                    SeekGlassButton(seekIcon(seekSeconds, back = true), side, rotateBack = true) {
                        clickEvent()
                        viewModel.seekBy(-seekSeconds)
                    }
                } else {
                    SeekGlassButton(Icons.Rounded.SkipPrevious, side, enabled = hasPrevious) {
                        clickEvent()
                        viewModel.changeEpisode(true)
                    }
                }
            }
            AnimatedVisibility(showButtons, Modifier.offset(x = offset), enter = fadeIn(), exit = fadeOut()) {
                if (seekMode) {
                    SeekGlassButton(seekIcon(seekSeconds, back = false), side) {
                        clickEvent()
                        viewModel.seekBy(seekSeconds)
                    }
                } else {
                    SeekGlassButton(Icons.Rounded.SkipNext, side, enabled = hasNext) {
                        clickEvent()
                        viewModel.changeEpisode(false)
                    }
                }
            }
        }
    }
}

private fun seekIcon(seconds: Int, back: Boolean): ImageVector = when (seconds) {
    5 -> if (back) Icons.Rounded.Replay5 else Icons.Rounded.Forward5
    10 -> if (back) Icons.Rounded.Replay10 else Icons.Rounded.Forward10
    30 -> if (back) Icons.Rounded.Replay30 else Icons.Rounded.Forward30
    else -> if (back) Icons.Rounded.Replay else Icons.Rounded.FastForward
}

/** Glass glyph that gives a short spin when tapped, so a seek feels acknowledged. */
@Composable
private fun SeekGlassButton(
    icon: ImageVector,
    size: Dp,
    enabled: Boolean = true,
    rotateBack: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clickable(interaction, indication = null, enabled = enabled) {
                onClick()
                scope.launch {
                    turn.snapTo(if (rotateBack) 28f else -28f)
                    turn.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(
            icon = icon,
            size = size * 0.82f,
            modifier = Modifier.rotate(-turn.value),
            enabled = enabled,
            interactionSource = interaction,
        )
    }
}

/**
 * Bottom of the premium layout: anime title, "T1 E2 • episode", "Via server", a thin progress bar
 * across the screen and, under it, the configured buttons with the time on the right.
 */
@Composable
fun CinemaBottomSection(
    viewModel: PlayerViewModel,
    position: Float,
    duration: Float,
    readAhead: Float,
    chapters: List<Segment>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    buttons: List<PlayerButton>,
    castManager: CastManager,
    onBackPress: () -> Unit,
    onCastClick: () -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val info = rememberPlayerEpisodeInfo(viewModel)
    val prefs = remember { CinemaPreferences() }
    val showSource by prefs.showSource().collectPref()
    val remaining by prefs.remainingTime().collectPref()
    val barHeight by prefs.barHeight().collectPref()
    val clickEvent = LocalPlayerButtonsClickEvent.current

    val look = LocalPlayerButtonLook.current
    // Glyphs start this far inside each button; the row moves left by it to line up with the title.
    val buttonInset = look.spacing + 16.dp * look.scale

    val time: @Composable () -> Unit = {
        val total = duration.toInt()
        val current = position.toInt().coerceIn(0, total.coerceAtLeast(0))
        Text(
            text = if (remaining) {
                Utils.prettyTime(current - total, true)
            } else {
                "${Utils.prettyTime(current)} / ${Utils.prettyTime(total)}"
            },
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                shadow = TextShadow,
            ),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    clickEvent()
                    prefs.remainingTime().set(!remaining)
                },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // In landscape the title stops short of the right side, where "Skip opening" shows.
        Column(Modifier.fillMaxWidth(if (landscape) 0.68f else 1f)) {
            Text(
                text = info.animeTitle,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, shadow = TextShadow),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(info.label.takeIf { it.isNotBlank() }, info.name).joinToString(" • ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showSource && info.source != null) {
                Text(
                    text = stringResource(YMR.strings.player_via_source, info.source),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CinemaSeekBar(
                value = position.coerceIn(0f, duration.coerceAtLeast(0f)),
                max = duration,
                readAhead = readAhead,
                chapterStarts = remember(chapters, duration) {
                    chapters.map { it.start }.filter { it > 0f && it < duration }
                },
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                trackHeight = barHeight.coerceIn(2, 8).dp,
                modifier = Modifier.weight(1f),
            )
            // Portrait has no room next to the buttons: the time sits by the bar.
            if (!landscape) time()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .offset(x = -buttonInset)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Fade the edges that hide more buttons.
                        val back = scroll.canScrollBackward
                        val forward = scroll.canScrollForward
                        if (back || forward) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to if (back) Color.Transparent else Color.Black,
                                    0.08f to Color.Black,
                                    0.88f to Color.Black,
                                    1f to if (forward) Color.Transparent else Color.Black,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                buttons.forEach { button ->
                    RenderPlayerButton(
                        button = button,
                        viewModel = viewModel,
                        castManager = castManager,
                        onBackPress = onBackPress,
                        onCastClick = onCastClick,
                    )
                }
            }
            if (landscape) time()
        }
    }
}

/**
 * Thin progress bar: white for what was watched, lighter for what is buffered, small gaps at
 * chapters. It thickens and shows a thumb while dragged. Drawn in one pass, no glass.
 */
@Composable
fun CinemaSeekBar(
    value: Float,
    max: Float,
    readAhead: Float,
    chapterStarts: List<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1f else 0f, label = "seekGrow")
    val latestMax by rememberUpdatedState(max)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinish by rememberUpdatedState(onValueChangeFinished)
    val gap = Color.Black.copy(alpha = 0.55f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (latestMax <= 0f) return@awaitEachGesture
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f) * latestMax
                    dragging = true
                    down.consume()
                    onChange(at(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        onChange(at(change.position.x))
                    }
                    dragging = false
                    onFinish()
                }
            },
    ) {
        val height = trackHeight.toPx() * (1f + 0.75f * grow)
        val top = (size.height - height) / 2
        val radius = CornerRadius(height / 2, height / 2)
        fun x(v: Float) = if (max > 0f) (v / max).coerceIn(0f, 1f) * size.width else 0f

        drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(0f, top), Size(size.width, height), radius)
        if (readAhead > value) {
            drawRoundRect(Color.White.copy(alpha = 0.42f), Offset(0f, top), Size(x(readAhead), height), radius)
        }
        drawRoundRect(Color.White, Offset(0f, top), Size(x(value), height), radius)
        val gapWidth = 2.dp.toPx()
        chapterStarts.forEach { start ->
            drawRect(gap, Offset(x(start) - gapWidth / 2, top), Size(gapWidth, height))
        }
        if (grow > 0f) {
            drawCircle(Color.White, radius = 8.dp.toPx() * grow, center = Offset(x(value), size.height / 2))
        }
    }
}
