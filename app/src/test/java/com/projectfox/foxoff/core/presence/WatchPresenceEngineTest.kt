package com.projectfox.foxoff.core.presence

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Teste WatchPresenceEngine — le modèle PILOTÉ PAR ÉCHÉANCE (pas de
 * sondage périodique) — avec un scope entièrement VIRTUEL (backgroundScope
 * de kotlinx-coroutines-test) : aucune attente réelle, même pour les
 * délais de 15s + 5s (compromis "modéré" validé : "Hors ligne" visible en
 * ~20s). Aucune horloge à injecter : les échéances sont de simples delay()
 * de coroutine, dont le temps est piloté par le TestCoroutineScheduler
 * ambiant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchPresenceEngineTest {

    private val gracePeriod = Duration.ofSeconds(15)
    private val confirmationTimeout = Duration.ofSeconds(5)
    private val disconnectDebounceMs = 3_000L

    private class Recorder {
        var verifyingCount = 0
        var confirmationRequests = 0
        var declaredOfflineCount = 0
    }

    private fun TestScope.newEngine(recorder: Recorder) = WatchPresenceEngine(
        gracePeriod = gracePeriod,
        confirmationTimeout = confirmationTimeout,
        disconnectDebounceMs = disconnectDebounceMs,
        scope = backgroundScope,
        onVerifying = { recorder.verifyingCount++ },
        onRequestConfirmation = { recorder.confirmationRequests++ },
        onDeclareOffline = { recorder.declaredOfflineCount++ }
    )

    @Test
    fun `no proof for 15s triggers verifying and an immediate confirmation request`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.recordProof()
        advanceTimeBy(gracePeriod.toMillis() + 1)
        runCurrent()

        assertEquals(1, recorder.verifyingCount)
        assertEquals(1, recorder.confirmationRequests)
        assertEquals(0, recorder.declaredOfflineCount)
    }

    @Test
    fun `no reply within 5s after the confirmation declares offline (about 20s total)`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.recordProof()

        // Juste avant l'échéance totale (15s + 5s) : encore rien.
        advanceTimeBy(gracePeriod.toMillis() + confirmationTimeout.toMillis() - 1)
        runCurrent()
        assertEquals(1, recorder.confirmationRequests)
        assertEquals(0, recorder.declaredOfflineCount)

        // Juste après : déclaré Hors ligne.
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, recorder.declaredOfflineCount)
    }

    @Test
    fun `a reply during the 5s confirmation window returns to connected without ever going offline`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.recordProof()
        advanceTimeBy(gracePeriod.toMillis() + 1)
        runCurrent()
        assertEquals(1, recorder.confirmationRequests)

        // A real reply arrives well inside the 5s confirmation window.
        engine.recordProof()
        advanceTimeBy(confirmationTimeout.toMillis() + 10_000)
        runCurrent()

        assertEquals(0, recorder.declaredOfflineCount)
    }

    @Test
    fun `a reply during the initial 15s grace period cancels the deadline immediately`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.recordProof()
        advanceTimeBy(gracePeriod.toMillis() / 2)
        runCurrent()

        // Reply arrives well before the 15s grace elapses.
        engine.recordProof()

        // Advance past where the ORIGINAL deadline would have fired.
        advanceTimeBy(gracePeriod.toMillis() / 2 + 1_000)
        runCurrent()

        assertEquals(0, recorder.verifyingCount)
        assertEquals(0, recorder.confirmationRequests)
    }

    @Test
    fun `a real onPeerDisconnected declares offline after the 3s debounce, bypassing the deadline`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)
        engine.recordProof()

        engine.onPeerDisconnected("watch-1", "watch-1")
        assertEquals(0, recorder.declaredOfflineCount)

        advanceTimeBy(disconnectDebounceMs + 1)
        runCurrent()

        assertEquals(1, recorder.declaredOfflineCount)
        // Le chemin rapide a court-circuité l'échéance de 15s+5s.
        assertEquals(0, recorder.confirmationRequests)
    }

    @Test
    fun `reconnecting within the debounce cancels the event-driven disconnect`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)
        engine.recordProof()

        engine.onPeerDisconnected("watch-1", "watch-1")
        engine.onPeerConnected("watch-1", "watch-1")

        advanceTimeBy(disconnectDebounceMs + 1)
        runCurrent()

        assertEquals(0, recorder.declaredOfflineCount)
    }

    @Test
    fun `disconnection of another node is ignored`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)
        engine.recordProof()

        engine.onPeerDisconnected("watch-1", "some-other-node")

        advanceTimeBy(disconnectDebounceMs + 1)
        runCurrent()

        assertEquals(0, recorder.declaredOfflineCount)
    }

    @Test
    fun `reset cancels every pending deadline`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.recordProof()
        advanceTimeBy(gracePeriod.toMillis() + 1)
        runCurrent()
        assertEquals(1, recorder.confirmationRequests)
        assertTrue(engine.isArmed)

        engine.reset()
        assertFalse(engine.isArmed)

        advanceTimeBy(confirmationTimeout.toMillis() + 10_000)
        runCurrent()

        assertEquals(0, recorder.declaredOfflineCount)
    }
}
