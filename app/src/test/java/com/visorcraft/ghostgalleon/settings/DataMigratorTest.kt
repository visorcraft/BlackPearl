package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataMigratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `export copies settings library and art then writes ready marker`() {
        val files = tmp.newFolder("files")
        File(files, "settings.json").writeText("""{"schemaVersion":6}""")
        File(files, "rom_library.json").writeText("[]")
        val art = File(files, "art").apply { mkdirs() }
        File(art, "abc.png").writeBytes(byteArrayOf(1, 2, 3))
        File(art, "nested").mkdirs()
        File(art, "nested/x.hero.png").writeBytes(byteArrayOf(9))

        val out = tmp.newFolder("export")
        val n = DataMigrator.exportFrom(files, out)

        assertEquals(3, n)
        assertTrue(File(out, DataMigrator.READY_MARKER).isFile)
        assertEquals("""{"schemaVersion":6}""", File(out, "settings.json").readText())
        assertEquals("[]", File(out, "rom_library.json").readText())
        assertTrue(File(out, "art/abc.png").isFile)
        assertTrue(File(out, "art/nested/x.hero.png").isFile)
    }

    @Test
    fun `import requires ready marker and is idempotent via done marker`() {
        val src = tmp.newFolder("import")
        File(src, "settings.json").writeText("""{"theme":"dark"}""")
        File(src, "rom_library.json").writeText("""[{"id":"a"}]""")
        File(src, "art").mkdirs()
        File(src, "art/k.png").writeBytes(byteArrayOf(7))
        File(src, DataMigrator.READY_MARKER).writeText("ok\n")

        val dest = tmp.newFolder("files2")
        assertEquals(3, DataMigrator.importInto(src, dest))
        assertEquals("""{"theme":"dark"}""", File(dest, "settings.json").readText())
        assertEquals("""[{"id":"a"}]""", File(dest, "rom_library.json").readText())
        assertTrue(File(dest, "art/k.png").isFile)
        assertTrue(File(src, DataMigrator.DONE_MARKER).isFile)
        assertFalse(File(src, DataMigrator.READY_MARKER).exists())

        // Second pass is a no-op (done marker present).
        assertEquals(0, DataMigrator.importInto(src, dest))
    }

    @Test
    fun `import skips when ready marker missing`() {
        val src = tmp.newFolder("empty-import")
        File(src, "settings.json").writeText("{}")
        val dest = tmp.newFolder("files3")
        assertEquals(0, DataMigrator.importInto(src, dest))
        assertFalse(File(dest, "settings.json").exists())
    }

    @Test
    fun `export with only settings still writes ready marker`() {
        val files = tmp.newFolder("sparse")
        File(files, "settings.json").writeText("{}")
        val out = File(tmp.root, "out-sparse").apply { mkdirs() }
        assertEquals(1, DataMigrator.exportFrom(files, out))
        assertTrue(File(out, DataMigrator.READY_MARKER).isFile)
        assertFalse(File(out, "rom_library.json").exists())
    }
}
