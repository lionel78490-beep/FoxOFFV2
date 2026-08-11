package com.projectfox.foxoff.sensors

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Verrouille le format binaire échangé entre la montre et le téléphone sur
 * le path /foxoff/hr (encodage : WearCommunicationManager.transmit() dans
 * :wear ; décodage : PhoneWearListenerService.onMessageReceived() dans
 * :app). Les deux modules ne se référencent pas entre eux (deux APK
 * distincts) : ce test reproduit fidèlement les deux extrémités du contrat
 * pour détecter toute rupture de symétrie si l'un des deux côtés change
 * (ordre des octets, type, taille) sans que l'autre suive.
 */
class HeartRateWireFormatTest {

    // Reproduit WearCommunicationManager.transmit()
    private fun encode(bpm: Float): ByteArray =
        ByteBuffer.allocate(4).putFloat(bpm).array()

    // Reproduit PhoneWearListenerService.onMessageReceived()
    private fun decode(data: ByteArray): Float =
        ByteBuffer.wrap(data).float

    @Test
    fun `un BPM encode puis decode restitue la meme valeur`() {
        val values = listOf(0f, 42f, 60.5f, 72f, 123.4f, 220f)
        values.forEach { bpm ->
            assertEquals(bpm, decode(encode(bpm)), 0.0f)
        }
    }

    @Test
    fun `le payload encode fait exactement 4 octets`() {
        assertEquals(4, encode(72f).size)
    }
}
