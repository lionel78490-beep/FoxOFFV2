package com.projectfox.foxoff.tv

import android.content.Context
import com.projectfox.foxoff.tv.remote.TvRemoteClient

object FoxTvController {

    suspend fun togglePlayPause(
        context: Context,
        tvIp: String
    ): Result<Unit> {
        return try {
            require(tvIp.isNotBlank()) {
                "Adresse IP de la TV manquante"
            }

            val result = TvRemoteClient.connectAndPause(
                context = context.applicationContext,
                ip = tvIp.trim()
            )

            if (result.contains("REMOTE CONNECTÉ")) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(result)
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}