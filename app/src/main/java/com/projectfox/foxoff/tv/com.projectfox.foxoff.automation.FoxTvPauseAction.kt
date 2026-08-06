package com.projectfox.foxoff.automation

import android.content.Context
import com.projectfox.foxoff.tv.FoxTvController
import com.projectfox.foxoff.tv.FoxTvSettings

object FoxTvPauseAction {

    suspend fun execute(
        context: Context
    ): Result<Unit> {
        val tvIp = FoxTvSettings.getTvIp(context)
            ?: return Result.failure(
                IllegalStateException(
                    "Aucune TV configurée"
                )
            )

        return FoxTvController.togglePlayPause(
            context = context,
            tvIp = tvIp
        )
    }
}