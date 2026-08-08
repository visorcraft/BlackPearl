package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaProgressGateTest {

    private val sample = RaProgress(
        gameId = 1,
        title = "Demo",
        numAwarded = 2,
        numPossible = 10,
        userScore = 100,
        hardcore = false,
    )

    @Test
    fun `mayFetch requires credentials and one attempt`() {
        assertFalse(RaProgressGate.mayFetch("rom", null, "k", emptySet(), emptySet()))
        assertFalse(RaProgressGate.mayFetch("rom", "u", "", emptySet(), emptySet()))
        assertFalse(RaProgressGate.mayFetch("", "u", "k", emptySet(), emptySet()))
        assertTrue(RaProgressGate.mayFetch("rom", "u", "k", emptySet(), emptySet()))
        assertFalse(RaProgressGate.mayFetch("rom", "u", "k", setOf("rom"), emptySet()))
        assertFalse(RaProgressGate.mayFetch("rom", "u", "k", emptySet(), setOf("rom")))
    }

    @Test
    fun `isSameProgress field equality`() {
        assertTrue(RaProgressGate.isSameProgress(sample, sample.copy()))
        assertFalse(RaProgressGate.isSameProgress(null, sample))
        assertFalse(RaProgressGate.isSameProgress(sample, sample.copy(numAwarded = 3)))
    }

    @Test
    fun `notifyAfterStore is selection only never settings`() {
        assertEquals(
            RaProgressGate.NotifyKind.NONE,
            RaProgressGate.notifyAfterStore(sample, sample.copy()),
        )
        assertEquals(
            RaProgressGate.NotifyKind.NONE,
            RaProgressGate.notifyAfterStore(null, RaProgress()),
        )
        assertEquals(
            RaProgressGate.NotifyKind.SELECTION_ONLY,
            RaProgressGate.notifyAfterStore(null, sample),
        )
        assertEquals(
            RaProgressGate.NotifyKind.SELECTION_ONLY,
            RaProgressGate.notifyAfterStore(sample, sample.copy(numAwarded = 9)),
        )
    }
}
