package com.visorcraft.blackpearl.rom

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomLauncherTest {

    private fun entry(
        platformId: String,
        uri: String = "content://com.android.externalstorage.documents/document/7F7E-2949%3Aroms%2Fsnes%2Fx.smc",
        path: String? = "/storage/7F7E-2949/roms/snes/x.smc",
    ) = RomEntry(id = "$platformId:roms/snes/x.smc", name = "x", platformId = platformId, uri = uri, path = path)

    @Test
    fun `uri style sets data and substitutes no extras`() {
        val plan = LaunchPlanBuilder.build(Platforms.N3DS.player, entry("3ds"))!!
        assertEquals("android.intent.action.VIEW", plan.action)
        assertEquals("org.azahar_emu.azahar", plan.packageName)
        assertEquals("org.citra.citra_emu.activities.EmulationActivity", plan.className)
        assertEquals(entry("3ds").uri, plan.dataString)
        assertTrue(plan.extras.isEmpty())
        assertTrue(plan.grantRead)
    }

    @Test
    fun `path style has no data and substitutes the ROM extra`() {
        val e = entry("snes")
        val plan = LaunchPlanBuilder.build(Platforms.SNES.player, e)!!
        assertEquals("android.intent.action.MAIN", plan.action)
        assertEquals("com.retroarch.aarch64", plan.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", plan.className)
        assertNull(plan.dataString)
        assertEquals(e.path, plan.extras.getValue("ROM"))
        assertEquals(
            "/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so",
            plan.extras.getValue("LIBRETRO"),
        )
        assertFalse(plan.grantRead)
    }

    @Test
    fun `path style with null path fails to build`() {
        assertNull(LaunchPlanBuilder.build(Platforms.SNES.player, entry("snes", path = null)))
    }

    @Test
    fun `melonDualDS custom action and uri extra are substituted`() {
        val e = entry("nds")
        val plan = LaunchPlanBuilder.build(Platforms.NDS.player, e)!!
        assertEquals("me.magnum.melondualds.LAUNCH_ROM", plan.action)
        assertEquals("me.magnum.melonds.ui.emulator.EmulatorActivity", plan.className)
        assertEquals(e.uri, plan.dataString)
        assertEquals(e.uri, plan.extras.getValue("uri"))
    }

    @Test
    fun `eden keeps the grant flag and TECH_DISCOVERED action`() {
        val plan = LaunchPlanBuilder.build(Platforms.SWITCH.player, entry("switch"))!!
        assertEquals("android.nfc.action.TECH_DISCOVERED", plan.action)
        assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", plan.className)
        assertTrue(plan.grantRead)
    }

    @Test
    fun `dot-relative component class names expand against the package`() {
        val e = entry("ps2")
        val plan = LaunchPlanBuilder.build(Platforms.PS2.player, e)!!
        assertEquals("xyz.aethersx2.android", plan.packageName)
        assertEquals("xyz.aethersx2.android.EmulationActivity", plan.className)
        assertEquals(e.uri, plan.extras.getValue("bootPath"))
    }

    @Test
    fun `placeholders substitute inside larger strings`() {
        val template = PlayerTemplate(
            id = "t",
            displayName = "T",
            component = "a.b/a.b.C",
            action = null,
            uriStyle = UriStyle.PATH,
            extras = mapOf("X" to "pre {file.path} mid {file.uri} post"),
        )
        val e = entry("gba", uri = "u", path = "p")
        val plan = LaunchPlanBuilder.build(template, e)!!
        assertEquals("pre p mid u post", plan.extras.getValue("X"))
        assertNull(plan.action)
    }

    @Test
    fun `every registry template builds for an entry with uri and path`() {
        Platforms.ALL.forEach { p ->
            val plan = LaunchPlanBuilder.build(p.player, entry(p.id))
            assertTrue("platform ${p.id}", plan != null)
            assertFalse(plan!!.packageName.isEmpty())
            assertFalse(plan.className.isEmpty())
            plan.extras.values.forEach { v ->
                assertFalse("unsubstituted placeholder in ${p.id}: $v", v.contains("{file."))
            }
        }
    }

    @Test
    fun `every plan carries NEW_TASK`() {
        Platforms.ALL.forEach { p ->
            val plan = LaunchPlanBuilder.build(p.player, entry(p.id))!!
            assertTrue(
                "platform ${p.id} missing NEW_TASK",
                plan.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
            )
        }
    }

    @Test
    fun `registry clear-task and clear-top flags are applied per platform`() {
        val clearFlags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        // Azahar, NetherSX2, PPSSPP, Cemu carry
        // --activity-clear-task --activity-clear-top in the registry.
        val clearing = setOf(Platforms.N3DS, Platforms.PS2, Platforms.PSP, Platforms.WIIU)
        // RetroArch (all cores), Eden, melonDualDS, Dolphin, Flycast launch
        // with NEW_TASK only — RetroArch deliberately deviates from the
        // registry: clear-task hangs its warm relaunch on this device.
        // Note: PSP primary is PPSSPP (clearing); PS1/NES/Saturn/Arcade are
        // RetroArch-primary (plain). Dreamcast primary is standalone Flycast.
        val plain = setOf(
            Platforms.GB, Platforms.GBC, Platforms.GBA, Platforms.NES, Platforms.SNES,
            Platforms.GENESIS, Platforms.N64, Platforms.NDS, Platforms.SWITCH,
            Platforms.PS1, Platforms.SATURN, Platforms.DREAMCAST, Platforms.ARCADE,
            Platforms.GAMECUBE, Platforms.WII,
        )
        assertEquals(clearing.size + plain.size, Platforms.ALL.size)
        clearing.forEach { p ->
            val plan = LaunchPlanBuilder.build(p.player, entry(p.id))!!
            assertEquals(
                "platform ${p.id}",
                Intent.FLAG_ACTIVITY_NEW_TASK or clearFlags,
                plan.flags,
            )
        }
        plain.forEach { p ->
            val plan = LaunchPlanBuilder.build(p.player, entry(p.id))!!
            assertEquals("platform ${p.id}", Intent.FLAG_ACTIVITY_NEW_TASK, plan.flags)
        }
    }

    @Test
    fun `explicit template flags flow into the plan`() {
        val template = PlayerTemplate(
            id = "t",
            displayName = "T",
            component = "a.b/a.b.C",
            action = null,
            uriStyle = UriStyle.PATH,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        val plan = LaunchPlanBuilder.build(template, entry("gba", uri = "u", path = "p"))!!
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            plan.flags,
        )
    }
}
