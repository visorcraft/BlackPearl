package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRoleTest {

    @Test
    fun `parse accepts names case-insensitively and blanks to HERO`() {
        assertEquals(CompanionRole.HERO, CompanionRole.parse("HERO"))
        assertEquals(CompanionRole.NOW_PLAYING, CompanionRole.parse("now_playing"))
        assertEquals(CompanionRole.PERF_HUD, CompanionRole.parse(" Perf_Hud "))
        assertEquals(CompanionRole.PINNED_APP, CompanionRole.parse("PINNED_APP"))
        assertEquals(CompanionRole.HERO, CompanionRole.parse(null))
        assertEquals(CompanionRole.HERO, CompanionRole.parse(""))
        assertEquals(CompanionRole.HERO, CompanionRole.parse("nope"))
    }

    @Test
    fun `effective keeps HERO and PERF_HUD as preferred`() {
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.HERO),
            ),
        )
        assertEquals(
            CompanionRole.PERF_HUD,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.PERF_HUD),
            ),
        )
    }

    @Test
    fun `effective NOW_PLAYING needs an open session else HERO`() {
        assertEquals(
            CompanionRole.NOW_PLAYING,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.NOW_PLAYING,
                    openSessionKey = "rom:snes:x.sfc",
                ),
            ),
        )
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.NOW_PLAYING),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP when package set and installed`() {
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    pinnedPackageInstalled = true,
                ),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP dual-claim nds degrades to NOW_PLAYING or HERO`() {
        assertEquals(
            CompanionRole.NOW_PLAYING,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    openSessionKey = "rom:nds:mario.nds",
                    openSessionPlatformId = "nds",
                ),
            ),
        )
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    openSessionPlatformId = "3ds",
                    openSessionKey = null,
                ),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP missing pin package falls to HERO`() {
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = null,
                ),
            ),
        )
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "   ",
                ),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP uninstalled pin falls to HERO`() {
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.gone",
                    pinnedPackageInstalled = false,
                ),
            ),
        )
    }
}
