package com.projectfox.foxoff.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la logique de routage/état d'une tentative de connexion à une TV
 * mémorisée (voir FoxTvEngine.applyConnectionResult), isolée de tout code
 * réseau : TvConnectionTestResult est fabriqué directement, aucun socket
 * n'est impliqué.
 */
class TvConnectionOutcomeResolverTest {

    @Test
    fun `matching fingerprint at the same address reconnects without an address update`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.Connected("aabbcc")
        )

        assertEquals(TvConnectionOutcome.Reconnected(updateStoredAddress = false), outcome)
    }

    @Test
    fun `matching fingerprint at a new address reconnects and asks to update the stored address`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.99",
            result = TvConnectionTestResult.Connected("aabbcc")
        )

        assertEquals(TvConnectionOutcome.Reconnected(updateStoredAddress = true), outcome)
    }

    @Test
    fun `mismatched fingerprint is never treated as a reconnection, regardless of address`() {
        val sameAddress = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.Connected("ddeeff")
        )
        val differentAddress = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.99",
            result = TvConnectionTestResult.Connected("ddeeff")
        )

        assertEquals(TvConnectionOutcome.IdentityMismatch, sameAddress)
        assertEquals(TvConnectionOutcome.IdentityMismatch, differentAddress)
    }

    @Test
    fun `no stored fingerprint requires confirmation instead of auto-connecting`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = null,
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.Connected("aabbcc")
        )

        assertTrue(outcome is TvConnectionOutcome.ConfirmationRequired)
        assertEquals("aabbcc", (outcome as TvConnectionOutcome.ConfirmationRequired).fingerprint)
    }

    @Test
    fun `network failure never touches identity, only reports offline`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.NetworkUnreachable
        )

        assertEquals(TvConnectionOutcome.Offline, outcome)
    }

    @Test
    fun `confirmed TLS rejection is the only case requiring a new pairing`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "aabbcc",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.AuthRejected
        )

        assertEquals(TvConnectionOutcome.PairingRequired, outcome)
    }

    @Test
    fun `brand new device with no stored address still resolves address update correctly`() {
        // Cas d'un tout nouvel appairage (pas encore de storedAddress) :
        // ne doit pas planter, et updateStoredAddress doit rester cohérent
        // (une adresse null est toujours "différente" de l'adresse connectée).
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = null,
            storedAddress = null,
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.Connected("aabbcc")
        )

        assertTrue(outcome is TvConnectionOutcome.ConfirmationRequired)
    }

    @Test
    fun `fingerprint comparison is not fooled by case differences`() {
        val outcome = TvConnectionOutcomeResolver.resolve(
            storedFingerprint = "AABBCC",
            storedAddress = "192.168.1.22",
            connectedAtAddress = "192.168.1.22",
            result = TvConnectionTestResult.Connected("aabbcc")
        )

        assertTrue(outcome is TvConnectionOutcome.Reconnected)
        assertFalse((outcome as TvConnectionOutcome.Reconnected).updateStoredAddress)
    }
}
