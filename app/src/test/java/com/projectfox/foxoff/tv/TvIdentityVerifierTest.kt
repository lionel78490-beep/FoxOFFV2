package com.projectfox.foxoff.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvIdentityVerifierTest {

    @Test
    fun `identical fingerprints are confirmed`() {
        val result = TvIdentityVerifier.check(
            storedFingerprint = "AA:BB:CC:DD",
            presentedFingerprint = "AA:BB:CC:DD"
        )

        assertEquals(TvIdentityCheck.Confirmed, result)
    }

    @Test
    fun `fingerprint comparison is case-insensitive`() {
        val result = TvIdentityVerifier.check(
            storedFingerprint = "aa:bb:cc:dd",
            presentedFingerprint = "AA:BB:CC:DD"
        )

        assertEquals(TvIdentityCheck.Confirmed, result)
    }

    @Test
    fun `different fingerprints are a mismatch`() {
        val result = TvIdentityVerifier.check(
            storedFingerprint = "AA:BB:CC:DD",
            presentedFingerprint = "11:22:33:44"
        )

        assertEquals(TvIdentityCheck.Mismatch, result)
    }

    @Test
    fun `no stored fingerprint is reported distinctly, never treated as a match`() {
        val result = TvIdentityVerifier.check(
            storedFingerprint = null,
            presentedFingerprint = "AA:BB:CC:DD"
        )

        assertEquals(TvIdentityCheck.NoStoredFingerprint, result)
    }
}
