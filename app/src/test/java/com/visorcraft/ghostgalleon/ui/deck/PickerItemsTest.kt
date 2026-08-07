package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.RomEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerItemsTest {

    private fun app(pkg: String, label: String = pkg) =
        AppEntry(packageName = pkg, label = label, isGame = false)

    private fun rom(id: String, name: String, platformId: String) =
        RomEntry(id = id, name = name, platformId = platformId, uri = "content://$id", path = null)

    private val apps = listOf(
        app("com.brave.browser", "Brave"),
        app("org.videolan.vlc", "VLC"),
    )

    private val roms = listOf(
        rom("snes:roms/snes/zelda.smc", "Zelda", "snes"),
        rom("3ds:roms/3ds/mario.3ds", "Mario 3D Land", "3ds"),
        rom("snes:roms/snes/mario.smc", "Super Mario World", "snes"),
        rom("gb:roms/gb/tetris.gb", "Tetris", "gb"),
    )

    @Test
    fun `apps section precedes roms section with headers`() {
        val items = PickerItems.build(apps, roms, "")
        assertEquals(PickerItem.Header("Apps"), items.first())
        val romsHeader = items.indexOf(PickerItem.Header("ROMs"))
        assertTrue(romsHeader > 0)
        // All App items sit between the two headers; all Rom items after.
        items.subList(1, romsHeader).forEach { assertTrue(it is PickerItem.App) }
        items.subList(romsHeader + 1, items.size).forEach { assertTrue(it is PickerItem.Rom) }
        assertEquals(2 + 2 + 4, items.size)
    }

    @Test
    fun `roms sort by platform display name then rom name`() {
        val sorted = PickerItems.sortedRoms(roms)
        // Game Boy < Nintendo 3DS < Super Nintendo; SNES entries by name.
        assertEquals(
            listOf("Tetris", "Mario 3D Land", "Super Mario World", "Zelda"),
            sorted.map { it.name },
        )
    }

    @Test
    fun `query filters both sections`() {
        val items = PickerItems.build(apps, roms, "mario")
        val names = items.filterIsInstance<PickerItem.Rom>().map { it.entry.name }
        assertEquals(listOf("Mario 3D Land", "Super Mario World"), names)
        // No app matches "mario" -> the Apps header must disappear.
        assertTrue(items.none { it == PickerItem.Header("Apps") })
        assertEquals(PickerItem.Header("ROMs"), items.first())
    }

    @Test
    fun `query matches app labels and package names`() {
        val byLabel = PickerItems.build(apps, roms, "brave")
        assertEquals(
            listOf("Brave"),
            byLabel.filterIsInstance<PickerItem.App>().map { it.entry.label },
        )
        val byPackage = PickerItems.build(apps, roms, "videolan")
        assertEquals(
            listOf("VLC"),
            byPackage.filterIsInstance<PickerItem.App>().map { it.entry.label },
        )
    }

    @Test
    fun `query matches platform display name`() {
        val items = PickerItems.build(apps, roms, "super nintendo")
        assertEquals(
            listOf("Super Mario World", "Zelda"),
            items.filterIsInstance<PickerItem.Rom>().map { it.entry.name },
        )
    }

    @Test
    fun `empty sections drop their headers`() {
        val noRoms = PickerItems.build(apps, emptyList(), "")
        assertTrue(noRoms.none { it is PickerItem.Header && it.title == "ROMs" })
        assertEquals(3, noRoms.size)

        val noApps = PickerItems.build(emptyList(), roms, "")
        assertEquals(PickerItem.Header("ROMs"), noApps.first())

        assertTrue(PickerItems.build(apps, roms, "nothing matches this").isEmpty())
    }

    @Test
    fun `blank query behaves like no query`() {
        assertEquals(
            PickerItems.build(apps, roms, ""),
            PickerItems.build(apps, roms, "   "),
        )
    }

    @Test
    fun `dedupe-hidden roms are excluded from picker and sort`() {
        val withHidden = roms + rom(
            "switch:roms/switch/Hades II [0100A00019DE0800][v196608].nsp",
            "Hades II [0100A00019DE0800][v196608]",
            "switch",
        ).copy(visibleInUi = false)
        assertEquals(
            PickerItems.sortedRoms(roms),
            PickerItems.sortedRoms(withHidden),
        )
        val items = PickerItems.build(apps, withHidden, "hades")
        assertTrue(items.filterIsInstance<PickerItem.Rom>().isEmpty())
    }
}
