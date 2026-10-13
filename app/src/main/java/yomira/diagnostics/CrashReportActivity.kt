package yomira.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Crash report built with plain Views, running in its own process (see [CrashGuard]). It shows the
 * last Java crash, the recent process exits and the app's own logcat, and lets the user copy or
 * share them, open the app in safe mode (no glass effects) or try again normally.
 */
class CrashReportActivity : Activity() {

    private lateinit var reportView: TextView
    private var report: String = ""
    private val portuguese = Locale.getDefault().language == "pt"

    private fun t(pt: String, en: String) = if (portuguese) pt else en

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 12, 16))
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(
            TextView(this).apply {
                text = t("O Yomira fechou inesperadamente", "Yomira closed unexpectedly")
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
            },
        )
        root.addView(
            TextView(this).apply {
                text = t(
                    "Copie o relatório abaixo e envie para o desenvolvedor. \"Abrir em modo seguro\" desliga os efeitos de vidro para você continuar usando o app.",
                    "Copy the report below and send it to the developer. \"Open in safe mode\" turns the glass effects off so you can keep using the app.",
                )
                setTextColor(Color.rgb(200, 200, 210))
                textSize = 14f
                setPadding(0, pad / 2, 0, pad / 2)
            },
        )

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        button(t("Copiar relatório", "Copy report")) { copy() }
        button(t("Compartilhar relatório", "Share report")) { share() }
        button(t("Abrir em modo seguro (sem vidro)", "Open in safe mode (no glass)")) { reopen(safeMode = true) }
        button(t("Tentar abrir normalmente", "Try opening normally")) { reopen(safeMode = false) }
        root.addView(buttons)

        reportView = TextView(this).apply {
            setTextColor(Color.rgb(220, 220, 220))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = t("Coletando informações…", "Collecting information…")
        }
        val horizontal = HorizontalScrollView(this).apply { addView(reportView) }
        val vertical = ScrollView(this).apply {
            addView(horizontal)
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(vertical, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.gravity = Gravity.TOP
        setContentView(root)

        thread(name = "crash-report") {
            val text = buildReport()
            runOnUiThread {
                report = text
                reportView.text = text
            }
        }
    }

    private fun buildReport(): String = buildString {
        appendLine("Yomira ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE} ${BuildConfig.COMMIT_SHA}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABIs ${Build.SUPPORTED_ABIS.joinToString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("Safe mode: ${CrashGuard.isSafeMode(this@CrashReportActivity)}")
        appendLine()
        appendLine("== Last Java crash ==")
        appendLine(CrashGuard.lastJavaCrash(this@CrashReportActivity) ?: "(none)")
        appendLine()
        appendLine("== Recent exits ==")
        appendLine(exitReasons())
        appendLine()
        appendLine("== Logcat ==")
        appendLine(logcat())
    }

    private fun exitReasons(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(needs Android 11)"
        return runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
            am.getHistoricalProcessExitReasons(packageName, 0, 6).joinToString("\n") { info ->
                val reason = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
                    ApplicationExitInfo.REASON_ANR -> "ANR"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                    else -> "reason ${info.reason}"
                }
                "${format.format(Date(info.timestamp))} ${info.processName} $reason status=${info.status} ${info.description.orEmpty()}"
            }.ifEmpty { "(none)" }
        }.getOrElse { "(unavailable: ${it.message})" }
    }

    private fun logcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "1500")
            .redirectErrorStream(true)
            .start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.destroy()
        // Keep warnings, errors and fatal lines (and the native crash dump), which carry the cause.
        lines.filter { line ->
            val level = line.split(Regex("\\s+")).getOrNull(4)
            level == "W" || level == "E" || level == "F" || "DEBUG" in line || "AndroidRuntime" in line
        }.takeLast(600).joinToString("\n").ifEmpty { "(empty)" }
    }.getOrElse { "(unavailable: ${it.message})" }

    private fun copy() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Yomira crash", report))
        Toast.makeText(this, t("Relatório copiado", "Report copied"), Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val text = if (report.length > 90_000) report.takeLast(90_000) else report
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Yomira crash report")
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching { startActivity(Intent.createChooser(intent, null)) }
    }

    private fun reopen(safeMode: Boolean) {
        CrashGuard.setSafeMode(this, safeMode)
        CrashGuard.resetLaunches(this)
        CrashGuard.clearJavaCrash(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}
