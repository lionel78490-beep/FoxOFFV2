package com.projectfox.foxoff.core.service

import java.util.concurrent.atomic.AtomicBoolean

interface ServiceReconciliationActions {
    fun start()
    fun stop()
    fun disableAndStop(reason: String)
}

/**
 * Orchestration TESTABLE de ServiceReconciliationDecision : effets injectés
 * (voir FoxServiceReconciliation pour le câblage réel Android), aucune
 * dépendance directe à Context/Service. Le verrou startIssued empêche
 * plusieurs démarrages lors de reprises successives rapprochées où l'état
 * réel n'a pas encore eu le temps de passer à STARTING/RUNNING entre deux
 * appels (ex: plusieurs événements ON_START coup sur coup) — sans lui, deux
 * reconcile() consécutifs verraient tous les deux "STOPPED" et
 * démarreraient tous les deux le service.
 */
class ServiceReconciler(private val actions: ServiceReconciliationActions) {

    private val startIssued = AtomicBoolean(false)

    fun reconcile(
        intentEnabled: Boolean,
        realStatus: FoxForegroundServiceStatus,
        bluetoothGranted: Boolean,
        notificationsVisible: Boolean
    ): ServiceReconciliationAction {
        val action = ServiceReconciliationDecision.decide(
            intentEnabled, realStatus, bluetoothGranted, notificationsVisible
        )

        when (action) {
            ServiceReconciliationAction.START -> {
                if (startIssued.compareAndSet(false, true)) {
                    actions.start()
                }
            }
            ServiceReconciliationAction.STOP -> {
                startIssued.set(false)
                actions.stop()
            }
            ServiceReconciliationAction.DISABLE_AND_STOP -> {
                startIssued.set(false)
                val reason = if (!notificationsVisible) {
                    "Notification indisponible (autorisation ou canal désactivé) : surveillance désactivée."
                } else {
                    "Autorisation Bluetooth manquante : surveillance désactivée."
                }
                actions.disableAndStop(reason)
            }
            ServiceReconciliationAction.NO_OP -> {
                // Une fois l'état réel passé à STARTING/RUNNING (ou l'intention
                // désactivée), on réarme le verrou pour une future séquence
                // légitime de démarrage.
                startIssued.set(false)
            }
        }

        return action
    }
}
