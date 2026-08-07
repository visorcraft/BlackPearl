package com.visorcraft.blackpearl.settings

import com.visorcraft.blackpearl.rom.RomEntry
import com.visorcraft.blackpearl.rom.RomLibrary
import com.visorcraft.blackpearl.state.UIMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBundleTest {

    private val settings = Settings(
        accentColor = 0xFFFF5722.toInt(),
        defaultMode = UIMode.GAME,
        gridDirection = "horizontal",
        dockSlots = DockSlots.compact(listOf("a.b")),
        hiddenPackages = setOf("x.y"),
        customNames = mapOf("a.b" to "Renamed"),
        customIcons = mapOf("a.b" to "content://media/external/images/media/7"),
    )

    private val roms = listOf(
        RomEntry(
            id = "snes:roms/snes/Chrono Trigger (USA).sfc",
            name = "Chrono Trigger (USA)",
            platformId = "snes",
            uri = "content://com.android.externalstorage.documents/document/7F7E-2949%3Aroms%2Fsnes%2Fx.sfc",
            path = "/storage/7F7E-2949/roms/snes/x.sfc",
            artUri = null,
            visibleInUi = true,
        ),
        RomEntry(
            id = "switch:roms/switch/Game [v1].nsp",
            name = "Game",
            platformId = "switch",
            uri = "content://example/doc/2",
            path = null,
            artUri = "content://example/art/2",
            visibleInUi = false,
        ),
    )

    @Test
    fun `pack then unpack round-trips settings and rom library`() {
        val text = SettingsBundle.pack(
            SettingsStore.toJson(settings),
            RomLibrary.entriesToJson(roms),
        )
        val (settingsJson, romJson) = SettingsBundle.unpack(text)
        assertEquals(settings, SettingsStore.parse(settingsJson))
        assertEquals(roms, RomLibrary.parseEntries(romJson))
    }

    @Test
    fun `unpack rejects non-json text`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBundle.unpack("{ this is not json")
        }
    }

    @Test
    fun `unpack rejects json without the bundle marker`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBundle.unpack("""{"settings":{},"romLibrary":[]}""")
        }
    }

    @Test
    fun `unpack rejects a bundle missing the settings object`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBundle.unpack(
                """{"bundle":"blackpearl-settings","romLibrary":[]}""")
        }
    }

    @Test
    fun `unpack rejects a bundle missing the rom library array`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBundle.unpack(
                """{"bundle":"blackpearl-settings","settings":{}}""")
        }
    }

    @Test
    fun `unpack rejects a rom library with the wrong shape`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBundle.unpack(
                """{"bundle":"blackpearl-settings","settings":{},"romLibrary":{}}""")
        }
    }
}
