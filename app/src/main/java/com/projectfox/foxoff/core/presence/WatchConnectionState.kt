package com.projectfox.foxoff.core.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État de connexion UI explicite, plus riche que FoxBrainState.watchConnected
 * (simple Boolean) : distingue une vérification en cours (délai de grâce
 * écoulé, demande de confirmation envoyée, en attente de réponse) et une
 * reconnexion active (nœud détecté après une coupure, demande envoyée) d'un
 * simple Hors ligne passif.
 *
 * FoxBrainState.watchConnected reste la source de vérité pour "connectée ou
 * non" dans le reste de l'app (déjà largement utilisée) — cet état la
 * complète avec le détail des sous-phases, alimenté exclusivement par
 * WatchPresenceCoordinator.
 */
enum class WatchConnectionState {
    CONNECTED,
    VERIFYING,
    OFFLINE,
    RECONNECTING
}

object WatchConnectionStateHolder {
    private val _state = MutableStateFlow(WatchConnectionState.OFFLINE)
    val state: StateFlow<WatchConnectionState> = _state.asStateFlow()

    fun update(newState: WatchConnectionState) {
        _state.value = newState
    }
}
