/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.layout.padding
import yomira.player.playerCapsule
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.ui.player.controls.LocalPlayerButtonsClickEvent
import `is`.xyz.mpv.Utils
import tachiyomi.presentation.core.components.material.padding

/**
 * Width of the video timer slots flanking the seekbar.
 * Reused by the player prompt so it can align with the duration timer's text.
 */
internal val videoTimerWidth = 92.dp

@Immutable
data class IndexedSegment(
    val name: String,
    val start: Float,
    val color: Color = Color.Unspecified,
    val index: Int = 0,
    val chapterType: ChapterType = ChapterType.Other,
) {
    companion object {
        val Unspecified = IndexedSegment(name = "", start = 0f)
    }

    fun toSegment(): Segment = Segment(name, start, color)
}

@Composable
fun SeekbarWithTimers(
    position: Float,
    duration: Float,
    readAheadValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    timersInverted: Pair<Boolean, Boolean>,
    positionTimerOnClick: () -> Unit,
    durationTimerOnCLick: () -> Unit,
    onDurationTextLayout: (TextLayoutResult) -> Unit = {},
    chapters: List<Segment>,
    modifier: Modifier = Modifier,
) {
    val clickEvent = LocalPlayerButtonsClickEvent.current
    Row(
        // YMR --> timers and progress bar share one glass capsule (iOS 27 player)
        modifier = modifier
            .padding(horizontal = 8.dp)
            .playerCapsule()
            .padding(horizontal = 4.dp)
            .height(48.dp),
        // YMR <--
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        VideoTimer(
            value = position,
            timersInverted.first,
            onClick = {
                clickEvent()
                positionTimerOnClick()
            },
            modifier = Modifier.width(videoTimerWidth),
        )
        // YMR --> liquid glass slider (iOS style) instead of the Material seeker
        yomira.player.GlassSeekBar(
            value = position.coerceIn(0f, duration),
            range = 0f..duration.coerceAtLeast(0.001f),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            readAheadValue = readAheadValue,
            chapterStarts = remember(chapters, duration) {
                chapters
                    .filter { it.start in 0f..duration }
                    .map { it.start }
            },
            modifier = Modifier.weight(1f),
            // YMR <--
        )
        VideoTimer(
            value = if (timersInverted.second) position - duration else duration,
            isInverted = timersInverted.second,
            onClick = {
                clickEvent()
                durationTimerOnCLick()
            },
            onTextLayout = onDurationTextLayout,
            modifier = Modifier.width(videoTimerWidth),
        )
    }
}

@Composable
fun VideoTimer(
    value: Float,
    isInverted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .wrapContentHeight(Alignment.CenterVertically),
        text = Utils.prettyTime(value.toInt(), isInverted),
        color = Color.White,
        textAlign = TextAlign.Center,
        onTextLayout = onTextLayout,
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
            fontFeatureSettings = "tnum"
        )
    )
}

@Preview
@Composable
private fun PreviewSeekBar() {
    SeekbarWithTimers(
        5f,
        20f,
        4f,
        {},
        {},
        Pair(false, true),
        {},
        {},
        chapters = emptyList(),
    )
}
