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
 * Teste WatchReconnectionEngine avec un scope entièrement virtuel
 * (backgroundScope de kotlinx-coroutines-test) : aucune attente réelle,
 * même pour plusieurs cycles de délai progressif.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchReconnectionEngineTest {

    private val initialBackoff = Duration.ofSeconds(5)
    private val maxBackoff = Duration.ofSeconds(20)
    private val multiplier = 1.5

    private class Recorder {
        var nodeAvailable = false
        var requestsSent = 0
        var availabilityChecks = 0
    }

    private fun TestScope.newEngine(recorder: Recorder) = WatchReconnectionEngine(
        initialBackoff = initialBackoff,
        maxBackoff = maxBackoff,
        backoffMultiplier = multiplier,
        scope = backgroundScope,
        isNodeAvailable = {
            recorder.availabilityChecks++
            recorder.nodeAvailable
        },
        sendReconnectionRequest = { recorder.requestsSent++ }
    )

    @Test
    fun `1 - offline then node available triggers an automatic request`() = runTest {
        val recorder = Recorder()
        val engine = newEngine(recorder)

        engine.onOffline()
        recorder.nodeAvailable = true

        advanceTimeBy(initialBackoff.toMillis() + 1)
        runCurrent()

        assertTrue("expected at least 1 request, got ${recorder.requestsSent}", recorder.requestsSent >= 1)
    }

    @Test
    fun `2 - no reply retries after backoff, never concurrently`() = runTest {
        val recorder = Recorder()
        recorder.nodeAvailable = true
        val engine = newEngine(recorder)

        engine.onOffline()

        // La première tentative est immédiate (aucun délai initial avant le
        // premier essai — voir WatchReconnectionEngine.onOffline) : runCurrent()
        // exécute exactement ce premier cycle, jusqu'à sa suspension sur
        // delay(initialBackoff).
        runCurrent()
        assertEquals(1, recorder.requestsSent)

        // No reply arrives (onOnline() never called) — un second cycle doit réessayer.
        advanceTimeBy(initialBackoff.toMillis() + 1)
        runCurrent()

        assertTrue("expected a second attempt, got ${recorder.requestsSent}", recorder.requestsSent >= 2)
        // Une seule tentative à la fois par construction (boucle séquentielle) :
        // le nombre de vérifications de disponibilité est toujours >= au nombre
        // de demandes envoyées, jamais de double envoi simultané détectable ici.
        assertTrue(recorder.availabilityChecks >= recorder.requestsSent)
    }

    @Test
    fun `3 - a reply stops further attempts`() = runTest {
        val recorder = Recorder()
        recorder.nodeAvailable = true
        val engine = newEngine(recorder)

        engine.onOffline()
        advanceTimeBy(initialBackoff.toMillis() + 1)
        runCurrent()
        assertTrue(recorder.requestsSent >= 1)

        engine.onOnline()
        val countAtOnline = recorder.requestsSent
        assertFalse(engine.isAttempting)

        advanceTimeBy(maxBackoff.toMillis() * 3)
        runCurrent()

        assertEquals(countAtOnline, recorder.requestsSent)
    }

    @Test
    fun `4 - no node ever available never sends a request (no false connection)`() = runTest {
        val recorder = Recorder()
        recorder.nodeAvailable = false
        val engine = newEngine(recorder)

        engine.onOffline()

        advanceTimeBy(maxBackoff.toMillis() * 3)
        runCurrent()

        assertEquals(0, recorder.requestsSent)
        assertTrue(recorder.availabilityChecks > 0)
    }

    @Test
    fun `5 - stopping cancels all pending attempts`() = runTest {
        val recorder = Recorder()
        recorder.nodeAvailable = true
        val engine = newEngine(recorder)

        engine.onOffline()
        advanceTimeBy(initialBackoff.toMillis() + 1)
        runCurrent()
        assertTrue(recorder.requestsSent >= 1)

        engine.stop()
        assertFalse(engine.isAttempting)
        val countAtStop = recorder.requestsSent

        advanceTimeBy(maxBackoff.toMillis() * 5)
        runCurrent()

        assertEquals(countAtStop, recorder.requestsSent)
    }

    @Test
    fun `calling onOffline twice does not start a second concurrent loop`() = runTest {
        val recorder = Recorder()
        recorder.nodeAvailable = true
        val engine = newEngine(recorder)

        engine.onOffline()
        engine.onOffline()

        // Un seul cycle immédiat doit s'exécuter, pas deux (pas de boucle
        // concurrente dupliquée) — voir le commentaire du test précédent sur
        // le déclenchement immédiat du premier essai.
        runCurrent()

        assertEquals(1, recorder.requestsSent)
    }
}
