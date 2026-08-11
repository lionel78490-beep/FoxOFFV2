package com.projectfox.foxoff.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvMultiDeviceMatcherTest {

    private val storedFreebox = TvDevice(
        id = "id-freebox",
        name = "Freebox Player POP",
        address = "192.168.1.22",
        port = 6467
    )

    private val storedSamsung = TvDevice(
        id = "id-samsung",
        name = "Samsung Smart TV",
        address = "192.168.1.50",
        port = 8009
    )

    private val storedDevices = listOf(storedFreebox, storedSamsung)

    @Test
    fun `same address matches by address, even with a different discovery id`() {
        val discovered = storedFreebox.copy(id = "ssdp_192.168.1.22")

        val result = TvMultiDeviceMatcher.match(discovered, storedDevices)

        assertEquals(TvMatchCandidate.AddressCandidate(storedFreebox), result)
    }

    @Test
    fun `same name but different address is an ambiguous name-only candidate`() {
        val discovered = storedFreebox.copy(id = "ssdp_192.168.1.99", address = "192.168.1.99")

        val result = TvMultiDeviceMatcher.match(discovered, storedDevices)

        assertEquals(TvMatchCandidate.NameOnlyCandidate(storedFreebox), result)
    }

    @Test
    fun `different name and different address is no candidate`() {
        val discovered = TvDevice(
            id = "id-other",
            name = "LG WebOS TV",
            address = "192.168.1.77",
            port = 3000
        )

        val result = TvMultiDeviceMatcher.match(discovered, storedDevices)

        assertEquals(TvMatchCandidate.NoCandidate, result)
    }

    @Test
    fun `empty stored list never produces a candidate`() {
        val discovered = storedFreebox.copy()

        val result = TvMultiDeviceMatcher.match(discovered, emptyList())

        assertEquals(TvMatchCandidate.NoCandidate, result)
    }

    @Test
    fun `address match takes priority over a name-only match on another entry`() {
        // Adresse de storedSamsung, mais nom de storedFreebox : l'adresse
        // doit gagner (signal plus fort, voir TvMultiDeviceMatcher).
        val discovered = storedFreebox.copy(id = "mixed", address = storedSamsung.address)

        val result = TvMultiDeviceMatcher.match(discovered, storedDevices)

        assertEquals(TvMatchCandidate.AddressCandidate(storedSamsung), result)
    }
}
