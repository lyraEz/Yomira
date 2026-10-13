package yomira.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
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
 * Only plain SharedPreferences and files are used here on purpose.
 */
object CrashGuard {

    private const val PREFS = "yomira_crash_guard"
    private const val KEY_LAUNCHES = "unstable_launches"
    private const val KEY_SAFE_MODE = "safe_mode"
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    /** Called when the main screen is created. Returns true when the report should open instead. */
    fun onLaunch(context: Context): Boolean {
        val launches = prefs(context).getInt(KEY_LAUNCHES, 0) + 1
        prefs(context).edit().putInt(KEY_LAUNCHES, launches).commit()
        return launches > LAUNCHES_BEFORE_REPORT
    }

    /** Called once the main screen has been visible for a few seconds. */
    fun markStable(context: Context) {
        prefs(context).edit().putInt(KEY_LAUNCHES, 0).apply()
    }

    fun resetLaunches(context: Context) {
        prefs(context).edit().putInt(KEY_LAUNCHES, 0).commit()
    }

    fun isSafeMode(context: Context): Boolean = prefs(context).getBoolean(KEY_SAFE_MODE, false)

    fun setSafeMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAFE_MODE, enabled).commit()
    }
}
