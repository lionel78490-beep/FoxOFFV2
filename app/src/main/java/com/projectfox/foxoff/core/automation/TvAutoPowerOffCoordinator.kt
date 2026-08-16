package com.projectfox.foxoff.core.automation

import java.time.Instant

/**
 * Signal d'interaction manuelle avec la télécommande FoxOFF, utilisé par
 * `FoxForegroundService` pour décider si l'extinction automatique de la TV
 * (2026-08-16, demande explicite : "si pas de remise en route de la TV
 * après 10 min, éteindre la TV") doit avoir lieu ou être annulée.
 *
 * FoxOFF n'a AUCUN moyen d'interroger l'état réel de lecture de la TV (la
 * télécommande envoie un simple toggle Play/Pause à l'aveugle, voir
 * TvRemoteClient/FoxTvEngine.pause()) — ce signal est donc un PROXY,
 * pas une vraie détection de reprise : toute pression sur Play/Pause dans
 * RemoteScreen pendant la fenêtre de 10 min est traitée comme "l'utilisateur
 * gère la TV lui-même", que ce soit réellement une reprise ou non. Limite
 * assumée, cohérente avec le reste du mécanisme post-pause (voir
 * POST_PAUSE_HEARTBEAT_MAX_DURATION dans FoxForegroundService).
 */
object TvAutoPowerOffCoordinator {

    @Volatile
    private var lastManualInteractionAt: Instant? = null

    /** Appelé par FoxTvEngine.pause(manual=true) — jamais par le chemin automatique du sommeil. */
    fun notifyManualInteraction() {
        lastManualInteractionAt = Instant.now()
    }

    /** Vrai si une interaction manuelle a eu lieu APRÈS l'instant donné (typiquement le début de la pause). */
    fun hasManualInteractionSince(instant: Instant): Boolean {
        val last = lastManualInteractionAt ?: return false
        return !last.isBefore(instant)
    }

    /** Repart de zéro pour un nouvel épisode de pause (voir FoxForegroundService). */
    fun reset() {
        lastManualInteractionAt = null
    }
}
