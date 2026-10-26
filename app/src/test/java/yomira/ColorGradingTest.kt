package yomira

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yomira.player.ColorCorrection
import yomira.player.ColorGrade
import yomira.player.ColorLook
import yomira.player.ColorShader
import java.util.Locale

class ColorGradingTest {

    @Test
    fun `nothing to do means no shader`() {
        assertNull(ColorShader.build(null, null))
        assertNull(ColorShader.build(ColorCorrection(), ColorGrade()))
        // Hues and balance alone do nothing until a tint amount is set.
        assertNull(ColorShader.build(null, ColorGrade(shadowHue = 120, highlightHue = 300, balance = 50)))
    }

    @Test
    fun `shader hooks the final picture and keeps alpha`() {
        val shader = ColorShader.build(ColorCorrection(temperature = 30), null)!!
        assertTrue(shader.startsWith("//!HOOK OUTPUT\n//!BIND HOOKED\n"))
        assertTrue("return vec4(clamp(c, 0.0, 1.0), color.a);" in shader)
        assertTrue("pow(c, vec3(2.2))" in shader)
        assertFalse("vibrance" in shader)
    }

    @Test
    fun `numbers use a dot in any language`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            ColorLook.entries.filter { it != ColorLook.NONE }.forEach { look ->
                val shader = ColorShader.build(ColorCorrection(exposure = 12, vibrance = 20), look.grade)!!
                assertFalse(Regex("""\d,\d""").containsMatchIn(shader), "comma in a number for $look")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `every look builds a balanced shader`() {
        ColorLook.entries.filter { it != ColorLook.NONE }.forEach { look ->
            val shader = ColorShader.build(null, look.grade)
            assertNotNull(shader, "$look")
            assertEquals(shader!!.count { it == '{' }, shader.count { it == '}' }, "$look")
            assertEquals(shader.count { it == '(' }, shader.count { it == ')' }, "$look")
        }
    }

    @Test
    fun `each setting adds only its own step`() {
        val vibrance = ColorShader.build(ColorCorrection(vibrance = 40), null)!!
        assertTrue("float sat" in vibrance)
        val muted = ColorShader.build(ColorCorrection(vibrance = -40), null)!!
        assertFalse("float sat" in muted)
        assertTrue("0.60000" in muted)
        val noir = ColorShader.build(null, ColorLook.NOIR.grade)!!
        assertTrue("mix(vec3(l), c, 0.00000)" in noir)
        assertFalse("smoothstep" in noir)
        val fade = ColorShader.build(null, ColorGrade(fade = 100))!!
        assertTrue("c = 0.15000 + c * 0.85000;" in fade)
    }

    @Test
    fun `hues and tints`() {
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0), ColorShader.hueToRgb(0.0), 1e-9)
        assertArrayEquals(doubleArrayOf(0.0, 1.0, 0.0), ColorShader.hueToRgb(120.0), 1e-9)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 1.0), ColorShader.hueToRgb(240.0), 1e-9)
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0), ColorShader.hueToRgb(360.0), 1e-9)
        // A tint moves color without changing brightness.
        listOf(0, 35, 195, 270).forEach { hue ->
            val t = ColorShader.tint(hue, 100)
            assertEquals(0.0, t[0] * 0.2126 + t[1] * 0.7152 + t[2] * 0.0722, 1e-9)
        }
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), ColorShader.tint(90, 0), 1e-12)
    }

    @Test
    fun `looks are recognized from their values`() {
        ColorLook.entries.forEach { assertEquals(it, ColorLook.of(it.grade)) }
        assertNull(ColorLook.of(ColorLook.CINEMA.grade.copy(fade = 1)))
    }
}
