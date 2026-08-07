package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePackTest {

    @Test
    fun `byId returns all four builtins and defaults to GHOST`() {
        assertEquals(ThemePack.GHOST, ThemePack.byId("ghost"))
        assertEquals(ThemePack.THREEDS, ThemePack.byId("threeds"))
        assertEquals(ThemePack.OLED, ThemePack.byId("OLED"))
        assertEquals(ThemePack.NEON, ThemePack.byId(" neon "))
        assertEquals(ThemePack.GHOST, ThemePack.byId(null))
        assertEquals(ThemePack.GHOST, ThemePack.byId("unknown"))
        assertEquals(4, ThemePack.BUILTINS.size)
    }

    @Test
    fun `parseJson accepts valid accent hex`() {
        val tokens = ThemePack.parseJson(
            """{"id":"custom","displayName":"Custom","accentColor":"#FF2D95"}""",
        )
        assertNotNull(tokens)
        assertEquals("custom", tokens!!.id)
        assertEquals("Custom", tokens.displayName)
        assertEquals(0xFFFF2D95.toInt(), tokens.accentColor)
    }

    @Test
    fun `applyToSettings changes accent and pack id`() {
        val base = Settings.DEFAULT.copy(accentColor = 0xFF000000.toInt(), themePackId = "ghost")
        val applied = ThemePack.applyToSettings(base, ThemePack.NEON)
        assertEquals(ThemePack.NEON.id, applied.themePackId)
        assertEquals(ThemePack.NEON.accentColor, applied.accentColor)
        assertNull(applied.themeCustomJson)
    }

    @Test
    fun `parseJson null on garbage`() {
        assertNull(ThemePack.parseJson("not json"))
        assertNull(ThemePack.parseJson("{}"))
        assertNull(ThemePack.parseJson("""{"id":"x"}"""))
        assertNull(ThemePack.parseJson("""{"accentColor":"#FF00FF"}"""))
    }

    @Test
    fun `resolve uses custom JSON when set`() {
        val customJson =
            """{"id":"mine","displayName":"Mine","accentColor":"#00FF00"}"""
        val settings = Settings.DEFAULT.copy(
            themePackId = ThemePack.OLED.id,
            themeCustomJson = customJson,
        )
        val tokens = ThemePack.resolve(settings)
        assertEquals("mine", tokens.id)
        assertEquals(0xFF00FF00.toInt(), tokens.accentColor)

        val builtinOnly = Settings.DEFAULT.copy(
            themePackId = ThemePack.OLED.id,
            themeCustomJson = null,
        )
        assertEquals(ThemePack.OLED, ThemePack.resolve(builtinOnly))

        val badCustom = Settings.DEFAULT.copy(
            themePackId = ThemePack.NEON.id,
            themeCustomJson = "garbage",
        )
        assertEquals(ThemePack.NEON, ThemePack.resolve(badCustom))
    }
}
