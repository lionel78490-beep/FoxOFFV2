package com.projectfox.foxoff.testing

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ne teste AUCUNE logique métier FoxOFF. Vérifie uniquement que la chaîne
 * d'infrastructure de test (JUnit + runTest + Turbine + MockK) fonctionne
 * réellement de bout en bout, avec un agencement représentatif de ce qui
 * sera testé plus tard pour de vrai : une dépendance mockée (port/repository),
 * un composant qui expose un StateFlow en lecture seule, et une assertion
 * de séquence sur ce Flow au sein d'une coroutine de test.
 *
 * Voir ROADMAP.md Phase 0 / Tâche 0.4 et Phase 5 (tests réels de FoxBrain).
 */

/** Dépendance fictive mockée — représentative d'un port/repository injecté. */
private interface FakeReadingPort {
    fun read(): Int
}

/**
 * Composant minimal sous test, local à ce fichier — même agencement que
 * FoxBrain/WearCommunicationManager (état interne + StateFlow exposé en
 * lecture seule, mis à jour via un appel à une dépendance injectée), mais
 * sans aucun code ni logique FoxOFF réels.
 */
private class InfraProbe(private val port: FakeReadingPort) {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state.asStateFlow()

    fun pull() {
        _state.value = port.read()
    }
}

class TestInfrastructureSmokeTest {

    @Test
    fun `JUnit, runTest, Turbine et MockK fonctionnent ensemble`() = runTest {
        // MockK : simulation d'une dépendance simple.
        val port = mockk<FakeReadingPort>()
        every { port.read() } returns 42

        val probe = InfraProbe(port)

        // Turbine : assertion de séquence sur un StateFlow, au sein d'une
        // coroutine de test (runTest) qui contrôle le scheduling.
        probe.state.test {
            assertEquals(0, awaitItem())
            probe.pull()
            assertEquals(42, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // MockK : vérification que la dépendance a bien été sollicitée.
        verify(exactly = 1) { port.read() }
    }
}
