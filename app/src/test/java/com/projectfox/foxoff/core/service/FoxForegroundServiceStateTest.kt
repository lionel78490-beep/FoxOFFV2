package com.projectfox.foxoff.core.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vérifie que l'état réel du service (distinct de l'intention persistée
 * dans BackgroundServiceSettings) reflète bien des transitions précises,
 * pas un simple booléen.
 */
class FoxForegroundServiceStateTest {

    @After
    fun tearDown() {
        // Singleton partagé entre les tests : on le remet dans son état par
        // défaut pour ne pas faire fuiter l'état d'un test vers un autre.
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STOPPED)
    }

    @Test
    fun `default state is STOPPED`() {
        assertEquals(FoxForegroundServiceStatus.STOPPED, FoxForegroundServiceState.status.value)
    }

    @Test
    fun `reflects the full start sequence`() {
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STARTING)
        assertEquals(FoxForegroundServiceStatus.STARTING, FoxForegroundServiceState.status.value)

        FoxForegroundServiceState.update(FoxForegroundServiceStatus.RUNNING)
        assertEquals(FoxForegroundServiceStatus.RUNNING, FoxForegroundServiceState.status.value)

        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STOPPED)
        assertEquals(FoxForegroundServiceStatus.STOPPED, FoxForegroundServiceState.status.value)
    }

    @Test
    fun `a failed promotion is reported distinctly as ERROR, not STOPPED`() {
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.STARTING)
        FoxForegroundServiceState.update(FoxForegroundServiceStatus.ERROR)

        assertEquals(FoxForegroundServiceStatus.ERROR, FoxForegroundServiceState.status.value)
    }
}
