package com.visorcraft.blackpearl.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformsTest {

    @Test
    fun `registry covers exactly the 15 ROM-launchable platforms`() {
        assertEquals(
            listOf(
                "gb", "gbc", "gba", "snes", "genesis", "n64", "nds", "3ds",
                "switch", "psp", "ps2", "dreamcast", "gamecube", "wii", "wiiu",
            ),
            Platforms.ALL.map { it.id },
        )
    }

    @Test
    fun `winlator is not in the registry`() {
        assertNull(Platforms.byId("windows"))
        assertTrue(Platforms.ALL.none { it.player.component.startsWith("com.winlator") })
    }

    @Test
    fun `every platform id resolves through byId`() {
        Platforms.ALL.forEach { assertEquals(it, Platforms.byId(it.id)) }
    }

    @Test
    fun `retroarch cores use the exact installed so names`() {
        fun core(p: Platform) = p.player.extras.getValue("LIBRETRO")
        val dir = "/data/data/com.retroarch.aarch64/cores"
        assertEquals("$dir/gambatte_libretro_android.so", core(Platforms.GB))
        assertEquals("$dir/gambatte_libretro_android.so", core(Platforms.GBC))
        assertEquals("$dir/mgba_libretro_android.so", core(Platforms.GBA))
        assertEquals("$dir/snes9x_libretro_android.so", core(Platforms.SNES))
        assertEquals("$dir/genesis_plus_gx_libretro_android.so", core(Platforms.GENESIS))
        assertEquals("$dir/mupen64plus_next_gles3_libretro_android.so", core(Platforms.N64))
    }

    @Test
    fun `retroarch players are path-only with the ROM extra`() {
        listOf(Platforms.GB, Platforms.GBC, Platforms.GBA, Platforms.SNES,
            Platforms.GENESIS, Platforms.N64).forEach { p ->
            assertEquals(UriStyle.PATH, p.player.uriStyle)
            assertFalse(p.player.grantRead)
            assertEquals("{file.path}", p.player.extras.getValue("ROM"))
            assertEquals(
                "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
                p.player.component,
            )
        }
    }

    @Test
    fun `switch uses Eden with content URI and the grant flag`() {
        val p = Platforms.SWITCH.player
        assertEquals(
            "dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
            p.component,
        )
        assertEquals("android.nfc.action.TECH_DISCOVERED", p.action)
        assertEquals(UriStyle.URI, p.uriStyle)
        assertTrue(p.grantRead)
    }

    @Test
    fun `3ds uses Azahar VIEW with both card folders`() {
        val p = Platforms.N3DS
        assertEquals(listOf("3ds", "new-nintendo-3ds"), p.folderNames)
        assertEquals("android.intent.action.VIEW", p.player.action)
        assertEquals(
            "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
            p.player.component,
        )
        assertEquals(UriStyle.URI, p.player.uriStyle)
        assertTrue(p.player.grantRead)
    }

    @Test
    fun `nds uses melonDualDS custom action and uri extra`() {
        val p = Platforms.NDS.player
        assertEquals("me.magnum.melondualds.LAUNCH_ROM", p.action)
        assertEquals(
            "me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity",
            p.component,
        )
        assertEquals("{file.uri}", p.extras.getValue("uri"))
    }

    @Test
    fun `extra-style players carry their launch extras`() {
        assertEquals("{file.uri}",
            Platforms.GAMECUBE.player.extras.getValue("AutoStartFile"))
        assertEquals("{file.uri}",
            Platforms.WII.player.extras.getValue("AutoStartFile"))
        assertEquals("{file.uri}",
            Platforms.PS2.player.extras.getValue("bootPath"))
        assertEquals("android.intent.action.MAIN", Platforms.WII.player.action)
        assertEquals("android.intent.action.VIEW", Platforms.GAMECUBE.player.action)
    }

    @Test
    fun `folder matching is case-insensitive across card and internal layouts`() {
        assertEquals(Platforms.SNES, Platforms.platformForFolder("snes"))
        assertEquals(Platforms.SNES, Platforms.platformForFolder("SNES"))
        assertEquals(Platforms.GENESIS, Platforms.platformForFolder("genesis-slash-megadrive"))
        assertEquals(Platforms.GAMECUBE, Platforms.platformForFolder("GameCube"))
        assertEquals(Platforms.WIIU, Platforms.platformForFolder("WiiU"))
        assertNull(Platforms.platformForFolder("roms"))
        assertNull(Platforms.platformForFolder("windows"))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertTrue(Platforms.SNES.acceptsExtension("SMC"))
        assertTrue(Platforms.GBA.acceptsExtension("agb"))
        assertFalse(Platforms.SNES.acceptsExtension("srm"))
        assertFalse(Platforms.NDS.acceptsExtension("3ds"))
    }
}
