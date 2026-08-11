package com.projectfox.foxoff.core.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNodeFilterTest {

    @Test
    fun `disconnection of another node is ignored`() {
        assertFalse(WatchNodeFilter.isActiveNode(knownNodeId = "watch-1", eventNodeId = "other-node"))
    }

    @Test
    fun `event matching the known node is active`() {
        assertTrue(WatchNodeFilter.isActiveNode(knownNodeId = "watch-1", eventNodeId = "watch-1"))
    }

    @Test
    fun `no known node yet means no event can be the active watch`() {
        assertFalse(WatchNodeFilter.isActiveNode(knownNodeId = null, eventNodeId = "watch-1"))
    }
}
