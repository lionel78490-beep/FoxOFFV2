package com.projectfox.foxoff.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État RÉEL du service de premier plan, distinct de l'INTENTION persistée
 * (BackgroundServiceSettings.isEnabled). Un utilisateur peut avoir demandé
 * l'activation sans que le service tourne réellement (échec de promotion,
 * tué par le système, notifications devenues invisibles...) — l'interface
 * doit toujours pouvoir distinguer les deux.
 *
 * Singleton en mémoire de processus : si le processus est tué, cet état
 * disparaît avec lui (ce qui est correct — pas de valeur périmée à
 * réconcilier). Réinitialisé explicitement à STOPPED au démarrage de
 * l'application (voir FoxApplication) pour ne jamais partir d'un état
 * ambigu, sans jamais se fier au seul appel de onDestroy() comme preuve
 * d'arrêt (non garanti en cas de mort brutale du processus).
 */
enum class FoxForegroundServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

object FoxForegroundServiceState {
    private val _status = MutableStateFlow(FoxForegroundServiceStatus.STOPPED)
    val status: StateFlow<FoxForegroundServiceStatus> = _status.asStateFlow()

    fun update(newStatus: FoxForegroundServiceStatus) {
        _status.value = newStatus
    }
}
