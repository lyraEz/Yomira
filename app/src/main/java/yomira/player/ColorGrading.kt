package yomira.player

import android.app.Application
import `is`.xyz.mpv.MPV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Color correction fixes the picture. Every value goes from -100 to 100; 0 changes nothing. */
data class ColorCorrection(
    val temperature: Int = 0,
    val tint: Int = 0,
    val exposure: Int = 0,
    val shadows: Int = 0,
    val highlights: Int = 0,
    val vibrance: Int = 0,
) {
    val isNeutral: Boolean get() = this == ColorCorrection()
}

/**
 * Color grading gives the picture a look. Hues are degrees (0-359), the tint amounts and the fade
 * go from 0 to 100, the rest from -100 to 100.
 */
data class ColorGrade(
    val shadowHue: Int = 210,
    val shadowAmount: Int = 0,
    val highlightHue: Int = 35,
    val highlightAmount: Int = 0,
    val balance: Int = 0,
    val curve: Int = 0,
    val saturation: Int = 0,
    val fade: Int = 0,
) {
    val isNeutral: Boolean
        get() = shadowAmount == 0 && highlightAmount == 0 && curve == 0 && saturation == 0 && fade == 0
}

/** Ready-made looks for the grading card. */
enum class ColorLook(val grade: ColorGrade) {
    NONE(ColorGrade()),
    CINEMA(ColorGrade(shadowHue = 195, shadowAmount = 35, highlightHue = 32, highlightAmount = 30, curve = 20, saturation = 5)),
    WARM(ColorGrade(shadowHue = 25, shadowAmount = 20, highlightHue = 40, highlightAmount = 30, curve = 5)),
    COOL(ColorGrade(shadowHue = 215, shadowAmount = 30, highlightHue = 195, highlightAmount = 15, curve = 5)),
    VIVID(ColorGrade(curve = 25, saturation = 30)),
    VINTAGE(
        ColorGrade(shadowHue = 30, shadowAmount = 20, highlightHue = 50, highlightAmount = 25, curve = -10, saturation = -25, fade = 35),
    ),
    PASTEL(
        ColorGrade(shadowHue = 270, shadowAmount = 12, highlightHue = 330, highlightAmount = 12, curve = -25, saturation = -15, fade = 25),
    ),
    DRAMATIC(ColorGrade(shadowHue = 220, shadowAmount = 25, highlightHue = 40, highlightAmount = 10, curve = 45, saturation = -10)),
    NOIR(ColorGrade(curve = 35, saturation = -100)),
    ;

    companion object {
        fun of(grade: ColorGrade): ColorLook? = entries.firstOrNull { it.grade == grade }
    }
}

class ColorPreferences(private val store: PreferenceStore = Injekt.get()) {
    fun correctionOn() = store.getBoolean("yomira_color_correction_on", true)
    fun temperature() = store.getInt("yomira_cc_temperature", 0)
    fun tint() = store.getInt("yomira_cc_tint", 0)
    fun exposure() = store.getInt("yomira_cc_exposure", 0)
    fun shadows() = store.getInt("yomira_cc_shadows", 0)
    fun highlights() = store.getInt("yomira_cc_highlights", 0)
    fun vibrance() = store.getInt("yomira_cc_vibrance", 0)

    fun gradingOn() = store.getBoolean("yomira_color_grading_on", true)
    fun shadowHue() = store.getInt("yomira_cg_shadow_hue", ColorGrade().shadowHue)
    fun shadowAmount() = store.getInt("yomira_cg_shadow_amount", 0)
    fun highlightHue() = store.getInt("yomira_cg_highlight_hue", ColorGrade().highlightHue)
    fun highlightAmount() = store.getInt("yomira_cg_highlight_amount", 0)
    fun balance() = store.getInt("yomira_cg_balance", 0)
    fun curve() = store.getInt("yomira_cg_curve", 0)
    fun saturation() = store.getInt("yomira_cg_saturation", 0)
    fun fade() = store.getInt("yomira_cg_fade", 0)

    private fun correctionPrefs() = listOf(temperature(), tint(), exposure(), shadows(), highlights(), vibrance())

    private fun gradePrefs() =
        listOf(shadowHue(), shadowAmount(), highlightHue(), highlightAmount(), balance(), curve(), saturation(), fade())

    fun correction() = ColorCorrection(
        temperature = temperature().get(),
        tint = tint().get(),
        exposure = exposure().get(),
        shadows = shadows().get(),
        highlights = highlights().get(),
        vibrance = vibrance().get(),
    )

    fun grade() = ColorGrade(
        shadowHue = shadowHue().get(),
        shadowAmount = shadowAmount().get(),
        highlightHue = highlightHue().get(),
        highlightAmount = highlightAmount().get(),
        balance = balance().get(),
        curve = curve().get(),
        saturation = saturation().get(),
        fade = fade().get(),
    )

