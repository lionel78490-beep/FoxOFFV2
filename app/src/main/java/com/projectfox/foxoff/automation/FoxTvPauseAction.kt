package com.projectfox.foxoff.automation

import android.content.Context
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.tv.FoxTvController

object FoxTvPauseAction {

    /**
     * Cible EXCLUSIVEMENT la TV utilisée (active) — jamais la surcharge IP
     * manuelle de TvLabActivity, jamais une boucle sur plusieurs TV. Passe
     * par FoxTvEngine.commandTarget() (état live, pas FoxTvSettings) pour
     * n'envoyer une commande que si cette TV est active ET que son dernier
     * état connu est CONNECTED — jamais vers une TV hors ligne ou en
     * l'absence de TV active.
     */
    suspend fun execute(
        context: Context
    ): Result<Unit> {
        val target = FoxCore.tvEngine?.commandTarget()
            ?: return Result.failure(
                IllegalStateException(
                    "Aucune TV utilisée et joignable"
                )
            )

        return FoxTvController.togglePlayPause(
            context = context,
            tvIp = target.address
        )
    }
}
