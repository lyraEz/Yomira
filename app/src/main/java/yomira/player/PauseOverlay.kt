package yomira.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import yomira.glass.LocalGlassStyle
import yomira.glass.glass
import yomira.glass.rememberGlassInteraction
import yomira.i18n.YMR
import tachiyomi.presentation.core.util.collectAsState as collectPref

private val OverlayShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 12f)
private val MinOverlayHeight = 240.dp
private val MinOverlayWidth = 280.dp

/**
 * "You are watching" screen shown while the video is paused and the controls are hidden (after a
 * tap outside them or a few idle seconds): title logo, season and episode, the episode's name and
 * description over a darkened frame. It has no touch handling: a tap reaches the player, which
 * brings the controls back and hides this.
 */
@Composable
fun PauseOverlay(viewModel: PlayerViewModel, visible: Boolean) {
    val prefs = remember { CinemaPreferences() }
    val showLogo by prefs.showLogo().collectPref()
    val info = rememberPlayerEpisodeInfo(viewModel)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 550, delayMillis = 120)),
        exit = fadeOut(tween(durationMillis = 220)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Picture-in-picture and small split screens: just the video.
            if (maxHeight < MinOverlayHeight || maxWidth < MinOverlayWidth) return@BoxWithConstraints
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.86f),
                            0.55f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black.copy(alpha = 0.38f),
                        ),
                    ),
            )
            val wide = maxWidth > maxHeight
            val screenWidth = maxWidth
            val start: Dp = if (wide) screenWidth * 0.08f else 24.dp
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = start, end = 24.dp)
                    .widthIn(max = if (wide) screenWidth * 0.56f else screenWidth),
                verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
            ) {
                Text(
                    stringResource(YMR.strings.pause_watching),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = OverlayShadow),
                    color = Color.White.copy(alpha = 0.72f),
                )
                var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
                if (showLogo && info.logoUrl != null && !logoFailed) {
                    AsyncImage(
                        model = info.logoUrl,
                        contentDescription = info.animeTitle,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { logoFailed = true },
                        modifier = Modifier
                            .heightIn(max = if (wide) 116.dp else 84.dp)
                            .widthIn(max = if (wide) screenWidth * 0.34f else screenWidth * 0.7f),
                    )
                } else {
                    Text(
                        info.animeTitle,
                        style = TextStyle(
                            fontSize = if (wide) 36.sp else 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = if (wide) 40.sp else 32.sp,
                            shadow = OverlayShadow,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (info.label.isNotBlank()) {
                    Text(
                        info.label,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, shadow = OverlayShadow),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                info.name?.let { name ->
                    Text(
                        name,
                        style = TextStyle(
                            fontSize = if (wide) 30.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = if (wide) 34.sp else 28.sp,
                            shadow = OverlayShadow,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                info.overview?.let { overview ->
                    Text(
                        overview,
                        style = TextStyle(
                            fontSize = if (wide) 16.sp else 14.sp,
                            lineHeight = if (wide) 23.sp else 20.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = OverlayShadow,
                        ),
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = if (wide) 4 else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Netflix-style "Skip opening" button in liquid glass, at the bottom right while the video is in a
 * skippable part. It shows with or without the controls and moves up above them when they show.
 */
@Composable
fun SkipSegmentButton(
    viewModel: PlayerViewModel,
    controlsShown: Boolean,
    modifier: Modifier = Modifier,
) {
    val text by viewModel.skipIntroText.collectAsState()
    val chapter by viewModel.currentChapter.collectAsState()
    val dontSkip = stringResource(MR.strings.player_aniskip_dontskip)
    val previewName = stringResource(YMR.strings.segment_preview)
    val label = text?.let { resolveSkipLabel(it, chapter, dontSkip, previewName) }
    var lastLabel by remember { mutableStateOf("") }
    if (label != null) lastLabel = label

    // Dark glass whatever the app theme, so the white label reads over bright scenes too.
    val baseStyle = LocalGlassStyle.current
    val darkStyle = remember(baseStyle) { baseStyle.copy(dark = true) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomy = maxHeight >= MinOverlayHeight && maxWidth >= MinOverlayWidth
        // With the controls: above the progress bar, at the right of the title (which stops short
        // of it in landscape); in portrait above the title, out of the way of the buttons row.
        val bottom = when {
            !controlsShown -> 36.dp
            maxWidth > maxHeight -> 98.dp
            else -> 172.dp
        }
        AnimatedVisibility(
            visible = label != null && roomy,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = bottom),
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
        ) {
            val interaction = rememberGlassInteraction()
            Row(
                modifier = Modifier
                    .glass(
                        RoundedCornerShape(percent = 50),
                        style = darkStyle,
                        interaction = interaction,
                        // Also the solid colour when live glass is off.
                        tint = Color.Black.copy(alpha = 0.5f),
                    )
                    .clickable(remember { MutableInteractionSource() }, indication = null) { viewModel.onSkipIntro() }
                    .padding(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    lastLabel,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

private val openingName = Regex("""(?i)\b(?:op|opening|intro|abertura)\b""")
private val endingName = Regex("""(?i)\b(?:ed|ending|credits|outro|encerramento)\b""")
private val recapName = Regex("""(?i)recap""")

/** The chapter list keeps only names, so the kind of part comes from the chapter's name. */
@Composable
private fun resolveSkipLabel(text: String, chapter: Segment?, dontSkip: String, previewName: String): String {
    val name = chapter?.name.orEmpty()
    return when {
        text == dontSkip -> text
        name == previewName -> stringResource(YMR.strings.skip_preview)
        name == stringResource(YMR.strings.segment_opening) || openingName.containsMatchIn(name) ->
            stringResource(YMR.strings.skip_opening)
        name == stringResource(YMR.strings.segment_ending) || endingName.containsMatchIn(name) ->
            stringResource(YMR.strings.skip_ending)
        name == stringResource(YMR.strings.segment_recap) || recapName.containsMatchIn(name) ->
            stringResource(YMR.strings.skip_recap)
        else -> text
    }
}
