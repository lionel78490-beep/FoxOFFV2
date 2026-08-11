package com.projectfox.foxoff.core.service

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.application.WatchSettings
import com.projectfox.foxoff.core.automation.SleepPauseCoordinator
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.presence.WatchNodeFilter
import com.projectfox.foxoff.core.presence.WatchPresenceCoordinator
import com.projectfox.foxoff.sensors.events.SensorEvent
import com.projectfox.foxoff.sensors.model.HeartRateSample
import com.projectfox.foxoff.sensors.model.SensorBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.time.Instant

/**
 * Listens for data coming from the Wear OS module.
 *
 * Déclaré au Manifest avec l'action BIND_LISTENER (voir AndroidManifest.xml)
 * — c'est ce qui fait lier ce service par Google Play Services pour TOUS
 * les événements Data Layer, y compris onPeerConnected/onPeerDisconnected
 * ci-dessous, même processus non actif. L'ancienne déclaration
 * (MESSAGE_RECEIVED + filtre de chemin) ne couvrait pas ces deux callbacks,
 * ce qui était la cause du bug "Bluetooth coupé mais toujours Connectée".
 * Toute décision de présence passe par WatchPresenceCoordinator (source de
 * vérité unique, qui combine ce chemin événementiel avec un chemin de
 * fraîcheur basé sur les preuves applicatives — voir sa documentation).
 */
class PhoneWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FoxLogger.i("FOX-PHONE | Listener | Service CRÉÉ et prêt à recevoir des messages")
    }

    override fun onPeerConnected(node: Node) {
        super.onPeerConnected(node)
        FoxLogger.i(
            "FOX-PHONE | Listener | Présence | onPeerConnected : " +
                    "${node.displayName} (${node.id}) isNearby=${node.isNearby}"
        )
        val knownNodeId = WatchSettings.getLastKnownNodeId(this)
        WatchPresenceCoordinator.onPeerConnected(knownNodeId, node.id)
        if (!WatchNodeFilter.isActiveNode(knownNodeId, node.id)) return

        WatchSettings.saveLastKnownNodeId(this, node.id)

        // Ne repasse PAS "Connectée" ici directement : demande d'abord une
        // confirmation réelle (/foxoff/watch_info). Le passage définitif à
        // "Connectée" se fait quand la réponse arrive (onMessageReceived).
        serviceScope.launch {
            requestWatchInfo(node.id)
        }
    }

    override fun onPeerDisconnected(node: Node) {
        super.onPeerDisconnected(node)
        FoxLogger.i(
            "FOX-PHONE | Listener | Présence | onPeerDisconnected : " +
                    "${node.displayName} (${node.id}) isNearby=${node.isNearby}"
        )
        val knownNodeId = WatchSettings.getLastKnownNodeId(this)
        WatchPresenceCoordinator.onPeerDisconnected(knownNodeId, node.id)
    }

    private suspend fun requestWatchInfo(nodeId: String) = withContext(Dispatchers.IO) {
        try {
            Tasks.await(
                Wearable.getMessageClient(this@PhoneWearListenerService)
                    .sendMessage(nodeId, "/foxoff/request_watch_info", byteArrayOf())
            )
            FoxLogger.i("FOX-PHONE | Listener | Présence | Demande watch_info envoyée à $nodeId (reconnexion)")
        } catch (e: Exception) {
            FoxLogger.e("FOX-PHONE | Listener | Présence | Échec envoi demande watch_info (reconnexion)", e)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == "/foxoff/hr") {
            val bpm = ByteBuffer.wrap(messageEvent.data).float
            val now = Instant.now()
            WatchPresenceCoordinator.recordProof()
            FoxLogger.i("FOX-PHONE | Listener | Message REÇU [/foxoff/hr] : $bpm BPM | Source: ${messageEvent.sourceNodeId}")

            serviceScope.launch {
                val sample = HeartRateSample(
                    bpm = bpm,
                    accuracy = 3,
                    timestamp = now,
                    backend = SensorBackend.UNKNOWN
                )
                FoxLogger.i("FOX-PHONE | Listener | Publication EventBus BPM: $bpm")
                FoxCore.eventBus.publish(SensorEvent.HeartRateReceived(sample))
            }
        } else if (messageEvent.path == "/foxoff/movement") {
            val magnitude = ByteBuffer.wrap(messageEvent.data).float
            WatchPresenceCoordinator.recordProof()
            FoxLogger.i("FOX-PHONE | Listener | Message REÇU [/foxoff/movement] : $magnitude | Source: ${messageEvent.sourceNodeId}")

            serviceScope.launch {
                FoxCore.eventBus.publish(SensorEvent.MovementDetected(magnitude))
            }
        } else if (messageEvent.path == "/foxoff/watch_info") {
            val data = String(messageEvent.data).split("|")
            if (data.size >= 2) {
                val name = data[0]
                val battery = data[1].toIntOrNull() ?: 0
                WatchPresenceCoordinator.recordProof()
                FoxLogger.i("FOX-PHONE | Listener | Infos Montre REÇUES : $name | Batterie : $battery% | Source: ${messageEvent.sourceNodeId}")

                // Confirme le nœud de la montre active à partir d'une vraie
                // réponse applicative, pas seulement de la connexion
                // Bluetooth (plus fiable qu'onPeerConnected seul).
                WatchSettings.saveLastKnownNodeId(this, messageEvent.sourceNodeId)

                serviceScope.launch {
                    FoxCore.eventBus.publish(SensorEvent.WatchInfoReceived(name, battery, true))
                }
            }
        } else if (messageEvent.path == "/foxoff/confirm_response") {
            FoxLogger.i("FOX-PHONE | Listener | Confirmation reçue depuis la montre -> annulation de la pause")
            SleepPauseCoordinator.cancel()
        }
    }
}
