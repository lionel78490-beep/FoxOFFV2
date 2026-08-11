package com.projectfox.foxoff.core.watch

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implémentation WatchTransport pour Wear OS, via l'API GMS Wearable
 * (Data Layer) — extraction directe des appels qui vivaient auparavant en
 * ligne dans WatchPresenceCoordinator et FoxCore.discoverWatch(), sans
 * changement de comportement (mêmes appels, même gestion d'erreur : une
 * exception donne une liste vide / un échec silencieux, jamais de crash).
 */
class WearOsTransport(private val context: Context) : WatchTransport {

    override suspend fun connectedDevices(): List<WatchTransportDevice> =
        withContext(Dispatchers.IO) {
            try {
                Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                    .map { WatchTransportDevice(id = it.id, displayName = it.displayName) }
            } catch (e: Exception) {
                FoxLogger.e("FOX-WATCH-TRANSPORT | Wear OS | Échec connectedDevices()", e)
                emptyList()
            }
        }

    override suspend fun requestInfo(deviceId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Tasks.await(
                    Wearable.getMessageClient(context)
                        .sendMessage(deviceId, "/foxoff/request_watch_info", byteArrayOf())
                )
                true
            } catch (e: Exception) {
                FoxLogger.e("FOX-WATCH-TRANSPORT | Wear OS | Échec requestInfo($deviceId)", e)
                false
            }
        }

    override suspend fun sendCommand(deviceId: String, path: String, payload: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Tasks.await(
                    Wearable.getMessageClient(context).sendMessage(deviceId, path, payload)
                )
                true
            } catch (e: Exception) {
                FoxLogger.e("FOX-WATCH-TRANSPORT | Wear OS | Échec sendCommand($path)", e)
                false
            }
        }
}
