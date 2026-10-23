package yomira.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import eu.kanade.tachiyomi.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Keeps the app diagnosable when it crashes on start:
 *
 * - any uncaught exception is written to a file and shown by [CrashReportActivity], which runs in
 *   its own process and needs nothing from the app (no Compose, no DI), so it opens even when the
 *   crash happens while the app is still starting up;
 * - repeated launches that never reach a stable screen (for example a native crash in the GPU
 *   renderer, which skips Java crash handlers) open the same report on the next launch;
 * - safe mode turns the liquid glass effects off, so the app can still be used while the cause
 *   is investigated.
 *
 * Only plain files are used here on purpose.
 */
object CrashGuard {

    private const val STATE_DIR = "crash_guard"
    private const val REPORT_PROCESS_SUFFIX = ":crash_report"
    private const val CRASH_FILE = "last_crash.txt"

    /** Launches without reaching a stable screen before the report is shown. */
    private const val LAUNCHES_BEFORE_REPORT = 2

    fun processName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName()
        return runCatching {
            File("/proc/${Process.myPid()}/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
        }.getOrDefault(context.packageName)
    }

    fun isReportProcess(context: Context): Boolean = processName(context).endsWith(REPORT_PROCESS_SUFFIX)

    /** Installs the handler; call it first thing in Application.onCreate (and again after other handlers). */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app.applicationContext, previous))
    }

    private class Handler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching { writeCrash(context, thread, throwable) }
            val opened = runCatching {
                context.startActivity(
                    Intent(context, CrashReportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            }.isSuccess
            if (opened) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun crashFile(context: Context) = File(context.filesDir, CRASH_FILE)

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        crashFile(context).writeText(
            buildString {
                appendLine("Time: $time")
                appendLine("Process: ${processName(context)}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(throwable.stackTraceToString())
            },
        )
    }

    fun lastJavaCrash(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun clearJavaCrash(context: Context) {
        crashFile(context).delete()
    }

    /*
     * The launch count and safe mode live in small files read on every call, not in
     * SharedPreferences: the report runs in another process, and SharedPreferences keep a copy in
     * memory per process, so a value written there was not seen by a main process still alive
     * (the report then opened again on every launch).
     */
    private fun stateDir(context: Context) = File(context.filesDir, STATE_DIR).apply { mkdirs() }

    private fun launchesFile(context: Context) = File(stateDir(context), "launches")

    private fun safeModeFile(context: Context) = File(stateDir(context), "safe_mode")

    /** Launches counted for this app version ("versionCode:count"); another version starts at 0. */
    private fun readLaunches(context: Context): Int {
        val (version, count) = runCatching {
            launchesFile(context).readText().trim().split(':').let { it[0].toLong() to it[1].toInt() }
        }.getOrDefault(-1L to 0)
        return if (version == BuildConfig.VERSION_CODE.toLong()) count else 0
    }

    private fun writeLaunches(context: Context, count: Int) {
        runCatching { launchesFile(context).writeText("${BuildConfig.VERSION_CODE}:$count") }
    }

    /**
     * True when the main process last ended in a crash (Java crashes end as SIGNALED, killed by
     * the handler above), false when it ended normally (closed by the user, killed for memory),
     * null when Android cannot tell (before Android 11).
     */
    private fun lastExitWasCrash(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .firstOrNull { it.processName == context.packageName }
                ?: return null
            last.reason in crashReasons
        }.getOrNull()
    }

    private val crashReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_SIGNALED,
    )

    /** Called when the main screen is created. Returns true when the report should open instead. */
    fun onLaunch(context: Context): Boolean {
        val previous = if (lastExitWasCrash(context) == false) 0 else readLaunches(context)
        val launches = previous + 1
        writeLaunches(context, launches)
        return launches > LAUNCHES_BEFORE_REPORT
    }

    /** Called once the main screen has been visible for a few seconds. */
    fun markStable(context: Context) {
        writeLaunches(context, 0)
    }

    fun resetLaunches(context: Context) {
        writeLaunches(context, 0)
    }

    fun isSafeMode(context: Context): Boolean = safeModeFile(context).exists()

    fun setSafeMode(context: Context, enabled: Boolean) {
        runCatching {
            if (enabled) safeModeFile(context).writeText("on") else safeModeFile(context).delete()
        }
    }
}
