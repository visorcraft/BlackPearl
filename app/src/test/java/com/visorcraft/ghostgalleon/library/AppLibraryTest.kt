package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLibraryTest {

    private val entries = listOf(
        AppEntry("com.zeta.game", "Zeta", isGame = true),
        AppEntry("com.alpha.app", "Alpha", isGame = false),
        AppEntry("com.mid.emu", "Middle", isGame = false),
    )
    private val library = AppLibrary { entries }

    @Test
    fun `visible returns all apps sorted by label`() {
        val labels = library.visible(Settings.DEFAULT).map { it.label }
        assertEquals(listOf("Alpha", "Middle", "Zeta"), labels)
    }

    @Test
    fun `visible excludes hidden packages`() {
        val s = Settings.DEFAULT.copy(hiddenPackages = setOf("com.mid.emu"))
        val pkgs = library.visible(s).map { it.packageName }
        assertEquals(listOf("com.alpha.app", "com.zeta.game"), pkgs)
    }

    @Test
    fun `dock returns dock packages in dock order`() {
        val s = Settings.DEFAULT.copy(
            dockSlots = listOf("com.zeta.game", null, "com.alpha.app", null, null)
        )
        val pkgs = library.dock(s).map { it.packageName }
        assertEquals(listOf("com.zeta.game", "com.alpha.app"), pkgs)
    }

    @Test
    fun `dock skips packages that are not installed or hidden`() {
        val s = Settings.DEFAULT.copy(
            dockSlots = listOf("com.gone.app", "com.mid.emu", null, null, null),
            hiddenPackages = setOf("com.mid.emu"),
        )
        assertEquals(emptyList<AppEntry>(), library.dock(s))
    }

    @Test
    fun `curated returns grid slot apps in slot order`() {
        val s = Settings.DEFAULT.copy(
            gridSlots = listOf("com.zeta.game", null, "com.alpha.app", "com.mid.emu"),
        )
        val pkgs = library.curated(s).map { it.packageName }
        assertEquals(listOf("com.zeta.game", "com.alpha.app", "com.mid.emu"), pkgs)
    }

    @Test
    fun `curated skips blank slots and packages that no longer resolve`() {
        val s = Settings.DEFAULT.copy(
            gridSlots = listOf(null, "com.gone.app", "com.alpha.app", null),
        )
        val pkgs = library.curated(s).map { it.packageName }
        assertEquals(listOf("com.alpha.app"), pkgs)
    }

    @Test
    fun `curated keeps hidden apps that occupy a slot`() {
        val s = Settings.DEFAULT.copy(
            gridSlots = listOf("com.mid.emu"),
            hiddenPackages = setOf("com.mid.emu"),
        )
        val pkgs = library.curated(s).map { it.packageName }
        assertEquals(listOf("com.mid.emu"), pkgs)
    }

    @Test
    fun `custom names replace labels in visible`() {
        val s = Settings.DEFAULT.copy(
            customNames = mapOf("com.alpha.app" to "My Alpha"),
        )
        val labels = library.visible(s).map { it.label }
        assertEquals(listOf("My Alpha", "Middle", "Zeta"), labels)
    }

    @Test
    fun `custom names apply in all, curated and dock`() {
        val s = Settings.DEFAULT.copy(
            gridSlots = listOf("com.zeta.game"),
            dockSlots = listOf("com.zeta.game", null, null, null, null),
            customNames = mapOf("com.zeta.game" to "Best Game"),
        )
        assertEquals("Best Game",
            library.all(s).first { it.packageName == "com.zeta.game" }.label)
        assertEquals(listOf("Best Game"), library.curated(s).map { it.label })
        assertEquals(listOf("Best Game"), library.dock(s).map { it.label })
    }

    @Test
    fun `packages without a custom name keep their label`() {
        val s = Settings.DEFAULT.copy(
            customNames = mapOf("com.other.app" to "Nope"),
        )
        val labels = library.visible(s).map { it.label }
        assertEquals(listOf("Alpha", "Middle", "Zeta"), labels)
    }
}
