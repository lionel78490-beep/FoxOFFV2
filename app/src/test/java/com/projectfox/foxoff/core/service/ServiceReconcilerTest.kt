package com.projectfox.foxoff.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceReconcilerTest {

    private class Recorder : ServiceReconciliationActions {
        var startCount = 0
        var stopCount = 0
        var disableAndStopCount = 0
        var lastReason: String? = null

        override fun start() {
            startCount++
        }

        override fun stop() {
            stopCount++
        }

        override fun disableAndStop(reason: String) {
            disableAndStopCount++
            lastReason = reason
        }
    }

    @Test
    fun `6 - several resume events with a still-STOPPED status start the service only once`() {
        val recorder = Recorder()
        val reconciler = ServiceReconciler(recorder)

        // Simule plusieurs ON_START rapprochés (ex: recompositions/reprises
        // successives) où l'état réel n'a PAS ENCORE eu le temps de passer à
        // STARTING entre deux appels — le pire cas pour un simple verrou de
        // réentrance, mais pas pour le verrou startIssued.
        repeat(5) {
            reconciler.reconcile(
                intentEnabled = true,
                realStatus = FoxForegroundServiceStatus.STOPPED,
                bluetoothGranted = true,
                notificationsVisible = true
            )
        }

        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `the latch re-arms once the service is observed RUNNING, allowing a future legitimate restart`() {
        val recorder = Recorder()
        val reconciler = ServiceReconciler(recorder)

        reconciler.reconcile(true, FoxForegroundServiceStatus.STOPPED, bluetoothGranted = true, notificationsVisible = true)
        assertEquals(1, recorder.startCount)

        // L'état réel a rattrapé le démarrage.
        reconciler.reconcile(true, FoxForegroundServiceStatus.RUNNING, bluetoothGranted = true, notificationsVisible = true)

        // Le service meurt plus tard (ex: forcé par l'utilisateur) : un
        // nouveau cycle STOPPED doit pouvoir redémarrer.
        reconciler.reconcile(true, FoxForegroundServiceStatus.STOPPED, bluetoothGranted = true, notificationsVisible = true)

        assertEquals(2, recorder.startCount)
    }

    @Test
    fun `disabling intent while running stops exactly once`() {
        val recorder = Recorder()
        val reconciler = ServiceReconciler(recorder)

        reconciler.reconcile(false, FoxForegroundServiceStatus.RUNNING, bluetoothGranted = true, notificationsVisible = true)

        assertEquals(1, recorder.stopCount)
        assertEquals(0, recorder.startCount)
    }

    @Test
    fun `invalid prerequisites disable and stop with a meaningful reason`() {
        val recorder = Recorder()
        val reconciler = ServiceReconciler(recorder)

        reconciler.reconcile(true, FoxForegroundServiceStatus.STOPPED, bluetoothGranted = true, notificationsVisible = false)

        assertEquals(1, recorder.disableAndStopCount)
        assertEquals(0, recorder.startCount)
        assert(recorder.lastReason!!.contains("Notification"))
    }
}
