package com.projectfox.foxoff.automation

import com.projectfox.foxoff.tv.FoxTvEngine

class RealTvController(private val engine: FoxTvEngine) : TvController {
    override suspend fun pause() {
        // manual=false (2026-08-16) : cette pause est la pause AUTOMATIQUE du
        // sommeil (SleepPauseCoordinator.executePause()) — elle ne doit
        // surtout pas se signaler elle-même comme une reprise manuelle
        // auprès de TvAutoPowerOffCoordinator, sous peine d'annuler
        // immédiatement l'extinction automatique programmée derrière elle.
        engine.pause(manual = false)
    }
}
