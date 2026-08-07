package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.state.UIMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `missing file returns defaults`() {
        val store = SettingsStore(tmp.root.resolve("nope/settings.json"))
        assertEquals(Settings.DEFAULT, store.load())
    }

    @Test
    fun `corrupt file returns defaults`() {
        val f = tmp.newFile("settings.json")
        f.writeText("{ this is not json !!!")
        assertEquals(Settings.DEFAULT, SettingsStore(f).load())
    }

    @Test
    fun `save then load round-trips all fields`() {
        val f = tmp.root.resolve("cfg/settings.json")
        val store = SettingsStore(f)
        val s = Settings(
            theme = "oled",
            accentColor = 0xFFFF5722.toInt(),
            background = "gradient",
            gridColumns = 7,
            iconSizeDp = 96,
            cardSizeDp = 240,
            animationMs = 120,
            defaultMode = UIMode.GAME,
            primaryDisplay = 1,
            gyroEnabled = false,
            angleLock = true,
            haptics = false,
            showHints = false,
            showLabels = false,
            gridDirection = "horizontal",
            wallpaperUri = "content://media/external/images/media/42",
            romTreeUris = listOf(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms",
                "content://com.android.externalstorage.documents/tree/primary%3AEmulation%2FROMs",
            ),
            sgdbApiKey = "sgdb-test-key-123",
            dockSlots = DockSlots.compact(listOf("a.b", "c.d", "rom:snes:x.sfc")),
            hiddenPackages = setOf("x.y"),
            keyMap = mapOf(23 to Action.CONFIRM, 4 to Action.BACK),
            lastLaunchedMs = mapOf("rom:snes:x.sfc" to 1_700_000_000_000L),
            playtimeMs = mapOf("rom:snes:x.sfc" to 120_000L),
            defaultPlayers = mapOf("nds" to "drastic"),
            artOverrides = mapOf("snes:x.sfc" to "content://art/1"),
            favorites = setOf("rom:snes:x.sfc"),
            collections = mapOf("RPGs" to listOf("rom:snes:x.sfc", "rom:3ds:y")),
            setupDismissed = true,
            companionRole = CompanionRole.PERF_HUD.name,
            companionPinnedPackage = "com.example.pin",
            romProfiles = mapOf("snes:x.sfc" to "snes9x"),
            folders = mapOf(
                "f1" to FolderSpec("f1", "RPGs", listOf("rom:snes:x.sfc")),
            ),
            themePackId = ThemePack.NEON.id,
            themeCustomJson = """{"id":"neon","accentColor":"#FF2D95"}""",
            raApiKey = "ra-key",
            raUsername = "player1",
            deviceProfileId = "onex-sugar",
            interactiveDisplayMode = "secondary",
            orientationMode = "sensor_landscape",
            userPinnedPrimaryId = 1,
        )
        store.save(s)
        val loaded = SettingsStore(f).load()
        assertEquals(s, loaded)
        assertEquals(true, loaded.setupDismissed)
        assertEquals(CompanionRole.PERF_HUD.name, loaded.companionRole)
        assertEquals("com.example.pin", loaded.companionPinnedPackage)
        assertEquals(mapOf("snes:x.sfc" to "snes9x"), loaded.romProfiles)
        assertEquals(FolderSpec("f1", "RPGs", listOf("rom:snes:x.sfc")), loaded.folders["f1"])
        assertEquals(ThemePack.NEON.id, loaded.themePackId)
        assertEquals("""{"id":"neon","accentColor":"#FF2D95"}""", loaded.themeCustomJson)
        assertEquals("ra-key", loaded.raApiKey)
        assertEquals("player1", loaded.raUsername)
        assertEquals("onex-sugar", loaded.deviceProfileId)
        assertEquals("secondary", loaded.interactiveDisplayMode)
        assertEquals("sensor_landscape", loaded.orientationMode)
        assertEquals(1, loaded.userPinnedPrimaryId)
    }

    @Test
    fun `v7 primaryDisplay 1 migrates to interactive secondary`() {
        val f = tmp.root.resolve("cfg-v7-pd/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(primaryDisplay = 1, setupDismissed = true))
        val raw = org.json.JSONObject(f.readText())
        raw.put("schemaVersion", 7)
        raw.put("primaryDisplay", 1)
        raw.remove("interactiveDisplayMode")
        raw.remove("deviceProfileId")
        raw.remove("orientationMode")
        raw.remove("userPinnedPrimaryId")
        f.writeText(raw.toString())
        val loaded = SettingsStore(f).load()
        assertEquals("secondary", loaded.interactiveDisplayMode)
        assertEquals("auto", loaded.deviceProfileId)
        assertEquals(SettingsStore.CURRENT_SCHEMA, loaded.schemaVersion)
    }

    @Test
    fun `v7 primaryDisplay 0 migrates to interactive default`() {
        val f = tmp.root.resolve("cfg-v7-pd0/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(primaryDisplay = 0))
        val raw = org.json.JSONObject(f.readText())
        raw.put("schemaVersion", 7)
        raw.put("primaryDisplay", 0)
        raw.remove("interactiveDisplayMode")
        f.writeText(raw.toString())
        assertEquals("default", SettingsStore(f).load().interactiveDisplayMode)
    }

    @Test
    fun `v6 json without v7 fields loads defaults and stamps schema 7`() {
        val f = tmp.root.resolve("cfg-v6/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(setupDismissed = true))
        val raw = org.json.JSONObject(f.readText())
        raw.put("schemaVersion", 6)
        raw.remove("companionRole")
        raw.remove("companionPinnedPackage")
        raw.remove("romProfiles")
        raw.remove("folders")
        raw.remove("themePackId")
        raw.remove("themeCustomJson")
        raw.remove("raApiKey")
        raw.remove("raUsername")
        f.writeText(raw.toString())
        val loaded = SettingsStore(f).load()
        assertEquals(CompanionRole.HERO.name, loaded.companionRole)
        assertEquals(null, loaded.companionPinnedPackage)
        assertTrue(loaded.romProfiles.isEmpty())
        assertTrue(loaded.folders.isEmpty())
        assertEquals(ThemePack.GHOST.id, loaded.themePackId)
        assertEquals(null, loaded.themeCustomJson)
        assertEquals(null, loaded.raApiKey)
        assertEquals(null, loaded.raUsername)
        assertEquals(SettingsStore.CURRENT_SCHEMA, loaded.schemaVersion)
        assertEquals(
            SettingsStore.CURRENT_SCHEMA,
            org.json.JSONObject(f.readText()).getInt("schemaVersion"),
        )
    }

    @Test
    fun `v1 json migrates primaryDisplay to 1 and stamps schemaVersion`() {
        val f = tmp.root.resolve("cfg/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(primaryDisplay = 0))
        // strip schemaVersion to simulate a v1 file
        val raw = org.json.JSONObject(f.readText())
        raw.remove("schemaVersion")
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(1, migrated.primaryDisplay)
        assertEquals(SettingsStore.CURRENT_SCHEMA,
            org.json.JSONObject(f.readText()).getInt("schemaVersion"))
    }

    @Test
    fun `v2 json keeps stored primaryDisplay`() {
        val f = tmp.root.resolve("cfg2/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(primaryDisplay = 0))
        assertEquals(0, SettingsStore(f).load().primaryDisplay)
    }

    @Test
    fun `v2 json without showHints defaults to true`() {
        val f = tmp.root.resolve("cfg3/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(showHints = false))
        // strip showHints to simulate a file written before the field existed
        val raw = org.json.JSONObject(f.readText())
        raw.remove("showHints")
        f.writeText(raw.toString())
        assertTrue(SettingsStore(f).load().showHints)
    }

    @Test
    fun `v2 json without showLabels defaults to true`() {
        val f = tmp.root.resolve("cfg4/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(showLabels = false))
        // strip showLabels to simulate a file written before the field existed
        val raw = org.json.JSONObject(f.readText())
        raw.remove("showLabels")
        f.writeText(raw.toString())
        assertTrue(SettingsStore(f).load().showLabels)
    }

    @Test
    fun `v2 json without wallpaperUri defaults to null`() {
        val f = tmp.root.resolve("cfg5/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(wallpaperUri = "content://x/1"))
        // strip wallpaperUri to simulate a file written before the field existed
        val raw = org.json.JSONObject(f.readText())
        raw.remove("wallpaperUri")
        f.writeText(raw.toString())
        assertEquals(null, SettingsStore(f).load().wallpaperUri)
    }

    @Test
    fun `null wallpaperUri round-trips as null`() {
        val f = tmp.root.resolve("cfg6/settings.json")
        val store = SettingsStore(f)
        store.save(Settings.DEFAULT)
        assertEquals(null, SettingsStore(f).load().wallpaperUri)
    }

    @Test
    fun `save is atomic and creates parent directories`() {
        val f = tmp.root.resolve("deep/nested/settings.json")
        SettingsStore(f).save(Settings.DEFAULT)
        assertTrue(f.exists())
        assertTrue(!f.parentFile.resolve("settings.json.tmp").exists())
    }

    @Test
    fun `gridSlots with mixed nulls and apps round-trips`() {
        val f = tmp.root.resolve("cfg7/settings.json")
        val store = SettingsStore(f)
        val slots = listOf("a.b", null, null, "c.d", null, "e.f") +
            List<String?>(6) { null }
        val s = Settings.DEFAULT.copy(gridSlots = slots)
        store.save(s)
        assertEquals(slots, SettingsStore(f).load().gridSlots)
    }

    @Test
    fun `gridSlots nulls are preserved in the json array`() {
        val f = tmp.root.resolve("cfg8/settings.json")
        val slots = List<String?>(12) { null }.toMutableList()
            .apply { set(3, "a.b") }
        SettingsStore(f).save(Settings.DEFAULT.copy(gridSlots = slots))
        val arr = org.json.JSONObject(f.readText()).getJSONArray("gridSlots")
        assertEquals(12, arr.length())
        assertTrue(arr.isNull(0))
        assertEquals("a.b", arr.getString(3))
        assertTrue(arr.isNull(11))
    }

    @Test
    fun `v2 json migrates to 12 blank gridSlots and stamps the current schema`() {
        val f = tmp.root.resolve("cfg9/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(primaryDisplay = 0))
        // Downgrade to a v2 file: no gridSlots field, schemaVersion 2.
        val raw = org.json.JSONObject(f.readText())
        raw.remove("gridSlots")
        raw.put("schemaVersion", 2)
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(List<String?>(12) { null }, migrated.gridSlots)
        assertEquals(SettingsStore.CURRENT_SCHEMA, migrated.schemaVersion)
        // The migration is persisted immediately, like the v1 stamp.
        assertEquals(SettingsStore.CURRENT_SCHEMA,
            org.json.JSONObject(f.readText()).getInt("schemaVersion"))
        // Other v2 fields survive the migration.
        assertEquals(0, migrated.primaryDisplay)
    }

    @Test
    fun `v3 json keeps stored gridSlots`() {
        val f = tmp.root.resolve("cfg10/settings.json")
        val slots = listOf("a.b", null, "c.d") + List<String?>(9) { null }
        SettingsStore(f).save(Settings.DEFAULT.copy(gridSlots = slots))
        assertEquals(slots, SettingsStore(f).load().gridSlots)
    }

    @Test
    fun `defaults contain 12 blank gridSlots`() {
        assertEquals(List<String?>(12) { null }, Settings.DEFAULT.gridSlots)
    }

    @Test
    fun `gridDirection defaults to vertical`() {
        assertEquals("vertical", Settings.DEFAULT.gridDirection)
    }

    @Test
    fun `romTreeUris default to empty`() {
        assertEquals(emptyList<String>(), Settings.DEFAULT.romTreeUris)
    }

    @Test
    fun `v3 json without romTreeUris defaults to empty`() {
        val f = tmp.root.resolve("cfg12/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(
            romTreeUris = listOf("content://x/tree/primary%3Aroms"),
        ))
        // Strip the field to simulate a v3 file written before it existed.
        val raw = org.json.JSONObject(f.readText())
        raw.remove("romTreeUris")
        f.writeText(raw.toString())
        assertEquals(emptyList<String>(), SettingsStore(f).load().romTreeUris)
    }

    @Test
    fun `v3 json without gridDirection defaults to vertical`() {
        val f = tmp.root.resolve("cfg11/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(gridDirection = "horizontal"))
        // Strip the field to simulate a v3 file written before it existed.
        val raw = org.json.JSONObject(f.readText())
        raw.remove("gridDirection")
        f.writeText(raw.toString())
        assertEquals("vertical", SettingsStore(f).load().gridDirection)
    }

    @Test
    fun `custom names and icons round-trip`() {
        val f = tmp.root.resolve("cfg13/settings.json")
        val store = SettingsStore(f)
        val s = Settings.DEFAULT.copy(
            customNames = mapOf("a.b" to "Renamed App", "c.d" to "Other"),
            customIcons = mapOf(
                "a.b" to "content://media/external/images/media/7",
            ),
        )
        store.save(s)
        assertEquals(s, SettingsStore(f).load())
    }

    @Test
    fun `custom name and icon maps default to empty`() {
        assertEquals(emptyMap<String, String>(), Settings.DEFAULT.customNames)
        assertEquals(emptyMap<String, String>(), Settings.DEFAULT.customIcons)
    }

    @Test
    fun `v3 json without custom maps defaults to empty`() {
        val f = tmp.root.resolve("cfg14/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(
            customNames = mapOf("a.b" to "X"),
            customIcons = mapOf("a.b" to "content://y/1"),
        ))
        // Strip the fields to simulate a v3 file written before they existed.
        val raw = org.json.JSONObject(f.readText())
        raw.remove("customNames")
        raw.remove("customIcons")
        f.writeText(raw.toString())
        val loaded = SettingsStore(f).load()
        assertEquals(emptyMap<String, String>(), loaded.customNames)
        assertEquals(emptyMap<String, String>(), loaded.customIcons)
    }

    @Test
    fun `defaults contain CAPACITY blank dockSlots`() {
        assertEquals(List<String?>(DockSlots.CAPACITY) { null }, Settings.DEFAULT.dockSlots)
    }

    @Test
    fun `dockSlots persist filled keys only and re-pad on load`() {
        val f = tmp.root.resolve("cfg15/settings.json")
        val slots = DockSlots.compact(listOf("a.b", "rom:snes:x.sfc", "c.d"))
        SettingsStore(f).save(Settings.DEFAULT.copy(dockSlots = slots))
        // v6 storage: only the filled keys, in slot order — blanks are a
        // render-time derivation, not stored.
        val arr = org.json.JSONObject(f.readText()).getJSONArray("dockSlots")
        assertEquals(3, arr.length())
        assertEquals("a.b", arr.getString(0))
        assertEquals("rom:snes:x.sfc", arr.getString(1))
        assertEquals("c.d", arr.getString(2))
        assertEquals(slots, SettingsStore(f).load().dockSlots)
    }

    @Test
    fun `v3 json migrates dockPackages into leading dockSlots and stamps current schema`() {
        val f = tmp.root.resolve("cfg16/settings.json")
        SettingsStore(f).save(Settings.DEFAULT.copy(
            dockSlots = DockSlots.compact(listOf("a.b", "c.d"))))
        // Downgrade to a v3 file: dockPackages instead of dockSlots.
        val raw = org.json.JSONObject(f.readText())
        raw.remove("dockSlots")
        raw.put("dockPackages", org.json.JSONArray(listOf("a.b", "c.d")))
        raw.put("schemaVersion", 3)
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(DockSlots.compact(listOf("a.b", "c.d")), migrated.dockSlots)
        assertEquals(SettingsStore.CURRENT_SCHEMA, migrated.schemaVersion)
        // The migration is persisted immediately.
        assertEquals(SettingsStore.CURRENT_SCHEMA,
            org.json.JSONObject(f.readText()).getInt("schemaVersion"))
    }

    @Test
    fun `dockPackages overflow beyond the dock capacity is dropped`() {
        val f = tmp.root.resolve("cfg17/settings.json")
        SettingsStore(f).save(Settings.DEFAULT)
        val raw = org.json.JSONObject(f.readText())
        raw.remove("dockSlots")
        raw.put("dockPackages",
            org.json.JSONArray((1..11).map { "a.$it" }))
        raw.put("schemaVersion", 3)
        f.writeText(raw.toString())
        assertEquals(DockSlots.compact((1..9).map { "a.$it" }),
            SettingsStore(f).load().dockSlots)
    }

    @Test
    fun `v3 json without any dock field migrates to blank dockSlots`() {
        val f = tmp.root.resolve("cfg18/settings.json")
        SettingsStore(f).save(Settings.DEFAULT)
        val raw = org.json.JSONObject(f.readText())
        raw.remove("dockSlots")
        raw.put("schemaVersion", 3)
        f.writeText(raw.toString())
        assertEquals(List<String?>(DockSlots.CAPACITY) { null },
            SettingsStore(f).load().dockSlots)
    }

    @Test
    fun `v4 fixed-slot dockSlots keep their filled entries in order`() {
        val f = tmp.root.resolve("cfg19/settings.json")
        SettingsStore(f).save(Settings.DEFAULT)
        // A v4/v5 file: fixed 5-slot array, nulls inline (a removal left a
        // gap). The 5 filled entries must survive in order, padded to 9.
        val raw = org.json.JSONObject(f.readText())
        val legacy = org.json.JSONArray()
        legacy.put("com.brave.browser")
        legacy.put("com.retroarch.aarch64")
        legacy.put(org.json.JSONObject.NULL)
        legacy.put("org.schabi.newpipe")
        legacy.put("org.videolan.vlc")
        raw.put("dockSlots", legacy)
        raw.put("schemaVersion", 5)
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(
            DockSlots.compact(listOf(
                "com.brave.browser", "com.retroarch.aarch64",
                "org.schabi.newpipe", "org.videolan.vlc")),
            migrated.dockSlots)
        assertEquals(5, DockSlots.visibleCount(migrated.dockSlots))
        assertEquals(SettingsStore.CURRENT_SCHEMA, migrated.schemaVersion)
        // The re-saved file stores filled keys only.
        val arr = org.json.JSONObject(f.readText()).getJSONArray("dockSlots")
        assertEquals(4, arr.length())
    }
}
