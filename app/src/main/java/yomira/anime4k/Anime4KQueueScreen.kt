package yomira.anime4k

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import yomira.i18n.YMR

/** The enhancement jobs with their progress and controls, and the speed test of this phone. */
class Anime4KQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val jobs by EnhanceJobs.jobs.collectAsState()
        remember { EnhanceJobs.load(context) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(YMR.strings.a4k_queue_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { BenchmarkCard() }
                if (jobs.isEmpty()) {
                    item {
                        Text(
                            stringResource(YMR.strings.a4k_queue_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(jobs.sortedByDescending { it.createdAt }, key = { it.id }) { job -> JobCard(job) }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun JobCard(job: EnhanceJob) {
    val context = LocalContext.current
    Card {
        Text("${job.animeTitle} · ${job.episodeName}", style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(
            text = stringResource(YMR.strings.a4k_job_download, job.preset.displayName()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            Anime4KNotifier.describe(context, job),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (job.totalFrames > 0 && job.state != JobState.DONE) {
            LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                job.state.isActive -> FilledTonalButton(onClick = { Anime4KQueue.pause(context, job.id) }) {
                    Text(stringResource(YMR.strings.a4k_action_pause))
                }
                job.state == JobState.PAUSED -> FilledTonalButton(onClick = { Anime4KQueue.resume(context, job.id) }) {
                    Text(stringResource(YMR.strings.a4k_action_resume))
                }
                job.state == JobState.FAILED -> FilledTonalButton(onClick = { Anime4KQueue.retry(context, job.id) }) {
                    Text(stringResource(YMR.strings.a4k_action_retry))
                }
            }
            OutlinedButton(onClick = { Anime4KQueue.cancel(context, job.id) }) {
                Text(stringResource(if (job.state == JobState.DONE) YMR.strings.a4k_action_remove else YMR.strings.a4k_cancel))
            }
        }
    }
}

@Composable
private fun BenchmarkCard() {
    val context = LocalContext.current
    val prefs = remember { Anime4KPreferences() }
    val scope = rememberCoroutineScope()
    var enhancer by remember { mutableStateOf(Enhancer.fromId(prefs.downloadPreset().get()) ?: Enhancer.DEFAULT) }
    var height by remember { mutableStateOf(1080) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val output = OutputSize.fromKey(prefs.outputSize().get())

    Card {
        Text(stringResource(YMR.strings.a4k_bench_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(YMR.strings.a4k_bench_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EnhancerPicker(enhancer = enhancer, onChange = { enhancer = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(480, 720, 1080).forEach { value ->
                FilterChip(
                    selected = height == value,
                    onClick = { height = value },
                    label = { Text("${value}p") },
                    shape = RoundedCornerShape(percent = 50),
                )
            }
        }
        FilledTonalButton(
            enabled = !running,
            onClick = {
                running = true
                result = null
                scope.launch {
                    result = try {
                        val width = height * 16 / 9
                        val measured = Anime4KBenchmark.run(context, enhancer, width, height, output.forEnhancer(enhancer))
                        EnhanceSpeed.remember(prefs, measured)
                        benchmarkText(context, measured)
                    } catch (e: Exception) {
                        context.stringResource(YMR.strings.a4k_bench_failed, e.message ?: e.javaClass.simpleName)
                    } finally {
                        running = false
                    }
                }
            },
        ) {
            Text(stringResource(if (running) YMR.strings.a4k_bench_running else YMR.strings.a4k_bench_run))
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun benchmarkText(context: android.content.Context, result: BenchmarkResult): String =
    context.stringResource(
        YMR.strings.a4k_bench_result,
        "%.2f".format(result.secondsPerFrame),
        "%.1f".format(result.gflopsPerFrame),
        Anime4KNotifier.formatDuration(context, (result.hoursPerEpisode * 3600).toLong()),
        "%.0f".format(result.slowerThanRealTime.coerceAtLeast(1.0)),
        "${result.outWidth}x${result.outHeight}",
    )
