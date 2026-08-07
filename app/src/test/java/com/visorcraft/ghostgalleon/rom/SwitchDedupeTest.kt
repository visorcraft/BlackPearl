package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchDedupeTest {

    // The real 63 on-card Switch packages (15 bases, 13 updates, 35 DLC),
    // from the 2026-08-04 card selection manifests
    // (~/onex-sugar/switch-dedupe/sugar-switch-selection*-20260804.json).
    private val cardFilenames = listOf(
        "v-sid_meiers_civilization_vii_ashoka_world_conqueror_persona_dlc.nsp",
        "v-sid_meiers_civilization_vii_crossroads_of_the_world_collection_cosmetic_bonus_dlc.nsp",
        "v-sid_meiers_civilization_vii_deluxe_cosmetics_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_founders_cosmetics_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_leader_persona_1_dlc.nsp",
        "v-sid_meiers_civilization_vii_leader_persona_3_dlc.nsp",
        "v-sid_meiers_civilization_vii_right_to_rule_collection_cosmetic_bonus_dlc.nsp",
        "v-sid_meiers_civilization_vii_tecumseh_and_shawnee_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_xerxes_the_achaemenid_persona_dlc.nsp",
        "v-sid_meiers_civilization_vii.nsp",
        "v-sid_meiers_civilization_vii_v720896.nsp",
        "v-sid_meiers_civilization_vii_ada_lovelace_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_carthage_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_crossroads_of_the_world_collection_natural_wonder_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_great_britain_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_bulgaria_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_nepal_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_simon_bolivar_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_assyria_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_dai_viet_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_genghis_khan_pack_dlc.nsp",
        "v-sid_meiers_civilization_vii_right_to_rule_collection_wonder_pack_dlc.nsp",
        "Super Mario Bros. Wonder [010015100B514000][v0][Base].xci",
        "Super Mario Bros. Wonder [010015100B514800][v65536][US](nsw2u.com).nsp",
        "Mario Kart 8 Deluxe [Booster Course][0100152000023001][v65536][US].nsp",
        "Mario Kart 8 Deluxe.nsp",
        "v-mario_kart_8_deluxe_v1179648.nsp",
        "The Legend of Zelda Echoes of Wisdom [01008CF01BAAC000][v0][US].nsp",
        "The Legend of Zelda Echoes of Wisdom [01008CF01BAAC800] [UPD v65536].nsp",
        "The Legend of Zelda Tears of the Kingdom [0100F2C0115B6000].xci",
        "The Legend of Zelda ToTK 1.4.2 [0100F2C0115B6800][v655360].nsp",
        "The Legend of Zelda BoTW [01007EF00011E000][US][v0].nsp",
        "The Legend of Zelda BoTW v1.8.2 [01007EF00011E800][v1048576].nsp",
        "The Legend of Zelda Breath of the Wild [DLC Pack 1 The Master Trials] [01007EF00011F001][v196608].nsp",
        "The Legend of Zelda Breath of the Wild [DLC Pack 2 The Champions Ballad] [01007EF00011F002][v196608].nsp",
        "Xenoblade Chronicles 3 [010074F013262000][v0][US](nsw2u.in).nsp",
        "Xenoblade Chronicles 3 [010074F013262800][v589824][US](nsw2u.com).nsp",
        "Xenoblade Chronicles 3 [DLC Wave 1] [010074F013263001][v0].nsp",
        "Xenoblade Chronicles 3 [DLC Wave 2].nsp",
        "Xenoblade Chronicles 3 [DLC Wave 3].nsp",
        "Xenoblade Chronicles 3 [Wave 4][010074F013263004][v0][US].nsp",
        "MONSTER HUNTER GENERATIONS ULTIMATE [0100770008DD8000][v0].nsp",
        "MONSTER HUNTER GENERATIONS ULTIMATE [0100770008DD8800][v262144].nsp",
        "Hades II [0100A00019DE0000][v0].nsp",
        "Hades II [0100A00019DE0800][v196608].nsp",
        "Tomodachi Life Living the Dream [010051F0207B2000][v0].xci",
        "DRAGON QUEST XI S Echoes of an Elusive Age – Definitive Edition [01006C300E9F0000][v0].nsp",
        "DRAGON QUEST XI S Echoes of an Elusive Age – Definitive Edition [01006C300E9F0800][v262144][US][1.0.4].nsp",
        "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Baby Boar Set DLC][01006C300E9F1015][v0][US].nsp",
        "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Happy Adventurer Set DLC][01006C300E9F1017][v0][US].nsp",
        "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Trodain Set DLC][01006C300E9F1016][v0][US].nsp",
        "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Vests of Success Set DLC][01006C300E9F1019][v0][US].nsp",
        "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Wolf Wear DLC][01006C300E9F1018][v0][US].nsp",
        "OCTOPATH TRAVELER II [0100A3501946E000][v0][US].nsp",
        "OCTOPATH TRAVELER II [0100A3501946E800][131072][1.1.1].nsp",
        "OCTOPATH TRAVELER II [Travel Provisions][0100A3501946F001][DLC].nsp",
        "Tactics Ogre Reborn [0100E12013C1A000][v0][US].nsp",
        "Tactics Ogre Reborn [0100E12013C1A800][v393216][US](nsw2u.com).nsp",
        "Paper Mario The Thousand-Year Door [0100ECD018EBE000][v0].xci",
        "Unicorn Overlord [010069401ADB8000][v0][US].nsp",
        "Unicorn Overlord [010069401ADB8800][262144][1.05].nsp",
        "Unicorn Overlord [ATLUS x Vanillaware Heraldry Pack] [010069401ADB9001][v0].nsp",
        "Unicorn Overlord [Unicorn Overlord 16-bit Arranged Music Album and Digital Artbook] [010069401ADB9002][v0].nsp",
    )

    private fun entry(fileName: String): RomEntry = RomEntry(
        id = "switch:switch/$fileName",
        name = fileName.substringBeforeLast('.'),
        platformId = Platforms.SWITCH.id,
        uri = "content://docs/document/7F7E-2949%3Aroms%2Fswitch%2F$fileName",
        path = "/storage/7F7E-2949/roms/switch/$fileName",
    )

    @Test
    fun `real 63-package card library dedupes to its 15 bases`() {
        val result = SwitchDedupe.apply(cardFilenames.map(::entry))
        assertEquals(63, result.size)
        val visible = result.filter { it.visibleInUi }
        assertEquals(
            "hidden: " + result.filterNot { it.visibleInUi }.joinToString("\n") { it.name },
            15, visible.size,
        )
        val expectedBases = setOf(
            "v-sid_meiers_civilization_vii",
            "Super Mario Bros. Wonder [010015100B514000][v0][Base]",
            "Mario Kart 8 Deluxe",
            "The Legend of Zelda Echoes of Wisdom [01008CF01BAAC000][v0][US]",
            "The Legend of Zelda Tears of the Kingdom [0100F2C0115B6000]",
            "The Legend of Zelda BoTW [01007EF00011E000][US][v0]",
            "Xenoblade Chronicles 3 [010074F013262000][v0][US](nsw2u.in)",
            "MONSTER HUNTER GENERATIONS ULTIMATE [0100770008DD8000][v0]",
            "Hades II [0100A00019DE0000][v0]",
            "Tomodachi Life Living the Dream [010051F0207B2000][v0]",
            "DRAGON QUEST XI S Echoes of an Elusive Age – Definitive Edition [01006C300E9F0000][v0]",
            "OCTOPATH TRAVELER II [0100A3501946E000][v0][US]",
            "Tactics Ogre Reborn [0100E12013C1A000][v0][US]",
            "Paper Mario The Thousand-Year Door [0100ECD018EBE000][v0]",
            "Unicorn Overlord [010069401ADB8000][v0][US]",
        )
        assertEquals(expectedBases, visible.map { it.name }.toSet())
    }

    @Test
    fun `non-switch entries pass through untouched`() {
        val snes = RomEntry(
            id = "snes:roms/snes/Chrono Trigger (USA).sfc",
            name = "Chrono Trigger (USA)",
            platformId = "snes",
            uri = "content://x",
            path = null,
        )
        val result = SwitchDedupe.apply(listOf(snes))
        assertEquals(listOf(snes), result)
    }

    @Test
    fun `update without its base stays visible`() {
        val updateOnly = entry("Hades II [0100A00019DE0800][v196608].nsp")
        val result = SwitchDedupe.apply(listOf(updateOnly))
        assertTrue(result.single().visibleInUi)
    }

    @Test
    fun `lowest-version base is preferred when a group has several`() {
        val v1 = entry("Some Game [0100ABCD12340000][v65536].nsp")
        val v0 = entry("Some Game [0100ABCD12340000][v0].nsp")
        val result = SwitchDedupe.apply(listOf(v1, v0))
        assertTrue(result.first { it.id == v0.id }.visibleInUi)
        assertFalse(result.first { it.id == v1.id }.visibleInUi)
    }

    @Test
    fun `title normalization strips tags symbols and version tokens`() {
        assertEquals(
            "dragon quest xi s echoes of an elusive age definitive edition",
            SwitchDedupe.normalizeTitle(
                "DRAGON QUEST® XI S Echoes of an Elusive Age – Definitive Edition [Baby Boar Set DLC][01006C300E9F1015][v0][US]",
            ),
        )
        assertEquals(
            "the legend of zelda botw",
            SwitchDedupe.normalizeTitle(
                "The Legend of Zelda BoTW v1.8.2 [01007EF00011E800][v1048576]",
            ),
        )
        assertEquals(
            "xenoblade chronicles 3",
            SwitchDedupe.normalizeTitle("Xenoblade Chronicles 3 [DLC Wave 2]"),
        )
        assertEquals(
            "sid meiers civilization vii",
            SwitchDedupe.normalizeTitle("v-sid_meiers_civilization_vii_v720896"),
        )
    }

    @Test
    fun `title id key groups base update and dlc of one game`() {
        assertEquals(
            "01006C300E9F",
            SwitchDedupe.titleIdKey("x [01006C300E9F0000][v0].nsp"),
        )
        assertEquals(
            "01006C300E9F",
            SwitchDedupe.titleIdKey("x [01006C300E9F0800][v262144].nsp"),
        )
        assertEquals(
            "01006C300E9F",
            SwitchDedupe.titleIdKey("x [01006C300E9F1015][v0].nsp"),
        )
        assertEquals(null, SwitchDedupe.titleIdKey("Mario Kart 8 Deluxe"))
    }
}
