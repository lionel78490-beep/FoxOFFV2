package com.projectfox.foxoff.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvStateRepositoryTest {

    private fun device(id: String, address: String = "192.168.1.22") =
        TvDevice(id = id, name = "TV $id", address = address, port = 6467)

    @Test
    fun `updateStatus only changes the targeted device`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1"), device("id-2", "192.168.1.50")))

        repo.updateStatus("id-1", TvConnectionStatus.CONNECTED)

        assertEquals(TvConnectionStatus.CONNECTED, repo.getDevice("id-1")?.status)
        assertEquals(TvConnectionStatus.DISCONNECTED, repo.getDevice("id-2")?.status)
    }

    @Test
    fun `updateStatus on an unknown id is a no-op`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1")))

        repo.updateStatus("unknown-id", TvConnectionStatus.CONNECTED)

        assertNull(repo.getDevice("unknown-id"))
        assertEquals(1, repo.pairedDeviceStates.value.size)
    }

    @Test
    fun `removing a non-active device leaves the active device untouched`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1"), device("id-2", "192.168.1.50")))
        repo.setActiveDeviceId("id-1")

        repo.removePairedDevice("id-2")

        assertEquals("id-1", repo.activeDeviceId.value)
        assertNull(repo.getDevice("id-2"))
    }

    @Test
    fun `removing the active device clears activeDeviceId (reassignment is FoxTvEngine's job)`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1"), device("id-2", "192.168.1.50")))
        repo.setActiveDeviceId("id-1")

        repo.removePairedDevice("id-1")

        assertNull(repo.activeDeviceId.value)
    }

    @Test
    fun `upsertPairedDevice adds a new entry without disturbing existing ones`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1")))

        repo.upsertPairedDevice(device("id-2", "192.168.1.50"))

        assertEquals(2, repo.pairedDeviceStates.value.size)
        assertTrue(repo.pairedDeviceStates.value.containsKey("id-1"))
        assertTrue(repo.pairedDeviceStates.value.containsKey("id-2"))
    }

    @Test
    fun `upsertPairedDevice on an existing id replaces it entirely`() {
        val repo = TvStateRepository()
        repo.setPairedDevices(listOf(device("id-1")))

        repo.upsertPairedDevice(device("id-1", "192.168.1.100").copy(status = TvConnectionStatus.CONNECTED))

        val updated = repo.getDevice("id-1")
        assertEquals("192.168.1.100", updated?.address)
        assertEquals(TvConnectionStatus.CONNECTED, updated?.status)
        assertEquals(1, repo.pairedDeviceStates.value.size)
    }
}
