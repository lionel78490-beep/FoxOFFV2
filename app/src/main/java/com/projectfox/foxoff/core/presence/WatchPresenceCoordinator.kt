package com.projectfox.foxoff.core.presence

import android.content.Context
import com.projectfox.foxoff.core.application.FoxCore
import com.projectfox.foxoff.core.application.WatchSettings
import com.projectfox.foxoff.core.logging.FoxLogger
import com.projectfox.foxoff.core.watch.WatchTransport
import com.projectfox.foxoff.core.watch.WearOsTransport
import com.projectfox.foxoff.sensors.events.SensorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Duration

/**
 * Câblage PRODUCTION de WatchPresenceEngine : scope Dispatchers.Default
 * possédé ICI (aucune autre copie ailleurs), effets réels (GMS pour la
 * demande de confirmation et la reconnexion, FoxCore.eventBus pour la
 * publication). La logique de décision elle-même est entièrement déléguée
 * à WatchPresenceEngine (piloté par échéance, voir sa documentation) et
 * WatchReconnectionEngine (délai progressif tant que Hors ligne), testés
 * indépendamment avec un scope virtuel (voir WatchPresenceEngineTest /
 * WatchReconnectionEngineTest) — ce fichier ne fait que les relier au monde
 * réel et met à jour WatchConnectionStateHolder (état UI).
 *
 * start(context) doit être appelé UNE SEULE FOIS, depuis
 * FoxCore.startOrchestration() (dès le lancement du processus).
 */
object WatchPresenceCoordinator {

    // Délai de grâce avant de suspecter une absence — compromis "modéré" :
    // "Hors ligne" visible en ~20s (15s de grâce + 5s de confirmation).
    private val GRACE_PERIOD: Duration = Duration.ofSeconds(15)

    // Délai laissé à la montre pour répondre à la demande de confirmation
    // avant de déclarer "Hors ligne".
    private val CONFIRMATION_TIMEOUT: Duration = Duration.ofSeconds(5)

    // Anti-rebond du chemin événementiel rapide (onPeerDisconnected).
    private const val DISCONNECT_DEBOUNCE_MS = 3_000L

    // Reconnexion automatique tant que la montre connue est Hors ligne :
    // délai progressif pour préserver la batterie, plafonné — accéléré au
    // même compromis "modéré" (recharge visible en quelques secondes).
    private val RECONNECT_INITIAL_BACKOFF: Duration = Duration.ofSeconds(5)
    private val RECONNECT_MAX_BACKOFF: Duration = Duration.ofSeconds(20)
    private const val RECONNECT_BACKOFF_MULTIPLIER = 1.5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appContext: Context? = null

    // Wear OS par défaut — voir WatchTransport pour l'abstraction préparée
    // en vue d'un futur GarminTransport (Connect IQ Mobile SDK).
    @Volatile
    private var transport: WatchTransport? = null

    private val engine = WatchPresenceEngine(
        gracePeriod = GRACE_PERIOD,
        confirmationTimeout = CONFIRMATION_TIMEOUT,
        disconnectDebounceMs = DISCONNECT_DEBOUNCE_MS,
        scope = scope,
        onVerifying = {
            FoxLogger.i("FOX-PRESENCE | ${GRACE_PERIOD.seconds}s sans preuve -> Vérification de la montre")
            WatchConnectionStateHolder.update(WatchConnectionState.VERIFYING)
        },
        onRequestConfirmation = {
            sendConfirmationRequest()
        },
        onDeclareOffline = {
            WatchConnectionStateHolder.update(WatchConnectionState.OFFLINE)
            FoxLogger.w("FOX-PRESENCE | Déclaration Hors ligne -> publication WatchDisconnected")
            FoxCore.eventBus.publish(SensorEvent.WatchDisconnected)
        }
    )

    private val reconnectionEngine = WatchReconnectionEngine(
        initialBackoff = RECONNECT_INITIAL_BACKOFF,
        maxBackoff = RECONNECT_MAX_BACKOFF,
        backoffMultiplier = RECONNECT_BACKOFF_MULTIPLIER,
        scope = scope,
        isNodeAvailable = {
            transport?.connectedDevices()?.isNotEmpty() == true
        },
        sendReconnectionRequest = {
            val device = transport?.connectedDevices()?.firstOrNull()
            if (device != null) {
                if (transport?.requestInfo(device.id) == true) {
                    FoxLogger.i("FOX-PRESENCE | Reconnexion | Demande envoyée à ${device.id}")
                    WatchConnectionStateHolder.update(WatchConnectionState.RECONNECTING)
                } else {
                    FoxLogger.e("FOX-PRESENCE | Reconnexion | Échec envoi demande")
                }
            }
        }
    )

    /** À appeler UNE FOIS depuis FoxCore.startOrchestration(). */
    fun start(context: Context, transport: WatchTransport = WearOsTransport(context)) {
        appContext = context.applicationContext
        this.transport = transport
        FoxLogger.i("FOX-PRESENCE | Moteur de présence armé (grâce ${GRACE_PERIOD.seconds}s + confirmation ${CONFIRMATION_TIMEOUT.seconds}s)")
    }

    /** BPM ou watch_info reçu — y compris une réponse à une demande de confirmation. */
    fun recordProof() {
        engine.recordProof()
        WatchConnectionStateHolder.update(WatchConnectionState.CONNECTED)
    }

    fun onPeerConnected(knownNodeId: String?, eventNodeId: String) =
        engine.onPeerConnected(knownNodeId, eventNodeId)

    fun onPeerDisconnected(knownNodeId: String?, eventNodeId: String) {
        if (!WatchNodeFilter.isActiveNode(knownNodeId, eventNodeId)) {
            FoxLogger.i("FOX-PRESENCE | Déconnexion d'un nœud Wear différent de la montre active -> ignorée")
            return
        }
        engine.onPeerDisconnected(knownNodeId, eventNodeId)
    }

    private suspend fun sendConfirmationRequest() {
        val context = appContext ?: return
        val nodeId = WatchSettings.getLastKnownNodeId(context) ?: return
        if (transport?.requestInfo(nodeId) == true) {
            FoxLogger.i("FOX-PRESENCE | Demande de confirmation envoyée à $nodeId")
        } else {
            FoxLogger.e("FOX-PRESENCE | Échec envoi demande de confirmation")
        }
    }

    /**
     * Piloté depuis FoxCore (observation de FoxBrainState.watchConnected —
     * source de vérité unique) : couvre TOUS les chemins qui mènent à
     * "Hors ligne", y compris un lancement d'app où la montre connue n'a
     * jamais répondu de toute la durée de vie du processus.
     */
    fun onWatchOffline(context: Context) {
        appContext = context.applicationContext
        val known = WatchSettings.getLastKnownNodeId(context) != null ||
                WatchSettings.getLastKnownWatchName(context) != null
        if (!known) return
        if (WatchConnectionStateHolder.state.value != WatchConnectionState.RECONNECTING) {
            WatchConnectionStateHolder.update(WatchConnectionState.OFFLINE)
        }
        reconnectionEngine.onOffline()
    }

    fun onWatchConnected() {
        WatchConnectionStateHolder.update(WatchConnectionState.CONNECTED)
        reconnectionEngine.onOnline()
    }

}