    fun setGrade(grade: ColorGrade) {
        shadowHue().set(grade.shadowHue)
        shadowAmount().set(grade.shadowAmount)
        highlightHue().set(grade.highlightHue)
        highlightAmount().set(grade.highlightAmount)
        balance().set(grade.balance)
        curve().set(grade.curve)
        saturation().set(grade.saturation)
        fade().set(grade.fade)
    }

    fun resetCorrection() = correctionPrefs().forEach { it.delete() }

    fun resetGrade() = gradePrefs().forEach { it.delete() }
}

/**
 * Writes the mpv user shader for a correction and a grade. It hooks OUTPUT, so it works on the
 * final picture after scaling and any Anime4K shaders, in display gamma, and never touches the
 * subtitles. Everything is a constant in the file: no uniforms, so it runs the same on gpu and
 * gpu-next. Returns null when nothing would change the picture.
 */
object ColorShader {

    private val LUMA = doubleArrayOf(0.2126, 0.7152, 0.0722)

    fun build(correction: ColorCorrection?, grade: ColorGrade?): String? {
        val c = correction?.takeUnless { it.isNeutral }
        val g = grade?.takeUnless { it.isNeutral }
        if (c == null && g == null) return null

        val body = StringBuilder()
        if (c != null) correction(c, body)
        if (g != null) grading(g, body)

        return buildString {
            appendLine("//!HOOK OUTPUT")
            appendLine("//!BIND HOOKED")
            appendLine("//!DESC Yomira color correction and grading")
            appendLine()
            appendLine("const vec3 LUMA = vec3(${f(LUMA[0])}, ${f(LUMA[1])}, ${f(LUMA[2])});")
            appendLine()
            appendLine("vec4 hook() {")
            appendLine("    vec4 color = HOOKED_texOff(0);")
            appendLine("    vec3 c = clamp(color.rgb, 0.0, 1.0);")
            appendLine("    float l;")
            append(body)
            appendLine("    return vec4(clamp(c, 0.0, 1.0), color.a);")
            appendLine("}")
        }
    }

    private fun correction(c: ColorCorrection, out: StringBuilder) {
        if (c.temperature != 0 || c.tint != 0 || c.exposure != 0) {
            // White balance and exposure act on light, so they run in (approximately) linear light.
            val t = c.temperature / 100.0
            val g = c.tint / 100.0
            val gain = doubleArrayOf(1.0 + 0.3 * t, 1.0 - 0.2 * g, 1.0 - 0.3 * t)
            val white = gain[0] * LUMA[0] + gain[1] * LUMA[1] + gain[2] * LUMA[2]
            val exposure = 2.0.pow(c.exposure / 100.0)
            val k = gain.map { it / white * exposure }
            out.appendLine("    // temperature ${c.temperature}, tint ${c.tint}, exposure ${c.exposure}")
            out.appendLine("    c = pow(c, vec3(2.2)) * vec3(${f(k[0])}, ${f(k[1])}, ${f(k[2])});")
            out.appendLine("    c = pow(max(c, 0.0), vec3(1.0 / 2.2));")
        }
        if (c.shadows != 0 || c.highlights != 0) {
            // Smooth curves that keep black and white in place; the ratio keeps the hue.
            val s = 1.5 * c.shadows / 100.0
            val h = 1.5 * c.highlights / 100.0
            out.appendLine("    // shadows ${c.shadows}, highlights ${c.highlights}")
            out.appendLine("    l = dot(c, LUMA);")
            out.appendLine(
                "    c *= (l + ${f(s)} * l * (1.0 - l) * (1.0 - l) + ${f(h)} * l * l * (1.0 - l)) / max(l, 1e-4);",
            )
        }
        if (c.vibrance != 0) {
            val v = c.vibrance / 100.0
            out.appendLine("    // vibrance ${c.vibrance}")
            out.appendLine("    l = dot(c, LUMA);")
            if (v > 0) {
                // Muted colors gain the most; already strong colors and skin barely change.
                out.appendLine("    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));")
                out.appendLine("    c = mix(vec3(l), c, 1.0 + ${f(v)} * (1.0 - sat));")
            } else {
                out.appendLine("    c = mix(vec3(l), c, ${f(1.0 + v)});")
            }
        }
    }

