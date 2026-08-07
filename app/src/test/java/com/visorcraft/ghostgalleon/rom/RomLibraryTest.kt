package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RomLibraryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        id: String = "snes:snes/smw.smc",
        name: String = "smw",
        platformId: String = "snes",
        uri: String = "content://doc/1",
        path: String? = "/storage/7F7E-2949/roms/snes/smw.smc",
        artUri: String? = null,
    ) = RomEntry(id, name, platformId, uri, path, artUri)

    @Test
    fun `missing file loads as empty`() {
        assertEquals(emptyList<RomEntry>(),
            RomLibrary(tmp.root.resolve("nope/rom_library.json")).load())
    }

    @Test
    fun `corrupt file loads as empty`() {
        val f = tmp.newFile("rom_library.json")
        f.writeText("{ definitely not a json array")
        assertEquals(emptyList<RomEntry>(), RomLibrary(f).load())
    }

    @Test
    fun `save then load round-trips entries`() {
        val f = tmp.root.resolve("lib/rom_library.json")
        val entries = listOf(
            entry(),
            entry(id = "3ds:3ds/bravely.3ds", name = "bravely", platformId = "3ds",
                uri = "content://doc/2", path = null),
            entry(id = "switch:switch/mk8.nsp", name = "mk8", platformId = "switch",
                uri = "content://doc/3", path = "/storage/7F7E-2949/roms/switch/mk8.nsp"),
        )
        RomLibrary(f).save(entries)
        assertEquals(entries, RomLibrary(f).load())
    }

    @Test
    fun `description and screenshotUri round-trip`() {
        val f = tmp.root.resolve("lib-meta/rom_library.json")
        val e = entry().copy(
            description = "A classic platformer.",
            screenshotUri = "content://doc/shot.png",
            artUri = "content://doc/art.png",
        )
        RomLibrary(f).save(listOf(e))
        val loaded = RomLibrary(f).load().single()
        assertEquals("A classic platformer.", loaded.description)
        assertEquals("content://doc/shot.png", loaded.screenshotUri)
        assertEquals("content://doc/art.png", loaded.artUri)
    }

    @Test
    fun `null path round-trips as null`() {
        val f = tmp.root.resolve("lib2/rom_library.json")
        RomLibrary(f).save(listOf(entry(path = null)))
        assertEquals(null, RomLibrary(f).load()[0].path)
    }

    @Test
    fun `artUri round-trips`() {
        val f = tmp.root.resolve("lib-art/rom_library.json")
        RomLibrary(f).save(listOf(
            entry(artUri = "content://doc/art/smw.png"),
            entry(id = "snes:snes/ct.smc", name = "ct", uri = "content://doc/2"),
        ))
        val loaded = RomLibrary(f).load()
        assertEquals("content://doc/art/smw.png", loaded[0].artUri)
        assertEquals(null, loaded[1].artUri)
    }

    @Test
    fun `old library json without artUri still loads`() {
        val f = tmp.newFile("old_rom_library.json")
        f.writeText(
            """[{"id":"snes:snes/smw.smc","name":"smw","platformId":"snes",""" +
                """"uri":"content://doc/1","path":null}]""",
        )
        val loaded = RomLibrary(f).load()
        assertEquals(1, loaded.size)
        assertEquals(null, loaded[0].artUri)
    }

    @Test
    fun `save is atomic and creates parent directories`() {
        val f = tmp.root.resolve("deep/nested/rom_library.json")
        RomLibrary(f).save(listOf(entry()))
        assertTrue(f.exists())
        assertFalse(f.parentFile.resolve("rom_library.json.tmp").exists())
    }

    @Test
    fun `saving an empty library round-trips`() {
        val f = tmp.root.resolve("lib3/rom_library.json")
        RomLibrary(f).save(emptyList())
        assertEquals(emptyList<RomEntry>(), RomLibrary(f).load())
    }

    // ---- Rescan guard: unreadable trees must never wipe the library ----

    private class FakeTree(private val files: List<DocFile>) : DocumentTree {
        override fun walk(): List<DocFile> = files
    }

    private fun treeUri(docId: String) =
        "content://com.android.externalstorage.documents/tree/" + docId.replace(":", "%3A")

    // SAF child document URIs embed the tree URI, so stored entries can be
    // attributed to their tree by prefix.
    private fun childUri(treeDocId: String, childDocId: String) =
        treeUri(treeDocId) + "/document/" +
            childDocId.replace(":", "%3A").replace("/", "%2F")

    private val cardTree = treeUri("7F7E-2949:roms")
    private val internalTree = treeUri("primary:Emulation/ROMs")

    @Test
    fun `all trees unreadable aborts without scanning`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { false },
            treeFor = { error("unreadable trees must never be scanned") },
        )
        assertEquals(RomLibrary.RescanResult.Unreadable, result)
    }

    @Test
    fun `unreadable tree retains its prior entries while readable trees rescan`() {
        val retained = entry(
            id = "gba:gba/fe.gba", name = "fe", platformId = "gba",
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/gba/fe.gba"),
        )
        val stale = entry( // internal-tree entry the rescan no longer finds
            id = "snes:snes/old.smc", name = "old",
            uri = childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/old.smc"),
        )
        val freshTree = FakeTree(listOf(
            DocFile("smw.smc",
                childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/smw.smc"),
                "snes/smw.smc"),
        ))
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree, internalTree),
            prior = listOf(retained, stale),
            isReadable = { it == internalTree },
            treeFor = { uri ->
                assertEquals(internalTree, uri)
                freshTree to "ROMs"
            },
        )
        val success = result as RomLibrary.RescanResult.Success
        // gba (retained from the unreadable card) sorts before snes (fresh).
        assertEquals(listOf("gba:gba/fe.gba", "snes:snes/smw.smc"),
            success.entries.map { it.id })
        // The stale internal entry is gone: its tree was readable and rescan
        // no longer reports it.
        assertFalse(success.entries.any { it.id == "snes:snes/old.smc" })
    }

    @Test
    fun `all trees readable drops prior entries entirely`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { true },
            treeFor = { FakeTree(emptyList<DocFile>()) to "roms" },
        )
        assertEquals(RomLibrary.RescanResult.Success(emptyList()), result)
    }

    @Test
    fun `no granted trees succeeds with an empty library`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = emptyList(),
            prior = listOf(entry()),
            isReadable = { error("no trees to check") },
            treeFor = { error("no trees to scan") },
        )
        assertEquals(RomLibrary.RescanResult.Success(emptyList()), result)
    }

    @Test
    fun `retained entries that collide with fresh ids are not duplicated`() {
        // Entry ids are platform:relativePath — tree-independent — so a file
        // visible from two grants collides. The fresh scan wins, once.
        val old = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree, internalTree),
            prior = listOf(old),
            isReadable = { it == internalTree },
            treeFor = {
                FakeTree(listOf(DocFile("smw.smc",
                    childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/smw.smc"),
                    "snes/smw.smc"))) to "ROMs"
            },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertTrue(success.entries[0].uri.startsWith(internalTree))
    }

    // ---- Incremental fingerprint skip ----

    @Test
    fun `clean tree is skipped and prior entries retained when not forced`() {
        val priorEntry = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val fp = TreeFingerprint.ofCombined(files)
        var treeForCalls = 0
        val (result, newFp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = {
                treeForCalls++
                FakeTree(files) to "roms"
            },
            priorFingerprints = mapOf(cardTree to fp),
            force = false,
            fingerprintOf = { TreeFingerprint.ofCombined(it) },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(1, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertEquals(fp, newFp[cardTree])
        // treeFor still called once to read the listing for fingerprint.
        assertEquals(1, treeForCalls)
    }

    @Test
    fun `dirty tree is rescanned even when prior fingerprint exists`() {
        val priorEntry = entry(
            id = "snes:snes/old.smc",
            name = "old",
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/old.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = { FakeTree(files) to "roms" },
            priorFingerprints = mapOf(cardTree to "stale-fingerprint"),
            force = false,
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(0, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertFalse(success.entries.any { it.id == "snes:snes/old.smc" })
    }

    @Test
    fun `force rescan rescans clean trees`() {
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val fp = TreeFingerprint.ofCombined(files)
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { true },
            treeFor = { FakeTree(files) to "roms" },
            priorFingerprints = mapOf(cardTree to fp),
            force = true,
            fingerprintOf = { TreeFingerprint.ofCombined(it) },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(0, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
    }

    @Test
    fun `all unreadable still Unreadable with fingerprints preserved`() {
        val priorFp = mapOf(cardTree to "abc")
        val (result, fp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { false },
            treeFor = { error("must not scan") },
            priorFingerprints = priorFp,
            force = false,
        )
        assertEquals(RomLibrary.RescanResult.Unreadable, result)
        assertEquals(priorFp, fp)
    }

    @Test
    fun `quickMeta pure match skips treeFor walk`() {
        val priorEntry = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val meta = TreeFingerprint.ofFilesMeta(files)
        var treeForCalls = 0
        val (result, newFp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = {
                treeForCalls++
                error("clean meta must not walk")
            },
            priorFingerprints = mapOf(cardTree to meta),
            force = false,
            quickMeta = { meta },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(1, success.skippedCleanTrees)
        assertEquals(0, treeForCalls)
        assertEquals(meta, newFp[cardTree])
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
    }
}
