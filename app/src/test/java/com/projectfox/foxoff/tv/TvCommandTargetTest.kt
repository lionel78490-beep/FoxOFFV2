package com.projectfox.foxoff.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Garantit qu'une commande (Play/Pause manuel, future pause automatique)
 * ne peut jamais viser qu'une seule TV — la TV active — et jamais une TV
 * hors ligne pour l'automatisation. Décision pure, sans moteur ni réseau.
 */
class TvCommandTargetTest {

    private fun device(id: String, status: TvConnectionStatus) =
        TvDevice(id = id, name = "TV $id", address = "192.168.1.$id", port = 6467, status = status)

    @Test
    fun `no active device means no command target, regardless of requireConnected`() {
        val paired = mapOf("id-1" to device("id-1", TvConnectionStatus.CONNECTED))

        assertNull(TvCommandTarget.resolve(activeDeviceId = null, pairedDevices = paired, requireConnected = true))
        assertNull(TvCommandTarget.resolve(activeDeviceId = null, pairedDevices = paired, requireConnected = false))
    }

    @Test
    fun `active device connected is a valid target for automatic commands`() {
        val paired = mapOf("id-1" to device("id-1", TvConnectionStatus.CONNECTED))

        val target = TvCommandTarget.resolve(activeDeviceId = "id-1", pairedDevices = paired, requireConnected = true)

        assertEquals("id-1", target?.id)
    }

    @Test
    fun `active device offline is never a target for automatic commands`() {
        val paired = mapOf("id-1" to device("id-1", TvConnectionStatus.OFFLINE))

        val target = TvCommandTarget.resolve(activeDeviceId = "id-1", pairedDevices = paired, requireConnected = true)

        assertNull(target)
    }

    @Test
    fun `manual commands do not require a CONNECTED status`() {
        val paired = mapOf("id-1" to device("id-1", TvConnectionStatus.OFFLINE))

        val target = TvCommandTarget.resolve(activeDeviceId = "id-1", pairedDevices = paired, requireConnected = false)

        assertEquals("id-1", target?.id)
    }

    @Test
    fun `a command can never target a non-active device, even if it is connected and the active one is not`() {
        val paired = mapOf(
            "id-1" to device("id-1", TvConnectionStatus.OFFLINE),
            "id-2" to device("id-2", TvConnectionStatus.CONNECTED)
        )

        // id-1 est active mais hors ligne ; id-2 est connectée mais pas
        // active : aucune commande automatique ne doit partir vers id-2.
        val target = TvCommandTarget.resolve(activeDeviceId = "id-1", pairedDevices = paired, requireConnected = true)

        assertNull(target)
    }

    @Test
    fun `active device missing from the paired map yields no target`() {
        // Cas défensif : activeDeviceId pointe vers un id qui n'existe plus
        // (ex: race avec une dissociation) -- jamais de commande envoyée.
        val paired = mapOf("id-2" to device("id-2", TvConnectionStatus.CONNECTED))

        val target = TvCommandTarget.resolve(activeDeviceId = "id-1", pairedDevices = paired, requireConnected = true)

        assertNull(target)
    }
}