    private fun grading(g: ColorGrade, out: StringBuilder) {
        if (g.curve != 0) {
            // S-shaped (or flattening) contrast curve; stays monotonic for the whole range.
            out.appendLine("    // contrast curve ${g.curve}")
            out.appendLine("    c = clamp(c, 0.0, 1.0);")
            out.appendLine("    c += ${f(g.curve / 100.0)} * c * (1.0 - c) * (2.0 * c - 1.0);")
        }
        if (g.saturation != 0) {
            out.appendLine("    // color intensity ${g.saturation}")
            out.appendLine("    l = dot(c, LUMA);")
            out.appendLine("    c = mix(vec3(l), c, ${f(1.0 + g.saturation / 100.0)});")
        }
        if (g.shadowAmount != 0 || g.highlightAmount != 0) {
            // Split toning: a hue pushed into the shadows and another into the highlights without
            // changing brightness. Pure black stays black, so letterbox bars keep their color.
            val shadow = tint(g.shadowHue, g.shadowAmount)
            val highlight = tint(g.highlightHue, g.highlightAmount)
            val pivot = 0.5 - 0.25 * g.balance / 100.0
            out.appendLine(
                "    // shadows ${g.shadowHue}° ${g.shadowAmount}, highlights ${g.highlightHue}° ${g.highlightAmount}, balance ${g.balance}",
            )
            out.appendLine("    l = dot(c, LUMA);")
            out.appendLine("    float wh = smoothstep(${f(pivot - 0.4)}, ${f(pivot + 0.4)}, l);")
            out.appendLine("    float ws = (1.0 - wh) * smoothstep(0.0, 0.08, l);")
            out.appendLine("    c += vec3(${f(shadow[0])}, ${f(shadow[1])}, ${f(shadow[2])}) * ws;")
            out.appendLine("    c += vec3(${f(highlight[0])}, ${f(highlight[1])}, ${f(highlight[2])}) * wh;")
        }
        if (g.fade != 0) {
            // Film fade: raises the blacks, leaves white where it is.
            val lift = 0.15 * g.fade / 100.0
            out.appendLine("    // fade ${g.fade}")
            out.appendLine("    c = ${f(lift)} + c * ${f(1.0 - lift)};")
        }
    }

    /** The color of [hue] with its brightness taken out, scaled by [amount] (0-100). */
    internal fun tint(hue: Int, amount: Int): DoubleArray {
        val rgb = hueToRgb(hue.toDouble())
        val luma = rgb[0] * LUMA[0] + rgb[1] * LUMA[1] + rgb[2] * LUMA[2]
        val scale = 0.3 * amount / 100.0
        return DoubleArray(3) { (rgb[it] - luma) * scale }
    }

    /** Fully saturated color of a hue in degrees (HSV with S = V = 1). */
    internal fun hueToRgb(hue: Double): DoubleArray {
        val h = ((hue % 360.0) + 360.0) % 360.0
        fun channel(n: Double): Double {
            val k = (n + h / 60.0) % 6.0
            return 1.0 - max(0.0, min(min(k, 4.0 - k), 1.0))
        }
        return doubleArrayOf(channel(5.0), channel(3.0), channel(1.0))
    }

    /** GLSL float literal, always with a dot whatever the phone's language. */
    private fun f(value: Double): String = String.format(Locale.US, "%.5f", value)
}

/**
 * Keeps mpv's shader list in step with the color settings. The color shader always goes last and
 * only replaces its own previous file, so Anime4K and any other shader stay as they are.
 */
object ColorGrading {

    private const val DIR = "yomira_color"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    /** The last color shader put in mpv's list, so the next change can take it out. */
    @Volatile private var applied: String? = null

    fun shader(prefs: ColorPreferences = ColorPreferences()): String? = ColorShader.build(
        correction = prefs.correction().takeIf { prefs.correctionOn().get() },
        grade = prefs.grade().takeIf { prefs.gradingOn().get() },
    )

    /**
     * Writes the shader for the current settings and returns its path, or null when nothing changes
     * the picture. The name follows the content: mpv keeps files it already read by path, so a new
     * setting needs a new name to be read again.
     */
    @Synchronized
    fun shaderPath(): String? {
        val text = shader() ?: return null
        val dir = File(Injekt.get<Application>().filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "color_${digest(text)}.glsl")
        if (!file.exists()) {
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
        dir.listFiles()?.filter { it.name != file.name }?.forEach { it.delete() }
        return file.absolutePath
    }

    /** A full shader list (for code that sets the whole list) with the color shader at the end. */
    fun withColor(chain: String): String {
        val path = runCatching { shaderPath() }
            .onFailure { logcat(LogPriority.WARN, it) { "Color: could not write the shader" } }
            .getOrNull()
        applied = path
        return listOfNotNull(chain.takeIf { it.isNotEmpty() }, path).joinToString(":")
    }

    /**
     * Puts the current color shader in mpv's list in place of the previous one. [debounceMs] lets a
     * slider settle first, so mpv does not rebuild its shaders on every step.
     */
    fun apply(mpv: MPV?, debounceMs: Long = 0) {
        mpv ?: return
        pending?.cancel()
        pending = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val path = runCatching { shaderPath() }
                .onFailure { logcat(LogPriority.WARN, it) { "Color: could not write the shader" } }
                .getOrNull()
            runCatching {
                listOfNotNull(applied, path).distinct().forEach {
                    mpv.command("change-list", "glsl-shaders", "remove", it)
                }
                if (path != null) mpv.command("change-list", "glsl-shaders", "append", path)
                applied = path
            }.onFailure { logcat(LogPriority.WARN, it) { "Color: could not update mpv's shaders" } }
        }
    }

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray())
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
