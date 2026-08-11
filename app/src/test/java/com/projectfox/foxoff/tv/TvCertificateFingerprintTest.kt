package com.projectfox.foxoff.tv

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

class TvCertificateFingerprintTest {

    private fun selfSignedCertificate(commonName: String): X509Certificate {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        val name = X500Name("CN=$commonName")
        val now = System.currentTimeMillis()

        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(1),
            Date(now),
            Date(now + 3650L * 24 * 60 * 60 * 1000),
            name,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)

        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    @Test
    fun `fingerprint is deterministic for the same certificate`() {
        val cert = selfSignedCertificate("atvremote-1")

        val first = TvCertificateFingerprint.of(cert)
        val second = TvCertificateFingerprint.of(cert)

        assertEquals(first, second)
    }

    @Test
    fun `different certificates produce different fingerprints`() {
        val certA = selfSignedCertificate("atvremote-a")
        val certB = selfSignedCertificate("atvremote-b")

        assertNotEquals(
            TvCertificateFingerprint.of(certA),
            TvCertificateFingerprint.of(certB)
        )
    }

    @Test
    fun `canonical format is lowercase hex, 64 characters, no separators`() {
        val fingerprint = TvCertificateFingerprint.of(selfSignedCertificate("atvremote-format"))

        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
