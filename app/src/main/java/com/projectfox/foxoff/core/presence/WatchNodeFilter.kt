package com.projectfox.foxoff.core.presence

/**
 * Un téléphone peut voir plusieurs nœuds Wear (montre d'un autre profil,
 * appareil de test, etc.). Une connexion/déconnexion ne doit affecter l'état
 * de présence que si elle concerne LA montre reconnue (WatchSettings), pas
 * un nœud quelconque.
 */
object WatchNodeFilter {
    fun isActiveNode(knownNodeId: String?, eventNodeId: String): Boolean {
        return knownNodeId != null && knownNodeId == eventNodeId
    }
}
