package yomira.anime4k

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.core.common.i18n.stringResource
import yomira.i18n.YMR

/** Progress and result notifications of the Anime4K worker. */
object Anime4KNotifier {

    const val CHANNEL = "yomira_anime4k"
    const val ID_PROGRESS = -2601
    private const val ID_RESULT_BASE = -2700

    private fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.stringResource(YMR.strings.a4k_title))
                .build(),
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun foreground(context: Context, job: EnhanceJob?): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle(job?.let { "${it.animeTitle} · ${it.episodeName}" } ?: context.stringResource(YMR.strings.a4k_title))
            .setContentText(job?.let { describe(context, it) } ?: context.stringResource(YMR.strings.a4k_state_queued))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .apply {
                if (job != null && job.totalFrames > 0) {
                    setProgress(1000, (job.progress * 1000).toInt(), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ID_PROGRESS, notification, type) else ForegroundInfo(ID_PROGRESS, notification)
    }

    fun update(context: Context, job: EnhanceJob) {
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROGRESS, foreground(context, job).notification) }
    }

    fun result(context: Context, job: EnhanceJob) {
        ensureChannel(context)
        val text = when (job.state) {
            JobState.DONE -> context.stringResource(YMR.strings.a4k_notify_done, job.preset.displayName(context))
            else -> context.stringResource(YMR.strings.a4k_notify_failed, job.message.orEmpty())
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle("${job.animeTitle} · ${job.episodeName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT_BASE - (job.episodeId % 90).toInt(), notification) }
    }

    fun describe(context: Context, job: EnhanceJob): String {
        val percent = (job.progress * 100).toInt()
        val eta = job.remainingSeconds?.let { formatDuration(context, it) }
        return when (job.state) {
            JobState.QUEUED -> context.stringResource(YMR.strings.a4k_state_queued)
            JobState.PROCESSING -> if (eta != null) {
                context.stringResource(YMR.strings.a4k_state_processing_eta, percent, eta)
            } else {
                context.stringResource(YMR.strings.a4k_state_processing, percent)
            }
            JobState.WAITING -> context.stringResource(YMR.strings.a4k_state_waiting, job.message.orEmpty())
            JobState.PAUSED -> context.stringResource(YMR.strings.a4k_state_paused, percent)
            JobState.JOINING -> context.stringResource(YMR.strings.a4k_state_joining)
            JobState.DONE -> context.stringResource(YMR.strings.a4k_state_done)
            JobState.FAILED -> context.stringResource(YMR.strings.a4k_state_failed, job.message.orEmpty())
        }
    }

    fun formatDuration(context: Context, seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
            context.stringResource(YMR.strings.a4k_hours_minutes, hours.toInt(), minutes.toInt())
        } else {
            context.stringResource(YMR.strings.a4k_minutes, minutes.coerceAtLeast(1).toInt())
        }
    }
}
