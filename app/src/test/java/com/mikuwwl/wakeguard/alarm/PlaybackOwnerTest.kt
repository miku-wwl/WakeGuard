package com.mikuwwl.wakeguard.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOwnerTest {
    @Test fun anOldPreparedCallbackCannotStartAfterReplacementOrStop() {
        val owner = PlaybackOwner<DeferredPlayer>()
        val first = requireNotNull(owner.start(10) { DeferredPlayer() })
        assertNull(owner.start(10) { error("Duplicate delivery created playback") })
        val second = requireNotNull(owner.start(11) { DeferredPlayer() })
        assertEquals(1, first.releases)
        assertFalse(owner.owns(first))
        assertTrue(owner.owns(second))
        owner.close()
        owner.close()
        assertFalse(owner.owns(second))
        assertEquals(1, second.releases)
    }

    private class DeferredPlayer : AutoCloseable {
        var releases = 0
        override fun close() { releases++ }
    }
}
