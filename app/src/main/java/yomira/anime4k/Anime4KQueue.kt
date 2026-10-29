package yomira.anime4k

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Starts and controls the Anime4K worker. Every screen goes through here. */
object Anime4KQueue {

    const val WORK_NAME = "yomira-anime4k"

    /** True while the player is open; with "prioritize the player" processing waits for it to close. */
    @Volatile var playerOpen = false
        private set

    /** Starts the worker if it is not running. The constraints follow the settings. */
    fun kick(context: Context) {
        EnhanceJobs.load(context)
        if (EnhanceJobs.next() == null) return
        val prefs = Anime4KPreferences()
        // Jobs work on downloaded files, so no network is needed.
        val constraints = Constraints.Builder()
            .setRequiresCharging(prefs.onlyWhileCharging().get())
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<Anime4KWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun pause(context: Context, id: String) {
        EnhanceJobs.update(context, id) { if (it.state.isActive) it.copy(state = JobState.PAUSED, message = null) else it }
    }

    fun resume(context: Context, id: String) {
        EnhanceJobs.update(context, id) { it.copy(state = JobState.QUEUED, message = null) }
        kick(context)
    }

    fun retry(context: Context, id: String) = resume(context, id)

    /** Stops and forgets a job with its pieces. The original video is never touched. */
    fun cancel(context: Context, id: String) {
        EnhanceJobs.update(context, id) { it.copy(state = JobState.PAUSED) }
        EnhanceJobs.remove(context, id)
    }

    fun setPlayerOpen(context: Context, open: Boolean) {
        playerOpen = open
        if (!open) kick(context)
    }
}
