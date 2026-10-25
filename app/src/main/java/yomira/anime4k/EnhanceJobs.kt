package yomira.anime4k

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.UUID

/**
 * The Anime4K job list, shared by Enhanced Downloads and Smart Anime4K: one queue, one worker,
 * one engine. Saved to anime4k/jobs.json after every change.
 */
object EnhanceJobs {

    private val json = Json { ignoreUnknownKeys = true }
    private val _jobs = MutableStateFlow<List<EnhanceJob>>(emptyList())
    val jobs: StateFlow<List<EnhanceJob>> = _jobs.asStateFlow()

    @Volatile private var loaded = false
    private val lock = Any()

    fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "anime4k").also { it.mkdirs() }

    fun workDir(context: Context, job: EnhanceJob): File = File(root(context), "jobs/${job.id}")

    fun smartDir(context: Context): File = File(root(context), "smart").also { it.mkdirs() }

    private fun file(context: Context) = File(root(context), "jobs.json")

    fun load(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val saved = runCatching {
                file(context).takeIf { it.exists() }?.readText()?.let { json.decodeFromString(ListSerializer(EnhanceJob.serializer()), it) }
            }.onFailure { logcat(LogPriority.WARN, it) { "Anime4K: could not read the job list" } }.getOrNull().orEmpty()
            // A job cut off by the app closing continues where it stopped.
            _jobs.value = saved.map {
                if (it.state == JobState.PROCESSING || it.state == JobState.JOINING || it.state == JobState.DOWNLOADING) {
                    it.copy(state = JobState.QUEUED)
                } else {
                    it
                }
            }
            loaded = true
        }
    }

    private fun save(context: Context) {
        runCatching {
            val target = file(context)
            val tmp = File(target.parentFile, "jobs.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(EnhanceJob.serializer()), _jobs.value))
            tmp.renameTo(target)
        }.onFailure { logcat(LogPriority.WARN, it) { "Anime4K: could not save the job list" } }
    }

    fun get(id: String): EnhanceJob? = _jobs.value.firstOrNull { it.id == id }

    /**
     * Adds a job unless the same result is already queued, running or done: the existing one is
     * returned instead, so nothing is processed twice.
     */
    fun add(context: Context, job: EnhanceJob): EnhanceJob {
        load(context)
        synchronized(lock) {
            _jobs.value.firstOrNull {
                it.kind == job.kind &&
                    it.resultKey == job.resultKey &&
                    it.episodeId == job.episodeId &&
                    (it.state.isActive || it.state == JobState.DONE || it.state == JobState.PAUSED)
            }?.let { return it }
            _jobs.update { it + job }
            save(context)
        }
        return job
    }

    fun update(context: Context, id: String, transform: (EnhanceJob) -> EnhanceJob): EnhanceJob? {
        synchronized(lock) {
            var updated: EnhanceJob? = null
            _jobs.update { list ->
                list.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()).also { new -> updated = new } else it }
            }
            save(context)
            return updated
        }
    }

    fun remove(context: Context, id: String) {
        val job = get(id) ?: return
        synchronized(lock) {
            _jobs.update { list -> list.filterNot { it.id == id } }
            save(context)
        }
        workDir(context, job).deleteRecursively()
    }

    /** The next job to run: Smart before downloads (someone is waiting for it), then oldest first. */
    fun next(): EnhanceJob? = _jobs.value
        .filter { it.state == JobState.QUEUED || it.state == JobState.WAITING }
        .sortedWith(compareBy<EnhanceJob> { if (it.kind == JobKind.SMART) 0 else 1 }.thenBy { it.createdAt })
        .firstOrNull()

    fun newId(): String = UUID.randomUUID().toString()

    /** Finished jobs older than a week leave the list (their files are already gone or delivered). */
    fun prune(context: Context, now: Long = System.currentTimeMillis()) {
        val old = _jobs.value.filter { it.state == JobState.DONE && now - it.updatedAt > 7L * 24 * 3600 * 1000 }
        old.forEach { remove(context, it.id) }
    }
}
