package com.visorcraft.blackpearl.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResolverTest {

    private val dual = Platforms.NDS // melondualds, melonds, drastic

    @Test
    fun `resolve prefers installed preferred id`() {
        val installed = setOf("me.magnum.melonds", "com.dsemu.drastic")
        val t = PlayerResolver.resolve(dual, "drastic") { it in installed }!!
        assertEquals("drastic", t.id)
    }

    @Test
    fun `resolve falls back to first installed when preferred missing`() {
        val installed = setOf("me.magnum.melonds")
        val t = PlayerResolver.resolve(dual, "drastic") { it in installed }!!
        assertEquals("melonds", t.id)
    }

    @Test
    fun `resolve returns null when none installed`() {
        assertNull(PlayerResolver.resolve(dual, null) { false })
    }

    @Test
    fun `byId finds alternate players`() {
        assertEquals("melondualds", dual.player.id)
        assertEquals("drastic", PlayerResolver.byId(dual, "drastic")!!.id)
        assertNull(PlayerResolver.byId(dual, "nope"))
    }

    @Test
    fun `LaunchPlanBuilder builds alternate player intents`() {
        val drastic = PlayerResolver.byId(dual, "drastic")!!
        val entry = RomEntry(
            id = "nds:x.nds", name = "x", platformId = "nds",
            uri = "content://u", path = "/p/x.nds",
        )
        val plan = LaunchPlanBuilder.build(drastic, entry)!!
        assertEquals("com.dsemu.drastic", plan.packageName)
        assertEquals(entry.uri, plan.dataString)
    }

    @Test
    fun `every platform has at least one player with non-blank id`() {
        Platforms.ALL.forEach { p ->
            assertTrue(p.players.isNotEmpty())
            p.players.forEach { assertTrue(it.id.isNotBlank()) }
        }
    }

    @Test
    fun `SNES exposes multiple retroarch cores`() {
        assertTrue(Platforms.SNES.players.size >= 2)
        assertEquals("ra-snes9x", Platforms.SNES.player.id)
    }
}
