package com.visorcraft.blackpearl.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoragePathsTest {

    @Test
    fun `documentId decodes tree uris`() {
        assertEquals(
            "7F7E-2949:roms",
            StoragePaths.documentId(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms"),
        )
        assertEquals(
            "primary:Emulation/ROMs",
            StoragePaths.documentId(
                "content://com.android.externalstorage.documents/tree/primary%3AEmulation%2FROMs"),
        )
    }

    @Test
    fun `documentId decodes document uris`() {
        assertEquals(
            "7F7E-2949:roms/snes/x.smc",
            StoragePaths.documentId(
                "content://com.android.externalstorage.documents/document/7F7E-2949%3Aroms%2Fsnes%2Fx.smc"),
        )
    }

    @Test
    fun `documentId is null for foreign providers and empty tails`() {
        assertNull(StoragePaths.documentId("content://media/external/images/media/42"))
        assertNull(StoragePaths.documentId("content://com.android.externalstorage.documents/tree/"))
    }

    @Test
    fun `treeRootName returns the root folder name`() {
        assertEquals("roms", StoragePaths.treeRootName(
            "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms"))
        assertEquals("ROMs", StoragePaths.treeRootName(
            "content://com.android.externalstorage.documents/tree/primary%3AEmulation%2FROMs"))
        assertEquals("snes", StoragePaths.treeRootName(
            "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms%2Fsnes"))
        assertNull(StoragePaths.treeRootName("content://media/external/42"))
    }

    @Test
    fun `filesystemPath maps the primary volume to emulated storage`() {
        assertEquals(
            "/storage/emulated/0/Emulation/ROMs/GBA/x.gba",
            StoragePaths.filesystemPath(
                "content://com.android.externalstorage.documents/document/primary%3AEmulation%2FROMs%2FGBA%2Fx.gba"),
        )
    }

    @Test
    fun `filesystemPath maps removable volumes to their mount point`() {
        assertEquals(
            "/storage/7F7E-2949/roms/snes/x.smc",
            StoragePaths.filesystemPath(
                "content://com.android.externalstorage.documents/document/7F7E-2949%3Aroms%2Fsnes%2Fx.smc"),
        )
    }

    @Test
    fun `filesystemPath decodes spaces and special characters`() {
        assertEquals(
            "/storage/7F7E-2949/roms/nds/007 - Blood Stone (USA).nds",
            StoragePaths.filesystemPath(
                "content://com.android.externalstorage.documents/document/7F7E-2949%3Aroms%2Fnds%2F007%20-%20Blood%20Stone%20(USA).nds"),
        )
    }

    @Test
    fun `filesystemPath is null for unknown uri shapes`() {
        assertNull(StoragePaths.filesystemPath("content://media/external/file/1"))
        assertNull(StoragePaths.filesystemPath("file:///storage/7F7E-2949/roms/snes/x.smc"))
    }
}
