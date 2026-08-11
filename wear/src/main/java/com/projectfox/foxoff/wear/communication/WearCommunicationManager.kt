package com.projectfox.foxoff.wear.communication

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.projectfox.foxoff.wear.communication.model.ConnectionState
import com.projectfox.foxoff.wear.core.FoxWearLogger
import com.projectfox.foxoff.wear.sensors.model.WearHeartRateSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.ArrayDeque

/**
 * Manages communication between the watch and the phone.
 * Handles connection states and temporary data buffering.
 */
class WearCommunicationManager(private val context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: Flow<ConnectionState> = _connectionState.asSharedFlow()

    private val buffer = ArrayDeque<WearHeartRateSample>()
    
    private var cachedNodes: List<Node> = emptyList()
    private var lastNodeDiscoveryTime: Long = 0

    companion object {
        private const val MAX_BUFFER_SIZE = 50
        private const val DISCOVERY_INTERVAL_MS = 30000 // 30s cache
    }

    init {
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
    }

    /**
     * Sends a heart rate sample to the phone.
     * Buffers the sample if disconnected.
     */
    suspend fun sendSample(sample: WearHeartRateSample) {
        val nodes = getNodes()
        val isConnected = nodes.isNotEmpty()
        
        FoxWearLogger.i("FOX-WEAR | Envoi BPM. Nombre de nœuds : ${nodes.size}")
        nodes.forEach { node ->
            FoxWearLogger.i("FOX-WEAR | Nœud cible : ${node.displayName} (id=${node.id})")
        }

        if (isConnected) {
            if (_connectionState.replayCache.firstOrNull() != ConnectionState.CONNECTED) {
                FoxWearLogger.i("FOX-WEAR | Connexion au téléphone restaurée")
                _connectionState.emit(ConnectionState.CONNECTED)
            }
            flushBuffer(nodes.map { it.id })
            transmit(sample, nodes.map { it.id })
        } else {
            if (_connectionState.replayCache.firstOrNull() == ConnectionState.CONNECTED) {
                FoxWearLogger.w("FOX-WEAR | Connexion au téléphone perdue")
                _connectionState.emit(ConnectionState.DISCONNECTED)
            }
            bufferSample(sample)
        }
    }

    private suspend fun getNodes(): List<Node> {
        val now = System.currentTimeMillis()
        if (cachedNodes.isNotEmpty() && (now - lastNodeDiscoveryTime) < DISCOVERY_INTERVAL_MS) {
            return cachedNodes
        }

        return withContext(Dispatchers.IO) {
            try {
                FoxWearLogger.d("FOX-WEAR | Recherche de nœuds connectés (Data Layer)...")
                val nodes = Tasks.await(nodeClient.connectedNodes)
                cachedNodes = nodes
                lastNodeDiscoveryTime = System.currentTimeMillis()
                
                if (nodes.isNotEmpty()) {
                    nodes.forEach { node ->
                        FoxWearLogger.i("FOX-WEAR | Connexion au téléphone | Nœud trouvé: ${node.displayName} (ID: ${node.id})")
                    }
                } else {
                    FoxWearLogger.w("FOX-WEAR | Aucun nœud détecté. Vérifiez l'application ID et la signature.")
                }
                nodes
            } catch (e: Exception) {
                FoxWearLogger.e("FOX-WEAR | Erreur lors de la récupération des nœuds", e)
                emptyList()
            }
        }
    }

    private fun bufferSample(sample: WearHeartRateSample) {
        if (buffer.size >= MAX_BUFFER_SIZE) {
            buffer.removeFirst()
        }
        buffer.addLast(sample)
        FoxWearLogger.d("Sample buffered. Buffer size: ${buffer.size}")
    }

    private suspend fun flushBuffer(nodeIds: List<String>) {
        while (buffer.isNotEmpty()) {
            val sample = buffer.removeFirst()
            transmit(sample, nodeIds)
        }
    }

    private suspend fun transmit(sample: WearHeartRateSample, nodeIds: List<String>) {
        val payload = ByteBuffer.allocate(4).putFloat(sample.bpm).array()
        nodeIds.forEach { nodeId ->
            try {
                FoxWearLogger.i("FOX-WEAR | GMS MessageLayer | Envoi BPM ${sample.bpm} vers $nodeId | Path: /foxoff/hr")
                val result = withContext(Dispatchers.IO) {
                    Tasks.await(messageClient.sendMessage(nodeId, "/foxoff/hr", payload))
                }
                FoxWearLogger.i("FOX-WEAR | GMS MessageLayer | Succès envoi à $nodeId (ID: $result)")
            } catch (e: Exception) {
                FoxWearLogger.e("FOX-WEAR | GMS MessageLayer | ÉCHEC envoi à $nodeId", e)
            }
        }
    }

    suspend fun updateConnectionState(state: ConnectionState) {
        _connectionState.emit(state)
        FoxWearLogger.i("Connection state changed to $state")
    }

    /**
     * Envoie une magnitude de mouvement agrégée (voir MovementEngine) au
     * téléphone. Pas de buffer contrairement au BPM : une magnitude
     * périodique manquée ponctuellement n'a pas besoin d'être rattrapée
     * (contrairement à un échantillon BPM), la suivante arrive 30s après.
     */
    suspend fun sendMovement(magnitude: Float) {
        val nodes = getNodes()
        if (nodes.isEmpty()) return

        val payload = ByteBuffer.allocate(4).putFloat(magnitude).array()
        nodes.map { it.id }.forEach { nodeId ->
            try {
                withContext(Dispatchers.IO) {
                    Tasks.await(messageClient.sendMessage(nodeId, "/foxoff/movement", payload))
                }
                FoxWearLogger.d("FOX-WEAR | Mouvement $magnitude envoyé à $nodeId")
            } catch (e: Exception) {
                FoxWearLogger.e("FOX-WEAR | Échec envoi mouvement", e)
            }
        }
    }

    /**
     * Sends watch information (name and battery) to the phone. Retourne
     * true si au moins un nœud téléphone était joignable (donc un envoi a
     * été tenté) — permet à FoxWearCore de savoir s'il doit réessayer au
     * démarrage plutôt que d'attendre le prochain envoi périodique.
     */
    suspend fun sendWatchInfo(name: String, battery: Int): Boolean {
        val nodes = getNodes()
        if (nodes.isEmpty()) return false

        val payload = "$name|$battery".toByteArray()
        val nodeIds = nodes.map { it.id }
        nodeIds.forEach { nodeId ->
            try {
                withContext(Dispatchers.IO) {
                    Tasks.await(messageClient.sendMessage(nodeId, "/foxoff/watch_info", payload))
                }
                FoxWearLogger.i("FOX-WEAR | Infos montre envoyées à $nodeId")
            } catch (e: Exception) {
                FoxWearLogger.e("FOX-WEAR | Erreur envoi infos montre", e)
            }
        }
        return true
    }

    /**
     * Répond à une demande de confirmation "toujours là" (voir
     * ConfirmAwakeNotifier côté montre / SleepPauseCoordinator côté
     * téléphone). Même pattern que sendMovement()/sendWatchInfo() : pas de
     * buffer, une réponse manquée ponctuellement n'a pas de sens à
     * rattraper plus tard.
     */
    suspend fun sendConfirmResponse(): Boolean {
        val nodes = getNodes()
        if (nodes.isEmpty()) return false

        nodes.map { it.id }.forEach { nodeId ->
            try {
                withContext(Dispatchers.IO) {
                    Tasks.await(messageClient.sendMessage(nodeId, "/foxoff/confirm_response", byteArrayOf()))
                }
                FoxWearLogger.i("FOX-WEAR | Confirmation 'toujours là' envoyée à $nodeId")
            } catch (e: Exception) {
                FoxWearLogger.e("FOX-WEAR | Échec envoi confirmation", e)
            }
        }
        return true
    }
}
