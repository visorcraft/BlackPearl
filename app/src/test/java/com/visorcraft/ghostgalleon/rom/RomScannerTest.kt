package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomScannerTest {

    private class FakeTree(private val files: List<DocFile>) : DocumentTree {
        override fun walk(): List<DocFile> = files
    }

    private fun docUri(documentId: String): String {
        val encoded = documentId
            .replace("%", "%25")
            .replace(":", "%3A")
            .replace("/", "%2F")
            .replace(" ", "%20")
        return "content://com.android.externalstorage.documents/document/$encoded"
    }

    private fun doc(documentId: String): DocFile {
        val rel = documentId.substringAfter(':').substringAfter("roms/")
        return DocFile(rel.substringAfterLast('/'), docUri(documentId), rel)
    }

    /** Card-style tree granted at `/roms`: platform folders under the root. */
    private fun cardTree(files: List<DocFile>) = FakeTree(files) to "roms"

    @Test
    fun `files under platform folders map to the right platform`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/gb/Tetris.gb"),
            doc("7F7E-2949:roms/gbc/Oracle of Ages.gbc"),
            doc("7F7E-2949:roms/gba/Fire Emblem.gba"),
            doc("7F7E-2949:roms/snes/2020 Super Baseball (U).smc"),
            doc("7F7E-2949:roms/genesis-slash-megadrive/Sonic.md"),
            doc("7F7E-2949:roms/nds/007 - Blood Stone (USA).nds"),
            doc("7F7E-2949:roms/3ds/Bravely Default.3ds"),
            doc("7F7E-2949:roms/new-nintendo-3ds/Xenoblade.3ds"),
            doc("7F7E-2949:roms/switch/Mario Kart 8 Deluxe.nsp"),
        ))))
        assertEquals(
            listOf("gb", "gbc", "gba", "snes", "genesis", "nds", "3ds", "3ds", "switch").sorted(),
            entries.map { it.platformId }.sorted(),
        )
    }

    @Test
    fun `extension matching is case-insensitive and names drop the extension`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/Chrono Trigger.SMC"),
        ))))
        assertEquals(1, entries.size)
        assertEquals("Chrono Trigger", entries[0].name)
        assertEquals("snes:snes/Chrono Trigger.SMC", entries[0].id)
    }

    @Test
    fun `junk files and dot-directories are ignored`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/.DS_Store"),
            doc("7F7E-2949:roms/snes/._Chrono Trigger.smc"),
            doc("7F7E-2949:roms/snes/.hidden/secret.smc"),
            doc("7F7E-2949:roms/snes/real.smc"),
        ))))
        assertEquals(listOf("real"), entries.map { it.name })
    }

    @Test
    fun `wrong extensions and non-rom files are ignored`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/save.srm"),       // save file, not a ROM
            doc("7F7E-2949:roms/snes/readme.txt"),
            doc("7F7E-2949:roms/snes/noextension"),
            doc("7F7E-2949:roms/snes/trailingdot."),
            doc("7F7E-2949:roms/notes/readme.gb"),     // not a platform folder
            doc("7F7E-2949:roms/loose.smc"),           // no platform folder
        ))))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `nested subfolders under a platform folder still match`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/hacks/smw.smc"),
        ))))
        assertEquals(1, entries.size)
        assertEquals("snes:snes/hacks/smw.smc", entries[0].id)
    }

    @Test
    fun `tree granted at a platform folder treats the whole tree as that platform`() {
        val tree = FakeTree(listOf(
            DocFile("smw.smc", docUri("7F7E-2949:roms/snes/smw.smc"), "smw.smc"),
            DocFile("ct.sfc", docUri("7F7E-2949:roms/snes/sub/ct.sfc"), "sub/ct.sfc"),
        ))
        val entries = RomScanner.scan(listOf(tree to "snes"))
        assertEquals(listOf("snes", "snes"), entries.map { it.platformId })
        assertEquals(listOf("snes:smw.smc", "snes:sub/ct.sfc"), entries.map { it.id }.sorted())
    }

    @Test
    fun `platform-rooted trees still filter by extension`() {
        val tree = FakeTree(listOf(
            DocFile("smw.smc", docUri("7F7E-2949:roms/snes/smw.smc"), "smw.smc"),
            DocFile("notes.txt", docUri("7F7E-2949:roms/snes/notes.txt"), "notes.txt"),
        ))
        val entries = RomScanner.scan(listOf(tree to "snes"))
        assertEquals(listOf("smw"), entries.map { it.name })
    }

    @Test
    fun `paths are reconstructed for known volumes and null otherwise`() {
        val card = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
        ))))
        assertEquals("/storage/7F7E-2949/roms/snes/smw.smc", card[0].path)

        val internal = RomScanner.scan(listOf(cardTree(listOf(
            doc("primary:roms/snes/smw.smc"),
        ))))
        assertEquals("/storage/emulated/0/roms/snes/smw.smc", internal[0].path)

        val foreign = FakeTree(listOf(
            DocFile("smw.smc", "content://media/external/file/1", "snes/smw.smc"),
        ))
        val unknown = RomScanner.scan(listOf(foreign to "roms"))
        assertNull(unknown[0].path)
    }

    @Test
    fun `multiple trees combine and output is sorted by platform then name`() {
        val card = cardTree(listOf(
            doc("7F7E-2949:roms/snes/zelda.smc"),
            doc("7F7E-2949:roms/gba/emerald.gba"),
        ))
        val internal = FakeTree(listOf(
            DocFile("metroid.smc", docUri("primary:Emulation/ROMs/SNES/metroid.smc"),
                "SNES/metroid.smc"),
        )) to "ROMs" // root name is not a platform folder; first segment wins
        val entries = RomScanner.scan(listOf(card, internal))
        assertEquals(
            listOf("gba:gba/emerald.gba", "snes:SNES/metroid.smc", "snes:snes/zelda.smc"),
            entries.map { it.id },
        )
    }

    @Test
    fun `internal uppercase folders match the same platforms`() {
        val tree = FakeTree(listOf(
            DocFile("x.gba", docUri("primary:Emulation/ROMs/GBA/x.gba"), "GBA/x.gba"),
            DocFile("y.wua", docUri("primary:Emulation/ROMs/WiiU/y.wua"), "WiiU/y.wua"),
            DocFile("z.iso", docUri("primary:Emulation/ROMs/GameCube/z.iso"), "GameCube/z.iso"),
        ))
        val entries = RomScanner.scan(listOf(tree to "ROMs"))
        assertEquals(listOf("gamecube", "gba", "wiiu"), entries.map { it.platformId })
    }

    @Test
    fun `genesis folder owns bin even though ps2 also lists it`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/genesis-slash-megadrive/Sonic.bin"),
        ))))
        assertEquals("genesis", entries[0].platformId)
    }

    // ---- Local artwork discovery (images//media//art/ sibling folders) ----

    @Test
    fun `images folder art matches by exact stem including region tags`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/Super Mario Kart (USA).smc"),
            doc("7F7E-2949:roms/snes/images/Super Mario Kart (USA).png"),
        ))))
        assertEquals(1, entries.size)
        assertEquals(
            docUri("7F7E-2949:roms/snes/images/Super Mario Kart (USA).png"),
            entries[0].artUri,
        )
    }

    @Test
    fun `art matching is case-insensitive`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/Chrono Trigger.smc"),
            doc("7F7E-2949:roms/snes/images/chrono trigger.PNG"),
        ))))
        assertEquals(
            docUri("7F7E-2949:roms/snes/images/chrono trigger.PNG"),
            entries[0].artUri,
        )
    }

    @Test
    fun `media and art folders also match`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
            doc("7F7E-2949:roms/snes/media/smw.jpg"),
            doc("7F7E-2949:roms/gba/emerald.gba"),
            doc("7F7E-2949:roms/gba/art/emerald.jpeg"),
        ))))
        assertEquals(
            docUri("7F7E-2949:roms/snes/media/smw.jpg"),
            entries.first { it.platformId == "snes" }.artUri,
        )
        assertEquals(
            docUri("7F7E-2949:roms/gba/art/emerald.jpeg"),
            entries.first { it.platformId == "gba" }.artUri,
        )
    }

    @Test
    fun `image and thumb suffixes match`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
            doc("7F7E-2949:roms/snes/images/smw-image.png"),
            doc("7F7E-2949:roms/snes/ct.smc"),
            doc("7F7E-2949:roms/snes/images/ct_thumb.jpg"),
        ))))
        assertEquals(
            docUri("7F7E-2949:roms/snes/images/smw-image.png"),
            entries.first { it.name == "smw" }.artUri,
        )
        assertEquals(
            docUri("7F7E-2949:roms/snes/images/ct_thumb.jpg"),
            entries.first { it.name == "ct" }.artUri,
        )
    }

    @Test
    fun `no artwork leaves artUri null`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
            doc("7F7E-2949:roms/snes/images/other.png"),
        ))))
        assertNull(entries[0].artUri)
    }

    @Test
    fun `art in another platform folder does not match`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
            doc("7F7E-2949:roms/gba/images/smw.png"),
        ))))
        assertNull(entries[0].artUri)
    }

    @Test
    fun `exact stem wins over suffixed and images wins over media`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/smw.smc"),
            doc("7F7E-2949:roms/snes/images/smw-image.png"),
            doc("7F7E-2949:roms/snes/media/smw.png"),
            doc("7F7E-2949:roms/snes/images/smw.png"),
        ))))
        assertEquals(
            docUri("7F7E-2949:roms/snes/images/smw.png"),
            entries[0].artUri,
        )
    }

    @Test
    fun `platform-rooted tree finds art in its images subfolder`() {
        val tree = FakeTree(listOf(
            DocFile("smw.smc", docUri("7F7E-2949:roms/snes/smw.smc"), "smw.smc"),
            DocFile("smw.png", docUri("7F7E-2949:roms/snes/images/smw.png"),
                "images/smw.png"),
        ))
        val entries = RomScanner.scan(listOf(tree to "snes"))
        assertEquals(docUri("7F7E-2949:roms/snes/images/smw.png"), entries[0].artUri)
    }

    @Test
    fun `nested roms match art by stem from the platform folder images dir`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/hacks/smw.smc"),
            doc("7F7E-2949:roms/snes/images/smw.png"),
        ))))
        assertEquals(docUri("7F7E-2949:roms/snes/images/smw.png"), entries[0].artUri)
    }

    @Test
    fun `scan enriches names and descriptions from gamelist xml via readText`() {
        val gamelistUri = docUri("7F7E-2949:roms/snes/gamelist.xml")
        val smc = doc("7F7E-2949:roms/snes/Chrono Trigger.smc")
        val xml = """
            <gameList>
              <game>
                <path>./Chrono Trigger.smc</path>
                <name>Chrono Trigger</name>
                <desc>Time-travel RPG classic.</desc>
              </game>
            </gameList>
        """.trimIndent()
        val entries = RomScanner.scan(
            listOf(cardTree(listOf(
                smc,
                DocFile("gamelist.xml", gamelistUri, "snes/gamelist.xml"),
            ))),
            readText = { uri -> if (uri == gamelistUri) xml else null },
        )
        assertEquals(1, entries.size)
        assertEquals("Chrono Trigger", entries[0].name)
        assertEquals("Time-travel RPG classic.", entries[0].description)
        // gamelist.xml itself is not a ROM entry
        assertTrue(entries.none { it.name.equals("gamelist", ignoreCase = true) })
    }

    @Test
    fun `scan without readText leaves filename titles when gamelist present`() {
        val entries = RomScanner.scan(listOf(cardTree(listOf(
            doc("7F7E-2949:roms/snes/Chrono Trigger.smc"),
            DocFile(
                "gamelist.xml",
                docUri("7F7E-2949:roms/snes/gamelist.xml"),
                "snes/gamelist.xml",
            ),
        ))))
        assertEquals(1, entries.size)
        assertEquals("Chrono Trigger", entries[0].name)
        assertNull(entries[0].description)
    }
}
