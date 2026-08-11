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

    /**
     * Vérifie une connexion de contrôle réelle vers la TV (handshake TLS),
     * sans envoyer aucune commande. Distingue un échec réseau (TV éteinte,
     * injoignable...) d'un rejet d'identité TLS confirmé, pour décider s'il
     * faut retenter plus tard ou proposer un nouvel appairage.
     */
    suspend fun testConnection(
        context: Context,
        tvIp: String
    ): TvConnectionTestResult {
        if (tvIp.isBlank()) return TvConnectionTestResult.NetworkUnreachable

        return TvRemoteClient.testConnection(
            context = context.applicationContext,
            ip = tvIp.trim()
        )
    }
}