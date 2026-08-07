package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeFingerprintTest {

    @Test
    fun `of is order-independent`() {
        val a = listOf(
            DocFile("b.smc", "u1", "snes/b.smc"),
            DocFile("a.smc", "u0", "snes/a.smc"),
        )
        val b = listOf(
            DocFile("a.smc", "u0", "snes/a.smc"),
            DocFile("b.smc", "u1", "snes/b.smc"),
        )
        assertEquals(TreeFingerprint.of(a), TreeFingerprint.of(b))
    }

    @Test
    fun `of changes when files change`() {
        val a = listOf(DocFile("a.smc", "u0", "snes/a.smc"))
        val b = listOf(DocFile("a.smc", "u0", "snes/a.smc"), DocFile("b.smc", "u1", "snes/b.smc"))
        assertNotEquals(TreeFingerprint.of(a), TreeFingerprint.of(b))
    }

    @Test
    fun `isDirty when force or missing prior or mismatch`() {
        val uri = "content://tree/1"
        assertTrue(TreeFingerprint.isDirty(uri, "h1", emptyMap(), force = false))
        assertTrue(TreeFingerprint.isDirty(uri, "h1", mapOf(uri to "h0"), force = false))
        assertFalse(TreeFingerprint.isDirty(uri, "h1", mapOf(uri to "h1"), force = false))
        assertTrue(TreeFingerprint.isDirty(uri, "h1", mapOf(uri to "h1"), force = true))
    }

    @Test
    fun `ofMeta dirties on count or name change`() {
        val a = TreeFingerprint.ofMeta(2, listOf("a.smc", "b.smc"))
        val b = TreeFingerprint.ofMeta(2, listOf("b.smc", "a.smc"))
        val c = TreeFingerprint.ofMeta(3, listOf("a.smc", "b.smc", "c.smc"))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `ofCombined embeds meta and path`() {
        val files = listOf(
            DocFile("a.smc", "u0", "snes/a.smc"),
            DocFile("b.smc", "u1", "snes/b.smc"),
        )
        val combined = TreeFingerprint.ofCombined(files)
        assertTrue(combined.startsWith("c"))
        assertTrue(combined.contains("|"))
    }

    @Test
    fun `isDirtyFromMetaProbe pure meta match is clean`() {
        val uri = "t"
        val meta = TreeFingerprint.ofMeta(1, listOf("x.smc"))
        assertFalse(
            TreeFingerprint.isDirtyFromMetaProbe(uri, meta, mapOf(uri to meta), force = false),
        )
        assertTrue(
            TreeFingerprint.isDirtyFromMetaProbe(uri, meta, mapOf(uri to "m0:empty"), force = false),
        )
    }
}
