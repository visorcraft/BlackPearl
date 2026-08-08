package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemountPolicyTest {

    @Test
    fun `no granted trees never rescans`() {
        assertFalse(
            RemountPolicy.shouldQuietRescanOnResume(
                grantedTreeCount = 0,
                libraryEntryCount = 0,
                lastHadUnreadableTree = true,
                hadSuccessfulScan = false,
            ),
        )
    }

    @Test
    fun `unreadable last time triggers quiet rescan`() {
        assertTrue(
            RemountPolicy.shouldQuietRescanOnResume(
                grantedTreeCount = 1,
                libraryEntryCount = 100,
                lastHadUnreadableTree = true,
                hadSuccessfulScan = true,
            ),
        )
    }

    @Test
    fun `never scanned empty library triggers quiet rescan`() {
        assertTrue(
            RemountPolicy.shouldQuietRescanOnResume(
                grantedTreeCount = 2,
                libraryEntryCount = 0,
                lastHadUnreadableTree = false,
                hadSuccessfulScan = false,
            ),
        )
    }

    @Test
    fun `healthy library does not quiet rescan`() {
        assertFalse(
            RemountPolicy.shouldQuietRescanOnResume(
                grantedTreeCount = 1,
                libraryEntryCount = 50,
                lastHadUnreadableTree = false,
                hadSuccessfulScan = true,
            ),
        )
    }

    @Test
    fun `nextHadUnreadableFlag tracks all and partial unreadable`() {
        assertTrue(RemountPolicy.nextHadUnreadableFlag(allUnreadable = true))
        assertTrue(
            RemountPolicy.nextHadUnreadableFlag(
                allUnreadable = false,
                retainedUnreadableTreeCount = 1,
            ),
        )
        assertFalse(
            RemountPolicy.nextHadUnreadableFlag(
                allUnreadable = false,
                retainedUnreadableTreeCount = 0,
            ),
        )
    }
}
