package com.projectfox.foxoff.tv

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoxTvSettingsTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        // FoxTvSettings logue via FoxLogger -> android.util.Log, non
        // disponible en test JVM pur sans ce mock statique.
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        fakePrefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns fakePrefs
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun device(id: String, name: String, address: String, port: Int = 6467) =
        TvDevice(id = id, name = name, address = address, port = port)

    // -----------------------------------------------------------------
    // Plusieurs TV
    // -----------------------------------------------------------------

    @Test
    fun `saving several devices keeps them all retrievable`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        val tv2 = device("id-2", "Samsung Smart TV", "192.168.1.50")

        FoxTvSettings.savePairedDevice(context, tv1)
        FoxTvSettings.savePairedDevice(context, tv2)

        val stored = FoxTvSettings.getPairedDevices(context)

        assertEquals(2, stored.size)
        assertTrue(stored.any { it.id == "id-1" && it.address == "192.168.1.22" })
        assertTrue(stored.any { it.id == "id-2" && it.address == "192.168.1.50" })
    }

    // -----------------------------------------------------------------
    // Unicité de la TV active
    // -----------------------------------------------------------------

    @Test
    fun `first paired device becomes active by default, and only one device is ever active`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        val tv2 = device("id-2", "Samsung Smart TV", "192.168.1.50")

        FoxTvSettings.savePairedDevice(context, tv1)
        FoxTvSettings.savePairedDevice(context, tv2)

        assertEquals("id-1", FoxTvSettings.getActiveDeviceId(context))

        FoxTvSettings.setActiveDevice(context, "id-2")

        assertEquals("id-2", FoxTvSettings.getActiveDeviceId(context))
        // Un seul id actif possible par construction (une seule clé) : pas
        // de risque que id-1 reste "aussi" actif.
    }

    @Test
    fun `setActiveDevice is ignored for an unknown id`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        FoxTvSettings.savePairedDevice(context, tv1)

        FoxTvSettings.setActiveDevice(context, "unknown-id")

        assertEquals("id-1", FoxTvSettings.getActiveDeviceId(context))
    }

    // -----------------------------------------------------------------
    // Suppression de la TV active
    // -----------------------------------------------------------------

    @Test
    fun `removing the active device promotes another remaining device to active`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        val tv2 = device("id-2", "Samsung Smart TV", "192.168.1.50")
        FoxTvSettings.savePairedDevice(context, tv1)
        FoxTvSettings.savePairedDevice(context, tv2)
        assertEquals("id-1", FoxTvSettings.getActiveDeviceId(context))

        FoxTvSettings.removePairedDevice(context, "id-1")

        assertEquals("id-2", FoxTvSettings.getActiveDeviceId(context))
        assertNull(FoxTvSettings.getDevice(context, "id-1"))
        assertEquals(1, FoxTvSettings.getPairedDevices(context).size)
    }

    @Test
    fun `removing the only device leaves no active device`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        FoxTvSettings.savePairedDevice(context, tv1)

        FoxTvSettings.removePairedDevice(context, "id-1")

        assertNull(FoxTvSettings.getActiveDeviceId(context))
        assertTrue(FoxTvSettings.getPairedDevices(context).isEmpty())
    }

    // -----------------------------------------------------------------
    // Empreinte : jamais renseignée automatiquement, jamais modifiée sans appel explicite
    // -----------------------------------------------------------------

    @Test
    fun `a newly paired device has no fingerprint until explicitly set`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")

        FoxTvSettings.savePairedDevice(context, tv1)

        assertNull(FoxTvSettings.getDevice(context, "id-1")?.certificateFingerprint)
    }

    @Test
    fun `an identity mismatch decision alone never mutates the stored entry`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
            .copy(certificateFingerprint = "AA:BB:CC")
        FoxTvSettings.savePairedDevice(context, tv1)

        // La décision de comparaison est pure : l'appeler ne modifie rien
        // par elle-même. Seul un appel explicite à updateFingerprint/
        // updateDeviceAddress changerait le stockage — qu'on ne fait PAS
        // ici, volontairement, pour simuler un Mismatch détecté par
        // l'étape B.
        val check = TvIdentityVerifier.check(
            storedFingerprint = tv1.certificateFingerprint,
            presentedFingerprint = "DD:EE:FF"
        )
        assertEquals(TvIdentityCheck.Mismatch, check)

        val reread = FoxTvSettings.getDevice(context, "id-1")
        assertEquals(tv1.address, reread?.address)
        assertEquals(tv1.certificateFingerprint, reread?.certificateFingerprint)
    }

    @Test
    fun `updateFingerprint only affects the targeted device`() {
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        val tv2 = device("id-2", "Samsung Smart TV", "192.168.1.50")
        FoxTvSettings.savePairedDevice(context, tv1)
        FoxTvSettings.savePairedDevice(context, tv2)

        FoxTvSettings.updateFingerprint(context, "id-1", "AA:BB:CC")

        assertEquals("AA:BB:CC", FoxTvSettings.getDevice(context, "id-1")?.certificateFingerprint)
        assertNull(FoxTvSettings.getDevice(context, "id-2")?.certificateFingerprint)
    }

    // -----------------------------------------------------------------
    // Migration idempotente
    // -----------------------------------------------------------------

    @Test
    fun `legacy single-TV storage is migrated into the multi-TV list`() {
        seedLegacyPrefs(id = "legacy-id", name = "Ancienne Freebox", ip = "192.168.1.10", port = 6467)

        val migrated = FoxTvSettings.getPairedDevice(context)

        assertNotNull(migrated)
        assertEquals("legacy-id", migrated?.id)
        assertEquals("Ancienne Freebox", migrated?.name)
        assertEquals("192.168.1.10", migrated?.address)
        assertNull("la migration ne doit jamais inventer d'empreinte", migrated?.certificateFingerprint)
        assertEquals("legacy-id", FoxTvSettings.getActiveDeviceId(context))
    }

    @Test
    fun `migration is idempotent - calling it twice does not duplicate or change anything`() {
        seedLegacyPrefs(id = "legacy-id", name = "Ancienne Freebox", ip = "192.168.1.10", port = 6467)

        val firstPass = FoxTvSettings.getPairedDevices(context)
        val firstActive = FoxTvSettings.getActiveDeviceId(context)

        // Deuxième déclenchement (n'importe quel appel public suffit).
        val secondPass = FoxTvSettings.getPairedDevices(context)
        val secondActive = FoxTvSettings.getActiveDeviceId(context)

        assertEquals(1, firstPass.size)
        assertEquals(firstPass, secondPass)
        assertEquals(firstActive, secondActive)
    }

    @Test
    fun `migration with no legacy data does nothing and is safe to repeat`() {
        val before = FoxTvSettings.getPairedDevices(context)
        val beforeActive = FoxTvSettings.getActiveDeviceId(context)

        val after = FoxTvSettings.getPairedDevices(context)
        val afterActive = FoxTvSettings.getActiveDeviceId(context)

        assertTrue(before.isEmpty())
        assertTrue(after.isEmpty())
        assertNull(beforeActive)
        assertNull(afterActive)
    }

    @Test
    fun `migration does not clobber a device already saved through the new API`() {
        // Un appairage réalisé après la mise à jour de l'app ne doit pas
        // être perturbé par une migration qui se déclencherait après coup.
        val tv1 = device("id-1", "Freebox Player POP", "192.168.1.22")
        FoxTvSettings.savePairedDevice(context, tv1)

        val stored = FoxTvSettings.getPairedDevices(context)

        assertEquals(1, stored.size)
        assertEquals("id-1", stored.first().id)
    }

    private fun seedLegacyPrefs(id: String, name: String, ip: String, port: Int) {
        fakePrefs.edit()
            .putString("selected_tv_ip", ip)
            .putString("selected_tv_id", id)
            .putString("selected_tv_name", name)
            .putInt("selected_tv_port", port)
            .apply()
    }
}
