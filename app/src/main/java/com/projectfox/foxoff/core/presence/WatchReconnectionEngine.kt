package com.projectfox.foxoff.core.presence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Reconnexion automatique tant que la montre CONNUE est Hors ligne : sonde
 * périodiquement la présence d'un nœud Wear (sans jamais la considérer
 * comme une preuve de connexion à elle seule, voir WatchPresenceEngine) et,
 * dès qu'un nœud apparaît, déclenche une demande /foxoff/request_watch_info
 * via l'effet injecté. Ne décide JAMAIS elle-même "Connectée" — seule une
 * vraie réponse applicative (recordProof côté WatchPresenceCoordinator) le
 * fait, via onOnline() qui arrête alors les tentatives.
 *
 * Délai progressif (backoff) entre les tentatives pour préserver la
 * batterie, plafonné à maxBackoff. Une seule tentative à la fois par
 * construction : la boucle attend la fin de sendReconnectionRequest() avant
 * de planifier la suivante, donc pas de concurrence possible.
 */
class WatchReconnectionEngine(
    private val initialBackoff: Duration,
    private val maxBackoff: Duration,
    private val backoffMultiplier: Double,
    private val scope: CoroutineScope,
    private val isNodeAvailable: suspend () -> Boolean,
    private val sendReconnectionRequest: suspend () -> Unit
) {
    private var attemptJob: Job? = null

    val isAttempting: Boolean get() = attemptJob?.isActive == true

    /** La montre connue est Hors ligne : démarre les tentatives. Idempotent. */
    fun onOffline() {
        if (attemptJob?.isActive == true) return
        attemptJob = scope.launch {
            var backoffMs = initialBackoff.toMillis()
            while (isActive) {
                if (isNodeAvailable()) {
                    sendReconnectionRequest()
                }
                delay(backoffMs)
                backoffMs = (backoffMs * backoffMultiplier).toLong().coerceAtMost(maxBackoff.toMillis())
            }
        }
    }

    /** Une preuve applicative réelle est arrivée : arrête immédiatement les tentatives. */
    fun onOnline() {
        attemptJob?.cancel()
        attemptJob = null
    }

    /** Arrêt complet (ex: arrêt du service) — annule toute tentative en cours. */
    fun stop() {
        attemptJob?.cancel()
        attemptJob = null
    }
}
