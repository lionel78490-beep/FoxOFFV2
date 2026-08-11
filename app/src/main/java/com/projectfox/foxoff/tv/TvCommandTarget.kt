package com.projectfox.foxoff.tv

/**
 * Détermine la TV (s'il y en a une) pouvant recevoir une commande — Play/
 * Pause manuel ou future pause automatique. Décision pure, sans E/S, pour
 * rester testable indépendamment du moteur :
 * - jamais plus d'une TV possible (une seule activeDeviceId, structurellement) ;
 * - jamais une TV non active ;
 * - pour la pause AUTOMATIQUE spécifiquement (voir requireConnected),
 *   jamais une TV dont le dernier statut connu n'est pas CONNECTED —
 *   aucune TV active ou hors ligne ne doit recevoir de commande automatique.
 */
object TvCommandTarget {

    fun resolve(
        activeDeviceId: String?,
        pairedDevices: Map<String, TvDevice>,
        requireConnected: Boolean
    ): TvDevice? {
        val id = activeDeviceId ?: return null
        val device = pairedDevices[id] ?: return null

        return if (!requireConnected || device.status == TvConnectionStatus.CONNECTED) {
            device
        } else {
            null
        }
    }
}
