package yomira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.i18n.stringResource
import yomira.glass.AlertDialog
import yomira.i18n.YMR
import yomira.player.WatchLog
import yomira.player.WatchLogPreferences
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import tachiyomi.presentation.core.util.collectAsState as collectPref

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

/**
 * "Your watch time" card for the More tab: today against an optional daily goal, a seven-day bar
 * chart, the week total, the streak and the best day. Fed by [WatchLog], which counts only time the
 * video was actually playing.
 */
@Composable
fun WatchTimeCard(modifier: Modifier = Modifier) {
    val prefs = remember { WatchLogPreferences() }
    val raw by prefs.log().collectPref()
    val goalMinutes by prefs.dailyGoal().collectPref()
    val stats = remember(raw) { WatchLog.stats(raw) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(YMR.strings.watch_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showGoalDialog = true }) {
                    Text(
                        if (goalMinutes > 0) {
                            stringResource(YMR.strings.watch_goal_value, formatDuration(goalMinutes * 60L))
                        } else {
                            stringResource(YMR.strings.watch_goal_set)
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (goalMinutes > 0) {
                    val progress = (stats.todaySeconds / (goalMinutes * 60f)).coerceIn(0f, 1f)
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(64.dp)) {
                            val stroke = 7.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            drawArc(
                                primary,
                                -90f,
                                360f * progress,
                                false,
                                Offset(inset, inset),
                                arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Column {
                    Text(
                        stringResource(YMR.strings.watch_today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDuration(stats.todaySeconds),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, letterSpacing = (-0.4).sp),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            WeekBars(stats.lastSevenDays, goalMinutes)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(YMR.strings.watch_week), formatDuration(stats.weekSeconds), Modifier.weight(1f))
                StatTile(
                    stringResource(YMR.strings.watch_streak),
                    if (stats.streakDays == 1) {
                        stringResource(YMR.strings.watch_streak_one)
                    } else {
                        stringResource(YMR.strings.watch_streak_days, stats.streakDays)
                    },
                    Modifier.weight(1f),
                )
                StatTile(stringResource(YMR.strings.watch_best), formatDuration(stats.bestDaySeconds), Modifier.weight(1f))
            }
        }
    }

    if (showGoalDialog) {
        val options = listOf(0, 15, 30, 45, 60, 90, 120, 180)
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(stringResource(YMR.strings.watch_goal_done))
                }
            },
            title = { Text(stringResource(YMR.strings.watch_goal_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(YMR.strings.watch_goal_summary))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { minutes ->
                            FilterChip(
                                selected = minutes == goalMinutes,
                                onClick = { prefs.dailyGoal().set(minutes) },
                                label = {
                                    Text(
                                        if (minutes == 0) {
                                            stringResource(YMR.strings.watch_goal_none)
                                        } else {
                                            formatDuration(minutes * 60L)
                                        },
                                    )
                                },
                                shape = RoundedCornerShape(percent = 50),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun WeekBars(days: List<Long>, goalMinutes: Int) {
    val max = maxOf(days.maxOrNull() ?: 0L, goalMinutes * 60L, 60L).toFloat()
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, seconds ->
            val isToday = index == days.lastIndex
            val day = today.minusDays((days.lastIndex - index).toLong())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val fraction = (seconds / max).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .width(18.dp)
                        .weight(1f, fill = false)
                        .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = if (seconds > 0) 0.45f else 0.12f)
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
