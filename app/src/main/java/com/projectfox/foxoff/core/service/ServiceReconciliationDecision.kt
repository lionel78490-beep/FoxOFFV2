package com.projectfox.foxoff.core.service

enum class ServiceReconciliationAction { START, STOP, DISABLE_AND_STOP, NO_OP }

/**
 * Décision PURE (aucun effet de bord, aucune dépendance Android) : réconcilie
 * l'intention persistée (BackgroundServiceSettings.isEnabled) avec l'état
 * réel du service (FoxForegroundServiceState). Corrige le cas où l'intention
 * vaut true mais le service n'a jamais (re)démarré — relance de processus,
 * nouvelle installation — sans quoi l'utilisateur devait désactiver puis
 * réactiver l'interrupteur manuellement pour que la surveillance reprenne.
 *
 * Voir ServiceReconciler pour le câblage réel (permissions Android,
 * visibilité de la notification, démarrage/arrêt effectif du service).
 */
object ServiceReconciliationDecision {
    fun decide(
        intentEnabled: Boolean,
        realStatus: FoxForegroundServiceStatus,
        bluetoothGranted: Boolean,
        notificationsVisible: Boolean
    ): ServiceReconciliationAction {
        val activeOrStarting = realStatus == FoxForegroundServiceStatus.RUNNING ||
                realStatus == FoxForegroundServiceStatus.STARTING

        if (!intentEnabled) {
            // Intention désactivée : un service encore RUNNING/STARTING est une
            // incohérence à corriger (STOP) ; sinon rien à faire (NO_OP).
            return if (activeOrStarting) ServiceReconciliationAction.STOP else ServiceReconciliationAction.NO_OP
        }

        // Intention activée : déjà actif ou en cours de démarrage -> ne pas
        // relancer par-dessus.
        if (activeOrStarting) return ServiceReconciliationAction.NO_OP

        // realStatus est STOPPED ou ERROR (état récupérable) : vérifier les
        // prérequis avant de tenter un démarrage.
        if (!notificationsVisible || !bluetoothGranted) return ServiceReconciliationAction.DISABLE_AND_STOP

        return ServiceReconciliationAction.START
    }
}
