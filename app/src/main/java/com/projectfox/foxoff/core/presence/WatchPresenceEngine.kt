package com.projectfox.foxoff.core.presence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Moteur de présence PILOTÉ PAR ÉCHÉANCE — pas par sondage périodique.
 * Chaque preuve applicative (recordProof) programme une échéance unique à
 * "maintenant + gracePeriod" ; une nouvelle preuve annule et reprogramme
 * cette échéance depuis zéro. À l'échéance : passage visuel "Vérification"
 * (onVerifying), envoi d'une demande de confirmation (onRequestConfirmation),
 * puis une seconde échéance à "+ confirmationTimeout". Si aucune preuve
 * n'arrive avant CETTE seconde échéance, la montre est déclarée Hors ligne
 * (onDeclareOffline). Une preuve arrivant à N'IMPORTE quel moment (pendant
 * la grâce initiale OU pendant la fenêtre de confirmation) annule tout et
 * repart d'une échéance fraîche — jamais de passage par Hors ligne si une
 * réponse arrive à temps.
 *
 * Les deux échéances sont delay() successifs au sein d'une seule et même
 * coroutine (deadlineJob) : l'annuler à tout moment (recordProof) coupe la
 * séquence là où elle en est, sans variable d'état séparée à synchroniser.
 * Aucun ticker, aucun sondage — un seul Job actif à la fois, consommation
 * minimale.
 *
 * Complémentaire du chemin événementiel (onPeerConnected/onPeerDisconnected,
 * anti-rebond de disconnectDebounceMs) : reste le chemin RAPIDE quand l'OS
 * notifie effectivement la déconnexion, indépendant des deux échéances
 * ci-dessus. Sa déclaration Hors ligne annule aussi l'échéance en cours
 * (plus besoin d'attendre la vérification si l'OS a déjà confirmé la perte).
 */
class WatchPresenceEngine(
    private val gracePeriod: Duration,
    private val confirmationTimeout: Duration,
    private val disconnectDebounceMs: Long,
    private val scope: CoroutineScope,
    private val onVerifying: suspend () -> Unit = {},
    private val onRequestConfirmation: suspend () -> Unit,
    private val onDeclareOffline: suspend () -> Unit
) {
    private var deadlineJob: Job? = null
    private var disconnectDebounceJob: Job? = null

    val isArmed: Boolean get() = deadlineJob?.isActive == true

    /** BPM ou watch_info reçu — y compris une réponse à une demande de confirmation. */
    fun recordProof() {
        disconnectDebounceJob?.cancel()
        disconnectDebounceJob = null
        deadlineJob?.cancel()
        deadlineJob = scope.launch {
            delay(gracePeriod.toMillis())
            onVerifying()
            onRequestConfirmation()
            delay(confirmationTimeout.toMillis())
            onDeclareOffline()
        }
    }

    /** La montre active revient (chemin événementiel rapide). */
    fun onPeerConnected(knownNodeId: String?, eventNodeId: String) {
        if (!WatchNodeFilter.isActiveNode(knownNodeId, eventNodeId)) return
        disconnectDebounceJob?.cancel()
        disconnectDebounceJob = null
    }

    /** La montre active se déconnecte (chemin événementiel rapide). */
    fun onPeerDisconnected(knownNodeId: String?, eventNodeId: String) {
        if (!WatchNodeFilter.isActiveNode(knownNodeId, eventNodeId)) return
        disconnectDebounceJob?.cancel()
        disconnectDebounceJob = scope.launch {
            delay(disconnectDebounceMs)
            deadlineJob?.cancel()
            deadlineJob = null
            onDeclareOffline()
        }
    }

    fun reset() {
        deadlineJob?.cancel()
        deadlineJob = null
        disconnectDebounceJob?.cancel()
        disconnectDebounceJob = null
    }
}
