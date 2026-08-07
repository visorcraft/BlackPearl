package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    @Test
    fun `key isFolder and folderId round-trip`() {
        assertEquals("folder:f1", Folders.key("f1"))
        assertTrue(Folders.isFolder("folder:f1"))
        assertFalse(Folders.isFolder("com.example.app"))
        assertFalse(Folders.isFolder(null))
        assertFalse(Folders.isFolder("folder:"))
        assertEquals("f1", Folders.folderId("folder:f1"))
        assertNull(Folders.folderId("com.example.app"))
        assertNull(Folders.folderId("folder:"))
        assertNull(Folders.folderId(null))
    }

    @Test
    fun `create rename delete`() {
        val empty = emptyMap<String, FolderSpec>()
        val created = Folders.create(empty, "f1", " Favorites ", listOf("a.b", "rom:snes:x.sfc", "a.b", "  "))
        assertEquals(
            mapOf(
                "f1" to FolderSpec(
                    id = "f1",
                    name = "Favorites",
                    members = listOf("a.b", "rom:snes:x.sfc"),
                ),
            ),
            created,
        )

        val renamed = Folders.rename(created, "f1", " Best ")
        assertEquals("Best", renamed["f1"]!!.name)

        val deleted = Folders.delete(renamed, "f1")
        assertEquals(emptyMap<String, FolderSpec>(), deleted)
    }

    @Test
    fun `create ignores blank id and rename no-ops on missing`() {
        assertEquals(emptyMap<String, FolderSpec>(), Folders.create(emptyMap(), "  ", "x"))
        val base = Folders.create(emptyMap(), "f1", "A")
        assertEquals(base, Folders.rename(base, "missing", "B"))
        assertEquals(base, Folders.rename(base, "f1", "  "))
    }

    @Test
    fun `addMember removeMember setMembers`() {
        val base = Folders.create(emptyMap(), "f1", "A")
        val with = Folders.addMember(base, "f1", "a.b")
        assertEquals(listOf("a.b"), Folders.members(with, "f1"))
        // duplicate / blank no-op
        assertEquals(with, Folders.addMember(with, "f1", "a.b"))
        assertEquals(with, Folders.addMember(with, "f1", "  "))

        val two = Folders.addMember(with, "f1", "rom:snes:x.sfc")
        assertEquals(listOf("a.b", "rom:snes:x.sfc"), Folders.members(two, "f1"))

        val removed = Folders.removeMember(two, "f1", "a.b")
        assertEquals(listOf("rom:snes:x.sfc"), Folders.members(removed, "f1"))

        val set = Folders.setMembers(removed, "f1", listOf("x.y", "x.y", "  ", "z.w"))
        assertEquals(listOf("x.y", "z.w"), Folders.members(set, "f1"))
    }

    @Test
    fun `nextId allocates unique fN`() {
        assertEquals("f1", Folders.nextId(emptyMap()))
        val one = Folders.create(emptyMap(), "f1", "A")
        assertEquals("f2", Folders.nextId(one))
        // skip collision when map size implies f1 but f1 is free under another id
        val skewed = mapOf("f2" to FolderSpec("f2", "B"))
        assertEquals("f1", Folders.nextId(skewed))
        val both = Folders.create(one, "f2", "B")
        assertEquals("f3", Folders.nextId(both))
    }
}
