package yomira.anime4k

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.UUID

/**
 * The Anime4K job list of Enhanced Downloads: one queue, one worker, one engine. Saved to
 * anime4k/jobs.json after every change.
 */
object EnhanceJobs {

    private const val LEGACY_SMART_KIND = "SMART"

    private val json = Json { ignoreUnknownKeys = true }
    private val _jobs = MutableStateFlow<List<EnhanceJob>>(emptyList())
    val jobs: StateFlow<List<EnhanceJob>> = _jobs.asStateFlow()

    @Volatile private var loaded = false
    private val lock = Any()

    fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "anime4k").also { it.mkdirs() }

    fun workDir(context: Context, job: EnhanceJob): File = File(root(context), "jobs/${job.id}")

    private fun file(context: Context) = File(root(context), "jobs.json")

    fun load(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val saved = runCatching { file(context).takeIf { it.exists() }?.readText()?.let(::decode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Anime4K: could not read the job list" } }
                .getOrNull().orEmpty()
            // A job cut off by the app closing continues where it stopped.
            _jobs.value = saved.map {
                if (it.state == JobState.PROCESSING || it.state == JobState.JOINING) {
                    it.copy(state = JobState.QUEUED)
                } else {
                    it
                }
            }
            loaded = true
        }
    }

    /**
     * Reads the saved list one job at a time, so a job this version cannot read is dropped alone
     * instead of losing the whole list. Jobs of the removed Smart Anime4K are dropped too.
     */
    private fun decode(text: String): List<EnhanceJob> = json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
        if ((element as? JsonObject)?.get("kind")?.jsonPrimitive?.content == LEGACY_SMART_KIND) return@mapNotNull null
        runCatching { json.decodeFromJsonElement(EnhanceJob.serializer(), element) }
            .onFailure { logcat(LogPriority.WARN, it) { "Anime4K: dropped a job that could not be read" } }
            .getOrNull()
    }

    /**
     * Removes what Smart Anime4K left behind: its cache of prepared episodes, the pieces of its
     * unfinished jobs and the jobs themselves. Run once when updating to 1.13.0.
     */
    fun removeSmartLeftovers(context: Context) {
        File(root(context), "smart").deleteRecursively()
        val text = file(context).takeIf { it.exists() }?.readText() ?: return
        val smartIds = runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val job = element as? JsonObject ?: return@mapNotNull null
                job["id"]?.jsonPrimitive?.content?.takeIf { job["kind"]?.jsonPrimitive?.content == LEGACY_SMART_KIND }
            }
        }.getOrDefault(emptyList())
        smartIds.forEach { File(root(context), "jobs/$it").deleteRecursively() }
        if (smartIds.isNotEmpty()) {
            synchronized(lock) {
                loaded = false
                load(context)
                save(context)
            }
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

    /** The next job to run: the oldest waiting one. */
    fun next(): EnhanceJob? = _jobs.value
        .filter { it.state == JobState.QUEUED || it.state == JobState.WAITING }
        .minByOrNull { it.createdAt }

    fun newId(): String = UUID.randomUUID().toString()

    /** Finished jobs older than a week leave the list (their files are already gone or delivered). */
    fun prune(context: Context, now: Long = System.currentTimeMillis()) {
        val old = _jobs.value.filter { it.state == JobState.DONE && now - it.updatedAt > 7L * 24 * 3600 * 1000 }
        old.forEach { remove(context, it.id) }
    }
}
