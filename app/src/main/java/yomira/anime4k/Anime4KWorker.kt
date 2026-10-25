package yomira.anime4k

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.episode.interactor.GetEpisode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs the Anime4K jobs one after another in a foreground service: gets the episode (Smart), copies
 * the original next to the pieces, processes, joins, checks and delivers the result. Heat, battery,
 * the charger and the player pause it between pieces; the job continues from there later.
 */
class Anime4KWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val prefs = Anime4KPreferences()
    private var current: EnhanceJob? = null

    override suspend fun getForegroundInfo(): ForegroundInfo = Anime4KNotifier.foreground(applicationContext, current)

    override suspend fun doWork(): Result {
        val context = applicationContext
        EnhanceJobs.load(context)
        EnhanceJobs.prune(context)
        if (!Anime4KNative.available) {
            EnhanceJobs.jobs.value.filter { it.state.isActive }.forEach { job ->
                EnhanceJobs.update(context, job.id) { it.copy(state = JobState.FAILED, message = context.stringResource(YMR.strings.a4k_error_engine)) }
            }
            return Result.failure()
        }
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { logcat(LogPriority.WARN, it) { "Anime4K: not allowed to run in the foreground" } }

        while (true) {
            val job = EnhanceJobs.next() ?: return Result.success()
            current = job
            val outcome = try {
                runJob(job)
            } catch (e: CancellationException) {
                markWaiting(job.id, context.stringResource(YMR.strings.a4k_wait_stopped))
                throw e
            }
            when (outcome) {
                is Outcome.Done, is Outcome.Failed -> EnhanceJobs.get(job.id)?.let { Anime4KNotifier.result(context, it) }
                is Outcome.Paused -> {
                    // The user's pause just moves on; the phone's reasons end this run (it is started again).
                    if (outcome.byUser) continue
                    return if (outcome.retry) Result.retry() else Result.success()
                }
            }
        }
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data class Failed(val message: String) : Outcome
        data class Paused(val byUser: Boolean, val retry: Boolean) : Outcome
    }

    private suspend fun runJob(job: EnhanceJob): Outcome {
        val context = applicationContext
        val workDir = EnhanceJobs.workDir(context, job).also { it.mkdirs() }
        return try {
            val source = obtainSource(job, workDir)
            val exporter = Anime4KExporter(context)
            val info = exporter.probe(source)
            val segments = SegmentPlan.of(info.durationUs)
            val settings = job.settings
            val (outWidth, outHeight) = settings.output.resolve(info.width, info.height)
            val bitrate = settings.quality.bitrate(outWidth, outHeight, info.frameRate, settings.codec)
            checkStorage(job, workDir, StorageEstimate.bytesFor(info.durationUs, bitrate, info.audioBytesEstimate))

            val eta = EtaTracker().apply {
                seed(job.secondsPerFrame ?: prefs.measuredSecondsPerFrame(Anime4KPreferences.benchmarkKey(job.preset, info.height, settings.output)))
            }
            update(job.id) {
                it.copy(state = JobState.PROCESSING, totalFrames = info.frames, totalSegments = segments.size, message = null)
            }
            var lastSaved = 0L
            val output = exporter.export(
                source = source,
                info = info,
                workDir = workDir,
                settings = settings,
                done = job.doneSegments.toSet(),
                listener = object : Anime4KExporter.Listener {
                    override fun onProgress(doneFrames: Long, totalFrames: Long, secondsPerFrame: Double?, segment: Int, segments: Int) {
                        val now = System.currentTimeMillis()
                        if (now - lastSaved < PROGRESS_SAVE_MS) return
                        lastSaved = now
                        update(job.id) { it.copy(doneFrames = doneFrames, secondsPerFrame = secondsPerFrame) }
                    }

                    override fun onSegmentDone(index: Int, secondsPerFrame: Double?) {
                        update(job.id) { it.copy(doneSegments = (it.doneSegments + index).distinct(), secondsPerFrame = secondsPerFrame) }
                    }

                    override fun onJoining() {
                        update(job.id) { it.copy(state = JobState.JOINING, doneFrames = it.totalFrames) }
                    }
                },
                eta = eta,
                pauseCheck = { pauseReason(job.id) },
                throttle = { throttleMillis(eta) },
            )
            deliver(job, output, source)
            update(job.id) { it.copy(state = JobState.DONE, message = null) }
            if (prefs.cleanTemporary().get()) workDir.deleteRecursively()
            Outcome.Done
        } catch (e: ExportPaused) {
            handlePause(job.id, e.reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Anime4K: job ${job.id} failed" }
            val message = when (e) {
                is LowStorage -> context.stringResource(YMR.strings.a4k_error_storage)
                else -> e.message ?: e.javaClass.simpleName
            }
            update(job.id) { it.copy(state = JobState.FAILED, message = message) }
            Outcome.Failed(message)
        }
    }

    private suspend fun handlePause(id: String, reason: String): Outcome {
        val context = applicationContext
        return when (reason) {
            REASON_USER, REASON_GONE -> Outcome.Paused(byUser = true, retry = false)
            REASON_HEAT -> {
                markWaiting(id, context.stringResource(YMR.strings.a4k_wait_heat))
                // Cool down in place, then carry on.
                while (thermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE && !isStopped) delay(COOLDOWN_CHECK_MS)
                if (isStopped) return Outcome.Paused(byUser = false, retry = true)
                update(id) { it.copy(state = JobState.QUEUED, message = null) }
                Outcome.Paused(byUser = true, retry = false)
            }
            REASON_PLAYER -> {
                // Starts again when the player closes (Anime4KQueue.setPlayerOpen).
                markWaiting(id, context.stringResource(YMR.strings.a4k_wait_player))
                Outcome.Paused(byUser = false, retry = false)
            }
            REASON_CHARGER -> {
                markWaiting(id, context.stringResource(YMR.strings.a4k_wait_charger))
                Outcome.Paused(byUser = false, retry = true)
            }
            else -> {
                markWaiting(id, context.stringResource(YMR.strings.a4k_wait_stopped))
                Outcome.Paused(byUser = false, retry = true)
            }
        }
    }

    /** Why the current job should stop now, or null to continue. */
    private fun pauseReason(id: String): String? {
        val job = EnhanceJobs.get(id) ?: return REASON_GONE
        if (job.state == JobState.PAUSED) return REASON_USER
        if (isStopped) return REASON_STOPPED
        if (prefs.onlyWhileCharging().get() && !isCharging()) return REASON_CHARGER
        if (prefs.prioritizePlayer().get() && Anime4KQueue.playerOpen && job.kind == JobKind.DOWNLOAD) return REASON_PLAYER
        if (prefs.pauseWhenHot().get() && thermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) return REASON_HEAT
        return null
    }

    /** Warm phone: rest as long as each frame takes (half speed, much less heat). */
    private fun throttleMillis(eta: EtaTracker): Long {
        val status = thermalStatus()
        val frame = ((eta.secondsPerFrame ?: 0.0) * 1000).toLong()
        // Smart jobs keep going during playback, at half speed so the player stays smooth.
        if (prefs.prioritizePlayer().get() && Anime4KQueue.playerOpen) return frame.coerceIn(50, 4000)
        if (!prefs.pauseWhenHot().get()) return 0
        return when {
            status >= PowerManager.THERMAL_STATUS_MODERATE -> frame.coerceIn(50, 4000)
            status >= PowerManager.THERMAL_STATUS_LIGHT -> (frame / 3).coerceIn(0, 1500)
            else -> 0
        }
    }

    private fun thermalStatus(): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.currentThermalStatus
    }

    private fun isCharging(): Boolean {
        val battery = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private class LowStorage : Exception("low storage")

    private fun checkStorage(job: EnhanceJob, workDir: File, needed: Long) {
        val free = runCatching { StatFs(workDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        val used = EnhanceJobs.root(applicationContext).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val limit = prefs.storageLimitGb().get().toLong() * 1024 * 1024 * 1024
        val pieces = workDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (needed > free || used - pieces + needed > limit) {
            logcat(LogPriority.WARN) { "Anime4K: job ${job.id} needs $needed bytes, $free free, $used of $limit used" }
            throw LowStorage()
        }
    }

    /** The original as a local file: a download in shared storage is copied next to the pieces. */
    private suspend fun obtainSource(job: EnhanceJob, workDir: File): File {
        if (job.kind == JobKind.SMART && job.sourceUri == null) return downloadStream(job, workDir)
        val uri = Uri.parse(job.sourceUri ?: throw IllegalStateException("job without a source"))
        if (uri.scheme == "file" || uri.scheme == null) return File(uri.path!!)
        val local = File(workDir, "source.${job.videoExtension()}")
        if (local.length() > 0) return local
        val tmp = File(workDir, "source.tmp")
        val input = UniFile.fromUri(applicationContext, uri) ?: throw IllegalStateException("original not found")
        input.openInputStream().use { from -> tmp.outputStream().use { to -> from.copyTo(to, 1 shl 20) } }
        if (!tmp.renameTo(local)) throw IllegalStateException("could not keep the original copy")
        return local
    }

    /**
     * Smart: downloads the stream (with its headers) without re-encoding. A saved address can expire
     * while the job waits, so a failed download looks the episode up again once.
     */
    private suspend fun downloadStream(job: EnhanceJob, workDir: File): File {
        update(job.id) { it.copy(state = JobState.DOWNLOADING, message = null) }
        val target = File(workDir, "source.mkv")
        val saved = job.streamUrl?.let { it to job.streamHeaders }
        try {
            ffmpegDownload(saved ?: resolve(job), target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (saved == null) throw e
            ffmpegDownload(resolve(job), target)
        }
        update(job.id) { it.copy(sourceUri = Uri.fromFile(target).toString()) }
        return target
    }

    private suspend fun resolve(job: EnhanceJob): Pair<String, Map<String, String>> {
        val anime = Injekt.get<GetAnime>().await(job.animeId) ?: throw IllegalStateException("anime not found")
        val episode = Injekt.get<GetEpisode>().await(job.episodeId) ?: throw IllegalStateException("episode not found")
        return SmartAnime4K.resolveStream(anime, episode)
            ?: throw IllegalStateException(applicationContext.stringResource(YMR.strings.a4k_error_stream))
    }

    private suspend fun ffmpegDownload(stream: Pair<String, Map<String, String>>, target: File) {
        val (url, headerMap) = stream
        val workDir = target.parentFile!!
        val tmp = File(workDir, "source.part.mkv")
        tmp.delete()
        val headers = headerMap.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val args = buildList {
            if (headers.isNotEmpty()) addAll(listOf("-headers", headers))
            addAll(listOf("-i", url, "-map", "0:v:0", "-map", "0:a?", "-map", "0:s?", "-c", "copy", "-f", "matroska", "-y", tmp.absolutePath))
        }
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { result ->
                if (ReturnCode.isSuccess(result.returnCode)) {
                    continuation.resume(Unit)
                } else if (!ReturnCode.isCancel(result.returnCode)) {
                    continuation.resumeWithException(IllegalStateException("download failed (${result.returnCode})"))
                }
            }
            continuation.invokeOnCancellation { FFmpegKitConfig.getSession(session.sessionId)?.cancel() }
        }
        if (!tmp.renameTo(target)) throw IllegalStateException("could not keep the download")
    }

    /** Puts the result where it is used: the download folder, or the Smart cache. */
    private fun deliver(job: EnhanceJob, output: File, source: File) {
        when (job.kind) {
            JobKind.DOWNLOAD -> EnhancedDownloads.deliver(applicationContext, job, output)
            JobKind.SMART -> SmartAnime4K.store(applicationContext, job, output)
        }
        // The original copied for processing is not needed any more (the download keeps its own).
        if (source.parentFile == EnhanceJobs.workDir(applicationContext, job)) source.delete()
    }

    private fun update(id: String, transform: (EnhanceJob) -> EnhanceJob) {
        val updated = EnhanceJobs.update(applicationContext, id, transform) ?: return
        current = updated
        Anime4KNotifier.update(applicationContext, updated)
    }

    private fun markWaiting(id: String, message: String) {
        update(id) { if (it.state == JobState.PAUSED) it else it.copy(state = JobState.WAITING, message = message) }
    }

    companion object {
        private const val PROGRESS_SAVE_MS = 2_000L
        private const val COOLDOWN_CHECK_MS = 30_000L
        const val REASON_USER = "user"
        const val REASON_GONE = "gone"
        const val REASON_STOPPED = "stopped"
        const val REASON_CHARGER = "charger"
        const val REASON_PLAYER = "player"
        const val REASON_HEAT = "heat"
    }
}

/** Extension of the downloaded video, for the local copy. */
internal fun EnhanceJob.videoExtension(): String =
    sourceUri?.substringAfterLast('.', "mkv")?.takeIf { it.length in 2..4 }?.lowercase() ?: "mkv"
